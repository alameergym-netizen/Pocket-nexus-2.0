package com.limelight.pocket.ui.headphones;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Cell;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Level;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Mode;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Phase;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.State;

/**
 * The Headphones Center's face: the earbuds' name and connection, left / case / right battery,
 * the three noise modes (Awareness, Off, ANC) and, one step away, the four ANC levels.
 *
 * <p>Built for a handheld in landscape (two panes from 520 dp wide, one column below), the
 * D-pad (rows left to right, then down) and touch alike. It takes the accent and corners of the
 * current look, so it fits every Nexus theme. It never talks to Bluetooth: {@link #bind} draws a
 * {@link State}; {@link Listener} reports what the user asked for.
 */
public final class HeadphonesCenterView extends LinearLayout {
    /** What the user asked for; the owner of the transport acts on it. */
    public interface Listener {
        void onMode(Mode mode);

        void onLevel(Level level);

        void onRefresh();

        void onChooseDevice();

        void onBluetoothSettings();

        void onAllowNearbyDevices();
    }

    private static final Mode[] MODES = {Mode.AWARENESS, Mode.OFF, Mode.ANC};
    private static final Level[] LEVELS = {Level.ADAPTIVE, Level.LIGHT, Level.BALANCED, Level.DEEP};
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xB3FFFFFF;
    private static final int AMBER = 0xFFF5B841;
    private static final int RED = 0xFFFF6B6B;

    private final boolean wide;
    private final boolean square;
    private final Typeface face;
    private final int accent;
    private Listener listener;
    private State state = State.detecting();
    private boolean levelsOpen;

    private final TextView device;
    private final TextView status;
    private final View statusDot;
    private final BatteryCell left;
    private final BatteryCell box;
    private final BatteryCell right;
    private final TextView[] modes = new TextView[MODES.length];
    private final TextView levelsToggle;
    private final LinearLayout levelStrip;
    private final TextView[] levels = new TextView[LEVELS.length];
    private final TextView allow;
    private final TextView refresh;
    private final TextView choose;
    private final TextView bluetooth;
    private final TextView freshness;

    public HeadphonesCenterView(Context context) {
        this(context, context.getResources().getConfiguration().screenWidthDp >= 520);
    }

    public HeadphonesCenterView(Context context, boolean wide) {
        super(context);
        this.wide = wide;
        this.square = PocketTheme.legacy(context);
        this.face = square || PocketTheme.titanium(context) ? Typeface.MONOSPACE : Typeface.DEFAULT_BOLD;
        int a = PocketPrefs.accentColor(context);
        this.accent = com.limelight.pocket.ui.HorizonLook.readableAccent(a) ? a : WHITE;
        setOrientation(wide ? HORIZONTAL : VERTICAL);
        setPadding(dp(4), dp(4), dp(4), dp(4));

        // ── The device: name, connection, batteries ─────────────────────────────────────
        LinearLayout info = new LinearLayout(context);
        info.setOrientation(VERTICAL);
        info.setPadding(dp(14), dp(12), dp(14), dp(12));
        info.setBackground(panel());
        device = text(18, WHITE, true);
        device.setId(R.id.horizon_hp_device);
        device.setMaxLines(1);
        device.setEllipsize(TextUtils.TruncateAt.END);
        ViewCompat.setAccessibilityHeading(device, true);
        info.addView(device);

        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(4), 0, dp(10));
        statusDot = new View(context);
        statusRow.addView(statusDot, new LayoutParams(dp(8), dp(8)));
        status = text(12, MUTED, false);
        status.setId(R.id.horizon_hp_status);
        status.setPadding(dp(8), 0, 0, 0);
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        // A screen reader hears connection changes without moving focus
        ViewCompat.setAccessibilityLiveRegion(status, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
        statusRow.addView(status, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(statusRow);

        LinearLayout cells = new LinearLayout(context);
        cells.setOrientation(HORIZONTAL);
        left = new BatteryCell(context, R.string.horizon_hp_left, R.id.horizon_hp_left);
        box = new BatteryCell(context, R.string.horizon_hp_case, R.id.horizon_hp_case);
        right = new BatteryCell(context, R.string.horizon_hp_right, R.id.horizon_hp_right);
        for (BatteryCell c : new BatteryCell[]{left, box, right}) {
            LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (c != left) lp.setMarginStart(dp(8));
            cells.addView(c, lp);
        }
        info.addView(cells);

        // ── Noise control: three modes, then the levels one step away ───────────────────
        LinearLayout noise = new LinearLayout(context);
        noise.setOrientation(VERTICAL);
        noise.setPadding(dp(14), dp(12), dp(14), dp(12));
        noise.setBackground(panel());
        TextView heading = text(12, MUTED, true);
        heading.setText(R.string.horizon_hp_noise);
        heading.setAllCaps(true);
        heading.setLetterSpacing(0.08f);
        ViewCompat.setAccessibilityHeading(heading, true);
        noise.addView(heading);

        LinearLayout modeRow = new LinearLayout(context);
        modeRow.setPadding(0, dp(8), 0, 0);
        int[] modeIds = {R.id.horizon_hp_mode_awareness, R.id.horizon_hp_mode_off, R.id.horizon_hp_mode_anc};
        for (int i = 0; i < MODES.length; i++) {
            final Mode m = MODES[i];
            TextView b = segment(modeIds[i], modeName(m), 15);
            b.setOnClickListener(v -> {
                if (state.controllable() && !state.active(m) && listener != null) listener.onMode(m);
            });
            LayoutParams lp = new LayoutParams(0, dp(56), 1f);
            if (i > 0) lp.setMarginStart(dp(6));
            modeRow.addView(b, lp);
            modes[i] = b;
        }
        noise.addView(modeRow);

        levelsToggle = segment(R.id.horizon_hp_levels, "", 13);
        levelsToggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        levelsToggle.setPadding(dp(14), 0, dp(14), 0);
        levelsToggle.setOnClickListener(v -> {
            levelsOpen = !levelsOpen;
            render();
            if (levelsOpen) focusLevel();
        });
        LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        tlp.topMargin = dp(8);
        noise.addView(levelsToggle, tlp);

        levelStrip = new LinearLayout(context);
        int[] levelIds = {R.id.horizon_hp_level_adaptive, R.id.horizon_hp_level_light,
                R.id.horizon_hp_level_balanced, R.id.horizon_hp_level_deep};
        for (int i = 0; i < LEVELS.length; i++) {
            final Level l = LEVELS[i];
            TextView b = segment(levelIds[i], levelName(l), 13);
            b.setOnClickListener(v -> {
                if (state.controllable() && !state.active(l) && listener != null) listener.onLevel(l);
            });
            LayoutParams lp = new LayoutParams(0, dp(44), 1f);
            if (i > 0) lp.setMarginStart(dp(6));
            levelStrip.addView(b, lp);
            levels[i] = b;
        }
        LayoutParams slp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(6);
        noise.addView(levelStrip, slp);

        LinearLayout actions = new LinearLayout(context);
        actions.setPadding(0, dp(10), 0, 0);
        allow = action(R.id.horizon_hp_allow, R.string.horizon_hp_allow, () -> listener.onAllowNearbyDevices());
        refresh = action(R.id.horizon_hp_refresh, R.string.horizon_hp_refresh, () -> listener.onRefresh());
        choose = action(R.id.horizon_hp_choose, R.string.horizon_hp_choose, () -> listener.onChooseDevice());
        bluetooth = action(R.id.horizon_hp_bluetooth, R.string.horizon_hp_bluetooth,
                () -> listener.onBluetoothSettings());
        for (TextView b : new TextView[]{allow, refresh, choose, bluetooth}) {
            LayoutParams lp = new LayoutParams(0, dp(40), 1f);
            if (b != allow) lp.setMarginStart(dp(6));
            actions.addView(b, lp);
        }
        noise.addView(actions);
        freshness = text(11, MUTED, false);
        freshness.setId(R.id.horizon_hp_freshness);
        freshness.setPadding(0, dp(6), 0, 0);
        freshness.setMaxLines(1);
        freshness.setEllipsize(TextUtils.TruncateAt.END);
        noise.addView(freshness);

        if (wide) {
            addView(info, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.42f));
            LayoutParams nlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f);
            nlp.setMarginStart(dp(10));
            addView(noise, nlp);
        } else {
            addView(info, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LayoutParams nlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(10);
            addView(noise, nlp);
        }
        render();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public State state() {
        return state;
    }

    public boolean levelsOpen() {
        return levelsOpen;
    }

    /** Draws a new state; focus stays where it is (only text and marks change). */
    public void bind(State next) {
        state = next == null ? State.detecting() : next;
        render();
    }

    private void render() {
        Context c = getContext();
        device.setText(state.device == null ? c.getString(R.string.horizon_hp_no_device) : state.device);
        status.setText(statusText());
        int dot = statusColor();
        statusDot.setBackground(round(dot, dp(4)));
        status.setTextColor(dot == accent ? WHITE : dot);

        left.bind(state.left, state.phase != Phase.LIVE && state.phase != Phase.APPLYING);
        box.bind(state.box, state.phase != Phase.LIVE && state.phase != Phase.APPLYING);
        right.bind(state.right, state.phase != Phase.LIVE && state.phase != Phase.APPLYING);

        boolean can = state.controllable();
        for (int i = 0; i < MODES.length; i++) {
            Mode m = MODES[i];
            boolean on = state.active(m);
            boolean pending = state.pending == m;
            mark(modes[i], modeName(m), on, pending, can || on || pending,
                    m == Mode.ANC && on && state.level == null ? c.getString(R.string.horizon_hp_anc_earbud) : null);
        }

        String levelValue = state.active(Mode.ANC) && state.level != null ? levelName(state.level)
                : state.active(Mode.ANC) ? c.getString(R.string.horizon_hp_anc_earbud) : "—";
        levelsToggle.setText(c.getString(levelsOpen ? R.string.horizon_hp_levels_open : R.string.horizon_hp_levels,
                levelValue));
        levelsToggle.setContentDescription(c.getString(R.string.horizon_hp_levels_cd, levelValue));
        ViewCompat.setStateDescription(levelsToggle, c.getString(levelsOpen
                ? R.string.horizon_hp_expanded : R.string.horizon_hp_collapsed));
        levelStrip.setVisibility(levelsOpen ? VISIBLE : GONE);
        for (int i = 0; i < LEVELS.length; i++) {
            Level l = LEVELS[i];
            boolean on = state.active(l);
            boolean pending = state.pending == l;
            mark(levels[i], levelName(l), on, pending, can || on || pending, null);
        }

        allow.setVisibility(state.phase == Phase.PERMISSION ? VISIBLE : GONE);
        refresh.setVisibility(state.phase == Phase.PERMISSION || state.phase == Phase.UNSUPPORTED ? GONE : VISIBLE);
        choose.setVisibility(state.phase == Phase.PERMISSION || state.phase == Phase.UNSUPPORTED ? GONE : VISIBLE);
        refresh.setEnabled(state.phase != Phase.READING && state.phase != Phase.APPLYING
                && state.phase != Phase.DETECTING);
        refresh.setAlpha(refresh.isEnabled() ? 1f : 0.45f);
        showFreshness();
    }

    private void showFreshness() {
        Context c = getContext();
        long age = HeadphonesCenterModel.ageSeconds(state, SystemClock.elapsedRealtime());
        if (state.phase == Phase.PAUSED) {
            freshness.setText(R.string.horizon_hp_fresh_paused);
        } else if (age < 0) {
            freshness.setText(R.string.horizon_hp_fresh_none);
        } else if (age < 5) {
            freshness.setText(R.string.horizon_hp_fresh_now);
        } else {
            freshness.setText(c.getString(R.string.horizon_hp_fresh_ago, age < 60 ? age + " s" : (age / 60) + " min"));
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showFreshness();
            postDelayed(this, 1000);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(tick);
        postDelayed(tick, 1000);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    /** Opening the levels puts focus on the active one (else the first), so A then picks. */
    private void focusLevel() {
        for (int i = 0; i < LEVELS.length; i++) {
            if (state.active(LEVELS[i])) {
                levels[i].requestFocus();
                return;
            }
        }
        levels[0].requestFocus();
    }

    private String statusText() {
        Context c = getContext();
        switch (state.phase) {
            case UNSUPPORTED: return c.getString(R.string.horizon_hp_phase_unsupported);
            case PERMISSION: return c.getString(R.string.horizon_hp_phase_permission);
            case NOT_PAIRED: return c.getString(R.string.horizon_hp_phase_not_paired);
            case CHOOSE: return c.getString(R.string.horizon_hp_phase_choose);
            case READING: return c.getString(R.string.horizon_hp_phase_reading);
            case LIVE: return c.getString(R.string.horizon_hp_phase_live);
            case APPLYING: return c.getString(R.string.horizon_hp_phase_applying,
                    state.pending instanceof Mode ? modeName((Mode) state.pending)
                            : state.pending instanceof Level ? levelName((Level) state.pending) : "");
            case UNVERIFIED: return c.getString(R.string.horizon_hp_phase_unverified);
            case UNREACHABLE: return c.getString(R.string.horizon_hp_phase_unreachable);
            case PAUSED: return c.getString(R.string.horizon_hp_phase_paused);
            case DETECTING:
            default: return c.getString(R.string.horizon_hp_phase_detecting);
        }
    }

    private int statusColor() {
        switch (state.phase) {
            case LIVE:
            case APPLYING:
                return accent;
            case PERMISSION:
            case NOT_PAIRED:
            case UNVERIFIED:
            case UNREACHABLE:
            case UNSUPPORTED:
                return AMBER;
            default:
                return MUTED;
        }
    }

    /** One segment's look and words: active (from the earbuds), being applied, or waiting. */
    private void mark(TextView b, String name, boolean on, boolean pending, boolean usable, String detail) {
        Context c = getContext();
        b.setSelected(on);
        b.setActivated(pending);
        b.setText(detail == null ? name : name + "\n" + detail);
        b.setAlpha(usable ? 1f : 0.45f);
        String why = on ? c.getString(R.string.horizon_hp_state_active)
                : pending ? c.getString(R.string.horizon_hp_state_applying)
                : state.verified() ? (state.controllable() ? c.getString(R.string.horizon_hp_state_off)
                : c.getString(R.string.horizon_hp_state_wait))
                : c.getString(R.string.horizon_hp_state_unread);
        b.setContentDescription(name);
        ViewCompat.setStateDescription(b, why);
    }

    // ── Pieces ──────────────────────────────────────────────────────────────────────────

    private TextView segment(int id, String label, int sp) {
        TextView b = text(sp, WHITE, true);
        b.setId(id);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setMaxLines(2);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(segmentBackground());
        b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_selected}, {}},
                new int[]{0xFF000000, WHITE}));
        return b;
    }

    private TextView action(int id, int label, Runnable run) {
        TextView b = segment(id, getContext().getString(label), 12);
        b.setMaxLines(1);
        b.setOnClickListener(v -> {
            if (listener != null && v.isEnabled()) run.run();
        });
        return b;
    }

    /**
     * Idle: faint glass. Active (selected): filled with the accent, black text (an accent always
     * has 5:1 against black, by the Studio's readability rule). Applying (activated): an accent
     * outline. Focused: a white ring on top, so focus and the active mode never look alike.
     */
    private Drawable segmentBackground() {
        int r = square ? 0 : dp(12);
        int ring = Math.max(2, dp(2));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused, android.R.attr.state_selected},
                shape(r, accent, ring, WHITE));
        s.addState(new int[]{android.R.attr.state_focused}, shape(r, 0x33FFFFFF, ring, WHITE));
        s.addState(new int[]{android.R.attr.state_pressed}, shape(r, 0x40FFFFFF, 0, 0));
        s.addState(new int[]{android.R.attr.state_selected}, shape(r, accent, 0, 0));
        s.addState(new int[]{android.R.attr.state_activated}, shape(r, 0x14FFFFFF, ring, accent));
        s.addState(new int[]{}, shape(r, 0x14FFFFFF, Math.max(1, dp(1) / 2 + 1), 0x1FFFFFFF));
        if (PocketPrefs.animationsEnabled(getContext())) {
            s.setEnterFadeDuration(140);
            s.setExitFadeDuration(180);
        }
        return s;
    }

    private Drawable panel() {
        return shape(square ? 0 : dp(18), 0x8C0B0D12, Math.max(1, dp(1) / 2 + 1), 0x1AFFFFFF);
    }

    private static GradientDrawable shape(int radius, int fill, int stroke, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radius);
        d.setColor(fill);
        if (stroke > 0) d.setStroke(stroke, strokeColor);
        return d;
    }

    private static GradientDrawable round(int color, int radius) {
        return shape(radius, color, 0, 0);
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(bold ? face : (face == Typeface.MONOSPACE ? Typeface.MONOSPACE : Typeface.DEFAULT));
        t.setIncludeFontPadding(false);
        return t;
    }

    private String modeName(Mode m) {
        switch (m) {
            case AWARENESS: return getContext().getString(R.string.horizon_hp_mode_awareness);
            case OFF: return getContext().getString(R.string.horizon_hp_mode_off);
            case ANC:
            default: return getContext().getString(R.string.horizon_hp_mode_anc);
        }
    }

    private String levelName(Level l) {
        switch (l) {
            case ADAPTIVE: return getContext().getString(R.string.horizon_hp_level_adaptive);
            case LIGHT: return getContext().getString(R.string.horizon_hp_level_light);
            case BALANCED: return getContext().getString(R.string.horizon_hp_level_balanced);
            case DEEP:
            default: return getContext().getString(R.string.horizon_hp_level_deep);
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Left earbud, case or right earbud: label, level and a slim bar; read as one item. */
    private final class BatteryCell extends LinearLayout {
        private final int label;
        private final TextView name;
        private final TextView value;
        private final FrameLayout track;
        private final View fill;

        BatteryCell(Context context, int label, int id) {
            super(context);
            this.label = label;
            setId(id);
            setOrientation(VERTICAL);
            setPadding(dp(10), dp(8), dp(10), dp(10));
            setBackground(shape(square ? 0 : dp(12), 0x14FFFFFF, 0, 0));
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            name = text(11, MUTED, true);
            name.setText(label);
            name.setAllCaps(true);
            name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(name);
            value = text(20, WHITE, true);
            value.setPadding(0, dp(4), 0, dp(6));
            value.setMaxLines(1);
            value.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(value);
            track = new FrameLayout(context);
            track.setBackground(round(0x26FFFFFF, dp(2)));
            fill = new View(context);
            track.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            addView(track, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
        }

        void bind(Cell cell, boolean stale) {
            Context c = getContext();
            HeadphonesCenterModel.Charge charge = HeadphonesCenterModel.charge(cell);
            int color = charge == HeadphonesCenterModel.Charge.CRITICAL ? RED
                    : charge == HeadphonesCenterModel.Charge.LOW ? AMBER : accent;
            String shown = cell.level == null ? "—" : cell.level + "%";
            value.setText(cell.charging && cell.level != null ? "⚡ " + shown : shown);
            value.setTextColor(cell.level == null ? MUTED : WHITE);
            setAlpha(stale && cell.level != null ? 0.6f : 1f);
            fill.setBackground(round(color, dp(2)));
            final int level = cell.level == null ? 0 : cell.level;
            track.post(() -> {
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                lp.width = Math.round(track.getWidth() * level / 100f);
                fill.setLayoutParams(lp);
            });
            String spoken = c.getString(label) + ", " + (cell.level == null
                    ? c.getString(R.string.horizon_hp_battery_unknown)
                    : c.getString(cell.charging ? R.string.horizon_hp_battery_charging : R.string.horizon_hp_battery,
                    cell.level));
            setContentDescription(spoken);
        }
    }
}
