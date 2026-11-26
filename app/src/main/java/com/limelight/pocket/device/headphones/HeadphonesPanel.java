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
import com.limelight.pocket.device.headphones.HeadphonesDiscovery.Candidate;

/** A page of the existing Nexus controller/touch menu, never an external companion app.
 * Work stops on leaving the visible page. A mode change always uses fresh identity + readback. */
public final class HeadphonesPanel {
    private interface Host {
        void show(List<StreamMenuView.Item> rows);
        boolean visible(List<StreamMenuView.Item> rows);
        boolean update(List<StreamMenuView.Item> old, List<StreamMenuView.Item> rows);
    }
    // Passive Home reads and panel reads share the same discovery worker and socket lease.
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
    private int busyRetries;
    private long nextRead;
    private String status="Detecting paired audio devices…";
    private String lastCommand="";
    private HeadphonesPanel(Activity activity, Host host) { this.activity=activity; this.host=host; }
    public static void open(ConsoleActivity activity) { openConsole(activity,false); }
    public static void openHome(ConsoleActivity activity) { openConsole(activity,true); }
    private static void openConsole(ConsoleActivity activity,boolean root) {
        new HeadphonesPanel(activity,new Host() {
            public void show(List<StreamMenuView.Item> r) {
                String title=activity.getString(R.string.horizon_headphones_center);
                if(root) activity.showConsolePanel(title,activity.getString(R.string.horizon_headphones_subtitle),null,r);
                else activity.pushConsolePage(title,r);
            }
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
        HeadphonesDiscovery.WORKER.execute(()->{
            HeadphonesDiscovery.Inventory inventory=HeadphonesDiscovery.read(app);
            List<Candidate> found=inventory.candidates; String failure=inventory.error;
            final List<Candidate> result=found; final String error=failure;
            main.post(()->{
                reading=false;
                if(!alive()) { discoveryAttempted=false; return; }
                candidates=result;
                if(error!=null) status=error;
                else {
                    Candidate automatic=inventory.automatic();
                    if(automatic!=null) { choose(automatic); return; }
                    status=result.isEmpty()?"Pair your earbuds in Android Bluetooth first":"Select paired earbuds to verify model";
                    choosing=true;
                }
                publish();
            });
        });
    }
    private void choose(Candidate c) {
        if(!alive() || reading || session!=null) return;
        selected=c.device; choosing=false; auto=true; ready=false; busyRetries=0; battery=null; noise=null; lastCommand="";
        start(null);
    }
    private void start(HeadphonesNoiseControl.Selection selection) {
        if(!alive() || selected==null || reading || session!=null || (selection!=null&&!ready)) return;
        if(!permitted()) { ready=false; status="Nearby devices permission required"; publish(); return; }
        if(selection!=null) busyRetries=0;
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
                        if(!observed && r.status==HeadphonesBluetooth.Status.BUSY && busyRetries++<2) {
                            status="Waiting for previous read to close";
                        } else if(!observed) auto=false;
                        else busyRetries=0;
                    }
                    nextRead=android.os.SystemClock.elapsedRealtime()+(wasWrite?750:
                            r.status==HeadphonesBluetooth.Status.BUSY?1000:REFRESH_MS);
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
