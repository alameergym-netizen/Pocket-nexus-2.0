package com.limelight.pocket.streaming;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonStreamProfilesTest {
    @Test public void allProfilesRoundTrip() {
        HorizonStreamProfiles.Profile[] profiles = HorizonStreamProfiles.Profile.values();
        assertEquals(6, profiles.length);
        for (int i = 0; i < profiles.length; i++) {
            HorizonStreamProfiles.Profile p = profiles[i];
            assertEquals(i, HorizonStreamProfiles.match(p.resolution, Integer.toString(p.fps), p.wifiKbps, p.usbKbps));
        }
    }

    @Test public void manualOverridesAreCustom() {
        assertEquals(-1, HorizonStreamProfiles.match("1920x1080", "120", 20000, 40000));
    }

    @Test public void remoteProfilesUseConservativeStartingBitrates() {
        for (HorizonStreamProfiles.Profile p : HorizonStreamProfiles.Profile.values()) {
            if (p.name().startsWith("REMOTE")) {
                assertTrue(p.wifiKbps <= 20000);
            }
        }
    }
}
