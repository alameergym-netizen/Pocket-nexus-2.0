package com.limelight.pocket.ui;

import android.app.Application;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.device.HorizonAudio;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAudioManager;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class HeadphonesPanelTest {
    public static class PanelHost extends ConsoleActivity {
        List<StreamMenuView.Item> rows;

        @Override public void showConsolePanel(String title, String subtitle, String stats,
                                               List<StreamMenuView.Item> items) {
            rows = items;
        }

        @Override public void replaceConsolePageItems(List<StreamMenuView.Item> items) {
            rows = items;
        }

        StreamMenuView.Item find(String title) {
            for (StreamMenuView.Item item : rows) {
                if (title.equals(item.title)) return item;
            }
            return null;
        }
    }

    @Test public void refreshPicksUpFreeBudsAndClearsTheirReadingsAfterDisconnect() {
        PanelHost host = Robolectric.buildActivity(PanelHost.class).get();
        ShadowAudioManager audio = shadowOf((AudioManager) host.getSystemService(Context.AUDIO_SERVICE));
        audio.setOutputDevices(Collections.emptyList());
        HorizonAudio.showHeadphones(host);
        String empty = host.getString(R.string.horizon_headphones_empty);
        String refresh = host.getString(R.string.horizon_headphones_refresh);
        assertNotNull(host.find(empty));
        assertNotNull(host.find(host.getString(R.string.horizon_audio_bluetooth_settings)).action);

        AudioDeviceInfo buds = mock(AudioDeviceInfo.class);
        when(buds.getType()).thenReturn(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
        when(buds.getProductName()).thenReturn("HUAWEI FreeBuds 7i");
        when(buds.getSampleRates()).thenReturn(new int[] {48000});
        when(buds.getChannelCounts()).thenReturn(new int[] {2});
        audio.setOutputDevices(Collections.singletonList(buds));
        host.find(refresh).action.run();
        assertNull(host.find(empty));
        assertNotNull(host.find("HUAWEI FreeBuds 7i"));
        assertNotNull(host.find(host.getString(R.string.horizon_buds_name_match)));
        assertEquals("A2DP", host.find(host.getString(R.string.horizon_buds_transport)).value);
        assertEquals("48000", host.find(host.getString(R.string.horizon_buds_rates)).value);

        audio.setOutputDevices(Collections.emptyList());
        host.find(refresh).action.run();
        assertNotNull(host.find(empty));
        assertNull(host.find("HUAWEI FreeBuds 7i"));
        assertNull(host.find(host.getString(R.string.horizon_buds_rates)));
    }
}
