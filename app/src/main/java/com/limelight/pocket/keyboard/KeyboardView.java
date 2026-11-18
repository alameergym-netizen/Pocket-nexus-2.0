package com.limelight.pocket.keyboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.limelight.R;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.Spring;

import java.util.ArrayList;
import java.util.List;

/**
 * The controller keyboard itself, drawn like a console's: a frosted panel along the bottom, the
 * text being typed in a box at its top (its cursor blinking), big keys with the chosen one lit
 * white (the light glides from key to key as you move, and a key pressed gives a little), the
 * special keys as icons, and the buttons' jobs along the bottom as the controller's own glyphs.
 * English (with capitals) and Arabic (with its marks on shift), and a page of numbers and
 * symbols. The D-pad (or the stick) moves, A types.
 */
public final class KeyboardView extends View {

    public interface Listener {
        void onKey(Key key);
    }

    public static final int CHAR = 0;
    public static final int SHIFT = 1;
    public static final int SYMBOLS = 2;
    public static final int LANGUAGE = 3;
    public static final int SPACE = 4;
    public static final int DELETE = 5;
    public static final int ENTER = 6;
    public static final int LEFT = 7;
    public static final int RIGHT = 8;

    public static final class Key {
        public final int type;
        public final String text;
        final float weight;
        final RectF r = new RectF();
        /** When it was last pressed (it gives a little, then springs back). */
        long pressedAt;

        Key(int type, String text, float weight) {
            this.type = type;
            this.text = text;
            this.weight = weight;
        }
    }

    private static final String[][] EN = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l", "'"},
            {"z", "x", "c", "v", "b", "n", "m", ",", ".", "?"},
    };
    private static final String[][] AR = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"},
            {"ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"},
            {"ذ", "ئ", "ء", "ؤ", "ر", "ى", "ة", "و", "ز", "ظ", "د"},
    };
    /** Arabic on shift: the marks and the hamza forms. */
    private static final String[][] AR_SHIFT = {
            {"١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠"},
            {"َ", "ً", "ُ", "ٌ", "ِ", "ٍ", "ّ", "ْ", "ـ", "آ", "إ"},
            {"أ", "لا", "لأ", "لإ", "لآ", "،", "؛", "؟", "«", "»", "…"},
            {"ذ", "ئ", "ء", "ؤ", "ر", "ى", "ة", "و", "ز", "ظ", "د"},
    };
    private static final String[][] SYM = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"},
            {"!", "\"", ":", ";", "/", "_", "=", "\\", "[", "]"},
            {"{", "}", "<", ">", "~", "`", "^", "|", "€", "£"},
    };

    private final float d;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();
    private final int accent;
    private final List<List<Key>> rows = new ArrayList<>();
    private int row = 1;
    private int col = 0;
    private boolean shift;
    private boolean caps;
    private boolean symbols;
    private String lang = "en";
    private String enterLabel = "";
    private CharSequence before = "";
    private CharSequence after = "";
    private CharSequence placeholder = "";
    private boolean hasText;
    private float keyH;
    private boolean laidOut;

    /** The light under the chosen key, gliding from the last one. */
    private final RectF lit = new RectF();
    private final RectF litFrom = new RectF();
    private final RectF litTo = new RectF();
    private ValueAnimator litAnim;
    private boolean litSet;
    private long lastTyped;

    public KeyboardView(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        d = c.getResources().getDisplayMetrics().density;
        accent = PocketTheme.accent(c);
        Typeface medium = PocketFonts.medium();
        text.setTypeface(medium != null ? medium : Typeface.DEFAULT);
        text.setTextAlign(Paint.Align.CENTER);
        small.setTypeface(PocketFonts.regular() != null ? PocketFonts.regular() : Typeface.DEFAULT);
        lang = PocketKeyboardService.prefs(c).getString(PocketKeyboardService.KEY_LANG, "en");
        build();
    }

    // ---- State --------------------------------------------------------------------------

    public String language() {
        return lang;
    }

    public void setLanguage(String l) {
        lang = "ar".equals(l) ? "ar" : "en";
        shift = false;
        caps = false;
        build();
    }

    public boolean shifted() {
        return shift || caps;
    }

    /** One capital (or Arabic's marks) for the next key; twice: stays on. */
    public void toggleShift() {
        if (caps) {
            caps = false;
            shift = false;
        }
        else if (shift) {
            caps = true;
        }
        else {
            shift = true;
        }
        build();
    }

    public void setShift(boolean on) {
        if (caps) {
            return;
        }
        if (shift != on) {
            shift = on;
            build();
        }
    }

    /** After a letter: a one-time shift goes back off. */
    public void typed() {
        lastTyped = SystemClock.uptimeMillis();
        if (shift && !caps) {
            shift = false;
            build();
        }
    }

    public void toggleSymbols() {
        symbols = !symbols;
        build();
    }

    public void setEnterLabel(String label) {
        enterLabel = label != null ? label : "";
        build();
    }

    /** The field's own hint ("Search games"), shown while it's empty. */
    public void setPlaceholder(CharSequence hint) {
        placeholder = hint != null ? hint : "";
        invalidate();
    }

    /** What's around the cursor in the field (shown in the box at the top). */
    public void setPreview(CharSequence before, CharSequence after, boolean hasField) {
        this.before = before != null ? before : "";
        this.after = after != null ? after : "";
        this.hasText = hasField;
        lastTyped = SystemClock.uptimeMillis();
        invalidate();
    }

    public Key selected() {
        if (row < 0 || row >= rows.size() || rows.get(row).isEmpty()) {
            return null;
        }
        List<Key> r = rows.get(row);
        return r.get(Math.max(0, Math.min(r.size() - 1, col)));
    }

    // ---- Keys ---------------------------------------------------------------------------

    private void build() {
        Key keep = selected();
        int keepType = keep != null ? keep.type : CHAR;
        rows.clear();
        String[][] grid = symbols ? SYM : "ar".equals(lang) ? (shifted() ? AR_SHIFT : AR) : EN;
        for (String[] line : grid) {
            List<Key> r = new ArrayList<>();
            for (String s : line) {
                String t = !symbols && "en".equals(lang) && shifted() ? s.toUpperCase(java.util.Locale.ROOT) : s;
                r.add(new Key(CHAR, t, 1f));
            }
            rows.add(r);
        }
        Context c = getContext();
        List<Key> bottom = new ArrayList<>();
        bottom.add(new Key(SHIFT, "", 1.3f));
        bottom.add(new Key(SYMBOLS, symbols ? ("ar".equals(lang) ? "أبج" : "ABC") : "?123", 1.3f));
        bottom.add(new Key(LANGUAGE, "ar".equals(lang) ? "EN" : "ع", 1.1f));
        bottom.add(new Key(SPACE, c.getString("ar".equals(lang) ? R.string.pocket_kb_arabic : R.string.pocket_kb_english), 3.6f));
        bottom.add(new Key(LEFT, "", 0.9f));
        bottom.add(new Key(RIGHT, "", 0.9f));
        bottom.add(new Key(DELETE, "", 1.3f));
        bottom.add(new Key(ENTER, enterLabel, 1.7f));
        rows.add(bottom);
        if (keep != null && keep.type != CHAR) {
            // Still on the same special key after the page changed
            List<Key> last = rows.get(rows.size() - 1);
            for (int i = 0; i < last.size(); i++) {
                if (last.get(i).type == keepType) {
                    row = rows.size() - 1;
                    col = i;
                }
            }
        }
        row = Math.max(0, Math.min(rows.size() - 1, row));
        col = Math.max(0, Math.min(rows.get(row).size() - 1, col));
        laidOut = false;
        litSet = false;
        requestLayout();
        invalidate();
    }

    // ---- Moving -------------------------------------------------------------------------

    public void moveH(int dir) {
        List<Key> r = rows.get(row);
        col = (col + dir + r.size()) % r.size();
        glide();
    }

    public void moveV(int dir) {
        Key s = selected();
        float x = s != null ? s.r.centerX() : 0;
        row = (row + dir + rows.size()) % rows.size();
        List<Key> r = rows.get(row);
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < r.size(); i++) {
            float dd = Math.abs(r.get(i).r.centerX() - x);
            if (dd < bestD) {
                bestD = dd;
                best = i;
            }
        }
        col = best;
        glide();
    }

    /** The key of this kind (bottom row), selected. */
    public void selectType(int type) {
        List<Key> last = rows.get(rows.size() - 1);
        for (int i = 0; i < last.size(); i++) {
            if (last.get(i).type == type) {
                row = rows.size() - 1;
                col = i;
                glide();
                return;
            }
        }
    }

    /** The chosen key was pressed: it gives a little and springs back. */
    public void press() {
        Key k = selected();
        if (k != null) {
            k.pressedAt = SystemClock.uptimeMillis();
        }
        postInvalidateOnAnimation();
    }

    /** The light moves to the chosen key (a short glide, not a jump). */
    private void glide() {
        Key k = selected();
        if (k == null || !laidOut) {
            invalidate();
            return;
        }
        litFrom.set(lit);
        litTo.set(k.r);
        if (litAnim != null) {
            litAnim.cancel();
        }
        litAnim = ValueAnimator.ofFloat(0f, 1f);
        litAnim.setDuration(130);
        litAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        litAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            lit.set(litFrom.left + (litTo.left - litFrom.left) * t, litFrom.top + (litTo.top - litFrom.top) * t,
                    litFrom.right + (litTo.right - litFrom.right) * t, litFrom.bottom + (litTo.bottom - litFrom.bottom) * t);
            invalidate();
        });
        litAnim.start();
    }

    // ---- Size and layout ----------------------------------------------------------------

    private float topBar() {
        return 52 * d;
    }

    private float hintBar() {
        return 28 * d;
    }

    private float gap() {
        return 6 * d;
    }

    /** The keys' width: compact, like a console's keyboard (PS5), not the screen's. */
    private float panelWidth(float width) {
        return Math.min(width - 64 * d, 640 * d);
    }

    /** Room under the panel (it floats) and round it. */
    private float floatGap() {
        return 10 * d;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        keyH = PocketKeyboardService.keySizeDp(getContext()) * d;
        float h = topBar() + rows.size() * keyH + (rows.size() - 1) * gap() + hintBar() + 14 * d + floatGap();
        setMeasuredDimension(w, Math.round(h));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        laidOut = false;
        litSet = false;
    }

    private void layoutKeys() {
        float width = getWidth();
        float maxW = panelWidth(width);
        float left = (width - maxW) / 2f;
        float y = topBar() + 4 * d;
        for (List<Key> r : rows) {
            float total = 0;
            for (Key k : r) {
                total += k.weight;
            }
            float unit = (maxW - (r.size() - 1) * gap()) / total;
            float x = left;
            for (Key k : r) {
                float kw = unit * k.weight;
                k.r.set(x, y, x + kw, y + keyH);
                x += kw + gap();
            }
            y += keyH + gap();
        }
        laidOut = true;
    }

    // ---- Drawing ------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        if (!laidOut) {
            layoutKeys();
        }
        Key sel = selected();
        if (!litSet && sel != null) {
            lit.set(sel.r);
            litSet = true;
        }
        float w = getWidth();
        float h = getHeight() - floatGap();
        float r = 24 * d;
        // A floating panel (round corners all round, the screen showing either side of it)
        float pw = panelWidth(w) + 28 * d;
        float pl = (w - pw) / 2f;
        path.reset();
        path.addRoundRect(new RectF(pl, 0, pl + pw, h), r, r, Path.Direction.CW);
        // Its soft shadow
        paint.setShader(null);
        paint.setColor(0x40000000);
        c.drawRoundRect(new RectF(pl - 3 * d, 3 * d, pl + pw + 3 * d, h + 5 * d), r + 3 * d, r + 3 * d, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h, 0xF71C1C24, 0xFC09090D, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        // The accent, faint, glowing up from the bottom
        paint.setShader(new android.graphics.RadialGradient(w / 2f, h * 1.15f, w * 0.55f,
                (accent & 0x00FFFFFF) | 0x26000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        paint.setShader(new LinearGradient(0, 0, w, 0, 0x00FFFFFF, 0x33FFFFFF, Shader.TileMode.MIRROR));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 1f * d));
        c.drawPath(path, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);

        drawField(c, w);

        // The light under the chosen key (gliding), a soft glow round it
        if (sel != null) {
            float pressed = pressScale(sel);
            tmp.set(lit);
            float dx = tmp.width() * (1 - pressed) / 2f;
            float dy = tmp.height() * (1 - pressed) / 2f;
            tmp.inset(dx, dy);
            // A soft glow in the accent: a few rings fading out
            for (int i = 3; i >= 1; i--) {
                glow.setColor((accent & 0x00FFFFFF) | ((0x1C - i * 4) << 24));
                tmp.inset(-i * 2.5f * d, -i * 2.5f * d);
                c.drawRoundRect(tmp, 13 * d + i * 2.5f * d, 13 * d + i * 2.5f * d, glow);
                tmp.inset(i * 2.5f * d, i * 2.5f * d);
            }
            tmp.inset(-1.5f * d, -1.5f * d);
            paint.setColor(0xFFFFFFFF);
            c.drawRoundRect(tmp, 13 * d, 13 * d, paint);
        }
        for (List<Key> line : rows) {
            for (Key k : line) {
                drawKey(c, k, k == sel);
            }
        }
        drawHints(c, w, h);
        if (hasText || (sel != null && SystemClock.uptimeMillis() - sel.pressedAt < 260)) {
            // The cursor blinks; a pressed key springs back
            postInvalidateDelayed(sel != null && SystemClock.uptimeMillis() - sel.pressedAt < 260 ? 16 : 120);
        }
    }

    /** A key pressed shrinks to 0.9 and springs back in a quarter of a second. */
    private float pressScale(Key k) {
        long t = SystemClock.uptimeMillis() - k.pressedAt;
        if (t >= 240) {
            return 1f;
        }
        float f = t / 240f;
        return 0.9f + 0.1f * Spring.POP.getInterpolation(f);
    }

    /** The box with what's typed: the language, the text, the cursor. */
    private void drawField(Canvas c, float w) {
        float maxW = panelWidth(w);
        float left = (w - maxW) / 2f;
        float top = 12 * d;
        float bh = 36 * d;
        tmp.set(left, top, left + maxW, top + bh);
        paint.setColor(0x14FFFFFF);
        c.drawRoundRect(tmp, bh / 2f, bh / 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, d));
        paint.setColor(hasText ? (accent & 0x00FFFFFF) | 0x80000000 : 0x1FFFFFFF);
        c.drawRoundRect(tmp, bh / 2f, bh / 2f, paint);
        paint.setStyle(Paint.Style.FILL);
        float cy = top + bh / 2f;

        // The language (and shift), as small lit badges inside the box
        String badge = symbols ? "#+=" : "ar".equals(lang) ? "ع" : "EN";
        text.setTextSize(11.5f * d);
        text.setTypeface(PocketFonts.bold());
        float bw = Math.max(30 * d, text.measureText(badge) + 16 * d);
        RectF b = new RectF(left + 5 * d, cy - 13 * d, left + 5 * d + bw, cy + 13 * d);
        paint.setColor(accent);
        c.drawRoundRect(b, 13 * d, 13 * d, paint);
        text.setColor(isLight(accent) ? 0xFF111114 : 0xFFFFFFFF);
        c.drawText(badge, b.centerX(), cy - (text.descent() + text.ascent()) / 2f, text);
        float tx = b.right + 12 * d;
        if (shifted()) {
            RectF s = new RectF(b.right + 6 * d, cy - 13 * d, b.right + 6 * d + 30 * d, cy + 13 * d);
            paint.setColor(caps ? 0xFFFFFFFF : 0x33FFFFFF);
            c.drawRoundRect(s, 13 * d, 13 * d, paint);
            drawShift(c, s.centerX(), cy, 13 * d, caps ? 0xFF111114 : 0xFFFFFFFF, true);
            tx = s.right + 12 * d;
        }
        text.setTypeface(PocketFonts.medium());

        float room = left + maxW - tx - 16 * d;
        small.setTextSize(15 * d);
        small.setTextAlign(Paint.Align.LEFT);
        float base = cy - (small.descent() + small.ascent()) / 2f;
        if (!hasText) {
            small.setColor(0x80FFFFFF);
            c.drawText(getContext().getString(R.string.pocket_kb_no_field), tx, base, small);
            return;
        }
        String bs = before.toString().replace('\n', ' ');
        String as = after.toString().replace('\n', ' ');
        if (bs.isEmpty() && as.isEmpty() && placeholder.length() > 0) {
            small.setColor(0x66FFFFFF);
            String ph = TextUtils.ellipsize(placeholder, small, room, TextUtils.TruncateAt.END).toString();
            c.drawText(ph, tx + 6 * d, base, small);
        }
        // Keep the cursor in view: as much of what's before it as fits in two thirds
        small.setColor(0xFFFFFFFF);
        String shownB = TextUtils.ellipsize(bs, small, room * 0.7f, TextUtils.TruncateAt.START).toString();
        float bW = small.measureText(shownB);
        c.drawText(shownB, tx, base, small);
        float cx = tx + bW + 2 * d;
        // The cursor: solid while typing, blinking after
        long since = SystemClock.uptimeMillis() - lastTyped;
        boolean on = since < 500 || (since / 530) % 2 == 0;
        if (on) {
            paint.setColor(accent);
            c.drawRoundRect(new RectF(cx, cy - 10 * d, cx + 2.2f * d, cy + 10 * d), d, d, paint);
        }
        small.setColor(0x99FFFFFF);
        String shownA = TextUtils.ellipsize(as, small, Math.max(0, room - bW - 8 * d), TextUtils.TruncateAt.END).toString();
        c.drawText(shownA, cx + 6 * d, base, small);
    }

    private static boolean isLight(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 160;
    }

    private void drawKey(Canvas c, Key k, boolean sel) {
        float rad = 12 * d;
        tmp.set(k.r);
        float pressed = pressScale(k);
        if (pressed < 1f) {
            float dx = tmp.width() * (1 - pressed) / 2f;
            float dy = tmp.height() * (1 - pressed) / 2f;
            tmp.inset(dx, dy);
        }
        boolean special = k.type != CHAR;
        boolean on = (k.type == SHIFT && shifted()) || (k.type == SYMBOLS && symbols);
        if (!sel) {
            // Glass keys: a little lighter at the top
            if (on) {
                paint.setColor((accent & 0x00FFFFFF) | 0xD9000000);
                c.drawRoundRect(tmp, rad, rad, paint);
            }
            else {
                paint.setShader(new LinearGradient(0, tmp.top, 0, tmp.bottom,
                        special ? 0x1FFFFFFF : 0x2EFFFFFF, special ? 0x0FFFFFFF : 0x17FFFFFF, Shader.TileMode.CLAMP));
                c.drawRoundRect(tmp, rad, rad, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, 0.8f * d));
                paint.setColor(0x14FFFFFF);
                c.drawRoundRect(tmp, rad, rad, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        int ink = sel ? 0xFF111114 : on && isLight(accent) ? 0xFF111114 : 0xF2FFFFFF;
        float cx = tmp.centerX();
        float cy = tmp.centerY();
        float glyph = Math.min(tmp.height() * 0.42f, 18 * d);
        switch (k.type) {
            case SHIFT:
                drawShift(c, cx, cy, glyph, ink, caps);
                return;
            case DELETE:
                drawDelete(c, cx, cy, glyph, ink);
                return;
            case LEFT:
                drawChevron(c, cx, cy, glyph * 0.8f, ink, -1);
                return;
            case RIGHT:
                drawChevron(c, cx, cy, glyph * 0.8f, ink, 1);
                return;
            case ENTER:
                if (k.text.isEmpty()) {
                    drawEnter(c, cx, cy, glyph, ink);
                    return;
                }
                break;
            default:
                break;
        }
        boolean arabic = k.type == CHAR && !k.text.isEmpty() && k.text.charAt(0) >= 0x0600 && k.text.charAt(0) <= 0x06FF;
        float size = k.type == CHAR ? (arabic ? 20 : 18) : (k.type == SPACE || k.type == ENTER || k.type == SYMBOLS ? 13f : 16f);
        text.setTextSize(size * d);
        text.setTypeface(k.type == CHAR ? PocketFonts.medium() : PocketFonts.semibold());
        text.setColor(ink);
        if (k.type == SPACE) {
            // A space bar: its language small, a thin bar under it
            text.setColor(sel ? 0xB3111114 : 0x99FFFFFF);
            c.drawText(k.text, cx, cy - (text.descent() + text.ascent()) / 2f - 3 * d, text);
            paint.setColor(sel ? 0x66111114 : 0x40FFFFFF);
            c.drawRoundRect(new RectF(cx - 18 * d, tmp.bottom - 9 * d, cx + 18 * d, tmp.bottom - 7 * d), d, d, paint);
            return;
        }
        c.drawText(k.text, cx, cy - (text.descent() + text.ascent()) / 2f, text);
    }

    private void drawShift(Canvas c, float cx, float cy, float s, int color, boolean filled) {
        icon.setColor(color);
        icon.setStrokeWidth(1.8f * d);
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStyle(filled ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
        path.reset();
        float h = s / 2f;
        path.moveTo(cx, cy - h);
        path.lineTo(cx + h, cy);
        path.lineTo(cx + h * 0.45f, cy);
        path.lineTo(cx + h * 0.45f, cy + h * 0.8f);
        path.lineTo(cx - h * 0.45f, cy + h * 0.8f);
        path.lineTo(cx - h * 0.45f, cy);
        path.lineTo(cx - h, cy);
        path.close();
        c.drawPath(path, icon);
        icon.setStyle(Paint.Style.FILL);
    }

    private void drawDelete(Canvas c, float cx, float cy, float s, int color) {
        icon.setColor(color);
        icon.setStrokeWidth(1.8f * d);
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStyle(Paint.Style.STROKE);
        float h = s / 2f;
        path.reset();
        path.moveTo(cx - h * 1.2f, cy);
        path.lineTo(cx - h * 0.5f, cy - h * 0.75f);
        path.lineTo(cx + h * 1.1f, cy - h * 0.75f);
        path.lineTo(cx + h * 1.1f, cy + h * 0.75f);
        path.lineTo(cx - h * 0.5f, cy + h * 0.75f);
        path.close();
        c.drawPath(path, icon);
        c.drawLine(cx - h * 0.05f, cy - h * 0.32f, cx + h * 0.6f, cy + h * 0.32f, icon);
        c.drawLine(cx + h * 0.6f, cy - h * 0.32f, cx - h * 0.05f, cy + h * 0.32f, icon);
        icon.setStyle(Paint.Style.FILL);
    }

    private void drawEnter(Canvas c, float cx, float cy, float s, int color) {
        icon.setColor(color);
        icon.setStrokeWidth(1.9f * d);
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStyle(Paint.Style.STROKE);
        float h = s / 2f;
        path.reset();
        path.moveTo(cx + h, cy - h * 0.8f);
        path.lineTo(cx + h, cy + h * 0.2f);
        path.lineTo(cx - h, cy + h * 0.2f);
        c.drawPath(path, icon);
        path.reset();
        path.moveTo(cx - h * 0.45f, cy - h * 0.35f);
        path.lineTo(cx - h, cy + h * 0.2f);
        path.lineTo(cx - h * 0.45f, cy + h * 0.75f);
        c.drawPath(path, icon);
        icon.setStyle(Paint.Style.FILL);
    }

    private void drawChevron(Canvas c, float cx, float cy, float s, int color, int dir) {
        icon.setColor(color);
        icon.setStrokeWidth(2f * d);
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStyle(Paint.Style.STROKE);
        float h = s / 2f;
        path.reset();
        path.moveTo(cx - dir * h * 0.4f, cy - h);
        path.lineTo(cx + dir * h * 0.4f, cy);
        path.lineTo(cx - dir * h * 0.4f, cy + h);
        c.drawPath(path, icon);
        icon.setStyle(Paint.Style.FILL);
    }

    /** The buttons' jobs: the controller's glyphs (round A B X Y, pills for the rest) and words. */
    private void drawHints(Canvas c, float w, float h) {
        String[][] hints = {
                {"A", getContext().getString(R.string.pocket_kb_h_type)},
                {"X", getContext().getString(R.string.pocket_kb_h_delete)},
                {"Y", getContext().getString(R.string.pocket_kb_h_space)},
                {"L1 R1", getContext().getString(R.string.pocket_kb_h_cursor)},
                {"L2", getContext().getString(R.string.pocket_kb_h_shift)},
                {"R2", getContext().getString(R.string.pocket_kb_h_symbols)},
                {"SELECT", getContext().getString(R.string.pocket_kb_h_language)},
                {"START", getContext().getString(R.string.pocket_kb_h_enter)},
                {"B", getContext().getString(R.string.pocket_kb_h_close)},
        };
        small.setTextSize(9.5f * d);
        small.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(PocketFonts.bold());
        text.setTextSize(7.5f * d);
        float gh = 13 * d;
        float total = 0;
        float[] widths = new float[hints.length];
        for (int i = 0; i < hints.length; i++) {
            float gw = hints[i][0].length() == 1 ? gh : text.measureText(hints[i][0]) + 10 * d;
            widths[i] = gw + 5 * d + small.measureText(hints[i][1]);
            total += widths[i] + (i > 0 ? 10 * d : 0);
        }
        float x = (w - total) / 2f;
        float cy = h - 15 * d;
        for (int i = 0; i < hints.length; i++) {
            String g = hints[i][0];
            float gw = g.length() == 1 ? gh : text.measureText(g) + 10 * d;
            paint.setColor(0xE6FFFFFF);
            c.drawRoundRect(new RectF(x, cy - gh / 2, x + gw, cy + gh / 2), gh / 2, gh / 2, paint);
            text.setColor(0xFF0B0B0D);
            c.drawText(g, x + gw / 2f, cy - (text.descent() + text.ascent()) / 2f, text);
            small.setColor(0x99FFFFFF);
            c.drawText(hints[i][1], x + gw + 5 * d, cy - (small.descent() + small.ascent()) / 2f, small);
            x += widths[i] + 10 * d;
        }
        text.setTypeface(PocketFonts.medium());
    }

    // ---- Touch --------------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
            pick(e.getX(), e.getY());
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (pick(e.getX(), e.getY())) {
                Key k = selected();
                if (k != null && listener != null) {
                    listener.onKey(k);
                }
            }
            return true;
        }
        return true;
    }

    private boolean pick(float x, float y) {
        for (int i = 0; i < rows.size(); i++) {
            List<Key> line = rows.get(i);
            for (int j = 0; j < line.size(); j++) {
                if (line.get(j).r.contains(x, y)) {
                    if (row != i || col != j) {
                        row = i;
                        col = j;
                        glide();
                    }
                    return true;
                }
            }
        }
        return false;
    }

}
