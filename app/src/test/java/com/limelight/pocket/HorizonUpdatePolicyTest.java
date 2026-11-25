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

    @Test public void onlyTheAdvertisedAssetUnderTheProjectStableTagIsAccepted() {
        String asset = "https://github.com/" + HorizonUpdatePolicy.REPO
                + "/releases/download/latest/PocketNexus2-a1b2c3d.apk";
        assertTrue(HorizonUpdatePolicy.releaseAsset(asset, "a1b2c3d"));
        assertFalse(HorizonUpdatePolicy.releaseAsset(asset, "1234567"));
        assertFalse(HorizonUpdatePolicy.releaseAsset(asset.replace("latest/", "preview/"), "a1b2c3d"));
        assertFalse(HorizonUpdatePolicy.releaseAsset(asset.replace(HorizonUpdatePolicy.REPO, "other/repo"), "a1b2c3d"));
        assertFalse(HorizonUpdatePolicy.releaseAsset(asset + "?other=asset", "a1b2c3d"));
    }

    @Test public void verifiesTheActualSha256Digest() throws Exception {
        String expected = "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        byte[] data = java.security.MessageDigest.getInstance("SHA-256").digest(
                "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(HorizonUpdatePolicy.matchesDigest(expected, data));
        data[0] ^= 1;
        assertFalse(HorizonUpdatePolicy.matchesDigest(expected, data));
        assertFalse(HorizonUpdatePolicy.validDigest("sha256:1234"));
    }
}
