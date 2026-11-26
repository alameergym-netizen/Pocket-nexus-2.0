package com.limelight.pocket.device;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import com.limelight.R;

import java.util.Set;

/** Reads an Android bond record without discovery, firmware guesses or vendor messages. */
final class HorizonBluetoothPairing {
    enum Result { NEEDS_PERMISSION, RADIO_OFF, NO_MATCH, BONDED_BY_NAME, UNAVAILABLE }

    private HorizonBluetoothPairing() {}

    static Result observeFreeBuds(Context context) {
        int permission = HorizonPermissions.status(context, HorizonPermissions.Capability.BLUETOOTH_DETAILS);
        if (permission == R.string.horizon_perm_missing) return Result.NEEDS_PERMISSION;
        if (permission != R.string.horizon_perm_granted) return Result.UNAVAILABLE;
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) return Result.UNAVAILABLE;
            int state = adapter.getState();
            if (state == BluetoothAdapter.STATE_OFF) return Result.RADIO_OFF;
            if (state != BluetoothAdapter.STATE_ON) return Result.UNAVAILABLE;
            Set<BluetoothDevice> paired = adapter.getBondedDevices();
            if (paired == null) return Result.UNAVAILABLE;
            for (BluetoothDevice device : paired) {
                if (HorizonBudsPolicy.matches7i(device.getName())) return Result.BONDED_BY_NAME;
            }
            return adapter.getState() == BluetoothAdapter.STATE_ON ? Result.NO_MATCH : Result.UNAVAILABLE;
        } catch (RuntimeException unavailable) {
            return Result.UNAVAILABLE;
        }
    }
}
