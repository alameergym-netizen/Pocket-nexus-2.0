package com.limelight.pocket.device;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonCpuPlanTest {
    @Test public void parsesSortedUniqueSteps() {
        assertArrayEquals(new long[]{300000,600000,1000000},
                HorizonCpuPlan.parse("1000000 300000 600000 300000"));
    }
    @Test public void malformedOrMissingStepsFailClosed() {
        assertEquals(0,HorizonCpuPlan.parse(null).length);
        assertEquals(0,HorizonCpuPlan.parse("300000 unknown").length);
        assertEquals(0,HorizonCpuPlan.parse("99999999999").length);
    }
    @Test public void ecoAndBalancedChooseAvailableSteps() {
        long[] steps={300000,600000,800000,1000000};
        assertEquals(600000,HorizonCpuPlan.cap(HorizonCpuPlan.Mode.ECO,300000,1000000,steps));
        assertEquals(800000,HorizonCpuPlan.cap(HorizonCpuPlan.Mode.BALANCED,300000,1000000,steps));
    }
    @Test public void preservesObservedLimits() {
        long[] steps={300000,600000,800000,1000000};
        assertEquals(800000,HorizonCpuPlan.cap(HorizonCpuPlan.Mode.PERFORMANCE,300000,900000,steps));
        assertEquals(800000,HorizonCpuPlan.cap(HorizonCpuPlan.Mode.ECO,800000,1000000,steps));
        assertEquals(-1,HorizonCpuPlan.cap(HorizonCpuPlan.Mode.ECO,900000,800000,steps));
    }
    @Test public void absentSelectableRangeNeverInventsFrequency() {
        assertEquals(-1,HorizonCpuPlan.cap(HorizonCpuPlan.Mode.BALANCED,300000,1000000,new long[0]));
        assertEquals(-1,HorizonCpuPlan.cap(HorizonCpuPlan.Mode.BALANCED,300000,1000000,new long[]{2000000}));
    }
}
