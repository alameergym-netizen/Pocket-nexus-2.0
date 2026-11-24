package com.limelight.pocket.core;

/** Serial ownership for launcher dispatch, not proof of an active external game/session. */
public final class HorizonLaunchRequests {
    private static final HorizonLaunchRequests SHARED = new HorizonLaunchRequests();
    public static HorizonLaunchRequests shared() { return SHARED; }
    static final long REPEAT_MS = 650L;
    static final long ASYNC_LIMIT_MS = 15000L;
    private long sequence, issuedAt;
    private HorizonLaunchTarget last;
    private boolean claimed;

    /** A different selection supersedes older work; repeated game selection is debounced. */
    public synchronized long begin(HorizonLaunchTarget target, long now) {
        if (target == null || now < 0 || sequence == Long.MAX_VALUE) return 0L;
        long age = now - issuedAt;
        if (target.isGameRequest() && target.equals(last) && age >= 0 && age < REPEAT_MS) return 0L;
        last = target;
        issuedAt = now;
        claimed = false;
        return ++sequence;
    }
    /** One callback may launch, only while it still owns the latest selection. */
    public synchronized boolean claim(long ticket, long now) {
        long age = now - issuedAt;
        if (ticket <= 0 || sequence != ticket || claimed || age < 0 || age > ASYNC_LIMIT_MS) return false;
        claimed = true;
        return true;
    }
}
