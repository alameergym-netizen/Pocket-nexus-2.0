package com.limelight.pocket.streaming;

/** Conservative starting points, not a throughput or end-to-end latency measurement. */
public final class HorizonLinkPolicy {
    private HorizonLinkPolicy() {}

    public static int suggestMbps(boolean wired, boolean wifi, boolean vpn,
                                  int signal, int band, int linkMbps,
                                  int attempts, int failures, int connectMs, int jitterMs) {
        if (attempts <= 0 || failures < 0 || failures >= attempts || connectMs < 0 || jitterMs < 0) {
            return 0;
        }
        int cap;
        if (vpn) { cap = 15; }
        else if (wired) { cap = 25; }
        else if (wifi && linkMbps > 0) {
            cap = Math.min(25, linkMbps / 5);
            if (signal <= 1 || band == 24) { cap = Math.min(cap, 10); }
        } else { return 0; }
        if (connectMs > 50 || jitterMs > 15 || failures > 0) { cap = Math.min(cap, 10); }
        if (connectMs > 100 || jitterMs > 30 || failures * 4 >= attempts) {
            cap = Math.min(cap, 5);
        }
        return cap >= 5 ? cap / 5 * 5 : 0;
    }

    /** No recommendation to start a heavy stream under severe thermal pressure. */
    public static int limitForThermal(int mbps, int thermalStatus, boolean batterySaver) {
        if (thermalStatus >= 3) { return 0; }
        return (thermalStatus == 2 || batterySaver) ? Math.min(mbps, 10) : mbps;
    }

    public static int suggestedFps(int mbps) { return mbps >= 10 ? 60 : 30; }
    public static String suggestedResolution(int mbps) {
        return mbps >= 15 ? "1920x1080" : "1280x720";
    }
}
