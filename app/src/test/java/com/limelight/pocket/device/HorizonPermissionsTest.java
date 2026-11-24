package com.limelight.pocket.device;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.provider.Settings;
import com.limelight.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static com.limelight.pocket.device.HorizonPermissions.Capability.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HorizonPermissionsTest {
    @Test public void listsAllSixOptionalCapabilities() {
        assertEquals(6, HorizonPermissions.Capability.values().length);
        for (HorizonPermissions.Capability c : HorizonPermissions.Capability.values()) {
            assertNotEquals(0, c.title);
            assertNotEquals(0, c.description);
        }
    }
    @Test public void systemSettingsRoutesAreExplicitAndPackageScopedWhereNeeded() {
        Application c = RuntimeEnvironment.getApplication();
        Intent brightness = HorizonPermissions.settingsIntent(c, BRIGHTNESS);
        assertEquals(Settings.ACTION_MANAGE_WRITE_SETTINGS, brightness.getAction());
        assertEquals("package:" + c.getPackageName(), brightness.getDataString());
        Intent mic = HorizonPermissions.settingsIntent(c, MICROPHONE);
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, mic.getAction());
        assertEquals("package:" + c.getPackageName(), mic.getDataString());
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS,
                HorizonPermissions.settingsIntent(c, GLOBAL_BUTTONS).getAction());
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
                HorizonPermissions.settingsIntent(c, QUIET_MODE).getAction());
    }
    @Test public void microphoneReflectsCurrentGrantWithoutStartingActivity() {
        Application c = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(c).denyPermissions(Manifest.permission.RECORD_AUDIO);
        assertEquals(R.string.horizon_perm_missing, HorizonPermissions.status(c, MICROPHONE));
        Shadows.shadowOf(c).grantPermissions(Manifest.permission.RECORD_AUDIO);
        assertEquals(R.string.horizon_perm_granted, HorizonPermissions.status(c, MICROPHONE));
        assertNull(Shadows.shadowOf(c).getNextStartedActivity());
    }
    @Test public void readingAllStatusesDoesNotOpenSettings() {
        Application c = RuntimeEnvironment.getApplication();
        for (HorizonPermissions.Capability capability : HorizonPermissions.Capability.values()) {
            int value = HorizonPermissions.status(c, capability);
            assertTrue(value == R.string.horizon_perm_granted
                    || value == R.string.horizon_perm_missing || value == R.string.horizon_perm_unavailable);
        }
        assertNull(Shadows.shadowOf(c).getNextStartedActivity());
    }
}
