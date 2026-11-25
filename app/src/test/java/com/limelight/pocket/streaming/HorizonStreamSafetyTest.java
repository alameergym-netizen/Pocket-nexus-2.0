package com.limelight.pocket.streaming;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonStreamSafetyTest {
    @Test public void lowBatteryLimitTakesPrecedenceOverAHighRequestedBitrate() {
        HorizonStreamSafety.Decision low = HorizonStreamSafety.limit(
                40_000, true, HorizonStreamSafety.Thermal.SEVERE, true);
        assertEquals(15_000, low.bitrateKbps);
        assertEquals(HorizonStreamSafety.Reason.LOW_BATTERY, low.reason);

        HorizonStreamSafety.Decision lowerRequest = HorizonStreamSafety.limit(
                9_000, true, HorizonStreamSafety.Thermal.SEVERE, true);
        assertEquals(9_000, lowerRequest.bitrateKbps);
        assertEquals(HorizonStreamSafety.Reason.NONE, lowerRequest.reason);
    }

    @Test public void onlyValidUnpluggedBatteryAtTheExistingThresholdAppliesTheBudget() {
        assertEquals(20, HorizonStreamSafety.lowBatteryPercent(200, 1000, 0));
        assertEquals(-1, HorizonStreamSafety.lowBatteryPercent(21, 100, 0));
        assertEquals(-1, HorizonStreamSafety.lowBatteryPercent(20, 100, 1));
        assertEquals(-1, HorizonStreamSafety.lowBatteryPercent(20, 0, 0));
        assertEquals(-1, HorizonStreamSafety.lowBatteryPercent(-1, 100, 0));
    }

    @Test public void videoBudgetAllowsBoth720pOrientationsAndCapsLargerModes() {
        assertFalse(HorizonStreamSafety.exceedsLowBatteryResolution(1280, 720));
        assertFalse(HorizonStreamSafety.exceedsLowBatteryResolution(720, 1280));
        assertTrue(HorizonStreamSafety.exceedsLowBatteryResolution(1920, 1080));
        assertTrue(HorizonStreamSafety.exceedsLowBatteryResolution(Integer.MAX_VALUE, 2160));
    }

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
