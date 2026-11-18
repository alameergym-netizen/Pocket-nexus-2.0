package com.limelight.pocket.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick Resume over any game, drawn like a console's: the game behind dims, the games and apps
 * used last line up as big cards (the one in front first, marked "Now playing"), the chosen card
 * lifts out with a ring of light and its name above, and everything moves on springs. Left and
 * right (or the stick, or L1 / R1) choose; the host does the rest.
 */
public final class QuickSwitchView extends View {

    /** A card. */
    public static final class Card {
        public final String key;
        public final String title;
        public final String subtitle;
        public Bitmap art;
        /** The app's icon, drawn on its own colour when there's no art. */
        public Bitmap icon;
        public int tint = 0xFF2A2D36;
        public final boolean now;

        public Card(String key, String title, String subtitle, boolean now) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.now = now;
        }
    }

    private final float d;
    private final int accent;
    private final List<Card> cards = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint eyebrow = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint badge = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF r = new RectF();
    private final RectF dst = new RectF();

    private final Spring open = new Spring(220f, 0.8f, 0f);
    private final Spring scroll = new Spring(260f, 0.92f, 0f);
    private final Spring nameSwap = new Spring(420f, 1f, 1f);
    private Spring[] lift = new Spring[0];
    private Spring[] rise = new Spring[0];
    private long[] riseAt = new long[0];
    private long lastFrame;
    private int selected;
    private boolean closing;
    private Runnable closed;
    private String hints = "";
    private boolean loading = true;

    public QuickSwitchView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        accent = PocketPrefs.accentColor(c);
        Typeface medium = PocketFonts.medium();
        Typeface semibold = PocketFonts.semibold();
        title.setTypeface(semibold != null ? semibold : medium);
        title.setColor(0xFFFFFFFF);
        title.setTextSize(26 * c.getResources().getDisplayMetrics().scaledDensity);
        sub.setTypeface(medium);
        sub.setColor(0xB3FFFFFF);
        sub.setTextSize(13 * c.getResources().getDisplayMetrics().scaledDensity);
        eyebrow.setTypeface(medium);
        eyebrow.setColor(0x99FFFFFF);
        eyebrow.setLetterSpacing(0.2f);
        eyebrow.setTextSize(10.5f * c.getResources().getDisplayMetrics().scaledDensity);
        badge.setTypeface(medium);
        badge.setTextAlign(Paint.Align.CENTER);
        badge.setLetterSpacing(0.08f);
        badge.setTextSize(9.5f * c.getResources().getDisplayMetrics().scaledDensity);
        ring.setStyle(Paint.Style.STROKE);
        setWillNotDraw(false);
    }

    public void setHints(String hints) {
        this.hints = hints;
        invalidate();
    }

    /** Opens (empty: "looking...") with the bloom-in. */
    public void open() {
        open.snap(0f);
        open.target = 1f;
        closing = false;
        lastFrame = 0;
        invalidate();
    }

    public void setCards(List<Card> list, int focus) {
        cards.clear();
        cards.addAll(list);
        loading = false;
        lift = new Spring[cards.size()];
        rise = new Spring[cards.size()];
        riseAt = new long[cards.size()];
        long now = System.nanoTime();
        boolean anim = PocketPrefs.animationsEnabled(getContext());
        for (int i = 0; i < cards.size(); i++) {
            lift[i] = new Spring(520f, 0.62f, 0f);
            rise[i] = new Spring(300f, 0.78f, anim ? 0f : 1f);
            riseAt[i] = now + i * 45_000_000L;
        }
        selected = Math.max(0, Math.min(cards.size() - 1, focus));
        if (!cards.isEmpty()) {
            lift[selected].target = 1f;
        }
        scroll.snap(targetScroll());
        invalidate();
    }

    public void updateArt() {
        invalidate();
    }

    public int selected() {
        return cards.isEmpty() ? -1 : selected;
    }

    public Card selectedCard() {
        return cards.isEmpty() ? null : cards.get(selected);
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public void move(int dir) {
        if (cards.isEmpty()) {
            return;
        }
        int next = Math.max(0, Math.min(cards.size() - 1, selected + dir));
        if (next == selected) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            return;
        }
        lift[selected].target = 0f;
        selected = next;
        lift[selected].target = 1f;
        scroll.target = targetScroll();
        nameSwap.snap(0f);
        nameSwap.target = 1f;
        PocketSounds.play(getContext(), PocketSounds.NAV);
        invalidate();
    }

    /** Closes with the cards sinking away, then done. */
    public void close(Runnable done) {
        closing = true;
        closed = done;
        open.target = 0f;
        if (!PocketPrefs.animationsEnabled(getContext())) {
            open.snap(0f);
        }
        invalidate();
        // Done for sure, even if a frame never comes (the screen lost focus, a spring that won't settle)
        postDelayed(() -> {
            if (closed != null) {
                Runnable r = closed;
                closed = null;
                r.run();
            }
        }, 420);
    }

    /** It closed (or is closing): nothing of it is on screen any more. */
    public boolean isClosed() {
        return closing && closed == null;
    }

    private float cardW() {
        return Math.min(getHeight() * 0.36f, 170 * d);
    }

    private float cardH() {
        return cardW() * 1.42f;
    }

    private float gap() {
        return 22 * d;
    }

    private float targetScroll() {
        // The chosen card stays in the left third once the row is longer than the screen
        float w = getWidth() > 0 ? getWidth() : 1280;
        float left = 56 * d;
        float x = selected * (cardW() + gap());
        float maxScroll = Math.max(0, cards.size() * (cardW() + gap()) - gap() - (w - 2 * left));
        return Math.max(0, Math.min(maxScroll, x - (w - 2 * left) * 0.35f));
    }

    private boolean step() {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.033f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        boolean moving = open.step(dt);
        moving |= scroll.step(dt);
        moving |= nameSwap.step(dt);
        for (int i = 0; i < lift.length; i++) {
            moving |= lift[i].step(dt);
            if (rise[i].target == 0f && now >= riseAt[i]) {
                rise[i].target = 1f;
            }
            moving |= rise[i].step(dt) || rise[i].target == 0f;
        }
        return moving;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        scroll.snap(targetScroll());
    }

    @Override
    protected void onDraw(Canvas c) {
        boolean moving = step();
        float w = getWidth();
        float h = getHeight();
        float o = Math.max(0f, Math.min(1f, open.value));
        // The game behind, dimmed towards the bottom
        paint.setShader(new LinearGradient(0, 0, 0, h, (int) (0x8C * o) << 24, (int) (0xE6 * o) << 24,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        float left = 56 * d;
        float cw = cardW();
        float ch = cardH();
        float rowY = h * 0.56f;
        int alpha = (int) (255 * o);

        // Above: what this is, and the chosen one's name
        eyebrow.setAlpha((int) (0x99 * o));
        c.drawText(getContext().getString(R.string.pocket_quick_title).toUpperCase(java.util.Locale.getDefault()),
                left, rowY - ch / 2f - 86 * d, eyebrow);
        Card focus = cards.isEmpty() ? null : cards.get(selected);
        float swap = Math.max(0f, Math.min(1f, nameSwap.value));
        float ty = rowY - ch / 2f - 50 * d + (1 - swap) * 8 * d;
        title.setAlpha((int) (alpha * swap));
        sub.setAlpha((int) (0xB3 * o * swap));
        String name = focus != null ? focus.title : getContext().getString(loading ? R.string.pocket_gb_looking
                : R.string.pocket_gb_quick_empty);
        c.drawText(TextUtils.ellipsize(name, title, w - 2 * left, TextUtils.TruncateAt.END).toString(), left, ty, title);
        if (focus != null && focus.subtitle != null) {
            c.drawText(TextUtils.ellipsize(focus.subtitle, sub, w - 2 * left, TextUtils.TruncateAt.END).toString(),
                    left, ty + 24 * d, sub);
        }

        // The cards
        float sx = left - scroll.value;
        for (int i = 0; i < cards.size(); i++) {
            Card k = cards.get(i);
            float l = Math.max(0f, lift[i].value);
            float rv = Math.max(0f, Math.min(1.2f, rise[i].value));
            float scale = (0.92f + 0.08f * o) * (1f + 0.1f * l);
            float x = sx + i * (cw + gap());
            float cx = x + cw / 2f;
            float cy = rowY + (1 - rv) * 60 * d + (1 - o) * 30 * d - l * 6 * d;
            if (x + cw < -cw || x > w + cw) {
                continue;
            }
            float hw = cw * scale / 2f;
            float hh = ch * scale / 2f;
            r.set(cx - hw, cy - hh, cx + hw, cy + hh);
            int cardAlpha = (int) (alpha * Math.min(1f, rv) * (i == selected ? 1f : 0.72f + 0.28f * l));
            float rad = 16 * d;
            if (l > 0.02f) {
                glow.setShader(new RadialGradient(cx, cy, cw * 1.1f,
                        PocketTheme.withAlpha(accent, (int) (0x55 * Math.min(1f, l) * o)), 0, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, cw * 1.1f, glow);
            }
            c.save();
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.clipPath(clip);
            if (k.art != null) {
                float bw = k.art.getWidth();
                float bh = k.art.getHeight();
                float s = Math.max(r.width() / bw, r.height() / bh);
                dst.set(r.centerX() - bw * s / 2f, r.centerY() - bh * s / 2f, r.centerX() + bw * s / 2f,
                        r.centerY() + bh * s / 2f);
                paint.setAlpha(cardAlpha);
                c.drawBitmap(k.art, null, dst, paint);
            }
            else {
                // Its own colour, the icon big in the middle and its name under it
                paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, k.tint, darker(k.tint),
                        Shader.TileMode.CLAMP));
                paint.setAlpha(cardAlpha);
                c.drawRect(r, paint);
                paint.setShader(null);
                if (k.icon != null) {
                    float isz = r.width() * 0.46f;
                    dst.set(r.centerX() - isz / 2f, r.centerY() - isz / 2f - 10 * d, r.centerX() + isz / 2f,
                            r.centerY() + isz / 2f - 10 * d);
                    paint.setAlpha(cardAlpha);
                    c.drawBitmap(k.icon, null, dst, paint);
                }
                badge.setColor(0xFFFFFFFF);
                badge.setAlpha(cardAlpha);
                float tsz = badge.getTextSize();
                badge.setTextSize(tsz * 1.3f);
                c.drawText(TextUtils.ellipsize(k.title, badge, r.width() - 20 * d, TextUtils.TruncateAt.END).toString(),
                        r.centerX(), r.bottom - 22 * d, badge);
                badge.setTextSize(tsz);
            }
            // A soft shade at the bottom, a glass edge all round
            paint.setShader(new LinearGradient(0, r.bottom - r.height() * 0.35f, 0, r.bottom, 0, 0x66000000,
                    Shader.TileMode.CLAMP));
            paint.setAlpha(cardAlpha);
            c.drawRect(r, paint);
            paint.setShader(null);
            c.restore();
            ring.setStrokeWidth(1f * d);
            ring.setColor(PocketTheme.withAlpha(0xFFFFFFFF, (int) (0x33 * cardAlpha / 255f)));
            c.drawRoundRect(r, rad, rad, ring);
            if (l > 0.02f) {
                ring.setStrokeWidth(3.2f * d);
                ring.setColor(PocketTheme.withAlpha(0xFFFFFFFF, (int) (255 * Math.min(1f, l) * o)));
                RectF outer = new RectF(r.left - 5 * d, r.top - 5 * d, r.right + 5 * d, r.bottom + 5 * d);
                c.drawRoundRect(outer, rad + 5 * d, rad + 5 * d, ring);
            }
            if (k.now) {
                String tag = getContext().getString(R.string.pocket_gb_now_playing);
                float tw = badge.measureText(tag) + 16 * d;
                RectF pill = new RectF(r.centerX() - tw / 2f, r.top + 10 * d, r.centerX() + tw / 2f, r.top + 30 * d);
                paint.setColor(PocketTheme.withAlpha(accent, cardAlpha));
                c.drawRoundRect(pill, 10 * d, 10 * d, paint);
                badge.setColor(0xFFFFFFFF);
                badge.setAlpha(cardAlpha);
                c.drawText(tag, pill.centerX(), pill.bottom - 6.5f * d, badge);
            }
        }

        // The buttons' jobs
        sub.setAlpha((int) (0x99 * o));
        float hw = sub.measureText(hints);
        c.drawText(hints, w - left - hw, h - 26 * d, sub);

        if (moving) {
            postInvalidateOnAnimation();
        }
        else if (closing && closed != null) {
            Runnable done = closed;
            closed = null;
            done.run();
        }
    }

    private static int darker(int color) {
        int r = (int) (((color >> 16) & 0xFF) * 0.55f);
        int g = (int) (((color >> 8) & 0xFF) * 0.55f);
        int b = (int) ((color & 0xFF) * 0.6f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
