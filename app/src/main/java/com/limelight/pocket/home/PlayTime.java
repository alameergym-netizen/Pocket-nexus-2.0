package com.limelight.pocket.home;

import android.content.Context;
import android.content.SharedPreferences;

import com.limelight.pocket.PocketPrefs;

/**
 * How long each game has been played, for everything Pocket Nexus starts (Android games and
 * apps, emulated games, cloud services, shortcuts). A session runs from the launch until a
 * Pocket Nexus screen comes back. Streamed PC games keep their own, exact time (the stream).
 */
public final class PlayTime {
    private PlayTime() {}

    private static final String KEY_SESSION = "pocket_pt_session";
    private static final String KEY_SESSION_AT = "pocket_pt_session_at";
    private static final String PREFIX = "pocket_pt_";
    /** Shorter: just a look. Longer: the handheld was left alone (or asleep), not played. */
    private static final long MIN_MS = 45_000L;
    private static final long MAX_MS = 10L * 60 * 60 * 1000;

    /** A game was just started (called with the launch). */
    public static void started(Context context, HomeItem item) {
        if (item == null || isStream(item)) {
            return;
        }
        PocketPrefs.prefs(context).edit()
                .putString(KEY_SESSION, item.key())
                .putLong(KEY_SESSION_AT, System.currentTimeMillis())
                .apply();
    }

    /** A Pocket Nexus screen is back: the running session ends. */
    public static void resumed(Context context) {
        SharedPreferences p = PocketPrefs.prefs(context);
        String key = p.getString(KEY_SESSION, null);
        if (key == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - p.getLong(KEY_SESSION_AT, 0);
        SharedPreferences.Editor e = p.edit().remove(KEY_SESSION).remove(KEY_SESSION_AT);
        if (elapsed >= MIN_MS && elapsed <= MAX_MS) {
            e.putLong(PREFIX + key, p.getLong(PREFIX + key, 0) + elapsed / 1000);
        }
        e.apply();
        if (elapsed >= MIN_MS && elapsed <= MAX_MS) {
            addToToday(context, elapsed / 1000);
        }
    }

    // ---- The days (for the play stats) --------------------------------------------------

    private static final String DAY = "pocket_pt_day_";
    private static final int KEEP_DAYS = 60;

    private static String dayKey(long time) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(time);
        return DAY + String.format(java.util.Locale.ROOT, "%04d%02d%02d", c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    /** Adds to today's time played (and forgets days older than two months). */
    public static void addToToday(Context context, long seconds) {
        if (seconds <= 0) {
            return;
        }
        SharedPreferences p = PocketPrefs.prefs(context);
        String today = dayKey(System.currentTimeMillis());
        SharedPreferences.Editor e = p.edit().putLong(today, p.getLong(today, 0) + seconds);
        String oldest = dayKey(System.currentTimeMillis() - KEEP_DAYS * 24L * 60 * 60 * 1000);
        for (String k : p.getAll().keySet()) {
            if (k.startsWith(DAY) && k.compareTo(oldest) < 0) {
                e.remove(k);
            }
        }
        e.apply();
    }

    /** Seconds played on each of the last n days (index 0: today, 1: yesterday...). */
    public static long[] lastDays(Context context, int n) {
        SharedPreferences p = PocketPrefs.prefs(context);
        long[] out = new long[n];
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            out[i] = p.getLong(dayKey(now - i * 24L * 60 * 60 * 1000), 0);
        }
        return out;
    }

    /** Seconds played (0 when never, or not known). */
    public static long seconds(Context context, HomeItem item) {
        if (item == null) {
            return 0;
        }
        if (HomeItem.PC.equals(item.kind) && item.a != null) {
            return PocketPrefs.getPlaytime(context, item.a, item.n);
        }
        return PocketPrefs.prefs(context).getLong(PREFIX + item.key(), 0);
    }

    /** "12 h", "45 min" (or null under a minute). */
    public static String format(Context context, long seconds) {
        if (seconds < 60) {
            return null;
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return context.getString(com.limelight.R.string.pocket_pt_minutes, minutes);
        }
        long hours = minutes / 60;
        if (hours < 10 && minutes % 60 >= 6) {
            return context.getString(com.limelight.R.string.pocket_pt_hours_dec,
                    String.format(java.util.Locale.getDefault(), "%.1f", minutes / 60f));
        }
        return context.getString(com.limelight.R.string.pocket_pt_hours, hours);
    }

    private static boolean isStream(HomeItem item) {
        return HomeItem.PC.equals(item.kind) || HomeItem.PC_LIBRARY_GAME.equals(item.kind)
                || HomeItem.PC_GAMES.equals(item.kind);
    }
}
