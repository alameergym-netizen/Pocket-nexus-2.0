package com.limelight.pocket.music;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.limelight.pocket.PocketPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Pocket Nexus's music, playing on its own: in the background, over a game, with the screen
 * off, its controls in the notification and on the lock screen, and answering the headset's and
 * the handheld's media buttons (and the radial menu's). Android's media session makes it the
 * music any app sees. What was playing (the queue, the song, where in it) is kept and comes back
 * paused the next time.
 */
public class MusicService extends MediaSessionService {

    private static final String KEY_QUEUE = "pocket_music_queue";
    private static final String KEY_INDEX = "pocket_music_index";
    private static final String KEY_POSITION = "pocket_music_position";
    private static final String KEY_SHUFFLE = "pocket_music_shuffle";
    private static final String KEY_REPEAT = "pocket_music_repeat";

    private ExoPlayer player;
    private MediaSession session;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable saveLater = this::save;

    @Override
    public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                // Headphones out: it pauses (it doesn't go on through the speaker)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onEvents(@NonNull Player p, @NonNull Player.Events events) {
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED)) {
                    main.removeCallbacks(saveLater);
                    main.postDelayed(saveLater, 600);
                }
            }
        });
        session = new MediaSession.Builder(this, player)
                .setCallback(new MediaSession.Callback() {
                    @NonNull
                    @Override
                    public ListenableFuture<List<MediaItem>> onAddMediaItems(@NonNull MediaSession mediaSession,
                                                                             @NonNull MediaSession.ControllerInfo controller,
                                                                             @NonNull List<MediaItem> mediaItems) {
                        // A controller sends only the song's id: its file is found here
                        List<MediaItem> out = new ArrayList<>(mediaItems.size());
                        for (MediaItem m : mediaItems) {
                            out.add(resolve(m));
                        }
                        return Futures.immediateFuture(out);
                    }
                })
                .build();
        restore();
    }

    /** A song as Media3 plays it: its file, its words and its album's picture. */
    public static MediaItem item(MusicLibrary.Song s) {
        return new MediaItem.Builder()
                .setMediaId(String.valueOf(s.id))
                .setUri(s.uri())
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .setArtworkUri(MusicLibrary.albumArt(s.albumId))
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .build())
                .build();
    }

    private MediaItem resolve(MediaItem m) {
        if (m.localConfiguration != null) {
            return m;
        }
        try {
            long id = Long.parseLong(m.mediaId);
            MusicLibrary.Snapshot lib = MusicLibrary.cached();
            MusicLibrary.Song s = lib != null ? lib.byId.get(id) : null;
            if (s != null) {
                return item(s);
            }
            return m.buildUpon().setUri(Uri.withAppendedPath(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id))).build();
        } catch (NumberFormatException e) {
            return m;
        }
    }

    /** What was playing last time, back in place (paused). */
    private void restore() {
        SharedPreferences p = PocketPrefs.prefs(this);
        String q = p.getString(KEY_QUEUE, "");
        player.setShuffleModeEnabled(p.getBoolean(KEY_SHUFFLE, false));
        player.setRepeatMode(p.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF));
        if (q.isEmpty()) {
            return;
        }
        final int index = p.getInt(KEY_INDEX, 0);
        final long position = p.getLong(KEY_POSITION, 0);
        final String[] ids = q.split(",");
        final Context app = getApplicationContext();
        new Thread(() -> {
            MusicLibrary.Snapshot lib = MusicLibrary.load(app, false);
            final List<MediaItem> items = new ArrayList<>();
            int at = 0;
            for (int i = 0; i < ids.length; i++) {
                try {
                    MusicLibrary.Song s = lib.byId.get(Long.parseLong(ids[i]));
                    if (s != null) {
                        if (i == index) {
                            at = items.size();
                        }
                        items.add(item(s));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            final int start = at;
            main.post(() -> {
                if (player == null || items.isEmpty() || player.getMediaItemCount() > 0) {
                    return;
                }
                player.setMediaItems(items, start, position);
                player.prepare();
            });
        }, "pocket-music-restore").start();
    }

    private void save() {
        if (player == null) {
            return;
        }
        int n = player.getMediaItemCount();
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(player.getMediaItemAt(i).mediaId);
        }
        PocketPrefs.prefs(this).edit()
                .putString(KEY_QUEUE, TextUtils.join(",", ids))
                .putInt(KEY_INDEX, player.getCurrentMediaItemIndex())
                .putLong(KEY_POSITION, Math.max(0, player.getCurrentPosition()))
                .putBoolean(KEY_SHUFFLE, player.getShuffleModeEnabled())
                .putInt(KEY_REPEAT, player.getRepeatMode())
                .apply();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        save();
        // Swiped away while paused: nothing keeps it; while playing it goes on
        if (player == null || !player.getPlayWhenReady() || player.getMediaItemCount() == 0) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        save();
        if (session != null) {
            session.release();
            session = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
