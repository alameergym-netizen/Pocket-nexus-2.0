package com.limelight.pocket.info;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.PocketFonts;

/**
 * A YouTube video (a trailer, or gameplay for a game too old to have one) played here, full
 * screen, with the controller: YouTube's own embedded player in a page of ours, so there's no
 * need to leave for the YouTube app. A pauses, left / right go back and forward 10 seconds
 * (L1 / R1: 30), X mutes, Y opens it in YouTube, B closes. A video whose owner doesn't allow it
 * to play outside YouTube says so and offers YouTube.
 */
public class VideoPlayerActivity extends Activity {

    private static final String EXTRA_ID = "video_id";
    private static final String EXTRA_TITLE = "video_title";

    /** Plays a YouTube video here (falls back to the YouTube app when this can't). */
    public static void open(Context context, String youtubeId, String title) {
        if (youtubeId == null || youtubeId.isEmpty()) {
            return;
        }
        try {
            context.startActivity(new Intent(context, VideoPlayerActivity.class)
                    .putExtra(EXTRA_ID, youtubeId)
                    .putExtra(EXTRA_TITLE, title));
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
    private TextView hints;
    private TextView message;
    private String id;
    private final Runnable fadeHints = () -> {
        if (hints != null) {
            hints.animate().alpha(0f).setDuration(400).start();
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null || !id.matches("[A-Za-z0-9_-]{6,20}")) {
            finish();
            return;
        }
        ConsoleActivity.applyFullscreenWindow(this);
        float d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                // A link inside the player (its title, "Watch on YouTube"): YouTube, not here
                Uri u = request.getUrl();
                if (u != null && request.isForMainFrame()) {
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

        message = new TextView(this);
        message.setTextColor(0xFFFFFFFF);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        message.setTypeface(PocketFonts.medium());
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(0, 1.2f);
        message.setPadding(Math.round(40 * d), 0, Math.round(40 * d), 0);
        message.setVisibility(View.GONE);
        root.addView(message, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        hints = new TextView(this);
        hints.setTextColor(0xE6FFFFFF);
        hints.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        hints.setTypeface(PocketFonts.medium());
        hints.setText(getString(R.string.pocket_video_hints));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xB3000000);
        pill.setCornerRadius(18 * d);
        hints.setBackground(pill);
        hints.setPadding(Math.round(16 * d), Math.round(8 * d), Math.round(16 * d), Math.round(8 * d));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hlp.bottomMargin = Math.round(18 * d);
        root.addView(hints, hlp);
        setContentView(root);

        // YouTube's player wants a page from a web address (it checks where it's shown)
        web.loadDataWithBaseURL("https://www.youtube.com", page(id), "text/html", "utf-8", null);
        showHints();
    }

    private static String page(String id) {
        return "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}"
                + "#p{position:absolute;top:0;left:0;width:100%;height:100%}</style></head><body><div id='p'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var player;function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',"
                + "videoId:'" + id + "',playerVars:{autoplay:1,controls:1,rel:0,playsinline:1,fs:0,iv_load_policy:3,"
                + "origin:'https://www.youtube.com'},events:{onReady:function(e){e.target.playVideo();},"
                + "onError:function(e){Pocket.error(e.data);}}});}"
                + "function toggle(){if(!player||!player.getPlayerState)return;"
                + "if(player.getPlayerState()==1)player.pauseVideo();else player.playVideo();}"
                + "function seek(d){if(!player||!player.getCurrentTime)return;"
                + "player.seekTo(Math.max(0,player.getCurrentTime()+d),true);}"
                + "function mute(){if(!player||!player.isMuted)return;if(player.isMuted())player.unMute();else player.mute();}"
                + "function pause(){if(player&&player.pauseVideo)player.pauseVideo();}"
                + "</script></body></html>";
    }

    private final class Bridge {
        @JavascriptInterface
        public void error(final int code) {
            main.post(() -> {
                if (isFinishing()) {
                    return;
                }
                // 101 / 150 / 152 / 153: its owner keeps it on YouTube; others: gone or broken
                boolean embedOff = code == 101 || code == 150 || code == 152 || code == 153;
                message.setText(getString(embedOff ? R.string.pocket_video_not_here : R.string.pocket_video_failed));
                message.setVisibility(View.VISIBLE);
                web.setVisibility(View.INVISIBLE);
                showHints();
            });
        }
    }

    private void js(String call) {
        if (web != null) {
            web.evaluateJavascript(call, null);
        }
    }

    private void showHints() {
        if (hints == null) {
            return;
        }
        main.removeCallbacks(fadeHints);
        hints.animate().cancel();
        hints.setAlpha(1f);
        main.postDelayed(fadeHints, 3500);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            // The release of a button handled here goes nowhere else
            return isOurs(code) || super.dispatchKeyEvent(event);
        }
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (event.getRepeatCount() == 0) {
                    js("toggle()");
                    showHints();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                js("seek(-10)");
                showHints();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                js("seek(10)");
                showHints();
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                js("seek(-30)");
                showHints();
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                js("seek(30)");
                showHints();
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (event.getRepeatCount() == 0) {
                    js("mute()");
                    showHints();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (event.getRepeatCount() == 0) {
                    js("pause()");
                    openInYouTube(this, id);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (event.getRepeatCount() == 0) {
                    PocketSounds.play(this, PocketSounds.BACK);
                    finish();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                showHints();
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
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
}
