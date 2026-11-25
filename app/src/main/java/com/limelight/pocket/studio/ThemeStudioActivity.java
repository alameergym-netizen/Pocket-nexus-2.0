package com.limelight.pocket.studio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
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

import androidx.core.view.ViewCompat;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.HorizonLook;
import com.limelight.pocket.ui.HorizonThemeStudio;
import com.limelight.pocket.ui.Layouts;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.PocketUi;

import java.util.HashMap;
import java.util.Map;

/**
 * Theme Studio: every theme preset, the three custom looks and the default, previewed on Home,
 * Library and Control before anything changes. Moving the focus to a look previews it; A (or a
 * tap) takes you to Apply; B or Close leaves without applying. Applying remembers the look it
 * replaced, so it can be brought back. Looks can be copied and pasted as plain text.
 */
public class ThemeStudioActivity extends ConsoleActivity {
    private static final String STATE_PREVIEW = "horizon_studio_preview";
    private static final String STATE_NAME = "horizon_studio_name";
    private static final String STATE_SCENE = "horizon_studio_scene";
    private static final String STATE_FOCUS = "horizon_studio_focus";
    private static final String KEY_APPLY = "apply";
    private static final String KEY_CLOSE = "close";
    /** Presets with their own Horizon names come first. */
    private static final int HORIZON_FIRST = 15;

    private HorizonLook applied;
    private HorizonLook preview;
    private String previewName;
    private int scene = ThemePreviewView.HOME;
    private String focusKey;
    private String lastLookKey;
    private int armedClear = -1;

    private ThemePreviewView previewView;
    private TextView status;
    private TextView summary;
    private TextView applyButton;
    private LinearLayout rows;
    private final TextView[] sceneTabs = new TextView[3];
    private final Map<String, View> rowByKey = new HashMap<>();
    /** Where the focus goes once the window has it (after any touch-mode change on arrival). */
    private View initialFocus;

    public static void open(Context context) {
        context.startActivity(new Intent(context, ThemeStudioActivity.class));
    }

    @Override
    protected boolean allowsAutoResume() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applied = HorizonThemeStudio.current(this);
        preview = applied;
        if (savedInstanceState != null) {
            try {
                preview = HorizonLook.parse(savedInstanceState.getString(STATE_PREVIEW));
            } catch (HorizonLook.Invalid e) {
                preview = applied;
            }
            previewName = savedInstanceState.getString(STATE_NAME);
            scene = savedInstanceState.getInt(STATE_SCENE, ThemePreviewView.HOME);
            focusKey = savedInstanceState.getString(STATE_FOCUS);
        }
        build();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_PREVIEW, preview.toText());
        out.putString(STATE_NAME, previewName);
        out.putInt(STATE_SCENE, scene);
        View focused = getCurrentFocus();
        out.putString(STATE_FOCUS, focused != null && focused.getTag() instanceof String
                ? (String) focused.getTag() : focusKey);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) {
            focusKey = (String) focused.getTag();
        }
        build();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Opened with a button: the base screen has just left touch mode and focused the first
        // row; the applied look is where the focus belongs
        View target = initialFocus;
        if (hasFocus && target != null) {
            initialFocus = null;
            if (!getWindow().getDecorView().isInTouchMode() && target.isAttachedToWindow()) {
                target.requestFocus();
            }
        }
    }

    // ---- Screen -----------------------------------------------------------------------------

    private void build() {
        float density = getResources().getDisplayMetrics().density;
        float widthDp = getResources().getDisplayMetrics().widthPixels / density;
        float heightDp = getResources().getDisplayMetrics().heightPixels / density;
        float fontScale = getResources().getConfiguration().fontScale;
        HorizonStudioLayout layout = HorizonStudioLayout.forWindow(widthDp, heightDp, fontScale);
        rowByKey.clear();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF08090C);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(layout.padding), dp(12), dp(layout.padding), dp(10));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = text(getString(R.string.horizon_studio_title), 19, 0xFFFFFFFF, true);
        ViewCompat.setAccessibilityHeading(title, true);
        column.addView(title);
        TextView intro = text(getString(R.string.horizon_studio_intro), 11, 0xA3FFFFFF, false);
        intro.setPadding(0, dp(2), 0, dp(heightDp < 420 ? 6 : 10));
        // Short screens keep one line; the full sentence stays available to screen readers
        intro.setMaxLines(heightDp < 420 ? 1 : 3);
        intro.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(intro);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(layout.split ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        column.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        listScroll.setClipToPadding(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(2), dp(2), dp(2), dp(8));
        listScroll.addView(rows, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout previewPane = previewColumn();

        if (layout.split) {
            body.addView(listScroll, new LinearLayout.LayoutParams(dp(layout.listWidth),
                    ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            plp.setMarginStart(dp(16));
            body.addView(previewPane, plp);
        }
        else {
            // The picture, its texts and the actions above the list
            int paneHeight = Math.min(dp(layout.previewHeight + 140), Math.round(heightDp * 0.62f * density));
            body.addView(previewPane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, paneHeight));
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
        fillHintBar(this, hints, hintA(this, R.string.pocket_hint_select), hintB(this, R.string.pocket_hint_close),
                hintShoulders(this, R.string.horizon_studio_hint_scene));

        summary.setMaxLines(heightDp < 420 ? 2 : 3);
        setContentView(root);
        buildRows();
        previewView.setLook(preview, false);
        previewView.setScene(scene, false);
        updateScenes();
        updatePreviewText();
        String want = focusKey != null && rowByKey.containsKey(focusKey) ? focusKey : appliedKey();
        View target = rowByKey.get(want);
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

    /**
     * Scene tabs, the picture, what it shows and the two actions. The picture takes whatever
     * height is left, so Apply and Close stay on screen with large text on a short display.
     */
    private LinearLayout previewColumn() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        int[] labels = {R.string.horizon_studio_scene_home, R.string.horizon_studio_scene_library,
                R.string.horizon_studio_scene_control};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tab = text(getString(labels[i]), 12, 0xFFFFFFFF, true);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(dp(32));
            tab.setMinWidth(dp(72));
            tab.setPadding(dp(14), dp(4), dp(14), dp(4));
            tab.setFocusable(false);
            tab.setClickable(true);
            tab.setOnClickListener(v -> showScene(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            tabs.addView(tab, lp);
            sceneTabs[i] = tab;
        }
        col.addView(tabs);

        previewView = new ThemePreviewView(this);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        vlp.topMargin = dp(8);
        col.addView(previewView, vlp);

        status = text("", 13.5f, 0xFFFFFFFF, true);
        status.setPadding(0, dp(8), 0, 0);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        col.addView(status);
        summary = text("", 11, 0xA3FFFFFF, false);
        summary.setPadding(0, dp(2), 0, dp(8));
        summary.setMaxLines(3);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(summary);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        applyButton = button(KEY_APPLY, getString(R.string.horizon_studio_apply_button), this::applyPreview);
        actions.addView(applyButton);
        TextView close = button(KEY_CLOSE, getString(R.string.horizon_studio_cancel_button), this::finish);
        LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) close.getLayoutParams();
        clp.setMarginStart(dp(8));
        actions.addView(close);
        col.addView(actions);
        return col;
    }

    private TextView button(String key, String label, Runnable action) {
        TextView b = text(label, 13, 0, true);
        b.setTag(key);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(44));
        b.setPadding(dp(18), dp(6), dp(18), dp(6));
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(pill());
        b.setTextColor(rowText(false));
        b.setOnClickListener(v -> {
            PocketSounds.play(this, PocketSounds.SELECT);
            action.run();
        });
        b.setOnFocusChangeListener((v, has) -> {
            if (has) {
                PocketUi.tick(this);
            }
        });
        b.setOnKeyListener(this::confirmKey);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rowByKey.put(key, b);
        return b;
    }

    // ---- The list ---------------------------------------------------------------------------

    private void buildRows() {
        rows.removeAllViews();
        View apply = rowByKey.get(KEY_APPLY);
        View close = rowByKey.get(KEY_CLOSE);
        rowByKey.clear();
        rowByKey.put(KEY_APPLY, apply);
        rowByKey.put(KEY_CLOSE, close);
        int appliedPreset = HorizonThemeStudio.presetIndex(applied);

        header(R.string.horizon_studio_section_horizon);
        for (int i = HORIZON_FIRST; i < PocketTheme.THEMES.length; i++) {
            presetRow(i, appliedPreset);
        }
        header(R.string.horizon_studio_section_classic);
        for (int i = 0; i < HORIZON_FIRST; i++) {
            presetRow(i, appliedPreset);
        }

        header(R.string.horizon_studio_section_mine);
        for (int i = 0; i < HorizonThemeStudio.slots(); i++) {
            final int slot = i;
            final HorizonLook saved = HorizonThemeStudio.slot(this, i);
            final String name = getString(R.string.horizon_studio_slot, i + 1);
            String value = saved == null ? getString(R.string.horizon_studio_empty)
                    : saved.equals(applied) ? getString(R.string.horizon_studio_applied_badge) : null;
            if (saved != null) {
                lookRow("slot:" + i, name, value, saved.accent, () -> saved, name);
            }
            else {
                actionRow("slot:" + i, name, value, () -> focus("save:" + slot));
            }
            actionRow("save:" + i, getString(saved == null ? R.string.horizon_studio_save_slot
                    : R.string.horizon_studio_replace_slot, i + 1), null, () -> saveSlot(slot));
            if (saved != null) {
                actionRow("clear:" + i, getString(armedClear == i ? R.string.horizon_studio_clear_confirm
                        : R.string.horizon_studio_clear_slot, i + 1), null, () -> clearSlot(slot));
            }
        }

        header(R.string.horizon_studio_section_tools);
        final String defaultName = getString(R.string.horizon_studio_default);
        lookRow("default", defaultName, getString(R.string.horizon_studio_default_sub),
                HorizonLook.defaults().accent, HorizonLook::defaults, defaultName);
        final HorizonLook undo = HorizonThemeStudio.undo(this);
        if (undo != null) {
            final String undoName = getString(R.string.horizon_studio_undo);
            lookRow("undo", undoName, getString(R.string.horizon_studio_undo_sub), undo.accent, () -> undo, undoName);
        }
        actionRow("export", getString(R.string.horizon_studio_export), getString(R.string.horizon_studio_export_sub),
                this::exportPreview);
        actionRow("import", getString(R.string.horizon_studio_import), getString(R.string.horizon_studio_import_sub),
                this::importLook);
    }

    private interface LookSource {
        HorizonLook get();
    }

    private void presetRow(int index, int appliedPreset) {
        final String name = getString(PocketTheme.THEMES[index].name);
        lookRow("preset:" + index, name, index == appliedPreset ? getString(R.string.horizon_studio_applied_badge) : null,
                PocketTheme.THEMES[index].accent, () -> HorizonThemeStudio.preset(applied, index), name);
    }

    private void lookRow(String key, String title, String value, int swatch, LookSource source, String name) {
        LinearLayout row = row(key, title, value, swatch);
        row.setOnFocusChangeListener((v, has) -> {
            if (has) {
                PocketUi.tick(this);
                lastLookKey = key;
                disarmClear(key);
                showPreview(source.get(), name);
            }
        });
        row.setOnClickListener(v -> {
            PocketSounds.play(this, PocketSounds.SELECT);
            lastLookKey = key;
            showPreview(source.get(), name);
            applyButton.requestFocus();
        });
    }

    private void actionRow(String key, String title, String value, Runnable action) {
        LinearLayout row = row(key, title, value, null);
        row.setOnFocusChangeListener((v, has) -> {
            if (has) {
                PocketUi.tick(this);
                disarmClear(key);
            }
        });
        row.setOnClickListener(v -> {
            PocketSounds.play(this, PocketSounds.SELECT);
            action.run();
        });
    }

    private LinearLayout row(String key, String title, String value, Integer swatch) {
        LinearLayout row = new LinearLayout(this);
        row.setTag(key);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPaddingRelative(dp(12), dp(7), dp(12), dp(7));
        row.setBackground(PocketTheme.row(this));
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnKeyListener(this::confirmKey);
        if (swatch != null) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(swatch);
            g.setStroke(Math.max(1, dp(1)), 0x80FFFFFF);
            dot.setBackground(g);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(14), dp(14));
            dlp.setMarginEnd(dp(10));
            row.addView(dot, dlp);
        }
        TextView t = text(title, 13.5f, 0, false);
        t.setTextColor(rowText(false));
        t.setDuplicateParentStateEnabled(true);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) {
            TextView v = text(value, 11, 0, true);
            v.setTextColor(rowText(true));
            v.setDuplicateParentStateEnabled(true);
            v.setPaddingRelative(dp(8), 0, 0, 0);
            v.setMaxWidth(dp(170));
            row.addView(v);
        }
        row.setContentDescription(value == null ? title : title + ", " + value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(5);
        rows.addView(row, lp);
        rowByKey.put(key, row);
        return row;
    }

    private void header(int label) {
        TextView h = text(getString(label).toUpperCase(java.util.Locale.getDefault()), 9.5f, 0xA3FFFFFF, true);
        h.setLetterSpacing(0.16f);
        h.setPadding(dp(4), dp(rows.getChildCount() == 0 ? 2 : 12), 0, dp(6));
        ViewCompat.setAccessibilityHeading(h, true);
        rows.addView(h);
    }

    // ---- Actions ----------------------------------------------------------------------------

    private void showPreview(HorizonLook look, String name) {
        if (look == null) {
            return;
        }
        preview = look;
        previewName = name;
        previewView.setLook(look, animate());
        updatePreviewText();
    }

    private void showScene(int index) {
        scene = Math.max(ThemePreviewView.HOME, Math.min(ThemePreviewView.CONTROL, index));
        previewView.setScene(scene, animate());
        updateScenes();
        updatePreviewText();
    }

    @Override
    protected boolean onShoulder(int direction) {
        PocketUi.tick(this);
        showScene((scene + direction + 3) % 3);
        return true;
    }

    private void applyPreview() {
        if (preview.equals(applied)) {
            Toast.makeText(this, R.string.horizon_studio_already, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!HorizonThemeStudio.apply(this, preview)) {
            Toast.makeText(this, R.string.horizon_studio_error, Toast.LENGTH_SHORT).show();
            return;
        }
        PocketSounds.reload(this);
        applied = HorizonThemeStudio.current(this);
        preview = applied;
        Toast.makeText(this, R.string.horizon_studio_done, Toast.LENGTH_SHORT).show();
        focusKey = lastLookKey;
        // The studio's own rows and buttons take the new accent
        build();
    }

    private void saveSlot(int slot) {
        boolean ok = HorizonThemeStudio.saveSlot(this, slot, preview);
        Toast.makeText(this, ok ? R.string.horizon_studio_saved : R.string.horizon_studio_error,
                Toast.LENGTH_SHORT).show();
        rebuildRows(ok ? "slot:" + slot : "save:" + slot);
    }

    private void clearSlot(int slot) {
        if (armedClear != slot) {
            armedClear = slot;
            rebuildRows("clear:" + slot);
            return;
        }
        armedClear = -1;
        HorizonThemeStudio.clearSlot(this, slot);
        Toast.makeText(this, R.string.horizon_studio_cleared, Toast.LENGTH_SHORT).show();
        rebuildRows("save:" + slot);
    }

    private void disarmClear(String key) {
        if (armedClear >= 0 && !("clear:" + armedClear).equals(key)) {
            int was = armedClear;
            armedClear = -1;
            View row = rowByKey.get("clear:" + was);
            if (row instanceof LinearLayout) {
                for (int i = 0; i < ((LinearLayout) row).getChildCount(); i++) {
                    View child = ((LinearLayout) row).getChildAt(i);
                    if (child instanceof TextView) {
                        String label = getString(R.string.horizon_studio_clear_slot, was + 1);
                        ((TextView) child).setText(label);
                        row.setContentDescription(label);
                        break;
                    }
                }
            }
        }
    }

    private void exportPreview() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, R.string.horizon_studio_error, Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.horizon_studio_clip_label), preview.toText()));
        Toast.makeText(this, R.string.horizon_studio_exported, Toast.LENGTH_SHORT).show();
    }

    private void importLook() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        CharSequence text = null;
        try {
            ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
            if (clip != null && clip.getItemCount() > 0) {
                text = clip.getItemAt(0).getText();
            }
        } catch (RuntimeException unavailable) {
            text = null;
        }
        try {
            HorizonLook look = HorizonLook.parse(text == null ? null : text.toString());
            lastLookKey = "import";
            showPreview(look, getString(R.string.horizon_studio_imported));
            applyButton.requestFocus();
        } catch (HorizonLook.Invalid e) {
            int message = e.reason == HorizonLook.Invalid.UNSUPPORTED
                    ? R.string.horizon_studio_import_unsupported : R.string.horizon_studio_import_missing;
            status.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void rebuildRows(String focus) {
        buildRows();
        focus(focus);
    }

    private void focus(String key) {
        View v = rowByKey.get(key);
        if (v != null) {
            v.requestFocus();
        }
    }

    private String appliedKey() {
        int index = HorizonThemeStudio.presetIndex(applied);
        return index >= 0 ? "preset:" + index : "preset:" + HORIZON_FIRST;
    }

    // ---- Text -------------------------------------------------------------------------------

    private void updatePreviewText() {
        boolean same = preview.equals(applied);
        String name = previewName != null ? previewName : getString(R.string.horizon_studio_custom_mix);
        status.setText(same ? getString(R.string.horizon_studio_showing_applied)
                : getString(R.string.horizon_studio_previewing, name));
        String line = describe(preview);
        if (HorizonThemeStudio.ownLayouts(preview)) {
            line += "\n" + getString(R.string.horizon_studio_own_layouts);
        }
        summary.setText(line);
        applyButton.setAlpha(same ? 0.55f : 1f);
        int preset = HorizonThemeStudio.presetIndex(preview);
        String shown = !same ? name : preset >= 0 ? getString(PocketTheme.THEMES[preset].name)
                : getString(R.string.horizon_studio_custom_mix);
        previewView.setContentDescription(getString(R.string.horizon_studio_preview_desc,
                sceneTabs[scene].getText(), shown, line));
    }

    private void updateScenes() {
        for (int i = 0; i < sceneTabs.length; i++) {
            boolean on = i == scene;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(100));
            g.setColor(on ? 0x2EFFFFFF : 0x00000000);
            g.setStroke(Math.max(1, dp(1)), on ? 0x4DFFFFFF : 0x1FFFFFFF);
            sceneTabs[i].setBackground(g);
            sceneTabs[i].setSelected(on);
            sceneTabs[i].setTextColor(on ? 0xFFFFFFFF : 0x99FFFFFF);
        }
    }

    String describe(HorizonLook l) {
        int backdrop;
        switch (l.backdrop) {
            case "art":
                backdrop = R.string.pocket_backdrop_art;
                break;
            case "glow":
                backdrop = R.string.pocket_backdrop_glow;
                break;
            case "black":
                backdrop = R.string.pocket_backdrop_black;
                break;
            default:
                backdrop = R.string.pocket_backdrop_ambient;
                break;
        }
        int material = l.frost() ? R.string.pocket_set_frost : R.string.horizon_studio_material_glass;
        int[] sounds = {R.string.pocket_sound_glass, R.string.pocket_sound_soft, R.string.pocket_sound_crisp,
                R.string.pocket_sound_retro, R.string.pocket_sound_pop, R.string.pocket_sound_aurora,
                R.string.pocket_sound_crystal, R.string.pocket_sound_tactile};
        int sound = sounds[Math.max(0, java.util.Arrays.asList(PocketPrefs.SOUND_STYLES).indexOf(l.sound))];
        int layout = R.string.pocket_layout_tiles;
        for (Layouts.Layout candidate : Layouts.ALL) {
            if (candidate.id.equals(l.layout)) {
                layout = candidate.label;
            }
        }
        int focus = "ring".equals(l.focus) ? R.string.pocket_focus_ring
                : "zoom".equals(l.focus) ? R.string.pocket_focus_zoom : R.string.pocket_focus_ring_zoom;
        return getString(R.string.horizon_studio_summary, getString(backdrop), getString(material),
                getString(sound), getString(layout), getString(focus))
                + " · " + getString(l.motion ? R.string.horizon_studio_motion_on : R.string.horizon_studio_motion_off);
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

    private ColorStateList rowText(boolean dim) {
        int on = HorizonStudioLayout.textOn(applied.accent);
        int focused = dim ? (on & 0x00FFFFFF) | 0xB3000000 : on;
        int normal = dim ? 0x99FFFFFF : 0xFFFFFFFF;
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focused, normal});
    }

    private StateListDrawable pill() {
        float r = dp(100);
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(r);
        focused.setColor(applied.accent);
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(r);
        normal.setColor(0x1FFFFFFF);
        normal.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
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

    private boolean animate() {
        return PocketPrefs.animationsEnabled(this) && android.provider.Settings.Global.getFloat(getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }
}
