package com.limelight.pocket.device;

import java.util.concurrent.Executor;

/** Nonblocking, single-flight readback of Android's ROM-level USB tethering capability. */
public final class HorizonTetherSupport {
    public static final int UNKNOWN = 0, SUPPORTED = 1, UNSUPPORTED = 2, CHECKING = 3;
    public interface Readback { int read(); }
    public interface Clock { long now(); }
    private static final long DEADLINE_MS = 2_000;
    private static final long CACHE_MS = 60_000;
    private final Executor worker;
    private final Clock clock;
    private boolean running, completed;
    private long startedAt, completedAt;
    private int result = UNKNOWN;

    public HorizonTetherSupport(Executor worker, Clock clock) {
        this.worker = worker;
        this.clock = clock;
    }

    public synchronized int current(Readback readback) {
        long now = clock.now();
        if (running) {
            // A timed-out platform call can ignore interruption. Keep just that one
            // worker; repeated reads must never accumulate blocked threads or tasks.
            return inTime(now) ? CHECKING : UNKNOWN;
        }
        if (completed && now >= completedAt && now - completedAt < CACHE_MS) return result;
        running = true;
        startedAt = now;
        try {
            worker.execute(() -> {
                int value;
                try { value = readback.read(); }
                catch (RuntimeException | LinkageError unavailable) { value = UNKNOWN; }
                synchronized (HorizonTetherSupport.this) {
                    long finishedAt = clock.now();
                    result = inTime(finishedAt) && (value == SUPPORTED || value == UNSUPPORTED) ? value : UNKNOWN;
                    completedAt = finishedAt;
                    completed = true;
                    running = false;
                }
            });
        } catch (RuntimeException unavailable) {
            running = false;
            completed = true;
            completedAt = now;
            result = UNKNOWN;
            return UNKNOWN;
        }
        return running ? CHECKING : result;
    }

    private boolean inTime(long now) {
        return now >= startedAt && now - startedAt < DEADLINE_MS;
    }
}
