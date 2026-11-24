package com.limelight.pocket.retro;

import java.io.File;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HorizonRetroScanTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private EsdeConfig.SystemDef system() {
        return new EsdeConfig.SystemDef("ps2","PS2","ps2","","",
                Collections.singleton(".iso"),Collections.emptyList());
    }
    @Test public void accessibleFolderIsComplete() throws Exception {
        File dir=tmp.newFolder("ps2");
        assertTrue(new File(dir,"Game.iso").createNewFile());
        RomScanner.ScanResult r=RomScanner.scanChecked(system(),dir);
        assertTrue(r.complete);
        assertEquals(1,r.games.size());
    }
    @Test public void absentFolderIsNotComplete() {
        assertFalse(RomScanner.scanChecked(system(),new File(tmp.getRoot(),"missing")).complete);
    }
    @Test public void enumerationFailureIsNotComplete() {
        File dir=new File(tmp.getRoot(),"unreadable") {
            @Override public boolean isDirectory() { return true; }
            @Override public File[] listFiles() { return null; }
        };
        assertFalse(RomScanner.scanChecked(system(),dir).complete);
    }
    @Test public void intentionallySkippedFolderIsComplete() throws Exception {
        File dir=tmp.newFolder("skip");
        assertTrue(new File(dir,"noload.txt").createNewFile());
        assertTrue(new File(dir,"Game.iso").createNewFile());
        RomScanner.ScanResult r=RomScanner.scanChecked(system(),dir);
        assertTrue(r.complete);
        assertTrue(r.games.isEmpty());
    }
}
