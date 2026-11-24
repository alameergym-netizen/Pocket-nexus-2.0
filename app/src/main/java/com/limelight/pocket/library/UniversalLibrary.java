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

        // The ROMs
        File root = RetroLibrary.romRoot(app);
        RetroLibrary.Library lib = root != null ? RetroLibrary.cached(app, root) : null;
        if (lib != null) {
            EsdeConfig cfg = RetroLibrary.config(app);
            for (List<RomScanner.Rom> roms : lib.games.values()) {
                for (RomScanner.Rom r : roms) {
                    EsdeConfig.SystemDef s = cfg.system(r.system);
                    String where = s != null && s.fullName != null ? s.fullName : r.system.toUpperCase(Locale.ROOT);
                    add(games, seenWays, wayOwner, new HomeItem(HomeItem.RETRO, RomScanner.searchName(r.name), r.system, r.relPath, "", 0),
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
                    case HomeItem.RETRO:
                        add(games, seenWays, wayOwner, i, i.a.toUpperCase(Locale.ROOT), i.a.toUpperCase(Locale.ROOT), e.lastUsed,
                                PLATFORM_RETRO);
                        break;
                    default:
                        break;
                }
            }
        } catch (RuntimeException ignored) {
            // Whatever was gathered so far is shown
        }

        List<Game> out = new ArrayList<>(games.values());
        for (Game g : out) {
            long t = 0;
            for (Way w : g.ways) {
                t += PlayTime.seconds(app, w.item);
            }
            g.seconds = t;
        }
        return out;
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
