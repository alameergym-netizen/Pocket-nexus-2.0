package com.limelight.binding.audio;

import android.media.AudioDeviceInfo;
import android.media.AudioTrack;
import android.os.Build;
import java.lang.ref.WeakReference;

/** Explicitly requested diagnostics for the currently playing PC stream, not device-wide routing. */
public final class HorizonAudioRoute {
    private static Session active;

    private HorizonAudioRoute() { }

    private static final class Session {
        final WeakReference<AudioTrack> track;
        final HorizonAudioStats delivery;

        Session(AudioTrack track, HorizonAudioStats delivery) {
            this.track = new WeakReference<>(track);
            this.delivery = delivery;
        }
    }

    static synchronized void register(AudioTrack track, HorizonAudioStats delivery) {
        if (track != null && delivery != null) active = new Session(track, delivery);
    }

    static synchronized void clear(AudioTrack track) {
        if (track != null && active != null && active.track.get() == track) active = null;
    }

    private static synchronized Session current() {
        return active;
    }

    public static final class Snapshot {
        public final int type, sampleRate, channels, bufferFrames, underruns, performanceMode;
        public final String name;
        public final HorizonAudioStats.Snapshot delivery;
        Snapshot(int type,String name,int sampleRate,int channels,int bufferFrames,int underruns,int performanceMode,
                 HorizonAudioStats.Snapshot delivery) {
            this.type=type;this.name=name;this.sampleRate=sampleRate;this.channels=channels;
            this.bufferFrames=bufferFrames;this.underruns=underruns;this.performanceMode=performanceMode;
            this.delivery = delivery;
        }
    }

    public static Snapshot snapshot() {
        Session session = current();
        AudioTrack t = session != null ? session.track.get() : null;
        if(t==null) { return null; }
        try {
            if(t.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING) { return null; }
            int type=-1, frames=-1, underruns=-1, mode=-1;
            String name="";
            if(Build.VERSION.SDK_INT>=23) {
                AudioDeviceInfo device=t.getRoutedDevice();
                if(device!=null) {
                    type=device.getType();
                    CharSequence label=device.getProductName();
                    if(label!=null) { name=label.toString(); }
                }
                frames=t.getBufferSizeInFrames();
            }
            if(Build.VERSION.SDK_INT>=24) { underruns=t.getUnderrunCount(); }
            if(Build.VERSION.SDK_INT>=26) {
                int reported=t.getPerformanceMode();
                if(reported==AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) { mode=1; }
                else if(reported==AudioTrack.PERFORMANCE_MODE_POWER_SAVING) { mode=2; }
                else if(reported==AudioTrack.PERFORMANCE_MODE_NONE) { mode=0; }
            }
            Snapshot result=new Snapshot(type,name,t.getSampleRate(),t.getChannelCount(),frames,underruns,mode,
                    session.delivery.snapshot());
            return current()==session && t.getPlayState()==AudioTrack.PLAYSTATE_PLAYING ? result : null;
        } catch(RuntimeException e) { return null; }
    }

    /** Buffer capacity in milliseconds, not measured latency or fill level. */
    public static double bufferMs(int frames,int sampleRate) {
        return frames<0 || sampleRate<=0 ? -1 : frames*1000.0/sampleRate;
    }
}
