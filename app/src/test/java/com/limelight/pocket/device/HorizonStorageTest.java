package com.limelight.pocket.device;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonStorageTest {
    @Test public void mountedAndReadOnlyAreAccessibleStates() {
        assertTrue(HorizonStorage.mounted("mounted"));
        assertTrue(HorizonStorage.mounted("mounted_ro"));
        assertFalse(HorizonStorage.mounted("unmounted"));
        assertFalse(HorizonStorage.mounted(null));
    }

    @Test public void pathPrefixRequiresDirectoryBoundary() {
        assertTrue(HorizonStorage.containsPath("/storage/ABCD", "/storage/ABCD/ROMs"));
        assertTrue(HorizonStorage.containsPath("/storage/ABCD/", "/storage/ABCD"));
        assertFalse(HorizonStorage.containsPath("/storage/ABCD", "/storage/ABCDE/ROMs"));
        assertFalse(HorizonStorage.containsPath(null, "/storage/ABCD"));
    }
}
