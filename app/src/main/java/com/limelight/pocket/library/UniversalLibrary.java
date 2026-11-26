package com.limelight.pocket.library;

import android.content.Context;

import com.limelight.R;
import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.pocket.RecentGames;
import com.limelight.pocket.apps.AndroidAppsActivity;
import com.limelight.pocket.apps.AndroidLibrary;
import com.limelight.pocket.cloud.CloudService;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.home.HomeStore;
import com.limelight.pocket.home.PlayTime;
import com.limelight.pocket.retro.EsdeConfig;
import com.limelight.pocket.retro.RetroLibrary;
import com.limelight.pocket.retro.RomScanner;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every game you have, from everywhere, as one list: the games of each PC (streamed), the
 * PCs' own libraries you played from here, the Android games, the ROMs, the cloud games you
 * played and the PC games run on the handheld. The same game in several places is one game
 * with several ways to play it (each PC, a ROM, Android...), its time played added up.
 */
public final class UniversalLibrary {

    /** One way to play a game: where, and what starts it. */
    public static final class Way {
        public final HomeItem item;
        /** Where, in words ("Gaming PC", "Sony PlayStation 2", "Android"...). */
        public final String label;
        /** Where, in one short word for the card ("PC", "PS2", "Android"...). */
        public final String badge;
        public final long last;
        public final int platform;
        /** Whether it starts as things are on the handheld (set when the Library is gathered). */
        public HorizonLibraryReadiness.Ready ready = HorizonLibraryReadiness.Ready.READY;
        /** The app it is waiting for, when {@link #ready} is APP_MISSING (an emulator's name...). */
        public String needs;

        Way(HomeItem item, String label, String badge, long last, int platform) {
            this.item = item;
            this.label = label;
            this.badge = badge;
            this.last = last;
            this.platform = platform;
        }
    }

    public static final int PLATFORM_PC = 0;
    public static final int PLATFORM_ANDROID = 1;
    public static final int PLATFORM_RETRO = 2;
    public static final int PLATFORM_CLOUD = 3;

    public static final class Game {
        public final String key;
        public String title;
        public final List<Way> ways = new ArrayList<>();
        public long last;
        public long seconds;

        Game(String key, String title) {
            this.key = key;
            this.title = title;
        }

        /** The way it was played last (else the best there is: a PC before a ROM). */
        public Way best() {
            Way pick = null;
            for (Way w : ways) {
                if (pick == null || w.last > pick.last || (w.last == pick.last && w.platform < pick.platform)) {
                    pick = w;
                }
            }
            return pick;
        }

        /** A title can match multiple distinct stream applications on the same PC. */
        boolean requiresPcChoice() {
            Map<String, Integer> idsByHost = new HashMap<>();
            for (Way way : ways) {
                HomeItem item = way.item;
                if (!HomeItem.PC.equals(item.kind) || item.a.isEmpty() || item.n <= 0) continue;
                Integer recorded = idsByHost.put(item.a.toLowerCase(Locale.ROOT), item.n);
                if (recorded != null && recorded != item.n) return true;
            }
            return false;
        }

        boolean requiresRetroChoice() {
            String first = null;
            for (Way way : ways) {
                HomeItem item = way.item;
                if (!HomeItem.RETRO.equals(item.kind) || item.c.isEmpty()) continue;
                String key = item.key();
                if (first != null && !first.equals(key)) return true;
                first = key;
            }
            return false;
        }

        boolean requiresAlternateProviderChoice() {
            Way preferred = best();
            if (preferred == null) return false;
            for (Way way : ways) {
                if (way.last == preferred.last && !way.item.kind.equals(preferred.item.kind)) return true;
            }
            return false;
        }

        boolean requiresLaunchChoice() {
            return requiresPcChoice() || requiresRetroChoice() || requiresAlternateProviderChoice();
        }

        /** Whether it can start as things are (the best of its ways). */
        public HorizonLibraryReadiness.Ready ready() {
            List<HorizonLibraryReadiness.Ready> r = new ArrayList<>(ways.size());
            for (Way w : ways) r.add(w.ready);
            return HorizonLibraryReadiness.summary(r);
        }

        /** The one app it is waiting for, or null. */
        public String missingApp() {
            List<HorizonLibraryReadiness.Ready> r = new ArrayList<>(ways.size());
            List<String> n = new ArrayList<>(ways.size());
            for (Way w : ways) {
                r.add(w.ready);
                n.add(w.needs);
            }
            return HorizonLibraryReadiness.missingApp(r, n);
        }

        public boolean on(int platform) {
            for (Way w : ways) {
                if (w.platform == platform) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Apps of a PC that aren't games. */
    private static final String[] NOT_GAMES = {"desktop", "steam big picture", "big picture", "steam", "playnite",
            "playnite fullscreen", "virtual desktop", "remote desktop", "launchbox", "big box", "pocket nexus desktop"};

    private UniversalLibrary() {
    }

    /** Everything, merged (blocking: never on the main thread). */
    public static List<Game> gather(Context context) {
        Context app = context.getApplicationContext();
        Map<String, Game> games = new LinkedHashMap<>();
        Map<String, Way> seenWays = new HashMap<>();
        Map<String, Game> wayOwner = new HashMap<>();

        // The PCs, and each one's games
        List<ComputerDetails> pcs = new ArrayList<>();
        ComputerDatabaseManager db = null;
        try {
            db = new ComputerDatabaseManager(app);
            pcs = db.getAllComputers();
        } catch (RuntimeException ignored) {
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (RuntimeException ignored) {
                }
            }
        }
        Map<String, String> pcNames = new HashMap<>();
        for (ComputerDetails pc : pcs) {
            if (pc.uuid == null) {
                continue;
            }
            pcNames.put(pc.uuid, pc.name);
            for (RecentGames.Game g : RecentGames.forPc(app, pc.uuid, 600)) {
                String name = g.app.getAppName();
                if (notAGame(name)) {
                    continue;
                }
                add(games, seenWays, wayOwner, new HomeItem(HomeItem.PC, name, pc.uuid, "", "", g.app.getAppId()),
                        pc.name != null ? pc.name : "PC", "PC", g.lastPlayed, PLATFORM_PC);
            }
        }

        // The Android games (and what was picked for the Android section, music and video apps aside)
        Set<String> picked = AndroidLibrary.packages(app);
        Set<String> androidGames = new java.util.HashSet<>();
        for (AndroidLibrary.InstalledApp a : AndroidLibrary.installed(app)) {
            int id = AndroidLibrary.idFor(a.packageName);
            // A game by its own say, or one picked for the Android section that isn't a music or
            // video app, nor one the handheld came with (Camera...) unless it was given a cover
            boolean game = a.type == AndroidAppsActivity.TYPE_GAME
                    || (picked.contains(a.packageName) && a.type == AndroidAppsActivity.TYPE_APP && !a.media
                    && (!a.system || com.limelight.pocket.PocketArt.has(app, AndroidLibrary.UUID, id,
                    com.limelight.pocket.PocketArt.COVER)));
            if (!game) {
                continue;
            }
            androidGames.add(a.packageName);
            long used = com.limelight.pocket.PocketPrefs.prefs(app).getLong("pocket_apps_used_" + a.packageName, 0);
            String name = com.limelight.pocket.PocketPrefs.customName(app, AndroidLibrary.UUID, id, a.label);
            add(games, seenWays, wayOwner, new HomeItem(HomeItem.ANDROID, name, a.packageName, "", "", 0),
                    app.getString(R.string.pocket_ul_android), "Android", used, PLATFORM_ANDROID);
        }

        // Use the last complete index from each readable, remembered source. Keep the configured
        // source on the individual game so a later mount loss cannot redirect a launch.
        List<String> romRoots = RetroLibrary.rememberedRoots(app);
        Map<String, String> readableRoots = new HashMap<>();
        Map<String, String> cachedRoots = new HashMap<>();
        Set<String> absoluteRoms = new java.util.HashSet<>();
        EsdeConfig cfg = romRoots.isEmpty() ? null : RetroLibrary.config(app);
        for (String path : romRoots) {
            File root = new File(path);
            String canonical;
            try {
                if (!root.isDirectory() || !root.canRead()) continue;
                canonical = root.getCanonicalPath();
            } catch (java.io.IOException | SecurityException e) {
                continue;
            }
            readableRoots.put(path, canonical);
            if (cachedRoots.containsKey(canonical)) continue;
            RetroLibrary.Library lib = RetroLibrary.cached(app, root);
            if (lib == null) continue;
            cachedRoots.put(canonical, path);
            for (List<RomScanner.Rom> roms : lib.games.values()) {
                for (RomScanner.Rom r : roms) {
                    EsdeConfig.SystemDef s = cfg.system(r.system);
                    if (s != null && s.absolute && !absoluteRoms.add(r.key())) continue;
                    String where = s != null && s.fullName != null ? s.fullName : r.system.toUpperCase(Locale.ROOT);
                    add(games, seenWays, wayOwner, new HomeItem(HomeItem.RETRO, RomScanner.searchName(r.name),
                                    r.system, r.relPath, path, 0),
                            where, r.system.toUpperCase(Locale.ROOT), 0, PLATFORM_RETRO);
                }
            }
        }

        // Everything played from Pocket Nexus: when (and the cloud games, the PCs' libraries,
        // GameNative / GameHub games and shortcuts that are only known from there)
        try {
            for (HomeStore.Entry e : HomeStore.load(app)) {
                HomeItem i = e.item;
                switch (i.kind) {
                    case HomeItem.PC:
                        add(games, seenWays, wayOwner, i, e.pcName.isEmpty() ? "PC" : e.pcName, "PC", e.lastUsed, PLATFORM_PC);
                        break;
                    case HomeItem.PC_LIBRARY_GAME:
                        add(games, seenWays, wayOwner, i, i.c.isEmpty() ? "PC" : i.c, "PC", e.lastUsed, PLATFORM_PC);
                        break;
                    case HomeItem.CLOUD: {
                        if (i.b.isEmpty()) {
                            break; // the service itself, not a game
                        }
                        CloudService s = CloudService.byId(i.a);
                        add(games, seenWays, wayOwner, i, s != null ? app.getString(s.name) : i.a,
                                app.getString(R.string.pocket_ul_badge_cloud), e.lastUsed, PLATFORM_CLOUD);
                        break;
                    }
                    case HomeItem.NATIVE:
                        add(games, seenWays, wayOwner, i, app.getString(R.string.pocket_ul_on_handheld), "PC", e.lastUsed, PLATFORM_ANDROID);
                        break;
                    case HomeItem.ANDROID:
                        if (androidGames.contains(i.a)) {
                            add(games, seenWays, wayOwner, i, app.getString(R.string.pocket_ul_android), "Android", e.lastUsed,
                                    PLATFORM_ANDROID);
                        }
                        break;
                    case HomeItem.RETRO: {
                        String root = i.c.isEmpty() && romRoots.size() == 1 ? romRoots.get(0) : i.c;
                        String canonical = readableRoots.get(root);
                        if (canonical == null) break;
                        root = cachedRoots.getOrDefault(canonical, root);
                        HomeItem bound = i.c.equals(root) ? i : new HomeItem(i.kind, i.name, i.a, i.b, root, i.n);
                        String system = i.a.toUpperCase(Locale.ROOT);
                        add(games, seenWays, wayOwner, bound, system, system, e.lastUsed, PLATFORM_RETRO);
                        break;
                    }
                    default:
                        break;
                }
            }
        } catch (RuntimeException ignored) {
            // Whatever was gathered so far is shown
        }

        List<Game> out = new ArrayList<>(games.values());
        assess(app, out);
        for (Game g : out) {
            long t = 0;
            for (Way w : g.ways) {
                t += PlayTime.seconds(app, w.item);
                if (romRoots.size() == 1 && HomeItem.RETRO.equals(w.item.kind)) {
                    HomeItem rom = w.item;
                    t += PlayTime.seconds(app, new HomeItem(rom.kind, rom.name, rom.a, rom.b, "", rom.n));
                }
            }
            g.seconds = t;
        }
        return out;
    }

    /**
     * Checks each way the way its launch would (S03): ROMs against their system's emulator app,
     * GameNative / GameHub Lite games against that app. Blocking (package lookups).
     */
    private static void assess(Context app, List<Game> games) {
        EsdeConfig cfg = null;
        // An emulator installed or removed since the last look counts now
        com.limelight.pocket.retro.RetroLauncher.forget();
        for (Game g : games) {
            for (Way w : g.ways) {
                HomeItem i = w.item;
                try {
                    switch (i.kind) {
                        case HomeItem.PC:
                        case HomeItem.PC_LIBRARY_GAME:
                        case HomeItem.CLOUD:
                            w.ready = HorizonLibraryReadiness.Ready.STREAMS;
                            break;
                        case HomeItem.NATIVE: {
                            if (cfg == null) cfg = RetroLibrary.config(app);
                            boolean lite = com.limelight.pocket.home.HomeLauncher.GAMEHUB_LITE.equals(i.a);
                            if (com.limelight.pocket.retro.RetroLauncher.component(app, cfg,
                                    lite ? "GAMEHUB-LITE" : "GAMENATIVE") == null) {
                                w.ready = HorizonLibraryReadiness.Ready.APP_MISSING;
                                w.needs = lite ? "GameHub Lite" : "GameNative";
                            }
                            break;
                        }
                        case HomeItem.RETRO: {
                            if (cfg == null) cfg = RetroLibrary.config(app);
                            EsdeConfig.SystemDef system = cfg.system(i.a);
                            if (system == null) {
                                w.ready = HorizonLibraryReadiness.Ready.NO_EMULATOR;
                                break;
                            }
                            RomScanner.Rom rom = new RomScanner.Rom(system.name, i.b, new File(i.c, i.b), false);
                            EsdeConfig.Command cmd = RetroLibrary.gameCommand(app, null, system, rom);
                            if (cmd == null || cmd.isPlaceholder()) {
                                w.ready = HorizonLibraryReadiness.Ready.NO_EMULATOR;
                            }
                            else if (!com.limelight.pocket.retro.RetroLauncher.installed(app, cfg, cmd)) {
                                w.ready = HorizonLibraryReadiness.Ready.APP_MISSING;
                                // The app to install: RetroArch for a core ("Beetle PSX"), else the emulator
                                w.needs = cmd.isRetroArch() ? "RetroArch" : cmd.shortLabel();
                            }
                            break;
                        }
                        default:
                            break;
                    }
                } catch (RuntimeException unknown) {
                    // Unknown stays as it was (never flagged on a guess)
                    w.ready = HorizonLibraryReadiness.Ready.READY;
                }
            }
        }
    }

    private static void add(Map<String, Game> games, Map<String, Way> seenWays, Map<String, Game> wayOwner, HomeItem item, String label,
                            String badge, long last, int platform) {
        Way known = seenWays.get(item.key());
        if (known != null) {
            // This very way is known already (maybe under another name, e.g. a pin renamed on
            // Home): only a newer "last played" for the game it belongs to
            Game owner = wayOwner.get(item.key());
            if (owner != null && last > known.last) {
                owner.ways.remove(known);
                Way w = new Way(known.item, known.label, known.badge, last, known.platform);
                owner.ways.add(w);
                seenWays.put(item.key(), w);
                owner.last = Math.max(owner.last, last);
            }
            return;
        }
        String key = GameShelf.key(item.name);
        if (key.isEmpty()) {
            return;
        }
        Game g = games.get(key);
        if (g == null) {
            g = new Game(key, GameShelf.clean(item.name));
            games.put(key, g);
        }
        else if (platform == PLATFORM_PC || platform == PLATFORM_ANDROID) {
            // A PC's or a store's name reads better than a ROM's file name
            g.title = GameShelf.clean(item.name);
        }
        Way w = new Way(item, label, badge, last, platform);
        g.ways.add(w);
        seenWays.put(item.key(), w);
        wayOwner.put(item.key(), g);
        g.last = Math.max(g.last, last);
    }

    private static boolean notAGame(String name) {
        if (name == null) {
            return true;
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (String s : NOT_GAMES) {
            if (n.equals(s)) {
                return true;
            }
        }
        return false;
    }
}
