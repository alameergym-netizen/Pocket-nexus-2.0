package com.limelight.pocket.ui;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyStyleTest {
    @Test
    public void onePixelOfTheLookIsAWholeNumberOfDevicePixels() {
        assertEquals(1, LegacyStyle.unit(0.5f));
        assertEquals(2, LegacyStyle.unit(1f));
        assertEquals(3, LegacyStyle.unit(2f));
        assertEquals(4, LegacyStyle.unit(2.75f));
        assertEquals(5, LegacyStyle.unit(3f));
    }

    private static boolean covered(int[][] edges, int x, int y) {
        for (int[] e : edges) {
            if (x >= e[0] && x < e[2] && y >= e[1] && y < e[3]) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void framesHaveSteppedCornersAndClosedEdges() {
        int w = 40, h = 24, u = 2;
        int[][] edges = LegacyStyle.steppedEdges(w, h, u);
        // The outermost corner pixels stay open: the frame is stepped, not rounded or square
        assertFalse(covered(edges, 0, 0));
        assertFalse(covered(edges, w - 1, 0));
        assertFalse(covered(edges, 0, h - 1));
        assertFalse(covered(edges, w - 1, h - 1));
        // Edges and the stepped corner pixels are drawn
        assertTrue(covered(edges, w / 2, 0));
        assertTrue(covered(edges, w / 2, h - 1));
        assertTrue(covered(edges, 0, h / 2));
        assertTrue(covered(edges, w - 1, h / 2));
        assertTrue(covered(edges, u, u));
        assertTrue(covered(edges, w - u - 1, h - u - 1));
        // Nothing is drawn outside the bounds
        for (int[] e : edges) {
            assertTrue(e[0] >= 0 && e[1] >= 0 && e[2] <= w && e[3] <= h && e[0] < e[2] && e[1] < e[3]);
        }
    }

    @Test
    public void tinyFramesFallBackToAFilledBlock() {
        int[][] edges = LegacyStyle.steppedEdges(5, 5, 2);
        assertEquals(1, edges.length);
        assertTrue(covered(edges, 0, 0));
    }

    @Test
    public void legacyPresetIsRecognisedOnlyWithItsOwnLayout() {
        int i = PocketTheme.matchingPreset(0xFFE0C98A, PocketPrefs.BACKDROP_BLACK, false, "retro",
                "grid_covers", PocketPrefs.FOCUS_RING);
        assertEquals(R.string.horizon_theme_legacy, PocketTheme.THEMES[i].name);
        assertEquals(-1, PocketTheme.matchingPreset(0xFFE0C98A, PocketPrefs.BACKDROP_BLACK, false, "retro",
                "tiles", PocketPrefs.FOCUS_RING));
    }
}
