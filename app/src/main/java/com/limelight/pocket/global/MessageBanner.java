package com.limelight.pocket.global;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.device.PocketNotifications;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketIcons;
import com.limelight.pocket.ui.PocketMotion;
import com.limelight.pocket.ui.Spring;

/**
 * A message (Discord, WhatsApp, Telegram...) shown over whatever is on screen, a game included,
 * the way a console shows one: a small glass card at the top that slides in and leaves by
 * itself. While it's there, Guide answers it (a quick reply, typed with the controller's
 * keyboard) instead of doing what it usually does, like the PS button on a PS5.
 */
public final class MessageBanner {

    private static final long SHOW_MS = 7000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static View card;
    private static WindowManager wm;
    private static PocketNotifications.Note showing;
    private static long shownAt;
    private static final Runnable HIDE = () -> hide(true);

    private MessageBanner() {
    }

    /** True while one is on screen (Guide then answers it). */
    public static boolean isShowing() {
        return card != null && showing != null;
    }

    /** Guide while it's shown: answers it (or opens it when it can't be answered here). */
    public static boolean consumeGuide(Context c) {
        if (!isShowing() || System.currentTimeMillis() - shownAt < 250) {
            return false;
        }
        PocketNotifications.Note n = showing;
        hide(false);
        if (n.canReply()) {
            QuickReplyActivity.open(c, n);
        }
        else {
            PocketNotifications.open(c, n);
        }
        return true;
    }

    private static boolean actable;

    /** True while one is on screen that Guide can answer (not over a stream: Guide is its). */
    public static boolean canAnswer() {
        return isShowing() && actable;
    }

    public static void show(final AccessibilityService service, final PocketNotifications.Note n, boolean act) {
        if (service == null || n == null) {
            return;
        }
        final float d = service.getResources().getDisplayMetrics().density;
        if (card != null) {
            removeNow();
        }
        wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return;
        }
        showing = n;
        actable = act;
        shownAt = System.currentTimeMillis();
        View v = build(service, n, d, act);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Math.round(500 * d), ViewGroup.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 22 ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = Math.round(14 * d);
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        try {
            wm.addView(v, lp);
        } catch (RuntimeException e) {
            showing = null;
            return;
        }
        card = v;
        PocketSounds.play(service, PocketSounds.OPEN);
        boolean motion = PocketPrefs.animationsEnabled(service);
        View inner = ((ViewGroup) v).getChildAt(0);
        if (motion) {
            inner.setTranslationY(-90 * d);
            inner.setAlpha(0f);
            inner.setScaleX(0.94f);
            inner.setScaleY(0.94f);
            inner.animate().translationY(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(420)
                    .setInterpolator(Spring.POP).start();
        }
        MAIN.removeCallbacks(HIDE);
        MAIN.postDelayed(HIDE, SHOW_MS);
    }

    private static final String IN_APP = "pocket.inapp.banner";

    /**
     * The same card inside one of this app's screens (no overlay needed): any notification while
     * a console screen is up, whole (the system's little toast cut it to the app and a title).
     * Pressing it opens what it's about; it leaves by itself.
     */
    public static void showIn(final android.app.Activity a, final PocketNotifications.Note n) {
        if (a == null || n == null || a.isFinishing()) {
            return;
        }
        final ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        final float d = a.getResources().getDisplayMetrics().density;
        View old = content.findViewWithTag(IN_APP);
        if (old != null) {
            content.removeView(old);
        }
        final View v = build(a, n, d, false);
        v.setTag(IN_APP);
        v.setElevation(60 * d);
        v.setClickable(true);
        v.setOnClickListener(x -> {
            content.removeView(v);
            if (n.canReply()) {
                QuickReplyActivity.open(a, n);
            }
            else {
                PocketNotifications.open(a, n);
            }
        });
        int w = Math.min(Math.round(520 * d), a.getResources().getDisplayMetrics().widthPixels - Math.round(32 * d));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Math.round(10 * d);
        content.addView(v, lp);
        final View inner = ((ViewGroup) v).getChildAt(0);
        final boolean motion = PocketPrefs.animationsEnabled(a);
        if (motion) {
            inner.setTranslationY(-90 * d);
            inner.setAlpha(0f);
            inner.setScaleX(0.94f);
            inner.setScaleY(0.94f);
            inner.animate().translationY(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(420)
                    .setInterpolator(Spring.POP).start();
        }
        // Long ones stay a little longer: time to read them
        long ms = SHOW_MS - 2000 + Math.min(4000, (n.title.length() + n.text.length()) * 25L);
        v.postDelayed(() -> {
            if (v.getParent() == null) {
                return;
            }
            if (motion) {
                inner.animate().translationY(-90 * d).alpha(0f).setDuration(260)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator())
                        .withEndAction(() -> content.removeView(v)).start();
            }
            else {
                content.removeView(v);
            }
        }, ms);
    }

    public static void hide(boolean animate) {
        MAIN.removeCallbacks(HIDE);
        final View v = card;
        showing = null;
        if (v == null) {
            return;
        }
        card = null;
        View inner = ((ViewGroup) v).getChildAt(0);
        if (animate && PocketPrefs.animationsEnabled(v.getContext())) {
            float d = v.getResources().getDisplayMetrics().density;
            inner.animate().translationY(-90 * d).alpha(0f).setDuration(260)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> remove(v)).start();
        }
        else {
            remove(v);
        }
    }

    private static void removeNow() {
        MAIN.removeCallbacks(HIDE);
        View v = card;
        card = null;
        showing = null;
        remove(v);
    }

    private static void remove(View v) {
        if (v == null || wm == null) {
            return;
        }
        try {
            wm.removeViewImmediate(v);
        } catch (RuntimeException ignored) {
        }
    }

    private static View build(Context c, PocketNotifications.Note n, float d, boolean act) {
        FrameLayout outer = new FrameLayout(c);
        outer.setClipChildren(false);
        outer.setClipToPadding(false);
        int pad = Math.round(14 * d);
        outer.setPadding(pad, pad / 2, pad, pad);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Math.round(14 * d), Math.round(12 * d), Math.round(16 * d), Math.round(12 * d));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF2202027, 0xF214141A});
        bg.setCornerRadius(20 * d);
        bg.setStroke(Math.max(1, Math.round(d)), 0x26FFFFFF);
        box.setBackground(bg);
        box.setElevation(18 * d);
        outer.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The sender (or the app), with the app's badge on its corner
        FrameLayout who = new FrameLayout(c);
        ImageView face = new ImageView(c);
        face.setScaleType(ImageView.ScaleType.CENTER_CROP);
        face.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        face.setClipToOutline(true);
        Drawable app = appIcon(c, n.packageName);
        Drawable pic = null;
        if (n.picture != null && Build.VERSION.SDK_INT >= 23) {
            try {
                pic = n.picture.loadDrawable(c);
            } catch (RuntimeException ignored) {
            }
        }
        face.setImageDrawable(pic != null ? pic : app);
        int fs = Math.round(44 * d);
        who.addView(face, new FrameLayout.LayoutParams(fs, fs));
        if (pic != null && app != null) {
            ImageView badge = new ImageView(c);
            badge.setImageDrawable(app);
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(0xFF16161C);
            badge.setBackground(ring);
            int bp = Math.round(2 * d);
            badge.setPadding(bp, bp, bp, bp);
            int bs = Math.round(20 * d);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(bs, bs, Gravity.BOTTOM | Gravity.END);
            blp.rightMargin = -Math.round(3 * d);
            blp.bottomMargin = -Math.round(3 * d);
            who.addView(badge, blp);
        }
        who.setClipChildren(false);
        box.addView(who, new LinearLayout.LayoutParams(fs, fs));

        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wl.setMarginStart(Math.round(13 * d));
        box.addView(words, wl);
        TextView from = new TextView(c);
        String where = n.conversation != null && !n.conversation.equals(n.title) ? "  ·  " + n.conversation : "";
        from.setText((n.app + where).toUpperCase(java.util.Locale.ROOT));
        from.setTypeface(PocketFonts.bold());
        from.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        from.setLetterSpacing(0.12f);
        from.setTextColor(0x99FFFFFF);
        from.setMaxLines(1);
        from.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(from);
        TextView title = new TextView(c);
        title.setText(n.title);
        title.setTypeface(PocketFonts.semibold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        title.setTextColor(0xFFFFFFFF);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Math.round(2 * d);
        words.addView(title, tl);
        TextView text = new TextView(c);
        text.setText(n.text);
        text.setTypeface(PocketFonts.regular());
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        text.setTextColor(0xD9FFFFFF);
        // The whole message, not its first line: up to five lines (a long one ends in "...")
        text.setMaxLines(5);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setLineSpacing(0, 1.12f);
        words.addView(text);

        // What Guide does now
        LinearLayout hint = new LinearLayout(c);
        hint.setOrientation(LinearLayout.VERTICAL);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView g = new ImageView(c);
        int gs = Math.round(26 * d);
        g.setImageDrawable(PocketIcons.drawable("GUIDE", Math.round(15 * d), 0xFF0B0B0D));
        g.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable gb = new GradientDrawable();
        gb.setShape(GradientDrawable.OVAL);
        gb.setColor(0xF2FFFFFF);
        g.setBackground(gb);
        hint.addView(g, new LinearLayout.LayoutParams(gs, gs));
        TextView what = new TextView(c);
        what.setText(n.canReply() ? R.string.pocket_message_reply : R.string.pocket_message_open);
        what.setTypeface(PocketFonts.semibold());
        what.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        what.setTextColor(0xCCFFFFFF);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Math.round(4 * d);
        hint.addView(what, hl);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.setMarginStart(Math.round(12 * d));
        if (act) {
            box.addView(hint, hlp);
        }
        return outer;
    }

    static Drawable appIcon(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return PocketIcons.drawable("BELL", 64, 0xFFFFFFFF);
        }
    }
}
