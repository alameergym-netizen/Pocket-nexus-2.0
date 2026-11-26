package com.limelight.pocket.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HorizonAtmosphereTest {
    @Test public void realBlurOnlyOnAndroid12WithLiveGlassOn() {
        assertTrue(HorizonAtmosphere.live(31, true));
        assertTrue(HorizonAtmosphere.live(36, true));
        assertFalse("the fallback draws the same layers without a blur", HorizonAtmosphere.live(30, true));
        assertFalse("turning live glass off is honoured", HorizonAtmosphere.live(33, false));
    }

    @Test public void theVeilNeedsRoomAndTakesItsPaddingFromIt() {
        assertTrue(HorizonAtmosphere.veil(200f));
        assertFalse(HorizonAtmosphere.veil(199.9f));
        assertEquals(200f - 2 * HorizonAtmosphere.VEIL_PAD_Y - HorizonAtmosphere.VEIL_LIFT,
                HorizonAtmosphere.roomInsideVeil(200f), 0.001f);
    }

    @Test public void theTrayRunsFromAboveTheRowToAboveTheHints() {
        // A row 104 dp tall standing 99 dp up (the dock under it): the tray covers label, row and dock
        assertEquals(40, HorizonAtmosphere.trayBottom(99));
        assertEquals(99 + 104 + HorizonAtmosphere.TRAY_OVER_ROW - 40, HorizonAtmosphere.trayHeight(99, 104));
        // Its top edge is TRAY_OVER_ROW above the row's top wherever the row stands
        for (int bottom : new int[]{0, 20, 40, 99, 140}) {
            int top = HorizonAtmosphere.trayBottom(bottom) + HorizonAtmosphere.trayHeight(bottom, 90);
            assertEquals(bottom + 90 + HorizonAtmosphere.TRAY_OVER_ROW, top);
            assertTrue("never below the row", HorizonAtmosphere.trayBottom(bottom) <= bottom);
        }
    }

    @Test public void controlIsDenserWithoutABlurBehindIt() {
        int[] blurred = HorizonAtmosphere.controlPane(true);
        int[] plain = HorizonAtmosphere.controlPane(false);
        assertNotEquals(blurred[0], plain[0]);
        for (int i = 0; i < 2; i++) {
            assertTrue((plain[i] >>> 24) > (blurred[i] >>> 24));
            assertTrue("still layered (see-through) over a blur", (blurred[i] >>> 24) < 0xE0);
            assertTrue("near solid without one", (plain[i] >>> 24) >= 0xF0);
        }
    }
}
