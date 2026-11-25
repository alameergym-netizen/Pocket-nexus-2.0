package com.limelight.pocket.streaming;

import com.limelight.preferences.PreferenceConfiguration;

import java.util.concurrent.atomic.AtomicBoolean;

/** An explicitly armed, process-local compatibility profile for one manual PC stream. */
public final class HorizonStreamCompatibility {
    private static final AtomicBoolean NEXT = new AtomicBoolean();
    private static final int MAX_BITRATE_KBPS = 10_000;

    private HorizonStreamCompatibility() {}

    public static boolean isArmed() {
        return NEXT.get();
    }

    public static void setArmed(boolean armed) {
        NEXT.set(armed);
    }

    public static boolean take(boolean automatic, int appId) {
        return !automatic && appId > 0 && NEXT.getAndSet(false);
    }

    public static void configure(PreferenceConfiguration current) {
        current.videoFormat = PreferenceConfiguration.FormatOption.FORCE_H264;
        current.enableHdr = false;
    }

    public static int capBitrate(int chosen) {
        return Math.min(chosen, MAX_BITRATE_KBPS);
    }
}
