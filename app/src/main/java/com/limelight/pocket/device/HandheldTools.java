package com.limelight.pocket.device;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Two things for the device panel: the music playing in any app (YouTube Music, Spotify...),
 * with play / pause, next and back, read from Android's media sessions (the notification access
 * Pocket Nexus already asks for), and the handheld's own tools found on it (the maker's
 * settings with the fan and performance modes, ClusterTune, PULSE, Lossless Scaling's frame
 * generation), one press away.
 */
public final class HandheldTools {
    private HandheldTools() {}

    /** Words in an app's name (made plain: lower case letters and digits) that make it a handheld tool. */
    private static final String[] CONTAINS = {"retroid", "handheld", "fancontrol", "fanspeed", "fanmode",
            "fansettings", "clustertune", "lossless", "lsfg", "performancemode", "odinsettings", "aynsettings",
            "gamespace", "gameassistant"};
    /** Whole names that are one (too short or too common a word to look for inside names). */
    private static final String[] EXACT = {"fan", "pulse", "performance", "pulseperformance"};

    /** The playing (or last paused) media session, or null (none, or no access). */
    static MediaController controller(Context c) {
        if (!PocketNotifications.allowed(c)) {
            return null; // no access to the sessions without the notification access
        }
        try {
            MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (m == null) {
                return null;
            }
            List<MediaController> all = m.getActiveSessions(new ComponentName(c, PocketNotifications.class));
            MediaController paused = null;
            for (MediaController mc : all) {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    return mc;
                }
                if (paused == null && mc.getMetadata() != null) {
                    paused = mc;
                }
            }
            return paused;
        } catch (RuntimeException e) {
            return null; // no notification access
        }
    }

    /** The session playing right now (not a paused one), or null. */
    public static MediaController playing(Context c) {
        MediaController mc = controller(c);
        PlaybackState st = mc != null ? mc.getPlaybackState() : null;
        return st != null && st.getState() == PlaybackState.STATE_PLAYING ? mc : null;
    }

    /** "Title · Artist" of what it plays, or null when it says nothing. */
    public static String songLine(MediaController mc) {
        MediaMetadata md = mc.getMetadata();
        if (md == null) {
            return null;
        }
        String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (title == null || title.isEmpty()) {
            return null;
        }
        return artist != null && !artist.isEmpty() ? title + "  ·  " + artist : title;
    }

    private static String nowPlaying(Context c, MediaController mc) {
        MediaMetadata md = mc.getMetadata();
        String title = md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
        String artist = md != null ? md.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;
        String app = mc.getPackageName();
        try {
            PackageManager pm = c.getPackageManager();
            app = pm.getApplicationLabel(pm.getApplicationInfo(mc.getPackageName(), 0)).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
        }
        StringBuilder b = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            b.append(title);
        }
        if (artist != null && !artist.isEmpty()) {
            b.append(b.length() > 0 ? "  ·  " : "").append(artist);
        }
        return b.length() > 0 ? b + "  (" + app + ")" : app;
    }

    /** The music rows: what's playing and its buttons (nothing when nothing plays). */
    public static void addMusic(final ConsoleActivity a, List<StreamMenuView.Item> items) {
        final MediaController mc = controller(a);
        if (mc == null) {
            return;
        }
        PlaybackState st = mc.getPlaybackState();
        final boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        items.add(StreamMenuView.Item.header(a.getString(R.string.pocket_device_music)));
        items.add(StreamMenuView.Item.note(nowPlaying(a, mc)));
        items.add(StreamMenuView.Item.repeatable(a.getString(playing ? R.string.pocket_device_music_pause
                : R.string.pocket_device_music_play), () -> {
            PlaybackState now = mc.getPlaybackState();
            if (now != null && now.getState() == PlaybackState.STATE_PLAYING) {
                mc.getTransportControls().pause();
            }
            else {
                mc.getTransportControls().play();
            }
            PocketSounds.play(a, PocketSounds.SELECT);
        }).withIcon("PLAY_PAUSE"));
        items.add(StreamMenuView.Item.repeatable(a.getString(R.string.pocket_device_music_next), () -> {
            mc.getTransportControls().skipToNext();
            PocketSounds.play(a, PocketSounds.NAV);
        }).withIcon("PLAY"));
        items.add(StreamMenuView.Item.repeatable(a.getString(R.string.pocket_device_music_previous), () -> {
            mc.getTransportControls().skipToPrevious();
            PocketSounds.play(a, PocketSounds.NAV);
        }));
        items.add(StreamMenuView.Item.action(a.getString(R.string.pocket_device_music_open), () -> {
            Intent open = a.getPackageManager().getLaunchIntentForPackage(mc.getPackageName());
            if (open != null) {
                try {
                    a.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException ignored) {
                }
            }
        }));
    }

    /** The handheld's tools installed here, each a row that opens it (nothing when none). */
    public static void addTools(final ConsoleActivity a, List<StreamMenuView.Item> items) {
        List<String[]> found = find(a);
        if (found.isEmpty()) {
            return;
        }
        items.add(StreamMenuView.Item.header(a.getString(R.string.pocket_device_handheld_tools)));
        for (final String[] app : found) {
            items.add(StreamMenuView.Item.action(app[2], () -> {
                try {
                    a.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(new ComponentName(app[0], app[1])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    Toast.makeText(a, R.string.pocket_apps_cant_open, Toast.LENGTH_SHORT).show();
                }
            }).withIcon("SLIDERS"));
        }
    }

    /** {package, activity, name} of every handheld tool with a launcher icon here. */
    public static List<String[]> find(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            PackageManager pm = c.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                if (ri.activityInfo == null || c.getPackageName().equals(ri.activityInfo.packageName)) {
                    continue;
                }
                String label = String.valueOf(ri.loadLabel(pm));
                String plain = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                String pkg = ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
                boolean tool = pkg.contains("retroid");
                for (String w : CONTAINS) {
                    tool |= plain.contains(w);
                }
                for (String w : EXACT) {
                    tool |= plain.equals(w);
                }
                if (tool) {
                    out.add(new String[]{ri.activityInfo.packageName, ri.activityInfo.name, label});
                }
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }
}
