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

    @Test public void sameTitleOnPcAndCloudNeedsAChoiceWithoutDistinctRecency() {
        UniversalLibrary.Game game = titleWithPc("pc-a", 9);
        game.ways.add(cloudWay(0));
        assertTrue(game.requiresLaunchChoice());
    }

    @Test public void previouslyUsedProviderRetainsQuickLaunchWhenItIsUnique() {
        UniversalLibrary.Game game = titleWithPc("pc-a", 9);
        UniversalLibrary.Way usedCloud = cloudWay(300);
        game.ways.add(usedCloud);
        assertFalse(game.requiresLaunchChoice());
        assertSame(usedCloud, game.best());
    }

    @Test public void aTieBetweenUsedProvidersLeavesTheChoiceOpen() {
        UniversalLibrary.Game game = new UniversalLibrary.Game("example game", "Example Game");
        HomeItem pc = new HomeItem(HomeItem.PC, "Example Game", "pc-a", "", "", 9);
        game.ways.add(new UniversalLibrary.Way(pc, "PC", "PC", 300, UniversalLibrary.PLATFORM_PC));
        game.ways.add(cloudWay(300));
        assertTrue(game.requiresLaunchChoice());
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

    private static UniversalLibrary.Way cloudWay(long last) {
        HomeItem cloud = new HomeItem(HomeItem.CLOUD, "Example Game", "xcloud", "example-game", "", 0);
        return new UniversalLibrary.Way(cloud, "Xbox Cloud Gaming", "Cloud", last, UniversalLibrary.PLATFORM_CLOUD);
    }
}
