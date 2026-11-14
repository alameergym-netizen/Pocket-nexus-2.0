package com.limelight.pocket.global;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import com.limelight.R;
import com.limelight.pocket.PocketArt;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.home.HomeActivity;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.home.HomeStore;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketIcons;
import com.limelight.pocket.ui.PocketTheme;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the buttons open over any game: the radial menu (eight actions round a ring) and Quick
 * Resume (the games played last, to jump between them). Drawn in an accessibility overlay on
 * top of whatever is in front; the controller drives it through the service's key filter (the
 * game underneath gets none of those presses).
 */
final class GlobalOverlay {

    interface Host {
        void run(GlobalButtons.Action action);

        void launch(HomeItem item);

        /** The app in front now (its package), or null. */
        String foreground();
    }

    private static final int MODE_NONE = 0;
    private static final int MODE_RADIAL = 1;
    private static final int MODE_QUICK = 2;

    private final Context context;
    private final Host host;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Panel view;
    private int mode = MODE_NONE;

    GlobalOverlay(Context context, Host host) {
        this.context = context;
        this.host = host;
        this.wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    boolean showing() {
        return mode != MODE_NONE;
    }

    void showRadial() {
        GlobalButtons.Action[] slots = new GlobalButtons.Action[GlobalButtons.RADIAL_SLOTS];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = GlobalButtons.radial(context, i);
        }
        open(MODE_RADIAL);
        view.radial = slots;
        view.selected = 0;
        view.invalidate();
    }

    void showQuickResume() {
        open(MODE_QUICK);
        view.cards.clear();
        view.selected = 0;
        view.invalidate();
        final String front = host.foreground();
        io.execute(() -> {
            List<Card> cards = new ArrayList<>();
            List<HomeStore.Entry> all;
            try {
                all = HomeStore.load(context);
            } catch (RuntimeException e) {
                all = new ArrayList<>();
            }
            List<HomeStore.Entry> recent = new ArrayList<>();
            for (HomeStore.Entry e : all) {
                if (e.lastUsed > 0) {
                    recent.add(e);
                }
            }
            java.util.Collections.sort(recent, (a, b) -> Long.compare(b.lastUsed, a.lastUsed));
            float d = context.getResources().getDisplayMetrics().density;
            int w = Math.round(120 * d);
            for (HomeStore.Entry e : recent) {
                if (cards.size() >= 8) {
                    break;
                }
                Card c = new Card();
                c.item = e.item;
                c.name = e.item.name;
                c.now = front != null && HomeItem.ANDROID.equals(e.item.kind) && front.equals(e.item.a);
                String uuid = HomeActivity.artUuid(e.item);
                if (uuid != null) {
                    c.art = decode(PocketArt.file(context, uuid, HomeActivity.artId(e.item), PocketArt.COVER), w);
                }
                cards.add(c);
            }
            main.post(() -> {
                if (view != null && mode == MODE_QUICK) {
                    view.cards.clear();
                    view.cards.addAll(cards);
                    // The game in front first, then the one before it (the usual jump)
                    view.selected = cards.size() > 1 && cards.get(0).now ? 1 : 0;
                    view.invalidate();
                }
            });
        });
    }

    private static Bitmap decode(File f, int width) {
        if (f == null || !f.exists()) {
            return null;
        }
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= width) {
                sample *= 2;
            }
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), d);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private void open(int m) {
        if (view == null) {
            view = new Panel(context);
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                    ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            try {
                wm.addView(view, lp);
            } catch (RuntimeException e) {
                view = null;
                return;
            }
        }
        mode = m;
        view.mode = m;
        view.appear();
        PocketSounds.play(context, PocketSounds.OPEN);
    }

    void hide() {
        mode = MODE_NONE;
        final Panel v = view;
        view = null;
        if (v == null) {
            return;
        }
        v.disappear(() -> {
            try {
                wm.removeView(v);
            } catch (RuntimeException ignored) {
            }
        });
    }

    /** All the keys while it's open (true: used here, the game doesn't get it). */
    boolean handleKey(KeyEvent event) {
        if (view == null) {
            return false;
        }
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_POWER
                || code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // These keep their own jobs (Home closes this on its way)
            if (code == KeyEvent.KEYCODE_HOME) {
                hide();
            }
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (event.getRepeatCount() == 0) {
                    PocketSounds.play(context, PocketSounds.CLOSE);
                    hide();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (event.getRepeatCount() == 0) {
                    choose();
                }
                return true;
            default:
                break;
        }
        if (mode == MODE_RADIAL) {
            int s = -1;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    s = step(view.selected, 0);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    s = step(view.selected, 2);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    s = step(view.selected, 4);
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    s = step(view.selected, 6);
                    break;
                case KeyEvent.KEYCODE_BUTTON_R1:
                case KeyEvent.KEYCODE_BUTTON_R2:
                    s = (view.selected + 1) % GlobalButtons.RADIAL_SLOTS;
                    break;
                case KeyEvent.KEYCODE_BUTTON_L1:
                case KeyEvent.KEYCODE_BUTTON_L2:
                    s = (view.selected + GlobalButtons.RADIAL_SLOTS - 1) % GlobalButtons.RADIAL_SLOTS;
                    break;
                default:
                    break;
            }
            if (s >= 0) {
                select(s);
            }
            return true;
        }
        int n = view.cards.size();
        if (n > 0) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_BUTTON_R1) {
                select(Math.min(n - 1, view.selected + 1));
            }
            else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_BUTTON_L1) {
                select(Math.max(0, view.selected - 1));
            }
        }
        return true;
    }

    /**
     * The D-pad on the ring: a direction picks its slot; the same direction again leans to the
     * slot beside it (so the four diagonals can be reached with the four arrows).
     */
    private static int step(int from, int target) {
        if (from == target) {
            return (target + 1) % GlobalButtons.RADIAL_SLOTS;
        }
        if (from == (target + 1) % GlobalButtons.RADIAL_SLOTS) {
            return (target + GlobalButtons.RADIAL_SLOTS - 1) % GlobalButtons.RADIAL_SLOTS;
        }
        return target;
    }

    private void select(int s) {
        if (view == null || s == view.selected) {
            return;
        }
        view.selected = s;
        view.pulse();
        PocketSounds.play(context, PocketSounds.NAV);
    }

    private void choose() {
        if (view == null) {
            return;
        }
        if (mode == MODE_RADIAL) {
            GlobalButtons.Action a = view.radial != null ? view.radial[view.selected] : null;
            PocketSounds.play(context, PocketSounds.SELECT);
            hide();
            if (a != null && a != GlobalButtons.Action.RADIAL) {
                // After the ring is gone (a screenshot shouldn't have it in)
                main.postDelayed(() -> host.run(a), 220);
            }
            return;
        }
        if (view.cards.isEmpty()) {
            hide();
            return;
        }
        Card c = view.cards.get(Math.max(0, Math.min(view.cards.size() - 1, view.selected)));
        PocketSounds.play(context, PocketSounds.SELECT);
        hide();
        if (!c.now) {
            host.launch(c.item);
        }
    }

    private static final class Card {
        HomeItem item;
        String name;
        Bitmap art;
        boolean now;
    }

    /** The overlay itself: the dimmed game, and the ring or the row of games. */
    private final class Panel extends View {
        final float d;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final int accent;
        int mode;
        GlobalButtons.Action[] radial;
        final List<Card> cards = new ArrayList<>();
        int selected;
        float shown;
        float pop = 1f;
        ValueAnimator anim;

        Panel(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            accent = PocketTheme.accent(c);
            Typeface t = PocketFonts.semibold();
            text.setTypeface(t != null ? t : Typeface.DEFAULT_BOLD);
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
        }

        void appear() {
            if (anim != null) {
                anim.cancel();
            }
            anim = ValueAnimator.ofFloat(shown, 1f);
            anim.setDuration(220);
            anim.setInterpolator(com.limelight.pocket.ui.PocketMotion.DECELERATE);
            anim.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        void disappear(Runnable done) {
            if (anim != null) {
                anim.cancel();
            }
            anim = ValueAnimator.ofFloat(shown, 0f);
            anim.setDuration(150);
            anim.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    done.run();
                }
            });
            anim.start();
        }

        void pulse() {
            ValueAnimator p = ValueAnimator.ofFloat(0.9f, 1f);
            p.setDuration(220);
            p.setInterpolator(com.limelight.pocket.ui.Spring.POP);
            p.addUpdateListener(a -> {
                pop = (float) a.getAnimatedValue();
                invalidate();
            });
            p.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(170 * shown), 0, 0, 0));
            c.drawRect(0, 0, getWidth(), getHeight(), paint);
            if (mode == MODE_RADIAL) {
                drawRadial(c);
            }
            else {
                drawQuick(c);
            }
        }

        private void drawRadial(Canvas c) {
            if (radial == null) {
                return;
            }
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float scale = 0.85f + 0.15f * shown;
            float ring = Math.min(getWidth(), getHeight()) * 0.33f * scale;
            int alpha = Math.round(255 * shown);
            // The ring's glass
            paint.setColor(Color.argb(Math.round(200 * shown), 18, 18, 24));
            c.drawCircle(cx, cy, ring + 44 * d, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f * d);
            paint.setColor(Color.argb(Math.round(50 * shown), 255, 255, 255));
            c.drawCircle(cx, cy, ring + 44 * d, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < radial.length; i++) {
                double ang = Math.toRadians(-90 + i * 45);
                float x = cx + (float) Math.cos(ang) * ring;
                float y = cy + (float) Math.sin(ang) * ring;
                boolean sel = i == selected;
                float rad = (sel ? 34 * pop : 28) * d;
                paint.setColor(sel ? withAlpha(0xFFFFFFFF, alpha) : Color.argb(Math.round(40 * shown), 255, 255, 255));
                c.drawCircle(x, y, rad, paint);
                if (sel) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3 * d);
                    paint.setColor(withAlpha(accent, alpha));
                    c.drawCircle(x, y, rad + 5 * d, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                PocketIcons.draw(c, icon(radial[i]), x, y, (sel ? 26 : 22) * d,
                        sel ? withAlpha(0xFF111114, alpha) : withAlpha(0xE6FFFFFF, alpha), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            // In the middle: what A does now
            text.setAlpha(alpha);
            text.setTextSize(15 * d);
            String name = getContext().getString(radial[selected].label);
            c.drawText(name, cx, cy + 5 * d, text);
            text.setTextSize(10.5f * d);
            text.setAlpha(Math.round(150 * shown));
            c.drawText(getContext().getString(R.string.pocket_gb_radial_hint), cx, cy + 24 * d, text);
        }

        private void drawQuick(Canvas c) {
            float w = 120 * d;
            float h = 180 * d;
            float gap = 18 * d;
            float cy = getHeight() / 2f + 14 * d;
            int alpha = Math.round(255 * shown);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(20 * d);
            text.setAlpha(alpha);
            float left = 40 * d;
            c.drawText(getContext().getString(R.string.pocket_launcher_quick), left, cy - h / 2f - 34 * d, text);
            text.setTextSize(11 * d);
            text.setAlpha(Math.round(160 * shown));
            c.drawText(getContext().getString(R.string.pocket_gb_quick_hint), left, cy - h / 2f - 16 * d, text);
            text.setTextAlign(Paint.Align.CENTER);
            if (cards.isEmpty()) {
                text.setTextSize(13 * d);
                text.setAlpha(Math.round(200 * shown));
                c.drawText(getContext().getString(R.string.pocket_gb_quick_empty), getWidth() / 2f, cy, text);
                return;
            }
            // The chosen card stays in view: the row slides under it
            float start = left;
            float selX = start + selected * (w + gap);
            if (selX + w > getWidth() - left) {
                start -= selX + w - (getWidth() - left);
            }
            float slide = (1f - shown) * 40 * d;
            for (int i = 0; i < cards.size(); i++) {
                Card k = cards.get(i);
                boolean sel = i == selected;
                float s = sel ? 1.08f * pop + (1 - pop) * 1.0f : 1f;
                float x = start + i * (w + gap) + slide;
                float cw = w * s;
                float ch = h * s;
                r.set(x + (w - cw) / 2f, cy - ch / 2f, x + (w - cw) / 2f + cw, cy + ch / 2f);
                float rad = 12 * d;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(Math.round(230 * shown), 34, 34, 42));
                c.drawRoundRect(r, rad, rad, paint);
                if (k.art != null) {
                    c.save();
                    android.graphics.Path clip = new android.graphics.Path();
                    clip.addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW);
                    c.clipPath(clip);
                    float bw = k.art.getWidth();
                    float bh = k.art.getHeight();
                    float sc = Math.max(r.width() / bw, r.height() / bh);
                    float dw = bw * sc;
                    float dh = bh * sc;
                    paint.setAlpha(sel ? alpha : Math.round(alpha * 0.7f));
                    c.drawBitmap(k.art, null, new RectF(r.centerX() - dw / 2f, r.centerY() - dh / 2f,
                            r.centerX() + dw / 2f, r.centerY() + dh / 2f), paint);
                    paint.setAlpha(255);
                    c.restore();
                }
                else {
                    text.setTextSize(13 * d);
                    text.setAlpha(alpha);
                    drawWrapped(c, k.name, r.centerX(), r.centerY(), r.width() - 16 * d);
                }
                if (sel) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3 * d);
                    paint.setColor(withAlpha(0xFFFFFFFF, alpha));
                    RectF o = new RectF(r.left - 4 * d, r.top - 4 * d, r.right + 4 * d, r.bottom + 4 * d);
                    c.drawRoundRect(o, rad + 4 * d, rad + 4 * d, paint);
                    paint.setStyle(Paint.Style.FILL);
                    text.setTextSize(13 * d);
                    text.setAlpha(alpha);
                    c.drawText(ellipsize(k.name, getWidth() * 0.6f), r.centerX(), r.bottom + 26 * d, text);
                }
                if (k.now) {
                    String tag = getContext().getString(R.string.pocket_gb_now_playing);
                    text.setTextSize(9.5f * d);
                    float tw = text.measureText(tag) + 14 * d;
                    RectF pill = new RectF(r.centerX() - tw / 2f, r.top + 8 * d, r.centerX() + tw / 2f, r.top + 26 * d);
                    paint.setColor(withAlpha(accent, alpha));
                    c.drawRoundRect(pill, 9 * d, 9 * d, paint);
                    text.setAlpha(alpha);
                    c.drawText(tag, r.centerX(), pill.bottom - 5.5f * d, text);
                }
            }
        }

        private void drawWrapped(Canvas c, String s, float cx, float cy, float maxW) {
            if (s == null) {
                return;
            }
            String[] words = s.split(" ");
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String w : words) {
                String next = line.length() == 0 ? w : line + " " + w;
                if (text.measureText(next) > maxW && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(w);
                }
                else {
                    line = new StringBuilder(next);
                }
            }
            lines.add(line.toString());
            float lh = text.getTextSize() * 1.25f;
            float y = cy - (lines.size() - 1) * lh / 2f + text.getTextSize() / 3f;
            for (String l : lines) {
                c.drawText(l, cx, y, text);
                y += lh;
            }
        }

        private String ellipsize(String s, float max) {
            if (s == null) {
                return "";
            }
            if (text.measureText(s) <= max) {
                return s;
            }
            String e = s;
            while (e.length() > 1 && text.measureText(e + "…") > max) {
                e = e.substring(0, e.length() - 1);
            }
            return e + "…";
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.round(Color.alpha(color) * alpha / 255f) << 24);
    }

    static String icon(GlobalButtons.Action a) {
        switch (a) {
            case HOME:
                return "GUIDE";
            case QUICK_RESUME:
                return "QUICK_MENU";
            case RADIAL:
                return "RADIAL";
            case LAST_GAME:
                return "RESTART";
            case RECENTS:
                return "ALT_TAB";
            case ANDROID_HOME:
                return "ANDROID_HOME";
            case BACK:
                return "ESC";
            case SCREENSHOT:
                return "SCREENSHOT";
            case NOTIFICATIONS:
                return "LIST";
            case QUICK_SETTINGS:
            case SETTINGS:
                return "SLIDERS";
            case PLAY_PAUSE:
                return "PLAY_PAUSE";
            case NEXT_TRACK:
                return "PLAY";
            case VOLUME_UP:
                return "VOLUME_UP";
            case VOLUME_DOWN:
                return "VOLUME_DOWN";
            default:
                return "NONE";
        }
    }
}
