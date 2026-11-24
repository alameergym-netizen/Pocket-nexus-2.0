package com.limelight.pocket.ui;

import android.content.Context;
import com.limelight.pocket.PocketPrefs;

/** Opt-in visual geometry independent of the existing theme presets. */
public final class HorizonAppearance {
    public static final String KEY_DENSITY = "horizon_card_density";
    public static final String KEY_CORNERS = "horizon_card_corners";
    public static final String KEY_ART_MOTION = "horizon_art_motion";
    private HorizonAppearance() {}

    static int index(int n) { return n >= 0 && n <= 2 ? n : 0; }
    public static int density(Context c) { return index(PocketPrefs.prefs(c).getInt(KEY_DENSITY, 0)); }
    public static int corners(Context c) { return index(PocketPrefs.prefs(c).getInt(KEY_CORNERS, 0)); }
    public static boolean artMotion(Context c) { return PocketPrefs.prefs(c).getBoolean(KEY_ART_MOTION, true); }

    public static float cardScale(int density, boolean shelf, float heightDp) {
        if (index(density) == 1) { return 0.88f; }
        if (index(density) == 2) {
            // Preserve vertical hero room on compact handheld screens.
            return shelf && heightDp < 480f ? 1f : 1.12f;
        }
        return 1f;
    }

    public static float radius(int corners, float original, float shortestEdge) {
        float want = index(corners) == 1 ? 4f : index(corners) == 2 ? 22f : original;
        return Math.max(0f, Math.min(want, shortestEdge / 2f));
    }
}
