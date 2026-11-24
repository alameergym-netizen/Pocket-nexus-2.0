package com.limelight.pocket.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonPendingPcOwnershipTest {
    private final HorizonLaunchTarget a = HorizonLaunchTarget.pc("host", 7);
    private final HorizonLaunchTarget b = HorizonLaunchTarget.pc("host", 8);

    @Test public void repeatDuringDiscoveryKeepsOriginalTicket() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long first = q.beginPc(a, 100);
        assertEquals(0L, q.beginPc(a, 1000));
        assertEquals(0L, q.beginPc(a, 90000));
        assertTrue(q.claim(first, 90001));
        assertFalse(q.claim(first, 90002));
    }

    @Test public void cancelledOrExpiredDiscoveryCanRetry() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long first = q.beginPc(a, 100);
        q.cancel(first);
        long next = q.beginPc(a, 101);
        assertTrue(next > first);
        assertEquals(0L, q.beginPc(a, 120101));
        long last = q.beginPc(a, 120102);
        assertTrue(last > next);
        assertFalse(q.claim(next, 120103));
        assertTrue(q.claim(last, 120103));
    }

    @Test public void differentTargetStillSupersedesPendingRequest() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long first = q.beginPc(a, 100);
        long next = q.beginPc(b, 101);
        assertTrue(next > first);
        assertFalse(q.claim(first, 102));
        assertTrue(q.claim(next, 102));
        assertTrue(q.beginPc(b, 1000) > next);
    }
}
