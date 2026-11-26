package com.limelight.pocket.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nexus Horizon's streaming cards: the host, the session and the network, decided from what was
 * actually observed. A PC is only called on or off from a recent poll, a running game only from
 * what the PC itself reported, and the network only from what Android reports; anything older
 * or unknown says so instead of guessing. Plain Java so every rule is unit tested.
 */
public final class HorizonStreamCards {
    private HorizonStreamCards() {}

    /** A poll older than this no longer says whether a PC is on. */
    public static final long FRESH_MS = 30_000;

    public enum Reach { UNKNOWN, ONLINE, OFFLINE }

    /** How the PC is reached, when the address in use matches one the PC is known by. */
    public enum Route { UNKNOWN, LOCAL, REMOTE, MANUAL }

    public enum Transport { NONE, WIFI, WIRED, CELLULAR, OTHER }

    /** A paired PC, with what the last poll saw (if any). */
    public static final class Host {
        public final String id;
        public final String name;
        public final Reach reach;
        /** When {@link #reach} was observed (elapsed realtime); 0: never. */
        public final long observedAt;
        public final Route route;
        /** The game the PC reported running (0: none reported). */
        public final int runningAppId;
        /** Its name, when the PC's game list knows it; null otherwise. */
        public final String runningName;
        /** The game played last on this PC from the handheld, and when (wall clock; 0: never). */
        public final String lastGame;
        public final long lastPlayedAt;

        public Host(String id, String name, Reach reach, long observedAt, Route route, int runningAppId,
                    String runningName, String lastGame, long lastPlayedAt) {
            this.id = id == null ? "" : id;
            this.name = name == null || name.trim().isEmpty() ? this.id : name.trim();
            this.reach = reach == null ? Reach.UNKNOWN : reach;
            this.observedAt = Math.max(0, observedAt);
            this.route = route == null ? Route.UNKNOWN : route;
            this.runningAppId = Math.max(0, runningAppId);
            this.runningName = blank(runningName) ? null : runningName.trim();
            this.lastGame = blank(lastGame) ? null : lastGame.trim();
            this.lastPlayedAt = Math.max(0, lastPlayedAt);
        }

        /** Its reach now: an old poll is no longer an answer. */
        public Reach reachAt(long now) {
            if (observedAt == 0 || now < observedAt || now - observedAt > FRESH_MS) {
                return Reach.UNKNOWN;
            }
            return reach;
        }

        /** A game it reported running, while that report is fresh and the PC is on. */
        public boolean runningAt(long now) {
            return runningAppId != 0 && reachAt(now) == Reach.ONLINE;
        }
    }

    /** What Android reports about the network in use. Unknown numbers are below zero. */
    public static final class Link {
        public final Transport transport;
        public final boolean vpn;
        /** 24, 50 or 60 (2.4, 5, 6 GHz); 0: unknown. */
        public final int bandGhz10;
        /** 0..4; below zero: unknown. */
        public final int signal;
        /** The radio's link rate (not a measured throughput); below one: unknown. */
        public final int linkMbps;

        public Link(Transport transport, boolean vpn, int bandGhz10, int signal, int linkMbps) {
            this.transport = transport == null ? Transport.NONE : transport;
            this.vpn = vpn;
            this.bandGhz10 = bandGhz10 == 24 || bandGhz10 == 50 || bandGhz10 == 60 ? bandGhz10 : 0;
            this.signal = signal >= 0 && signal <= 4 ? signal : -1;
            this.linkMbps = linkMbps > 0 ? linkMbps : -1;
        }
    }

    /** The PC the host card shows: one running a game, then one on, then the one played last. */
    public static Host featured(List<Host> hosts, long now) {
        if (hosts == null || hosts.isEmpty()) {
            return null;
        }
        Host best = null;
        int bestRank = -1;
        for (Host h : hosts) {
            int rank = h.runningAt(now) ? 3 : h.reachAt(now) == Reach.ONLINE ? 2 : h.lastPlayedAt > 0 ? 1 : 0;
            if (best == null || rank > bestRank || (rank == bestRank && h.lastPlayedAt > best.lastPlayedAt)) {
                best = h;
                bestRank = rank;
            }
        }
        return best;
    }

    public enum SessionKind {
        /** No PC paired: nothing to stream from. */
        NO_HOSTS,
        /** A PC reported a game running (fresh): it can be resumed. */
        RUNNING,
        /** The PCs answered and none is running a game. */
        IDLE,
        /** Not every PC has answered yet: nothing is claimed either way. */
        UNKNOWN
    }

    public static final class Session {
        public final SessionKind kind;
        /** RUNNING: the PC running it. */
        public final Host host;
        /** The game played last anywhere (for IDLE and UNKNOWN); null: none. */
        public final Host last;

        Session(SessionKind kind, Host host, Host last) {
            this.kind = kind;
            this.host = host;
            this.last = last;
        }
    }

    public static Session session(List<Host> hosts, long now) {
        if (hosts == null || hosts.isEmpty()) {
            return new Session(SessionKind.NO_HOSTS, null, null);
        }
        Host last = null;
        boolean allKnown = true;
        for (Host h : hosts) {
            if (h.runningAt(now)) {
                return new Session(SessionKind.RUNNING, h, null);
            }
            if (h.reachAt(now) == Reach.UNKNOWN) {
                allKnown = false;
            }
            if (h.lastGame != null && (last == null || h.lastPlayedAt > last.lastPlayedAt)) {
                last = h;
            }
        }
        return new Session(allKnown ? SessionKind.IDLE : SessionKind.UNKNOWN, null, last);
    }

    /** Which address the PC is being reached on, when it is one of the addresses it is known by. */
    public static Route route(String active, String local, String remote, String manual) {
        if (blank(active)) {
            return Route.UNKNOWN;
        }
        if (active.equalsIgnoreCase(nonNull(manual))) {
            return Route.MANUAL;
        }
        if (active.equalsIgnoreCase(nonNull(local))) {
            return Route.LOCAL;
        }
        if (active.equalsIgnoreCase(nonNull(remote))) {
            return Route.REMOTE;
        }
        return Route.UNKNOWN;
    }

    /** "5 GHz" and the like; null when the band is unknown. */
    public static String band(int bandGhz10) {
        switch (bandGhz10) {
            case 24: return "2.4 GHz";
            case 50: return "5 GHz";
            case 60: return "6 GHz";
            default: return null;
        }
    }

    /** How the cards are placed: side by side when each gets this much width, else stacked. */
    public static final int MIN_CARD_DP = 140;

    public static boolean sideBySide(float availableWidthDp, int cards) {
        return cards > 0 && availableWidthDp >= cards * MIN_CARD_DP + (cards - 1) * 8;
    }

    // ---- What Home last observed, for screens that don't poll the PCs themselves --------------

    private static final Map<String, Host> OBSERVED = new HashMap<>();

    /** Home's poll saw this PC: Library and Nexus Control may show it while it is fresh. */
    public static void observe(Host host) {
        if (host == null || host.id.isEmpty() || host.observedAt == 0) {
            return;
        }
        synchronized (OBSERVED) {
            OBSERVED.put(host.id, host);
        }
    }

    /** The last observation of a PC, or null. */
    public static Host observed(String id) {
        synchronized (OBSERVED) {
            return id == null ? null : OBSERVED.get(id);
        }
    }

    /** The saved hosts with what was observed of each merged in (reach, route and running game). */
    public static List<Host> withObservations(List<Host> saved) {
        if (saved == null || saved.isEmpty()) {
            return Collections.emptyList();
        }
        List<Host> out = new ArrayList<>(saved.size());
        for (Host s : saved) {
            Host o = observed(s.id);
            out.add(o == null ? s : new Host(s.id, s.name, o.reach, o.observedAt, o.route, o.runningAppId,
                    o.runningName, s.lastGame != null ? s.lastGame : o.lastGame,
                    Math.max(s.lastPlayedAt, o.lastPlayedAt)));
        }
        return out;
    }

    static void forgetObservations() {
        synchronized (OBSERVED) {
            OBSERVED.clear();
        }
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nonNull(String s) {
        return s == null ? "\u0000" : s;
    }
}
