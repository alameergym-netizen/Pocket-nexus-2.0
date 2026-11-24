package com.limelight.pocket.core;

/**
 * Pure session lifecycle policy. No Android dependencies or side effects.
 * A single active ticket owns transitions; stale callbacks cannot mutate a new session.
 * This is a foundation for later adapters, not yet a runtime session manager.
 */
public final class HorizonSession {
    public enum State { IDLE, LAUNCHING, RUNNING, BACKGROUND, RECONNECTING, ENDING, ENDED, FAILED }
    public enum Event { STARTED, BACKGROUND, FOREGROUND, CONNECTION_LOST, RECONNECTED, STOP, FINISHED, FAILURE }

    public final long ticket;
    public final State state;
    private HorizonSession(long ticket, State state) {
        this.ticket = ticket;
        this.state = state;
    }
    public static HorizonSession idle() {
        return new HorizonSession(0, State.IDLE);
    }
    public boolean terminal() {
        return state == State.IDLE || state == State.ENDED || state == State.FAILED;
    }
    /** Caller supplies a monotonic positive ticket; reuse or concurrent starts are rejected. */
    public HorizonSession begin(long nextTicket) {
        if (!terminal() || nextTicket <= ticket) return this;
        return new HorizonSession(nextTicket, State.LAUNCHING);
    }
    public HorizonSession transition(long eventTicket, Event event) {
        if (eventTicket != ticket || terminal() || event == null) return this;
        State next = state;
        if (event == Event.FAILURE) next = State.FAILED;
        else if (event == Event.FINISHED) next = State.ENDED;
        else if (event == Event.STOP) next = State.ENDING;
        else switch (state) {
            case LAUNCHING:
                if (event == Event.STARTED) next = State.RUNNING;
                break;
            case RUNNING:
                if (event == Event.BACKGROUND) next = State.BACKGROUND;
                else if (event == Event.CONNECTION_LOST) next = State.RECONNECTING;
                break;
            case BACKGROUND:
                if (event == Event.FOREGROUND) next = State.RUNNING;
                else if (event == Event.CONNECTION_LOST) next = State.RECONNECTING;
                break;
            case RECONNECTING:
                if (event == Event.RECONNECTED) next = State.RUNNING;
                break;
            default:
                break;
        }
        return next == state ? this : new HorizonSession(ticket, next);
    }
}
