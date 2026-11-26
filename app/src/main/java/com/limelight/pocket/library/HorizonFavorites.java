package com.limelight.pocket.library;

import android.content.Context;

import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.retro.RetroLibrary;
import com.limelight.pocket.retro.RomScanner;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * L03: one favourite mark for a game, whichever way it is played. The mark lives where each
 * provider already keeps it, so the PC game page and the Retro screen show the same thing:
 * a PC game in the PC favourites, a ROM in the Retro favourites (ES-DE's own favourites count
 * until unmarked here), and anything without a store of its own (Android, cloud, GameNative...)
 * in the Library's.
 */
public final class HorizonFavorites {
    private HorizonFavorites() {}

    /** Ways that have no favourite store of their own, by their item key. */
    static final String KEY = "horizon_library_favorites";

    /** A game is a favourite when any way of playing it is. */
    public static boolean any(List<Boolean> marks) {
        if (marks == null) return false;
        for (Boolean m : marks) {
            if (Boolean.TRUE.equals(m)) return true;
        }
        return false;
    }

    /** One way's mark. {@code lib}: the ROM's scanned folder, for ES-DE's favourites (may be null). */
    public static boolean marked(Context c, HomeItem item, RetroLibrary.Library lib) {
        switch (item.kind) {
            case HomeItem.PC:
                return PocketPrefs.isFavorite(c, item.a, item.n);
            case HomeItem.RETRO: {
                RomScanner.Rom rom = rom(item);
                return lib != null ? RetroLibrary.isFavorite(c, lib, rom)
                        : Boolean.TRUE.equals(RetroLibrary.favoriteMark(c, rom.key()));
            }
            default:
                return own(c).contains(item.key());
        }
    }

    /** Marks (or unmarks) every way of the game, each in its own store. */
    public static void set(Context c, UniversalLibrary.Game g, boolean on) {
        Set<String> own = new HashSet<>(own(c));
        boolean ownChanged = false;
        for (UniversalLibrary.Way w : g.ways) {
            HomeItem i = w.item;
            switch (i.kind) {
                case HomeItem.PC:
                    PocketPrefs.setFavorite(c, i.a, i.n, on);
                    break;
                case HomeItem.RETRO:
                    RetroLibrary.setFavorite(c, rom(i), on);
                    break;
                default:
                    ownChanged |= on ? own.add(i.key()) : own.remove(i.key());
                    break;
            }
        }
        if (ownChanged) {
            PocketPrefs.prefs(c).edit().putStringSet(KEY, own).apply();
        }
        g.favorite = on;
    }

    private static Set<String> own(Context c) {
        return PocketPrefs.prefs(c).getStringSet(KEY, Collections.<String>emptySet());
    }

    private static RomScanner.Rom rom(HomeItem i) {
        return new RomScanner.Rom(i.a, i.b, new File(i.c, i.b), false);
    }
}
