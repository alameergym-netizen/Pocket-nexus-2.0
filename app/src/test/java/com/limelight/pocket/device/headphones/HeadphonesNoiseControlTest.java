package com.limelight.pocket.device.headphones;

import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.limelight.pocket.device.headphones.HeadphonesNoiseControl.Selection.*;

public class HeadphonesNoiseControlTest {
    private static byte[] frame(int... payload) {
        byte[] f=new byte[payload.length+6];
        f[0]=0x5a; f[1]=(byte)((payload.length+1)>>>8); f[2]=(byte)(payload.length+1);
        for(int i=0;i<payload.length;i++) f[i+4]=(byte)payload[i];
        int crc=0;
        for(int i=0;i<f.length-2;i++) {
            crc^=(f[i]&255)<<8;
            for(int b=0;b<8;b++) crc=((crc<<1)^((crc&0x8000)!=0?0x1021:0))&65535;
        }
        f[f.length-2]=(byte)(crc>>>8); f[f.length-1]=(byte)crc;
        return f;
    }
    private static byte[] join(byte[]... items) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] f:items) out.write(f);
        return out.toByteArray();
    }
    private static byte[] model() { return frame(1,7,10,8,'X','-','0','0','0','1','6','3'); }
    private static byte[] noise(int level,int mode) { return frame(0x2b,0x2a,1,2,level,mode); }
    private static byte[] ack(int status) { return frame(0x2b,4,2,1,status); }
    @Test public void allFixedVectorsAreValidAndModelSpecific() {
        int[] levels={0,1,0,2,3,255};
        int i=0;
        for(HeadphonesNoiseControl.Selection s:HeadphonesNoiseControl.Selection.values()) {
            byte[] packet=s.packet();
            assertTrue(HeadphonesReadback.valid(packet));
            assertEquals(4,packet[5]&255); assertEquals(levels[i++],packet[9]&255);
            packet[0]=0; assertTrue(HeadphonesReadback.valid(s.packet()));
        }
    }
    @Test public void confirmedOnlyAfterMatchingAckAndReadback() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        HeadphonesNoiseControl.Change c=HeadphonesNoiseControl.change(
                new ByteArrayInputStream(join(model(),noise(0,0),ack(0),noise(3,1))),out,DEEP,()->true);
        assertEquals(HeadphonesNoiseControl.Outcome.CONFIRMED,c.outcome);
        assertEquals(3,c.observed.rawLevel);
        assertArrayEquals(join(HeadphonesReadback.modelRequest(),HeadphonesReadback.noiseRequest(),DEEP.packet(),
                HeadphonesReadback.noiseRequest()),out.toByteArray());
    }
    @Test public void acceptedButDifferentLevelNeverConfirms() throws Exception {
        HeadphonesNoiseControl.Change c=HeadphonesNoiseControl.change(
                new ByteArrayInputStream(join(model(),noise(0,0),ack(0),noise(1,1))),new ByteArrayOutputStream(),DEEP,()->true);
        assertEquals(HeadphonesNoiseControl.Outcome.UNCONFIRMED,c.outcome);
        assertEquals(1,c.observed.rawLevel);
    }
    @Test public void rejectedAckNeverConfirmsEvenIfModeMatches() throws Exception {
        HeadphonesNoiseControl.Change c=HeadphonesNoiseControl.change(
                new ByteArrayInputStream(join(model(),noise(0,0),ack(1),noise(3,1))),new ByteArrayOutputStream(),DEEP,()->true);
        assertEquals(HeadphonesNoiseControl.Outcome.UNCONFIRMED,c.outcome);
    }
    @Test public void wrongModelNeverWrites() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        HeadphonesNoiseControl.Change c=HeadphonesNoiseControl.change(new ByteArrayInputStream(frame(1,7,10,1,'X')),out,DEEP,()->true);
        assertEquals(HeadphonesNoiseControl.Outcome.MODEL_UNCONFIRMED,c.outcome);
        assertArrayEquals(HeadphonesReadback.modelRequest(),out.toByteArray());
    }
    @Test public void noWriteWhenAlreadySelectedOrUnsupportedState() throws Exception {
        for(int level:new int[]{3,255}) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            HeadphonesNoiseControl.Change c=HeadphonesNoiseControl.change(
                new ByteArrayInputStream(join(model(),noise(level,1))),out,DEEP,()->true);
            assertEquals(level==3?HeadphonesNoiseControl.Outcome.ALREADY_SELECTED:HeadphonesNoiseControl.Outcome.READ_REQUIRED,c.outcome);
            assertArrayEquals(join(HeadphonesReadback.modelRequest(),HeadphonesReadback.noiseRequest()),out.toByteArray());
        }
    }
    @Test public void cancelledBeforeWriteNeverSendsChange() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        java.util.concurrent.atomic.AtomicInteger checks=new java.util.concurrent.atomic.AtomicInteger();
        HeadphonesNoiseControl.Change c=HeadphonesNoiseControl.change(
                new ByteArrayInputStream(join(model(),noise(0,0))),out,DEEP,()->checks.incrementAndGet()<3);
        assertEquals(HeadphonesNoiseControl.Outcome.READ_REQUIRED,c.outcome);
        assertArrayEquals(join(HeadphonesReadback.modelRequest(),HeadphonesReadback.noiseRequest()),out.toByteArray());
    }
}
