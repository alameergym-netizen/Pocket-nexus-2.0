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
    @Test public void acceptsNewVersion() throws Exception {
        JSONObject root = backup("{\"x\":{\"i\":42}}").put("version",2).put("app","Pocket Nexus 2.0");
        assertEquals(42, SettingsBackup.decode(root).get("main").get("x"));
    }
}
