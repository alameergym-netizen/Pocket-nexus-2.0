package com.limelight.pocket;
import com.limelight.binding.audio.HorizonStreamDsp;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class HorizonStreamDspTest {
    private short[] tone(int frequency, int n) {
        short[] input=new short[n*2];
        for(int i=0;i<n;i++) { input[i*2]=(short)(12000*Math.sin(2*Math.PI*frequency*i/48000.0)); }
        return input;
    }
    private double ratio(String mode,int frequency) {
        short[] in=tone(frequency,48000), out=new short[in.length];
        assertTrue(HorizonStreamDsp.create(mode,48000,2).process(in,out));
        double a=0,b=0;
        for(int i=24000;i<in.length;i+=2){a+=(double)in[i]*in[i];b+=(double)out[i]*out[i];}
        return Math.sqrt(b/a);
    }
    @Test public void bypassUnsupportedFormats() {
        assertNull(HorizonStreamDsp.create("off",48000,2));
        assertNull(HorizonStreamDsp.create("unknown",48000,2));
        assertNull(HorizonStreamDsp.create("dialogue",48000,6));
        assertNull(HorizonStreamDsp.create("dialogue",8000,2));
    }
    @Test public void bassAndTrebleCutsAreFrequencySelective() {
        assertTrue(ratio("bass_restraint",120)<0.50);
        assertTrue(ratio("bass_restraint",1000)>0.70);
        assertTrue(ratio("soft_treble",6000)<0.50);
        assertTrue(ratio("soft_treble",500)>0.70);
    }
    @Test public void channelsStayIndependentAndInputUnchanged() {
        short[] input=tone(120,1000), copy=input.clone(), out=new short[input.length];
        assertTrue(HorizonStreamDsp.create("dialogue",48000,2).process(input,out));
        assertArrayEquals(copy,input);
        for(int i=1;i<out.length;i+=2){assertEquals(0,out[i]);}
    }
    @Test public void splitBlocksMatchWholeBlock() {
        short[] input=tone(120,1000), whole=new short[input.length];
        HorizonStreamDsp.create("bass_restraint",48000,2).process(input,whole);
        HorizonStreamDsp split=HorizonStreamDsp.create("bass_restraint",48000,2);
        short[] first=new short[1000],second=new short[1000];
        split.process(Arrays.copyOfRange(input,0,1000),first);
        split.process(Arrays.copyOfRange(input,1000,2000),second);
        assertArrayEquals(Arrays.copyOfRange(whole,0,1000),first);
        assertArrayEquals(Arrays.copyOfRange(whole,1000,2000),second);
    }
    @Test public void invalidBuffersBypassAndResetIsRepeatable() {
        HorizonStreamDsp eq=HorizonStreamDsp.create("dialogue",48000,2);
        assertFalse(eq.process(new short[3],new short[3]));
        assertFalse(eq.process(new short[4],new short[2]));
        short[] in=tone(1000,1000), a=new short[in.length],b=new short[in.length];
        eq.process(in,a);eq.reset();eq.process(in,b);
        assertArrayEquals(a,b);
    }
}
