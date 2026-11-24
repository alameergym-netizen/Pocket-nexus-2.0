package com.limelight.pocket.video;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.limelight.R;
import com.limelight.framegen.FrameGen;
import com.limelight.pocket.PocketPrefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

/**
 * Lossless Scaling, built in: the user's own Lossless.dll (from their copy of Lossless Scaling;
 * never shipped with the app) is copied in once, its shaders are translated for the handheld's
 * GPU and checked against its driver, and the stream can then use the real frame generation.
 * Its settings: how many frames for each real one, the flow scale, performance mode.
 */
public final class LsfgSetup {

    private static final String KEY_READY = "pocket_lsfg_ready";
    private static final String KEY_SHA = "pocket_lsfg_sha";
    public static final String KEY_MULTIPLIER = "pocket_lsfg_multiplier";
    public static final String KEY_FLOW = "pocket_lsfg_flow";
    public static final String KEY_PERFORMANCE = "pocket_lsfg_performance";
    public static final int[] MULTIPLIERS = {2, 3, 4};
    public static final float[] FLOWS = {0.5f, 0.75f, 1.0f};

    public interface Done {
        /** ok, and what to tell the user. */
        void onDone(boolean ok, String message);
    }

    private LsfgSetup() {
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "lsfg");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static File dll(Context c) {
        return new File(dir(c), "Lossless.dll");
    }

    static File cache(Context c) {
        File d = new File(dir(c), "spirv");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    /** The handheld can run it (Android 10+, the library loads). */
    public static boolean supported() {
        return FrameGen.load();
    }

    /** A Lossless.dll is in and its shaders were taken by this GPU. */
    public static boolean ready(Context c) {
        return PocketPrefs.prefs(c).getBoolean(KEY_READY, false) && dll(c).isFile() && supported();
    }

    public static int multiplier(Context c) {
        int m = PocketPrefs.prefs(c).getInt(KEY_MULTIPLIER, 2);
        return m >= 2 && m <= 4 ? m : 2;
    }

    public static float flowScale(Context c) {
        return PocketPrefs.prefs(c).getFloat(KEY_FLOW, 1.0f);
    }

    public static boolean performance(Context c) {
        return PocketPrefs.prefs(c).getBoolean(KEY_PERFORMANCE, true);
    }

    /** What the settings row says now. */
    public static String status(Context c) {
        if (!supported()) {
            return c.getString(R.string.pocket_lsfgb_unsupported);
        }
        return c.getString(ready(c) ? R.string.pocket_lsfgb_ready : R.string.pocket_lsfgb_needs_dll);
    }

    /**
     * Copies the picked Lossless.dll in, translates its shaders and checks them on this GPU
     * (on a thread of its own; a few seconds). done runs on the main thread.
     */
    public static void importDll(Context context, final Uri uri, final Done done) {
        final Context c = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean ok = false;
            String msg;
            try {
                if (!supported()) {
                    throw new IllegalStateException(c.getString(R.string.pocket_lsfgb_unsupported)
                            + (FrameGen.loadError() != null ? " (" + FrameGen.loadError() + ")" : ""));
                }
                File out = dll(c);
                File tmp = new File(out.getPath() + ".part");
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                long size = 0;
                try (InputStream in = c.getContentResolver().openInputStream(uri);
                     OutputStream o = new FileOutputStream(tmp)) {
                    if (in == null) {
                        throw new java.io.IOException("unreadable");
                    }
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        o.write(buf, 0, n);
                        sha.update(buf, 0, n);
                        size += n;
                    }
                }
                // Lossless.dll is a Windows program library of some megabytes ("MZ" at its start)
                if (size < 256 * 1024 || !startsWithMz(tmp)) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    throw new IllegalArgumentException(c.getString(R.string.pocket_lsfgb_not_dll));
                }
                if (!tmp.renameTo(out)) {
                    throw new java.io.IOException("can't keep it");
                }
                String hex = hex(sha.digest());
                File cache = cache(c);
                File[] old = cache.listFiles();
                if (old != null) {
                    for (File f : old) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
                int rc = FrameGen.extractShaders(out.getAbsolutePath(), hex, cache.getAbsolutePath());
                if (rc == FrameGen.OK) {
                    rc = FrameGen.probeShaders(cache.getAbsolutePath());
                }
                ok = rc == FrameGen.OK;
                PocketPrefs.prefs(c).edit().putBoolean(KEY_READY, ok).putString(KEY_SHA, hex).apply();
                msg = ok ? c.getString(R.string.pocket_lsfgb_loaded) : explain(c, rc);
            } catch (Throwable t) {
                PocketPrefs.prefs(c).edit().putBoolean(KEY_READY, false).apply();
                msg = t.getMessage() != null ? t.getMessage() : t.toString();
            }
            final boolean result = ok;
            final String message = msg;
            main.post(() -> done.onDone(result, message));
        }, "pocket-lsfg-import").start();
    }

    /** A native result in words. */
    static String explain(Context c, int rc) {
        switch (rc) {
            case FrameGen.ERR_DLL_UNREADABLE:
            case FrameGen.ERR_MISSING_RESOURCE:
                return c.getString(R.string.pocket_lsfgb_err_dll);
            case FrameGen.ERR_TRANSLATION_FAILED:
            case FrameGen.ERR_WRITE_FAILED:
                return c.getString(R.string.pocket_lsfgb_err_translate, rc);
            case FrameGen.ERR_NO_VULKAN:
            case FrameGen.ERR_DRIVER_REJECTED:
            case FrameGen.ERR_MISSING_SPIRV:
                return c.getString(R.string.pocket_lsfgb_err_gpu, rc);
            default:
                return c.getString(R.string.pocket_lsfgb_err_other, rc);
        }
    }

    private static boolean startsWithMz(File f) {
        try (InputStream in = new java.io.FileInputStream(f)) {
            return in.read() == 'M' && in.read() == 'Z';
        } catch (Exception e) {
            return false;
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) {
            s.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xFF));
        }
        return s.toString();
    }
}
