package com.limelight.pocket.streaming;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonAdaptiveDecisionTest {
    @Test public void duplicatePoorReportsDoNotCountAsFlappingOrResetDeadline() {
        HorizonAdaptiveDecision p=new HorizonAdaptiveDecision();
        for (long now=9000;now<=12000;now+=1000) p.observe(true,now,1000);
        assertFalse(p.shouldStep(12000));
        assertEquals(3000L,p.delayUntilSustained(12000));
        assertTrue(p.shouldStep(15000));
    }
    @Test public void healthyStatusCancelsSustainedTimer() {
        HorizonAdaptiveDecision p=new HorizonAdaptiveDecision();
        p.observe(true,9000,1000);
        p.observe(false,14000,1000);
        assertFalse(p.shouldStep(15000));
        assertEquals(-1L,p.delayUntilSustained(15000));
    }
    @Test public void fourDistinctEpisodesTriggerOnlyWhilePoor() {
        HorizonAdaptiveDecision p=new HorizonAdaptiveDecision();
        for (long now=9000;now<=15000;now+=2000) {
            p.observe(true,now,1000);
            if(now<15000) p.observe(false,now+1000,1000);
        }
        assertTrue(p.shouldStep(15000));
        p.observe(false,15001,1000);
        assertFalse(p.shouldStep(15001));
    }
    @Test public void oldEpisodesExpire() {
        HorizonAdaptiveDecision p=new HorizonAdaptiveDecision();
        for (long now=9000;now<=13000;now+=2000) {
            p.observe(true,now,1000);p.observe(false,now+1000,1000);
        }
        p.observe(true,34001,1000);
        assertFalse(p.shouldStep(34001));
    }
    @Test public void startupAndRetryBudgetAreBounded() {
        HorizonAdaptiveDecision p=new HorizonAdaptiveDecision();
        p.observe(true,8999,1000);
        assertFalse(p.shouldStep(15000));
        assertFalse(p.allowed(8999,1000,0));
        assertTrue(p.allowed(9000,1000,2));
        assertFalse(p.allowed(9000,1000,3));
        assertFalse(p.allowed(9000,1000,-1));
    }
    @Test public void resetAndReversedClockRejectOldEvidence() {
        HorizonAdaptiveDecision p=new HorizonAdaptiveDecision();
        p.observe(true,9000,1000);
        p.observe(true,15000,1000);
        assertTrue(p.shouldStep(15000));
        p.observe(true,10000,1000);
        assertFalse(p.shouldStep(10000));
        p.reset();
        assertFalse(p.shouldStep(20000));
    }
}
