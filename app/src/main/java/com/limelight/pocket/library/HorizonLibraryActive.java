package com.limelight.pocket.library;

import com.limelight.pocket.ui.HorizonStreamCards;

/**
 * S03: the "Running" badge of a Library game. A game is only called running from what a PC
 * actually reported in a fresh poll (the same observation the streaming cards use): the PC is on,
 * the report is recent, and the game it named is this way's game. Nothing is guessed from
 * history, and Android or ROM games are never called running, since nothing here can see them.
 */
public final class HorizonLibraryActive {
    private HorizonLibraryActive() {
    }

    /** Looks up what was last observed of a PC (by its id), or null. */
    public interface Observations {
        HorizonStreamCards.Host of(String pcId);
    }

    /** True if this stream way's game is the one its PC reported running, freshly. */
    public static boolean runs(String pcId, int appId, HorizonStreamCards.Host observed, long now) {
        return pcId != null && !pcId.isEmpty() && appId > 0 && observed != null
                && pcId.equals(observed.id) && observed.runningAt(now) && observed.runningAppId == appId;
    }

    /**
     * The name of the PC running one of these stream ways ({@code pcIds[i]}, {@code appIds[i]}),
     * or null when none is observed running.
     */
    public static String runningOn(String[] pcIds, int[] appIds, Observations seen, long now) {
        if (pcIds == null || appIds == null || seen == null) return null;
        for (int i = 0; i < Math.min(pcIds.length, appIds.length); i++) {
            HorizonStreamCards.Host h = pcIds[i] == null ? null : seen.of(pcIds[i]);
            if (runs(pcIds[i], appIds[i], h, now)) {
                return h.name == null || h.name.trim().isEmpty() ? "PC" : h.name;
            }
        }
        return null;
    }
}
