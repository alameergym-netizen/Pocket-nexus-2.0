package com.limelight.pocket.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HorizonWorkspaceLookTest {
    @Test
    public void screensMapToTheirWorkspace() {
        assertEquals(HorizonWorkspaceLook.LIBRARY,
                HorizonWorkspaceLook.workspaceOf("com.limelight.pocket.library.LibraryActivity"));
        assertEquals(HorizonWorkspaceLook.CONTROL,
                HorizonWorkspaceLook.workspaceOf("com.limelight.pocket.global.ControlCenterActivity"));
        assertNull(HorizonWorkspaceLook.workspaceOf("com.limelight.pocket.home.HomeActivity"));
        assertNull(HorizonWorkspaceLook.workspaceOf("com.limelight.pocket.QuickSettingsActivity"));
        assertNull(HorizonWorkspaceLook.workspaceOf(null));
    }

    @Test
    public void aReadableOverrideWinsAndNoneOrUnreadableFollowsTheLook() {
        int look = 0xFFFFFFFF;
        assertEquals(0xFF86F0C8, HorizonWorkspaceLook.accent(look, 0xFF86F0C8));
        assertEquals("none", look, HorizonWorkspaceLook.accent(look, 0));
        assertEquals("unreadable on dark surfaces", look, HorizonWorkspaceLook.accent(look, 0xFF101010));
    }

    @Test
    public void cyclingGoesThroughThePaletteAndBackToNone() {
        int[] palette = {0xFFFFFFFF, 0xFF9FD8FF, 0xFF86F0C8};
        assertEquals(0xFFFFFFFF, HorizonWorkspaceLook.next(0, palette));
        assertEquals(0xFF9FD8FF, HorizonWorkspaceLook.next(0xFFFFFFFF, palette));
        assertEquals(0, HorizonWorkspaceLook.next(0xFF86F0C8, palette));
        assertEquals("an unknown color restarts the cycle", 0xFFFFFFFF, HorizonWorkspaceLook.next(0xFF123456, palette));
        assertEquals(0, HorizonWorkspaceLook.next(0, new int[0]));
    }
}
