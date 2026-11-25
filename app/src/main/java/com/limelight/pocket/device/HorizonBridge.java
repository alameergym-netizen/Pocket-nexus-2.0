package com.limelight.pocket.device;

import android.os.Build;
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
            java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[512];
            int n;
            while ((n = in.read(chunk)) != -1) {
                if (data.size() + n > 4096) { return null; }
                data.write(chunk,0,n);
            }
            return data.size() == 0 ? null : new String(data.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) { return null; }
    }

    private static String frequency(Context c, File f, double scale) {
        long value = HorizonBridgePolicy.parseFrequency(read(f));
        return value < 0 ? c.getString(R.string.horizon_bridge_unknown)
                : String.format(Locale.ROOT, "%.0f MHz", value / scale);
    }

    /** The hidden platform API may be blocked. Probe only; never launch or send commands. */
    private static void probe(ConsoleActivity a) {
        new Thread(() -> {
            int label = R.string.horizon_pserver_restricted;
            try {
                Object service = Class.forName("android.os.ServiceManager")
                        .getMethod("checkService", String.class).invoke(null, "PServerBinder");
                if (service == null) {
                    label = R.string.horizon_pserver_not_visible;
                } else if (service instanceof android.os.IBinder) {
                    android.os.IBinder binder = (android.os.IBinder) service;
                    label = binder.isBinderAlive() && binder.pingBinder()
                            ? R.string.horizon_pserver_alive : R.string.horizon_pserver_not_visible;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
            final int result = label;
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) { return; }
                List<StreamMenuView.Item> rows = new ArrayList<>();
                rows.add(StreamMenuView.Item.info("PServerBinder", a.getString(result)));
                rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_pserver_probe_limit)));
                a.showConsolePanel(a.getString(R.string.horizon_pserver_probe), null, null, rows);
            });
        }, "HorizonPServerProbe").start();
    }

    private static String mhz(Context c, long khz) {
        return khz <= 0 ? c.getString(R.string.horizon_bridge_unknown)
                : String.format(Locale.ROOT, "%.0f MHz", khz / 1000.0);
    }

    private static void preview(ConsoleActivity a) {
        new Thread(() -> {
            List<StreamMenuView.Item> rows = new ArrayList<>();
            rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_cpu_preview_note)));
            File[] policies = null;
            try { policies = new File("/sys/devices/system/cpu/cpufreq").listFiles(); }
            catch (RuntimeException ignored) {}
            int count = 0;
            if (policies != null) {
                java.util.Arrays.sort(policies, (x,y) -> x.getName().compareTo(y.getName()));
                for (File p : policies) {
                    if (!p.getName().matches("policy[0-9]{1,3}") || count >= 32) { continue; }
                    count++;
                    long min = HorizonBridgePolicy.parseFrequency(read(new File(p,"scaling_min_freq")));
                    long max = HorizonBridgePolicy.parseFrequency(read(new File(p,"scaling_max_freq")));
                    long[] available = HorizonCpuPlan.parse(read(new File(p,"scaling_available_frequencies")));
                    rows.add(StreamMenuView.Item.header(p.getName()));
                    rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_cpu_baseline),
                            mhz(a,min) + " – " + mhz(a,max)));
                    for (HorizonCpuPlan.Mode mode : HorizonCpuPlan.Mode.values()) {
                        long cap = HorizonCpuPlan.cap(mode,min,max,available);
                        rows.add(StreamMenuView.Item.info(mode.name(), mhz(a,cap)));
                    }
                }
            }
            if (count == 0) { rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_bridge_unknown))); }
            a.runOnUiThread(() -> {
                if (!a.isFinishing() && !a.isDestroyed()) {
                    a.showConsolePanel(a.getString(R.string.horizon_cpu_preview),null,null,rows);
                }
            });
        }, "HorizonCpuPreview").start();
    }

    private static String gpuMhz(Context c, long hz) {
        return hz <= 0 ? c.getString(R.string.horizon_bridge_unknown)
                : String.format(Locale.ROOT, "%.0f MHz", hz / 1000000.0);
    }

    private static void previewGpu(ConsoleActivity a) {
        new Thread(() -> {
            List<StreamMenuView.Item> rows = new ArrayList<>();
            rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_gpu_preview_note)));
            // devfreq uses Hz. Do not mix a KGSL maximum with a devfreq minimum.
            File devfreq = new File("/sys/class/kgsl/kgsl-3d0/devfreq");
            long min = HorizonBridgePolicy.parseFrequency(read(new File(devfreq,"min_freq")));
            long max = HorizonBridgePolicy.parseFrequency(read(new File(devfreq,"max_freq")));
            long[] steps = HorizonCpuPlan.parseSteps(read(new File(devfreq,"available_frequencies")), 10000000000L);
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_cpu_baseline),
                    gpuMhz(a,min)+" – "+gpuMhz(a,max)));
            for (HorizonCpuPlan.Mode mode : HorizonCpuPlan.Mode.values()) {
                rows.add(StreamMenuView.Item.info(mode.name(),gpuMhz(a,
                        HorizonCpuPlan.capWithin(mode,min,max,steps,10000000000L))));
            }
            a.runOnUiThread(() -> {
                if (!a.isFinishing() && !a.isDestroyed()) {
                    a.showConsolePanel(a.getString(R.string.horizon_gpu_preview),null,null,rows);
                }
            });
        }, "HorizonGpuPreview").start();
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
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_pserver_probe),
                () -> probe(a)));
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_cpu_preview),
                () -> preview(a)));
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_gpu_preview),
                () -> previewGpu(a)));
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
        HorizonPower.Observations power = HorizonPower.observe(a);
        int thermal = power.thermal;
        Boolean saver = power.batterySaver;
        int[] thermalLabel = {R.string.horizon_thermal_none, R.string.horizon_thermal_light,
                R.string.horizon_thermal_moderate, R.string.horizon_thermal_severe, R.string.horizon_thermal_critical,
                R.string.horizon_thermal_emergency, R.string.horizon_thermal_shutdown};
        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_thermal_status),
                a.getString(HorizonBridgePolicy.hasThermalStatus(thermal)
                        ? thermalLabel[thermal] : R.string.horizon_bridge_unknown)));
        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_bridge_battery_saver),
                a.getString(saver == null ? R.string.horizon_bridge_unknown
                        : saver ? R.string.pocket_on : R.string.pocket_off)));
        int[] advice = {R.string.horizon_bridge_advice_unknown, R.string.horizon_bridge_advice_normal,
                R.string.horizon_bridge_advice_reduce, R.string.horizon_bridge_advice_stop};
        rows.add(StreamMenuView.Item.note(a.getString(advice[
                HorizonBridgePolicy.recommendation(thermal, Boolean.TRUE.equals(saver))])));
        rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_power_title), HorizonPower.batteryPower(a)));
        HandheldTools.addTools(a, rows);
        rows.add(StreamMenuView.Item.action(a.getString(R.string.horizon_bridge_refresh), () -> show(a)));
        a.showConsolePanel(a.getString(R.string.horizon_bridge_title), null, null, rows);
    }
}
