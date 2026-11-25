package com.limelight.pocket.clients;

import com.limelight.pocket.clients.HorizonClientRegistry.Availability;
import com.limelight.pocket.clients.HorizonClientRegistry.Client;
import com.limelight.pocket.clients.HorizonClientRegistry.Group;
import com.limelight.pocket.clients.HorizonClientRegistry.Mode;
import com.limelight.pocket.clients.HorizonClientRegistry.Session;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HorizonClientRegistryTest {
    private static final Pattern PACKAGE = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+(\\.\\*)?");

    @Test
    public void idsAreUniqueAndEveryGroupHasEntries() {
        Set<String> ids = new HashSet<>();
        for (Client c : HorizonClientRegistry.ALL) {
            assertTrue("duplicate id " + c.id, ids.add(c.id));
            assertEquals(c, HorizonClientRegistry.byId(c.id));
        }
        for (Group g : Group.values()) {
            assertFalse("empty group " + g, HorizonClientRegistry.inGroup(g).isEmpty());
        }
        assertNull(HorizonClientRegistry.byId("unknown"));
    }

    @Test
    public void linksAreHttpsAndOnlyUnresolvedProjectsMayLackOne() {
        for (Client c : HorizonClientRegistry.ALL) {
            if (c.link == null) {
                assertEquals(c.id, Mode.UNRESOLVED, c.mode);
            }
            else {
                assertTrue(c.id, c.link.startsWith("https://"));
            }
        }
    }

    @Test
    public void onlyInstalledAppsCarryPackagesAndEachPackageBelongsToOneClient() {
        Map<String, String> owner = new HashMap<>();
        for (Client c : HorizonClientRegistry.ALL) {
            if (c.mode == Mode.INSTALLED_APP) {
                assertFalse(c.id + " has no package", c.packages().isEmpty());
            }
            else {
                assertTrue(c.id + " claims a package", c.packages().isEmpty());
            }
            for (String p : c.packages()) {
                assertTrue(c.id + ": " + p, PACKAGE.matcher(p).matches());
                assertNull(p + " listed twice", owner.put(p, c.id));
            }
        }
    }

    @Test
    public void sessionsAreHonestForEachMode() {
        for (Client c : HorizonClientRegistry.ALL) {
            switch (c.mode) {
                case INSTALLED_APP:
                    assertTrue(c.id, c.session == Session.OPENED_ONLY || c.session == Session.MEDIA_CONTROLS);
                    break;
                case OFFICIAL_WEB:
                    assertEquals(c.id, Session.PROVIDER_MANAGED, c.session);
                    break;
                case BUILT_IN:
                    assertTrue(c.id, c.session != Session.OPENED_ONLY && c.session != Session.MEDIA_CONTROLS);
                    break;
                default:
                    assertEquals(c.id, Session.NONE, c.session);
                    break;
            }
        }
    }

    @Test
    public void onlyTheBuiltInPcStreamIsObservedOrResumable() {
        for (Client c : HorizonClientRegistry.ALL) {
            boolean pc = c.id.equals("nexus-pc-stream");
            assertEquals(c.id, pc, c.resume);
            assertEquals(c.id, pc, c.session == Session.OBSERVED);
        }
        Client pc = HorizonClientRegistry.byId("nexus-pc-stream");
        assertEquals(Mode.BUILT_IN, pc.mode);
        assertEquals(Group.PC_STREAMING, pc.group);
    }

    @Test
    public void unresolvedAndResearchProjectsAreNeverPresentedAsAvailable() {
        for (String id : new String[]{"ariane", "vibemis", "syncthing"}) {
            assertEquals(id, Availability.UNVERIFIED, HorizonClientRegistry.availability(HorizonClientRegistry.byId(id), true));
        }
        for (String id : new String[]{"starwire", "moonlight-x", "kast", "aurora", "greenlight", "chiaki-ng", "argosy"}) {
            assertEquals(id, Availability.RESEARCH_ONLY,
                    HorizonClientRegistry.availability(HorizonClientRegistry.byId(id), true));
        }
    }

    @Test
    public void availabilityFollowsModeAndInstallation() {
        Client gfn = HorizonClientRegistry.byId("gfn-app");
        assertEquals(Availability.INSTALLED, HorizonClientRegistry.availability(gfn, true));
        assertEquals(Availability.NOT_INSTALLED, HorizonClientRegistry.availability(gfn, false));
        assertEquals(Availability.BUILT_IN,
                HorizonClientRegistry.availability(HorizonClientRegistry.byId("gfn-player"), false));
        assertEquals(Availability.WEB,
                HorizonClientRegistry.availability(HorizonClientRegistry.byId("xbox-remote"), false));
        assertEquals(Availability.ON_PC,
                HorizonClientRegistry.availability(HorizonClientRegistry.byId("vibepollo"), false));
    }

    @Test
    public void packageFamiliesMatchVariantsButNotLookalikes() {
        Client retroarch = HorizonClientRegistry.byId("retroarch");
        assertTrue(retroarch.matches("com.retroarch"));
        assertTrue(retroarch.matches("com.retroarch.aarch64"));
        assertFalse(retroarch.matches("com.retroarchx"));
        assertFalse(retroarch.matches(null));
        Client gamehub = HorizonClientRegistry.byId("gamehub");
        assertTrue(gamehub.matches("com.xiaoji.egggame"));
        assertFalse("exact packages do not match other apps by the same publisher", gamehub.matches("com.xiaoji.other"));
        assertEquals("com.retroarch.aarch64",
                retroarch.installedPackage(Arrays.asList("org.ppsspp.ppsspp", "com.retroarch.aarch64")));
        assertNull(retroarch.installedPackage(null));
        Client xbox = HorizonClientRegistry.byId("xbox-app");
        assertEquals("com.microsoft.xboxone.smartglass",
                xbox.installedPackage(Arrays.asList("com.microsoft.xboxone.smartglass")));
    }

    @Test
    public void registryIsReadOnly() {
        try {
            HorizonClientRegistry.ALL.clear();
        } catch (UnsupportedOperationException expected) {
            assertNotNull(HorizonClientRegistry.byId("nexus-pc-stream"));
            return;
        }
        throw new AssertionError("registry list is mutable");
    }
}
