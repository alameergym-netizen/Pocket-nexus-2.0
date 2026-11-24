package com.limelight.pocket.core;

/**
 * Observes the Android PC-stream attempt, not the remote game's lifetime.
 * Process-local only; never starts/stops a stream or changes existing resume behavior.
 * Each attempt has an owner ticket so delayed callbacks from an older Game activity
 * cannot overwrite the latest observation.
 */
public final class HorizonStreamObserver {
    private static final HorizonStreamObserver INSTANCE = new HorizonStreamObserver();
    private HorizonSession session = HorizonSession.idle();
    private HorizonLaunchTarget target;

    public static HorizonStreamObserver instance() { return INSTANCE; }

    public synchronized HorizonSession snapshot() { return session; }

    /** Pair of state and target from the same attempt, not a remote process ID. */
    public static final class Observation {
        public final HorizonSession session;
        public final HorizonLaunchTarget target;
        private Observation(HorizonSession session, HorizonLaunchTarget target) {
            this.session = session; this.target = target;
        }
    }
    public synchronized Observation observation() { return new Observation(session, target); }

    /** New actual connection attempt supersedes observation of any older attempt. */
    public synchronized long beginAttempt() { return beginAttempt(null); }

    public synchronized long beginAttempt(HorizonLaunchTarget launchTarget) {
        if (session.ticket == Long.MAX_VALUE) return 0L;
        if (!session.terminal()) {
            session = session.transition(session.ticket, HorizonSession.Event.FINISHED);
        }
        session = session.begin(session.ticket + 1L);
        target = launchTarget;
        return session.ticket;
    }

    public synchronized void connected(long ticket) {
        apply(ticket, HorizonSession.Event.STARTED);
    }

    public synchronized void failed(long ticket) {
        apply(ticket, HorizonSession.Event.FAILURE);
    }

    public synchronized void ended(long ticket) {
        apply(ticket, HorizonSession.Event.FINISHED);
    }

    private void apply(long ticket, HorizonSession.Event event) {
        if (ticket > 0L) session = session.transition(ticket, event);
    }
}
