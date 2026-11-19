package com.limelight.pocket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handheld mode and docked mode, like a Switch: when the handheld is on a TV (HDMI, a dock, a
 * USB-C display) it plays the part. The stream gets its own settings for each (resolution,
 * frame rate, bit rate: set them once while docked and they come back every time it's docked,
 * and the handheld's own come back when it's lifted out); the handheld stops buzzing (it's on
 * a desk, not in hands); the screen edges get room for TVs that crop; a TV icon sits by the
 * clock. Automatic, or held in one mode from Settings.
 */
public final class DockMode {

    public static final String KEY_MODE = "pocket_dock_mode";
    public static final String AUTO = "auto";
    public static final String HANDHELD = "handheld";
    public static final String DOCKED = "docked";
    private static final String KEY_WAS = "pocket_dock_was";
    private static final String KEY_SAFE = "pocket_dock_safe_area";
    /** The stream settings each mode keeps for itself. */
    private static final String[] STREAM_KEYS = {"list_resolution", "list_fps", "seekbar_bitrate_kbps",
            "edit_diy_w_h", "custom_refresh_rate", "edit_diy_bitrate", "checkbox_enable_hdr",
            "list_video_scale_mode", "seekbar_resolution_scale_factor"};

    public interface Listener {
        void onDockChanged(boolean docked);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static boolean watching;
    private static boolean hdmi;
    private static volatile boolean current;

    /** Docked as last seen (no lookups: for the haptics, many times a second). */
    public static boolean docked() {
        return current;
    }

    private DockMode() {
    }

    public static String mode(Context c) {
        return PocketPrefs.prefs(c).getString(KEY_MODE, AUTO);
    }

    public static void setMode(Context c, String mode) {
        PocketPrefs.prefs(c).edit().putString(KEY_MODE, mode).apply();
        check(c, true);
    }

    /** True when it's docked now (on a TV, or held docked). */
    public static boolean isDocked(Context c) {
        String m = mode(c);
        if (DOCKED.equals(m)) {
            return true;
        }
        if (HANDHELD.equals(m)) {
            return false;
        }
        return onTv(c);
    }

    /** A TV or monitor is plugged in: HDMI, or a second screen (a dock, USB-C). */
    public static boolean onTv(Context c) {
        if (hdmi) {
            return true;
        }
        try {
            Intent sticky = c.getApplicationContext().registerReceiver(null,
                    new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
            if (sticky != null && sticky.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1) {
                return true;
            }
        } catch (RuntimeException ignored) {
        }
        DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null) {
            for (Display d : dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
                if (d.getDisplayId() != Display.DEFAULT_DISPLAY && (d.getFlags() & Display.FLAG_PRIVATE) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Room at the screen's edges for TVs that crop the picture (in dp; 0 handheld). */
    public static int safeMarginDp(Context c) {
        return isDocked(c) ? PocketPrefs.prefs(c).getInt(KEY_SAFE, 24) : 0;
    }

    public static void addListener(Listener l) {
        if (!LISTENERS.contains(l)) {
            LISTENERS.add(l);
        }
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    /** Starts watching for a TV being plugged in or out (once, for the app's life). */
    public static void watch(Context context) {
        if (watching) {
            return;
        }
        watching = true;
        final Context app = context.getApplicationContext();
        try {
            app.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    hdmi = i.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1;
                    MAIN.postDelayed(() -> check(app, false), 600);
                }
            }, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
        } catch (RuntimeException ignored) {
        }
        DisplayManager dm = (DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null) {
            dm.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int id) {
                    MAIN.postDelayed(() -> check(app, false), 600);
                }

                @Override
                public void onDisplayRemoved(int id) {
                    MAIN.postDelayed(() -> check(app, false), 600);
                }

                @Override
                public void onDisplayChanged(int id) {
                }
            }, MAIN);
        }
        check(app, false);
    }

    /**
     * Puts the right mode in place when it changed since last time: each mode's stream settings
     * swapped in, the listeners (the screens) told, a word on screen.
     */
    public static void check(Context c, boolean fromSettings) {
        SharedPreferences p = PocketPrefs.prefs(c);
        boolean now = isDocked(c);
        current = now;
        boolean was = p.getBoolean(KEY_WAS, false);
        if (now == was) {
            return;
        }
        SharedPreferences.Editor e = p.edit();
        swap(p, e, was ? "pocket_dk_" : "pocket_hh_", now ? "pocket_dk_" : "pocket_hh_");
        e.putBoolean(KEY_WAS, now);
        e.apply();
        PocketSounds.play(c, now ? PocketSounds.OPEN : PocketSounds.CLOSE);
        if (!fromSettings) {
            try {
                Toast.makeText(c.getApplicationContext(), now ? R.string.pocket_dock_now_docked
                        : R.string.pocket_dock_now_handheld, Toast.LENGTH_LONG).show();
            } catch (RuntimeException ignored) {
            }
        }
        for (Listener l : new ArrayList<>(LISTENERS)) {
            l.onDockChanged(now);
        }
    }

    /** Keeps the leaving mode's stream settings and brings back the arriving mode's. */
    private static void swap(SharedPreferences p, SharedPreferences.Editor e, String leaving, String arriving) {
        Map<String, ?> all = p.getAll();
        for (String k : STREAM_KEYS) {
            Object cur = all.get(k);
            put(e, leaving + k, cur);
            if (all.containsKey(arriving + k)) {
                put(e, k, all.get(arriving + k));
            }
            else if (!all.containsKey(arriving + "set")) {
                // First time in this mode: it starts from the other's settings (and keeps its own after)
                put(e, k, cur);
            }
        }
        e.putBoolean(leaving + "set", true);
    }

    private static void put(SharedPreferences.Editor e, String k, Object v) {
        if (v == null) {
            e.remove(k);
        }
        else if (v instanceof Boolean) {
            e.putBoolean(k, (Boolean) v);
        }
        else if (v instanceof Integer) {
            e.putInt(k, (Integer) v);
        }
        else if (v instanceof Long) {
            e.putLong(k, (Long) v);
        }
        else if (v instanceof Float) {
            e.putFloat(k, (Float) v);
        }
        else if (v instanceof String) {
            e.putString(k, (String) v);
        }
    }
}
