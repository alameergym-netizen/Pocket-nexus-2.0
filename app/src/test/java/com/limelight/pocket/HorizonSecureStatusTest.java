package com.limelight.pocket;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;

import org.junit.Test;

public class HorizonSecureStatusTest {
    private static final String FINGERPRINT = "1133557799bbee001133557799bbee001133557799bbee001133557799bbee00";
    private static final String KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test public void codeBindsPortCertificateAndDeviceKey() {
        HorizonSecureStatus.Pairing pairing = HorizonSecureStatus.Pairing.parse(
                " NEXUS1:47111:" + FINGERPRINT + ":" + KEY + "\n");
        assertNotNull(pairing);
        assertEquals(47111, pairing.port);
        assertEquals(KEY, pairing.key);
        assertEquals(32, pairing.fingerprint.length);
        assertEquals(0x11, pairing.fingerprint[0]);
    }

    @Test public void rejectsInvalidPortMissingPinAndMoreThanOneCode() {
        String suffix = ":" + FINGERPRINT + ":" + KEY;
        assertNull(HorizonSecureStatus.Pairing.parse("NEXUS1:1023" + suffix));
        assertNull(HorizonSecureStatus.Pairing.parse("NEXUS1:65536" + suffix));
        assertNull(HorizonSecureStatus.Pairing.parse("NEXUS1:47111::" + KEY));
        assertNull(HorizonSecureStatus.Pairing.parse("NEXUS1:47111" + suffix + "\nNEXUS1:47111" + suffix));
        assertNull(HorizonSecureStatus.Pairing.parse("APC1:47111" + suffix));
    }

    @Test public void acceptsOnlyBoundedStatusWithAllFields() {
        HorizonSecureStatus.Status status = HorizonSecureStatus.Status.parse(
                "STATUS cpu=10 ram=42 up=123456 bat=-1 ac=1");
        assertNotNull(status);
        assertEquals(10, status.cpu);
        assertEquals(42, status.ram);
        assertEquals(123456L, status.uptime);
        assertEquals(-1, status.battery);
        assertTrue(status.pluggedIn);
        assertNull(HorizonSecureStatus.Status.parse("STATUS cpu=101 ram=42 up=123456 bat=-1 ac=1"));
        assertNull(HorizonSecureStatus.Status.parse("STATUS cpu=10 ram=42 up=-1 bat=-1 ac=1"));
        assertNull(HorizonSecureStatus.Status.parse("STATUS cpu=10 ram=42 up=123456 bat=-1 ac=2"));
        assertNull(HorizonSecureStatus.Status.parse("STATUS cpu=10 ram=42 up=123456 bat=-1"));
    }

    @Test(expected = IOException.class) public void rejectsAnOversizedSecureFrame() throws IOException {
        PcCompanion.readLine(new ByteArrayInputStream("long\n".getBytes(StandardCharsets.UTF_8)), 3);
    }

    @Test(expected = CertificateException.class) public void rejectsAnEmptyCertificateChain() throws Exception {
        new HorizonSecureStatus.PinnedCertificate(new byte[32]).checkServerTrusted(null, "RSA");
    }
}
