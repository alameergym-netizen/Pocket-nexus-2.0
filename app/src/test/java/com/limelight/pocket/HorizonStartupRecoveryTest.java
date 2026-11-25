package com.limelight.pocket;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import com.limelight.BuildConfig;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder;
import org.robolectric.shadows.ShadowProcess;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HorizonStartupRecoveryTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final Object first = new Object();
    private final Object next = new Object();

    @After public void clearOwners() {
        HorizonStartupRecovery.end(first);
        HorizonStartupRecovery.end(next);
        HorizonStartupRecovery.take(context);
    }

    @Test public void onlyTheCurrentAutomaticStartupCanRecordAOneShotPause() throws IOException {
        beginResume(first);
        beginResume(next);
        HorizonStartupRecovery.end(first);
        HorizonStartupRecovery.recordFatal(context);
        assertTrue(HorizonStartupRecovery.take(context));
        assertFalse(HorizonStartupRecovery.take(context));
        HorizonStartupRecovery.end(next);
        HorizonStartupRecovery.recordFatal(context);
        assertFalse(HorizonStartupRecovery.take(context));
    }

    @Test public void manualLaunchClearsAnEarlierMarkerAndNeverArmsRecovery() throws IOException {
        beginResume(first);
        HorizonStartupRecovery.recordFatal(context);
        HorizonStartupRecovery.begin(context, next, new Intent());
        HorizonStartupRecovery.recordFatal(context);
        assertFalse(HorizonStartupRecovery.take(context));
    }

    @Test public void discardsStaleFutureAndOldVersionMarkers() throws IOException {
        long now = System.currentTimeMillis();
        putMarker(BuildConfig.VERSION_CODE, now - 180_000L);
        assertFalse(HorizonStartupRecovery.take(context));
        putMarker(BuildConfig.VERSION_CODE, now + 180_000L);
        assertFalse(HorizonStartupRecovery.take(context));
        putMarker(BuildConfig.VERSION_CODE - 1, now);
        assertFalse(HorizonStartupRecovery.take(context));
    }

    @Test public void recentNativeCrashInThePreviousAutomaticStartPausesOnce() throws IOException {
        long now = System.currentTimeMillis();
        int previousPid = Process.myPid() + 105;
        putAttempt(previousPid, now - 1_000);
        recordExit(previousPid, processName(), ApplicationExitInfo.REASON_CRASH_NATIVE, now);
        assertTrue(HorizonStartupRecovery.take(context));
        assertFalse(HorizonStartupRecovery.take(context));
    }

    @Test public void applicationNotRespondingExitCanPauseButOldCrashCannot() throws IOException {
        long now = System.currentTimeMillis();
        int previousPid = Process.myPid() + 108;
        putAttempt(previousPid, now - 20_000);
        recordExit(previousPid, processName(), ApplicationExitInfo.REASON_ANR, now - 1_000);
        assertTrue(HorizonStartupRecovery.take(context));

        putAttempt(previousPid + 1, now - 300_000);
        recordExit(previousPid + 1, processName(), ApplicationExitInfo.REASON_CRASH, now - 180_000);
        assertFalse(HorizonStartupRecovery.take(context));
    }

    @Test public void abnormalExitFromOutsideStartupDoesNotPauseAutomaticResume() throws IOException {
        long now = System.currentTimeMillis();
        int previousPid = Process.myPid() + 200;
        putAttempt(previousPid, now - 1_000);
        recordExit(previousPid, processName(), ApplicationExitInfo.REASON_USER_REQUESTED, now);
        assertFalse(HorizonStartupRecovery.take(context));

        putAttempt(previousPid + 1, now - 20_000);
        recordExit(previousPid + 1, processName(), ApplicationExitInfo.REASON_CRASH_NATIVE, now - 30_000);
        assertFalse(HorizonStartupRecovery.take(context));

        putAttempt(previousPid + 2, now - 1_000);
        recordExit(previousPid + 2, processName() + ":other", ApplicationExitInfo.REASON_ANR, now);
        assertFalse(HorizonStartupRecovery.take(context));
    }

    @Test public void activeStartupStaysRecordedUntilItsOwnerEnds() {
        ShadowProcess.setPid(200);
        beginResume(first);
        assertFalse(HorizonStartupRecovery.take(context));
        assertTrue(attemptFile().isFile());
        HorizonStartupRecovery.end(next);
        assertTrue(attemptFile().isFile());
        HorizonStartupRecovery.end(first);
        assertFalse(attemptFile().exists());
    }

    private void beginResume(Object owner) {
        Intent intent = new Intent().putExtra(HorizonStartupRecovery.EXTRA_RESUME_START, true);
        HorizonStartupRecovery.begin(context, owner, intent);
        assertFalse(intent.hasExtra(HorizonStartupRecovery.EXTRA_RESUME_START));
    }

    private void putMarker(int version, long at) throws IOException {
        File file = new File(context.getNoBackupFilesDir(), "horizon_startup_recovery");
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(file))) {
            output.writeInt(version);
            output.writeLong(at);
        }
    }

    private void putAttempt(int pid, long time) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(attemptFile()))) {
            out.writeInt(BuildConfig.VERSION_CODE);
            out.writeInt(pid);
            out.writeLong(time);
        }
    }

    private File attemptFile() { return new File(context.getNoBackupFilesDir(), "horizon_startup_attempt"); }

    private String processName() {
        String process = context.getApplicationInfo().processName;
        return process == null ? context.getPackageName() : process;
    }

    private void recordExit(int pid, String process, int reason, long at) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        Shadows.shadowOf(manager).addApplicationExitInfo(ApplicationExitInfoBuilder.newBuilder()
                .setPid(pid).setProcessName(process).setReason(reason).setTimestamp(at).build());
    }
}
