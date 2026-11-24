package com.limelight.pocket;

import android.content.Context;
import android.content.SharedPreferences;
import com.limelight.pocket.core.HorizonGamePreset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class HorizonProfileSnapshotTest {
    @Test public void presetSnapshotDoesNotMixFieldsOrChangeWhenNextPresetSaved() {
        Context c = RuntimeEnvironment.getApplication();
        GameProfile.setFrameGen(c, "fixture", 1, GameProfile.FRAME_GEN_LSFG);
        assertTrue(GameProfile.applyPreset(c, "fixture", 1, HorizonGamePreset.LOCAL_HIGH_REFRESH));
        GameProfile.Snapshot old = GameProfile.snapshot(c, "fixture", 1);
        assertEquals(120, old.fps);
        assertEquals(50000, old.bitrateKbps);
        assertEquals("1920x1080", old.resolution);
        assertTrue(GameProfile.applyPreset(c, "fixture", 1, HorizonGamePreset.REMOTE_SAVER));
        GameProfile.Snapshot next = GameProfile.snapshot(c, "fixture", 1);
        assertEquals(60, next.fps);
        assertEquals(10000, next.bitrateKbps);
        assertEquals("1280x720", next.resolution);
        assertEquals(120, old.fps);
        assertEquals(GameProfile.FRAME_GEN_LSFG, next.frameGen);
    }

    @Test public void malformedLegacyValuesFallBackWithoutErasingOtherFields() {
        Context c = RuntimeEnvironment.getApplication();
        SharedPreferences raw = c.getSharedPreferences("pocket_game_profiles", Context.MODE_PRIVATE);
        raw.edit().putString("fixture_7_bitrate", "wrong-type")
                .putInt("fixture_7_fps", 90).putString("fixture_7_res", "1920x1080x")
                .putInt("fixture_7_framegen", 99).commit();
        GameProfile.Snapshot profile = GameProfile.snapshot(c, "fixture", 7);
        assertEquals(0, profile.bitrateKbps);
        assertEquals(90, profile.fps);
        assertEquals("", profile.resolution);
        assertEquals(GameProfile.FRAME_GEN_DEFAULT, profile.frameGen);
        assertEquals(0, GameProfile.bitrateKbps(c, "fixture", 7));
        assertEquals("wrong-type", raw.getString("fixture_7_bitrate", ""));
        assertEquals("90 FPS", GameProfile.summary(c, "fixture", 7));
    }

    @Test public void distinctHostAndAppProfilesRemainIndependent() {
        Context c = RuntimeEnvironment.getApplication();
        GameProfile.setFps(c, "first", 8, 120);
        GameProfile.setFps(c, "second", 8, 60);
        GameProfile.setFps(c, "first", 9, 30);
        assertEquals(120, GameProfile.snapshot(c, "first", 8).fps);
        assertEquals(60, GameProfile.snapshot(c, "second", 8).fps);
        assertEquals(30, GameProfile.snapshot(c, "first", 9).fps);
        assertFalse(GameProfile.snapshot(c, null, 8).hasAny());
    }
}
