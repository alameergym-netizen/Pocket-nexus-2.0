package com.limelight.pocket.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.pocket.info.Ratings;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketMotion;

/**
 * The facts under a game's name, the way a console's game hub shows them: each score a small
 * ring that fills to it in its colour (the number inside, whose score and a word for it beside
 * it), then the plain facts (last played, time played, trophies, year and genre), each a small
 * caption over its value, with hairlines between them.
 */
public final class MetaStrip {

    private MetaStrip() {
    }

    private static float d(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    /** A score as a ring that fills to it, and whose it is, and a word for it. */
    public static View score(Context c, int score, String value, String source, String note, boolean animate) {
        float d = d(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        String shown = value != null && value.length() <= 3 ? value : score >= 0 ? String.valueOf(score) : value;
        Ring ring = new Ring(c, score, shown);
        int rs = Math.round(34 * d);
        box.addView(ring, new LinearLayout.LayoutParams(rs, rs));
        if (animate) {
            ring.grow();
        }
        String word = note != null && !note.isEmpty() && note.length() <= 18 && !note.matches(".*\\d.*")
                ? note : verdict(c, score);
        LinearLayout words = twoLines(c, source, word);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.setMarginStart(Math.round(9 * d));
        box.addView(words, wl);
        return box;
    }

    /** Just the ring (for the game's page): the score's arc in its colour, the number inside. */
    public static View ring(Context c, int score, String value, boolean animate) {
        String shown = value != null && value.length() <= 3 ? value
                : value != null && value.contains("/") && value.indexOf('/') <= 3 ? value.substring(0, value.indexOf('/'))
                : score >= 0 ? String.valueOf(score) : value;
        Ring r = new Ring(c, score, shown);
        if (animate) {
            r.grow();
        }
        return r;
    }

    /** A plain fact: a small caption (with its icon) over its value. */
    public static View fact(Context c, int icon, String caption, String value) {
        float d = d(c);
        LinearLayout words = twoLines(c, caption, value);
        if (icon != 0) {
            TextView cap = (TextView) words.getChildAt(0);
            Drawable dr = c.getDrawable(icon);
            if (dr != null) {
                dr = dr.mutate();
                dr.setTint(0x99FFFFFF);
                int s = Math.round(11 * d);
                dr.setBounds(0, 0, s, s);
                cap.setCompoundDrawablesRelative(dr, null, null, null);
                cap.setCompoundDrawablePadding(Math.round(4 * d));
            }
        }
        return words;
    }

    /** The hairline between two facts. */
    public static View hairline(Context c) {
        float d = d(c);
        View v = new View(c);
        v.setBackgroundColor(0x38FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, Math.round(d)), Math.round(26 * d));
        lp.setMarginStart(Math.round(14 * d));
        lp.setMarginEnd(Math.round(14 * d));
        v.setLayoutParams(lp);
        return v;
    }

    private static LinearLayout twoLines(Context c, String caption, String value) {
        float d = d(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView cap = new TextView(c);
        cap.setText(caption != null ? caption.toUpperCase(java.util.Locale.ROOT) : "");
        cap.setTypeface(PocketFonts.bold());
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        cap.setLetterSpacing(0.12f);
        cap.setTextColor(0x9EFFFFFF);
        cap.setIncludeFontPadding(false);
        cap.setGravity(Gravity.CENTER_VERTICAL);
        cap.setMaxLines(1);
        cap.setEllipsize(TextUtils.TruncateAt.END);
        cap.setShadowLayer(8 * d, 0, d, 0x99000000);
        col.addView(cap);
        TextView val = new TextView(c);
        val.setText(value);
        val.setTypeface(PocketFonts.semibold());
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        val.setTextColor(0xFFFFFFFF);
        val.setIncludeFontPadding(false);
        val.setMaxLines(1);
        val.setEllipsize(TextUtils.TruncateAt.END);
        val.setMaxWidth(Math.round(150 * d));
        val.setShadowLayer(10 * d, 0, d, 0x99000000);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.topMargin = Math.round(4 * d);
        col.addView(val, vl);
        return col;
    }

    /** A word for a score out of 100. */
    public static String verdict(Context c, int score) {
        if (score < 0) {
            return c.getString(R.string.pocket_verdict_rated);
        }
        return c.getString(score >= 90 ? R.string.pocket_verdict_acclaim
                : score >= 75 ? R.string.pocket_verdict_favorable
                : score >= 50 ? R.string.pocket_verdict_mixed : R.string.pocket_verdict_unfavorable);
    }

    /**
     * Drops what doesn't fit from the end (and the hairline before it), so the strip never runs
     * out of its column; call after it's laid out.
     */
    public static void fit(LinearLayout strip, int room) {
        if (room <= 0) {
            return;
        }
        strip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        while (strip.getChildCount() > 1 && strip.getMeasuredWidth() > room) {
            strip.removeViewAt(strip.getChildCount() - 1);
            if (strip.getChildCount() > 0 && !(strip.getChildAt(strip.getChildCount() - 1) instanceof ViewGroup)) {
                strip.removeViewAt(strip.getChildCount() - 1);
            }
            strip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        }
    }

    /** The ring: a faint track, the score's arc in its colour with a soft glow, the number inside. */
    static final class Ring extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final int score;
        private final String shown;
        private float progress = 1f;

        Ring(Context c, int score, String shown) {
            super(c);
            this.score = score;
            this.shown = shown != null ? shown : "";
            float d = d(c);
            int color = Ratings.color(score);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(3f * d);
            track.setColor(0x2EFFFFFF);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(3f * d);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(color);
            arc.setShadowLayer(5 * d, 0, 0, (color & 0x00FFFFFF) | 0xAA000000);
            fill.setColor(0x59000000);
            text.setColor(0xFFFFFFFF);
            text.setTypeface(PocketFonts.bold());
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize((this.shown.length() >= 3 ? 10.5f : 12f) * getResources().getDisplayMetrics().scaledDensity);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        void grow() {
            progress = 0f;
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(700);
            a.setStartDelay(140);
            a.setInterpolator(PocketMotion.DECELERATE);
            a.addUpdateListener(an -> {
                progress = (float) an.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            // The number in proportion to the ring (a small one on Home, a bigger one on a game's page)
            text.setTextSize(Math.min(w, h) * (shown.length() >= 3 ? 0.30f : 0.36f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            float inset = 3.5f * d;
            oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawOval(oval, fill);
            canvas.drawOval(oval, track);
            float sweep = 360f * Math.max(0, Math.min(100, score < 0 ? 100 : score)) / 100f * progress;
            if (sweep > 0.5f) {
                canvas.drawArc(oval, -90f, sweep, false, arc);
            }
            Paint.FontMetrics fm = text.getFontMetrics();
            float y = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(shown, getWidth() / 2f, y, text);
        }
    }
}
