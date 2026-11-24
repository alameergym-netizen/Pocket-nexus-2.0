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
}
