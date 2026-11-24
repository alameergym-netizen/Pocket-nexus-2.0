package com.limelight.pocket.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.GamePageActivity;
import com.limelight.pocket.PocketArt;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.apps.AndroidLibrary;
import com.limelight.pocket.home.HomeActivity;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.home.HomeLauncher;
import com.limelight.pocket.home.HomeStore;
import com.limelight.pocket.library.UniversalLibrary;
import com.limelight.pocket.retro.RetroLibrary;
import com.limelight.pocket.retro.RomScanner;
import com.limelight.pocket.ui.ConsoleScreen;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Search everything at once, like a console's search: the games on Home, every Android app and
 * game, every retro game in the ROM folder, and Pocket Nexus's own places (Files, the browser,
 * settings...). The results come as you type (with the controller keyboard); A starts one,
 * X opens its page.
 */
public class SearchActivity extends Activity {

    public static void open(Context c) {
        try {
            c.startActivity(new Intent(c, SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
        }
    }

    /** A thing that can be found. */
    private static final class Hit {
        final String name;
        final String where;
        final HomeItem item;
        final Runnable tool;
        final String key;
        final String sourceKey;
        int score;

        Hit(String name, String where, HomeItem item, Runnable tool) {
            this.name = name;
            this.where = where;
            this.item = item;
            this.tool = tool;
            this.key = plain(name);
            this.sourceKey = plain(where);
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Hit> all = new ArrayList<>();
    private final List<Hit> shown = new ArrayList<>();
    private final LruCache<String, Bitmap> art = new LruCache<>(48);
    private EditText field;
    private RecyclerView list;
    private Rows rows;
    private TextView count;
    private boolean ready;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConsoleActivity.applyFullscreenWindow(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(ConsoleScreen.backdrop(this));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(30), dp(20), dp(30), dp(10));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ConsoleScreen.title(this, getString(R.string.pocket_search_title)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        count = ConsoleScreen.text(this, 11.5f, 0x8CFFFFFF, true);
        head.addView(count);
        column.addView(head);

        field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(17);
        field.setTextColor(0xFFFFFFFF);
        field.setHintTextColor(0x66FFFFFF);
        field.setHint(R.string.pocket_search_hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setPadding(dp(18), dp(12), dp(18), dp(12));
        android.graphics.drawable.GradientDrawable fbg = new android.graphics.drawable.GradientDrawable();
        fbg.setColor(0x1FFFFFFF);
        fbg.setCornerRadius(dp(22));
        fbg.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
        field.setBackground(fbg);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(12);
        flp.bottomMargin = dp(12);
        column.addView(field, flp);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter();
            }
        });
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                focusResult(0);
                return true;
            }
            return false;
        });

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setItemAnimator(null);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(40));
        rows = new Rows();
        list.setAdapter(rows);
        column.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout hints = ConsoleScreen.hintBar(this);
        column.addView(hints, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        ConsoleActivity.fillHintBar(this, hints,
                ConsoleActivity.hintA(this, R.string.pocket_hint_play),
                ConsoleActivity.hintX(this, R.string.pocket_hint_details),
                ConsoleActivity.hintY(this, R.string.pocket_search_type),
                ConsoleActivity.hintB(this, R.string.pocket_hint_back));
        setContentView(root);
        gather();
        field.requestFocus();
        main.postDelayed(this::showKeyboard, 250);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConsoleActivity.applyFullscreenWindow(this);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(float v) {
        return ConsoleScreen.dp(this, v);
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && field.hasFocus()) {
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        }
    }

    // ---- What can be found --------------------------------------------------------------

    private void gather() {
        final Context app = getApplicationContext();
        // Pocket Nexus's own places first (they're found by name like the rest)
        all.add(new Hit(getString(R.string.pocket_ul_title), getString(R.string.pocket_search_tool), null,
                () -> com.limelight.pocket.library.LibraryActivity.open(this)));
        all.add(new Hit(getString(R.string.pocket_files_title), getString(R.string.pocket_search_tool), null,
                () -> com.limelight.pocket.files.FilesActivity.open(this)));
        all.add(new Hit(getString(R.string.pocket_stats_title), getString(R.string.pocket_search_tool), null,
                () -> com.limelight.pocket.home.StatsActivity.open(this)));
        all.add(new Hit(getString(R.string.pocket_browser_title), getString(R.string.pocket_search_tool), null,
                () -> com.limelight.pocket.browser.BrowserActivity.open(this, null)));
        all.add(new Hit(getString(R.string.pocket_set_title_all), getString(R.string.pocket_search_tool), null,
                () -> startActivity(new Intent(this, com.limelight.pocket.QuickSettingsActivity.class))));
        all.add(new Hit(getString(R.string.pocket_retro_title), getString(R.string.pocket_search_tool), null,
                () -> com.limelight.pocket.retro.RetroActivity.open(this)));
        all.add(new Hit(getString(R.string.pocket_apps_title), getString(R.string.pocket_search_tool), null,
                () -> com.limelight.pocket.apps.AndroidAppsActivity.open(this)));
        io.execute(() -> {
            final List<Hit> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            try {
                for (HomeStore.Entry e : HomeStore.load(app)) {
                    if (seen.add(e.item.key())) {
                        found.add(new Hit(e.item.name, e.pcName.isEmpty() ? app.getString(R.string.pocket_search_home)
                                : e.pcName, e.item, null));
                    }
                }
            } catch (RuntimeException ignored) {
            }
            for (AndroidLibrary.InstalledApp a : AndroidLibrary.installed(app)) {
                HomeItem item = new HomeItem(HomeItem.ANDROID, a.label, a.packageName, "", "", 0);
                if (seen.add(item.key())) {
                    found.add(new Hit(a.label, app.getString(R.string.pocket_search_android), item, null));
                }
            }
            File root = RetroLibrary.romRoot(app);
            RetroLibrary.Library lib = root != null ? RetroLibrary.cached(app, root) : null;
            if (lib != null) {
                com.limelight.pocket.retro.EsdeConfig cfg = RetroLibrary.config(app);
                for (List<RomScanner.Rom> roms : lib.games.values()) {
                    for (RomScanner.Rom r : roms) {
                        HomeItem item = com.limelight.pocket.retro.HorizonRetroCatalog.item(root.getAbsolutePath(),r);
                        if (item != null && seen.add(item.key())) {
                            com.limelight.pocket.retro.EsdeConfig.SystemDef s = cfg.system(r.system);
                            found.add(new Hit(r.name,com.limelight.pocket.retro.HorizonRetroCatalog.label(
                                    s != null ? s.fullName : r.system,item.c),item,null));
                        }
                    }
                }
            }
            // Include every cached PC game and other launch path known to the unified
            // library, even if it has never been pinned or launched from Home.
            // Keep existing Home and app hits first so custom names remain intact.
            try {
                for (UniversalLibrary.Game game : UniversalLibrary.gather(app)) {
                    if (Thread.currentThread().isInterrupted()) { return; }
                    for (UniversalLibrary.Way way : game.ways) {
                        if (seen.add(way.item.key())) {
                            found.add(new Hit(way.item.name, way.label, way.item, null));
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // The local index is still useful if a library source is unavailable.
            }
            main.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                all.addAll(found);
                ready = true;
                filter();
            });
        });
    }

    static String plain(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private void filter() {
        String q = plain(field.getText().toString());
        shown.clear();
        if (q.isEmpty()) {
            // Nothing typed yet: what's on Home, then Pocket Nexus's places
            for (Hit h : all) {
                if (h.item != null && h.where.equals(getString(R.string.pocket_search_home)) && shown.size() < 12) {
                    shown.add(h);
                }
            }
            for (Hit h : all) {
                if (h.tool != null) {
                    shown.add(h);
                }
            }
        }
        else {
            String[] words = q.split(" ");
            for (Hit h : all) {
                int s = rank(h.key, h.sourceKey, q, words);
                if (s > 0) {
                    h.score = s + (h.where.equals(getString(R.string.pocket_search_home)) ? 5 : 0);
                    shown.add(h);
                }
            }
            Collections.sort(shown, (a, b) -> b.score != a.score ? Integer.compare(b.score, a.score)
                    : a.name.compareToIgnoreCase(b.name));
            if (shown.size() > 80) {
                shown.subList(80, shown.size()).clear();
            }
        }
        rows.notifyDataSetChanged();
        count.setText(ready ? (q.isEmpty() ? "" : getString(R.string.pocket_search_count, shown.size()))
                : getString(R.string.pocket_search_loading));
    }

    /** A name match outranks a source-only match; mixed queries such as "zelda wii u" work too. */
    static int rank(String name, String source, String q, String[] words) {
        if (q.isEmpty()) { return 0; }
        int direct = score(name, q, words);
        if (direct > 0) { return direct; }
        int combined = score(name + " " + source, q, words);
        if (combined == 0) { return 0; }
        // Match the source alone, or a mixture of title and source. Never outrank a name match.
        return score(source, q, words) > 0 ? 5 : 15;
    }

    /** How well a name matches: the whole of it, its start, a word's start, inside it, all words. */
    private static int score(String name, String q, String[] words) {
        if (name.equals(q)) {
            return 100;
        }
        if (name.startsWith(q)) {
            return 80;
        }
        if (name.contains(" " + q)) {
            return 60;
        }
        if (name.contains(q)) {
            return 40;
        }
        int all = 0;
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (!name.contains(w)) {
                return 0;
            }
            all++;
        }
        return all > 0 ? 20 : 0;
    }

    // ---- Doing --------------------------------------------------------------------------

    private void run(Hit h) {
        PocketSounds.play(this, PocketSounds.SELECT);
        if (h.tool != null) {
            h.tool.run();
            return;
        }
        HomeItem item = h.item;
        if (HomeItem.PC.equals(item.kind) || HomeItem.PC_LIBRARY_GAME.equals(item.kind) || HomeItem.PC_GAMES.equals(item.kind)) {
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(HomeActivity.EXTRA_ACTION, HomeActivity.ACTION_LAUNCH)
                    .putExtra(HomeActivity.EXTRA_ITEM, item.toLine()));
            return;
        }
        HomeLauncher.launch(this, item);
    }

    private void page(Hit h) {
        if (h.item == null) {
            return;
        }
        String uuid = HomeActivity.artUuid(h.item);
        if (uuid == null) {
            return;
        }
        PocketSounds.play(this, PocketSounds.OPEN);
        GamePageActivity.open(this, null, uuid, HomeActivity.artId(h.item), RomScanner.searchName(h.name), h.where,
                false, false, null,HomeItem.RETRO.equals(h.item.kind)?h.item:null);
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode==GamePageActivity.REQUEST && resultCode==RESULT_OK && data!=null
                && GamePageActivity.ACTION_PLAY.equals(data.getStringExtra(GamePageActivity.RESULT_ACTION))) {
            String retro=data.getStringExtra(GamePageActivity.RESULT_RETRO_ITEM);
            HomeItem item=com.limelight.pocket.retro.HorizonRetroPageIdentity.read(retro,
                    data.getIntExtra(GamePageActivity.RESULT_APP_ID,0));
            if (item!=null) { HomeLauncher.launch(this,item); }
        }
    }

    private Hit focusedHit() {
        View f = getCurrentFocus();
        if (f == null || f.getParent() != list) {
            return null;
        }
        int p = list.getChildAdapterPosition(f);
        return p >= 0 && p < shown.size() ? shown.get(p) : null;
    }

    private void focusResult(int position) {
        if (shown.isEmpty()) {
            return;
        }
        list.scrollToPosition(position);
        list.post(() -> {
            RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(position);
            if (h != null) {
                h.itemView.requestFocus();
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
                if (up) {
                    if (list.hasFocus()) {
                        field.requestFocus();
                        showKeyboard();
                    }
                    else {
                        PocketSounds.play(this, PocketSounds.CLOSE);
                        finish();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
                if (up && list.hasFocus()) {
                    Hit h = focusedHit();
                    if (h != null) {
                        run(h);
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (up && list.hasFocus()) {
                    Hit h = focusedHit();
                    if (h != null) {
                        page(h);
                    }
                }
                return list.hasFocus() || super.dispatchKeyEvent(event);
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (up) {
                    field.requestFocus();
                    showKeyboard();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (field.hasFocus() && event.getAction() == KeyEvent.ACTION_DOWN) {
                    hideKeyboard();
                    focusResult(0);
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    // ---- Rows ---------------------------------------------------------------------------

    private final class Rows extends RecyclerView.Adapter<Rows.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView name;
            final TextView where;
            String bound;

            Holder(LinearLayout row) {
                super(row);
                image = (ImageView) row.getChildAt(0);
                LinearLayout t = (LinearLayout) row.getChildAt(1);
                name = (TextView) t.getChildAt(0);
                where = (TextView) t.getChildAt(1);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(6), dp(14), dp(6));
            row.setFocusable(true);
            row.setClickable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            row.setLayoutParams(lp);
            ImageView image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(7));
                }
            });
            image.setClipToOutline(true);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(30), dp(42));
            ilp.setMarginEnd(dp(12));
            row.addView(image, ilp);
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = ConsoleScreen.text(c, 14.5f, 0xF2FFFFFF, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            TextView where = ConsoleScreen.text(c, 11, 0x8CFFFFFF, false);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wl.topMargin = dp(3);
            texts.addView(where, wl);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder h, int position) {
            final Hit hit = shown.get(position);
            h.name.setText(hit.name);
            h.where.setText(hit.where);
            ConsoleScreen.lightRow(h.itemView, h.itemView.hasFocus(), h.name, h.where);
            h.itemView.setOnFocusChangeListener((v, has) -> {
                ConsoleScreen.lightRow(v, has, h.name, h.where);
                if (has) {
                    PocketSounds.play(SearchActivity.this, PocketSounds.NAV);
                }
            });
            h.itemView.setOnClickListener(v -> run(hit));
            h.image.setImageDrawable(null);
            h.image.setBackgroundColor(0x1FFFFFFF);
            final String key = hit.item != null ? hit.item.key() : null;
            h.bound = key;
            if (key == null) {
                return;
            }
            Bitmap known = art.get(key);
            if (known != null) {
                h.image.setImageBitmap(known);
                return;
            }
            final String uuid = HomeActivity.artUuid(hit.item);
            if (uuid == null) {
                return;
            }
            final int id = HomeActivity.artId(hit.item);
            final int w = dp(60);
            io.execute(() -> {
                File f = PocketArt.file(SearchActivity.this, uuid, id, PocketArt.COVER);
                if (!f.exists()) {
                    f = PocketArt.file(SearchActivity.this, uuid, id, PocketArt.ICON);
                }
                if (!f.exists()) {
                    return;
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 4;
                final Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
                if (b == null) {
                    return;
                }
                main.post(() -> {
                    art.put(key, b);
                    if (key.equals(h.bound)) {
                        h.image.setImageBitmap(b);
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
