package com.limelight.pocket.ui;

/**
 * T08: per-workspace overrides. The Library and Nexus Control can each keep their own accent
 * on top of the applied look; everything else follows the look. An override is a stored color
 * (0: none, follow the look) and is only used when it is readable on the dark surfaces, the same
 * rule the Studio's import uses. Which workspace a screen is comes from its activity class, so
 * every view on that screen (focus, section bar, rings...) takes the override through the one
 * accent lookup, without each screen being changed.
 */
public final class HorizonWorkspaceLook {
    private HorizonWorkspaceLook() {
    }

    public static final String LIBRARY = "library";
    public static final String CONTROL = "control";
    public static final String[] WORKSPACES = {LIBRARY, CONTROL};

    /** The preference holding a workspace's accent override (an int, 0 or absent: none). */
    public static String key(String workspace) {
        return "horizon_accent_" + workspace;
    }

    /** The workspace a screen belongs to, from its activity's class name (null: no override). */
    public static String workspaceOf(String activityClass) {
        if (activityClass == null) return null;
        if (activityClass.endsWith(".library.LibraryActivity")) return LIBRARY;
        if (activityClass.endsWith(".global.ControlCenterActivity")) return CONTROL;
        return null;
    }

    /** The accent a workspace shows: its readable override, else the look's. */
    public static int accent(int lookAccent, int override) {
        return override != 0 && HorizonLook.readableAccent(override) ? 0xFF000000 | override : lookAccent;
    }

    /** The next choice when cycling: none, then each palette color, then none again. */
    public static int next(int override, int[] palette) {
        if (palette == null || palette.length == 0) return 0;
        for (int i = 0; i < palette.length; i++) {
            if ((palette[i] | 0xFF000000) == (override | 0xFF000000) && override != 0) {
                return i + 1 < palette.length ? palette[i + 1] : 0;
            }
        }
        return palette[0];
    }
}
