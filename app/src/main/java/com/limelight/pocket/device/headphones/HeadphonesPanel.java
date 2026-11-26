package com.limelight.pocket.device.headphones;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.device.HorizonBudsPolicy;
import com.limelight.pocket.ui.StreamMenuView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A page of the existing Nexus controller/touch menu, never an external companion app.
 * Work stops on leaving the visible page. A mode change always uses fresh identity + readback. */
public final class HeadphonesPanel {
    private interface Host {
        void show(List<StreamMenuView.Item> rows);
        boolean visible(List<StreamMenuView.Item> rows);
        boolean update(List<StreamMenuView.Item> old, List<StreamMenuView.Item> rows);
    }
    private static final ExecutorService DISCOVERY=Executors.newSingleThreadExecutor();
    private static final long REFRESH_MS=15000;
    private final Activity activity;
    private final Host host;
    private final Handler main=new Handler(Looper.getMainLooper());
    private List<StreamMenuView.Item> rows;
    private List<Candidate> candidates=Collections.emptyList();
    private BluetoothDevice selected;
    private HeadphonesBluetooth.Session session;
    private HeadphonesReadback.Battery battery;
    private HeadphonesReadback.Noise noise;
    private boolean levels, choosing, reading, stopped, ready, auto=true, writing, suspended, cancelled;
    private boolean discoveryAttempted;
    private long nextRead;
    private String status="Detecting paired audio devices…";
    private String lastCommand="";
    private HeadphonesPanel(Activity activity, Host host) { this.activity=activity; this.host=host; }
    public static void open(ConsoleActivity activity) {
        new HeadphonesPanel(activity,new Host() {
            public void show(List<StreamMenuView.Item> r) { activity.pushConsolePage(activity.getString(R.string.horizon_headphones_center),r); }
            public boolean visible(List<StreamMenuView.Item> r) { return activity.isConsolePageCurrent(r); }
            public boolean update(List<StreamMenuView.Item> o,List<StreamMenuView.Item> r) { return activity.refreshConsolePage(o,r); }
        }).begin();
    }
    public static void open(Activity activity, StreamMenuView menu) { openMenu(activity,menu,false); }
    public static void openRoot(Activity activity, StreamMenuView menu) { openMenu(activity,menu,true); }
    private static void openMenu(Activity activity,StreamMenuView menu,boolean root) {
        new HeadphonesPanel(activity,new Host() {
            public void show(List<StreamMenuView.Item> r) {
                String title=activity.getString(R.string.horizon_headphones_center);
                if(root) menu.show(title,activity.getString(R.string.horizon_headphones_subtitle),null,r,
                    ConsoleActivity.hintA(activity,R.string.pocket_hint_select),
                    ConsoleActivity.hintB(activity,R.string.pocket_hint_back));
                else menu.push(title,r);
            }
            public boolean visible(List<StreamMenuView.Item> r) { return menu.isCurrentPage(r); }
            public boolean update(List<StreamMenuView.Item> o,List<StreamMenuView.Item> r) { return menu.refreshLivePage(o,r); }
        }).begin();
    }
    private void begin() {
        rows=makeRows(); host.show(rows);
        discover(); main.postDelayed(tick,150);
    }
    private boolean pageAlive() { return !stopped && !activity.isFinishing() && !activity.isDestroyed() && host.visible(rows); }
    private boolean alive() { return pageAlive() && activity.hasWindowFocus(); }
    private void publish() {
        if(!alive()) return;
        List<StreamMenuView.Item> next=makeRows();
        if(host.update(rows,next)) rows=next;
    }
    private static class Candidate {
        final BluetoothDevice device; final String name; final boolean available;
        Candidate(BluetoothDevice d,String n,boolean a) { device=d; name=n; available=a; }
    }
    private boolean permitted() {
        return Build.VERSION.SDK_INT>=31 && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                ==PackageManager.PERMISSION_GRANTED;
    }
    private void discover() {
        if(stopped || reading || session!=null) return;
        if(!permitted()) { status=Build.VERSION.SDK_INT<31?"Requires Android 12 or newer":"Nearby devices permission required"; publish(); return; }
        discoveryAttempted=true; reading=true; ready=false; selected=null; candidates=Collections.emptyList(); battery=null; noise=null;
        status="Detecting paired audio devices…"; publish();
        Context app=activity.getApplicationContext();
        DISCOVERY.execute(()->{
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
            final List<Candidate> result=found; final String error=failure;
            main.post(()->{
                reading=false;
                if(!alive()) { discoveryAttempted=false; return; }
                candidates=result;
                if(error!=null) status=error;
                else {
                    List<String> names=new ArrayList<>(); List<Boolean> connected=new ArrayList<>();
                    for(Candidate c:result) { names.add(c.name); connected.add(c.available); }
                    int index=HeadphonesSelection.automatic(names,connected);
                    if(index>=0) { choose(result.get(index)); return; }
                    status=result.isEmpty()?"Pair your earbuds in Android Bluetooth first":"Select paired earbuds to verify model";
                    choosing=true;
                }
                publish();
            });
        });
    }
    private void choose(Candidate c) {
        if(!alive() || reading || session!=null) return;
        selected=c.device; choosing=false; auto=true; ready=false; battery=null; noise=null; lastCommand="";
        start(null);
    }
    private void start(HeadphonesNoiseControl.Selection selection) {
        if(!alive() || selected==null || reading || session!=null || (selection!=null&&!ready)) return;
        if(!permitted()) { ready=false; status="Nearby devices permission required"; publish(); return; }
        cancelled=false; writing=selection!=null; ready=false;
        if(writing) lastCommand=label(selection);
        status=writing?"Applying one command…":"Refreshing · last readings";
        session=writing?HeadphonesBluetooth.startNoiseChange(activity,selected,selection):HeadphonesBluetooth.start(activity,selected);
        publish();
    }
    private final Runnable tick=new Runnable() {
        public void run() {
            if(!pageAlive()) { stop(); return; }
            if(!activity.hasWindowFocus()) {
                if(!suspended) {
                    suspended=true;
                    if(session!=null) session.cancel();
                    session=null; ready=false; writing=false; battery=null; noise=null;
                    status="Paused · return to refresh"; nextRead=0;
                }
                main.postDelayed(this,500);
                return;
            }
            if(suspended) {
                suspended=false; publish();
                if(selected==null && !reading) discover();
            }
            if(!discoveryAttempted && permitted() && selected==null && !reading) discover();
            if(session!=null) {
                HeadphonesBluetooth.Result r=session.result();
                if(r.status!=HeadphonesBluetooth.Status.READING) {
                    boolean wasWrite=writing;
                    session=null; writing=false;
                    boolean observed=r.noise!=null && (r.status==HeadphonesBluetooth.Status.OBSERVED || r.status==HeadphonesBluetooth.Status.PARTIAL);
                    if(wasWrite) {
                        lastCommand=lastCommand+": "+r.status.name().replace('_',' ');
                        noise=r.noise; status="Refreshing after command";
                    } else {
                        battery=r.battery; noise=r.noise;
                        ready=observed; status=observed?"Model verified · live readback":r.status.name().replace('_',' ');
                        if(!observed) auto=false;
                    }
                    nextRead=android.os.SystemClock.elapsedRealtime()+(wasWrite?750:REFRESH_MS);
                    if(wasWrite && !cancelled) auto=true;
                    publish();
                }
            } else if(auto && !reading && !choosing && selected!=null && android.os.SystemClock.elapsedRealtime()>=nextRead) start(null);
            main.postDelayed(this,150);
        }
    };
    private void stop() {
        stopped=true; main.removeCallbacks(tick);
        if(session!=null) session.cancel();
        session=null; ready=false;
    }
    private List<StreamMenuView.Item> makeRows() {
        List<StreamMenuView.Item> r=new ArrayList<>();
        r.add(StreamMenuView.Item.header(activity.getString(R.string.horizon_headphones_7i)));
        r.add(StreamMenuView.Item.info("Connection",status));
        r.add(StreamMenuView.Item.info("Left",percentage(battery==null?null:battery.left,battery==null?null:battery.leftCharging)));
        r.add(StreamMenuView.Item.info("Case",percentage(battery==null?null:battery.caseLevel,battery==null?null:battery.caseCharging)));
        r.add(StreamMenuView.Item.info("Right",percentage(battery==null?null:battery.right,battery==null?null:battery.rightCharging)));
        r.add(StreamMenuView.Item.header("Noise control"));
        for(HeadphonesNoiseControl.Selection s:new HeadphonesNoiseControl.Selection[]{HeadphonesNoiseControl.Selection.AWARENESS,HeadphonesNoiseControl.Selection.OFF,HeadphonesNoiseControl.Selection.ANC_AUTO}) r.add(modeRow(s));
        r.add(StreamMenuView.Item.repeatable("ANC levels",()->{ levels=!levels; publish(); }).withValue(levels?"Hide":"Show").withIcon("SPEED"));
        if(levels) for(HeadphonesNoiseControl.Selection s:new HeadphonesNoiseControl.Selection[]{HeadphonesNoiseControl.Selection.ADAPTIVE,HeadphonesNoiseControl.Selection.LIGHT,HeadphonesNoiseControl.Selection.BALANCED,HeadphonesNoiseControl.Selection.DEEP}) r.add(modeRow(s));
        if(!lastCommand.isEmpty()) r.add(StreamMenuView.Item.info("Last command",lastCommand));
        r.add(StreamMenuView.Item.header("Connection settings"));
        if(!permitted() && Build.VERSION.SDK_INT>=31) r.add(StreamMenuView.Item.repeatable("Allow Nearby devices",()->activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},7013)));
        r.add(StreamMenuView.Item.repeatable("Refresh now",()->{ if(selected==null) discover(); else {auto=true;start(null);} }));
        if(session!=null) r.add(StreamMenuView.Item.repeatable("Cancel",()->{ cancelled=true; if(session!=null)session.cancel(); auto=false; ready=false; }));
        r.add(StreamMenuView.Item.repeatable("Auto refresh",()->{auto=!auto;nextRead=android.os.SystemClock.elapsedRealtime();publish();}).withValue(auto?"On":"Off"));
        r.add(StreamMenuView.Item.repeatable("Choose paired device",()->{if(session==null&&!reading){choosing=!choosing;publish();}}));
        if(choosing) for(Candidate c:candidates) {
            String suffix="";
            try { String addr=c.device.getAddress(); suffix=addr.substring(Math.max(0,addr.length()-5)); } catch(RuntimeException ignored){}
            r.add(StreamMenuView.Item.repeatable(c.name+" · "+suffix,()->choose(c)).withValue(c.available?"Audio available":"Paired"));
        }
        r.add(StreamMenuView.Item.repeatable("Bluetooth settings",()->{
            try { activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); } catch(RuntimeException ignored){}
        }));
        r.add(StreamMenuView.Item.note("Readback controls only verified FreeBuds 7i. Hardware EQ and gestures are not available yet."));
        return r;
    }
    private StreamMenuView.Item modeRow(HeadphonesNoiseControl.Selection s) {
        String value=!ready?"—":s.matches(noise)?"Selected":"";
        return ready?StreamMenuView.Item.repeatable(label(s),()->start(s)).withValue(value)
                :StreamMenuView.Item.info(label(s),value);
    }
    private static String percentage(Integer n,Boolean charging) { return n==null?"—":n+"%"+(Boolean.TRUE.equals(charging)?" · Charging":""); }
    private static String label(HeadphonesNoiseControl.Selection s) {
        switch(s) { case ANC_AUTO:return "ANC"; case AWARENESS:return "Awareness"; case OFF:return "Off";
            case ADAPTIVE:return "Adaptive"; case LIGHT:return "Light"; case BALANCED:return "Balanced"; case DEEP:return "Deep";
            default:return s.name(); }
    }
}
