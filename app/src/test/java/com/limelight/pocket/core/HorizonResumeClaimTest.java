package com.limelight.pocket.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class HorizonResumeClaimTest {
    @Test public void dispatchIsSingleUse() {
        HorizonResumeClaim q=new HorizonResumeClaim();
        HorizonResumeClaim.Claim t=q.begin(100,60000,101,1,2);
        assertNull(q.begin(100,60000,102,1,2));
        assertSame(t,q.finish(t.id,103,1));
        assertNull(q.finish(t.id,104,1));
    }
    @Test public void newerSelectionRejectsOldResume() {
        HorizonResumeClaim q=new HorizonResumeClaim();
        HorizonResumeClaim.Claim t=q.begin(100,60000,101,1,2);
        assertFalse(q.current(t.id,102,2));
        assertNull(q.finish(t.id,103,2));
        assertNotNull(q.begin(104,60000,105,2,0));
    }
    @Test public void restorationKeepsOriginalExpiry() {
        HorizonResumeClaim q=new HorizonResumeClaim();
        HorizonResumeClaim.Claim t=q.begin(100,60000,59999,1,2);
        HorizonResumeClaim.Claim restored=q.finish(t.id,60000,1);
        assertEquals(100,restored.armedAt);
        assertEquals(2,restored.reason);
        assertNull(q.begin(restored.armedAt,restored.maxAge,60100,1,restored.reason));
    }
    @Test public void lateCancelCannotRemoveNewClaim() {
        HorizonResumeClaim q=new HorizonResumeClaim();
        HorizonResumeClaim.Claim a=q.begin(100,60000,101,1,0);
        q.invalidate();
        HorizonResumeClaim.Claim b=q.begin(200,60000,201,2,0);
        q.cancel(a.id);
        assertTrue(q.current(b.id,202,2));
    }
    @Test public void reversedClockAndExpiredClaimsFail() {
        HorizonResumeClaim q=new HorizonResumeClaim();
        assertNull(q.begin(100,60000,99,1,0));
        HorizonResumeClaim.Claim t=q.begin(100,60000,101,1,0);
        assertFalse(q.current(t.id,99,1));
        assertNull(q.finish(t.id,60100,1));
    }
}
