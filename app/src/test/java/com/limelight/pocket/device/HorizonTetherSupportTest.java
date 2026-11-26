package com.limelight.pocket.device;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class HorizonTetherSupportTest {
    @Test public void readsReturnWhileThePlatformIsPendingAndDoNotQueueMoreWork() {
        AtomicLong clock = new AtomicLong(10);
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        HorizonTetherSupport probe = new HorizonTetherSupport(work::add, clock::get);
        assertEquals(HorizonTetherSupport.CHECKING, probe.current(() -> HorizonTetherSupport.SUPPORTED));
        for (int i = 0; i < 100; i++) assertEquals(HorizonTetherSupport.CHECKING, probe.current(() -> failReadback()));
        assertEquals(1, work.size());
        work.remove().run();
        assertEquals(HorizonTetherSupport.SUPPORTED, probe.current(() -> failReadback()));
        assertTrue(work.isEmpty());
    }

    @Test public void anUnresponsiveServiceBecomesUnknownAndItsLateResultIsNotPublished() {
        AtomicLong clock = new AtomicLong(10);
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        HorizonTetherSupport probe = new HorizonTetherSupport(work::add, clock::get);
        probe.current(() -> HorizonTetherSupport.SUPPORTED);
        clock.set(2010);
        for (int i = 0; i < 100; i++) assertEquals(HorizonTetherSupport.UNKNOWN, probe.current(() -> failReadback()));
        assertEquals(1, work.size());
        work.remove().run();
        assertEquals(HorizonTetherSupport.UNKNOWN, probe.current(() -> failReadback()));
        clock.set(62_010);
        assertEquals(HorizonTetherSupport.CHECKING, probe.current(() -> HorizonTetherSupport.UNSUPPORTED));
        work.remove().run();
        assertEquals(HorizonTetherSupport.UNSUPPORTED, probe.current(() -> failReadback()));
    }

    @Test public void serviceErrorsAndAClockResetNeverInventSupport() {
        AtomicLong clock = new AtomicLong(100);
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        HorizonTetherSupport probe = new HorizonTetherSupport(work::add, clock::get);
        probe.current(() -> { throw new IllegalStateException("service unavailable"); });
        work.remove().run();
        assertEquals(HorizonTetherSupport.UNKNOWN, probe.current(() -> failReadback()));
        clock.set(60_100);
        probe.current(() -> HorizonTetherSupport.UNSUPPORTED);
        clock.set(50);
        assertEquals(HorizonTetherSupport.UNKNOWN, probe.current(() -> failReadback()));
        work.remove().run();
        assertEquals(HorizonTetherSupport.UNKNOWN, probe.current(() -> failReadback()));
    }

    private static int failReadback() {
        throw new AssertionError("A cache read queued or invoked another platform query");
    }
}
