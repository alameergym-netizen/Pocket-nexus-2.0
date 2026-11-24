package com.limelight.pocket.streaming;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonLinkPolicyTest {
    @Test public void wiredDoesNotAssume150Mbps() {
        assertEquals(25, HorizonLinkPolicy.suggestMbps(true,false,false,0,0,0,10,0,5,1));
    }
    @Test public void vpnDoesNotAssumeDirect() {
        assertEquals(15, HorizonLinkPolicy.suggestMbps(false,true,true,4,50,1200,10,0,5,1));
    }
    @Test public void weakLinkAndFailedAttemptsReduceBitrate() {
        assertEquals(5, HorizonLinkPolicy.suggestMbps(false,true,false,1,24,72,10,3,10,2));
        assertEquals(10, HorizonLinkPolicy.suggestMbps(false,true,false,4,50,600,10,1,60,5));
    }
    @Test public void missingOrFailedEvidenceHasNoRecommendation() {
        assertEquals(0, HorizonLinkPolicy.suggestMbps(true,false,false,0,0,0,0,0,-1,-1));
        assertEquals(0, HorizonLinkPolicy.suggestMbps(true,false,false,0,0,0,10,10,-1,-1));
    }
    @Test public void thermalPressureLimitsRecommendation() {
        assertEquals(0, HorizonLinkPolicy.limitForThermal(25,3,false));
        assertEquals(10, HorizonLinkPolicy.limitForThermal(25,2,false));
        assertEquals(10, HorizonLinkPolicy.limitForThermal(25,0,true));
        assertEquals(25, HorizonLinkPolicy.limitForThermal(25,0,false));
    }
    @Test public void profileQualityIsBounded() {
        assertEquals("1280x720", HorizonLinkPolicy.suggestedResolution(5));
        assertEquals(30, HorizonLinkPolicy.suggestedFps(5));
        assertEquals("1920x1080", HorizonLinkPolicy.suggestedResolution(15));
        assertEquals(60, HorizonLinkPolicy.suggestedFps(25));
    }
}
