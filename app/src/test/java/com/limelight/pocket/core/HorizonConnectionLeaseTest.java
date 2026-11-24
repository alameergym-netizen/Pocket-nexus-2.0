package com.limelight.pocket.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class HorizonConnectionLeaseTest {
    @Test public void stopBeforeStartNeverInterruptsBridge() {
        HorizonConnectionLease l = new HorizonConnectionLease();
        assertFalse(l.requestStop(() -> fail("not owner")));
        assertFalse(l.begin());
        assertFalse(l.enterNative());
        assertFalse(l.leaveNative());
    }
    @Test public void waitingWorkerCancellationDoesNotInterruptOtherOwner() {
        HorizonConnectionLease owner = new HorizonConnectionLease(), waiting = new HorizonConnectionLease();
        assertTrue(owner.begin()); assertTrue(owner.enterNative()); assertTrue(waiting.begin());
        assertFalse(waiting.requestStop(() -> fail("waiting worker")));
        assertFalse(waiting.enterNative());
        assertTrue(owner.leaveNative());
    }
    @Test public void nativeOwnerStopsExactlyOnce() {
        HorizonConnectionLease l = new HorizonConnectionLease();
        int[] interrupts = {0};
        assertTrue(l.begin()); assertFalse(l.begin()); assertTrue(l.enterNative());
        assertTrue(l.requestStop(() -> interrupts[0]++));
        assertFalse(l.requestStop(() -> interrupts[0]++));
        assertTrue(l.leaveNative()); assertFalse(l.leaveNative());
        assertEquals(1, interrupts[0]); assertTrue(l.cancelled());
    }
    @Test public void failedNativeStartCannotInterruptSuccessor() {
        HorizonConnectionLease l = new HorizonConnectionLease();
        assertTrue(l.begin()); assertTrue(l.enterNative()); assertTrue(l.leaveNative());
        assertFalse(l.requestStop(() -> fail("already released")));
        assertFalse(l.leaveNative());
    }
}
