package com.limelight.pocket.ui;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;
import org.junit.Test;
import static org.junit.Assert.*;

public class PocketThemeTest {
    @Test public void newPresetsRoundTrip() {
        assertEquals(22, PocketTheme.THEMES.length);
        for (int i = 15; i < PocketTheme.THEMES.length; i++) {
            PocketTheme.Preset p = PocketTheme.THEMES[i];
            assertEquals(i, PocketTheme.matchingPreset(p.accent, p.backdrop, p.frost,
                    p.sounds, p.layout, p.focus));
        }
    }

    @Test public void pureOledWinsOverLegacyMono() {
        int i = PocketTheme.matchingPreset(0xFFFFFFFF, PocketPrefs.BACKDROP_BLACK,
                false, "crisp", "grid_tiles", PocketPrefs.FOCUS_RING);
        assertEquals(R.string.horizon_theme_pure, PocketTheme.THEMES[i].name);
    }

    @Test public void modifiedSignatureIsCustom() {
        PocketTheme.Preset p = PocketTheme.THEMES[15];
        assertEquals(-1, PocketTheme.matchingPreset(p.accent, p.backdrop, p.frost,
                "retro", p.layout, p.focus));
    }

    @Test public void legacyMonoStillMatches() {
        int i = PocketTheme.matchingPreset(0xFFFFFFFF, PocketPrefs.BACKDROP_BLACK,
                false, "crisp", "covers", PocketPrefs.FOCUS_RING_ZOOM);
        assertEquals(R.string.pocket_theme_mono, PocketTheme.THEMES[i].name);
    }
}
