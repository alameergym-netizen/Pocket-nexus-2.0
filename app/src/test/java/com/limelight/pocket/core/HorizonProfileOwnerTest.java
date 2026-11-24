package com.limelight.pocket.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonProfileOwnerTest {
    @Test public void directGameUsesItsOwnProfile() {
        HorizonProfileOwner owner = HorizonProfileOwner.resolve("pc", 9, 0, null, 0, 0);
        assertTrue(owner.valid());
        assertEquals(9, owner.profileId);
        assertFalse(owner.isOverrideFor("pc", 9));
    }
    @Test public void desktopProfileSurvivesMatchingResume() {
        HorizonProfileOwner launched = HorizonProfileOwner.resolve("pc", 1, 777, null, 0, 0);
        assertTrue(launched.isOverrideFor("pc", 1));
        HorizonProfileOwner resumed = HorizonProfileOwner.resolve("pc", 1, 0,
                launched.host, launched.streamAppId, launched.profileId);
        assertEquals(777, resumed.profileId);
        assertEquals(1, resumed.streamAppId);
    }
    @Test public void newValidatedCommandSupersedesOldResumeProfile() {
        assertEquals(888, HorizonProfileOwner.resolve("pc", 1, 888, "pc", 1, 777).profileId);
    }
    @Test public void mismatchedHostOrStreamCannotReuseProfile() {
        assertEquals(1, HorizonProfileOwner.resolve("other", 1, 0, "pc", 1, 777).profileId);
        assertEquals(2, HorizonProfileOwner.resolve("pc", 2, 0, "pc", 1, 777).profileId);
    }
    @Test public void missingIdentityCannotApplyProfile() {
        assertFalse(HorizonProfileOwner.resolve(null, 1, 777, null, 1, 777).valid());
        assertFalse(HorizonProfileOwner.resolve("pc", 0, 777, "pc", 0, 777).valid());
        assertFalse(HorizonProfileOwner.resolve(" ", 1, 777, " ", 1, 777).valid());
    }
    @Test public void negativeSyntheticProfileIdRemainsSupported() {
        assertEquals(-123, HorizonProfileOwner.resolve("pc", 1, -123, null, 0, 0).profileId);
    }
}
