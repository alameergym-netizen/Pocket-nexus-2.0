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

    private static final int VERSION = 2;
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
            root.put("app", "Pocket Nexus 2.0");
            root.put("version", VERSION);
            root.put("at", System.currentTimeMillis());
            int count = 0;
            JSONObject files = new JSONObject();
            for (String name : FILES) {
                JSONObject one = new JSONObject();
                for (Map.Entry<String, ?> e : prefs(c, name).getAll().entrySet()) {
                    if (excluded(e.getKey()) || e.getValue() == null) {
                        continue;
                    }
                    Object v = portableValue(e.getKey(), e.getValue());
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
            Map<String, Map<String, Object>> staged = decode(new JSONObject(text));
            Map<String, SharedPreferences> stores = new java.util.LinkedHashMap<>();
            for (String name : FILES) {
                String file = name == null ? "main" : name;
                if (staged.containsKey(file)) stores.put(file, prefs(c, name));
            }
            return restoreStaged(stores, staged);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Validate all stores before writing. On a synchronous commit failure, attempt to
     * restore only the touched keys (including keys in the failing store). This is
     * not a crash-atomic database transaction; import must be done while idle.
     */
    static synchronized int restoreStaged(Map<String, SharedPreferences> stores,
            Map<String, Map<String, Object>> staged) {
        Map<String, Map<String, Object>> originals = new java.util.LinkedHashMap<>();
        java.util.List<String> attempted = new java.util.ArrayList<>();
        int count = 0;
        try {
            for (Map.Entry<String, Map<String, Object>> file : staged.entrySet()) {
                SharedPreferences store = stores.get(file.getKey());
                if (store == null) return -1;
                Map<String, ?> existing = store.getAll();
                Map<String, Object> before = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : file.getValue().entrySet()) {
                    Object old = existing.get(entry.getKey());
                    if (old != null && !sameType(old, entry.getValue())) return -1;
                    before.put(entry.getKey(), old instanceof Set ? new HashSet<>((Set<?>) old) : old);
                }
                originals.put(file.getKey(), before);
            }
            for (Map.Entry<String, Map<String, Object>> file : staged.entrySet()) {
                if (file.getValue().isEmpty()) continue;
                attempted.add(file.getKey());
                if (!commitValues(stores.get(file.getKey()), file.getValue())) {
                    throw new IllegalStateException("Settings commit failed");
                }
                count += file.getValue().size();
            }
            return count;
        } catch (RuntimeException failed) {
            for (int i = attempted.size() - 1; i >= 0; i--) {
                String file = attempted.get(i);
                try {
                    if (!commitValues(stores.get(file), originals.get(file))) {
                        android.util.Log.e("NexusBackup", "Settings rollback commit failed");
                    }
                } catch (RuntimeException rollbackFailed) {
                    android.util.Log.e("NexusBackup", "Settings rollback failed");
                }
            }
            return -1;
        }
    }

    private static boolean commitValues(SharedPreferences prefs, Map<String, Object> values) {
        SharedPreferences.Editor ed = prefs.edit();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            if (v == null) ed.remove(k);
            else if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) ed.putInt(k, (Integer) v);
            else if (v instanceof Long) ed.putLong(k, (Long) v);
            else if (v instanceof Float) ed.putFloat(k, (Float) v);
            else if (v instanceof String) ed.putString(k, (String) v);
            else if (v instanceof Set) {
                @SuppressWarnings("unchecked") Set<String> strings = (Set<String>) v;
                ed.putStringSet(k, new HashSet<>(strings));
            } else throw new IllegalArgumentException("Unsupported settings value");
        }
        return ed.commit();
    }

    /** Device-bound state is not portable across app installs. */
    static boolean excluded(String key) {
        return secret(key) || key.startsWith("pocket_update_")
                || key.startsWith("pocket_companion_seen_")
                || key.startsWith("pocket_adaptive_cap_")
                || key.equals("pocket_last_usb_ip") || key.equals("pocket_last_usb_uuid")
                || key.equals("pocket_music_uri") || key.equals("pocket_last_pc_uuid");
    }

    static Object portableValue(String key, Object value) {
        if ("pocket_home_widgets".equals(key) && value instanceof String) {
            java.util.List<String> widgets = new java.util.ArrayList<>();
            for (String widget : ((String) value).split(",")) {
                String w = widget.trim();
                if (!w.isEmpty() && !w.startsWith("aw:") && !widgets.contains(w)) {
                    widgets.add(w);
                }
            }
            return android.text.TextUtils.join(",", widgets);
        }
        return value;
    }

    private static boolean sameType(Object a, Object b) {
        return a instanceof Set ? b instanceof Set : a.getClass().equals(b.getClass());
    }

    /** Parse and validate the entire backup before any preference store is changed. */
    static Map<String, Map<String, Object>> decode(JSONObject root) throws org.json.JSONException {
        String app = root.optString("app");
        int version = root.optInt("version", -1);
        if (!(version == 1 && "Pocket Nexus".equals(app))
                && !(version == 2 && "Pocket Nexus 2.0".equals(app))) {
            throw new org.json.JSONException("Unsupported backup");
        }
        JSONObject files = root.getJSONObject("files");
        Map<String, Map<String, Object>> staged = new java.util.LinkedHashMap<>();
        int count = 0;
        for (String name : FILES) {
            String file = name == null ? "main" : name;
            if (!files.has(file)) { continue; }
            JSONObject one = files.getJSONObject(file);
            Map<String, Object> values = new java.util.LinkedHashMap<>();
            Iterator<String> keys = one.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (excluded(key)) { continue; }
                if (++count > 50000 || key.isEmpty() || key.length() > 4096) {
                    throw new org.json.JSONException("Invalid preference key or too many entries");
                }
                JSONObject typed = one.getJSONObject(key);
                if (typed.length() != 1) { throw new org.json.JSONException("Ambiguous preference type"); }
                Object v;
                if (typed.has("b") && typed.get("b") instanceof Boolean) { v = typed.getBoolean("b"); }
                else if (typed.has("i") && typed.get("i") instanceof Number) {
                    Number n = (Number) typed.get("i");
                    long l = n.longValue();
                    if (n.doubleValue() != l || l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                        throw new org.json.JSONException("Invalid integer");
                    }
                    v = (int) l;
                } else if (typed.has("l") && (typed.get("l") instanceof Integer || typed.get("l") instanceof Long)) {
                    v = typed.getLong("l");
                } else if (typed.has("f") && typed.get("f") instanceof Number) {
                    float value = (float) typed.getDouble("f");
                    if (Float.isNaN(value) || Float.isInfinite(value)) { throw new org.json.JSONException("Invalid float"); }
                    v = value;
                } else if (typed.has("s") && typed.get("s") instanceof String) {
                    v = typed.getString("s");
                } else if (typed.has("set")) {
                    JSONArray arr = typed.getJSONArray("set");
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        Object element = arr.get(i);
                        if (!(element instanceof String)) { throw new org.json.JSONException("Invalid string set"); }
                        set.add((String) element);
                    }
                    v = set;
                } else { throw new org.json.JSONException("Unsupported preference type"); }
                values.put(key, portableValue(key, v));
            }
            staged.put(file, values);
        }
        if (staged.isEmpty()) { throw new org.json.JSONException("No supported preference stores"); }
        return staged;
    }

    /** A name for the file: "PocketNexus-settings-2026-11-19.json". */
    public static String fileName() {
        return "PocketNexus2-settings-" + android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()) + ".json";
    }
}
