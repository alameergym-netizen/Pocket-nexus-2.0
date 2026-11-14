package com.limelight.pocket.files;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * A file's icon, drawn: a folder (in the accent), or a page with its kind written on a coloured
 * band (APK green, pictures violet, videos red, music orange, archives yellow, games blue).
 */
final class FileIcon extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final boolean folder;
    private final String ext;
    private final int color;

    FileIcon(boolean folder, String name, int accent) {
        this.folder = folder;
        String e = "";
        int dot = name.lastIndexOf('.');
        if (!folder && dot > 0 && dot < name.length() - 1) {
            e = name.substring(dot + 1).toUpperCase(Locale.ROOT);
            if (e.length() > 4) {
                e = e.substring(0, 4);
            }
        }
        ext = e;
        color = folder ? accent : colorOf(e);
        label.setColor(0xFFFFFFFF);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextAlign(Paint.Align.CENTER);
    }

    static int colorOf(String ext) {
        switch (ext) {
            case "APK":
            case "APKS":
            case "XAPK":
                return 0xFF3DDC84;
            case "PNG":
            case "JPG":
            case "JPEG":
            case "WEBP":
            case "GIF":
            case "HEIC":
                return 0xFFB388FF;
            case "MP4":
            case "MKV":
            case "WEBM":
            case "MOV":
            case "AVI":
                return 0xFFFF5252;
            case "MP3":
            case "FLAC":
            case "OGG":
            case "WAV":
            case "M4A":
            case "OPUS":
                return 0xFFFFAB40;
            case "ZIP":
            case "7Z":
            case "RAR":
            case "TAR":
            case "GZ":
                return 0xFFFFD740;
            case "ISO":
            case "CHD":
            case "CUE":
            case "BIN":
            case "RVZ":
            case "NSP":
            case "XCI":
            case "PBP":
            case "CSO":
            case "WUA":
            case "3DS":
            case "NDS":
            case "GBA":
            case "SFC":
            case "SMC":
            case "NES":
            case "N64":
            case "Z64":
            case "MD":
            case "GEN":
            case "M3U":
                return 0xFF448AFF;
            case "PDF":
                return 0xFFFF6E40;
            default:
                return 0xFF90A4AE;
        }
    }

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        float w = b.width();
        float h = b.height();
        float x = b.left;
        float y = b.top;
        if (folder) {
            // Its tab, then its body
            path.reset();
            path.moveTo(x + w * 0.08f, y + h * 0.26f);
            path.lineTo(x + w * 0.4f, y + h * 0.26f);
            path.lineTo(x + w * 0.48f, y + h * 0.36f);
            path.lineTo(x + w * 0.92f, y + h * 0.36f);
            path.lineTo(x + w * 0.92f, y + h * 0.84f);
            path.lineTo(x + w * 0.08f, y + h * 0.84f);
            path.close();
            paint.setColor((color & 0x00FFFFFF) | 0xB3000000);
            c.drawPath(path, paint);
            r.set(x + w * 0.08f, y + h * 0.42f, x + w * 0.92f, y + h * 0.84f);
            paint.setColor(color);
            c.drawRoundRect(r, w * 0.06f, w * 0.06f, paint);
            return;
        }
        // A page with a folded corner
        float fold = w * 0.22f;
        path.reset();
        path.moveTo(x + w * 0.18f, y + h * 0.08f);
        path.lineTo(x + w * 0.82f - fold, y + h * 0.08f);
        path.lineTo(x + w * 0.82f, y + h * 0.08f + fold);
        path.lineTo(x + w * 0.82f, y + h * 0.92f);
        path.lineTo(x + w * 0.18f, y + h * 0.92f);
        path.close();
        paint.setColor(0xFFE8EAED);
        c.drawPath(path, paint);
        paint.setColor(0xFFB0B4BA);
        path.reset();
        path.moveTo(x + w * 0.82f - fold, y + h * 0.08f);
        path.lineTo(x + w * 0.82f - fold, y + h * 0.08f + fold);
        path.lineTo(x + w * 0.82f, y + h * 0.08f + fold);
        path.close();
        c.drawPath(path, paint);
        if (!ext.isEmpty()) {
            r.set(x + w * 0.06f, y + h * 0.5f, x + w * 0.94f, y + h * 0.78f);
            paint.setColor(color);
            c.drawRoundRect(r, w * 0.06f, w * 0.06f, paint);
            label.setTextSize(h * (ext.length() > 3 ? 0.17f : 0.2f));
            c.drawText(ext, r.centerX(), r.centerY() - (label.descent() + label.ascent()) / 2f, label);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
