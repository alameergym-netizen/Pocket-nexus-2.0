package com.limelight.pocket.ui;

import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Map;

/** Keep a press that was used by a menu out of the screen behind it, even after dismissal. */
public final class MenuKeyOwnership {
    private final Map<Long, Long> pressed = new HashMap<>();

    public boolean consume(KeyEvent event, boolean menuShowing) {
        if (!menuShowing && pressed.isEmpty()) return false;
        long key = ((long) event.getDeviceId() << 32) | ((event.getScanCode() & 0xffffL) << 16)
                | (event.getKeyCode() & 0xffffL);
        if (event.getAction() == KeyEvent.ACTION_UP) {
            Long started = pressed.get(key);
            if (started != null && started == event.getDownTime()) {
                pressed.remove(key);
                return true;
            }
            return menuShowing;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (menuShowing) {
                pressed.put(key, event.getDownTime());
                return true;
            }
            Long started = pressed.get(key);
            if (event.getRepeatCount() > 0 && started != null && started == event.getDownTime()) return true;
            pressed.remove(key);
        }
        return menuShowing;
    }
}
