package com.limelight.binding.audio;

import android.app.Application;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class HorizonAudioOutputTest {
    @Test public void staleRoutePanelCannotChangeAReplacementStream() {
        AudioTrack track = playingTrack();
        AudioTrack replacement = playingTrack();
        try {
            HorizonAudioRoute.register(track, new HorizonAudioStats());
            HorizonAudioRoute.Snapshot oldPanel = HorizonAudioRoute.snapshot();
            assertNotNull(oldPanel);
            HorizonAudioRoute.register(replacement, new HorizonAudioStats());
            HorizonAudioRoute.Snapshot current = HorizonAudioRoute.snapshot();
            HorizonAudioRoute.clear(track);
            assertFalse(HorizonAudioRoute.requestOutput(RuntimeEnvironment.getApplication(), oldPanel, null));
            assertTrue(HorizonAudioRoute.requestOutput(RuntimeEnvironment.getApplication(), current, null));
            HorizonAudioRoute.clear(replacement);
            assertFalse(HorizonAudioRoute.requestOutput(RuntimeEnvironment.getApplication(), current, null));
        } finally {
            HorizonAudioRoute.clear(track);
            HorizonAudioRoute.clear(replacement);
            track.release();
            replacement.release();
        }
    }

    private static AudioTrack playingTrack() {
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, 48000,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, 32768, AudioTrack.MODE_STREAM);
        track.play();
        return track;
    }
}
