package com.limelight.pocket.info;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketIcons;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.RemoteImages;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A YouTube video (a trailer, or gameplay for a game too old to have one) played here, full
 * screen, with the controller and Pocket Nexus's own controls drawn over YouTube's player (its
 * title, a progress bar, play / pause and seek feedback). Many uploads of a trailer exist: when
 * the one picked won't play outside YouTube, the next one does, by itself (and more are looked
 * up when those run out); only when none will is YouTube offered.
 *
 * YouTube's embedded player wants to know which page it's in (the "Video player configuration
 * error" otherwise), so the page is served from Android's own address for an app's local pages
 * (appassets.androidplatform.net), a real https origin that's given to the player as such.
 */
public class VideoPlayerActivity extends Activity {

    private static final String EXTRA_ID = "video_id";
    private static final String EXTRA_IDS = "video_ids";
    private static final String EXTRA_TITLE = "video_title";
    private static final String EXTRA_QUERY = "video_query";

    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String PAGE = ORIGIN + "/pocket/player.html";

    /** Plays a YouTube video here (falls back to the YouTube app when this can't). */
    public static void open(Context context, String youtubeId, String title) {
        open(context, youtubeId, title, null);
    }

    /**
     * Plays a YouTube video here; query (what to search YouTube for, "Halo 3 official trailer")
     * finds other uploads of it when this one only plays on YouTube.
     */
    public static void open(Context context, String youtubeId, String title, String query) {
        if (youtubeId == null || youtubeId.isEmpty()) {
            return;
        }
        try {
            context.startActivity(new Intent(context, VideoPlayerActivity.class)
                    .putExtra(EXTRA_ID, youtubeId)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_QUERY, query));
        } catch (RuntimeException e) {
            openInYouTube(context, youtubeId);
        }
    }

    static void openInYouTube(Context context, String id) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(context, R.string.pocket_page_no_youtube, Toast.LENGTH_SHORT).show();
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private ImageView poster;
    private Chrome chrome;
    private LinearLayout hintBar;
    private String title;
    private String query;
    private final List<String> ids = new ArrayList<>();
    private final Set<String> tried = new HashSet<>();
    private int index;
    private boolean nocookie;
    private boolean searched;
    private boolean revealed;
    private boolean closing;
    private int viewportWidth;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String first = getIntent().getStringExtra(EXTRA_ID);
        if (first == null || !validId(first)) {
            finish();
            return;
        }
        ids.add(first);
        ArrayList<String> more = getIntent().getStringArrayListExtra(EXTRA_IDS);
        if (more != null) {
            for (String m : more) {
                if (validId(m) && !ids.contains(m)) {
                    ids.add(m);
                }
            }
        }
        title = getIntent().getStringExtra(EXTRA_TITLE);
        query = getIntent().getStringExtra(EXTRA_QUERY);
        ConsoleActivity.applyFullscreenWindow(this);
        float d = getResources().getDisplayMetrics().density;
        // The page is laid out at the screen's real width, so YouTube picks a stream as sharp as it
        viewportWidth = Math.max(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // The video's own picture (darkened) while it loads: never a black screen
        poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setAlpha(0f);
        poster.setColorFilter(0x99000000);
        root.addView(poster, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setAlpha(0f);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && "appassets.androidplatform.net".equals(u.getHost())
                        && "/pocket/player.html".equals(u.getPath())) {
                    String id = u.getQueryParameter("v");
                    boolean nc = "1".equals(u.getQueryParameter("nc"));
                    if (id == null || !validId(id)) {
                        return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null,
                                new ByteArrayInputStream(new byte[0]));
                    }
                    byte[] html = page(id, nc, viewportWidth).getBytes(StandardCharsets.UTF_8);
                    return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream(html));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // A link inside the player (its title, "Watch on YouTube"): YouTube, not here
                Uri u = request.getUrl();
                if (u != null && request.isForMainFrame() && !"appassets.androidplatform.net".equals(u.getHost())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (RuntimeException ignored) {
                    }
                    return true;
                }
                return false;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Pocket");
        web.setFocusable(false);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        chrome = new Chrome(this, title);
        root.addView(chrome, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hintBar = new LinearLayout(this);
        hintBar.setOrientation(LinearLayout.HORIZONTAL);
        hintBar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        hlp.bottomMargin = Math.round(22 * d);
        hlp.setMarginEnd(Math.round(40 * d));
        root.addView(hintBar, hlp);
        fillHints(false);
        setContentView(root);

        chrome.setStatus(getString(R.string.pocket_video_loading), true);
        load();
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{6,20}");
    }

    private void fillHints(boolean ended) {
        ConsoleActivity.fillHintBar(this, hintBar,
                ConsoleActivity.hintA(this, ended ? R.string.pocket_video_replay : R.string.pocket_video_play_pause),
                new ConsoleActivity.Hint("◀▶", 0xFFFFFFFF, getString(R.string.pocket_video_seek)),
                ConsoleActivity.hintX(this, R.string.pocket_video_mute),
                ConsoleActivity.hintY(this, R.string.pocket_video_youtube),
                ConsoleActivity.hintB(this, R.string.pocket_video_close));
    }

    /** Plays ids[index] (the page is made afresh: each try gets a clean player). */
    private void load() {
        if (web == null || index >= ids.size()) {
            return;
        }
        String id = ids.get(index);
        tried.add(id);
        revealed = false;
        web.animate().cancel();
        web.setAlpha(0f);
        // Its picture first, while YouTube's player starts
        final int w = viewportWidth;
        RemoteImages.load(this, "https://i.ytimg.com/vi/" + id + "/maxresdefault.jpg", w, bitmap -> {
            if (bitmap == null || isFinishing()) {
                return;
            }
            showPoster(bitmap);
        });
        web.loadUrl(PAGE + "?v=" + id + (nocookie ? "&nc=1" : ""));
    }

    private void showPoster(Bitmap b) {
        poster.setImageBitmap(b);
        poster.animate().alpha(1f).setDuration(320).start();
    }

    private static String page(String id, boolean nocookie, int width) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=" + width + ", user-scalable=no'>"
                + "<meta name='referrer' content='strict-origin-when-cross-origin'>"
                + "<style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}"
                + "#p{position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none}</style></head>"
                + "<body><div id='p'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var player,timer;function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',"
                + (nocookie ? "host:'https://www.youtube-nocookie.com'," : "")
                + "videoId:'" + id + "',playerVars:{autoplay:1,controls:0,disablekb:1,rel:0,playsinline:1,fs:0,"
                + "iv_load_policy:3,cc_load_policy:0,origin:'" + ORIGIN + "',widget_referrer:'" + ORIGIN + "'},"
                + "events:{onReady:function(e){e.target.playVideo();timer=setInterval(report,250);},"
                + "onStateChange:function(e){report();},"
                + "onError:function(e){Pocket.error(e.data);}}});}"
                + "function report(){if(!player||!player.getCurrentTime)return;"
                + "Pocket.state(player.getCurrentTime()||0,player.getDuration()||0,player.getPlayerState(),"
                + "player.getVideoLoadedFraction?player.getVideoLoadedFraction():0,player.isMuted?player.isMuted():false);}"
                + "function toggle(){if(!player||!player.getPlayerState)return;"
                + "var s=player.getPlayerState();if(s==1||s==3)player.pauseVideo();"
                + "else{if(s==0)player.seekTo(0,true);player.playVideo();}}"
                + "function seek(d){if(!player||!player.getCurrentTime)return;"
                + "player.seekTo(Math.max(0,Math.min(player.getDuration()-1,player.getCurrentTime()+d)),true);report();}"
                + "function mute(){if(!player||!player.isMuted)return;if(player.isMuted())player.unMute();else player.mute();"
                + "setTimeout(report,60);}"
                + "function pause(){if(player&&player.pauseVideo)player.pauseVideo();}"
                + "</script></body></html>";
    }

    private final class Bridge {
        @JavascriptInterface
        public void error(final int code) {
            main.post(() -> onVideoError(code));
        }

        @JavascriptInterface
        public void state(final double time, final double duration, final int state, final double loaded,
                          final boolean muted) {
            main.post(() -> {
                if (isFinishing() || chrome == null) {
                    return;
                }
                chrome.update((float) time, (float) duration, state, (float) loaded, muted);
                if ((state == 1 || state == 2) && !revealed && time > 0.05) {
                    reveal();
                }
                if (state == 0) {
                    fillHints(true);
                    chrome.showGlyph(Chrome.GLYPH_REPLAY);
                }
            });
        }
    }

    /** It's playing: YouTube's picture fades in over the poster, the status goes. */
    private void reveal() {
        revealed = true;
        chrome.setStatus(null, false);
        web.animate().alpha(1f).setDuration(420).start();
        poster.animate().alpha(0f).setStartDelay(420).setDuration(200).start();
        chrome.poke();
    }

    /**
     * A video that won't play: its owner keeps it on YouTube (101 / 150), the page wasn't
     * accepted (152 / 153: tried again from YouTube's other address), or it's gone (100) or broken
     * (2, 5). The next upload is tried; more are looked up when there are none left.
     */
    private void onVideoError(int code) {
        if (isFinishing() || web == null) {
            return;
        }
        boolean config = code == 152 || code == 153;
        if (config && !nocookie) {
            nocookie = true;
            load();
            return;
        }
        index++;
        if (index < ids.size()) {
            chrome.setStatus(getString(R.string.pocket_video_trying_another), true);
            load();
            return;
        }
        if (!searched && query != null && !query.isEmpty()) {
            searched = true;
            chrome.setStatus(getString(R.string.pocket_video_looking_more), true);
            final String q = query;
            new Thread(() -> {
                final List<String> found = GameInfoService.youtubeSearch(q, 10);
                main.post(() -> {
                    if (isFinishing() || web == null) {
                        return;
                    }
                    for (String f : found) {
                        if (!tried.contains(f) && !ids.contains(f)) {
                            ids.add(f);
                        }
                    }
                    if (index < ids.size()) {
                        load();
                    }
                    else {
                        giveUp(true);
                    }
                });
            }, "pocket-yt-more").start();
            return;
        }
        giveUp(code == 101 || code == 150 || config);
    }

    private void giveUp(boolean youtubeOnly) {
        index = Math.max(0, Math.min(index, ids.size() - 1));
        web.setAlpha(0f);
        chrome.setStatus(getString(youtubeOnly ? R.string.pocket_video_not_here : R.string.pocket_video_failed), false);
        PocketSounds.play(this, PocketSounds.ERROR);
    }

    private String currentId() {
        return ids.get(Math.max(0, Math.min(index, ids.size() - 1)));
    }

    private void js(String call) {
        if (web != null) {
            web.evaluateJavascript(call, null);
        }
    }

    private void close() {
        if (closing) {
            return;
        }
        closing = true;
        js("pause()");
        PocketSounds.play(this, PocketSounds.BACK);
        View root = (View) chrome.getParent();
        root.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            finish();
            overridePendingTransition(0, android.R.anim.fade_out);
        }).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            // The release of a button handled here goes nowhere else
            return isOurs(code) || super.dispatchKeyEvent(event);
        }
        boolean first = event.getRepeatCount() == 0;
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (first) {
                    js("toggle()");
                    chrome.showGlyph(chrome.isPlaying() ? Chrome.GLYPH_PAUSE : Chrome.GLYPH_PLAY);
                    fillHints(false);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(-10);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(10);
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                seek(-30);
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                seek(30);
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (first) {
                    js("mute()");
                    chrome.showGlyph(chrome.isMuted() ? Chrome.GLYPH_SOUND : Chrome.GLYPH_MUTE);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (first) {
                    js("pause()");
                    PocketSounds.play(this, PocketSounds.SELECT);
                    openInYouTube(this, currentId());
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (first) {
                    close();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                chrome.poke();
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void seek(int seconds) {
        js("seek(" + seconds + ")");
        chrome.showSeek(seconds);
        PocketSounds.play(this, PocketSounds.NAV);
    }

    private static boolean isOurs(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        js("pause()");
        if (web != null) {
            web.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
        }
        ConsoleActivity.applyFullscreenWindow(this);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    // ---- The controls drawn over the video ---------------------------------------------------

    /**
     * Title at the top, a progress bar with the times at the bottom, a big play / pause / seek
     * sign in the middle when pressed, and a spinner with a line of status while a video loads.
     * They show on any press and fade a few seconds later while it plays.
     */
    static final class Chrome extends View {
        static final int GLYPH_PLAY = 1;
        static final int GLYPH_PAUSE = 2;
        static final int GLYPH_MUTE = 3;
        static final int GLYPH_SOUND = 4;
        static final int GLYPH_REPLAY = 5;

        private final float d;
        private final int accent;
        private final String title;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint smallPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint statusPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        private float time;
        private float duration;
        private int state = -1;
        private float loaded;
        private boolean muted;
        private String status;
        private boolean spinning;

        /** How visible the controls are (0..1), and where they're heading. */
        private float shown = 1f;
        private float shownTarget = 1f;
        private long lastPoke = SystemClock.uptimeMillis();
        private long lastFrame;

        private int glyph;
        private long glyphAt;
        private int seekTotal;
        private long seekAt;

        Chrome(Context c, String title) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            accent = PocketTheme.accent(c);
            this.title = title != null ? title : "";
            titlePaint.setColor(0xFFFFFFFF);
            titlePaint.setTypeface(PocketFonts.display());
            titlePaint.setTextSize(24 * d);
            smallPaint.setColor(0xCCFFFFFF);
            smallPaint.setTypeface(PocketFonts.medium());
            smallPaint.setTextSize(12.5f * d);
            statusPaint.setColor(0xF2FFFFFF);
            statusPaint.setTypeface(PocketFonts.medium());
            statusPaint.setTextSize(16 * d);
            statusPaint.setTextAlign(Paint.Align.CENTER);
        }

        boolean isPlaying() {
            return state == 1 || state == 3;
        }

        boolean isMuted() {
            return muted;
        }

        void update(float time, float duration, int state, float loaded, boolean muted) {
            boolean changed = state != this.state;
            this.time = time;
            this.duration = duration;
            this.state = state;
            this.loaded = loaded;
            this.muted = muted;
            if (changed) {
                poke();
            }
            invalidate();
        }

        void setStatus(String text, boolean spin) {
            status = text;
            spinning = spin;
            poke();
        }

        /** Something was pressed: the controls show (and fade again later while it plays). */
        void poke() {
            lastPoke = SystemClock.uptimeMillis();
            shownTarget = 1f;
            postInvalidateOnAnimation();
        }

        void showGlyph(int g) {
            glyph = g;
            glyphAt = SystemClock.uptimeMillis();
            poke();
        }

        void showSeek(int seconds) {
            long now = SystemClock.uptimeMillis();
            // Pressed again quickly: one bubble adds up ("+30 s")
            if (now - seekAt < 900 && Integer.signum(seconds) == Integer.signum(seekTotal)) {
                seekTotal += seconds;
            }
            else {
                seekTotal = seconds;
            }
            seekAt = now;
            poke();
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            boolean playing = state == 1;
            boolean idle = playing && status == null && now - lastPoke > 3200;
            shownTarget = idle ? 0f : 1f;
            shown += (shownTarget - shown) * Math.min(1f, dt * (shownTarget > shown ? 14f : 5f));
            if (Math.abs(shown - shownTarget) < 0.004f) {
                shown = shownTarget;
            }
            int w = getWidth();
            int h = getHeight();
            boolean animating = shown != shownTarget || spinning;

            if (shown > 0.01f) {
                int a = Math.round(255 * shown);
                // Shade top and bottom, so the text reads over any picture
                paint.setShader(new LinearGradient(0, 0, 0, 150 * d, Color.argb(Math.round(170 * shown), 0, 0, 0),
                        0, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, 150 * d, paint);
                paint.setShader(new LinearGradient(0, h - 190 * d, 0, h, 0,
                        Color.argb(Math.round(200 * shown), 0, 0, 0), Shader.TileMode.CLAMP));
                c.drawRect(0, h - 190 * d, w, h, paint);
                paint.setShader(null);

                float left = 40 * d;
                titlePaint.setAlpha(a);
                CharSequence t = TextUtils.ellipsize(title, titlePaint, w - 2 * left, TextUtils.TruncateAt.END);
                c.drawText(t, 0, t.length(), left, 52 * d, titlePaint);
                smallPaint.setAlpha(Math.round(a * 0.75f));
                c.drawText("YouTube", left, 74 * d, smallPaint);

                // The bar: what's loaded, what's played, the playhead
                float barY = h - 74 * d;
                float right = w - 40 * d;
                float barH = 4 * d;
                paint.setColor(Color.argb(Math.round(64 * shown), 255, 255, 255));
                rect.set(left, barY - barH / 2, right, barY + barH / 2);
                c.drawRoundRect(rect, barH, barH, paint);
                if (duration > 0) {
                    float lf = Math.max(0, Math.min(1, loaded));
                    paint.setColor(Color.argb(Math.round(90 * shown), 255, 255, 255));
                    rect.set(left, barY - barH / 2, left + (right - left) * lf, barY + barH / 2);
                    c.drawRoundRect(rect, barH, barH, paint);
                    float pf = Math.max(0, Math.min(1, time / duration));
                    float x = left + (right - left) * pf;
                    paint.setColor(PocketTheme.withAlpha(accent, a));
                    rect.set(left, barY - barH / 2, x, barY + barH / 2);
                    c.drawRoundRect(rect, barH, barH, paint);
                    paint.setColor(Color.argb(a, 255, 255, 255));
                    c.drawCircle(x, barY, 7 * d, paint);
                    smallPaint.setAlpha(Math.round(a * 0.9f));
                    smallPaint.setTextAlign(Paint.Align.LEFT);
                    c.drawText(clock(time), left, barY + 26 * d, smallPaint);
                    smallPaint.setTextAlign(Paint.Align.RIGHT);
                    // (the hints sit at the bottom right; the length goes above the bar's end)
                    c.drawText("-" + clock(Math.max(0, duration - time)), right, barY - 14 * d, smallPaint);
                    smallPaint.setTextAlign(Paint.Align.LEFT);
                }
            }

            // The sign in the middle (pressed A / X, or the end)
            float cx = w / 2f;
            float cy = h / 2f;
            if (glyph != 0) {
                float t = (now - glyphAt) / 650f;
                boolean stays = glyph == GLYPH_REPLAY && state == 0;
                if (t < 1f || stays) {
                    float k = Math.min(1f, t);
                    float alpha = stays ? 1f : 1f - k * k;
                    float scale = 0.82f + 0.3f * (1f - (1f - Math.min(1f, k * 1.6f)) * (1f - Math.min(1f, k * 1.6f)));
                    float r = 46 * d * (stays ? 1f : scale);
                    paint.setColor(Color.argb(Math.round(150 * alpha), 0, 0, 0));
                    c.drawCircle(cx, cy, r, paint);
                    int col = Color.argb(Math.round(255 * alpha), 255, 255, 255);
                    drawGlyph(c, glyph, cx, cy, r * 0.42f, col);
                    animating |= !stays;
                }
                else {
                    glyph = 0;
                }
            }

            // Seeking: a bubble on its side, "+30 s"
            if (seekTotal != 0) {
                float t = (now - seekAt) / 800f;
                if (t < 1f) {
                    float alpha = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f;
                    float sx = seekTotal > 0 ? w * 0.78f : w * 0.22f;
                    float rr = 48 * d;
                    paint.setColor(Color.argb(Math.round(120 * alpha), 0, 0, 0));
                    c.drawCircle(sx, cy, rr, paint);
                    int col = Color.argb(Math.round(255 * alpha), 255, 255, 255);
                    paint.setColor(col);
                    // Chevrons pointing the way it went
                    float dir = Math.signum(seekTotal);
                    for (int i = 0; i < 2; i++) {
                        float ox = sx + dir * (i * 10 * d - 6 * d) - dir * 4 * d;
                        path.reset();
                        path.moveTo(ox - dir * 5 * d, cy - 22 * d);
                        path.lineTo(ox + dir * 5 * d, cy - 14 * d);
                        path.lineTo(ox - dir * 5 * d, cy - 6 * d);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.6f * d);
                        paint.setStrokeCap(Paint.Cap.ROUND);
                        paint.setStrokeJoin(Paint.Join.ROUND);
                        c.drawPath(path, paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                    statusPaint.setColor(col);
                    c.drawText((seekTotal > 0 ? "+" : "−") + Math.abs(seekTotal) + " s", sx, cy + 22 * d, statusPaint);
                    statusPaint.setColor(0xF2FFFFFF);
                    animating = true;
                }
                else {
                    seekTotal = 0;
                }
            }

            // Loading: a spinner and what's happening
            if (status != null) {
                float y = cy;
                if (spinning) {
                    float r = 22 * d;
                    float spin = (now % 900) / 900f * 360f;
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3.2f * d);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setColor(0x33FFFFFF);
                    c.drawCircle(cx, cy - 20 * d, r, paint);
                    paint.setColor(accent);
                    rect.set(cx - r, cy - 20 * d - r, cx + r, cy - 20 * d + r);
                    c.drawArc(rect, spin, 100f, false, paint);
                    paint.setStyle(Paint.Style.FILL);
                    y = cy + 34 * d;
                }
                String[] lines = status.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    c.drawText(lines[i], cx, y + i * 24 * d, statusPaint);
                }
            }
            if (animating || shown > 0f && shownTarget == 0f || playing && !idle) {
                postInvalidateOnAnimation();
            }
        }

        private void drawGlyph(Canvas c, int g, float cx, float cy, float s, int color) {
            paint.setColor(color);
            switch (g) {
                case GLYPH_PLAY:
                    path.reset();
                    path.moveTo(cx - s * 0.55f, cy - s * 0.85f);
                    path.lineTo(cx + s * 0.9f, cy);
                    path.lineTo(cx - s * 0.55f, cy + s * 0.85f);
                    path.close();
                    c.drawPath(path, paint);
                    break;
                case GLYPH_PAUSE:
                    rect.set(cx - s * 0.65f, cy - s * 0.8f, cx - s * 0.18f, cy + s * 0.8f);
                    c.drawRoundRect(rect, s * 0.12f, s * 0.12f, paint);
                    rect.set(cx + s * 0.18f, cy - s * 0.8f, cx + s * 0.65f, cy + s * 0.8f);
                    c.drawRoundRect(rect, s * 0.12f, s * 0.12f, paint);
                    break;
                case GLYPH_MUTE:
                    PocketIcons.draw(c, "MUTE", cx, cy, s * 2f, color, paint);
                    break;
                case GLYPH_SOUND:
                    PocketIcons.draw(c, "VOLUME_UP", cx, cy, s * 2f, color, paint);
                    break;
                case GLYPH_REPLAY:
                    PocketIcons.draw(c, "RESTART", cx, cy, s * 2f, color, paint);
                    break;
                default:
                    break;
            }
        }

        private static String clock(float seconds) {
            int s = Math.round(seconds);
            int h = s / 3600;
            int m = (s / 60) % 60;
            s = s % 60;
            return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                    : String.format(Locale.US, "%d:%02d", m, s);
        }
    }
}
