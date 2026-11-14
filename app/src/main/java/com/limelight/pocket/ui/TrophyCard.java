package com.limelight.pocket.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.retro.Achievements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The focused game's trophies in a library (RetroAchievements), on a glass card at the top end
 * of the screen like a console's game hub: how many of how many, a bar, the latest badges and
 * the one earned last. It floats over the art, so showing or hiding it never moves the row.
 */
public final class TrophyCard extends LinearLayout {

    private final float d;
    private final TextView label;
    private final TextView points;
    private final TextView count;
    private final TextView percent;
    private final Bar bar;
    private final LinearLayout badges;
    private final TextView last;
    private final TextView hint;
    private Achievements.Set shown;

    /** Puts a card on the screen (hidden until there's something to show), or null. */
    public static TrophyCard attach(Activity a) {
        View root = a.findViewById(R.id.pocketRoot);
        if (!(root instanceof FrameLayout)) {
            return null;
        }
        TrophyCard card = new TrophyCard(a);
        float d = a.getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(236 * d),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = Math.round(52 * d);
        lp.setMarginEnd(Math.round(24 * d));
        ((FrameLayout) root).addView(card, lp);
        return card;
    }

    public TrophyCard(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setVisibility(GONE);
        setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(0xC70F0F14);
        bg.setStroke(Math.max(1, dp(0.8f)), 0x24FFFFFF);
        setBackground(bg);
        setElevation(dp(10));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView cup = new ImageView(c);
        cup.setImageResource(R.drawable.ic_pocket_trophy);
        cup.setColorFilter(0xFFFFC94D);
        head.addView(cup, new LayoutParams(dp(14), dp(14)));
        label = text(9.5f, 0x99FFFFFF, true);
        label.setAllCaps(true);
        label.setLetterSpacing(0.1f);
        label.setText("RetroAchievements");
        LayoutParams llp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMarginStart(dp(6));
        head.addView(label, llp);
        points = text(10f, 0x99FFFFFF, true);
        head.addView(points);
        addView(head);

        LinearLayout big = new LinearLayout(c);
        big.setOrientation(HORIZONTAL);
        big.setGravity(Gravity.BOTTOM);
        count = text(22f, 0xFFFFFFFF, true);
        count.setTypeface(PocketFonts.semibold());
        count.setLetterSpacing(-0.02f);
        big.addView(count);
        percent = text(12f, 0xB3FFFFFF, true);
        percent.setPadding(dp(8), 0, 0, dp(3));
        big.addView(percent);
        LayoutParams blp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(4);
        addView(big, blp);

        bar = new Bar(c);
        LayoutParams barLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
        barLp.topMargin = dp(6);
        addView(bar, barLp);

        badges = new LinearLayout(c);
        badges.setOrientation(HORIZONTAL);
        LayoutParams bdl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bdl.topMargin = dp(10);
        addView(badges, bdl);

        last = text(10f, 0xA6FFFFFF, false);
        last.setSingleLine(true);
        last.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams lastLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lastLp.topMargin = dp(8);
        addView(last, lastLp);

        hint = text(10.5f, 0xCCFFFFFF, false);
        hint.setMaxLines(3);
        hint.setLineSpacing(0, 1.12f);
        hint.setVisibility(GONE);
        LayoutParams hlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(6);
        addView(hint, hlp);
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    private TextView text(float sp, int color, boolean medium) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(medium ? PocketFonts.medium() : PocketFonts.regular());
        t.setShadowLayer(6 * d, 0, 0, 0x66000000);
        t.setIncludeFontPadding(false);
        return t;
    }

    private boolean animated() {
        return PocketPrefs.animationsEnabled(getContext());
    }

    /** A game's trophies (null or none: the card goes away). */
    public void show(Achievements.Set set) {
        if (set == null || set.total <= 0) {
            hide();
            return;
        }
        boolean same = shown == set && getVisibility() == VISIBLE && hint.getVisibility() == GONE;
        shown = set;
        if (same) {
            return;
        }
        hint.setVisibility(GONE);
        for (int i = 1; i < getChildCount() - 1; i++) {
            getChildAt(i).setVisibility(VISIBLE);
        }
        boolean done = set.earned >= set.total;
        int pct = Math.round(set.earned * 100f / set.total);
        count.setText(set.earned + " / " + set.total);
        count.setTextColor(done ? 0xFFFFC94D : 0xFFFFFFFF);
        percent.setText(pct + "%");
        points.setText(set.pointsTotal > 0 ? set.points + " / " + set.pointsTotal + " pts" : "");
        bar.set(set.earned / (float) set.total, done, getVisibility() != VISIBLE && animated());
        fillBadges(set);
        Achievements.Trophy latest = null;
        for (Achievements.Trophy t : set.trophies) {
            if (t.earned && t.earnedAt > 0 && (latest == null || t.earnedAt > latest.earnedAt)) {
                latest = t;
            }
        }
        if (latest != null) {
            last.setText(getContext().getString(R.string.pocket_trophy_last, latest.title,
                    DateUtils.getRelativeTimeSpanString(latest.earnedAt, System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS)));
            last.setVisibility(VISIBLE);
        }
        else {
            last.setText(R.string.pocket_trophy_none_yet);
            last.setVisibility(VISIBLE);
        }
        appear();
    }

    /** A line instead of the numbers (no account yet, or none for this game). */
    public void showHint(String text) {
        shown = null;
        for (int i = 1; i < getChildCount() - 1; i++) {
            getChildAt(i).setVisibility(GONE);
        }
        hint.setText(text);
        hint.setVisibility(VISIBLE);
        points.setText("");
        appear();
    }

    private void appear() {
        if (getVisibility() == VISIBLE) {
            return;
        }
        setVisibility(VISIBLE);
        if (animated()) {
            animate().cancel();
            setAlpha(0f);
            setTranslationY(-dp(8));
            animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(PocketMotion.DECELERATE).start();
        }
    }

    public void hide() {
        shown = null;
        if (getVisibility() != VISIBLE) {
            return;
        }
        if (animated()) {
            animate().cancel();
            animate().alpha(0f).translationY(-dp(6)).setDuration(160).withEndAction(() -> {
                setVisibility(GONE);
                setAlpha(1f);
                setTranslationY(0f);
            }).start();
        }
        else {
            setVisibility(GONE);
        }
    }

    /** The latest ones earned, then the next ones to get (greyed). */
    private void fillBadges(Achievements.Set set) {
        badges.removeAllViews();
        List<Achievements.Trophy> earned = new ArrayList<>();
        List<Achievements.Trophy> locked = new ArrayList<>();
        for (Achievements.Trophy t : set.trophies) {
            (t.earned ? earned : locked).add(t);
        }
        Collections.sort(earned, (a, b) -> Long.compare(b.earnedAt, a.earnedAt));
        List<Achievements.Trophy> pick = new ArrayList<>();
        for (int i = 0; i < earned.size() && pick.size() < 3; i++) {
            pick.add(earned.get(i));
        }
        for (int i = 0; i < locked.size() && pick.size() < 6; i++) {
            pick.add(locked.get(i));
        }
        for (int i = 0; i < earned.size() && pick.size() < 6; i++) {
            if (!pick.contains(earned.get(i))) {
                pick.add(earned.get(i));
            }
        }
        int size = dp(30);
        int n = 0;
        for (final Achievements.Trophy t : pick) {
            final ImageView badge = new ImageView(getContext());
            badge.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bb = new GradientDrawable();
            bb.setColor(0xFF24242A);
            bb.setCornerRadius(dp(8));
            badge.setBackground(bb);
            badge.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
                }
            });
            badge.setClipToOutline(true);
            if (!t.earned) {
                android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
                m.setSaturation(0f);
                badge.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
            }
            final float alpha = t.earned ? 1f : 0.45f;
            badge.setAlpha(alpha);
            LayoutParams lp = new LayoutParams(size, size);
            lp.setMarginEnd(dp(6));
            badges.addView(badge, lp);
            String url = t.earned ? t.icon : (t.iconLocked != null ? t.iconLocked : t.icon);
            if (url != null) {
                RemoteImages.load(getContext(), url, size, b -> {
                    if (b != null) {
                        badge.setImageBitmap(b);
                    }
                });
            }
            if (animated()) {
                badge.setAlpha(0f);
                badge.setScaleX(0.8f);
                badge.setScaleY(0.8f);
                badge.animate().alpha(alpha).scaleX(1f).scaleY(1f).setStartDelay(80L + n * 40L).setDuration(240)
                        .setInterpolator(Spring.POP).start();
            }
            n++;
        }
        badges.setVisibility(pick.isEmpty() ? GONE : VISIBLE);
    }

    /** A rounded bar that fills with a gradient (gold when it's all done). */
    private static final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float shown;
        private boolean gold;

        Bar(Context c) {
            super(c);
            track.setColor(0x26FFFFFF);
        }

        void set(float fraction, boolean done, boolean animate) {
            final float target = Math.max(0f, Math.min(1f, fraction));
            gold = done;
            if (animate) {
                android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, target);
                va.setDuration(700);
                va.setStartDelay(120);
                va.setInterpolator(PocketMotion.DECELERATE);
                va.addUpdateListener(an -> {
                    shown = (float) an.getAnimatedValue();
                    invalidate();
                });
                va.start();
            }
            else {
                shown = target;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight();
            r.set(0, 0, getWidth(), h);
            c.drawRoundRect(r, h / 2f, h / 2f, track);
            if (shown <= 0f) {
                return;
            }
            r.set(0, 0, Math.max(h, getWidth() * shown), h);
            fill.setShader(new LinearGradient(0, 0, r.right, 0, gold ? 0xFFFFE08A : 0xFF6FB8FF,
                    gold ? 0xFFFFC94D : 0xFFC08CFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, h / 2f, h / 2f, fill);
        }
    }
}
