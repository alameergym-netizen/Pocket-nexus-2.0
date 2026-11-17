package com.limelight.pocket.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.ConsoleScreen;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.List;

/**
 * A web browser for the controller: a pointer on the page moved with the left stick (or the
 * D-pad), A clicks, the right stick scrolls, Y types an address or a search, L1 / R1 go back
 * and forward, L2 / R2 zoom, Start has the menu (bookmarks, home, desktop site...), B goes
 * back. The start page has the bookmarks as tiles.
 */
public class BrowserActivity extends Activity {

    public static final String EXTRA_URL = "url";
    private static final String KEY_BOOKMARKS = "pocket_browser_bookmarks";
    private static final String KEY_DESKTOP = "pocket_browser_desktop";
    private static final String HOME = "pocket://home";

    public static void open(Context c, String url) {
        try {
            Intent i = new Intent(c, BrowserActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (url != null) {
                i.putExtra(EXTRA_URL, url);
            }
            c.startActivity(i);
        } catch (RuntimeException ignored) {
        }
    }

    private static final String[][] DEFAULT_BOOKMARKS = {
            {"Google", "https://www.google.com"},
            {"YouTube", "https://m.youtube.com"},
            {"Wikipedia", "https://www.wikipedia.org"},
            {"Reddit", "https://www.reddit.com"},
            {"RetroAchievements", "https://retroachievements.org"},
            {"SteamGridDB", "https://www.steamgriddb.com"},
            {"Emulation Wiki", "https://emulation.gametechwiki.com"},
            {"GitHub", "https://github.com"},
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private Pointer pointer;
    private TextView address;
    private View progress;
    private LinearLayout hints;
    private StreamMenuView menu;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private FrameLayout root;
    private float px;
    private float py;
    private float stickX;
    private float stickY;
    private float scrollX;
    private float scrollY;
    private long lastFrame;
    private long lastProbe;
    private int dpadX;
    private int dpadY;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            float d = getResources().getDisplayMetrics().density;
            float vx = stickX + dpadX;
            float vy = stickY + dpadY;
            boolean moving = Math.abs(vx) > 0.01f || Math.abs(vy) > 0.01f;
            if (moving) {
                float mag = (float) Math.min(1.0, Math.hypot(vx, vy));
                // Over something to click, it slows a little (easier to land on)
                float speed = (220 + 900 * mag * mag) * d * (pointer.onTarget() ? 0.62f : 1f);
                movePointer(vx / Math.max(0.0001f, mag) * mag * speed * dt, vy / Math.max(0.0001f, mag) * mag * speed * dt);
            }
            if (Math.abs(scrollX) > 0.01f || Math.abs(scrollY) > 0.01f) {
                wheel(scrollX * 1.6f * dt * 60f, scrollY * 1.6f * dt * 60f);
            }
            if ((moving || Math.abs(scrollX) > 0.01f || Math.abs(scrollY) > 0.01f) && now - lastProbe > 90) {
                lastProbe = now;
                probe();
            }
            if (moving || Math.abs(scrollX) > 0.01f || Math.abs(scrollY) > 0.01f) {
                main.postDelayed(this, 16);
            }
            else {
                lastFrame = 0;
                // Settled: what's under it now, exactly
                main.postDelayed(BrowserActivity.this::probe, 40);
            }
        }
    };

    // ---- Screen -------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConsoleActivity.applyFullscreenWindow(this);
        float d = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        root.setBackground(ConsoleScreen.backdrop(this));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The address bar: the page's address in a glass pill, its loading line under it
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(8), dp(14), dp(6));
        TextView back = pill("PREV");
        back.setOnClickListener(v -> goBack());
        bar.addView(back);
        TextView fwd = pill("NEXT");
        fwd.setOnClickListener(v -> {
            if (web.canGoForward()) {
                web.goForward();
            }
        });
        bar.addView(fwd);
        TextView reload = pill("RESTART");
        reload.setOnClickListener(v -> web.reload());
        bar.addView(reload);
        address = ConsoleScreen.text(this, 13, 0xE6FFFFFF, true);
        address.setSingleLine(true);
        address.setEllipsize(TextUtils.TruncateAt.END);
        address.setPadding(dp(14), dp(7), dp(14), dp(7));
        android.graphics.drawable.GradientDrawable abg = new android.graphics.drawable.GradientDrawable();
        abg.setColor(0x1FFFFFFF);
        abg.setCornerRadius(dp(16));
        address.setBackground(abg);
        address.setOnClickListener(v -> askAddress());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        alp.setMarginStart(dp(8));
        bar.addView(address, alp);
        hints = ConsoleScreen.hintBar(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26));
        hlp.setMarginStart(dp(10));
        bar.addView(hints, hlp);
        ConsoleActivity.fillHintBar(this, hints,
                ConsoleActivity.hintY(this, R.string.pocket_browser_address),
                ConsoleActivity.hintStart(this, R.string.pocket_hint_options),
                ConsoleActivity.hintB(this, R.string.pocket_hint_back));
        column.addView(bar);
        progress = new View(this);
        progress.setBackgroundColor(PocketTheme.accent(this));
        progress.setPivotX(0);
        progress.setScaleX(0f);
        column.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, dp(2))));

        FrameLayout page = new FrameLayout(this);
        column.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        applyDesktop(PocketPrefs.prefs(this).getBoolean(KEY_DESKTOP, false));
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u != null ? u.getScheme() : null;
                if (scheme != null && !scheme.equals("http") && !scheme.equals("https")
                        && !scheme.equals("about") && !scheme.equals("data") && !scheme.equals("javascript")) {
                    // intent:, market:, mailto:... go to the app for them
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException ignored) {
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                showAddress(url, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                showAddress(url, view.getTitle());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.animate().cancel();
                if (p >= 100) {
                    progress.animate().scaleX(1f).alpha(0f).setDuration(220).start();
                }
                else {
                    progress.setAlpha(1f);
                    progress.animate().scaleX(p / 100f).setDuration(180).start();
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // A video full screen
                customView = view;
                customCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                pointer.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                leaveFullScreen();
            }
        });
        web.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> download(url,
                userAgent, contentDisposition, mimetype));
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        page.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pointer = new Pointer(this);
        page.addView(pointer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        menu = new StreamMenuView(this);
        menu.setConfirmLabel(getString(R.string.pocket_sm_confirm));
        menu.setPlacement(true, 336);
        menu.setBaseColor(0xF514141A);
        root.addView(menu, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        page.post(() -> {
            px = page.getWidth() / 2f;
            py = page.getHeight() / 3f;
            pointer.invalidate();
        });
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        }
        else {
            load(url != null ? url : HOME);
        }
    }

    private TextView pill(String icon) {
        TextView t = ConsoleScreen.text(this, 14, 0xE6FFFFFF, true);
        t.setGravity(Gravity.CENTER);
        android.graphics.drawable.Drawable g = com.limelight.pocket.ui.PocketIcons.drawable(icon, dp(15), 0xE6FFFFFF);
        g.setBounds(0, 0, dp(15), dp(15));
        t.setCompoundDrawablesRelative(g, null, null, null);
        t.setPadding(dp(8), 0, 0, 0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x14FFFFFF);
        bg.setCornerRadius(dp(15));
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(30));
        lp.setMarginEnd(dp(6));
        t.setLayoutParams(lp);
        return t;
    }

    private int dp(float v) {
        return ConsoleScreen.dp(this, v);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) {
            web.saveState(out);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConsoleActivity.applyFullscreenWindow(this);
        if (web != null) {
            web.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            web.onPause();
        }
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    // ---- Pages --------------------------------------------------------------------------

    private void load(String url) {
        if (HOME.equals(url)) {
            web.loadDataWithBaseURL("https://pocket.home/", homePage(), "text/html", "utf-8", null);
            showAddress(HOME, getString(R.string.pocket_browser_home));
            return;
        }
        web.loadUrl(url);
    }

    private void showAddress(String url, String title) {
        if (url == null || url.startsWith("https://pocket.home") || HOME.equals(url)) {
            address.setText(getString(R.string.pocket_browser_home));
            return;
        }
        Uri u = Uri.parse(url);
        String host = u.getHost() != null ? u.getHost().replaceFirst("^www\\.", "") : url;
        address.setText(title != null && !title.isEmpty() && !title.equals(url) ? host + "  ·  " + title : url);
        // A lock for a secure page
        boolean secure = "https".equalsIgnoreCase(u.getScheme());
        android.graphics.drawable.Drawable lock = secure
                ? com.limelight.pocket.ui.PocketIcons.drawable("LOCK", dp(13), 0xB3FFFFFF) : null;
        if (lock != null) {
            lock.setBounds(0, 0, dp(13), dp(13));
        }
        address.setCompoundDrawablesRelative(lock, null, null, null);
        address.setCompoundDrawablePadding(dp(7));
    }

    /**
     * The start page: a greeting and the time, a search line (Y), and the bookmarks as big
     * tiles with the sites' own icons (the pointer clicks them; they light up under it).
     */
    private String homePage() {
        StringBuilder tiles = new StringBuilder();
        for (String[] b : bookmarks()) {
            String host = Uri.parse(b[1]).getHost();
            String letter = b[0].isEmpty() ? "?" : b[0].substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            String base = Uri.parse(b[1]).getScheme() + "://" + host;
            tiles.append("<a class='t' href='").append(TextUtils.htmlEncode(b[1])).append("'><div class='l' style='background:")
                    .append(color(host)).append("'><span>").append(TextUtils.htmlEncode(letter))
                    .append("</span><img src='").append(TextUtils.htmlEncode(base)).append("/favicon.ico' onerror=\"this.remove()\"")
                    .append(" onload=\"this.parentNode.classList.add('i')\"></div><div class='n'>")
                    .append(TextUtils.htmlEncode(b[0])).append("</div><div class='h'>")
                    .append(TextUtils.htmlEncode(host != null ? host.replaceFirst("^www\\.", "") : ""))
                    .append("</div></a>");
        }
        int accent = PocketTheme.accent(this) & 0xFFFFFF;
        String acc = String.format("%06X", accent);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        String hello = getString(hour < 12 ? R.string.pocket_browser_morning : hour < 18 ? R.string.pocket_browser_afternoon
                : R.string.pocket_browser_evening);
        String time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(cal.getTime());
        return "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>*{box-sizing:border-box}body{margin:0;color:#fff;font-family:sans-serif;padding:34px 44px;"
                + "background:radial-gradient(circle at 85% -10%,#" + acc + "33,transparent 55%),"
                + "radial-gradient(circle at 0% 110%,#ffffff10,transparent 45%),#08080b;min-height:100vh}"
                + ".top{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:22px}"
                + "h1{font-weight:700;font-size:30px;margin:0;letter-spacing:-.5px}.time{font-size:15px;color:#ffffffaa}"
                + ".s{display:flex;align-items:center;gap:12px;background:#ffffff12;border:1px solid #ffffff1c;border-radius:28px;"
                + "padding:14px 20px;color:#ffffff99;font-size:15px;margin-bottom:28px}"
                + ".k{background:#fff;color:#0b0b0e;font-weight:700;font-size:11px;border-radius:50%;width:22px;height:22px;"
                + "display:inline-flex;align-items:center;justify-content:center}"
                + ".g{display:grid;grid-template-columns:repeat(auto-fill,minmax(136px,1fr));gap:16px}"
                + ".t{text-decoration:none;color:#fff;background:linear-gradient(180deg,#ffffff14,#ffffff08);border-radius:18px;"
                + "padding:18px 10px 14px;text-align:center;border:1px solid #ffffff14;transition:transform .15s}"
                + ".t:hover,.t:focus{transform:scale(1.04);border-color:#" + acc + "}"
                + ".l{position:relative;width:56px;height:56px;border-radius:16px;margin:0 auto 12px;font-size:24px;font-weight:700;"
                + "line-height:56px;overflow:hidden;box-shadow:0 6px 18px #0006}.l img{position:absolute;inset:10px;width:36px;height:36px}"
                + ".l.i{background:#fff!important}.l.i span{display:none}"
                + ".n{font-size:13.5px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"
                + ".h{font-size:11px;color:#ffffff66;margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"
                + ".f{margin-top:30px;font-size:12px;color:#ffffff55}"
                + "</style></head><body><div class='top'><h1>" + TextUtils.htmlEncode(hello) + "</h1><div class='time'>"
                + TextUtils.htmlEncode(time) + "</div></div><div class='s'><span class='k'>Y</span>"
                + TextUtils.htmlEncode(getString(R.string.pocket_browser_search_line)) + "</div><div class='g'>" + tiles
                + "</div><div class='f'>" + TextUtils.htmlEncode(getString(R.string.pocket_browser_home_sub))
                + "</div></body></html>";
    }

    private static String color(String host) {
        String[] palette = {"#e53935", "#8e24aa", "#3949ab", "#039be5", "#00897b", "#7cb342", "#fb8c00", "#6d4c41"};
        int h = host != null ? Math.abs(host.hashCode()) : 0;
        return palette[h % palette.length];
    }

    private List<String[]> bookmarks() {
        String saved = PocketPrefs.prefs(this).getString(KEY_BOOKMARKS, null);
        List<String[]> out = new ArrayList<>();
        if (saved == null) {
            for (String[] b : DEFAULT_BOOKMARKS) {
                out.add(b);
            }
            return out;
        }
        for (String line : saved.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab > 0) {
                out.add(new String[]{line.substring(0, tab), line.substring(tab + 1)});
            }
        }
        return out;
    }

    private void saveBookmarks(List<String[]> list) {
        StringBuilder b = new StringBuilder();
        for (String[] e : list) {
            if (b.length() > 0) {
                b.append('\n');
            }
            b.append(e[0].replace('\t', ' ').replace('\n', ' ')).append('\t').append(e[1]);
        }
        PocketPrefs.prefs(this).edit().putString(KEY_BOOKMARKS, b.toString()).apply();
    }

    private boolean isBookmarked(String url) {
        for (String[] b : bookmarks()) {
            if (b[1].equals(url)) {
                return true;
            }
        }
        return false;
    }

    private void askAddress() {
        String now = web.getUrl();
        if (now != null && now.startsWith("https://pocket.home")) {
            now = "";
        }
        ConsoleScreen.prompt(this, getString(R.string.pocket_browser_address), now,
                getString(R.string.pocket_browser_address_hint), text -> {
                    if (text.isEmpty()) {
                        return;
                    }
                    load(toUrl(text));
                });
    }

    /** An address as typed ("example.com"), or a search for the words. */
    static String toUrl(String text) {
        String t = text.trim();
        if (t.startsWith("http://") || t.startsWith("https://")) {
            return t;
        }
        if (!t.contains(" ") && Patterns.WEB_URL.matcher(t).matches()) {
            return "https://" + t;
        }
        return "https://www.google.com/search?q=" + Uri.encode(t);
    }

    private void applyDesktop(boolean on) {
        WebSettings s = web.getSettings();
        if (on) {
            s.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36");
        }
        else {
            s.setUserAgentString(null);
        }
    }

    private void download(String url, String userAgent, String disposition, String mime) {
        try {
            String name = URLUtil.guessFileName(url, disposition, mime);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mime)
                    .addRequestHeader("User-Agent", userAgent)
                    .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(r);
                Toast.makeText(this, getString(R.string.pocket_browser_downloading, name), Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.pocket_browser_cant_download, Toast.LENGTH_SHORT).show();
        }
    }

    private void leaveFullScreen() {
        if (customView != null) {
            root.removeView(customView);
            customView = null;
        }
        if (customCallback != null) {
            customCallback.onCustomViewHidden();
            customCallback = null;
        }
        pointer.setVisibility(View.VISIBLE);
    }

    private void goBack() {
        if (customView != null) {
            leaveFullScreen();
            return;
        }
        if (web.canGoBack()) {
            PocketSounds.play(this, PocketSounds.BACK);
            web.goBack();
        }
        else {
            PocketSounds.play(this, PocketSounds.CLOSE);
            finish();
        }
    }

    // ---- The menu -----------------------------------------------------------------------

    private void showMenu() {
        final String url = web.getUrl();
        final boolean real = url != null && !url.startsWith("https://pocket.home");
        List<StreamMenuView.Item> items = new ArrayList<>();
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_browser_home), () -> load(HOME)).withIcon("ANDROID_HOME"));
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_browser_address), this::askAddress).withIcon("KEYBOARD"));
        items.add(StreamMenuView.Item.submenu(getString(R.string.pocket_browser_bookmarks), () ->
                menu.push(getString(R.string.pocket_browser_bookmarks), bookmarkItems())));
        if (real) {
            final boolean saved = isBookmarked(url);
            items.add(StreamMenuView.Item.action(getString(saved ? R.string.pocket_browser_unbookmark
                    : R.string.pocket_browser_bookmark), () -> {
                List<String[]> list = bookmarks();
                if (saved) {
                    for (int i = list.size() - 1; i >= 0; i--) {
                        if (list.get(i)[1].equals(url)) {
                            list.remove(i);
                        }
                    }
                }
                else {
                    String title = web.getTitle() != null && !web.getTitle().isEmpty() ? web.getTitle() : Uri.parse(url).getHost();
                    list.add(new String[]{title, url});
                }
                saveBookmarks(list);
                PocketSounds.play(this, saved ? PocketSounds.TOGGLE_OFF : PocketSounds.TOGGLE_ON);
            }).withIcon("STAR"));
        }
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_browser_reload), () -> web.reload()).withIcon("RESTART"));
        final boolean desktop = PocketPrefs.prefs(this).getBoolean(KEY_DESKTOP, false);
        items.add(StreamMenuView.Item.value(getString(R.string.pocket_browser_desktop),
                getString(desktop ? R.string.pocket_on : R.string.pocket_off), direction -> {
                    boolean now = !PocketPrefs.prefs(this).getBoolean(KEY_DESKTOP, false);
                    PocketPrefs.prefs(this).edit().putBoolean(KEY_DESKTOP, now).apply();
                    applyDesktop(now);
                    web.reload();
                    return getString(now ? R.string.pocket_on : R.string.pocket_off);
                }));
        if (real) {
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_browser_share), () -> {
                try {
                    startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, url), null));
                } catch (RuntimeException ignored) {
                }
            }));
            items.add(StreamMenuView.Item.header(url));
        }
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_browser_close), this::finish));
        PocketSounds.play(this, PocketSounds.OPEN);
        menu.show(getString(R.string.pocket_browser_title), web.getTitle(), null, items);
    }

    private List<StreamMenuView.Item> bookmarkItems() {
        List<StreamMenuView.Item> items = new ArrayList<>();
        for (final String[] b : bookmarks()) {
            items.add(StreamMenuView.Item.action(b[0], () -> load(b[1])).withValue(Uri.parse(b[1]).getHost()));
        }
        if (items.isEmpty()) {
            items.add(StreamMenuView.Item.header(getString(R.string.pocket_browser_no_bookmarks)));
        }
        return items;
    }

    // ---- The pointer --------------------------------------------------------------------

    private void movePointer(float dx, float dy) {
        float w = pointer.getWidth();
        float h = pointer.getHeight();
        float edge = 24 * getResources().getDisplayMetrics().density;
        px += dx;
        py += dy;
        // At the top or the bottom the page scrolls under it
        if (py > h - edge && dy > 0) {
            wheel(0, dy * 0.06f);
        }
        else if (py < edge && dy < 0) {
            wheel(0, dy * 0.06f);
        }
        px = Math.max(0, Math.min(w - 1, px));
        py = Math.max(0, Math.min(h - 1, py));
        pointer.invalidate();
    }

    /** Scrolls what's under the pointer (a list in the page, or the page), like a mouse wheel. */
    private void wheel(float dx, float dy) {
        MotionEvent.PointerProperties[] props = {new MotionEvent.PointerProperties()};
        props[0].id = 0;
        props[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords()};
        coords[0].x = px;
        coords[0].y = py;
        coords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, -dy);
        coords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, dx);
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_MOUSE, 0);
        web.dispatchGenericMotionEvent(e);
        e.recycle();
    }

    private void click() {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, px, py, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(down);
        MotionEvent up = MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, px, py, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        pointer.press();
        web.requestFocus();
    }

    /** What's under the pointer that can be clicked (a link, a button, a box): lit around. */
    private void probe() {
        if (web == null || customView != null) {
            return;
        }
        @SuppressWarnings("deprecation")
        final float scale = Math.max(0.1f, web.getScale());
        float x = px / scale;
        float y = py / scale;
        String js = "(function(x,y){var e=document.elementFromPoint(x,y),n=0;while(e&&n<7){var t=e.tagName;"
                + "if(t=='A'||t=='BUTTON'||t=='INPUT'||t=='SELECT'||t=='TEXTAREA'||t=='LABEL'||t=='SUMMARY'||e.onclick"
                + "||(e.getAttribute&&(e.getAttribute('role')=='button'||e.getAttribute('role')=='link'"
                + "||e.getAttribute('role')=='tab'||e.getAttribute('onclick'))))break;e=e.parentElement;n++;}"
                + "if(!e||n>=7||e===document.body||e===document.documentElement)return '';"
                + "var r=e.getBoundingClientRect();if(r.width>window.innerWidth*0.9&&r.height>window.innerHeight*0.5)return '';"
                + "return r.left+','+r.top+','+r.width+','+r.height;})(" + x + "," + y + ")";
        try {
            web.evaluateJavascript(js, value -> {
                if (pointer == null) {
                    return;
                }
                String v = value != null ? value.replace("\"", "") : "";
                String[] p = v.split(",");
                if (p.length != 4) {
                    pointer.target(null);
                    return;
                }
                try {
                    float l = Float.parseFloat(p[0]) * scale;
                    float t = Float.parseFloat(p[1]) * scale;
                    float w = Float.parseFloat(p[2]) * scale;
                    float h = Float.parseFloat(p[3]) * scale;
                    pointer.target(new android.graphics.RectF(l, t, l + w, t + h));
                } catch (NumberFormatException e) {
                    pointer.target(null);
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    private void kick() {
        main.removeCallbacks(frame);
        main.post(frame);
    }

    /**
     * The pointer: an arrow with a soft ring that flashes on a click; what it's over that can be
     * clicked gets a glowing outline that glides from one thing to the next.
     */
    private final class Pointer extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private float ring;
        private final android.graphics.RectF box = new android.graphics.RectF();
        private android.graphics.RectF goal;
        private float boxAlpha;
        private android.animation.ValueAnimator boxAnim;

        boolean onTarget() {
            return goal != null && goal.contains(px, py);
        }

        /** The thing under the pointer (null: none): the outline moves to it. */
        void target(android.graphics.RectF r) {
            if (r == null && goal == null) {
                return;
            }
            if (r != null && goal != null && Math.abs(r.left - goal.left) < 1 && Math.abs(r.top - goal.top) < 1
                    && Math.abs(r.width() - goal.width()) < 1) {
                return;
            }
            final android.graphics.RectF from = new android.graphics.RectF(boxAlpha > 0.05f ? box : (r != null ? r : box));
            final float fromAlpha = boxAlpha;
            final android.graphics.RectF to = r != null ? r : new android.graphics.RectF(box);
            final float toAlpha = r != null ? 1f : 0f;
            goal = r;
            if (boxAnim != null) {
                boxAnim.cancel();
            }
            boxAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            boxAnim.setDuration(150);
            boxAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
            boxAnim.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                box.set(from.left + (to.left - from.left) * t, from.top + (to.top - from.top) * t,
                        from.right + (to.right - from.right) * t, from.bottom + (to.bottom - from.bottom) * t);
                boxAlpha = fromAlpha + (toAlpha - fromAlpha) * t;
                invalidate();
            });
            boxAnim.start();
        }

        Pointer(Context c) {
            super(c);
            fill.setColor(0xFFFFFFFF);
            edge.setColor(0xFF111114);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1.6f * c.getResources().getDisplayMetrics().density);
            edge.setStrokeJoin(Paint.Join.ROUND);
            setWillNotDraw(false);
        }

        void press() {
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(1f, 0f);
            a.setDuration(320);
            a.addUpdateListener(v -> {
                ring = (float) v.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            if (boxAlpha > 0.01f) {
                int accent = PocketTheme.accent(getContext());
                float pad = 4 * d;
                android.graphics.RectF r = new android.graphics.RectF(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad);
                lit.setStyle(Paint.Style.FILL);
                lit.setColor((accent & 0x00FFFFFF) | (Math.round(36 * boxAlpha) << 24));
                c.drawRoundRect(r, 9 * d, 9 * d, lit);
                lit.setStyle(Paint.Style.STROKE);
                lit.setStrokeWidth(2.2f * d);
                lit.setColor((accent & 0x00FFFFFF) | (Math.round(230 * boxAlpha) << 24));
                c.drawRoundRect(r, 9 * d, 9 * d, lit);
            }
            if (ring > 0) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor((PocketTheme.accent(getContext()) & 0x00FFFFFF) | (Math.round(140 * ring) << 24));
                c.drawCircle(px, py, (1.6f - ring) * 18 * d, p);
            }
            float s = d * 1.1f;
            arrow.reset();
            arrow.moveTo(px, py);
            arrow.lineTo(px, py + 18 * s);
            arrow.lineTo(px + 4.6f * s, py + 13.6f * s);
            arrow.lineTo(px + 8 * s, py + 20.6f * s);
            arrow.lineTo(px + 10.6f * s, py + 19.4f * s);
            arrow.lineTo(px + 7.2f * s, py + 12.6f * s);
            arrow.lineTo(px + 13 * s, py + 12.6f * s);
            arrow.close();
            c.drawPath(arrow, fill);
            c.drawPath(arrow, edge);
        }
    }

    // ---- Buttons ------------------------------------------------------------------------

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (menu.isShowing() || customView != null) {
            return super.dispatchGenericMotionEvent(ev);
        }
        if ((ev.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && ev.getAction() == MotionEvent.ACTION_MOVE) {
            stickX = dead(ev.getAxisValue(MotionEvent.AXIS_X));
            stickY = dead(ev.getAxisValue(MotionEvent.AXIS_Y));
            scrollX = dead(ev.getAxisValue(MotionEvent.AXIS_Z));
            scrollY = dead(ev.getAxisValue(MotionEvent.AXIS_RZ));
            float hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X);
            float hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y);
            dpadX = hx > 0.5f ? 1 : hx < -0.5f ? -1 : 0;
            dpadY = hy > 0.5f ? 1 : hy < -0.5f ? -1 : 0;
            kick();
            return true;
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    private static float dead(float v) {
        float a = Math.abs(v);
        if (a < 0.18f) {
            return 0f;
        }
        return Math.signum(v) * (a - 0.18f) / 0.82f;
    }

    /** True when the page is taking typing (a field has the focus and the keyboard is up). */
    private boolean typing() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        return imm != null && imm.isAcceptingText() && web.hasFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (menu.isShowing()) {
            menu.handleKey(event);
            return true;
        }
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        if (customView != null && (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK)) {
            if (up) {
                leaveFullScreen();
            }
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (typing()) {
                    return super.dispatchKeyEvent(event);
                }
                int vx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
                int vy = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
                if (down) {
                    if (vx != 0) {
                        dpadX = vx;
                    }
                    if (vy != 0) {
                        dpadY = vy;
                    }
                }
                else if (up) {
                    if (vx != 0) {
                        dpadX = 0;
                    }
                    if (vy != 0) {
                        dpadY = 0;
                    }
                }
                kick();
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (up) {
                    click();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
                if (up) {
                    goBack();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (up) {
                    askAddress();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (up) {
                    web.reload();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                if (up) {
                    goBack();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (up && web.canGoForward()) {
                    web.goForward();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (up) {
                    web.zoomOut();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (up) {
                    web.zoomIn();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                if (up) {
                    showMenu();
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }
}
