package com.limelight.pocket.core;

import com.limelight.pocket.home.HomeItem;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.limelight.pocket.core.HorizonProviderPolicy.Provider;
import static com.limelight.pocket.core.HorizonProviderPolicy.Observation;

public class HorizonProviderPolicyTest {
    private static HomeItem item(String kind) {
        return new HomeItem(kind, "Example", "source", "target", "", 1);
    }
    @Test public void allExistingKindsAreMapped() {
        String[] kinds = {HomeItem.PC, HomeItem.PC_LIBRARY_GAME, HomeItem.CLOUD, HomeItem.RETRO,
                HomeItem.ANDROID, HomeItem.NATIVE, HomeItem.SHORTCUT, HomeItem.PC_GAMES, HomeItem.NEXUS};
        Provider[] expected = {Provider.PC_STREAM, Provider.PC_LIBRARY, Provider.CLOUD, Provider.RETRO,
                Provider.ANDROID, Provider.NATIVE, Provider.SHORTCUT, Provider.PC_BROWSER, Provider.NEXUS_TOOL};
        for (int i = 0; i < kinds.length; i++) {
            assertEquals(expected[i], HorizonProviderPolicy.provider(item(kinds[i])));
        }
    }
    @Test public void unknownInputIsConservative() {
        assertEquals(Provider.UNKNOWN, HorizonProviderPolicy.provider(null));
        assertEquals(Provider.UNKNOWN, HorizonProviderPolicy.provider(item(null)));
        assertEquals(Provider.UNKNOWN, HorizonProviderPolicy.provider(item("future")));
        assertEquals(Observation.UNKNOWN, HorizonProviderPolicy.observation(null));
        assertEquals(Observation.UNKNOWN, HorizonProviderPolicy.observation(Provider.UNKNOWN));
    }
    @Test public void externalLaunchCannotClaimConfirmedSession() {
        for (Provider p : new Provider[]{Provider.RETRO, Provider.ANDROID, Provider.NATIVE, Provider.SHORTCUT}) {
            assertEquals(Observation.EXTERNAL_UNCONFIRMED, HorizonProviderPolicy.observation(p));
        }
        assertEquals(Observation.PROVIDER_DEPENDENT, HorizonProviderPolicy.observation(Provider.CLOUD));
    }
    @Test public void toolsAreNotGameSessions() {
        assertEquals(Observation.NOT_A_GAME, HorizonProviderPolicy.observation(Provider.PC_BROWSER));
        assertEquals(Observation.NOT_A_GAME, HorizonProviderPolicy.observation(Provider.NEXUS_TOOL));
    }
    @Test public void pcPathsUseInternalStreamObservation() {
        assertEquals(Observation.INTERNAL_STREAM, HorizonProviderPolicy.observation(Provider.PC_STREAM));
        assertEquals(Observation.INTERNAL_STREAM, HorizonProviderPolicy.observation(Provider.PC_LIBRARY));
    }
}
