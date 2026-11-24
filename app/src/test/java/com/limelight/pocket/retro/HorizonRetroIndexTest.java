package com.limelight.pocket.retro;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.io.IOException;
import static org.junit.Assert.*;

public class HorizonRetroIndexTest {
    private static final String ROOT="/storage/1234/ROMs";
    @Test public void sourceKeysAreIndependent() throws Exception {
        assertEquals(HorizonRetroIndex.key(ROOT),HorizonRetroIndex.key(ROOT+"/"));
        assertNotEquals(HorizonRetroIndex.key(ROOT),HorizonRetroIndex.key("/storage/5678/ROMs"));
    }
    @Test public void roundTripUnicodeAndDirectory() throws Exception {
        HorizonRetroIndex.Row row=new HorizonRetroIndex.Row("ps3","Folder/遊戲.ps3",true);
        byte[] data=HorizonRetroIndex.encode(ROOT,Collections.singletonList(row));
        HorizonRetroIndex.Row restored=HorizonRetroIndex.decode(ROOT,data).get(0);
        assertEquals(row.system,restored.system);
        assertEquals(row.relative,restored.relative);
        assertTrue(restored.directory);
    }
    @Test(expected=IOException.class) public void rejectsWrongSource() throws Exception {
        HorizonRetroIndex.decode("/storage/other",HorizonRetroIndex.encode(ROOT,Collections.emptyList()));
    }
    @Test(expected=IOException.class) public void rejectsTruncated() throws Exception {
        byte[] data=HorizonRetroIndex.encode(ROOT,Collections.singletonList(new HorizonRetroIndex.Row("ps2","Game.iso",false)));
        HorizonRetroIndex.decode(ROOT,Arrays.copyOf(data,data.length-1));
    }
    @Test(expected=IOException.class) public void rejectsTrailingBytes() throws Exception {
        byte[] data=HorizonRetroIndex.encode(ROOT,Collections.emptyList());
        HorizonRetroIndex.decode(ROOT,Arrays.copyOf(data,data.length+1));
    }
    @Test public void rejectsPathTraversalAndSiblingPrefix() {
        assertFalse(HorizonRetroIndex.valid("ps2","../Game.iso"));
        assertFalse(HorizonRetroIndex.valid("ps2","/Game.iso"));
        assertFalse(HorizonRetroIndex.valid("ps2","A//Game.iso"));
        assertTrue(HorizonRetroIndex.valid("ps2","A/Game.iso"));
        assertFalse(HorizonRetroIndex.inside("/storage/roms","/storage/roms2/Game.iso"));
        assertTrue(HorizonRetroIndex.inside("/storage/roms","/storage/roms/Game.iso"));
    }
}
