package com.limelight.pocket;
import android.view.InputDevice;
import android.view.KeyEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class HorizonBackKeyTest {
    private KeyEvent key(int action, int code, int scan, int flags, int source, long time) {
        return new KeyEvent(time, time + (action == KeyEvent.ACTION_UP ? 10 : 0),
                action, code, 0, 0, 3, scan, flags, source);
    }
    private KeyEvent back(int action, int scan, int flags) {
        return key(action, KeyEvent.KEYCODE_BACK, scan, flags, InputDevice.SOURCE_KEYBOARD, 100);
    }
    @Test public void physicalBackWorksAndLegacyMappingRemainsCompatible() {
        assertTrue(HorizonBackKey.matches("auto", back(0,158,0)));
        assertTrue(HorizonBackKey.matches("k:4", back(0,158,0)));
    }
    @Test public void bNeverBecomesDedicatedBackEvenWithStoredMapping() {
        KeyEvent b = key(0, KeyEvent.KEYCODE_BUTTON_B,305,0,InputDevice.SOURCE_GAMEPAD,100);
        assertFalse(HorizonBackKey.matches("k:97", b));
        assertNull(HorizonBackKey.encode(b));
        assertFalse(HorizonBackKey.matches("auto", back(0,305,0)));
        assertFalse(HorizonBackKey.matches("auto", back(0,304,0)));
    }
    @Test public void fallbackVirtualAndScanlessBackDoNotOpenControl() {
        assertFalse(HorizonBackKey.matches("auto", back(0,158,KeyEvent.FLAG_FALLBACK)));
        assertFalse(HorizonBackKey.matches("k:4", back(0,158,KeyEvent.FLAG_VIRTUAL_HARD_KEY)));
        assertFalse(HorizonBackKey.matches("auto", back(0,0,0)));
    }
    @Test public void mouseBackIsNotShortcut() {
        assertFalse(HorizonBackKey.matches("auto", key(0,4,158,0,InputDevice.SOURCE_MOUSE,100)));
    }
    @Test public void calibratedPhysicalKeyMatchesBothCodeAndScan() {
        String spec = HorizonBackKey.encode(back(0,158,0));
        assertEquals("p:4:158", spec);
        assertTrue(HorizonBackKey.matches(spec, back(0,158,0)));
        assertFalse(HorizonBackKey.matches(spec, back(0,159,0)));
        assertFalse(HorizonBackKey.matches(spec, back(0,158,KeyEvent.FLAG_FALLBACK)));
    }
    @Test public void matchingPressOnlyOpensOnRelease() {
        HorizonBackKey.Press p = new HorizonBackKey.Press();
        assertFalse(p.accept(back(1,158,0)));
        assertFalse(p.accept(back(0,158,0)));
        assertTrue(p.accept(back(1,158,0)));
        assertFalse(p.accept(back(1,158,0)));
    }
    @Test public void cancelledOrMismatchedReleaseDoesNotOpen() {
        HorizonBackKey.Press p = new HorizonBackKey.Press();
        p.accept(back(0,158,0));
        assertFalse(p.accept(back(1,158,KeyEvent.FLAG_CANCELED)));
        p.accept(back(0,158,0));
        assertFalse(p.accept(back(1,159,0)));
        p.accept(back(0,158,0));
        assertFalse(p.accept(key(1,4,158,0,InputDevice.SOURCE_KEYBOARD,200)));
    }
}
