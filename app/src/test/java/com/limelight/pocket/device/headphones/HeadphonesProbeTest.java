package com.limelight.pocket.device.headphones;

import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public class HeadphonesProbeTest {
    private static byte[] packet(int... payload) {
        byte[] data=new byte[payload.length+6];
        data[0]=0x5a; data[1]=(byte)((payload.length+1)>>>8); data[2]=(byte)(payload.length+1);
        for(int i=0;i<payload.length;i++) data[i+4]=(byte)payload[i];
        int crc=0;
        for(int i=0;i<data.length-2;i++) {
            crc^=(data[i]&255)<<8;
            for(int b=0;b<8;b++) crc=((crc<<1)^((crc&0x8000)!=0?0x1021:0))&65535;
        }
        data[data.length-2]=(byte)(crc>>>8); data[data.length-1]=(byte)crc;
        return data;
    }
    private static byte[] join(byte[]... items) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] a:items) out.write(a);
        return out.toByteArray();
    }
    @Test public void splitReadUsesOnlyThreeQueries() throws Exception {
        byte[] input=join(packet(1,7,10,8,'X','-','0','0','0','1','6','3'),packet(1,8,2,3,80,75,60),packet(0x2b,0x2a,1,2,3,1));
        ByteArrayOutputStream sent=new ByteArrayOutputStream();
        InputStream split=new ByteArrayInputStream(input) {
            @Override public synchronized int read(byte[] b,int off,int len) { return super.read(b,off,Math.min(1,len)); }
        };
        HeadphonesProbe.Observation o=HeadphonesProbe.read(split,sent);
        assertTrue(o.modelMatched); assertEquals(Integer.valueOf(75),o.battery.right);
        assertEquals(HeadphonesReadback.NoiseMode.ANC,o.noise.mode);
        assertArrayEquals(join(HeadphonesReadback.modelRequest(),HeadphonesReadback.batteryRequest(),HeadphonesReadback.noiseRequest()),sent.toByteArray());
    }
    @Test public void wrongModelStopsBeforeOtherQueries() throws Exception {
        ByteArrayOutputStream sent=new ByteArrayOutputStream();
        HeadphonesProbe.Observation o=HeadphonesProbe.read(new ByteArrayInputStream(packet(1,7,10,1,'X')),sent);
        assertFalse(o.modelMatched); assertNull(o.battery); assertNull(o.noise);
        assertArrayEquals(HeadphonesReadback.modelRequest(),sent.toByteArray());
    }
    @Test(expected=IOException.class) public void truncatedFrameFails() throws Exception {
        HeadphonesProbe.frame(new ByteArrayInputStream(new byte[]{0x5a,0,9,0}));
    }
    @Test(expected=IOException.class) public void oversizedFrameFails() throws Exception {
        HeadphonesProbe.frame(new ByteArrayInputStream(new byte[]{0x5a,0x7f,0x7f}));
    }
    @Test(expected=IOException.class) public void unrelatedRepliesAreBounded() throws Exception {
        byte[][] messages=new byte[8][];
        for(int i=0;i<messages.length;i++) messages[i]=packet(1,8,1,1,50);
        HeadphonesProbe.read(new ByteArrayInputStream(join(messages)),new ByteArrayOutputStream());
    }
}
