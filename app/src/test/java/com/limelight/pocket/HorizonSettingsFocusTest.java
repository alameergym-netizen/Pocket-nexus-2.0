package com.limelight.pocket;

import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 33, application = Application.class, qualifiers = "w960dp-h540dp-land")
public class HorizonSettingsFocusTest {
    @Test(timeout = 60000) public void rebuildingFocusedRowsKeepsTheExplicitTabSelected() {
        PocketPrefs.prefs(RuntimeEnvironment.getApplication()).edit()
                .putBoolean(PocketPrefs.KEY_ANIMATIONS, false)
                .putBoolean(PocketPrefs.KEY_UI_SOUNDS, false)
                .putBoolean(PocketPrefs.KEY_HAPTICS, false).commit();
        ActivityController<QuickSettingsActivity> controller = Robolectric.buildActivity(QuickSettingsActivity.class).setup().visible();
        try {
            QuickSettingsActivity activity = controller.get();
            LinearLayout tabs = activity.findViewById(R.id.pocketTabs);
            LinearLayout rows = activity.findViewById(R.id.pocketRows);
            tabs.getChildAt(0).requestFocusFromTouch();
            // Repeated touch selections replace the currently focused row subtree.
            // Android may move that focus to the first tab while removing it.
            for (int i = 0; i < 3; i++) {
                tabs.getChildAt(3).performClick();
                assertTrue(tabs.getChildAt(3).isSelected());
                assertFalse(tabs.getChildAt(0).isSelected());
                assertTrue(contains(rows, activity.getString(R.string.horizon_scene_display_cap)));
                assertTrue(contains(rows, activity.getString(R.string.horizon_eq_title)));
                tabs.getChildAt(5).performClick();
                assertTrue(tabs.getChildAt(5).isSelected());
            }
        } finally { controller.pause().stop().destroy(); }
    }

    private static boolean contains(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (contains(group.getChildAt(i), text)) return true;
        }
        return false;
    }
}
