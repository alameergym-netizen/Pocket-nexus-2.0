package com.limelight.pocket.retro;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;

/**
 * ScreenScraper (screenscraper.fr), the big retro games database: for each ROM, found by its
 * checksum and name, its box art, logo (wheel), screenshots, title screen, fan art, 3D box and
 * video, saved where ES-DE keeps its own scraped media (so ES-DE, the Retro section, the game's
 * page and Home all use them: covers, logos and backgrounds straight away, the video behind the
 * chosen game). One game at a time, at ScreenScraper's pace, with the player's own account.
 */
public final class ScreenScraper {

    public static final String KEY_USER = "pocket_ss_user";
    public static final String KEY_PASS = "pocket_ss_pass";
    public static final String KEY_DEV_ID = "pocket_ss_devid";
    public static final String KEY_DEV_PASS = "pocket_ss_devpass";
    public static final String KEY_REGION = "pocket_ss_region";
    public static final String KEY_VIDEOS = "pocket_ss_videos";
    public static final String KEY_ONLY_MISSING = "pocket_ss_only_missing";
    private static final String SOFT = "PocketNexus";
    private static final String API = "https://api.screenscraper.fr/api2/jeuInfos.php";
    private static final int MAX_PICTURE = 16 * 1024 * 1024;
    private static final int MAX_VIDEO = 80 * 1024 * 1024;
    private static final long HASH_LIMIT = 96L * 1024 * 1024;
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean running;
    private static volatile boolean stop;

    /** ES-DE's system folders and ScreenScraper's system numbers. */
    private static final Map<String, Integer> SYSTEMS = new HashMap<>();

    static {
        Object[][] s = {
                {"nes", 3}, {"famicom", 3}, {"fds", 106}, {"snes", 4}, {"sfc", 4}, {"satellaview", 107}, {"n64", 14},
                {"n64dd", 122}, {"gb", 9}, {"gbc", 10}, {"gba", 12}, {"nds", 15}, {"n3ds", 17}, {"3ds", 17},
                {"gc", 13}, {"wii", 16}, {"wiiu", 18}, {"switch", 225}, {"virtualboy", 11}, {"pokemini", 211},
                {"megadrive", 1}, {"genesis", 1}, {"mastersystem", 2}, {"gamegear", 21}, {"segacd", 20},
                {"megacd", 20}, {"sega32x", 19}, {"saturn", 22}, {"dreamcast", 23}, {"sg-1000", 109},
                {"naomi", 56}, {"atomiswave", 53}, {"psx", 57}, {"ps2", 58}, {"ps3", 59}, {"psp", 61},
                {"psvita", 62}, {"pcengine", 31}, {"tg16", 31}, {"pcenginecd", 114}, {"tg-cd", 114},
                {"supergrafx", 105}, {"neogeo", 142}, {"neogeocd", 70}, {"ngp", 25}, {"ngpc", 82},
                {"arcade", 75}, {"mame", 75}, {"fbneo", 75}, {"cps1", 6}, {"cps2", 7}, {"cps3", 8},
                {"atari2600", 26}, {"atari5200", 40}, {"atari7800", 41}, {"atarilynx", 28}, {"lynx", 28},
                {"atarijaguar", 27}, {"jaguar", 27}, {"atarist", 42}, {"wonderswan", 45},
                {"wonderswancolor", 46}, {"colecovision", 48}, {"intellivision", 115}, {"vectrex", 102},
                {"3do", 29}, {"dos", 135}, {"msx", 113}, {"msx2", 116}, {"c64", 66}, {"amiga", 64},
                {"amstradcpc", 65}, {"zxspectrum", 76}, {"x68000", 79}, {"pc88", 221}, {"pc98", 208},
                {"odyssey2", 104}, {"channelf", 80}, {"gameandwatch", 52}, {"scummvm", 123},
        };
        for (Object[] p : s) {
            SYSTEMS.put((String) p[0], (Integer) p[1]);
        }
    }

    /** What's saved, and where (ES-DE's folder names): ScreenScraper's media kinds, in preference. */
    private static final String[][] MEDIA = {
            {"covers", "box-2D"},
            {"marquees", "wheel-hd", "wheel"},
            {"screenshots", "ss"},
            {"titlescreens", "sstitle"},
            {"fanart", "fanart"},
            {"3dboxes", "box-3D"},
            {"videos", "video-normalized", "video"},
    };

    public interface Progress {
        void onProgress(int done, int total, int found, String name);

        void onDone(int found, int total, String problem);
    }

    private ScreenScraper() {
    }

    private static SharedPreferences prefs(Context c) {
        return PocketPrefs.prefs(c);
    }

    /** The developer ID and password are what ScreenScraper asks every app for (free, on request). */
    public static boolean ready(Context c) {
        return !prefs(c).getString(KEY_DEV_ID, "").trim().isEmpty() && !prefs(c).getString(KEY_DEV_PASS, "").trim().isEmpty();
    }

    public static boolean hasAccount(Context c) {
        return !prefs(c).getString(KEY_USER, "").trim().isEmpty();
    }

    public static boolean isRunning() {
        return running;
    }

    public static void stop() {
        stop = true;
    }

    // ---- One game ------------------------------------------------------------------------------

    /** Scrapes one game (blocking): how many pictures / videos were saved. */
    public static int scrape(Context context, RomScanner.Rom rom) throws ScrapeException {
        Context c = context.getApplicationContext();
        if (!ready(c)) {
            throw new ScrapeException(c.getString(R.string.pocket_ss_need_dev));
        }
        JSONObject game = lookup(c, rom);
        if (game == null) {
            return 0;
        }
        JSONArray medias = game.optJSONArray("medias");
        if (medias == null) {
            return 0;
        }
        File sysDir = new File(RetroLibrary.mediaDir(), rom.system);
        String rel = rom.relPath;
        int slash = rel.lastIndexOf('/');
        int dot = rel.lastIndexOf('.');
        String relNoExt = rom.directory ? rel : (dot > slash + 1 ? rel.substring(0, dot) : rel);
        boolean videos = prefs(c).getBoolean(KEY_VIDEOS, true);
        boolean onlyMissing = prefs(c).getBoolean(KEY_ONLY_MISSING, true);
        String region = prefs(c).getString(KEY_REGION, "wor");
        int saved = 0;
        for (String[] kind : MEDIA) {
            String folder = kind[0];
            if ("videos".equals(folder) && !videos) {
                continue;
            }
            File dir = new File(sysDir, folder);
            if (onlyMissing && exists(dir, relNoExt)) {
                continue;
            }
            JSONObject pick = null;
            for (int t = 1; t < kind.length && pick == null; t++) {
                pick = best(medias, kind[t], region);
            }
            if (pick == null) {
                continue;
            }
            String url = pick.optString("url", null);
            String format = pick.optString("format", "videos".equals(folder) ? "mp4" : "png");
            if (url == null || url.isEmpty()) {
                continue;
            }
            File out = new File(dir, relNoExt + "." + format.toLowerCase(java.util.Locale.ROOT));
            try {
                if (download(url, out, "videos".equals(folder) ? MAX_VIDEO : MAX_PICTURE)) {
                    saved++;
                }
            } catch (Exception ignored) {
                // The next kind
            }
        }
        if (saved > 0) {
            // Covers, backgrounds and logos into the app's own art (Home, the game's page, Retro)
            RetroLibrary.importEsdeMedia(c, rom);
        }
        return saved;
    }

    private static boolean exists(File dir, String relNoExt) {
        for (String ext : new String[]{".png", ".jpg", ".jpeg", ".webp", ".mp4", ".mkv", ".webm"}) {
            if (new File(dir, relNoExt + ext).isFile()) {
                return true;
            }
        }
        return false;
    }

    /** The media of a kind in the region asked for, else worldwide, US, Europe, Japan, any. */
    private static JSONObject best(JSONArray medias, String type, String region) {
        String[] order = {region, "wor", "us", "eu", "ss", "jp", null};
        for (String r : order) {
            for (int i = 0; i < medias.length(); i++) {
                JSONObject m = medias.optJSONObject(i);
                if (m == null || !type.equals(m.optString("type"))) {
                    continue;
                }
                String mr = m.optString("region", "");
                if (r == null || r.equals(mr) || (mr.isEmpty() && "wor".equals(r))) {
                    return m;
                }
            }
        }
        return null;
    }

    /** ScreenScraper's game for this ROM (by checksum, name and size), or null when unknown. */
    private static JSONObject lookup(Context c, RomScanner.Rom rom) throws ScrapeException {
        SharedPreferences p = prefs(c);
        StringBuilder q = new StringBuilder(API);
        try {
            q.append("?devid=").append(enc(p.getString(KEY_DEV_ID, "").trim()));
            q.append("&devpassword=").append(enc(p.getString(KEY_DEV_PASS, "").trim()));
            q.append("&softname=").append(SOFT).append("&output=json");
            if (hasAccount(c)) {
                q.append("&ssid=").append(enc(p.getString(KEY_USER, "").trim()));
                q.append("&sspassword=").append(enc(p.getString(KEY_PASS, "")));
            }
            Integer sys = SYSTEMS.get(rom.system);
            if (sys != null) {
                q.append("&systemeid=").append(sys);
            }
            File f = rom.file;
            q.append("&romtype=").append(rom.directory ? "dossier" : "rom");
            q.append("&romnom=").append(enc(f.getName()));
            if (!rom.directory && f.isFile()) {
                q.append("&romtaille=").append(f.length());
                if (f.length() <= HASH_LIMIT) {
                    String crc = crc(f);
                    if (crc != null) {
                        q.append("&crc=").append(crc);
                    }
                }
            }
        } catch (Exception e) {
            throw new ScrapeException(e.getMessage());
        }
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(q.toString()).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", SOFT);
            int code = con.getResponseCode();
            if (code == 404) {
                return null;
            }
            if (code == 401 || code == 403) {
                throw new ScrapeException(c.getString(R.string.pocket_ss_bad_login));
            }
            if (code == 423 || code == 426) {
                throw new ScrapeException(c.getString(R.string.pocket_ss_closed));
            }
            if (code == 429 || code == 430 || code == 431) {
                throw new ScrapeException(c.getString(R.string.pocket_ss_quota));
            }
            if (code != 200) {
                return null;
            }
            String body = read(con.getInputStream(), 4 * 1024 * 1024);
            JSONObject o = new JSONObject(body);
            JSONObject resp = o.optJSONObject("response");
            return resp != null ? resp.optJSONObject("jeu") : null;
        } catch (ScrapeException e) {
            throw e;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    // ---- The whole library -----------------------------------------------------------------

    /** Every ROM, one after another, with a notification showing how far it got. */
    public static void scrapeAll(Context context, final List<RomScanner.Rom> roms, final Progress progress) {
        if (running) {
            return;
        }
        running = true;
        stop = false;
        final Context c = context.getApplicationContext();
        final NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel("pocket_scrape",
                    c.getString(R.string.pocket_ss_title), NotificationManager.IMPORTANCE_LOW));
        }
        final NotificationCompat.Builder b = new NotificationCompat.Builder(c, "pocket_scrape")
                .setSmallIcon(R.drawable.ic_pocket_plus)
                .setContentTitle(c.getString(R.string.pocket_ss_title))
                .setOnlyAlertOnce(true)
                .setOngoing(true);
        ONE.execute(() -> {
            int found = 0;
            String problem = null;
            int i = 0;
            for (RomScanner.Rom r : roms) {
                if (stop) {
                    break;
                }
                final int at = i;
                final int got = found;
                final String name = RomScanner.searchName(r.name);
                MAIN.post(() -> progress.onProgress(at, roms.size(), got, name));
                if (nm != null) {
                    try {
                        nm.notify(7701, b.setContentText(name).setProgress(roms.size(), at, false).build());
                    } catch (RuntimeException ignored) {
                    }
                }
                try {
                    if (scrape(c, r) > 0) {
                        found++;
                    }
                } catch (ScrapeException e) {
                    problem = e.getMessage();
                    break;
                }
                i++;
                try {
                    Thread.sleep(1200); // ScreenScraper's pace for one account
                } catch (InterruptedException e) {
                    break;
                }
            }
            running = false;
            if (nm != null) {
                try {
                    nm.notify(7701, new NotificationCompat.Builder(c, "pocket_scrape")
                            .setSmallIcon(R.drawable.ic_pocket_plus)
                            .setContentTitle(c.getString(R.string.pocket_ss_title))
                            .setContentText(problem != null ? problem
                                    : c.getString(R.string.pocket_ss_done, found, roms.size()))
                            .setAutoCancel(true).build());
                } catch (RuntimeException ignored) {
                }
            }
            final int f = found;
            final String pr = problem;
            MAIN.post(() -> progress.onDone(f, roms.size(), pr));
        });
    }

    /** Every ROM the Retro section knows. */
    public static List<RomScanner.Rom> allRoms(Context c) {
        List<RomScanner.Rom> out = new ArrayList<>();
        File root = RetroLibrary.romRoot(c);
        RetroLibrary.Library lib = root != null ? RetroLibrary.cached(c, root) : null;
        if (lib != null) {
            for (List<RomScanner.Rom> roms : lib.games.values()) {
                out.addAll(roms);
            }
        }
        return out;
    }

    // ---- Small things -------------------------------------------------------------------------

    public static final class ScrapeException extends Exception {
        ScrapeException(String m) {
            super(m);
        }
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String crc(File f) {
        CRC32 crc = new CRC32();
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) > 0) {
                crc.update(buf, 0, r);
            }
            return String.format("%08X", crc.getValue());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean download(String url, File out, int max) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setRequestProperty("User-Agent", SOFT);
        try {
            if (con.getResponseCode() != 200) {
                return false;
            }
            String type = con.getContentType();
            if (type != null && type.startsWith("text")) {
                return false; // "NOMEDIA" and such
            }
            File dir = out.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            File part = new File(out.getPath() + ".part");
            long total = 0;
            try (InputStream in = con.getInputStream(); OutputStream o = new FileOutputStream(part)) {
                byte[] buf = new byte[1 << 16];
                int r;
                while ((r = in.read(buf)) > 0) {
                    total += r;
                    if (total > max) {
                        break;
                    }
                    o.write(buf, 0, r);
                }
            }
            if (total == 0 || total > max) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                return false;
            }
            return part.renameTo(out);
        } finally {
            con.disconnect();
        }
    }

    private static String read(InputStream in, int max) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int r;
        while ((r = in.read(buf)) > 0 && out.size() < max) {
            out.write(buf, 0, r);
        }
        in.close();
        return out.toString("UTF-8");
    }
}
