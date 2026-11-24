package com.limelight.pocket.home;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonHomeWorkspaceTest {
    @Test public void regionsFitLandscapeAndLeaveRoomForDock() {
        for (int w : new int[]{640, 720, 850, 960, 1920}) {
            HorizonHomeWorkspace p = HorizonHomeWorkspace.forWindow(w, 480);
            assertEquals(w, p.gridWidth + p.detailWidth + 2*p.margin + 12);
            assertEquals(480, p.bodyTop + p.bodyHeight + 100);
            assertTrue(p.columns * (p.tile + 20) <= p.gridWidth - 12);
        }
    }
    @Test public void shortWindowUsesExistingGridFallback() {
        assertFalse(HorizonHomeWorkspace.supported(640, 320));
        assertFalse(HorizonHomeWorkspace.supported(500, 480));
        assertTrue(HorizonHomeWorkspace.supported(850, 480));
    }
    @Test public void compactSupportedWindowSuppressesDescription() {
        HorizonHomeWorkspace p = HorizonHomeWorkspace.forWindow(640, 360);
        assertEquals(0, p.descriptionLines);
        assertTrue(p.logoHeight < 80);
    }
    @Test public void largeWindowBoundsDetailAndTile() {
        HorizonHomeWorkspace p = HorizonHomeWorkspace.forWindow(2560, 1440);
        assertEquals(400, p.detailWidth);
        assertEquals(7, p.columns);
        assertEquals(102, p.tile);
    }
}
