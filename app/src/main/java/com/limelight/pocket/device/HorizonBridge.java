package com.limelight.pocket.device;

import android.os.Build;
import android.os.PowerManager;
import android.content.Context;
import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.ui.StreamMenuView;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only capability inventory. Does not bind to privileged services or alter kernel settings. */
public final class HorizonBridge {
    private HorizonBridge() {}

    private static String read(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[256];
            int n = in.read(data);
            if (n <= 0 || n == data.length) { return null; }
            return new String(data, 0, n, StandardCharsets.UTF_8).trim();
        } catch (Exception e) { return null; }
    }

    private static String frequency(Context c, File f, double scale) {
        long value = HorizonBridgePolicy.parseFrequency(read(f));
        return value < 0 ? c.getString(R.string.horizon_bridge_unknown)
                : String.format(Locale.ROOT, "%.0f MHz", value / scale);
    }

    public static void show(ConsoleActivity a) {
        List<StreamMenuView.Item> rows = new ArrayList<>();
        rows.add(StreamMenuView.Item.header(a.getString(R.string.horizon_bridge_identity)));
        rows.add(StreamMenuView.Item.info(a.getString(R.string.pocket_info_device), Build.MANUFACTURER + " " + Build.MODEL));
        rows.add(StreamMenuView.Item.info(a.getString(R.string.pocket_info_processor), DeviceInfoPanel.chipLabel()));
        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_bridge_firmware), Build.DISPLAY));
        boolean rp6 = HorizonBridgePolicy.isRp6(Build.MANUFACTURER, Build.MODEL);
        rows.add(StreamMenuView.Item.info("Retroid Pocket 6", a.getString(rp6
                ? R.string.horizon_bridge_detected : R.string.horizon_bridge_not_confirmed)));
        rows.add(StreamMenuView.Item.info("PServer", a.getString(R.string.horizon_bridge_unverified)));
        rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_bridge_read_only)));

        rows.add(StreamMenuView.Item.header(a.getString(R.string.horizon_bridge_cpu)));
        File cpu = new File("/sys/devices/system/cpu/cpufreq");
        File[] policies = null;
        try { policies = cpu.listFiles(); } catch (RuntimeException ignored) {}
        int count = 0;
        if (policies != null) {
            java.util.Arrays.sort(policies, (x, y) -> x.getName().compareTo(y.getName()));
            for (File policy : policies) {
                if (!policy.getName().matches("policy[0-9]{1,3}") || count >= 32) { continue; }
                String cores = read(new File(policy, "related_cpus"));
                String current = frequency(a, new File(policy, "scaling_cur_freq"), 1000.0);
                String limit = frequency(a, new File(policy, "scaling_max_freq"), 1000.0);
                rows.add(StreamMenuView.Item.info(policy.getName() + (cores == null ? "" : " · CPU " + cores), current));
                rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_bridge_limit, limit)));
                count++;
            }
        }
        if (count == 0) { rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_bridge_unknown))); }
        rows.add(StreamMenuView.Item.header(a.getString(R.string.horizon_bridge_gpu)));
        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_bridge_current),
                frequency(a, new File("/sys/class/kgsl/kgsl-3d0/gpuclk"), 1000000.0)));
        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_bridge_max),
                frequency(a, new File("/sys/class/kgsl/kgsl-3d0/max_gpuclk"), 1000000.0)));

        rows.add(StreamMenuView.Item.header(a.getString(R.string.horizon_bridge_safety)));
        PowerManager pm = (PowerManager) a.getSystemService(Context.POWER_SERVICE);
        int thermal = -1;
        boolean saver = false;
        try {
            if (pm != null) {
                saver = pm.isPowerSaveMode();
                if (Build.VERSION.SDK_INT >= 29) { thermal = pm.getCurrentThermalStatus(); }
            }
        } catch (RuntimeException ignored) {}
        int[] advice = {R.string.horizon_bridge_advice_unknown, R.string.horizon_bridge_advice_normal,
                R.string.horizon_bridge_advice_reduce, R.string.horizon_bridge_advice_stop};
        rows.add(StreamMenuView.Item.note(a.getString(advice[HorizonBridgePolicy.recommendation(thermal, saver)])));
        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_power_title), HorizonPower.batteryPower(a)));
        HandheldTools.addTools(a, rows);
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_bridge_refresh), () -> show(a)));
        a.showConsolePanel(a.getString(R.string.horizon_bridge_title), null, null, rows);
    }
}
