package com.limelight.pocket;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HorizonSecureScreenPairingTest {
    private static final String FIXTURE = ":47111:" + "a".repeat(64) + ":" + "b".repeat(64);
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void encryptedRouteRequiresAGrantForThisPcAndANetworkAddress() {
        context.getSharedPreferences("horizon_secure_status", Context.MODE_PRIVATE).edit()
                .putString("pc-a", "NEXUS4" + FIXTURE)
                .putString("pc-b", "NEXUS2" + FIXTURE).commit();
        assertTrue(HorizonSecureStatus.hasScreenAccess(context, "192.0.2.10", "pc-a", PcPower.LOCK));
        assertFalse(HorizonSecureStatus.hasScreenAccess(context, "192.0.2.10", "pc-b", PcPower.LOCK));
        assertFalse(HorizonSecureStatus.hasScreenAccess(context, "192.0.2.10", "unknown", PcPower.LOCK));
        assertFalse(HorizonSecureStatus.hasScreenAccess(context, UsbBridge.HOST_IP, "pc-a", PcPower.LOCK));
        assertFalse(HorizonSecureStatus.hasScreenAccess(context, null, "pc-a", PcPower.LOCK));
        assertFalse(HorizonSecureStatus.hasScreenAccess(context, "192.0.2.10", "pc-a", PcPower.RESTART));
    }
}
