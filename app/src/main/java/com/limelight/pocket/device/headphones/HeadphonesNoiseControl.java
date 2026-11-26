/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Model-specific command vectors documented by HuaweiPods contributors
 * at e2cf07eda48575c718d0568c0d762c029835bbea, HuaweiAncPackets.kt and
 * HuaweiAncLevelProfile.kt. ACK/readback semantics documented by LibreBuds
 * c7b0833e73e15c427719ed2b684060a98f67cb75, command/Anc.kt.
 * See docs/HEADPHONES_PROTOCOL.md.
 */
package com.limelight.pocket.device.headphones;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;

/** Experimental 7i-only transaction; no automatic write or general raw command API. */
public final class HeadphonesNoiseControl {
    private HeadphonesNoiseControl() {}
    public enum Selection {
        OFF(HeadphonesReadback.NoiseMode.OFF,0,"5A 00 07 00 2B 04 01 02 00 00 D2 2D"),
        ADAPTIVE(HeadphonesReadback.NoiseMode.ANC,1,"5A 00 07 00 2B 04 01 02 01 01 F1 3D"),
        LIGHT(HeadphonesReadback.NoiseMode.ANC,0,"5A 00 07 00 2B 04 01 02 01 00 E1 1C"),
        BALANCED(HeadphonesReadback.NoiseMode.ANC,2,"5A 00 07 00 2B 04 01 02 01 02 C1 5E"),
        DEEP(HeadphonesReadback.NoiseMode.ANC,3,"5A 00 07 00 2B 04 01 02 01 03 D1 7F"),
        AWARENESS(HeadphonesReadback.NoiseMode.AWARENESS,255,"5A 00 07 00 2B 04 01 02 02 FF AA BF");
        final HeadphonesReadback.NoiseMode mode;
        final int level;
        private final String vector;
        Selection(HeadphonesReadback.NoiseMode mode,int level,String vector) {
            this.mode=mode; this.level=level; this.vector=vector;
        }
        byte[] packet() {
            String[] parts=vector.split(" ");
            byte[] out=new byte[parts.length];
            for(int i=0;i<parts.length;i++) out[i]=(byte)Integer.parseInt(parts[i],16);
            return out;
        }
        boolean matches(HeadphonesReadback.Noise n) {
            return n!=null && n.mode==mode && (mode!=HeadphonesReadback.NoiseMode.ANC || n.rawLevel==level);
        }
    }
    enum Outcome { MODEL_UNCONFIRMED, READ_REQUIRED, ALREADY_SELECTED, CONFIRMED, UNCONFIRMED }
    static final class Change {
        final Outcome outcome;
        final HeadphonesReadback.Noise observed;
        Change(Outcome outcome,HeadphonesReadback.Noise observed) { this.outcome=outcome; this.observed=observed; }
    }
    /** Fresh identity and state required on the same selected socket. At most one write.
     * An ACK by itself never changes the displayed state. */
    static Change change(InputStream in, OutputStream out, Selection selection, BooleanSupplier active) throws IOException {
        if(selection==null || !active.getAsBoolean()) return new Change(Outcome.READ_REQUIRED,null);
        HeadphonesProbe.send(out,HeadphonesReadback.modelRequest());
        if(!HeadphonesReadback.reported7iModel(HeadphonesProbe.response(in,1,7)))
            return new Change(Outcome.MODEL_UNCONFIRMED,null);
        if(!active.getAsBoolean()) return new Change(Outcome.READ_REQUIRED,null);
        HeadphonesProbe.send(out,HeadphonesReadback.noiseRequest());
        HeadphonesReadback.Noise before=HeadphonesReadback.noise(HeadphonesProbe.response(in,0x2b,0x2a));
        if(before==null || (before.mode==HeadphonesReadback.NoiseMode.ANC && before.rawLevel>3))
            return new Change(Outcome.READ_REQUIRED,null);
        if(selection.matches(before)) return new Change(Outcome.ALREADY_SELECTED,before);
        if(!active.getAsBoolean()) return new Change(Outcome.READ_REQUIRED,null);
        HeadphonesProbe.send(out,selection.packet());
        byte[] ack=HeadphonesProbe.response(in,0x2b,4);
        byte[][] fields=HeadphonesReadback.fields(ack);
        boolean accepted=fields!=null && fields[2]!=null && fields[2].length==1 && fields[2][0]==0;
        if(!active.getAsBoolean()) return new Change(Outcome.UNCONFIRMED,null);
        HeadphonesProbe.send(out,HeadphonesReadback.noiseRequest());
        HeadphonesReadback.Noise after=HeadphonesReadback.noise(HeadphonesProbe.response(in,0x2b,0x2a));
        return new Change(accepted && selection.matches(after)?Outcome.CONFIRMED:Outcome.UNCONFIRMED,after);
    }
}
