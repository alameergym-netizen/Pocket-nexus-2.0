package com.limelight.binding.audio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.os.Build;
import java.lang.ref.WeakReference;

/** Observations and optional output selection for the current PC stream's own AudioTrack. */
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
        public final int preferredType;
        public final String preferredName;
        public final HorizonAudioStats.Snapshot delivery;
        public final double queuedAudioMs;
        private final Session session;

        private Snapshot(Session session, int type, String name, int sampleRate, int channels, int bufferFrames,
                 int underruns, int performanceMode, AudioDeviceInfo preferred, HorizonAudioStats.Snapshot delivery,
                 double queuedAudioMs) {
            this.type=type;this.name=name;this.sampleRate=sampleRate;this.channels=channels;
            this.bufferFrames=bufferFrames;this.underruns=underruns;this.performanceMode=performanceMode;
            this.delivery = delivery;
            this.queuedAudioMs = queuedAudioMs;
            this.session = session;
            this.preferredType = preferred == null ? -1 : preferred.getType();
            this.preferredName = deviceName(preferred);
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
            AudioDeviceInfo preferred = null;
            if(Build.VERSION.SDK_INT>=23) {
                AudioDeviceInfo device=t.getRoutedDevice();
                if(device!=null) {
                    type=device.getType();
                    name=deviceName(device);
                }
                preferred = t.getPreferredDevice();
                frames=t.getBufferSizeInFrames();
            }
            if(Build.VERSION.SDK_INT>=24) { underruns=t.getUnderrunCount(); }
            if(Build.VERSION.SDK_INT>=26) {
                int reported=t.getPerformanceMode();
                if(reported==AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) { mode=1; }
                else if(reported==AudioTrack.PERFORMANCE_MODE_POWER_SAVING) { mode=2; }
                else if(reported==AudioTrack.PERFORMANCE_MODE_NONE) { mode=0; }
            }
            int sampleRate = t.getSampleRate();
            int channels = t.getChannelCount();
            AudioTimestamp playback = playbackPosition(t);
            long nowNs = System.nanoTime();
            HorizonAudioStats.Snapshot delivery = session.delivery.snapshot();
            double queuedAudioMs = playback == null ? -1 : delivery.queuedAudioMs(
                    channels, sampleRate, playback.framePosition, playback.nanoTime, nowNs);
            Snapshot result = new Snapshot(session, type, name, sampleRate, channels, frames, underruns, mode,
                    preferred, delivery, queuedAudioMs);
            return current()==session && t.getPlayState()==AudioTrack.PLAYSTATE_PLAYING ? result : null;
        } catch(RuntimeException e) { return null; }
    }

    private static String deviceName(AudioDeviceInfo device) {
        CharSequence name = device == null ? null : device.getProductName();
        return name == null ? "" : name.toString();
    }

    /** Android can accept this preference and still choose a different route. */
    public static boolean requestOutput(Context context, Snapshot owner, AudioDeviceInfo selected) {
        if (Build.VERSION.SDK_INT < 23 || owner == null) return false;
        try {
            AudioDeviceInfo current = null;
            if (selected != null) {
                AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (manager == null) return false;
                for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    if (device.getId() == selected.getId() && device.getType() == selected.getType()
                            && deviceName(device).equals(deviceName(selected))) {
                        current = device;
                        break;
                    }
                }
                if (current == null) return false;
            }
            synchronized (HorizonAudioRoute.class) {
                if (active != owner.session) return false;
                AudioTrack track = active.track.get();
                return track != null && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING
                        && track.setPreferredDevice(current);
            }
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static AudioTimestamp playbackPosition(AudioTrack track) {
        try {
            AudioTimestamp timestamp = new AudioTimestamp();
            return track.getTimestamp(timestamp) ? timestamp : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Buffer capacity in milliseconds, not measured latency or fill level. */
    public static double bufferMs(int frames,int sampleRate) {
        return frames<0 || sampleRate<=0 ? -1 : frames*1000.0/sampleRate;
    }
}
