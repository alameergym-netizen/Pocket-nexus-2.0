package com.limelight.pocket.apps;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class HomeScreenLauncherTest {
    @Test public void excludesNexusAndSystemFallbackFromLauncherEscape() {
        Context context = RuntimeEnvironment.getApplication();
        ResolveInfo self = launcher(context.getPackageName(), HomeScreen.class.getName());
        ResolveInfo fallback = launcher("com.android.settings", "com.android.settings.FallbackHome");
        ResolveInfo external = launcher("test.launcher", "test.launcher.Home");
        assertEquals(Arrays.asList(new ComponentName("test.launcher", "test.launcher.Home")),
                HomeScreen.otherLaunchers(context, Arrays.asList(self, fallback, external, external)));
    }

    @Test public void onlyOffersEnabledExportedLaunchersWithoutMissingPermissions() {
        Context context = RuntimeEnvironment.getApplication();
        ResolveInfo invisible = launcher("test.private", "test.private.Home");
        invisible.activityInfo.exported = false;
        ResolveInfo disabled = launcher("test.disabled", "test.disabled.Home");
        disabled.activityInfo.enabled = false;
        ResolveInfo privileged = launcher("test.privileged", "test.privileged.Home");
        privileged.activityInfo.permission = "test.permission.PRIVILEGED";
        List<ComponentName> launchers = HomeScreen.otherLaunchers(context,
                Arrays.asList(invisible, disabled, privileged, new ResolveInfo()));
        assertTrue(launchers.isEmpty());
    }

    private static ResolveInfo launcher(String name, String activity) {
        ResolveInfo target = new ResolveInfo();
        target.activityInfo = new ActivityInfo();
        target.activityInfo.packageName = name;
        target.activityInfo.name = activity;
        target.activityInfo.enabled = true;
        target.activityInfo.exported = true;
        return target;
    }
}
