package com.limelight.pocket.device;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Measurements exposed to ordinary Android apps by the installed kernel. */
final class HorizonPerformanceReadings {
    static final long MISSING = -1;

    static final class Frequency {
        final String name, cores, governor;
        final long current, minimum, maximum, divisor;
        final long[] steps;

        Frequency(String name, String cores, String governor, long current, long minimum, long maximum,
                  long divisor, long[] steps) {
            this.name = name;
            this.cores = cores;
            this.governor = governor;
            this.current = current;
            this.minimum = minimum;
            this.maximum = maximum;
            this.divisor = divisor;
            this.steps = steps;
        }
    }

    static final class Cooling {
        final String name;
        final long state, maximum;

        Cooling(String name, long state, long maximum) {
            this.name = name;
            this.state = state;
            this.maximum = maximum;
        }
    }

    final List<Frequency> cpu;
    final Frequency gpu;
    final List<Cooling> fans;

    private HorizonPerformanceReadings(List<Frequency> cpu, Frequency gpu, List<Cooling> fans) {
        this.cpu = cpu;
        this.gpu = gpu;
        this.fans = fans;
    }

    static HorizonPerformanceReadings sample(File cpuRoot, File kgsl, File thermalRoot) {
        List<Frequency> cpu = new ArrayList<>();
        for (File policy : children(cpuRoot, "policy[0-9]{1,3}")) {
            if (cpu.size() >= 32) break;
            long current = frequency(policy, "scaling_cur_freq", 10_000_000);
            if (current == MISSING) current = frequency(policy, "cpuinfo_cur_freq", 10_000_000);
            cpu.add(new Frequency(policy.getName(), text(policy, "related_cpus", "[0-9\\s,-]{1,128}"),
                    text(policy, "scaling_governor", "[A-Za-z0-9_-]{1,48}"), current,
                    frequency(policy, "scaling_min_freq", 10_000_000), frequency(policy, "scaling_max_freq", 10_000_000),
                    1000, HorizonCpuPlan.parse(read(new File(policy, "scaling_available_frequencies")))));
        }
        File devfreq = new File(kgsl, "devfreq");
        long gpuCurrent = frequency(devfreq, "cur_freq", 10_000_000_000L);
        long gpuMin = frequency(devfreq, "min_freq", 10_000_000_000L);
        long gpuMax = frequency(devfreq, "max_freq", 10_000_000_000L);
        if (gpuCurrent == MISSING && gpuMin == MISSING && gpuMax == MISSING) {
            gpuCurrent = frequency(kgsl, "gpuclk", 10_000_000_000L);
            gpuMax = frequency(kgsl, "max_gpuclk", 10_000_000_000L);
        }
        Frequency gpu = new Frequency("", "", text(devfreq, "governor", "[A-Za-z0-9_-]{1,48}"),
                gpuCurrent, gpuMin, gpuMax, 1_000_000,
                HorizonCpuPlan.parseSteps(read(new File(devfreq, "available_frequencies")), 10_000_000_000L));

        List<Cooling> fans = new ArrayList<>();
        for (File cooling : children(thermalRoot, "cooling_device[0-9]{1,4}")) {
            if (fans.size() >= 16) break;
            String name = text(cooling, "type", "(?i)([a-z0-9]+[-_])?fan[0-9]{0,2}");
            if (name.isEmpty()) continue;
            long current = coolingState(cooling, "cur_state");
            long maximum = coolingState(cooling, "max_state");
            if (maximum >= 0 && current > maximum) current = MISSING;
            fans.add(new Cooling(name, current, maximum));
        }
        return new HorizonPerformanceReadings(cpu, gpu, fans);
    }

    private static File[] children(File root, String pattern) {
        try {
            File[] files = root.listFiles((dir, name) -> name.matches(pattern));
            if (files != null) {
                Arrays.sort(files, (left, right) -> Integer.compare(numericSuffix(left.getName()), numericSuffix(right.getName())));
                return files;
            }
        } catch (RuntimeException ignored) { }
        return new File[0];
    }

    private static int numericSuffix(String name) {
        int start = name.length() - 1;
        while (start > 0 && Character.isDigit(name.charAt(start - 1))) start--;
        return Integer.parseInt(name.substring(start));
    }

    static String read(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] chunk = new byte[512];
            int n;
            while ((n = in.read(chunk)) != -1) {
                if (data.size() + n > 4096) return null;
                data.write(chunk, 0, n);
            }
            return data.size() == 0 ? null : new String(data.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (Exception unavailable) { return null; }
    }

    private static String text(File folder, String name, String format) {
        String value = read(new File(folder, name));
        return value != null && value.matches(format) ? value : "";
    }

    private static long frequency(File folder, String name, long limit) {
        long value = HorizonBridgePolicy.parseFrequency(read(new File(folder, name)));
        return value <= limit ? value : MISSING;
    }

    private static long coolingState(File folder, String name) {
        String value = text(folder, name, "[0-9]{1,4}");
        return value.isEmpty() ? MISSING : Long.parseLong(value);
    }
}
