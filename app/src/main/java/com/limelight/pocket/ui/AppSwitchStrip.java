package com.limelight.pocket.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
import com.limelight.pocket.PocketSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick Switch: the open apps as a small row of icons in the middle of the screen, like
 * Alt+Tab. It opens on the app before the one in front; each press of the button moves on,
 * and it switches by itself a moment after you stop (or on A, or when the held button is let
 * go). Lighter than Quick Resume: for hopping between two or three apps.
 */
public final class AppSwitchStrip extends FrameLayout {

    public static final class App {
        public final String pkg;
        public final String name;
        public final Bitmap icon;
        public final boolean now;

        public App(String pkg, String name, Bitmap icon, boolean now) {
            this.pkg = pkg;
            this.name = name;
            this.icon = icon;
            this.now = now;
        }
    }

    private final float d;
    private final LinearLayout bar;
    private final LinearLayout row;
    private final TextView name;
    private final TextView sub;
    private final View timer;
    private final List<App> apps = new ArrayList<>();
    private final List<View> rings = new ArrayList<>();
    private final List<View> icons = new ArrayList<>();
    private int sel = -1;
    private boolean closing;
    private Runnable closed;

    public AppSwitchStrip(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClipChildren(false);
        setClipToPadding(false);
        View shade = new View(c);
        shade.setBackgroundColor(0x66000000);
        addView(shade, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        bar.setPadding(dp(22), dp(16), dp(22), dp(14));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xEB23232B, 0xEB15151B});
        bg.setCornerRadius(dp(30));
        bg.setStroke(Math.max(1, Math.round(d)), 0x26FFFFFF);
        bar.setBackground(bg);
        bar.setElevation(dp(24));
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        TextView eyebrow = new TextView(c);
        eyebrow.setText(c.getString(R.string.pocket_qswitch_title).toUpperCase(java.util.Locale.ROOT));
        eyebrow.setTypeface(PocketFonts.bold());
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setTextColor(0x8CFFFFFF);
        bar.addView(eyebrow);

        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(84));
        rl.topMargin = dp(8);
        bar.addView(row, rl);

        name = new TextView(c);
        name.setTypeface(PocketFonts.semibold());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        name.setTextColor(0xFFFFFFFF);
        name.setMaxLines(1);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = dp(4);
        bar.addView(name, nl);
        sub = new TextView(c);
        sub.setTypeface(PocketFonts.medium());
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        sub.setTextColor(0x99FFFFFF);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(2);
        bar.addView(sub, sl);

        // How long until it switches by itself
        timer = new View(c);
        GradientDrawable tb = new GradientDrawable();
        tb.setCornerRadius(dp(2));
        tb.setColor(0xCCFFFFFF);
        timer.setBackground(tb);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(dp(120), dp(3));
        tl.topMargin = dp(10);
        bar.addView(timer, tl);
        timer.setPivotX(0);
        timer.setAlpha(0f);
    }

    public void setApps(List<App> list, int focus) {
        apps.clear();
        apps.addAll(list);
        row.removeAllViews();
        rings.clear();
        icons.clear();
        for (int i = 0; i < apps.size(); i++) {
            App a = apps.get(i);
            FrameLayout cell = new FrameLayout(getContext());
            cell.setClipChildren(false);
            View ring = new View(getContext());
            GradientDrawable rd = new GradientDrawable();
            rd.setShape(GradientDrawable.OVAL);
            rd.setColor(0x14FFFFFF);
            rd.setStroke(Math.round(2.5f * d), 0xFFFFFFFF);
            ring.setBackground(rd);
            ring.setAlpha(0f);
            cell.addView(ring, new LayoutParams(dp(74), dp(74), Gravity.CENTER));
            ImageView icon = new ImageView(getContext());
            icon.setImageBitmap(a.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            icon.setElevation(dp(3));
            if (Build.VERSION.SDK_INT >= 28) {
                icon.setOutlineAmbientShadowColor(0xFFFFFFFF);
                icon.setOutlineSpotShadowColor(0xFFFFFFFF);
            }
            icon.setAlpha(a.now ? 0.55f : 1f);
            cell.addView(icon, new LayoutParams(dp(58), dp(58), Gravity.CENTER));
            if (a.now) {
                // A small dot under the one in front
                View dot = new View(getContext());
                GradientDrawable dd = new GradientDrawable();
                dd.setShape(GradientDrawable.OVAL);
                dd.setColor(0xCCFFFFFF);
                dot.setBackground(dd);
                LayoutParams dl = new LayoutParams(dp(5), dp(5), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                cell.addView(dot, dl);
            }
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(78), dp(84));
            if (i > 0) {
                cl.setMarginStart(dp(6));
            }
            row.addView(cell, cl);
            rings.add(ring);
            icons.add(icon);
            if (PocketPrefs.animationsEnabled(getContext())) {
                cell.setAlpha(0f);
                cell.setTranslationY(dp(10));
                cell.animate().alpha(1f).translationY(0).setStartDelay(30L * i).setDuration(260)
                        .setInterpolator(PocketMotion.DECELERATE).start();
            }
        }
        sel = -1;
        select(Math.max(0, Math.min(apps.size() - 1, focus)), false);
    }

    public int count() {
        return apps.size();
    }

    public App selectedApp() {
        return sel >= 0 && sel < apps.size() ? apps.get(sel) : null;
    }

    public void move(int dir) {
        if (apps.isEmpty() || closing) {
            return;
        }
        select((sel + dir + apps.size()) % apps.size(), true);
    }

    private void select(int i, boolean sound) {
        if (i == sel) {
            return;
        }
        boolean motion = PocketPrefs.animationsEnabled(getContext());
        if (sel >= 0 && sel < icons.size()) {
            icons.get(sel).animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(motion ? 160 : 0).start();
            rings.get(sel).animate().alpha(0f).scaleX(0.86f).scaleY(0.86f).setDuration(motion ? 150 : 0).start();
        }
        sel = i;
        View icon = icons.get(i);
        View ring = rings.get(i);
        icon.animate().scaleX(1.1f).scaleY(1.1f).translationZ(dp(12)).setDuration(motion ? 240 : 0)
                .setInterpolator(Spring.POP).start();
        ring.setScaleX(0.86f);
        ring.setScaleY(0.86f);
        ring.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(motion ? 240 : 0).setInterpolator(Spring.POP).start();
        App a = apps.get(i);
        PocketUi.swapText(name, a.name);
        sub.setText(a.now ? R.string.pocket_switch_now : R.string.pocket_switch_to);
        if (sound) {
            PocketSounds.play(getContext(), PocketSounds.NAV);
        }
    }

    /** The bar under it runs down until it switches by itself. */
    public void countdown(long ms) {
        timer.animate().cancel();
        timer.setAlpha(1f);
        timer.setScaleX(1f);
        timer.animate().scaleX(0f).setDuration(ms).setInterpolator(null).start();
    }

    public void stopCountdown() {
        timer.animate().cancel();
        timer.animate().alpha(0f).setDuration(120).start();
    }

    public void open() {
        if (!PocketPrefs.animationsEnabled(getContext())) {
            return;
        }
        bar.setScaleX(0.9f);
        bar.setScaleY(0.9f);
        bar.setAlpha(0f);
        bar.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(Spring.POP).start();
    }

    public boolean isClosed() {
        return closing && closed == null;
    }

    public void close(Runnable done) {
        if (closing) {
            return;
        }
        closing = true;
        closed = done;
        Runnable end = () -> {
            Runnable r = closed;
            closed = null;
            if (r != null) {
                r.run();
            }
        };
        if (PocketPrefs.animationsEnabled(getContext())) {
            bar.animate().scaleX(0.94f).scaleY(0.94f).alpha(0f).setDuration(150).withEndAction(end).start();
            animate().alpha(0f).setDuration(170).start();
            postDelayed(end, 400);
        }
        else {
            end.run();
        }
    }

    private int dp(float v) {
        return Math.round(v * d);
    }
}
