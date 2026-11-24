package com.limelight.pocket.streaming;

import android.content.SharedPreferences;

import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSetup;

/** Connection-specific starting points, not measured link capacity or hardware tuning. */
public final class HorizonStreamProfiles {
    private HorizonStreamProfiles() {}

    public enum Profile {
        REMOTE_ECO("Remote Eco", "1280x720", 60, 10000, 30000),
        REMOTE_BALANCED("Remote Balanced", "1920x1080", 60, 15000, 30000),
        REMOTE_120("Remote 120 Hz", "1920x1080", 120, 20000, 30000),
        LOCAL_BALANCED("Local Balanced", "1920x1080", 60, 25000, 40000),
        LOCAL_ULTRA("Local Ultra", "1920x1080", 120, 50000, 60000),
        USB_WIRED("USB Wired", "1920x1080", 120, 25000, 60000);

        public final String label, resolution;
        public final int fps, wifiKbps, usbKbps;
        Profile(String label, String resolution, int fps, int wifiKbps, int usbKbps) {
            this.label = label;
            this.resolution = resolution;
            this.fps = fps;
            this.wifiKbps = wifiKbps;
            this.usbKbps = usbKbps;
        }

        public String description() {
            return label + " · " + ("1280x720".equals(resolution) ? "720p" : "1080p")
                    + " / " + fps + " FPS · Wi-Fi " + wifiKbps / 1000
                    + " / USB " + usbKbps / 1000 + " Mbps";
        }

        /** Only these four settings change. Codec, controls and frame generation stay untouched. */
        public void apply(SharedPreferences prefs) {
            prefs.edit().putString("list_resolution", resolution)
                    .putString("list_fps", Integer.toString(fps))
                    .putInt("seekbar_bitrate_kbps", wifiKbps)
                    .putInt(PocketPrefs.KEY_USB_BITRATE, usbKbps)
                    .putString(PocketPrefs.KEY_PRESET, PocketSetup.PRESET_CUSTOM).apply();
        }
    }

    public static int match(String resolution, String fps, int wifiKbps, int usbKbps) {
        Profile[] profiles = Profile.values();
        for (int i = 0; i < profiles.length; i++) {
            Profile p = profiles[i];
            if (p.resolution.equals(resolution) && Integer.toString(p.fps).equals(fps)
                    && p.wifiKbps == wifiKbps && p.usbKbps == usbKbps) {
                return i;
            }
        }
        return -1;
    }

    public static int current(SharedPreferences prefs) {
        return match(prefs.getString("list_resolution", "1920x1080"),
                prefs.getString("list_fps", "60"),
                prefs.getInt("seekbar_bitrate_kbps", 30000),
                prefs.getInt(PocketPrefs.KEY_USB_BITRATE, PocketPrefs.DEFAULT_USB_BITRATE_KBPS));
    }
}
