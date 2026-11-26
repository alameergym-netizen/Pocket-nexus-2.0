package com.limelight.pocket;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** S10: Android's "Remove animations" stills every screen, without changing the user's own setting. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class PocketMotionPrefsTest {
    @Test public void systemRemoveAnimationsWinsButKeepsTheUsersChoice() {
        Application app = RuntimeEnvironment.getApplication();
        Settings.Global.putFloat(app.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        assertTrue(PocketPrefs.animationsEnabled(app));

        Settings.Global.putFloat(app.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        assertFalse("reduced motion is honoured", PocketPrefs.animationsEnabled(app));
        assertTrue("the user's own setting is untouched", PocketPrefs.animationsPreferred(app));
        assertFalse(PocketPrefs.systemMotionAllowed(app));

        Settings.Global.putFloat(app.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0.5f);
        assertTrue(PocketPrefs.animationsEnabled(app));
        PocketPrefs.prefs(app).edit().putBoolean(PocketPrefs.KEY_ANIMATIONS, false).commit();
        assertFalse("the user's Off still stills everything", PocketPrefs.animationsEnabled(app));
    }
}
