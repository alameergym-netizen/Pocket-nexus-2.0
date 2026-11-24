package com.limelight.pocket.updates;

import android.content.Context;
import android.net.Uri;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/** Local, read-only catalog imported from Obtainium. Does not fetch, install, or execute URLs. */
public final class ObtainiumCatalog {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_APPS = 2000;
    private static final String PREFS = "horizon_obtainium_catalog";
    private static final String KEY = "sources";
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
        catch (JSONException e) { return new JSONArray(); }
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
            String installed = row.optString("installedVersion");
            String latest = row.optString("latestVersion");
            String detail = row.optString("host");
            if (row.optBoolean("trackOnly")) { detail += " · Track only"; }
            if (row.optBoolean("prereleases")) { detail += " · Pre-release"; }
            if (row.optBoolean("disabled")) { detail += " · Disabled"; }
            if (installed.isEmpty()) { installed = "Unknown"; }
            if (latest.isEmpty()) { latest = "Unknown"; }
            items.add(StreamMenuView.Item.info(row.optString("name"), installed + " → " + latest));
            items.add(StreamMenuView.Item.note(detail));
        }
        a.showConsolePanel(a.getString(R.string.horizon_obtainium_catalog),
                a.getString(R.string.horizon_obtainium_count, data.length()), null, items);
    }
}
