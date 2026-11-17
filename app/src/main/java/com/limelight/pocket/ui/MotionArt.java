package com.limelight.pocket.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;

import com.limelight.pocket.PocketArt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Art that moves: an animated cover (a GIF or an animated WebP, from SteamGridDB's animated art
 * or a file of your own) or a background that's a video (WebM / MP4) or an animated picture.
 * Kept next to the game's still art (which stays as its first frame, for everywhere that shows
 * stills); played where the game is in focus (Home's chosen tile, the backdrop behind it).
 */
public final class MotionArt {
    private MotionArt() {}

    private static final String[] EXTS = {"webm", "mp4", "gif", "webp"};

    /** The moving art of this kind for it, or null. */
    public static File file(Context c, String uuid, int id, int kind) {
        if (uuid == null) {
            return null;
        }
        File dir = PocketArt.file(c, uuid, id, kind).getParentFile();
        if (dir == null) {
            return null;
        }
        for (String ext : EXTS) {
            File f = new File(dir, "motion_" + kind + "." + ext);
            if (f.exists() && f.length() > 0) {
                return f;
            }
        }
        return null;
    }

    public static boolean isVideo(File f) {
        String n = f.getName();
        return n.endsWith(".webm") || n.endsWith(".mp4");
    }

    /** The extension for a type ("video/webm" → webm), or null when it can't move. */
    public static String extFor(String mime, String url) {
        String m = mime != null ? mime.toLowerCase(java.util.Locale.ROOT) : "";
        String u = url != null ? url.toLowerCase(java.util.Locale.ROOT) : "";
        if (m.contains("webm") || u.endsWith(".webm")) {
            return "webm";
        }
        if (m.contains("mp4") || u.endsWith(".mp4")) {
            return "mp4";
        }
        if (m.contains("gif") || u.endsWith(".gif")) {
            return "gif";
        }
        if (m.contains("webp") || u.endsWith(".webp")) {
            return "webp";
        }
        return null;
    }

    /**
     * Keeps it as this kind's moving art, and its first frame as the still art (so every screen
     * has a picture). Blocking.
     */
    public static void save(Context c, String uuid, int id, int kind, byte[] data, String ext) throws IOException {
        clear(c, uuid, id, kind);
        File dir = PocketArt.file(c, uuid, id, kind).getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            throw new IOException("No folder");
        }
        File f = new File(dir, "motion_" + kind + "." + ext);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
        byte[] still = firstFrame(f);
        if (still != null) {
            PocketArt.save(c, uuid, id, kind, still);
        }
    }

    /** Back to still art only. */
    public static void clear(Context c, String uuid, int id, int kind) {
        File dir = PocketArt.file(c, uuid, id, kind).getParentFile();
        if (dir == null) {
            return;
        }
        for (String ext : EXTS) {
            File f = new File(dir, "motion_" + kind + "." + ext);
            if (f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /** Its first frame as a JPEG (or null). */
    private static byte[] firstFrame(File f) {
        Bitmap b = null;
        try {
            if (isVideo(f)) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(f.getPath());
                    b = r.getFrameAtTime(0);
                } finally {
                    try {
                        r.release();
                    } catch (Exception ignored) {
                    }
                }
            }
            else {
                b = android.graphics.BitmapFactory.decodeFile(f.getPath());
            }
        } catch (RuntimeException | OutOfMemoryError ignored) {
        }
        if (b == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 92, out);
        return out.toByteArray();
    }

    /** An animated picture, decoded and started (null: not an animated picture, or Android 8). */
    public static Drawable animated(File f) {
        if (f == null || isVideo(f) || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null;
        }
        try {
            Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(f));
            if (d instanceof AnimatedImageDrawable) {
                ((AnimatedImageDrawable) d).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                ((AnimatedImageDrawable) d).start();
            }
            return d;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }
}
