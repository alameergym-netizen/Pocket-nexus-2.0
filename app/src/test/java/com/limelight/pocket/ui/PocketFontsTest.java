package com.limelight.pocket.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.limelight.pocket.PocketPrefs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** T09: the typeface choice is kept, unknown values fall back to Inter, and screens learn of a change. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class PocketFontsTest {
    @Test public void choosingATypefaceKeepsItAndTellsOpenScreens() {
        Application app = RuntimeEnvironment.getApplication();
        assertEquals(PocketFonts.INTER, PocketFonts.chosen(app));
        int before = PocketFonts.generation();
        PocketFonts.choose(app, PocketFonts.SYSTEM);
        assertEquals(PocketFonts.SYSTEM, PocketFonts.chosen(app));
        assertTrue("open screens rebuild", PocketFonts.generation() > before);
        assertNotNull(PocketFonts.bold());
        assertNotNull(PocketFonts.display());
        PocketFonts.choose(app, PocketFonts.CONDENSED);
        assertEquals(PocketFonts.CONDENSED, PocketFonts.chosen(app));
        PocketPrefs.prefs(app).edit().putString(PocketFonts.KEY, "comic").commit();
        assertEquals("unknown values read as Inter", PocketFonts.INTER, PocketFonts.chosen(app));
        PocketFonts.choose(app, PocketFonts.INTER);
        assertNotNull(PocketFonts.regular());
    }
}
