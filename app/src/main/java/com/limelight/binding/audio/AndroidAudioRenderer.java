package com.limelight.binding.audio;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.AudioEffect;
import android.os.Build;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.jni.MoonBridge;

public class AndroidAudioRenderer implements AudioRenderer {

    private final Context context;
    private final boolean enableAudioFx;

    private AudioTrack track;
    private HorizonStreamDsp dsp;
    private short[] dspBuffer;
    private int openedEffectSession = -1;

    public AndroidAudioRenderer(Context context, boolean enableAudioFx) {
        this.context = context;
        this.enableAudioFx = enableAudioFx;
    }

    private AudioTrack createAudioTrack(int channelConfig, int sampleRate, int bufferSize, boolean lowLatency) {
        AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME);
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Use FLAG_LOW_LATENCY on L through N
            if (lowLatency) {
                attributesBuilder.setFlags(AudioAttributes.FLAG_LOW_LATENCY);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioTrack.Builder trackBuilder = new AudioTrack.Builder()
                    .setAudioFormat(format)
                    .setAudioAttributes(attributesBuilder.build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize);

            // Use PERFORMANCE_MODE_LOW_LATENCY on O and later
            if (lowLatency) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
            }

            return trackBuilder.build();
        }
        else {
            return new AudioTrack(attributesBuilder.build(),
                    format,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
        }
    }

    @Override
    public int setup(MoonBridge.AudioConfiguration audioConfiguration, int sampleRate, int samplesPerFrame) {
        dsp = null;
        dspBuffer = null;
        int channelConfig;
        int bytesPerFrame;

        switch (audioConfiguration.channelCount)
        {
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 4:
                channelConfig = AudioFormat.CHANNEL_OUT_QUAD;
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            case 8:
                // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND isn't available until Android 6.0,
                // yet the CHANNEL_OUT_SIDE_LEFT and CHANNEL_OUT_SIDE_RIGHT constants were added
                // in 5.0, so just hardcode the constant so we can work on Lollipop.
                channelConfig = 0x000018fc; // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
                break;
            default:
                LimeLog.severe("Decoder returned unhandled channel count");
                return -1;
        }

        LimeLog.info("Audio channel config: "+String.format("0x%X", channelConfig));

        bytesPerFrame = audioConfiguration.channelCount * samplesPerFrame * 2;

        // We're not supposed to request less than the minimum
        // buffer size for our buffer, but it appears that we can
        // do this on many devices and it lowers audio latency.
        // We'll try the small buffer size first and if it fails,
        // use the recommended larger buffer size.

        for (int i = 0; i < 4; i++) {
            boolean lowLatency;
            int bufferSize;

            // We will try:
            // 1) Small buffer, low latency mode
            // 2) Large buffer, low latency mode
            // 3) Small buffer, standard mode
            // 4) Large buffer, standard mode

            switch (i) {
                case 0:
                case 1:
                    lowLatency = true;
                    break;
                case 2:
                case 3:
                    lowLatency = false;
                    break;
                default:
                    // Unreachable
                    throw new IllegalStateException();
            }

            switch (i) {
                case 0:
                case 2:
                    bufferSize = bytesPerFrame * 2;
                    break;

                case 1:
                case 3:
                    // Try the larger buffer size
                    bufferSize = Math.max(AudioTrack.getMinBufferSize(sampleRate,
                            channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT),
                            bytesPerFrame * 2);

                    // Round to next frame
                    bufferSize = (((bufferSize + (bytesPerFrame - 1)) / bytesPerFrame) * bytesPerFrame);
                    break;
                default:
                    // Unreachable
                    throw new IllegalStateException();
            }

            // Skip low latency options if hardware sample rate doesn't match the content
            if (AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) != sampleRate && lowLatency) {
                continue;
            }

            // Skip low latency options when using audio effects, since low latency mode
            // precludes the use of the audio effect pipeline (as of Android 13).
            if (enableAudioFx && lowLatency) {
                continue;
            }

            try {
                track = createAudioTrack(channelConfig, sampleRate, bufferSize, lowLatency);
                track.play();

                // Successfully created working AudioTrack. We're done here.
                LimeLog.info("Audio track configuration: "+bufferSize+" "+lowLatency);
                break;
            } catch (Exception e) {
                // Try to release the AudioTrack if we got far enough
                e.printStackTrace();
                try {
                    if (track != null) {
                        track.release();
                        track = null;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (track == null) {
            // Couldn't create any audio track for playback
            return -2;
        }

        // Software EQ is opt-in, stereo only and mutually exclusive with external DSP.
        if (!enableAudioFx && samplesPerFrame > 0 && samplesPerFrame <= 8192) {
            String mode = com.limelight.pocket.PocketPrefs.prefs(context)
                    .getString(HorizonStreamDsp.KEY_PROFILE, "off");
            dsp = HorizonStreamDsp.create(mode, sampleRate, audioConfiguration.channelCount);
            if (dsp != null) { dspBuffer = new short[samplesPerFrame * 2]; }
        }
        return 0;
    }

    @Override
    public void playDecodedAudio(short[] audioData) {
        // Only queue up to 40 ms of pending audio data in addition to what AudioTrack is buffering for us.
        if (MoonBridge.getPendingAudioDuration() < 40) {
            // This will block until the write is completed. That can cause a backlog
            // of pending audio data, so we do the above check to be able to bound
            // latency at 40 ms in that situation.
            AudioTrack output = track;
            if (output == null || audioData == null) { return; }
            short[] pcm = audioData;
            HorizonStreamDsp eq = dsp;
            short[] scratch = dspBuffer;
            if (eq != null && scratch != null) {
                if (eq.process(audioData,scratch)) { pcm = scratch; }
                else { eq.reset(); } // Unexpected frame shape: bypass and clear filter history.
            }
            output.write(pcm, 0, audioData.length);
        }
        else {
            LimeLog.info("Too much pending audio data: " + MoonBridge.getPendingAudioDuration() +" ms");
        }
    }

    @Override
    public void start() {
        if (enableAudioFx && track != null && openedEffectSession < 0) {
            int session = track.getAudioSessionId();
            if (session <= 0) { return; }
            try {
                Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
                i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session);
                i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
                i.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_GAME);
                context.sendBroadcast(i);
                openedEffectSession = session;
            } catch (RuntimeException e) {
                LimeLog.info("Audio effect session could not be opened");
            }
        }
    }

    @Override
    public void stop() {
        closeEffectSession();
    }

    private void closeEffectSession() {
        int session = openedEffectSession;
        openedEffectSession = -1;
        if (session <= 0) { return; }
        try {
            Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session);
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            context.sendBroadcast(i);
        } catch (RuntimeException e) {
            LimeLog.info("Audio effect session could not be closed");
        }
    }

    @Override
    public void cleanup() {
        closeEffectSession();
        // Immediately drop pending data, even if setup did not finish.
        AudioTrack closing = track;
        track = null;
        dsp = null;
        dspBuffer = null;
        if (closing != null) {
            try {
                closing.pause();
                closing.flush();
            } catch (RuntimeException ignored) {
            } finally {
                closing.release();
            }
        }
    }
}
