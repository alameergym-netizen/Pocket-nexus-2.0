package com.limelight.pocket.ui.headphones;

/**
 * What the Headphones Center shows, as plain data: no Bluetooth here. The native transport
 * (device/headphones, Core lane) reads the earbuds; an adapter fills a {@link State} from its
 * result and the view draws it. Every "active" mark comes from a readback the earbuds sent: when
 * nothing has been read, nothing is marked active and the controls say why they wait.
 */
public final class HeadphonesCenterModel {
    private HeadphonesCenterModel() {
    }

    /** The three primary noise modes. ANC uses the level chosen on the earbuds. */
    public enum Mode { AWARENESS, OFF, ANC }

    /** The secondary ANC levels. */
    public enum Level { ADAPTIVE, LIGHT, BALANCED, DEEP }

    /** Where the connection is, in the terms the user needs. */
    public enum Phase {
        /** Looking through paired devices. */
        DETECTING,
        /** Android 11 or older: the earbuds can't be read here. */
        UNSUPPORTED,
        /** Nearby devices permission is missing. */
        PERMISSION,
        /** Bluetooth is turned off. */
        BLUETOOTH_OFF,
        /** No paired earbuds found. */
        NOT_PAIRED,
        /** Several paired devices, none recognised for sure: the user picks. */
        CHOOSE,
        /** Reading the earbuds now. */
        READING,
        /** Last read succeeded: values are live. */
        LIVE,
        /** A command is being sent; values are from before it. */
        APPLYING,
        /** The earbuds answered but aren't a model this build can control. */
        UNVERIFIED,
        /** The earbuds didn't answer (out of the case, off, or busy with another app). */
        UNREACHABLE,
        /** The screen lost focus; reading resumes on return. */
        PAUSED
    }

    /** One earbud or the case: a level 0..100 (null: not reported) and whether it charges. */
    public static final class Cell {
        public final Integer level;
        public final boolean charging;

        public Cell(Integer level, boolean charging) {
            this.level = level == null ? null : Math.max(0, Math.min(100, level));
            this.charging = charging;
        }

        public static final Cell UNKNOWN = new Cell(null, false);
    }

    /** A complete, immutable picture of the Center. */
    public static final class State {
        public final Phase phase;
        /** The recognised device's name, or null while none is selected. */
        public final String device;
        public final Cell left, right, box;
        /** The mode the earbuds reported (null: not read). */
        public final Mode mode;
        /** The ANC level the earbuds reported (null: not read, or not in ANC). */
        public final Level level;
        /** A mode or level being applied, or null. */
        public final Object pending;
        /** When the last good read finished (elapsed ms), 0 if never. */
        public final long readAt;

        public State(Phase phase, String device, Cell left, Cell right, Cell box, Mode mode, Level level,
                     Object pending, long readAt) {
            this.phase = phase == null ? Phase.DETECTING : phase;
            this.device = device;
            this.left = left == null ? Cell.UNKNOWN : left;
            this.right = right == null ? Cell.UNKNOWN : right;
            this.box = box == null ? Cell.UNKNOWN : box;
            this.mode = mode;
            this.level = mode == Mode.ANC ? level : null;
            this.pending = pending instanceof Mode || pending instanceof Level ? pending : null;
            this.readAt = readAt;
        }

        public static State detecting() {
            return new State(Phase.DETECTING, null, null, null, null, null, null, null, 0);
        }

        /** True when the earbuds' own report backs the mode marks (live, or mid-command). */
        public boolean verified() {
            return (phase == Phase.LIVE || phase == Phase.APPLYING) && mode != null;
        }

        /** Commands can be sent only from a verified state with nothing in flight. */
        public boolean controllable() {
            return phase == Phase.LIVE && mode != null && pending == null;
        }

        public boolean active(Mode m) {
            return verified() && mode == m;
        }

        public boolean active(Level l) {
            return verified() && mode == Mode.ANC && level == l;
        }
    }

    /**
     * The readback's raw ANC level as a {@link Level}: 0 light, 1 adaptive, 2 balanced, 3 deep
     * (the FreeBuds 7i codes); anything else is no level.
     */
    public static Level levelOf(int raw) {
        switch (raw) {
            case 0: return Level.LIGHT;
            case 1: return Level.ADAPTIVE;
            case 2: return Level.BALANCED;
            case 3: return Level.DEEP;
            default: return null;
        }
    }

    /** The transport's mode name (ANC, AWARENESS, OFF) as a {@link Mode}, or null. */
    public static Mode modeOf(String name) {
        if (name == null) return null;
        switch (name) {
            case "ANC": return Mode.ANC;
            case "AWARENESS": return Mode.AWARENESS;
            case "OFF": return Mode.OFF;
            default: return null;
        }
    }

    /** How a battery level is judged: low under 20 %, critical at 10 % or under. */
    public enum Charge { UNKNOWN, CRITICAL, LOW, OK }

    public static Charge charge(Cell c) {
        if (c == null || c.level == null) return Charge.UNKNOWN;
        if (c.level <= 10) return Charge.CRITICAL;
        if (c.level < 20) return Charge.LOW;
        return Charge.OK;
    }

    /** Seconds since the last good read, or -1 if there has been none. */
    public static long ageSeconds(State s, long now) {
        if (s == null || s.readAt <= 0 || now < s.readAt) return -1;
        return (now - s.readAt) / 1000;
    }
}
