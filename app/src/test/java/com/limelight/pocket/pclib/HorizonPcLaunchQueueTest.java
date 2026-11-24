package com.limelight.pocket.pclib;
import org.junit.Test;
import static org.junit.Assert.*;
public class HorizonPcLaunchQueueTest {
    @Test public void profileAndCommandBelongToSameTicket() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        q.arm("pc",2,"START steam 10","Game",42,100);
        HorizonPcLaunchQueue.Ticket t=q.snapshotFor("pc",2,110);
        assertEquals(42,t.profile);
        assertArrayEquals(new String[]{"START steam 10","Game"},q.take(t,"pc",2,120));
        assertNull(q.take(t,"pc",2,121));
    }
    @Test public void olderStreamCannotConsumeReplacement() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        q.arm("pc",2,"old","Old",42,100);
        HorizonPcLaunchQueue.Ticket old=q.snapshotFor("pc",2,110);
        q.arm("pc",2,"new","New",43,120);
        assertNull(q.take(old,"pc",2,130));
        assertEquals("new",q.take(q.snapshotFor("pc",2,130),"pc",2,140)[0]);
    }
    @Test public void differentHostCannotConsumeOrClear() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        q.arm("pc",2,"cmd","Game",42,100);
        HorizonPcLaunchQueue.Ticket t=q.snapshotFor("pc",2,110);
        assertNull(q.snapshotFor("other",2,115));
        assertNull(q.take(t,"other",2,120));
        assertNotNull(q.take(t,"pc",2,130));
    }
    @Test public void differentStreamInvalidatesPendingForSameHost() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        q.arm("pc",2,"cmd","Game",42,100);
        HorizonPcLaunchQueue.Ticket t=q.snapshotFor("pc",2,110);
        assertNull(q.snapshotFor("pc",3,120));
        assertNull(q.take(t,"pc",2,130));
    }
    @Test public void expiryAndInvalidNewArmFailClosed() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        q.arm("pc",2,"cmd","Game",42,100);
        HorizonPcLaunchQueue.Ticket t=q.snapshotFor("pc",2,110);
        assertNull(q.take(t,"pc",2,180101));
        q.arm("pc",2,"cmd","Game",42,200000);
        t=q.snapshotFor("pc",2,200010);
        q.arm("pc",2,"","Game",42,200020);
        assertNull(q.take(t,"pc",2,200030));
    }
    @Test public void noSnapshotCannotPickUpLaterWish() {
        HorizonPcLaunchQueue q=new HorizonPcLaunchQueue();
        q.arm("pc",2,"cmd","Game",42,100);
        assertNull(q.take(null,"pc",2,110));
        assertNotNull(q.snapshotFor("pc",2,120));
    }
}
