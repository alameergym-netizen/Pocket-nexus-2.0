package com.limelight.pocket.streaming;

/** Opt-in bitrate guard applied only to the next stream configuration. */
public final class HorizonStreamSafety {
    public enum Thermal { UNRESTRICTED, SEVERE, CRITICAL }
    public enum Reason { NONE, BATTERY_SAVER, SEVERE_THERMAL, CRITICAL_THERMAL }

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
        int cap = Integer.MAX_VALUE;
        Reason reason = Reason.NONE;
        if (thermal == Thermal.CRITICAL) {
            cap = 15000;
            reason = Reason.CRITICAL_THERMAL;
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
}
