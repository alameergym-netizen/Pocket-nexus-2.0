package com.limelight.pocket.device.headphones;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in SPP probe and experimental explicit 7i noise change. No pairing or persistent service. */
public final class HeadphonesBluetooth {
    private HeadphonesBluetooth() {}
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService DEADLINE=Executors.newSingleThreadScheduledExecutor();
    private static final AtomicReference<Session> ACTIVE=new AtomicReference<>();
    public enum Status { READING, BUSY, PERMISSION_REQUIRED, UNAVAILABLE, MODEL_UNCONFIRMED,
                         OBSERVED, PARTIAL, TIMEOUT, CANCELLED, WRITE_CONFIRMED, ALREADY_SELECTED, WRITE_UNCONFIRMED }
    public static final class Result {
        public final Status status;
        public final HeadphonesReadback.Battery battery;
        public final HeadphonesReadback.Noise noise;
        private Result(Status s, HeadphonesReadback.Battery b, HeadphonesReadback.Noise n) {
            status=s; battery=b; noise=n;
        }
    }
    /** Caller explicitly chooses a bonded device and owns the returned handle.
     * Poll result() from the UI; cancel on dismissal. Do not persist readings. */
    public static Session start(Context context, BluetoothDevice selected) {
        return startInternal(context,selected,null);
    }
    /** Experimental only: UI must require explicit confirmation and physical verification before general release. */
    public static Session startNoiseChange(Context context, BluetoothDevice selected, HeadphonesNoiseControl.Selection selection) {
        if(selection==null) { Session s=new Session(); s.finish(Status.UNAVAILABLE,null); return s; }
        return startInternal(context,selected,selection);
    }
    private static Session startInternal(Context context,BluetoothDevice selected,HeadphonesNoiseControl.Selection selection) {
        Session s=new Session();
        if (context==null || selected==null) { s.finish(Status.UNAVAILABLE,null); return s; }
        Context app=context.getApplicationContext();
        if (app==null) { s.finish(Status.UNAVAILABLE,null); return s; }
        if (!ACTIVE.compareAndSet(null,s)) { s.finish(Status.BUSY,null); return s; }
        ScheduledFuture<?> deadline=DEADLINE.schedule(() -> s.stop(Status.TIMEOUT),12,TimeUnit.SECONDS);
        IO.execute(() -> {
            try {
                if (s.result().status!=Status.READING) return;
                if (Build.VERSION.SDK_INT<31 || app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        !=PackageManager.PERMISSION_GRANTED) { s.finish(Status.PERMISSION_REQUIRED,null); return; }
                BluetoothManager manager=(BluetoothManager)app.getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
                if (adapter==null || !adapter.isEnabled() || selected.getBondState()!=BluetoothDevice.BOND_BONDED
                        || !adapter.getBondedDevices().contains(selected)) { s.finish(Status.UNAVAILABLE,null); return; }
                BluetoothSocket socket=selected.createRfcommSocketToServiceRecord(SPP);
                if (!s.bind(socket)) { close(socket); return; }
                socket.connect();
                if (s.result().status!=Status.READING) return;
                if(selection!=null) {
                    HeadphonesNoiseControl.Change change=HeadphonesNoiseControl.change(socket.getInputStream(),
                            socket.getOutputStream(),selection,() -> s.result().status==Status.READING);
                    Status status;
                    switch(change.outcome) {
                        case MODEL_UNCONFIRMED: status=Status.MODEL_UNCONFIRMED; break;
                        case CONFIRMED: status=Status.WRITE_CONFIRMED; break;
                        case ALREADY_SELECTED: status=Status.ALREADY_SELECTED; break;
                        default: status=Status.WRITE_UNCONFIRMED;
                    }
                    s.finish(status,new HeadphonesProbe.Observation(false,null,change.observed));
                    return;
                }
                HeadphonesProbe.Observation o=HeadphonesProbe.read(socket.getInputStream(),socket.getOutputStream());
                Status status=!o.modelMatched?Status.MODEL_UNCONFIRMED:
                        o.battery!=null && o.noise!=null?Status.OBSERVED:Status.PARTIAL;
                s.finish(status,o);
            } catch (SecurityException e) { s.finish(Status.PERMISSION_REQUIRED,null); }
            catch (IOException | RuntimeException e) { s.finish(selection==null?Status.UNAVAILABLE:Status.WRITE_UNCONFIRMED,null); }
            finally {
                deadline.cancel(false);
                close(s.detach());
                ACTIVE.compareAndSet(s,null);
            }
        });
        return s;
    }
    public static final class Session {
        private volatile Result result=new Result(Status.READING,null,null);
        private BluetoothSocket socket;
        public Result result() { return result; }
        public void cancel() { stop(Status.CANCELLED); }
        private synchronized boolean bind(BluetoothSocket value) {
            if (result.status!=Status.READING) return false;
            socket=value; return true;
        }
        private synchronized BluetoothSocket detach() { BluetoothSocket v=socket; socket=null; return v; }
        private synchronized void finish(Status status, HeadphonesProbe.Observation o) {
            if (result.status==Status.READING) result=new Result(status,o==null?null:o.battery,o==null?null:o.noise);
        }
        private void stop(Status status) {
            BluetoothSocket value;
            synchronized(this) {
                if (result.status!=Status.READING) return;
                finish(status,null);
                // Retain socket ownership for worker's final close and lease release.
                value=socket;
            }
            if(value!=null) DEADLINE.execute(() -> close(value));
        }
    }
    private static void close(BluetoothSocket socket) {
        if(socket!=null) try { socket.close(); } catch(IOException | RuntimeException ignored) {}
    }
}
