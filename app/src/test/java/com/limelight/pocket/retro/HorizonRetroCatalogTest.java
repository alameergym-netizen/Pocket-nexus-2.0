package com.limelight.pocket.retro;

import com.limelight.pocket.home.HomeItem;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonRetroCatalogTest {
    private RomScanner.Rom rom(String relative) {
        return new RomScanner.Rom("ps2",relative,new File(relative),false);
    }
    @Test public void sameGameFromTwoSourcesHasTwoLaunchWays() {
        HomeItem a=HorizonRetroCatalog.item("/storage/a/ROMs",rom("Game.iso"));
        HomeItem b=HorizonRetroCatalog.item("/storage/b/ROMs",rom("Game.iso"));
        assertNotNull(a);assertNotNull(b);
        assertEquals(a.name,b.name);
        assertNotEquals(a.key(),b.key());
        assertEquals("/storage/b/ROMs",b.c);
    }
    @Test public void normalizesSourceAndRejectsUnsafeRelativePaths() {
        assertEquals("/storage/a/ROMs",HorizonRetroCatalog.item("/storage//a/ROMs/",rom("Game.iso")).c);
        assertNull(HorizonRetroCatalog.item("/storage/a/ROMs",rom("../Game.iso")));
        assertNull(HorizonRetroCatalog.item("relative",rom("Game.iso")));
    }
    @Test public void distinguishesSourceLabels() {
        assertEquals("PS2 · /storage/a/ROMs",HorizonRetroCatalog.label("PS2","/storage/a/ROMs"));
        assertEquals("PS2",HorizonRetroCatalog.label("PS2",""));
    }
}
