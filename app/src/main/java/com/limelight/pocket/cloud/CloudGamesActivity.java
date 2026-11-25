package com.limelight.pocket.cloud;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.RenderEffect;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketArt;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.home.HomeActivity;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.home.HomeStore;
import com.limelight.pocket.ui.ConsoleScreen;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.RemoteImages;
import com.limelight.pocket.ui.Spring;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Xbox Cloud Gaming's games, as covers: the chosen one's art fills the back, its name, publisher
 * and a few lines about it on top. A plays it straight away (in the client picked for Xbox
 * Cloud Gaming), Y has its options (play with another client, put it on Home), X searches,
 * L1 / R1 page through, B goes back.
 */
public class CloudGamesActivity extends Activity {

    public static void open(Context c) {
        try {
            c.startActivity(new Intent(c, CloudGamesActivity.class));
        } catch (RuntimeException ignored) {
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private float d;
    private final List<XcloudCatalog.Game> all = new ArrayList<>();
    private final List<XcloudCatalog.Game> shown = new ArrayList<>();
    private String filter = "";
    private RecyclerView grid;
    private Tiles tiles;
    private ImageView backA;
    private ImageView backB;
    private boolean backOnA = true;
    private TextView title;
    private TextView sub;
    private TextView about;
    private TextView status;
    private LinearLayout hints;
    private int focused = -1;
    private StreamMenuView menu;
    private final Runnable showFocusedArt = this::loadBackdrop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConsoleActivity.applyFullscreenWindow(this);
        d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF050507);
        setContentView(root);
        backA = backdrop();
        backB = backdrop();
        root.addView(backA, full());
        root.addView(backB, full());
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99050507, 0xD9050507, 0xF7050507}));
        root.addView(shade, full());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(40), dp(24), dp(40), 0);
        root.addView(column, full());

        TextView eyebrow = new TextView(this);
        eyebrow.setText(R.string.pocket_cloud_games_eyebrow);
        eyebrow.setTypeface(PocketFonts.bold());
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        eyebrow.setTextColor(0xFF7CD957);
        column.addView(eyebrow);
        title = new TextView(this);
        title.setTypeface(PocketFonts.display());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTextColor(0xFFFFFFFF);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(6);
        column.addView(title, tlp);
        sub = new TextView(this);
        sub.setTypeface(PocketFonts.semibold());
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sub.setTextColor(0xB3FFFFFF);
        column.addView(sub);
        about = new TextView(this);
        about.setTypeface(PocketFonts.regular());
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        about.setTextColor(0xA6FFFFFF);
        about.setMaxLines(2);
        about.setEllipsize(TextUtils.TruncateAt.END);
        about.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(8);
        column.addView(about, alp);

        FrameLayout body = new FrameLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        blp.topMargin = dp(12);
        column.addView(body, blp);
        grid = new RecyclerView(this);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(0, dp(8), 0, dp(70));
        grid.setItemAnimator(null);
        int width = getResources().getDisplayMetrics().widthPixels - dp(80);
        grid.setLayoutManager(new GridLayoutManager(this, Math.max(4, width / dp(124))));
        tiles = new Tiles();
        grid.setAdapter(tiles);
        body.addView(grid, full());
        status = new TextView(this);
        status.setTypeface(PocketFonts.medium());
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        status.setTextColor(0xCCFFFFFF);
        status.setGravity(Gravity.CENTER);
        body.addView(status, full());

        hints = new LinearLayout(this);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        hlp.rightMargin = dp(32);
        hlp.bottomMargin = dp(18);
        root.addView(hints, hlp);
        ConsoleActivity.fillHintBar(this, hints,
                ConsoleActivity.hintA(this, R.string.pocket_hero_play),
                ConsoleActivity.hintX(this, R.string.pocket_cloud_games_search),
                ConsoleActivity.hintY(this, R.string.pocket_hint_options),
                ConsoleActivity.hintB(this, R.string.pocket_hint_back));

        menu = new StreamMenuView(this);
        root.addView(menu, full());

        title.setText(R.string.pocket_cloud_games_title);
        status.setText(R.string.pocket_cloud_games_loading);
        load(false);
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    private static FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private ImageView backdrop() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        if (Build.VERSION.SDK_INT >= 31) {
            v.setRenderEffect(RenderEffect.createBlurEffect(6 * d, 6 * d, Shader.TileMode.CLAMP));
        }
        return v;
    }

    private void load(final boolean fresh) {
        final Context app = getApplicationContext();
        new Thread(() -> {
            List<XcloudCatalog.Game> games = null;
            try {
                games = XcloudCatalog.load(app, fresh);
            } catch (Exception ignored) {
            }
            final List<XcloudCatalog.Game> got = games;
            main.post(() -> {
                if (isFinishing()) {
                    return;
                }
                if (got == null || got.isEmpty()) {
                    status.setText(R.string.pocket_cloud_games_offline);
                    return;
                }
                all.clear();
                all.addAll(got);
                applyFilter();
            });
        }, "pocket-xcloud-catalog").start();
    }

    private void applyFilter() {
        shown.clear();
        String f = filter.toLowerCase(Locale.ROOT);
        for (XcloudCatalog.Game g : all) {
            if (f.isEmpty() || g.title.toLowerCase(Locale.ROOT).contains(f)) {
                shown.add(g);
            }
        }
        tiles.notifyDataSetChanged();
        status.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (shown.isEmpty()) {
            status.setText(getString(R.string.pocket_cloud_games_none, filter));
        }
        focused = -1;
        grid.scrollToPosition(0);
        focusAt(0, 0);
    }

    private void focusAt(final int position, final int attempt) {
        grid.post(() -> {
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(position);
            if (h != null) {
                h.itemView.requestFocus();
            }
            else if (attempt < 6) {
                main.postDelayed(() -> focusAt(position, attempt + 1), 40);
            }
        });
    }

    private void onFocused(int position) {
        if (position == focused || position < 0 || position >= shown.size()) {
            return;
        }
        focused = position;
        XcloudCatalog.Game g = shown.get(position);
        title.setText(g.title);
        sub.setText(g.publisher != null ? g.publisher : "");
        about.setText(g.about != null ? g.about.replace('\n', ' ') : "");
        title.setAlpha(0.4f);
        title.setTranslationX(dp(8));
        title.animate().alpha(1f).translationX(0).setDuration(220).start();
        // The art behind follows once you rest on it
        main.removeCallbacks(showFocusedArt);
        main.postDelayed(showFocusedArt, 260);
    }

    private void loadBackdrop() {
        if (focused < 0 || focused >= shown.size()) {
            return;
        }
        final XcloudCatalog.Game g = shown.get(focused);
        String url = g.hero != null ? g.hero : g.box;
        if (url == null) {
            return;
        }
        RemoteImages.load(this, XcloudCatalog.sized(url, 1280), 1280, b -> {
            if (b == null || isFinishing() || focused < 0 || focused >= shown.size() || shown.get(focused) != g) {
                return;
            }
            ImageView in = backOnA ? backB : backA;
            ImageView out = backOnA ? backA : backB;
            backOnA = !backOnA;
            in.setImageBitmap(b);
            in.animate().cancel();
            in.animate().alpha(0.7f).setDuration(600).start();
            out.animate().cancel();
            out.animate().alpha(0f).setDuration(600).start();
        });
    }

    // ---- Playing and pinning --------------------------------------------------------------------

    private void play(XcloudCatalog.Game g, CloudClients.Client with) {
        HorizonCloudDispatch.Result result = with != null
                ? CloudClients.play(this, CloudService.XCLOUD, with, g.launchUrl())
                : CloudClients.play(this, CloudService.XCLOUD, g.launchUrl());
        if (result.dispatched()) {
            PocketSounds.play(this, PocketSounds.SELECT);
            if (result.dispatchedRequestedTarget(true)) HomeStore.record(this, item(g));
        }
    }

    static HomeItem item(XcloudCatalog.Game g) {
        return new HomeItem(HomeItem.CLOUD, g.title, CloudService.XCLOUD.id, g.launchUrl(), g.id, 0);
    }

    /** On Home, with its own cover, backdrop and logo. */
    private void pin(final XcloudCatalog.Game g) {
        final HomeItem item = item(g);
        HomeStore.pin(this, item);
        PocketSounds.play(this, PocketSounds.TOGGLE_ON);
        Toast.makeText(this, getString(R.string.pocket_home_pinned_toast, g.title), Toast.LENGTH_SHORT).show();
        final Context app = getApplicationContext();
        new Thread(() -> saveArt(app, item, g), "pocket-xcloud-art").start();
    }

    static void saveArt(Context c, HomeItem item, XcloudCatalog.Game g) {
        String uuid = HomeActivity.artUuid(item);
        int id = HomeActivity.artId(item);
        String[][] parts = {{g.poster, String.valueOf(PocketArt.COVER), "600"}, {g.hero, String.valueOf(PocketArt.HERO), "1920"},
                {g.logo, String.valueOf(PocketArt.LOGO), "800"}, {g.box, String.valueOf(PocketArt.SQUARE), "512"}};
        for (String[] p : parts) {
            if (p[0] == null) {
                continue;
            }
            int kind = Integer.parseInt(p[1]);
            if (PocketArt.has(c, uuid, id, kind)) {
                continue;
            }
            try {
                PocketArt.save(c, uuid, id, kind, XcloudCatalog.bytes(XcloudCatalog.sized(p[0], Integer.parseInt(p[2]))));
            } catch (Exception ignored) {
            }
        }
    }

    private void options(final XcloudCatalog.Game g) {
        List<StreamMenuView.Item> items = new ArrayList<>();
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_hero_play), () -> {
            menu.hide();
            play(g, null);
        }).withIcon("PLAY"));
        for (final CloudClients.Client cl : CloudClients.available(this, CloudService.XCLOUD)) {
            items.add(StreamMenuView.Item.action(cl.label, () -> {
                menu.hide();
                play(g, cl);
            }).withIcon("GAMEPAD"));
        }
        boolean pinned = HomeStore.isPinned(this, item(g));
        items.add(StreamMenuView.Item.action(getString(pinned ? R.string.pocket_home_unpin : R.string.pocket_home_pin), () -> {
            menu.hide();
            if (pinned) {
                HomeStore.unpin(this, item(g));
                PocketSounds.play(this, PocketSounds.TOGGLE_OFF);
            }
            else {
                pin(g);
            }
        }).withIcon("STAR"));
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_cloud_games_refresh), () -> {
            menu.hide();
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.pocket_cloud_games_loading);
            load(true);
        }).withIcon("RESTART"));
        PocketSounds.play(this, PocketSounds.OPEN);
        menu.show(g.title, g.publisher, null, items);
    }

    private void search() {
        ConsoleScreen.prompt(this, getString(R.string.pocket_cloud_games_search), filter,
                getString(R.string.pocket_cloud_games_search_hint), text -> {
                    filter = text != null ? text : "";
                    applyFilter();
                });
    }

    // ---- Buttons --------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (menu.isShowing()) {
            if (menu.handleKey(event)) {
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B || event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    menu.hide();
                }
                return true;
            }
            return true;
        }
        boolean first = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
        XcloudCatalog.Game g = focused >= 0 && focused < shown.size() ? shown.get(focused) : null;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (first && g != null) {
                    play(g, null);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (first && g != null) {
                    options(g);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (first) {
                    search();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (event.getAction() == KeyEvent.ACTION_DOWN && !shown.isEmpty()) {
                    int cols = ((GridLayoutManager) grid.getLayoutManager()).getSpanCount();
                    int to = Math.max(0, Math.min(shown.size() - 1, Math.max(0, focused)
                            + (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_R1 ? 3 : -3) * cols));
                    grid.scrollToPosition(to);
                    focusAt(to, 0);
                    PocketSounds.play(this, PocketSounds.NAV);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (first) {
                    if (!filter.isEmpty()) {
                        filter = "";
                        applyFilter();
                    }
                    else {
                        PocketSounds.play(this, PocketSounds.BACK);
                        finish();
                    }
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConsoleActivity.applyFullscreenWindow(this);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---- Covers ---------------------------------------------------------------------------------

    private final class Tiles extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout cell = new FrameLayout(CloudGamesActivity.this);
            cell.setFocusable(true);
            cell.setClipChildren(false);
            cell.setPadding(dp(6), dp(8), dp(6), dp(8));
            ImageView art = new ImageView(CloudGamesActivity.this);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
                }
            });
            art.setClipToOutline(true);
            art.setBackgroundColor(0xFF1A1A20);
            art.setElevation(dp(4));
            cell.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(168)));
            TextView name = new TextView(CloudGamesActivity.this);
            name.setTypeface(PocketFonts.semibold());
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            name.setTextColor(0xE6FFFFFF);
            name.setGravity(Gravity.CENTER);
            name.setPadding(dp(8), 0, dp(8), 0);
            name.setMaxLines(3);
            cell.addView(name, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(168)));
            final RecyclerView.ViewHolder h = new RecyclerView.ViewHolder(cell) {
            };
            cell.setOnFocusChangeListener((v, has) -> {
                art.animate().cancel();
                if (has) {
                    PocketSounds.play(CloudGamesActivity.this, PocketSounds.NAV);
                    art.animate().scaleX(1.08f).scaleY(1.08f).translationZ(dp(12)).setDuration(240)
                            .setInterpolator(Spring.POP).start();
                    GradientDrawable ring = new GradientDrawable();
                    ring.setCornerRadius(dp(10));
                    ring.setStroke(dp(3), 0xFFFFFFFF);
                    art.setForeground(ring);
                    onFocused(h.getBindingAdapterPosition());
                }
                else {
                    art.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(180).start();
                    art.setForeground(null);
                }
            });
            cell.setOnClickListener(v -> {
                int p = h.getBindingAdapterPosition();
                if (p >= 0 && p < shown.size()) {
                    play(shown.get(p), null);
                }
            });
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final XcloudCatalog.Game g = shown.get(position);
            FrameLayout cell = (FrameLayout) holder.itemView;
            final ImageView art = (ImageView) cell.getChildAt(0);
            final TextView name = (TextView) cell.getChildAt(1);
            name.setText(g.title);
            name.setVisibility(View.VISIBLE);
            art.setImageDrawable(null);
            art.setTag(g.id);
            String url = g.poster != null ? g.poster : g.box;
            if (url == null) {
                return;
            }
            int w = dp(120);
            RemoteImages.load(CloudGamesActivity.this, XcloudCatalog.sized(url, 300), w, b -> {
                if (b != null && g.id.equals(art.getTag())) {
                    art.setImageBitmap(b);
                    name.setVisibility(View.GONE);
                }
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
