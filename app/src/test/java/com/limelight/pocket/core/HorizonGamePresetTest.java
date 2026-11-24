package com.limelight.pocket.core;

import com.limelight.pocket.GameProfile;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonGamePresetTest {
    @Test public void everyPresetMatchesItsExactValues() {
        for (HorizonGamePreset p : HorizonGamePreset.values()) {
            assertSame(p, HorizonGamePreset.match(p.resolution, p.fps, p.kbps));
        }
    }
    @Test public void noPresetClaimsCustomOrDefaultSettings() {
        assertNull(HorizonGamePreset.match("", 0, 0));
        assertNull(HorizonGamePreset.match(null, 60, 10000));
        assertNull(HorizonGamePreset.match("1920x1080", 120, 25000));
    }
    @Test public void valuesRemainEditableInExistingGameSettings() {
        for (HorizonGamePreset p : HorizonGamePreset.values()) {
            assertTrue(GameProfile.indexOf(GameProfile.RESOLUTIONS, p.resolution) > 0);
            assertTrue(GameProfile.indexOf(GameProfile.FPS, p.fps) > 0);
            assertTrue(GameProfile.indexOf(GameProfile.BITRATES_MBPS, p.kbps / 1000) > 0);
        }
    }
    @Test public void remoteOptionsAreConservativeStartingPoints() {
        assertTrue(HorizonGamePreset.REMOTE_SAVER.kbps < HorizonGamePreset.REMOTE_BALANCED.kbps);
        assertTrue(HorizonGamePreset.REMOTE_HIGH_REFRESH.kbps < HorizonGamePreset.LOCAL_HIGH_REFRESH.kbps);
    }
}
