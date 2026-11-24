package com.limelight.pocket.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonDeferredTaskTest {
    @Test public void onlyOneCallbackCanClaim() {
        HorizonDeferredTask t = new HorizonDeferredTask(1, 2, 10, 100);
        assertTrue(t.current(1, 2, 10));
        assertTrue(t.claim(1, 2, 20));
        assertFalse(t.claim(1, 2, 21));
        assertFalse(t.expire(1, 2, 110));
    }
    @Test public void newSelectionOrStreamRejectsOldCompletion() {
        HorizonDeferredTask t = new HorizonDeferredTask(1, 2, 10, 100);
        assertFalse(t.claim(2, 2, 20));
        assertFalse(t.claim(1, 3, 20));
        assertFalse(t.expire(1, 3, 120));
        assertTrue(t.claim(1, 2, 20));
    }
    @Test public void deadlineHasExclusiveOwnership() {
        HorizonDeferredTask t = new HorizonDeferredTask(1, 2, 10, 100);
        assertFalse(t.expire(1, 2, 109));
        assertTrue(t.current(1, 2, 109));
        assertFalse(t.claim(1, 2, 110));
        assertTrue(t.expire(1, 2, 110));
        assertFalse(t.expire(1, 2, 111));
    }
    @Test public void stopCancelsBothSuccessAndTimeout() {
        HorizonDeferredTask t = new HorizonDeferredTask(1, 2, 10, 100);
        t.cancel();
        assertFalse(t.claim(1, 2, 20));
        assertFalse(t.expire(1, 2, 110));
    }
    @Test public void invalidClockAndTicketRejected() {
        assertFalse(new HorizonDeferredTask(0, 0, 10, 100).claim(0, 0, 20));
        assertFalse(new HorizonDeferredTask(1, 0, 10, 100).claim(1, 0, 9));
        assertFalse(new HorizonDeferredTask(1, 0, 10, 0).expire(1, 0, 20));
        assertTrue(new HorizonDeferredTask(1, 0, 10, 100).claim(1, 0, 20));
    }
}
