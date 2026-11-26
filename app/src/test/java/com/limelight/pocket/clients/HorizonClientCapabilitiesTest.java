package com.limelight.pocket.clients;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.limelight.pocket.clients.HorizonClientRegistry.Capability;
import com.limelight.pocket.clients.HorizonClientRegistry.Client;
import com.limelight.pocket.clients.HorizonClientRegistry.Group;
import com.limelight.pocket.clients.HorizonClientRegistry.Mode;
import com.limelight.pocket.clients.HorizonClientRegistry.Session;

import org.junit.Test;

import java.util.EnumSet;

/** X08: every client states what Pocket Nexus does with it and what it offers instead, honestly. */
public class HorizonClientCapabilitiesTest {
    private static Client get(String id) {
        Client c = HorizonClientRegistry.byId(id);
        assertNotNull(id, c);
        return c;
    }

    @Test public void nothingIsClaimedForResearchOrUnverifiedProjects() {
        for (Client c : HorizonClientRegistry.ALL) {
            if (c.mode == Mode.RESEARCH || c.mode == Mode.UNRESOLVED) {
                assertTrue(c.id + " claims " + c.capabilities(), c.capabilities().isEmpty());
            }
        }
    }

    @Test public void onlyTheBuiltInStreamIsSeenAndResumed() {
        for (Client c : HorizonClientRegistry.ALL) {
            boolean observed = c.session == Session.OBSERVED;
            assertEquals(c.id, observed, c.can(Capability.RESUMES));
            assertEquals(c.id, observed || c.session == Session.MEDIA_CONTROLS, c.can(Capability.SEES_SESSION));
        }
        assertTrue(get("nexus-pc-stream").capabilities().containsAll(EnumSet.of(Capability.OPENS,
                Capability.STARTS_GAMES, Capability.LIBRARY, Capability.SEES_SESSION, Capability.RESUMES)));
        assertFalse("a cloud service runs its own session", get("xcloud-player").can(Capability.SEES_SESSION));
    }

    @Test public void gamesInTheLibraryAreGamesPocketNexusCanStart() {
        for (Client c : HorizonClientRegistry.ALL) {
            if (c.can(Capability.LIBRARY)) {
                assertTrue(c.id, c.can(Capability.STARTS_GAMES));
                assertTrue(c.id, c.can(Capability.OPENS));
            }
        }
        assertTrue(get("xcloud-player").can(Capability.LIBRARY));
        assertTrue(get("gamenative").can(Capability.LIBRARY));
        assertFalse("only xCloud titles are saved as games", get("gfn-player").can(Capability.STARTS_GAMES));
        assertFalse(get("boosteroid-player").can(Capability.STARTS_GAMES));
    }

    @Test public void pcSoftwareIsConnectedToNotOpened() {
        for (Client c : HorizonClientRegistry.ALL) {
            if (c.mode == Mode.PC_HOST) {
                assertEquals(c.id, EnumSet.of(Capability.CONNECTS), c.capabilities());
            }
            else {
                assertFalse(c.id, c.can(Capability.CONNECTS));
            }
        }
    }

    @Test public void catalogsAreSourcesNotApps() {
        for (Client c : HorizonClientRegistry.inGroup(Group.SOURCES)) {
            assertFalse(c.id, c.can(Capability.OPENS));
        }
    }

    @Test public void everyFallbackIsAnAvailableClientOfTheSameKind() {
        int withFallback = 0;
        for (Client c : HorizonClientRegistry.ALL) {
            String id = c.fallback();
            if (id == null) continue;
            withFallback++;
            Client f = HorizonClientRegistry.byId(id);
            assertNotNull(c.id + " falls back to an unknown client", f);
            assertNotEquals(c.id, f.id);
            assertTrue(c.id + " -> " + f.id, f.mode == Mode.BUILT_IN || f.mode == Mode.INSTALLED_APP);
            assertEquals(c.id + " -> " + f.id, c.group, f.group);
            assertTrue(f.id, f.can(Capability.OPENS));
            assertEquals("no chains: a fallback stands on its own", null, f.fallback());
        }
        assertTrue(withFallback >= 10);
        assertEquals("xcloud-player", get("xbox-app").fallback());
        assertEquals("gfn-player", get("gfn-app").fallback());
        assertEquals("boosteroid-player", get("boosteroid-app").fallback());
        assertEquals("nexus-pc-stream", get("starwire").fallback());
        assertEquals("ps-remote", get("chiaki-ng").fallback());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void capabilitiesAreReadOnly() {
        get("nexus-pc-stream").capabilities().clear();
    }
}
