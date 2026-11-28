package com.limelight.pocket.global;

import android.app.Activity;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.device.HorizonPower;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketIcons;
import com.limelight.pocket.ui.PocketUi;
import com.limelight.pocket.ui.Spring;
import com.limelight.pocket.ui.TitaniumStyle;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Nexus Control: responsive action and device workspaces with a fixed header and footer. Tiles (sleep, Wi-Fi, Bluetooth, a screenshot, do not disturb,
 * the maker's performance tool) light up white when they have the focus; brightness and volume
 * are bars moved with left / right; the battery, its temperature and the screen's refresh rate
 * sit at the bottom. B (or Guide) closes it. Opens over anything: a game, another app, Home.
 */
public class ControlCenterActivity extends Activity {

    private static final Handler AFTER = new Handler(Looper.getMainLooper());
    private static final int INK = 0xFF111114;

    private static int accent(Context c) {
        return com.limelight.pocket.ui.PocketTheme.accent(c);
    }

    private static void paintSurface(View v, GradientDrawable bg, boolean focused, boolean on, float d) {
        int tint = accent(v.getContext());
        bg.setColor(focused ? HorizonControls.focusColor(tint) : on ? (tint & 0xFFFFFF) | 0x66000000 : 0x1FFFFFFF);
        bg.setStroke(Math.max(1, Math.round(d)), focused ? 0xFFFFFFFF : on ? (tint & 0xFFFFFF) | 0xCC000000 : 0x28FFFFFF);
    }

    private float d;
    private FrameLayout root;
    private LinearLayout panel;
    private boolean closing;
    private boolean titanium;
    private boolean legacy;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Nexus Horizon: the host, session and network cards under the title. */
    private com.limelight.pocket.ui.HorizonStreamDeck streamDeck;
    private long streamDeckAt;

    private TextView clock;
    private TextView batteryBig;
    private TextView batterySub;
    private TextView tempBig;
    private TextView hzBig;
    private TextView thermalBig;
    private Tile wifi;
    private Tile bluetooth;
    private com.limelight.pocket.ui.StreamMenuView headphonesMenu;
    private final com.limelight.pocket.ui.MenuKeyOwnership headphonesKeys = new com.limelight.pocket.ui.MenuKeyOwnership();
    private Tile quiet;
    private Bar brightness;
    private Bar volume;

    public static void open(Context c) {
        try {
            c.startActivity(new Intent(c, ControlCenterActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (RuntimeException e) {
            Toast.makeText(c, R.string.pocket_gb_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        titanium = com.limelight.pocket.ui.PocketTheme.titanium(this);
        legacy = com.limelight.pocket.ui.PocketTheme.legacy(this);
        if (!titanium && !legacy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.setBlurBehindRadius(Math.round(18 * d));
            getWindow().setAttributes(lp);
        }
        root = new FrameLayout(this);
        root.setBackgroundColor(0x73000000);
        root.setOnClickListener(v -> close());
        setContentView(root);
        build();
        enter();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (closing || root == null) return;
        if (panel != null) panel.animate().cancel();
        root.animate().cancel();
        root.setAlpha(1f);
        d = getResources().getDisplayMetrics().density;
        if (headphonesMenu != null) { headphonesMenu.hide(); headphonesMenu = null; }
        root.removeAllViews();
        build();
        refresh();
        Intent current = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (current != null) battery.onReceive(this, current);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        main.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(battery);
        } catch (RuntimeException ignored) {
        }
        main.removeCallbacks(tick);
        if (headphonesMenu != null) headphonesMenu.hide();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (clock != null) {
                clock.setText(DateFormat.getTimeFormat(ControlCenterActivity.this).format(new Date()));
            }
            refresh();
            main.postDelayed(this, 2000);
        }
    };

    // ---- The panel ------------------------------------------------------------------------------

    private void build() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        // A press on the panel isn't a press outside it; neither is a place for the focus
        panel.setClickable(true);
        panel.setFocusable(false);
        root.setFocusable(false);
        float screenWidthDp = getResources().getDisplayMetrics().widthPixels / d;
        float screenHeightDp = getResources().getDisplayMetrics().heightPixels / d;
        boolean pureOled = com.limelight.pocket.ui.PocketTheme.pureOled(this);
        HorizonControlLayout spec = HorizonControlLayout.forWindow(screenWidthDp, screenHeightDp, pureOled);
        int pad = dp(spec.padding);
        panel.setPadding(pad, dp(12), pad, dp(12));
        panel.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFA191D26, 0xFC10141B});
        if (pureOled) bg.setColor(0xFF000000);
        bg.setCornerRadius(dp(pureOled ? 4 : 18));
        bg.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
        panel.setBackground(titanium ? TitaniumStyle.surface(this)
                : legacy ? com.limelight.pocket.ui.LegacyStyle.dialog(this) : bg);
        if (com.limelight.pocket.ui.PocketTheme.signature(this)) {
            // Nexus Signature: a black slate, square-cornered, with a hairline in the accent, in
            // a letterboxed frame
            GradientDrawable slate = new GradientDrawable();
            slate.setColor(com.limelight.pocket.ui.HorizonSignature.SLATE);
            slate.setCornerRadius(dp(com.limelight.pocket.ui.HorizonSignature.SLATE_CORNER));
            slate.setStroke(Math.max(1, dp(1)), (accent(this) & 0x00FFFFFF) | 0x80000000);
            panel.setBackground(slate);
            panel.setId(R.id.horizon_signature_panel);
            com.limelight.pocket.ui.HorizonSignatureFrame.place(root, 0, true,
                    com.limelight.pocket.ui.HorizonSignature.TOP_BAR);
            com.limelight.pocket.ui.HorizonSignatureFrame.place(root, 1, false, 40);
        }
        if (com.limelight.pocket.ui.PocketTheme.atmosphere(this)) {
            // Nexus Atmosphere: a lighter, layered pane over the blurred screen (Android 12+),
            // denser where nothing is blurred behind it, with a light from above and a bright rim
            boolean blurBehind = !titanium && !legacy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
            GradientDrawable pane = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    com.limelight.pocket.ui.HorizonAtmosphere.controlPane(blurBehind));
            pane.setCornerRadius(dp(22));
            pane.setStroke(Math.max(1, dp(1)), com.limelight.pocket.ui.HorizonAtmosphere.RIM);
            GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{com.limelight.pocket.ui.HorizonAtmosphere.SHEEN_TOP, 0x00FFFFFF});
            sheen.setCornerRadius(dp(22));
            android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{pane, sheen});
            layers.setLayerInset(1, 0, 0, 0, dp(spec.height) * 3 / 5);
            panel.setBackground(layers);
            panel.setId(R.id.horizon_atmosphere_panel);
        }
        if (legacy) {
            // The dialog frame draws its own rule; keep the workspace padding inside it
            panel.setPadding(pad + dp(4), dp(16), pad + dp(4), dp(16));
            panel.setId(R.id.horizon_legacy_panel);
        }
        panel.setElevation(dp(pureOled || legacy ? 0 : 20));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(spec.width), dp(spec.height),
                Gravity.END | Gravity.CENTER_VERTICAL);
        plp.setMarginEnd(dp(spec.margin));
        root.addView(panel, plp);

        // Workspace header stays visible while the contents scroll.
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("PROJECT HORIZON", 9f, HorizonControls.focusColor(accent(this)), PocketFonts.bold());
        brand.setLetterSpacing(0.16f);
        titles.addView(brand, wrap());
        TextView name = text(getString(R.string.pocket_nc_title), 23f, 0xFFFFFFFF, PocketFonts.display());
        name.setIncludeFontPadding(false);
        titles.addView(name, wrap());
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clock = text("", 20f, 0xFFFFFFFF, PocketFonts.semibold());
        clock.setIncludeFontPadding(false);
        clock.setText(DateFormat.getTimeFormat(this).format(new Date()));
        head.addView(clock, wrap());
        panel.addView(head, matchWrap());
        streamDeck = null;
        if (com.limelight.pocket.ui.PocketTheme.horizon(this)) {
            addStreamDeck(spec.width - 2 * spec.padding);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(4), dp(8), dp(4), dp(8));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = dp(6);
        panel.addView(scroll, scrollLp);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(spec.split ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.setClipChildren(false);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Device telemetry and adjustments are one workspace; action tiles are another.
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setClipChildren(false);
        LinearLayout device = new LinearLayout(this);
        device.setOrientation(LinearLayout.VERTICAL);
        device.setClipChildren(false);
        if (spec.split) {
            body.addView(tools, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams side = new LinearLayout.LayoutParams(dp(292), ViewGroup.LayoutParams.WRAP_CONTENT);
            side.setMarginStart(dp(16));
            body.addView(device, side);
        } else {
            body.addView(tools, matchWrap());
            LinearLayout.LayoutParams side = matchWrap();
            side.topMargin = dp(12);
            body.addView(device, side);
        }
        tools.addView(section(R.string.horizon_control_actions), matchWrap());
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        tools.addView(grid, matchWrap());
        final String[] tool = performanceTool();
        Tile sleep = new Tile(this, "MOON", getString(R.string.pocket_nc_sleep));
        sleep.setOnClickListener(v -> sleep());
        wifi = new Tile(this, "WIFI", getString(R.string.pocket_nc_wifi));
        wifi.setOnClickListener(v -> openWifi());
        bluetooth = new Tile(this, "SWAP", getString(R.string.pocket_nc_bluetooth));
        bluetooth.setOnClickListener(v -> openSystem(Settings.ACTION_BLUETOOTH_SETTINGS));
        Tile shot = new Tile(this, "SCREENSHOT", getString(R.string.pocket_nc_screenshot));
        shot.setOnClickListener(v -> screenshot());
        quiet = new Tile(this, "BELL", getString(R.string.pocket_nc_quiet));
        quiet.setOnClickListener(v -> toggleQuiet());
        Tile perf = new Tile(this, "SPEED", getString(tool != null ? R.string.pocket_nc_performance : R.string.pocket_nc_settings));
        perf.setState(tool != null ? tool[2] : getString(R.string.pocket_nc_android));
        perf.setOnClickListener(v -> {
            if (tool != null) openComponent(tool[0], tool[1]);
            else openSystem(Settings.ACTION_SETTINGS);
        });
        Tile[] tiles = {sleep, wifi, bluetooth, shot, quiet, perf};
        int[] order = HorizonControls.order(PocketPrefs.prefs(this).getInt(HorizonControls.KEY_LAYOUT, 0));
        LinearLayout tileRow = null;
        for (int i = 0; i < order.length; i++) {
            if (i % spec.columns == 0) {
                tileRow = row(grid, i == 0 ? dp(6) : dp(8));
                tileRow.setClipChildren(false);
            }
            Tile t = tiles[order[i]];
            t.setId(View.generateViewId());
            if (titanium) t.setMinimumHeight(dp(76));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    titanium ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(pureOled ? 74 : 86), 1f);
            if (i % spec.columns != spec.columns - 1) lp.setMarginEnd(dp(8));
            tileRow.addView(t, lp);
        }
        first = tiles[order[0]];
        Tile headphones = new Tile(this, "VOLUME_UP", getString(R.string.horizon_headphones_center));
        headphones.setId(View.generateViewId());
        headphones.setMinimumHeight(dp(52));
        headphones.setOnClickListener(v -> openHeadphones(headphones));
        LinearLayout.LayoutParams headphonesLp = matchWrap();
        headphonesLp.topMargin = dp(8);
        tools.addView(headphones, headphonesLp);

        if (!titanium) device.addView(section(R.string.horizon_control_device), matchWrap());
        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.setPadding(0, dp(8), 0, dp(10));
        batteryBig = stat(stats, getString(R.string.pocket_nc_battery));
        batterySub = (TextView) ((ViewGroup) batteryBig.getParent()).getChildAt(1);
        tempBig = stat(stats, getString(titanium ? R.string.horizon_titanium_battery_temp : R.string.pocket_nc_temperature));
        hzBig = stat(stats, getString(R.string.pocket_nc_display));
        if (titanium) {
            stats.setId(R.id.horizon_titanium_telemetry);
            stats.setBackground(TitaniumStyle.surface(this));
            thermalBig = stat(stats, getString(R.string.horizon_titanium_thermal_label));
            LinearLayout.LayoutParams telemetryLp = matchWrap();
            telemetryLp.topMargin = dp(8);
            panel.addView(stats, panel.indexOfChild(scroll), telemetryLp);
        } else {
            device.addView(stats, matchWrap());
        }

        device.addView(section(R.string.horizon_control_adjust), matchWrap());
        brightness = bar("SUN", getString(R.string.pocket_nc_brightness));
        brightness.setId(View.generateViewId());
        LinearLayout.LayoutParams bl = matchWrap();
        bl.topMargin = dp(6);
        device.addView(brightness, bl);
        volume = bar("VOLUME_UP", getString(R.string.pocket_nc_volume));
        volume.setId(View.generateViewId());
        LinearLayout.LayoutParams vl = matchWrap();
        vl.topMargin = dp(8);
        device.addView(volume, vl);
        brightness.stepper = this::stepBrightness;
        volume.stepper = this::stepVolume;
        // Touch users can tap the left/right half to adjust; gamepad left/right stays unchanged.
        brightness.setOnClickListener(v -> brightness.step(1));
        volume.setOnClickListener(v -> volume.step(1));
        brightness.setOnTouchListener((v, event) -> adjustTouch(brightness, event));
        volume.setOnTouchListener((v, event) -> adjustTouch(volume, event));
        if (spec.split) {
            // Up/down provides an escape from sliders whose left/right keys adjust values.
            Tile topEdge = tiles[order[spec.columns - 1]];
            Tile bottomEdge = tiles[order[order.length - 1]];
            topEdge.setNextFocusRightId(brightness.getId());
            bottomEdge.setNextFocusRightId(volume.getId());
            brightness.setNextFocusUpId(topEdge.getId());
            brightness.setNextFocusDownId(volume.getId());
            volume.setNextFocusUpId(brightness.getId());
            volume.setNextFocusDownId(bottomEdge.getId());
        }

        // Footer belongs to the workspace, never overlaps scrollable actions.
        LinearLayout hints = com.limelight.pocket.ui.ConsoleScreen.hintBar(this);
        ConsoleActivity.fillHintBar(this, hints,
                ConsoleActivity.hintA(this, R.string.pocket_nc_hint_select),
                new ConsoleActivity.Hint("\u25C0\u25B6", 0xFFFFFFFF, getString(R.string.pocket_nc_hint_adjust)),
                ConsoleActivity.hintB(this, R.string.pocket_hint_close));
        noFocus(hints);
        panel.addView(hints, matchWrap());
        if (legacy) pixelType(panel, com.limelight.pocket.ui.LegacyStyle.typeface(this));

        // With the controller the focus starts on the first tile (also when the screen was
        // last touched: in touch mode a plain request would be refused)
        root.post(() -> {
            if (getCurrentFocus() == null || !(getCurrentFocus() instanceof Tile || getCurrentFocus() instanceof Bar)) {
                first.requestFocusFromTouch();
            }
        });
    }

    /**
     * Nexus Horizon's streaming cards, one line each, side by side when they fit across the
     * panel (else stacked). They open Streaming, the running game there, or Android's network
     * panel; the focus reaches them UP from the tiles and goes back DOWN.
     */
    private void addStreamDeck(int innerWidthDp) {
        streamDeck = new com.limelight.pocket.ui.HorizonStreamDeck(this, true,
                new com.limelight.pocket.ui.HorizonStreamDeck.Actions() {
                    @Override public void openHosts() {
                        com.limelight.pocket.ui.SectionBar.go(ControlCenterActivity.this, -1,
                                com.limelight.pocket.ui.SectionBar.STREAMING);
                    }

                    @Override public void resume(com.limelight.pocket.ui.HorizonStreamCards.Host running) {
                        openHosts();
                    }

                    @Override public void openNetwork() {
                        com.limelight.pocket.ui.HorizonStreamDeck.openNetworkPanel(ControlCenterActivity.this);
                    }
                });
        boolean side = com.limelight.pocket.ui.HorizonStreamCards.sideBySide(innerWidthDp, 3);
        streamDeck.arrange(!side, true);
        streamDeck.setEdges(null, null, () -> {
            if (first != null) first.requestFocus();
        });
        streamDeck.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(10);
        panel.addView(streamDeck, lp);
        streamDeckAt = 0;
    }

    /** The saved PCs with what Home last saw of them, and the network (every 10 s at most). */
    private void updateStreamDeck() {
        final com.limelight.pocket.ui.HorizonStreamDeck deck = streamDeck;
        long now = android.os.SystemClock.elapsedRealtime();
        if (deck == null || (streamDeckAt != 0 && now - streamDeckAt < 10_000)) return;
        streamDeckAt = now;
        final Context app = getApplicationContext();
        new Thread(() -> {
            final java.util.List<com.limelight.pocket.ui.HorizonStreamCards.Host> hosts = com.limelight.pocket.ui
                    .HorizonStreamDeck.hosts(app, com.limelight.pocket.home.HomeStore.computers(app), null, null);
            main.post(() -> {
                if (isFinishing() || deck != streamDeck) return;
                deck.show(hosts, com.limelight.pocket.ui.HorizonStreamDeck.readLink(app));
                deck.setVisibility(View.VISIBLE);
            });
        }, "horizon-stream-cards").start();
    }

    private TextView section(int label) {
        TextView title = text(getString(label).toUpperCase(Locale.ROOT), 10f,
                0xB8FFFFFF, PocketFonts.bold());
        title.setLetterSpacing(0.10f);
        return title;
    }

    private boolean adjustTouch(Bar bar, android.view.MotionEvent event) {
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                return true;
            case android.view.MotionEvent.ACTION_UP:
                if (event.getX() >= 0 && event.getX() < bar.getWidth()
                        && event.getY() >= 0 && event.getY() < bar.getHeight()) {
                    bar.requestFocusFromTouch();
                    if (event.getX() < bar.getWidth() / 2f) bar.step(-1);
                    else bar.performClick();
                }
                return true;
            case android.view.MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    private Tile first;

    /** Hints (and anything else that isn't a control) never take the focus. */
    private static void noFocus(View v) {
        v.setFocusable(false);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                noFocus(g.getChildAt(i));
            }
        }
    }

    private LinearLayout row(LinearLayout grid, int top) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = top;
        grid.addView(r, lp);
        return r;
    }

    private Tile tile(LinearLayout row, String icon, String label, boolean gapAfter, int height) {
        Tile t = new Tile(this, icon, label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, height, 1f);
        if (gapAfter) {
            lp.setMarginEnd(dp(10));
        }
        row.addView(t, lp);
        return t;
    }

    private Bar bar(String icon, String label) {
        return new Bar(this, icon, label);
    }

    private TextView stat(LinearLayout stats, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView big = text(titanium ? getString(R.string.horizon_titanium_unavailable) : "—", 15f,
                titanium ? TitaniumStyle.TEXT : 0xFFFFFFFF, titanium ? Typeface.MONOSPACE : PocketFonts.semibold());
        if (titanium) {
            big.setSingleLine(true);
            big.setEllipsize(TextUtils.TruncateAt.END);
        }
        cell.addView(big, wrap());
        TextView small = text(label, 9f, titanium ? TitaniumStyle.SECONDARY : 0x80FFFFFF, PocketFonts.medium());
        if (titanium) {
            small.setSingleLine(true);
            small.setEllipsize(TextUtils.TruncateAt.END);
        }
        small.setLetterSpacing(0.04f);
        cell.addView(small, wrap());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        stats.addView(cell, lp);
        return big;
    }

    // ---- What each does -------------------------------------------------------------------------

    private void sleep() {
        PocketSounds.play(this, PocketSounds.SELECT);
        exitThen(() -> {
            if (!GlobalButtonEngine.lockScreen()) {
                Toast.makeText(getApplicationContext(), R.string.pocket_gb_needs_service, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void screenshot() {
        PocketSounds.play(this, PocketSounds.SELECT);
        final Context app = getApplicationContext();
        // After the panel is gone (it mustn't be in the picture)
        exitThen(() -> GlobalButtonEngine.runAction(app, GlobalButtons.Action.SCREENSHOT, null));
    }

    private void openWifi() {
        PocketSounds.play(this, PocketSounds.OPEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startActivity(new Intent(Settings.Panel.ACTION_WIFI));
                return;
            } catch (RuntimeException ignored) {
            }
        }
        openSystem(Settings.ACTION_WIFI_SETTINGS);
    }

    private void toggleQuiet() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !nm.isNotificationPolicyAccessGranted()) {
            // Android lets an app turn it on only once allowed (asked once, in its settings)
            Toast.makeText(this, R.string.pocket_nc_quiet_allow, Toast.LENGTH_LONG).show();
            openSystem(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            return;
        }
        boolean on = nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_ALL;
        try {
            nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    : NotificationManager.INTERRUPTION_FILTER_ALL);
        } catch (RuntimeException ignored) {
        }
        PocketSounds.play(this, on ? PocketSounds.TOGGLE_ON : PocketSounds.TOGGLE_OFF);
        PocketUi.haptic(this, PocketUi.HAPTIC_CLICK);
        refresh();
    }

    private void openSystem(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.pocket_apps_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void openComponent(String pkg, String cls) {
        PocketSounds.play(this, PocketSounds.OPEN);
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.pocket_apps_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    /** The maker's own tool with the performance and fan modes (Retroid's, AYN's...), or null. */
    private String[] performanceTool() {
        List<String[]> found = com.limelight.pocket.device.HandheldTools.find(this);
        String[] best = null;
        int priority = 0;
        for (String[] t : found) {
            int score = com.limelight.pocket.device.HorizonPower.toolPriority(t[2]);
            if (score > priority) {
                priority = score;
                best = t;
            }
        }
        return best;
    }

    private String stepBrightness(int direction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            Toast.makeText(this, R.string.pocket_nc_brightness_allow, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException ignored) {
            }
            return null;
        }
        int now = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        int next = Math.max(1, Math.min(255, now + direction * 16));
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, next);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private String stepVolume(int direction) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int now = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            try {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(max, now + direction)), 0);
            } catch (SecurityException ignored) {
                // Do not disturb won't let it change
            }
        }
        return null;
    }

    // ---- Now ------------------------------------------------------------------------------------

    private void refresh() {
        if (wifi == null) {
            return;
        }
        updateStreamDeck();
        boolean wifiOn = false;
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            wifiOn = wm != null && wm.isWifiEnabled();
        } catch (RuntimeException ignored) {
        }
        wifi.setOn(wifiOn);
        wifi.setState(getString(wifiOn ? R.string.pocket_on : R.string.pocket_off));
        boolean btOn = false;
        boolean btKnown = true;
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            btOn = bt != null && bt.isEnabled();
        } catch (RuntimeException e) {
            btKnown = false;
        }
        bluetooth.setOn(btOn);
        bluetooth.setState(btKnown ? getString(btOn ? R.string.pocket_on : R.string.pocket_off) : "");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean quietOn = nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
        quiet.setOn(quietOn);
        quiet.setState(getString(quietOn ? R.string.pocket_on : R.string.pocket_off));

        int b = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        brightness.setValue(b / 255f);
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            volume.setValue(am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max);
        }
        float hz = getWindowManager().getDefaultDisplay().getRefreshRate();
        if (titanium) {
            hzBig.setText(hz > 0 && !Float.isInfinite(hz) ? getString(R.string.horizon_titanium_hertz, Math.round(hz))
                    : getString(R.string.horizon_titanium_unavailable));
            thermalBig.setText(TitaniumStyle.thermal(this, HorizonPower.observe(this).thermal));
        } else {
            hzBig.setText(Math.round(hz) + " Hz");
        }
    }

    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (level >= 0 && scale > 0 && (!titanium || level <= scale)) {
                int percent = Math.round(level * 100f / scale);
                batteryBig.setText(titanium ? getString(R.string.horizon_titanium_percent, percent) : percent + "%");
            } else if (titanium) {
                batteryBig.setText(R.string.horizon_titanium_unavailable);
            }
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            batterySub.setText(getString(charging ? R.string.pocket_nc_charging : R.string.pocket_nc_battery));
            if (temp != Integer.MIN_VALUE) {
                tempBig.setText(Math.round(temp / 10f) + "°C");
            } else if (titanium) {
                tempBig.setText(R.string.horizon_titanium_unavailable);
            }
        }
    };

    private void openHeadphones(View returnFocus) {
        if (headphonesMenu != null && headphonesMenu.isShowing()) return;
        final com.limelight.pocket.ui.StreamMenuView menu = new com.limelight.pocket.ui.StreamMenuView(this);
        headphonesMenu = menu;
        // The Control workspace has its own elevation; child card elevation cannot
        // lift this sibling above it. Own the modal layer explicitly.
        final View underlying = panel;
        final int previousAccessibility = underlying.getImportantForAccessibility();
        underlying.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        menu.setElevation(underlying.getZ() + dp(2));
        menu.setPlacement(true, 336);
        menu.setConfirmLabel(getString(R.string.pocket_sm_confirm));
        menu.setListener(() -> {
            if (headphonesMenu != menu) return;
            headphonesMenu = null;
            root.removeView(menu);
            underlying.setImportantForAccessibility(previousAccessibility);
            if (returnFocus.isAttachedToWindow()) returnFocus.requestFocusFromTouch();
        });
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        menu.bringToFront();
        com.limelight.pocket.device.headphones.HeadphonesPanel.openRoot(this, menu);
    }

    // ---- Buttons --------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean headphonesShowing = headphonesMenu != null && headphonesMenu.isShowing();
        if (headphonesKeys.consume(e, headphonesShowing)) {
            if (headphonesShowing) headphonesMenu.handleKey(e);
            return true;
        }
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        View focus = getCurrentFocus();
        if (down && first != null && !(focus instanceof Tile) && !(focus instanceof Bar)
                && !(streamDeck != null && streamDeck.owns(focus))
                && code != KeyEvent.KEYCODE_BUTTON_B && code != KeyEvent.KEYCODE_BACK
                && code != KeyEvent.KEYCODE_BUTTON_MODE && code != KeyEvent.KEYCODE_ESCAPE) {
            // Nothing of the panel has the focus yet: the first key puts it on the first tile
            first.requestFocusFromTouch();
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_MODE:
                if (down && e.getRepeatCount() == 0) {
                    PocketSounds.play(this, PocketSounds.CLOSE);
                    close();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: {
                if (down && e.getRepeatCount() == 0) {
                    View f = getCurrentFocus();
                    if (f instanceof Bar) {
                        ((Bar) f).step(1);
                    }
                    else if (f != null) {
                        PocketUi.haptic(this, PocketUi.HAPTIC_CLICK);
                        f.performClick();
                    }
                }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                View f = getCurrentFocus();
                if (f instanceof Bar) {
                    if (down) {
                        ((Bar) f).step(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    }
                    return true;
                }
                break;
            }
            default:
                break;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- In and out -----------------------------------------------------------------------------

    private void enter() {
        if (!PocketPrefs.animationsEnabled(this)) {
            return;
        }
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(200).start();
        panel.setTranslationX(dp(70));
        panel.setScaleX(0.97f);
        panel.setScaleY(0.97f);
        panel.animate().translationX(0).scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(Spring.POP).start();
        PocketSounds.play(this, PocketSounds.OPEN);
    }

    private void close() {
        exitThen(null);
    }

    private void exitThen(final Runnable after) {
        if (closing) {
            return;
        }
        closing = true;
        Runnable done = () -> {
            finish();
            overridePendingTransition(0, 0);
            if (after != null) {
                AFTER.postDelayed(after, 250);
            }
        };
        if (!PocketPrefs.animationsEnabled(this)) {
            done.run();
            return;
        }
        root.animate().alpha(0f).setDuration(180).start();
        panel.animate().translationX(dp(60)).setDuration(180).withEndAction(done).start();
    }

    // ---- Little helpers -------------------------------------------------------------------------

    private int dp(float v) {
        return Math.round(v * d);
    }

    private TextView text(String s, float sp, int color, android.graphics.Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Nexus Legacy: every word on the panel in pixel type (the hint keys keep their glyphs). */
    private static void pixelType(View v, android.graphics.Typeface face) {
        if (v instanceof TextView) {
            Object tag = v.getTag();
            if (!(tag instanceof String && ((String) tag).startsWith("hint:"))) {
                ((TextView) v).setTypeface(face);
            }
        }
        else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) pixelType(g.getChildAt(i), face);
        }
    }

    /** Apple TV's focus: the tile turns white (its icon and words dark) and lifts a little. */
    private static void focusLook(View v, GradientDrawable bg, boolean has, boolean on, float d) {
        paintSurface(v, bg, has, on, d);
        if (com.limelight.pocket.ui.PocketTheme.pureOled(v.getContext())
                || com.limelight.pocket.ui.PocketTheme.titanium(v.getContext())
                || com.limelight.pocket.ui.PocketTheme.legacy(v.getContext())) return;
        v.animate().cancel();
        boolean motion = PocketPrefs.animationsEnabled(v.getContext());
        v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).translationZ(has ? 8 * d : 0)
                .setDuration(motion ? (has ? 260 : 160) : 0).setInterpolator(has ? Spring.POP
                        : new android.view.animation.DecelerateInterpolator()).start();
    }

    /** A tile: an icon, its name, and what it is now (On / Off ...). */
    private static final class Tile extends LinearLayout {
        private final GradientDrawable bg = new GradientDrawable();
        private final ImageView icon;
        private final String iconName;
        private final TextView label;
        private final TextView state;
        private final float d;
        private boolean on;

        Tile(Context c, String iconName, String name) {
            super(c);
            this.iconName = iconName;
            d = c.getResources().getDisplayMetrics().density;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            boolean pureOled = com.limelight.pocket.ui.PocketTheme.pureOled(c);
            int p = Math.round((pureOled ? 6 : 11) * d);
            setPadding(Math.round(12 * d), p, p, p);
            boolean titanium = com.limelight.pocket.ui.PocketTheme.titanium(c);
            boolean legacy = com.limelight.pocket.ui.PocketTheme.legacy(c);
            bg.setCornerRadius((legacy ? 0 : titanium ? 5 : pureOled ? 4 : 18) * d);
            bg.setColor(0x1FFFFFFF);
            setBackground(bg);
            setFocusable(true);
            setClickable(true);
            if (Build.VERSION.SDK_INT >= 26) {
                setDefaultFocusHighlightEnabled(false);
            }
            icon = new ImageView(c);
            addView(icon, new LayoutParams(Math.round(22 * d), Math.round(22 * d)));
            label = new TextView(c);
            label.setText(name);
            label.setIncludeFontPadding(false);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            label.setTypeface(PocketFonts.semibold());
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams ll = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ll.topMargin = Math.round(5 * d);
            addView(label, ll);
            state = new TextView(c);
            state.setIncludeFontPadding(false);
            state.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            state.setTypeface(PocketFonts.medium());
            state.setSingleLine(true);
            state.setEllipsize(TextUtils.TruncateAt.END);
            addView(state, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            paint(false);
            paintSurface(this, bg, false, false, d);
            setOnFocusChangeListener((v, has) -> {
                if (has) {
                    PocketSounds.play(getContext(), PocketSounds.NAV);
                    PocketUi.haptic(getContext(), PocketUi.HAPTIC_TICK);
                }
                paint(has);
                focusLook(this, bg, has, on, d);
            });
        }

        void setOn(boolean on) {
            this.on = on;
            paint(hasFocus());
            paintSurface(this, bg, hasFocus(), on, d);
        }

        void setState(String s) {
            state.setText(s);
            state.setVisibility(s == null || s.isEmpty() ? GONE : VISIBLE);
        }

        private void paint(boolean focused) {
            int ink = focused ? INK : 0xFFFFFFFF;
            icon.setImageDrawable(PocketIcons.drawable(iconName, Math.round(22 * d), ink));
            label.setTextColor(ink);
            state.setTextColor(focused ? 0x99111114 : 0x8CFFFFFF);
        }
    }

    /** A bar moved with left / right (brightness, volume): its icon, its name and how full it is. */
    private static final class Bar extends LinearLayout {
        interface Stepper {
            String step(int direction);
        }

        private final GradientDrawable bg = new GradientDrawable();
        private final ImageView icon;
        private final String iconName;
        private final TextView label;
        private final Track track;
        private final TextView value;
        private final float d;
        Stepper stepper;

        Bar(Context c, String iconName, String name) {
            super(c);
            this.iconName = iconName;
            d = c.getResources().getDisplayMetrics().density;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            int p = Math.round(12 * d);
            setPadding(p, Math.round(10 * d), Math.round(14 * d), Math.round(10 * d));
            bg.setCornerRadius(com.limelight.pocket.ui.PocketTheme.legacy(c) ? 0 : 16 * d);
            bg.setColor(0x1FFFFFFF);
            setBackground(bg);
            setFocusable(true);
            setClickable(true);
            if (Build.VERSION.SDK_INT >= 26) {
                setDefaultFocusHighlightEnabled(false);
            }
            icon = new ImageView(c);
            addView(icon, new LayoutParams(Math.round(20 * d), Math.round(20 * d)));
            label = new TextView(c);
            label.setText(name);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            label.setTypeface(PocketFonts.semibold());
            label.setSingleLine(true);
            LayoutParams ll = new LayoutParams(Math.round(74 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
            label.setEllipsize(TextUtils.TruncateAt.END);
            ll.setMarginStart(Math.round(10 * d));
            addView(label, ll);
            track = new Track(c);
            addView(track, new LayoutParams(0, Math.round(16 * d), 1f));
            value = new TextView(c);
            value.setText("—");
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            value.setTypeface(PocketFonts.medium());
            value.setSingleLine(true);
            value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LayoutParams vl = new LayoutParams(Math.round(40 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
            vl.setMarginStart(Math.round(6 * d));
            addView(value, vl);
            paint(false);
            paintSurface(this, bg, false, false, d);
            setOnFocusChangeListener((v, has) -> {
                if (has) {
                    PocketSounds.play(getContext(), PocketSounds.NAV);
                    PocketUi.haptic(getContext(), PocketUi.HAPTIC_TICK);
                }
                paint(has);
                focusLook(this, bg, has, false, d);
            });
        }

        void setValue(float v) {
            track.target = HorizonControls.clampLevel(v);
            value.setText(java.text.NumberFormat.getPercentInstance().format(track.target));
            track.invalidate();
        }

        void step(int direction) {
            if (stepper != null) {
                stepper.step(direction);
                PocketUi.haptic(getContext(), PocketUi.HAPTIC_TICK);
                PocketSounds.play(getContext(), PocketSounds.NAV);
                // The settings change a moment later: read again soon
                postDelayed(() -> {
                    Context c = getContext();
                    if (c instanceof ControlCenterActivity) {
                        ((ControlCenterActivity) c).refresh();
                    }
                }, 60);
            }
        }

        private void paint(boolean focused) {
            int ink = focused ? INK : 0xFFFFFFFF;
            icon.setImageDrawable(PocketIcons.drawable(iconName, Math.round(18 * d), ink));
            label.setTextColor(ink);
            value.setTextColor(ink);
            track.focused = focused;
            track.invalidate();
        }

        /** The bar itself: a rounded track, filled as far as the value, eased toward it. */
        private static final class Track extends View {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            float target;
            float shown = -1f;
            boolean focused;
            private final boolean motion;
            private final int fill;

            Track(Context c) {
                super(c);
                motion = PocketPrefs.animationsEnabled(c);
                fill = HorizonControls.focusColor(accent(c));
            }

            @Override
            protected void onDraw(Canvas c) {
                if (shown < 0) {
                    shown = target;
                }
                shown = motion ? shown + (target - shown) * 0.35f : target;
                if (Math.abs(target - shown) > 0.002f) {
                    postInvalidateOnAnimation();
                }
                else {
                    shown = target;
                }
                float h = getHeight() * 0.5f;
                float top = (getHeight() - h) / 2f;
                r.set(0, top, getWidth(), top + h);
                paint.setColor(focused ? 0x26111114 : 0x33FFFFFF);
                c.drawRoundRect(r, h / 2f, h / 2f, paint);
                if (shown > 0f) {
                    r.right = getWidth() * shown;
                    paint.setColor(focused ? INK : fill);
                    c.drawRoundRect(r, h / 2f, h / 2f, paint);
                }
            }
        }
    }
}
