package com.limelight.pocket.cloud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.limelight.pocket.PocketPrefs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Better xCloud (by redphx, MIT licensed) inside the built-in Xbox Cloud Gaming player: its
 * stream settings (resolution, codec, bit rate, sharpening, stats...), its own menu, and all the
 * rest, exactly as its author ships it. It isn't bundled: it's fetched from its official GitHub
 * releases (where its author asks that it's only ever taken from), kept, and refreshed now and
 * then, then run at the very start of every xbox.com page, as a userscript manager would.
 */
public final class BetterXcloud {

    /** Its official download (the same address its own userscript header names). */
    private static final String DOWNLOAD =
            "https://github.com/redphx/better-xcloud/releases/latest/download/better-xcloud.user.js";
    private static final String ORIGIN = "https://www.xbox.com";
    private static final String KEY_ON = "pocket_better_xcloud";
    private static final String KEY_FETCHED = "pocket_better_xcloud_at";
    private static final long REFRESH_MS = 3L * 24 * 60 * 60 * 1000;
    private static final Pattern VERSION = Pattern.compile("@version\\s+(\\S+)");

    /**
     * Run first: Pocket Nexus keeps it up to date itself (its own "a new version" note would only
     * point at a userscript manager that isn't there).
     */
    private static final String PRELUDE =
            "window.BX_FLAGS = Object.assign({CheckForUpdate: false}, window.BX_FLAGS || {});";

    public interface Done {
        void onDone(boolean ok);
    }

    private BetterXcloud() {
    }

    public static boolean enabled(Context c) {
        return PocketPrefs.prefs(c).getBoolean(KEY_ON, true);
    }

    public static void setEnabled(Context c, boolean on) {
        PocketPrefs.prefs(c).edit().putBoolean(KEY_ON, on).apply();
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "better-xcloud.user.js");
    }

    /** The version kept (null: not fetched yet). */
    public static String version(Context c) {
        String js = cached(c);
        if (js == null) {
            return null;
        }
        Matcher m = VERSION.matcher(js.substring(0, Math.min(js.length(), 2000)));
        return m.find() ? m.group(1) : "?";
    }

    /** The script as kept on the handheld, or null. */
    static String cached(Context c) {
        File f = file(c);
        if (!f.isFile() || f.length() < 1024) {
            return null;
        }
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Has the page run it from the start of every xbox.com document (before xCloud's own code,
     * which it changes). False when this WebView is too old for that: then the caller runs it as
     * each page starts ({@link #lateScripts}).
     */
    static boolean install(WebView web, String js) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return false;
        }
        try {
            // In this order: the flags first, then the script (its "use strict" stays its first line)
            WebViewCompat.addDocumentStartJavaScript(web, PRELUDE, Collections.singleton(ORIGIN));
            WebViewCompat.addDocumentStartJavaScript(web, js, Collections.singleton(ORIGIN));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** For an older WebView: what to run as an xbox.com page starts, in order. */
    static String[] lateScripts(String js) {
        return new String[]{PRELUDE, js};
    }

    static boolean isItsPage(String url) {
        return url != null && url.startsWith(ORIGIN + "/");
    }

    /** A newer one, now and then (quietly, for the next start). */
    static void refreshIfOld(Context context) {
        final Context c = context.getApplicationContext();
        long at = PocketPrefs.prefs(c).getLong(KEY_FETCHED, 0);
        if (System.currentTimeMillis() - at < REFRESH_MS) {
            return;
        }
        fetch(c, null);
    }

    /** Fetches the newest from its official releases; done on the main thread (null: no reply). */
    public static void fetch(Context context, final Done done) {
        final Context c = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean ok = false;
            try {
                byte[] body = download(DOWNLOAD);
                String text = new String(body, StandardCharsets.UTF_8);
                // Only the real thing is kept: its own header, its own name, a sane size
                if (body.length > 64 * 1024 && text.startsWith("// ==UserScript==")
                        && Pattern.compile("@name\\s+Better xCloud").matcher(text.substring(0, Math.min(text.length(), 800))).find()) {
                    File tmp = new File(c.getFilesDir(), "better-xcloud.user.js.part");
                    try (FileOutputStream out = new FileOutputStream(tmp)) {
                        out.write(body);
                    }
                    ok = tmp.renameTo(file(c));
                    if (ok) {
                        PocketPrefs.prefs(c).edit().putLong(KEY_FETCHED, System.currentTimeMillis()).apply();
                    }
                }
            } catch (Exception ignored) {
                // Kept as it was; tried again next time
            }
            final boolean got = ok;
            if (done != null) {
                main.post(() -> done.onDone(got));
            }
        }, "pocket-better-xcloud").start();
    }

    /** GitHub sends its downloads on to its file servers: followed by hand (across hosts). */
    private static byte[] download(String url) throws Exception {
        String at = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(at).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(12000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", "PocketNexus");
            try {
                int code = con.getResponseCode();
                if (code >= 300 && code < 400) {
                    String next = con.getHeaderField("Location");
                    if (next == null || !next.startsWith("https://")) {
                        break;
                    }
                    at = next;
                    continue;
                }
                if (code != 200) {
                    throw new java.io.IOException("HTTP " + code);
                }
                try (InputStream in = con.getInputStream()) {
                    return readAll(in);
                }
            } finally {
                con.disconnect();
            }
        }
        throw new java.io.IOException("Too many redirects");
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
