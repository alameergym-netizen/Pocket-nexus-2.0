package com.limelight.pocket.core;

/** Explicit per-game starting points; never automatically select a route or claim link capacity. */
public enum HorizonGamePreset {
    REMOTE_SAVER("1280x720", 60, 10000),
    REMOTE_BALANCED("1920x1080", 60, 15000),
    REMOTE_HIGH_REFRESH("1920x1080", 120, 20000),
    LOCAL_BALANCED("1920x1080", 60, 30000),
    LOCAL_HIGH_REFRESH("1920x1080", 120, 50000),
    USB_HIGH_REFRESH("1920x1080", 120, 60000);

    public final String resolution;
    public final int fps, kbps;
    HorizonGamePreset(String resolution, int fps, int kbps) {
        this.resolution = resolution;
        this.fps = fps;
        this.kbps = kbps;
    }
    public static HorizonGamePreset match(String resolution, int fps, int kbps) {
        for (HorizonGamePreset p : values()) {
            if (p.resolution.equals(resolution) && p.fps == fps && p.kbps == kbps) return p;
        }
        return null;
    }
    public String description() {
        return resolution.replace('x', '×') + " / " + fps + " FPS / " + (kbps / 1000) + " Mbps";
    }
}
