package com.limelight.pocket.device.headphones;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Explicit read-only probe. Transport owner enforces a wall-clock deadline. */
final class HeadphonesProbe {
    private HeadphonesProbe() {}
    static final class Observation {
        final boolean modelMatched;
        final HeadphonesReadback.Battery battery;
        final HeadphonesReadback.Noise noise;
        Observation(boolean matched, HeadphonesReadback.Battery battery, HeadphonesReadback.Noise noise) {
            this.modelMatched = matched; this.battery = battery; this.noise = noise;
        }
    }
    static Observation read(InputStream in, OutputStream out) throws IOException {
        send(out, HeadphonesReadback.modelRequest());
        if (!HeadphonesReadback.reported7iModel(response(in, 1, 7))) return new Observation(false, null, null);
        send(out, HeadphonesReadback.batteryRequest());
        HeadphonesReadback.Battery battery = HeadphonesReadback.battery(response(in, 1, 8));
        send(out, HeadphonesReadback.noiseRequest());
        HeadphonesReadback.Noise noise = HeadphonesReadback.noise(response(in, 0x2b, 0x2a));
        return new Observation(true, battery, noise);
    }
    static void send(OutputStream out, byte[] query) throws IOException { out.write(query); out.flush(); }
    static byte[] response(InputStream in, int service, int command) throws IOException {
        for (int i = 0; i < 8; i++) {
            byte[] f = frame(in);
            if ((f[4]&255) == service && (f[5]&255) == command) return f;
        }
        throw new IOException("Expected reply not observed");
    }
    static byte[] frame(InputStream in) throws IOException {
        int magic = octet(in);
        if (magic != 0x5a) throw new IOException("Unsupported frame");
        int high = octet(in), low = octet(in), size = (high << 8 | low) + 5;
        if (size < 8 || size > 4096) throw new IOException("Invalid frame length");
        byte[] data = new byte[size];
        data[0]=(byte)magic; data[1]=(byte)high; data[2]=(byte)low;
        for (int offset=3; offset<size;) {
            int count = in.read(data, offset, size-offset);
            if (count < 0) throw new EOFException();
            if (count == 0) throw new IOException("Empty read");
            offset += count;
        }
        if (!HeadphonesReadback.valid(data)) throw new IOException("Unsupported or corrupt frame");
        return data;
    }
    private static int octet(InputStream in) throws IOException {
        int b=in.read(); if (b<0) throw new EOFException(); return b;
    }
}
