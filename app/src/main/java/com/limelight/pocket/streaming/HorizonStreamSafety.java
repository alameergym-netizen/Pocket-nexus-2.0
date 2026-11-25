package com.limelight.pocket.streaming;

/** Opt-in limits applied to one stream configuration. */
public final class HorizonStreamSafety {
    public enum Thermal { UNRESTRICTED, SEVERE, CRITICAL }
    public enum Reason { NONE, LOW_BATTERY, BATTERY_SAVER, SEVERE_THERMAL, CRITICAL_THERMAL }

    public static final int LOW_BATTERY_WIDTH = 1280;
    public static final int LOW_BATTERY_HEIGHT = 720;
    public static final int LOW_BATTERY_FPS = 60;

    public static final class Decision {
        public final int bitrateKbps;
        public final Reason reason;

        private Decision(int bitrateKbps, Reason reason) {
            this.bitrateKbps = bitrateKbps;
            this.reason = reason;
        }
    }

    private HorizonStreamSafety() { }

    public static Decision limit(int requestedKbps, boolean batterySaver, Thermal thermal) {
        return limit(requestedKbps, batterySaver, thermal, false);
    }

    public static Decision limit(int requestedKbps, boolean batterySaver, Thermal thermal, boolean lowBattery) {
        int cap = Integer.MAX_VALUE;
        Reason reason = Reason.NONE;
        if (thermal == Thermal.CRITICAL) {
            cap = 15000;
            reason = Reason.CRITICAL_THERMAL;
        } else if (lowBattery) {
            cap = 15000;
            reason = Reason.LOW_BATTERY;
        } else if (thermal == Thermal.SEVERE) {
            cap = 25000;
            reason = Reason.SEVERE_THERMAL;
        } else if (batterySaver) {
            cap = 30000;
            reason = Reason.BATTERY_SAVER;
        }
        if (requestedKbps <= cap) return new Decision(requestedKbps, Reason.NONE);
        return new Decision(cap, reason);
    }

    public static int lowBatteryPercent(int level, int scale, int plugged) {
        if (level < 0 || scale <= 0 || level > scale || plugged != 0) return -1;
        int percent = Math.round(level * 100f / scale);
        return percent <= 20 ? percent : -1;
    }

    public static boolean exceedsLowBatteryResolution(int width, int height) {
        return (long) width * height > LOW_BATTERY_WIDTH * LOW_BATTERY_HEIGHT;
    }
}
