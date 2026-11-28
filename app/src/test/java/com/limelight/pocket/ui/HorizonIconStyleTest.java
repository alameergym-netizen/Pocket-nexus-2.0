package com.limelight.pocket.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class HorizonIconStyleTest {
    @Test
    public void storedValuesAreKnownStylesAndCycle() {
        assertEquals(HorizonIconStyle.FULL, HorizonIconStyle.normalize(null));
        assertEquals(HorizonIconStyle.FULL, HorizonIconStyle.normalize("neon"));
        assertEquals(HorizonIconStyle.THEMED, HorizonIconStyle.next(HorizonIconStyle.FULL));
        assertEquals(HorizonIconStyle.CLASSIC, HorizonIconStyle.next(HorizonIconStyle.THEMED));
        assertEquals(HorizonIconStyle.FULL, HorizonIconStyle.next(HorizonIconStyle.CLASSIC));
    }

    @Test
    public void onlyAdaptiveIconsAreRestyledAndThemedNeedsTheAppsMonochrome() {
        Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        assertFalse("a flat icon is drawn the usual way",
                HorizonIconStyle.draw(c, new ColorDrawable(Color.RED), 100, HorizonIconStyle.CLASSIC, Color.WHITE));
        AdaptiveIconDrawable plain = new AdaptiveIconDrawable(new ColorDrawable(Color.BLUE), new ColorDrawable(0));
        assertFalse("full is the usual way", HorizonIconStyle.draw(c, plain, 100, HorizonIconStyle.FULL, Color.WHITE));
        assertFalse("no monochrome layer: not recoloured by guesswork",
                HorizonIconStyle.draw(c, plain, 100, HorizonIconStyle.THEMED, Color.WHITE));
        AdaptiveIconDrawable themed = new AdaptiveIconDrawable(new ColorDrawable(Color.BLUE), new ColorDrawable(0),
                new ColorDrawable(Color.BLACK));
        assertTrue(HorizonIconStyle.draw(c, themed, 100, HorizonIconStyle.THEMED, 0xFF86F0C8));
        assertTrue(HorizonIconStyle.draw(c, plain, 100, HorizonIconStyle.CLASSIC, Color.WHITE));
    }
}
