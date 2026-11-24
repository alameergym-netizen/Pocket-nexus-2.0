package com.limelight.pocket.core;
import com.limelight.pocket.home.HomeItem;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonLaunchRequestsTest {
    private HorizonLaunchTarget item(String kind, String a, String b, String c, int n) {
        return HorizonLaunchTarget.from(new HomeItem(kind, "Title", a, b, c, n));
    }
    @Test public void pcIdentityDoesNotDependOnLabel() {
        assertEquals(HorizonLaunchTarget.pc("pc", 12),
                HorizonLaunchTarget.from(new HomeItem(HomeItem.PC, "Renamed", "pc", "", "", 12)));
        assertNotEquals(HorizonLaunchTarget.pc("pc",12), HorizonLaunchTarget.pc("pc2",12));
    }
    @Test public void nativeStoreAndRetroRootAreDistinct() {
        assertNotEquals(item(HomeItem.NATIVE,"gamenative","STEAM","",7),
                item(HomeItem.NATIVE,"gamenative","GOG","",7));
        assertEquals(item(HomeItem.NATIVE,"gamenative","","",7),
                item(HomeItem.NATIVE,"gamenative","steam","",7));
        assertNotEquals(item(HomeItem.RETRO,"ps2","game.iso","sd",0),
                item(HomeItem.RETRO,"ps2","game.iso","usb",0));
    }
    @Test public void structuredFieldsAvoidDelimiterCollision() {
        assertNotEquals(item(HomeItem.RETRO,"a:b","c","",0),
                item(HomeItem.RETRO,"a","b:c","",0));
    }
    @Test public void repeatDoesNotDispatchSecondGame() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        HorizonLaunchTarget t = HorizonLaunchTarget.pc("pc",12);
        long first = q.begin(t,100);
        assertTrue(first > 0);
        assertEquals(0L,q.begin(t,200));
        assertTrue(q.begin(t,800)>first);
    }
    @Test public void newSelectionInvalidatesOldCallback() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long first = q.begin(item(HomeItem.RETRO,"ps2","game.iso","sd",0),100);
        long next = q.begin(item(HomeItem.NEXUS,"files","","",0),200);
        assertFalse(q.claim(first,210));
        assertTrue(q.claim(next,210));
        assertFalse(q.claim(next,220));
    }
    @Test public void expiredAndReversedClockCallbacksRejected() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long t = q.begin(HorizonLaunchTarget.pc("pc",1),100);
        assertFalse(q.claim(t,99));
        assertFalse(q.claim(t,15101));
    }
    @Test public void pcWakeHasBoundedLongerOwnership() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long ticket = q.beginPc(HorizonLaunchTarget.pc("pc",1),100);
        assertTrue(q.current(ticket,75100));
        assertTrue(q.claim(ticket,75100));
        assertFalse(q.current(ticket,75101));
        assertFalse(q.claim(ticket,75102));
        long next = q.beginPc(HorizonLaunchTarget.pc("pc",2),80000);
        assertFalse(q.current(next,200001));
    }
    @Test public void cancelAndSupersedeCannotLaunchOldRequest() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long old = q.beginPc(HorizonLaunchTarget.pc("pc",1),100);
        long next = q.beginPc(HorizonLaunchTarget.pc("pc",2),200);
        q.cancel(old);
        assertTrue(q.current(next,300));
        q.cancel(next);
        assertFalse(q.claim(next,400));
        assertTrue(q.beginPc(HorizonLaunchTarget.pc("pc",2),401)>next);
    }
    @Test public void longPcWindowDoesNotApplyToRetro() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        HorizonLaunchTarget retro = item(HomeItem.RETRO,"ps2","game.iso","sd",0);
        assertEquals(0L,q.beginPc(retro,100));
        long ticket = q.begin(retro,100);
        assertFalse(q.current(ticket,15101));
    }
    @Test public void activityHandoffClaimsOriginalRequestWithoutSecondBegin() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long request = q.beginPc(HorizonLaunchTarget.pc("pc",1),100);
        assertTrue(q.current(request,101));
        assertEquals(0L,q.beginPc(HorizonLaunchTarget.pc("pc",1),102));
        assertTrue(q.claim(request,30100));
        assertFalse(q.claim(request,30101));
    }
    @Test public void supersededHandoffCannotDispatchAfterDiscovery() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long request = q.beginPc(HorizonLaunchTarget.pc("pc",1),100);
        long newer = q.beginPc(HorizonLaunchTarget.pc("pc",2),200);
        q.cancel(request);
        assertFalse(q.claim(request,300));
        assertTrue(q.claim(newer,300));
    }
    @Test public void abandonedHandoffCanBeRetried() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long request = q.beginPc(HorizonLaunchTarget.pc("pc",1),100);
        q.cancel(request);
        assertFalse(q.current(request,101));
        assertTrue(q.beginPc(HorizonLaunchTarget.pc("pc",1),102)>request);
    }
    @Test public void missingUuidsDoNotMatchDifferentRunningGames() {
        assertFalse(HorizonLaunchTarget.sameRunningPcApp(17, null, 18, null));
        assertFalse(HorizonLaunchTarget.sameRunningPcApp(17, "", 18, ""));
        assertFalse(HorizonLaunchTarget.sameRunningPcApp(-1, null, -1, null));
        assertTrue(HorizonLaunchTarget.sameRunningPcApp(17, null, 17, ""));
    }
    @Test public void nonemptyUuidTakesPrecedenceOverNumericFallback() {
        assertTrue(HorizonLaunchTarget.sameRunningPcApp(17, "ABC", -1, "abc"));
        assertFalse(HorizonLaunchTarget.sameRunningPcApp(17, "abc", 17, "def"));
        assertTrue(HorizonLaunchTarget.sameRunningPcApp(17, "abc", 17, null));
    }
    @Test public void numericShortcutSharesOrdinaryPcTarget() {
        assertEquals(HorizonLaunchTarget.pc("pc", 17),
                HorizonLaunchTarget.pcShortcut("pc", null, 17, "renamed"));
    }
    @Test public void uuidOnlyShortcutsAndHostAreDistinct() {
        HorizonLaunchTarget a = HorizonLaunchTarget.pcShortcut("pc", "a", -1, null);
        HorizonLaunchTarget b = HorizonLaunchTarget.pcShortcut("pc", "b", -1, null);
        assertNotEquals(a, b);
        assertNotEquals(a, HorizonLaunchTarget.pcShortcut("pc", null, 0, null));
        assertNotEquals(a, HorizonLaunchTarget.pc("pc", -1));
    }
    @Test public void shortcutCannotDispatchAfterNewSelectionOrPause() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        long old = q.beginPc(HorizonLaunchTarget.pcShortcut("pc", "a", -1, null), 100);
        long next = q.beginPc(HorizonLaunchTarget.pc("pc", 17), 200);
        assertFalse(q.claim(old, 201));
        q.cancel(next);
        assertFalse(q.claim(next, 202));
    }
    @Test public void toolsAreNotDebounced() {
        HorizonLaunchRequests q = new HorizonLaunchRequests();
        HorizonLaunchTarget t = item(HomeItem.NEXUS,"music","","",0);
        assertTrue(q.begin(t,100)>0);
        assertTrue(q.begin(t,101)>0);
    }
    @Test public void observationPairsTargetAndTicketAndRejectsStaleCallback() {
        HorizonStreamObserver o = new HorizonStreamObserver();
        HorizonLaunchTarget a = HorizonLaunchTarget.pc("pc",1);
        HorizonLaunchTarget b = HorizonLaunchTarget.pc("pc",2);
        long old = o.beginAttempt(a);
        long next = o.beginAttempt(b);
        o.connected(old);
        assertEquals(b,o.observation().target);
        assertEquals(next,o.observation().session.ticket);
        assertEquals(HorizonSession.State.LAUNCHING,o.observation().session.state);
        o.connected(next);
        assertEquals(HorizonSession.State.RUNNING,o.observation().session.state);
    }
    @Test public void classifiesUncomparablePcAppIdentitySeparatelyFromDifferent() {
        assertEquals(HorizonLaunchTarget.PcAppMatch.IDLE,
                HorizonLaunchTarget.compareRunningPcApp(0, "", 23, null));
        assertEquals(HorizonLaunchTarget.PcAppMatch.UNKNOWN,
                HorizonLaunchTarget.compareRunningPcApp(23, null, 0, "uuid-only"));
        assertEquals(HorizonLaunchTarget.PcAppMatch.UNKNOWN,
                HorizonLaunchTarget.compareRunningPcApp(0, "uuid-only", 23, null));
        assertEquals(HorizonLaunchTarget.PcAppMatch.DIFFERENT,
                HorizonLaunchTarget.compareRunningPcApp(23, null, 24, null));
        assertEquals(HorizonLaunchTarget.PcAppMatch.DIFFERENT,
                HorizonLaunchTarget.compareRunningPcApp(23, "running", 23, "requested"));
        assertEquals(HorizonLaunchTarget.PcAppMatch.SAME,
                HorizonLaunchTarget.compareRunningPcApp(0, "UUID-ONLY", 0, "uuid-only"));
    }

}
