package com.limelight.pocket;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HorizonSecureHostTlsTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String STATUS = "STATUS cpu=5 ram=10 up=20 bat=-1 ac=1";

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void rejectsWrongIdentityBeforeSendingKeyThenReadsStatusWithPinnedIdentity() throws Exception {
        // Robolectric changes the process-wide providers. A fresh JVM leaves JSSE and the
        // platform crypto services together while using the app's actual socket and framing code.
        Set<String> classpath = new LinkedHashSet<>();
        for (Class<?> type : new Class<?>[]{HorizonSecureHostTlsTest.class, HorizonSecureStatus.class,
                PcCompanion.class, android.content.Context.class, org.junit.Assert.class, org.hamcrest.Matcher.class}) {
            classpath.add(new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath());
        }
        File report = temporary.newFile("tls-loopback.txt");
        Process fixture = new ProcessBuilder(javaTool("java").getAbsolutePath(), "-cp",
                String.join(File.pathSeparator, classpath), getClass().getName(), temporary.getRoot().getPath())
                .redirectErrorStream(true).redirectOutput(report).start();
        try {
            assertTrue("The isolated TLS check exceeded its deadline", fixture.waitFor(45, TimeUnit.SECONDS));
            assertEquals(new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8), 0, fixture.exitValue());
        } finally {
            if (fixture.isAlive()) fixture.destroyForcibly();
        }
    }

    public static void main(String[] args) throws Exception {
        new HorizonSecureHostTlsTest().checkSocket(new File(args[0]));
    }

    private void checkSocket(File directory) throws Exception {
        KeyStore keys = generateFixture(directory);
        char[] password = fixturePassword.toCharArray();
        KeyManagerFactory keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManager.init(keys, password);
        SSLContext fixture = SSLContext.getInstance("TLSv1.2");
        fixture.init(keyManager.getKeyManagers(), null, null);

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket) fixture.getServerSocketFactory()
                .createServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            server.setEnabledProtocols(new String[]{"TLSv1.2"});
            server.setSoTimeout(8000);
            Future<?> denied = worker.submit(() -> {
                try (SSLSocket wrong = (SSLSocket) server.accept()) {
                    wrong.setSoTimeout(5000);
                    wrong.startHandshake();
                    assertEquals("Unexpected application data from client with a wrong pin",
                            -1, wrong.getInputStream().read());
                } catch (SSLException | SocketException expected) {
                    // Depending on the Java TLS provider, the peer sends an alert or resets TCP.
                }
                return null;
            });
            byte[] pinned = MessageDigest.getInstance("SHA-256").digest(keys.getCertificate("host").getEncoded());
            byte[] wrong = pinned.clone();
            wrong[0] ^= 1;
            String prefix = "NEXUS1:" + server.getLocalPort() + ":";
            HorizonSecureStatus.Pairing wrongPairing = HorizonSecureStatus.Pairing.parse(prefix + hex(wrong) + ":" + KEY);
            SSLException rejected = assertThrows(SSLException.class,
                    () -> HorizonSecureStatus.requestStatus("127.0.0.1", wrongPairing));
            assertTrue(rejected.getMessage().contains("Host identity changed"));
            denied.get(8, TimeUnit.SECONDS);

            Future<String> received = worker.submit(() -> {
                try (SSLSocket client = (SSLSocket) server.accept()) {
                    client.setSoTimeout(5000);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                    String command = reader.readLine();
                    client.getOutputStream().write((STATUS + "\n").getBytes(StandardCharsets.US_ASCII));
                    client.getOutputStream().flush();
                    return command;
                }
            });

            HorizonSecureStatus.Pairing correctPairing = HorizonSecureStatus.Pairing.parse(prefix + hex(pinned) + ":" + KEY);
            String response = HorizonSecureStatus.requestStatus("127.0.0.1", correctPairing);
            assertEquals("NEXUS1 " + KEY, received.get(8, TimeUnit.SECONDS));
            assertEquals(STATUS, response);
        } finally {
            worker.shutdownNow();
        }
    }

    private final String fixturePassword = UUID.randomUUID().toString();

    private static File javaTool(String name) {
        String tool = System.getProperty("os.name").startsWith("Windows") ? name + ".exe" : name;
        return new File(new File(System.getProperty("java.home"), "bin"), tool);
    }

    private KeyStore generateFixture(File directory) throws Exception {
        File store = new File(directory, "host-fixture.p12");
        File trace = new File(directory, "keytool-output.txt");
        Process generation = new ProcessBuilder(javaTool("keytool").getAbsolutePath(), "-genkeypair", "-alias", "host",
                "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=Synthetic Nexus Host",
                "-validity", "2", "-storetype", "PKCS12", "-keystore", store.getAbsolutePath(),
                "-storepass", fixturePassword, "-keypass", fixturePassword, "-noprompt")
                .redirectErrorStream(true).redirectOutput(trace).start();
        try {
            assertTrue("Timed out creating the disposable TLS certificate", generation.waitFor(20, TimeUnit.SECONDS));
            assertEquals("Could not create the disposable TLS certificate", 0, generation.exitValue());
        } finally {
            if (generation.isAlive()) generation.destroyForcibly();
        }
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (java.io.InputStream input = Files.newInputStream(store.toPath())) {
            keys.load(input, fixturePassword.toCharArray());
        }
        return keys;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
