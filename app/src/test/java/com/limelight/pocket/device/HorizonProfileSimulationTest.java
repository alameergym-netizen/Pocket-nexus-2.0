package com.limelight.pocket.device;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonProfileSimulationTest {
    private Map<String,Long> limits(long cpu,long gpu) {
        Map<String,Long> m=new LinkedHashMap<>();
        m.put("policy0",cpu); m.put("gpu",gpu); return m;
    }
    @Test public void completeApply() {
        Map<String,Long> before=limits(2000000,800000000), after=limits(1600000,600000000);
        HorizonProfileSimulation.Result r=HorizonProfileSimulation.run(before,after,-1,-1);
        assertEquals(HorizonProfileSimulation.Outcome.APPLIED,r.outcome);
        assertEquals(after,r.observed);
        assertEquals(Long.valueOf(2000000),before.get("policy0"));
    }
    @Test public void rollbackRestoresAllTouchedDomains() {
        Map<String,Long> before=limits(2000000,800000000);
        HorizonProfileSimulation.Result r=HorizonProfileSimulation.run(before,limits(1600000,600000000),1,-1);
        assertEquals(HorizonProfileSimulation.Outcome.ROLLED_BACK,r.outcome);
        assertEquals(before,r.observed);
        assertEquals("rollback:policy0",r.journal.get(2));
        assertEquals("rollback:gpu",r.journal.get(3));
    }
    @Test public void rollbackFailureIsIndeterminate() {
        Map<String,Long> before=limits(2000000,800000000);
        HorizonProfileSimulation.Result r=HorizonProfileSimulation.run(before,limits(1600000,600000000),1,0);
        assertEquals(HorizonProfileSimulation.Outcome.INDETERMINATE,r.outcome);
        assertNotEquals(before,r.observed);
    }
    @Test public void rejectsIncreaseBeforeAnyMutation() {
        Map<String,Long> before=limits(2000000,800000000);
        HorizonProfileSimulation.Result r=HorizonProfileSimulation.run(before,limits(2100000,600000000),-1,-1);
        assertEquals(HorizonProfileSimulation.Outcome.REJECTED,r.outcome);
        assertEquals(before,r.observed);
        assertTrue(r.journal.isEmpty());
    }
    @Test public void rejectsChangedTopology() {
        Map<String,Long> before=limits(2000000,800000000), after=limits(1600000,600000000);
        after.remove("gpu");
        assertEquals(HorizonProfileSimulation.Outcome.REJECTED,
                HorizonProfileSimulation.run(before,after,-1,-1).outcome);
    }
    @Test public void gpuUsesHzWithoutCpuUnitConfusion() {
        long[] steps=HorizonCpuPlan.parseSteps("300000000 600000000 800000000",10000000000L);
        assertEquals(3,steps.length);
        assertEquals(600000000,HorizonCpuPlan.capWithin(HorizonCpuPlan.Mode.BALANCED,300000000,800000000,steps,10000000000L));
        assertEquals(0,HorizonCpuPlan.parse("300000000").length);
    }
}
