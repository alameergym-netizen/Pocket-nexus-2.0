package com.limelight.pocket.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HorizonSignatureTest {
    @Test public void theBottomBarCoversTheRowAndItsLabel() {
        // A row 104 dp tall standing 99 dp up (the dock under it)
        assertEquals(99 + 104 + HorizonSignature.BOTTOM_OVER_ROW, HorizonSignature.bottomBar(99, 104));
        assertEquals(HorizonSignature.BOTTOM_OVER_ROW, HorizonSignature.bottomBar(-5, -1));
    }

    @Test public void theBarsOnlyFrameAScreenWithRoomForAPicture() {
        int bar = HorizonSignature.bottomBar(99, 104);
        assertEquals(540 - HorizonSignature.TOP_BAR - bar, HorizonSignature.band(540, bar));
        assertTrue("a 540 dp handheld screen is framed", HorizonSignature.framed(540, bar));
        assertFalse("a 360 dp screen keeps its picture", HorizonSignature.framed(360, bar));
        int edge = HorizonSignature.TOP_BAR + bar + HorizonSignature.MIN_BAND;
        assertTrue(HorizonSignature.framed(edge, bar));
        assertFalse(HorizonSignature.framed(edge - 1, bar));
    }

    @Test public void theLibraryStillIsWidescreenWithinBounds() {
        assertEquals(Math.round(300 / HorizonSignature.SCOPE), HorizonSignature.stillHeight(300));
        assertEquals(72, HorizonSignature.stillHeight(100));
        assertEquals(162, HorizonSignature.stillHeight(1000));
        float aspect = 300f / HorizonSignature.stillHeight(300);
        assertEquals(HorizonSignature.SCOPE, aspect, 0.02f);
    }

    @Test public void theSlateIsNearBlackAndOpaqueEnoughToRead() {
        assertTrue((HorizonSignature.SLATE >>> 24) >= 0xF0);
        assertTrue((HorizonSignature.SLATE & 0xFFFFFF) < 0x101010);
        assertEquals(0xFF, HorizonSignature.BAR >>> 24);
        assertEquals(0, HorizonSignature.BAR_EDGE >>> 24);
    }
}
