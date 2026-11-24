package com.limelight.pocket;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.PixelCopy;
import android.view.SurfaceView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Saves the current stream picture to the handheld's gallery (Pictures/Artemis Pocket). */
public final class StreamScreenshot {
    private StreamScreenshot() {}

    public interface Callback {
        /** true when saved. Called on the main thread. */
        void onDone(boolean saved);
    }

    public static void capture(final Context context, SurfaceView view, final String gameName, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || view == null
                || view.getWidth() <= 0 || view.getHeight() <= 0 || !view.getHolder().getSurface().isValid()) {
            main.post(() -> callback.onDone(false));
            return;
        }
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            main.post(() -> callback.onDone(false));
            return;
        }
        final HandlerThread worker = new HandlerThread("PocketScreenshot");
        worker.start();
        final Handler workerHandler = new Handler(worker.getLooper());
        final Context app = context.getApplicationContext();
        try {
            PixelCopy.request(view, bitmap, result -> {
                boolean saved = result == PixelCopy.SUCCESS && save(app, bitmap, gameName);
                bitmap.recycle();
                worker.quitSafely();
                main.post(() -> callback.onDone(saved));
            }, workerHandler);
        } catch (RuntimeException e) {
            worker.quitSafely();
            main.post(() -> callback.onDone(false));
        }
    }

    // ---- The last session's picture (the PC's card on Streaming shows where you left off) ------

    private static final String KEY_SESSION_APP = "pocket_session_app_";
    private static final String KEY_SESSION_AT = "pocket_session_at_";
    private static final long SESSION_KEEP_MS = 14L * 24 * 60 * 60 * 1000;
    private static final android.util.LruCache<String, Bitmap> SESSIONS = new android.util.LruCache<>(3);

    private static java.io.File sessionFile(Context c, String uuid) {
        java.io.File dir = new java.io.File(c.getFilesDir(), "pocket_sessions");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new java.io.File(dir, uuid.replaceAll("[^A-Za-z0-9_-]", "_") + ".jpg");
    }

    /**
     * A frame of the stream as it's left (small, kept per PC with its game): quietly, nothing
     * shown, nothing saved to the gallery.
     */
    public static void captureSession(Context context, SurfaceView view, final String uuid, final int appId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || view == null || uuid == null || appId == 0
                || view.getWidth() <= 0 || view.getHeight() <= 0 || !view.getHolder().getSurface().isValid()) {
            return;
        }
        final Context app = context.getApplicationContext();
        int w = Math.min(960, view.getWidth());
        int h = Math.max(1, Math.round(w * view.getHeight() / (float) view.getWidth()));
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            return;
        }
        final HandlerThread worker = new HandlerThread("PocketSessionShot");
        worker.start();
        try {
            PixelCopy.request(view, bitmap, result -> {
                try {
                    if (result == PixelCopy.SUCCESS && !mostlyBlack(bitmap)) {
                        java.io.File f = sessionFile(app, uuid);
                        java.io.File tmp = new java.io.File(f.getPath() + ".part");
                        try (OutputStream out = new java.io.FileOutputStream(tmp)) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 84, out);
                        }
                        if (tmp.renameTo(f)) {
                            com.limelight.pocket.PocketPrefs.prefs(app).edit().putInt(KEY_SESSION_APP + uuid, appId)
                                    .putLong(KEY_SESSION_AT + uuid, System.currentTimeMillis()).apply();
                            SESSIONS.remove(uuid);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    bitmap.recycle();
                    worker.quitSafely();
                }
            }, new Handler(worker.getLooper()));
        } catch (RuntimeException e) {
            worker.quitSafely();
        }
    }

    /** A dark loading screen or a black frame isn't worth keeping. */
    private static boolean mostlyBlack(Bitmap b) {
        int bright = 0;
        int n = 0;
        for (int y = b.getHeight() / 10; y < b.getHeight(); y += Math.max(1, b.getHeight() / 9)) {
            for (int x = b.getWidth() / 10; x < b.getWidth(); x += Math.max(1, b.getWidth() / 16)) {
                int p = b.getPixel(x, y);
                int l = (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p)) / 3;
                if (l > 28) {
                    bright++;
                }
                n++;
            }
        }
        return n == 0 || bright < n / 8;
    }

    /** The last session's picture for this PC and game (recent enough), or null. */
    public static Bitmap sessionShot(Context c, String uuid, int appId) {
        if (uuid == null || appId == 0) {
            return null;
        }
        android.content.SharedPreferences p = com.limelight.pocket.PocketPrefs.prefs(c);
        if (p.getInt(KEY_SESSION_APP + uuid, 0) != appId
                || System.currentTimeMillis() - p.getLong(KEY_SESSION_AT + uuid, 0) > SESSION_KEEP_MS) {
            return null;
        }
        Bitmap hit = SESSIONS.get(uuid);
        if (hit != null) {
            return hit;
        }
        java.io.File f = sessionFile(c, uuid);
        if (!f.isFile()) {
            return null;
        }
        try {
            Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getPath());
            if (b != null) {
                SESSIONS.put(uuid, b);
            }
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static boolean save(Context context, Bitmap bitmap, String gameName) {
        String safe = gameName == null ? "Stream" : gameName.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        if (safe.isEmpty()) {
            safe = "Stream";
        }
        String name = safe + " " + new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(new Date()) + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Pocket Nexus");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        ContentResolver resolver = context.getContentResolver();
        Uri uri = null;
        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return false;
            }
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new java.io.IOException("write failed");
                }
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return true;
        } catch (Exception e) {
            if (uri != null) {
                try {
                    resolver.delete(uri, null, null);
                } catch (RuntimeException ignored) {
                }
            }
            return false;
        }
    }
}
