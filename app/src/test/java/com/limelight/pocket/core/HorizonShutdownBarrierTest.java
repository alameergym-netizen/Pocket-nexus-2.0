package com.limelight.pocket.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class HorizonShutdownBarrierTest {
    @Test public void olderFinishCannotClearNewerShutdown() {
        HorizonShutdownBarrier b = new HorizonShutdownBarrier();
        long a = b.begin(100), c = b.begin(200);
        b.finish(a); b.finish(a);
        assertTrue(b.waiting(300));
        b.finish(c);
        assertFalse(b.waiting(301));
    }
    @Test public void newerFinishCannotClearOlderShutdown() {
        HorizonShutdownBarrier b = new HorizonShutdownBarrier();
        long a = b.begin(100), c = b.begin(200);
        b.finish(c);
        assertTrue(b.waiting(300));
        b.finish(a);
        assertFalse(b.waiting(301));
    }
    @Test public void expiredUiWaitDoesNotBlockForever() {
        HorizonShutdownBarrier b = new HorizonShutdownBarrier();
        long a = b.begin(0);
        assertTrue(b.waiting(4999));
        assertFalse(b.waiting(5000));
        long c = b.begin(5001);
        b.finish(a);
        assertTrue(b.waiting(5002));
        b.finish(c);
        assertFalse(b.waiting(5003));
    }
    @Test public void reversedClockAndUnknownOwnerSafe() {
        HorizonShutdownBarrier b = new HorizonShutdownBarrier();
        b.begin(100); b.finish(0); b.finish(999);
        assertTrue(b.waiting(100));
        assertFalse(b.waiting(99));
    }
}
