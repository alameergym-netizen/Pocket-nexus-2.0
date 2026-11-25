package com.limelight.pocket.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.home.HomeStore;
import com.limelight.pocket.retro.RetroLibrary;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HorizonLibraryRomSourcesTest {
    private static final String GAME = "Twin Circuit";
    private final Context context = RuntimeEnvironment.getApplication();

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void twoScannedRootsStayDistinctAndAnUnavailableRootDropsOut() throws IOException {
        File internal = scan("internal", "ps2");
        File removable = scan("removable", "PS2");
        UniversalLibrary.Game combined = findGame();
        assertTrue(combined.requiresRetroChoice());
        Set<String> roots = new HashSet<>();
        for (UniversalLibrary.Way way : combined.ways) roots.add(way.item.c);
        assertEquals(Set.of(internal.getAbsolutePath(), removable.getAbsolutePath()), roots);
        assertEquals(new File(removable, "PS2"),
                RetroLibrary.systemFolder(removable, RetroLibrary.config(context).system("ps2")));

        assertTrue(removable.renameTo(new File(temporary.getRoot(), "detached")));
        UniversalLibrary.Game stillAvailable = findGame();
        assertFalse(stillAvailable.requiresRetroChoice());
        assertEquals(1, stillAvailable.ways.size());
        assertEquals(internal.getAbsolutePath(), stillAvailable.best().item.c);
    }

    @Test public void singleSourceKeepsLegacyHistoryWithoutCreatingAnAmbiguousWay() throws IOException {
        File internal = scan("internal", "ps2");
        HomeItem legacy = new HomeItem(HomeItem.RETRO, GAME, "ps2", GAME + ".iso", "", 0);
        HomeStore.pin(context, legacy);
        HomeStore.record(context, legacy);
        PocketPrefs.prefs(context).edit().putLong("pocket_pt_" + legacy.key(), 120).commit();
        UniversalLibrary.Game game = findGame();
        assertEquals(1, game.ways.size());
        assertEquals(internal.getAbsolutePath(), game.best().item.c);
        assertTrue(game.best().last > 0);
        assertEquals(120, game.seconds);
        assertTrue(HomeStore.load(context).stream().anyMatch(entry -> entry.item.key().equals(legacy.key())));
    }

    private File scan(String rootName, String system) throws IOException {
        File root = temporary.newFolder(rootName);
        File games = new File(root, system);
        assertTrue(games.mkdir());
        assertTrue(new File(games, GAME + ".iso").createNewFile());
        RetroLibrary.setRoot(context, root);
        assertNotNull(RetroLibrary.scan(context, root).find("ps2/" + GAME + ".iso"));
        return root;
    }

    private UniversalLibrary.Game findGame() {
        return UniversalLibrary.gather(context).stream().filter(game -> GAME.equals(game.title)).findFirst()
                .orElseThrow(() -> new AssertionError("Scanned ROM was absent from Library"));
    }
}
