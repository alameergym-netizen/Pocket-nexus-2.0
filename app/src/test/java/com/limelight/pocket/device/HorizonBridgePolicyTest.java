package com.limelight.pocket.device;
import org.junit.Test;
import static org.junit.Assert.*;
public class HorizonBridgePolicyTest {
    @Test public void missingOrUnexpectedThermalValuesRemainUnknown() {
        assertTrue(HorizonBridgePolicy.hasThermalStatus(0));
        assertTrue(HorizonBridgePolicy.hasThermalStatus(6));
        assertFalse(HorizonBridgePolicy.hasThermalStatus(-1));
        assertFalse(HorizonBridgePolicy.hasThermalStatus(7));
        assertEquals(0, HorizonBridgePolicy.recommendation(7, false));
        assertEquals(2, HorizonBridgePolicy.recommendation(7, true));
    }
    @Test public void modelDetectionRequiresRetroidIdentity() {
        assertTrue(HorizonBridgePolicy.isRp6("Retroid", "RP6"));
        assertTrue(HorizonBridgePolicy.isRp6("unknown", "Retroid Pocket 6"));
        assertFalse(HorizonBridgePolicy.isRp6("Other", "RP6"));
        assertFalse(HorizonBridgePolicy.isRp6("Retroid", "Pocket 5"));
    }
    @Test public void thermalGuidanceIsConservative() {
        assertEquals(0, HorizonBridgePolicy.recommendation(-1, false));
        assertEquals(1, HorizonBridgePolicy.recommendation(0, false));
        assertEquals(2, HorizonBridgePolicy.recommendation(2, false));
        assertEquals(2, HorizonBridgePolicy.recommendation(0, true));
        assertEquals(3, HorizonBridgePolicy.recommendation(4, false));
    }
    @Test public void invalidFrequencyIsNotZeroOrRealReading() {
        assertEquals(1804800, HorizonBridgePolicy.parseFrequency("1804800"));
        assertEquals(-1, HorizonBridgePolicy.parseFrequency(null));
        assertEquals(-1, HorizonBridgePolicy.parseFrequency("0"));
        assertEquals(-1, HorizonBridgePolicy.parseFrequency("invalid"));
    }
}
