package com.limelight.pocket.core;

/** Two prerequisite rendezvous for launching the Android stream activity. Not a native lease. */
public final class HorizonStreamDispatch {
    public enum Stage { ANIMATION, SIGN_IN }
    public static final long LIMIT_MS = 15000L;
    private static final HorizonStreamDispatch SHARED = new HorizonStreamDispatch();
    public static HorizonStreamDispatch shared() { return SHARED; }
    private long sequence, epoch, startedAt;
    private boolean active, animation, signedIn;

    public synchronized long begin(long requestEpoch, long now) {
        if (now < 0 || sequence == Long.MAX_VALUE) return 0;
        epoch = requestEpoch;
        startedAt = now;
        animation = signedIn = false;
        active = true;
        return ++sequence;
    }

    public synchronized boolean pending(long ticket, long requestEpoch, long now) {
        return active && ticket > 0 && ticket == sequence && epoch == requestEpoch
                && now >= startedAt && now - startedAt < LIMIT_MS;
    }

    /** Exactly one completion; duplicate stages and callbacks from older tickets do nothing. */
    public synchronized boolean arrive(long ticket, Stage stage, long requestEpoch, long now) {
        if (stage == null || !pending(ticket, requestEpoch, now)) return false;
        if (stage == Stage.ANIMATION) animation = true;
        else signedIn = true;
        if (!animation || !signedIn) return false;
        active = false;
        return true;
    }

    public synchronized void cancel(long ticket) {
        if (ticket > 0 && ticket == sequence) active = false;
    }
}
