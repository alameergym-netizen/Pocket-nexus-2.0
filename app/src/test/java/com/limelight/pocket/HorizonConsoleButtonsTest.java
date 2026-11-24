package com.limelight.pocket;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonConsoleButtonsTest {
    @Test public void backAndHomeHaveDistinctStreamingActions() {
        assertEquals("quickmenu",HorizonConsoleButtons.actions().get("pocket_btn_back_tap"));
        assertEquals("guide",HorizonConsoleButtons.actions().get("pocket_btn_home_tap"));
        assertEquals("guide",HorizonConsoleButtons.actions().get("pocket_btn_home_hold"));
        assertEquals("none",HorizonConsoleButtons.actions().get("pocket_btn_home_double"));
        assertTrue(ButtonGestures.isLive(PocketAction.GUIDE,PocketAction.NONE,PocketAction.GUIDE));
    }
    @Test public void localActionsAreNotRemoteGuide() {
        assertEquals("control",HorizonConsoleButtons.actions().get("pocket_gb_back_tap"));
        assertEquals("quick",HorizonConsoleButtons.actions().get("pocket_gb_guide_tap"));
        assertEquals("none",HorizonConsoleButtons.actions().get("pocket_gb_back_double"));
    }
    @Test public void preservesCalibrationAndOtherButtons() {
        assertEquals(12,HorizonConsoleButtons.actions().size());
        assertFalse(HorizonConsoleButtons.actions().containsKey(PocketPrefs.KEY_HOME_KEY));
        assertFalse(HorizonConsoleButtons.actions().containsKey(PocketPrefs.KEY_BACK_KEY));
        assertFalse(HorizonConsoleButtons.actions().containsKey("pocket_btn_menu_tap"));
        assertFalse(HorizonConsoleButtons.actions().containsKey("pocket_btn_m2_tap"));
    }
}
