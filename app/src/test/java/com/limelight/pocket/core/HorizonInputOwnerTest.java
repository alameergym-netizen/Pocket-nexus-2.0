package com.limelight.pocket.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonInputOwnerTest {
    @Test public void visibleStreamKeepsOwnershipThroughDialogFocusLoss() {
        assertTrue(HorizonInputOwner.streamOwnsInput(true, false));
    }
    @Test public void observedStreamWindowProtectsDuringStartup() {
        assertTrue(HorizonInputOwner.streamOwnsInput(false, true));
    }
    @Test public void externalAppAfterStreamStopAllowsGlobalShortcuts() {
        assertFalse(HorizonInputOwner.streamOwnsInput(false, false));
    }
    @Test public void onlyExactStreamClassCountsNotOtherGameScreens() {
        assertTrue(HorizonInputOwner.isStreamClass("com.limelight.Game"));
        assertTrue(HorizonInputOwner.isStreamClass(".Game"));
        assertFalse(HorizonInputOwner.isStreamClass("com.limelight.pocket.GamePageActivity"));
        assertFalse(HorizonInputOwner.isStreamClass(null));
    }
}
