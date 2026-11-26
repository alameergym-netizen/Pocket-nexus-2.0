package com.limelight.pocket.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HorizonLookTest {
    private static HorizonLook sample() {
        return new HorizonLook(0xFFA8C8E8, "art", "glass", "crystal", "portal", "zoom", 1, 2, false, true, false);
    }

    @Test
    public void textRoundTripKeepsEveryField() throws Exception {
        HorizonLook got = HorizonLook.parse(sample().toText());
        assertEquals(sample(), got);
        assertEquals(sample().hashCode(), got.hashCode());
        assertEquals(0xFFA8C8E8, got.accent);
        assertEquals("portal", got.layout);
        assertEquals(1, got.density);
        assertEquals(2, got.corners);
        assertFalse(got.motion);
        assertFalse(got.liveGlass);
        assertFalse(got.frost());
    }

    @Test
    public void defaultsAreTheNexusPresetWithTheOriginalHome() {
        HorizonLook d = HorizonLook.defaults();
        assertTrue(d.valid());
        assertEquals(0xFFFFFFFF, d.accent);
        assertEquals("ambient", d.backdrop);
        assertEquals("aurora", d.sound);
        assertEquals("tiles", d.layout);
        assertEquals("ring_zoom", d.focus);
        assertTrue(d.frost());
    }

    @Test
    public void pastedTextMayCarryWindowsLineEndsAndSurroundingSpace() throws Exception {
        String pasted = "\n  " + sample().toText().replace("\n", "\r\n") + "\r\n\r\n";
        assertEquals(sample(), HorizonLook.parse(pasted));
    }

    @Test
    public void accentAlwaysOpaque() {
        assertEquals(0xFF102030, new HorizonLook(0x00102030, "art", "frost", "soft", "tiles", "ring", 0, 0,
                true, true, true).accent);
    }

    @Test
    public void presetReplacesColourMaterialAndSoundButKeepsGeometry() {
        HorizonLook base = sample();
        HorizonLook p = base.withPreset(0xFF112233, "black", true, "retro", null, null);
        assertEquals(0xFF112233, p.accent);
        assertEquals("black", p.backdrop);
        assertEquals("frost", p.material);
        assertEquals("retro", p.sound);
        assertEquals(base.layout, p.layout);
        assertEquals(base.focus, p.focus);
        assertEquals(base.density, p.density);
        assertEquals(base.corners, p.corners);
        assertEquals(base.motion, p.motion);
        HorizonLook withLayout = base.withPreset(0xFF112233, "black", false, "retro", "list", "ring");
        assertEquals("list", withLayout.layout);
        assertEquals("ring", withLayout.focus);
        assertEquals("glass", withLayout.material);
    }

    @Test
    public void equalityCoversEveryField() {
        HorizonLook a = sample();
        assertNotEquals(a, new HorizonLook(a.accent, a.backdrop, a.material, a.sound, a.layout, a.focus,
                a.density, a.corners, a.motion, a.colorArt, true));
        assertNotEquals(a, new HorizonLook(a.accent, a.backdrop, a.material, a.sound, a.layout, "ring",
                a.density, a.corners, a.motion, a.colorArt, a.liveGlass));
        assertNotEquals(a, null);
    }

    @Test
    public void rejectsTextWithoutTheHeader() {
        expect(HorizonLook.Invalid.NOT_A_LOOK, sample().toText().replace(HorizonLook.HEADER, "pocket-nexus-look 2"));
        expect(HorizonLook.Invalid.NOT_A_LOOK, "accent #FFFFFF");
        expect(HorizonLook.Invalid.NOT_A_LOOK, "");
        expect(HorizonLook.Invalid.NOT_A_LOOK, null);
        expect(HorizonLook.Invalid.NOT_A_LOOK, "{\"version\":1}");
    }

    @Test
    public void rejectsOversizedText() {
        StringBuilder b = new StringBuilder(sample().toText());
        while (b.length() <= HorizonLook.MAX_TEXT) {
            b.append(' ');
        }
        b.append("x");
        expect(HorizonLook.Invalid.NOT_A_LOOK, b.toString());
    }

    @Test
    public void rejectsUnknownDuplicateAndMissingKeys() {
        expect(HorizonLook.Invalid.UNSUPPORTED, sample().toText() + "stream-bitrate 150000\n");
        expect(HorizonLook.Invalid.UNSUPPORTED, sample().toText() + "accent #000000\n");
        expect(HorizonLook.Invalid.UNSUPPORTED, sample().toText().replace("live-glass off\n", ""));
        expect(HorizonLook.Invalid.UNSUPPORTED, sample().toText() + "orphan\n");
    }

    @Test
    public void rejectsValuesThisBuildCannotShow() {
        String text = sample().toText();
        expect(HorizonLook.Invalid.UNSUPPORTED, text.replace("accent #A8C8E8", "accent #A8C8E"));
        expect(HorizonLook.Invalid.UNSUPPORTED, text.replace("accent #A8C8E8", "accent A8C8E8F"));
        expect(HorizonLook.Invalid.UNSUPPORTED, text.replace("accent #A8C8E8", "accent #A8C8EG"));
        expect(HorizonLook.Invalid.UNSUPPORTED, text.replace("layout portal", "layout carousel3d"));
        expect(HorizonLook.Invalid.UNSUPPORTED, text.replace("backdrop art", "backdrop video"));
        expect(HorizonLook.Invalid.UNSUPPORTED, text.replace("sound crystal", "sound ../../boot"));
        expect(HorizonLook.Invalid.UNSUPPORTED, text.replace("density 1", "density 3"));
        expect(HorizonLook.Invalid.UNSUPPORTED, text.replace("corners 2", "corners -1"));
        expect(HorizonLook.Invalid.UNSUPPORTED, text.replace("motion off", "motion yes"));
        expect(HorizonLook.Invalid.UNSUPPORTED, text.replace("focus zoom", "focus "));
    }

    @Test
    public void validRequiresKnownValues() {
        assertFalse(new HorizonLook(0xFFFFFFFF, null, "frost", "soft", "tiles", "ring", 0, 0, true, true, true).valid());
        assertFalse(new HorizonLook(0xFFFFFFFF, "art", "frost", "soft", "tiles", "ring", 0, 5, true, true, true).valid());
        assertTrue(sample().valid());
    }

    private static void expect(int reason, String text) {
        try {
            HorizonLook.parse(text);
            fail("Accepted: " + text);
        } catch (HorizonLook.Invalid e) {
            assertEquals(text, reason, e.reason);
        }
    }

    @Test
    public void pastedAccentsMustBeReadableOnTheDarkScreens() {
        String text = sample().toText();
        // S10: a near-black accent would vanish as text on black and under dark text
        expect(HorizonLook.Invalid.UNREADABLE, text.replace("accent #A8C8E8", "accent #101010"));
        expect(HorizonLook.Invalid.UNREADABLE, text.replace("accent #A8C8E8", "accent #1E3A8A"));
        assertTrue(HorizonLook.readableAccent(0xFF3D8BFF));
        assertTrue(HorizonLook.readableAccent(0xFFFFFFFF));
        assertFalse(HorizonLook.readableAccent(0xFF000000));
        double l = HorizonLook.MIN_ACCENT_LUMINANCE;
        assertTrue("4.5:1 on black at the limit", (l + 0.05) / 0.05 >= 4.5);
        double ink = HorizonLook.luminance(0xFF101014);
        assertTrue("4.5:1 for #101014 text on the accent at the limit", (l + 0.05) / (ink + 0.05) >= 4.5);
    }
}
