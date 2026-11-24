package com.limelight.pocket;

import android.view.InputDevice;
import android.view.KeyEvent;

/** Distinguishes a dedicated system shortcut from B, synthesized navigation and mouse Back. */
public final class HorizonBackKey {
    private HorizonBackKey() {}

    /** Prevent stale/legacy shortcut mappings from stealing a normal face-button press. */
    public static boolean reservedForNavigation(KeyEvent e) {
        int key = e.getKeyCode(), scan = e.getScanCode();
        // Linux BTN_SOUTH/EAST/C/NORTH/WEST/Z are face keys even when a keylayout aliases them.
        return key == KeyEvent.KEYCODE_BUTTON_B || (scan >= 304 && scan <= 309)
                || (e.getFlags() & (KeyEvent.FLAG_FALLBACK | KeyEvent.FLAG_VIRTUAL_HARD_KEY)) != 0;
    }

    private static boolean eligible(KeyEvent e) {
        int source = e.getSource();
        if (reservedForNavigation(e)) return false;
        return (source & InputDevice.SOURCE_MOUSE) != InputDevice.SOURCE_MOUSE
                && source != InputDevice.SOURCE_MOUSE_RELATIVE;
    }

    public static String encode(KeyEvent e) {
        if (!eligible(e)) return null;
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getScanCode() <= 0) return null;
        if (e.getScanCode() > 0) return "p:" + e.getKeyCode() + ":" + e.getScanCode();
        return e.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN ? null : "k:" + e.getKeyCode();
    }

    public static boolean matches(String spec, KeyEvent e) {
        if (!eligible(e) || spec == null) return false;
        if ("auto".equals(spec) || ("k:" + KeyEvent.KEYCODE_BACK).equals(spec)) {
            // No physical scan: Android navigation, injection or ambiguous alias; fail to normal Back.
            return e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getScanCode() > 0
                    && e.getDeviceId() >= 0;
        }
        if (spec.startsWith("p:")) return spec.equals("p:" + e.getKeyCode() + ":" + e.getScanCode())
                && e.getScanCode() > 0;
        if (spec.startsWith("s:")) return e.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN
                && e.getScanCode() > 0 && spec.equals("s:" + e.getScanCode());
        return e.getKeyCode() != KeyEvent.KEYCODE_UNKNOWN && spec.equals("k:" + e.getKeyCode());
    }

    static String describe(String spec) {
        if (spec == null || !spec.startsWith("p:")) return null;
        String[] p = spec.split(":");
        if (p.length != 3) return null;
        try {
            return KeyEvent.keyCodeToString(Integer.parseInt(p[1])).replace("KEYCODE_", "")
                    + " / SCAN " + Integer.parseInt(p[2]);
        } catch (NumberFormatException e) { return null; }
    }

    /** Never open a panel on an orphan, mismatched or cancelled key release. */
    public static final class Press {
        private int key, scan, device;
        private long downTime;
        private boolean armed;
        public void clear() { armed = false; }
        public boolean accept(KeyEvent e) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) {
                    key = e.getKeyCode(); scan = e.getScanCode(); device = e.getDeviceId();
                    downTime = e.getDownTime(); armed = true;
                }
                return false;
            }
            if (e.getAction() != KeyEvent.ACTION_UP) return false;
            boolean match = armed && !e.isCanceled() && key == e.getKeyCode()
                    && scan == e.getScanCode() && device == e.getDeviceId() && downTime == e.getDownTime();
            clear();
            return match;
        }
    }
}
