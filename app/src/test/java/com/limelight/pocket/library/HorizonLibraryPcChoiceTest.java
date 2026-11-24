package com.limelight.pocket.library;

import com.limelight.pocket.home.HomeItem;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonLibraryPcChoiceTest {
    @Test public void samePcWithDistinctAppIdsRequiresAChoice() {
        UniversalLibrary.Game game = titleWithPc("PC-A", 9);
        game.ways.add(way(HomeItem.PC, "pc-a", 41));
        assertTrue(game.requiresPcChoice());
    }

    @Test public void aSecondPcAndAnAlternateProviderKeepNormalQuickLaunch() {
        UniversalLibrary.Game game = titleWithPc("pc-a", 9);
        game.ways.add(way(HomeItem.PC, "pc-b", 41));
        game.ways.add(way(HomeItem.PC_LIBRARY_GAME, "pc-a", 42));
        assertFalse(game.requiresPcChoice());
    }

    @Test public void duplicateOrMissingIdentitiesDoNotInventAConflict() {
        UniversalLibrary.Game game = titleWithPc("pc-a", 9);
        game.ways.add(way(HomeItem.PC, "pc-a", 9));
        game.ways.add(way(HomeItem.PC, "pc-a", 0));
        game.ways.add(way(HomeItem.PC, "", 41));
        assertFalse(game.requiresPcChoice());
    }

    private static UniversalLibrary.Game titleWithPc(String host, int appId) {
        UniversalLibrary.Game game = new UniversalLibrary.Game("example game", "Example Game");
        game.ways.add(way(HomeItem.PC, host, appId));
        return game;
    }

    private static UniversalLibrary.Way way(String kind, String host, int id) {
        HomeItem item = new HomeItem(kind, "Example Game", host, "", "", id);
        return new UniversalLibrary.Way(item, "PC", "PC", 0, UniversalLibrary.PLATFORM_PC);
    }
}
