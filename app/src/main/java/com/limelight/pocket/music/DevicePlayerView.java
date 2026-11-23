package com.limelight.pocket.music;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.device.PocketNotifications;
import com.limelight.pocket.ui.FocusGlow;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketIcons;
import com.limelight.pocket.ui.PocketMotion;
import com.limelight.pocket.ui.Spring;

import java.util.ArrayList;
import java.util.List;

/**
 * Whatever is playing on the handheld, from any app (Spotify, YouTube Music, Anghami...):
 * its cover, its name, where it is in the song, and its controls (play, skip, seek, and the
 * app's own like / shuffle buttons), with the app opened in one press for anything more. Below,
 * the music apps on the handheld, to start one. Seen through the app's media session (the
 * notification access gives it).
 */
public final class DevicePlayerView extends FrameLayout {

    /** Told when the cover changes (for the colours behind) and when anything does (the hints). */
    public interface Host {
        void onArt(Bitmap art);

        void onChanged();
    }

    /** Music apps worth a button, the most likely first. */
    private static final String[][] APPS = {
            {"com.spotify.music", "Spotify"},
            {"com.google.android.apps.youtube.music", "YouTube Music"},
            {"com.anghami", "Anghami"},
            {"com.apple.android.music", "Apple Music"},
            {"deezer.android.app", "Deezer"},
            {"com.amazon.mp3", "Amazon Music"},
            {"com.aspiro.tidal", "TIDAL"},
            {"com.soundcloud.android", "SoundCloud"},
            {"com.pandora.android", "Pandora"},
            {"com.maxmpz.audioplayer", "Poweramp"},
            {"com.google.android.youtube", "YouTube"},
    };

    private final Host host;
    private final float d;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessions;
    private final ComponentName listener;

    private final ImageView art;
    private final ImageView sourceIcon;
    private final TextView sourceName;
    private final TextView title;
    private final TextView artist;
    private final TextView album;
    private final Bar bar;
    private final TextView elapsed;
    private final TextView remaining;
    private final LinearLayout controls;
    private final LinearLayout extras;
    private final View timeRow;
    private final ImageView prev;
    private final ImageView playPause;
    private final ImageView next;
    private final TextView openApp;
    private final LinearLayout appRow;
    private final TextView appsCaption;

    private final List<MediaController> list = new ArrayList<>();
    private MediaController current;
    private boolean started;
    private boolean noAccess;
    private Bitmap lastArt;
    private String lastArtKey;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            render(false);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            render(true);
        }

        @Override
        public void onSessionDestroyed() {
            main.post(DevicePlayerView.this::reload);
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = controllers -> main.post(this::reload);

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            progress();
            PlaybackState st = current != null ? current.getPlaybackState() : null;
            if (started && st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                main.postDelayed(this, 500);
            }
        }
    };

    public DevicePlayerView(Context c, Host host) {
        super(c);
        this.host = host;
        d = c.getResources().getDisplayMetrics().density;
        sessions = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(c, PocketNotifications.class);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(dp(6), dp(8), dp(6), dp(96));

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setClipChildren(false);
        top.setClipToPadding(false);
        column.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(212)));

        // The cover
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 18 * d);
            }
        });
        art.setClipToOutline(true);
        art.setElevation(dp(14));
        top.addView(art, new LinearLayout.LayoutParams(dp(204), dp(204)));

        // Its words, where it is, and the controls
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setClipChildren(false);
        words.setClipToPadding(false);
        words.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        wl.setMarginStart(dp(28));
        top.addView(words, wl);

        LinearLayout source = new LinearLayout(c);
        source.setOrientation(LinearLayout.HORIZONTAL);
        source.setGravity(Gravity.CENTER_VERTICAL);
        sourceIcon = new ImageView(c);
        source.addView(sourceIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));
        sourceName = text(c, 10f, 0xA6FFFFFF, PocketFonts.bold());
        sourceName.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.setMarginStart(dp(7));
        source.addView(sourceName, sl);
        words.addView(source);

        title = text(c, 25f, 0xFFFFFFFF, PocketFonts.display());
        title.setMaxLines(2);
        title.setLetterSpacing(-0.02f);
        title.setLineSpacing(0, 0.95f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(8);
        words.addView(title, tl);
        artist = text(c, 15f, 0xD9FFFFFF, PocketFonts.semibold());
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        al.topMargin = dp(5);
        words.addView(artist, al);
        album = text(c, 12f, 0x8CFFFFFF, PocketFonts.medium());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(2);
        words.addView(album, bl);

        // Where it is in the song: a bar you can move along (left / right) when it's chosen
        bar = new Bar(c);
        LinearLayout.LayoutParams barl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22));
        barl.topMargin = dp(12);
        words.addView(bar, barl);
        LinearLayout times = new LinearLayout(c);
        times.setOrientation(LinearLayout.HORIZONTAL);
        elapsed = text(c, 11f, 0xA6FFFFFF, PocketFonts.medium());
        remaining = text(c, 11f, 0xA6FFFFFF, PocketFonts.medium());
        remaining.setGravity(Gravity.END);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        times.addView(remaining, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        timeRow = times;
        words.addView(times);

        controls = new LinearLayout(c);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setClipChildren(false);
        controls.setClipToPadding(false);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        cl.topMargin = dp(8);
        words.addView(controls, cl);
        prev = round(c, "PREV", 44, false);
        playPause = round(c, "PLAY", 56, true);
        next = round(c, "NEXT", 44, false);
        prev.setOnClickListener(v -> {
            FocusGlow.press(v);
            skip(-1);
        });
        playPause.setOnClickListener(v -> {
            FocusGlow.press(v);
            togglePlay();
        });
        next.setOnClickListener(v -> {
            FocusGlow.press(v);
            skip(1);
        });
        controls.addView(prev, roundLp(44, 0));
        controls.addView(playPause, roundLp(56, 14));
        controls.addView(next, roundLp(44, 14));
        extras = new LinearLayout(c);
        extras.setOrientation(LinearLayout.HORIZONTAL);
        extras.setGravity(Gravity.CENTER_VERTICAL);
        extras.setClipChildren(false);
        extras.setClipToPadding(false);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(60));
        xl.setMarginStart(dp(4));
        controls.addView(extras, xl);
        View gap = new View(c);
        controls.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        openApp = pill(c);
        openApp.setOnClickListener(v -> {
            FocusGlow.press(v);
            openCurrent();
        });
        controls.addView(openApp, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        // The music apps on the handheld
        appsCaption = text(c, 10f, 0x8CFFFFFF, PocketFonts.bold());
        appsCaption.setLetterSpacing(0.14f);
        appsCaption.setText(c.getString(R.string.pocket_device_apps).toUpperCase(java.util.Locale.ROOT));
        LinearLayout.LayoutParams acl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        acl.topMargin = dp(22);
        column.addView(appsCaption, acl);
        appRow = new LinearLayout(c);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setGravity(Gravity.CENTER_VERTICAL);
        appRow.setClipChildren(false);
        appRow.setClipToPadding(false);
        LinearLayout.LayoutParams arl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        arl.topMargin = dp(8);
        column.addView(appRow, arl);
        fillApps();
    }

    // ---- Life ------------------------------------------------------------------------------------

    /** Starts following the sessions (when the tab is shown and the screen is on). */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        try {
            sessions.addOnActiveSessionsChangedListener(changed, listener, main);
            noAccess = false;
        } catch (RuntimeException e) {
            noAccess = true;
        }
        reload();
    }

    public void stop() {
        started = false;
        main.removeCallbacks(tick);
        try {
            sessions.removeOnActiveSessionsChangedListener(changed);
        } catch (RuntimeException ignored) {
        }
        if (current != null) {
            try {
                current.unregisterCallback(callback);
            } catch (RuntimeException ignored) {
            }
        }
        current = null;
    }

    /** True when something from another app can be controlled. */
    public boolean hasSession() {
        return current != null;
    }

    public int sessionCount() {
        return list.size();
    }

    public boolean isPlaying() {
        PlaybackState st = current != null ? current.getPlaybackState() : null;
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    /** True when an app other than this one is playing right now (to open on this tab). */
    public static boolean somethingPlaying(Context c) {
        if (!PocketNotifications.allowed(c)) {
            return false;
        }
        try {
            MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController mc : m.getActiveSessions(new ComponentName(c, PocketNotifications.class))) {
                PlaybackState st = mc.getPlaybackState();
                if (!c.getPackageName().equals(mc.getPackageName()) && st != null
                        && st.getState() == PlaybackState.STATE_PLAYING) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private void reload() {
        if (!started) {
            return;
        }
        list.clear();
        if (!noAccess) {
            try {
                List<MediaController> all = sessions.getActiveSessions(listener);
                String me = getContext().getPackageName();
                // The one playing first, then the paused ones that still have a song
                for (MediaController mc : all) {
                    PlaybackState st = mc.getPlaybackState();
                    if (!me.equals(mc.getPackageName()) && st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                        list.add(mc);
                    }
                }
                for (MediaController mc : all) {
                    if (!me.equals(mc.getPackageName()) && !list.contains(mc) && mc.getMetadata() != null) {
                        list.add(mc);
                    }
                }
            } catch (RuntimeException e) {
                noAccess = true;
            }
        }
        MediaController keep = null;
        if (current != null) {
            for (MediaController mc : list) {
                if (mc.getSessionToken().equals(current.getSessionToken())) {
                    keep = mc;
                    break;
                }
            }
        }
        use(keep != null ? keep : list.isEmpty() ? null : list.get(0));
    }

    private void use(MediaController mc) {
        if (current != null && current != mc) {
            try {
                current.unregisterCallback(callback);
            } catch (RuntimeException ignored) {
            }
        }
        boolean same = current == mc;
        current = mc;
        if (mc != null && !same) {
            mc.registerCallback(callback, main);
        }
        render(true);
    }

    /** Y: the next app that's playing (or paused with a song). */
    public void nextSource() {
        if (list.size() < 2 || current == null) {
            return;
        }
        int i = 0;
        for (int k = 0; k < list.size(); k++) {
            if (list.get(k).getSessionToken().equals(current.getSessionToken())) {
                i = k;
                break;
            }
        }
        PocketSounds.play(getContext(), PocketSounds.NAV);
        use(list.get((i + 1) % list.size()));
        if (PocketPrefs.animationsEnabled(getContext())) {
            art.setAlpha(0f);
            art.setTranslationX(dp(24));
            art.animate().alpha(1f).translationX(0).setDuration(320).setInterpolator(PocketMotion.DECELERATE).start();
        }
    }

    // ---- Controls ------------------------------------------------------------------------------

    public void togglePlay() {
        if (current == null) {
            return;
        }
        PocketSounds.play(getContext(), PocketSounds.SELECT);
        try {
            if (isPlaying()) {
                current.getTransportControls().pause();
            }
            else {
                current.getTransportControls().play();
            }
        } catch (RuntimeException ignored) {
        }
    }

    public void skip(int dir) {
        if (current == null) {
            return;
        }
        PocketSounds.play(getContext(), PocketSounds.NAV);
        try {
            if (dir < 0) {
                current.getTransportControls().skipToPrevious();
            }
            else {
                current.getTransportControls().skipToNext();
            }
        } catch (RuntimeException ignored) {
        }
        if (PocketPrefs.animationsEnabled(getContext())) {
            art.animate().cancel();
            art.animate().translationX(-dir * dp(14)).setDuration(110).withEndAction(() ->
                    art.animate().translationX(0).setDuration(380).setInterpolator(Spring.POP).start()).start();
        }
    }

    /** Moves along the song by this many seconds. */
    public void seekBy(int seconds) {
        if (current == null) {
            return;
        }
        PlaybackState st = current.getPlaybackState();
        long duration = duration();
        if (st == null || (st.getActions() & PlaybackState.ACTION_SEEK_TO) == 0 || duration <= 0) {
            PocketSounds.play(getContext(), PocketSounds.ERROR);
            return;
        }
        long to = Math.max(0, Math.min(duration - 1000, position() + seconds * 1000L));
        try {
            current.getTransportControls().seekTo(to);
        } catch (RuntimeException ignored) {
        }
        PocketSounds.play(getContext(), PocketSounds.NAV);
        bar.set(duration > 0 ? to / (float) duration : 0f);
    }

    private void openCurrent() {
        if (noAccess) {
            PocketNotifications.askForAccess(getContext());
            return;
        }
        if (current == null) {
            return;
        }
        PocketSounds.play(getContext(), PocketSounds.OPEN);
        PendingIntent pi = current.getSessionActivity();
        if (pi != null) {
            try {
                pi.send();
                return;
            } catch (Exception ignored) {
            }
        }
        launch(current.getPackageName());
    }

    private void launch(String pkg) {
        Context c = getContext();
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        try {
            if (i != null) {
                c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            else {
                // Not on the handheld: its page in the store
                c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (RuntimeException e) {
            PocketSounds.play(c, PocketSounds.ERROR);
        }
    }

    /** Focus on the play button (or what's there when nothing plays). */
    public void focusMain() {
        post(() -> {
            if (playPause.getVisibility() == VISIBLE) {
                playPause.requestFocus();
            }
            else if (openApp.getVisibility() == VISIBLE) {
                openApp.requestFocus();
            }
            else if (appRow.getChildCount() > 0) {
                appRow.getChildAt(0).requestFocus();
            }
        });
    }

    // ---- Showing it ------------------------------------------------------------------------------

    private void render(boolean metadata) {
        Context c = getContext();
        boolean has = current != null;
        int vis = has ? VISIBLE : GONE;
        bar.setVisibility(vis);
        timeRow.setVisibility(vis);
        prev.setVisibility(vis);
        playPause.setVisibility(vis);
        next.setVisibility(vis);
        extras.setVisibility(vis);
        artist.setMaxLines(has ? 1 : 3);
        artist.setLineSpacing(0, has ? 1f : 1.2f);
        if (noAccess) {
            sourceIcon.setImageDrawable(PocketIcons.drawable("BELL", dp(16), 0xA6FFFFFF));
            sourceName.setText(c.getString(R.string.pocket_device_needs).toUpperCase(java.util.Locale.ROOT));
            title.setText(R.string.pocket_device_access);
            artist.setText(R.string.pocket_device_access_sub);
            album.setText("");
            openApp.setText(R.string.pocket_device_allow_access);
            openApp.setVisibility(VISIBLE);
            setArt(null, "none");
        }
        else if (!has) {
            sourceIcon.setImageDrawable(PocketIcons.drawable("MUSIC", dp(16), 0xA6FFFFFF));
            sourceName.setText(c.getString(R.string.pocket_device_this_handheld).toUpperCase(java.util.Locale.ROOT));
            title.setText(R.string.pocket_device_nothing);
            artist.setText(R.string.pocket_device_nothing_sub);
            album.setText("");
            openApp.setVisibility(GONE);
            setArt(null, "none");
        }
        else {
            String pkg = current.getPackageName();
            String appName = appName(pkg);
            sourceIcon.setImageDrawable(appIcon(pkg));
            String from = appName.toUpperCase(java.util.Locale.ROOT);
            if (list.size() > 1) {
                from += "   " + c.getString(R.string.pocket_device_n_of, indexOfCurrent() + 1, list.size());
            }
            sourceName.setText(from);
            MediaMetadata m = current.getMetadata();
            String t = m != null ? str(m, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE) : null;
            String a = m != null ? str(m, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                    MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) : null;
            String b = m != null ? str(m, MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION) : null;
            title.setText(t != null ? t : c.getString(R.string.pocket_device_unknown));
            artist.setText(a != null ? a : "");
            album.setText(b != null && !b.equals(t) ? b : "");
            openApp.setText(c.getString(R.string.pocket_device_open_in, appName));
            openApp.setVisibility(VISIBLE);
            playPause.setImageDrawable(PocketIcons.drawable(isPlaying() ? "PAUSE" : "PLAY", dp(24), 0xFF0B0B0D));
            if (metadata) {
                Bitmap bm = m != null ? bitmap(m) : null;
                setArt(bm, (t != null ? t : "") + "|" + (a != null ? a : "") + "|" + pkg + "|" + (bm != null));
            }
            fillExtras();
            progress();
            main.removeCallbacks(tick);
            if (started && isPlaying()) {
                main.postDelayed(tick, 500);
            }
        }
        host.onChanged();
    }

    private int indexOfCurrent() {
        for (int k = 0; k < list.size(); k++) {
            if (list.get(k).getSessionToken().equals(current.getSessionToken())) {
                return k;
            }
        }
        return 0;
    }

    private void setArt(Bitmap bm, String key) {
        if (key.equals(lastArtKey)) {
            return;
        }
        lastArtKey = key;
        lastArt = bm;
        if (bm != null) {
            art.setImageBitmap(bm);
            art.setBackground(null);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        else {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF2A2F45, 0xFF15161E});
            art.setBackground(g);
            art.setScaleType(ImageView.ScaleType.CENTER);
            art.setImageDrawable(PocketIcons.drawable("MUSIC", dp(64), 0x59FFFFFF));
        }
        if (PocketPrefs.animationsEnabled(getContext())) {
            art.setScaleX(0.94f);
            art.setScaleY(0.94f);
            art.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(Spring.POP).start();
        }
        host.onArt(bm);
    }

    /** The app's own buttons (like, shuffle, repeat...), up to three, with its own icons. */
    private void fillExtras() {
        PlaybackState st = current != null ? current.getPlaybackState() : null;
        List<PlaybackState.CustomAction> acts = st != null ? st.getCustomActions() : null;
        int n = acts == null ? 0 : Math.min(3, acts.size());
        View focused = findFocus();
        boolean hadFocus = focused != null && focused.getParent() == extras;
        int focusedIndex = hadFocus ? extras.indexOfChild(focused) : -1;
        // Only rebuilt when they change (they change with like / shuffle)
        StringBuilder sig = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sig.append(acts.get(i).getAction()).append(acts.get(i).getIcon()).append(',');
        }
        if (sig.toString().equals(extras.getTag())) {
            return;
        }
        extras.setTag(sig.toString());
        extras.removeAllViews();
        final String pkg = current != null ? current.getPackageName() : null;
        for (int i = 0; i < n; i++) {
            final PlaybackState.CustomAction a = acts.get(i);
            ImageView b = round(getContext(), null, 38, false);
            Drawable icon = null;
            try {
                icon = getContext().getPackageManager().getResourcesForApplication(pkg)
                        .getDrawable(a.getIcon(), null);
            } catch (Exception ignored) {
            }
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(0xFFFFFFFF);
                b.setImageDrawable(icon);
                b.setPadding(dp(9), dp(9), dp(9), dp(9));
                b.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
            b.setContentDescription(a.getName());
            b.setOnClickListener(v -> {
                FocusGlow.press(v);
                PocketSounds.play(getContext(), PocketSounds.TOGGLE_ON);
                try {
                    current.getTransportControls().sendCustomAction(a, a.getExtras());
                } catch (RuntimeException ignored) {
                }
            });
            extras.addView(b, roundLp(38, 10));
        }
        if (focusedIndex >= 0 && extras.getChildCount() > 0) {
            extras.getChildAt(Math.min(focusedIndex, extras.getChildCount() - 1)).requestFocus();
        }
    }

    private void fillApps() {
        Context c = getContext();
        PackageManager pm = c.getPackageManager();
        appRow.removeAllViews();
        boolean spotify = false;
        boolean ytm = false;
        for (String[] app : APPS) {
            if (installed(pm, app[0])) {
                appRow.addView(appPill(c, app[0], appName(app[0]), appIcon(app[0]), false), appLp());
                spotify |= app[0].equals("com.spotify.music");
                ytm |= app[0].equals("com.google.android.apps.youtube.music");
            }
            if (appRow.getChildCount() >= 5) {
                break;
            }
        }
        // The two most asked for, to get from the store when they're not here
        if (!spotify && appRow.getChildCount() < 5) {
            appRow.addView(appPill(c, "com.spotify.music", c.getString(R.string.pocket_device_get, "Spotify"),
                    PocketIcons.drawable("MUSIC", dp(18), 0xFF1ED760), true), appLp());
        }
        if (!ytm && appRow.getChildCount() < 5) {
            appRow.addView(appPill(c, "com.google.android.apps.youtube.music",
                    c.getString(R.string.pocket_device_get, "YouTube Music"),
                    PocketIcons.drawable("PLAY", dp(18), 0xFFFF3B30), true), appLp());
        }
    }

    private LinearLayout.LayoutParams appLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        lp.setMarginEnd(dp(10));
        return lp;
    }

    private View appPill(Context c, final String pkg, String name, Drawable icon, boolean get) {
        TextView t = new TextView(c);
        t.setText(name);
        t.setTypeface(PocketFonts.semibold());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        t.setTextColor(get ? 0xB3FFFFFF : 0xF2FFFFFF);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(12), 0, dp(16), 0);
        t.setMaxLines(1);
        if (icon != null) {
            icon.setBounds(0, 0, dp(22), dp(22));
            t.setCompoundDrawablesRelative(icon, null, null, null);
            t.setCompoundDrawablePadding(dp(9));
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(21));
        bg.setColor(0x1FFFFFFF);
        bg.setStroke(Math.max(1, Math.round(d)), 0x1FFFFFFF);
        t.setBackground(bg);
        t.setFocusable(true);
        t.setClickable(true);
        FocusGlow.apply(t, dp(21), false, 1.05f, null);
        t.setOnClickListener(v -> {
            FocusGlow.press(v);
            PocketSounds.play(getContext(), PocketSounds.OPEN);
            launch(pkg);
        });
        return t;
    }

    private ImageView round(Context c, String glyph, int size, boolean filled) {
        ImageView b = new ImageView(c);
        b.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(filled ? 0xFFFFFFFF : 0x24FFFFFF);
        if (!filled) {
            g.setStroke(Math.max(1, Math.round(d)), 0x1FFFFFFF);
        }
        b.setBackground(g);
        if (glyph != null) {
            b.setImageDrawable(PocketIcons.drawable(glyph, dp(size >= 50 ? 24 : 18), filled ? 0xFF0B0B0D : 0xFFFFFFFF));
        }
        b.setFocusable(true);
        b.setClickable(true);
        FocusGlow.apply(b, 0, true, 1.1f, null);
        return b;
    }

    private LinearLayout.LayoutParams roundLp(int size, int start) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
        lp.setMarginStart(dp(start));
        return lp;
    }

    private TextView pill(Context c) {
        TextView t = new TextView(c);
        t.setTypeface(PocketFonts.semibold());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), 0, dp(18), 0);
        t.setMaxLines(1);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(19));
        bg.setColor(0x26FFFFFF);
        t.setBackground(bg);
        t.setFocusable(true);
        t.setClickable(true);
        FocusGlow.apply(t, dp(19), false, 1.05f, null);
        return t;
    }

    private void progress() {
        long duration = duration();
        if (current == null || duration <= 0) {
            bar.set(0f);
            elapsed.setText("");
            remaining.setText("");
            return;
        }
        long pos = position();
        bar.set(pos / (float) duration);
        elapsed.setText(DateUtils.formatElapsedTime(pos / 1000));
        remaining.setText("-" + DateUtils.formatElapsedTime(Math.max(0, duration - pos) / 1000));
    }

    private long duration() {
        MediaMetadata m = current != null ? current.getMetadata() : null;
        return m != null ? m.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
    }

    /** Where it is now: the last known place, moved on by the time since (when playing). */
    private long position() {
        PlaybackState st = current != null ? current.getPlaybackState() : null;
        if (st == null) {
            return 0;
        }
        long pos = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING && st.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        long dur = duration();
        return Math.max(0, dur > 0 ? Math.min(dur, pos) : pos);
    }

    // ---- Small things -----------------------------------------------------------------------------

    private static String str(MediaMetadata m, String... keys) {
        for (String k : keys) {
            CharSequence v = m.getText(k);
            if (v != null && v.length() > 0) {
                return v.toString();
            }
        }
        return null;
    }

    private static Bitmap bitmap(MediaMetadata m) {
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) {
            b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        if (b == null) {
            b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        return b;
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String appName(String pkg) {
        try {
            PackageManager pm = getContext().getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            for (String[] a : APPS) {
                if (a[0].equals(pkg)) {
                    return a[1];
                }
            }
            return pkg;
        }
    }

    private Drawable appIcon(String pkg) {
        try {
            return getContext().getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return PocketIcons.drawable("MUSIC", dp(16), 0xFFFFFFFF);
        }
    }

    private TextView text(Context c, float sp, int color, android.graphics.Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    /** The bar: a faint track, the part played in white, a dot where it is (bigger when chosen). */
    private final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint done = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float at;

        Bar(Context c) {
            super(c);
            track.setColor(0x33FFFFFF);
            done.setColor(0xFFFFFFFF);
            dot.setColor(0xFFFFFFFF);
            dot.setShadowLayer(8 * d, 0, 0, 0xCCFFFFFF);
            setFocusable(true);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                setDefaultFocusHighlightEnabled(false);
            }
            setOnFocusChangeListener((v, has) -> {
                if (has) {
                    PocketSounds.play(getContext(), PocketSounds.NAV);
                }
                invalidate();
            });
        }

        void set(float f) {
            at = Math.max(0f, Math.min(1f, f));
            invalidate();
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int step = event.getRepeatCount() > 6 ? 30 : 10;
                seekBy(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -step : step);
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        @Override
        protected void onDraw(Canvas c) {
            boolean on = isFocused();
            float h = (on ? 6f : 4f) * d;
            float cy = getHeight() / 2f;
            float pad = 7 * d;
            float w = getWidth() - pad * 2;
            r.set(pad, cy - h / 2, pad + w, cy + h / 2);
            c.drawRoundRect(r, h / 2, h / 2, track);
            r.set(pad, cy - h / 2, pad + w * at, cy + h / 2);
            c.drawRoundRect(r, h / 2, h / 2, done);
            c.drawCircle(pad + w * at, cy, (on ? 7.5f : 5f) * d, dot);
        }
    }
}
