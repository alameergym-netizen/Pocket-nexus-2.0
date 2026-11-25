package com.limelight.pocket.updates;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.limelight.pocket.ui.StreamMenuView;

import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ReleaseCheckMenuTest {
    @Test public void resultReplacesOnlyTheOriginalVisiblePage() {
        StreamMenuView menu = new StreamMenuView(RuntimeEnvironment.getApplication());
        List<StreamMenuView.Item> loading = row("Checking GitHub…");
        List<StreamMenuView.Item> result = row("release/v2");
        menu.show("Sources", null, null, row("Source"));
        menu.push("Release", loading);
        assertTrue(contains(menu, "Checking GitHub…"));
        menu.replaceCurrentPage(loading, result);
        assertTrue(contains(menu, "release/v2"));

        menu.show("Settings", null, null, row("Unrelated setting"));
        menu.replaceCurrentPage(loading, result);
        assertTrue(contains(menu, "Unrelated setting"));
        assertFalse(contains(menu, "release/v2"));
    }

    private static List<StreamMenuView.Item> row(String value) {
        return Collections.singletonList(StreamMenuView.Item.note(value));
    }

    private static boolean contains(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) {
                if (contains(parent.getChildAt(i), label)) return true;
            }
        }
        return false;
    }
}
