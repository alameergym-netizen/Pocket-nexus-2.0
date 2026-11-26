package com.pocketnexus.headphones.lab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.pocket.device.headphones.HeadphonesBluetooth;
import com.limelight.pocket.device.headphones.HeadphonesNoiseControl;
import com.limelight.pocket.device.headphones.HeadphonesReadback;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Owner-only experimental hardware test. No network access, auto pairing or background work. */
public final class LabActivity extends Activity {
    private static final int BT_REQUEST=7;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<Button> modeButtons=new ArrayList<>();
    private BluetoothDevice selected;
    private HeadphonesBluetooth.Session session;
    private TextView device, state, battery, noise;
    private Button choose, read, cancel;
    private boolean readConfirmed;
    private boolean writeInProgress;
    private boolean foreground;
    private CheckBox autoRefresh;
    private boolean choosingOrConfirming;
    private String lastCommand = "";
    private static final long REFRESH_MS=15000;
    private final Runnable automaticRead=() -> {
        if(foreground && autoRefresh.isChecked() && !choosingOrConfirming && session==null && selected!=null) start(null);
    };
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20),dp(18),dp(20),dp(20));
        panel.setBackgroundColor(Color.rgb(13,21,30));
        scroll.addView(panel);
        setContentView(scroll);
        TextView title=text(panel,"NEXUS / HEADPHONES LAB",24);
        title.setTextColor(Color.rgb(112,218,207));
        text(panel,"FreeBuds 7i • Experimental hardware test",16);
        text(panel,"Pair in Android first. Close other earbud control apps. Choose the exact bonded device. Read before trying a change. No values are simulated.",14);
        device=text(panel,"Device: not selected",16);
        choose=button(panel,"Choose paired device",this::chooseDevice);
        read=button(panel,"Refresh now",() -> start(null));
        autoRefresh=new CheckBox(this);
        autoRefresh.setText("Auto refresh (15 seconds, while open)");
        autoRefresh.setTextColor(Color.WHITE);
        autoRefresh.setChecked(true);
        panel.addView(autoRefresh);
        autoRefresh.setOnCheckedChangeListener((b,checked)-> {
            handler.removeCallbacks(automaticRead);
            if(checked) scheduleRead(300);
        });
        state=text(panel,"Status: ready",16);
        battery=text(panel,"Left: —  |  Right: —  |  Case: —",18);
        noise=text(panel,"Noise mode: not reported",18);
        cancel=button(panel,"Cancel operation",() -> cancelOperation("Cancelled. If a write was in progress, read again before another change."));
        text(panel,"EXPERIMENTAL NOISE CONTROL",16);
        for(HeadphonesNoiseControl.Selection s:HeadphonesNoiseControl.Selection.values()) {
            Button b=button(panel,label(s),() -> confirm(s));
            modeButtons.add(b);
        }
        text(panel,"ANC (earbud-selected level) is an experimental alternative mode command. Try it from Awareness to check the native voice prompt. Auto refresh pauses on failed reads; enable it again or refresh manually. No firmware, codec or hardware EQ writes.",14);
        updateControls();
        choose.requestFocus();
    }
    private TextView text(LinearLayout parent,String content,int size) {
        TextView t=new TextView(this);
        t.setText(content); t.setTextSize(size); t.setTextColor(Color.WHITE);
        t.setPadding(0,dp(7),0,dp(7)); parent.addView(t); return t;
    }
    private Button button(LinearLayout parent,String label,Runnable action) {
        Button b=new Button(this);
        b.setText(label); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setFocusable(true); b.setOnClickListener(v -> action.run());
        parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
        return b;
    }
    private boolean permission() { return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED; }
    private void chooseDevice() {
        if(session!=null || !foreground || choosingOrConfirming) return;
        handler.removeCallbacks(automaticRead);
        if(!permission()) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},BT_REQUEST); return; }
        try {
            BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null || !adapter.isEnabled()) { state.setText("Status: Bluetooth unavailable or off"); return; }
            Set<BluetoothDevice> bonded=adapter.getBondedDevices();
            if(bonded==null || bonded.isEmpty()) { state.setText("Status: no paired devices. Pair in Android settings first."); return; }
            List<BluetoothDevice> list=new ArrayList<>(bonded);
            List<String> names=new ArrayList<>();
            for(BluetoothDevice b:list) {
                String name=b.getName();
                String address=b.getAddress();
                String tail=address==null || address.length()<5?"":address.substring(address.length()-5);
                names.add((name==null || name.trim().isEmpty()?"Unnamed paired device":name)+(tail.isEmpty()?"":" • "+tail));
            }
            choosingOrConfirming=true;
            AlertDialog picker=new AlertDialog.Builder(this).setTitle("Select your FreeBuds 7i")
                    .setItems(names.toArray(new String[0]),(dialog,which)-> {
                        selected=list.get(which);
                        device.setText("Device: "+names.get(which));
                        resetReadings(); readConfirmed=false; lastCommand="";
                        state.setText("Status: selected; read to verify model");
                        updateControls();
                    }).setNegativeButton("Cancel",null).create();
            picker.setOnDismissListener(d->{ choosingOrConfirming=false; scheduleRead(300); });
            picker.show();
        } catch(RuntimeException denied) { state.setText("Status: Bluetooth permission or device unavailable"); }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==BT_REQUEST) {
            if(permission()) { state.setText("Status: permission granted; choose device"); if(foreground) chooseDevice(); }
            else state.setText("Status: Nearby devices permission denied");
        }
    }
    private void confirm(HeadphonesNoiseControl.Selection mode) {
        if(!readConfirmed || session!=null || selected==null || !foreground) return;
        handler.removeCallbacks(automaticRead);
        choosingOrConfirming=true;
        AlertDialog confirmation=new AlertDialog.Builder(this).setTitle("Experimental setting change")
                .setMessage("Send "+label(mode)+" to the selected FreeBuds 7i? The app will recheck the model, then read back the state. If the operation times out, read again.")
                .setNegativeButton("Cancel",null).setPositiveButton("Send once",(d,w)->start(mode)).create();
        confirmation.setOnDismissListener(d->{ choosingOrConfirming=false; if(session==null) scheduleRead(REFRESH_MS); });
        confirmation.show();
    }
    private void start(HeadphonesNoiseControl.Selection mode) {
        if(selected==null || session!=null || !foreground || (mode!=null&&!readConfirmed)) return;
        if(!permission()) { readConfirmed=false; updateControls(); state.setText("Status: permission required; choose device again"); return; }
        handler.removeCallbacks(automaticRead);
        writeInProgress=mode!=null; readConfirmed=false;
        // During a refresh the last observation is labelled as such; never treat it as fresh.
        if(writeInProgress) lastCommand="Requested "+label(mode);
        state.setText(writeInProgress?"Status: checking and sending one command… (last readings below)":"Status: refreshing… (last readings below)");
        session=mode==null?HeadphonesBluetooth.start(this,selected):HeadphonesBluetooth.startNoiseChange(this,selected,mode);
        updateControls(); handler.post(poll);
    }
    private final Runnable poll=new Runnable() {
        @Override public void run() {
            if(session==null || !foreground) return;
            HeadphonesBluetooth.Result result=session.result();
            if(result.status==HeadphonesBluetooth.Status.READING) { handler.postDelayed(this,150); return; }
            session=null;
            boolean wasWrite=writeInProgress;
            if(wasWrite) lastCommand=lastCommand+": "+result.status.name().replace('_',' ');
            String status="Status: "+result.status.name().replace('_',' ');
            if(!lastCommand.isEmpty()) status+="\n"+lastCommand;
            if(!wasWrite) resetReadings();
            else noise.setText("Noise mode: not reported (refreshing)");
            state.setText(status);
            if(result.battery!=null) {
                HeadphonesReadback.Battery b=result.battery;
                battery.setText("Left: "+level(b.left,b.leftCharging)+"  |  Right: "+level(b.right,b.rightCharging)+"  |  Case: "+level(b.caseLevel,b.caseCharging));
                if(b.overall!=null) battery.append("\nOverall: "+b.overall+"%");
            }
            if(result.noise!=null) noise.setText("Noise mode: "+result.noise.mode+" (reported level "+result.noise.rawLevel+")");
            // Automatic read still verifies identity and state before enabling each write.
            boolean observed=result.noise!=null &&
                    (result.status==HeadphonesBluetooth.Status.OBSERVED||result.status==HeadphonesBluetooth.Status.PARTIAL);
            readConfirmed=!wasWrite && observed;
            writeInProgress=false;
            if(!wasWrite && !observed) {
                autoRefresh.setChecked(false);
                state.append("\nAuto refresh paused; refresh manually or enable again.");
            }
            updateControls();
            scheduleRead(wasWrite?750:REFRESH_MS);
        }
    };
    private String level(Integer value,Boolean charging) {
        return value==null?"—":value+"%"+(Boolean.TRUE.equals(charging)?" (charging)":"");
    }
    private static String label(HeadphonesNoiseControl.Selection s) {
        switch(s) {
            case OFF:return "Off"; case AWARENESS:return "Awareness"; case ADAPTIVE:return "Adaptive ANC";
            case ANC_AUTO:return "ANC (earbud-selected level)";
            case LIGHT:return "Light ANC"; case BALANCED:return "Balanced ANC"; case DEEP:return "Deep ANC";
            default:return s.name();
        }
    }
    private void resetReadings() {
        battery.setText("Left: —  |  Right: —  |  Case: —");
        noise.setText("Noise mode: not reported");
    }
    private void cancelOperation(String message) {
        handler.removeCallbacks(poll);
        handler.removeCallbacks(automaticRead);
        autoRefresh.setChecked(false);
        if(session!=null) session.cancel();
        session=null; readConfirmed=false; writeInProgress=false; resetReadings();
        state.setText("Status: "+message); updateControls();
    }
    private void updateControls() {
        boolean idle=session==null;
        choose.setEnabled(idle); read.setEnabled(idle&&selected!=null);
        cancel.setEnabled(!idle);
        for(Button b:modeButtons) b.setEnabled(idle&&readConfirmed);
    }
    private void scheduleRead(long delay) {
        handler.removeCallbacks(automaticRead);
        if(foreground && selected!=null && autoRefresh.isChecked() && session==null && !choosingOrConfirming)
            handler.postDelayed(automaticRead,delay);
    }
    @Override protected void onStart() { super.onStart(); foreground=true; scheduleRead(300); }
    @Override protected void onStop() {
        foreground=false;
        handler.removeCallbacks(automaticRead);
        if(session!=null) cancelOperation("Stopped. Read again after returning.");
        else { readConfirmed=false; updateControls(); }
        super.onStop();
    }
}
