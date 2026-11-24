package com.limelight.pocket.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonResumeOwnershipTest {
    @Test public void cannotTakeTwiceOrConsumeTwice() {
        HorizonResumeOwnership p = new HorizonResumeOwnership();
        HorizonResumeOwnership.Lease a = p.take(100,1000,3,101);
        assertNotNull(a);
        assertNull(p.take(100,1000,3,102));
        assertTrue(p.consume(a,3,103));
        assertFalse(p.consume(a,3,104));
    }
    @Test public void oldLeaseCannotAffectNewRecord() {
        HorizonResumeOwnership p = new HorizonResumeOwnership();
        HorizonResumeOwnership.Lease old = p.take(100,1000,3,101);
        p.invalidate();
        HorizonResumeOwnership.Lease next = p.take(200,1000,3,201);
        p.discard(old);
        assertFalse(p.consume(old,3,202));
        assertTrue(p.consume(next,3,203));
    }
    @Test public void newerSelectionInvalidatesCallback() {
        HorizonResumeOwnership p = new HorizonResumeOwnership();
        HorizonResumeOwnership.Lease a = p.take(100,1000,3,101);
        assertFalse(p.current(a,4,102));
        assertFalse(p.consume(a,4,103));
        p.discard(a);
        assertNotNull(p.take(200,1000,4,201));
    }
    @Test public void restoredRecordKeepsOriginalDeadline() {
        HorizonResumeOwnership p = new HorizonResumeOwnership();
        HorizonResumeOwnership.Lease a = p.take(100,1000,3,900);
        assertTrue(p.consume(a,3,950));
        HorizonResumeOwnership.Lease b = p.take(a.armedAt,1000,3,951);
        assertTrue(p.current(b,3,1099));
        assertFalse(p.current(b,3,1100));
    }
    @Test public void rejectsExpiredAndReversedTime() {
        HorizonResumeOwnership p = new HorizonResumeOwnership();
        assertNull(p.take(100,1000,3,99));
        assertNull(p.take(100,1000,3,1100));
        assertNull(p.take(0,1000,3,200));
        HorizonResumeOwnership.Lease a = p.take(100,1000,3,101);
        assertFalse(p.current(a,3,99));
        p.discard(a);
        assertFalse(p.current(a,3,102));
    }
}
