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
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketIcons;
import com.limelight.pocket.ui.PocketUi;
import com.limelight.pocket.ui.Spring;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Nexus Control: the handheld's controls in one glass panel that slides in from the right, as
 * Apple TV's Control Center does. Tiles (sleep, Wi-Fi, Bluetooth, a screenshot, do not disturb,
 * the maker's performance tool) light up white when they have the focus; brightness and volume
 * are bars moved with left / right; the battery, its temperature and the screen's refresh rate
 * sit at the bottom. B (or Guide) closes it. Opens over anything: a game, another app, Home.
 */
public class ControlCenterActivity extends Activity {

    private static final Handler AFTER = new Handler(Looper.getMainLooper());
    private static final int INK = 0xFF111114;

    private float d;
    private FrameLayout root;
    private LinearLayout panel;
    private boolean closing;
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView clock;
    private TextView batteryBig;
    private TextView batterySub;
    private TextView tempBig;
    private TextView hzBig;
    private Tile wifi;
    private Tile bluetooth;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        int pad = dp(20);
        panel.setPadding(pad, dp(18), pad, dp(18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xEB26262E, 0xF0141418});
        bg.setCornerRadius(dp(28));
        bg.setStroke(Math.max(1, dp(1)), 0x22FFFFFF);
        panel.setBackground(bg);
        panel.setElevation(dp(24));
        // A floating card at the top right, as Apple TV's Control Center (its height: what it holds)
        float screenDp = getResources().getDisplayMetrics().heightPixels / d;
        int tileH = dp(Math.max(72f, Math.min(96f, (screenDp - 262f) / 2f)));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Math.min(dp(440),
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.5f)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        plp.setMarginEnd(dp(14));
        plp.topMargin = dp(14);
        root.addView(panel, plp);

        // The heading: the name and the time; the battery, its warmth and the screen's rate
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(getString(R.string.pocket_nc_title).toUpperCase(Locale.ROOT), 10f, 0x8CFFFFFF, PocketFonts.bold());
        name.setLetterSpacing(0.22f);
        titles.addView(name, wrap());
        clock = text("", 24f, 0xFFFFFFFF, PocketFonts.display());
        clock.setIncludeFontPadding(false);
        LinearLayout.LayoutParams cl = wrap();
        cl.topMargin = dp(3);
        titles.addView(clock, cl);
        head.addView(titles, wrap());
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        batteryBig = stat(head, getString(R.string.pocket_nc_battery));
        batterySub = (TextView) ((ViewGroup) batteryBig.getParent()).getChildAt(1);
        tempBig = stat(head, getString(R.string.pocket_nc_temperature));
        hzBig = stat(head, getString(R.string.pocket_nc_display));
        panel.addView(head, matchWrap());

        // Six tiles, three by two
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gl = matchWrap();
        gl.topMargin = dp(14);
        panel.addView(grid, gl);
        LinearLayout row1 = row(grid, 0);
        LinearLayout row2 = row(grid, dp(10));

        Tile sleep = tile(row1, "MOON", getString(R.string.pocket_nc_sleep), true, tileH);
        sleep.setOnClickListener(v -> sleep());
        first = sleep;
        wifi = tile(row1, "WIFI", getString(R.string.pocket_nc_wifi), true, tileH);
        wifi.setOnClickListener(v -> openWifi());
        bluetooth = tile(row1, "SWAP", getString(R.string.pocket_nc_bluetooth), false, tileH);
        bluetooth.setOnClickListener(v -> openSystem(Settings.ACTION_BLUETOOTH_SETTINGS));
        Tile shot = tile(row2, "SCREENSHOT", getString(R.string.pocket_nc_screenshot), true, tileH);
        shot.setOnClickListener(v -> screenshot());
        quiet = tile(row2, "BELL", getString(R.string.pocket_nc_quiet), true, tileH);
        quiet.setOnClickListener(v -> toggleQuiet());
        final String[] tool = performanceTool();
        Tile perf = tile(row2, "SPEED", getString(tool != null ? R.string.pocket_nc_performance : R.string.pocket_nc_settings),
                false, tileH);
        perf.setState(tool != null ? tool[2] : getString(R.string.pocket_nc_android));
        perf.setOnClickListener(v -> {
            if (tool != null) {
                openComponent(tool[0], tool[1]);
            }
            else {
                openSystem(Settings.ACTION_SETTINGS);
            }
        });

        // Reorder existing tiles without changing their actions or removing essential controls.
        Tile[] tiles = {sleep, wifi, bluetooth, shot, quiet, perf};
        int[] order = HorizonControls.order(PocketPrefs.prefs(this).getInt(HorizonControls.KEY_LAYOUT, 0));
        row1.removeAllViews();
        row2.removeAllViews();
        for (int i = 0; i < order.length; i++) {
            Tile t = tiles[order[i]];
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, tileH, 1f);
            if (i % 3 != 2) { lp.setMarginEnd(dp(10)); }
            (i < 3 ? row1 : row2).addView(t, lp);
        }
        first = tiles[order[0]];

        // Brightness and volume
        brightness = bar("SUN", getString(R.string.pocket_nc_brightness));
        LinearLayout.LayoutParams bl = matchWrap();
        bl.topMargin = dp(12);
        panel.addView(brightness, bl);
        volume = bar("VOLUME_UP", getString(R.string.pocket_nc_volume));
        LinearLayout.LayoutParams vl = matchWrap();
        vl.topMargin = dp(8);
        panel.addView(volume, vl);
        brightness.stepper = this::stepBrightness;
        volume.stepper = this::stepVolume;

        TextView tagline = text(getString(R.string.pocket_nc_tagline), 9.5f, 0x59FFFFFF, PocketFonts.medium());
        tagline.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams tg = matchWrap();
        tg.topMargin = dp(12);
        if (screenDp >= 500) {
            panel.addView(tagline, tg); // (a short screen keeps the room for the hints under it)
        }

        // What the buttons do: at the bottom of the screen, as everywhere else
        LinearLayout hints = com.limelight.pocket.ui.ConsoleScreen.hintBar(this);
        ConsoleActivity.fillHintBar(this, hints,
                ConsoleActivity.hintA(this, R.string.pocket_nc_hint_select),
                new ConsoleActivity.Hint("\u25C0\u25B6", 0xFFFFFFFF, getString(R.string.pocket_nc_hint_adjust)),
                ConsoleActivity.hintB(this, R.string.pocket_hint_close));
        noFocus(hints);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        hl.setMarginEnd(dp(18));
        hl.bottomMargin = dp(9);
        root.addView(hints, hl);

        // With the controller the focus starts on the first tile (also when the screen was
        // last touched: in touch mode a plain request would be refused)
        root.post(() -> {
            if (getCurrentFocus() == null || !(getCurrentFocus() instanceof Tile || getCurrentFocus() instanceof Bar)) {
                first.requestFocusFromTouch();
            }
        });
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
        TextView big = text("—", 15f, 0xFFFFFFFF, PocketFonts.semibold());
        cell.addView(big, wrap());
        TextView small = text(label, 9f, 0x80FFFFFF, PocketFonts.medium());
        small.setLetterSpacing(0.04f);
        cell.addView(small, wrap());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(12));
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
        hzBig.setText(Math.round(hz) + " Hz");
    }

    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (level >= 0 && scale > 0) {
                batteryBig.setText(Math.round(level * 100f / scale) + "%");
            }
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            batterySub.setText(getString(charging ? R.string.pocket_nc_charging : R.string.pocket_nc_battery));
            if (temp != Integer.MIN_VALUE) {
                tempBig.setText(Math.round(temp / 10f) + "°C");
            }
        }
    };

    // ---- Buttons --------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        View focus = getCurrentFocus();
        if (down && first != null && !(focus instanceof Tile) && !(focus instanceof Bar)
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

    /** Apple TV's focus: the tile turns white (its icon and words dark) and lifts a little. */
    private static void focusLook(View v, GradientDrawable bg, boolean has, boolean on, float d) {
        bg.setColor(has ? 0xFFFFFFFF : on ? 0x3DFFFFFF : 0x1FFFFFFF);
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
            int p = Math.round(11 * d);
            setPadding(Math.round(12 * d), p, p, p);
            bg.setCornerRadius(18 * d);
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
            ll.topMargin = Math.round(7 * d);
            addView(label, ll);
            state = new TextView(c);
            state.setIncludeFontPadding(false);
            state.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            state.setTypeface(PocketFonts.medium());
            state.setSingleLine(true);
            state.setEllipsize(TextUtils.TruncateAt.END);
            addView(state, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            paint(false);
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
            bg.setColor(hasFocus() ? 0xFFFFFFFF : on ? 0x3DFFFFFF : 0x1FFFFFFF);
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
            bg.setCornerRadius(16 * d);
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
            LayoutParams ll = new LayoutParams(Math.round(84 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
            ll.setMarginStart(Math.round(10 * d));
            addView(label, ll);
            track = new Track(c);
            addView(track, new LayoutParams(0, Math.round(16 * d), 1f));
            paint(false);
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
            track.target = Math.max(0f, Math.min(1f, v));
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

            Track(Context c) {
                super(c);
            }

            @Override
            protected void onDraw(Canvas c) {
                if (shown < 0) {
                    shown = target;
                }
                shown += (target - shown) * 0.35f;
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
                r.right = Math.max(h, getWidth() * shown);
                paint.setColor(focused ? INK : 0xFFFFFFFF);
                c.drawRoundRect(r, h / 2f, h / 2f, paint);
            }
        }
    }
}
