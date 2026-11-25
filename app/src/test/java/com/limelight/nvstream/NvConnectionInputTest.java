package com.limelight.nvstream;

import static org.junit.Assert.*;

import android.app.Application;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.pocket.core.HorizonConnectionLease;
import com.limelight.shadows.ShadowMoonBridge;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class, shadows = ShadowMoonBridge.class)
public class NvConnectionInputTest {
    @Test public void staleControllerMouseAndKeepaliveCannotReachSuccessorBridge() {
        NvConnection oldConnection = testConnection();
        NvConnection newConnection = testConnection();
        HorizonConnectionLease oldLease = ReflectionHelpers.getField(oldConnection, "lease");
        HorizonConnectionLease newLease = ReflectionHelpers.getField(newConnection, "lease");
        assertTrue(oldLease.begin());
        assertTrue(newLease.begin());
        assertTrue(oldLease.enterNative());

        assertEquals(MoonBridge.LI_ERR_UNSUPPORTED, sendSampleInput(newConnection));
        assertEquals(0, sendSampleInput(oldConnection));
        assertInputCounts(1);

        assertTrue(oldLease.requestStop(() -> {}));
        assertEquals(MoonBridge.LI_ERR_UNSUPPORTED, sendSampleInput(oldConnection));
        assertInputCounts(1);
        assertTrue(oldLease.leaveNative());
        assertTrue(newLease.enterNative());

        assertEquals(MoonBridge.LI_ERR_UNSUPPORTED, sendSampleInput(oldConnection));
        assertEquals(0, sendSampleInput(newConnection));
        assertInputCounts(2);
    }

    private static NvConnection testConnection() {
        return new NvConnection(RuntimeEnvironment.getApplication(), new ComputerDetails.AddressTuple("127.0.0.1", 47989),
                47984, "fixture", null, null, null);
    }

    private static int sendSampleInput(NvConnection connection) {
        connection.sendMouseMove((short) 1, (short) 2);
        connection.sendEmptyPayload();
        return connection.sendControllerArrivalEvent((byte) 0, (short) 1, (byte) 0, 0, (short) 0);
    }

    private static void assertInputCounts(int expected) {
        assertEquals(expected, ShadowMoonBridge.mouseMoves);
        assertEquals(expected, ShadowMoonBridge.controllerArrivals);
        assertEquals(expected, ShadowMoonBridge.keepalives);
    }
}
