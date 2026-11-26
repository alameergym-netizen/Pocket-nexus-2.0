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

/** Home-only, best-effort read indicator. Never probes from the background or writes a setting. */
public final class HeadphonesHomeChip {
    private static final long PERIOD=30000, FAILURE_BACKOFF=60000;
    private final ConsoleActivity activity;
    public interface Active { boolean isActive(); }
    private final Active active;
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
    public HeadphonesHomeChip(ConsoleActivity activity,Active active) {
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
        chip.setVisibility(View.GONE);
        // Reachable with the controller (Up from Home's hero buttons) as well as by touch: a
        // dark scrim keeps the text readable over any art; focus fills it with the accent.
        chip.setFocusable(true); chip.setClickable(true);
        int accent=PocketTheme.accent(activity);
        GradientDrawable idle=new GradientDrawable();
        idle.setCornerRadius(dp(14)); idle.setColor(0x8C0B0D12);
        idle.setStroke(Math.max(1,dp(1)),(accent&0xFFFFFF)|0x88000000);
        GradientDrawable lit=new GradientDrawable();
        lit.setCornerRadius(dp(14)); lit.setColor(accent);
        lit.setStroke(Math.max(2,dp(2)),0xFFFFFFFF);
        android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused},lit);
        bg.addState(new int[]{},idle);
        chip.setBackground(bg);
        chip.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_focused},{}},
                new int[]{0xFF000000,0xFFFFFFFF}));
        androidx.core.view.ViewCompat.replaceAccessibilityAction(chip,
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                activity.getString(R.string.horizon_hp_home_open),null);
        chip.setOnKeyListener((v,keyCode,event)->{
            if(keyCode==android.view.KeyEvent.KEYCODE_DPAD_DOWN && exit!=null) {
                if(event.getAction()==android.view.KeyEvent.ACTION_DOWN) exit.run();
                return true;
            }
            // Nothing else up here: left, right and up stay on the chip
            return keyCode==android.view.KeyEvent.KEYCODE_DPAD_UP || keyCode==android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode==android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        });
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
    private Runnable exit;
    /** Where focus goes when it leaves the chip (Down), or when the chip hides while focused. */
    public void setExit(Runnable exit) { this.exit=exit; }
    /** Takes focus when the chip is showing; false when there is nothing to focus. */
    public boolean focus() {
        return chip!=null && chip.getVisibility()==View.VISIBLE && chip.isShown() && chip.requestFocus();
    }
    private void hideChip() {
        if(chip==null) return;
        boolean had=chip.isFocused();
        chip.setVisibility(View.GONE);
        if(had && exit!=null) exit.run();
    }
    private boolean canRead() {
        return running && chip!=null && chip.isAttachedToWindow() && !activity.isFinishing() && !activity.isDestroyed()
                && activity.hasWindowFocus() && active.isActive() && !activity.isConsolePanelShowing()
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
        hideChip();
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
                    hideChip(); nextRead=SystemClock.elapsedRealtime()+PERIOD;
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
                        chip.setText(activity.getString(R.string.horizon_hp_home_chip,level));
                        chip.setContentDescription(activity.getString(R.string.horizon_hp_home_cd,
                                HeadphonesHomeStatus.description(activity,b)));
                        chip.setVisibility(View.VISIBLE);
                        nextRead=SystemClock.elapsedRealtime()+PERIOD;
                    } else {
                        hideChip();
                        nextRead=SystemClock.elapsedRealtime()+FAILURE_BACKOFF;
                    }
                }
            } else if(!inventoryPending && SystemClock.elapsedRealtime()>=nextRead) discover();
            main.postDelayed(this,session!=null?250:1000);
        }
    };
}
