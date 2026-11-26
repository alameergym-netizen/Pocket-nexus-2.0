package com.limelight.pocket.device.headphones;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketTheme;
import java.util.function.BooleanSupplier;

/** Home-only, best-effort read indicator. Never probes from the background or writes a setting. */
public final class HeadphonesHomeChip {
    private static final long PERIOD=30000, FAILURE_BACKOFF=60000;
    private final ConsoleActivity activity;
    private final BooleanSupplier active;
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView chip;
    private HeadphonesBluetooth.Session session;
    private boolean running, inventoryPending;
    private long nextRead, generation;
    private final float density;
    private AudioManager audioManager;
    private boolean routeListenerRegistered;
    private final AudioDeviceCallback routeListener=new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { routeChanged(added); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { routeChanged(removed); }
    };
    private void routeChanged(AudioDeviceInfo[] devices) {
        if(!running || devices==null) return;
        for(AudioDeviceInfo d:devices) {
            int t=d.getType();
            if(t==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t==AudioDeviceInfo.TYPE_BLE_HEADSET || t==AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                invalidateReading();
                // The next foreground tick verifies the new inventory. Never infer battery from a route event.
                break;
            }
        }
    }
    public HeadphonesHomeChip(ConsoleActivity activity,BooleanSupplier active) {
        this.activity=activity; this.active=active; density=activity.getResources().getDisplayMetrics().density;
        View battery=activity.findViewById(R.id.pocketBattery);
        if(battery==null || !(battery.getParent() instanceof LinearLayout)) return;
        LinearLayout parent=(LinearLayout)battery.getParent();
        chip=new TextView(activity);
        chip.setTextSize(11); chip.setTypeface(PocketFonts.medium());
        chip.setTextColor(0xFFFFFFFF);
        chip.setSingleLine(true); chip.setIncludeFontPadding(false);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(dp(8),0,dp(8),0);
        chip.setVisibility(View.GONE); chip.setFocusable(false);
        GradientDrawable bg=new GradientDrawable();
        bg.setCornerRadius(dp(14)); bg.setColor(0x30101016);
        bg.setStroke(Math.max(1,dp(1)),(PocketTheme.accent(activity)&0xFFFFFF)|0x88000000);
        chip.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(26));
        lp.setMarginStart(dp(8));
        parent.addView(chip,parent.indexOfChild(battery),lp);
        chip.setOnClickListener(v->{
            if(!canRead()) return;
            invalidateReading();
            HeadphonesPanel.openHome(activity);
        });
    }
    private int dp(int v) { return Math.round(v*density); }
    private boolean canRead() {
        return running && chip!=null && chip.isAttachedToWindow() && !activity.isFinishing() && !activity.isDestroyed()
                && activity.hasWindowFocus() && active.getAsBoolean() && !activity.isConsolePanelShowing()
                && Build.VERSION.SDK_INT>=31 && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                ==PackageManager.PERMISSION_GRANTED;
    }
    /** Do not squeeze the section tabs, device battery or clock on compact displays. */
    private boolean hasRoom() {
        if(chip==null || !(chip.getParent() instanceof ViewGroup)) return false;
        ViewGroup parent=(ViewGroup)chip.getParent();
        int spare=0;
        for(int i=0;i<parent.getChildCount();i++) if(parent.getChildAt(i) instanceof Space) spare+=parent.getChildAt(i).getWidth();
        return spare+(chip.getVisibility()==View.VISIBLE?chip.getWidth()+dp(8):0)>=dp(100);
    }
    public void resume() {
        if(chip==null || running) return;
        running=true; nextRead=0;
        if(Build.VERSION.SDK_INT>=31) try {
            audioManager=(AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
            if(audioManager!=null) { audioManager.registerAudioDeviceCallback(routeListener,main); routeListenerRegistered=true; }
        } catch(RuntimeException ignored) { routeListenerRegistered=false; }
        main.post(tick);
    }
    public void pause() {
        running=false; main.removeCallbacks(tick);
        if(routeListenerRegistered && audioManager!=null) try { audioManager.unregisterAudioDeviceCallback(routeListener); }
        catch(RuntimeException ignored) {}
        routeListenerRegistered=false; audioManager=null;
        invalidateReading();
    }
    private void invalidateReading() {
        generation++;
        if(session!=null) session.cancel();
        session=null; nextRead=0;
        if(chip!=null) chip.setVisibility(View.GONE);
    }
    private void discover() {
        inventoryPending=true;
        long ticket=generation;
        Context app=activity.getApplicationContext();
        HeadphonesDiscovery.WORKER.execute(()->{
            HeadphonesDiscovery.Inventory inventory=HeadphonesDiscovery.read(app);
            HeadphonesDiscovery.Candidate candidate=inventory.automatic();
            main.post(()->{
                inventoryPending=false;
                if(ticket!=generation || !canRead() || !hasRoom()) return;
                if(candidate==null) {
                    chip.setVisibility(View.GONE); nextRead=SystemClock.elapsedRealtime()+PERIOD;
                    return;
                }
                session=HeadphonesBluetooth.start(activity,candidate.device);
            });
        });
    }
    private final Runnable tick=new Runnable() {
        public void run() {
            if(!running) return;
            if(!canRead() || !hasRoom()) {
                if(session!=null || inventoryPending || chip.getVisibility()==View.VISIBLE) invalidateReading();
            } else if(session!=null) {
                HeadphonesBluetooth.Result r=session.result();
                if(r.status!=HeadphonesBluetooth.Status.READING) {
                    session=null;
                    HeadphonesReadback.Battery b=r.battery;
                    Integer level=HeadphonesHomeStatus.level(b);
                    if(level!=null && (r.status==HeadphonesBluetooth.Status.OBSERVED || r.status==HeadphonesBluetooth.Status.PARTIAL)) {
                        String shortText="Buds "+level+"%";
                        chip.setText(shortText);
                        chip.setContentDescription("FreeBuds 7i, "+HeadphonesHomeStatus.description(b)+". Open Headphones Center");
                        chip.setVisibility(View.VISIBLE);
                        nextRead=SystemClock.elapsedRealtime()+PERIOD;
                    } else {
                        chip.setVisibility(View.GONE);
                        nextRead=SystemClock.elapsedRealtime()+FAILURE_BACKOFF;
                    }
                }
            } else if(!inventoryPending && SystemClock.elapsedRealtime()>=nextRead) discover();
            main.postDelayed(this,session!=null?250:1000);
        }
    };
}
