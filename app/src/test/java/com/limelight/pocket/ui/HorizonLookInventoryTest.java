package com.limelight.pocket.ui;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Theme Studio's look values and the preset inventory stay in step with the rest of the app. */
public class HorizonLookInventoryTest {
    @Test
    public void lookValuesMatchTheAppsOwnLists() {
        assertArrayEquals(PocketPrefs.SOUND_STYLES, HorizonLook.SOUNDS);
        List<String> layouts = new ArrayList<>();
        for (Layouts.Layout l : Layouts.ALL) {
            layouts.add(l.id);
        }
        assertEquals(layouts, Arrays.asList(HorizonLook.LAYOUTS));
        assertEquals(Arrays.asList(PocketPrefs.BACKDROP_GLOW, PocketPrefs.BACKDROP_AMBIENT,
                PocketPrefs.BACKDROP_ART, PocketPrefs.BACKDROP_BLACK), Arrays.asList(HorizonLook.BACKDROPS));
        assertEquals(Arrays.asList(PocketPrefs.MATERIAL_FROST, PocketPrefs.MATERIAL_GLASS),
                Arrays.asList(HorizonLook.MATERIALS));
        assertEquals(Arrays.asList(PocketPrefs.FOCUS_RING_ZOOM, PocketPrefs.FOCUS_RING, PocketPrefs.FOCUS_ZOOM),
                Arrays.asList(HorizonLook.FOCUS));
        assertEquals(PocketPrefs.DEFAULT_SOUND_STYLE, HorizonLook.defaults().sound);
    }

    /** T07: the 15 classic presets keep their order and the 7 Horizon looks follow them. */
    @Test
    public void presetInventoryIsPreserved() {
        int[] expected = {R.string.pocket_theme_nexus, R.string.pocket_theme_midnight, R.string.pocket_theme_aurora,
                R.string.pocket_theme_sakura, R.string.pocket_theme_ember, R.string.pocket_theme_neon,
                R.string.pocket_theme_arcade, R.string.pocket_theme_ocean, R.string.pocket_theme_mono,
                R.string.pocket_theme_portal, R.string.pocket_theme_glass_tv, R.string.pocket_theme_console,
                R.string.pocket_theme_grid, R.string.pocket_theme_minimal, R.string.pocket_theme_deck,
                R.string.horizon_theme_signature, R.string.horizon_theme_horizon, R.string.horizon_theme_atmosphere,
                R.string.horizon_theme_cinema, R.string.horizon_theme_pure, R.string.horizon_theme_legacy,
                R.string.horizon_theme_titanium};
        assertEquals(expected.length, PocketTheme.THEMES.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("preset " + i, expected[i], PocketTheme.THEMES[i].name);
        }
    }

    @Test
    public void everyPresetPreviewIsAValidLookAndCanBeExported() throws Exception {
        HorizonLook base = HorizonLook.defaults();
        for (int i = 0; i < PocketTheme.THEMES.length; i++) {
            HorizonLook look = HorizonThemeStudio.preset(base, i);
            assertTrue("preset " + i, look.valid());
            assertEquals("preset " + i, look, HorizonLook.parse(look.toText()));
        }
    }

    @Test
    public void horizonPresetsAreRecognisedFromTheirPreview() {
        HorizonLook base = HorizonLook.defaults();
        for (int i = 15; i < PocketTheme.THEMES.length; i++) {
            assertEquals(i, HorizonThemeStudio.presetIndex(HorizonThemeStudio.preset(base, i)));
        }
        assertEquals(0, HorizonThemeStudio.presetIndex(base));
    }

    @Test
    public void everyHorizonFamilyButCinemaBringsItsOwnLayouts() {
        HorizonLook base = HorizonLook.defaults();
        int own = 0;
        for (int i = 0; i < PocketTheme.THEMES.length; i++) {
            if (HorizonThemeStudio.ownLayouts(HorizonThemeStudio.preset(base, i))) {
                own++;
                int name = PocketTheme.THEMES[i].name;
                assertTrue(name == R.string.horizon_theme_pure || name == R.string.horizon_theme_titanium
                        || name == R.string.horizon_theme_legacy || name == R.string.horizon_theme_signature
                        || name == R.string.horizon_theme_atmosphere || name == R.string.horizon_theme_horizon);
            }
        }
        // T01-T06: Signature, Atmosphere, Horizon, Pure OLED, Legacy, Titanium; Cinema shares a shelf
        assertEquals(6, own);
    }

    @Test
    public void everyBuiltInAccentIsReadable() {
        for (int accent : PocketTheme.ACCENTS) {
            assertTrue(Integer.toHexString(accent), HorizonLook.readableAccent(accent));
        }
        for (PocketTheme.Preset p : PocketTheme.THEMES) {
            assertTrue(Integer.toHexString(p.accent), HorizonLook.readableAccent(p.accent));
        }
    }
}
