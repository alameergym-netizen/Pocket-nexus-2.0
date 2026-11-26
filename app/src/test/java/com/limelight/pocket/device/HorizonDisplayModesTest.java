package com.limelight.pocket.device;

import android.content.Context;
import android.view.Display;
import android.view.WindowManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HorizonDisplayModesTest {
    @Test public void android13ReadsCurrentDisplayWithoutInferringAdaptiveSupport() {
        WindowManager windows = (WindowManager) RuntimeEnvironment.getApplication().getSystemService(Context.WINDOW_SERVICE);
        Display display = windows.getDefaultDisplay();
        HorizonDisplayModes reported = HorizonDisplayModes.read(display);
        assertNotNull(reported.active);
        assertTrue(reported.active.startsWith(display.getMode().getPhysicalWidth() + " × "));
        assertNotNull(reported.supported);
        assertNull(reported.adaptive);
    }

    @Test public void preservesFractionalRatesAndSeparatesSeamlessAlternativesFromAllModes() {
        Display display = mock(Display.class);
        Display.Mode active = mode(1, 1920, 1080, 59.94f, 60f, 60f, Float.NaN);
        Display.Mode sameResolution = mode(2, 1920, 1080, 60f);
        Display.Mode alternateResolution = mode(3, 1280, 720, 120f);
        when(display.getMode()).thenReturn(active);
        when(display.getSupportedModes()).thenReturn(new Display.Mode[]{alternateResolution, active, sameResolution, active});
        HorizonDisplayModes reported = HorizonDisplayModes.read(display);
        assertEquals("1920 × 1080 · 59.94 Hz", reported.active);
        assertEquals("59.94, 60, 120 Hz", reported.supported);
        assertEquals("60 Hz", reported.seamless);
        assertNull(reported.adaptive);
    }

    @Test public void distinguishesAnEmptyAlternativeListFromAnUnavailableQuery() {
        Display display = mock(Display.class);
        Display.Mode active = mode(1, 1920, 1080, 60f);
        when(display.getMode()).thenReturn(active);
        when(display.getSupportedModes()).thenThrow(new IllegalStateException());
        HorizonDisplayModes empty = HorizonDisplayModes.read(display);
        assertEquals("", empty.seamless);
        assertNull(empty.supported);

        when(display.getMode()).thenThrow(new IllegalStateException());
        assertNull(HorizonDisplayModes.read(display).seamless);
    }

    // The system creates Mode objects. Use the API 33 framework constructor in this fixture.
    private static Display.Mode mode(int id, int width, int height, float rate, float... alternatives) {
        return ReflectionHelpers.callConstructor(Display.Mode.class,
                ClassParameter.from(int.class, id), ClassParameter.from(int.class, width),
                ClassParameter.from(int.class, height), ClassParameter.from(float.class, rate),
                ClassParameter.from(float[].class, alternatives));
    }
}
