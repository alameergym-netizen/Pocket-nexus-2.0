package com.limelight.pocket.ui;

import android.app.Application;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class MenuKeyOwnershipTest {
    @Test public void theMenuKeepsRepeatsAndReleaseAfterClosing() {
        MenuKeyOwnership keys = new MenuKeyOwnership();
        assertTrue(keys.consume(key(10, KeyEvent.ACTION_DOWN, 0, 1, 17), true));
        assertTrue(keys.consume(key(10, KeyEvent.ACTION_DOWN, 1, 1, 17), false));
        assertTrue(keys.consume(key(10, KeyEvent.ACTION_UP, 0, 1, 17), false));
        assertFalse(keys.consume(key(20, KeyEvent.ACTION_DOWN, 0, 1, 17), false));
        assertFalse(keys.consume(key(20, KeyEvent.ACTION_UP, 0, 1, 17), false));
    }

    @Test public void anotherDeviceOrScanCodeKeepsItsOwnRelease() {
        MenuKeyOwnership keys = new MenuKeyOwnership();
        keys.consume(key(10, KeyEvent.ACTION_DOWN, 0, 1, 17), true);
        assertFalse(keys.consume(key(10, KeyEvent.ACTION_UP, 0, 2, 17), false));
        assertFalse(keys.consume(key(10, KeyEvent.ACTION_UP, 0, 1, 18), false));
        assertTrue(keys.consume(key(10, KeyEvent.ACTION_UP, 0, 1, 17), false));
    }

    private static KeyEvent key(long started, int action, int repeats, int device, int scan) {
        return new KeyEvent(started, started + 1, action, KeyEvent.KEYCODE_BUTTON_B, repeats, 0, device, scan);
    }
}
