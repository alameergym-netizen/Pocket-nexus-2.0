package com.limelight.pocket.music;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.net.Uri;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;
import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketIcons;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.Spring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Music: the songs on the handheld, by album, artist, song or folder, played by Pocket Nexus's
 * own player (it goes on in the background and over games). The screen takes the colours of
 * what's playing (its cover, blurred, behind everything). Y opens Now Playing: the cover big,
 * the progress, the controls and what's next.
 *
 * Buttons: L1 / R1 the tabs, A opens or plays, X shuffles, Y Now Playing, Start play / pause,
 * L2 / R2 the previous / next song, B back.
 */
public class MusicActivity extends Activity {

    public static void open(Context c) {
        try {
            c.startActivity(new Intent(c, MusicActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
        }
    }

    /** What any app on the handheld is playing (Spotify, YouTube Music...), with its controls. */
    private static final int TAB_PLAYING = 0;
    private static final int TAB_ALBUMS = 1;
    private static final int TAB_ARTISTS = 2;
    private static final int TAB_SONGS = 3;
    private static final int TAB_FOLDERS = 4;
    private static final int[] TAB_NAMES = {R.string.pocket_music_playing, R.string.pocket_music_albums, R.string.pocket_music_artists,
            R.string.pocket_music_songs, R.string.pocket_music_folders};
    private static final String KEY_TAB = "pocket_music_tab2";
    private static final int REQUEST_AUDIO = 7501;

    private final Handler main = new Handler(Looper.getMainLooper());
    private float d;
    private int accent;
    private MusicLibrary.Snapshot lib;
    private int tab;

    private ImageView backA;
    private ImageView backB;
    private boolean backOnA = true;
    private long backAlbum = Long.MIN_VALUE;

    private LinearLayout tabRow;
    private TextView countView;
    private FrameLayout body;
    private RecyclerView grid;
    private Items gridItems;
    private LinearLayout emptyPanel;
    private TextView emptyView;
    private Button audioSettings;
    private boolean audioGranted;
    private int scanGeneration;
    private LinearLayout hints;

    /** An album, an artist or a folder opened: its own page over the tab. */
    private View detail;
    private RecyclerView detailList;
    private List<MusicLibrary.Song> detailSongs;
    private int focusBeforeDetail = -1;

    private MiniBar mini;
    private NowPlaying now;
    private DevicePlayerView device;

    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (controller != null) {
                if (mini != null) {
                    mini.progress();
                }
                if (now != null && now.isOpen()) {
                    now.progress();
                }
                if (controller.isPlaying()) {
                    main.postDelayed(this, 250);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConsoleActivity.applyFullscreenWindow(this);
        d = getResources().getDisplayMetrics().density;
        accent = PocketTheme.accent(this);
        tab = Math.max(0, Math.min(TAB_NAMES.length - 1, PocketPrefs.prefs(this).getInt(KEY_TAB, TAB_PLAYING)));
        if (DevicePlayerView.somethingPlaying(this)) {
            tab = TAB_PLAYING; // another app is playing: its controls first
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF07070A);
        setContentView(root);

        // The colours of what's playing, behind everything
        backA = backdropImage();
        backB = backdropImage();
        root.addView(backA, full());
        root.addView(backB, full());
        View shade = new View(this);
        shade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xB307070A, 0xE007070A, 0xF507070A}));
        root.addView(shade, full());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(40), dp(26), dp(40), 0);
        root.addView(column, full());

        // Header: the name, then the tabs
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(R.string.pocket_music_title);
        title.setTypeface(PocketFonts.display());
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setLetterSpacing(-0.02f);
        header.addView(title);
        TextView l1 = glyph("L1");
        LinearLayout.LayoutParams l1p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(18));
        l1p.setMarginStart(dp(30));
        header.addView(l1, l1p);
        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(tabRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        TextView r1 = glyph("R1");
        header.addView(r1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(18)));
        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        countView = new TextView(this);
        countView.setTypeface(PocketFonts.medium());
        countView.setTextColor(0x99FFFFFF);
        countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        header.addView(countView);
        column.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            TextView t = new TextView(this);
            t.setText(TAB_NAMES[i]);
            t.setTypeface(PocketFonts.semibold());
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(16), 0, dp(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
            lp.setMarginStart(dp(4));
            lp.setMarginEnd(dp(4));
            tabRow.addView(t, lp);
        }

        body = new FrameLayout(this);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        blp.topMargin = dp(14);
        column.addView(body, blp);

        grid = new RecyclerView(this);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(dp(6), dp(10), dp(6), dp(96));
        grid.setItemAnimator(null);
        grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        gridItems = new Items();
        grid.setAdapter(gridItems);
        body.addView(grid, full());

        emptyPanel = new LinearLayout(this);
        emptyPanel.setOrientation(LinearLayout.VERTICAL);
        emptyPanel.setGravity(Gravity.CENTER);
        emptyPanel.setPadding(dp(20), 0, dp(20), 0);
        emptyPanel.setVisibility(View.GONE);
        body.addView(emptyPanel, full());
        emptyView = new TextView(this);
        emptyView.setTypeface(PocketFonts.medium());
        emptyView.setTextColor(0xCCFFFFFF);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setLineSpacing(0, 1.25f);
        emptyPanel.addView(emptyView, new LinearLayout.LayoutParams(-1, -2));
        audioSettings = new Button(this);
        audioSettings.setText(R.string.horizon_music_access_settings);
        audioSettings.setFocusableInTouchMode(true);
        audioSettings.setVisibility(View.GONE);
        audioSettings.setOnClickListener(v -> openAudioSettings());
        LinearLayout.LayoutParams audioLp = new LinearLayout.LayoutParams(-2, -2);
        audioLp.topMargin = dp(12);
        emptyPanel.addView(audioSettings, audioLp);

        device = new DevicePlayerView(this, new DevicePlayerView.Host() {
            @Override
            public void onArt(android.graphics.Bitmap art) {
                externalBackdrop(art);
            }

            @Override
            public void onChanged() {
                updateHints();
            }
        });
        device.setVisibility(View.GONE);
        body.addView(device, full());

        mini = new MiniBar(this);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(dp(470), dp(66), Gravity.BOTTOM | Gravity.START);
        mlp.leftMargin = dp(40);
        mlp.bottomMargin = dp(20);
        root.addView(mini, mlp);

        hints = new LinearLayout(this);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        hints.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        hlp.rightMargin = dp(32);
        hlp.bottomMargin = dp(40);
        root.addView(hints, hlp);

        now = new NowPlaying(this);
        root.addView(now, full());

        styleTabs(false);
        updateHints();
        connect();
        audioGranted = hasAccess();
        if (audioGranted) {
            loadLibrary(false);
        }
        else {
            showEmpty(getString(R.string.pocket_music_need_access));
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{audioPermission()}, REQUEST_AUDIO);
            }
        }
        if (tab == TAB_PLAYING || !audioGranted) showTab(false);
    }

    private static String audioPermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasAccess() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) refreshAudioPermission();
    }

    private void openAudioSettings() {
        if (Build.VERSION.SDK_INT >= 23 && shouldShowRequestPermissionRationale(audioPermission())) {
            requestPermissions(new String[]{audioPermission()}, REQUEST_AUDIO);
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.pocket_music_need_access, Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshAudioPermission() {
        boolean current = hasAccess();
        if (current == audioGranted) return;
        audioGranted = current;
        if (audioGranted) {
            loadLibrary(true);
        } else {
            scanGeneration++;
            lib = null;
            if (detail != null) closeDetail(false);
            showEmpty(getString(R.string.pocket_music_need_access));
            showTab(false);
        }
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    private static FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private ImageView backdropImage() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        if (Build.VERSION.SDK_INT >= 31) {
            v.setRenderEffect(RenderEffect.createBlurEffect(60 * d, 60 * d, Shader.TileMode.CLAMP));
        }
        return v;
    }

    private TextView glyph(String s) {
        TextView g = new TextView(this);
        g.setText(s);
        g.setTypeface(PocketFonts.bold());
        g.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        g.setTextColor(0xFF0B0B0D);
        g.setGravity(Gravity.CENTER);
        g.setIncludeFontPadding(false);
        g.setPadding(dp(6), 0, dp(6), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(9));
        bg.setColor(0xD9FFFFFF);
        g.setBackground(bg);
        return g;
    }

    // ---- The player (Pocket Nexus's music service) -----------------------------------------------

    private void connect() {
        SessionToken token = new SessionToken(this, new ComponentName(this, MusicService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
            } catch (Exception e) {
                return;
            }
            if (isFinishing()) {
                return;
            }
            controller.addListener(new Player.Listener() {
                @Override
                public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                    onPlayerChanged();
                }
            });
            onPlayerChanged();
        }, ContextCompat.getMainExecutor(this));
    }

    /** What's playing changed (or its state): the mini bar, Now Playing and the colours follow. */
    private void onPlayerChanged() {
        if (controller == null) {
            return;
        }
        MediaItem item = controller.getCurrentMediaItem();
        mini.bind(item, controller.isPlaying());
        now.bind(item);
        MusicLibrary.Song s = songOf(item);
        setBackdrop(s);
        main.removeCallbacks(tick);
        main.post(tick);
        updateHints();
    }

    private MusicLibrary.Song songOf(MediaItem item) {
        if (item == null || lib == null) {
            return null;
        }
        try {
            return lib.byId.get(Long.parseLong(item.mediaId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void setBackdrop(MusicLibrary.Song s) {
        if (tab == TAB_PLAYING && device != null && device.hasSession()) {
            return; // the other app's cover is behind
        }
        long album = s != null ? s.albumId : -1;
        if (album == backAlbum) {
            return;
        }
        backAlbum = album;
        if (s == null) {
            return;
        }
        MusicArt.load(this, s.albumId, s.album, 256, art -> {
            if (backAlbum != s.albumId || isFinishing()) {
                return;
            }
            ImageView in = backOnA ? backB : backA;
            ImageView out = backOnA ? backA : backB;
            backOnA = !backOnA;
            in.setImageBitmap(art);
            in.animate().cancel();
            in.animate().alpha(0.85f).setDuration(900).start();
            out.animate().cancel();
            out.animate().alpha(0f).setDuration(900).start();
        });
    }

    /** Another app's cover behind everything (faded out when it has none). */
    private void externalBackdrop(android.graphics.Bitmap art) {
        if (tab != TAB_PLAYING || isFinishing()) {
            return;
        }
        backAlbum = -2;
        ImageView in = backOnA ? backB : backA;
        ImageView out = backOnA ? backA : backB;
        backOnA = !backOnA;
        in.setImageBitmap(art);
        in.animate().cancel();
        in.animate().alpha(art != null ? 0.85f : 0f).setDuration(900).start();
        out.animate().cancel();
        out.animate().alpha(0f).setDuration(900).start();
    }

    /** Plays these songs from the one at start (shuffled: in a random order, start first). */
    private void play(List<MusicLibrary.Song> songs, int start, boolean shuffle) {
        if (controller == null || songs == null || songs.isEmpty()) {
            return;
        }
        List<MediaItem> items = new ArrayList<>(songs.size());
        for (MusicLibrary.Song s : songs) {
            items.add(MusicService.item(s));
        }
        int first = start;
        if (shuffle && start < 0) {
            first = (int) (Math.random() * items.size());
        }
        controller.setShuffleModeEnabled(shuffle);
        controller.setMediaItems(items, Math.max(0, first), 0L);
        controller.prepare();
        controller.play();
        PocketSounds.play(this, PocketSounds.SELECT);
    }

    // ---- The library -----------------------------------------------------------------------------

    private void loadLibrary(final boolean fresh) {
        showEmpty(getString(R.string.pocket_music_loading));
        final int scan = ++scanGeneration;
        final Context app = getApplicationContext();
        new Thread(() -> {
            final MusicLibrary.Snapshot s = MusicLibrary.load(app, fresh);
            main.post(() -> {
                if (isFinishing() || isDestroyed() || scan != scanGeneration || !audioGranted) return;
                lib = s;
                if (s.isEmpty()) {
                    showEmpty(getString(R.string.pocket_music_empty));
                }
                showTab(false);
                onPlayerChanged();
            });
        }, "pocket-music-library").start();
    }

    private void showEmpty(String text) {
        emptyView.setText(text);
        audioSettings.setVisibility(audioGranted ? View.GONE : View.VISIBLE);
        if (tab == TAB_PLAYING) {
            return;
        }
        emptyPanel.setVisibility(View.VISIBLE);
        grid.setVisibility(View.INVISIBLE);
    }

    private void showTab(boolean animate) {
        PocketPrefs.prefs(this).edit().putInt(KEY_TAB, tab).apply();
        styleTabs(animate);
        boolean playing = tab == TAB_PLAYING;
        if (device.getVisibility() == View.VISIBLE && !playing) {
            // Back to this app's own music: its colours behind again
            device.stop();
            device.setVisibility(View.GONE);
            backAlbum = Long.MIN_VALUE;
            if (controller != null) {
                onPlayerChanged();
            }
        }
        if (playing) {
            countView.setText("");
            emptyPanel.setVisibility(View.GONE);
            grid.setVisibility(View.INVISIBLE);
            device.setVisibility(View.VISIBLE);
            device.start();
            if (animate && PocketPrefs.animationsEnabled(this)) {
                device.setAlpha(0f);
                device.setTranslationY(dp(14));
                device.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(Spring.POP).start();
            }
            device.focusMain();
            updateHints();
            return;
        }
        if (lib == null || lib.isEmpty()) {
            countView.setText("");
            emptyPanel.setVisibility(View.VISIBLE);
            grid.setVisibility(View.INVISIBLE);
            if (!audioGranted) audioSettings.requestFocus();
            updateHints();
            return;
        }
        emptyPanel.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        boolean list = tab == TAB_SONGS || tab == TAB_FOLDERS;
        if (list) {
            grid.setLayoutManager(new LinearLayoutManager(this));
        }
        else {
            // Covers about 170dp wide, as many as fit
            int width = getResources().getDisplayMetrics().widthPixels - dp(92);
            int cols = Math.max(3, width / dp(tab == TAB_ARTISTS ? 160 : 176));
            grid.setLayoutManager(new GridLayoutManager(this, cols));
        }
        List<Object> rows = new ArrayList<>();
        switch (tab) {
            case TAB_ALBUMS:
                rows.addAll(lib.albums);
                countView.setText(getResources().getQuantityString(R.plurals.pocket_music_n_albums, lib.albums.size(), lib.albums.size()));
                break;
            case TAB_ARTISTS:
                rows.addAll(lib.artists);
                countView.setText(getResources().getQuantityString(R.plurals.pocket_music_n_artists, lib.artists.size(), lib.artists.size()));
                break;
            case TAB_SONGS:
                rows.addAll(lib.songs);
                countView.setText(getResources().getQuantityString(R.plurals.pocket_music_n_songs, lib.songs.size(), lib.songs.size()));
                break;
            default:
                rows.addAll(lib.folders);
                countView.setText(getResources().getQuantityString(R.plurals.pocket_music_n_folders, lib.folders.size(), lib.folders.size()));
                break;
        }
        gridItems.set(rows);
        grid.scrollToPosition(0);
        if (animate && PocketPrefs.animationsEnabled(this)) {
            grid.setAlpha(0f);
            grid.setTranslationY(dp(14));
            grid.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(Spring.POP).start();
        }
        focusGrid(0, 0);
    }

    private void focusGrid(final int position, final int attempt) {
        grid.post(() -> {
            if (isFinishing() || detail != null || now.isOpen()) {
                return;
            }
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(position);
            if (h != null) {
                h.itemView.requestFocus();
            }
            else if (attempt < 6) {
                grid.scrollToPosition(position);
                main.postDelayed(() -> focusGrid(position, attempt + 1), 40);
            }
        });
    }

    private void styleTabs(boolean animate) {
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            TextView t = (TextView) tabRow.getChildAt(i);
            boolean on = i == tab;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            bg.setColor(on ? 0xF2FFFFFF : 0x00000000);
            t.setBackground(bg);
            t.setTextColor(on ? 0xFF0B0B0D : 0x99FFFFFF);
            if (on && animate) {
                t.setScaleX(0.9f);
                t.setScaleY(0.9f);
                t.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(Spring.POP).start();
            }
        }
    }

    private void moveTab(int dir) {
        closeDetail(false);
        tab = (tab + dir + TAB_NAMES.length) % TAB_NAMES.length;
        PocketSounds.play(this, PocketSounds.NAV);
        showTab(true);
    }

    // ---- An album / artist / folder's page ------------------------------------------------------

    private void openDetail(Object what, int fromPosition) {
        String name;
        String sub;
        long coverAlbum;
        List<MusicLibrary.Song> songs;
        if (what instanceof MusicLibrary.Album) {
            MusicLibrary.Album a = (MusicLibrary.Album) what;
            name = a.title;
            sub = a.artist + (a.year > 0 ? "  ·  " + a.year : "") + "  ·  "
                    + getResources().getQuantityString(R.plurals.pocket_music_n_songs, a.songs.size(), a.songs.size())
                    + "  ·  " + MusicLibrary.clock(a.durationMs());
            coverAlbum = a.id;
            songs = a.songs;
        }
        else if (what instanceof MusicLibrary.Artist) {
            MusicLibrary.Artist a = (MusicLibrary.Artist) what;
            name = a.name;
            sub = getResources().getQuantityString(R.plurals.pocket_music_n_albums, a.albums.size(), a.albums.size())
                    + "  ·  " + getResources().getQuantityString(R.plurals.pocket_music_n_songs, a.songs.size(), a.songs.size());
            coverAlbum = a.coverAlbum();
            songs = new ArrayList<>();
            for (MusicLibrary.Album al : a.albums) {
                songs.addAll(al.songs);
            }
        }
        else {
            MusicLibrary.Folder f = (MusicLibrary.Folder) what;
            name = f.name;
            sub = f.path;
            coverAlbum = f.songs.isEmpty() ? -1 : f.songs.get(0).albumId;
            songs = f.songs;
        }
        focusBeforeDetail = fromPosition;
        detailSongs = songs;
        PocketSounds.play(this, PocketSounds.OPEN);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);

        // Left: the cover, the name, Play and Shuffle
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        rounded(cover, dp(16));
        cover.setElevation(dp(14));
        side.addView(cover, new LinearLayout.LayoutParams(dp(230), dp(230)));
        final String coverName = name;
        MusicArt.load(this, coverAlbum, coverName, dp(230), cover::setImageBitmap);
        TextView t = new TextView(this);
        t.setText(name);
        t.setTypeface(PocketFonts.display());
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(16);
        side.addView(t, tlp);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTypeface(PocketFonts.medium());
        s.setTextColor(0xA6FFFFFF);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        s.setMaxLines(2);
        s.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(6);
        side.addView(s, slp);
        TextView how = new TextView(this);
        how.setText(R.string.pocket_music_detail_hint);
        how.setTypeface(PocketFonts.medium());
        how.setTextColor(0x80FFFFFF);
        how.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(14);
        side.addView(how, hlp);
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(dp(290), ViewGroup.LayoutParams.MATCH_PARENT);
        page.addView(side, sideLp);

        // Right: its songs
        detailList = new RecyclerView(this);
        detailList.setLayoutManager(new LinearLayoutManager(this));
        detailList.setClipToPadding(false);
        detailList.setPadding(0, 0, 0, dp(96));
        detailList.setItemAnimator(null);
        Items rows = new Items();
        rows.numbered = what instanceof MusicLibrary.Album;
        rows.set(new ArrayList<Object>(songs));
        detailList.setAdapter(rows);
        page.addView(detailList, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        detail = page;
        body.addView(page, full());
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        grid.animate().alpha(0f).setDuration(160).start();
        if (PocketPrefs.animationsEnabled(this)) {
            page.setAlpha(0f);
            page.setTranslationX(dp(30));
            page.animate().alpha(1f).translationX(0).setDuration(300).setInterpolator(Spring.POP).start();
        }
        detailList.post(() -> {
            RecyclerView.ViewHolder h = detailList.findViewHolderForAdapterPosition(0);
            if (h != null) {
                h.itemView.requestFocus();
            }
        });
        updateHints();
    }

    private boolean closeDetail(boolean sound) {
        if (detail == null) {
            return false;
        }
        final View page = detail;
        detail = null;
        detailList = null;
        detailSongs = null;
        if (sound) {
            PocketSounds.play(this, PocketSounds.BACK);
        }
        page.animate().alpha(0f).translationX(dp(30)).setDuration(180).withEndAction(() -> body.removeView(page)).start();
        grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        grid.animate().alpha(1f).setDuration(220).start();
        focusGrid(Math.max(0, focusBeforeDetail), 0);
        updateHints();
        return true;
    }

    private static void rounded(View v, final float r) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    // ---- Buttons ---------------------------------------------------------------------------------

    private void updateHints() {
        List<ConsoleActivity.Hint> h = new ArrayList<>();
        if (tab == TAB_PLAYING && detail == null && !now.isOpen()) {
            h.add(ConsoleActivity.hintA(this, R.string.pocket_music_hint_select));
            if (device != null && device.hasSession()) {
                h.add(new ConsoleActivity.Hint("L2 R2", 0xFFFFFFFF, getString(R.string.pocket_music_hint_skip)));
                h.add(ConsoleActivity.hintStart(this, R.string.pocket_music_hint_pause));
                if (device.sessionCount() > 1) {
                    h.add(ConsoleActivity.hintY(this, R.string.pocket_device_hint_switch));
                }
            }
            h.add(ConsoleActivity.hintB(this, R.string.pocket_music_hint_close));
            ConsoleActivity.fillHintBar(this, hints, h.toArray(new ConsoleActivity.Hint[0]));
            return;
        }
        if (now.isOpen()) {
            h.add(ConsoleActivity.hintA(this, R.string.pocket_music_hint_select));
            h.add(new ConsoleActivity.Hint("L1 R1", 0xFFFFFFFF, getString(R.string.pocket_music_hint_skip)));
            h.add(ConsoleActivity.hintX(this, R.string.pocket_music_hint_shuffle));
            h.add(ConsoleActivity.hintB(this, R.string.pocket_hint_back));
        }
        else {
            h.add(ConsoleActivity.hintA(this, detail != null ? R.string.pocket_music_hint_play : R.string.pocket_music_hint_open));
            h.add(ConsoleActivity.hintX(this, R.string.pocket_music_hint_shuffle));
            if (controller != null && controller.getCurrentMediaItem() != null) {
                h.add(ConsoleActivity.hintY(this, R.string.pocket_music_hint_now));
                h.add(ConsoleActivity.hintStart(this, R.string.pocket_music_hint_pause));
            }
            h.add(ConsoleActivity.hintB(this, detail != null ? R.string.pocket_hint_back : R.string.pocket_music_hint_close));
        }
        ConsoleActivity.fillHintBar(this, hints, h.toArray(new ConsoleActivity.Hint[0]));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean first = down && event.getRepeatCount() == 0;
        if (tab == TAB_PLAYING && detail == null && !now.isOpen()) {
            // Another app's music: its controls
            switch (code) {
                case KeyEvent.KEYCODE_BUTTON_START:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    if (device.hasSession()) {
                        if (first) {
                            device.togglePlay();
                        }
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_BUTTON_L2:
                case KeyEvent.KEYCODE_BUTTON_R2:
                    if (device.hasSession()) {
                        if (first) {
                            device.skip(code == KeyEvent.KEYCODE_BUTTON_L2 ? -1 : 1);
                        }
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_BUTTON_Y:
                    if (device.sessionCount() > 1) {
                        if (first) {
                            device.nextSource();
                        }
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (first) {
                        View f = getCurrentFocus();
                        if (f != null && f.isClickable()) {
                            f.performClick();
                        }
                    }
                    return true;
                default:
                    break;
            }
        }
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (first && controller != null) {
                    if (controller.isPlaying()) {
                        controller.pause();
                    }
                    else {
                        controller.play();
                    }
                    PocketSounds.play(this, PocketSounds.SELECT);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (first) {
                    skip(-1);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (first) {
                    skip(1);
                }
                return true;
            default:
                break;
        }
        if (now.isOpen()) {
            if (now.handleKey(event)) {
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_L1:
                if (first) {
                    moveTab(-1);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (first) {
                    moveTab(1);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (first) {
                    shuffleHere();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (first && controller != null && controller.getCurrentMediaItem() != null) {
                    now.open();
                    updateHints();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (first) {
                    if (!closeDetail(true)) {
                        PocketSounds.play(this, PocketSounds.BACK);
                        finish();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (first) {
                    activateFocused();
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void skip(int dir) {
        if (controller == null || controller.getMediaItemCount() == 0) {
            return;
        }
        if (dir < 0) {
            controller.seekToPrevious();
        }
        else {
            controller.seekToNext();
        }
        PocketSounds.play(this, PocketSounds.NAV);
        now.nudge(dir);
    }

    private void shuffleHere() {
        if (lib == null || lib.isEmpty()) {
            return;
        }
        List<MusicLibrary.Song> songs = detailSongs != null ? detailSongs : lib.songs;
        play(new ArrayList<>(songs), -1, true);
    }

    private void activateFocused() {
        View f = getCurrentFocus();
        if (f == audioSettings) {
            audioSettings.performClick();
            return;
        }
        if (f == null) {
            return;
        }
        RecyclerView list = detail != null ? detailList : grid;
        if (list == null) {
            return;
        }
        RecyclerView.ViewHolder h = list.findContainingViewHolder(f);
        if (h == null) {
            return;
        }
        int pos = h.getBindingAdapterPosition();
        Items items = (Items) list.getAdapter();
        if (items == null || pos < 0 || pos >= items.rows.size()) {
            return;
        }
        Object o = items.rows.get(pos);
        if (o instanceof MusicLibrary.Song) {
            List<MusicLibrary.Song> songs = new ArrayList<>();
            for (Object r : items.rows) {
                songs.add((MusicLibrary.Song) r);
            }
            play(songs, pos, false);
            pulse(f);
        }
        else {
            openDetail(o, pos);
        }
    }

    private void pulse(View v) {
        v.animate().cancel();
        v.setScaleX(0.97f);
        v.setScaleY(0.97f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(Spring.POP).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAudioPermission();
        ConsoleActivity.applyFullscreenWindow(this);
        main.removeCallbacks(tick);
        main.post(tick);
        if (tab == TAB_PLAYING && device != null) {
            device.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
        if (device != null) {
            device.stop();
        }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        controller = null;
        super.onDestroy();
    }

    // ---- Tiles and rows --------------------------------------------------------------------------

    private final class Items extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<Object> rows = new ArrayList<>();
        boolean numbered;

        void set(List<Object> list) {
            rows.clear();
            rows.addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object o = rows.get(position);
            if (o instanceof MusicLibrary.Album) {
                return 0;
            }
            if (o instanceof MusicLibrary.Artist) {
                return 1;
            }
            if (o instanceof MusicLibrary.Song) {
                return 2;
            }
            return 3;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v = type == 0 || type == 1 ? new Tile(MusicActivity.this, type == 1) : new Row(MusicActivity.this);
            return new RecyclerView.ViewHolder(v) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object o = rows.get(position);
            if (holder.itemView instanceof Tile) {
                ((Tile) holder.itemView).bind(o);
            }
            else {
                ((Row) holder.itemView).bind(o, numbered ? position + 1 : 0);
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    /** An album's cover (square) or an artist's (round), with the name under it. */
    private final class Tile extends LinearLayout {
        final ImageView art;
        final TextView name;
        final TextView sub;
        final boolean round;
        long albumKey = Long.MIN_VALUE;

        Tile(Context c, boolean round) {
            super(c);
            this.round = round;
            setOrientation(VERTICAL);
            setGravity(round ? Gravity.CENTER_HORIZONTAL : Gravity.START);
            setFocusable(true);
            setClipChildren(false);
            setClipToPadding(false);
            setPadding(dp(10), dp(10), dp(10), dp(14));
            art = new ImageView(c);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int size = round ? dp(128) : dp(148);
            if (round) {
                art.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                art.setClipToOutline(true);
            }
            else {
                rounded(art, dp(12));
            }
            art.setElevation(dp(6));
            addView(art, new LayoutParams(size, size));
            name = new TextView(c);
            name.setTypeface(PocketFonts.semibold());
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            name.setTextColor(0xE6FFFFFF);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(round ? Gravity.CENTER : Gravity.START);
            LayoutParams nlp = new LayoutParams(size, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(10);
            addView(name, nlp);
            sub = new TextView(c);
            sub.setTypeface(PocketFonts.medium());
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            sub.setTextColor(0x8CFFFFFF);
            sub.setMaxLines(1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setGravity(round ? Gravity.CENTER : Gravity.START);
            LayoutParams slp = new LayoutParams(size, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(2);
            addView(sub, slp);
            setOnFocusChangeListener((v, has) -> focus(has));
            setOnClickListener(v -> activateFocused());
        }

        void bind(Object o) {
            final long album;
            final String label;
            if (o instanceof MusicLibrary.Album) {
                MusicLibrary.Album a = (MusicLibrary.Album) o;
                name.setText(a.title);
                sub.setText(a.artist);
                album = a.id;
                label = a.title;
            }
            else {
                MusicLibrary.Artist a = (MusicLibrary.Artist) o;
                name.setText(a.name);
                sub.setText(getResources().getQuantityString(R.plurals.pocket_music_n_songs, a.songs.size(), a.songs.size()));
                album = a.coverAlbum();
                label = a.name;
            }
            if (album == albumKey) {
                return;
            }
            albumKey = album;
            int size = art.getLayoutParams().width;
            Bitmap hit = MusicArt.cached(album, size);
            art.setImageBitmap(hit);
            if (hit == null) {
                art.setAlpha(0f);
                MusicArt.load(getContext(), album, label, size, b -> {
                    if (albumKey == album) {
                        art.setImageBitmap(b);
                        art.animate().alpha(1f).setDuration(220).start();
                    }
                });
            }
            else {
                art.setAlpha(1f);
            }
        }

        void focus(boolean has) {
            art.animate().cancel();
            if (has) {
                PocketSounds.play(getContext(), PocketSounds.NAV);
                art.animate().scaleX(1.07f).scaleY(1.07f).translationZ(dp(10)).setDuration(240).setInterpolator(Spring.POP).start();
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(round ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
                ring.setCornerRadius(dp(14));
                ring.setStroke(dp(3), 0xFFFFFFFF);
                art.setForeground(ring);
                name.setTextColor(0xFFFFFFFF);
            }
            else {
                art.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(200).start();
                art.setForeground(null);
                name.setTextColor(0xE6FFFFFF);
            }
        }
    }

    /** A song (number or cover, name, artist and album, length) or a folder. */
    private final class Row extends LinearLayout {
        final TextView number;
        final ImageView art;
        final TextView name;
        final TextView sub;
        final TextView length;
        final View playing;
        long songId = -1;
        long albumKey = Long.MIN_VALUE;

        Row(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            setPadding(dp(12), 0, dp(16), 0);
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
            number = new TextView(c);
            number.setTypeface(PocketFonts.semibold());
            number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            number.setGravity(Gravity.CENTER);
            addView(number, new LayoutParams(dp(40), dp(40)));
            art = new ImageView(c);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            rounded(art, dp(7));
            addView(art, new LayoutParams(dp(40), dp(40)));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(VERTICAL);
            name = new TextView(c);
            name.setTypeface(PocketFonts.semibold());
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(name);
            sub = new TextView(c);
            sub.setTypeface(PocketFonts.medium());
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            sub.setMaxLines(1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(sub);
            LayoutParams wlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wlp.setMarginStart(dp(14));
            addView(words, wlp);
            playing = new EqualizerView(c);
            LayoutParams plp = new LayoutParams(dp(16), dp(16));
            plp.setMarginEnd(dp(14));
            addView(playing, plp);
            length = new TextView(c);
            length.setTypeface(PocketFonts.medium());
            length.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            addView(length);
            setOnFocusChangeListener((v, has) -> {
                ConsoleScreenLight.apply(this, has, name, sub, length, number);
                if (has) {
                    PocketSounds.play(getContext(), PocketSounds.NAV);
                }
            });
            setOnClickListener(v -> activateFocused());
            ConsoleScreenLight.apply(this, false, name, sub, length, number);
        }

        void bind(Object o, int trackNumber) {
            if (o instanceof MusicLibrary.Song) {
                MusicLibrary.Song s = (MusicLibrary.Song) o;
                songId = s.id;
                name.setText(s.title);
                sub.setText(trackNumber > 0 ? s.artist : s.artist + "  ·  " + s.album);
                length.setText(MusicLibrary.clock(s.durationMs));
                length.setVisibility(VISIBLE);
                boolean numbered = trackNumber > 0;
                number.setVisibility(numbered ? VISIBLE : GONE);
                art.setVisibility(numbered ? GONE : VISIBLE);
                number.setText(String.valueOf(trackNumber));
                if (!numbered) {
                    setArt(s.albumId, s.album);
                }
            }
            else {
                MusicLibrary.Folder f = (MusicLibrary.Folder) o;
                songId = -1;
                name.setText(f.name);
                sub.setText(f.path);
                length.setText(getResources().getQuantityString(R.plurals.pocket_music_n_songs, f.songs.size(), f.songs.size()));
                number.setVisibility(GONE);
                art.setVisibility(VISIBLE);
                albumKey = Long.MIN_VALUE;
                art.setImageDrawable(PocketIcons.drawable("FOLDER", dp(40), 0xCCFFFFFF));
                art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                art.setPadding(dp(9), dp(9), dp(9), dp(9));
                art.setBackgroundColor(0x1FFFFFFF);
            }
            refreshPlaying();
        }

        void setArt(final long album, String label) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setPadding(0, 0, 0, 0);
            art.setBackground(null);
            if (album == albumKey) {
                return;
            }
            albumKey = album;
            int size = dp(40);
            Bitmap hit = MusicArt.cached(album, size);
            art.setImageBitmap(hit);
            if (hit == null) {
                MusicArt.load(getContext(), album, label, size, b -> {
                    if (albumKey == album) {
                        art.setImageBitmap(b);
                    }
                });
            }
        }

        void refreshPlaying() {
            boolean on = false;
            if (controller != null && songId >= 0) {
                MediaItem cur = controller.getCurrentMediaItem();
                on = cur != null && String.valueOf(songId).equals(cur.mediaId);
            }
            playing.setVisibility(on ? VISIBLE : INVISIBLE);
            ((EqualizerView) playing).setRunning(on && controller != null && controller.isPlaying());
        }
    }

    /** A row's look, focused (white, dark words) or not (glass). */
    private static final class ConsoleScreenLight {
        static void apply(View row, boolean focused, TextView name, TextView sub, TextView length, TextView number) {
            com.limelight.pocket.ui.ConsoleScreen.lightRow(row, focused, name, sub);
            length.setTextColor(focused ? 0x99111114 : 0x80FFFFFF);
            number.setTextColor(focused ? 0x99111114 : 0x80FFFFFF);
            if (!focused) {
                row.setBackground(null);
            }
        }
    }

    /** Three little bars dancing beside the song that's playing. */
    static final class EqualizerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean running;
        private final RectF rect = new RectF();

        EqualizerView(Context c) {
            super(c);
        }

        void setRunning(boolean r) {
            running = r;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            paint.setColor(PocketTheme.accent(getContext()));
            float w = getWidth() / 5f;
            long t = android.os.SystemClock.uptimeMillis();
            for (int i = 0; i < 3; i++) {
                float k = running ? (float) (0.35 + 0.65 * Math.abs(Math.sin(t / (210.0 + i * 70) + i * 1.7))) : 0.35f + i * 0.15f;
                float h = getHeight() * k;
                float x = i * 2 * w;
                rect.set(x, getHeight() - h, x + w, getHeight());
                c.drawRoundRect(rect, w / 2, w / 2, paint);
            }
            if (running) {
                postInvalidateOnAnimation();
            }
        }
    }

    // ---- The bar at the bottom -------------------------------------------------------------------

    /** What's playing, small: its cover, name and artist, a thin progress line. */
    private final class MiniBar extends FrameLayout {
        final ImageView art;
        final TextView name;
        final TextView sub;
        final ImageView state;
        final View line;
        final View lineBack;
        long album = Long.MIN_VALUE;

        MiniBar(Context c) {
            super(c);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(0xCC16161C);
            bg.setStroke(Math.max(1, dp(1)), 0x1FFFFFFF);
            setBackground(bg);
            setElevation(dp(12));
            setVisibility(GONE);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), 0, dp(16), 0);
            art = new ImageView(c);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            rounded(art, dp(9));
            row.addView(art, new LinearLayout.LayoutParams(dp(46), dp(46)));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            name = new TextView(c);
            name.setTypeface(PocketFonts.semibold());
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            name.setTextColor(0xFFFFFFFF);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(name);
            sub = new TextView(c);
            sub.setTypeface(PocketFonts.medium());
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            sub.setTextColor(0x99FFFFFF);
            sub.setMaxLines(1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(sub);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wlp.setMarginStart(dp(12));
            row.addView(words, wlp);
            state = new ImageView(c);
            row.addView(state, new LinearLayout.LayoutParams(dp(20), dp(20)));
            addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            lineBack = new View(c);
            lineBack.setBackgroundColor(0x26FFFFFF);
            LayoutParams lb = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.BOTTOM);
            lb.leftMargin = dp(18);
            lb.rightMargin = dp(18);
            lb.bottomMargin = dp(5);
            addView(lineBack, lb);
            line = new View(c);
            line.setBackgroundColor(accent);
            line.setPivotX(0);
            LayoutParams ll = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.BOTTOM);
            ll.leftMargin = dp(18);
            ll.rightMargin = dp(18);
            ll.bottomMargin = dp(5);
            addView(line, ll);
        }

        void bind(MediaItem item, boolean isPlaying) {
            if (item == null) {
                if (getVisibility() == VISIBLE) {
                    animate().alpha(0f).translationY(dp(20)).setDuration(200).withEndAction(() -> setVisibility(GONE)).start();
                }
                return;
            }
            MediaMetadata m = item.mediaMetadata;
            name.setText(m.title);
            sub.setText(m.artist);
            state.setImageDrawable(PocketIcons.drawable(isPlaying ? "PAUSE" : "PLAY", dp(20), 0xE6FFFFFF));
            MusicLibrary.Song s = songOf(item);
            if (s != null && s.albumId != album) {
                album = s.albumId;
                final long want = album;
                MusicArt.load(getContext(), s.albumId, s.album, dp(46), b -> {
                    if (album == want) {
                        art.setImageBitmap(b);
                    }
                });
            }
            if (getVisibility() != VISIBLE) {
                setVisibility(VISIBLE);
                setAlpha(0f);
                setTranslationY(dp(20));
                animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(Spring.POP).start();
            }
            progress();
            // The playing song's mark in the lists
            refreshRows(grid);
            refreshRows(detailList);
        }

        void progress() {
            if (controller == null) {
                return;
            }
            long dur = controller.getDuration();
            float f = dur > 0 ? Math.max(0f, Math.min(1f, controller.getCurrentPosition() / (float) dur)) : 0f;
            line.setScaleX(f);
        }
    }

    private void refreshRows(RecyclerView list) {
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            if (v instanceof Row) {
                ((Row) v).refreshPlaying();
            }
        }
    }

    // ---- Now Playing -----------------------------------------------------------------------------

    /**
     * The song big: its cover on the left (it springs a little when the song changes), its name,
     * artist and album, the progress with the times, the controls (shuffle, back, play / pause,
     * next, repeat) and the next songs.
     */
    private final class NowPlaying extends FrameLayout {
        final ImageView cover;
        final TextView eyebrow;
        final TextView name;
        final TextView sub;
        final ProgressBarView bar;
        final TextView elapsed;
        final TextView remaining;
        final LinearLayout controls;
        final ImageView[] buttons = new ImageView[5];
        final LinearLayout upNext;
        boolean open;
        int focus = 2;
        long album = Long.MIN_VALUE;
        String mediaId;

        NowPlaying(Context c) {
            super(c);
            setVisibility(GONE);
            setBackgroundColor(0xD907070A);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(70), dp(40), dp(70), dp(70));
            cover = new ImageView(c);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            rounded(cover, dp(20));
            cover.setElevation(dp(24));
            row.addView(cover, new LinearLayout.LayoutParams(dp(330), dp(330)));

            LinearLayout right = new LinearLayout(c);
            right.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rlp.setMarginStart(dp(54));
            row.addView(right, rlp);

            eyebrow = new TextView(c);
            eyebrow.setText(R.string.pocket_music_now_playing);
            eyebrow.setTypeface(PocketFonts.bold());
            eyebrow.setLetterSpacing(0.16f);
            eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            eyebrow.setTextColor(accent);
            right.addView(eyebrow);
            name = new TextView(c);
            name.setTypeface(PocketFonts.display());
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
            name.setTextColor(0xFFFFFFFF);
            name.setLetterSpacing(-0.02f);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(8);
            right.addView(name, nlp);
            sub = new TextView(c);
            sub.setTypeface(PocketFonts.medium());
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            sub.setTextColor(0xB3FFFFFF);
            sub.setMaxLines(1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(6);
            right.addView(sub, slp);

            bar = new ProgressBarView(c);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16));
            blp.topMargin = dp(28);
            right.addView(bar, blp);
            LinearLayout times = new LinearLayout(c);
            times.setOrientation(LinearLayout.HORIZONTAL);
            elapsed = smallText(c);
            remaining = smallText(c);
            times.addView(elapsed, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            remaining.setGravity(Gravity.END);
            times.addView(remaining, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(4);
            right.addView(times, tlp);

            controls = new LinearLayout(c);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.CENTER_VERTICAL);
            String[] icons = {"SHUFFLE", "PREV", "PLAY", "NEXT", "REPEAT"};
            for (int i = 0; i < 5; i++) {
                ImageView b = new ImageView(c);
                boolean big = i == 2;
                int size = big ? dp(66) : dp(48);
                b.setScaleType(ImageView.ScaleType.CENTER);
                b.setImageDrawable(PocketIcons.drawable(icons[i], big ? dp(26) : dp(22), 0xFFFFFFFF));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMarginEnd(dp(18));
                controls.addView(b, lp);
                buttons[i] = b;
            }
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = dp(22);
            right.addView(controls, clp);

            TextView next = new TextView(c);
            next.setText(R.string.pocket_music_up_next);
            next.setTypeface(PocketFonts.bold());
            next.setLetterSpacing(0.12f);
            next.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            next.setTextColor(0x80FFFFFF);
            LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            xlp.topMargin = dp(30);
            right.addView(next, xlp);
            upNext = new LinearLayout(c);
            upNext.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ulp.topMargin = dp(8);
            right.addView(upNext, ulp);

            addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        private TextView smallText(Context c) {
            TextView t = new TextView(c);
            t.setTypeface(PocketFonts.medium());
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t.setTextColor(0x99FFFFFF);
            return t;
        }

        boolean isOpen() {
            return open;
        }

        void open() {
            if (open) {
                return;
            }
            open = true;
            PocketSounds.play(getContext(), PocketSounds.OPEN);
            grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            if (detailList != null) {
                detailList.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
            setVisibility(VISIBLE);
            setAlpha(0f);
            cover.setScaleX(0.86f);
            cover.setScaleY(0.86f);
            cover.setTranslationY(dp(30));
            animate().alpha(1f).setDuration(260).start();
            cover.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(460).setInterpolator(Spring.POP).start();
            focus = 2;
            styleButtons();
            if (controller != null) {
                bind(controller.getCurrentMediaItem());
            }
            mini.animate().alpha(0f).setDuration(160).start();
        }

        void close() {
            if (!open) {
                return;
            }
            open = false;
            PocketSounds.play(getContext(), PocketSounds.CLOSE);
            animate().alpha(0f).setDuration(200).withEndAction(() -> setVisibility(GONE)).start();
            grid.setDescendantFocusability(detail != null ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (detailList != null) {
                detailList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            mini.animate().alpha(1f).setDuration(200).start();
            updateHints();
        }

        /** The song changed by L1 / R1: the cover leans the way it went. */
        void nudge(int dir) {
            if (!open) {
                return;
            }
            cover.animate().cancel();
            cover.setTranslationX(dir * dp(26));
            cover.setRotationY(-dir * 8f);
            cover.animate().translationX(0).rotationY(0).setDuration(420).setInterpolator(Spring.POP).start();
        }

        void bind(MediaItem item) {
            if (item == null) {
                if (open) {
                    close();
                }
                return;
            }
            boolean changed = !item.mediaId.equals(mediaId);
            mediaId = item.mediaId;
            MediaMetadata m = item.mediaMetadata;
            name.setText(m.title);
            sub.setText((m.artist != null ? m.artist : "") + (m.albumTitle != null ? "  ·  " + m.albumTitle : ""));
            MusicLibrary.Song s = songOf(item);
            if (s != null && s.albumId != album) {
                album = s.albumId;
                final long want = album;
                MusicArt.load(getContext(), s.albumId, s.album, dp(330), b -> {
                    if (album == want) {
                        cover.setImageBitmap(b);
                        if (open) {
                            cover.setAlpha(0.4f);
                            cover.animate().alpha(1f).setDuration(260).start();
                        }
                    }
                });
            }
            if (changed && open && PocketPrefs.animationsEnabled(getContext())) {
                name.setAlpha(0f);
                name.setTranslationY(dp(8));
                name.animate().alpha(1f).translationY(0).setDuration(300).start();
            }
            if (controller != null) {
                buttons[2].setImageDrawable(PocketIcons.drawable(controller.isPlaying() ? "PAUSE" : "PLAY", dp(26),
                        focus == 2 ? 0xFF0B0B0D : 0xFFFFFFFF));
                buttons[0].setAlpha(controller.getShuffleModeEnabled() ? 1f : 0.45f);
                buttons[4].setAlpha(controller.getRepeatMode() != Player.REPEAT_MODE_OFF ? 1f : 0.45f);
            }
            fillUpNext();
            progress();
        }

        void fillUpNext() {
            upNext.removeAllViews();
            if (controller == null) {
                return;
            }
            int idx = controller.getCurrentMediaItemIndex();
            androidx.media3.common.Timeline tl = controller.getCurrentTimeline();
            int shown = 0;
            int at = idx;
            while (shown < 3 && !tl.isEmpty()) {
                at = tl.getNextWindowIndex(at, controller.getRepeatMode() == Player.REPEAT_MODE_ONE
                        ? Player.REPEAT_MODE_OFF : controller.getRepeatMode(), controller.getShuffleModeEnabled());
                if (at == androidx.media3.common.C.INDEX_UNSET || at == idx) {
                    break;
                }
                MediaItem mi = controller.getMediaItemAt(at);
                TextView t = new TextView(getContext());
                t.setTypeface(PocketFonts.medium());
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                t.setTextColor(shown == 0 ? 0xD9FFFFFF : 0x8CFFFFFF);
                t.setMaxLines(1);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setText(mi.mediaMetadata.title + "  —  " + mi.mediaMetadata.artist);
                t.setPadding(0, dp(4), 0, dp(4));
                upNext.addView(t);
                shown++;
            }
        }

        void progress() {
            if (controller == null) {
                return;
            }
            long dur = controller.getDuration();
            long pos = controller.getCurrentPosition();
            bar.set(dur > 0 ? pos / (float) dur : 0f, dur > 0 ? controller.getBufferedPosition() / (float) dur : 0f);
            elapsed.setText(MusicLibrary.clock(pos));
            remaining.setText(dur > 0 ? "-" + MusicLibrary.clock(dur - pos) : "");
        }

        void styleButtons() {
            for (int i = 0; i < buttons.length; i++) {
                boolean f = i == focus;
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(f ? 0xFFFFFFFF : (i == 2 ? 0x33FFFFFF : 0x00000000));
                buttons[i].setBackground(bg);
                String icon;
                switch (i) {
                    case 0:
                        icon = "SHUFFLE";
                        break;
                    case 1:
                        icon = "PREV";
                        break;
                    case 2:
                        icon = controller != null && controller.isPlaying() ? "PAUSE" : "PLAY";
                        break;
                    case 3:
                        icon = "NEXT";
                        break;
                    default:
                        icon = "REPEAT";
                        break;
                }
                buttons[i].setImageDrawable(PocketIcons.drawable(icon, i == 2 ? dp(26) : dp(22), f ? 0xFF0B0B0D : 0xFFFFFFFF));
                if (f) {
                    buttons[i].setScaleX(0.9f);
                    buttons[i].setScaleY(0.9f);
                    buttons[i].animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(Spring.POP).start();
                }
            }
        }

        void press(int i) {
            if (controller == null) {
                return;
            }
            pulse(buttons[i]);
            PocketSounds.play(getContext(), PocketSounds.SELECT);
            switch (i) {
                case 0:
                    controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());
                    break;
                case 1:
                    skip(-1);
                    break;
                case 2:
                    if (controller.isPlaying()) {
                        controller.pause();
                    }
                    else {
                        controller.play();
                    }
                    break;
                case 3:
                    skip(1);
                    break;
                default: {
                    int r = controller.getRepeatMode();
                    controller.setRepeatMode(r == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL
                            : r == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
                    break;
                }
            }
        }

        boolean handleKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            boolean first = down && e.getRepeatCount() == 0;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down && focus > 0) {
                        focus--;
                        PocketSounds.play(getContext(), PocketSounds.NAV);
                        styleButtons();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down && focus < buttons.length - 1) {
                        focus++;
                        PocketSounds.play(getContext(), PocketSounds.NAV);
                        styleButtons();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down && controller != null) {
                        // Up / down: 10 seconds back / on
                        long to = controller.getCurrentPosition() + (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ? 10000 : -10000);
                        controller.seekTo(Math.max(0, to));
                        progress();
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (first) {
                        press(focus);
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    if (first) {
                        skip(-1);
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    if (first) {
                        skip(1);
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_X:
                    if (first) {
                        press(0);
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                case KeyEvent.KEYCODE_BUTTON_Y:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (first) {
                        close();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    /** The song's progress: a track, what's loaded, what's played, and a dot where it is. */
    private final class ProgressBarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float played;
        private float loaded;

        ProgressBarView(Context c) {
            super(c);
        }

        void set(float played, float loaded) {
            this.played = Math.max(0, Math.min(1, played));
            this.loaded = Math.max(0, Math.min(1, loaded));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = dp(5);
            float y = getHeight() / 2f;
            float w = getWidth() - dp(14);
            float x0 = dp(7);
            paint.setShader(null);
            paint.setColor(0x26FFFFFF);
            r.set(x0, y - h / 2, x0 + w, y + h / 2);
            c.drawRoundRect(r, h, h, paint);
            paint.setColor(0x33FFFFFF);
            r.set(x0, y - h / 2, x0 + w * loaded, y + h / 2);
            c.drawRoundRect(r, h, h, paint);
            float px = x0 + w * played;
            paint.setShader(new LinearGradient(x0, 0, px, 0, Color.argb(200, Color.red(accent), Color.green(accent),
                    Color.blue(accent)), accent, Shader.TileMode.CLAMP));
            r.set(x0, y - h / 2, px, y + h / 2);
            c.drawRoundRect(r, h, h, paint);
            paint.setShader(null);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(px, y, dp(7), paint);
        }
    }

    @SuppressWarnings("unused")
    private static void sortByAdded(List<MusicLibrary.Song> songs) {
        Collections.sort(songs, (a, b) -> Long.compare(b.added, a.added));
    }
}
