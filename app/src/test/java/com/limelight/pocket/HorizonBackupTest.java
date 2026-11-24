package com.limelight.pocket;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class HorizonBackupTest {
    private static JSONObject backup(String entries) throws Exception {
        return new JSONObject("{\"app\":\"Pocket Nexus\",\"version\":1,\"files\":{\"main\":"+entries+"}}");
    }

    @Test public void migratesLegacyAndRemovesNonportableState() throws Exception {
        Map<String,Object> values = SettingsBackup.decode(backup("{\"list_fps\":{\"s\":\"120\"},"
                + "\"pocket_home_widgets\":{\"s\":\"clock,aw:12,device,clock\"},"
                + "\"pocket_update_available\":{\"s\":\"old\"},"
                + "\"pocket_companion_token_demo\":{\"s\":\"private\"}}")).get("main");
        assertEquals("120", values.get("list_fps"));
        assertEquals("clock,device", values.get("pocket_home_widgets"));
        assertFalse(values.containsKey("pocket_update_available"));
        assertFalse(values.containsKey("pocket_companion_token_demo"));
    }
    @Test(expected=JSONException.class) public void rejectsUnsupportedVersion() throws Exception {
        SettingsBackup.decode(backup("{}").put("version", 99));
    }
    @Test(expected=JSONException.class) public void rejectsAmbiguousType() throws Exception {
        SettingsBackup.decode(backup("{\"x\":{\"s\":\"a\",\"i\":1}}"));
    }
    @Test(expected=JSONException.class) public void rejectsWrongType() throws Exception {
        SettingsBackup.decode(backup("{\"x\":{\"b\":\"true\"}}"));
    }
    private android.content.SharedPreferences store(String name) {
        return org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences(name, 0);
    }

    @Test public void restoresExistingAndNewKeysWithoutClearingOtherSettings() throws Exception {
        android.content.SharedPreferences prefs = store("restore-success");
        prefs.edit().putString("old", "before").putString("untouched", "keep").commit();
        Map<String, android.content.SharedPreferences> stores = new java.util.LinkedHashMap<>();
        stores.put("main", prefs);
        assertEquals(2, SettingsBackup.restoreStaged(stores,
                SettingsBackup.decode(backup("{\"old\":{\"s\":\"after\"},\"new\":{\"i\":7}}"))));
        assertEquals("after", prefs.getString("old", null));
        assertEquals(7, prefs.getInt("new", 0));
        assertEquals("keep", prefs.getString("untouched", null));
    }

    @Test public void laterStoreTypeMismatchMakesNoChanges() throws Exception {
        android.content.SharedPreferences first = store("restore-first");
        android.content.SharedPreferences second = store("restore-second");
        first.edit().putString("old", "before").commit();
        second.edit().putInt("bad", 1).commit();
        Map<String, android.content.SharedPreferences> stores = new java.util.LinkedHashMap<>();
        stores.put("main", first);
        stores.put("pocket_game_profiles", second);
        JSONObject root = backup("{\"old\":{\"s\":\"after\"}}");
        root.getJSONObject("files").put("pocket_game_profiles",
                new JSONObject("{\"bad\":{\"s\":\"wrong type\"}}"));
        assertEquals(-1, SettingsBackup.restoreStaged(stores, SettingsBackup.decode(root)));
        assertEquals("before", first.getString("old", null));
        assertEquals(1, second.getInt("bad", 0));
    }

    @Test public void failedLaterCommitRollsBackTouchedKeys() throws Exception {
        android.content.SharedPreferences first = store("restore-rollback");
        first.edit().putString("old", "before").putString("untouched", "keep").commit();
        android.content.SharedPreferences second = org.mockito.Mockito.mock(android.content.SharedPreferences.class);
        android.content.SharedPreferences.Editor editor = org.mockito.Mockito.mock(android.content.SharedPreferences.Editor.class);
        org.mockito.Mockito.when(second.getAll()).thenReturn(java.util.Collections.emptyMap());
        org.mockito.Mockito.when(second.edit()).thenReturn(editor);
        org.mockito.Mockito.when(editor.commit()).thenReturn(false, true);
        Map<String, android.content.SharedPreferences> stores = new java.util.LinkedHashMap<>();
        stores.put("main", first);
        stores.put("pocket_game_profiles", second);
        JSONObject root = backup("{\"old\":{\"s\":\"after\"},\"new\":{\"i\":7}}");
        root.getJSONObject("files").put("pocket_game_profiles", new JSONObject("{\"bad\":{\"i\":2}}"));
        assertEquals(-1, SettingsBackup.restoreStaged(stores, SettingsBackup.decode(root)));
        assertEquals("before", first.getString("old", null));
        assertFalse(first.contains("new"));
        assertEquals("keep", first.getString("untouched", null));
        org.mockito.Mockito.verify(editor).remove("bad");
    }

    @Test public void actualLegacyFileImportAndV2ExportPreservePortableSettings() throws Exception {
        android.content.Context c = org.robolectric.RuntimeEnvironment.getApplication();
        java.io.File legacy = java.io.File.createTempFile("nexus-legacy-", ".json", c.getCacheDir());
        java.io.File exported = java.io.File.createTempFile("nexus-export-", ".json", c.getCacheDir());
        android.content.SharedPreferences main = PocketPrefs.prefs(c);
        android.content.SharedPreferences games = c.getSharedPreferences("pocket_game_profiles", 0);
        android.content.SharedPreferences cloud = c.getSharedPreferences("pocket_cloud_clients", 0);
        main.edit().clear().putString("pocket_companion_token_demo", "local-secret").commit();
        games.edit().clear().commit();
        cloud.edit().clear().commit();
        try {
            JSONObject root = backup("{}");
            JSONObject files = root.getJSONObject("files");
            JSONObject values = files.getJSONObject("main");
            values.put("pocket_home_pins", new JSONObject().put("s", "nexus\tMusic\tmusic\t\t\t0"));
            values.put("pocket_key_back", new JSONObject().put("s", "k:4"));
            values.put("pocket_companion_token_demo", new JSONObject().put("s", "foreign-secret"));
            values.put("pocket_home_widgets", new JSONObject().put("s", "clock,aw:12,device"));
            files.put("pocket_game_profiles", new JSONObject().put("demo_fps", new JSONObject().put("i", 120)));
            files.put("pocket_cloud_clients", new JSONObject().put("demo_client", new JSONObject().put("s", "browser")));
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(legacy)) {
                out.write(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            assertEquals(5, SettingsBackup.read(c, android.net.Uri.fromFile(legacy)));
            assertEquals("nexus\tMusic\tmusic\t\t\t0", main.getString("pocket_home_pins", null));
            assertEquals("k:4", main.getString("pocket_key_back", null));
            assertEquals("clock,device", main.getString("pocket_home_widgets", null));
            assertEquals("local-secret", main.getString("pocket_companion_token_demo", null));
            assertEquals(120, games.getInt("demo_fps", 0));
            assertEquals("browser", cloud.getString("demo_client", null));
            assertEquals(5, SettingsBackup.write(c, android.net.Uri.fromFile(exported)));
            String json = new String(java.nio.file.Files.readAllBytes(exported.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(json.contains("local-secret"));
            assertFalse(json.contains("foreign-secret"));
            JSONObject exportedRoot = new JSONObject(json);
            assertEquals(2, exportedRoot.getInt("version"));
            assertEquals("Pocket Nexus 2.0", exportedRoot.getString("app"));
            main.edit().clear().commit(); games.edit().clear().commit(); cloud.edit().clear().commit();
            assertEquals(5, SettingsBackup.read(c, android.net.Uri.fromFile(exported)));
            assertEquals(120, games.getInt("demo_fps", 0));
            assertEquals("browser", cloud.getString("demo_client", null));
            assertEquals("clock,device", main.getString("pocket_home_widgets", null));
            assertFalse(main.contains("pocket_companion_token_demo"));
        } finally {
            main.edit().clear().commit(); games.edit().clear().commit(); cloud.edit().clear().commit();
            legacy.delete(); exported.delete();
        }
    }

    @Test public void acceptsNewVersion() throws Exception {
        JSONObject root = backup("{\"x\":{\"i\":42}}").put("version",2).put("app","Pocket Nexus 2.0");
        assertEquals(42, SettingsBackup.decode(root).get("main").get("x"));
    }
}
