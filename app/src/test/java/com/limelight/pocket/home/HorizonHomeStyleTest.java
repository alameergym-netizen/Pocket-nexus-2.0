package com.limelight.pocket.home;
import org.junit.Test;
import static org.junit.Assert.*;
public class HorizonHomeStyleTest {
    @Test public void lightAccentKeepsOpaqueReadablePlayFace() {
        assertEquals(0xFF9E9E9E,HorizonHomeStyle.lightAccent(0xFF000000,62));
        assertEquals(0xFFFFFFFF,HorizonHomeStyle.lightAccent(0xFF000000,100));
        assertEquals(0xFF123456,HorizonHomeStyle.lightAccent(0x00123456,-5));
    }
    @Test public void narrowHeaderOmitsBrandBeforeStatusClips() {
        assertEquals(0,HorizonHomeStyle.brandMode(640));
        assertEquals(1,HorizonHomeStyle.brandMode(768));
        assertEquals(2,HorizonHomeStyle.brandMode(960));
    }
    @Test public void summaryReservedForSpaciousShelf() {
        assertTrue(HorizonHomeStyle.showSummary(768,432,true,false));
        assertFalse(HorizonHomeStyle.showSummary(640,360,true,false));
        assertFalse(HorizonHomeStyle.showSummary(960,540,true,true));
        assertFalse(HorizonHomeStyle.showSummary(960,540,false,false));
    }
}
