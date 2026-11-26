package com.limelight.pocket.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.limelight.pocket.library.HorizonLibraryReadiness.Ready;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class HorizonLibraryReadinessTest {
    @Test public void anyWayThatStartsMakesTheGamePlayable() {
        assertEquals(Ready.READY, HorizonLibraryReadiness.summary(Arrays.asList(Ready.APP_MISSING, Ready.READY)));
        assertEquals(Ready.STREAMS, HorizonLibraryReadiness.summary(Arrays.asList(Ready.NO_EMULATOR, Ready.STREAMS)));
        assertEquals(Ready.APP_MISSING, HorizonLibraryReadiness.summary(Arrays.asList(Ready.NO_EMULATOR,
                Ready.APP_MISSING)));
        assertEquals(Ready.NO_EMULATOR, HorizonLibraryReadiness.summary(Collections.singletonList(Ready.NO_EMULATOR)));
    }

    @Test public void unknownWaysAreNeverFlagged() {
        assertEquals(Ready.READY, HorizonLibraryReadiness.summary(null));
        assertEquals(Ready.READY, HorizonLibraryReadiness.summary(Collections.<Ready>emptyList()));
        assertEquals(Ready.READY, HorizonLibraryReadiness.summary(Arrays.asList(null, Ready.NO_EMULATOR)));
    }

    @Test public void onlyGamesThatCannotStartAreFlagged() {
        assertFalse(HorizonLibraryReadiness.flagged(Ready.READY));
        assertFalse("a stream is available: the PC or service decides", HorizonLibraryReadiness.flagged(Ready.STREAMS));
        assertTrue(HorizonLibraryReadiness.flagged(Ready.APP_MISSING));
        assertTrue(HorizonLibraryReadiness.flagged(Ready.NO_EMULATOR));
    }

    @Test public void theMissingAppIsNamedOnlyWhenItIsTheOneThing() {
        assertEquals("DuckStation", HorizonLibraryReadiness.missingApp(
                Arrays.asList(Ready.APP_MISSING, Ready.NO_EMULATOR), Arrays.asList(" DuckStation ", null)));
        assertEquals("DuckStation", HorizonLibraryReadiness.missingApp(
                Arrays.asList(Ready.APP_MISSING, Ready.APP_MISSING), Arrays.asList("DuckStation", "DuckStation")));
        assertNull("two different apps: no single name", HorizonLibraryReadiness.missingApp(
                Arrays.asList(Ready.APP_MISSING, Ready.APP_MISSING), Arrays.asList("DuckStation", "GameNative")));
        assertNull("it can start another way", HorizonLibraryReadiness.missingApp(
                Arrays.asList(Ready.APP_MISSING, Ready.STREAMS), Arrays.asList("DuckStation", null)));
        assertNull(HorizonLibraryReadiness.missingApp(Arrays.asList(Ready.APP_MISSING), Arrays.asList("")));
        assertNull(HorizonLibraryReadiness.missingApp(Arrays.asList(Ready.APP_MISSING), null));
    }

    @Test public void theAppsTheLibraryWaitsForAreCountedMostFirst() {
        java.util.LinkedHashMap<String, Integer> w = HorizonLibraryReadiness.waiting(Arrays.asList(
                "RetroArch", "DuckStation", null, "RetroArch", " ", "Dolphin", "RetroArch", "DuckStation"));
        assertEquals(Arrays.asList("RetroArch", "DuckStation", "Dolphin"), new java.util.ArrayList<>(w.keySet()));
        assertEquals(Integer.valueOf(3), w.get("RetroArch"));
        assertEquals(Integer.valueOf(2), w.get("DuckStation"));
        assertTrue(HorizonLibraryReadiness.waiting(null).isEmpty());
    }
}
