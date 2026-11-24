package com.limelight.pocket.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Bounded resume UI barrier; never substitutes for actual native connection ownership. */
public final class HorizonShutdownBarrier {
    static final long MAX_WAIT_MS = 5000L;
    private long sequence;
    private final Map<Long, Long> pending = new HashMap<>();

    public synchronized long begin(long now) {
        prune(now);
        if (now < 0 || sequence == Long.MAX_VALUE) return 0;
        long owner = ++sequence;
        pending.put(owner, now);
        return owner;
    }
    public synchronized void finish(long owner) {
        if (owner > 0) pending.remove(owner);
    }
    public synchronized boolean waiting(long now) {
        prune(now);
        return !pending.isEmpty();
    }
    private void prune(long now) {
        Iterator<Long> it = pending.values().iterator();
        while (it.hasNext()) {
            long start = it.next();
            if (now < start || now - start >= MAX_WAIT_MS) it.remove();
        }
    }
}
