package com.limelight.pocket.pclib;
import org.junit.Test;
import static org.junit.Assert.*;
public class HorizonPcLaunchQueueTest {
    @Test public void profileAndCommandBelongToSameTicket() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        String token=q.arm("pc",2,"START steam 10","Game",42,100);
        HorizonPcLaunchQueue.Ticket t=q.snapshotFor(token,"pc",2,110);
        assertEquals(42,t.profile);
        assertArrayEquals(new String[]{"START steam 10","Game"},q.take(t,"pc",2,120));
        assertNull(q.take(t,"pc",2,121));
    }
    @Test public void olderStreamCannotConsumeReplacement() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        String first=q.arm("pc",2,"old","Old",42,100);
        HorizonPcLaunchQueue.Ticket old=q.snapshotFor(first,"pc",2,110);
        String next=q.arm("pc",2,"new","New",43,120);
        assertNull(q.take(old,"pc",2,130));
        assertNull(q.snapshotFor(first,"pc",2,130));
        assertEquals("new",q.take(q.snapshotFor(next,"pc",2,130),"pc",2,140)[0]);
    }
    @Test public void differentHostCannotConsumeOrClear() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        String token=q.arm("pc",2,"cmd","Game",42,100);
        HorizonPcLaunchQueue.Ticket t=q.snapshotFor(token,"pc",2,110);
        assertNull(q.snapshotFor(token,"other",2,115));
        assertNull(q.take(t,"other",2,120));
        assertNotNull(q.take(t,"pc",2,130));
    }
    @Test public void wrongAppCannotCaptureOrClearExplicitWish() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        String token=q.arm("pc",2,"cmd","Game",42,100);
        assertNull(q.snapshotFor(token,"pc",3,120));
        assertNotNull(q.snapshotFor(token,"pc",2,130));
    }
    @Test public void expiryAndInvalidNewArmFailClosed() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        String token=q.arm("pc",2,"cmd","Game",42,100);
        HorizonPcLaunchQueue.Ticket t=q.snapshotFor(token,"pc",2,110);
        assertNull(q.take(t,"pc",2,180101));
        token=q.arm("pc",2,"cmd","Game",42,200000);
        t=q.snapshotFor(token,"pc",2,200010);
        assertNull(q.arm("pc",2,"","Game",42,200020));
        assertNull(q.take(t,"pc",2,200030));
    }
    @Test public void clearingHostDoesNotAffectOtherHost() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        String token=q.arm("pc",2,"cmd","Game",42,100);
        HorizonPcLaunchQueue.Ticket t=q.snapshotFor(token,"pc",2,110);
        q.clearHost("other");
        assertNotNull(q.snapshotFor(token,"pc",2,120));
        q.clearHost("pc");
        assertNull(q.take(t,"pc",2,130));
    }
    @Test public void noSnapshotCannotPickUpLaterWish() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        String token=q.arm("pc",2,"cmd","Game",42,100);
        assertNull(q.take(null,"pc",2,110));
        assertNotNull(q.snapshotFor(token,"pc",2,120));
    }
    @Test public void ordinaryDesktopNeverCapturesPendingCommand() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        String token=q.arm("pc",2,"cmd","Game",42,100);
        assertNull(q.snapshotFor(null,"pc",2,110));
        assertNull(q.snapshotFor("unrelated","pc",2,111));
        assertNotNull(q.snapshotFor(token,"pc",2,112));
    }
    @Test public void explicitTokenDoesNotSurviveProcessReplacement() {
        HorizonPcLaunchQueue old=new HorizonPcLaunchQueue(), fresh=new HorizonPcLaunchQueue();
        String token=old.arm("pc",2,"cmd","Game",42,100);
        String newer=fresh.arm("pc",2,"new","New",43,100);
        assertNotEquals(token,newer);
        assertNull(fresh.snapshotFor(token,"pc",2,110));
    }
    @Test public void copiedIntentCannotExecuteCommandTwice() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        String token=q.arm("pc",2,"cmd","Game",42,100);
        HorizonPcLaunchQueue.Ticket one=q.snapshotFor(token,"pc",2,110), two=q.snapshotFor(token,"pc",2,111);
        assertNotNull(q.take(one,"pc",2,112));
        assertNull(q.take(two,"pc",2,113));
    }
}
