package com.limelight.pocket.cloud;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.limelight.R;

/**
 * Keeps an app's web client (Discord, YouTube Music, Spotify) alive while it's in the background:
 * Android freezes a background process, which would stop the music and cut the voice call. One
 * per client, in that client's own process, with a quiet notification that brings it back.
 */
public abstract class WebClientKeepAlive extends Service {

    public static final class Discord extends WebClientKeepAlive {
    }

    public static final class YtMusic extends WebClientKeepAlive {
    }

    public static final class Spotify extends WebClientKeepAlive {
    }

    private static final String CHANNEL = "pocket_web_clients";
    private static final String EXTRA_ID = "id";

    private static Class<? extends WebClientKeepAlive> forId(String id) {
        switch (id) {
            case "discord":
                return Discord.class;
            case "ytmusic":
                return YtMusic.class;
            case "spotify":
                return Spotify.class;
            default:
                return null;
        }
    }

    public static void start(Context c, CloudService s) {
        Class<? extends WebClientKeepAlive> cls = forId(s.id);
        if (cls == null) {
            return;
        }
        try {
            ContextCompat.startForegroundService(c, new Intent(c, cls).putExtra(EXTRA_ID, s.id));
        } catch (RuntimeException ignored) {
            // Not allowed right now: it still works while on screen
        }
    }

    public static void stop(Context c) {
        for (Class<?> cls : new Class<?>[]{Discord.class, YtMusic.class, Spotify.class}) {
            try {
                c.stopService(new Intent(c, cls));
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String id = intent != null ? intent.getStringExtra(EXTRA_ID) : null;
        CloudService s = id != null ? CloudService.byId(id) : null;
        if (s == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.pocket_cloud_keepalive_channel),
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, s.player).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, s.id.hashCode(), open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_pocket_plus)
                .setContentTitle(getString(R.string.pocket_cloud_keepalive_title, getString(s.name)))
                .setContentText(getString(s.id.equals("discord") ? R.string.pocket_cloud_keepalive_voice
                        : R.string.pocket_cloud_keepalive_music))
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
                if (s.id.equals("discord") && Build.VERSION.SDK_INT >= 30
                        && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                }
                startForeground(7801, n, type);
            }
            else {
                startForeground(7801, n);
            }
        } catch (RuntimeException e) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
