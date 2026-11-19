package com.limelight.pocket.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Xml;

import com.limelight.pocket.PocketPrefs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Game news on Home, like a console's news channel: the newest headlines from the feeds the
 * player picks (PlayStation, Xbox, Nintendo, retro handhelds...), any RSS or Atom feed can be
 * added. Kept for half an hour; each opens in the browser.
 */
public final class News {

    public static final class Story {
        public final String title;
        public final String link;
        public final String source;
        public final String image;
        public final long at;

        Story(String title, String link, String source, String image, long at) {
            this.title = title;
            this.link = link;
            this.source = source;
            this.image = image;
            this.at = at;
        }
    }

    public interface Callback {
        void onNews(List<Story> stories);
    }

    /** The feeds to choose from: name, address. */
    public static final String[][] FEEDS = {
            {"PlayStation Blog", "https://blog.playstation.com/feed/"},
            {"Xbox Wire", "https://news.xbox.com/en-us/feed/"},
            {"Nintendo Life", "https://www.nintendolife.com/feeds/latest"},
            {"The Verge Games", "https://www.theverge.com/rss/games/index.xml"},
            {"PC Gamer", "https://www.pcgamer.com/rss/"},
            {"Retro Dodo", "https://retrododo.com/feed/"},
            {"Steam News", "https://store.steampowered.com/feeds/news/"},
    };
    private static final String KEY_FEEDS = "pocket_news_feeds";
    private static final String KEY_CUSTOM = "pocket_news_custom";
    private static final String KEY_CACHE = "pocket_news_cache";
    private static final String KEY_AT = "pocket_news_at";
    private static final String DEFAULT = "https://blog.playstation.com/feed/|https://news.xbox.com/en-us/feed/|https://www.nintendolife.com/feeds/latest";
    private static final long FRESH_MS = 30 * 60 * 1000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> IMAGES = new LruCache<>(12);
    private static volatile boolean busy;

    private News() {
    }

    // ---- Which feeds ---------------------------------------------------------------------------

    public static Set<String> chosen(Context c) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : PocketPrefs.prefs(c).getString(KEY_FEEDS, DEFAULT).split("\\|")) {
            if (!s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    public static void setChosen(Context c, String url, boolean on) {
        Set<String> now = chosen(c);
        if (on) {
            now.add(url);
        }
        else {
            now.remove(url);
        }
        StringBuilder b = new StringBuilder();
        for (String s : now) {
            b.append(b.length() > 0 ? "|" : "").append(s);
        }
        PocketPrefs.prefs(c).edit().putString(KEY_FEEDS, b.toString()).remove(KEY_AT).apply();
    }

    /** Feeds the player added by address. */
    public static List<String> custom(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : PocketPrefs.prefs(c).getString(KEY_CUSTOM, "").split("\\|")) {
            if (!s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    public static void addCustom(Context c, String url) {
        String u = url.trim();
        if (!u.startsWith("http")) {
            u = "https://" + u;
        }
        List<String> now = custom(c);
        if (!now.contains(u)) {
            now.add(u);
        }
        StringBuilder b = new StringBuilder();
        for (String s : now) {
            b.append(b.length() > 0 ? "|" : "").append(s);
        }
        PocketPrefs.prefs(c).edit().putString(KEY_CUSTOM, b.toString()).apply();
        setChosen(c, u, true);
    }

    // ---- The stories --------------------------------------------------------------------------

    public static List<Story> cached(Context c) {
        List<Story> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(PocketPrefs.prefs(c).getString(KEY_CACHE, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Story(o.optString("t"), o.optString("l"), o.optString("s"),
                        o.optString("i", null), o.optLong("a")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void refresh(Context context, boolean force, final Callback cb) {
        final Context c = context.getApplicationContext();
        long at = PocketPrefs.prefs(c).getLong(KEY_AT, 0);
        if ((!force && System.currentTimeMillis() - at < FRESH_MS) || busy) {
            return;
        }
        busy = true;
        IO.execute(() -> {
            List<Story> all = new ArrayList<>();
            for (String url : chosen(c)) {
                try {
                    all.addAll(fetch(url));
                } catch (Exception ignored) {
                }
            }
            Collections.sort(all, (x, y) -> Long.compare(y.at, x.at));
            List<Story> top = all.subList(0, Math.min(12, all.size()));
            JSONArray keep = new JSONArray();
            try {
                for (Story s : top) {
                    JSONObject o = new JSONObject();
                    o.put("t", s.title);
                    o.put("l", s.link);
                    o.put("s", s.source);
                    if (s.image != null) {
                        o.put("i", s.image);
                    }
                    o.put("a", s.at);
                    keep.put(o);
                }
            } catch (Exception ignored) {
            }
            if (!top.isEmpty()) {
                PocketPrefs.prefs(c).edit().putString(KEY_CACHE, keep.toString())
                        .putLong(KEY_AT, System.currentTimeMillis()).apply();
            }
            busy = false;
            final List<Story> got = new ArrayList<>(top);
            MAIN.post(() -> {
                if (cb != null && !got.isEmpty()) {
                    cb.onNews(got);
                }
            });
        });
    }

    /** RSS or Atom: each item's title, link, date and picture. */
    private static List<Story> fetch(String url) throws Exception {
        List<Story> out = new ArrayList<>();
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(10000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 PocketNexus");
        try (InputStream in = con.getInputStream()) {
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(in, null);
            String source = null;
            String title = null;
            String link = null;
            String image = null;
            String date = null;
            boolean inItem = false;
            int depthItem = -1;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT && out.size() < 8) {
                String name = p.getName();
                if (ev == XmlPullParser.START_TAG) {
                    if ("item".equals(name) || "entry".equals(name)) {
                        inItem = true;
                        depthItem = p.getDepth();
                        title = link = image = date = null;
                    }
                    else if ("title".equals(name)) {
                        String t = text(p);
                        if (inItem) {
                            title = t;
                        }
                        else if (source == null) {
                            source = t;
                        }
                    }
                    else if (inItem && "link".equals(name)) {
                        String href = p.getAttributeValue(null, "href");
                        String rel = p.getAttributeValue(null, "rel");
                        if (href != null) {
                            if (link == null && (rel == null || "alternate".equals(rel))) {
                                link = href;
                            }
                        }
                        else {
                            link = text(p);
                        }
                    }
                    else if (inItem && ("pubDate".equals(name) || "published".equals(name) || "updated".equals(name)
                            || "dc:date".equals(name))) {
                        if (date == null) {
                            date = text(p);
                        }
                    }
                    else if (inItem && image == null && ("media:content".equals(name) || "media:thumbnail".equals(name)
                            || "enclosure".equals(name))) {
                        String u = p.getAttributeValue(null, "url");
                        String type = p.getAttributeValue(null, "type");
                        String medium = p.getAttributeValue(null, "medium");
                        if (u != null && (type == null || type.startsWith("image") || "image".equals(medium)
                                || "media:thumbnail".equals(name))) {
                            image = u;
                        }
                    }
                    else if (inItem && image == null && ("description".equals(name) || "content:encoded".equals(name)
                            || "content".equals(name) || "summary".equals(name))) {
                        String html = text(p);
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']")
                                .matcher(html != null ? html : "");
                        if (m.find()) {
                            image = m.group(1);
                        }
                    }
                }
                else if (ev == XmlPullParser.END_TAG && inItem && ("item".equals(name) || "entry".equals(name))
                        && p.getDepth() == depthItem) {
                    inItem = false;
                    if (title != null && link != null) {
                        out.add(new Story(clean(title), link.trim(), source != null ? clean(source) : host(url),
                                image != null && image.startsWith("http") ? image.replace("&amp;", "&") : null, when(date)));
                    }
                }
                ev = p.next();
            }
        } finally {
            con.disconnect();
        }
        return out;
    }

    private static String text(XmlPullParser p) throws Exception {
        StringBuilder b = new StringBuilder();
        int depth = p.getDepth();
        int ev = p.next();
        while (!(ev == XmlPullParser.END_TAG && p.getDepth() == depth)) {
            if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) {
                b.append(p.getText());
            }
            else if (ev == XmlPullParser.END_DOCUMENT) {
                break;
            }
            ev = p.next();
        }
        return b.toString().trim();
    }

    private static String clean(String s) {
        String t = android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        return t.replaceAll("\\s+", " ").trim();
    }

    private static String host(String url) {
        try {
            return new URL(url).getHost().replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return url;
        }
    }

    private static final String[] DATES = {"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'"};

    private static long when(String s) {
        if (s == null) {
            return 0;
        }
        for (String f : DATES) {
            try {
                Date d = new SimpleDateFormat(f, Locale.US).parse(s.trim());
                if (d != null) {
                    return d.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    /** A story's picture (small), loaded once. */
    public static void image(final String url, final java.util.function.Consumer<Bitmap> done) {
        if (url == null) {
            return;
        }
        Bitmap known = IMAGES.get(url);
        if (known != null) {
            done.accept(known);
            return;
        }
        IO.execute(() -> {
            Bitmap b = null;
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(10000);
                con.setRequestProperty("User-Agent", "Mozilla/5.0 PocketNexus");
                try (InputStream in = con.getInputStream()) {
                    byte[] data = readAll(in, 6 * 1024 * 1024);
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    int sample = 1;
                    while (o.outWidth / (sample * 2) >= 480) {
                        sample *= 2;
                    }
                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize = sample;
                    b = BitmapFactory.decodeByteArray(data, 0, data.length, o2);
                } finally {
                    con.disconnect();
                }
            } catch (Exception | OutOfMemoryError ignored) {
            }
            final Bitmap got = b;
            if (got != null) {
                IMAGES.put(url, got);
                MAIN.post(() -> done.accept(got));
            }
        });
    }

    private static byte[] readAll(InputStream in, int max) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int r;
        while ((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
            if (out.size() > max) {
                break;
            }
        }
        return out.toByteArray();
    }
}
