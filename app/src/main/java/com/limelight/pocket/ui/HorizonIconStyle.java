package com.limelight.pocket.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.limelight.pocket.PocketPrefs;

/**
 * T09: how Android apps' icons are drawn on their tiles.
 * <ul>
 * <li><b>Full</b> (default, as before): the icon's background is the tile, its mark in the middle.</li>
 * <li><b>Themed</b>: the app's own monochrome icon (Android 13+) in the look's accent on a dark
 * tile, so a page of apps reads as one set. An app without one keeps its full icon: nothing is
 * recoloured by guesswork.</li>
 * <li><b>Classic</b>: the icon in its launcher circle on a dark tile.</li>
 * </ul>
 */
public final class HorizonIconStyle {
    private HorizonIconStyle() {
    }

    public static final String KEY = "horizon_icon_style";
    public static final String FULL = "full";
    public static final String THEMED = "themed";
    public static final String CLASSIC = "classic";
    public static final String[] STYLES = {FULL, THEMED, CLASSIC};
    static final int TILE = 0xFF16181D;

    /** A stored value as a known style (anything else: Full). */
    public static String normalize(String stored) {
        for (String s : STYLES) {
            if (s.equals(stored)) return s;
        }
        return FULL;
    }

    public static String next(String style) {
        String s = normalize(style);
        for (int i = 0; i < STYLES.length; i++) {
            if (STYLES[i].equals(s)) return STYLES[(i + 1) % STYLES.length];
        }
        return FULL;
    }

    public static String current(Context context) {
        return normalize(PocketPrefs.prefs(context).getString(KEY, FULL));
    }

    /** Part of a cache key: tiles drawn in another style (or accent, when themed) differ. */
    public static String cacheTag(Context context) {
        String s = current(context);
        return THEMED.equals(s) ? s + Integer.toHexString(PocketPrefs.accentColor(context)) : s;
    }

    /**
     * Draws an adaptive icon over the whole tile in a style. Returns false when it can't (then the
     * caller draws it the usual way).
     */
    public static boolean draw(Canvas c, Drawable icon, int size, String style, int accent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !(icon instanceof AdaptiveIconDrawable)) return false;
        AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) icon;
        String s = normalize(style);
        if (THEMED.equals(s)) {
            Drawable mono = Build.VERSION.SDK_INT >= 33 ? adaptive.getMonochrome() : null;
            if (mono == null) return false;
            c.drawColor(TILE);
            int grow = Math.round(size * 0.25f);
            Drawable m = mono.mutate();
            m.setColorFilter(new PorterDuffColorFilter(0xFF000000 | accent, PorterDuff.Mode.SRC_IN));
            m.setBounds(-grow, -grow, size + grow, size + grow);
            m.draw(c);
            return true;
        }
        if (CLASSIC.equals(s)) {
            c.drawColor(TILE);
            int inset = Math.round(size * 0.18f);
            int save = c.save();
            Path circle = new Path();
            circle.addCircle(size / 2f, size / 2f, (size - 2 * inset) / 2f, Path.Direction.CW);
            c.clipPath(circle);
            int grow = Math.round((size - 2 * inset) * 0.25f);
            Drawable back = adaptive.getBackground();
            Drawable front = adaptive.getForeground();
            if (back != null) {
                back.setBounds(inset - grow, inset - grow, size - inset + grow, size - inset + grow);
                back.draw(c);
            }
            if (front != null) {
                front.setBounds(inset - grow, inset - grow, size - inset + grow, size - inset + grow);
                front.draw(c);
            }
            c.restoreToCount(save);
            return true;
        }
        return false;
    }

    /** A sample tile of this app's own icon in a style, for the Studio row. */
    public static Bitmap sample(Context context, String style, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Drawable icon;
        try {
            icon = context.getPackageManager().getApplicationIcon(context.getPackageName());
        } catch (RuntimeException e) {
            return out;
        }
        if (!draw(c, icon, size, style, PocketPrefs.accentColor(context))) {
            int grow = Math.round(size * 0.25f);
            if (icon instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
                for (Drawable d : new Drawable[]{a.getBackground(), a.getForeground()}) {
                    if (d == null) continue;
                    d.setBounds(-grow, -grow, size + grow, size + grow);
                    d.draw(c);
                }
            } else {
                icon.setBounds(0, 0, size, size);
                icon.draw(c);
            }
        }
        return out;
    }
}
