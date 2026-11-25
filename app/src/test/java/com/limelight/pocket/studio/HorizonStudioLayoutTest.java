package com.limelight.pocket.studio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HorizonStudioLayoutTest {
    @Test
    public void wideHandheldPlacesTheListBesideThePreview() {
        HorizonStudioLayout l = HorizonStudioLayout.forWindow(960, 540, 1f);
        assertTrue(l.split);
        assertEquals(384, l.listWidth);
        assertEquals(20, l.padding);
    }

    @Test
    public void compactLandscapeStillSplitsWithRoomForThePreview() {
        HorizonStudioLayout l = HorizonStudioLayout.forWindow(640, 360, 1f);
        assertTrue(l.split);
        assertTrue(l.listWidth >= 250);
        assertTrue(640 - 2 * l.padding - l.listWidth >= 240);
    }

    @Test
    public void largerTextWidensTheList() {
        int normal = HorizonStudioLayout.forWindow(960, 540, 1f).listWidth;
        int large = HorizonStudioLayout.forWindow(960, 540, 1.3f).listWidth;
        assertTrue(large > normal);
        assertTrue(large <= 440);
    }

    @Test
    public void narrowWindowsStackThePreviewAboveTheList() {
        HorizonStudioLayout l = HorizonStudioLayout.forWindow(420, 800, 1f);
        assertFalse(l.split);
        assertEquals(300, l.previewHeight);
        assertEquals(12, l.padding);
        assertEquals(140, HorizonStudioLayout.forWindow(500, 300, 1f).previewHeight);
    }

    @Test
    public void textOnAccentKeepsContrast() {
        assertEquals(0xFF0B0B0D, HorizonStudioLayout.textOn(0xFFFFFFFF));
        assertEquals(0xFF0B0B0D, HorizonStudioLayout.textOn(0xFF3D8BFF));
        assertEquals(0xFF0B0B0D, HorizonStudioLayout.textOn(0xFF2ECC40));
        assertEquals(0xFFFFFFFF, HorizonStudioLayout.textOn(0xFF202020));
        assertEquals(0xFFFFFFFF, HorizonStudioLayout.textOn(0xFF5B3CC4));
    }
}
