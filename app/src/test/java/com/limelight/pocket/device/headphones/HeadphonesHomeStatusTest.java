package com.limelight.pocket.device.headphones;
import org.junit.Test;
import static org.junit.Assert.*;
public class HeadphonesHomeStatusTest {
    @Test public void weakerEarbudIsShown() { assertEquals(Integer.valueOf(40),HeadphonesHomeStatus.level(90,40,85)); }
    @Test public void knownOneEarbudDoesNotInventOther() { assertEquals(Integer.valueOf(70),HeadphonesHomeStatus.level(70,null,90)); }
    @Test public void reportedOverallFallback() { assertEquals(Integer.valueOf(65),HeadphonesHomeStatus.level(null,null,65)); }
    @Test public void absentReadbackDoesNotBecomeZero() { assertNull(HeadphonesHomeStatus.level(null,null,null)); assertNull(HeadphonesHomeStatus.level(null)); }
}
