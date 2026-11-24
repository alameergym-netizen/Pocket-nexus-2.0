package com.limelight.pocket.streaming;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonStreamSafetyTest {
    @Test public void ordinaryDeviceStatePreservesTheSelectedBitrate() {
        HorizonStreamSafety.Decision decision = HorizonStreamSafety.limit(80000, false,
                HorizonStreamSafety.Thermal.UNRESTRICTED);
        assertEquals(80000, decision.bitrateKbps);
        assertEquals(HorizonStreamSafety.Reason.NONE, decision.reason);
    }

    @Test public void batterySaverOnlyLowersABitrateAboveItsLimit() {
        assertEquals(HorizonStreamSafety.Reason.BATTERY_SAVER, HorizonStreamSafety.limit(40000, true,
                HorizonStreamSafety.Thermal.UNRESTRICTED).reason);
        HorizonStreamSafety.Decision decision = HorizonStreamSafety.limit(19000, true,
                HorizonStreamSafety.Thermal.UNRESTRICTED);
        assertEquals(19000, decision.bitrateKbps);
        assertEquals(HorizonStreamSafety.Reason.NONE, decision.reason);
    }

    @Test public void heatTakesPriorityOverBatterySaver() {
        HorizonStreamSafety.Decision severe = HorizonStreamSafety.limit(90000, true,
                HorizonStreamSafety.Thermal.SEVERE);
        assertEquals(25000, severe.bitrateKbps);
        assertEquals(HorizonStreamSafety.Reason.SEVERE_THERMAL, severe.reason);
        HorizonStreamSafety.Decision critical = HorizonStreamSafety.limit(90000, true,
                HorizonStreamSafety.Thermal.CRITICAL);
        assertEquals(15000, critical.bitrateKbps);
        assertEquals(HorizonStreamSafety.Reason.CRITICAL_THERMAL, critical.reason);
    }
}
