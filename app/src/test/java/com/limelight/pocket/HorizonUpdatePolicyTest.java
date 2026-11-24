package com.limelight.pocket;

import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonUpdatePolicyTest {
    @Test public void officialHttpsHostsAllowed() {
        assertTrue(HorizonUpdatePolicy.allowedUrl("https://api.github.com/repos/owner/repo/releases"));
        assertTrue(HorizonUpdatePolicy.allowedUrl("https://github.com/owner/repo/releases/download/latest/app.apk"));
        assertTrue(HorizonUpdatePolicy.allowedUrl("https://release-assets.githubusercontent.com/asset?sig=example"));
    }

    @Test public void insecureOrLookalikeHostsRejected() {
        assertFalse(HorizonUpdatePolicy.allowedUrl("http://github.com/app.apk"));
        assertFalse(HorizonUpdatePolicy.allowedUrl("https://github.com.example.org/app.apk"));
        assertFalse(HorizonUpdatePolicy.allowedUrl("https://github.com@evil.example/app.apk"));
        assertFalse(HorizonUpdatePolicy.allowedUrl("https://user@github.com/app.apk"));
        assertFalse(HorizonUpdatePolicy.allowedUrl("https://github.com:8080/app.apk"));
        assertFalse(HorizonUpdatePolicy.allowedUrl("file:///sdcard/app.apk"));
        assertFalse(HorizonUpdatePolicy.allowedUrl(null));
    }

    @Test public void fileNameShaIsStrict() {
        assertTrue(HorizonUpdatePolicy.validSha("a1b2c3d"));
        assertFalse(HorizonUpdatePolicy.validSha("../file"));
        assertFalse(HorizonUpdatePolicy.validSha("A1B2C3D"));
        assertFalse(HorizonUpdatePolicy.validSha(null));
    }
}
