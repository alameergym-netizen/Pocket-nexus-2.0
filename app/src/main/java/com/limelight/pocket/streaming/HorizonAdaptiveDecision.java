package com.limelight.pocket.streaming;

import java.util.ArrayDeque;

/** Conservative reconnect policy. Status reports are not measurements of frame pacing. */
public final class HorizonAdaptiveDecision {
    static final long STARTUP_MS = 8000L;
    static final long SUSTAINED_MS = 6000L;
    static final long WINDOW_MS = 20000L;
    static final int MAX_STEPS = 3;
    private final ArrayDeque<Long> episodes = new ArrayDeque<>();
    private long poorSince = -1;
    private long lastAt = -1;

    public void reset() {
        episodes.clear();
        poorSince = lastAt = -1;
    }
    public void observe(boolean poor, long now, long started) {
        if (now < started || started < 0 || now - started < STARTUP_MS) return;
        if (lastAt > now) reset();
        lastAt = now;
        prune(now);
        if (poor && poorSince < 0) {
            poorSince = now;
            episodes.addLast(now);
        } else if (!poor) {
            poorSince = -1;
        }
    }
    private void prune(long now) {
        while (!episodes.isEmpty() && now - episodes.peekFirst() > WINDOW_MS) episodes.removeFirst();
    }
    public boolean allowed(long now, long started, int steps) {
        return started > 0 && now >= started && now - started >= STARTUP_MS
                && steps >= 0 && steps < MAX_STEPS;
    }
    public boolean shouldStep(long now) {
        if (poorSince < 0 || now < poorSince || now < lastAt) return false;
        prune(now);
        return now - poorSince >= SUSTAINED_MS || episodes.size() >= 4;
    }
    public long delayUntilSustained(long now) {
        if (poorSince < 0 || now < poorSince) return -1;
        return Math.max(0, SUSTAINED_MS - (now - poorSince));
    }
}
