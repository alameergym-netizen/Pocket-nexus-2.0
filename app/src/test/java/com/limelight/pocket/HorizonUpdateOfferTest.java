package com.limelight.pocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HorizonUpdateOfferTest {
    private static final String SHA = "a1b2c3d";
    private static final String DIGEST = "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test public void servesOnlyUniqueStableAssetsAndPreservesLegacyMetadata() throws Exception {
        JSONObject asset = apk(SHA);
        Updater.Release offer = Updater.readOffer(release(asset), "0000000");
        assertNotNull(offer);
        assertEquals(SHA, offer.sha);
        assertNull(offer.digest);
        assertNull(Updater.readOffer(release(asset), SHA));
        asset.put("digest", DIGEST);
        assertEquals(DIGEST, Updater.readOffer(release(asset), "0000000").digest);
        assertThrows(JSONException.class, () -> Updater.readOffer(release(asset, apk("1234567")), "0000000"));
    }

    @Test public void ignoresDraftsPreviewsAndOtherTags() throws Exception {
        assertNull(Updater.readOffer(release(apk(SHA)).put("draft", true), "0000000"));
        assertNull(Updater.readOffer(release(apk(SHA)).put("prerelease", true), "0000000"));
        assertNull(Updater.readOffer(release(apk(SHA)).put("tag_name", "nightly"), "0000000"));
    }

    @Test public void rejectsWrongOriginsMalformedDigestsAndOversizedAssets() throws Exception {
        JSONObject wrongUrl = apk(SHA).put("browser_download_url", "https://github.com/other/repo/asset.apk");
        assertThrows(JSONException.class, () -> Updater.readOffer(release(wrongUrl), "0000000"));
        JSONObject wrongDigest = apk(SHA).put("digest", "sha256:123");
        assertThrows(JSONException.class, () -> Updater.readOffer(release(wrongDigest), "0000000"));
        JSONObject tooLarge = apk(SHA).put("size", HorizonUpdatePolicy.MAX_APK_BYTES + 1);
        assertThrows(JSONException.class, () -> Updater.readOffer(release(tooLarge), "0000000"));
    }

    private static JSONObject release(JSONObject... assets) throws JSONException {
        JSONArray list = new JSONArray();
        for (JSONObject asset : assets) list.put(asset);
        return new JSONObject().put("tag_name", "latest").put("draft", false)
                .put("prerelease", false).put("assets", list);
    }

    private static JSONObject apk(String sha) throws JSONException {
        String name = "PocketNexus2-" + sha + ".apk";
        return new JSONObject().put("name", name).put("size", 123)
                .put("browser_download_url", "https://github.com/" + HorizonUpdatePolicy.REPO
                        + "/releases/download/latest/" + name);
    }
}
