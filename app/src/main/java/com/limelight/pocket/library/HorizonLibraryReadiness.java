package com.limelight.pocket.library;

import java.util.List;

/**
 * Whether a game in the Library can start right now, from what is actually on the handheld
 * (S03: actual install state, not a decoration). Each way to play is checked the way its launch
 * checks it: an Android game is installed (it was listed from the installed apps), a ROM needs
 * its system's emulator app, a GameNative / GameHub Lite game needs that app, and a PC or cloud
 * game streams (the PC or the service decides when it is started). Plain Java, unit tested.
 */
public final class HorizonLibraryReadiness {
    private HorizonLibraryReadiness() {}

    public enum Ready {
        /** Installed here: it starts on the handheld. */
        READY,
        /** Streamed from a PC or a cloud service: available, nothing to install here. */
        STREAMS,
        /** Its app (an emulator, GameNative...) isn't installed. */
        APP_MISSING,
        /** No emulator exists for its system on Android. */
        NO_EMULATOR
    }

    /** The game as a whole: the best of its ways (one that starts wins over one that can't). */
    public static Ready summary(List<Ready> ways) {
        if (ways == null || ways.isEmpty()) {
            return Ready.READY;
        }
        Ready best = null;
        for (Ready r : ways) {
            Ready v = r == null ? Ready.READY : r;
            if (best == null || v.ordinal() < best.ordinal()) {
                best = v;
            }
        }
        return best;
    }

    /** Whether the card says it can't start as it is. */
    public static boolean flagged(Ready summary) {
        return summary == Ready.APP_MISSING || summary == Ready.NO_EMULATOR;
    }

    /**
     * The one app the game is waiting for, when every way that could start is waiting for the
     * same app (null otherwise, or when the game can start).
     */
    public static String missingApp(List<Ready> ways, List<String> needs) {
        if (summary(ways) != Ready.APP_MISSING || needs == null || needs.size() != ways.size()) {
            return null;
        }
        String one = null;
        for (int i = 0; i < ways.size(); i++) {
            if (ways.get(i) != Ready.APP_MISSING) continue;
            String n = needs.get(i);
            if (n == null || n.trim().isEmpty()) return null;
            if (one != null && !one.equals(n.trim())) return null;
            one = n.trim();
        }
        return one;
    }
}
