package com.limelight.pocket.device;

import android.os.Build;
import android.view.Display;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Android display capabilities at the time the handheld information panel opens. */
final class HorizonDisplayModes {
    String active;
    String supported;
    String seamless;
    Boolean adaptive;

    static HorizonDisplayModes read(Display display) {
        HorizonDisplayModes result = new HorizonDisplayModes();
        if (display == null) return result;
        DecimalFormat hz = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Display.Mode mode = display.getMode();
                if (mode != null) {
                    if (validRate(mode.getRefreshRate())) {
                        result.active = mode.getPhysicalWidth() + " × " + mode.getPhysicalHeight()
                                + " · " + hz.format(mode.getRefreshRate()) + " Hz";
                    }
                    if (Build.VERSION.SDK_INT >= 31) result.seamless = rates(mode.getAlternativeRefreshRates(), hz);
                }
            } catch (RuntimeException unavailable) { /* The display may have been disconnected. */ }
            try {
                Display.Mode[] modes = display.getSupportedModes();
                float[] values = new float[modes.length];
                for (int i = 0; i < modes.length; i++) {
                    if (modes[i] != null) values[i] = modes[i].getRefreshRate();
                }
                result.supported = rates(values, hz);
            } catch (RuntimeException unavailable) { /* Keep independent observations. */ }
        } else {
            try {
                float rate = display.getRefreshRate();
                if (validRate(rate)) result.active = hz.format(rate) + " Hz";
                result.supported = rates(display.getSupportedRefreshRates(), hz);
            } catch (RuntimeException unavailable) { /* Older Android has no Display.Mode. */ }
        }
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                result.adaptive = display.hasArrSupport();
            } catch (RuntimeException unavailable) { /* The capability was not reported. */ }
        }
        return result;
    }

    private static boolean validRate(float rate) {
        return rate > 0 && !Float.isInfinite(rate);
    }

    private static String rates(float[] values, DecimalFormat hz) {
        if (values == null) return null;
        Set<Integer> hundredths = new TreeSet<>();
        for (float value : values) {
            if (validRate(value)) hundredths.add(Math.round(value * 100));
        }
        if (hundredths.isEmpty()) return values.length == 0 ? "" : null;
        StringBuilder list = new StringBuilder();
        for (int value : hundredths) {
            if (list.length() > 0) list.append(", ");
            list.append(hz.format(value / 100d));
        }
        return list.append(" Hz").toString();
    }
}
