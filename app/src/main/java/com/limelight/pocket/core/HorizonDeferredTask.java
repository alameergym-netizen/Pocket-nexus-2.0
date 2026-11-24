package com.limelight.pocket.core;

/** A bounded, single-use continuation tied to one stream and one launcher selection. */
public final class HorizonDeferredTask {
    private final long ticket, generation, started, limit;
    private boolean finished;

    public HorizonDeferredTask(long ticket, long generation, long started, long limit) {
        this.ticket = ticket;
        this.generation = generation;
        this.started = started;
        this.limit = limit;
    }

    private boolean owner(long currentTicket, long currentGeneration, long now) {
        return !finished && ticket > 0 && ticket == currentTicket && generation >= 0
                && generation == currentGeneration && started >= 0 && now >= started && limit > 0;
    }

    public synchronized boolean current(long currentTicket, long currentGeneration, long now) {
        return owner(currentTicket, currentGeneration, now) && now - started < limit;
    }

    public synchronized boolean claim(long currentTicket, long currentGeneration, long now) {
        if (!current(currentTicket, currentGeneration, now)) return false;
        finished = true;
        return true;
    }

    /** The deadline may deliver a failure instead of the expired success callback. */
    public synchronized boolean expire(long currentTicket, long currentGeneration, long now) {
        if (!owner(currentTicket, currentGeneration, now) || now - started < limit) return false;
        finished = true;
        return true;
    }

    public synchronized void cancel() { finished = true; }
}
