package com.limelight.pocket.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.limelight.R;

/**
 * Nexus Legacy: an 8-bit console look of its own. Pixel type (Pixelify Sans, SIL Open Font
 * License), square cards, dialog boxes with a double gold rule and stepped corners, and faint
 * scanlines. Every edge is snapped to whole device pixels so lines stay crisp.
 */
public final class LegacyStyle {
    public static final int BACKGROUND = 0xFF0D0A06;
    public static final int PANEL = 0xFF19130B;
    public static final int INK = 0xFFF3E7C4;
    public static final int DIM = 0xFFBCA97C;
    public static final int GOLD = 0xFFE0C98A;
    private static final int GOLD_DARK = 0xFF7A6638;

    private static Typeface pixel;

    private LegacyStyle() {}

    /** The pixel typeface; the system monospace if the font cannot be read. */
    public static synchronized Typeface typeface(Context context) {
        if (pixel == null) {
            try {
                pixel = ResourcesCompat.getFont(context.getApplicationContext(), R.font.horizon_pixelify_sans);
            } catch (RuntimeException unavailable) {
                pixel = null;
            }
            if (pixel == null) {
                pixel = Typeface.MONOSPACE;
            }
        }
        return pixel;
    }

    /** One "pixel" of the look: a whole number of device pixels, at least one. */
    public static int unit(float density) {
        return Math.max(1, Math.round(density * 1.5f));
    }

    /** A dialog box: dark panel, double gold rule, corners stepped one pixel in. */
    public static Drawable dialog(Context context) {
        return new Frame(unit(context.getResources().getDisplayMetrics().density), PANEL, GOLD, GOLD_DARK, true);
    }

    /** A card or tile frame: square, one gold rule when focused, a darker rule otherwise. */
    public static Drawable card(Context context, boolean focused) {
        return new Frame(unit(context.getResources().getDisplayMetrics().density), 0,
                focused ? GOLD : GOLD_DARK, 0, false);
    }

    /** Faint horizontal scanlines for a whole screen (drawn over its content). */
    public static Drawable scanlines(Context context) {
        return new Scanlines(unit(context.getResources().getDisplayMetrics().density));
    }

    /** The stepped outline of a frame, as pixel rectangles (left, top, right, bottom) per edge. */
    static int[][] steppedEdges(int width, int height, int u) {
        if (width < 4 * u || height < 4 * u) {
            return new int[][]{{0, 0, width, height}};
        }
        return new int[][]{
                {2 * u, 0, width - 2 * u, u},                 // top
                {2 * u, height - u, width - 2 * u, height},   // bottom
                {0, 2 * u, u, height - 2 * u},                // left
                {width - u, 2 * u, width, height - 2 * u},    // right
                {u, u, 2 * u, 2 * u},                         // stepped corners
                {width - 2 * u, u, width - u, 2 * u},
                {u, height - 2 * u, 2 * u, height - u},
                {width - 2 * u, height - 2 * u, width - u, height - u},
        };
    }

    private static final class Frame extends Drawable {
        private final int u, fill, rule, inner;
        private final boolean doubled;
        private final Paint paint = new Paint();
        private final Rect r = new Rect();

        Frame(int u, int fill, int rule, int inner, boolean doubled) {
            this.u = u;
            this.fill = fill;
            this.rule = rule;
            this.inner = inner;
            this.doubled = doubled;
            paint.setAntiAlias(false);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            int w = b.width(), h = b.height();
            if (fill != 0) {
                paint.setColor(fill);
                canvas.drawRect(b.left + u, b.top + u, b.right - u, b.bottom - u, paint);
            }
            paint.setColor(rule);
            for (int[] e : steppedEdges(w, h, u)) {
                r.set(b.left + e[0], b.top + e[1], b.left + e[2], b.top + e[3]);
                canvas.drawRect(r, paint);
            }
            if (doubled && w > 8 * u && h > 8 * u) {
                paint.setColor(inner);
                int i = 3 * u;
                canvas.drawRect(b.left + i, b.top + i, b.right - i, b.top + i + u, paint);
                canvas.drawRect(b.left + i, b.bottom - i - u, b.right - i, b.bottom - i, paint);
                canvas.drawRect(b.left + i, b.top + i, b.left + i + u, b.bottom - i, paint);
                canvas.drawRect(b.right - i - u, b.top + i, b.right - i, b.bottom - i, paint);
            }
        }

        @Override
        public boolean getPadding(@NonNull Rect padding) {
            int p = doubled ? 5 * u : 2 * u;
            padding.set(p, p, p, p);
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class Scanlines extends Drawable {
        private final int u;
        private final Paint paint = new Paint();

        Scanlines(int u) {
            this.u = u;
            paint.setColor(0x1A000000);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            for (int y = b.top; y < b.bottom; y += 3 * u) {
                canvas.drawRect(b.left, y, b.right, y + u, paint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
