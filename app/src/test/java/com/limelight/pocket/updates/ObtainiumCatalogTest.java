package com.limelight.pocket.updates;

import android.content.Context;
import android.content.pm.PackageInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class ObtainiumCatalogTest {
    @Test public void schemaTwoAndSerializedSettings() throws Exception {
        JSONObject app = new JSONObject().put("id", "org.example.game")
                .put("url", "https://github.com/example/game")
                .put("name", "Game").put("latestVersion", "2.0")
                .put("additionalSettings", new JSONObject().put("trackOnly", true)
                        .put("includePrereleases", true).put("requestHeader", "private").toString());
        JSONArray data = ObtainiumCatalog.parse(new JSONObject().put("schemaVersion", 2)
                .put("apps", new JSONArray().put(app).put(app)).toString());
        assertEquals(1, data.length());
        JSONObject row = data.getJSONObject(0);
        assertTrue(row.getBoolean("trackOnly"));
        assertTrue(row.getBoolean("prereleases"));
        assertEquals("github.com", row.getString("host"));
        assertEquals("example/game", row.getString("githubRepository"));
        assertFalse(row.has("additionalSettings"));
        assertFalse(row.has("url"));
    }

    @Test public void readsOnlyAVisibleInstalledPackageForTheSelectedId() {
        Context context = RuntimeEnvironment.getApplication();
        PackageInfo info = ObtainiumCatalog.installedInfo(context, context.getPackageName());
        assertNotNull(info);
        assertEquals(context.getPackageName(), info.packageName);
        assertNull(ObtainiumCatalog.installedInfo(context, "org.example.missingpackage"));
        assertNull(ObtainiumCatalog.installedInfo(context, "https://example.org/package"));
    }

    @Test(expected = JSONException.class) public void rejectsNonHttpsSource() throws Exception {
        ObtainiumCatalog.parse("{\"schemaVersion\":2,\"apps\":[{\"id\":\"test\",\"url\":\"file:///private\"}]}");
    }

    @Test(expected = JSONException.class) public void rejectsUnknownSchema() throws Exception {
        ObtainiumCatalog.parse("{\"schemaVersion\":999,\"apps\":[]}");
    }

    @Test public void githubLookupAcceptsOnlyLiteralPublicRepositoryOrReleaseList() {
        assertEquals("owner-7/Game.App", ObtainiumGithubRelease.repositoryFrom("https://github.com/owner-7/Game.App/releases/"));
        for (String source : new String[] {
                "https://github.com/owner/game/releases/tag/v1", "https://github.com/owner/game?token=secret",
                "https://github.com/owner/game#source", "https://alice@github.com/owner/game",
                "https://github.com:8080/owner/game", "https://github.com.invalid/owner/game",
                "https://github.com/owner/%2e%2e", "https://github.com/owner/..", "https://github.com/owner//game"
        }) assertNull(source, ObtainiumGithubRelease.repositoryFrom(source));
        assertFalse(ObtainiumGithubRelease.isRepository("../game"));
        assertFalse(ObtainiumGithubRelease.isRepository("owner/game?token=secret"));
    }

    @Test public void releaseTagRequiresExplicitStableMetadataAndPlainText() throws Exception {
        JSONObject stable = new JSONObject().put("draft", false).put("prerelease", false).put("tag_name", "release/2.0");
        assertEquals("release/2.0", ObtainiumGithubRelease.stableTag(stable));
        assertThrows(JSONException.class, () -> ObtainiumGithubRelease.stableTag(new JSONObject().put("tag_name", "v2")));
        stable.put("prerelease", true);
        assertThrows(JSONException.class, () -> ObtainiumGithubRelease.stableTag(stable));
        stable.put("prerelease", false).put("tag_name", "v2\nreset");
        assertThrows(JSONException.class, () -> ObtainiumGithubRelease.stableTag(stable));
        stable.put("tag_name", "v2\u202ereset");
        assertThrows(JSONException.class, () -> ObtainiumGithubRelease.stableTag(stable));
    }
}
