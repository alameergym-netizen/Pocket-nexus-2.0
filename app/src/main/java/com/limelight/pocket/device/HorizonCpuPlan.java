package com.limelight.pocket.device;

import java.util.TreeSet;

/** Dry-run CPU limits relative to the observed current ceiling, never a write operation. */
public final class HorizonCpuPlan {
    private HorizonCpuPlan() {}
    public enum Mode {
        ECO(60), BALANCED(80), PERFORMANCE(100);
        final int percent;
        Mode(int percent) { this.percent = percent; }
    }

    public static long[] parse(String values) {
        if (values == null || values.length() > 4096 || values.trim().isEmpty()) { return new long[0]; }
        TreeSet<Long> sorted = new TreeSet<>();
        for (String token : values.trim().split("\\s+")) {
            long value = HorizonBridgePolicy.parseFrequency(token);
            if (value < 0 || value > 10000000L || sorted.size() >= 256) { return new long[0]; }
            sorted.add(value);
        }
        long[] result = new long[sorted.size()];
        int i=0;
        for (Long v : sorted) { result[i++]=v; }
        return result;
    }

    /** Fail closed when the current limits or selectable steps are missing.
     * Never suggest above the observed maximum or below the current minimum.
     */
    public static long cap(Mode mode, long min, long max, long[] steps) {
        if (mode == null || min <= 0 || max < min || max > 10000000L || steps == null || steps.length == 0) {
            return -1;
        }
        long target = Math.max(min, max * mode.percent / 100);
        long best = -1;
        long lowest = Long.MAX_VALUE;
        for (long step : steps) {
            if (step < min || step > max) { continue; }
            lowest = Math.min(lowest,step);
            if (step <= target) { best = Math.max(best,step); }
        }
        return best >= 0 ? best : lowest == Long.MAX_VALUE ? -1 : lowest;
    }
}
