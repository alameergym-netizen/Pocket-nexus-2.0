package com.limelight.pocket.device;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonPowerTest {
    @Test public void batteryUnitsAndDirection() {
        assertEquals(-4.0, HorizonPower.watts(-1000000, 4000), 0.00001);
        assertEquals(2.0, HorizonPower.watts(500000, 4000), 0.00001);
    }

    @Test public void unavailableIsNotZeroWatts() {
        assertTrue(Double.isNaN(HorizonPower.watts(Integer.MIN_VALUE, 4000)));
        assertTrue(Double.isNaN(HorizonPower.watts(1000000, -1)));
        assertTrue(Double.isNaN(HorizonPower.watts(0, 4000)));
    }

    @Test public void purposeBuiltToolsFirst() {
        assertTrue(HorizonPower.toolPriority("ClusterTune") > HorizonPower.toolPriority("Retroid Settings"));
        assertTrue(HorizonPower.toolPriority("PULSE") > HorizonPower.toolPriority("Fan Control"));
        assertEquals(0, HorizonPower.toolPriority("Lossless Scaling"));
        assertEquals(0, HorizonPower.toolPriority(null));
    }
}
