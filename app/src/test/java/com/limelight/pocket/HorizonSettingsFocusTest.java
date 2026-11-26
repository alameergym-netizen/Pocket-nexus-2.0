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
                .putBoolean(PocketPrefs.KEY_FIRST_RUN_DONE, true)
                .putBoolean(PocketPrefs.KEY_ONBOARDING_PENDING, false)
                .putInt("pocket_defaults_version", 4)
                .putBoolean(PocketPrefs.KEY_ANIMATIONS, false)
                .putBoolean(PocketPrefs.KEY_UI_SOUNDS, false)
                .putBoolean(PocketPrefs.KEY_HAPTICS, false).commit();
        java.util.Timer trace = new java.util.Timer("settings-fixture-stacks", true);
        trace.schedule(new java.util.TimerTask() {
            @Override public void run() {
                for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                    System.out.println("SETTINGS THREAD " + entry.getKey().getName() + " " + entry.getKey().getState());
                    for (StackTraceElement frame : entry.getValue()) System.out.println("    at " + frame);
                }
            }
        }, 20000);
        System.out.println("Settings fixture: creating the view tree");
        ActivityController<QuickSettingsActivity> controller = Robolectric.buildActivity(QuickSettingsActivity.class).create();
        System.out.println("Settings fixture: Activity created");
        try {
            QuickSettingsActivity activity = controller.get();
            // Exercise the real view/focus tree without attaching an indefinitely
            // animated native window to Robolectric's full-surface renderer.
            View decor = activity.getWindow().getDecorView();
            decor.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
            decor.layout(0, 0, 1920, 1080);
            System.out.println("Settings fixture: layout complete");
            LinearLayout tabs = activity.findViewById(R.id.pocketTabs);
            LinearLayout rows = activity.findViewById(R.id.pocketRows);
            tabs.getChildAt(0).requestFocusFromTouch();
            // Repeated touch selections replace the currently focused row subtree.
            // Android may move that focus to the first tab while removing it.
            for (int i = 0; i < 3; i++) {
                System.out.println("Settings fixture: selecting Streaming, iteration " + i);
                tabs.getChildAt(3).performClick();
                assertTrue(tabs.getChildAt(3).isSelected());
                assertFalse(tabs.getChildAt(0).isSelected());
                assertTrue(contains(rows, activity.getString(R.string.horizon_scene_display_cap)));
                assertTrue(contains(rows, activity.getString(R.string.horizon_eq_title)));
                tabs.getChildAt(5).performClick();
                assertTrue(tabs.getChildAt(5).isSelected());
            }
        } finally { trace.cancel(); controller.destroy(); }
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
