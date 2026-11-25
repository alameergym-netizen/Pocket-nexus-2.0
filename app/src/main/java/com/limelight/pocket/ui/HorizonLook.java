package com.limelight.pocket.ui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One complete console look as plain values, independent of Android. The text form is the
 * Theme Studio export format: a fixed header and one "key value" line per field. Parsing is
 * strict (every field once, known keys and values only, bounded size) so a pasted look can never
 * set anything other than appearance.
 */
public final class HorizonLook {
    public static final String HEADER = "pocket-nexus-look 1";
    public static final int MAX_TEXT = 2048;
    private static final int MAX_LINES = 32;

    // Mirrors PocketPrefs and Layouts; HorizonLookInventoryTest keeps them in step.
    static final String[] BACKDROPS = {"glow", "ambient", "art", "black"};
    static final String[] MATERIALS = {"frost", "glass"};
    static final String[] SOUNDS = {"glass", "soft", "crisp", "retro", "pop", "aurora", "crystal", "tactile"};
    static final String[] LAYOUTS = {"tiles", "covers", "wide", "grid_covers", "grid_tiles", "grid_wide",
            "list", "portal", "horizon_workspace"};
    static final String[] FOCUS = {"ring_zoom", "ring", "zoom"};
    private static final String[] KEYS = {"accent", "backdrop", "material", "sound", "layout", "focus",
            "density", "corners", "motion", "color-art", "live-glass"};

    public final int accent;
    public final String backdrop, material, sound, layout, focus;
    public final int density, corners;
    public final boolean motion, colorArt, liveGlass;

    public HorizonLook(int accent, String backdrop, String material, String sound, String layout, String focus,
                       int density, int corners, boolean motion, boolean colorArt, boolean liveGlass) {
        this.accent = 0xFF000000 | accent;
        this.backdrop = backdrop;
        this.material = material;
        this.sound = sound;
        this.layout = layout;
        this.focus = focus;
        this.density = density;
        this.corners = corners;
        this.motion = motion;
        this.colorArt = colorArt;
        this.liveGlass = liveGlass;
    }

    /** The look of a fresh install (the Nexus preset with the original Home layout). */
    public static HorizonLook defaults() {
        return new HorizonLook(0xFFFFFFFF, "ambient", "frost", "aurora", "tiles", "ring_zoom",
                0, 0, true, true, true);
    }

    /** True when every value is one this build can show. */
    public boolean valid() {
        return contains(BACKDROPS, backdrop) && contains(MATERIALS, material) && contains(SOUNDS, sound)
                && contains(LAYOUTS, layout) && contains(FOCUS, focus)
                && density >= 0 && density <= 2 && corners >= 0 && corners <= 2;
    }

    public boolean frost() {
        return !"glass".equals(material);
    }

    /**
     * A theme preset over this look: its color, backdrop, material and sounds, and its layout and
     * focus when it has them. Geometry, motion and art choices stay as they are (as applying a
     * preset from Settings always has).
     */
    public HorizonLook withPreset(int presetAccent, String presetBackdrop, boolean presetFrost, String presetSound,
                                  String presetLayout, String presetFocus) {
        return new HorizonLook(presetAccent, presetBackdrop, presetFrost ? "frost" : "glass", presetSound,
                presetLayout != null ? presetLayout : layout, presetFocus != null ? presetFocus : focus,
                density, corners, motion, colorArt, liveGlass);
    }

    public String toText() {
        StringBuilder b = new StringBuilder(HEADER).append('\n');
        line(b, "accent", String.format(Locale.ROOT, "#%06X", accent & 0xFFFFFF));
        line(b, "backdrop", backdrop);
        line(b, "material", material);
        line(b, "sound", sound);
        line(b, "layout", layout);
        line(b, "focus", focus);
        line(b, "density", Integer.toString(density));
        line(b, "corners", Integer.toString(corners));
        line(b, "motion", onOff(motion));
        line(b, "color-art", onOff(colorArt));
        line(b, "live-glass", onOff(liveGlass));
        return b.toString();
    }

    /** Why a text could not be read as a look. */
    public static final class Invalid extends Exception {
        public static final int NOT_A_LOOK = 0;
        public static final int UNSUPPORTED = 1;
        public final int reason;

        Invalid(int reason, String detail) {
            super(detail);
            this.reason = reason;
        }
    }

    public static HorizonLook parse(String text) throws Invalid {
        if (text == null) {
            throw new Invalid(Invalid.NOT_A_LOOK, "empty");
        }
        String body = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (body.isEmpty() || body.length() > MAX_TEXT) {
            throw new Invalid(Invalid.NOT_A_LOOK, "size");
        }
        String[] lines = body.split("\n", -1);
        if (lines.length > MAX_LINES || !HEADER.equals(lines[0].trim())) {
            throw new Invalid(Invalid.NOT_A_LOOK, "header");
        }
        Map<String, String> values = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            int space = line.indexOf(' ');
            if (space <= 0) {
                throw new Invalid(Invalid.UNSUPPORTED, "line " + i);
            }
            String key = line.substring(0, space);
            String value = line.substring(space + 1).trim();
            if (!contains(KEYS, key) || values.put(key, value) != null) {
                throw new Invalid(Invalid.UNSUPPORTED, "key " + key);
            }
        }
        if (values.size() != KEYS.length) {
            throw new Invalid(Invalid.UNSUPPORTED, "missing");
        }
        HorizonLook look = new HorizonLook(color(values.get("accent")), values.get("backdrop"),
                values.get("material"), values.get("sound"), values.get("layout"), values.get("focus"),
                level(values.get("density")), level(values.get("corners")), flag(values.get("motion")),
                flag(values.get("color-art")), flag(values.get("live-glass")));
        if (!look.valid()) {
            throw new Invalid(Invalid.UNSUPPORTED, "value");
        }
        return look;
    }

    private static int color(String value) throws Invalid {
        if (value == null || value.length() != 7 || value.charAt(0) != '#') {
            throw new Invalid(Invalid.UNSUPPORTED, "accent");
        }
        for (int i = 1; i < 7; i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                throw new Invalid(Invalid.UNSUPPORTED, "accent");
            }
        }
        return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
    }

    private static int level(String value) throws Invalid {
        if ("0".equals(value) || "1".equals(value) || "2".equals(value)) {
            return value.charAt(0) - '0';
        }
        throw new Invalid(Invalid.UNSUPPORTED, "level");
    }

    private static boolean flag(String value) throws Invalid {
        if ("on".equals(value)) {
            return true;
        }
        if ("off".equals(value)) {
            return false;
        }
        throw new Invalid(Invalid.UNSUPPORTED, "flag");
    }

    private static void line(StringBuilder b, String key, String value) {
        b.append(key).append(' ').append(value).append('\n');
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }

    static boolean contains(String[] values, String value) {
        return value != null && Arrays.asList(values).contains(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HorizonLook)) {
            return false;
        }
        HorizonLook l = (HorizonLook) o;
        return accent == l.accent && density == l.density && corners == l.corners && motion == l.motion
                && colorArt == l.colorArt && liveGlass == l.liveGlass && Objects.equals(backdrop, l.backdrop)
                && Objects.equals(material, l.material) && Objects.equals(sound, l.sound)
                && Objects.equals(layout, l.layout) && Objects.equals(focus, l.focus);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{accent, backdrop, material, sound, layout, focus, density, corners,
                motion, colorArt, liveGlass});
    }
}
