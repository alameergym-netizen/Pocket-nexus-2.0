package com.limelight.pocket.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HorizonRemotePlayTest {
    private static final String PS_PACKAGE = "com.playstation.remoteplay";
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void xboxUsesOfficialBrowserEntryPoint() {
        Intent xbox = HorizonRemotePlay.XBOX.intent(context);
        assertEquals(Intent.ACTION_VIEW, xbox.getAction());
        assertTrue(xbox.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertEquals("https://www.xbox.com/remoteplay", xbox.getDataString());
    }

    @Test public void playStationOpensOfficialStoreWhenAppIsUnavailable() {
        Intent playStation = HorizonRemotePlay.PLAYSTATION.intent(context);
        assertEquals(Intent.ACTION_VIEW, playStation.getAction());
        assertEquals("play.google.com", playStation.getData().getHost());
        assertEquals(PS_PACKAGE, playStation.getData().getQueryParameter("id"));
    }

    @Test public void playStationLaunchesOfficialAppWhenInstalled() {
        ActivityInfo entry = new ActivityInfo();
        entry.name = PS_PACKAGE + ".FixtureActivity";
        entry.packageName = PS_PACKAGE;
        entry.applicationInfo = new ApplicationInfo();
        entry.applicationInfo.packageName = PS_PACKAGE;
        ResolveInfo app = new ResolveInfo();
        app.activityInfo = entry;
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(PS_PACKAGE);
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(launcher, app);

        Intent launched = HorizonRemotePlay.PLAYSTATION.intent(context);
        assertEquals(new ComponentName(PS_PACKAGE, entry.name), launched.getComponent());
    }
}
