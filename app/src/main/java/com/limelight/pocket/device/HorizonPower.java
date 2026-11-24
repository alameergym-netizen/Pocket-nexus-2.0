package com.limelight.pocket.device;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.util.Locale;

/** Battery-side electrical estimate, not SoC power or a wall-power measurement. */
public final class HorizonPower {
    private HorizonPower() {}

    /** A positive value means charging, a negative value means discharge. */
    static double watts(int microamps, int millivolts) {
        if (microamps == Integer.MIN_VALUE || microamps == 0 || millivolts <= 0) {
            return Double.NaN;
        }
        return microamps * (double) millivolts / 1_000_000_000.0;
    }

    public static String batteryPower(Context c) {
        try {
            BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
            Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bm == null || battery == null) { return c.getString(com.limelight.R.string.horizon_power_unavailable); }
            int current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            double estimate = watts(current, voltage);
            if (Double.isNaN(estimate)) { return c.getString(com.limelight.R.string.horizon_power_unavailable); }
            return c.getString(com.limelight.R.string.horizon_power_value,
                    String.format(Locale.ROOT, "%.2f", Math.abs(estimate)),
                    c.getString(estimate > 0 ? com.limelight.R.string.horizon_power_charge
                            : com.limelight.R.string.horizon_power_discharge));
        } catch (RuntimeException ex) {
            return c.getString(com.limelight.R.string.horizon_power_unavailable);
        }
    }

    /** Ranking launcher tools only; never grants permissions or invokes privileged commands. */
    public static int toolPriority(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (n.contains("clustertune")) { return 100; }
        if (n.equals("pulse") || n.contains("pulseperformance")) { return 90; }
        if (n.contains("lossless") || n.contains("lsfg")) { return 0; }
        if (n.contains("performance")) { return 70; }
        if (n.contains("fan")) { return 60; }
        if (n.contains("retroid") || n.contains("odin") || n.contains("ayn")) { return 50; }
        return 0;
    }
}
