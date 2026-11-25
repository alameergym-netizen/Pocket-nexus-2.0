package com.limelight.pocket.streaming;

import java.util.Arrays;

/** Bounded input-to-output timing records shared by the decoder's input and output threads. */
public final class HorizonDecodeTimestamps {
    public static final long MISSING = Long.MIN_VALUE;
    private static final int CAPACITY = 256;
    private final long[] pts = new long[CAPACITY];
    private final long[] timestamps = new long[CAPACITY];
    private final boolean[] available = new boolean[CAPACITY];
    private int next;

    public synchronized void record(long presentationUs, long queuedNs) {
        pts[next] = presentationUs;
        timestamps[next] = queuedNs;
        available[next] = true;
        next = (next + 1) % CAPACITY;
    }

    public synchronized long take(long presentationUs) {
        int index = next;
        for (int checked = 0; checked < CAPACITY; checked++) {
            if (--index < 0) index = CAPACITY - 1;
            if (available[index] && pts[index] == presentationUs) {
                available[index] = false;
                return timestamps[index];
            }
        }
        return MISSING;
    }

    public synchronized void clear() {
        Arrays.fill(available, false);
        next = 0;
    }
}
