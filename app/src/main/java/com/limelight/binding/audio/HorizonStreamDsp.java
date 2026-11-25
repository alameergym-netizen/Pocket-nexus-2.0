package com.limelight.binding.audio;

/**
 * Stereo PCM16 streaming EQ, attenuation-only peaking filters, independent channel state.
 * Coefficients follow the standard Audio EQ Cookbook peaking-EQ equations:
 * https://www.w3.org/TR/audio-eq-cookbook/
 * No Android effects, root, device-specific tuning, or per-block allocation.
 */
public final class HorizonStreamDsp {
    public static final String KEY_PROFILE = "horizon_stream_dsp";
    private final Biquad[] left, right;
    private final double leftGain, rightGain;
    private static final int[] CUSTOM_FREQUENCIES = {120, 480, 2_200, 6_500};
    private static final double HEADROOM = 0.7943282347; // -2 dB
    private HorizonStreamDsp(Biquad[] left, Biquad[] right, double leftGain, double rightGain) {
        this.left = left;
        this.right = right;
        this.leftGain = leftGain;
        this.rightGain = rightGain;
    }

    static HorizonStreamDsp createCustom(int sampleRate, int channels, int[] bands, int preamp, int balance) {
        if (channels != 2 || sampleRate < 16_000 || sampleRate > 192_000
                || bands == null || bands.length != CUSTOM_FREQUENCIES.length) return null;
        int count = 0;
        for (int band : bands) if (band < 0) count++;
        Biquad[] left = new Biquad[count];
        Biquad[] right = new Biquad[count];
        int slot = 0;
        for (int i = 0; i < bands.length; i++) {
            if (bands[i] >= 0) continue;
            int reduction = Math.max(-9, bands[i]);
            left[slot] = new Biquad(sampleRate, CUSTOM_FREQUENCIES[i], 0.9, reduction);
            right[slot] = new Biquad(sampleRate, CUSTOM_FREQUENCIES[i], 0.9, reduction);
            slot++;
        }
        int trim = Math.max(-12, Math.min(preamp, 0));
        int pan = Math.max(-6, Math.min(balance, 6));
        return new HorizonStreamDsp(left, right, level(trim - Math.max(0, pan)), level(trim + Math.min(0, pan)));
    }

    private static double level(int db) { return HEADROOM * Math.pow(10, db / 20.0); }

    public static HorizonStreamDsp create(String mode, int sampleRate, int channels) {
        if (channels != 2 || sampleRate < 16000 || sampleRate > 192000 || mode == null) { return null; }
        double[][] bands;
        switch (mode) {
            case "bass_restraint": bands=new double[][]{{120,0.8,-5}}; break;
            case "dialogue": bands=new double[][]{{180,0.8,-5},{6500,0.8,-3}}; break;
            case "soft_treble": bands=new double[][]{{6000,0.8,-5}}; break;
            default: return null;
        }
        Biquad[] l=new Biquad[bands.length],r=new Biquad[bands.length];
        for (int i=0;i<bands.length;i++) {
            double[] b=bands[i];
            l[i]=new Biquad(sampleRate,b[0],b[1],b[2]);
            r[i]=new Biquad(sampleRate,b[0],b[1],b[2]);
        }
        return new HorizonStreamDsp(l, r, HEADROOM, HEADROOM);
    }

    /** Input remains unchanged; false means caller must bypass. Single audio callback thread. */
    public boolean process(short[] input, short[] output) {
        if (input==null || output==null || input==output || input.length==0
                || (input.length&1)!=0 || output.length<input.length) { return false; }
        for (int i=0;i<input.length;i+=2) {
            double l=input[i]*leftGain, r=input[i+1]*rightGain;
            for (Biquad b:left) { l=b.step(l); }
            for (Biquad b:right) { r=b.step(r); }
            output[i]=pcm(l);
            output[i+1]=pcm(r);
        }
        return true;
    }

    public void reset() {
        for (Biquad b:left) { b.reset(); }
        for (Biquad b:right) { b.reset(); }
    }

    private static short pcm(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) { return 0; }
        return (short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(value)));
    }

    private static final class Biquad {
        final double b0,b1,b2,a1,a2;
        double z1,z2;
        Biquad(int rate,double frequency,double q,double db) {
            double w=2*Math.PI*frequency/rate;
            double a=Math.pow(10,db/40);
            double alpha=Math.sin(w)/(2*q);
            double norm=1+alpha/a;
            b0=(1+alpha*a)/norm;
            b1=-2*Math.cos(w)/norm;
            b2=(1-alpha*a)/norm;
            a1=b1;
            a2=(1-alpha/a)/norm;
        }
        double step(double x) {
            double y=b0*x+z1;
            z1=b1*x-a1*y+z2;
            z2=b2*x-a2*y;
            return y;
        }
        void reset() { z1=0;z2=0; }
    }
}
