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
    private boolean active;
    private long lifetime;
    static final long PC_WAIT_MS = 120000L;

    /** A different selection supersedes older work; repeated game selection is debounced. */
    public synchronized long begin(HorizonLaunchTarget target, long now) {
        return begin(target, now, ASYNC_LIMIT_MS);
    }
    /** PC discovery/wake and confirmation may take longer than local launch. */
    public synchronized long beginPc(HorizonLaunchTarget target, long now) {
        if (target == null || (target.provider != HorizonProviderPolicy.Provider.PC_STREAM
                && target.provider != HorizonProviderPolicy.Provider.PC_LIBRARY)) return 0L;
        return begin(target, now, PC_WAIT_MS);
    }
    private long begin(HorizonLaunchTarget target, long now, long limit) {
        if (target == null || now < 0 || sequence == Long.MAX_VALUE) return 0L;
        long age = now - issuedAt;
        if (target.isGameRequest() && target.equals(last) && age >= 0 && age < REPEAT_MS) return 0L;
        last = target;
        issuedAt = now;
        claimed = false;
        active = true;
        lifetime = limit;
        return ++sequence;
    }
    /** One callback may launch, only while it still owns the latest selection. */
    public synchronized boolean claim(long ticket, long now) {
        if (!current(ticket, now)) return false;
        claimed = true;
        return true;
    }
    public synchronized boolean current(long ticket, long now) {
        long age = now - issuedAt;
        return active && ticket > 0 && sequence == ticket && !claimed && age >= 0 && age <= lifetime;
    }
    public synchronized void cancel(long ticket) {
        if (ticket > 0 && sequence == ticket && !claimed) {
            active = false;
            last = null;
        }
    }
}
