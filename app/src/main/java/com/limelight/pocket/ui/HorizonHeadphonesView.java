package com.limelight.pocket.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Headphones view, decided in plain Java (unit tested). Android lists one pair of earbuds as
 * several outputs (A2DP for media, SCO for calls, LE Audio...), so the raw list repeats the same
 * name. This groups them into one entry per device and says what each is used for, whether it is
 * the output media plays on now (only when Android reports it), and a short quality line instead
 * of raw rate and channel lists. Battery and noise control stay "not reported" until a verified
 * vendor adapter supplies them: nothing here is inferred from a name.
 */
public final class HorizonHeadphonesView {
    private HorizonHeadphonesView() {}

    // AudioDeviceInfo type values (stable public API constants)
    public static final int TYPE_BLUETOOTH_SCO = 7;
    public static final int TYPE_BLUETOOTH_A2DP = 8;
    public static final int TYPE_BLE_HEADSET = 26;
    public static final int TYPE_BLE_SPEAKER = 27;
    public static final int TYPE_BLE_BROADCAST = 30;

    /** One Android output as reported. */
    public static final class Output {
        final int type;
        final String name;
        final String address;
        final int[] rates;
        final int[] channels;

        public Output(int type, String name, String address, int[] rates, int[] channels) {
            this.type = type;
            this.name = name == null ? "" : name.trim();
            this.address = address == null ? "" : address.trim();
            this.rates = rates;
            this.channels = channels;
        }
    }

    /** One physical device, grouped. */
    public static final class Device {
        /** Its name, or null when Android gives none. */
        public final String name;
        /** Bluetooth Low Energy Audio (vs classic). */
        public final boolean le;
        public final boolean media;
        public final boolean calls;
        /** TRUE/FALSE when Android reports where media plays; null when it doesn't (before 13). */
        public final Boolean playingMedia;
        /** Highest reported rate in Hz (0: none reported). */
        public final int maxRate;
        /** Highest reported channel count (0: none reported). */
        public final int maxChannels;

        Device(String name, boolean le, boolean media, boolean calls, Boolean playingMedia, int maxRate,
               int maxChannels) {
            this.name = name;
            this.le = le;
            this.media = media;
            this.calls = calls;
            this.playingMedia = playingMedia;
            this.maxRate = maxRate;
            this.maxChannels = maxChannels;
        }
    }

    public static boolean bluetooth(int type) {
        return type == TYPE_BLUETOOTH_A2DP || type == TYPE_BLUETOOTH_SCO || type == TYPE_BLE_HEADSET
                || type == TYPE_BLE_SPEAKER || type == TYPE_BLE_BROADCAST;
    }

    /**
     * Groups the Bluetooth outputs by device (address when Android gives one, else name).
     * {@code mediaAddresses}: where media plays now, as Android reports it (null: not reported).
     */
    public static List<Device> devices(List<Output> outputs, List<String> mediaAddresses) {
        Map<String, List<Output>> groups = new LinkedHashMap<>();
        if (outputs != null) {
            for (Output o : outputs) {
                if (o == null || !bluetooth(o.type)) continue;
                String key = !o.address.isEmpty() ? "a:" + o.address.toUpperCase(java.util.Locale.ROOT)
                        : !o.name.isEmpty() ? "n:" + o.name : "t:" + o.type;
                List<Output> g = groups.get(key);
                if (g == null) {
                    g = new ArrayList<>();
                    groups.put(key, g);
                }
                g.add(o);
            }
        }
        List<Device> out = new ArrayList<>();
        for (List<Output> g : groups.values()) {
            String name = null;
            boolean le = false, media = false, calls = false;
            int rate = 0, ch = 0;
            Boolean playing = mediaAddresses == null ? null : Boolean.FALSE;
            for (Output o : g) {
                if (name == null && !o.name.isEmpty()) name = o.name;
                if (o.type == TYPE_BLE_HEADSET || o.type == TYPE_BLE_SPEAKER || o.type == TYPE_BLE_BROADCAST) le = true;
                if (o.type == TYPE_BLUETOOTH_SCO) calls = true;
                else media = true;
                if (o.type == TYPE_BLE_HEADSET) calls = true;
                rate = Math.max(rate, max(o.rates));
                // Call-only channels (mono SCO) don't describe the media sound
                if (o.type != TYPE_BLUETOOTH_SCO) ch = Math.max(ch, max(o.channels));
                if (mediaAddresses != null && !o.address.isEmpty()) {
                    for (String a : mediaAddresses) {
                        if (o.address.equalsIgnoreCase(a)) playing = Boolean.TRUE;
                    }
                }
            }
            out.add(new Device(name, le, media, calls, playing, rate, ch));
        }
        return out;
    }

    /** "Stereo · up to 48 kHz", "Mono", "up to 16 kHz", or null when nothing was reported. */
    public static String quality(Device d) {
        String channels = d.maxChannels >= 2 ? "Stereo" : d.maxChannels == 1 ? "Mono" : null;
        String rate = d.maxRate <= 0 ? null : d.maxRate % 1000 == 0 ? "up to " + (d.maxRate / 1000) + " kHz"
                : String.format(java.util.Locale.ROOT, "up to %.1f kHz", d.maxRate / 1000.0);
        if (channels != null && rate != null) return channels + " · " + rate;
        return channels != null ? channels : rate;
    }

    private static int max(int[] values) {
        int m = 0;
        if (values != null) for (int v : values) m = Math.max(m, v);
        return m;
    }
}
