package com.limelight.pocket.retro;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HorizonRetroSelectionTest {
    @Rule public TemporaryFolder tmp=new TemporaryFolder();
    private RomScanner.Rom rom(File file) {
        return new RomScanner.Rom("ps2","Game.iso",file,false);
    }
    @Test public void newerRequestsInvalidateEarlierCallbacks() {
        HorizonRetroSelection gate=new HorizonRetroSelection();
        long first=gate.next(),second=gate.next();
        assertFalse(gate.current(first));
        assertTrue(gate.current(second));
    }
    @Test public void currentReadableFileCanLaunch() throws Exception {
        File root=tmp.newFolder("source"),game=new File(root,"Game.iso");
        assertTrue(game.createNewFile());
        assertTrue(HorizonRetroSelection.canLaunch(root,root,rom(game),rom(game)));
    }
    @Test public void rejectsPreviousSourceEvenWithSameGameKey() throws Exception {
        File a=tmp.newFolder("a"),b=tmp.newFolder("b");
        File ga=new File(a,"Game.iso"),gb=new File(b,"Game.iso");
        assertTrue(ga.createNewFile());assertTrue(gb.createNewFile());
        assertFalse(HorizonRetroSelection.canLaunch(b,a,rom(ga),rom(ga)));
        assertFalse(HorizonRetroSelection.canLaunch(b,b,rom(ga),rom(gb)));
    }
    @Test public void rejectsMissingFileOrIndexEntry() throws Exception {
        File root=tmp.newFolder("source"),game=new File(root,"Game.iso");
        assertFalse(HorizonRetroSelection.canLaunch(root,root,rom(game),rom(game)));
        assertTrue(game.createNewFile());
        assertFalse(HorizonRetroSelection.canLaunch(root,root,rom(game),null));
    }
}
