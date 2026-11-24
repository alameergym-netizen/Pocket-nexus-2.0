package com.limelight.pocket.pclib;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonSecureLibraryTest {
    @Test public void readsOnlyCountedDisplayEntries() throws IOException {
        HorizonSecureLibrary.Result result = read("S\tFixture title\tsteam\t1", "S\tAnother\tgog\t0", "END 2");
        assertNull(result.error);
        assertEquals(2, result.entries.size());
        assertEquals("Fixture title", result.entries.get(0).title);
        assertEquals("steam", result.entries.get(0).source);
        assertTrue(result.entries.get(0).installed);
        assertFalse(result.entries.get(1).installed);
    }

    @Test public void authenticationFailureExposesNoPartialRows() throws IOException {
        HorizonSecureLibrary.Result result = read("S\tFixture title\tsteam\t1", "ERR auth");
        assertEquals("ERR auth", result.error);
        assertTrue(result.entries.isEmpty());
    }

    @Test public void rejectsTruncatedAndExtendedRows() {
        assertThrows(IOException.class, () -> read("S\tFixture title\tsteam\t1"));
        assertThrows(IOException.class, () -> read("S\tFixture title\tsteam\t1\tunexpected", "END 1"));
        assertThrows(IOException.class, () -> read("S\tFixture title\tsteam\t1", "END 2"));
    }

    private HorizonSecureLibrary.Result read(String... lines) throws IOException {
        Iterator<String> data = Arrays.asList(lines).iterator();
        return HorizonSecureLibrary.read(() -> data.hasNext() ? data.next() : null);
    }
}
