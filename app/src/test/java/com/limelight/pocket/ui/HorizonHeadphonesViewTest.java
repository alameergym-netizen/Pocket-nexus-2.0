package com.limelight.pocket.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.limelight.pocket.ui.HorizonHeadphonesView.Device;
import com.limelight.pocket.ui.HorizonHeadphonesView.Output;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HorizonHeadphonesViewTest {
    private static final int SPEAKER = 2;

    @Test public void onePairOfEarbudsIsOneEntryWithItsUses() {
        List<Device> d = HorizonHeadphonesView.devices(Arrays.asList(
                new Output(HorizonHeadphonesView.TYPE_BLUETOOTH_A2DP, "HUAWEI FreeBuds 7i", "AA:BB:CC:00:11:22",
                        new int[]{44100, 48000}, new int[]{2}),
                new Output(HorizonHeadphonesView.TYPE_BLUETOOTH_SCO, "HUAWEI FreeBuds 7i", "aa:bb:cc:00:11:22",
                        new int[]{8000, 16000}, new int[]{1}),
                new Output(SPEAKER, "Retroid Pocket 6", "", new int[]{48000}, new int[]{2})), null);
        assertEquals("the speaker isn't headphones, and A2DP + SCO are one device", 1, d.size());
        Device buds = d.get(0);
        assertEquals("HUAWEI FreeBuds 7i", buds.name);
        assertTrue(buds.media);
        assertTrue(buds.calls);
        assertFalse(buds.le);
        assertEquals("the call link's mono doesn't describe media", "Stereo · up to 48 kHz",
                HorizonHeadphonesView.quality(buds));
        assertNull("before Android 13 nobody claims where media plays", buds.playingMedia);
    }

    @Test public void mediaIsPlayingOnlyWhereAndroidSaysSo() {
        List<Output> outs = Arrays.asList(
                new Output(HorizonHeadphonesView.TYPE_BLUETOOTH_A2DP, "Buds A", "11:11", null, null),
                new Output(HorizonHeadphonesView.TYPE_BLE_HEADSET, "Buds B", "22:22", null, null));
        List<Device> d = HorizonHeadphonesView.devices(outs, Collections.singletonList("22:22"));
        assertEquals(Boolean.FALSE, d.get(0).playingMedia);
        assertEquals(Boolean.TRUE, d.get(1).playingMedia);
        assertTrue(d.get(1).le);
        assertTrue("LE Audio headsets carry calls too", d.get(1).calls);
        assertNull("nothing reported: no quality line", HorizonHeadphonesView.quality(d.get(0)));
    }

    @Test public void devicesWithoutAddressesGroupByNameAndNamelessOnesStayApart() {
        List<Device> d = HorizonHeadphonesView.devices(Arrays.asList(
                new Output(HorizonHeadphonesView.TYPE_BLUETOOTH_A2DP, "Pods", "", null, new int[]{2}),
                new Output(HorizonHeadphonesView.TYPE_BLUETOOTH_SCO, "Pods", null, null, new int[]{1}),
                new Output(HorizonHeadphonesView.TYPE_BLUETOOTH_A2DP, "", "", new int[]{44100}, null)), null);
        assertEquals(2, d.size());
        assertNull("no invented name", d.get(1).name);
        assertEquals("Stereo", HorizonHeadphonesView.quality(d.get(0)));
        assertEquals("up to 44.1 kHz", HorizonHeadphonesView.quality(d.get(1)));
        assertTrue(HorizonHeadphonesView.devices(null, null).isEmpty());
    }
}
