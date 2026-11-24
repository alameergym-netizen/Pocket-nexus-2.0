package com.limelight.pocket.library;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonLibraryLayoutTest {
    @Test public void handheldLandscapeUsesSeparateDetailsAndGrid() {
        HorizonLibraryLayout s = HorizonLibraryLayout.forWindow(850, 480);
        assertTrue(s.split);
        assertTrue(s.columns >= 4);
        assertTrue(s.detailWidth <= 300);
        assertTrue(s.artHeight <= 162);
    }
    @Test public void shortLandscapeUsesCompactDetails() {
        HorizonLibraryLayout s = HorizonLibraryLayout.forWindow(640, 280);
        assertFalse(s.split);
        assertEquals(0, s.artHeight);
        assertEquals(4, s.columns);
    }
    @Test public void narrowScreenDoesNotForceFourColumns() {
        HorizonLibraryLayout s = HorizonLibraryLayout.forWindow(360, 720);
        assertFalse(s.split);
        assertEquals(2, s.columns);
    }
    @Test public void hugeDisplayHasBoundedDetailAndGridColumns() {
        HorizonLibraryLayout s = HorizonLibraryLayout.forWindow(2560, 1440);
        assertTrue(s.split);
        assertEquals(300, s.detailWidth);
        assertEquals(9, s.columns);
    }
}
