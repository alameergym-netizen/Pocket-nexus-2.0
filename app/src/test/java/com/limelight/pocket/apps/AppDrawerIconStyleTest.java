package com.limelight.pocket.apps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.ui.HorizonIconStyle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The App Drawer's own tile drawing, in each icon style, checked pixel by pixel. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, application = Application.class)
public class AppDrawerIconStyleTest {
    private static final int SIZE = 120;
    private static final int ACCENT = 0xFF86F0C8;
    private static final int TILE = 0xFF16181D;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PocketPrefs.prefs(context).edit().putInt(PocketPrefs.KEY_ACCENT, ACCENT).commit();
    }

    private Bitmap tile(String style, Drawable icon) {
        PocketPrefs.prefs(context).edit().putString(HorizonIconStyle.KEY, style).commit();
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Bitmap drawn = AppDrawerActivity.iconTile(context, out, icon, null, SIZE);
        assertNotNull(drawn);
        return drawn;
    }

    /** Blue background, nothing in front; a small black monochrome mark in the middle (or none). */
    private static AdaptiveIconDrawable icon(boolean monochrome) {
        Drawable mark = new InsetDrawable(new ColorDrawable(Color.BLACK), 0.4f);
        return monochrome
                ? new AdaptiveIconDrawable(new ColorDrawable(Color.BLUE), new ColorDrawable(0), mark)
                : new AdaptiveIconDrawable(new ColorDrawable(Color.BLUE), new ColorDrawable(0));
    }

    private static void near(String what, int want, int got) {
        int d = Math.abs(Color.red(want) - Color.red(got)) + Math.abs(Color.green(want) - Color.green(got))
                + Math.abs(Color.blue(want) - Color.blue(got));
        assertEquals(what + String.format(" #%08X vs #%08X", want, got), true, d <= 24);
    }

    @Test
    public void fullFillsTheTileWithTheIconsBackground() {
        Bitmap b = tile(HorizonIconStyle.FULL, icon(true));
        near("corner", Color.BLUE, b.getPixel(2, 2));
        near("centre", Color.BLUE, b.getPixel(SIZE / 2, SIZE / 2));
    }

    @Test
    public void themedDrawsTheAppsMonochromeMarkInTheAccentOnADarkTile() {
        Bitmap b = tile(HorizonIconStyle.THEMED, icon(true));
        near("corner is the dark tile", TILE, b.getPixel(2, 2));
        near("the mark takes the accent", ACCENT, b.getPixel(SIZE / 2, SIZE / 2));
    }

    @Test
    public void themedKeepsTheFullIconWhenTheAppHasNoMonochrome() {
        Bitmap b = tile(HorizonIconStyle.THEMED, icon(false));
        near("no guessing: the app's own colours", Color.BLUE, b.getPixel(2, 2));
    }

    @Test
    public void classicPutsTheIconInACircleOnADarkTile() {
        Bitmap b = tile(HorizonIconStyle.CLASSIC, icon(false));
        near("corner is the dark tile", TILE, b.getPixel(2, 2));
        near("centre is the icon", Color.BLUE, b.getPixel(SIZE / 2, SIZE / 2));
    }

    @Test
    public void aFlatIconSitsOnATileInItsOwnColoursInEveryStyle() {
        for (String style : HorizonIconStyle.STYLES) {
            Bitmap b = tile(style, new ColorDrawable(Color.RED));
            near(style + " centre", Color.RED, b.getPixel(SIZE / 2, SIZE / 2));
        }
    }
}
