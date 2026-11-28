package com.limelight.pocket.device.headphones;

import org.junit.Test;
import static org.junit.Assert.*;

public class HeadphonesReadbackTest {
    private static byte[] hex(String s) {
        String[] parts = s.split(" ");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = (byte)Integer.parseInt(parts[i], 16);
        return out;
    }
    // Independently construct a test frame; test vector CRCs below additionally check against upstream.
    private static byte[] frame(int... payload) {
        byte[] f = new byte[payload.length + 6];
        f[0] = 0x5a; f[1] = (byte)((payload.length+1) >>> 8); f[2] = (byte)(payload.length+1);
        for (int i=0;i<payload.length;i++) f[4+i]=(byte)payload[i];
        int crc=0;
        for(int i=0;i<f.length-2;i++) {
            crc ^= (f[i]&255)<<8;
            for(int bit=0;bit<8;bit++) crc=((crc<<1)^((crc&0x8000)!=0?0x1021:0))&65535;
        }
        f[f.length-2]=(byte)(crc>>>8); f[f.length-1]=(byte)crc;
        return f;
    }
    @Test public void knownRequestAndReply() {
        assertArrayEquals(hex("5A 00 09 00 01 08 01 00 02 00 03 00 FB B9"), HeadphonesReadback.batteryRequest());
        HeadphonesReadback.Battery b = HeadphonesReadback.battery(hex("5A 00 10 00 01 08 01 01 4A 02 03 64 54 4A 03 03 00 00 00 55 36"));
        assertNotNull(b); assertEquals(Integer.valueOf(100),b.left); assertEquals(Integer.valueOf(84),b.right);
        assertEquals(Integer.valueOf(74),b.caseLevel); assertEquals(Boolean.FALSE,b.caseCharging);
    }
    @Test public void unsolicitedBatteryReport() {
        HeadphonesReadback.Battery b=HeadphonesReadback.battery(hex("5A 00 10 00 01 27 01 01 5A 02 03 64 5A 30 03 03 00 00 01 E7 9E"));
        assertNotNull(b); assertEquals(Integer.valueOf(48),b.caseLevel); assertEquals(Boolean.TRUE,b.caseCharging);
    }
    @Test public void missingAndOutOfRangeAreUnknown() {
        HeadphonesReadback.Battery b=HeadphonesReadback.battery(frame(1,8,1,1,50,2,3,255,101,0,3,3,1,1,128));
        assertNotNull(b); assertNull(b.left); assertNull(b.right); assertEquals(Integer.valueOf(0),b.caseLevel);
        assertNull(b.leftCharging); assertNull(b.rightCharging); assertEquals(Boolean.TRUE,b.caseCharging);
        b=HeadphonesReadback.battery(frame(1,8,1,1,40,2,1,40));
        assertNotNull(b); assertEquals(Integer.valueOf(40),b.overall); assertNull(b.left); assertNull(b.caseLevel);
    }
    @Test public void corruptTruncatedDuplicateAndFragmentRejected() {
        byte[] f=frame(1,8,1,1,50); f[6]^=1;
        assertNull(HeadphonesReadback.battery(f));
        assertNull(HeadphonesReadback.battery(new byte[]{0x5a,0,9}));
        assertNull(HeadphonesReadback.battery(frame(1,8,2,3,1,2)));
        assertNull(HeadphonesReadback.battery(frame(1,8,1,1,50,1,1,60)));
        f=frame(1,8,1,1,50); f[3]=1;
        assertNull(HeadphonesReadback.battery(f));
        assertNull(HeadphonesReadback.battery(new byte[4097]));
    }
    @Test public void noiseModeUsesOnlyReportedMode() {
        HeadphonesReadback.Noise n=HeadphonesReadback.noise(hex("5A 00 07 00 2B 2A 01 02 00 00 15 31"));
        assertNotNull(n); assertEquals(HeadphonesReadback.NoiseMode.OFF,n.mode);
        n=HeadphonesReadback.noise(frame(0x2b,0x2a,1,2,3,1));
        assertNotNull(n); assertEquals(HeadphonesReadback.NoiseMode.ANC,n.mode); assertEquals(3,n.rawLevel);
        assertEquals(HeadphonesReadback.NoiseMode.AWARENESS,HeadphonesReadback.noise(frame(0x2b,0x2a,1,2,2,2)).mode);
        assertNull(HeadphonesReadback.noise(frame(0x2b,0x2a,1,2,0,9)));
        assertNull(HeadphonesReadback.noise(frame(0x2b,4,2,1,0)));
        assertNull(HeadphonesReadback.noise(frame(1,8,1,1,50)));
    }
}
