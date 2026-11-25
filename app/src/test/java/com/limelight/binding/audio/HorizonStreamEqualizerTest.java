package com.limelight.binding.audio;

import org.junit.Test;

import static org.junit.Assert.*;

public class HorizonStreamEqualizerTest {
    private static short[] tone(int frequency) {
        short[] input = new short[96_000];
        for (int frame = 0; frame < input.length / 2; frame++) {
            short sample = (short) (30_000 * Math.sin(2 * Math.PI * frequency * frame / 48_000.0));
            input[frame * 2] = sample;
            input[frame * 2 + 1] = sample;
        }
        return input;
    }

    private static double rms(short[] pcm, int channel) {
        double energy = 0;
        for (int i = pcm.length / 2 + channel; i < pcm.length; i += 2) energy += (double) pcm[i] * pcm[i];
        return Math.sqrt(energy / (pcm.length / 4));
    }

    @Test public void fourCutBandsReduceTheirOwnFrequencies() {
        int[] frequencies = {120, 480, 2_200, 6_500};
        for (int i = 0; i < frequencies.length; i++) {
            int[] bands = new int[4];
            bands[i] = -6;
            HorizonStreamDsp eq = HorizonStreamDsp.createCustom(48_000, 2, bands, 0, 0);
            short[] input = tone(frequencies[i]);
            short[] output = new short[input.length];
            assertTrue(eq.process(input, output));
            assertEquals(0.398, rms(output, 0) / rms(input, 0), 0.01);
            eq.reset();
            input = tone(frequencies[i] < 2_000 ? 7_000 : 150);
            assertTrue(eq.process(input, output));
            assertTrue(rms(output, 0) / rms(input, 0) > 0.72);
        }
    }

    @Test public void preampAndBalanceOnlyAttenuateUsingTheExistingHeadroom() {
        short[] input = tone(2_200), output = new short[input.length];
        HorizonStreamDsp eq = HorizonStreamDsp.createCustom(48_000, 2, new int[]{8, 0, 0, 0}, -6, 6);
        assertTrue(eq.process(input, output));
        assertEquals(0.200, rms(output, 0) / rms(input, 0), 0.01);
        assertEquals(0.398, rms(output, 1) / rms(input, 1), 0.01);
    }

    @Test public void malformedBandsAndUnsupportedFormatsBypass() {
        assertNull(HorizonStreamDsp.createCustom(48_000, 2, new int[3], 0, 0));
        assertNull(HorizonStreamDsp.createCustom(48_000, 6, new int[4], 0, 0));
        assertNull(HorizonStreamDsp.createCustom(8_000, 2, new int[4], 0, 0));
    }
}
