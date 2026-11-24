package com.limelight.pocket;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

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

@RunWith(RobolectricTestRunner.class)
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
}
