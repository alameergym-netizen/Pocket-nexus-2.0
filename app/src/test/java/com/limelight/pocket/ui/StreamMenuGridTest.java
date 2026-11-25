package com.limelight.pocket.ui;

import android.app.Application;
import android.view.KeyEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class, qualifiers = "w960dp-h540dp-land")
public class StreamMenuGridTest {
    @Test public void directionalNavigationKeepsTheTileSelectedWhenReturningFromSettings() {
        StreamMenuView menu = new StreamMenuView(RuntimeEnvironment.getApplication());
        AtomicInteger used = new AtomicInteger(-1);
        List<StreamMenuView.Item> actions = actions(used);
        actions.set(9, StreamMenuView.Item.submenu("Settings", () -> menu.push("Settings",
                Collections.singletonList(StreamMenuView.Item.action("Apply", () -> used.set(99)))));
        menu.showGrid("Game Assist", null, null, actions);

        press(menu, KeyEvent.KEYCODE_DPAD_DOWN);
        press(menu, KeyEvent.KEYCODE_DPAD_DOWN);
        press(menu, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(tile(menu, "Settings").isSelected());
        press(menu, KeyEvent.KEYCODE_BUTTON_A);
        assertTrue(menu.isShowing());
        assertEquals(-1, used.get());
        press(menu, KeyEvent.KEYCODE_BUTTON_B);
        press(menu, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(tile(menu, "Action 10").isSelected());
        press(menu, KeyEvent.KEYCODE_BUTTON_A);
        assertEquals(10, used.get());
        assertFalse(menu.isShowing());
    }

    @Test @Config(qualifiers = "w360dp-h360dp-land")
    public void compactLayoutUsesTwoColumnsAndTouchActivatesTheTappedAction() {
        StreamMenuView menu = new StreamMenuView(RuntimeEnvironment.getApplication());
        AtomicInteger used = new AtomicInteger(-1);
        menu.showGrid("Game Assist", null, null, actions(used));
        press(menu, KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(tile(menu, "Action 2").isSelected());
        tile(menu, "Action 7").performClick();
        assertEquals(7, used.get());
        assertFalse(menu.isShowing());
    }

    private static List<StreamMenuView.Item> actions(AtomicInteger used) {
        List<StreamMenuView.Item> items = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            final int index = i;
            items.add(StreamMenuView.Item.action("Action " + i, () -> used.set(index)));
        }
        return items;
    }

    private static View tile(StreamMenuView menu, String label) {
        ArrayList<View> found = new ArrayList<>();
        menu.findViewsWithText(found, label, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return found.get(0);
    }

    private static void press(StreamMenuView menu, int key) {
        assertTrue(menu.handleKey(new KeyEvent(KeyEvent.ACTION_DOWN, key)));
        assertTrue(menu.handleKey(new KeyEvent(KeyEvent.ACTION_UP, key)));
    }
}
