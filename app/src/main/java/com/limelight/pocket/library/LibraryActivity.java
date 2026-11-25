package com.limelight.pocket.library;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Outline;
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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.GamePageActivity;
import com.limelight.pocket.PocketArt;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.home.HomeActivity;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.home.HomeLauncher;
import com.limelight.pocket.home.PlayTime;
import com.limelight.pocket.ui.ConsoleScreen;
import com.limelight.pocket.ui.FocusGlow;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.PocketMotion;
import com.limelight.pocket.ui.StreamMenuView;
import com.limelight.pocket.ui.TextPrompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Library: every game from every platform in one place (the same game from two PCs and a
 * ROM folder is one card), as Apple TV shows a library: the focused game's art wide across the
 * top with its name, where it plays and how long you've played it, and the covers below.
 * L1 / R1: what's shown (all, playing, backlog, finished, a platform, your collections);
 * L2 / R2: the order (last played, A to Z, most played); A plays it (the way you played it
 * last); Y: every way to play it, its status and its collections; X: its page; Select: one
 * picked for you.
 */
public class LibraryActivity extends ConsoleActivity {

    private static final String KEY_SORT = "pocket_ul_sort";
    private static final String STATE_PAGE_GAME = "library.page.game";
    private static final String STATE_PAGE_WAY = "library.page.way";
    private static final String STATE_PAGE_PLAY = "library.page.play";
    private static final int SORT_RECENT = 0;
    private static final int SORT_NAME = 1;
    private static final int SORT_PLAYED = 2;

    // The fixed filters (the collections come after them)
    private static final int F_ALL = 0;
    private static final int F_PLAYING = 1;
    private static final int F_BACKLOG = 2;
    private static final int F_FINISHED = 3;
    private static final int F_PC = 4;
    private static final int F_ANDROID = 5;
    private static final int F_RETRO = 6;
    private static final int F_CLOUD = 7;
    private static final int FIXED = 8;

    public static void open(Activity from) {
        PocketSounds.play(from, PocketSounds.OPEN);
        from.startActivity(new Intent(from, LibraryActivity.class));
    }

    private float d;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<UniversalLibrary.Game> all = new ArrayList<>();
    private final List<UniversalLibrary.Game> shown = new ArrayList<>();
    private int filter = F_ALL;
    private int sort;
    private boolean refreshCollectionOnClose;
    private TextView optionsAction;
    private View detailsAction;
    private int heroPos = -1;
    private int heroToken;
    private int focusGeneration;
    private boolean loading = true;
    private String pageGame;
    private String pageWay;
    private boolean pagePlay;

    private ImageView heroArt;
    private boolean pureOled;
    private TextView kicker;
    private TextView title;
    private TextView meta;
    private TextView ways;
    private TextView empty;
    private LinearLayout detailActions;
    private LinearLayout tabs;
    private HorizontalScrollView tabScroll;
    private RecyclerView grid;
    private final Cards cards = new Cards();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pageGame = savedInstanceState.getString(STATE_PAGE_GAME);
            pageWay = savedInstanceState.getString(STATE_PAGE_WAY);
            pagePlay = savedInstanceState.getBoolean(STATE_PAGE_PLAY);
        }
        d = getResources().getDisplayMetrics().density;
        sort = Math.max(SORT_RECENT, Math.min(SORT_PLAYED, PocketPrefs.prefs(this).getInt(KEY_SORT, SORT_RECENT)));
        setContentView(build());
        setHints(hintA(this, R.string.pocket_hero_play),
                hintY(this, R.string.pocket_ul_hint_options),
                hintX(this, R.string.pocket_hint_details),
                new Hint("L1 R1", 0xFFFFFFFF, getString(R.string.pocket_ul_hint_show)),
                new Hint("L2 R2", 0xFFFFFFFF, getString(R.string.pocket_ul_hint_order)),
                hintB(this, R.string.pocket_hint_back));
        kicker.setText(R.string.pocket_ul_loading);
        load();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(STATE_PAGE_GAME, pageGame);
        state.putString(STATE_PAGE_WAY, pageWay);
        state.putBoolean(STATE_PAGE_PLAY, pagePlay);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        if (requestCode != GamePageActivity.REQUEST) return;
        pagePlay = pageGame != null && resultCode == RESULT_OK && result != null
                && GamePageActivity.ACTION_PLAY.equals(result.getStringExtra(GamePageActivity.RESULT_ACTION));
        if (!pagePlay) {
            pageGame = null;
            pageWay = null;
        }
        dispatchPagePlay();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) dispatchPagePlay();
    }

    @Override
    protected void onDestroy() {
        heroToken++;
        focusGeneration++;
        if (heroArt != null) heroArt.animate().cancel();
        if (title != null) title.animate().cancel();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected boolean allowsAutoResume() {
        return false;
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    private TextView text(float sp, android.graphics.Typeface face, int color) {
        TextView t = new TextView(this);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    // ---- The screen -----------------------------------------------------------------------------

    private View build() {
        FrameLayout root = new FrameLayout(this);
        pureOled = PocketTheme.pureOled(this);
        if (pureOled) root.setBackgroundColor(0xFF000000);
        else root.setBackground(ConsoleScreen.backdrop(this));
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        HorizonLibraryLayout spec = HorizonLibraryLayout.forWindow(w / d, h / d, pureOled);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setPadding(dp(spec.padding), dp(12), dp(spec.padding), dp(8));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(24f, PocketFonts.display(), 0xFFFFFFFF);
        heading.setText(R.string.horizon_library_workspace_title);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        kicker = text(10f, PocketFonts.bold(), 0xCCFFFFFF);
        kicker.setLetterSpacing(0.06f);
        header.addView(kicker, new LinearLayout.LayoutParams(-2, -2));
        column.addView(header, new LinearLayout.LayoutParams(-1, -2));

        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFocusable(false);
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabScroll.addView(tabs);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = dp(10);
        column.addView(tabScroll, sl);

        LinearLayout workspace = new LinearLayout(this);
        workspace.setOrientation(spec.split ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        workspace.setClipChildren(false);
        LinearLayout.LayoutParams workLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        workLp.topMargin = dp(8);
        column.addView(workspace, workLp);

        // Independent selected-game detail surface; artwork no longer covers the entire library.
        LinearLayout detail = new LinearLayout(this);
        detail.setId(R.id.horizon_library_detail);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(12), dp(12), dp(12), dp(10));
        GradientDrawable detailBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF0222935, 0xF0141820});
        if (pureOled) detailBg.setColor(0xFF000000);
        detailBg.setCornerRadius(dp(pureOled ? 4 : 14));
        detailBg.setStroke(dp(1), 0x33FFFFFF);
        detail.setBackground(detailBg);
        heroArt = new ImageView(this);
        heroArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (spec.artHeight > 0) {
            detail.addView(heroArt, new LinearLayout.LayoutParams(-1, dp(spec.artHeight)));
        }
        title = text(spec.split ? 22f : 17f, PocketFonts.bold(), 0xFFFFFFFF);
        title.setMaxLines(spec.split ? 2 : 1);
        if (spec.split) title.setSingleLine(false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = dp(spec.artHeight > 0 ? 10 : 0);
        detail.addView(title, tl);
        meta = text(12f, PocketFonts.medium(), 0xDEFFFFFF);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.topMargin = dp(5);
        detail.addView(meta, ml);
        ways = text(11f, PocketFonts.medium(), 0xB3FFFFFF);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.topMargin = dp(4);
        detail.addView(ways, wl);

        detailActions = new LinearLayout(this);
        detailActions.setVisibility(View.GONE);
        detailsAction = detailAction(R.string.pocket_hint_details, () -> onButtonX());
        detailsAction.setId(R.id.horizon_library_details_action);
        detailActions.addView(detailsAction, new LinearLayout.LayoutParams(0, dp(48), 1f));
        optionsAction = detailAction(R.string.pocket_ul_hint_options, this::openSelectedOptions);
        optionsAction.setOnKeyListener((view, code, event) -> {
            if (code != KeyEvent.KEYCODE_BUTTON_A && code != KeyEvent.KEYCODE_BUTTON_Y) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && !isConsolePanelShowing()) view.performClick();
            return true;
        });
        optionsAction.setId(R.id.horizon_library_options_action);
        LinearLayout.LayoutParams optionsLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        optionsLp.setMarginStart(dp(6));
        detailActions.addView(optionsAction, optionsLp);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, -2);
        actionLp.topMargin = dp(8);
        detail.addView(detailActions, actionLp);
        if (spec.split) {
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(spec.detailWidth), -1);
            dl.setMarginEnd(dp(12));
            workspace.addView(detail, dl);
        } else {
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
            dl.bottomMargin = dp(8);
            workspace.addView(detail, dl);
        }

        FrameLayout body = new FrameLayout(this);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        workspace.addView(body, spec.split
                ? new LinearLayout.LayoutParams(0, -1, 1f)
                : new LinearLayout.LayoutParams(-1, 0, 1f));
        grid = new RecyclerView(this);
        grid.setId(R.id.horizon_library_grid);
        grid.setLayoutManager(new GridLayoutManager(this, spec.columns));
        grid.setItemAnimator(null);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(dp(3), dp(4), dp(3), dp(6));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) grid.setDefaultFocusHighlightEnabled(false);
        grid.setAdapter(cards);
        body.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        empty = text(13f, PocketFonts.medium(), 0xCCFFFFFF);
        empty.setSingleLine(false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(12), dp(8), dp(12), dp(8));
        empty.setVisibility(View.GONE);
        body.addView(empty, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        LinearLayout hints = new LinearLayout(this);
        hints.setId(R.id.pocketHintBar);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        hints.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        HorizontalScrollView footer = new HorizontalScrollView(this);
        footer.setHorizontalScrollBarEnabled(false);
        footer.setFocusable(false);
        footer.addView(hints, new FrameLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = dp(6);
        column.addView(footer, hlp);
        return root;
    }

    private TextView detailAction(int label, Runnable action) {
        TextView button = text(12f, PocketFonts.semibold(), 0xFFFFFFFF);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setOnClickListener(view -> action.run());
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x24FFFFFF);
        background.setCornerRadius(dp(8));
        button.setBackground(background);
        FocusGlow.apply(button, dp(8), false, 1.02f, null);
        return button;
    }

    private void load() {
        final Context app = getApplicationContext();
        io.execute(() -> {
            final List<UniversalLibrary.Game> got = UniversalLibrary.gather(app);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                loading = false;
                all = got;
                show(true);
                dispatchPagePlay();
            });
        });
    }

    // ---- What's shown ---------------------------------------------------------------------------

    private int filterCount() {
        return FIXED + GameShelf.collections(this).size();
    }

    private String filterLabel(int f) {
        switch (f) {
            case F_ALL:
                return getString(R.string.pocket_ul_all);
            case F_PLAYING:
                return getString(R.string.pocket_ul_status_playing);
            case F_BACKLOG:
                return getString(R.string.pocket_ul_status_backlog);
            case F_FINISHED:
                return getString(R.string.pocket_ul_status_finished);
            case F_PC:
                return getString(R.string.pocket_ul_pc);
            case F_ANDROID:
                return getString(R.string.pocket_ul_android);
            case F_RETRO:
                return getString(R.string.pocket_ul_retro);
            case F_CLOUD:
                return getString(R.string.pocket_ul_cloud);
            default: {
                List<String> cols = GameShelf.collections(this);
                int i = f - FIXED;
                return i >= 0 && i < cols.size() ? cols.get(i) : "";
            }
        }
    }

    private boolean matches(UniversalLibrary.Game g, Set<String> inCollection) {
        switch (filter) {
            case F_ALL:
                return true;
            case F_PLAYING:
                return GameShelf.status(this, g.key) == GameShelf.PLAYING;
            case F_BACKLOG:
                return GameShelf.status(this, g.key) == GameShelf.BACKLOG;
            case F_FINISHED:
                return GameShelf.status(this, g.key) == GameShelf.FINISHED;
            case F_PC:
                return g.on(UniversalLibrary.PLATFORM_PC);
            case F_ANDROID:
                return g.on(UniversalLibrary.PLATFORM_ANDROID);
            case F_RETRO:
                return g.on(UniversalLibrary.PLATFORM_RETRO);
            case F_CLOUD:
                return g.on(UniversalLibrary.PLATFORM_CLOUD);
            default:
                return inCollection != null && inCollection.contains(g.key);
        }
    }

    private void show(boolean first) {
        focusGeneration++;
        heroToken++;
        heroArt.animate().cancel();
        heroArt.setAlpha(1f);
        heroArt.setScaleX(1f);
        heroArt.setScaleY(1f);
        heroArt.setImageDrawable(null);
        title.animate().cancel();
        title.setAlpha(1f);
        title.setTranslationX(0f);
        if (filter >= filterCount()) {
            filter = F_ALL;
        }
        Set<String> in = filter >= FIXED ? GameShelf.gamesIn(this, filterLabel(filter)) : null;
        String keep = heroPos >= 0 && heroPos < shown.size() ? shown.get(heroPos).key : null;
        shown.clear();
        for (UniversalLibrary.Game g : all) {
            if (matches(g, in)) {
                shown.add(g);
            }
        }
        final java.text.Collator byName = java.text.Collator.getInstance();
        byName.setStrength(java.text.Collator.PRIMARY);
        Collections.sort(shown, (a, b) -> {
            if (sort == SORT_PLAYED && a.seconds != b.seconds) {
                return Long.compare(b.seconds, a.seconds);
            }
            if (sort == SORT_RECENT && a.last != b.last) {
                return Long.compare(b.last, a.last);
            }
            return byName.compare(a.title, b.title);
        });
        buildTabs();
        cards.notifyDataSetChanged();
        kicker.setText(getString(R.string.horizon_library_workspace_count, shown.size(), all.size(), getString(sortLabel(sort))));
        boolean canManageEmptyCollection = shown.isEmpty() && filter >= FIXED;
        detailActions.setVisibility(shown.isEmpty() && !canManageEmptyCollection ? View.GONE : View.VISIBLE);
        detailsAction.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        optionsAction.setText(canManageEmptyCollection ? R.string.horizon_library_collection_options : R.string.pocket_ul_hint_options);
        optionsAction.setFocusableInTouchMode(canManageEmptyCollection);
        if (shown.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            empty.setText(all.isEmpty() ? R.string.pocket_ul_empty_all : R.string.pocket_ul_empty);
            heroPos = -1;
            title.setText(filterLabel(filter));
            meta.setText("");
            ways.setText("");
            heroArt.setImageDrawable(null);
            if (canManageEmptyCollection) optionsAction.requestFocus();
            return;
        }
        empty.setVisibility(View.GONE);
        int target = 0;
        for (int i = 0; keep != null && i < shown.size(); i++) {
            if (shown.get(i).key.equals(keep)) {
                target = i;
            }
        }
        heroPos = -1;
        updateHero(target);
        focusAt(target, 0);
        if (first && PocketPrefs.animationsEnabled(this)) {
            grid.setAlpha(0f);
            grid.setTranslationY(dp(24));
            grid.animate().alpha(1f).translationY(0).setDuration(380)
                    .setInterpolator(PocketMotion.DECELERATE).start();
        }
    }

    private void buildTabs() {
        tabs.removeAllViews();
        int n = filterCount();
        View current = null;
        for (int f = 0; f < n; f++) {
            TextView t = text(12f, PocketFonts.semibold(), f == filter ? 0xFF111114 : 0xB3FFFFFF);
            t.setText(filterLabel(f));
            final int target = f;
            t.setClickable(true);
            t.setFocusable(false); // shoulder controls remain the controller filter path
            t.setOnClickListener(v -> {
                if (loading || target == filter) return;
                filter = target;
                heroPos = -1;
                PocketSounds.play(this, PocketSounds.NAV);
                show(false);
            });
            t.setContentDescription(filterLabel(f));
            t.setSelected(f == filter);
            t.setPadding(dp(14), dp(7), dp(14), dp(7));
            if (f == filter) {
                GradientDrawable pill = new GradientDrawable();
                pill.setCornerRadius(dp(20));
                pill.setColor(0xFFFFFFFF);
                t.setBackground(pill);
                current = t;
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(6));
            tabs.addView(t, lp);
            if (f == FIXED - 1 && n > FIXED) {
                View bar = new View(this);
                bar.setBackgroundColor(0x33FFFFFF);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(1), dp(18));
                bp.gravity = Gravity.CENTER_VERTICAL;
                bp.setMarginEnd(dp(10));
                bp.setMarginStart(dp(4));
                tabs.addView(bar, bp);
            }
        }
        final View on = current;
        if (on != null) {
            tabScroll.post(() -> {
                int x = Math.max(0, on.getLeft() - dp(40));
                if (PocketPrefs.animationsEnabled(this)) tabScroll.smoothScrollTo(x, 0);
                else tabScroll.scrollTo(x, 0);
            });
        }
    }

    private static int sortLabel(int sort) {
        switch (sort) {
            case SORT_NAME:
                return R.string.pocket_ul_sort_name;
            case SORT_PLAYED:
                return R.string.pocket_ul_sort_played;
            default:
                return R.string.pocket_ul_sort_recent;
        }
    }

    private void focusAt(final int position, final int attempt) {
        focusAt(position, attempt, ++focusGeneration);
    }

    private void focusAt(final int position, final int attempt, final int generation) {
        if (generation != focusGeneration || loading || isFinishing() || isDestroyed()) return;
        if (attempt == 0) {
            grid.scrollToPosition(position);
        }
        grid.post(() -> {
            if (generation != focusGeneration || isFinishing() || isDestroyed() || position < 0 || position >= shown.size()) {
                return;
            }
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
            if (holder instanceof Cards.Holder && ((Cards.Holder) holder).card.requestFocus()) {
                return;
            }
            if (attempt < 6) {
                grid.postDelayed(() -> focusAt(position, attempt + 1, generation), attempt < 2 ? 16 : 120);
            }
            if (heroPos != position) {
                updateHero(position);
            }
        });
    }

    // ---- The top shelf --------------------------------------------------------------------------

    private void updateHero(int position) {
        if (position < 0 || position >= shown.size()) {
            return;
        }
        final UniversalLibrary.Game g = shown.get(position);
        boolean moved = position != heroPos;
        heroPos = position;
        if (moved) {
            title.animate().cancel();
            title.setAlpha(1f);
            title.setTranslationX(0f);
            title.setText(g.title);
            heroArt.animate().cancel();
            heroArt.setAlpha(1f);
            heroArt.setScaleX(1f);
            heroArt.setScaleY(1f);
            heroArt.setImageDrawable(null);
        }
        StringBuilder m = new StringBuilder();
        int status = GameShelf.status(this, g.key);
        if (status != GameShelf.NONE) {
            m.append(getString(GameShelf.statusLabel(status)));
        }
        String played = g.seconds >= 60 ? PlayTime.format(this, g.seconds) : null;
        if (played != null) {
            m.append(m.length() > 0 ? "  ·  " : "").append(getString(R.string.pocket_ul_played, played));
        }
        if (g.last > 0) {
            m.append(m.length() > 0 ? "  ·  " : "").append(android.text.format.DateUtils.getRelativeTimeSpanString(
                    g.last, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS));
        }
        if (m.length() == 0) {
            m.append(getString(R.string.pocket_ul_not_played));
        }
        meta.setText(m);
        StringBuilder w = new StringBuilder();
        for (UniversalLibrary.Way way : g.ways) {
            if (w.indexOf(way.label) >= 0) {
                continue;
            }
            w.append(w.length() > 0 ? "  ·  " : "").append(way.label);
        }
        ways.setText(g.ways.size() > 1 ? getString(R.string.pocket_ul_plays_on_many, w) : w);
        if (!moved || heroArt.getParent() == null) {
            return;
        }
        // Its wide art (else its cover, stretched dim), a moment after the focus rests
        final int token = ++heroToken;
        final UniversalLibrary.Way best = g.best();
        final String uuid = HomeActivity.artUuid(best.item);
        final int id = HomeActivity.artId(best.item);
        main.postDelayed(() -> {
            if (token != heroToken || isFinishing() || uuid == null) {
                return;
            }
            Bitmap cached = PocketArt.cached(this, uuid, id, PocketArt.HERO);
            if (cached != null) {
                setHero(cached, token);
                return;
            }
            PocketArt.load(this, uuid, id, PocketArt.HERO, b -> {
                if (token != heroToken || isFinishing()) {
                    return;
                }
                if (b != null) {
                    setHero(b, token);
                    return;
                }
                PocketArt.load(this, uuid, id, PocketArt.COVER, c -> {
                    if (token == heroToken && !isFinishing()) {
                        setHero(c, token);
                    }
                });
            });
        }, heroPos < 0 ? 0 : 140);
    }

    private void setHero(Bitmap b, int token) {
        if (token != heroToken || isFinishing() || isDestroyed()) return;
        if (!PocketPrefs.animationsEnabled(this)) {
            heroArt.setImageBitmap(b);
            return;
        }
        heroArt.animate().cancel();
        heroArt.animate().alpha(0.35f).setDuration(110).withEndAction(() -> {
            if (token != heroToken || isFinishing() || isDestroyed()) return;
            heroArt.setImageBitmap(b);
            heroArt.setScaleX(1.04f);
            heroArt.setScaleY(1.04f);
            heroArt.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420)
                    .setInterpolator(PocketMotion.DECELERATE).start();
        }).start();
    }

    // ---- Buttons --------------------------------------------------------------------------------

    @Override
    protected boolean onShoulder(int direction) {
        if (loading) return true;
        int n = filterCount();
        filter = (filter + direction + n) % n;
        PocketSounds.play(this, PocketSounds.NAV);
        heroPos = -1;
        show(false);
        return true;
    }

    @Override
    protected boolean onTrigger(int direction) {
        if (loading) return true;
        sort = (sort + direction + 3) % 3;
        PocketPrefs.prefs(this).edit().putInt(KEY_SORT, sort).apply();
        PocketSounds.play(this, PocketSounds.NAV);
        Toast.makeText(this, getString(R.string.pocket_ul_sorted, getString(sortLabel(sort))), Toast.LENGTH_SHORT).show();
        show(false);
        return true;
    }

    @Override
    protected boolean onQuickPlay() {
        UniversalLibrary.Game g = heroPos >= 0 && heroPos < shown.size() ? shown.get(heroPos) : null;
        if (g == null) {
            return false;
        }
        playGame(g);
        return true;
    }

    @Override
    protected boolean onButtonX() {
        UniversalLibrary.Game g = heroPos >= 0 && heroPos < shown.size() ? shown.get(heroPos) : null;
        if (g != null) {
            page(g);
        }
        return true;
    }

    @Override
    protected void onSelectButton() {
        // One picked for you: from the backlog when there is one, else from what's shown
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            if (GameShelf.status(this, shown.get(i).key) == GameShelf.BACKLOG) {
                pool.add(i);
            }
        }
        if (pool.isEmpty()) {
            for (int i = 0; i < shown.size(); i++) {
                pool.add(i);
            }
        }
        if (pool.isEmpty()) {
            return;
        }
        PocketSounds.play(this, PocketSounds.SELECT);
        int pick = pool.get(new Random().nextInt(pool.size()));
        Toast.makeText(this, getString(R.string.pocket_ul_picked, shown.get(pick).title), Toast.LENGTH_SHORT).show();
        focusAt(pick, 0);
    }

    private void playGame(UniversalLibrary.Game game) {
        if (game.requiresPcChoice() || game.requiresRetroChoice()) options(game);
        else play(game.best());
    }

    private void play(UniversalLibrary.Way way) {
        if (way == null) {
            return;
        }
        PocketSounds.play(this, PocketSounds.SELECT);
        HomeItem item = way.item;
        if (HomeItem.PC.equals(item.kind) || HomeItem.PC_LIBRARY_GAME.equals(item.kind)) {
            // Home starts streamed games (it knows the PC's state, wakes and pairs it)
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(HomeActivity.EXTRA_ACTION, HomeActivity.ACTION_LAUNCH)
                    .putExtra(HomeActivity.EXTRA_ITEM, item.toLine()));
            return;
        }
        HomeLauncher.launch(this, item);
    }

    private void page(UniversalLibrary.Game g) {
        UniversalLibrary.Way best = g.best();
        String uuid = HomeActivity.artUuid(best.item);
        if (uuid == null) {
            return;
        }
        pageGame = g.key;
        pageWay = best.item.key();
        pagePlay = false;
        PocketSounds.play(this, PocketSounds.OPEN);
        HomeItem retro = HomeItem.RETRO.equals(best.item.kind) && !g.requiresRetroChoice() ? best.item : null;
        GamePageActivity.open(this, null, uuid, HomeActivity.artId(best.item), g.title, best.label,
                false, false, null, retro);
    }

    private void dispatchPagePlay() {
        if (!pagePlay || loading || !hasWindowFocus() || isFinishing() || isDestroyed()) return;
        String gameKey = pageGame;
        String wayKey = pageWay;
        pageGame = null;
        pageWay = null;
        pagePlay = false;
        for (UniversalLibrary.Game game : all) {
            if (!game.key.equals(gameKey)) continue;
            if (game.requiresPcChoice() || game.requiresRetroChoice()) {
                int visible = shown.indexOf(game);
                if (visible >= 0 && visible != heroPos) updateHero(visible);
                options(game);
                return;
            }
            for (UniversalLibrary.Way way : game.ways) {
                if (way.item.key().equals(wayKey)) {
                    play(way);
                    return;
                }
            }
            break;
        }
        Toast.makeText(this, R.string.pocket_home_missing, Toast.LENGTH_LONG).show();
    }

    /** Y: every way to play it, its status, its collections, its page. */
    private void openSelectedOptions() {
        if (heroPos >= 0 && heroPos < shown.size()) {
            options(shown.get(heroPos));
        } else if (!loading && filter >= FIXED) {
            String current = filterLabel(filter);
            showConsolePanel(current, null, null, Collections.singletonList(deleteCollectionItem(current)));
        }
    }

    private void options(final UniversalLibrary.Game g) {
        List<StreamMenuView.Item> items = new ArrayList<>();
        items.add(StreamMenuView.Item.header(getString(R.string.pocket_ul_play_on)));
        boolean choosePc = g.requiresPcChoice();
        boolean chooseRom = g.requiresRetroChoice();
        if (choosePc) items.add(StreamMenuView.Item.note(getString(R.string.horizon_library_pc_choice)));
        if (chooseRom) items.add(StreamMenuView.Item.note(getString(R.string.horizon_library_rom_choice)));
        UniversalLibrary.Way best = g.best();
        for (final UniversalLibrary.Way w : g.ways) {
            String label = choosePc && HomeItem.PC.equals(w.item.kind)
                    ? getString(R.string.horizon_library_pc_app, w.label, w.item.n) : w.label;
            if (chooseRom && HomeItem.RETRO.equals(w.item.kind)) {
                items.add(StreamMenuView.Item.note(getString(R.string.horizon_library_rom_path, w.item.c, w.item.b)));
            }
            items.add(StreamMenuView.Item.action((!choosePc && !chooseRom && w == best ? "▶  " : "") + label, () -> {
                hideConsolePanel();
                play(w);
            }).withIcon(w.platform == UniversalLibrary.PLATFORM_PC ? "DESKTOP"
                    : w.platform == UniversalLibrary.PLATFORM_CLOUD ? "GLOBE"
                    : w.platform == UniversalLibrary.PLATFORM_RETRO ? "GAMEPAD" : "APPS"));
        }
        items.add(StreamMenuView.Item.header(getString(R.string.pocket_ul_yours)));
        items.add(StreamMenuView.Item.value(getString(R.string.pocket_ul_status),
                getString(GameShelf.statusLabel(GameShelf.status(this, g.key))), direction -> {
                    int s = (GameShelf.status(this, g.key) + direction + GameShelf.STATUS_COUNT) % GameShelf.STATUS_COUNT;
                    GameShelf.setStatus(this, g.key, s);
                    PocketSounds.play(this, PocketSounds.NAV);
                    cards.notifyItemChanged(heroPos);
                    int keep = heroPos;
                    heroPos = -2;
                    updateHero(keep);
                    return getString(GameShelf.statusLabel(s));
                }).withIcon("STAR"));
        items.add(StreamMenuView.Item.submenu(getString(R.string.pocket_ul_collections), () -> collections(g))
                .withIcon("LIST"));
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_hint_details), () -> {
            hideConsolePanel();
            page(g);
        }).withIcon("INFO"));
        PocketSounds.play(this, PocketSounds.OPEN);
        showConsolePanel(g.title, ways.getText().toString(), null, items);
    }

    private void collections(final UniversalLibrary.Game g) {
        List<StreamMenuView.Item> items = new ArrayList<>();
        for (final String c : GameShelf.collections(this)) {
            items.add(StreamMenuView.Item.value(c, GameShelf.isIn(this, c, g.key) ? "✓" : "", direction -> {
                boolean now = GameShelf.toggle(this, c, g.key);
                if (filter >= FIXED && c.equals(filterLabel(filter))) refreshCollectionOnClose = true;
                PocketSounds.play(this, now ? PocketSounds.TOGGLE_ON : PocketSounds.TOGGLE_OFF);
                return now ? "✓" : "";
            }));
        }
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_ul_new_collection), () -> {
            hideConsolePanel();
            TextPrompt.show(this, getString(R.string.pocket_ul_new_collection), getString(R.string.pocket_ul_new_collection_help),
                    "", getString(R.string.pocket_ul_collection_hint), name -> {
                        String activeCollection = filter >= FIXED ? filterLabel(filter) : null;
                        String added = GameShelf.addToCollection(this, name, g.key);
                        if (added == null) return;
                        if (activeCollection != null) {
                            int index = GameShelf.collections(this).indexOf(activeCollection);
                            filter = index < 0 ? F_ALL : FIXED + index;
                        }
                        PocketSounds.play(this, PocketSounds.TOGGLE_ON);
                        Toast.makeText(this, getString(R.string.pocket_ul_added_to, g.title, added),
                                Toast.LENGTH_SHORT).show();
                        show(false);
                    });
        }).withIcon("STAR"));
        if (filter >= FIXED) items.add(deleteCollectionItem(filterLabel(filter)));
        pushConsolePage(getString(R.string.pocket_ul_collections), items);
    }

    private StreamMenuView.Item deleteCollectionItem(String name) {
        return StreamMenuView.Item.confirm(getString(R.string.pocket_ul_delete_collection, name), () -> {
            GameShelf.deleteCollection(this, name);
            hideConsolePanel();
            filter = F_ALL;
            show(false);
        }).withIcon("TRASH");
    }

    @Override
    protected void onConsolePanelClosed() {
        if (refreshCollectionOnClose) {
            refreshCollectionOnClose = false;
            show(false);
        }
    }

    // ---- The covers -----------------------------------------------------------------------------

    /** A cover's shape: as wide as its cell, a box's height (2:3 and a little). */
    private static final class CoverFrame extends FrameLayout {
        private final int fixedHeight;

        CoverFrame(Context c, int fixedHeight) {
            super(c);
            this.fixedHeight = fixedHeight;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec);
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(fixedHeight > 0 ? fixedHeight : Math.round(w * 1.42f),
                            MeasureSpec.EXACTLY));
        }
    }

    private final class Cards extends RecyclerView.Adapter<Cards.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final FrameLayout card;
            final ImageView cover;
            final TextView name;
            final TextView badge;
            final View dot;
            String key;

            Holder(FrameLayout frame, FrameLayout card, ImageView cover, TextView name, TextView badge, View dot) {
                super(frame);
                this.card = card;
                this.cover = cover;
                this.name = name;
                this.badge = badge;
                this.dot = dot;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(LibraryActivity.this);
            frame.setClipChildren(false);
            frame.setPadding(dp(7), dp(7), dp(7), dp(7));
            final FrameLayout card = new CoverFrame(LibraryActivity.this, pureOled ? dp(76) : 0);
            final float radius = dp(pureOled ? 4 : 12);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2A2A33, 0xFF15151A});
            bg.setCornerRadius(radius);
            if (pureOled) {
                bg.setColor(0xFF000000);
                bg.setStroke(Math.max(1, dp(1)), 0x55FFFFFF);
            }
            card.setBackground(bg);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);
            card.setFocusableInTouchMode(true);
            card.setClickable(true);
            ImageView cover = new ImageView(LibraryActivity.this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            TextView name = text(12.5f, PocketFonts.bold(), 0xFFFFFFFF);
            name.setSingleLine(false);
            name.setMaxLines(pureOled ? 2 : 4);
            name.setGravity(pureOled ? Gravity.START : Gravity.CENTER);
            name.setPadding(dp(pureOled ? 12 : 8), 0, dp(pureOled ? 16 : 8), 0);
            FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(-1, -2,
                    pureOled ? Gravity.TOP : Gravity.CENTER);
            if (pureOled) nameLp.topMargin = dp(10);
            card.addView(name, nameLp);
            View shade = new View(LibraryActivity.this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0x00000000, 0xB3000000}));
            if (!pureOled) card.addView(shade, new FrameLayout.LayoutParams(-1, -1));
            TextView badge = text(8.5f, PocketFonts.bold(), 0xFFFFFFFF);
            badge.setLetterSpacing(0.06f);
            badge.setPadding(dp(6), dp(2), dp(6), dp(2));
            GradientDrawable bb = new GradientDrawable();
            bb.setCornerRadius(dp(6));
            bb.setColor(0x80000000);
            badge.setBackground(bb);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
            blp.setMargins(dp(6), 0, dp(6), dp(6));
            card.addView(badge, blp);
            View dot = new View(LibraryActivity.this);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.TOP | Gravity.END);
            dlp.setMargins(0, dp(7), dp(7), 0);
            card.addView(dot, dlp);
            frame.addView(card, new FrameLayout.LayoutParams(-1, -2));
            final Holder h = new Holder(frame, card, cover, name, badge, dot);
            FocusGlow.apply(card, radius, false, pureOled ? 1f : 1.08f, (v, has) -> {
                if (has) {
                    int p = h.getBindingAdapterPosition();
                    if (p != RecyclerView.NO_POSITION) {
                        updateHero(p);
                    }
                }
            });
            card.setOnClickListener(v -> {
                int p = h.getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION && p < shown.size()) {
                    FocusGlow.press(card);
                    playGame(shown.get(p));
                }
            });
            card.setOnKeyListener((v, code, e) -> {
                int p = h.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION || p >= shown.size() || isConsolePanelShowing()) {
                    return false;
                }
                boolean confirm = code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_DPAD_CENTER
                        || code == KeyEvent.KEYCODE_ENTER;
                if (confirm || code == KeyEvent.KEYCODE_BUTTON_Y) {
                    if (e.getAction() == KeyEvent.ACTION_UP) {
                        if (confirm) {
                            FocusGlow.press(card);
                            playGame(shown.get(p));
                        }
                        else {
                            options(shown.get(p));
                        }
                    }
                    return true;
                }
                return false;
            });
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            UniversalLibrary.Game g = shown.get(position);
            h.key = g.key;
            h.name.setText(g.title);
            h.card.setContentDescription(g.title);
            h.name.setVisibility(View.VISIBLE);
            h.cover.setImageDrawable(null);
            StringBuilder b = new StringBuilder();
            for (UniversalLibrary.Way w : g.ways) {
                if (b.indexOf(w.badge) < 0) {
                    b.append(b.length() > 0 ? " · " : "").append(w.badge);
                }
            }
            h.badge.setText(b);
            int status = GameShelf.status(LibraryActivity.this, g.key);
            if (status != GameShelf.NONE) {
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(GameShelf.statusColor(status));
                dot.setStroke(Math.max(1, dp(1.5f)), 0xCC000000);
                h.dot.setBackground(dot);
                h.dot.setVisibility(View.VISIBLE);
            }
            else {
                h.dot.setVisibility(View.GONE);
            }
            if (pureOled) return;
            UniversalLibrary.Way best = g.best();
            final String uuid = HomeActivity.artUuid(best.item);
            final int id = HomeActivity.artId(best.item);
            if (uuid == null) {
                return;
            }
            final String key = g.key;
            Bitmap cached = PocketArt.cached(LibraryActivity.this, uuid, id, PocketArt.COVER);
            if (cached != null) {
                h.cover.setImageBitmap(cached);
                h.name.setVisibility(View.GONE);
                return;
            }
            PocketArt.load(LibraryActivity.this, uuid, id, PocketArt.COVER, bitmap -> {
                if (bitmap != null && key.equals(h.key)) {
                    h.cover.setImageBitmap(bitmap);
                    h.name.setVisibility(View.GONE);
                }
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
