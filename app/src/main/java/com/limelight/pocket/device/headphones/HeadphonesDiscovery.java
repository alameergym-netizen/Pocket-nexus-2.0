package com.limelight.pocket.device.headphones;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import com.limelight.pocket.device.HorizonBudsPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared bonded-device + available output inventory; not a playback-route claim. */
final class HeadphonesDiscovery {
    private HeadphonesDiscovery() {}
    static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    static final class Candidate {
        final BluetoothDevice device; final String name; final boolean available;
        Candidate(BluetoothDevice d,String n,boolean a) { device=d; name=n; available=a; }
    }
    static final class Inventory {
        final List<Candidate> candidates; final String error;
        Inventory(List<Candidate> c,String e) { candidates=c; error=e; }
        Candidate automatic() {
            List<String> names=new ArrayList<>(); List<Boolean> available=new ArrayList<>();
            for(Candidate c:candidates) { names.add(c.name); available.add(c.available); }
            int i=HeadphonesSelection.automatic(names,available);
            return i<0?null:candidates.get(i);
        }
    }
    static Inventory read(Context app) {
        List<Candidate> found=new ArrayList<>();
        String failure=null;
        try {
            BluetoothManager bm=(BluetoothManager)app.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=bm==null?null:bm.getAdapter();
            if(adapter==null || !adapter.isEnabled()) failure="Bluetooth is off or unavailable";
            else {
                AudioManager audio=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
                AudioDeviceInfo[] outputs=audio==null?new AudioDeviceInfo[0]:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                Set<BluetoothDevice> bonded=adapter.getBondedDevices();
                int named7i=0;
                if(bonded!=null) for(BluetoothDevice d:bonded) {
                    if(HorizonBudsPolicy.matches7i(d.getName())) named7i++;
                }
                if(bonded!=null) for(BluetoothDevice d:bonded) {
                    if(found.size()>=32) { failure="Too many paired devices; disconnect unused devices"; found.clear(); break; }
                    String name=d.getName(); if(name==null) name="Unnamed paired device";
                    boolean available=false;
                    for(AudioDeviceInfo o:outputs) {
                        int type=o.getType();
                        if(type!=AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && type!=AudioDeviceInfo.TYPE_BLE_HEADSET
                                && type!=AudioDeviceInfo.TYPE_BLE_SPEAKER) continue;
                        String address=o.getAddress();
                        if(address!=null && !address.isEmpty() && d.getAddress().equalsIgnoreCase(address)) {
                            available=true; break;
                        }
                        // Some Android builds omit output addresses. Only a unique named bond
                        // may use the matching name hint; the socket still verifies model identity.
                        CharSequence product=o.getProductName();
                        if((address==null || address.isEmpty()) && named7i==1 && HorizonBudsPolicy.matches7i(name)
                                && product!=null && HorizonBudsPolicy.matches7i(product.toString())) {
                            available=true; break;
                        }
                    }
                    found.add(new Candidate(d,name,available));
                }
            }
        } catch(RuntimeException unavailable) { failure="Bluetooth discovery unavailable"; found.clear(); }

        return new Inventory(found,failure);
    }
}
