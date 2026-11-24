package com.limelight.pocket.core;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.limelight.pocket.core.HorizonSession.Event.*;
import static com.limelight.pocket.core.HorizonSession.State.LAUNCHING;
import static com.limelight.pocket.core.HorizonSession.State.RUNNING;
import static com.limelight.pocket.core.HorizonSession.State.ENDING;
import static com.limelight.pocket.core.HorizonSession.State.ENDED;
import static com.limelight.pocket.core.HorizonSession.State.RECONNECTING;
import static com.limelight.pocket.core.HorizonSession.State.FAILED;

public class HorizonSessionTest {
    @Test public void onlyOneActiveTicket() {
        HorizonSession idle = HorizonSession.idle();
        assertSame(idle, idle.begin(0));
        HorizonSession session = idle.begin(1);
        assertEquals(LAUNCHING, session.state);
        assertSame(session, session.begin(2));
        assertSame(session, session.transition(2, STARTED));
    }
    @Test public void normalLifecycle() {
        HorizonSession s = HorizonSession.idle().begin(1).transition(1, STARTED);
        assertEquals(RUNNING, s.state);
        s = s.transition(1, BACKGROUND);
        assertEquals(HorizonSession.State.BACKGROUND, s.state);
        s = s.transition(1, FOREGROUND);
        assertEquals(RUNNING, s.state);
        s = s.transition(1, STOP);
        assertEquals(ENDING, s.state);
        assertSame(s, s.transition(1, STARTED));
        assertEquals(ENDED, s.transition(1, FINISHED).state);
    }
    @Test public void reconnectAndRejectDuplicateEvents() {
        HorizonSession s = HorizonSession.idle().begin(5).transition(5, STARTED);
        s = s.transition(5, CONNECTION_LOST);
        assertEquals(RECONNECTING, s.state);
        assertSame(s, s.transition(5, CONNECTION_LOST));
        s = s.transition(5, RECONNECTED);
        assertEquals(RUNNING, s.state);
        assertSame(s, s.transition(5, RECONNECTED));
    }
    @Test public void staleCallbacksCannotChangeNextSession() {
        HorizonSession s = HorizonSession.idle().begin(10).transition(10, FAILURE);
        assertEquals(FAILED, s.state);
        assertSame(s, s.begin(10));
        s = s.begin(11);
        assertSame(s, s.transition(10, FINISHED));
        assertSame(s, s.transition(10, FAILURE));
        assertEquals(RUNNING, s.transition(11, STARTED).state);
    }
    @Test public void terminalStateIgnoresLateEvents() {
        HorizonSession s = HorizonSession.idle().begin(1).transition(1, FINISHED);
        assertTrue(s.terminal());
        assertSame(s, s.transition(1, FAILURE));
        assertSame(s, s.transition(1, null));
        assertEquals(LAUNCHING, s.begin(2).state);
    }
}
