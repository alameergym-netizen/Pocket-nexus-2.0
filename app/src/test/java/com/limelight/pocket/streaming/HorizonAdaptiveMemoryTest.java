package com.limelight.pocket.streaming;

import static org.junit.Assert.*;

import org.junit.Test;

public class HorizonAdaptiveMemoryTest {
    private static final HorizonAdaptiveMemory.Route WIFI = HorizonAdaptiveMemory.Route.WIFI;

    @Test public void oneHostsSampleDoesNotChangeAnotherHostsBitrate() {
        HorizonAdaptiveMemory memory = new HorizonAdaptiveMemory();
        memory.learn(HorizonAdaptiveMemory.scope("PC-A", 11, WIFI), 15000, 100);
        assertEquals(15000, memory.cap(HorizonAdaptiveMemory.scope("pc-a", 11, WIFI), 101));
        assertEquals(0, memory.cap(HorizonAdaptiveMemory.scope("pc-b", 11, WIFI), 101));
    }

    @Test public void activeNetworkAndTransportBothScopeTheCap() {
        HorizonAdaptiveMemory memory = new HorizonAdaptiveMemory();
        memory.learn(HorizonAdaptiveMemory.scope("pc", 12, WIFI), 9000, 100);
        assertEquals(0, memory.cap(HorizonAdaptiveMemory.scope("pc", 8, WIFI), 101));
        assertEquals(0, memory.cap(HorizonAdaptiveMemory.scope("pc", 12, HorizonAdaptiveMemory.Route.VPN), 101));
        assertEquals(9000, memory.cap(HorizonAdaptiveMemory.scope("pc", 12, WIFI), 101));
    }

    @Test public void staleUnknownAndReversedClockSamplesDoNotApply() {
        HorizonAdaptiveMemory memory = new HorizonAdaptiveMemory();
        HorizonAdaptiveMemory.Scope scope = HorizonAdaptiveMemory.scope("pc", 1, WIFI);
        assertNull(HorizonAdaptiveMemory.scope("pc", 0, WIFI));
        assertNull(HorizonAdaptiveMemory.scope("  ", 1, WIFI));
        assertNull(HorizonAdaptiveMemory.scope("pc", 1, null));
        memory.learn(scope, 10000, 50);
        assertEquals(10000, memory.cap(scope, 49 + HorizonAdaptiveMemory.LIFETIME_MS));
        assertEquals(0, memory.cap(scope, 50 + HorizonAdaptiveMemory.LIFETIME_MS));
        memory.learn(scope, 10000, 200);
        assertEquals(0, memory.cap(scope, 100));
        assertEquals(0, memory.cap(scope, 201));
        assertEquals(0, memory.cap(null, 201));
    }

    @Test public void scopesAreBoundedAndClearIndependently() {
        HorizonAdaptiveMemory memory = new HorizonAdaptiveMemory();
        HorizonAdaptiveMemory.Scope first = HorizonAdaptiveMemory.scope("pc", 100, WIFI);
        memory.learn(first, 10000, 100);
        for (int i = 1; i <= HorizonAdaptiveMemory.MAX_SCOPES; i++) {
            memory.learn(HorizonAdaptiveMemory.scope("pc", i, WIFI), 12000, 100);
        }
        assertEquals(0, memory.cap(first, 200));
        HorizonAdaptiveMemory.Scope second = HorizonAdaptiveMemory.scope("pc", 2, WIFI);
        memory.clear(second);
        assertEquals(0, memory.cap(second, 201));
        HorizonAdaptiveMemory.Scope third = HorizonAdaptiveMemory.scope("pc", 3, WIFI);
        assertEquals(12000, memory.cap(third, 201));
        memory.clearAll();
        assertEquals(0, memory.cap(third, 202));
    }
}
