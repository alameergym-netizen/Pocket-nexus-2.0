package com.limelight.pocket;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Every setting in one small file, and back: the look, Home, the dock and widgets, the buttons,
 * the radial menus, each game's own settings, the cloud clients. For a new handheld, a reset,
 * or before trying something. Passwords and sign-ins are not in it (they stay on the handheld).
 */
public final class SettingsBackup {

    private static final int VERSION = 1;
    /** What's kept: the app's settings and the stores that are settings too. */
    private static final String[] FILES = {null, "pocket_game_profiles", "pocket_cloud_clients"};

    private SettingsBackup() {
    }

    private static SharedPreferences prefs(Context c, String name) {
        return name == null ? PocketPrefs.prefs(c) : c.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    /** Never written out: anything that looks like a secret or a sign-in. */
    private static boolean secret(String key) {
        String k = key.toLowerCase(java.util.Locale.ROOT);
        return k.contains("password") || k.contains("token") || k.contains("secret") || k.contains("cookie")
                || k.contains("apikey") || k.contains("api_key") || k.contains("session");
    }

    /** Writes it all to the file; the number of settings written (or -1). */
    public static int write(Context c, Uri to) {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "Pocket Nexus");
            root.put("version", VERSION);
            root.put("at", System.currentTimeMillis());
            int count = 0;
            JSONObject files = new JSONObject();
            for (String name : FILES) {
                JSONObject one = new JSONObject();
                for (Map.Entry<String, ?> e : prefs(c, name).getAll().entrySet()) {
                    if (secret(e.getKey()) || e.getValue() == null) {
                        continue;
                    }
                    Object v = e.getValue();
                    JSONObject typed = new JSONObject();
                    if (v instanceof Boolean) {
                        typed.put("b", v);
                    }
                    else if (v instanceof Integer) {
                        typed.put("i", v);
                    }
                    else if (v instanceof Long) {
                        typed.put("l", v);
                    }
                    else if (v instanceof Float) {
                        typed.put("f", ((Float) v).doubleValue());
                    }
                    else if (v instanceof String) {
                        typed.put("s", v);
                    }
                    else if (v instanceof Set) {
                        JSONArray a = new JSONArray();
                        for (Object o : (Set<?>) v) {
                            a.put(String.valueOf(o));
                        }
                        typed.put("set", a);
                    }
                    else {
                        continue;
                    }
                    one.put(e.getKey(), typed);
                    count++;
                }
                files.put(name == null ? "main" : name, one);
            }
            root.put("files", files);
            try (OutputStream out = c.getContentResolver().openOutputStream(to, "wt")) {
                if (out == null) {
                    return -1;
                }
                out.write(root.toString(1).getBytes("UTF-8"));
            }
            return count;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Reads a backup over the current settings; the number restored (or -1 if it isn't one). */
    public static int read(Context c, Uri from) {
        try {
            String text;
            try (InputStream in = c.getContentResolver().openInputStream(from)) {
                if (in == null) {
                    return -1;
                }
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int r;
                while ((r = in.read(b)) > 0) {
                    buf.write(b, 0, r);
                    if (buf.size() > 8 * 1024 * 1024) {
                        return -1;
                    }
                }
                text = buf.toString("UTF-8");
            }
            JSONObject root = new JSONObject(text);
            if (!"Pocket Nexus".equals(root.optString("app")) || root.optJSONObject("files") == null) {
                return -1;
            }
            JSONObject files = root.getJSONObject("files");
            int count = 0;
            for (String name : FILES) {
                JSONObject one = files.optJSONObject(name == null ? "main" : name);
                if (one == null) {
                    continue;
                }
                SharedPreferences.Editor ed = prefs(c, name).edit();
                Iterator<String> keys = one.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (secret(k)) {
                        continue;
                    }
                    JSONObject typed = one.optJSONObject(k);
                    if (typed == null) {
                        continue;
                    }
                    if (typed.has("b")) {
                        ed.putBoolean(k, typed.getBoolean("b"));
                    }
                    else if (typed.has("i")) {
                        ed.putInt(k, typed.getInt("i"));
                    }
                    else if (typed.has("l")) {
                        ed.putLong(k, typed.getLong("l"));
                    }
                    else if (typed.has("f")) {
                        ed.putFloat(k, (float) typed.getDouble("f"));
                    }
                    else if (typed.has("s")) {
                        ed.putString(k, typed.getString("s"));
                    }
                    else if (typed.has("set")) {
                        JSONArray a = typed.getJSONArray("set");
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < a.length(); i++) {
                            set.add(a.getString(i));
                        }
                        ed.putStringSet(k, set);
                    }
                    else {
                        continue;
                    }
                    count++;
                }
                ed.commit();
            }
            return count;
        } catch (Exception e) {
            return -1;
        }
    }

    /** A name for the file: "PocketNexus-settings-2026-11-19.json". */
    public static String fileName() {
        return "PocketNexus-settings-" + android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()) + ".json";
    }
}
