package com.limelight.pocket.device;
import org.junit.Test;
import static org.junit.Assert.*;
import com.limelight.binding.audio.HorizonAudioRoute;

public class HorizonBudsPolicyTest {
    @Test public void explicitNamesOnly() {
        assertTrue(HorizonBudsPolicy.matches7i("HUAWEI FreeBuds 7i"));
        assertTrue(HorizonBudsPolicy.matches7i("freebuds-7i"));
        assertFalse(HorizonBudsPolicy.matches7i("FreeBuds 7"));
        assertFalse(HorizonBudsPolicy.matches7i("FreeBuds 7i fake"));
        assertFalse(HorizonBudsPolicy.matches7i(null));
    }
    @Test public void capabilitiesArePositiveSortedAndDeduplicated() {
        assertEquals("44100, 48000",HorizonBudsPolicy.positiveValues(new int[]{48000,0,44100,48000,-1}));
        assertEquals("",HorizonBudsPolicy.positiveValues(null));
        assertEquals("",HorizonBudsPolicy.positiveValues(new int[0]));
    }
    @Test public void buffersDoNotInventLatency() {
        assertEquals(10.0,HorizonAudioRoute.bufferMs(480,48000),0.001);
        assertEquals(-1,HorizonAudioRoute.bufferMs(-1,48000),0.001);
        assertEquals(-1,HorizonAudioRoute.bufferMs(480,0),0.001);
    }
}
