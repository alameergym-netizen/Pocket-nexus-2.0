package com.limelight.pocket.core;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.limelight.pocket.core.HorizonStreamDispatch.Stage.*;

public class HorizonStreamDispatchTest {
    @Test public void bothStagesRequiredInEitherOrder() {
        HorizonStreamDispatch g=new HorizonStreamDispatch();
        long a=g.begin(1,100);
        assertFalse(g.arrive(a,ANIMATION,1,101));
        assertFalse(g.arrive(a,ANIMATION,1,102));
        assertTrue(g.arrive(a,SIGN_IN,1,103));
        assertFalse(g.arrive(a,SIGN_IN,1,104));
        long b=g.begin(1,200);
        assertFalse(g.arrive(b,SIGN_IN,1,201));
        assertTrue(g.arrive(b,ANIMATION,1,202));
    }
    @Test public void pauseOrBackRejectsLateCompletion() {
        HorizonStreamDispatch g=new HorizonStreamDispatch();
        long a=g.begin(1,100);
        g.arrive(a,ANIMATION,1,101);
        g.cancel(a);
        assertFalse(g.arrive(a,SIGN_IN,1,102));
    }
    @Test public void oldScreenCannotCancelNewDispatch() {
        HorizonStreamDispatch g=new HorizonStreamDispatch();
        long a=g.begin(1,100), b=g.begin(2,200);
        g.cancel(a);
        assertFalse(g.arrive(a,SIGN_IN,2,201));
        assertFalse(g.arrive(b,SIGN_IN,2,202));
        assertTrue(g.arrive(b,ANIMATION,2,203));
    }
    @Test public void newerLaunchSelectionInvalidatesOldRendezvous() {
        HorizonStreamDispatch g=new HorizonStreamDispatch();
        long a=g.begin(4,100);
        g.arrive(a,ANIMATION,4,101);
        assertFalse(g.arrive(a,SIGN_IN,5,102));
    }
    @Test public void expiresAndRejectsReversedClock() {
        HorizonStreamDispatch g=new HorizonStreamDispatch();
        long a=g.begin(1,100);
        assertFalse(g.pending(a,1,99));
        assertTrue(g.pending(a,1,15099));
        assertFalse(g.pending(a,1,15100));
        assertFalse(g.arrive(a,SIGN_IN,1,15101));
    }
}
