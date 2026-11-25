package com.limelight.pocket.retro;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;

import com.limelight.pocket.PocketArt;
import com.limelight.pocket.PocketPrefs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The retro games: where the ROMs are (ES-DE's ROM folder by default), what's in each system
 * folder, what was played last, the favourites and the emulator picked for a system or a game.
 * ES-DE's own settings, gamelists and scraped pictures are used when ES-DE is on the handheld.
 */
public final class RetroLibrary {
    private RetroLibrary() {}

    /** Where the games' art and settings are kept (like a PC's uuid). */
    public static final String UUID = "retro";

    private static final String KEY_ROOT = "pocket_retro_root";
    private static final String KEY_RECENT = "pocket_retro_recent";
    private static final String KEY_FAVS = "pocket_retro_favs";
    private static final String KEY_SYS_EMU = "pocket_retro_emu_sys_";
    private static final String KEY_GAME_EMU = "pocket_retro_emu_game_";
    private static final String KEY_SHOW_EMPTY = "pocket_retro_show_empty";
    private static final int MAX_RECENT = 40;

    private static EsdeConfig config;

    // ---- ES-DE's files --------------------------------------------------------------------------

    /** ES-DE's systems and emulators (bundled, plus its custom_systems files). Blocking. */
    public static synchronized EsdeConfig config(Context context) {
        if (config != null) {
            return config;
        }
        EsdeConfig c;
        try (InputStream systems = context.getAssets().open("esde/es_systems.xml");
             InputStream rules = context.getAssets().open("esde/es_find_rules.xml")) {
            c = EsdeConfig.parse(systems, rules);
        } catch (IOException e) {
            try {
                c = EsdeConfig.parse(null, null);
            } catch (IOException never) {
                throw new IllegalStateException(never);
            }
        }
        File custom = new File(esdeDir(), "custom_systems");
        File cs = new File(custom, "es_systems.xml");
        File cr = new File(custom, "es_find_rules.xml");
        try (InputStream s = cs.canRead() ? new FileInputStream(cs) : null;
             InputStream r = cr.canRead() ? new FileInputStream(cr) : null) {
            if (s != null || r != null) {
                c.merge(s, r);
            }
        } catch (IOException | RuntimeException ignored) {
            // A broken custom file is left out
        }
        config = c;
        return c;
    }

    /** Reads the custom_systems files again next time. */
    public static synchronized void reloadConfig() {
        config = null;
    }

    /** ES-DE's folder (settings, gamelists, scraped media). */
    public static File esdeDir() {
        return new File(Environment.getExternalStorageDirectory(), "ES-DE");
    }

    private static String esdeSetting(String name) {
        File f = new File(new File(esdeDir(), "settings"), "es_settings.xml");
        if (!f.canRead() || f.length() > 512 * 1024) {
            return null;
        }
        Pattern p = Pattern.compile("<string\\s+name=\"" + Pattern.quote(name) + "\"\\s+value=\"([^\"]*)\"");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.find()) {
                    String v = m.group(1).replace("&amp;", "&").trim();
                    return v.isEmpty() ? null : v;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /** ES-DE's scraped pictures folder. */
    public static File mediaDir() {
        String custom = esdeSetting("MediaDirectory");
        return custom != null ? new File(custom) : new File(esdeDir(), "downloaded_media");
    }

    // ---- Storage access ----------------------------------------------------------------------

    /** True when the app may read the ROM folders (all files access on Android 11 and later). */
    public static boolean hasAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static final int REQUEST_ACCESS = 7311;

    /** Asks for it (the system's own screen on Android 11 and later). */
    public static void requestAccess(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + activity.getPackageName())));
            } catch (RuntimeException e) {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (RuntimeException ignored) {
                }
            }
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_ACCESS);
        }
    }

    // ---- The ROM folder --------------------------------------------------------------------

    /** The ROM folder: the one picked, ES-DE's, or a ROMs folder found on a drive (null: none). */
    public static File romRoot(Context context) {
        String picked = PocketPrefs.prefs(context).getString(KEY_ROOT, null);
        if (picked != null) {
            return new File(picked);
        }
        return detectRoot();
    }

    public static boolean rootWasPicked(Context context) {
        return PocketPrefs.prefs(context).getString(KEY_ROOT, null) != null;
    }

    public static File detectRoot() {
        String esde = esdeSetting("ROMDirectory");
        if (esde != null) {
            File f = new File(esde.replace("%ESPATH%", esdeDir().getPath()));
            if (f.isDirectory()) {
                return f;
            }
        }
        File internal = new File(Environment.getExternalStorageDirectory(), "ROMs");
        if (internal.isDirectory()) {
            return internal;
        }
        File[] drives = new File("/storage").listFiles();
        if (drives != null) {
            for (File d : drives) {
                for (String n : new String[] {"ROMs", "roms", "Roms"}) {
                    File f = new File(d, n);
                    if (f.isDirectory()) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    /** Where ES-DE would put it (to create it). */
    public static File defaultRoot() {
        return new File(Environment.getExternalStorageDirectory(), "ROMs");
    }

    public static void setRoot(Context context, File root) {
        String path = HorizonRomSources.normalize(root.getAbsolutePath());
        if (path == null) { return; }
        android.content.SharedPreferences prefs = PocketPrefs.prefs(context);
        List<String> remembered = HorizonRomSources.decode(prefs.getString(HorizonRomSources.KEY, ""));
        File previous = romRoot(context);
        if (previous != null) { remembered = HorizonRomSources.remember(remembered, previous.getAbsolutePath()); }
        remembered = HorizonRomSources.remember(remembered,path);
        prefs.edit().putString(KEY_ROOT,path).putString(HorizonRomSources.KEY,HorizonRomSources.encode(remembered)).apply();
    }

    public static List<String> rememberedRoots(Context context) {
        List<String> roots = HorizonRomSources.decode(PocketPrefs.prefs(context).getString(HorizonRomSources.KEY,""));
        File current = romRoot(context);
        return current == null ? roots : HorizonRomSources.remember(roots,current.getAbsolutePath());
    }

    public static void forgetRoot(Context context,String path) {
        File current = romRoot(context);
        String normalized = HorizonRomSources.normalize(path);
        if (normalized == null || current != null && normalized.equals(HorizonRomSources.normalize(current.getAbsolutePath()))) { return; }
        List<String> roots = rememberedRoots(context);
        roots.remove(normalized);
        PocketPrefs.prefs(context).edit().putString(HorizonRomSources.KEY,HorizonRomSources.encode(roots)).apply();
        HorizonRetroIndexStore.clear(context,normalized);
    }

    /** A folder picked with the system's folder picker, as a path ("primary:ROMs" -> /storage/emulated/0/ROMs). */
    public static File pathOfTree(Uri tree) {
        if (tree == null || !"com.android.externalstorage.documents".equals(tree.getAuthority())) { return null; }
        String id;
        try {
            id = DocumentsContract.getTreeDocumentId(tree);
        } catch (RuntimeException e) {
            return null;
        }
        int colon = id.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String volume = id.substring(0, colon);
        String rel = id.substring(colon + 1);
        if (!"primary".equalsIgnoreCase(volume) && !volume.matches("[A-Za-z0-9_-]{1,80}")) { return null; }
        if (rel.startsWith("/") || rel.indexOf('\\') >= 0) { return null; }
        File base = "primary".equalsIgnoreCase(volume) ? Environment.getExternalStorageDirectory()
                : new File("/storage/" + volume);
        File candidate = rel.isEmpty() ? base : new File(base, rel);
        if (HorizonRomSources.normalize(candidate.getAbsolutePath()) == null) { return null; }
        try {
            String parent = base.getCanonicalPath();
            String child = candidate.getCanonicalPath();
            return child.equals(parent) || child.startsWith(parent + "/") ? candidate : null;
        } catch (IOException | RuntimeException e) { return null; }
    }

    /**
     * Makes a folder for every system (ES-DE's names) with a systeminfo.txt saying what goes in
     * it, like ES-DE's "create system directories". Returns how many were made.
     */
    public static int createSystemFolders(Context context, File root) {
        int made = 0;
        if (!root.isDirectory() && !root.mkdirs()) {
            return 0;
        }
        for (EsdeConfig.SystemDef s : config(context).systems()) {
            if (s.absolute) {
                continue;
            }
            File dir = new File(root, s.folder);
            if (!dir.isDirectory()) {
                if (!dir.mkdirs()) {
                    continue;
                }
                made++;
            }
            File info = new File(dir, "systeminfo.txt");
            if (!info.exists()) {
                StringBuilder b = new StringBuilder();
                b.append("System name:\n").append(s.name).append("\n\nFull system name:\n").append(s.fullName)
                        .append("\n\nSupported file extensions:\n").append(android.text.TextUtils.join(" ", s.extensions))
                        .append("\n\nEmulators:\n");
                for (EsdeConfig.Command c : s.commands) {
                    b.append(c.label).append('\n');
                }
                try (Writer w = new OutputStreamWriter(new FileOutputStream(info), StandardCharsets.UTF_8)) {
                    w.write(b.toString());
                } catch (IOException ignored) {
                }
            }
        }
        return made;
    }

    // ---- Scanning ----------------------------------------------------------------------------

    /** What's in the ROM folder. */
    public static final class Library {
        public final File root;
        /** False if an observed read failure, interruption or scan limit prevented enumeration. */
        public boolean complete = true;
        /** System name -> its games, systems in ES-DE's order. */
        public final Map<String, List<RomScanner.Rom>> games = new LinkedHashMap<>();
        /** ES-DE's emulator picks: by system, and by game key (system/path). */
        final Map<String, String> esdeSystemEmu = new HashMap<>();
        final Map<String, String> esdeGameEmu = new HashMap<>();
        /** ES-DE's favourites (game keys). */
        final Set<String> esdeFavs = new HashSet<>();

        Library(File root) {
            this.root = root;
        }

        public int gameCount() {
            int n = 0;
            for (List<RomScanner.Rom> l : games.values()) {
                n += l.size();
            }
            return n;
        }

        public RomScanner.Rom find(String key) {
            int slash = key.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            List<RomScanner.Rom> l = games.get(key.substring(0, slash));
            if (l == null) {
                return null;
            }
            String rel = key.substring(slash + 1);
            for (RomScanner.Rom r : l) {
                if (r.relPath.equals(rel)) {
                    return r;
                }
            }
            return null;
        }
    }

    /** The system folders under the root, by lower case name (drives may differ in case). */
    private static Map<String, File> folders(File root) {
        return folders(root.listFiles());
    }

    private static Map<String,File> folders(File[] list) {
        Map<String, File> out = new HashMap<>();
        if (list != null) {
            for (File f : list) {
                if (f.isDirectory()) {
                    out.put(f.getName().toLowerCase(Locale.ROOT), f);
                }
            }
        }
        return out;
    }

    private static File folderOf(EsdeConfig.SystemDef s, File root, Map<String, File> folders) {
        if (s.absolute) {
            return new File(s.folder);
        }
        File exact = new File(root, s.folder);
        if (exact.isDirectory()) {
            return exact;
        }
        return folders.get(s.folder.toLowerCase(Locale.ROOT));
    }

    /** Use the scanner's folder spelling when launching from a source-bound shortcut. */
    public static File systemFolder(File root, EsdeConfig.SystemDef system) {
        if (root == null || system == null) return null;
        try {
            File exact = system.absolute ? new File(system.folder) : new File(root, system.folder);
            if (system.absolute || exact.isDirectory()) return exact;
            return folderOf(system, root, folders(root));
        } catch (SecurityException e) {
            return null;
        }
    }

    /** Looks through every system folder (blocking). */
    public static Library scan(Context context, File root) {
        EsdeConfig cfg = config(context);
        Library lib = new Library(root);
        File[] children = null;
        try { children = root.listFiles(); } catch (SecurityException ignored) {}
        lib.complete = children != null;
        Map<String, File> folders = folders(children);
        if (children != null) {
            for (EsdeConfig.SystemDef s : cfg.systems()) {
                if (Thread.currentThread().isInterrupted()) { lib.complete=false; break; }
                File dir = folderOf(s, root, folders);
                if (dir == null || s.absolute && !dir.exists()) { continue; }
                RomScanner.ScanResult result = RomScanner.scanChecked(s,dir);
                lib.complete &= result.complete;
                if (!result.games.isEmpty()) { lib.games.put(s.name,result.games); }
            }
        }
        if (!root.isDirectory() || !root.canRead()) { lib.complete=false; }
        applyGamelists(lib);
        applyCustomNames(context, lib);
        if (lib.complete) { saveCache(context, lib); }
        return lib;
    }

    /** What the last complete scan of this source found (may be out of date), or null. */
    public static Library cached(Context context, File root) {
        if (root == null || !root.isDirectory() || !root.canRead()) { return null; }
        List<HorizonRetroIndex.Row> rows = HorizonRetroIndexStore.load(context,root);
        if (rows == null) { return null; }
        EsdeConfig cfg = config(context);
        Library lib = new Library(root);
        Map<String, File> folders = folders(root);
        for (HorizonRetroIndex.Row row : rows) {
            EsdeConfig.SystemDef system = cfg.system(row.system);
            if (system == null) { continue; }
            File dir = folderOf(system,root,folders);
            if (dir == null || !dir.isDirectory()) { continue; }
            File game = new File(dir,row.relative);
            // Never follow a cached path outside the current system folder.
            try {
                if (!HorizonRetroIndex.inside(dir.getCanonicalPath(),game.getCanonicalPath())) { continue; }
            } catch (IOException | RuntimeException ignored) { continue; }
            List<RomScanner.Rom> list = lib.games.get(system.name);
            if (list == null) { list = new ArrayList<>(); lib.games.put(system.name,list); }
            list.add(new RomScanner.Rom(system.name,row.relative,game,row.directory));
        }
        applyGamelists(lib);
        applyCustomNames(context,lib);
        return lib;
    }

    private static void saveCache(Context context, Library lib) {
        if (lib.root == null || !lib.root.isDirectory() || !lib.root.canRead()) { return; }
        List<HorizonRetroIndex.Row> rows = new ArrayList<>();
        for (Map.Entry<String,List<RomScanner.Rom>> entry : lib.games.entrySet()) {
            for (RomScanner.Rom rom : entry.getValue()) {
                if (!HorizonRetroIndex.valid(entry.getKey(),rom.relPath)) { continue; }
                if (rows.size() >= HorizonRetroIndex.MAX_ROWS) { return; }
                rows.add(new HorizonRetroIndex.Row(entry.getKey(),rom.relPath,rom.directory));
            }
        }
        HorizonRetroIndexStore.save(context,lib.root,rows);
    }

    /** Names given here win over the file's and ES-DE's. */
    private static void applyCustomNames(Context context, Library lib) {
        for (List<RomScanner.Rom> l : lib.games.values()) {
            for (RomScanner.Rom r : l) {
                String n = PocketPrefs.customName(context, UUID, r.id());
                if (n != null) {
                    r.name = n;
                }
            }
        }
    }

    /** ES-DE's names, hidden games, favourites and emulator picks. */
    private static void applyGamelists(Library lib) {
        File base = new File(esdeDir(), "gamelists");
        if (!base.isDirectory()) {
            return;
        }
        for (Map.Entry<String, List<RomScanner.Rom>> e : lib.games.entrySet()) {
            File f = new File(new File(base, e.getKey()), "gamelist.xml");
            if (!f.canRead() || f.length() > 32L * 1024 * 1024) {
                continue;
            }
            EsdeGamelist gl;
            try (InputStream in = new FileInputStream(f)) {
                gl = EsdeGamelist.parse(in);
            } catch (IOException | RuntimeException ex) {
                continue;
            }
            if (gl.altEmulator != null) {
                lib.esdeSystemEmu.put(e.getKey(), gl.altEmulator);
            }
            List<RomScanner.Rom> keep = new ArrayList<>();
            for (RomScanner.Rom r : e.getValue()) {
                EsdeGamelist.Game g = gl.games.get(r.relPath);
                if (g != null) {
                    if (g.hidden) {
                        continue;
                    }
                    if (g.name != null && !g.name.isEmpty()) {
                        r.name = g.name;
                    }
                    if (g.favorite) {
                        lib.esdeFavs.add(r.key());
                    }
                    if (g.altEmulator != null) {
                        lib.esdeGameEmu.put(r.key(), g.altEmulator);
                    }
                }
                keep.add(r);
            }
            RomScanner.sortByName(keep);
            e.setValue(keep);
        }
    }

    // ---- Played, favourites ---------------------------------------------------------------

    public static void markPlayed(Context context, RomScanner.Rom rom) {
        List<String> lines = new ArrayList<>();
        lines.add(System.currentTimeMillis() + "\t" + rom.key());
        for (String l : recentLines(context)) {
            if (!l.endsWith("\t" + rom.key()) && lines.size() < MAX_RECENT) {
                lines.add(l);
            }
        }
        PocketPrefs.prefs(context).edit().putString(KEY_RECENT, android.text.TextUtils.join("\n", lines)).apply();
    }

    private static List<String> recentLines(Context context) {
        String s = PocketPrefs.prefs(context).getString(KEY_RECENT, "");
        List<String> out = new ArrayList<>();
        for (String l : s.split("\n")) {
            if (l.indexOf('\t') > 0) {
                out.add(l);
            }
        }
        return out;
    }

    /** The games played last as {time, "system/path"}, newest first (for Home). */
    public static List<String[]> recentEntries(Context context) {
        List<String[]> out = new ArrayList<>();
        for (String l : recentLines(context)) {
            int tab = l.indexOf('\t');
            out.add(new String[] {l.substring(0, tab), l.substring(tab + 1)});
        }
        return out;
    }

    /** When a game was played last from here (0: never). */
    public static long lastPlayed(Context context, String key) {
        for (String l : recentLines(context)) {
            int tab = l.indexOf('\t');
            if (l.substring(tab + 1).equals(key)) {
                try {
                    return Long.parseLong(l.substring(0, tab));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /** The games played last, newest first (those still in the library). */
    public static List<RomScanner.Rom> recent(Context context, Library lib) {
        List<RomScanner.Rom> out = new ArrayList<>();
        for (String l : recentLines(context)) {
            RomScanner.Rom r = lib.find(l.substring(l.indexOf('\t') + 1));
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * Games to try next: ones not played yet, from the systems played most (a favourite never
     * played, or one with its art, first), a new handful each day. At most max, no more than a
     * third from any one system.
     */
    public static List<RomScanner.Rom> suggested(Context context, Library lib, int max) {
        Map<String, Integer> plays = new HashMap<>();
        Set<String> played = new HashSet<>();
        int n = 0;
        for (String l : recentLines(context)) {
            String key = l.substring(l.indexOf('\t') + 1);
            played.add(key);
            int slash = key.indexOf('/');
            if (slash > 0) {
                String sys = key.substring(0, slash);
                // The latest plays count for more
                plays.put(sys, (plays.containsKey(sys) ? plays.get(sys) : 0) + (n < 10 ? 3 : 1));
            }
            n++;
        }
        long day = System.currentTimeMillis() / (24L * 60 * 60 * 1000);
        java.util.Random rnd = new java.util.Random(day * 7919L + lib.gameCount());
        final Map<RomScanner.Rom, Float> score = new HashMap<>();
        List<RomScanner.Rom> pool = new ArrayList<>();
        for (Map.Entry<String, List<RomScanner.Rom>> e : lib.games.entrySet()) {
            Integer p = plays.get(e.getKey());
            float affinity = p != null ? Math.min(6f, 1.5f + p * 0.5f) : 0f;
            for (RomScanner.Rom r : e.getValue()) {
                if (played.contains(r.key())) {
                    continue;
                }
                float sc = affinity + rnd.nextFloat() * 3.5f;
                if (isFavorite(context, lib, r)) {
                    sc += 4f;
                }
                score.put(r, sc);
                pool.add(r);
            }
        }
        Collections.sort(pool, (a, b) -> Float.compare(score.get(b), score.get(a)));
        // The best few: those with art go first (they look like something)
        List<RomScanner.Rom> top = new ArrayList<>(pool.subList(0, Math.min(pool.size(), max * 3)));
        for (RomScanner.Rom r : top) {
            if (PocketArt.has(context, UUID, r.id(), PocketArt.COVER)) {
                score.put(r, score.get(r) + 2f);
            }
        }
        Collections.sort(top, (a, b) -> Float.compare(score.get(b), score.get(a)));
        List<RomScanner.Rom> out = new ArrayList<>();
        Map<String, Integer> per = new HashMap<>();
        int cap = Math.max(2, max / 3);
        for (RomScanner.Rom r : top) {
            int k = per.containsKey(r.system) ? per.get(r.system) : 0;
            if (k >= cap && lib.games.size() > 1) {
                continue;
            }
            per.put(r.system, k + 1);
            out.add(r);
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    public static boolean isFavorite(Context context, Library lib, RomScanner.Rom rom) {
        Set<String> favs = PocketPrefs.prefs(context).getStringSet(KEY_FAVS, Collections.<String>emptySet());
        if (favs.contains(rom.key())) {
            return true;
        }
        // ES-DE's favourite, unless unmarked here
        return lib.esdeFavs.contains(rom.key()) && !favs.contains("-" + rom.key());
    }

    public static void setFavorite(Context context, RomScanner.Rom rom, boolean on) {
        Set<String> favs = new HashSet<>(PocketPrefs.prefs(context).getStringSet(KEY_FAVS, Collections.<String>emptySet()));
        favs.remove(rom.key());
        favs.remove("-" + rom.key());
        favs.add(on ? rom.key() : "-" + rom.key());
        PocketPrefs.prefs(context).edit().putStringSet(KEY_FAVS, favs).apply();
    }

    public static List<RomScanner.Rom> favorites(Context context, Library lib) {
        List<RomScanner.Rom> out = new ArrayList<>();
        for (List<RomScanner.Rom> l : lib.games.values()) {
            for (RomScanner.Rom r : l) {
                if (isFavorite(context, lib, r)) {
                    out.add(r);
                }
            }
        }
        RomScanner.sortByName(out);
        return out;
    }

    public static boolean showEmptySystems(Context context) {
        return PocketPrefs.prefs(context).getBoolean(KEY_SHOW_EMPTY, false);
    }

    public static void setShowEmptySystems(Context context, boolean on) {
        PocketPrefs.prefs(context).edit().putBoolean(KEY_SHOW_EMPTY, on).apply();
    }

    // ---- Emulators -------------------------------------------------------------------------

    /** The command picked here for the system (null: not picked). */
    public static String systemChoice(Context context, String system) {
        return PocketPrefs.prefs(context).getString(KEY_SYS_EMU + system, null);
    }

    public static void setSystemChoice(Context context, String system, String label) {
        if (label == null) {
            PocketPrefs.prefs(context).edit().remove(KEY_SYS_EMU + system).apply();
        }
        else {
            PocketPrefs.prefs(context).edit().putString(KEY_SYS_EMU + system, label).apply();
        }
    }

    public static String gameChoice(Context context, RomScanner.Rom rom) {
        return PocketPrefs.prefs(context).getString(KEY_GAME_EMU + rom.id(), null);
    }

    public static void setGameChoice(Context context, RomScanner.Rom rom, String label) {
        if (label == null) {
            PocketPrefs.prefs(context).edit().remove(KEY_GAME_EMU + rom.id()).apply();
        }
        else {
            PocketPrefs.prefs(context).edit().putString(KEY_GAME_EMU + rom.id(), label).apply();
        }
    }

    /** The system's emulator: picked here, picked in ES-DE, else the first one installed. */
    public static EsdeConfig.Command systemCommand(Context context, Library lib, EsdeConfig.SystemDef s) {
        EsdeConfig.Command c = s.command(systemChoice(context, s.name));
        if (c == null && lib != null) {
            c = s.command(lib.esdeSystemEmu.get(s.name));
        }
        if (c != null) {
            return c;
        }
        EsdeConfig cfg = config(context);
        for (EsdeConfig.Command cmd : s.commands) {
            if (!cmd.isPlaceholder() && RetroLauncher.installed(context, cfg, cmd)) {
                return cmd;
            }
        }
        return s.commands.isEmpty() ? null : s.commands.get(0);
    }

    /** The game's emulator: its own pick (here, then ES-DE's), else the system's. */
    public static EsdeConfig.Command gameCommand(Context context, Library lib, EsdeConfig.SystemDef s, RomScanner.Rom rom) {
        EsdeConfig.Command c = s.command(gameChoice(context, rom));
        if (c == null && lib != null) {
            c = s.command(lib.esdeGameEmu.get(rom.key()));
        }
        return c != null ? c : systemCommand(context, lib, s);
    }

    // ---- ES-DE's scraped pictures ----------------------------------------------------------

    private static final String[] IMAGE_EXT = {".png", ".jpg", ".jpeg", ".webp"};

    private static File media(File systemMedia, String folder, String relNoExt) {
        for (String ext : IMAGE_EXT) {
            File f = new File(new File(systemMedia, folder), relNoExt + ext);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    /** The video snap ES-DE downloaded for the game (a short clip of it being played), or null. */
    public static File esdeVideo(RomScanner.Rom rom) {
        return esdeFile(rom, "videos", new String[]{".mp4", ".mkv", ".webm", ".avi", ".mov"});
    }

    /** The game's manual ES-DE downloaded (a PDF), or null. */
    public static File esdeManual(RomScanner.Rom rom) {
        return esdeFile(rom, "manuals", new String[]{".pdf", ".PDF"});
    }

    private static File esdeFile(RomScanner.Rom rom, String folder, String[] exts) {
        if (rom == null) {
            return null;
        }
        File sys = new File(mediaDir(), rom.system);
        String rel = rom.relPath;
        int slash = rel.lastIndexOf('/');
        int dot = rel.lastIndexOf('.');
        String relNoExt = dot > slash + 1 ? rel.substring(0, dot) : rel;
        for (String ext : exts) {
            File f = new File(new File(sys, folder), relNoExt + ext);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    /**
     * Takes the cover, background and logo ES-DE downloaded for the game, for what it's still
     * missing here (blocking). True when something was added.
     */
    public static boolean importEsdeMedia(Context context, RomScanner.Rom rom) {
        File sys = new File(mediaDir(), rom.system);
        if (!sys.isDirectory()) {
            return false;
        }
        String rel = rom.relPath;
        int slash = rel.lastIndexOf('/');
        int dot = rel.lastIndexOf('.');
        String relNoExt = dot > slash + 1 ? rel.substring(0, dot) : rel;
        boolean changed = false;
        String[][] kinds = {
                {"covers"},
                {"fanart", "screenshots", "titlescreens"},
                {"marquees"},
        };
        int[] art = {PocketArt.COVER, PocketArt.HERO, PocketArt.LOGO};
        for (int k = 0; k < art.length; k++) {
            if (PocketArt.has(context, UUID, rom.id(), art[k])) {
                continue;
            }
            for (String folder : kinds[k]) {
                File f = media(sys, folder, relNoExt);
                if (f == null || f.length() > 24L * 1024 * 1024) {
                    continue;
                }
                try {
                    PocketArt.save(context, UUID, rom.id(), art[k], readAll(f));
                    changed = true;
                    break;
                } catch (IOException | RuntimeException ignored) {
                }
            }
        }
        return changed;
    }

    private static byte[] readAll(File f) throws IOException {
        byte[] buf = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
        }
        return buf;
    }
}
