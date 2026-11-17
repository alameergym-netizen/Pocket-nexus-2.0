package com.limelight.pocket.apps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.limelight.R;
import com.limelight.pocket.PocketArt;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketIcons;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Pocket Nexus's own apps (Music, Files, the browser, Search, your play, every app, the
 * settings): on Home's dock, in the Android section under "Nexus", and on Home like any game,
 * each with art of its own (a cover, a backdrop and a logo, drawn here, so they look like they
 * belong next to the games).
 */
public final class NexusApps {
    private NexusApps() {}

    /** Where their art is kept (like a PC's uuid). */
    public static final String UUID = "nexus";
    /** Drawn again when the design changes. */
    private static final int ART_VERSION = 1;
    private static final String KEY_ART = "pocket_nexus_art_v";

    public static final String MUSIC = "music";
    public static final String FILES = "files";
    public static final String BROWSER = "browser";
    public static final String SEARCH = "search";
    public static final String STATS = "stats";
    public static final String APPS = "apps";
    public static final String SETTINGS = "settings";

    public static final class App {
        public final String id;
        public final int label;
        public final int blurb;
        public final String icon;
        public final int from;
        public final int to;

        App(String id, int label, int blurb, String icon, int from, int to) {
            this.id = id;
            this.label = label;
            this.blurb = blurb;
            this.icon = icon;
            this.from = from;
            this.to = to;
        }
    }

    public static final App[] ALL = {
            new App(MUSIC, R.string.pocket_nexus_music, R.string.pocket_nexus_music_about, "MUSIC", 0xFFFF5C8A, 0xFF6D28D9),
            new App(FILES, R.string.pocket_nexus_files, R.string.pocket_nexus_files_about, "FOLDER", 0xFFFFB547, 0xFFD9480F),
            new App(BROWSER, R.string.pocket_nexus_browser, R.string.pocket_nexus_browser_about, "GLOBE", 0xFF4CB6FF, 0xFF1D3FD1),
            new App(SEARCH, R.string.pocket_nexus_search, R.string.pocket_nexus_search_about, "SEARCH", 0xFF3EE0C3, 0xFF0B6E7A),
            new App(STATS, R.string.pocket_nexus_stats, R.string.pocket_nexus_stats_about, "CHART", 0xFFA48BFF, 0xFF3B2491),
            new App(APPS, R.string.pocket_nexus_apps, R.string.pocket_nexus_apps_about, "APPS", 0xFF6EE08E, 0xFF13693A),
            new App(SETTINGS, R.string.pocket_nexus_settings, R.string.pocket_nexus_settings_about, "GEAR", 0xFF9AA3B5, 0xFF2E3442),
    };

    public static App byId(String id) {
        for (App a : ALL) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }

    public static int idFor(String id) {
        int h = ("nexus:" + id).hashCode() & 0x3FFFFFFF;
        return h == 0 ? 1 : h;
    }

    /** It as something for Home (and Quick Resume). */
    public static HomeItem item(Context c, App a) {
        return new HomeItem(HomeItem.NEXUS, c.getString(a.label), a.id, "", "", 0);
    }

    /** Opens it. False when it's not one of them. */
    public static boolean open(Activity from, String id) {
        switch (id) {
            case MUSIC:
                com.limelight.pocket.music.MusicActivity.open(from);
                return true;
            case FILES:
                com.limelight.pocket.files.FilesActivity.open(from);
                return true;
            case BROWSER:
                com.limelight.pocket.browser.BrowserActivity.open(from, null);
                return true;
            case SEARCH:
                com.limelight.pocket.search.SearchActivity.open(from);
                return true;
            case STATS:
                com.limelight.pocket.home.StatsActivity.open(from);
                return true;
            case APPS:
                AppDrawerActivity.open(from);
                return true;
            case SETTINGS:
                try {
                    from.startActivity(new Intent(from, com.limelight.pocket.QuickSettingsActivity.class));
                } catch (RuntimeException ignored) {
                }
                return true;
            default:
                return false;
        }
    }

    // ---- Their art ------------------------------------------------------------------------------

    /** Draws their covers, backdrops and logos when they aren't there yet (or are an older look). Blocking. */
    public static synchronized void ensureArt(Context c) {
        boolean fresh = PocketPrefs.prefs(c).getInt(KEY_ART, 0) != ART_VERSION;
        for (App a : ALL) {
            int id = idFor(a.id);
            String name = c.getString(a.label);
            try {
                if (fresh || !PocketArt.has(c, UUID, id, PocketArt.COVER)) {
                    write(PocketArt.file(c, UUID, id, PocketArt.COVER), cover(a, name, 600, 900), false);
                }
                if (fresh || !PocketArt.has(c, UUID, id, PocketArt.SQUARE)) {
                    write(PocketArt.file(c, UUID, id, PocketArt.SQUARE), cover(a, name, 512, 512), false);
                }
                if (fresh || !PocketArt.has(c, UUID, id, PocketArt.WIDE)) {
                    write(PocketArt.file(c, UUID, id, PocketArt.WIDE), cover(a, name, 920, 430), false);
                }
                if (fresh || !PocketArt.has(c, UUID, id, PocketArt.ICON)) {
                    write(PocketArt.file(c, UUID, id, PocketArt.ICON), icon(a, 512), true);
                }
                if (fresh || !PocketArt.has(c, UUID, id, PocketArt.HERO)) {
                    write(PocketArt.file(c, UUID, id, PocketArt.HERO), hero(a, 1920, 1080), false);
                }
                if (fresh || !PocketArt.has(c, UUID, id, PocketArt.LOGO)) {
                    write(PocketArt.file(c, UUID, id, PocketArt.LOGO), logo(a, name), true);
                }
            } catch (Exception | OutOfMemoryError ignored) {
            }
        }
        PocketPrefs.prefs(c).edit().putInt(KEY_ART, ART_VERSION).apply();
    }

    private static void write(File f, Bitmap b, boolean png) throws java.io.IOException {
        File dir = f.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new java.io.IOException("No folder");
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            b.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 92, out);
        }
        b.recycle();
    }

    /** Its colours from corner to corner, a soft light at the top, its icon big and its name. */
    public static Bitmap cover(App a, String name, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, a.from, a.to, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // Light from the top left, shade to the bottom
        float big = Math.max(w, h);
        p.setShader(new RadialGradient(w * 0.2f, h * 0.05f, big * 0.8f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, h * 0.45f, 0, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // Faint rings behind the icon (depth)
        float cx = w / 2f;
        boolean wide = w > h * 1.4f;
        float cy = wide ? h * 0.5f : h * 0.42f;
        float iconSize = Math.min(w, h) * (wide ? 0.42f : 0.40f);
        if (wide) {
            cx = w * 0.27f;
        }
        p.setStyle(Paint.Style.STROKE);
        for (int i = 1; i <= 3; i++) {
            p.setStrokeWidth(Math.max(2f, Math.min(w, h) * 0.006f));
            p.setColor(Color.argb(28 - i * 6, 255, 255, 255));
            c.drawCircle(cx, cy, iconSize * (0.75f + i * 0.32f), p);
        }
        p.setStyle(Paint.Style.FILL);
        // The icon, with a soft shadow under it
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setMaskFilter(new BlurMaskFilter(iconSize * 0.08f, BlurMaskFilter.Blur.NORMAL));
        PocketIcons.draw(c, a.icon, cx, cy + iconSize * 0.05f, iconSize, 0x55000000, shadow);
        PocketIcons.draw(c, a.icon, cx, cy, iconSize, 0xFFFFFFFF, p);
        p.setMaskFilter(null);
        // The name
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xFFFFFFFF);
        Typeface face = PocketFonts.display();
        t.setTypeface(face != null ? face : Typeface.DEFAULT_BOLD);
        if (wide) {
            t.setTextAlign(Paint.Align.LEFT);
            t.setTextSize(h * 0.2f);
            fit(t, name, w * 0.46f);
            c.drawText(name, w * 0.5f, h * 0.56f, t);
            eyebrow(c, w * 0.5f, h * 0.36f, h * 0.06f, Paint.Align.LEFT);
        }
        else {
            t.setTextAlign(Paint.Align.CENTER);
            t.setTextSize(Math.min(w, h) * 0.13f);
            fit(t, name, w * 0.84f);
            c.drawText(name, cx, h * (h > w ? 0.8f : 0.86f), t);
            if (h > w) {
                eyebrow(c, cx, h * 0.07f, w * 0.038f, Paint.Align.CENTER);
            }
        }
        return b;
    }

    /** "POCKET NEXUS", small and spaced out. */
    private static void eyebrow(Canvas c, float x, float y, float size, Paint.Align align) {
        Paint e = new Paint(Paint.ANTI_ALIAS_FLAG);
        e.setColor(0xB3FFFFFF);
        e.setTypeface(PocketFonts.bold());
        e.setTextSize(size);
        e.setLetterSpacing(0.24f);
        e.setTextAlign(align);
        c.drawText("POCKET NEXUS", x, y, e);
    }

    private static void fit(Paint t, String s, float max) {
        while (t.measureText(s) > max && t.getTextSize() > 10) {
            t.setTextSize(t.getTextSize() * 0.94f);
        }
    }

    /** A rounded square in its colours with its icon (for the small icon cards). */
    public static Bitmap icon(App a, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, size, size, a.from, a.to, Shader.TileMode.CLAMP));
        float r = size * 0.24f;
        c.drawRoundRect(new RectF(0, 0, size, size), r, r, p);
        p.setShader(new RadialGradient(size * 0.25f, 0, size, 0x44FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, size, size), r, r, p);
        p.setShader(null);
        PocketIcons.draw(c, a.icon, size / 2f, size / 2f, size * 0.5f, 0xFFFFFFFF, p);
        return b;
    }

    /** The backdrop behind Home: dark, its colour glowing from the right, its icon faint and big. */
    public static Bitmap hero(App a, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(0xFF08080C);
        p.setShader(new RadialGradient(w * 0.78f, h * 0.38f, w * 0.62f, withAlpha(a.from, 150), 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.95f, h * 0.95f, w * 0.5f, withAlpha(a.to, 170), 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        float cx = w * 0.74f;
        float cy = h * 0.42f;
        for (int i = 0; i < 5; i++) {
            p.setStrokeWidth(3f);
            p.setColor(Color.argb(22 - i * 3, 255, 255, 255));
            c.drawCircle(cx, cy, h * (0.26f + i * 0.13f), p);
        }
        p.setStyle(Paint.Style.FILL);
        PocketIcons.draw(c, a.icon, cx, cy, h * 0.42f, 0x30FFFFFF, p);
        return b;
    }

    /** Its logo: its icon in its colours, then its name (white, over Home's art). */
    public static Bitmap logo(App a, String name) {
        int h = 220;
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(PocketFonts.display());
        t.setTextSize(150);
        t.setColor(0xFFFFFFFF);
        float textW = t.measureText(name);
        int badge = 190;
        int w = Math.round(badge + 46 + textW + 10);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Bitmap ic = icon(a, badge);
        c.drawBitmap(ic, 0, (h - badge) / 2f, null);
        ic.recycle();
        Paint.FontMetrics fm = t.getFontMetrics();
        float base = h / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(name, badge + 46, base, t);
        return b;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
