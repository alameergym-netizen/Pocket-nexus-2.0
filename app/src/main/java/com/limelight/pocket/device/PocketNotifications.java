package com.limelight.pocket.device;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Notification access: the handheld's notifications, shown in Pocket Nexus's own panel (open
 * one with A, clear it with X) and announced on the home screens as they come in.
 */
public class PocketNotifications extends NotificationListenerService {

    /** A notification as the panel shows it. */
    public static final class Note {
        public final String key;
        public final String packageName;
        public final String app;
        public final String title;
        public String text;
        public final long when;
        public final boolean clearable;
        final PendingIntent open;
        /** Its reply (a message: Discord, WhatsApp, Telegram...), or null. */
        Notification.Action reply;
        android.app.RemoteInput replyInput;
        /** The sender's picture, when it has one. */
        public android.graphics.drawable.Icon picture;
        /** The conversation's name ("#general", a group), when it's one. */
        public String conversation;
        /** The last few messages, oldest first, each "Name: text". */
        public final List<String> history = new ArrayList<>();
        /** The newest message is one the user sent (an update after a reply, not news). */
        boolean fromMe;

        public boolean canReply() {
            return reply != null && replyInput != null;
        }

        Note(String key, String packageName, String app, String title, String text, long when, boolean clearable,
             PendingIntent open) {
            this.key = key;
            this.packageName = packageName;
            this.app = app;
            this.title = title;
            this.text = text;
            this.when = when;
            this.clearable = clearable;
            this.open = open;
        }
    }

    /** Told (on the main thread) when a notification comes in. */
    public interface Listener {
        void onNotification(Note note);
    }

    private static PocketNotifications connected;
    private static Listener listener;
    private static Listener messages;
    private static Note lastMessage;
    private static String lastBanner;
    private static long repliedAt;
    private static String repliedKey;

    /** Told (on the main thread) when a message comes in that can be answered. */
    public static void setMessageListener(Listener l) {
        messages = l;
    }

    /** The newest message that can be answered (or null). */
    public static Note lastMessage() {
        return lastMessage;
    }

    /** Message pop-ups over every game (with a quick reply), on unless turned off. */
    public static boolean popupsEnabled(Context c) {
        return com.limelight.pocket.PocketPrefs.prefs(c).getBoolean("pocket_message_popups", true);
    }

    /**
     * Answers a message with its own reply action, as if typed in its notification. True when
     * the app took it.
     */
    public static boolean reply(Context context, Note note, CharSequence text) {
        if (note == null || !note.canReply() || text == null || text.length() == 0) {
            return false;
        }
        Intent fill = new Intent();
        Bundle results = new Bundle();
        results.putCharSequence(note.replyInput.getResultKey(), text);
        android.app.RemoteInput.addResultsToIntent(note.reply.getRemoteInputs(), fill, results);
        if (Build.VERSION.SDK_INT >= 28) {
            android.app.RemoteInput.setResultsSource(fill, android.app.RemoteInput.SOURCE_FREE_FORM_INPUT);
        }
        try {
            note.reply.actionIntent.send(context, 0, fill);
            repliedAt = System.currentTimeMillis();
            repliedKey = note.key;
            return true;
        } catch (PendingIntent.CanceledException | RuntimeException e) {
            return false;
        }
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void setListener(Listener l) {
        listener = l;
    }

    /** True when the user gave notification access. */
    public static boolean allowed(Context context) {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.getPackageName());
    }

    /** Opens the system screen where it's given. */
    public static void askForAccess(Context context) {
        Intent i;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            new ComponentName(context, PocketNotifications.class).flattenToString());
        }
        else {
            i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(i);
        } catch (RuntimeException e) {
            try {
                context.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void onListenerConnected() {
        connected = this;
    }

    @Override
    public void onListenerDisconnected() {
        if (connected == this) {
            connected = null;
        }
    }

    @Override
    public void onDestroy() {
        if (connected == this) {
            connected = null;
        }
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        final Note n = toNote(this, sbn);
        if (n == null || !n.clearable) {
            return;
        }
        if (n.canReply()) {
            lastMessage = n;
            // A pop-up for news only: not an update after the user's own reply, not the same again
            String sig = n.key + "|" + n.text;
            boolean echo = n.fromMe || (n.key.equals(repliedKey) && System.currentTimeMillis() - repliedAt < 4000);
            if (echo) {
                return; // the user's own reply coming back: not news
            }
            if (!sig.equals(lastBanner) && popupsEnabled(this) && messages != null) {
                lastBanner = sig;
                MAIN.post(() -> {
                    Listener m = messages;
                    if (m != null) {
                        m.onNotification(n);
                    }
                });
                return; // its pop-up over everything says it (not Home's too)
            }
        }
        MAIN.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onNotification(n);
            }
        });
    }

    private static Note toNote(Context context, StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName().equals(context.getPackageName())) {
            return null;
        }
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return null;
        }
        Bundle extras = n.extras;
        CharSequence title = extras != null ? extras.getCharSequence(Notification.EXTRA_TITLE) : null;
        CharSequence text = extras != null ? extras.getCharSequence(Notification.EXTRA_TEXT) : null;
        if (extras != null) {
            // The whole of it: the expanded title and text (what the shade shows when opened),
            // or an inbox's lines, rather than the one-line summary the collapsed view shows
            CharSequence bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
            if (longer(bigTitle, title)) {
                title = bigTitle;
            }
            CharSequence big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (longer(big, text)) {
                text = big;
            }
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null && lines.length > 0) {
                StringBuilder all = new StringBuilder();
                for (CharSequence l : lines) {
                    if (l != null && l.length() > 0) {
                        all.append(all.length() > 0 ? "\n" : "").append(l);
                    }
                }
                if (longer(all, text)) {
                    text = all.toString();
                }
            }
        }
        if (title == null && text == null) {
            return null;
        }
        String app = sbn.getPackageName();
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(app, 0);
            app = pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
        }
        Note note = new Note(sbn.getKey(), sbn.getPackageName(), app, title != null ? title.toString() : "",
                text != null ? text.toString() : "", sbn.getPostTime(), sbn.isClearable(), n.contentIntent);
        // A message: its reply, the sender's picture and the last few messages
        if (n.actions != null) {
            for (Notification.Action a : n.actions) {
                android.app.RemoteInput[] inputs = a.getRemoteInputs();
                if (inputs == null) {
                    continue;
                }
                for (android.app.RemoteInput in : inputs) {
                    if (in.getAllowFreeFormInput()) {
                        note.reply = a;
                        note.replyInput = in;
                        break;
                    }
                }
                if (note.reply != null) {
                    break;
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            note.picture = n.getLargeIcon();
        }
        try {
            androidx.core.app.NotificationCompat.MessagingStyle ms =
                    androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);
            if (ms != null) {
                CharSequence conv = ms.getConversationTitle();
                note.conversation = conv != null ? conv.toString() : null;
                List<androidx.core.app.NotificationCompat.MessagingStyle.Message> msgs = ms.getMessages();
                int from = Math.max(0, msgs.size() - 4);
                for (int i = from; i < msgs.size(); i++) {
                    androidx.core.app.NotificationCompat.MessagingStyle.Message m = msgs.get(i);
                    CharSequence who = m.getPerson() != null ? m.getPerson().getName() : null;
                    note.history.add((who != null ? who + ": " : "") + (m.getText() != null ? m.getText() : ""));
                }
                if (!msgs.isEmpty()) {
                    androidx.core.app.NotificationCompat.MessagingStyle.Message last = msgs.get(msgs.size() - 1);
                    // The newest message whole (the summary line can be cut or say "2 new messages")
                    if (last.getText() != null && last.getText().length() > 0) {
                        note.text = last.getText().toString();
                    }
                    note.fromMe = last.getPerson() == null
                            || (ms.getUser() != null && ms.getUser().getName() != null && last.getPerson().getName() != null
                            && ms.getUser().getName().toString().equals(last.getPerson().getName().toString())
                            && (ms.getUser().getKey() == null || ms.getUser().getKey().equals(last.getPerson().getKey())));
                }
            }
        } catch (RuntimeException ignored) {
        }
        return note;
    }

    private static boolean longer(CharSequence a, CharSequence b) {
        return a != null && a.toString().trim().length() > (b != null ? b.toString().trim().length() : 0);
    }

    /** The notifications now, newest first (empty without access). */
    public static List<Note> current(Context context) {
        List<Note> out = new ArrayList<>();
        PocketNotifications s = connected;
        if (s == null) {
            return out;
        }
        StatusBarNotification[] active;
        try {
            active = s.getActiveNotifications();
        } catch (RuntimeException e) {
            return out;
        }
        if (active == null) {
            return out;
        }
        for (StatusBarNotification sbn : active) {
            Note n = toNote(context, sbn);
            if (n != null) {
                out.add(n);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.when, a.when));
        return out;
    }

    /** Opens what the notification is about. */
    public static boolean open(Context context, Note note) {
        if (note.open == null) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(note.packageName);
            if (launch == null) {
                return false;
            }
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // Lets it open its screen while Pocket Nexus is the one in front
                Bundle options = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle();
                note.open.send(context, 0, null, null, null, null, options);
            }
            else {
                note.open.send();
            }
            dismiss(note);
            return true;
        } catch (PendingIntent.CanceledException | RuntimeException e) {
            return false;
        }
    }

    public static void dismiss(Note note) {
        PocketNotifications s = connected;
        if (s != null && note.clearable) {
            try {
                s.cancelNotification(note.key);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static void dismissAll() {
        PocketNotifications s = connected;
        if (s != null) {
            try {
                s.cancelAllNotifications();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
