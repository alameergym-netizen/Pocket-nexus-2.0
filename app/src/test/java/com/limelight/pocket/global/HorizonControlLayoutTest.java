package com.limelight.pocket.global;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonControlLayoutTest {
    @Test public void landscapeHandheldUsesTwoWorkspaces() {
        HorizonControlLayout p = HorizonControlLayout.forWindow(850, 480);
        assertTrue(p.split);
        assertEquals(3, p.columns);
        assertTrue(p.width + 2 * p.margin <= 850);
        assertTrue(p.height + 2 * p.margin <= 480);
    }
    @Test public void portraitUsesScrollableStackAndTwoColumns() {
        HorizonControlLayout p = HorizonControlLayout.forWindow(360, 720);
        assertFalse(p.split);
        assertEquals(2, p.columns);
        assertTrue(p.height <= 560);
    }
    @Test public void shortLandscapeKeepsControlsWithinWindow() {
        HorizonControlLayout p = HorizonControlLayout.forWindow(640, 280);
        assertFalse(p.split);
        assertEquals(3, p.columns);
        assertTrue(p.height + 2 * p.margin <= 280);
    }
    @Test public void desktopAndTabletDoNotStretchWithoutLimit() {
        HorizonControlLayout p = HorizonControlLayout.forWindow(1920, 1080);
        assertTrue(p.split);
        assertEquals(880, p.width);
        assertEquals(560, p.height);
    }
}
