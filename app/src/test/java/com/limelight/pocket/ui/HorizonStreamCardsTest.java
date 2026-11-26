package com.limelight.pocket.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.limelight.pocket.ui.HorizonStreamCards.Host;
import com.limelight.pocket.ui.HorizonStreamCards.Link;
import com.limelight.pocket.ui.HorizonStreamCards.Reach;
import com.limelight.pocket.ui.HorizonStreamCards.Route;
import com.limelight.pocket.ui.HorizonStreamCards.Session;
import com.limelight.pocket.ui.HorizonStreamCards.SessionKind;
import com.limelight.pocket.ui.HorizonStreamCards.Transport;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HorizonStreamCardsTest {
    private static final long NOW = 1_000_000;

    @After public void forget() {
        HorizonStreamCards.forgetObservations();
    }

    private static Host host(String id, Reach reach, long observedAt, int running, String lastGame, long lastPlayed) {
        return new Host(id, id + "-pc", reach, observedAt, Route.LOCAL, running, running != 0 ? "Running " + id : null,
                lastGame, lastPlayed);
    }

    @Test public void aPollIsOnlyAnAnswerWhileItIsFresh() {
        Host fresh = host("a", Reach.ONLINE, NOW - 5_000, 0, null, 0);
        Host old = host("b", Reach.ONLINE, NOW - HorizonStreamCards.FRESH_MS - 1, 0, null, 0);
        Host never = host("c", Reach.OFFLINE, 0, 0, null, 0);
        Host future = host("d", Reach.OFFLINE, NOW + 10, 0, null, 0);
        assertEquals(Reach.ONLINE, fresh.reachAt(NOW));
        assertEquals(Reach.UNKNOWN, old.reachAt(NOW));
        assertEquals(Reach.UNKNOWN, never.reachAt(NOW));
        assertEquals(Reach.UNKNOWN, future.reachAt(NOW));
    }

    @Test public void aRunningGameNeedsAFreshOnlineReport() {
        assertTrue(host("a", Reach.ONLINE, NOW, 7, null, 0).runningAt(NOW));
        assertFalse(host("a", Reach.OFFLINE, NOW, 7, null, 0).runningAt(NOW));
        assertFalse(host("a", Reach.ONLINE, NOW - HorizonStreamCards.FRESH_MS - 1, 7, null, 0).runningAt(NOW));
        assertFalse(host("a", Reach.ONLINE, NOW, 0, null, 0).runningAt(NOW));
    }

    @Test public void theHostCardPrefersRunningThenOnlineThenPlayedLast() {
        Host idleOld = host("old", Reach.UNKNOWN, 0, 0, "Aster", 100);
        Host idleNew = host("new", Reach.UNKNOWN, 0, 0, "Boreal", 200);
        Host on = host("on", Reach.ONLINE, NOW, 0, null, 0);
        Host running = host("run", Reach.ONLINE, NOW, 3, null, 0);
        assertNull(HorizonStreamCards.featured(Collections.<Host>emptyList(), NOW));
        assertSame(idleNew, HorizonStreamCards.featured(Arrays.asList(idleOld, idleNew), NOW));
        assertSame(on, HorizonStreamCards.featured(Arrays.asList(idleOld, idleNew, on), NOW));
        assertSame(running, HorizonStreamCards.featured(Arrays.asList(idleOld, on, running, idleNew), NOW));
    }

    @Test public void theSessionIsOnlyCalledIdleWhenEveryPcAnswered() {
        assertEquals(SessionKind.NO_HOSTS, HorizonStreamCards.session(null, NOW).kind);
        Host a = host("a", Reach.OFFLINE, NOW, 0, "Aster", 300);
        Host b = host("b", Reach.UNKNOWN, 0, 0, "Boreal", 200);
        Session unknown = HorizonStreamCards.session(Arrays.asList(a, b), NOW);
        assertEquals(SessionKind.UNKNOWN, unknown.kind);
        assertSame("the game played last is still offered", a, unknown.last);
        Host bAnswered = host("b", Reach.ONLINE, NOW, 0, "Boreal", 200);
        assertEquals(SessionKind.IDLE, HorizonStreamCards.session(Arrays.asList(a, bAnswered), NOW).kind);
        Host bRunning = host("b", Reach.ONLINE, NOW, 9, "Boreal", 200);
        Session running = HorizonStreamCards.session(Arrays.asList(a, bRunning), NOW);
        assertEquals(SessionKind.RUNNING, running.kind);
        assertSame(bRunning, running.host);
        assertEquals("Running b", running.host.runningName);
    }

    @Test public void theRouteIsOnlyNamedWhenTheAddressMatches() {
        assertEquals(Route.LOCAL, HorizonStreamCards.route("192.168.1.5", "192.168.1.5", "8.8.4.4", null));
        assertEquals(Route.REMOTE, HorizonStreamCards.route("8.8.4.4", "192.168.1.5", "8.8.4.4", null));
        assertEquals(Route.MANUAL, HorizonStreamCards.route("100.64.0.2", "192.168.1.5", null, "100.64.0.2"));
        assertEquals(Route.UNKNOWN, HorizonStreamCards.route("10.0.0.9", "192.168.1.5", "8.8.4.4", null));
        assertEquals(Route.UNKNOWN, HorizonStreamCards.route(null, "192.168.1.5", null, null));
        assertEquals(Route.UNKNOWN, HorizonStreamCards.route("", "", "", ""));
    }

    @Test public void unknownNetworkNumbersStayUnknown() {
        Link link = new Link(Transport.WIFI, false, 33, 9, 0);
        assertEquals(0, link.bandGhz10);
        assertEquals(-1, link.signal);
        assertEquals(-1, link.linkMbps);
        assertNull(HorizonStreamCards.band(link.bandGhz10));
        assertEquals("5 GHz", HorizonStreamCards.band(50));
        assertEquals(Transport.NONE, new Link(null, true, 0, 0, 0).transport);
    }

    @Test public void cardsSitSideBySideOnlyWhenEachHasRoom() {
        assertTrue(HorizonStreamCards.sideBySide(3 * HorizonStreamCards.MIN_CARD_DP + 16, 3));
        assertFalse(HorizonStreamCards.sideBySide(3 * HorizonStreamCards.MIN_CARD_DP + 15, 3));
        assertFalse(HorizonStreamCards.sideBySide(1000, 0));
    }

    @Test public void otherScreensSeeWhatHomeObservedWithoutLosingTheirOwnHistory() {
        Host saved = new Host("a", "Aster PC", Reach.UNKNOWN, 0, Route.UNKNOWN, 0, null, "Cinder Quest", 500);
        HorizonStreamCards.observe(new Host("a", "Aster PC", Reach.ONLINE, NOW, Route.LOCAL, 4, "Boreal Drift", null, 0));
        HorizonStreamCards.observe(new Host("never", "x", Reach.ONLINE, 0, Route.LOCAL, 0, null, null, 0));
        List<Host> merged = HorizonStreamCards.withObservations(Arrays.asList(saved));
        Host a = merged.get(0);
        assertEquals(Reach.ONLINE, a.reachAt(NOW));
        assertEquals(Route.LOCAL, a.route);
        assertEquals("Boreal Drift", a.runningName);
        assertEquals("Cinder Quest", a.lastGame);
        assertEquals(500, a.lastPlayedAt);
        assertNull("an observation without a time is not kept", HorizonStreamCards.observed("never"));
        assertEquals(Reach.UNKNOWN, a.reachAt(NOW + HorizonStreamCards.FRESH_MS + 1));
    }

    @Test public void namesFallBackToTheIdAndBlanksAreDropped() {
        Host h = new Host("uuid-1", "  ", null, -5, null, -2, " ", "", -1);
        assertEquals("uuid-1", h.name);
        assertEquals(Reach.UNKNOWN, h.reach);
        assertEquals(0, h.observedAt);
        assertEquals(Route.UNKNOWN, h.route);
        assertEquals(0, h.runningAppId);
        assertNull(h.runningName);
        assertNull(h.lastGame);
        assertEquals(0, h.lastPlayedAt);
    }
}
