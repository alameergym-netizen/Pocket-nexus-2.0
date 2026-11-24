package com.limelight.framegen;

import android.hardware.HardwareBuffer;
import android.view.Surface;

/**
 * The native frame generator (lsfg-vk on the user's Lossless.dll). Load it with {@link #load()}
 * first; every call is a thin JNI call into one global render loop (one stream at a time).
 */
public final class FrameGen {

    // Results (from the native side)
    public static final int OK = 0;
    public static final int ERR_DLL_UNREADABLE = -1;
    public static final int ERR_MISSING_RESOURCE = -2;
    public static final int ERR_TRANSLATION_FAILED = -3;
    public static final int ERR_WRITE_FAILED = -4;
    public static final int ERR_NO_VULKAN = -10;
    public static final int ERR_MISSING_SPIRV = -11;
    public static final int ERR_DRIVER_REJECTED = -12;

    private static volatile Boolean loaded;
    private static volatile String loadError;

    private FrameGen() {
    }

    /** Loads the native library (once). False when it can't run here (and why: {@link #loadError()}). */
    public static synchronized boolean load() {
        if (loaded != null) {
            return loaded;
        }
        if (android.os.Build.VERSION.SDK_INT < 29) {
            loadError = "Android 10 or newer is needed";
            loaded = false;
            return false;
        }
        try {
            System.loadLibrary("pocketfg");
            loaded = true;
        } catch (Throwable t) {
            loadError = t.getMessage() != null ? t.getMessage() : t.toString();
            loaded = false;
        }
        return loaded;
    }

    public static String loadError() {
        return loadError;
    }

    public static native String nativeVersion();

    /** Lossless.dll's shaders, translated to SPIR-V into cacheDir (once per DLL). */
    public static native int extractShaders(String dllPath, String dllSha256, String cacheDir);

    /** Checks that the GPU's driver takes every translated shader. */
    public static native int probeShaders(String cacheDir);

    public static native boolean isFramegenFp16Supported(String cacheDir);

    public static native int initContext(String cacheDir, int width, int height, int multiplier, float flowScale,
                                         boolean performance, boolean hdr, boolean antiArtifacts, boolean framegenFp16,
                                         boolean npuPostProcessing, int npuPreset, int npuUpscaleFactor, float npuAmount,
                                         float npuRadius, float npuThreshold, boolean npuFp16,
                                         boolean cpuPostProcessing, int cpuPreset, float cpuStrength, float cpuSaturation,
                                         float cpuVibrance, float cpuVignette,
                                         boolean gpuPostProcessing, int gpuStage, int gpuMethod, float gpuUpscaleFactor,
                                         float gpuSharpness, float gpuStrength,
                                         int targetFpsCap, float emaAlpha, float outlierRatio, float vsyncSlackMs,
                                         int queueDepth);

    public static native void setOutputSurface(Surface surface, int w, int h);

    public static native void pushFrame(HardwareBuffer hardwareBuffer, long timestampNs);

    public static native long getGeneratedFrameCount();

    public static native long getPostedFrameCount();

    public static native double getAverageLatencyMs();

    public static native void setBypass(boolean bypass);

    public static native void setVsyncPeriodNs(long periodNs);

    public static native void destroyContext();
}
