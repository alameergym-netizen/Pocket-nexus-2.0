package com.limelight.pocket.cloud;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The games Xbox Cloud Gaming streams, with their art: Microsoft's own public lists (the ones its
 * site reads), each game's name, publisher, a few lines about it, and its pictures (the poster,
 * the wide hero art, the logo). Kept for a day. Each one starts straight from its own link
 * (xbox.com/play/launch/...), in whichever client is picked. Blocking: never on the main thread.
 */
public final class XcloudCatalog {
    private XcloudCatalog() {}

    /** Every game in Xbox Cloud Gaming (Microsoft's list id, as its site uses). */
    private static final String ALL_CLOUD_GAMES = "29a81209-df6f-41fd-a528-2ae6b91f719c";
    private static final String MARKET = "US";
    private static final String LANG = "en-us";
    private static final long FRESH_MS = 24L * 60 * 60 * 1000;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    public static final class Game {
        public String id;
        public String title;
        public String publisher;
        public String about;
        /** A tall poster (2:3). */
        public String poster;
        /** A square box art. */
        public String box;
        /** The wide picture behind it (16:9). */
        public String hero;
        public String logo;

        public String slug() {
            String s = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
            return s.isEmpty() ? "game" : s;
        }

        /** Its own link: opens it and starts it streaming. */
        public String launchUrl() {
            return "https://www.xbox.com/play/launch/" + slug() + "/" + id;
        }

        JSONObject toJson() throws org.json.JSONException {
            return new JSONObject().put("id", id).put("title", title).put("publisher", publisher)
                    .put("about", about).put("poster", poster).put("box", box).put("hero", hero).put("logo", logo);
        }

        static Game fromJson(JSONObject o) {
            Game g = new Game();
            g.id = o.optString("id");
            g.title = o.optString("title");
            g.publisher = o.optString("publisher", null);
            g.about = o.optString("about", null);
            g.poster = o.optString("poster", null);
            g.box = o.optString("box", null);
            g.hero = o.optString("hero", null);
            g.logo = o.optString("logo", null);
            return g;
        }
    }

    private static List<Game> memory;

    /** The games (kept from earlier when fresh enough; fresh: looked up again). */
    public static synchronized List<Game> load(Context c, boolean fresh) throws IOException {
        File cache = new File(c.getCacheDir(), "pocket_xcloud_catalog.json");
        if (!fresh && memory != null) {
            return memory;
        }
        if (!fresh && cache.exists() && System.currentTimeMillis() - cache.lastModified() < FRESH_MS) {
            try {
                memory = parseCache(read(cache));
                if (!memory.isEmpty()) {
                    return memory;
                }
            } catch (Exception ignored) {
            }
        }
        List<Game> games;
        try {
            games = fetch();
        } catch (IOException e) {
            // No connection: the last list, however old
            if (cache.exists()) {
                try {
                    memory = parseCache(read(cache));
                    return memory;
                } catch (Exception ignored) {
                }
            }
            throw e;
        }
        try {
            JSONArray out = new JSONArray();
            for (Game g : games) {
                out.put(g.toJson());
            }
            try (FileOutputStream f = new FileOutputStream(cache)) {
                f.write(out.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
        memory = games;
        return games;
    }

    private static String read(File f) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static List<Game> parseCache(String s) throws org.json.JSONException {
        JSONArray a = new JSONArray(s);
        List<Game> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            out.add(Game.fromJson(a.getJSONObject(i)));
        }
        return out;
    }

    private static String get(String url) throws IOException {
        Request r = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/130.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build();
        try (Response resp = CLIENT.newCall(r).execute()) {
            ResponseBody body = resp.body();
            if (!resp.isSuccessful() || body == null) {
                throw new IOException("HTTP " + resp.code());
            }
            return body.string();
        }
    }

    private static List<Game> fetch() throws IOException {
        List<String> ids = new ArrayList<>();
        try {
            JSONArray list = new JSONArray(get("https://catalog.gamepass.com/sigls/v2?id=" + ALL_CLOUD_GAMES
                    + "&language=" + LANG + "&market=" + MARKET));
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                String id = o != null ? o.optString("id", "") : "";
                if (!id.isEmpty() && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        List<Game> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 20) {
            List<String> batch = ids.subList(i, Math.min(ids.size(), i + 20));
            String json = get("https://displaycatalog.mp.microsoft.com/v7.0/products?bigIds="
                    + android.text.TextUtils.join(",", batch) + "&market=" + MARKET + "&languages=" + LANG);
            try {
                JSONArray products = new JSONObject(json).optJSONArray("Products");
                for (int p = 0; products != null && p < products.length(); p++) {
                    Game g = product(products.getJSONObject(p));
                    if (g != null) {
                        out.add(g);
                    }
                }
            } catch (org.json.JSONException ignored) {
            }
        }
        Collections.sort(out, (a, b) -> a.title.compareToIgnoreCase(b.title));
        return out;
    }

    private static Game product(JSONObject p) {
        JSONArray loc = p.optJSONArray("LocalizedProperties");
        JSONObject l = loc != null && loc.length() > 0 ? loc.optJSONObject(0) : null;
        if (l == null) {
            return null;
        }
        Game g = new Game();
        g.id = p.optString("ProductId");
        g.title = l.optString("ProductTitle", "").trim();
        if (g.id.isEmpty() || g.title.isEmpty()) {
            return null;
        }
        g.publisher = l.optString("PublisherName", null);
        g.about = l.optString("ShortDescription", null);
        if (g.about == null || g.about.isEmpty()) {
            g.about = l.optString("ProductDescription", null);
        }
        JSONArray images = l.optJSONArray("Images");
        for (int i = 0; images != null && i < images.length(); i++) {
            JSONObject im = images.optJSONObject(i);
            if (im == null) {
                continue;
            }
            String purpose = im.optString("ImagePurpose");
            String uri = im.optString("Uri", "");
            if (uri.isEmpty()) {
                continue;
            }
            if (uri.startsWith("//")) {
                uri = "https:" + uri;
            }
            switch (purpose) {
                case "Poster":
                    if (g.poster == null) {
                        g.poster = uri;
                    }
                    break;
                case "BoxArt":
                    if (g.box == null) {
                        g.box = uri;
                    }
                    break;
                case "SuperHeroArt":
                    g.hero = uri;
                    break;
                case "TitledHeroArt":
                    if (g.hero == null) {
                        g.hero = uri;
                    }
                    break;
                case "Logo":
                    if (g.logo == null) {
                        g.logo = uri;
                    }
                    break;
                default:
                    break;
            }
        }
        return g;
    }

    /** A picture at about this width (Microsoft's image server scales them). */
    public static String sized(String url, int width) {
        if (url == null) {
            return null;
        }
        return url + (url.contains("?") ? "&" : "?") + "w=" + width;
    }

    /** Its picture's bytes (for Home's art). Blocking. */
    static byte[] bytes(String url) throws IOException {
        Request r = new Request.Builder().url(url).build();
        try (Response resp = CLIENT.newCall(r).execute()) {
            ResponseBody body = resp.body();
            if (!resp.isSuccessful() || body == null) {
                throw new IOException("HTTP " + resp.code());
            }
            return body.bytes();
        }
    }
}
