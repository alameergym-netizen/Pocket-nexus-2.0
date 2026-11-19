package com.limelight.pocket.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.GamePageActivity;
import com.limelight.pocket.PocketArt;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.retro.RetroLibrary;
import com.limelight.pocket.retro.RomScanner;
import com.limelight.pocket.ui.ConsoleScreen;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketUi;
import com.limelight.pocket.ui.Spring;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * "Surprise me": can't choose what to play? The whole library spins past as a reel of covers,
 * slows with a tick for each, and lands on one. A plays it, X opens its page, Y spins again.
 */
public class SurpriseActivity extends Activity {

    public static void open(Activity from) {
        PocketSounds.play(from, PocketSounds.OPEN);
        from.startActivity(new Intent(from, SurpriseActivity.class));
        from.overridePendingTransition(android.R.anim.fade_in, 0);
    }

    private static final class Pick {
        final HomeItem item;
        final String name;
        final String where;
        Bitmap cover;

        Pick(HomeItem item, String name, String where) {
            this.item = item;
            this.name = name;
            this.where = where;
        }
    }

    private static final int SLOTS = 34;
    private static final int LAND = 29;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<Pick> all = new ArrayList<>();
    private float d;
    private FrameLayout root;
    private ImageView backdrop;
    private LinearLayout reel;
    private TextView eyebrow;
    private TextView title;
    private TextView where;
    private LinearLayout hints;
    private ValueAnimator spin;
    private Pick winner;
    private View winnerCard;
    private boolean landed;
    private int cardW;
    private int gap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConsoleActivity.applyFullscreenWindow(this);
        d = getResources().getDisplayMetrics().density;
        cardW = dp(150);
        gap = dp(18);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF07070A);
        setContentView(root);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        if (Build.VERSION.SDK_INT >= 31) {
            backdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(60 * d, 60 * d, Shader.TileMode.CLAMP));
        }
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB3000000, 0x66000000, 0xD9000000}));
        root.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        eyebrow = text(11f, 0x99FFFFFF, PocketFonts.bold());
        eyebrow.setLetterSpacing(0.2f);
        eyebrow.setText(getString(R.string.pocket_surprise_title).toUpperCase(java.util.Locale.ROOT));
        head.addView(eyebrow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hl.topMargin = dp(34);
        root.addView(head, hl);

        reel = new LinearLayout(this);
        reel.setOrientation(LinearLayout.HORIZONTAL);
        reel.setGravity(Gravity.CENTER_VERTICAL);
        reel.setClipChildren(false);
        reel.setClipToPadding(false);
        // In a scroll view only so it can be as long as it needs (it moves by itself, not by scrolling)
        android.widget.HorizontalScrollView holder = new android.widget.HorizontalScrollView(this);
        holder.setHorizontalScrollBarEnabled(false);
        holder.setFocusable(false);
        holder.setClipChildren(false);
        holder.setOverScrollMode(View.OVER_SCROLL_NEVER);
        holder.setOnTouchListener((v, e) -> true);
        holder.addView(reel, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(270)));
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(270),
                Gravity.CENTER_VERTICAL);
        rl.topMargin = -dp(14);
        root.addView(holder, rl);

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setGravity(Gravity.CENTER_HORIZONTAL);
        title = text(24f, 0xFFFFFFFF, PocketFonts.display());
        title.setGravity(Gravity.CENTER);
        title.setMaxWidth(dp(640));
        // Its own width (a vertical row's default would squeeze it to the hints' width)
        foot.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        where = text(13f, 0xB3FFFFFF, PocketFonts.medium());
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.topMargin = dp(4);
        foot.addView(where, wl);
        hints = ConsoleScreen.hintBar(this);
        LinearLayout.LayoutParams hbl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hbl.topMargin = dp(14);
        foot.addView(hints, hbl);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fl.bottomMargin = dp(24);
        root.addView(foot, fl);
        hints.setAlpha(0f);
        title.setText(R.string.pocket_surprise_finding);
        gather();
    }

    // ---- What it can land on ------------------------------------------------------------------

    private void gather() {
        final Context app = getApplicationContext();
        new Thread(() -> {
            List<Pick> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            try {
                for (HomeStore.Entry e : HomeStore.load(app)) {
                    HomeItem i = e.item;
                    if (i == null || !(HomeItem.ANDROID.equals(i.kind) || playable(app, i)) || !seen.add(i.key())) {
                        continue;
                    }
                    found.add(new Pick(i, i.name, e.pcName.isEmpty() ? app.getString(R.string.pocket_surprise_from_home) : e.pcName));
                }
            } catch (RuntimeException ignored) {
            }
            try {
                File rootDir = RetroLibrary.romRoot(app);
                RetroLibrary.Library lib = rootDir != null ? RetroLibrary.cached(app, rootDir) : null;
                if (lib == null && rootDir != null) {
                    try {
                        lib = RetroLibrary.scan(app, rootDir);
                    } catch (RuntimeException ignored) {
                    }
                }
                if (lib != null) {
                    com.limelight.pocket.retro.EsdeConfig cfg = RetroLibrary.config(app);
                    for (List<RomScanner.Rom> roms : lib.games.values()) {
                        for (RomScanner.Rom r : roms) {
                            HomeItem item = new HomeItem(HomeItem.RETRO, RomScanner.searchName(r.name), r.system, r.relPath, "", 0);
                            if (seen.add(item.key())) {
                                com.limelight.pocket.retro.EsdeConfig.SystemDef s = cfg.system(r.system);
                                found.add(new Pick(item, RomScanner.searchName(r.name), s != null ? s.fullName : r.system));
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) {
            }
            // Covers for a good handful (the reel looks best with them; those without get a card)
            Collections.shuffle(found, random);
            int withCover = 0;
            for (Pick p : found) {
                if (withCover >= 40) {
                    break;
                }
                p.cover = cover(app, p.item);
                if (p.cover != null) {
                    withCover++;
                }
            }
            main.post(() -> {
                if (isFinishing()) {
                    return;
                }
                all.clear();
                all.addAll(found);
                if (all.isEmpty()) {
                    title.setText(R.string.pocket_surprise_empty);
                    PocketSounds.play(this, PocketSounds.ERROR);
                    showHints(false);
                    return;
                }
                spinNow();
            });
        }, "pocket-surprise").start();
    }

    /** Games only: an Android app counts when it says it's a game. */
    private static boolean playable(Context c, HomeItem i) {
        if (HomeItem.ANDROID.equals(i.kind)) {
            try {
                ApplicationInfo ai = c.getPackageManager().getApplicationInfo(i.a, 0);
                return Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME;
            } catch (Exception e) {
                return false;
            }
        }
        return HomeItem.PC.equals(i.kind) || HomeItem.PC_LIBRARY_GAME.equals(i.kind) || HomeItem.RETRO.equals(i.kind)
                || HomeItem.CLOUD.equals(i.kind) || HomeItem.NATIVE.equals(i.kind);
    }

    private static Bitmap cover(Context c, HomeItem item) {
        String uuid = HomeActivity.artUuid(item);
        if (uuid == null) {
            return null;
        }
        int id = HomeActivity.artId(item);
        File f = PocketArt.file(c, uuid, id, PocketArt.COVER);
        if (!f.exists()) {
            return null;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 2;
        try {
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }

    // ---- The spin ------------------------------------------------------------------------------

    private void spinNow() {
        landed = false;
        showHints(false);
        title.setText(R.string.pocket_surprise_spinning);
        where.setText("");
        reel.removeAllViews();
        winner = all.get(random.nextInt(all.size()));
        List<Pick> slots = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            slots.add(i == LAND ? winner : all.get(random.nextInt(all.size())));
        }
        int pad = getResources().getDisplayMetrics().widthPixels / 2;
        reel.setPadding(pad, 0, pad, 0);
        for (int i = 0; i < slots.size(); i++) {
            View card = card(slots.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, dp(210));
            if (i > 0) {
                lp.setMarginStart(gap);
            }
            reel.addView(card, lp);
            if (i == LAND) {
                winnerCard = card;
            }
        }
        final float from = offset(2);
        final float to = offset(LAND);
        reel.setTranslationX(from);
        if (spin != null) {
            spin.cancel();
        }
        boolean motion = PocketPrefs.animationsEnabled(this);
        spin = ValueAnimator.ofFloat(0f, 1f);
        spin.setDuration(motion ? 3400 : 1);
        spin.setInterpolator(new DecelerateInterpolator(2.4f));
        final int[] last = {-1};
        spin.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            float x = from + (to - from) * f;
            reel.setTranslationX(x);
            int centered = centeredIndex(x);
            if (centered != last[0]) {
                last[0] = centered;
                PocketSounds.play(this, PocketSounds.NAV);
                if (centered >= 0 && centered < reel.getChildCount() && centered < SLOTS - 1) {
                    Pick p = slots.get(centered);
                    if (p.cover != null) {
                        backdrop.setImageBitmap(p.cover);
                        backdrop.setAlpha(0.55f);
                    }
                }
            }
            scaleAround(x);
        });
        spin.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!isFinishing()) {
                    land();
                }
            }
        });
        spin.start();
    }

    /** The reel's x that puts slot i in the middle of the screen. */
    private float offset(int i) {
        return -(i * (cardW + gap) + cardW / 2f);
    }

    private int centeredIndex(float x) {
        return Math.round((-x - cardW / 2f) / (cardW + gap));
    }

    /** Cards near the middle are bigger and brighter. */
    private void scaleAround(float x) {
        float mid = -x - cardW / 2f;
        for (int i = 0; i < reel.getChildCount(); i++) {
            View v = reel.getChildAt(i);
            float at = i * (cardW + gap);
            float dist = Math.abs(at - mid) / (cardW + gap);
            float s = 1f + 0.16f * Math.max(0f, 1f - dist);
            v.setScaleX(s);
            v.setScaleY(s);
            v.setAlpha(Math.max(0.35f, 1f - dist * 0.22f));
        }
    }

    private void land() {
        landed = true;
        PocketSounds.play(this, PocketSounds.SELECT);
        PocketUi.haptic(this, PocketUi.HAPTIC_HEAVY);
        for (int i = 0; i < reel.getChildCount(); i++) {
            View v = reel.getChildAt(i);
            if (v != winnerCard) {
                v.animate().alpha(0.22f).scaleX(0.94f).scaleY(0.94f).setDuration(320).start();
            }
        }
        if (winnerCard != null) {
            winnerCard.setElevation(dp(24));
            View ring = ((ViewGroup) winnerCard).findViewWithTag("ring");
            if (ring != null) {
                ring.animate().alpha(1f).setDuration(260).start();
            }
            winnerCard.animate().scaleX(1.24f).scaleY(1.24f).alpha(1f).setDuration(520).setInterpolator(Spring.POP).start();
        }
        if (winner.cover != null) {
            backdrop.setImageBitmap(winner.cover);
            backdrop.animate().alpha(0.75f).setDuration(500).start();
        }
        PocketUi.swapText(title, winner.name);
        where.setText(winner.where);
        showHints(true);
    }

    private void showHints(boolean landedNow) {
        if (landedNow) {
            ConsoleActivity.fillHintBar(this, hints,
                    ConsoleActivity.hintA(this, R.string.pocket_surprise_play),
                    ConsoleActivity.hintX(this, R.string.pocket_surprise_page),
                    ConsoleActivity.hintY(this, R.string.pocket_surprise_again),
                    ConsoleActivity.hintB(this, R.string.pocket_hint_back));
        }
        else {
            ConsoleActivity.fillHintBar(this, hints, ConsoleActivity.hintB(this, R.string.pocket_hint_back));
        }
        hints.animate().alpha(1f).setDuration(240).start();
    }

    private View card(Pick p) {
        FrameLayout f = new FrameLayout(this);
        f.setClipChildren(false);
        FrameLayout inner = new FrameLayout(this);
        inner.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 16 * d);
            }
        });
        inner.setClipToOutline(true);
        inner.setElevation(dp(6));
        if (p.cover != null) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(p.cover);
            inner.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        else {
            // No cover: its name on a colour of its own
            int hue = (p.name.hashCode() & 0x7fffffff) % 360;
            int a = android.graphics.Color.HSVToColor(new float[]{hue, 0.55f, 0.55f});
            int b = android.graphics.Color.HSVToColor(new float[]{(hue + 40) % 360, 0.7f, 0.22f});
            inner.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b}));
            TextView t = text(15f, 0xFFFFFFFF, PocketFonts.display());
            t.setText(p.name);
            t.setMaxLines(4);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(12), 0, dp(12), 0);
            inner.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        f.addView(inner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View ring = new View(this);
        ring.setTag("ring");
        GradientDrawable rd = new GradientDrawable();
        rd.setCornerRadius(19 * d);
        rd.setStroke(Math.round(2.5f * d), 0xFFFFFFFF);
        ring.setBackground(rd);
        ring.setAlpha(0f);
        ring.setElevation(dp(7));
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        int o = -dp(4);
        rl.setMargins(o, o, o, o);
        f.addView(ring, rl);
        return f;
    }

    // ---- Keys ----------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) {
            return true;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                PocketSounds.play(this, PocketSounds.BACK);
                close();
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (landed && !all.isEmpty()) {
                    PocketSounds.play(this, PocketSounds.OPEN);
                    spinNow();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!landed && spin != null && spin.isRunning()) {
                    spin.end(); // A while it spins: stop now
                    return true;
                }
                if (landed) {
                    play();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (landed) {
                    page();
                }
                return true;
            default:
                return true;
        }
    }

    private void play() {
        PocketSounds.play(this, PocketSounds.SELECT);
        HomeItem item = winner.item;
        if (HomeItem.PC.equals(item.kind) || HomeItem.PC_LIBRARY_GAME.equals(item.kind) || HomeItem.PC_GAMES.equals(item.kind)) {
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(HomeActivity.EXTRA_ACTION, HomeActivity.ACTION_LAUNCH)
                    .putExtra(HomeActivity.EXTRA_ITEM, item.toLine()));
        }
        else {
            HomeLauncher.launch(this, item);
        }
        finish();
    }

    private void page() {
        String uuid = HomeActivity.artUuid(winner.item);
        if (uuid == null) {
            PocketSounds.play(this, PocketSounds.ERROR);
            return;
        }
        PocketSounds.play(this, PocketSounds.OPEN);
        GamePageActivity.open(this, winnerCard, uuid, HomeActivity.artId(winner.item), RomScanner.searchName(winner.name),
                winner.where, false, false, winner.cover);
    }

    private void close() {
        if (spin != null) {
            spin.cancel();
        }
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (spin != null) {
            spin.removeAllUpdateListeners();
            spin.removeAllListeners();
            spin.cancel();
        }
    }

    private TextView text(float sp, int color, android.graphics.Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * d);
    }
}
