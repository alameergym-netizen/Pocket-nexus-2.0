package com.limelight.pocket.device;

import android.Manifest;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBluetoothAdapter;

import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HorizonBluetoothPairingTest {
    @Test public void needsExplicitPermissionAndReportsActualRadioState() {
        Application context = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        assertEquals(HorizonBluetoothPairing.Result.NEEDS_PERMISSION, HorizonBluetoothPairing.observeFreeBuds(context));
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        ShadowBluetoothAdapter radio = Shadows.shadowOf(manager.getAdapter());
        radio.setState(BluetoothAdapter.STATE_OFF);
        assertEquals(HorizonBluetoothPairing.Result.RADIO_OFF, HorizonBluetoothPairing.observeFreeBuds(context));
        radio.setState(BluetoothAdapter.STATE_TURNING_ON);
        assertEquals(HorizonBluetoothPairing.Result.UNAVAILABLE, HorizonBluetoothPairing.observeFreeBuds(context));
    }

    @Test public void reportsOnlyMatchingExistingBondRecordsAndRechecksAfterRemoval() {
        Application context = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT);
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();
        ShadowBluetoothAdapter radio = Shadows.shadowOf(adapter);
        radio.setState(BluetoothAdapter.STATE_ON);
        BluetoothDevice device = adapter.getRemoteDevice("02:00:00:00:00:01");
        Shadows.shadowOf(device).setName("FreeBuds 7i-like");
        radio.setBondedDevices(Collections.singleton(device));
        assertEquals(HorizonBluetoothPairing.Result.NO_MATCH, HorizonBluetoothPairing.observeFreeBuds(context));
        Shadows.shadowOf(device).setName("HUAWEI FreeBuds 7i");
        assertEquals(HorizonBluetoothPairing.Result.BONDED_BY_NAME, HorizonBluetoothPairing.observeFreeBuds(context));
        radio.setBondedDevices(Collections.emptySet());
        assertEquals(HorizonBluetoothPairing.Result.NO_MATCH, HorizonBluetoothPairing.observeFreeBuds(context));
    }
}
