package com.limelight.pocket.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.retro.RetroLibrary;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/** L03: one favourite mark, kept in the PC, Retro and Library stores, read back the same way. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class HorizonFavoritesTest {
    @Test public void anyMarkedWayMakesAFavorite() {
        assertFalse(HorizonFavorites.any(null));
        assertFalse(HorizonFavorites.any(Arrays.asList(false, null)));
        assertTrue(HorizonFavorites.any(Arrays.asList(null, false, true)));
    }

    @Test public void oneToggleMarksEveryProviderAndClearsThemAgain() {
        Application app = RuntimeEnvironment.getApplication();
        UniversalLibrary.Game g = new UniversalLibrary.Game("cinder", "Cinder Quest");
        HomeItem pc = new HomeItem(HomeItem.PC, "Cinder Quest", "pc-uuid", "", "", 503);
        HomeItem rom = new HomeItem(HomeItem.RETRO, "Cinder Quest", "psx", "Cinder.cue", "/roms", 0);
        HomeItem cloud = new HomeItem(HomeItem.CLOUD, "Cinder Quest", "xcloud", "cinder-quest", "", 0);
        g.ways.add(new UniversalLibrary.Way(pc, "PC", "PC", 0, UniversalLibrary.PLATFORM_PC));
        g.ways.add(new UniversalLibrary.Way(rom, "PSX", "PSX", 0, UniversalLibrary.PLATFORM_RETRO));
        g.ways.add(new UniversalLibrary.Way(cloud, "Cloud", "Cloud", 0, UniversalLibrary.PLATFORM_CLOUD));

        HorizonFavorites.set(app, g, true);
        assertTrue(g.favorite);
        assertTrue("the PC game page sees it", PocketPrefs.isFavorite(app, "pc-uuid", 503));
        assertEquals("the Retro screen sees it", Boolean.TRUE, RetroLibrary.favoriteMark(app, "psx/Cinder.cue"));
        for (UniversalLibrary.Way w : g.ways) {
            assertTrue(w.item.kind, HorizonFavorites.marked(app, w.item, null));
        }

        HorizonFavorites.set(app, g, false);
        assertFalse(g.favorite);
        assertFalse(PocketPrefs.isFavorite(app, "pc-uuid", 503));
        assertEquals("unmarked here also hides ES-DE's favourite", Boolean.FALSE,
                RetroLibrary.favoriteMark(app, "psx/Cinder.cue"));
        for (UniversalLibrary.Way w : g.ways) {
            assertFalse(w.item.kind, HorizonFavorites.marked(app, w.item, null));
        }
    }
}
