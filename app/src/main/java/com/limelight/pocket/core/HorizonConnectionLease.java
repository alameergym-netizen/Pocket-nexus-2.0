package com.limelight.pocket.core;

/** One connection's lifecycle lease. Global native calls remain under the bridge lock. */
public final class HorizonConnectionLease {
    private boolean started, cancelled, nativeOwner;
    public synchronized boolean begin() {
        if (started || cancelled) return false;
        started = true;
        return true;
    }
    public synchronized boolean cancelled() { return cancelled; }
    /** Hold this lease throughout the input call to exclude stop and ownership handoff. */
    public synchronized boolean ownsNativeInput() { return nativeOwner && !cancelled; }
    public synchronized boolean enterNative() {
        if (!started || cancelled || nativeOwner) return false;
        nativeOwner = true;
        return true;
    }
    /** Stop queued or negotiating work before scheduling any native cleanup off the UI thread. */
    public synchronized void cancelBeforeNative() {
        if (!nativeOwner) cancelled = true;
    }
    /** Interrupt while holding ownership, so failure/release cannot hand off mid-interrupt. */
    public synchronized boolean requestStop(Runnable interrupt) {
        if (cancelled) return false;
        cancelled = true;
        if (!nativeOwner) return false;
        interrupt.run();
        return true;
    }
    /** Called inside native bridge lock, exactly one caller can relinquish ownership. */
    public synchronized boolean leaveNative() {
        if (!nativeOwner) return false;
        nativeOwner = false;
        return true;
    }
}
