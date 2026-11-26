package com.limelight.pocket.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.limelight.pocket.ui.HorizonStreamCards;
import com.limelight.pocket.ui.HorizonStreamCards.Host;
import com.limelight.pocket.ui.HorizonStreamCards.Reach;
import com.limelight.pocket.ui.HorizonStreamCards.Route;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class HorizonLibraryActiveTest {
    private static Host host(String id, Reach reach, long at, int running) {
        return new Host(id, "Gaming PC", reach, at, Route.LOCAL, running, "Aurora", null, 0);
    }

    @Test
    public void onlyAFreshReportOfThisGameFromAnOnlinePcCounts() {
        long at = 100_000;
        Host h = host("pc1", Reach.ONLINE, at, 42);
        assertTrue(HorizonLibraryActive.runs("pc1", 42, h, at + 1_000));
        assertFalse("another game", HorizonLibraryActive.runs("pc1", 7, h, at + 1_000));
        assertFalse("another PC", HorizonLibraryActive.runs("pc2", 42, h, at + 1_000));
        assertFalse("stale report", HorizonLibraryActive.runs("pc1", 42, h, at + HorizonStreamCards.FRESH_MS + 1));
        assertFalse("PC off", HorizonLibraryActive.runs("pc1", 42, host("pc1", Reach.OFFLINE, at, 42), at + 1));
        assertFalse("nothing running", HorizonLibraryActive.runs("pc1", 0, host("pc1", Reach.ONLINE, at, 0), at + 1));
        assertFalse("not observed", HorizonLibraryActive.runs("pc1", 42, null, at + 1));
    }

    @Test
    public void namesThePcRunningOneOfTheGamesWays() {
        long at = 5_000;
        Map<String, Host> seen = new HashMap<>();
        seen.put("pc1", host("pc1", Reach.ONLINE, at, 3));
        seen.put("pc2", host("pc2", Reach.ONLINE, at, 42));
        assertEquals("Gaming PC", HorizonLibraryActive.runningOn(new String[]{"pc1", "pc2"},
                new int[]{42, 42}, seen::get, at + 10));
        assertNull(HorizonLibraryActive.runningOn(new String[]{"pc1"}, new int[]{42}, seen::get, at + 10));
        assertNull(HorizonLibraryActive.runningOn(new String[0], new int[0], seen::get, at + 10));
    }
}
