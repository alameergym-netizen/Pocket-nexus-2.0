package com.limelight.pocket;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.limelight.R;
import com.limelight.pocket.pclib.HorizonSecureLibrary;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** The opt-in encrypted status endpoint uses separate credentials from legacy PC control. */
public final class HorizonSecureStatus {
    private static final int TIMEOUT_MS = 6000;
    private static final int LIBRARY_TIMEOUT_MS = 20000;
    private static final String VERSION = "NEXUS1";
    private static final Pattern PAIRING = Pattern.compile(
            "(NEXUS[1-4]):([1-9][0-9]{3,4}):([0-9a-f]{64}):([0-9a-f]{64})");
    private static final Pattern STATUS = Pattern.compile(
            "STATUS cpu=(-?[0-9]{1,3}) ram=(-?[0-9]{1,3}) up=([0-9]{1,15}) bat=(-?[0-9]{1,3}) ac=([01])");

    private HorizonSecureStatus() { }

    static final class Pairing {
        final int port;
        final byte[] fingerprint;
        final String key;
        final boolean library;
        final boolean screen;

        private Pairing(int port, byte[] fingerprint, String key, boolean library, boolean screen) {
            this.port = port;
            this.fingerprint = fingerprint;
            this.key = key;
            this.library = library;
            this.screen = screen;
        }

        static Pairing parse(String code) {
            if (code == null || code.length() > 160) return null;
            Matcher match = PAIRING.matcher(code.trim());
            if (!match.matches()) return null;
            int port = Integer.parseInt(match.group(2));
            if (port < 1024 || port > 65535) return null;
            String encoded = match.group(3);
            byte[] fingerprint = new byte[32];
            for (int i = 0; i < fingerprint.length; i++) {
                fingerprint[i] = (byte) Integer.parseInt(encoded.substring(i * 2, i * 2 + 2), 16);
            }
            char scope = match.group(1).charAt(5);
            return new Pairing(port, fingerprint, match.group(4), scope == '2' || scope == '4',
                    scope == '3' || scope == '4');
        }
    }

    static final class Status {
        final int cpu, ram, battery;
        final long uptime;
        final boolean pluggedIn;

        private Status(int cpu, int ram, int battery, long uptime, boolean pluggedIn) {
            this.cpu = cpu;
            this.ram = ram;
            this.battery = battery;
            this.uptime = uptime;
            this.pluggedIn = pluggedIn;
        }

        static Status parse(String response) {
            if (response == null) return null;
            Matcher match = STATUS.matcher(response);
            if (!match.matches()) return null;
            int cpu = Integer.parseInt(match.group(1));
            int ram = Integer.parseInt(match.group(2));
            int battery = Integer.parseInt(match.group(4));
            if (!percent(cpu) || !percent(ram) || !percent(battery)) return null;
            return new Status(cpu, ram, battery, Long.parseLong(match.group(3)), match.group(5).equals("1"));
        }

        private static boolean percent(int value) {
            return value >= -1 && value <= 100;
        }
    }

    static final class PinnedCertificate implements X509TrustManager {
        private final byte[] fingerprint;

        PinnedCertificate(byte[] fingerprint) {
            this.fingerprint = fingerprint.clone();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Client certificates are unsupported");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("Missing host certificate");
            try {
                X509Certificate certificate = chain[0];
                certificate.checkValidity();
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
                if (!MessageDigest.isEqual(fingerprint, digest)) throw new CertificateException("Host identity changed");
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new CertificateException(e);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private interface ReplyReader<T> {
        T read(InputStream input) throws IOException;
    }

    static String requestStatus(String host, Pairing pairing) throws Exception {
        return request(host, pairing, VERSION, TIMEOUT_MS, input -> PcCompanion.readLine(input, 128));
    }

    private static <T> T request(String host, Pairing pairing, String command, int timeoutMs,
                                 ReplyReader<T> reply) throws Exception {
        SSLContext tls = SSLContext.getInstance("TLSv1.2");
        tls.init(null, new TrustManager[] {new PinnedCertificate(pairing.fingerprint)}, null);
        try (Socket connection = new Socket()) {
            Timer deadline = new Timer("NexusSecureStatusDeadline", true);
            deadline.schedule(new TimerTask() {
                @Override public void run() {
                    try { connection.close(); } catch (IOException ignored) { }
                }
            }, timeoutMs);
            try {
                connection.connect(new InetSocketAddress(host, pairing.port), TIMEOUT_MS);
                try (SSLSocket socket = (SSLSocket) tls.getSocketFactory().createSocket(
                        connection, host, pairing.port, true)) {
                    socket.setEnabledProtocols(new String[] {"TLSv1.2"});
                    socket.setSoTimeout(timeoutMs);
                    socket.startHandshake();
                    java.io.OutputStream output = socket.getOutputStream();
                    output.write(PcCompanion.encodeCommand(command + " " + pairing.key));
                    output.flush();
                    return reply.read(socket.getInputStream());
                }
            } finally {
                deadline.cancel();
            }
        }
    }

    public static void show(Activity activity, String host, String pcUuid) {
        if (pcUuid == null || pcUuid.isEmpty()) return;
        // A separate private store is excluded by Android backup and the in-app settings export.
        SharedPreferences pairings = activity.getSharedPreferences("horizon_secure_status", Context.MODE_PRIVATE);
        String code = pairings.getString(pcUuid, null);
        Pairing pairing = Pairing.parse(code);
        if (pairing == null) {
            showPairing(activity, host, pairings, pcUuid);
            return;
        }

        if (pairing.library || pairing.screen) {
            List<String> labels = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            labels.add(activity.getString(R.string.horizon_secure_check));
            actions.add(() -> check(activity, host, code, null));
            if (pairing.library) {
                labels.add(activity.getString(R.string.horizon_secure_games));
                actions.add(() -> showLibrary(activity, host, pairing));
            }
            if (pairing.screen) {
                labels.add(activity.getString(R.string.horizon_secure_screen));
                actions.add(() -> showScreenMenu(activity, host, pairing));
            }
            labels.add(activity.getString(R.string.horizon_secure_replace));
            actions.add(() -> showPairing(activity, host, pairings, pcUuid));
            labels.add(activity.getString(R.string.horizon_secure_forget));
            actions.add(() -> pairings.edit().remove(pcUuid).apply());
            new AlertDialog.Builder(activity).setTitle(R.string.horizon_secure_status)
                    .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> actions.get(which).run())
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.horizon_secure_status)
                .setMessage(R.string.horizon_secure_saved)
                .setPositiveButton(R.string.horizon_secure_check,
                        (dialog, which) -> check(activity, host, code, null))
                .setNeutralButton(R.string.horizon_secure_replace,
                        (dialog, which) -> showPairing(activity, host, pairings, pcUuid))
                .setNegativeButton(R.string.horizon_secure_forget,
                        (dialog, which) -> pairings.edit().remove(pcUuid).apply())
                .show();
    }

    private static void showPairing(Activity activity, String host, SharedPreferences pairings, String pcUuid) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        input.setHint(R.string.horizon_secure_code);
        FrameLayout field = new FrameLayout(activity);
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        field.setPadding(padding, 0, padding, 0);
        field.addView(input, new FrameLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.horizon_secure_status)
                .setMessage(R.string.horizon_secure_pair_body)
                .setView(field)
                .setPositiveButton(R.string.horizon_secure_pair, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String code = input.getText().toString().trim();
            if (Pairing.parse(code) == null) {
                input.setError(activity.getString(R.string.horizon_secure_bad_code));
                return;
            }
            input.setText("");
            dialog.dismiss();
            check(activity, host, code, () -> pairings.edit().putString(pcUuid, code).apply());
        });
    }

    private static void check(Activity activity, String host, String code, Runnable onVerified) {
        if (host == null || host.isEmpty() || UsbBridge.HOST_IP.equals(host)) {
            showResult(activity, activity.getString(R.string.horizon_secure_no_address));
            return;
        }
        Pairing pairing = Pairing.parse(code);
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(R.string.horizon_secure_status)
                .setMessage(R.string.horizon_pc_checking)
                .setCancelable(false)
                .show();
        new Thread(() -> {
            String reply;
            try { reply = requestStatus(host, pairing); }
            catch (Exception e) { reply = null; }
            Status status = Status.parse(reply);
            boolean revoked = "ERR auth".equals(reply);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                progress.dismiss();
                if (status != null) {
                    if (onVerified != null) onVerified.run();
                    String result = activity.getString(R.string.horizon_secure_result,
                            percent(activity, status.cpu), percent(activity, status.ram),
                            status.uptime / 60, percent(activity, status.battery),
                            activity.getString(status.pluggedIn ? R.string.horizon_secure_ac : R.string.horizon_secure_battery));
                    showResult(activity, result);
                } else {
                    showResult(activity, activity.getString(revoked
                            ? R.string.horizon_secure_rejected : R.string.horizon_secure_connect_failed));
                }
            });
        }, "NexusSecureStatus").start();
    }

    private enum ScreenCommand {
        LOCK("NEXUSL", R.string.horizon_secure_lock),
        DISPLAY_OFF("NEXUSD", R.string.horizon_secure_display_off);

        final String wire;
        final int label;

        ScreenCommand(String wire, int label) {
            this.wire = wire;
            this.label = label;
        }
    }

    private static void showScreenMenu(Activity activity, String host, Pairing pairing) {
        if (host == null || host.isEmpty() || UsbBridge.HOST_IP.equals(host)) {
            showResult(activity, activity.getString(R.string.horizon_secure_no_address));
            return;
        }
        ScreenCommand[] commands = ScreenCommand.values();
        String[] labels = new String[commands.length];
        for (int i = 0; i < commands.length; i++) labels[i] = activity.getString(commands[i].label);
        new AlertDialog.Builder(activity).setTitle(R.string.horizon_secure_screen)
                .setItems(labels, (menu, which) -> new AlertDialog.Builder(activity)
                        .setTitle(commands[which].label)
                        .setMessage(R.string.horizon_secure_screen_confirm)
                        .setPositiveButton(R.string.horizon_secure_screen_send,
                                (dialog, button) -> sendScreenCommand(activity, host, pairing, commands[which]))
                        .setNegativeButton(android.R.string.cancel, null).show())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void sendScreenCommand(Activity activity, String host, Pairing pairing, ScreenCommand command) {
        new Thread(() -> {
            String reply;
            try { reply = request(host, pairing, command.wire, TIMEOUT_MS, input -> PcCompanion.readLine(input, 128)); }
            catch (Exception e) { reply = null; }
            int message = "OK".equals(reply) ? R.string.horizon_secure_screen_accepted
                    : "ERR auth".equals(reply) ? R.string.horizon_secure_screen_denied
                    : "ERR busy".equals(reply) ? R.string.horizon_secure_screen_busy
                    : R.string.horizon_secure_screen_failed;
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    showResult(activity, activity.getString(message));
                }
            });
        }, "NexusSecureScreen").start();
    }

    private static void showLibrary(Activity activity, String host, Pairing pairing) {
        if (host == null || host.isEmpty() || UsbBridge.HOST_IP.equals(host)) {
            showResult(activity, activity.getString(R.string.horizon_secure_no_address));
            return;
        }
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(R.string.horizon_secure_games)
                .setMessage(R.string.horizon_pc_checking)
                .setCancelable(false)
                .show();
        new Thread(() -> {
            HorizonSecureLibrary.Result reply;
            try {
                reply = request(host, pairing, "NEXUSG", LIBRARY_TIMEOUT_MS,
                        input -> HorizonSecureLibrary.read(() -> PcCompanion.readLine(input, 2048)));
            } catch (Exception e) {
                reply = null;
            }
            HorizonSecureLibrary.Result result = reply;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                progress.dismiss();
                if (result == null || result.error != null) {
                    int message = result != null && "ERR auth".equals(result.error)
                            ? R.string.horizon_secure_rejected : R.string.horizon_secure_games_failed;
                    showResult(activity, activity.getString(message));
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                        .setTitle(R.string.horizon_secure_games)
                        .setPositiveButton(android.R.string.ok, null);
                if (result.entries.isEmpty()) {
                    builder.setMessage(R.string.horizon_secure_games_empty);
                } else {
                    String[] display = new String[result.entries.size()];
                    for (int i = 0; i < display.length; i++) {
                        HorizonSecureLibrary.Entry entry = result.entries.get(i);
                        String state = activity.getString(entry.installed ? R.string.horizon_secure_game_installed
                                : R.string.horizon_secure_game_other_state);
                        display[i] = entry.title + " · " + state;
                        if (!entry.source.isEmpty()) display[i] += " · " + entry.source;
                    }
                    builder.setItems(display, null);
                }
                builder.show();
            });
        }, "NexusSecureGameList").start();
    }

    private static String percent(Activity activity, int value) {
        return value < 0 ? activity.getString(R.string.horizon_secure_unknown)
                : activity.getString(R.string.horizon_secure_percent, value);
    }

    private static void showResult(Activity activity, String result) {
        new AlertDialog.Builder(activity).setTitle(R.string.horizon_secure_status).setMessage(result)
                .setPositiveButton(android.R.string.ok, null).show();
    }
}
