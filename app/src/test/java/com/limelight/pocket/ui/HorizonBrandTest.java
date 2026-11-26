package com.limelight.pocket.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** S01: one original N mark in standard and pixel, white and black variants, used consistently. */
public class HorizonBrandTest {
    private static final Pattern PATH = Pattern.compile("android:pathData=\"([^\"]+)\"");
    private static final Pattern COLOR = Pattern.compile("android:fillColor=\"(#[0-9A-Fa-f]{8})\"");

    private static String read(String name) throws IOException {
        Path p = Paths.get("src/main/res/drawable", name + ".xml");
        if (!Files.exists(p)) p = Paths.get("app/src/main/res/drawable", name + ".xml");
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static List<String> all(Pattern p, String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(xml);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test public void lightSurfacesGetTheBlackMarkAndDarkOnesTheWhite() {
        assertEquals(HorizonBrand.STANDARD_WHITE, HorizonBrand.variant(false, 0xFF000000));
        assertEquals(HorizonBrand.STANDARD_WHITE, HorizonBrand.variant(false, 0xFF1A2432));
        assertEquals(HorizonBrand.STANDARD_BLACK, HorizonBrand.variant(false, 0xFFF2F2F2));
        assertEquals(HorizonBrand.PIXEL_WHITE, HorizonBrand.variant(true, 0xFF0D0A06));
        assertEquals(HorizonBrand.PIXEL_BLACK, HorizonBrand.variant(true, 0xFFE0C98A));
    }

    @Test public void midWidthsShowTheMarkNotATypedLetter() {
        assertEquals(HorizonBrand.HIDDEN, HorizonBrand.presence(640));
        assertEquals(HorizonBrand.MARK, HorizonBrand.presence(680));
        assertEquals(HorizonBrand.MARK, HorizonBrand.presence(939));
        assertEquals(HorizonBrand.MARK_AND_NAME, HorizonBrand.presence(940));
    }

    @Test public void blackVariantsAreTheSameShapesAsTheWhiteOnes() throws IOException {
        assertEquals(all(PATH, read("ic_pocket_mark")), all(PATH, read("ic_pocket_mark_black")));
        assertEquals(all(PATH, read("ic_pocket_mark_pixel")), all(PATH, read("ic_pocket_mark_pixel_black")));
    }

    @Test public void marksUseOnlyTheirInkAndItsSeventyPercentLight() throws IOException {
        String[][] inks = {{"ic_pocket_mark", "FFFFFF"}, {"ic_pocket_mark_pixel", "FFFFFF"},
                {"ic_pocket_mark_black", "000000"}, {"ic_pocket_mark_pixel_black", "000000"},
                {"ic_launcher_pocket_fg", "FFFFFF"}, {"ic_launcher_pocket_mono", "FFFFFF"}};
        for (String[] ink : inks) {
            List<String> colors = all(COLOR, read(ink[0]));
            assertFalse(ink[0], colors.isEmpty());
            for (String c : colors) {
                String u = c.toUpperCase();
                assertTrue(ink[0] + " " + c, u.equals("#FF" + ink[1]) || u.equals("#B3" + ink[1]));
            }
        }
    }

    @Test public void theLauncherIconIsTheSameMarkScaled() throws IOException {
        // Two pillars and one diagonal, in the same order as the standard mark
        assertEquals(3, all(PATH, read("ic_pocket_mark")).size());
        assertEquals(3, all(PATH, read("ic_launcher_pocket_fg")).size());
        assertEquals(all(PATH, read("ic_launcher_pocket_fg")), all(PATH, read("ic_launcher_pocket_mono")));
    }
}
