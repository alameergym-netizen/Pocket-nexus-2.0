package com.limelight.pocket;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.limelight.LimeLog;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Talks to "Artemis Pocket PC" (pc-setup/ArtemisPocket-PC.ps1), the small helper that runs on
 * the gaming PC next to Apollo. It gives the handheld what the streaming protocol can't:
 * real PC power control, the PC's status and microphone passthrough.
 *
 * Protocol (TCP 47110, one text line per message):
 *   client  "APC1 <token or ->"          server "OK <authed 0|1> <pc name>"
 *   "PING" -> "PONG"   "TOKEN" -> "TOKEN <hex>" (only through the USB cable)
 *   "PAIR <pin>" -> "TOKEN <hex>" | "ERR pin" | "ERR locked"
 *   "STATUS" -> "STATUS cpu=.. ram=.. up=.. bat=.. ac=.."   "POWER <action>" -> "OK" | "ERR .."
 *   "MIC 48000 1" -> "OK <device>" then raw 16-bit PCM until the connection closes.
 * Over the USB cable (adb reverse to 127.0.0.1) the PC trusts the handheld; over Wi-Fi it needs
 * the token, which the handheld picks up by itself the first time it streams over the cable,
 * or by typing the PIN shown in the PC window.
 */
public final class PcCompanion {
    private PcCompanion() {}

    public static final int PORT = 47110;
    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int READ_TIMEOUT_MS = 5000;
    static final int MAX_RESPONSE_BYTES = 64 * 1024;
    static final int MAX_COMMAND_BYTES = 1023;

    public interface Callback {
        /** reply is null when the PC helper couldn't be reached. Called on the main thread. */
        void onReply(String reply);
    }

    public static final class Session implements Closeable {
        final Socket socket;
        final InputStream in;
        final OutputStream out;
        public final boolean authed;
        public final String pcName;

        private Session(Socket socket, boolean authed, String pcName) throws IOException {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
            this.authed = authed;
            this.pcName = pcName;
        }

        public String call(String line) throws IOException {
            send(line);
            return readLine();
        }

        public void send(String line) throws IOException {
            out.write(encodeCommand(line));
            out.flush();
        }

        public String readLine() throws IOException {
            return PcCompanion.readLine(in);
        }

        public OutputStream rawOut() {
            return out;
        }

        public void setReadTimeout(int ms) throws IOException {
            socket.setSoTimeout(ms);
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * The port to reach the helper at: over the cable always 47110 (the PC maps it to its own
     * port), over Wi-Fi the one set for that PC (Pocket Nexus PC's "Wi-Fi port").
     */
    public static int portFor(Context context, String host, String pcUuid) {
        if (host == null || UsbBridge.HOST_IP.equals(host) || host.startsWith("127.")) {
            return PORT;
        }
        return PocketPrefs.getCompanionPort(context, pcUuid);
    }

    /** Opens a session (blocking; never on the main thread). Null when the helper isn't there. */
    public static Session open(Context context, String host, String pcUuid) {
        if (host == null) {
            return null;
        }
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, portFor(context, host, pcUuid)), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            String token = PocketPrefs.getCompanionToken(context, pcUuid);
            OutputStream out = socket.getOutputStream();
            out.write(encodeCommand("APC1 " + (token != null && !token.isEmpty() ? token : "-")));
            out.flush();
            String hello = readLine(socket.getInputStream());
            if (hello == null || !hello.startsWith("OK ")) {
                socket.close();
                return null;
            }
            String[] parts = hello.split(" ", 3);
            boolean authed = parts.length > 1 && "1".equals(parts[1]);
            String name = parts.length > 2 ? parts[2] : "";
            return new Session(socket, authed, name);
        } catch (IOException | RuntimeException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            return null;
        }
    }

    /**
     * Whether the helper answers, fast (blocking, under ~1.6 s): it only runs while someone is
     * signed in to Windows. Used right before a stream starts, so it must never hold a launch up.
     */
    public static boolean answersQuickly(Context context, String host, String pcUuid) {
        if (host == null) {
            return false;
        }
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, portFor(context, host, pcUuid)), 800);
            socket.setSoTimeout(800);
            String token = PocketPrefs.getCompanionToken(context, pcUuid);
            OutputStream out = socket.getOutputStream();
            out.write(encodeCommand("APC1 " + (token != null && !token.isEmpty() ? token : "-")));
            out.flush();
            String hello = readLine(socket.getInputStream());
            boolean ok = hello != null && hello.startsWith("OK ");
            if (ok) {
                PocketPrefs.markCompanionSeen(context, pcUuid);
            }
            return ok;
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public interface DiagnosticCallback {
        void onResult(Diagnostic diagnostic);
    }

    public static final class Diagnostic {
        public final boolean reachable, paired, pong;
        public final long roundTripMs;
        public final String version, status;
        Diagnostic(boolean reachable, boolean paired, boolean pong, long roundTripMs,
                   String version, String status) {
            this.reachable = reachable;
            this.paired = paired;
            this.pong = pong;
            this.roundTripMs = roundTripMs;
            this.version = version;
            this.status = status;
        }
    }

    /** Read-only legacy protocol test; does not pair, run programs or change host settings. */
    public static void diagnoseAsync(Context context, String host, String uuid, DiagnosticCallback done) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Diagnostic result;
            try (Session session = open(app, host, uuid)) {
                if (session == null) {
                    result = new Diagnostic(false, false, false, -1, "", "");
                } else {
                    boolean pong = false;
                    long elapsed = -1;
                    String version = "", status = "";
                    try {
                        long start = android.os.SystemClock.elapsedRealtime();
                        pong = "PONG".equals(session.call("PING"));
                        if (pong) { elapsed = android.os.SystemClock.elapsedRealtime() - start; }
                        if (pong && session.authed) {
                            String v = session.call("VERSION");
                            if (v != null && v.startsWith("VERSION ")) { version = v.substring(8); }
                            String st = session.call("STATUS");
                            if (st != null && st.startsWith("STATUS ")) { status = st.substring(7); }
                        }
                    } catch (IOException ignored) {
                        // Preserve completed stages, so a later command failure is visible.
                    }
                    result = new Diagnostic(true, session.authed, pong, elapsed, version, status);
                }
            }
            final Diagnostic finalResult = result;
            main.post(() -> done.onResult(finalResult));
        }, "HorizonPcDiagnostic").start();
    }

    /** Runs one command (blocking). Returns the reply line, or null if unreachable. */
    public static String run(Context context, String host, String pcUuid, String command) {
        Session session = open(context, host, pcUuid);
        if (session == null) {
            return null;
        }
        // The helper only runs while someone is signed in to Windows: remember that it answers
        PocketPrefs.markCompanionSeen(context, pcUuid);
        try {
            if (!session.authed && !command.startsWith("PAIR") && !command.equals("PING")) {
                return "ERR auth";
            }
            String reply = session.call(command);
            if (reply != null && reply.startsWith("TOKEN ")) {
                PocketPrefs.setCompanionToken(context, pcUuid, reply.substring(6).trim());
            }
            return reply;
        } catch (IOException e) {
            return null;
        } finally {
            session.close();
        }
    }

    public static void runAsync(final Context context, final String host, final String pcUuid,
                                final String command, final Callback callback) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            final String reply = run(app, host, pcUuid, command);
            if (callback != null) {
                main.post(() -> callback.onReply(reply));
            }
        }, "PocketCompanion").start();
    }

    /**
     * Through the USB cable the PC hands over its token (the cable is the proof of trust), so
     * the same PC can be controlled over Wi-Fi later without typing a PIN.
     */
    public static void syncTokenOverUsb(final Context context, final String pcUuid) {
        if (pcUuid == null) {
            return;
        }
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            String reply = run(app, UsbBridge.HOST_IP, pcUuid, "TOKEN");
            LimeLog.info("Artemis Pocket PC over USB: " + (reply == null ? "not running" : "token synced"));
            // Its Wi-Fi port too (changed on the PC): the handheld finds it there from now on
            String port = reply != null ? run(app, UsbBridge.HOST_IP, pcUuid, "WIFIPORT") : null;
            if (port != null && port.startsWith("WIFIPORT ")) {
                try {
                    PocketPrefs.setCompanionPort(app, pcUuid, Integer.parseInt(port.substring(9).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }, "PocketCompanionSync").start();
    }

    /**
     * Asks which port Pocket Nexus PC listens on over Wi-Fi on this PC (set in its icon next to
     * the clock > Wi-Fi port), keeps it, then checks the helper answers there.
     */
    public static void askWifiPort(final android.app.Activity a, final String host, final String pcUuid, String pcName) {
        if (pcUuid == null) {
            return;
        }
        final android.widget.EditText input = new android.widget.EditText(a);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(PocketPrefs.getCompanionPort(a, pcUuid)));
        input.setSelectAllOnFocus(true);
        input.setTextSize(22);
        input.setGravity(android.view.Gravity.CENTER);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(5)});
        android.widget.FrameLayout box = new android.widget.FrameLayout(a);
        int pad = Math.round(20 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        ConsoleActivity.showImmersive(new android.app.AlertDialog.Builder(a, com.limelight.R.style.PocketDialog)
                .setTitle(a.getString(com.limelight.R.string.pocket_companion_port_title, pcName != null ? pcName : ""))
                .setMessage(com.limelight.R.string.pocket_companion_port_body)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int port;
                    try {
                        port = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        port = -1;
                    }
                    if (port < 1024 || port > 65535) {
                        android.widget.Toast.makeText(a, com.limelight.R.string.pocket_companion_port_bad,
                                android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    savePortAndCheck(a, host, pcUuid, port);
                })
                .setNeutralButton(com.limelight.R.string.pocket_companion_port_default,
                        (d, w) -> savePortAndCheck(a, host, pcUuid, PORT))
                .setNegativeButton(android.R.string.cancel, null)
                .create());
    }

    private static void savePortAndCheck(final android.app.Activity a, String host, String pcUuid, final int port) {
        PocketPrefs.setCompanionPort(a, pcUuid, port);
        if (host == null) {
            android.widget.Toast.makeText(a, a.getString(com.limelight.R.string.pocket_companion_port_saved, port),
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        runAsync(a, host, pcUuid, "PING", reply -> {
            if (a.isFinishing()) {
                return;
            }
            boolean ok = reply != null && reply.startsWith("PONG");
            if (ok) {
                PocketSounds.play(a, PocketSounds.TOGGLE_ON);
            }
            android.widget.Toast.makeText(a, a.getString(ok ? com.limelight.R.string.pocket_companion_port_ok
                    : com.limelight.R.string.pocket_companion_port_missing, port), android.widget.Toast.LENGTH_LONG).show();
        });
    }

    /** Where to find the helper for a stream host: the same address (127.0.0.1 over USB). */
    public static String hostFor(String streamHost) {
        return streamHost;
    }

    /** Enforce a single command within the legacy Windows helper's line limit. */
    static byte[] encodeCommand(String line) throws IOException {
        if (line == null || line.isEmpty() || line.indexOf('\r') >= 0
                || line.indexOf('\n') >= 0 || line.indexOf('\0') >= 0) {
            throw new IOException("Invalid companion command");
        }
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_COMMAND_BYTES) {
            throw new IOException("Companion command too long");
        }
        byte[] framed = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        framed[bytes.length] = '\n';
        return framed;
    }

    /** A long response is one frame, not a series of fabricated messages. */
    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int consumed = 0;
        while (true) {
            int b = in.read();
            if (b < 0) {
                if (consumed == 0) { return null; }
                throw new IOException("Incomplete companion response");
            }
            if (b == '\n') { return line.toString("UTF-8"); }
            if (++consumed > MAX_RESPONSE_BYTES) {
                throw new IOException("Companion response too long");
            }
            if (b != '\r') { line.write(b); }
        }
    }
}
