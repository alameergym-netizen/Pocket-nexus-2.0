package com.limelight.pocket.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketArt;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.ConsoleScreen;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketMotion;
import com.limelight.pocket.ui.PocketTheme;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Your play, like a console's year in review but for any day: the time played in all, this
 * week and today, a bar for each of the last seven days, and the games played most (Android
 * games, emulated games and streams alike).
 */
public class StatsActivity extends Activity {

    public static void open(Context c) {
        try {
            c.startActivity(new Intent(c, StatsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout cards;
    private Week week;
    private LinearLayout top;
    private int accent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConsoleActivity.applyFullscreenWindow(this);
        accent = PocketTheme.accent(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(ConsoleScreen.backdrop(this));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(true);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(30), dp(22), dp(30), dp(24));
        scroll.addView(column);

        column.addView(ConsoleScreen.title(this, getString(R.string.pocket_stats_title)));
        TextView sub = ConsoleScreen.text(this, 12, 0x8CFFFFFF, false);
        sub.setText(R.string.pocket_stats_sub);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(4);
        slp.bottomMargin = dp(16);
        column.addView(sub, slp);

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(cards, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(14);
        column.addView(body, blp);

        LinearLayout weekBox = panel();
        weekBox.addView(label(getString(R.string.pocket_stats_week)));
        week = new Week(this);
        weekBox.addView(week, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wlp.setMarginEnd(dp(14));
        body.addView(weekBox, wlp);

        top = panel();
        top.addView(label(getString(R.string.pocket_stats_top)));
        body.addView(top, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        LinearLayout hints = ConsoleScreen.hintBar(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        hlp.topMargin = dp(12);
        column.addView(hints, hlp);
        ConsoleActivity.fillHintBar(this, hints, ConsoleActivity.hintB(this, R.string.pocket_hint_back));
        setContentView(root);
        scroll.requestFocus();
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConsoleActivity.applyFullscreenWindow(this);
    }

    private int dp(float v) {
        return ConsoleScreen.dp(this, v);
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x14FFFFFF);
        bg.setCornerRadius(dp(18));
        bg.setStroke(Math.max(1, dp(0.8f)), 0x1FFFFFFF);
        p.setBackground(bg);
        return p;
    }

    private TextView label(String s) {
        TextView t = ConsoleScreen.text(this, 10, 0x99FFFFFF, true);
        t.setAllCaps(true);
        t.setLetterSpacing(0.12f);
        t.setText(s);
        t.setPadding(0, 0, 0, dp(10));
        return t;
    }

    private void card(String value, String what, boolean lit) {
        LinearLayout c = panel();
        if (lit) {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{(accent & 0x00FFFFFF) | 0x59000000, (accent & 0x00FFFFFF) | 0x1A000000});
            bg.setCornerRadius(dp(18));
            c.setBackground(bg);
        }
        TextView v = ConsoleScreen.text(this, 24, 0xFFFFFFFF, true);
        v.setTypeface(PocketFonts.semibold());
        v.setText(value);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(v);
        TextView w = ConsoleScreen.text(this, 11, 0x99FFFFFF, false);
        w.setText(what);
        w.setSingleLine(true);
        w.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        c.addView(w, lp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (cards.getChildCount() > 0) {
            clp.setMarginStart(dp(12));
        }
        cards.addView(c, clp);
        if (PocketPrefs.animationsEnabled(this)) {
            c.setAlpha(0f);
            c.setTranslationY(dp(10));
            c.animate().alpha(1f).translationY(0).setStartDelay(60L * cards.getChildCount()).setDuration(380)
                    .setInterpolator(PocketMotion.DECELERATE).start();
        }
    }

    private String time(long s) {
        String t = PlayTime.format(this, s);
        return t != null ? t : getString(R.string.pocket_stats_none);
    }

    private static final class Game {
        HomeItem item;
        long seconds;
        Bitmap cover;
    }

    private void load() {
        final Context app = getApplicationContext();
        new Thread(() -> {
            List<Game> games = new ArrayList<>();
            long total = 0;
            try {
                for (HomeStore.Entry e : HomeStore.load(app)) {
                    long s = PlayTime.seconds(app, e.item);
                    if (s >= 60) {
                        Game g = new Game();
                        g.item = e.item;
                        g.seconds = s;
                        games.add(g);
                        total += s;
                    }
                }
            } catch (RuntimeException ignored) {
            }
            Collections.sort(games, (a, b) -> Long.compare(b.seconds, a.seconds));
            for (int i = 0; i < games.size() && i < 6; i++) {
                Game g = games.get(i);
                String uuid = HomeActivity.artUuid(g.item);
                if (uuid != null) {
                    File f = PocketArt.file(app, uuid, HomeActivity.artId(g.item), PocketArt.COVER);
                    if (f.exists()) {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inSampleSize = 4;
                        g.cover = BitmapFactory.decodeFile(f.getPath(), o);
                    }
                }
            }
            final long[] days = PlayTime.lastDays(app, 7);
            final long all = total;
            final List<Game> list = games;
            main.post(() -> {
                if (isFinishing()) {
                    return;
                }
                long weekSum = 0;
                for (long d : days) {
                    weekSum += d;
                }
                card(time(all), getString(R.string.pocket_stats_total), true);
                card(time(weekSum), getString(R.string.pocket_stats_this_week), false);
                card(time(days[0]), getString(R.string.pocket_stats_today), false);
                card(String.valueOf(list.size()), getString(R.string.pocket_stats_games), false);
                week.set(days);
                fillTop(list);
            });
        }, "pocket-stats").start();
    }

    private void fillTop(List<Game> games) {
        if (games.isEmpty()) {
            TextView none = ConsoleScreen.text(this, 12.5f, 0x99FFFFFF, false);
            none.setText(R.string.pocket_stats_empty);
            top.addView(none);
            return;
        }
        long max = games.get(0).seconds;
        for (int i = 0; i < games.size() && i < 6; i++) {
            Game g = games.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(6));
                }
            });
            img.setClipToOutline(true);
            img.setBackgroundColor(0x1FFFFFFF);
            if (g.cover != null) {
                img.setImageBitmap(g.cover);
            }
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(26), dp(36));
            ilp.setMarginEnd(dp(12));
            row.addView(img, ilp);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            TextView name = ConsoleScreen.text(this, 13, 0xF2FFFFFF, true);
            name.setText(PocketPrefs.customName(this, HomeActivity.artUuid(g.item) != null ? HomeActivity.artUuid(g.item)
                    : g.item.kind, HomeActivity.artId(g.item), g.item.name));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView t = ConsoleScreen.text(this, 11.5f, 0xB3FFFFFF, true);
            t.setText(time(g.seconds));
            line.addView(t);
            texts.addView(line);
            View bar = new View(this);
            GradientDrawable bb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{accent, (accent & 0x00FFFFFF) | 0x80000000});
            bb.setCornerRadius(dp(3));
            bar.setBackground(bb);
            bar.setPivotX(0);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
            barLp.topMargin = dp(6);
            texts.addView(bar, barLp);
            final float f = Math.max(0.04f, g.seconds / (float) Math.max(1, max));
            bar.setScaleX(PocketPrefs.animationsEnabled(this) ? 0f : f);
            if (PocketPrefs.animationsEnabled(this)) {
                bar.animate().scaleX(f).setStartDelay(200L + i * 70L).setDuration(700)
                        .setInterpolator(PocketMotion.DECELERATE).start();
            }
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(10);
            top.addView(row, rlp);
        }
    }

    /** The last seven days as bars (today on the right), the hours over each. */
    private final class Week extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private long[] days = new long[7];
        private float grow = 1f;

        Week(Context c) {
            super(c);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(PocketFonts.medium());
            grid.setColor(0x14FFFFFF);
        }

        void set(long[] d) {
            days = d;
            if (PocketPrefs.animationsEnabled(getContext())) {
                android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
                a.setDuration(800);
                a.setInterpolator(PocketMotion.DECELERATE);
                a.addUpdateListener(v -> {
                    grow = (float) v.getAnimatedValue();
                    invalidate();
                });
                a.start();
            }
            else {
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float w = getWidth();
            float h = getHeight();
            float labelH = 18 * d;
            float topH = 16 * d;
            float chartH = h - labelH - topH;
            long max = 1;
            for (long s : days) {
                max = Math.max(max, s);
            }
            for (int i = 1; i <= 3; i++) {
                float y = topH + chartH * i / 4f;
                c.drawLine(0, y, w, y, grid);
            }
            float slot = w / 7f;
            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < 7; i++) {
                int day = 6 - i; // today on the right
                long s = days[day];
                float x = slot * i + slot * 0.22f;
                float bw = slot * 0.56f;
                float bh = s > 0 ? Math.max(4 * d, chartH * (s / (float) max) * grow) : 2 * d;
                r.set(x, topH + chartH - bh, x + bw, topH + chartH);
                boolean today = day == 0;
                bar.setShader(s > 0 ? new LinearGradient(0, r.top, 0, r.bottom, accent,
                        (accent & 0x00FFFFFF) | (today ? 0xB3000000 : 0x66000000), Shader.TileMode.CLAMP) : null);
                if (s <= 0) {
                    bar.setColor(0x26FFFFFF);
                }
                c.drawRoundRect(r, 6 * d, 6 * d, bar);
                if (s >= 60) {
                    text.setTextSize(9.5f * d);
                    text.setColor(0xCCFFFFFF);
                    String v = s >= 3600 ? String.format(Locale.getDefault(), "%.1fh", s / 3600f) : (s / 60) + "m";
                    c.drawText(v, r.centerX(), r.top - 4 * d, text);
                }
                cal.setTimeInMillis(System.currentTimeMillis() - day * 24L * 60 * 60 * 1000);
                String name = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
                text.setTextSize(10.5f * d);
                text.setColor(today ? 0xFFFFFFFF : 0x8CFFFFFF);
                c.drawText(name != null ? name : "", r.centerX(), h - 4 * d, text);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                PocketSounds.play(this, PocketSounds.CLOSE);
                finish();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
