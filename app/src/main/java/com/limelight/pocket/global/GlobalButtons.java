package com.limelight.pocket.global;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;

/**
 * The buttons that work in every game, not just while streaming (an Android game, an
 * emulator): Back and Guide, each with a press, a double press and a hold, set to open Pocket
 * Nexus's Home, Quick Resume, the radial menu, and a few of Android's own things. Read by
 * GlobalButtonEngine, in Pocket Nexus's accessibility service (the only way an app can see
 * the buttons while another app is in front).
 */
public final class GlobalButtons {
    private GlobalButtons() {}

    public static final int BACK = 0;
    public static final int GUIDE = 1;
    public static final int BUTTON_COUNT = 2;

    public static final int TAP = 0;
    public static final int DOUBLE = 1;
    public static final int HOLD = 2;
    public static final int GESTURE_COUNT = 3;

    public static final int RADIAL_SLOTS = 8;

    /** What a button does. */
    public enum Action {
        /** What it does anyway (Back goes back). */
        PASS("pass", R.string.pocket_gb_pass),
        NONE("none", R.string.pocket_act_none),
        HOME("home", R.string.pocket_gb_home),
        QUICK_RESUME("quick", R.string.pocket_launcher_quick),
        RADIAL("radial", R.string.pocket_act_radial),
        LAST_GAME("last", R.string.pocket_gb_last_game),
        RECENTS("recents", R.string.pocket_gb_recents),
        ANDROID_HOME("androidhome", R.string.pocket_act_androidhome),
        BACK("back", R.string.pocket_button_back),
        SCREENSHOT("screenshot", R.string.pocket_gb_screenshot),
        NOTIFICATIONS("notifications", R.string.pocket_gb_notifications),
        QUICK_SETTINGS("qs", R.string.pocket_gb_quick_settings),
        PLAY_PAUSE("playpause", R.string.pocket_act_playpause),
        NEXT_TRACK("next", R.string.pocket_gb_next_track),
        VOLUME_UP("volup", R.string.pocket_act_volup),
        VOLUME_DOWN("voldown", R.string.pocket_act_voldown),
        SETTINGS("settings", R.string.pocket_launcher_settings);

        public final String id;
        public final int label;

        Action(String id, int label) {
            this.id = id;
            this.label = label;
        }

        static Action of(String id, Action fallback) {
            if (id != null) {
                for (Action a : values()) {
                    if (a.id.equals(id)) {
                        return a;
                    }
                }
            }
            return fallback;
        }
    }

    /** The actions a button can have (a radial slot: all but the pass-through). */
    public static Action[] choices(boolean radial) {
        if (!radial) {
            return Action.values();
        }
        Action[] all = Action.values();
        Action[] out = new Action[all.length - 1];
        int i = 0;
        for (Action a : all) {
            if (a != Action.PASS) {
                out[i++] = a;
            }
        }
        return out;
    }

    private static final String[] BUTTON_KEYS = {"back", "guide"};
    private static final String[] GESTURE_KEYS = {"tap", "double", "hold"};

    private static final Action[][] DEFAULTS = {
            // Back: goes back; twice: Quick Resume; held: the radial menu
            {Action.PASS, Action.QUICK_RESUME, Action.RADIAL},
            // Guide: Pocket Nexus's Home; twice: Quick Resume; held: the radial menu
            {Action.HOME, Action.QUICK_RESUME, Action.RADIAL},
    };

    private static final Action[] RADIAL_DEFAULTS = {
            Action.HOME,
            Action.QUICK_RESUME,
            Action.SCREENSHOT,
            Action.PLAY_PAUSE,
            Action.NOTIFICATIONS,
            Action.RECENTS,
            Action.QUICK_SETTINGS,
            Action.LAST_GAME,
    };

    public static final String KEY_ENABLED = "pocket_gb_enabled";
    public static final String KEY_DOUBLE_MS = "pocket_gb_double_ms";

    /** On (and the service running): the buttons work in every game. */
    public static boolean enabled(Context c) {
        return PocketPrefs.prefs(c).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context c, boolean on) {
        PocketPrefs.prefs(c).edit().putBoolean(KEY_ENABLED, on).apply();
    }

    /** How quick a double press must be (ms). */
    public static int doubleMs(Context c) {
        return PocketPrefs.prefs(c).getInt(KEY_DOUBLE_MS, 300);
    }

    public static Action get(Context c, int button, int gesture) {
        return Action.of(PocketPrefs.prefs(c).getString("pocket_gb_" + BUTTON_KEYS[button] + "_" + GESTURE_KEYS[gesture],
                null), DEFAULTS[button][gesture]);
    }

    public static void set(Context c, int button, int gesture, Action a) {
        PocketPrefs.prefs(c).edit().putString("pocket_gb_" + BUTTON_KEYS[button] + "_" + GESTURE_KEYS[gesture], a.id).apply();
    }

    public static Action radial(Context c, int slot) {
        return Action.of(PocketPrefs.prefs(c).getString("pocket_gb_radial_" + slot, null), RADIAL_DEFAULTS[slot]);
    }

    public static void setRadial(Context c, int slot, Action a) {
        PocketPrefs.prefs(c).edit().putString("pocket_gb_radial_" + slot, a.id).apply();
    }

    public static int buttonLabel(int button) {
        return button == BACK ? R.string.pocket_button_back : R.string.pocket_gb_guide;
    }

    public static int gestureLabel(int gesture) {
        switch (gesture) {
            case TAP: return R.string.pocket_gesture_tap;
            case DOUBLE: return R.string.pocket_gesture_double;
            default: return R.string.pocket_gesture_hold;
        }
    }

    /** True when Android has the service switched on (Settings, Accessibility). */
    public static boolean serviceOn(Context c) {
        try {
            String on = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (on == null) {
                return false;
            }
            ComponentName me = new ComponentName(c, com.limelight.KeyboardAccessibilityService.class);
            TextUtils.SimpleStringSplitter split = new TextUtils.SimpleStringSplitter(':');
            split.setString(on);
            for (String s : split) {
                ComponentName cn = ComponentName.unflattenFromString(s);
                if (me.equals(cn)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }
}
