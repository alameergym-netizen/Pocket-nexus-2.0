package com.limelight.pocket.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.limelight.pocket.PocketPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The weather where the player is, from Open-Meteo (free, no account, no key). The place is
 * the city they set, or a first guess from the handheld's time zone ("Asia/Amman": Amman),
 * so it works with nothing set up. Kept for half an hour; Celsius or Fahrenheit.
 */
public final class Weather {

    /** What the sky is doing, simply. */
    public static final int CLEAR = 0;
    public static final int PARTLY = 1;
    public static final int CLOUDY = 2;
    public static final int FOG = 3;
    public static final int DRIZZLE = 4;
    public static final int RAIN = 5;
    public static final int SNOW = 6;
    public static final int STORM = 7;

    public static final class Now {
        public final String city;
        public final int temp;
        public final int high;
        public final int low;
        public final int sky;
        public final boolean day;
        public final int wind;
        public final int[] nextHigh;
        public final int[] nextSky;
        public final long at;

        Now(String city, int temp, int high, int low, int sky, boolean day, int wind, int[] nextHigh, int[] nextSky, long at) {
            this.city = city;
            this.temp = temp;
            this.high = high;
            this.low = low;
            this.sky = sky;
            this.day = day;
            this.wind = wind;
            this.nextHigh = nextHigh;
            this.nextSky = nextSky;
            this.at = at;
        }
    }

    public interface Callback {
        void onWeather(Now now);
    }

    private static final String KEY_CITY = "pocket_weather_city";
    private static final String KEY_LAT = "pocket_weather_lat";
    private static final String KEY_LON = "pocket_weather_lon";
    private static final String KEY_UNITS = "pocket_weather_f";
    private static final String KEY_CACHE = "pocket_weather_cache";
    private static final long FRESH_MS = 30 * 60 * 1000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean busy;

    private Weather() {
    }

    public static boolean fahrenheit(Context c) {
        SharedPreferences p = PocketPrefs.prefs(c);
        if (p.contains(KEY_UNITS)) {
            return p.getBoolean(KEY_UNITS, false);
        }
        String country = Locale.getDefault().getCountry();
        return "US".equals(country) || "LR".equals(country) || "MM".equals(country);
    }

    public static void setFahrenheit(Context c, boolean f) {
        PocketPrefs.prefs(c).edit().putBoolean(KEY_UNITS, f).remove(KEY_CACHE).apply();
    }

    /** The city it's for (set, found, or guessed from the time zone). */
    public static String city(Context c) {
        String set = PocketPrefs.prefs(c).getString(KEY_CITY, null);
        return set != null ? set : guessCity();
    }

    /** "Asia/Amman" → "Amman", "America/New_York" → "New York". */
    static String guessCity() {
        String id = TimeZone.getDefault().getID();
        int slash = id.lastIndexOf('/');
        if (slash < 0 || id.startsWith("Etc")) {
            return null;
        }
        return id.substring(slash + 1).replace('_', ' ');
    }

    /** The last weather known (null if none yet). */
    public static Now cached(Context c) {
        String s = PocketPrefs.prefs(c).getString(KEY_CACHE, null);
        if (s == null) {
            return null;
        }
        try {
            return parse(new JSONObject(s));
        } catch (Exception e) {
            return null;
        }
    }

    /** Fresh weather when what's kept is old (or none), told on the main thread. */
    public static void refresh(Context context, boolean force, final Callback cb) {
        final Context c = context.getApplicationContext();
        Now known = cached(c);
        if (!force && known != null && System.currentTimeMillis() - known.at < FRESH_MS) {
            return;
        }
        if (busy) {
            return;
        }
        busy = true;
        IO.execute(() -> {
            Now now = null;
            try {
                now = fetch(c);
            } catch (Exception ignored) {
            }
            busy = false;
            final Now got = now;
            MAIN.post(() -> {
                if (cb != null && got != null) {
                    cb.onWeather(got);
                }
            });
        });
    }

    /** Sets the city by its name (found with Open-Meteo's place search); told true/false. */
    public static void setCity(Context context, final String name, final Callback done) {
        final Context c = context.getApplicationContext();
        IO.execute(() -> {
            boolean ok = false;
            try {
                ok = locate(c, name);
            } catch (Exception ignored) {
            }
            Now now = null;
            if (ok) {
                try {
                    now = fetch(c);
                } catch (Exception ignored) {
                }
            }
            final Now got = now;
            MAIN.post(() -> done.onWeather(got));
        });
    }

    private static boolean locate(Context c, String name) throws Exception {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name="
                + URLEncoder.encode(name.trim(), "UTF-8");
        JSONObject o = new JSONObject(get(url));
        JSONArray r = o.optJSONArray("results");
        if (r == null || r.length() == 0) {
            return false;
        }
        JSONObject p = r.getJSONObject(0);
        PocketPrefs.prefs(c).edit()
                .putString(KEY_CITY, p.optString("name", name.trim()))
                .putFloat(KEY_LAT, (float) p.getDouble("latitude"))
                .putFloat(KEY_LON, (float) p.getDouble("longitude"))
                .remove(KEY_CACHE)
                .apply();
        return true;
    }

    private static Now fetch(Context c) throws Exception {
        SharedPreferences p = PocketPrefs.prefs(c);
        if (!p.contains(KEY_LAT)) {
            String guess = city(c);
            if (guess == null || !locate(c, guess)) {
                return null;
            }
        }
        float lat = p.getFloat(KEY_LAT, 0);
        float lon = p.getFloat(KEY_LON, 0);
        boolean f = fahrenheit(c);
        String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code,is_day,wind_speed_10m"
                + "&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=4&timezone=auto%s",
                lat, lon, f ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "");
        JSONObject o = new JSONObject(get(url));
        JSONObject cur = o.getJSONObject("current");
        JSONObject daily = o.getJSONObject("daily");
        JSONArray hi = daily.getJSONArray("temperature_2m_max");
        JSONArray lo = daily.getJSONArray("temperature_2m_min");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONObject keep = new JSONObject();
        keep.put("city", city(c));
        keep.put("temp", Math.round(cur.getDouble("temperature_2m")));
        keep.put("high", Math.round(hi.getDouble(0)));
        keep.put("low", Math.round(lo.getDouble(0)));
        keep.put("sky", sky(cur.getInt("weather_code")));
        keep.put("day", cur.optInt("is_day", 1) == 1);
        keep.put("wind", Math.round(cur.optDouble("wind_speed_10m", 0)));
        JSONArray nh = new JSONArray();
        JSONArray ns = new JSONArray();
        for (int i = 1; i < hi.length() && i < 4; i++) {
            nh.put(Math.round(hi.getDouble(i)));
            ns.put(sky(codes.getInt(i)));
        }
        keep.put("nextHigh", nh);
        keep.put("nextSky", ns);
        keep.put("at", System.currentTimeMillis());
        p.edit().putString(KEY_CACHE, keep.toString()).apply();
        return parse(keep);
    }

    private static Now parse(JSONObject o) throws Exception {
        JSONArray nh = o.optJSONArray("nextHigh");
        JSONArray ns = o.optJSONArray("nextSky");
        int n = nh != null ? nh.length() : 0;
        int[] h = new int[n];
        int[] s = new int[n];
        for (int i = 0; i < n; i++) {
            h[i] = nh.getInt(i);
            s[i] = ns.getInt(i);
        }
        return new Now(o.optString("city", ""), o.getInt("temp"), o.getInt("high"), o.getInt("low"),
                o.getInt("sky"), o.optBoolean("day", true), o.optInt("wind"), h, s, o.getLong("at"));
    }

    /** WMO weather codes, simply. */
    static int sky(int code) {
        if (code == 0) {
            return CLEAR;
        }
        if (code <= 2) {
            return PARTLY;
        }
        if (code == 3) {
            return CLOUDY;
        }
        if (code == 45 || code == 48) {
            return FOG;
        }
        if (code >= 51 && code <= 57) {
            return DRIZZLE;
        }
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) {
            return RAIN;
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return SNOW;
        }
        if (code >= 95) {
            return STORM;
        }
        return CLOUDY;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestProperty("User-Agent", "PocketNexus");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }
}
