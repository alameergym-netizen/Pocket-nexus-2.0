package com.limelight.pocket.clients;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.PocketUi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clients and sources: every entry of the project registry with how Pocket Nexus really relates
 * to it (built in, your installed app, an official site, on your PC, research only or
 * unverified), whether it is installed here and which version, and what Pocket Nexus can know
 * about a session with it. Read-only: the only actions open an official page or Android's own
 * app details screen, each by an explicit press.
 */
public class ClientsActivity extends ConsoleActivity {
    private static final String STATE_SELECTED = "horizon_clients_selected";

    private final Map<String, String> installed = new HashMap<>();
    private final Map<String, String> versions = new HashMap<>();
    private final Map<String, View> rowById = new HashMap<>();
    private final Map<String, TextView> stateById = new HashMap<>();
    /** U05: apps the Library's games wait for (name → games), from its readiness check. */
    private final Map<String, Integer> waiting = new java.util.LinkedHashMap<>();
    private TextView needed;
    /** The waiting line costs the list no rows: beside the title when wide, in the intro's place when short. */
    private TextView intro;
    private boolean compact;
    /** Where the focus goes once the window has it (after any touch-mode change on arrival). */
    private View initialFocus;
    private boolean scanned;
    private String selectedId;

    private LinearLayout rows;
    private TextView detailName, detailState, detailMode, detailSession, detailResume, detailNote, detailPackage;
    private TextView detailCan, detailFallback;
    private LinearLayout actions;

    public static void open(Context context) {
        context.startActivity(new Intent(context, ClientsActivity.class));
    }

    @Override
    protected boolean allowsAutoResume() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectedId = savedInstanceState != null ? savedInstanceState.getString(STATE_SELECTED) : null;
        if (HorizonClientRegistry.byId(selectedId == null ? "" : selectedId) == null) {
            selectedId = HorizonClientRegistry.ALL.get(0).id;
        }
        build();
        scan();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_SELECTED, selectedId);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // An app may have been installed or removed (App info, the store) while away
        if (scanned) {
            scan();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Opened with a button: the base screen has just left touch mode and focused the first
        // row; the selected client is where the focus belongs
        View target = initialFocus;
        if (hasFocus && target != null) {
            initialFocus = null;
            if (!getWindow().getDecorView().isInTouchMode() && target.isAttachedToWindow()) {
                target.requestFocus();
            }
        }
    }

    /** Finds which registry clients are installed here, off the main thread. */
    private void scan() {
        final Context app = getApplicationContext();
        new Thread(() -> {
            Map<String, String> found = new HashMap<>();
            Map<String, String> foundVersions = new HashMap<>();
            PackageManager pm = app.getPackageManager();
            Set<String> packages = new HashSet<>();
            try {
                Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
                    if (ri.activityInfo != null) {
                        packages.add(ri.activityInfo.packageName);
                    }
                }
            } catch (RuntimeException unavailable) {
                packages.clear();
            }
            for (HorizonClientRegistry.Client c : HorizonClientRegistry.ALL) {
                if (c.mode != HorizonClientRegistry.Mode.INSTALLED_APP) {
                    continue;
                }
                String pkg = c.installedPackage(packages);
                if (pkg == null) {
                    continue;
                }
                found.put(c.id, pkg);
                try {
                    String v = pm.getPackageInfo(pkg, 0).versionName;
                    if (v != null && !v.trim().isEmpty()) {
                        foundVersions.put(c.id, v.trim());
                    }
                } catch (PackageManager.NameNotFoundException | RuntimeException gone) {
                    found.remove(c.id);
                }
            }
            // U05: which apps the Library's games are waiting for (the same check as its cards)
            java.util.List<String> missing = new java.util.ArrayList<>();
            try {
                for (com.limelight.pocket.library.UniversalLibrary.Game g
                        : com.limelight.pocket.library.UniversalLibrary.gather(app)) {
                    if (g.ready() == com.limelight.pocket.library.HorizonLibraryReadiness.Ready.APP_MISSING) {
                        missing.add(g.missingApp());
                    }
                }
            } catch (RuntimeException unreadable) {
                missing.clear();
            }
            final Map<String, Integer> wait = com.limelight.pocket.library.HorizonLibraryReadiness.waiting(missing);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                waiting.clear();
                waiting.putAll(wait);
                showNeeded();
                installed.clear();
                installed.putAll(found);
                versions.clear();
                versions.putAll(foundVersions);
                scanned = true;
                // Update the labels in place: rebuilding the rows would drop the focus
                for (HorizonClientRegistry.Client c : HorizonClientRegistry.ALL) {
                    updateRowState(c);
                }
                showDetails(HorizonClientRegistry.byId(selectedId));
            });
        }, "nexus-clients-scan").start();
    }

    // ---- Screen -----------------------------------------------------------------------------

    private void build() {
        float density = getResources().getDisplayMetrics().density;
        float widthDp = getResources().getDisplayMetrics().widthPixels / density;
        float heightDp = getResources().getDisplayMetrics().heightPixels / density;
        float fontScale = getResources().getConfiguration().fontScale;
        boolean split = widthDp >= 600 && heightDp >= 320;
        int pad = widthDp >= 600 ? 20 : 12;
        int listWidth = Math.max(250, Math.min(440, Math.round(widthDp * (fontScale > 1.15f ? 0.46f : 0.42f))));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF08090C);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(pad), dp(12), dp(pad), dp(10));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = text(getString(R.string.horizon_clients_title), 19, 0xFFFFFFFF, true);
        ViewCompat.setAccessibilityHeading(title, true);
        // U05: on a wide screen the waiting line sits beside the title, costing the list no rows
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(titleRow);
        compact = !split && heightDp < 420;
        intro = text(getString(R.string.horizon_clients_intro), 11, 0xA3FFFFFF, false);
        intro.setPadding(0, dp(2), 0, dp(heightDp < 420 ? 6 : 10));
        intro.setMaxLines(heightDp < 420 ? 1 : 3);
        intro.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(intro);
        needed = text("", 11, 0xFFF5B841, true);
        needed.setId(R.id.horizon_clients_needed);
        needed.setMaxLines(1);
        needed.setEllipsize(TextUtils.TruncateAt.END);
        needed.setVisibility(View.GONE);
        if (split) {
            needed.setGravity(Gravity.END);
            needed.setPadding(dp(16), 0, 0, 0);
            titleRow.addView(needed, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            needed.setPadding(0, 0, 0, dp(6));
            column.addView(needed);
        }
        showNeeded();

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(split ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        column.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(2), dp(2), dp(2), dp(8));
        listScroll.addView(rows, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout detail = detailPane();
        if (split) {
            body.addView(listScroll, new LinearLayout.LayoutParams(dp(Math.min(listWidth, Math.round(widthDp) - 2 * pad - 240)),
                    ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            dlp.setMarginStart(dp(16));
            body.addView(detail, dlp);
        }
        else {
            body.addView(detail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.round(heightDp * 0.45f * density)));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            llp.topMargin = dp(8);
            body.addView(listScroll, llp);
        }

        LinearLayout hints = new LinearLayout(this);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        hints.setBackgroundResource(R.drawable.pocket_glass_pill);
        hints.setPadding(dp(2), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.END;
        hlp.topMargin = dp(6);
        column.addView(hints, hlp);
        fillHintBar(this, hints, hintA(this, R.string.pocket_hint_select), hintB(this, R.string.pocket_hint_close));

        setContentView(root);
        buildRows();
        HorizonClientRegistry.Client selected = HorizonClientRegistry.byId(selectedId);
        showDetails(selected);
        View target = rowById.get(selectedId);
        if (target != null) {
            // Keep touch mode when opened by touch (Settings would otherwise move its focus to
            // its first tab on return); the first D-pad press then lands on this row
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                target.setFocusedByDefault(true);
            }
            // Only on arrival; a rebuild while the window has focus just moves the focus
            initialFocus = hasWindowFocus() ? null : target;
            target.post(target::requestFocus);
        }
    }

    private LinearLayout detailPane() {
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setBackgroundResource(R.drawable.pocket_panel_bg);
        pane.setPadding(dp(16), dp(14), dp(16), dp(12));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        // Never a D-pad stop: moving right from the list goes straight to the actions
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(texts, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        pane.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        detailName = text("", 17, 0xFFFFFFFF, true);
        texts.addView(detailName);
        detailState = text("", 12, PocketTheme.accent(this), true);
        detailState.setPadding(0, dp(2), 0, dp(8));
        detailState.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        texts.addView(detailState);
        detailMode = paragraph(texts);
        detailSession = paragraph(texts);
        detailResume = paragraph(texts);
        detailCan = paragraph(texts);
        detailCan.setId(R.id.horizon_clients_capabilities);
        detailFallback = paragraph(texts);
        detailFallback.setId(R.id.horizon_clients_fallback);
        detailNote = paragraph(texts);
        detailPackage = text("", 10.5f, 0x8CFFFFFF, false);
        detailPackage.setPadding(0, dp(2), 0, 0);
        texts.addView(detailPackage);

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(8);
        pane.addView(actions, alp);
        return pane;
    }

    private TextView paragraph(LinearLayout parent) {
        TextView t = text("", 12, 0xD9FFFFFF, false);
        t.setPadding(0, 0, 0, dp(6));
        parent.addView(t);
        return t;
    }

    // ---- The list ---------------------------------------------------------------------------

    private static final HorizonClientRegistry.Group[] GROUPS = HorizonClientRegistry.Group.values();

    private static int groupLabel(HorizonClientRegistry.Group g) {
        switch (g) {
            case PC_STREAMING:
                return R.string.horizon_clients_group_pc_streaming;
            case CLOUD:
                return R.string.horizon_clients_group_cloud;
            case CONSOLE:
                return R.string.horizon_clients_group_console;
            case LOCAL_PC:
                return R.string.horizon_clients_group_local_pc;
            case EMULATION:
                return R.string.horizon_clients_group_emulation;
            case MEDIA:
                return R.string.horizon_clients_group_media;
            case PC_HOST:
                return R.string.horizon_clients_group_pc_host;
            default:
                return R.string.horizon_clients_group_sources;
        }
    }

    private void buildRows() {
        rows.removeAllViews();
        rowById.clear();
        stateById.clear();
        for (HorizonClientRegistry.Group g : GROUPS) {
            List<HorizonClientRegistry.Client> clients = HorizonClientRegistry.inGroup(g);
            if (clients.isEmpty()) {
                continue;
            }
            TextView h = text(getString(groupLabel(g)).toUpperCase(java.util.Locale.getDefault()), 9.5f,
                    0xA3FFFFFF, true);
            h.setLetterSpacing(0.16f);
            h.setPadding(dp(4), dp(rows.getChildCount() == 0 ? 2 : 12), 0, dp(6));
            ViewCompat.setAccessibilityHeading(h, true);
            rows.addView(h);
            for (HorizonClientRegistry.Client c : clients) {
                row(c);
            }
        }
    }

    private void row(HorizonClientRegistry.Client c) {
        LinearLayout row = new LinearLayout(this);
        row.setTag(c.id);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPaddingRelative(dp(12), dp(7), dp(12), dp(7));
        row.setBackground(PocketTheme.row(this));
        row.setFocusable(true);
        row.setClickable(true);
        TextView name = text(c.name, 13.5f, 0, false);
        name.setTextColor(ContextCompat.getColorStateList(this, R.color.pocket_row_text));
        name.setDuplicateParentStateEnabled(true);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String state = state(c);
        TextView value = text(state, 11, 0, true);
        value.setTextColor(ContextCompat.getColorStateList(this, R.color.pocket_row_text_dim));
        value.setDuplicateParentStateEnabled(true);
        value.setPaddingRelative(dp(8), 0, 0, 0);
        value.setMaxWidth(dp(170));
        row.addView(value);
        stateById.put(c.id, value);
        row.setContentDescription(c.name + ", " + state);
        row.setOnFocusChangeListener((v, has) -> {
            if (has) {
                PocketUi.tick(this);
                showDetails(c);
            }
        });
        row.setOnClickListener(v -> {
            PocketSounds.play(this, PocketSounds.SELECT);
            showDetails(c);
            if (actions.getChildCount() > 1) {
                actions.getChildAt(0).requestFocus();
            }
        });
        row.setOnKeyListener(this::confirmKey);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(5);
        rows.addView(row, lp);
        rowById.put(c.id, row);
    }

    private void updateRowState(HorizonClientRegistry.Client c) {
        TextView value = stateById.get(c.id);
        View row = rowById.get(c.id);
        if (value == null || row == null) {
            return;
        }
        String state = state(c);
        value.setText(state);
        row.setContentDescription(c.name + ", " + state);
    }

    // ---- Details ----------------------------------------------------------------------------

    /** U05: "Your Library is waiting for RetroArch (3), DuckStation (1)", or nothing. */
    private void showNeeded() {
        if (needed == null) return;
        if (waiting.isEmpty()) {
            needed.setVisibility(View.GONE);
            if (intro != null) {
                intro.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (intro != null) {
            // Narrow and short: the waiting line takes the intro's place; otherwise the intro stays
            intro.setVisibility(compact ? View.GONE : View.VISIBLE);
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> e : waiting.entrySet()) {
            parts.add(getString(R.string.horizon_clients_needed_item, e.getKey(), e.getValue()));
        }
        needed.setText(getString(R.string.horizon_clients_needed, TextUtils.join(", ", parts)));
        needed.setVisibility(View.VISIBLE);
    }

    /** How many Library games wait for this client (0: none, or it is installed). */
    private int waitingFor(HorizonClientRegistry.Client c) {
        for (Map.Entry<String, Integer> e : waiting.entrySet()) {
            if (e.getKey().equalsIgnoreCase(c.name)) return e.getValue();
        }
        return 0;
    }

    private String state(HorizonClientRegistry.Client c) {
        if (c.mode == HorizonClientRegistry.Mode.INSTALLED_APP && !scanned) {
            return getString(R.string.horizon_clients_checking);
        }
        switch (HorizonClientRegistry.availability(c, installed.containsKey(c.id))) {
            case BUILT_IN:
                return getString(R.string.horizon_clients_state_built_in);
            case INSTALLED:
                String v = versions.get(c.id);
                return v != null ? getString(R.string.horizon_clients_state_installed_version, v)
                        : getString(R.string.horizon_clients_state_installed);
            case NOT_INSTALLED:
                int games = waitingFor(c);
                return games > 0 ? getResources().getQuantityString(R.plurals.horizon_clients_state_missing_needed,
                        games, games) : getString(R.string.horizon_clients_state_missing);
            case WEB:
                return getString(R.string.horizon_clients_state_web);
            case ON_PC:
                return getString(R.string.horizon_clients_state_pc);
            case RESEARCH_ONLY:
                return getString(R.string.horizon_clients_state_research);
            default:
                return getString(R.string.horizon_clients_state_unverified);
        }
    }

    private void showDetails(HorizonClientRegistry.Client c) {
        if (c == null || detailName == null) {
            return;
        }
        selectedId = c.id;
        detailName.setText(c.name);
        detailState.setText(state(c));
        detailMode.setText(modeText(c.mode));
        detailSession.setText(sessionText(c.session));
        boolean runs = c.mode == HorizonClientRegistry.Mode.BUILT_IN || c.mode == HorizonClientRegistry.Mode.INSTALLED_APP
                || c.mode == HorizonClientRegistry.Mode.OFFICIAL_WEB;
        boolean game = c.group != HorizonClientRegistry.Group.MEDIA && c.group != HorizonClientRegistry.Group.SOURCES;
        show(detailResume, runs && game ? getString(c.resume ? R.string.horizon_clients_resume_yes
                : R.string.horizon_clients_resume_no) : null);
        show(detailCan, capabilitiesText(c));
        show(detailFallback, fallbackText(c));
        int note = note(c);
        show(detailNote, note != 0 ? getString(note) : null);
        String pkg = installed.get(c.id);
        if (pkg != null) {
            show(detailPackage, getString(R.string.horizon_clients_package, pkg));
        }
        else if (!c.packages().isEmpty()) {
            show(detailPackage, getString(R.string.horizon_clients_packages_expected, TextUtils.join(", ", c.packages())));
        }
        else {
            show(detailPackage, null);
        }
        buildActions(c, pkg);
    }

    /** X08: what Pocket Nexus does with it, in the registry's order. */
    private String capabilitiesText(HorizonClientRegistry.Client c) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (HorizonClientRegistry.Capability cap : c.capabilities()) {
            parts.add(getString(capabilityLabel(cap)));
        }
        return parts.isEmpty() ? getString(R.string.horizon_clients_can_nothing)
                : getString(R.string.horizon_clients_can, TextUtils.join(" · ", parts));
    }

    private static int capabilityLabel(HorizonClientRegistry.Capability cap) {
        switch (cap) {
            case OPENS: return R.string.horizon_clients_can_opens;
            case STARTS_GAMES: return R.string.horizon_clients_can_starts_games;
            case CONDITIONAL_GAME_LINK: return R.string.horizon_clients_can_game_link;
            case LIBRARY: return R.string.horizon_clients_can_library;
            case SEES_SESSION: return R.string.horizon_clients_can_sees_session;
            case RESUMES: return R.string.horizon_clients_can_resumes;
            default: return R.string.horizon_clients_can_connects;
        }
    }

    /**
     * X08: describe a possible alternative, not a selected dispatch route. An absent
     * installed-app client is identified as missing. Null: no named alternative.
     */
    private String fallbackText(HorizonClientRegistry.Client c) {
        HorizonClientRegistry.Client f = c.fallback() != null ? HorizonClientRegistry.byId(c.fallback()) : null;
        if (f == null) {
            return null;
        }
        boolean missing = c.mode == HorizonClientRegistry.Mode.INSTALLED_APP && scanned && !installed.containsKey(c.id);
        return getString(missing ? R.string.horizon_clients_fallback_using : R.string.horizon_clients_fallback, f.name);
    }

    private void buildActions(HorizonClientRegistry.Client c, String pkg) {
        actions.removeAllViews();
        if (c.link != null) {
            boolean official = c.mode == HorizonClientRegistry.Mode.BUILT_IN
                    || c.mode == HorizonClientRegistry.Mode.INSTALLED_APP || c.mode == HorizonClientRegistry.Mode.OFFICIAL_WEB;
            action(getString(official ? R.string.horizon_clients_open_official : R.string.horizon_clients_open_project),
                    () -> openLink(c.link));
        }
        if (pkg != null) {
            action(getString(R.string.horizon_clients_app_info), () -> openAppInfo(pkg));
        }
        action(getString(R.string.horizon_clients_close), this::finish);
    }

    private void action(String label, Runnable run) {
        TextView b = text(label, 12.5f, 0, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(44));
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(PocketTheme.row(this));
        b.setTextColor(ContextCompat.getColorStateList(this, R.color.pocket_row_text));
        b.setOnClickListener(v -> {
            PocketSounds.play(this, PocketSounds.SELECT);
            run.run();
        });
        b.setOnKeyListener(this::confirmKey);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (actions.getChildCount() > 0) {
            lp.setMarginStart(dp(8));
        }
        actions.addView(b, lp);
    }

    private void openLink(String link) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.horizon_clients_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.horizon_clients_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private int modeText(HorizonClientRegistry.Mode mode) {
        switch (mode) {
            case BUILT_IN:
                return R.string.horizon_clients_mode_built_in;
            case INSTALLED_APP:
                return R.string.horizon_clients_mode_app;
            case OFFICIAL_WEB:
                return R.string.horizon_clients_mode_web;
            case PC_HOST:
                return R.string.horizon_clients_mode_pc;
            case RESEARCH:
                return R.string.horizon_clients_mode_research;
            default:
                return R.string.horizon_clients_mode_unresolved;
        }
    }

    private int sessionText(HorizonClientRegistry.Session session) {
        switch (session) {
            case OBSERVED:
                return R.string.horizon_clients_session_observed;
            case PROVIDER_MANAGED:
                return R.string.horizon_clients_session_provider;
            case OPENED_ONLY:
                return R.string.horizon_clients_session_opened;
            case MEDIA_CONTROLS:
                return R.string.horizon_clients_session_media;
            default:
                return R.string.horizon_clients_session_none;
        }
    }

    private static int note(HorizonClientRegistry.Client c) {
        switch (c.id) {
            case "nexus-pc-stream":
                return R.string.horizon_clients_note_nexus_pc_stream;
            case "xcloud-player":
            case "gfn-player":
            case "boosteroid-player":
                return R.string.horizon_clients_note_player;
            case "better-xcloud":
                return R.string.horizon_clients_note_better_xcloud;
            case "romm":
                return R.string.horizon_clients_note_romm;
            case "obtainium":
                return R.string.horizon_clients_note_obtainium;
            case "vibepollo":
                return R.string.horizon_clients_note_vibepollo;
            case "playnite":
                return R.string.horizon_clients_note_playnite;
            case "ludusavi":
                return R.string.horizon_clients_note_ludusavi;
            default:
                return 0;
        }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private static void show(TextView view, String value) {
        view.setText(value == null ? "" : value);
        view.setVisibility(value == null ? View.GONE : View.VISIBLE);
    }

    private TextView text(String value, float sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (color != 0) {
            t.setTextColor(color);
        }
        t.setTypeface(medium ? PocketFonts.medium() : PocketFonts.regular());
        return t;
    }

    /** A on a focused row or button presses it once (the same as a tap). */
    private boolean confirmKey(View view, int code, KeyEvent event) {
        if (code != KeyEvent.KEYCODE_BUTTON_A) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            view.performClick();
        }
        return true;
    }
}
