package com.limelight.pocket.retro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.pocket.ArtPickerActivity;
import com.limelight.pocket.ArtScraper;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.GamePageActivity;
import com.limelight.pocket.PocketArt;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.AnchoredLayoutManager;
import com.limelight.pocket.ui.CarouselAdapter;
import com.limelight.pocket.ui.HeroArt;
import com.limelight.pocket.ui.HeroFocus;
import com.limelight.pocket.ui.KeyArtStage;
import com.limelight.pocket.ui.PlaceholderArt;
import com.limelight.pocket.ui.PocketMotion;
import com.limelight.pocket.ui.PocketUi;
import com.limelight.pocket.ui.SectionBar;
import com.limelight.pocket.ui.StreamMenuView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Retro section, like ES-DE: the systems found in the ROM folder (ES-DE's folder names),
 * then each system's games. A game starts with the emulator picked for it or its system:
 * a RetroArch core or a standalone emulator, with ES-DE's own launch commands. ES-DE's
 * settings, gamelists and scraped pictures are used when they're on the handheld.
 */
public class RetroActivity extends ConsoleActivity implements CarouselAdapter.Listener {

    private static final int REQUEST_TREE = 7312;
    private static final String SYS_RECENT = "@recent";
    private static final String SYS_FAVS = "@favorites";
    private static final String SYS_SUGGEST = "@suggested";
    private static final int SUGGEST_MAX = 18;

    /** Today's suggestions (worked out once per library, per day, per game played). */
    private List<RomScanner.Rom> suggestedCache;
    private String suggestedFor;

    static final int KIND_SYSTEM = 0;
    static final int KIND_GAME = 1;
    static final int KIND_SETUP = 2;

    public static void open(Activity from) {
        PocketSounds.play(from, PocketSounds.OPEN);
        from.startActivity(new Intent(from, RetroActivity.class));
    }

    static final class Entry {
        final int kind;
        final String key;
        final String label;
        /** For a system: its name (or SYS_RECENT / SYS_FAVS). */
        String system;
        RomScanner.Rom rom;
        int count;
        String note;
        Runnable action;

        Entry(int kind, String key, String label) {
            this.kind = kind;
            this.key = key;
            this.label = label;
        }
    }

    private interface Pick {
        void onPick(EsdeConfig.Command command);
    }

    private RecyclerView carousel;
    private final Cards adapter = new Cards();
    private KeyArtStage keyArt;
    private int heroPos = -1;
    private String heroKey;
    private int artVersion = PocketArt.version();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    /** Console art (downloaded the first time, then kept): apart from the games' covers. */
    private final ExecutorService systemWork = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> BACKDROPS = new LruCache<>(3);
    private TextView about;
    /** The focused game's trophies, at the top end over the art (carousel only). */
    private com.limelight.pocket.ui.TrophyCard trophyCard;
    private Runnable trophyLoad;
    private static final LruCache<String, Bitmap> CARDS = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private EsdeConfig cfg;
    private RetroLibrary.Library lib;
    /** The open system (null: the systems). */
    private String inSystem;
    private String systemsFocus;
    private boolean hadAccess;
    private boolean resumedOnce;

    @Override
    protected boolean allowsAutoResume() {
        return false;
    }

    @Override
    protected String layoutScope() {
        return scopeFor(inSystem);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // Built again (a new layout for this system): back in the same place
            inSystem = savedInstanceState.getString(STATE_SYSTEM);
            systemsFocus = savedInstanceState.getString(STATE_SYSTEMS_FOCUS);
            heroKey = savedInstanceState.getString(STATE_HERO);
        }
        // Each system has its own layout (and the list of systems its own)
        PocketPrefs.setLayoutScope(scopeFor(inSystem));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_view);
        TextView title = findViewById(R.id.appListText);
        if (title != null) {
            title.setText(R.string.pocket_retro_title);
        }
        SectionBar.attach(this, SectionBar.RETRO);
        hide(R.id.profilesButton);
        // No pill at the bottom: the ROM folder is in the options (Y)
        hide(R.id.pocketPcLibraryButton);
        carousel = findViewById(R.id.pocketAppCarousel);
        carousel.setLayoutManager(new AnchoredLayoutManager(this));
        carousel.setItemAnimator(null);
        carousel.setAdapter(new CarouselAdapter(adapter, this, 14));
        com.limelight.pocket.ui.LibraryLayout.apply(this, carousel);
        keyArt = KeyArtStage.find(this);
        if (com.limelight.pocket.ui.LibraryLayout.MODE_CAROUSEL.equals(PocketPrefs.viewMode(this))) {
            trophyCard = com.limelight.pocket.ui.TrophyCard.attach(this);
        }
        heroFocus = HeroFocus.install(this, new HeroFocus.Host() {
            @Override
            public boolean heroCanLift() {
                return carousel != null && carousel.hasFocus();
            }

            @Override
            public void heroPlay() {
                Entry e = entryAt(heroPos);
                if (e != null && e.kind == KIND_GAME) {
                    // The hero's Play: straight into the game
                    play(e.rom, null);
                }
                else if (e != null) {
                    activate(e);
                }
            }
        }, R.id.appFragmentContainer);
        heroIdle(getString(R.string.pocket_retro_loading), null);
        updateHints();
        load(false);
    }

    private void hide(int id) {
        View v = findViewById(id);
        if (v != null) {
            v.setVisibility(View.GONE);
        }
    }

    private void heroIdle(String text, String sub) {
        TextView t = findViewById(R.id.pocketHeroTitle);
        if (t != null) {
            t.setVisibility(View.VISIBLE);
            t.setText(text);
        }
        TextView s = findViewById(R.id.pocketHeroSubtitle);
        if (s != null) {
            s.setVisibility(sub != null ? View.VISIBLE : View.GONE);
            s.setText(sub);
        }
        hide(R.id.pocketHeroPlay);
        hide(R.id.pocketHeroPlaytime);
        hide(R.id.pocketHeroLogo);
        if (about != null) {
            about.setVisibility(View.GONE);
        }
        if (keyArt != null) {
            keyArt.show(null);
        }
    }

    private void updateHints() {
        if (inSystem == null) {
            setHints(hintA(this, R.string.pocket_apps_open),
                    hintY(this, R.string.pocket_hint_options),
                    hintShoulders(this, R.string.pocket_retro_hint_sections),
                    hintStart(this, R.string.pocket_hint_settings));
        }
        else {
            setHints(hintA(this, R.string.pocket_hint_details),
                    hintStart(this, R.string.pocket_hint_quick_play),
                    hintY(this, R.string.pocket_hint_options),
                    hintB(this, R.string.pocket_hint_back),
                    new Hint("L2 R2", 0xFFFFFFFF, getString(R.string.pocket_retro_hint_systems)),
                    hintSelect(this, R.string.pocket_layout_hint));
        }
    }

    private static final String STATE_SYSTEM = "retro_system";
    private static final String STATE_SYSTEMS_FOCUS = "retro_systems_focus";
    private static final String STATE_HERO = "retro_hero";

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_SYSTEM, inSystem);
        out.putString(STATE_SYSTEMS_FOCUS, systemsFocus);
        out.putString(STATE_HERO, heroKey != null ? heroKey : (heroPos >= 0 && entryAt(heroPos) != null
                ? entryAt(heroPos).key : null));
    }

    /** Whose layout this screen reads: a system's own, or the list of systems'. */
    private static String scopeFor(String system) {
        return "retro:" + (system != null ? system : "systems");
    }

    /** The layout read now, to tell whether another library's is different. */
    private String layoutNow() {
        return PocketPrefs.viewMode(this) + "/" + PocketPrefs.cardStyle(this);
    }

    /**
     * Moving to another library (into a system, back to the systems, the next system): when
     * its layout differs from this one, the screen is built again in it (in the same place).
     */
    private boolean rebuildFor(String system) {
        String before = layoutNow();
        PocketPrefs.setLayoutScope(scopeFor(system));
        if (before.equals(layoutNow())) {
            return false;
        }
        inSystem = system;
        recreate();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // The other screens read the layout every library shares
        PocketPrefs.setLayoutScope(null);
        stopSnap();
        if (snapPlayer != null) {
            snapPlayer.release();
        }
    }

    @Override
    protected void onResume() {
        PocketPrefs.setLayoutScope(scopeFor(inSystem));
        super.onResume();
        RetroLauncher.forget();
        if (!resumedOnce) {
            resumedOnce = true;
            return;
        }
        if (!hadAccess && RetroLibrary.hasAccess(this)) {
            load(false);
            return;
        }
        if (artVersion != PocketArt.version()) {
            artVersion = PocketArt.version();
            CARDS.evictAll();
        }
        if (lib != null) {
            // Played recently and the emulators installed may have changed
            showEntries();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (snapPlayer != null) {
            snapPlayer.release();
        }
        work.shutdownNow();
        systemWork.shutdownNow();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RetroLibrary.REQUEST_ACCESS) {
            load(false);
        }
    }

    // ---- Loading -------------------------------------------------------------------------------

    private final HorizonRetroSelection selection = new HorizonRetroSelection();

    private void load(final boolean rescan) {
        final long request = selection.next();
        hadAccess = RetroLibrary.hasAccess(this);
        if (!hadAccess) {
            showSetup();
            return;
        }
        final boolean firstTime = lib == null;
        work.execute(() -> {
            final EsdeConfig c = RetroLibrary.config(this);
            final File root = RetroLibrary.romRoot(this);
            if (root == null || !root.isDirectory()) {
                main.post(() -> {
                    if (!isFinishing() && !isDestroyed() && selection.current(request)) {
                        cfg = c;
                        lib = null;
                        showSetup();
                    }
                });
                return;
            }
            if (!rescan && firstTime) {
                final RetroLibrary.Library cached = RetroLibrary.cached(this, root);
                if (cached != null) {
                    main.post(() -> {
                        if (!isFinishing() && !isDestroyed() && selection.current(request) && lib == null) {
                            cfg = c;
                            lib = cached;
                            showEntries();
                        }
                    });
                }
                else {
                    main.post(() -> {
                        if (!isFinishing() && !isDestroyed() && selection.current(request)) {
                            heroIdle(getString(R.string.pocket_retro_looking),root.getAbsolutePath());
                        }
                    });
                }
            }
            final RetroLibrary.Library fresh = RetroLibrary.scan(this, root);
            final RetroLibrary.Library fallback = fresh.complete ? null : RetroLibrary.cached(this,root);
            main.post(() -> {
                if (isFinishing() || isDestroyed() || !selection.current(request)) {
                    return;
                }
                cfg = c;
                lib = fallback != null ? fallback : fresh;
                CARDS.evictAll();
                showEntries();
                if (!fresh.complete) {
                    Toast.makeText(this,R.string.horizon_retro_scan_incomplete,Toast.LENGTH_LONG).show();
                } else if (rescan) {
                    Toast.makeText(this, getString(R.string.pocket_retro_found, fresh.gameCount(), fresh.games.size()),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /** What to do before there are games: file access, a ROM folder, games in it. */
    private void showSetup() {
        List<Entry> out = new ArrayList<>();
        String title;
        String sub;
        if (!RetroLibrary.hasAccess(this)) {
            title = getString(R.string.pocket_retro_need_access);
            sub = getString(R.string.pocket_retro_need_access_sub);
            out.add(setup("access", getString(R.string.pocket_retro_allow_access), sub,
                    () -> RetroLibrary.requestAccess(this)));
        }
        else {
            File root = RetroLibrary.romRoot(this);
            boolean exists = root != null && root.isDirectory();
            title = getString(exists ? R.string.pocket_retro_empty : R.string.pocket_retro_no_folder);
            sub = exists ? getString(R.string.pocket_retro_empty_sub, root.getAbsolutePath())
                    : getString(R.string.pocket_retro_no_folder_sub);
            out.add(setup("pick", getString(R.string.pocket_retro_pick_folder), sub, this::pickFolder));
            out.add(setup("sources",getString(R.string.horizon_rom_sources),getString(R.string.horizon_rom_sources_note),
                    this::showRomSources));
            final File target = exists ? root : RetroLibrary.defaultRoot();
            out.add(setup("create", getString(R.string.pocket_retro_create_folders),
                    getString(R.string.pocket_retro_create_folders_sub, target.getAbsolutePath()), () -> createFolders(target)));
            if (exists) {
                out.add(setup("rescan", getString(R.string.pocket_retro_rescan), sub, () -> load(true)));
            }
        }
        inSystem = null;
        updateHints();
        adapter.set(out);
        updateSection(getString(R.string.pocket_retro_setup), out.size());
        heroPos = -1;
        heroKey = null;
        focusAt(0, 0);
        heroIdle(title, sub);
    }

    private Entry setup(String id, String label, String note, Runnable action) {
        Entry e = new Entry(KIND_SETUP, "setup:" + id, label);
        e.note = note;
        e.action = action;
        return e;
    }

    private void createFolders(final File root) {
        work.execute(() -> {
            final int made = RetroLibrary.createSystemFolders(this, root);
            main.post(() -> {
                if (isFinishing()) {
                    return;
                }
                if (made > 0 || root.isDirectory()) {
                    RetroLibrary.setRoot(this, root);
                    Toast.makeText(this, getString(R.string.pocket_retro_created, made, root.getAbsolutePath()),
                            Toast.LENGTH_LONG).show();
                    load(true);
                }
                else {
                    Toast.makeText(this, R.string.pocket_retro_cant_create, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void showRomSources() {
        List<StreamMenuView.Item> rows=new ArrayList<>();
        rows.add(StreamMenuView.Item.note(getString(R.string.horizon_rom_sources_note)));
        File current=RetroLibrary.romRoot(this);
        String selected=current==null?null:HorizonRomSources.normalize(current.getAbsolutePath());
        for (String path:RetroLibrary.rememberedRoots(this)) {
            File folder=new File(path);
            boolean active=path.equals(selected);
            boolean readable=folder.isDirectory() && folder.canRead();
            rows.add(StreamMenuView.Item.header(path));
            rows.add(StreamMenuView.Item.info(getString(R.string.horizon_rom_source_status),
                    getString(!readable ? R.string.horizon_rom_source_offline
                            : active ? R.string.horizon_rom_source_active : R.string.horizon_rom_source_available)));
            if (!active && readable) {
                rows.add(StreamMenuView.Item.confirm(getString(R.string.horizon_rom_source_select),() -> {
                    if (!folder.isDirectory() || !folder.canRead()) {
                        Toast.makeText(this,R.string.horizon_rom_source_offline,Toast.LENGTH_SHORT).show();
                        return;
                    }
                    RetroLibrary.setRoot(this,folder);
                    hideConsolePanel();
                    lib=null;inSystem=null;
                    load(true);
                }));
            }
            if (!active) {
                rows.add(StreamMenuView.Item.confirm(getString(R.string.horizon_rom_source_forget),() -> {
                    RetroLibrary.forgetRoot(this,path);
                    showRomSources();
                }));
            }
        }
        rows.add(StreamMenuView.Item.action(getString(R.string.pocket_retro_pick_folder),() -> {
            hideConsolePanel();
            pickFolder();
        }));
        showConsolePanel(getString(R.string.horizon_rom_sources),null,null,rows);
    }

    private void pickFolder() {
        if (!RetroLibrary.hasAccess(this)) {
            RetroLibrary.requestAccess(this);
            return;
        }
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_TREE);
            Toast.makeText(this, R.string.pocket_retro_pick_hint, Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.pocket_retro_cant_pick, Toast.LENGTH_LONG).show();
        }
    }

    // ---- What's shown ---------------------------------------------------------------------------

    private void showEntries() {
        if (lib == null || cfg == null) {
            return;
        }
        if (inSystem != null) {
            List<RomScanner.Rom> roms = romsOf(inSystem);
            if (roms.isEmpty()) {
                inSystem = null; // the last game of it went away
            }
            else {
                List<Entry> out = new ArrayList<>();
                for (RomScanner.Rom r : roms) {
                    Entry e = new Entry(KIND_GAME, "rom:" + r.key(), r.name);
                    e.rom = r;
                    e.system = r.system;
                    out.add(e);
                }
                show(out, systemTitle(inSystem));
                return;
            }
        }
        List<Entry> systems = systemEntries();
        if (systems.isEmpty()) {
            showSetup();
            return;
        }
        show(systems, getString(R.string.pocket_retro_systems));
    }

    private List<Entry> systemEntries() {
        List<Entry> out = new ArrayList<>();
        List<RomScanner.Rom> recent = RetroLibrary.recent(this, lib);
        if (!recent.isEmpty()) {
            Entry e = new Entry(KIND_SYSTEM, "sys:" + SYS_RECENT, getString(R.string.pocket_retro_recent));
            e.system = SYS_RECENT;
            e.count = recent.size();
            out.add(e);
        }
        List<RomScanner.Rom> favs = RetroLibrary.favorites(this, lib);
        if (!favs.isEmpty()) {
            Entry e = new Entry(KIND_SYSTEM, "sys:" + SYS_FAVS, getString(R.string.pocket_retro_favorites));
            e.system = SYS_FAVS;
            e.count = favs.size();
            out.add(e);
        }
        // Games to try next (the ones not played yet, from the systems played most)
        List<RomScanner.Rom> picks = suggested();
        if (picks.size() >= 4) {
            Entry e = new Entry(KIND_SYSTEM, "sys:" + SYS_SUGGEST, getString(R.string.pocket_retro_suggested));
            e.system = SYS_SUGGEST;
            e.count = picks.size();
            out.add(e);
        }
        boolean showEmpty = RetroLibrary.showEmptySystems(this);
        List<Entry> real = new ArrayList<>();
        for (EsdeConfig.SystemDef s : cfg.systems()) {
            List<RomScanner.Rom> roms = lib.games.get(s.name);
            int n = roms != null ? roms.size() : 0;
            if (n == 0 && !showEmpty) {
                continue;
            }
            Entry e = new Entry(KIND_SYSTEM, "sys:" + s.name, s.fullName);
            e.system = s.name;
            e.count = n;
            real.add(e);
        }
        Collections.sort(real, (a, b) -> {
            if ((a.count == 0) != (b.count == 0)) {
                return a.count == 0 ? 1 : -1;
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        out.addAll(real);
        boolean any = false;
        for (Entry e : real) {
            any |= e.count > 0;
        }
        return any ? out : new ArrayList<Entry>();
    }

    private List<RomScanner.Rom> suggested() {
        if (lib == null) {
            return new ArrayList<>();
        }
        List<String[]> recent = RetroLibrary.recentEntries(this);
        String sig = System.identityHashCode(lib) + "|" + (System.currentTimeMillis() / (24L * 60 * 60 * 1000))
                + "|" + (recent.isEmpty() ? "" : recent.get(0)[1]);
        if (suggestedCache == null || !sig.equals(suggestedFor)) {
            suggestedCache = RetroLibrary.suggested(this, lib, SUGGEST_MAX);
            suggestedFor = sig;
        }
        return suggestedCache;
    }

    /** The colour of a collection's card (recently played, favourites, suggestions), or 0. */
    private static int collectionColor(String system) {
        return SYS_FAVS.equals(system) ? 0xFFF9A825 : SYS_RECENT.equals(system) ? 0xFF37474F
                : SYS_SUGGEST.equals(system) ? 0xFF7E57C2 : 0;
    }

    private List<RomScanner.Rom> romsOf(String system) {
        if (SYS_SUGGEST.equals(system)) {
            return new ArrayList<>(suggested());
        }
        if (SYS_RECENT.equals(system)) {
            return RetroLibrary.recent(this, lib);
        }
        if (SYS_FAVS.equals(system)) {
            return RetroLibrary.favorites(this, lib);
        }
        List<RomScanner.Rom> l = lib.games.get(system);
        if (l == null) {
            return new ArrayList<>();
        }
        // ES-DE's "favourites first"
        List<RomScanner.Rom> favs = new ArrayList<>();
        List<RomScanner.Rom> rest = new ArrayList<>();
        for (RomScanner.Rom r : l) {
            (RetroLibrary.isFavorite(this, lib, r) ? favs : rest).add(r);
        }
        favs.addAll(rest);
        return favs;
    }

    private String systemTitle(String system) {
        if (SYS_RECENT.equals(system)) {
            return getString(R.string.pocket_retro_recent);
        }
        if (SYS_FAVS.equals(system)) {
            return getString(R.string.pocket_retro_favorites);
        }
        if (SYS_SUGGEST.equals(system)) {
            return getString(R.string.pocket_retro_suggested);
        }
        EsdeConfig.SystemDef s = cfg.system(system);
        return s != null ? s.fullName : system;
    }

    private void show(List<Entry> entries, String section) {
        String keep = heroKey;
        adapter.set(entries);
        updateSection(section, entries.size());
        int target = 0;
        for (int i = 0; keep != null && i < entries.size(); i++) {
            if (entries.get(i).key.equals(keep)) {
                target = i;
            }
        }
        heroPos = -1;
        heroKey = null;
        focusAt(target, 0);
    }

    /** The section line as it is without the art search's progress. */
    private String sectionText;

    private void updateSection(String text, int count) {
        TextView section = findViewById(R.id.pocketSectionTitle);
        sectionText = text + "  ·  " + count;
        if (section != null) {
            section.setText(sectionText);
            showScrapeProgress();
        }
    }

    private void focusAt(final int position, final int attempt) {
        if (attempt == 0) {
            carousel.scrollToPosition(position);
        }
        carousel.post(() -> {
            if (isFinishing() || position >= adapter.getCount()) {
                return;
            }
            RecyclerView.ViewHolder holder = carousel.findViewHolderForAdapterPosition(position);
            if (holder != null && holder.itemView.requestFocus()) {
                return;
            }
            if (attempt < 6) {
                carousel.postDelayed(() -> focusAt(position, attempt + 1), attempt < 2 ? 16 : 120);
            }
            if (heroPos != position) {
                updateHero(position);
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && carousel != null && !carousel.hasFocus() && adapter.getCount() > 0 && !isConsolePanelShowing()) {
            focusAt(heroPos >= 0 && heroPos < adapter.getCount() ? heroPos : 0, 0);
        }
    }

    private Entry entryAt(int position) {
        return position >= 0 && position < adapter.getCount() ? adapter.getItem(position) : null;
    }

    // ---- The hero -------------------------------------------------------------------------------

    private String emulatorLabel(EsdeConfig.Command c) {
        if (c == null || c.isPlaceholder()) {
            return getString(R.string.pocket_retro_no_emulator);
        }
        String l = c.isRetroArch() ? "RetroArch · " + c.shortLabel() : c.shortLabel();
        if (!RetroLauncher.installed(this, cfg, c)) {
            l += "  (" + getString(R.string.pocket_retro_not_installed) + ")";
        }
        return l;
    }

    private void updateHero(int position) {
        Entry e = entryAt(position);
        if (e == null) {
            return;
        }
        TextView title = findViewById(R.id.pocketHeroTitle);
        int dir = heroPos < 0 ? 0 : Integer.signum(position - heroPos);
        if (title != null && title.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            dir = -dir;
        }
        boolean changed = !e.key.equals(heroKey);
        heroPos = position;
        heroKey = e.key;
        if (title != null && changed) {
            title.setVisibility(View.VISIBLE);
            PocketMotion.swapText(title, e.label, dir);
        }
        TextView subtitle = findViewById(R.id.pocketHeroSubtitle);
        hide(R.id.pocketHeroPlaytime);
        TextView play = findViewById(R.id.pocketHeroPlay);
        String sub;
        int playLabel;
        if (e.kind == KIND_SYSTEM) {
            sub = systemLine(e, SystemArt.cachedInfo(e.system));
            playLabel = R.string.pocket_apps_open;
        }
        else if (e.kind == KIND_GAME) {
            EsdeConfig.SystemDef s = cfg.system(e.rom.system);
            sub = s != null ? s.fullName + "  ·  " + emulatorLabel(RetroLibrary.gameCommand(this, lib, s, e.rom)) : "";
            if (RetroLibrary.isFavorite(this, lib, e.rom)) {
                sub = "★  " + sub;
            }
            playLabel = R.string.pocket_hero_play;
        }
        else {
            sub = e.note;
            playLabel = 0;
        }
        if (subtitle != null) {
            subtitle.setVisibility(sub != null && !sub.isEmpty() ? View.VISIBLE : View.GONE);
            subtitle.setText(sub);
        }
        if (play != null) {
            if (playLabel == 0) {
                play.setVisibility(View.GONE);
            }
            else {
                PocketUi.stylePlayPill(play);
                play.setVisibility(View.VISIBLE);
                String label = "▶  " + getString(playLabel);
                if (!label.contentEquals(play.getText())) {
                    play.setText(label);
                }
            }
        }
        if (changed) {
            showAbout(e.kind == KIND_SYSTEM ? SystemArt.cachedInfo(e.system) : null);
            showArt(e);
            showTrophies(e);
            scheduleSnap(e);
        }
    }

    // ---- The game's video snap behind the hero (ES-DE's, after a moment on it) --------------

    private static final String KEY_SNAPS = "pocket_retro_video_snaps";
    private android.view.TextureView snapView;
    private com.limelight.pocket.ui.TrailerPlayer snapPlayer;
    private Runnable snapStart;
    private String snapKey;

    private boolean snapsOn() {
        return PocketPrefs.prefs(this).getBoolean(KEY_SNAPS, true);
    }

    private void scheduleSnap(final Entry e) {
        stopSnap();
        if (e == null || e.kind != KIND_GAME || !snapsOn() || !com.limelight.pocket.ui.LibraryLayout.MODE_CAROUSEL
                .equals(PocketPrefs.viewMode(this))) {
            return;
        }
        final String key = e.key;
        final RomScanner.Rom rom = e.rom;
        snapKey = key;
        snapStart = () -> work.execute(() -> {
            final File video = RetroLibrary.esdeVideo(rom);
            if (video == null) {
                return;
            }
            main.post(() -> {
                if (!isFinishing() && key.equals(snapKey) && key.equals(heroKey)) {
                    playSnap(video);
                }
            });
        });
        main.postDelayed(snapStart, 1500);
    }

    private void playSnap(File video) {
        ViewGroup backdrop = findViewById(R.id.pocketBackdrop);
        View frame = findViewById(R.id.pocketKeyArtFrame);
        if (backdrop == null) {
            return;
        }
        if (snapView == null) {
            snapView = new android.view.TextureView(this);
            snapView.setAlpha(0f);
            int at = frame != null ? backdrop.indexOfChild(frame) + 1 : 1;
            backdrop.addView(snapView, Math.min(at, backdrop.getChildCount()), new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            snapPlayer = new com.limelight.pocket.ui.TrailerPlayer(this, snapView);
        }
        snapView.animate().cancel();
        snapView.setAlpha(0f);
        snapPlayer.play(android.net.Uri.fromFile(video).toString(), true, true,
                new com.limelight.pocket.ui.TrailerPlayer.Listener() {
                    @Override
                    public void onFirstFrame() {
                        if (snapView != null) {
                            snapView.animate().alpha(0.92f).setDuration(600).start();
                        }
                    }

                    @Override
                    public void onError() {
                        stopSnap();
                    }

                    @Override
                    public void onVideoSize(int width, int height, float pixelRatio) {
                        cropSnap(width * pixelRatio, height);
                    }
                });
    }

    /** The video fills the backdrop (cut at its edges, never stretched). */
    private void cropSnap(float vw, float vh) {
        if (snapView == null || vw <= 0 || vh <= 0) {
            return;
        }
        float w = snapView.getWidth();
        float h = snapView.getHeight();
        if (w <= 0 || h <= 0) {
            snapView.post(() -> cropSnap(vw, vh));
            return;
        }
        float scale = Math.max(w / vw, h / vh);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(vw * scale / w, vh * scale / h, w / 2f, h / 2f);
        snapView.setTransform(m);
    }

    private void stopSnap() {
        if (snapStart != null) {
            main.removeCallbacks(snapStart);
            snapStart = null;
        }
        snapKey = null;
        if (snapView != null && snapPlayer != null) {
            final com.limelight.pocket.ui.TrailerPlayer player = snapPlayer;
            snapView.animate().cancel();
            snapView.animate().alpha(0f).setDuration(220).withEndAction(player::release).start();
        }
    }

    /**
     * The focused game's trophies on their card: what's known at once, the latest once the
     * focus rests on it a moment (scrolling past games doesn't ask RetroAchievements).
     */
    private void showTrophies(final Entry e) {
        if (trophyCard == null) {
            return;
        }
        if (trophyLoad != null) {
            main.removeCallbacks(trophyLoad);
            trophyLoad = null;
        }
        if (e == null || e.kind != KIND_GAME || Achievements.consoleId(e.rom.system) <= 0) {
            trophyCard.hide();
            return;
        }
        if (!Achievements.hasRa(this)) {
            trophyCard.showHint(getString(R.string.pocket_trophy_sign_in));
            return;
        }
        final Achievements.Target t = Achievements.Target.retro(e.rom.system, e.rom.relPath, e.rom.name);
        Achievements.Set known = Achievements.cached(this, t);
        if (known != null && known.total > 0) {
            trophyCard.show(known);
        }
        else {
            trophyCard.hide();
        }
        final String key = e.key;
        trophyLoad = () -> Achievements.load(this, t, false, set -> {
            if (isFinishing() || trophyCard == null || !key.equals(heroKey)) {
                return;
            }
            if (set != null && set.total > 0) {
                trophyCard.show(set);
            }
            else {
                trophyCard.hide();
            }
        });
        main.postDelayed(trophyLoad, 650);
    }

    private String sysArtLabel() {
        if (!SystemArt.enabled(this)) {
            return getString(R.string.pocket_off);
        }
        switch (SystemArt.style(this)) {
            case SystemArt.SCREENSHOTS:
                return getString(R.string.pocket_sysart_screens);
            case SystemArt.OUTLINE:
                return getString(R.string.pocket_sysart_outline);
            case SystemArt.CIRCUIT:
                return getString(R.string.pocket_sysart_circuit);
            case SystemArt.NOIR:
                return getString(R.string.pocket_sysart_noir);
            default:
                return getString(R.string.pocket_sysart_artwork);
        }
    }

    /** "Nintendo · 1996 · 109 games · RetroArch" (what's known of it). */
    private String systemLine(Entry e, SystemArt.Info info) {
        StringBuilder b = new StringBuilder();
        if (info != null && !e.system.startsWith("@")) {
            if (info.maker != null && !info.maker.isEmpty()) {
                b.append(info.maker);
            }
            if (info.year != null && !info.year.isEmpty()) {
                b.append(b.length() > 0 ? "  ·  " : "").append(info.year);
            }
        }
        b.append(b.length() > 0 ? "  ·  " : "").append(getResources().getQuantityString(R.plurals.pocket_retro_games, e.count, e.count));
        EsdeConfig.SystemDef s = cfg.system(e.system);
        if (s != null) {
            b.append("  ·  ").append(emulatorLabel(RetroLibrary.systemCommand(this, lib, s)));
        }
        return b.toString();
    }

    /** A system's story, under its name (two lines). */
    private void showAbout(SystemArt.Info info) {
        if (about == null) {
            ViewGroup hero = findViewById(R.id.pocketHero);
            if (hero == null) {
                return;
            }
            float d = getResources().getDisplayMetrics().density;
            about = new TextView(this);
            about.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f);
            about.setTextColor(0xB8FFFFFF);
            about.setMaxLines(2);
            about.setEllipsize(android.text.TextUtils.TruncateAt.END);
            about.setLineSpacing(0, 1.15f);
            about.setMaxWidth(Math.round(470 * d));
            about.setShadowLayer(8, 0, 0, 0x80000000);
            about.setTypeface(com.limelight.pocket.ui.PocketFonts.regular());
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(10 * d);
            hero.addView(about, lp);
        }
        String text = info != null ? info.description : null;
        if (text == null || text.isEmpty()) {
            // Among the systems its room stays (moving from one with a story to one without
            // never moves the row); a system's games have none
            about.setVisibility(inSystem == null ? View.INVISIBLE : View.GONE);
            return;
        }
        boolean was = about.getVisibility() == View.VISIBLE && about.getText().length() > 0;
        about.setText(text);
        about.setVisibility(View.VISIBLE);
        if (!was && PocketPrefs.animationsEnabled(this)) {
            about.setAlpha(0f);
            about.animate().alpha(1f).setDuration(240).start();
        }
    }

    private void showArt(final Entry e) {
        final ImageView logo = findViewById(R.id.pocketHeroLogo);
        final TextView title = findViewById(R.id.pocketHeroTitle);
        if (logo != null) {
            logo.setVisibility(View.GONE);
        }
        if (title != null) {
            title.setVisibility(View.VISIBLE);
        }
        final String key = e.key;
        if (e.kind == KIND_SYSTEM && SystemArt.enabled(this)) {
            // The console: its logo in place of its name, its facts, its art as the backdrop
            final float d = getResources().getDisplayMetrics().density;
            final int lw = Math.round(340 * d);
            final int lh = Math.round(60 * d);
            final int bw = Math.max(640, getResources().getDisplayMetrics().widthPixels * 2 / 3);
            final int bh = Math.round(bw * 9f / 16f);
            final String system = e.system;
            final int color = collectionColor(system) != 0 ? collectionColor(system) : colorOf(system);
            Bitmap ready = BACKDROPS.get(system + "|" + PocketArt.version());
            if (keyArt != null) {
                keyArt.show(ready);
            }
            // Recently played / favourites: their own games' art (the newest first)
            final List<RomScanner.Rom> games = system.startsWith("@") ? romsOf(system) : null;
            systemWork.execute(() -> {
                // Each picked by itself on SteamGridDB (logo, background), else the theme's
                Bitmap ownLogo = SystemArt.picked(this, system, PocketArt.LOGO, lw);
                final Bitmap mark = ownLogo != null ? ownLogo : games != null ? null : SystemArt.logo(this, system, lw, lh);
                final SystemArt.Info info = SystemArt.info(this, system);
                String backKey = system + "|" + PocketArt.version()
                        + (games != null && !games.isEmpty() ? "|" + games.get(0).key() : "");
                Bitmap back = BACKDROPS.get(backKey);
                if (back == null) {
                    back = SystemArt.picked(this, system, PocketArt.HERO, bw);
                    if (back == null && games != null) {
                        for (int i = 0; i < Math.min(4, games.size()) && back == null; i++) {
                            back = SystemArt.decodeCover(PocketArt.file(this, RetroLibrary.UUID, games.get(i).id(),
                                    PocketArt.HERO), bw);
                        }
                        for (int i = 0; i < Math.min(4, games.size()) && back == null; i++) {
                            back = SystemArt.decodeCover(PocketArt.file(this, RetroLibrary.UUID, games.get(i).id(),
                                    PocketArt.COVER), bw / 2);
                        }
                    }
                    if (back == null) {
                        back = SystemArt.backdrop(this, system, color, bw, bh);
                    }
                    if (back != null) {
                        BACKDROPS.put(backKey, back);
                    }
                }
                final Bitmap backdrop = back;
                main.post(() -> {
                    if (isFinishing() || !key.equals(heroKey)) {
                        return;
                    }
                    if (mark != null && logo != null && title != null) {
                        logo.setImageBitmap(mark);
                        logo.setVisibility(View.VISIBLE);
                        title.setVisibility(View.GONE);
                        if (PocketPrefs.animationsEnabled(this)) {
                            logo.setAlpha(0f);
                            logo.setTranslationY(6 * d);
                            logo.animate().alpha(1f).translationY(0f).setDuration(320)
                                    .setInterpolator(com.limelight.pocket.ui.PocketMotion.DECELERATE).start();
                        }
                    }
                    if (info != null) {
                        TextView subtitle = findViewById(R.id.pocketHeroSubtitle);
                        if (subtitle != null) {
                            subtitle.setText(systemLine(e, info));
                        }
                        showAbout(info);
                    }
                    if (backdrop != null && keyArt != null) {
                        keyArt.show(backdrop);
                    }
                    else if (backdrop == null) {
                        gameBackdrop(e, key);
                    }
                });
            });
            return;
        }
        if (e.kind == KIND_SYSTEM) {
            gameBackdrop(e, key);
            return;
        }
        if (e.kind != KIND_GAME) {
            if (keyArt != null) {
                keyArt.show(null);
            }
            return;
        }
        final int gameId = e.rom.id();
        final String system = e.rom.system;
        PocketArt.load(this, RetroLibrary.UUID, gameId, PocketArt.HERO, bitmap -> {
            if (!key.equals(heroKey) || keyArt == null) {
                return;
            }
            if (bitmap != null || !SystemArt.enabled(this)) {
                keyArt.show(bitmap);
                // Its moving background (a video or an animated picture), once you rest on it
                final java.io.File motion = bitmap != null
                        ? com.limelight.pocket.ui.MotionArt.file(this, RetroLibrary.UUID, gameId, PocketArt.HERO) : null;
                if (motion != null) {
                    carousel.postDelayed(() -> {
                        if (key.equals(heroKey) && keyArt != null && !isFinishing()) {
                            keyArt.showMotion(motion);
                        }
                    }, 900);
                }
                return;
            }
            // No background for the game: its console's
            Bitmap known = BACKDROPS.get(system + "|" + PocketArt.version());
            if (known != null) {
                keyArt.show(known);
                return;
            }
            final int bw = Math.max(640, getResources().getDisplayMetrics().widthPixels * 2 / 3);
            final int color = colorOf(system);
            systemWork.execute(() -> {
                Bitmap made = SystemArt.picked(this, system, PocketArt.HERO, bw);
                if (made == null) {
                    made = SystemArt.backdrop(this, system, color, bw, Math.round(bw * 9f / 16f));
                }
                final Bitmap back = made;
                if (back != null) {
                    BACKDROPS.put(system + "|" + PocketArt.version(), back);
                }
                main.post(() -> {
                    if (!isFinishing() && key.equals(heroKey) && keyArt != null) {
                        keyArt.show(back);
                    }
                });
            });
        });
        setHeroGame(RetroLibrary.UUID, gameId);
        PocketArt.load(this, RetroLibrary.UUID, gameId, PocketArt.LOGO, bitmap -> {
            if (key.equals(heroKey) && bitmap != null && logo != null && title != null) {
                logo.setImageBitmap(bitmap);
                logo.setVisibility(View.VISIBLE);
                title.setVisibility(View.GONE);
            }
        });
        if (!PocketArt.has(this, RetroLibrary.UUID, gameId, PocketArt.COVER)
                || !PocketArt.has(this, RetroLibrary.UUID, gameId, PocketArt.HERO)) {
            scrape(e.rom, false);
        }
    }

    /** A game of the system as the backdrop (the one played last, else the first with art). */
    private void gameBackdrop(final Entry e, final String key) {
        final List<RomScanner.Rom> roms = romsOf(e.system);
        if (keyArt != null) {
            keyArt.show(null);
        }
        work.execute(() -> {
            RomScanner.Rom pick = null;
            long best = -1;
            for (int i = 0; i < roms.size() && i < 80; i++) {
                RomScanner.Rom r = roms.get(i);
                if (PocketArt.has(this, RetroLibrary.UUID, r.id(), PocketArt.HERO)) {
                    long played = RetroLibrary.lastPlayed(this, r.key());
                    if (played > best) {
                        best = played;
                        pick = r;
                    }
                }
            }
            final RomScanner.Rom chosen = pick;
            if (chosen != null) {
                main.post(() -> PocketArt.load(this, RetroLibrary.UUID, chosen.id(), PocketArt.HERO, bitmap -> {
                    if (key.equals(heroKey) && keyArt != null) {
                        keyArt.show(bitmap);
                    }
                }));
            }
        });
    }

    // ---- Art ------------------------------------------------------------------------------------

    /** ES-DE's own pictures first, then SteamGridDB, Steam and IGDB (in the background). */
    private void scrape(final RomScanner.Rom rom, final boolean force) {
        final String name = RomScanner.searchName(rom.name);
        work.execute(() -> {
            final boolean local = RetroLibrary.importEsdeMedia(this, rom);
            main.post(() -> {
                if (isFinishing()) {
                    return;
                }
                if (local) {
                    refreshArt();
                }
                ArtScraper.scrape(this, artItem(rom), force, changed -> {
                    if (isFinishing()) {
                        return;
                    }
                    if (changed) {
                        refreshArt();
                    }
                    else if (force && !local) {
                        Toast.makeText(this, R.string.pocket_art_found_nothing, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }

    /** What the art is looked for by: its name, and its system and file for libretro's thumbnails. */
    private static ArtScraper.Item artItem(RomScanner.Rom rom) {
        ArtScraper.Item item = new ArtScraper.Item(RetroLibrary.UUID, rom.id(), RomScanner.searchName(rom.name));
        item.retroSystem = rom.system;
        item.retroFile = rom.file != null ? rom.file.getName() : rom.relPath;
        return item;
    }

    // ---- Find missing art (every game here without a cover or a background) -----------------

    private int scrapeTotal;
    private int scrapeDone;
    private int scrapeFound;
    private boolean scrapeRefreshQueued;

    /** system: that system's games (a collection's too), null: every game. */
    private void scrapeMissing(String system) {
        if (lib == null) {
            return;
        }
        hideConsolePanel();
        if (scrapeTotal > 0 && scrapeDone < scrapeTotal) {
            Toast.makeText(this, getString(R.string.pocket_retro_scrape_busy, scrapeDone, scrapeTotal), Toast.LENGTH_SHORT).show();
            return;
        }
        final List<RomScanner.Rom> pool = new ArrayList<>();
        if (system != null) {
            pool.addAll(romsOf(system));
        }
        else {
            for (List<RomScanner.Rom> l : lib.games.values()) {
                pool.addAll(l);
            }
        }
        Toast.makeText(this, R.string.pocket_retro_scrape_checking, Toast.LENGTH_SHORT).show();
        work.execute(() -> {
            final List<RomScanner.Rom> missing = new ArrayList<>();
            for (RomScanner.Rom r : pool) {
                // ES-DE's downloaded media first (no internet needed)
                RetroLibrary.importEsdeMedia(this, r);
                if (!PocketArt.has(this, RetroLibrary.UUID, r.id(), PocketArt.COVER)
                        || !PocketArt.has(this, RetroLibrary.UUID, r.id(), PocketArt.HERO)) {
                    missing.add(r);
                }
            }
            main.post(() -> startScrapes(missing));
        });
    }

    private void startScrapes(List<RomScanner.Rom> missing) {
        if (isFinishing()) {
            return;
        }
        refreshArt();
        if (missing.isEmpty()) {
            Toast.makeText(this, R.string.pocket_retro_scrape_none, Toast.LENGTH_SHORT).show();
            return;
        }
        scrapeTotal = missing.size();
        scrapeDone = 0;
        scrapeFound = 0;
        Toast.makeText(this, getResources().getQuantityString(R.plurals.pocket_retro_scrape_start,
                missing.size(), missing.size()), Toast.LENGTH_LONG).show();
        PocketSounds.play(this, PocketSounds.SELECT);
        for (RomScanner.Rom r : missing) {
            ArtScraper.scrape(this, artItem(r), true, changed -> {
                scrapeDone++;
                if (changed) {
                    scrapeFound++;
                }
                if (isFinishing()) {
                    return;
                }
                showScrapeProgress();
                if (scrapeDone >= scrapeTotal) {
                    refreshArt();
                    Toast.makeText(this, getString(R.string.pocket_retro_scrape_done, scrapeFound, scrapeTotal),
                            Toast.LENGTH_LONG).show();
                }
                else if (changed && !scrapeRefreshQueued) {
                    // New covers show up as they come (not more than every few seconds)
                    scrapeRefreshQueued = true;
                    main.postDelayed(() -> {
                        scrapeRefreshQueued = false;
                        if (!isFinishing()) {
                            refreshArt();
                        }
                    }, 3000);
                }
            });
        }
        showScrapeProgress();
    }

    private void showScrapeProgress() {
        TextView section = findViewById(R.id.pocketSectionTitle);
        if (section == null || sectionText == null) {
            return;
        }
        if (scrapeTotal > 0 && scrapeDone < scrapeTotal) {
            section.setText(sectionText + "  ·  " + getString(R.string.pocket_retro_scrape_progress, scrapeDone, scrapeTotal));
        }
        else {
            section.setText(sectionText);
        }
    }

    private void refreshArt() {
        CARDS.evictAll();
        adapter.notifyDataSetChanged();
        if (heroPos >= 0) {
            heroKey = null;
            updateHero(heroPos);
        }
    }

    // ---- Buttons ----------------------------------------------------------------------------

    @Override
    public void onItemFocused(int position, View itemView) {
        updateHero(position);
    }

    @Override
    public void onItemClicked(int position, View itemView) {
        Entry e = entryAt(position);
        if (e != null) {
            activate(e);
        }
    }

    private void activate(Entry e) {
        switch (e.kind) {
            case KIND_SYSTEM:
                if (e.count > 0) {
                    enterSystem(e);
                }
                else {
                    Toast.makeText(this, getString(R.string.pocket_retro_system_empty, systemFolder(e.system)),
                            Toast.LENGTH_LONG).show();
                }
                break;
            case KIND_GAME:
                // Its page first (media, details, achievements, Play); START plays at once
                openPage(e);
                break;
            default:
                if (e.action != null) {
                    PocketSounds.play(this, PocketSounds.SELECT);
                    e.action.run();
                }
                break;
        }
    }

    private String systemFolder(String system) {
        EsdeConfig.SystemDef s = cfg != null ? cfg.system(system) : null;
        if (s == null || lib == null) {
            return system;
        }
        return s.absolute ? s.folder : new File(lib.root, s.folder).getAbsolutePath();
    }

    private void enterSystem(Entry e) {
        systemsFocus = e.key;
        heroKey = null;
        if (rebuildFor(e.system)) {
            return;
        }
        inSystem = e.system;
        PocketSounds.play(this, PocketSounds.OPEN);
        updateHints();
        showEntries();
        PocketMotion.cascade(carousel, 0, 22, 18, 0, 8);
    }

    private void leaveSystem() {
        heroKey = systemsFocus;
        if (rebuildFor(null)) {
            return;
        }
        inSystem = null;
        updateHints();
        showEntries();
    }

    @Override
    public void onBackPressed() {
        if (isConsolePanelShowing()) {
            super.onBackPressed();
            return;
        }
        if (inSystem != null) {
            leaveSystem();
            return;
        }
        SectionBar.goHome(this, SectionBar.RETRO);
    }

    private com.limelight.pocket.home.HomeItem homeItem(RomScanner.Rom rom) {
        return new com.limelight.pocket.home.HomeItem(com.limelight.pocket.home.HomeItem.RETRO,
                RomScanner.searchName(rom.name), rom.system, rom.relPath,
                lib != null && lib.root != null ? lib.root.getAbsolutePath() : "", 0);
    }

    /** A name of your own for the game (its details are looked up by it). */
    private void rename(final RomScanner.Rom rom) {
        final android.widget.EditText field = new android.widget.EditText(this);
        field.setSingleLine(true);
        field.setText(rom.name);
        field.setSelectAllOnFocus(true);
        com.limelight.pocket.ConsoleActivity.showImmersive(new android.app.AlertDialog.Builder(this, R.style.PocketDialog)
                .setTitle(R.string.pocket_rename)
                .setView(field)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    com.limelight.pocket.PocketPrefs.setCustomName(this, RetroLibrary.UUID, rom.id(), field.getText().toString());
                    applyCustomName(rom);
                    heroKey = null;
                    showEntries();
                })
                .setNeutralButton(R.string.pocket_rename_reset, (d, w) -> {
                    com.limelight.pocket.PocketPrefs.setCustomName(this, RetroLibrary.UUID, rom.id(), null);
                    rom.name = RomScanner.cleanName(rom.file.getName(), rom.directory);
                    heroKey = null;
                    showEntries();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create());
    }

    private void applyCustomName(RomScanner.Rom rom) {
        String n = com.limelight.pocket.PocketPrefs.customName(this, RetroLibrary.UUID, rom.id());
        if (n != null) {
            rom.name = n;
        }
    }

    /** Starts the game with its emulator (or this one, once). */
    private void play(final RomScanner.Rom rom, EsdeConfig.Command with) {
        // A previously opened game or emulator dialog can outlive a storage switch.
        File active=RetroLibrary.romRoot(this);
        RomScanner.Rom current=lib==null || rom==null ? null : lib.find(rom.key());
        if (cfg==null || lib==null || !HorizonRetroSelection.canLaunch(active,lib.root,rom,current)) {
            Toast.makeText(this,R.string.horizon_retro_source_changed,Toast.LENGTH_LONG).show();
            load(true);
            return;
        }
        final EsdeConfig.SystemDef s = cfg.system(rom.system);
        if (s == null) {
            return;
        }
        final EsdeConfig.Command cmd = with != null ? with : RetroLibrary.gameCommand(this, lib, s, rom);
        RetroLauncher.Result r = RetroLauncher.launch(this, cfg, cmd, rom.file, lib.root);
        switch (r.status) {
            case RetroLauncher.OK:
                PocketSounds.play(this, PocketSounds.SELECT);
                RetroLibrary.markPlayed(this, rom);
                com.limelight.pocket.home.HomeStore.record(this, homeItem(rom));
                com.limelight.pocket.global.AppHistory.started(this, r.packageName, homeItem(rom));
                // Its achievements pop up over the game as they're earned
                TrophyWatch.start(this, Achievements.Target.retro(rom.system, rom.relPath, rom.name));
                break;
            case RetroLauncher.NOT_INSTALLED: {
                final String pkg = r.packageName;
                List<StreamMenuView.Item> items = new ArrayList<>();
                if (pkg != null) {
                    items.add(StreamMenuView.Item.action(getString(R.string.pocket_retro_get_it), () -> openStore(pkg)));
                }
                items.add(StreamMenuView.Item.submenu(getString(R.string.pocket_retro_play_with), () ->
                        pushConsolePage(getString(R.string.pocket_retro_play_with),
                                commandItems(s, null, false, c -> play(rom, c)))));
                PocketSounds.play(this, PocketSounds.OPEN);
                showConsolePanel(cmd != null ? cmd.shortLabel() : rom.name,
                        getString(R.string.pocket_retro_not_installed_sub), null, items);
                break;
            }
            case RetroLauncher.NO_EMULATOR:
                Toast.makeText(this, getString(R.string.pocket_retro_no_emulator_for, s.fullName), Toast.LENGTH_LONG).show();
                break;
            case RetroLauncher.NO_FILE:
                Toast.makeText(this, R.string.pocket_retro_file_gone, Toast.LENGTH_LONG).show();
                load(true);
                break;
            default:
                Toast.makeText(this, getString(R.string.pocket_retro_failed, r.detail), Toast.LENGTH_LONG).show();
                break;
        }
    }

    private void openStore(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            } catch (RuntimeException ignored) {
                Toast.makeText(this, pkg, Toast.LENGTH_LONG).show();
            }
        }
    }

    /** A system's commands, the one in use ticked, the ones not installed marked. */
    private List<StreamMenuView.Item> commandItems(EsdeConfig.SystemDef s, String current, boolean withDefault, final Pick pick) {
        List<StreamMenuView.Item> items = new ArrayList<>();
        if (withDefault) {
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_retro_use_system), () -> pick.onPick(null))
                    .withValue(current == null ? "✓" : null));
        }
        for (final EsdeConfig.Command c : s.commands) {
            if (c.isPlaceholder()) {
                continue;
            }
            String title = c.isRetroArch() ? "RetroArch · " + c.shortLabel() : c.shortLabel();
            String value = c.label.equals(current) ? "✓"
                    : (RetroLauncher.installed(this, cfg, c) ? null : getString(R.string.pocket_retro_not_installed));
            items.add(StreamMenuView.Item.action(title, () -> pick.onPick(c)).withValue(value));
        }
        if (items.isEmpty()) {
            items.add(StreamMenuView.Item.header(getString(R.string.pocket_retro_no_emulator)));
        }
        return items;
    }

    @Override
    protected boolean onButtonX() {
        Entry e = entryAt(heroPos);
        if (e == null || e.kind != KIND_GAME) {
            return false;
        }
        openPage(e);
        return true;
    }

    @Override
    protected boolean onGamePageShortcut() {
        return onButtonX();
    }

    /** START on a game: play it straight away. */
    @Override
    protected boolean onQuickPlay() {
        Entry e = entryAt(heroPos);
        if (e == null || e.kind != KIND_GAME) {
            return false;
        }
        play(e.rom, null);
        return true;
    }

    private void openPage(Entry e) {
        View card = null;
        Bitmap cover = null;
        RecyclerView.LayoutManager lm = carousel.getLayoutManager();
        View frame = lm != null && heroPos >= 0 ? lm.findViewByPosition(heroPos) : null;
        if (frame instanceof ViewGroup && ((ViewGroup) frame).getChildCount() > 0) {
            card = ((ViewGroup) frame).getChildAt(0);
            ImageView image = card.findViewById(R.id.grid_image);
            if (image != null && image.getDrawable() instanceof BitmapDrawable) {
                cover = ((BitmapDrawable) image.getDrawable()).getBitmap();
                card = image;
            }
        }
        EsdeConfig.SystemDef s = cfg.system(e.rom.system);
        GamePageActivity.open(this, card, RetroLibrary.UUID, e.rom.id(), RomScanner.searchName(e.rom.name),
                s != null ? s.fullName : getString(R.string.pocket_retro_title), false, false, cover);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TREE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                File root = RetroLibrary.pathOfTree(data.getData());
                if (root != null && root.isDirectory() && root.canRead()) {
                    RetroLibrary.setRoot(this, root);
                    Toast.makeText(this, getString(R.string.pocket_retro_folder_set, root.getAbsolutePath()),
                            Toast.LENGTH_LONG).show();
                    lib = null;
                    inSystem = null;
                    load(true);
                } else {
                    Toast.makeText(this,R.string.pocket_retro_cant_pick,Toast.LENGTH_LONG).show();
                }
            }
            return;
        }
        if (requestCode == GamePageActivity.REQUEST && data != null
                && GamePageActivity.ACTION_PLAY.equals(data.getStringExtra(GamePageActivity.RESULT_ACTION))) {
            int id = data.getIntExtra(GamePageActivity.RESULT_APP_ID, 0);
            // The surprise game first (it may be in a system not open here)
            if (surprise != null && surprise.id() == id) {
                RomScanner.Rom r = surprise;
                surprise = null;
                play(r, null);
                return;
            }
            for (int i = 0; i < adapter.getCount(); i++) {
                Entry e = adapter.getItem(i);
                if (e.kind == KIND_GAME && e.rom.id() == id) {
                    play(e.rom, null);
                    return;
                }
            }
        }
    }

    @Override
    public void onItemOptions(int position, View itemView) {
        final Entry e = entryAt(position);
        if (e == null) {
            return;
        }
        List<StreamMenuView.Item> items = new ArrayList<>();
        String subtitle;
        if (e.kind == KIND_GAME) {
            final RomScanner.Rom rom = e.rom;
            final EsdeConfig.SystemDef s = cfg.system(rom.system);
            if (s == null) {
                return;
            }
            subtitle = s.fullName;
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_hero_play), () -> play(rom, null)));
            items.add(StreamMenuView.Item.submenu(getString(R.string.pocket_retro_play_with), () ->
                    pushConsolePage(getString(R.string.pocket_retro_play_with), commandItems(s, null, false, c -> {
                        hideConsolePanel();
                        play(rom, c);
                    }))));
            items.add(StreamMenuView.Item.submenu(getString(R.string.pocket_retro_emu_game), () ->
                    pushConsolePage(getString(R.string.pocket_retro_emu_game),
                            commandItems(s, RetroLibrary.gameChoice(this, rom), true, c -> {
                                RetroLibrary.setGameChoice(this, rom, c != null ? c.label : null);
                                hideConsolePanel();
                                heroKey = null;
                                updateHero(heroPos);
                            })))
                    .withValue(emulatorLabel(RetroLibrary.gameCommand(this, lib, s, rom))));
            items.add(StreamMenuView.Item.submenu(getString(R.string.pocket_retro_emu_system, s.fullName), () ->
                    systemEmulatorPage(s)));
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_hint_details), () -> {
                hideConsolePanel();
                openPage(e);
            }));
            items.add(com.limelight.pocket.home.HomePins.item(this, homeItem(rom)));
            final Achievements.Target trophies = Achievements.hasRa(this) && Achievements.consoleId(rom.system) > 0
                    ? Achievements.Target.retro(rom.system, rom.relPath, rom.name) : null;
            if (trophies != null) {
                Achievements.Set known = Achievements.cached(this, trophies);
                items.add(StreamMenuView.Item.action(getString(R.string.pocket_trophy_title), () -> {
                    hideConsolePanel();
                    AchievementsActivity.open(this, trophies, RetroLibrary.UUID, rom.id());
                }).withValue(known != null && known.total > 0 ? known.earned + " / " + known.total : null));
            }
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_rename), () -> {
                hideConsolePanel();
                rename(rom);
            }));
            final boolean fav = RetroLibrary.isFavorite(this, lib, rom);
            items.add(StreamMenuView.Item.action(getString(fav ? R.string.pocket_retro_unfavorite : R.string.pocket_retro_favorite), () -> {
                RetroLibrary.setFavorite(this, rom, !fav);
                PocketSounds.play(this, fav ? PocketSounds.TOGGLE_OFF : PocketSounds.TOGGLE_ON);
                showEntries();
            }));
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_art_change), () -> {
                hideConsolePanel();
                ArtPickerActivity.open(this, RetroLibrary.UUID, rom.id(), RomScanner.searchName(rom.name));
            }));
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_art_find_again), () -> {
                Toast.makeText(this, R.string.pocket_art_finding, Toast.LENGTH_SHORT).show();
                scrape(rom, true);
            }));
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_ss_scrape_one), () -> {
                hideConsolePanel();
                if (!ScreenScraper.ready(this)) {
                    Toast.makeText(this, R.string.pocket_ss_need_dev, Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, R.string.pocket_ss_working, Toast.LENGTH_SHORT).show();
                final android.content.Context app = getApplicationContext();
                new Thread(() -> {
                    String msg;
                    try {
                        int n = ScreenScraper.scrape(app, rom);
                        msg = n > 0 ? getString(R.string.pocket_ss_got, n) : getString(R.string.pocket_ss_none);
                    } catch (ScreenScraper.ScrapeException err) {
                        msg = err.getMessage();
                    }
                    final String m = msg;
                    runOnUiThread(() -> {
                        if (!isFinishing()) {
                            Toast.makeText(this, m, Toast.LENGTH_LONG).show();
                            showEntries();
                        }
                    });
                }, "pocket-ss").start();
            }).withIcon("SEARCH"));
            final String here = inSystem != null ? inSystem : rom.system;
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_retro_scrape_system), () -> scrapeMissing(here)));
            items.add(StreamMenuView.Item.header(rom.file.getAbsolutePath()));
        }
        else if (e.kind == KIND_SYSTEM) {
            final EsdeConfig.SystemDef s = cfg.system(e.system);
            subtitle = getResources().getQuantityString(R.plurals.pocket_retro_games, e.count, e.count);
            if (e.count > 0) {
                items.add(StreamMenuView.Item.action(getString(R.string.pocket_apps_open), () -> enterSystem(e)));
                items.add(StreamMenuView.Item.action(getString(R.string.pocket_retro_scrape_system), () -> scrapeMissing(e.system)));
            }
            if (!e.system.startsWith("@") || SystemArt.enabled(this)) {
                final String searchName = s != null ? s.fullName : e.label;
                items.add(StreamMenuView.Item.action(getString(R.string.pocket_sysart_change), () -> {
                    hideConsolePanel();
                    // SteamGridDB: a cover for the card, a background, a logo (each by itself)
                    ArtPickerActivity.open(this, SystemArt.PICK_UUID, SystemArt.pickId(e.system), searchName);
                }));
            }
            if (s != null) {
                items.add(StreamMenuView.Item.submenu(getString(R.string.pocket_retro_emu_this_system), () -> systemEmulatorPage(s))
                        .withValue(emulatorLabel(RetroLibrary.systemCommand(this, lib, s))));
            }
            addLibraryItems(items);
            if (s != null) {
                items.add(StreamMenuView.Item.header(systemFolder(s.name) + "  ·  " + android.text.TextUtils.join(" ", s.extensions)));
            }
        }
        else {
            subtitle = e.note;
            addLibraryItems(items);
        }
        PocketSounds.play(this, PocketSounds.OPEN);
        showConsolePanel(e.label, subtitle, null, items);
    }

    private void systemEmulatorPage(final EsdeConfig.SystemDef s) {
        pushConsolePage(getString(R.string.pocket_retro_emu_system, s.fullName),
                commandItems(s, RetroLibrary.systemChoice(this, s.name), true, c -> {
                    RetroLibrary.setSystemChoice(this, s.name, c != null ? c.label : null);
                    hideConsolePanel();
                    heroKey = null;
                    updateHero(heroPos);
                }));
    }

    /** The game "Surprise me" picked (played when its page asks to). */
    private RomScanner.Rom surprise;

    /**
     * Surprise me: a game picked at random (from this system, or from all of them), its page
     * opened, ready to play.
     */
    private void surpriseMe() {
        if (lib == null) {
            return;
        }
        List<RomScanner.Rom> pool = new ArrayList<>();
        if (inSystem != null && !inSystem.startsWith("@")) {
            pool.addAll(romsOf(inSystem));
        }
        else {
            for (List<RomScanner.Rom> l : lib.games.values()) {
                pool.addAll(l);
            }
        }
        if (pool.isEmpty()) {
            return;
        }
        hideConsolePanel();
        RomScanner.Rom r = pool.get(new java.util.Random().nextInt(pool.size()));
        surprise = r;
        EsdeConfig.SystemDef s = cfg.system(r.system);
        GamePageActivity.open(this, null, RetroLibrary.UUID, r.id(), RomScanner.searchName(r.name),
                s != null ? s.fullName : getString(R.string.pocket_retro_title), false, false, null);
    }

    /** The ROM folder's settings (in every options menu here). */
    private void addLibraryItems(List<StreamMenuView.Item> items) {
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_search_everything), () -> {
            hideConsolePanel();
            com.limelight.pocket.search.SearchActivity.open(this);
        }));
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_surprise), this::surpriseMe));
        // Covers and backgrounds for every game here that has none (libretro, SteamGridDB...)
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_retro_scrape_all), () -> scrapeMissing(null))
                .withValue(scrapeTotal > 0 && scrapeDone < scrapeTotal ? scrapeDone + " / " + scrapeTotal : null));
        // The game's own short video behind the hero (ES-DE's video snaps), after a moment on it
        items.add(StreamMenuView.Item.value(getString(R.string.pocket_retro_video_snaps),
                getString(snapsOn() ? R.string.pocket_on : R.string.pocket_off), direction -> {
                    boolean now = !snapsOn();
                    PocketPrefs.prefs(this).edit().putBoolean(KEY_SNAPS, now).apply();
                    if (!now) {
                        stopSnap();
                    }
                    return getString(now ? R.string.pocket_on : R.string.pocket_off);
                }));
        // The emulators kept up to date from their own release pages (Obtainium)
        items.add(StreamMenuView.Item.submenu(getString(R.string.pocket_emu_update), () ->
                pushConsolePage(getString(R.string.pocket_emu_update), EmulatorUpdates.items(this))));
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_retro_rescan), () -> {
            Toast.makeText(this, R.string.pocket_retro_looking, Toast.LENGTH_SHORT).show();
            RetroLibrary.reloadConfig();
            load(true);
        }));
        File root = RetroLibrary.romRoot(this);
        items.add(StreamMenuView.Item.action(getString(R.string.horizon_rom_sources),this::showRomSources));
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_retro_pick_folder), () -> {
            hideConsolePanel();
            pickFolder();
        }).withValue(root != null ? root.getAbsolutePath() : null));
        final File target = root != null ? root : RetroLibrary.defaultRoot();
        items.add(StreamMenuView.Item.confirm(getString(R.string.pocket_retro_create_folders), () -> createFolders(target)));
        final boolean empty = RetroLibrary.showEmptySystems(this);
        items.add(StreamMenuView.Item.value(getString(R.string.pocket_retro_show_empty),
                getString(empty ? R.string.pocket_on : R.string.pocket_off), direction -> {
                    boolean now = !RetroLibrary.showEmptySystems(this);
                    RetroLibrary.setShowEmptySystems(this, now);
                    showEntries();
                    return getString(now ? R.string.pocket_on : R.string.pocket_off);
                }));
        items.add(StreamMenuView.Item.value(getString(R.string.pocket_sysart_title), sysArtLabel(), direction -> {
            // Artwork, screenshots, the controller, the circuit board, noir, or none
            if (!SystemArt.enabled(this)) {
                SystemArt.setEnabled(this, true);
                PocketPrefs.prefs(this).edit().putString("pocket_system_art", SystemArt.STYLES[0]).apply();
            }
            else if (SystemArt.style(this).equals(SystemArt.STYLES[SystemArt.STYLES.length - 1])) {
                SystemArt.setEnabled(this, false);
            }
            else {
                SystemArt.nextStyle(this);
            }
            PocketSounds.play(this, PocketSounds.NAV);
            BACKDROPS.evictAll();
            adapter.notifyDataSetChanged();
            heroKey = null;
            if (heroPos >= 0) {
                updateHero(heroPos);
            }
            return sysArtLabel();
        }));
        com.limelight.pocket.ui.LibraryLayout.addSettings(this, items);
        items.add(StreamMenuView.Item.header(getString(R.string.pocket_retro_tip)));
        items.add(StreamMenuView.Item.header(getString(R.string.pocket_sysart_credit)));
    }

    @Override
    protected boolean onShoulder(int direction) {
        return SectionBar.step(this, SectionBar.RETRO, direction);
    }

    /** L2 / R2 in a system: the system before / after it. */
    @Override
    protected boolean onTrigger(int direction) {
        if (inSystem == null || lib == null) {
            TextView section = findViewById(R.id.pocketSectionTitle);
            if (section != null) {
                PocketMotion.pop(section, 0.96f);
            }
            return true;
        }
        List<Entry> systems = systemEntries();
        int at = -1;
        for (int i = 0; i < systems.size(); i++) {
            if (systems.get(i).system.equals(inSystem)) {
                at = i;
            }
        }
        for (int step = 1; step <= systems.size(); step++) {
            Entry next = systems.get(((at + direction * step) % systems.size() + systems.size()) % systems.size());
            if (next.count > 0) {
                systemsFocus = next.key;
                heroKey = null;
                PocketSounds.play(this, PocketSounds.NAV);
                if (rebuildFor(next.system)) {
                    return true;
                }
                inSystem = next.system;
                showEntries();
                TextView section = findViewById(R.id.pocketSectionTitle);
                if (section != null) {
                    PocketMotion.bump(section);
                }
                return true;
            }
        }
        return true;
    }

    // ---- Cards ------------------------------------------------------------------------------

    private static final Map<String, String> SHORT = new HashMap<>();
    private static final Map<String, Integer> COLORS = new HashMap<>();

    static {
        String[][] s = {
                {"nes", "NES", "#C62828"}, {"famicom", "FC", "#B71C1C"}, {"snes", "SNES", "#5E35B1"},
                {"sfc", "SFC", "#6A1B9A"}, {"n64", "N64", "#2E7D32"}, {"gc", "GC", "#4527A0"},
                {"wii", "Wii", "#0288D1"}, {"wiiu", "Wii U", "#00838F"}, {"switch", "Switch", "#D32F2F"},
                {"gb", "GB", "#689F38"}, {"gbc", "GBC", "#7B1FA2"}, {"gba", "GBA", "#3949AB"},
                {"nds", "DS", "#546E7A"}, {"n3ds", "3DS", "#C62828"}, {"virtualboy", "VB", "#B71C1C"},
                {"psx", "PS1", "#616161"}, {"ps2", "PS2", "#1565C0"}, {"ps3", "PS3", "#263238"},
                {"ps4", "PS4", "#0D47A1"}, {"psp", "PSP", "#212121"}, {"psvita", "Vita", "#1E88E5"},
                {"megadrive", "MD", "#263238"}, {"genesis", "GEN", "#263238"}, {"mastersystem", "SMS", "#C62828"},
                {"gamegear", "GG", "#37474F"}, {"segacd", "SCD", "#455A64"}, {"sega32x", "32X", "#BF360C"},
                {"saturn", "Saturn", "#455A64"}, {"dreamcast", "DC", "#EF6C00"}, {"arcade", "Arcade", "#F9A825"},
                {"mame", "MAME", "#F57F17"}, {"fbneo", "FBNeo", "#E65100"}, {"neogeo", "Neo Geo", "#B8860B"},
                {"neogeocd", "NGCD", "#8D6E63"}, {"ngp", "NGP", "#4E342E"}, {"ngpc", "NGPC", "#5D4037"},
                {"pcengine", "PCE", "#E53935"}, {"tg16", "TG16", "#EF6C00"}, {"pcenginecd", "PCE CD", "#AD1457"},
                {"atari2600", "2600", "#6D4C41"}, {"atari5200", "5200", "#5D4037"}, {"atari7800", "7800", "#4E342E"},
                {"atarilynx", "Lynx", "#F57C00"}, {"atarijaguar", "Jaguar", "#B71C1C"}, {"wonderswan", "WS", "#00796B"},
                {"wonderswancolor", "WSC", "#00897B"}, {"xbox", "Xbox", "#2E7D32"}, {"xbox360", "360", "#43A047"},
                {"dos", "DOS", "#37474F"}, {"pc", "PC", "#37474F"}, {"scummvm", "ScummVM", "#558B2F"},
                {"ports", "Ports", "#00695C"}, {"msx", "MSX", "#1565C0"}, {"amiga", "Amiga", "#E64A19"},
                {"c64", "C64", "#5D4037"}, {"3do", "3DO", "#424242"}, {"pokemini", "Mini", "#C2185B"},
                {"androidapps", "Apps", "#388E3C"}, {"androidgames", "Games", "#43A047"}, {"steam", "Steam", "#1B2838"},
        };
        for (String[] r : s) {
            SHORT.put(r[0], r[1]);
            COLORS.put(r[0], Color.parseColor(r[2]));
        }
    }

    public static String shortName(String system, String fullName) {
        String s = SHORT.get(system);
        if (s != null) {
            return s;
        }
        return system.length() <= 6 ? system.toUpperCase(Locale.ROOT) : fullName.split(" ")[0];
    }

    private static int colorOf(String system) {
        Integer c = COLORS.get(system);
        if (c != null) {
            return c;
        }
        float hue = (system.hashCode() & 0xFFFF) % 360;
        return Color.HSVToColor(new float[] {hue, 0.55f, 0.55f});
    }

    private static int darker(int color, float f) {
        return Color.rgb(Math.round(Color.red(color) * f), Math.round(Color.green(color) * f), Math.round(Color.blue(color) * f));
    }

    /** A system's (or a setup step's) card: its short name big on its colour. */
    private Bitmap drawCard(String big, String small, String corner, int color, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, w * 0.3f, h, darker(color, 1.0f), darker(color, 0.28f), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(w * 0.85f, h * 0.12f, w * 0.9f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, glow);
        // A thin rule, like a cartridge label
        Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setColor(0x33FFFFFF);
        c.drawRect(w * 0.12f, h * 0.62f, w * 0.88f, h * 0.62f + Math.max(1, h / 160f), rule);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFFFFFFFF);
        text.setTypeface(com.limelight.pocket.ui.PocketFonts.bold());
        text.setTextAlign(Paint.Align.CENTER);
        float size = h * 0.2f;
        text.setTextSize(size);
        while (text.measureText(big) > w * 0.82f && size > h * 0.06f) {
            size *= 0.92f;
            text.setTextSize(size);
        }
        text.setShadowLayer(h * 0.03f, 0, h * 0.01f, 0x66000000);
        c.drawText(big, w / 2f, h * 0.48f, text);

        if (small != null) {
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setColor(0xD9FFFFFF);
            sp.setTypeface(com.limelight.pocket.ui.PocketFonts.medium());
            sp.setTextAlign(Paint.Align.CENTER);
            sp.setTextSize(h * 0.062f);
            drawWrapped(c, small, sp, w / 2f, h * 0.72f, w * 0.84f, 2);
        }
        if (corner != null) {
            Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
            cp.setTextSize(h * 0.062f);
            cp.setTypeface(com.limelight.pocket.ui.PocketFonts.semibold());
            float tw = cp.measureText(corner);
            float pad = h * 0.03f;
            RectF pill = new RectF(w - tw - pad * 4, pad * 2, w - pad * 2, pad * 2 + cp.getTextSize() + pad * 1.6f);
            Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG);
            pb.setColor(0x59000000);
            c.drawRoundRect(pill, pill.height() / 2, pill.height() / 2, pb);
            cp.setColor(0xFFFFFFFF);
            c.drawText(corner, pill.left + pad, pill.bottom - pad * 1.1f, cp);
        }
        return b;
    }

    private static void drawWrapped(Canvas c, String text, Paint p, float cx, float top, float maxW, int maxLines) {
        String[] words = text.split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String next = line.length() == 0 ? w : line + " " + w;
            if (p.measureText(next) > maxW && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(w);
            }
            else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        float y = top + p.getTextSize();
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            String l = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) {
                l = l + "…";
            }
            c.drawText(l, cx, y, p);
            y += p.getTextSize() * 1.25f;
        }
    }

    private Bitmap cardFor(Entry e, int w, int h) {
        String key = e.key + "#" + e.count;
        Bitmap cached = CARDS.get(key);
        if (cached != null) {
            return cached;
        }
        Bitmap b;
        if (e.kind == KIND_SYSTEM) {
            if (SYS_RECENT.equals(e.system)) {
                b = drawCard("↻", e.label, String.valueOf(e.count), 0xFF37474F, w, h);
            }
            else if (SYS_FAVS.equals(e.system)) {
                b = drawCard("★", e.label, String.valueOf(e.count), 0xFFF9A825, w, h);
            }
            else if (SYS_SUGGEST.equals(e.system)) {
                b = drawCard("✦", e.label, String.valueOf(e.count), 0xFF7E57C2, w, h);
            }
            else {
                EsdeConfig.SystemDef s = cfg.system(e.system);
                String full = s != null ? s.fullName : e.label;
                b = drawCard(shortName(e.system, full), full, String.valueOf(e.count),
                        e.count > 0 ? colorOf(e.system) : 0xFF2A2A30, w, h);
            }
        }
        else {
            b = drawCard("+", e.label, null, 0xFF34495E, w, h);
        }
        CARDS.put(key, b);
        return b;
    }

    private final class Cards extends BaseAdapter {
        private final List<Entry> entries = new ArrayList<>();

        void set(List<Entry> list) {
            entries.clear();
            entries.addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Entry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return entries.get(position).key.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View card = convertView != null ? convertView
                    : LayoutInflater.from(RetroActivity.this).inflate(R.layout.app_grid_item, parent, false);
            final Entry e = entries.get(position);
            final ImageView image = card.findViewById(R.id.grid_image);
            final TextView text = card.findViewById(R.id.grid_text);
            View overlay = card.findViewById(R.id.grid_overlay);
            if (overlay != null) {
                overlay.setVisibility(View.GONE);
            }
            card.setContentDescription(e.label);
            final String tag = e.key + "|" + e.count + "|" + PocketArt.version() + "|" + SystemArt.style(RetroActivity.this);
            if (tag.equals(image.getTag()) && image.getDrawable() != null) {
                return card;
            }
            image.setTag(tag);
            final float d = getResources().getDisplayMetrics().density;
            final int w = Math.round(108 * d);
            final int h = Math.round(162 * d);
            if (e.kind != KIND_GAME) {
                // Drawn here (no text under it: the card says it all)
                text.setVisibility(View.GONE);
                final String style = SystemArt.style(RetroActivity.this);
                // Recently played / favourites are drawn from their games: a new first one, a new card
                final List<RomScanner.Rom> collection = e.kind == KIND_SYSTEM && e.system != null && e.system.startsWith("@")
                        ? romsOf(e.system) : null;
                String lead = collection != null && !collection.isEmpty() ? "|" + collection.get(0).key() : "";
                final String artKey = "art|" + e.key + "#" + e.count + "|" + style + "|" + PocketArt.version() + lead;
                Bitmap art = e.kind == KIND_SYSTEM ? CARDS.get(artKey) : null;
                if (art != null) {
                    image.setImageBitmap(art);
                    return card;
                }
                image.setImageBitmap(cardFor(e, w, h));
                final boolean themeArt = SystemArt.enabled(RetroActivity.this);
                if (e.kind == KIND_SYSTEM && (e.count > 0 || e.system.startsWith("@")) && (themeArt || collection != null)) {
                    // The console's own art, once it's here (downloaded the first time)
                    final String system = e.system;
                    final int color = collectionColor(system) != 0 ? collectionColor(system) : colorOf(system);
                    final String count = String.valueOf(e.count);
                    final boolean ready = SystemArt.has(RetroActivity.this, system, style);
                    final String collectionName = e.label;
                    systemWork.execute(() -> {
                        // A cover picked on SteamGridDB for the console wins over its theme art
                        Bitmap own = SystemArt.picked(RetroActivity.this, system, PocketArt.COVER, w);
                        Bitmap fan = null;
                        if (own == null && collection != null) {
                            // Its own games' covers, fanned out (the newest in front)
                            List<Bitmap> covers = new ArrayList<>();
                            for (RomScanner.Rom r : collection) {
                                if (covers.size() >= 3) {
                                    break;
                                }
                                Bitmap c = SystemArt.decodeCover(PocketArt.file(RetroActivity.this, RetroLibrary.UUID,
                                        r.id(), PocketArt.COVER), Math.max(64, w / 2));
                                if (c != null) {
                                    covers.add(c);
                                }
                            }
                            fan = SystemArt.collageCard(covers, collectionName, count,
                                    com.limelight.pocket.ui.PocketTheme.accent(RetroActivity.this), w, h);
                        }
                        final Bitmap made = own != null ? SystemArt.pictureCard(own, count, w, h)
                                : fan != null ? fan
                                : themeArt ? SystemArt.card(RetroActivity.this, system, style, color, count, w, h) : null;
                        if (made == null) {
                            return;
                        }
                        CARDS.put(artKey, made);
                        main.post(() -> {
                            if (tag.equals(image.getTag())) {
                                image.setImageBitmap(made);
                                if (!ready && PocketPrefs.animationsEnabled(RetroActivity.this)) {
                                    image.setAlpha(0f);
                                    image.animate().alpha(1f).setDuration(260).start();
                                }
                            }
                        });
                    });
                }
                return card;
            }
            text.setText(e.label);
            final int id = e.rom.id();
            final String name = e.rom.name;
            Bitmap own = HeroArt.cachedCard(RetroActivity.this, RetroLibrary.UUID, id, name);
            if (own != null) {
                image.setImageBitmap(own);
                text.setVisibility(View.GONE);
                return card;
            }
            image.setImageBitmap(null);
            text.setVisibility(View.GONE);
            work.execute(() -> {
                Bitmap cover = HeroArt.loadCard(RetroActivity.this, RetroLibrary.UUID, id, name);
                final boolean real = cover != null;
                Bitmap stand = null;
                if (!real && SystemArt.enabled(RetroActivity.this)) {
                    // No cover yet: the game's name on its console's art (with the console's logo)
                    stand = SystemArt.gameCard(RetroActivity.this, e.rom.system, RomScanner.searchName(name),
                            colorOf(e.rom.system), w, h);
                }
                final Bitmap made = real ? cover : stand != null ? stand : PlaceholderArt.render(RomScanner.searchName(name), 300);
                main.post(() -> {
                    if (made != null && tag.equals(image.getTag())) {
                        boolean fresh = image.getDrawable() == null;
                        image.setImageBitmap(made);
                        if (fresh && PocketPrefs.animationsEnabled(RetroActivity.this)) {
                            image.setAlpha(0f);
                            image.animate().alpha(1f).setDuration(200).start();
                        }
                    }
                });
            });
            return card;
        }
    }
}
