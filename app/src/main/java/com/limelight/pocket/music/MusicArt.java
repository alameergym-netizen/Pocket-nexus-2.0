package com.limelight.pocket.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;

import com.limelight.pocket.ui.PocketIcons;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Album covers, read from the songs' own tags (Android keeps them per album), at the size a
 * screen needs and kept in memory. An album without one gets a cover drawn from its name (two
 * colours and a note), so no tile is ever blank.
 */
public final class MusicArt {
    private MusicArt() {}

    public interface Callback {
        /** On the main thread. */
        void onArt(Bitmap art);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Set<String> MISSING = new HashSet<>();
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(48 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private static String key(long albumId, int size) {
        return albumId + "@" + size;
    }

    /** Already in memory, or null. */
    public static Bitmap cached(long albumId, int size) {
        return CACHE.get(key(albumId, size));
    }

    /** The album's cover (or one drawn for it when it has none), about size px square. */
    public static void load(Context context, final long albumId, final String name, final int size, final Callback cb) {
        Bitmap hit = cached(albumId, size);
        if (hit != null) {
            cb.onArt(hit);
            return;
        }
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            Bitmap b = read(app, albumId, size);
            if (b == null) {
                b = drawn(name, size);
            }
            CACHE.put(key(albumId, size), b);
            final Bitmap out = b;
            MAIN.post(() -> cb.onArt(out));
        });
    }

    /** Blocking: the cover from the tags, or null when the album has none. */
    public static Bitmap read(Context c, long albumId, int size) {
        String k = String.valueOf(albumId);
        synchronized (MISSING) {
            if (MISSING.contains(k)) {
                return null;
            }
        }
        Bitmap b = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                b = c.getContentResolver().loadThumbnail(MusicLibrary.albumArt(albumId), new Size(size, size), null);
            }
            else {
                try (InputStream in = c.getContentResolver().openInputStream(MusicLibrary.albumArt(albumId))) {
                    if (in != null) {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        b = BitmapFactory.decodeStream(in, null, o);
                    }
                }
            }
        } catch (Exception | OutOfMemoryError e) {
            b = null;
        }
        if (b == null) {
            synchronized (MISSING) {
                MISSING.add(k);
            }
            return null;
        }
        if (b.getWidth() != b.getHeight() || b.getWidth() > size * 1.5f) {
            // Square, and no bigger than needed
            int side = Math.min(b.getWidth(), b.getHeight());
            Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
            b = Bitmap.createScaledBitmap(sq, size, size, true);
        }
        return b;
    }

    /** A cover made from the name: its own two colours, a note and the name's first letter. */
    public static Bitmap drawn(String name, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int h = name != null ? name.hashCode() : 0;
        float hue = ((h & 0xFFFF) % 360);
        int a = android.graphics.Color.HSVToColor(new float[]{hue, 0.55f, 0.42f});
        int z = android.graphics.Color.HSVToColor(new float[]{(hue + 40) % 360, 0.65f, 0.18f});
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, size, size, a, z, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, size, size, p);
        p.setShader(null);
        PocketIcons.draw(c, "MUSIC", size / 2f, size / 2f, size * 0.36f, 0x59FFFFFF, p);
        return b;
    }
}
