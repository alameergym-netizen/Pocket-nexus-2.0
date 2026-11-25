package com.limelight.pocket.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.preferences.PreferenceConfiguration;

import org.junit.After;
import org.junit.Test;

public class HorizonStreamCompatibilityTest {
    @After public void clear() {
        HorizonStreamCompatibility.setArmed(false);
    }

    @Test public void onlyTheNextManualStreamConsumesTheMode() {
        HorizonStreamCompatibility.setArmed(true);
        assertFalse(HorizonStreamCompatibility.take(true, 41));
        assertFalse(HorizonStreamCompatibility.take(false, 0));
        assertTrue(HorizonStreamCompatibility.isArmed());
        assertTrue(HorizonStreamCompatibility.take(false, 41));
        assertFalse(HorizonStreamCompatibility.take(false, 41));
    }

    @Test public void modeCanBeCancelledAndLowerBitratesAreRespected() {
        HorizonStreamCompatibility.setArmed(true);
        HorizonStreamCompatibility.setArmed(false);
        assertFalse(HorizonStreamCompatibility.take(false, 41));
        assertEquals(10_000, HorizonStreamCompatibility.capBitrate(25_000));
        assertEquals(6_000, HorizonStreamCompatibility.capBitrate(6_000));
    }

    @Test public void compatibilityChangesVideoFormatAndHdrOnTheActiveConfig() {
        PreferenceConfiguration current = new PreferenceConfiguration();
        current.videoFormat = PreferenceConfiguration.FormatOption.FORCE_AV1;
        current.enableHdr = true;
        current.width = 1920;
        current.fps = 60;
        HorizonStreamCompatibility.configure(current);
        assertEquals(PreferenceConfiguration.FormatOption.FORCE_H264, current.videoFormat);
        assertFalse(current.enableHdr);
        assertEquals(1920, current.width);
        assertEquals(60f, current.fps, 0f);
    }
}
