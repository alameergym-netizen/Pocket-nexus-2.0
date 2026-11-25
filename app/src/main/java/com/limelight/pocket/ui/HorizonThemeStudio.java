package com.limelight.pocket.ui;

import android.content.Context;
import android.content.SharedPreferences;
import com.limelight.pocket.PocketPrefs;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Local, versioned visual snapshots and the storage side of Theme Studio
 * ({@link com.limelight.pocket.studio.ThemeStudioActivity}). Never stores or changes
 * game/stream/host credentials. Custom looks keep their original version-1 JSON slots, so looks
 * saved by earlier builds stay readable.
 */
public final class HorizonThemeStudio {
    private static final int SLOTS = 3;
    private static final String PREFIX = "horizon_look_slot_";
    /** The look in place before the last apply from Theme Studio (HorizonLook text). */
    static final String KEY_UNDO = "horizon_look_undo";
    private HorizonThemeStudio() {}

    static final class Look {
        final int accent, density, corners;
        final String backdrop, material, sound, layout, focus;
        final boolean motion, colorArt, liveGlass;
        Look(int accent, String backdrop, String material, String sound, String layout,
             String focus, int density, int corners, boolean motion, boolean colorArt, boolean liveGlass) {
            this.accent = accent;
            this.backdrop = backdrop; this.material = material; this.sound = sound;
            this.layout = layout; this.focus = focus;
            this.density = density; this.corners = corners;
            this.motion = motion; this.colorArt = colorArt; this.liveGlass = liveGlass;
        }

        JSONObject encode() throws JSONException {
            return new JSONObject().put("version",1).put("accent",accent)
                    .put("backdrop",backdrop).put("material",material).put("sound",sound)
                    .put("layout",layout).put("focus",focus).put("density",density)
                    .put("corners",corners).put("motion",motion).put("colorArt",colorArt)
                    .put("liveGlass",liveGlass);
        }

        static Look decode(String raw) throws JSONException {
            if (raw == null || raw.length() > 4096) { throw new JSONException("Invalid look"); }
            JSONObject j = new JSONObject(raw);
            if (j.getInt("version") != 1) { throw new JSONException("Unknown look version"); }
            int accent = j.getInt("accent"), density = j.getInt("density"), corners = j.getInt("corners");
            String backdrop = j.getString("backdrop"), material = j.getString("material"),
                    sound = j.getString("sound"), layout = j.getString("layout"), focus = j.getString("focus");
            boolean knownLayout = false;
            for (Layouts.Layout l : Layouts.ALL) { knownLayout |= l.id.equals(layout); }
            if ((accent >>> 24) != 255 || density < 0 || density > 2 || corners < 0 || corners > 2
                    || !Arrays.asList("glow","ambient","art","black").contains(backdrop)
                    || !Arrays.asList("frost","glass").contains(material)
                    || !Arrays.asList(PocketPrefs.SOUND_STYLES).contains(sound)
                    || !knownLayout || !Arrays.asList("ring_zoom","ring","zoom").contains(focus)) {
                throw new JSONException("Unsupported look");
            }
            return new Look(accent,backdrop,material,sound,layout,focus,density,corners,
                    j.getBoolean("motion"),j.getBoolean("colorArt"),j.getBoolean("liveGlass"));
        }

        static Look capture(Context c) {
            SharedPreferences p = PocketPrefs.prefs(c);
            return new Look(PocketPrefs.accentColor(c), PocketPrefs.backdrop(c),
                    PocketPrefs.frost(c) ? PocketPrefs.MATERIAL_FROST : PocketPrefs.MATERIAL_GLASS,
                    p.getString(PocketPrefs.KEY_SOUND_STYLE,PocketPrefs.DEFAULT_SOUND_STYLE),
                    Layouts.home(c).id, PocketPrefs.focusStyle(c), HorizonAppearance.density(c),
                    HorizonAppearance.corners(c), HorizonAppearance.artMotion(c),
                    PocketPrefs.colorBackgroundArt(c), PocketPrefs.liveGlass(c));
        }

        static Look of(HorizonLook l) {
            return new Look(l.accent, l.backdrop, l.material, l.sound, l.layout, l.focus, l.density, l.corners,
                    l.motion, l.colorArt, l.liveGlass);
        }

        HorizonLook toLook() {
            return new HorizonLook(accent, backdrop, material, sound, layout, focus, density, corners,
                    motion, colorArt, liveGlass);
        }

        void apply(Context c) {
            PocketPrefs.prefs(c).edit().putInt(PocketPrefs.KEY_ACCENT,accent)
                    .putString(PocketPrefs.KEY_BACKDROP,backdrop).putString(PocketPrefs.KEY_MATERIAL,material)
                    .putString(PocketPrefs.KEY_SOUND_STYLE,sound).putString("pocket_home_layout",layout)
                    .putString(PocketPrefs.KEY_FOCUS_STYLE,focus)
                    .putInt(HorizonAppearance.KEY_DENSITY,density).putInt(HorizonAppearance.KEY_CORNERS,corners)
                    .putBoolean(HorizonAppearance.KEY_ART_MOTION,motion)
                    .putBoolean(PocketPrefs.KEY_COLOR_ART,colorArt).putBoolean(PocketPrefs.KEY_LIVE_GLASS,liveGlass)
                    .apply();
        }

        String summary() {
            return String.format(Locale.ROOT,"#%06X · %s · %s",accent & 0xffffff,layout,backdrop);
        }
    }

    private static Look load(Context c, int slot) {
        if (slot < 0 || slot >= SLOTS) { return null; }
        try { return Look.decode(PocketPrefs.prefs(c).getString(PREFIX+slot,null)); }
        catch (JSONException | RuntimeException e) { return null; }
    }

    public static int slots() {
        return SLOTS;
    }

    public static int count(Context c) {
        int n = 0;
        for (int i=0;i<SLOTS;i++) { if(load(c,i)!=null) { n++; } }
        return n;
    }

    /** The look the console shows now. */
    public static HorizonLook current(Context c) {
        return Look.capture(c).toLook();
    }

    /** A saved custom look, or null when the slot is empty or unreadable. */
    public static HorizonLook slot(Context c, int slot) {
        Look look = load(c, slot);
        return look == null ? null : look.toLook();
    }

    /** Saves a look into a custom slot (version-1 JSON, checked by reading it back). */
    public static boolean saveSlot(Context c, int slot, HorizonLook look) {
        if (slot < 0 || slot >= SLOTS || look == null || !look.valid()) { return false; }
        try {
            String raw = Look.of(look).encode().toString();
            Look.decode(raw);
            PocketPrefs.prefs(c).edit().putString(PREFIX+slot,raw).apply();
            return true;
        } catch (JSONException | RuntimeException e) { return false; }
    }

    public static void clearSlot(Context c, int slot) {
        if (slot >= 0 && slot < SLOTS) {
            PocketPrefs.prefs(c).edit().remove(PREFIX + slot).apply();
        }
    }

    /**
     * Applies a look and remembers the one it replaces, so Theme Studio can offer it back. Only
     * appearance keys are written.
     */
    public static boolean apply(Context c, HorizonLook look) {
        if (look == null || !look.valid()) { return false; }
        HorizonLook before = current(c);
        if (!before.equals(look)) {
            PocketPrefs.prefs(c).edit().putString(KEY_UNDO, before.toText()).apply();
        }
        Look.of(look).apply(c);
        return true;
    }

    /** The look replaced by the last apply from Theme Studio, or null. */
    public static HorizonLook undo(Context c) {
        try { return HorizonLook.parse(PocketPrefs.prefs(c).getString(KEY_UNDO, null)); }
        catch (HorizonLook.Invalid | RuntimeException e) { return null; }
    }

    /** A theme preset over a base look, exactly as applying it from Settings would leave things. */
    public static HorizonLook preset(HorizonLook base, int index) {
        PocketTheme.Preset p = PocketTheme.THEMES[index];
        return base.withPreset(p.accent, p.backdrop, p.frost, p.sounds, p.layout, p.focus);
    }

    /** The preset a look shows as, or -1 for a custom mix (same rule as the Settings theme row). */
    public static int presetIndex(HorizonLook look) {
        return PocketTheme.matchingPreset(look.accent, look.backdrop, look.frost(), look.sound,
                look.layout, look.focus);
    }

    /** True for the looks that bring their own Home, Library and Control layouts. */
    public static boolean ownLayouts(HorizonLook look) {
        int i = presetIndex(look);
        if (i < 0) { return false; }
        int name = PocketTheme.THEMES[i].name;
        return name == com.limelight.R.string.horizon_theme_pure || name == com.limelight.R.string.horizon_theme_titanium;
    }
}
