package com.limelight.pocket.updates;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.ui.StreamMenuView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Local catalog imported from Obtainium, with explicit read-only public release checks. */
public final class ObtainiumCatalog {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_APPS = 2000;
    private static final String PREFS = "horizon_obtainium_catalog";
    private static final String KEY = "sources";
    private static final Pattern PACKAGE_ID = Pattern.compile(
            "[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");
    private static final AtomicBoolean CHECK_RUNNING = new AtomicBoolean();
    private ObtainiumCatalog() {}

    static JSONArray parse(String input) throws JSONException {
        JSONObject root = new JSONObject(input);
        int schema = root.optInt("schemaVersion", 1);
        if (schema < 1 || schema > 2) { throw new JSONException("Unsupported export schema"); }
        JSONArray apps = root.getJSONArray("apps");
        if (apps.length() > MAX_APPS) { throw new JSONException("Too many apps"); }
        JSONArray result = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app == null) { continue; }
            String id = app.optString("id", "").trim();
            String url = app.optString("url", "").trim();
            if (id.isEmpty() || id.length() > 512 || !safeUrl(url) || !seen.add(id)) { continue; }
            JSONObject settings = app.optJSONObject("additionalSettings");
            if (settings == null) {
                try { settings = new JSONObject(app.optString("additionalSettings", "{}")); }
                catch (JSONException ignored) { settings = new JSONObject(); }
            }
            // Keep only non-executable catalog metadata. Never import headers, tokens, raw settings,
            // arbitrary regular expressions, download URLs or installer permission flags.
            JSONObject row = new JSONObject();
            row.put("id", id);
            row.put("name", limit(app.optString("name", id), 256));
            row.put("host", host(url));
            String repository = ObtainiumGithubRelease.repositoryFrom(url);
            if (repository != null) row.put("githubRepository", repository);
            row.put("installedVersion", version(app, "installedVersion"));
            row.put("latestVersion", version(app, "latestVersion"));
            row.put("disabled", app.optBoolean("disabled"));
            row.put("pinned", app.optBoolean("pinned"));
            row.put("trackOnly", settings.optBoolean("trackOnly"));
            row.put("prereleases", settings.optBoolean("includePrereleases"));
            result.put(row);
        }
        if (apps.length() > 0 && result.length() == 0) { throw new JSONException("No supported sources"); }
        return result;
    }

    private static boolean safeUrl(String url) {
        try {
            java.net.URI u = new java.net.URI(url);
            return url.length() <= 4096 && "https".equalsIgnoreCase(u.getScheme())
                    && u.getHost() != null && u.getUserInfo() == null;
        } catch (Exception e) { return false; }
    }

    private static String host(String url) {
        try { return new java.net.URI(url).getHost(); }
        catch (java.net.URISyntaxException e) { return ""; }
    }

    private static String limit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String version(JSONObject app, String key) {
        return app.isNull(key) ? "" : limit(app.optString(key, ""), 128);
    }

    public static synchronized int importFile(Context c, Uri uri) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) { return -1; }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (bytes.size() + n > MAX_BYTES) { throw new IOException("Export too large"); }
                bytes.write(buf, 0, n);
            }
            JSONArray parsed = parse(bytes.toString("UTF-8"));
            return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY, parsed.toString()).commit() ? parsed.length() : -1;
        } catch (Exception e) { return -1; }
    }

    private static JSONArray load(Context c) {
        try { return new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")); }
        catch (JSONException | ClassCastException e) { return new JSONArray(); }
    }

    public static int count(Context c) { return load(c).length(); }

    public static void show(ConsoleActivity a) {
        JSONArray data = load(a);
        List<StreamMenuView.Item> items = new ArrayList<>();
        items.add(StreamMenuView.Item.note(a.getString(R.string.horizon_obtainium_snapshot)));
        if (data.length() == 0) {
            items.add(StreamMenuView.Item.note(a.getString(R.string.horizon_obtainium_empty)));
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.optJSONObject(i);
            if (row == null) { continue; }
            String installed = importedVersion(a, row, "installedVersion");
            String latest = importedVersion(a, row, "latestVersion");
            items.add(StreamMenuView.Item.submenu(row.optString("name"), () -> showSource(a, row))
                    .withValue(installed + " → " + latest));
        }
        a.showConsolePanel(a.getString(R.string.horizon_obtainium_catalog),
                a.getString(R.string.horizon_obtainium_count, data.length()), null, items);
    }

    private static String importedVersion(Context context, JSONObject source, String field) {
        String version = source.optString(field);
        return version.isEmpty() ? context.getString(R.string.horizon_obtainium_unknown) : version;
    }

    static PackageInfo installedInfo(Context context, String packageId) {
        if (packageId == null || packageId.length() > 255 || !PACKAGE_ID.matcher(packageId).matches()) return null;
        try {
            return context.getPackageManager().getPackageInfo(packageId, 0);
        } catch (PackageManager.NameNotFoundException | SecurityException e) {
            return null;
        }
    }

    private static void showSource(ConsoleActivity activity, JSONObject source) {
        List<StreamMenuView.Item> info = new ArrayList<>();
        String packageId = source.optString("id");
        String title = source.optString("name");
        info.add(StreamMenuView.Item.info(activity.getString(R.string.horizon_obtainium_id), packageId));
        info.add(StreamMenuView.Item.info(activity.getString(R.string.horizon_obtainium_host), source.optString("host")));
        PackageInfo installed = installedInfo(activity, packageId);
        String deviceVersion = activity.getString(R.string.horizon_obtainium_unavailable);
        if (installed != null) {
            long code = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
            String version = installed.versionName == null || installed.versionName.isEmpty()
                    ? activity.getString(R.string.horizon_obtainium_unknown) : installed.versionName;
            deviceVersion = activity.getString(R.string.horizon_obtainium_local_version, version, code);
        }
        info.add(StreamMenuView.Item.info(activity.getString(R.string.horizon_obtainium_current), deviceVersion));
        info.add(StreamMenuView.Item.info(activity.getString(R.string.horizon_obtainium_imported_installed),
                importedVersion(activity, source, "installedVersion")));
        info.add(StreamMenuView.Item.info(activity.getString(R.string.horizon_obtainium_imported_latest),
                importedVersion(activity, source, "latestVersion")));
        if (source.optBoolean("trackOnly")) info.add(StreamMenuView.Item.note(activity.getString(R.string.horizon_obtainium_track_only)));
        if (source.optBoolean("prereleases")) info.add(StreamMenuView.Item.note(activity.getString(R.string.horizon_obtainium_prerelease)));
        if (source.optBoolean("disabled")) info.add(StreamMenuView.Item.note(activity.getString(R.string.horizon_obtainium_disabled)));
        String repository = source.optString("githubRepository");
        if (!source.optBoolean("disabled") && ObtainiumGithubRelease.isRepository(repository)) {
            info.add(StreamMenuView.Item.submenu(activity.getString(R.string.horizon_obtainium_check_release),
                    () -> checkGithubRelease(activity, repository)));
        }
        if (installed != null) {
            info.add(StreamMenuView.Item.action(activity.getString(R.string.horizon_obtainium_android_details), () -> {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageId, null)));
                } catch (RuntimeException e) {
                    Toast.makeText(activity, R.string.horizon_obtainium_unavailable, Toast.LENGTH_SHORT).show();
                }
            }));
        }
        activity.pushConsolePage(title, info);
    }

    private static void checkGithubRelease(ConsoleActivity activity, String repository) {
        if (!CHECK_RUNNING.compareAndSet(false, true)) {
            Toast.makeText(activity, R.string.horizon_obtainium_checking_release, Toast.LENGTH_SHORT).show();
            return;
        }
        List<StreamMenuView.Item> pending = Collections.singletonList(
                StreamMenuView.Item.note(activity.getString(R.string.horizon_obtainium_checking_release)));
        String page = activity.getString(R.string.horizon_obtainium_github_release);
        activity.pushConsolePage(page, pending);
        new Thread(() -> {
            String tag = null;
            boolean failed = false;
            try {
                tag = ObtainiumGithubRelease.latestTag(repository);
            } catch (IOException | JSONException | RuntimeException ignored) {
                failed = true;
            } finally {
                CHECK_RUNNING.set(false);
            }
            String value = failed ? activity.getString(R.string.horizon_obtainium_release_failed)
                    : tag == null ? activity.getString(R.string.horizon_obtainium_release_missing) : tag;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                activity.replaceConsolePage(pending, Collections.singletonList(StreamMenuView.Item.info(repository, value)));
            });
        }, "NexusCatalogRelease").start();
    }
}
