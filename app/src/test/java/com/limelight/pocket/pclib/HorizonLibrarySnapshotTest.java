package com.limelight.pocket.pclib;

import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class HorizonLibrarySnapshotTest {
    private static final long NOW=1700000000000L;
    private static final String GAME="G\tsteam\t123\t遊戲\tinstalled\t-1\t100\t0\t123\t25\tgame";

    @Test public void roundTripAndEmpty() throws Exception {
        assertEquals(Collections.singletonList(GAME),HorizonLibrarySnapshot.decode(
                HorizonLibrarySnapshot.encode(Collections.singletonList(GAME),NOW),NOW));
        assertTrue(HorizonLibrarySnapshot.decode(HorizonLibrarySnapshot.encode(Collections.emptyList(),NOW),NOW).isEmpty());
    }
    @Test(expected=IOException.class) public void rejectsTruncated() throws Exception {
        byte[] bytes=HorizonLibrarySnapshot.encode(Collections.singletonList(GAME),NOW);
        HorizonLibrarySnapshot.decode(Arrays.copyOf(bytes,bytes.length-1),NOW);
    }
    @Test(expected=IOException.class) public void rejectsTrailingBytes() throws Exception {
        byte[] bytes=HorizonLibrarySnapshot.encode(Collections.emptyList(),NOW);
        HorizonLibrarySnapshot.decode(Arrays.copyOf(bytes,bytes.length+1),NOW);
    }
    @Test(expected=IOException.class) public void rejectsExpired() throws Exception {
        HorizonLibrarySnapshot.decode(HorizonLibrarySnapshot.encode(Collections.emptyList(),NOW),NOW+31L*86400000);
    }
    @Test(expected=IOException.class) public void rejectsFutureTimestamp() throws Exception {
        HorizonLibrarySnapshot.decode(HorizonLibrarySnapshot.encode(Collections.emptyList(),NOW+600000),NOW);
    }
    @Test(expected=IOException.class) public void rejectsNonGameRecord() throws Exception {
        HorizonLibrarySnapshot.encode(Collections.singletonList("TOKEN dummy"),NOW);
    }
    @Test(expected=IOException.class) public void rejectsWrongVersion() throws Exception {
        byte[] bytes=HorizonLibrarySnapshot.encode(Collections.emptyList(),NOW);
        bytes[0]=0;
        HorizonLibrarySnapshot.decode(bytes,NOW);
    }
}
