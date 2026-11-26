package com.limelight.pocket.device.headphones;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
public class HeadphonesSelectionTest {
    @Test public void uniqueAvailableFreeBudsSelected() {
        assertEquals(1,HeadphonesSelection.automatic(Arrays.asList("Other", "HUAWEI FreeBuds 7i"),Arrays.asList(true,true)));
    }
    @Test public void pairedOnlyDoesNotAutomaticallyConnect() {
        assertEquals(-1,HeadphonesSelection.automatic(Arrays.asList("HUAWEI FreeBuds 7i"),Arrays.asList(false)));
    }
    @Test public void twoAvailableMatchingDevicesRequireChoice() {
        assertEquals(-1,HeadphonesSelection.automatic(Arrays.asList("FreeBuds 7i","HUAWEI FreeBuds 7i"),Arrays.asList(true,true)));
    }
    @Test public void noNameGuessForOtherModel() {
        assertEquals(-1,HeadphonesSelection.automatic(Arrays.asList("FreeBuds 6i","Renamed earbuds"),Arrays.asList(true,true)));
    }
}
