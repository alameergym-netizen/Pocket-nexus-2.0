package com.limelight.pocket;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;

import androidx.annotation.RequiresApi;

import com.limelight.BuildConfig;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** One-shot recovery after an abnormal exit during automatic PC stream startup. */
public final class HorizonStartupRecovery {
    static final String EXTRA_RESUME_START = "pocket.resume.start";
    private static final long MAX_AGE_MS = 120_000L;
    private static final long MAX_STARTUP_MS = 600_000L;
    private static volatile Object startingResume;
    private static File startingAttemptFile;

    private HorizonStartupRecovery() {}

    public static synchronized boolean begin(Context context, Object owner, Intent intent) {
        boolean automatic = intent.getBooleanExtra(EXTRA_RESUME_START, false);
        startingResume = automatic ? owner : null;
        intent.removeExtra(EXTRA_RESUME_START);
        marker(context).delete();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File attempt = attemptFile(context);
            attempt.delete();
            startingAttemptFile = null;
            if (automatic) {
                try (DataOutputStream output = new DataOutputStream(new FileOutputStream(attempt))) {
                    output.writeInt(BuildConfig.VERSION_CODE);
                    output.writeInt(Process.myPid());
                    output.writeLong(System.currentTimeMillis());
                    startingAttemptFile = attempt;
                } catch (IOException | SecurityException ignored) {
                    // Missing process-exit evidence leaves normal launch and Java recovery available.
                }
            }
        }
        return automatic;
    }

    public static synchronized void end(Object owner) {
        if (startingResume != owner) return;
        startingResume = null;
        if (startingAttemptFile != null) {
            startingAttemptFile.delete();
            startingAttemptFile = null;
        }
    }

    static void recordFatal(Context context) throws IOException {
        if (startingResume == null) return;
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(marker(context)))) {
            output.writeInt(BuildConfig.VERSION_CODE);
            output.writeLong(System.currentTimeMillis());
        }
    }

    static synchronized boolean take(Context context) {
        boolean javaFailure = takeJavaFailure(context);
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && takeProcessFailure(context)) || javaFailure;
    }

    private static boolean takeJavaFailure(Context context) {
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

    @RequiresApi(Build.VERSION_CODES.R)
    private static boolean takeProcessFailure(Context context) {
        File attempt = attemptFile(context);
        if (!attempt.isFile()) return false;
        boolean currentProcess = false;
        try (DataInputStream input = new DataInputStream(new FileInputStream(attempt))) {
            int version = input.readInt();
            int pid = input.readInt();
            long startedAt = input.readLong();
            long now = System.currentTimeMillis();
            if (version != BuildConfig.VERSION_CODE || pid <= 0 || startedAt <= 0 || startedAt > now
                    || now - startedAt >= MAX_STARTUP_MS + MAX_AGE_MS) return false;
            currentProcess = pid == Process.myPid();
            if (currentProcess) return false;

            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager == null) return false;
            String processName = context.getApplicationInfo().processName;
            if (processName == null) processName = context.getPackageName();
            for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(context.getPackageName(), pid, 1)) {
                int reason = exit.getReason();
                long exitAt = exit.getTimestamp();
                if (exit.getPid() == pid && processName.equals(exit.getProcessName())
                        && exitAt >= startedAt && exitAt - startedAt < MAX_STARTUP_MS
                        && exitAt <= now && now - exitAt < MAX_AGE_MS
                        && (reason == ApplicationExitInfo.REASON_CRASH || reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                        || reason == ApplicationExitInfo.REASON_ANR)) return true;
            }
            return false;
        } catch (IOException | RuntimeException ignored) {
            return false;
        } finally {
            if (!currentProcess) attempt.delete();
        }
    }

    private static File marker(Context context) {
        return new File(context.getNoBackupFilesDir(), "horizon_startup_recovery");
    }

    private static File attemptFile(Context context) {
        return new File(context.getNoBackupFilesDir(), "horizon_startup_attempt");
    }
}
