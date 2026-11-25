package com.limelight.binding.audio;

import android.content.SharedPreferences;

import com.limelight.R;

/** Shared settings for the equalizer editor and the next stereo renderer. */
public final class HorizonStreamEqSettings {
    public static final String CUSTOM = "custom";

    public enum Control {
        BASS("horizon_eq_bass", R.string.horizon_eq_bass, -9, 0),
        LOW_MID("horizon_eq_low_mid", R.string.horizon_eq_low_mid, -9, 0),
        PRESENCE("horizon_eq_presence", R.string.horizon_eq_presence, -9, 0),
        TREBLE("horizon_eq_treble", R.string.horizon_eq_treble, -9, 0),
        PREAMP("horizon_eq_preamp", R.string.horizon_eq_preamp, -12, 0),
        BALANCE("horizon_eq_balance", R.string.horizon_eq_balance, -6, 6);

        private final String key;
        public final int label;
        private final int minimum, maximum;

        Control(String key, int label, int minimum, int maximum) {
            this.key = key;
            this.label = label;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        private int bounded(int value) { return Math.max(minimum, Math.min(maximum, value)); }

        public int read(SharedPreferences prefs) {
            try { return bounded(prefs.getInt(key, 0)); }
            catch (ClassCastException invalidImport) { return 0; }
        }

        public int step(SharedPreferences prefs, int direction) {
            int value = bounded(read(prefs) + Integer.signum(direction));
            prefs.edit().putInt(key, value).apply();
            return value;
        }
    }

    private HorizonStreamEqSettings() {}

    static HorizonStreamDsp create(SharedPreferences prefs, int sampleRate, int channels) {
        String mode = "off";
        try { mode = prefs.getString(HorizonStreamDsp.KEY_PROFILE, "off"); }
        catch (ClassCastException invalidImport) { }
        if (!CUSTOM.equals(mode)) return HorizonStreamDsp.create(mode, sampleRate, channels);
        int[] bands = {Control.BASS.read(prefs), Control.LOW_MID.read(prefs),
                Control.PRESENCE.read(prefs), Control.TREBLE.read(prefs)};
        return HorizonStreamDsp.createCustom(sampleRate, channels, bands,
                Control.PREAMP.read(prefs), Control.BALANCE.read(prefs));
    }

    public static void reset(SharedPreferences prefs) {
        SharedPreferences.Editor options = prefs.edit();
        for (Control control : Control.values()) options.remove(control.key);
        options.apply();
    }
}
