package com.limelight.pocket.device;

import android.media.AudioDeviceInfo;
import android.media.session.PlaybackState;
import com.limelight.R;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonAudioTest {
    @Test public void classifiesSpeakerAndBluetooth() {
        assertEquals(R.string.horizon_audio_speaker, HorizonAudio.typeLabel(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertEquals(R.string.horizon_audio_bluetooth, HorizonAudio.typeLabel(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
    }
    @Test public void recognizesUsbAndUnknown() {
        assertEquals(R.string.horizon_audio_usb, HorizonAudio.typeLabel(AudioDeviceInfo.TYPE_USB_HEADSET));
        assertEquals(R.string.horizon_audio_other, HorizonAudio.typeLabel(-1));
    }
    @Test public void mediaToggleRequiresSupportedAction() {
        assertFalse(HorizonAudio.supportsToggle(0));
        assertFalse(HorizonAudio.supportsToggle(PlaybackState.ACTION_SKIP_TO_NEXT));
        assertTrue(HorizonAudio.supportsToggle(PlaybackState.ACTION_PLAY_PAUSE));
        assertTrue(HorizonAudio.supportsToggle(PlaybackState.ACTION_PLAY));
    }
}
