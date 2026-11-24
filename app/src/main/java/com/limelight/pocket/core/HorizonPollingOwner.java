package com.limelight.pocket.core;

/** Identity-based single foreground owner; service serializes network start/stop separately. */
public final class HorizonPollingOwner<T> {
    private Object owner;
    private T listener;

    public synchronized void acquire(Object owner, T listener) {
        if (owner == null || listener == null) throw new IllegalArgumentException("Missing polling owner");
        this.owner = owner;
        this.listener = listener;
    }

    public synchronized boolean owns(Object expected) {
        return expected != null && owner == expected;
    }

    public synchronized T listener() { return listener; }

    public synchronized boolean release(Object expected) {
        if (!owns(expected)) return false;
        clear();
        return true;
    }

    public synchronized void clear() {
        owner = null;
        listener = null;
    }
}
