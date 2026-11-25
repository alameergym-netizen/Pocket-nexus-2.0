package com.limelight.pocket;

import android.content.Context;
import android.content.Intent;

import com.limelight.BuildConfig;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** One-shot recovery after a fatal Java error during automatic PC stream startup. */
public final class HorizonStartupRecovery {
    static final String EXTRA_RESUME_START = "pocket.resume.start";
    private static final long MAX_AGE_MS = 120_000L;
    private static volatile Object startingResume;

    private HorizonStartupRecovery() {}

    public static synchronized boolean begin(Context context, Object owner, Intent intent) {
        boolean automatic = intent.getBooleanExtra(EXTRA_RESUME_START, false);
        startingResume = automatic ? owner : null;
        intent.removeExtra(EXTRA_RESUME_START);
        marker(context).delete();
        return automatic;
    }

    public static synchronized void end(Object owner) {
        if (startingResume == owner) startingResume = null;
    }

    static void recordFatal(Context context) throws IOException {
        if (startingResume == null) return;
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(marker(context)))) {
            output.writeInt(BuildConfig.VERSION_CODE);
            output.writeLong(System.currentTimeMillis());
        }
    }

    static boolean take(Context context) {
        File file = marker(context);
        if (!file.isFile()) return false;
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            int version = input.readInt();
            long recordedAt = input.readLong();
            long now = System.currentTimeMillis();
            return version == BuildConfig.VERSION_CODE && recordedAt > 0
                    && recordedAt <= now && now - recordedAt < MAX_AGE_MS;
        } catch (IOException ignored) {
            return false;
        } finally {
            file.delete();
        }
    }

    private static File marker(Context context) {
        return new File(context.getNoBackupFilesDir(), "horizon_startup_recovery");
    }
}
