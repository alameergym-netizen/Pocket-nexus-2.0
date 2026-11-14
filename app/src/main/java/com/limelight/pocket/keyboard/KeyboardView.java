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
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * The controller keyboard itself, drawn like a console's: a glass panel along the bottom, what
 * you're typing in a line at its top, big keys in a grid with the one you're on lit white, and
 * the buttons' jobs along the bottom. English (with capitals) and Arabic (with its marks on
 * shift), and a page of numbers and symbols. The D-pad (or the stick) moves, A types.
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
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private String enterLabel = "⏎";
    private CharSequence before = "";
    private CharSequence after = "";
    private boolean hasText;
    private float pop = 1f;
    private ValueAnimator popAnim;
    private float keyH;
    private boolean laidOut;

    public KeyboardView(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        d = c.getResources().getDisplayMetrics().density;
        accent = PocketTheme.accent(c);
        Typeface medium = PocketFonts.medium();
        text.setTypeface(medium != null ? medium : Typeface.DEFAULT);
        text.setTextAlign(Paint.Align.CENTER);
        small.setTypeface(PocketFonts.regular() != null ? PocketFonts.regular() : Typeface.DEFAULT);
        lang = PocketPrefs.prefs(c).getString(PocketKeyboardService.KEY_LANG,
                "ar".equals(java.util.Locale.getDefault().getLanguage()) ? "ar" : "en");
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
        enterLabel = label;
        build();
    }

    /** What's around the cursor in the field (shown along the top). */
    public void setPreview(CharSequence before, CharSequence after, boolean hasField) {
        this.before = before != null ? before : "";
        this.after = after != null ? after : "";
        this.hasText = hasField;
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
        bottom.add(new Key(SHIFT, "⇧", 1.3f));
        bottom.add(new Key(SYMBOLS, symbols ? ("ar".equals(lang) ? "أبج" : "ABC") : "?123", 1.3f));
        bottom.add(new Key(LANGUAGE, "ar".equals(lang) ? "EN" : "ع", 1.1f));
        bottom.add(new Key(SPACE, c.getString("ar".equals(lang) ? R.string.pocket_kb_arabic : R.string.pocket_kb_english), 3.6f));
        bottom.add(new Key(LEFT, "◀", 0.9f));
        bottom.add(new Key(RIGHT, "▶", 0.9f));
        bottom.add(new Key(DELETE, "⌫", 1.3f));
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
        requestLayout();
        invalidate();
    }

    // ---- Moving -------------------------------------------------------------------------

    public void moveH(int dir) {
        List<Key> r = rows.get(row);
        // Arabic reads right to left: right moves to the key on the right all the same
        col = (col + dir + r.size()) % r.size();
        bump();
    }

    public void moveV(int dir) {
        float x = selected() != null ? selected().r.centerX() : 0;
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
        bump();
    }

    /** The key of this kind (bottom row), selected. */
    public void selectType(int type) {
        List<Key> last = rows.get(rows.size() - 1);
        for (int i = 0; i < last.size(); i++) {
            if (last.get(i).type == type) {
                row = rows.size() - 1;
                col = i;
                bump();
                return;
            }
        }
    }

    public void press() {
        bump();
    }

    private void bump() {
        if (popAnim != null) {
            popAnim.cancel();
        }
        popAnim = ValueAnimator.ofFloat(0.9f, 1f);
        popAnim.setDuration(160);
        popAnim.setInterpolator(com.limelight.pocket.ui.Spring.POP);
        popAnim.addUpdateListener(a -> {
            pop = (float) a.getAnimatedValue();
            invalidate();
        });
        popAnim.start();
    }

    // ---- Size and layout ----------------------------------------------------------------

    private float topBar() {
        return 34 * d;
    }

    private float hintBar() {
        return 22 * d;
    }

    private float gap() {
        return 5 * d;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        keyH = PocketKeyboardService.keySizeDp(getContext()) * d;
        float h = topBar() + rows.size() * keyH + (rows.size() - 1) * gap() + hintBar() + 16 * d;
        setMeasuredDimension(w, Math.round(h));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        laidOut = false;
    }

    private void layoutKeys() {
        float width = getWidth();
        float maxW = Math.min(width - 24 * d, 780 * d);
        float left = (width - maxW) / 2f;
        float y = topBar() + 6 * d;
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
        float w = getWidth();
        float h = getHeight();
        float r = 22 * d;
        // The glass panel, its top corners rounded, a hairline of light along its top
        path.reset();
        path.addRoundRect(new RectF(0, 0, w, h + r), r, r, Path.Direction.CW);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h, 0xF71A1A20, 0xFA0C0C10, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 0.8f * d));
        paint.setColor(0x2EFFFFFF);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        drawPreview(c, w);
        Key sel = selected();
        for (List<Key> line : rows) {
            for (Key k : line) {
                drawKey(c, k, k == sel);
            }
        }
        // The buttons' jobs
        small.setTextSize(10.5f * d);
        small.setColor(0x99FFFFFF);
        small.setTextAlign(Paint.Align.CENTER);
        c.drawText(getContext().getString(R.string.pocket_kb_hints), w / 2f, h - 10 * d, small);
    }

    private void drawPreview(Canvas c, float w) {
        float maxW = Math.min(w - 24 * d, 780 * d);
        float left = (w - maxW) / 2f;
        float cy = 20 * d;
        // The language, as a small lit badge
        String badge = symbols ? "#" : "ar".equals(lang) ? "ع" : "EN";
        text.setTextSize(11 * d);
        float bw = Math.max(26 * d, text.measureText(badge) + 14 * d);
        tmp.set(left, cy - 10 * d, left + bw, cy + 10 * d);
        paint.setColor(accent);
        c.drawRoundRect(tmp, 10 * d, 10 * d, paint);
        text.setColor(isLight(accent) ? 0xFF111114 : 0xFFFFFFFF);
        c.drawText(badge, tmp.centerX(), cy + 4 * d, text);
        if (shifted()) {
            tmp.set(tmp.right + 6 * d, cy - 10 * d, tmp.right + 6 * d + 26 * d, cy + 10 * d);
            paint.setColor(caps ? 0xFFFFFFFF : 0x40FFFFFF);
            c.drawRoundRect(tmp, 10 * d, 10 * d, paint);
            text.setColor(caps ? 0xFF111114 : 0xFFFFFFFF);
            c.drawText("⇧", tmp.centerX(), cy + 4 * d, text);
        }
        // What's being typed, with the cursor
        float tx = tmp.right + 12 * d;
        float room = left + maxW - tx;
        small.setTextSize(14 * d);
        small.setTextAlign(Paint.Align.LEFT);
        if (!hasText) {
            small.setColor(0x80FFFFFF);
            c.drawText(getContext().getString(R.string.pocket_kb_no_field), tx, cy + 5 * d, small);
            return;
        }
        small.setColor(0xFFFFFFFF);
        String b = before.toString().replace('\n', ' ');
        String a = after.toString().replace('\n', ' ');
        // Keep the cursor in view: as much of what's before it as fits in two thirds
        String shownB = TextUtils.ellipsize(b, new android.text.TextPaint(small), room * 0.66f,
                TextUtils.TruncateAt.START).toString();
        float bW = small.measureText(shownB);
        c.drawText(shownB, tx, cy + 5 * d, small);
        float cx = tx + bW + 1.5f * d;
        paint.setColor(accent);
        c.drawRect(cx, cy - 9 * d, cx + 2 * d, cy + 9 * d, paint);
        small.setColor(0xA6FFFFFF);
        String shownA = TextUtils.ellipsize(a, new android.text.TextPaint(small), Math.max(0, room - bW - 8 * d),
                TextUtils.TruncateAt.END).toString();
        c.drawText(shownA, cx + 5 * d, cy + 5 * d, small);
    }

    private static boolean isLight(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 160;
    }

    private void drawKey(Canvas c, Key k, boolean sel) {
        float rad = 10 * d;
        tmp.set(k.r);
        if (sel) {
            float s = pop;
            float dx = tmp.width() * (1 - s) / 2f;
            float dy = tmp.height() * (1 - s) / 2f;
            tmp.inset(dx - 2 * d, dy - 2 * d);
        }
        boolean special = k.type != CHAR;
        boolean on = (k.type == SHIFT && shifted()) || (k.type == SYMBOLS && symbols);
        if (sel) {
            // A soft glow in the accent round the lit key
            paint.setColor((accent & 0x00FFFFFF) | 0x55000000);
            tmp.inset(-3 * d, -3 * d);
            c.drawRoundRect(tmp, rad + 3 * d, rad + 3 * d, paint);
            tmp.inset(3 * d, 3 * d);
            paint.setColor(0xFAFFFFFF);
        }
        else if (on) {
            paint.setColor((accent & 0x00FFFFFF) | 0xCC000000);
        }
        else {
            paint.setColor(special ? 0x14FFFFFF : 0x24FFFFFF);
        }
        c.drawRoundRect(tmp, rad, rad, paint);
        if (!sel && !on) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, 0.7f * d));
            paint.setColor(0x1AFFFFFF);
            c.drawRoundRect(tmp, rad, rad, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        boolean arabic = k.type == CHAR && !k.text.isEmpty() && k.text.charAt(0) >= 0x0600 && k.text.charAt(0) <= 0x06FF;
        float size = k.type == CHAR ? (arabic ? 19 : 17) : (k.type == SPACE || k.type == ENTER || k.type == SYMBOLS ? 12.5f : 17);
        text.setTextSize(size * d);
        text.setColor(sel ? 0xFF111114 : on && isLight(accent) ? 0xFF111114 : 0xF2FFFFFF);
        String label = k.text;
        if (k.type == SPACE) {
            label = "␣  " + label;
        }
        float ty = tmp.centerY() - (text.descent() + text.ascent()) / 2f;
        c.drawText(label, tmp.centerX(), ty, text);
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
                        invalidate();
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
