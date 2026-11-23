package com.limelight.pocket;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.limelight.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates from inside the app: the newest build on the project's releases page, what changed
 * since this one (its commits' first lines), and one press to download and install it. Checked
 * by itself now and then, so a new build is never missed.
 */
public final class Updater {

    private static final String REPO = "alameergym-netizen/artemis-pocket";
    private static final String KEY_LAST_CHECK = "pocket_update_checked";
    private static final String KEY_AVAILABLE = "pocket_update_available";
    private static final long EVERY_MS = 6 * 60 * 60 * 1000L;
    private static final Pattern APK = Pattern.compile("PocketNexus-([0-9a-f]{7})\\.apk");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean downloading;

    public static final class Release {
        public final String sha;
        public final String url;
        public final long size;
        public final List<String> changes;

        Release(String sha, String url, long size, List<String> changes) {
            this.sha = sha;
            this.url = url;
            this.size = size;
            this.changes = changes;
        }
    }

    public interface Checked {
        /** The newer release, or null when this is the newest (or it couldn't tell: error set). */
        void onChecked(Release newer, String error);
    }

    public interface Progress {
        void onProgress(int percent);

        void onDone(File apk, String error);
    }

    private Updater() {
    }

    public static String current() {
        return BuildConfig.GIT_SHA;
    }

    /** A newer build found by the last check (its commit), or null. */
    public static String knownNewer(Context c) {
        String s = PocketPrefs.prefs(c).getString(KEY_AVAILABLE, null);
        return s != null && !s.equals(current()) ? s : null;
    }

    /** Now and then (every few hours, quietly): looks for a newer build. */
    public static void checkSometimes(Context context, final Checked done) {
        final Context c = context.getApplicationContext();
        long last = PocketPrefs.prefs(c).getLong(KEY_LAST_CHECK, 0);
        if (System.currentTimeMillis() - last < EVERY_MS || "dev".equals(current())) {
            return;
        }
        check(c, done);
    }

    public static void check(Context context, final Checked done) {
        final Context c = context.getApplicationContext();
        IO.execute(() -> {
            Release r = null;
            String error = null;
            try {
                JSONObject rel = new JSONObject(get("https://api.github.com/repos/" + REPO + "/releases/tags/latest"));
                JSONArray assets = rel.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    Matcher m = APK.matcher(a.optString("name"));
                    if (m.matches()) {
                        String sha = m.group(1);
                        if (!sha.equals(current())) {
                            r = new Release(sha, a.getString("browser_download_url"), a.optLong("size"), changes(sha));
                        }
                        break;
                    }
                }
                PocketPrefs.prefs(c).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                        .putString(KEY_AVAILABLE, r != null ? r.sha : current()).apply();
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final Release got = r;
            final String err = error;
            MAIN.post(() -> done.onChecked(got, err));
        });
    }

    /** What changed from this build to that one: the commits' first lines, newest first. */
    private static List<String> changes(String to) {
        List<String> out = new ArrayList<>();
        if ("dev".equals(current())) {
            return out;
        }
        try {
            JSONObject cmp = new JSONObject(get("https://api.github.com/repos/" + REPO + "/compare/"
                    + current() + "..." + to));
            JSONArray commits = cmp.getJSONArray("commits");
            for (int i = commits.length() - 1; i >= 0 && out.size() < 25; i--) {
                String msg = commits.getJSONObject(i).getJSONObject("commit").optString("message");
                String first = msg.split("\n", 2)[0].trim();
                if (!first.isEmpty()) {
                    out.add(first);
                }
            }
        } catch (Exception ignored) {
            // The update still installs; only the list is missing
        }
        return out;
    }

    /** Downloads it (with progress), into the app's cache. */
    public static void download(Context context, final Release r, final Progress p) {
        if (downloading) {
            return;
        }
        downloading = true;
        final Context c = context.getApplicationContext();
        IO.execute(() -> {
            File out = null;
            String error = null;
            try {
                File dir = new File(c.getCacheDir(), "updates");
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new java.io.IOException("No room for the download");
                }
                File[] old = dir.listFiles();
                if (old != null) {
                    for (File f : old) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
                out = new File(dir, "PocketNexus-" + r.sha + ".apk");
                HttpURLConnection con = open(r.url);
                long total = con.getContentLengthLong() > 0 ? con.getContentLengthLong() : r.size;
                long got = 0;
                int lastPct = -1;
                try (InputStream in = con.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        o.write(buf, 0, n);
                        got += n;
                        int pct = total > 0 ? (int) (got * 100 / total) : 0;
                        if (pct != lastPct) {
                            lastPct = pct;
                            final int pp = pct;
                            MAIN.post(() -> p.onProgress(pp));
                        }
                    }
                } finally {
                    con.disconnect();
                }
                if (total > 0 && got < total) {
                    throw new java.io.IOException("The download stopped early");
                }
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
                out = null;
            }
            downloading = false;
            final File apk = out;
            final String err = error;
            MAIN.post(() -> p.onDone(apk, err));
        });
    }

    /** Hands the downloaded build to Android's installer (asking for the permission first if needed). */
    public static void install(Activity a, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            // Android asks once: "allow Pocket Nexus to install apps"
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
            return;
        }
        Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        a.startActivity(i);
    }

    // ---- The web -----------------------------------------------------------------------------

    /** Follows GitHub's redirects (to its file servers) by hand: Android doesn't across hosts. */
    private static HttpURLConnection open(String url) throws Exception {
        String at = url;
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(at).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(15000);
            con.setReadTimeout(60000);
            con.setRequestProperty("User-Agent", "PocketNexus");
            con.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream, */*");
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) {
                String next = con.getHeaderField("Location");
                con.disconnect();
                if (next == null) {
                    break;
                }
                at = next;
                continue;
            }
            if (code != 200) {
                con.disconnect();
                throw new java.io.IOException("GitHub answered " + code);
            }
            return con;
        }
        throw new java.io.IOException("Too many redirects");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = open(url);
        try (InputStream in = con.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }
}
