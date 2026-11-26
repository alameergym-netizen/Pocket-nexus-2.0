package com.limelight.pocket.device;

import android.content.Context;
import android.view.Display;
import android.view.WindowManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

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
        Display.Mode active = mock(Display.Mode.class);
        Display.Mode sameResolution = mock(Display.Mode.class);
        Display.Mode alternateResolution = mock(Display.Mode.class);
        when(display.getMode()).thenReturn(active);
        when(active.getPhysicalWidth()).thenReturn(1920);
        when(active.getPhysicalHeight()).thenReturn(1080);
        when(active.getRefreshRate()).thenReturn(59.94f);
        when(active.getAlternativeRefreshRates()).thenReturn(new float[]{60f, 60f, Float.NaN});
        when(sameResolution.getRefreshRate()).thenReturn(60f);
        when(alternateResolution.getRefreshRate()).thenReturn(120f);
        when(display.getSupportedModes()).thenReturn(new Display.Mode[]{alternateResolution, active, sameResolution, active});
        HorizonDisplayModes reported = HorizonDisplayModes.read(display);
        assertEquals("1920 × 1080 · 59.94 Hz", reported.active);
        assertEquals("59.94, 60, 120 Hz", reported.supported);
        assertEquals("60 Hz", reported.seamless);
        assertNull(reported.adaptive);
    }

    @Test public void distinguishesAnEmptyAlternativeListFromAnUnavailableQuery() {
        Display display = mock(Display.class);
        Display.Mode active = mock(Display.Mode.class);
        when(display.getMode()).thenReturn(active);
        when(active.getAlternativeRefreshRates()).thenReturn(new float[0]);
        when(display.getSupportedModes()).thenThrow(new IllegalStateException());
        HorizonDisplayModes empty = HorizonDisplayModes.read(display);
        assertEquals("", empty.seamless);
        assertNull(empty.supported);

        when(active.getAlternativeRefreshRates()).thenThrow(new IllegalStateException());
        assertNull(HorizonDisplayModes.read(display).seamless);
    }
}
