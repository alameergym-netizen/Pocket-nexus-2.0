package com.limelight.pocket.ui;

import android.content.Context;
import android.graphics.Typeface;

import androidx.core.content.res.ResourcesCompat;

import com.limelight.R;

/**
 * The interface's typeface: Inter (a clean, modern grotesque in the spirit of Apple's system
 * font, SIL Open Font License), in the weights the console uses. Arabic and other scripts fall
 * back to the system's font for their letters.
 */
public final class PocketFonts {
    private PocketFonts() {}

    private static Typeface regular;
    private static Typeface medium;
    private static Typeface semibold;
    private static Typeface bold;
    private static Typeface display;
    /** Bumped whenever the typeface changes, so open screens can rebuild with it. */
    private static int generation;

    /** T09: the interface typeface (Look tab): Inter, the device's own font, or its condensed cut. */
    public static final String KEY = "pocket_typeface";
    public static final String INTER = "inter";
    public static final String SYSTEM = "system";
    public static final String CONDENSED = "condensed";
    public static final String[] TYPEFACES = {INTER, SYSTEM, CONDENSED};

    /** The chosen typeface (Inter when unset or unknown). */
    public static String chosen(Context context) {
        String v = com.limelight.pocket.PocketPrefs.prefs(context).getString(KEY, INTER);
        for (String t : TYPEFACES) if (t.equals(v)) return t;
        return INTER;
    }

    /** Switches to a typeface: saved, loaded, and every screen opened from now uses it. */
    public static synchronized void choose(Context context, String typeface) {
        com.limelight.pocket.PocketPrefs.prefs(context).edit().putString(KEY, typeface).apply();
        regular = null;
        init(context, typeface);
        generation++;
    }

    public static synchronized int generation() {
        return generation;
    }

    /** Loads them once (the application calls this; anything else just gets the fallbacks). */
    public static synchronized void init(Context context) {
        if (regular != null) {
            return;
        }
        init(context, chosen(context));
    }

    private static void init(Context context, String typeface) {
        Context c = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (!INTER.equals(typeface)) {
            // The device's own font (it covers every script the device does), or its condensed cut
            String family = CONDENSED.equals(typeface) ? "sans-serif-condensed" : "sans-serif";
            regular = Typeface.create(family, Typeface.NORMAL);
            medium = CONDENSED.equals(typeface) ? regular : Typeface.create("sans-serif-medium", Typeface.NORMAL);
            semibold = Typeface.create(CONDENSED.equals(typeface) ? family : "sans-serif-medium", Typeface.BOLD);
            bold = Typeface.create(family, Typeface.BOLD);
            display = bold;
            return;
        }
        regular = load(c, R.font.inter_regular, Typeface.create("sans-serif", Typeface.NORMAL));
        medium = load(c, R.font.inter_medium, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        semibold = load(c, R.font.inter_semibold, Typeface.create("sans-serif-medium", Typeface.BOLD));
        bold = load(c, R.font.inter_bold, Typeface.create("sans-serif", Typeface.BOLD));
        display = load(c, R.font.inter_display, bold);
    }

    private static Typeface load(Context c, int id, Typeface fallback) {
        try {
            Typeface t = ResourcesCompat.getFont(c, id);
            return t != null ? t : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static Typeface regular() {
        return regular != null ? regular : Typeface.create("sans-serif", Typeface.NORMAL);
    }

    public static Typeface medium() {
        return medium != null ? medium : Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public static Typeface semibold() {
        return semibold != null ? semibold : Typeface.create("sans-serif-medium", Typeface.BOLD);
    }

    public static Typeface bold() {
        return bold != null ? bold : Typeface.create("sans-serif", Typeface.BOLD);
    }

    /** Big titles (Inter's display cut: tighter, made for large sizes). */
    public static Typeface display() {
        return display != null ? display : bold();
    }

    /** The old family names, mapped to Inter. */
    public static Typeface of(String family) {
        if (family == null) {
            return regular();
        }
        switch (family) {
            case "sans-serif-medium":
                return medium();
            case "sans-serif-black":
            case "sans-serif-condensed-bold":
                return bold();
            case "sans-serif-light":
            case "sans-serif-thin":
            case "sans-serif":
                return regular();
            default:
                return Typeface.create(family, Typeface.NORMAL);
        }
    }
}
