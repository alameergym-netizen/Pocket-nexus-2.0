/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Original Java implementation of the single-frame wire format documented by
 * LibreBuds contributors (GPL-3.0-or-later), pinned c7b0833e73e15c427719ed2b684060a98f67cb75.
 * See docs/HEADPHONES_PROTOCOL.md for provenance and scope.
 */
package com.limelight.pocket.device.headphones;

import java.util.Arrays;

/** Read-only, complete single-frame codec. Transport and model verification belong to the session. */
public final class HeadphonesReadback {
    private HeadphonesReadback() {}
    private static final int MAX_FRAME = 4096;
    public enum NoiseMode { OFF, ANC, AWARENESS }
    public static final class Battery {
        public final Integer overall, left, right, caseLevel;
        public final Boolean leftCharging, rightCharging, caseCharging;
        private Battery(Integer overall, Integer left, Integer right, Integer caseLevel,
                        Boolean leftCharging, Boolean rightCharging, Boolean caseCharging) {
            this.overall = overall; this.left = left; this.right = right; this.caseLevel = caseLevel;
            this.leftCharging = leftCharging; this.rightCharging = rightCharging; this.caseCharging = caseCharging;
        }
    }
    public static final class Noise {
        public final NoiseMode mode;
        public final int rawLevel;
        private Noise(NoiseMode mode, int rawLevel) { this.mode = mode; this.rawLevel = rawLevel; }
    }
    /** Fixed queries only; no arbitrary write or vendor-setting packet generator. */
    public static byte[] batteryRequest() { return request(1, 8, 1, 2, 3); }
    public static byte[] noiseRequest() { return request(0x2b, 0x2a, 1); }
    private static byte[] request(int service, int command, int... types) {
        byte[] result = new byte[8 + 2 * types.length];
        int len = result.length - 5;
        result[0] = 0x5a; result[1] = (byte)(len >>> 8); result[2] = (byte)len;
        result[4] = (byte)service; result[5] = (byte)command;
        for (int i = 0; i < types.length; i++) result[6 + 2*i] = (byte)types[i];
        int crc = crc16(result, result.length - 2);
        result[result.length-2] = (byte)(crc >>> 8); result[result.length-1] = (byte)crc;
        return result;
    }
    public static Battery battery(byte[] frame) {
        if (!valid(frame) || u(frame[4]) != 1 || (u(frame[5]) != 8 && u(frame[5]) != 0x27)) return null;
        byte[][] values = fields(frame);
        if (values == null) return null;
        byte[] overall = values[1], levels = values[2], flags = values[3];
        Integer all = overall != null && overall.length == 1 ? percent(overall[0]) : null;
        Integer l = level(levels, 0), r = level(levels, 1), c = level(levels, 2);
        return new Battery(all, l, r, c, charging(flags, 0, l), charging(flags, 1, r), charging(flags, 2, c));
    }
    public static Noise noise(byte[] frame) {
        if (!valid(frame) || u(frame[4]) != 0x2b || u(frame[5]) != 0x2a) return null;
        byte[][] values = fields(frame);
        if (values == null || values[1] == null || values[1].length != 2) return null;
        int mode = u(values[1][1]);
        if (mode > 2) return null;
        return new Noise(NoiseMode.values()[mode], u(values[1][0]));
    }
    private static Integer level(byte[] values, int i) {
        return values != null && values.length >= 3 ? percent(values[i]) : null;
    }
    private static Integer percent(byte b) { int v = u(b); return v <= 100 ? v : null; }
    private static Boolean charging(byte[] flags, int i, Integer level) {
        if (level == null || flags == null || flags.length < 3) return null;
        int flag = u(flags[i]);
        // Unknown flag encodings must not look like a confirmed charging state.
        return flag == 0 ? Boolean.FALSE : flag == 1 || flag == 0x80 ? Boolean.TRUE : null;
    }
    private static boolean valid(byte[] f) {
        return f != null && f.length >= 8 && f.length <= MAX_FRAME && u(f[0]) == 0x5a
                && ((u(f[1]) << 8) | u(f[2])) == f.length - 5 && f[3] == 0
                && crc16(f, f.length) == 0;
    }
    private static byte[][] fields(byte[] f) {
        byte[][] result = new byte[256][];
        int end = f.length - 2;
        for (int i = 6; i < end;) {
            if (end - i < 2) return null;
            int type = u(f[i++]), length = u(f[i++]);
            if (length > end - i || result[type] != null) return null;
            result[type] = Arrays.copyOfRange(f, i, i + length);
            i += length;
        }
        return result;
    }
    private static int u(byte b) { return b & 0xff; }
    private static int crc16(byte[] bytes, int end) {
        int crc = 0;
        for (int i = 0; i < end; i++) {
            crc ^= u(bytes[i]) << 8;
            for (int bit = 0; bit < 8; bit++)
                crc = ((crc << 1) ^ ((crc & 0x8000) != 0 ? 0x1021 : 0)) & 0xffff;
        }
        return crc;
    }
}
