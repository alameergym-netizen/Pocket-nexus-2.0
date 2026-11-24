package com.limelight.pocket.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonPollingOwnerTest {
    @Test public void staleScreenCannotStopSuccessor() {
        HorizonPollingOwner<String> p = new HorizonPollingOwner<>();
        Object first = new Object(), second = new Object();
        p.acquire(first, "first");
        p.acquire(second, "second");
        assertFalse(p.release(first));
        assertTrue(p.owns(second));
        assertEquals("second", p.listener());
        assertTrue(p.release(second));
        assertNull(p.listener());
    }
    @Test public void ownerUsesIdentityNotEquals() {
        HorizonPollingOwner<String> p = new HorizonPollingOwner<>();
        Object first = new String("screen"), second = new String("screen");
        p.acquire(first, "listener");
        assertFalse(p.release(second));
        assertTrue(p.release(first));
    }
    @Test public void realUnbindClearsAllOwnership() {
        HorizonPollingOwner<String> p = new HorizonPollingOwner<>();
        Object first = new Object();
        p.acquire(first, "first");
        p.clear();
        assertFalse(p.release(first));
        assertFalse(p.owns(null));
        assertNull(p.listener());
    }
    @Test public void reacquireReplacesListener() {
        HorizonPollingOwner<String> p = new HorizonPollingOwner<>();
        Object first = new Object();
        p.acquire(first, "old");
        p.acquire(first, "new");
        assertEquals("new", p.listener());
        assertTrue(p.release(first));
        assertFalse(p.release(first));
    }
}
