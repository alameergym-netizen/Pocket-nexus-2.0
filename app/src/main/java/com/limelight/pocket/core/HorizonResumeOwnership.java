package com.limelight.pocket.core;

/** In-memory ownership of a taken persisted resume record; never extends its original age. */
public final class HorizonResumeOwnership {
    public static final class Lease {
        public final long armedAt;
        private final long maxAge, epoch;
        private Lease(long armedAt, long maxAge, long epoch) {
            this.armedAt = armedAt; this.maxAge = maxAge; this.epoch = epoch;
        }
    }
    private Lease active;
    public static boolean validAge(long armedAt, long maxAge, long now) {
        return armedAt > 0 && maxAge > 0 && now >= armedAt && now - armedAt < maxAge;
    }
    public synchronized Lease take(long armedAt, long maxAge, long epoch, long now) {
        if (active != null || !validAge(armedAt, maxAge, now)) return null;
        active = new Lease(armedAt, maxAge, epoch);
        return active;
    }
    public synchronized boolean current(Lease lease, long epoch, long now) {
        return lease != null && lease == active && lease.epoch == epoch
                && validAge(lease.armedAt, lease.maxAge, now);
    }
    public synchronized boolean consume(Lease lease, long epoch, long now) {
        if (!current(lease, epoch, now)) return false;
        active = null;
        return true;
    }
    public synchronized void discard(Lease lease) {
        if (lease != null && active == lease) active = null;
    }
    public synchronized void invalidate() { active = null; }
}
