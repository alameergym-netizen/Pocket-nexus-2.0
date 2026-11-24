package com.limelight.pocket.ui;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class HorizonThemeStudioTest {
    private HorizonThemeStudio.Look look() {
        return new HorizonThemeStudio.Look(0xffa8c8e8,"art","frost","crystal","portal","ring_zoom",1,2,false,true,true);
    }

    @Test public void roundTrip() throws Exception {
        HorizonThemeStudio.Look got=HorizonThemeStudio.Look.decode(look().encode().toString());
        assertEquals(0xffa8c8e8,got.accent);
        assertEquals("portal",got.layout);
        assertEquals("crystal",got.sound);
        assertEquals(1,got.density);
        assertEquals(2,got.corners);
        assertFalse(got.motion);
        assertTrue(got.liveGlass);
    }
    @Test(expected=JSONException.class) public void rejectsUnknownVersion() throws Exception {
        HorizonThemeStudio.Look.decode(look().encode().put("version",999).toString());
    }
    @Test(expected=JSONException.class) public void rejectsUnknownLayout() throws Exception {
        HorizonThemeStudio.Look.decode(look().encode().put("layout","unknown").toString());
    }
    @Test(expected=JSONException.class) public void rejectsInvalidDensity() throws Exception {
        HorizonThemeStudio.Look.decode(look().encode().put("density",99).toString());
    }
    @Test(expected=JSONException.class) public void rejectsTranslucentAccent() throws Exception {
        HorizonThemeStudio.Look.decode(look().encode().put("accent",0x00a8c8e8).toString());
    }
}
