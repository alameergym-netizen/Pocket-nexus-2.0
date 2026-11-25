package com.limelight.pocket.clients;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The clients, hosts and sources in the project registry (docs/CLIENTS_AND_SOURCES.md), with
 * exactly how Pocket Nexus relates to each. It is a description only: nothing here launches,
 * installs or signs in. Package names are ones this app already recognises elsewhere, or that
 * were checked against the publisher's own listing; projects whose identity is not settled carry
 * none. Plain Java, no Android types.
 */
public final class HorizonClientRegistry {
    private HorizonClientRegistry() {}

    public enum Group { PC_STREAMING, CLOUD, CONSOLE, LOCAL_PC, EMULATION, MEDIA, PC_HOST, SOURCES }

    public enum Mode {
        /** Part of Pocket Nexus. */
        BUILT_IN,
        /** Pocket Nexus hands off to the user's installed app. */
        INSTALLED_APP,
        /** The provider's official site, in a browser. */
        OFFICIAL_WEB,
        /** Software on the user's PC that Pocket Nexus works with. */
        PC_HOST,
        /** Evaluated for the roadmap; not available in Pocket Nexus. */
        RESEARCH,
        /** The project's canonical identity is not verified yet. */
        UNRESOLVED
    }

    /** What Pocket Nexus can know about a session with this client. */
    public enum Session {
        /** Pocket Nexus runs and observes the stream itself. */
        OBSERVED,
        /** The service manages sign-in and play; Pocket Nexus only knows it opened it. */
        PROVIDER_MANAGED,
        /** Pocket Nexus opens the app and cannot see whether a game is running. */
        OPENED_ONLY,
        /** Playback is visible through Android's media controls when access is allowed. */
        MEDIA_CONTROLS,
        /** No session through Pocket Nexus. */
        NONE
    }

    /** What the user can do with it on this handheld right now. */
    public enum Availability { BUILT_IN, INSTALLED, NOT_INSTALLED, WEB, ON_PC, RESEARCH_ONLY, UNVERIFIED }

    public static final class Client {
        public final String id;
        public final String name;
        public final Group group;
        public final Mode mode;
        public final Session session;
        /** Pocket Nexus can bring this session back itself (Quick Resume). */
        public final boolean resume;
        /** Official site or source repository; null when unknown. */
        public final String link;
        /** Exact package names, or "family.*" for a package and its variants. */
        private final List<String> packages;

        Client(String id, String name, Group group, Mode mode, Session session, boolean resume, String link,
               String... packages) {
            this.id = id;
            this.name = name;
            this.group = group;
            this.mode = mode;
            this.session = session;
            this.resume = resume;
            this.link = link;
            this.packages = Collections.unmodifiableList(Arrays.asList(packages));
        }

        public List<String> packages() {
            return packages;
        }

        /** True when an installed package is this client (exactly, or within its family). */
        public boolean matches(String installed) {
            if (installed == null) {
                return false;
            }
            for (String p : packages) {
                if (p.endsWith(".*")) {
                    String base = p.substring(0, p.length() - 2);
                    if (installed.equals(base) || installed.startsWith(base + ".")) {
                        return true;
                    }
                }
                else if (installed.equals(p)) {
                    return true;
                }
            }
            return false;
        }

        /** The first installed package that is this client, or null. */
        public String installedPackage(Iterable<String> installed) {
            if (installed == null) {
                return null;
            }
            for (String p : installed) {
                if (matches(p)) {
                    return p;
                }
            }
            return null;
        }
    }

    public static Availability availability(Client c, boolean installed) {
        switch (c.mode) {
            case BUILT_IN:
                return Availability.BUILT_IN;
            case INSTALLED_APP:
                return installed ? Availability.INSTALLED : Availability.NOT_INSTALLED;
            case OFFICIAL_WEB:
                return Availability.WEB;
            case PC_HOST:
                return Availability.ON_PC;
            case RESEARCH:
                return Availability.RESEARCH_ONLY;
            default:
                return Availability.UNVERIFIED;
        }
    }

    private static final String GH = "https://github.com/";

    public static final List<Client> ALL = Collections.unmodifiableList(Arrays.asList(
            // PC streaming
            new Client("nexus-pc-stream", "Pocket Nexus PC streaming", Group.PC_STREAMING, Mode.BUILT_IN,
                    Session.OBSERVED, true, GH + "moonlight-stream/moonlight-android"),
            new Client("starwire", "Starwire", Group.PC_STREAMING, Mode.RESEARCH, Session.NONE, false,
                    GH + "SicarioGaming/Starwire"),
            new Client("moonlight-x", "Moonlight X", Group.PC_STREAMING, Mode.RESEARCH, Session.NONE, false,
                    GH + "MoreOrLessSoftware/moonlight-android"),
            new Client("kast", "KAST", Group.PC_STREAMING, Mode.RESEARCH, Session.NONE, false,
                    GH + "MikalaiKryvusha/KAST"),
            new Client("aurora", "Aurora Android", Group.PC_STREAMING, Mode.RESEARCH, Session.NONE, false,
                    GH + "Koloses/aurora-android"),
            new Client("vibemis", "Vibemis (Android)", Group.PC_STREAMING, Mode.UNRESOLVED, Session.NONE, false,
                    GH + "ineedmypills/Vibemis"),
            new Client("ariane", "Ariane", Group.PC_STREAMING, Mode.UNRESOLVED, Session.NONE, false, null),
            // Cloud gaming
            new Client("xcloud-player", "Xbox Cloud Gaming player", Group.CLOUD, Mode.BUILT_IN,
                    Session.PROVIDER_MANAGED, false, "https://www.xbox.com/play"),
            new Client("xbox-app", "Xbox app", Group.CLOUD, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    "https://www.xbox.com/play", "com.gamepass", "com.microsoft.xboxone.smartglass"),
            new Client("better-xcloud", "Better xCloud", Group.CLOUD, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    GH + "redphx/better-xcloud-android", "com.redphx.betterxc"),
            new Client("xstreaming", "XStreaming", Group.CLOUD, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    GH + "Geocld/XStreaming", "com.xstreaming"),
            new Client("gfn-player", "GeForce NOW player", Group.CLOUD, Mode.BUILT_IN, Session.PROVIDER_MANAGED,
                    false, "https://play.geforcenow.com"),
            new Client("gfn-app", "GeForce NOW app", Group.CLOUD, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    "https://www.nvidia.com/en-us/geforce-now/", "com.nvidia.geforcenow"),
            new Client("boosteroid-player", "Boosteroid player", Group.CLOUD, Mode.BUILT_IN,
                    Session.PROVIDER_MANAGED, false, "https://cloud.boosteroid.com"),
            new Client("boosteroid-app", "Boosteroid app", Group.CLOUD, Mode.INSTALLED_APP, Session.OPENED_ONLY,
                    false, "https://boosteroid.com/", "com.boosteroid.streaming"),
            new Client("greenlight", "Greenlight", Group.CLOUD, Mode.RESEARCH, Session.NONE, false,
                    GH + "unknownskl/greenlight"),
            // Your consoles
            new Client("xbox-remote", "Xbox Remote Play", Group.CONSOLE, Mode.OFFICIAL_WEB, Session.PROVIDER_MANAGED,
                    false, "https://www.xbox.com/en-US/consoles/remote-play"),
            new Client("ps-remote", "PS Remote Play", Group.CONSOLE, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    "https://www.playstation.com/en-us/support/games/playstation-remote-play-on-mobile-devices/",
                    "com.playstation.remoteplay"),
            new Client("chiaki-ng", "Chiaki-ng", Group.CONSOLE, Mode.RESEARCH, Session.NONE, false,
                    GH + "streetpea/chiaki-ng"),
            // PC games on the handheld
            new Client("gamenative", "GameNative", Group.LOCAL_PC, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    GH + "utkarshdalal/GameNative", "app.gamenative.*"),
            new Client("gamehub", "GameHub", Group.LOCAL_PC, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    "https://gamehub.xiaoji.com", "com.xiaoji.egggame"),
            new Client("winlator", "Winlator", Group.LOCAL_PC, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    GH + "brunodev85/winlator", "com.winlator.*"),
            // Emulation
            new Client("retroarch", "RetroArch", Group.EMULATION, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    GH + "libretro/RetroArch", "com.retroarch.*"),
            new Client("es-de", "ES-DE", Group.EMULATION, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    "https://es-de.org/", "org.es_de.*"),
            // Media
            new Client("youtube", "YouTube", Group.MEDIA, Mode.INSTALLED_APP, Session.MEDIA_CONTROLS, false,
                    "https://www.youtube.com/", "com.google.android.youtube"),
            new Client("youtube-music", "YouTube Music", Group.MEDIA, Mode.INSTALLED_APP, Session.MEDIA_CONTROLS,
                    false, "https://music.youtube.com/", "com.google.android.apps.youtube.music"),
            new Client("spotify", "Spotify", Group.MEDIA, Mode.INSTALLED_APP, Session.MEDIA_CONTROLS, false,
                    "https://developer.spotify.com/", "com.spotify.music"),
            new Client("discord", "Discord", Group.MEDIA, Mode.INSTALLED_APP, Session.OPENED_ONLY, false,
                    "https://discord.com/developers/docs/intro", "com.discord"),
            // On your PC
            new Client("vibepollo", "Vibepollo", Group.PC_HOST, Mode.PC_HOST, Session.NONE, false,
                    GH + "Nonary/Vibepollo"),
            new Client("apollo", "Apollo", Group.PC_HOST, Mode.PC_HOST, Session.NONE, false,
                    GH + "ClassicOldSong/Apollo"),
            new Client("sunshine", "Sunshine", Group.PC_HOST, Mode.PC_HOST, Session.NONE, false,
                    GH + "LizardByte/Sunshine"),
            new Client("playnite", "Playnite", Group.PC_HOST, Mode.PC_HOST, Session.NONE, false,
                    GH + "JosefNemec/Playnite"),
            new Client("ludusavi", "Ludusavi", Group.PC_HOST, Mode.PC_HOST, Session.NONE, false,
                    GH + "mtkennerly/ludusavi"),
            // Library and update sources
            new Client("romm", "RomM catalog", Group.SOURCES, Mode.BUILT_IN, Session.NONE, false,
                    GH + "rommapp/romm"),
            new Client("obtainium", "Obtainium catalog import", Group.SOURCES, Mode.BUILT_IN, Session.NONE, false,
                    GH + "ImranR98/Obtainium"),
            new Client("argosy", "Argosy Launcher", Group.SOURCES, Mode.RESEARCH, Session.NONE, false,
                    GH + "rommapp/argosy-launcher"),
            new Client("syncthing", "Syncthing-Fork", Group.SOURCES, Mode.UNRESOLVED, Session.NONE, false,
                    GH + "researchxxl/syncthing-android")
    ));

    public static List<Client> inGroup(Group group) {
        List<Client> out = new ArrayList<>();
        for (Client c : ALL) {
            if (c.group == group) {
                out.add(c);
            }
        }
        return out;
    }

    public static Client byId(String id) {
        for (Client c : ALL) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }
}
