package com.limelight.pocket;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.Base64;

import com.limelight.Game;

/**
 * Seamless suspend and resume.
 *
 * Every stream remembers how it was started. When the stream window goes away without the
 * player ending it (screen off, Home, the handheld sleeping) the game keeps running on the PC
 * and the ticket is "armed". The next console screen that comes back starts the same stream
 * again on its own. Quality changes reuse the same path: the stream ends, then comes straight
 * back with the new bitrate or frame rate (the game never stops on the PC).
 */
public final class ResumeTicket {
    private ResumeTicket() {}

    public static final String EXTRA_BITRATE = "pocket_bitrate_override";
    public static final String EXTRA_FPS = "pocket_fps_override";
    public static final String EXTRA_ADAPTIVE_STEPS = "pocket_adaptive_steps";

    public static final int REASON_SUSPEND = 0;
    public static final int REASON_QUALITY = 1;
    public static final int REASON_ADAPTIVE = 2;
    /** The stream dropped by itself: reconnect to the same game. */
    public static final int REASON_RECOVER = 3;
    /** Signed in to Windows: move from the main screen to a fresh virtual display. */
    public static final int REASON_SIGNIN = 4;
    /** How many reconnects in a row (so a dead link doesn't loop forever). */
    public static final String EXTRA_RECOVER = "pocket_recover_attempt";

    private static final String PREFS = "pocket_resume";
    private static final String KEY_EXTRAS = "extras";
    private static final String KEY_ARMED_AT = "armed_at_wall";
    private static final String KEY_REASON = "reason";
    /** A suspended game is picked up again for this long. */
    private static final long SUSPEND_MAX_AGE_MS = 3 * 60 * 60 * 1000L;
    /** Quality switches must happen right away or not at all. */
    private static final long QUALITY_MAX_AGE_MS = 60 * 1000L;

    private static Bundle memoryExtras;
    private static final com.limelight.pocket.core.HorizonResumeOwnership OWNERSHIP =
            new com.limelight.pocket.core.HorizonResumeOwnership();

    public static final class Taken {
        public final Intent intent;
        public final int reason;
        private final com.limelight.pocket.core.HorizonResumeOwnership.Lease lease;
        private Taken(Intent intent, int reason, com.limelight.pocket.core.HorizonResumeOwnership.Lease lease) {
            this.intent = intent; this.reason = reason; this.lease = lease;
        }
    }
    private static long epoch() {
        return com.limelight.pocket.core.HorizonLaunchRequests.shared().generation();
    }
    private static long maxAge(int reason) {
        return reason == REASON_SUSPEND ? SUSPEND_MAX_AGE_MS : QUALITY_MAX_AGE_MS;
    }
    private static final com.limelight.pocket.core.HorizonShutdownBarrier SHUTDOWN =
            new com.limelight.pocket.core.HorizonShutdownBarrier();

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** A stream started: remember how to start it again and forget any older ticket. */
    public static synchronized void remember(Context context, Intent gameIntent) {
        OWNERSHIP.invalidate();
        try {
            Bundle extras = gameIntent.getExtras() != null ? new Bundle(gameIntent.getExtras()) : new Bundle();
            extras.remove(com.limelight.pocket.pclib.PcLaunchTicket.EXTRA_TOKEN);
            memoryExtras = extras;
            prefs(context).edit()
                    .putString(KEY_EXTRAS, encode(extras))
                    .putLong(KEY_ARMED_AT, 0)
                    .apply();
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * The stream is going away but the game should come back. bitrateKbps / fps of 0 keep the
     * current values.
     */
    public static void arm(Context context, int reason, int bitrateKbps, int fps, int adaptiveSteps) {
        arm(context, reason, bitrateKbps, fps, adaptiveSteps, -1);
    }

    /** recoverAttempt >= 0 marks a reconnect after a drop; -1 clears the count. */
    public static synchronized void arm(Context context, int reason, int bitrateKbps, int fps, int adaptiveSteps, int recoverAttempt) {
        OWNERSHIP.invalidate();
        try {
            Bundle extras = memoryExtras != null ? new Bundle(memoryExtras) : decode(prefs(context).getString(KEY_EXTRAS, null));
            if (extras == null) {
                return;
            }
            if (bitrateKbps > 0) {
                extras.putInt(EXTRA_BITRATE, bitrateKbps);
            }
            else if (bitrateKbps < 0) {
                extras.remove(EXTRA_BITRATE);
            }
            if (fps > 0) {
                extras.putInt(EXTRA_FPS, fps);
            }
            else if (fps < 0) {
                extras.remove(EXTRA_FPS);
            }
            if (adaptiveSteps >= 0) {
                extras.putInt(EXTRA_ADAPTIVE_STEPS, adaptiveSteps);
            }
            if (recoverAttempt >= 0) {
                extras.putInt(EXTRA_RECOVER, recoverAttempt);
            }
            else {
                extras.remove(EXTRA_RECOVER);
            }
            memoryExtras = extras;
            prefs(context).edit()
                    .putString(KEY_EXTRAS, encode(extras))
                    .putLong(KEY_ARMED_AT, System.currentTimeMillis())
                    .putInt(KEY_REASON, reason)
                    .apply();
        } catch (RuntimeException ignored) {
        }
    }

    public static synchronized void disarm(Context context) {
        OWNERSHIP.invalidate();
        prefs(context).edit().putLong(KEY_ARMED_AT, 0).apply();
    }

    public static synchronized boolean isArmed(Context context) {
        return com.limelight.pocket.core.HorizonResumeOwnership.validAge(
                prefs(context).getLong(KEY_ARMED_AT, 0), maxAge(reason(context)), System.currentTimeMillis());
    }

    public static int reason(Context context) {
        return prefs(context).getInt(KEY_REASON, REASON_SUSPEND);
    }

    /** Take and disarm atomically. Restore is allowed only for this exact record and epoch. */
    public static synchronized Taken takeOwned(Context context) {
        long armedAt = prefs(context).getLong(KEY_ARMED_AT, 0);
        int reason = reason(context);
        com.limelight.pocket.core.HorizonResumeOwnership.Lease lease =
                OWNERSHIP.take(armedAt, maxAge(reason), epoch(), System.currentTimeMillis());
        if (lease == null) return null;
        Bundle extras = memoryExtras != null ? new Bundle(memoryExtras) : decode(prefs(context).getString(KEY_EXTRAS, null));
        prefs(context).edit().putLong(KEY_ARMED_AT, 0).apply();
        if (extras == null || extras.getString(Game.EXTRA_HOST) == null) {
            OWNERSHIP.discard(lease);
            return null;
        }
        extras.remove(com.limelight.pocket.pclib.PcLaunchTicket.EXTRA_TOKEN);
        Intent intent = new Intent(context, Game.class);
        intent.putExtras(extras);
        return new Taken(intent, reason, lease);
    }

    public static synchronized boolean current(Taken taken) {
        return taken != null && OWNERSHIP.current(taken.lease, epoch(), System.currentTimeMillis());
    }

    public static synchronized boolean consume(Taken taken) {
        return taken != null && OWNERSHIP.consume(taken.lease, epoch(), System.currentTimeMillis());
    }

    public static synchronized void discard(Taken taken) {
        if (taken != null) OWNERSHIP.discard(taken.lease);
    }

    /** Restore original age, never rearm a superseded, expired or already consumed record. */
    public static synchronized void restore(Context context, Taken taken) {
        if (taken != null && prefs(context).getLong(KEY_ARMED_AT, 0) == 0 && consume(taken)) {
            prefs(context).edit().putLong(KEY_ARMED_AT, taken.lease.armedAt)
                    .putInt(KEY_REASON, taken.reason).apply();
        } else {
            discard(taken);
        }
    }

    public static String appName(Intent intent) {
        String name = intent != null ? intent.getStringExtra(Game.EXTRA_APP_NAME) : null;
        return name != null ? name : "";
    }

    public static boolean isUsb(Intent intent) {
        return intent != null && UsbLink.isUsbAddress(intent.getStringExtra(Game.EXTRA_HOST));
    }

    // ---- The previous stream's shutdown (so the next one doesn't race it) ----------------

    public static long markStopStarted() {
        return SHUTDOWN.begin(SystemClock.elapsedRealtime());
    }

    public static void markStopFinished(long owner) {
        SHUTDOWN.finish(owner);
    }

    /** Bounded UI wait only; native connection exclusivity is enforced separately. */
    public static boolean isPreviousStreamStopping() {
        return SHUTDOWN.waiting(SystemClock.elapsedRealtime());
    }

    // ---- Storage ------------------------------------------------------------------------

    private static String encode(Bundle bundle) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(bundle);
            return Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP);
        } finally {
            parcel.recycle();
        }
    }

    private static Bundle decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        Parcel parcel = Parcel.obtain();
        try {
            byte[] data = Base64.decode(encoded, Base64.NO_WRAP);
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            return parcel.readBundle(ResumeTicket.class.getClassLoader());
        } catch (RuntimeException e) {
            return null;
        } finally {
            parcel.recycle();
        }
    }
}
