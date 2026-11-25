package com.limelight.pocket.core;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.limelight.pocket.core.HorizonSession.State.*;

public class HorizonStreamObserverTest {
    @Test public void attemptRequiresActualConnectedCallback() {
        HorizonStreamObserver observer = new HorizonStreamObserver();
        assertEquals(IDLE, observer.snapshot().state);
        long ticket = observer.beginAttempt();
        assertEquals(LAUNCHING, observer.snapshot().state);
        observer.connected(ticket);
        assertEquals(RUNNING, observer.snapshot().state);
        observer.ended(ticket);
        assertEquals(ENDED, observer.snapshot().state);
    }
    @Test public void retrySupersedesOlderActivity() {
        HorizonStreamObserver observer = new HorizonStreamObserver();
        long first = observer.beginAttempt();
        observer.connected(first);
        long retry = observer.beginAttempt();
        assertTrue(retry > first);
        observer.failed(first);
        observer.ended(first);
        observer.connected(first);
        assertEquals(retry, observer.snapshot().ticket);
        assertEquals(LAUNCHING, observer.snapshot().state);
        observer.connected(retry);
        assertEquals(RUNNING, observer.snapshot().state);
    }
    @Test public void failedAttemptDoesNotBecomeSuccessfulFromLateCallback() {
        HorizonStreamObserver observer = new HorizonStreamObserver();
        long ticket = observer.beginAttempt();
        observer.failed(ticket);
        observer.connected(ticket);
        observer.ended(ticket);
        assertEquals(FAILED, observer.snapshot().state);
    }
    @Test public void stopRemainsEndingUntilCleanupCompletes() {
        HorizonStreamObserver observer = new HorizonStreamObserver();
        long ticket = observer.beginAttempt();
        assertTrue(observer.connected(ticket));
        assertFalse(observer.connected(ticket));
        observer.stopping(ticket);
        assertEquals(ENDING, observer.snapshot().state);
        assertFalse(observer.connected(ticket));
        observer.ended(ticket);
        assertEquals(ENDED, observer.snapshot().state);
    }
    @Test public void lateCleanupDoesNotEndSuccessor() {
        HorizonStreamObserver observer = new HorizonStreamObserver();
        long old = observer.beginAttempt();
        observer.stopping(old);
        long next = observer.beginAttempt();
        observer.ended(old);
        assertEquals(next, observer.snapshot().ticket);
        assertTrue(observer.connected(next));
        assertEquals(RUNNING, observer.snapshot().state);
    }
    @Test public void delayedSideEffectsRequireAcceptedCurrentRunningAttempt() {
        HorizonStreamObserver observer = new HorizonStreamObserver();
        long old = observer.beginAttempt();
        assertFalse(observer.isRunning(0));
        assertFalse(observer.isRunning(old));
        assertTrue(observer.connected(old));
        assertTrue(observer.isRunning(old));
        observer.stopping(old);
        assertFalse(observer.isRunning(old));
        long next = observer.beginAttempt();
        assertFalse(observer.isRunning(old));
        assertFalse(observer.isRunning(next));
        assertTrue(observer.connected(next));
        assertTrue(observer.isRunning(next));
        observer.ended(old);
        assertTrue(observer.isRunning(next));
        observer.failed(next);
        assertFalse(observer.isRunning(next));
    }
    @Test public void zeroTicketDoesNotChangeObservation() {
        HorizonStreamObserver observer = new HorizonStreamObserver();
        observer.failed(0);
        assertEquals(IDLE, observer.snapshot().state);
        long ticket = observer.beginAttempt();
        observer.ended(0);
        assertEquals(ticket, observer.snapshot().ticket);
        assertEquals(LAUNCHING, observer.snapshot().state);
    }

    @Test public void pictureInPictureKeepsTheConnectedOwnerAndRecordsVisibility() {
        HorizonStreamObserver observer = new HorizonStreamObserver();
        long ticket = observer.beginAttempt();
        observer.setBackground(ticket, true);
        assertEquals(LAUNCHING, observer.snapshot().state);
        assertTrue(observer.connected(ticket));

        observer.setBackground(ticket, true);
        assertEquals(BACKGROUND, observer.snapshot().state);
        assertTrue(observer.isRunning(ticket));
        observer.setBackground(ticket, false);
        assertEquals(RUNNING, observer.snapshot().state);
    }

    @Test public void stalePictureInPictureStateCannotReplaceTheNextStreamOrReviveAnEndingOne() {
        HorizonStreamObserver observer = new HorizonStreamObserver();
        long old = observer.beginAttempt();
        observer.connected(old);
        long ticket = observer.beginAttempt();
        observer.connected(ticket);
        observer.setBackground(old, true);
        assertEquals(RUNNING, observer.snapshot().state);

        observer.setBackground(ticket, true);
        observer.stopping(ticket);
        observer.setBackground(ticket, false);
        assertEquals(ENDING, observer.snapshot().state);
        assertFalse(observer.isRunning(ticket));
    }
}
