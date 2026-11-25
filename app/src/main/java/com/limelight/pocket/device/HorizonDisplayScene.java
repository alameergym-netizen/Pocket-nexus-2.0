package com.limelight.pocket.device;

/** An opt-in brightness cap for the stream window when Android reports Battery Saver. */
public final class HorizonDisplayScene {
    private HorizonDisplayScene() { }

    public static final int[] LIMITS = {0, 30, 45, 60, 75};

    public static int limitIndex(int stored) {
        for (int i = 0; i < LIMITS.length; i++) {
            if (stored == LIMITS[i]) return i;
        }
        return 0;
    }

    public static int brightness(int baseline, int limit, Boolean batterySaver, boolean manual) {
        int cap = LIMITS[limitIndex(limit)];
        // Preserve system-managed brightness. A guessed cap could make the screen brighter.
        if (manual || baseline <= 0 || cap == 0 || !Boolean.TRUE.equals(batterySaver)) return baseline;
        return Math.min(baseline, cap);
    }
}
