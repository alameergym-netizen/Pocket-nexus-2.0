package com.limelight.pocket.library;

import android.content.Context;
import android.content.SharedPreferences;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What you think of each game, whichever platform it's played on: its status (playing, in the
 * backlog, finished, dropped) and the collections you put it in. Kept by the game's plain name,
 * so the same game on a PC, in a ROM folder and on Android is one game here. In the settings
 * backup with everything else.
 */
public final class GameShelf {

    public static final int NONE = 0;
    public static final int PLAYING = 1;
    public static final int BACKLOG = 2;
    public static final int FINISHED = 3;
    public static final int DROPPED = 4;
    public static final int STATUS_COUNT = 5;

    private static final String KEY_STATUS = "pocket_shelf_s_";
    private static final String KEY_COLLECTIONS = "pocket_shelf_cols";
    private static final String KEY_IN = "pocket_shelf_c_";

    private GameShelf() {
    }

    /**
     * A game's plain name: no region or dump tags ("(USA)", "[!]"), no marks or symbols, no
     * leading "The", one space between words. The same game from anywhere ends up the same.
     */
    public static String key(String title) {
        if (title == null) {
            return "";
        }
        String s = title.toLowerCase(Locale.ROOT);
        s = s.replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]", " ");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replace("&", " and ").replaceAll("[™®©]", "");
        s = s.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        s = s.replaceFirst("^the ", "");
        s = s.replaceFirst(" (game of the year|goty|definitive|complete|deluxe) edition$", "");
        return s;
    }

    /** The name to show: the tags taken off ("Super Metroid (Japan, USA)" -> "Super Metroid"). */
    public static String clean(String title) {
        if (title == null) {
            return "";
        }
        String s = title.replaceAll("\\s*\\([^)]*\\)|\\s*\\[[^\\]]*\\]", "").trim();
        return s.isEmpty() ? title : s;
    }

    private static SharedPreferences prefs(Context c) {
        return PocketPrefs.prefs(c);
    }

    public static int status(Context c, String key) {
        return prefs(c).getInt(KEY_STATUS + key, NONE);
    }

    public static void setStatus(Context c, String key, int status) {
        if (status == NONE) {
            prefs(c).edit().remove(KEY_STATUS + key).apply();
        }
        else {
            prefs(c).edit().putInt(KEY_STATUS + key, status).apply();
        }
    }

    public static int statusLabel(int status) {
        switch (status) {
            case PLAYING:
                return R.string.pocket_ul_status_playing;
            case BACKLOG:
                return R.string.pocket_ul_status_backlog;
            case FINISHED:
                return R.string.pocket_ul_status_finished;
            case DROPPED:
                return R.string.pocket_ul_status_dropped;
            default:
                return R.string.pocket_ul_status_none;
        }
    }

    /** Its colour (Apple's system colours): green, yellow, blue, grey. */
    public static int statusColor(int status) {
        switch (status) {
            case PLAYING:
                return 0xFF34C759;
            case BACKLOG:
                return 0xFFFFCC00;
            case FINISHED:
                return 0xFF0A84FF;
            case DROPPED:
                return 0xFF8E8E93;
            default:
                return 0;
        }
    }

    // ---- Collections ----------------------------------------------------------------------------

    /** Your collections, by name (A to Z). */
    public static List<String> collections(Context c) {
        List<String> out = new ArrayList<>(prefs(c).getStringSet(KEY_COLLECTIONS, Collections.emptySet()));
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Add the game without removing an existing membership. Create the collection in the same edit. */
    public static synchronized String addToCollection(Context c, String name, String key) {
        name = name != null ? name.trim() : "";
        if (name.isEmpty()) return null;
        SharedPreferences preferences = prefs(c);
        Set<String> all = new HashSet<>(preferences.getStringSet(KEY_COLLECTIONS, Collections.emptySet()));
        Set<String> members = gamesIn(c, name);
        all.add(name);
        members.add(key);
        preferences.edit().putStringSet(KEY_COLLECTIONS, all).putStringSet(KEY_IN + name, members).apply();
        return name;
    }

    public static synchronized void deleteCollection(Context c, String name) {
        Set<String> all = new HashSet<>(prefs(c).getStringSet(KEY_COLLECTIONS, Collections.emptySet()));
        all.remove(name);
        prefs(c).edit().putStringSet(KEY_COLLECTIONS, all).remove(KEY_IN + name).apply();
    }

    public static Set<String> gamesIn(Context c, String collection) {
        return new HashSet<>(prefs(c).getStringSet(KEY_IN + collection, Collections.emptySet()));
    }

    public static boolean isIn(Context c, String collection, String key) {
        return prefs(c).getStringSet(KEY_IN + collection, Collections.emptySet()).contains(key);
    }

    /** In it, or out of it (true: it's in now). */
    public static synchronized boolean toggle(Context c, String collection, String key) {
        Set<String> in = gamesIn(c, collection);
        boolean now = !in.remove(key);
        if (now) {
            in.add(key);
        }
        prefs(c).edit().putStringSet(KEY_IN + collection, in).apply();
        return now;
    }
}
