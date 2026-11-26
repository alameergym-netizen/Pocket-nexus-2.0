package com.limelight.pocket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ContextMenu;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.widget.PopupMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.limelight.R;
import com.limelight.pocket.ui.PocketUi;
import com.limelight.pocket.ui.MenuKeyOwnership;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.List;

import java.util.Date;

/**
 * Base for the console-style screens: full-screen, a status bar of our own (link, battery,
 * clock), a button-hint bar, and the controller shortcuts shared by every screen:
 *   START or the configured menu button  -> quick settings
 */
public abstract class ConsoleActivity extends AppCompatActivity {

    public static final class Hint {
        final String glyph;
        final int color;
        final String label;

        public Hint(String glyph, int color, String label) {
            this.glyph = glyph;
            this.color = color;
            this.label = label;
        }
    }

    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private String menuHotkey;
    private String m2Hotkey;
    private boolean hotkeyDown;
    private int batteryPct = -1;
    private boolean charging;
    private boolean statusRunning;
    private int createdWithScale;
    /** The person has moved the focus themselves (screens stop placing it for them). */
    protected boolean userNavigated;
    /** The look this screen was built with (accent, backdrop, glass): rebuilt when it changes. */
    private String createdWithLook;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (level >= 0 && scale > 0) {
                batteryPct = Math.round(level * 100f / scale);
            }
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            updateBattery();
        }
    };

    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            // Look for the PC's ADB USB bridge in the background
            UsbBridge.probeAsync();
            updateClock();
            updateLinkChip();
            updateMusicChip();
            onStatusTick();
            statusHandler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(PocketUi.wrapWithUiScale(newBase));
    }

    /**
     * Which library's layout this screen uses (its own, apart from every other section's: the
     * Android section's layout isn't Cloud's), or null for the one they share.
     */
    protected String layoutScope() {
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PocketSetup.applyFirstRunDefaults(this);
        PocketPrefs.setLayoutScope(layoutScope());
        super.onCreate(savedInstanceState);
        // Apps installed or removed while Pocket Nexus runs show (or leave) everywhere at once
        com.limelight.pocket.apps.AppWatcher.start(this);
        createdWithScale = PocketPrefs.uiScalePercent(this);
        createdWithLook = lookSignature();
        bootOnLaunch = savedInstanceState == null && isTaskRoot() && allowsAutoResume()
                && getIntent() != null && Intent.ACTION_MAIN.equals(getIntent().getAction());
        if (bootOnLaunch) {
            // A fresh launch of the app: the boot plays once (on whichever screen comes up first)
            bootPending = true;
        }
        PocketSounds.preload(this);
        applyFullscreenWindow(this);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        installLiveGlass();
        applyHeroLogoSize();
    }

    /** The logo size setting just changed (an options menu): the hero follows it now. */
    public void refreshHeroLogoSize() {
        applyHeroLogoSize();
    }

    /** The game the hero shows now (its art's uuid and id; null: none), for its own logo size. */
    private String heroUuid;
    private int heroId;

    /** The hero now shows this game: its logo at its own size (or everyone's). */
    public void setHeroGame(String uuid, int id) {
        boolean changed = uuid == null ? heroUuid != null : !uuid.equals(heroUuid) || id != heroId;
        heroUuid = uuid;
        heroId = id;
        if (changed) {
            applyHeroLogoSize();
        }
    }

    public String heroUuid() {
        return heroUuid;
    }

    public int heroId() {
        return heroId;
    }

    /** A library hero's logo box at 100% (dp): the logo size setting scales it. */
    private static final float HERO_LOGO_DP = 88f;

    /**
     * The library heroes' logo box at the size picked in settings (logos bigger or smaller).
     * Called when the screen is built and whenever it comes back (the setting may have changed);
     * screens with a hero of their own (Home) do theirs.
     */
    protected void applyHeroLogoSize() {
        View box = findViewById(R.id.pocketHeroName);
        android.widget.ImageView logo = findViewById(R.id.pocketHeroLogo);
        if (box == null || logo == null) {
            return;
        }
        float f = PocketPrefs.logoScale(this, heroUuid, heroId) / 100f;
        float d = getResources().getDisplayMetrics().density;
        // The box keeps one height for every game (nothing under it moves)
        float want = HERO_LOGO_DP * f;
        float h = Math.max(48f, Math.min(156f, want));
        ViewGroup.LayoutParams lp = box.getLayoutParams();
        int height = Math.round(h * d);
        if (lp.height != height) {
            lp.height = height;
            box.setLayoutParams(lp);
        }
        // The logo fills the box's height (LogoView scales it), as wide as the size allows
        ViewGroup.LayoutParams llp = logo.getLayoutParams();
        int width = Math.round(Math.min(540f, 360f * Math.max(1f, f)) * d);
        if (llp.width != width) {
            llp.width = width;
            logo.setLayoutParams(llp);
        }
        if (logo instanceof com.limelight.pocket.ui.LogoView) {
            ((com.limelight.pocket.ui.LogoView) logo).setGrow(want / h);
        }
    }

    /** Views tagged "glass" in the layout become real glass over the screen's backdrop. */
    protected void installLiveGlass() {
        View main = findViewById(R.id.pocketMain);
        if (main == null) {
            return;
        }
        View source = findViewById(R.id.pocketBackdrop);
        if (source == null) {
            source = findViewById(R.id.pocketAurora);
        }
        com.limelight.pocket.ui.LiveGlass.Backdrop backdrop = null;
        if (source != null && com.limelight.pocket.ui.LiveGlass.supported() && PocketPrefs.liveGlass(this)) {
            boolean frost = PocketPrefs.frost(this);
            backdrop = new com.limelight.pocket.ui.LiveGlass.Backdrop(source, frost ? 44f : 22f, frost);
        }
        liveGlassBackdrop = backdrop;
        com.limelight.pocket.ui.LiveGlass.attachTagged(main, backdrop);
    }

    private com.limelight.pocket.ui.LiveGlass.Backdrop liveGlassBackdrop;

    /** Makes a view added later (a panel) real glass over this screen's backdrop. */
    public void makeGlass(View view) {
        if (liveGlassBackdrop != null && view != null) {
            com.limelight.pocket.ui.LiveGlass.attach(view, liveGlassBackdrop, com.limelight.pocket.ui.LiveGlass.STYLE_PANEL);
        }
    }

    // ---- Quick Resume -------------------------------------------------------------------------

    private com.limelight.pocket.ui.QuickResumePanel quickPanel;
    private View focusBeforeQuick;

    /** True while Quick Resume is open. */
    public boolean isQuickResumeShowing() {
        return quickPanel != null;
    }

    /** Opens Quick Resume (screens that know games: the home screen and the library). */
    public void openQuickResume() {
        if (!(this instanceof com.limelight.pocket.ui.QuickResumePanel.Source)) {
            // Everywhere else: the same Quick Resume as over a game (the apps and games used last)
            com.limelight.pocket.global.GlobalMenuActivity.show(this,
                    com.limelight.pocket.global.GlobalMenuActivity.MODE_QUICK, getPackageName());
            return;
        }
        if (quickPanel != null) {
            return;
        }
        View rootView = findViewById(R.id.pocketRoot);
        if (!(rootView instanceof ViewGroup)) {
            return;
        }
        final ViewGroup main = findViewById(R.id.pocketMain);
        focusBeforeQuick = getCurrentFocus();
        if (main != null) {
            main.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        quickPanel = new com.limelight.pocket.ui.QuickResumePanel(this,
                (com.limelight.pocket.ui.QuickResumePanel.Source) this, this::makeGlass, () -> {
            quickPanel = null;
            if (main != null) {
                main.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            if (focusBeforeQuick != null && focusBeforeQuick.isAttachedToWindow()) {
                focusBeforeQuick.requestFocus();
            }
            focusBeforeQuick = null;
        });
        quickPanel.show((ViewGroup) rootView);
    }

    /** M1 / M2 on the home screens: settings, Quick Resume, the game's page or the last game. */
    protected void runLauncherShortcut(String action) {
        switch (action) {
            case ButtonActions.LAUNCHER_QUICK_RESUME:
                openQuickResume();
                break;
            case ButtonActions.LAUNCHER_GAME_PAGE:
                onGamePageShortcut();
                break;
            case ButtonActions.LAUNCHER_RESUME_LAST:
                resumeLastGame();
                break;
            case ButtonActions.LAUNCHER_APPS:
                com.limelight.pocket.apps.AndroidAppsActivity.open(this);
                break;
            case ButtonActions.LAUNCHER_RETRO:
                if (!(this instanceof com.limelight.pocket.retro.RetroActivity)) {
                    com.limelight.pocket.retro.RetroActivity.open(this);
                }
                break;
            case ButtonActions.LAUNCHER_CLOUD:
                if (!(this instanceof com.limelight.pocket.cloud.CloudActivity)) {
                    PocketSounds.play(this, PocketSounds.OPEN);
                    startActivity(new Intent(this, com.limelight.pocket.cloud.CloudActivity.class));
                }
                break;
            case ButtonActions.LAUNCHER_DEVICE:
                com.limelight.pocket.global.ControlCenterActivity.open(this);
                break;
            case ButtonActions.LAUNCHER_SEARCH:
                PocketSounds.play(this, PocketSounds.OPEN);
                com.limelight.pocket.search.SearchActivity.open(this);
                break;
            case ButtonActions.LAUNCHER_FILES:
                PocketSounds.play(this, PocketSounds.OPEN);
                com.limelight.pocket.files.FilesActivity.open(this);
                break;
            case ButtonActions.LAUNCHER_BROWSER:
                PocketSounds.play(this, PocketSounds.OPEN);
                com.limelight.pocket.browser.BrowserActivity.open(this, null);
                break;
            case ButtonActions.LAUNCHER_MUSIC:
                PocketSounds.play(this, PocketSounds.OPEN);
                com.limelight.pocket.music.MusicActivity.open(this);
                break;
            case ButtonActions.LAUNCHER_SURPRISE:
                com.limelight.pocket.home.SurpriseActivity.open(this);
                break;
            case ButtonActions.LAUNCHER_NONE:
                break;
            default:
                onMenuButton();
                break;
        }
    }

    /** Opens the focused game's page. Screens with games override. */
    protected boolean onGamePageShortcut() {
        return false;
    }

    private long startDownAt;

    /**
     * START on a game in a section: play it straight away (A opens its page first). True when
     * done; false gives START its usual job (settings).
     */
    protected boolean onQuickPlay() {
        return false;
    }

    /** Picks up the running game, or starts the one played last. */
    protected void resumeLastGame() {
        if (!(this instanceof com.limelight.pocket.ui.QuickResumePanel.Source)) {
            return;
        }
        com.limelight.pocket.ui.QuickResumePanel.Source source = (com.limelight.pocket.ui.QuickResumePanel.Source) this;
        com.limelight.pocket.ui.QuickResumePanel.Entry best = null;
        for (com.limelight.pocket.ui.QuickResumePanel.Entry e : source.quickResumeEntries()) {
            if (best == null || (e.running && !best.running)
                    || (e.running == best.running && e.lastPlayed > best.lastPlayed)) {
                best = e;
            }
        }
        if (best != null) {
            PocketSounds.play(this, PocketSounds.SELECT);
            source.quickLaunch(best);
        }
    }

    /**
     * SELECT (the "view" button): on a library, the next layout (tiles, covers, wide, grids,
     * list); elsewhere Quick Resume. Held down, it's always Quick Resume. Screens can override.
     */
    protected void onSelectButton() {
        if (com.limelight.pocket.ui.LibraryLayout.isApplied(this)) {
            com.limelight.pocket.ui.Layouts.nextLibrary(this);
            PocketSounds.play(this, PocketSounds.NAV);
            // The screen builds itself again in the new layout (its chip says which)
            getWindow().getDecorView().postDelayed(() -> {
                if (!isFinishing()) {
                    recreate();
                }
            }, 90);
            return;
        }
        openQuickResume();
    }

    private long selectDownAt;

    /** Edge-to-edge, behind the camera cutout, no system bars: the whole 1080p panel is ours. */
    public static void applyFullscreenWindow(android.app.Activity activity) {
        android.view.Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        window.setStatusBarColor(0);
        window.setNavigationBarColor(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No dark scrim behind a bar that is pulled out for a moment
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        hideSystemBars(activity);
        keepBarsHidden(activity);
        preferHighestRefreshRate(activity);
    }

    /**
     * The screen's fastest refresh rate for the menus (a 120 Hz panel scrolls and animates at
     * 120): the same resolution, the highest rate it offers. The stream sets its own.
     */
    private static void preferHighestRefreshRate(android.app.Activity a) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        try {
            @SuppressWarnings("deprecation")
            android.view.Display display = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? a.getDisplay() : a.getWindowManager().getDefaultDisplay();
            if (display == null) {
                return;
            }
            android.view.Display.Mode now = display.getMode();
            android.view.Display.Mode best = now;
            for (android.view.Display.Mode m : display.getSupportedModes()) {
                if (m.getPhysicalWidth() == now.getPhysicalWidth() && m.getPhysicalHeight() == now.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate() + 0.5f) {
                    best = m;
                }
            }
            WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            if (lp.preferredDisplayModeId != best.getModeId()) {
                lp.preferredDisplayModeId = best.getModeId();
                a.getWindow().setAttributes(lp);
            }
        } catch (RuntimeException ignored) {
            // the system's choice then
        }
    }

    @SuppressWarnings("deprecation")
    private static final int IMMERSIVE_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    @SuppressWarnings("deprecation")
    public static void hideSystemBars(android.app.Activity activity) {
        View decor = activity.getWindow().getDecorView();
        // Both ways of asking: some handhelds' system only honours the older flags for the
        // navigation handle (the white line at the bottom stayed up)
        decor.setSystemUiVisibility(IMMERSIVE_FLAGS);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), decor);
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.hide(WindowInsetsCompat.Type.navigationBars());
    }

    /**
     * Whenever the bars come back (a swipe from the edge, a dialog, the keyboard, the system
     * deciding to show them), they go away again after a moment while the screen has focus.
     */
    @SuppressWarnings("deprecation")
    private static void keepBarsHidden(final android.app.Activity activity) {
        final View decor = activity.getWindow().getDecorView();
        final Runnable again = () -> {
            if (!activity.isFinishing() && activity.hasWindowFocus()) {
                hideSystemBars(activity);
            }
        };
        decor.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN))
                    != (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN)) {
                decor.removeCallbacks(again);
                decor.postDelayed(again, 1600);
            }
        });
    }

    /** A dialog that keeps the screen full screen (no bars while it's up). */
    public static void showImmersive(android.app.Dialog dialog) {
        android.view.Window w = dialog.getWindow();
        if (w == null) {
            dialog.show();
            return;
        }
        // Not focusable while it appears, so Android doesn't bring the bars back for it
        w.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        dialog.show();
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        c.hide(WindowInsetsCompat.Type.systemBars());
        w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    @Override
    protected void onResume() {
        PocketPrefs.setLayoutScope(layoutScope());
        super.onResume();
        // Back from a game: no more watching for its achievements
        com.limelight.pocket.retro.TrophyWatch.stop(this);
        // The logo size may have been changed in Settings meanwhile
        applyHeroLogoSize();
        PocketMusic.screenShown(this);
        com.limelight.pocket.home.PlayTime.resumed(this);
        // Just switched layout: which one it is now
        getWindow().getDecorView().post(() -> com.limelight.pocket.ui.Layouts.showPendingChip(this));
        // A notification that comes in while a console screen is up: a short note about it
        com.limelight.pocket.device.PocketNotifications.setListener(note -> {
            if (!isFinishing() && PocketPrefs.prefs(this).getBoolean("pocket_announce_notifications", true)) {
                // The whole notification, in the console's own card (a toast cut it short)
                com.limelight.pocket.global.MessageBanner.showIn(this, note);
            }
        });
        if ((createdWithScale != 0 && createdWithScale != PocketPrefs.uiScalePercent(this))
                || (createdWithLook != null && !createdWithLook.equals(lookSignature()))) {
            // Interface size or look changed (settings, first boot setup): rebuild this screen
            recreate();
            return;
        }
        // Handheld or docked (on a TV): room at the edges, the TV icon by the clock
        DockMode.addListener(dockListener);
        applyDockLook();
        // A crash or caught error from last time: show it once, with a Copy button
        CrashReporter.showPendingReport(this);
        // A stream that was suspended (screen off, Home) or is switching quality comes back now
        maybeAutoResume();
        if (pendingResume == null) {
            maybeShowIntro();
            maybeStartOnboarding();
        }
        if (launchLayer != null) {
            if (launchCoverFresh) {
                launchCoverFresh = false;
            }
            else {
                launchCoverActive = false;
                returnFromLaunch();
            }
        }
        else if (!entrancePlayed && bootOverlay == null && pendingResume == null) {
            entrancePlayed = true;
            View main = findViewById(R.id.pocketMain);
            if (main != null) {
                main.post(this::playEntrance);
            }
        }
        menuHotkey = PocketPrefs.getMenuHotkey(this);
        m2Hotkey = PocketPrefs.getM2Hotkey(this);
        enterImmersive();
        try {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (RuntimeException ignored) {
        }
        statusRunning = true;
        statusHandler.removeCallbacks(statusTick);
        statusHandler.post(statusTick);
    }

    /** The hero that UP lifts (home and library), when the screen has one. */
    protected com.limelight.pocket.ui.HeroFocus heroFocus;

    @Override
    protected void onStop() {
        super.onStop();
        if (heroFocus != null) {
            heroFocus.reset();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        lastInputWasButton = false;
        if (heroFocus != null) {
            heroFocus.onTouch(event);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        // Lists draw their games' own rings: an empty list that gets the focus must not show
        // Android's grey focus box
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            quietFocusBoxes(findViewById(android.R.id.content));
        }
    }

    private static void quietFocusBoxes(View v) {
        if (v instanceof androidx.recyclerview.widget.RecyclerView && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.setDefaultFocusHighlightEnabled(false);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                quietFocusBoxes(g.getChildAt(i));
            }
        }
    }

    /** The last thing the user did was press a button (the next screen opens ready for buttons). */
    private static boolean lastInputWasButton = true;

    @Override
    protected void onPause() {
        controlBackPress.clear();
        normalBPress.clear();
        cancelStreamDispatch(streamDispatchTicket);
        super.onPause();
        DockMode.removeListener(dockListener);
        // The other screens read their own layout (or the shared one)
        PocketPrefs.setLayoutScope(null);
        com.limelight.pocket.device.PocketNotifications.setListener(null);
        PocketMusic.screenHidden();
        pausedSinceLaunch = true;
        abandonBoot();
        if (coachMark != null) {
            // Leaving the screen: the tip goes (it was marked as seen)
            android.view.ViewParent parent = coachMark.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(coachMark);
            }
            coachMark = null;
        }
        if (pendingResume != null) {
            // Left before the stream came back: keep the ticket for next time
            statusHandler.removeCallbacks(resumeLaunch);
            ResumeTicket.putBack(this, pendingResume);
            clearPendingResume();
            removeResumeOverlay();
        }
        statusRunning = false;
        statusHandler.removeCallbacks(statusTick);
        try {
            unregisterReceiver(batteryReceiver);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            controlBackPress.clear();
            normalBPress.clear();
        }
        if (hasFocus) {
            enterImmersive();
            // Opened with a button (L1 / R1, A...) while Android still thought the screen was
            // being touched: leave touch mode, so the screen's first game gets its focus ring
            // straight away instead of on the next press
            View decor = getWindow().getDecorView();
            if (lastInputWasButton && decor.isInTouchMode()) {
                PocketUi.quietFor(400);
                View focus = decor.findFocus();
                if (focus != null) {
                    focus.requestFocusFromTouch();
                }
                else {
                    decor.requestFocusFromTouch();
                }
            }
        }
    }

    protected void enterImmersive() {
        hideSystemBars(this);
    }

    /** Called every couple of seconds while the screen is visible. */
    protected void onStatusTick() {
    }

    /** START / menu button: open the console settings. Screens can override. */
    protected void onMenuButton() {
        openQuickSettings();
    }

    /** X button. Return true if the screen used it. */
    protected boolean onButtonX() {
        return false;
    }

    /** L1 / R1. Return true if the screen used it. */
    protected boolean onShoulder(int direction) {
        return false;
    }

    /** L2 / R2. Return true if the screen used it. */
    protected boolean onTrigger(int direction) {
        return false;
    }

    public void openQuickSettings() {
        PocketSounds.play(this, PocketSounds.OPEN);
        startActivity(new Intent(this, QuickSettingsActivity.class));
    }

    // ---- Seamless resume ------------------------------------------------------------------

    private Intent pendingResume;
    private boolean resumePreparing;
    private boolean resumeSignIn;
    private com.limelight.pocket.ui.ResumeOverlay resumeOverlay;
    private final Runnable resumeLaunch = this::launchPendingResume;

    /** Screens where a suspended stream may come back by itself. */
    protected boolean allowsAutoResume() {
        return true;
    }

    private void maybeAutoResume() {
        if (pendingResume != null || !allowsAutoResume()) {
            return;
        }
        if (HorizonStartupRecovery.take(this)) {
            ResumeTicket.disarm(this);
            Toast.makeText(this, R.string.horizon_resume_paused_after_crash, Toast.LENGTH_LONG).show();
            return;
        }
        if (!ResumeTicket.isArmed(this)) {
            return;
        }
        int reason = ResumeTicket.reason(this);
        if (reason == ResumeTicket.REASON_SUSPEND && !PocketPrefs.seamlessResume(this)) {
            ResumeTicket.disarm(this);
            return;
        }
        Intent intent = ResumeTicket.take(this);
        if (intent == null) {
            return;
        }
        pendingResume = intent;
        resumePreparing = false;
        resumeSignIn = false;

        String caption;
        String subtitle;
        if (reason == ResumeTicket.REASON_SUSPEND) {
            caption = getString(R.string.pocket_resume_caption);
            subtitle = getString(ResumeTicket.isUsb(intent) ? R.string.pocket_resume_sub_usb : R.string.pocket_resume_sub);
        }
        else if (reason == ResumeTicket.REASON_SIGNIN) {
            caption = getString(R.string.pocket_signin_switch_caption);
            subtitle = getString(R.string.pocket_signin_switch_sub);
        }
        else if (reason == ResumeTicket.REASON_RECOVER) {
            caption = getString(R.string.pocket_recover_caption);
            subtitle = getString(R.string.pocket_recover_sub,
                    intent.getIntExtra(ResumeTicket.EXTRA_RECOVER, 1), 3);
        }
        else {
            caption = getString(reason == ResumeTicket.REASON_ADAPTIVE
                    ? R.string.pocket_adaptive_caption : R.string.pocket_quality_caption);
            int kbps = intent.getIntExtra(ResumeTicket.EXTRA_BITRATE, 0);
            int fps = intent.getIntExtra(ResumeTicket.EXTRA_FPS, 0);
            StringBuilder sb = new StringBuilder();
            if (kbps > 0) {
                sb.append(Math.round(kbps / 1000f)).append(" Mbps");
            }
            if (fps > 0) {
                if (sb.length() > 0) {
                    sb.append("  \u00b7  ");
                }
                sb.append(fps).append(" FPS");
            }
            subtitle = sb.toString();
        }

        removeResumeOverlay();
        resumeOverlay = new com.limelight.pocket.ui.ResumeOverlay(this);
        resumeOverlay.bind(caption, ResumeTicket.appName(intent), subtitle,
                hintA(this, R.string.pocket_hint_now), hintB(this, R.string.pocket_hint_cancel));
        ViewGroup content = findViewById(android.R.id.content);
        content.addView(resumeOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        long delay = reason == ResumeTicket.REASON_SUSPEND ? 1500
                : (reason == ResumeTicket.REASON_RECOVER || reason == ResumeTicket.REASON_SIGNIN) ? 1800 : 700;
        resumeOverlay.start(delay);
        PocketSounds.play(this, PocketSounds.OPEN);
        statusHandler.postDelayed(resumeLaunch, delay);
    }

    private void launchPendingResume() {
        if (pendingResume == null || resumePreparing) return;
        if (!ResumeTicket.owns(pendingResume)) { cancelResume(); return; }
        statusHandler.removeCallbacks(resumeLaunch);
        if (ResumeTicket.isPreviousStreamStopping()) {
            // Let the last stream finish saying goodbye to the PC first
            if (resumeOverlay != null) {
                resumeOverlay.holdFull();
            }
            statusHandler.postDelayed(resumeLaunch, 150);
            return;
        }
        final Intent intent = pendingResume;
        resumePreparing = true;
        statusHandler.postDelayed(() -> {
            if (pendingResume == intent) cancelResume();
        }, 15000L);
        if (ResumeTicket.isUsb(intent)) {
            new Thread(() -> {
                UsbBridge.ensureConnected();
                runOnUiThread(() -> startResumedStream(intent));
            }).start();
        }
        else {
            startResumedStream(intent);
        }
    }

    private void startResumedStream(Intent intent) {
        if (pendingResume != intent) return;
        if (!ResumeTicket.owns(intent) || isFinishing() || isDestroyed() || signInChecking) {
            cancelResume();
            return;
        }
        resumeSignIn = true;
        runWhenSignedIn(intent, () -> {
            if (pendingResume != intent) return;
            if (isFinishing() || isDestroyed() || !ResumeTicket.claimDispatch(intent)) {
                cancelResume();
                return;
            }
            clearPendingResume();
            PocketSounds.play(this, PocketSounds.SELECT);
            startActivity(intent);
            if (PocketPrefs.animationsEnabled(this)) {
                overridePendingTransition(R.anim.pocket_launch_in, R.anim.pocket_launch_out);
            }
            statusHandler.postDelayed(this::removeResumeOverlay, 600);
        }, () -> { if (pendingResume == intent) cancelResume(); });
    }

    // ---- Windows sign-in screen -------------------------------------------------------------

    private boolean signInChecking;
    private long signInGeneration;
    private long streamDispatchTicket;
    private final com.limelight.pocket.core.HorizonStreamDispatch streamDispatch =
            com.limelight.pocket.core.HorizonStreamDispatch.shared();

    private long dispatchEpoch() {
        return com.limelight.pocket.core.HorizonLaunchRequests.shared().generation();
    }

    /** Reserve one deferred stream dispatch for this screen. UI thread only. */
    public long beginStreamDispatch() {
        if (isFinishing() || isDestroyed()) return 0;
        if (pendingResume != null) cancelResume();
        long now = android.os.SystemClock.elapsedRealtime();
        long epoch = dispatchEpoch();
        if (streamDispatch.pending(streamDispatchTicket, epoch, now)) return 0;
        if (streamDispatchTicket != 0) cancelStreamDispatch(streamDispatchTicket);
        if (signInChecking) return 0; // do not steal a separate pending resume check
        final long ticket = streamDispatch.begin(epoch, now);
        streamDispatchTicket = ticket;
        if (ticket != 0) statusHandler.postDelayed(() -> cancelStreamDispatch(ticket),
                com.limelight.pocket.core.HorizonStreamDispatch.LIMIT_MS);
        return ticket;
    }

    public boolean arriveStreamDispatch(long ticket, com.limelight.pocket.core.HorizonStreamDispatch.Stage stage) {
        if (streamDispatchTicket != ticket || ticket == 0) return false;
        long now = android.os.SystemClock.elapsedRealtime();
        long epoch = dispatchEpoch();
        if (isFinishing() || isDestroyed() || !streamDispatch.pending(ticket, epoch, now)) {
            cancelStreamDispatch(ticket);
            return false;
        }
        if (!streamDispatch.arrive(ticket, stage, epoch, now)) return false;
        streamDispatchTicket = 0;
        return true;
    }

    public void cancelStreamDispatch(long ticket) {
        if (ticket == 0 || streamDispatchTicket != ticket) return;
        streamDispatch.cancel(ticket);
        streamDispatchTicket = 0;
        signInGeneration++;
        signInChecking = false;
        dropLaunchLayer();
    }

    /**
     * Starting a stream while the PC is still at the Windows sign-in screen: Vibepollo/Apollo
     * restarts its display helper at sign-in, which drops a virtual display made before it, and
     * Windows then remembers that screen as off (later streams mirror until the device is paired
     * again). So at the sign-in screen the stream shows the PC's main screen (all you need to
     * sign in remotely), and once you've signed in the stream switches to the virtual display by
     * itself (see Game). The Artemis Pocket PC helper only runs after sign-in: when it has
     * answered from this PC before but doesn't now, the PC is at the sign-in screen.
     * Without the helper nothing changes.
     */
    public void runWhenSignedIn(final Intent gameIntent, final Runnable start, final Runnable onCancel) {
        final String uuid = gameIntent.getStringExtra(com.limelight.Game.EXTRA_PC_UUID);
        final String host = gameIntent.getStringExtra(com.limelight.Game.EXTRA_HOST);
        if (signInChecking) {
            if (onCancel != null) onCancel.run();
            return;
        }
        // Off unless turned on (Settings, Streaming): a slow answer from the helper (a busy PC, a
        // full-screen app in front) made a stream start on the PC's own screen and only switch
        // to the virtual display later, or never. The stream goes to the virtual display at once,
        // as in Artemis.
        if (!PocketPrefs.prefs(this).getBoolean(PocketPrefs.KEY_SIGNIN_HELPER, false)
                || uuid == null || host == null || !PocketPrefs.companionRecentlySeen(this, uuid)
                || !gameIntent.getBooleanExtra(com.limelight.Game.EXTRA_VDISPLAY,
                com.limelight.preferences.PreferenceConfiguration.readPreferences(this).useVirtualDisplay)) {
            start.run();
            return;
        }
        signInChecking = true;
        final long check = ++signInGeneration;
        new Thread(() -> {
            final boolean signedIn = PcCompanion.answersQuickly(getApplicationContext(), host, uuid);
            runOnUiThread(() -> {
                if (check != signInGeneration) return;
                signInChecking = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!signedIn) {
                    gameIntent.putExtra(com.limelight.Game.EXTRA_VDISPLAY, false);
                    gameIntent.putExtra(com.limelight.Game.EXTRA_POCKET_SIGNIN, true);
                }
                start.run();
            });
        }, "PocketSignInCheck").start();
    }

    private void clearPendingResume() {
        statusHandler.removeCallbacks(resumeLaunch);
        pendingResume = null;
        resumePreparing = false;
        if (resumeSignIn) {
            signInGeneration++;
            signInChecking = false;
        }
        resumeSignIn = false;
    }
    private void cancelResume() {
        ResumeTicket.cancel(pendingResume);
        clearPendingResume();
        PocketSounds.play(this, PocketSounds.BACK);
        if (resumeOverlay != null) {
            resumeOverlay.dismiss();
            resumeOverlay = null;
        }
    }

    private void removeResumeOverlay() {
        if (resumeOverlay != null) {
            ViewGroup parent = (ViewGroup) resumeOverlay.getParent();
            if (parent != null) {
                parent.removeView(resumeOverlay);
            }
            resumeOverlay = null;
        }
    }

    // ---- Boot sequence (once per app start) -----------------------------------------------

    /**
     * The app was just launched (its icon, the device's home): the boot is owed once. Nothing
     * else plays it: not a section opening, not a screen Android brings back after it closed the
     * app in the background to free memory (a new process, where a plain "not shown yet" flag
     * would be false again and the boot would replay over Streaming).
     */
    private static boolean bootPending;
    /** This screen was just launched fresh (from the app icon): it opens with the boot sequence. */
    private boolean bootOnLaunch;
    private com.limelight.pocket.ui.BootOverlay bootOverlay;

    /** This screen hands the boot to the screen it opens straight away (Streaming opens Home). */
    protected boolean deferIntro() {
        return false;
    }

    private void maybeShowIntro() {
        // Once per launch of the app, on the first screen that stays; never again when coming
        // back to a screen (a section, a game, a menu)
        if (!bootPending || !allowsAutoResume() || deferIntro() || PreviewSeed.skipsBoot(this)) {
            return;
        }
        bootPending = false;
        bootOnLaunch = false;
        if (!PocketPrefs.introAnimation(this) || !PocketPrefs.animationsEnabled(this)) {
            return;
        }
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        bootOverlay = new com.limelight.pocket.ui.BootOverlay(this);
        bootOverlay.setReveal(content.getChildCount() > 0 ? content.getChildAt(0) : null);
        final boolean[] built = {false};
        // The home screen builds itself while the boot's wave of light reveals it
        bootOverlay.setOnReveal(() -> {
            built[0] = true;
            entrancePlayed = true;
            playEntrance(true);
        });
        bootOverlay.setListener(() -> {
            bootOverlay = null;
            entrancePlayed = true;
            if (!built[0]) {
                playEntrance();
            }
            // Whatever waited for the boot to finish (opening the last PC, USB connect) goes now
            List<Runnable> waiting = new ArrayList<>(afterBoot);
            afterBoot.clear();
            for (Runnable r : waiting) {
                r.run();
            }
        });
        content.addView(bootOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private final List<Runnable> afterBoot = new ArrayList<>();

    private String lookSignature() {
        return PocketPrefs.accentColor(this) + "|" + PocketPrefs.backdrop(this) + "|" + PocketPrefs.liveGlass(this)
                + "|" + PocketPrefs.frost(this) + "|" + PocketPrefs.viewMode(this) + "|" + PocketPrefs.cardStyle(this)
                + "|" + PocketPrefs.focusStyle(this);
    }

    // ---- First boot setup & first-time tips ---------------------------------------------------

    /** Screens that may open the first boot setup (the home screens). */
    protected boolean allowsOnboarding() {
        return allowsAutoResume();
    }

    private void maybeStartOnboarding() {
        if (!allowsOnboarding() || !PocketPrefs.onboardingPending(this) || OnboardingActivity.isOpen()) {
            return;
        }
        // After the boot sequence, with a moment to see the home screen arrive
        runAfterBoot(() -> statusHandler.postDelayed(() -> {
            if (isFinishing() || !statusRunning || isBooting() || !PocketPrefs.onboardingPending(this)
                    || OnboardingActivity.isOpen()) {
                return;
            }
            startActivity(new Intent(this, OnboardingActivity.class));
            overridePendingTransition(R.anim.pocket_pop_enter, R.anim.pocket_pop_exit);
        }, 650));
    }

    private com.limelight.pocket.ui.CoachMark coachMark;
    private Runnable pendingTip;

    /**
     * Shows a first-time tip once the person has rested on target for a moment (moving on
     * cancels it; the next resting place tries again).
     */
    protected void scheduleTip(final String tip, final View target, final float targetRadiusDp,
                               final int titleRes, final int bodyRes) {
        if (target == null || PocketPrefs.tipSeen(this, tip)) {
            return;
        }
        if (pendingTip != null) {
            statusHandler.removeCallbacks(pendingTip);
        }
        pendingTip = () -> {
            pendingTip = null;
            View holder = target.getParent() instanceof View ? (View) target.getParent() : target;
            if (holder.hasFocus() || target.hasFocus()) {
                showTipOnce(tip, target, targetRadiusDp, titleRes, bodyRes);
            }
        };
        statusHandler.postDelayed(pendingTip, 1400);
    }

    /**
     * Shows a first-time tip pointing at target, once ever (Settings can bring tips back). Not
     * while something else is going on (boot, setup, a panel, a game launching).
     */
    public boolean showTipOnce(String tip, View target, float targetRadiusDp, int titleRes, int bodyRes) {
        if (target == null || coachMark != null || PocketPrefs.tipSeen(this, tip) || PocketPrefs.onboardingPending(this)
                || isBooting() || isConsolePanelShowing() || blockingCard != null || pendingResume != null
                || launchLayer != null || !hasWindowFocus() || !target.isAttachedToWindow()) {
            return false;
        }
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) {
            return false;
        }
        PocketPrefs.markTipSeen(this, tip);
        final com.limelight.pocket.ui.CoachMark mark = new com.limelight.pocket.ui.CoachMark(this, target, targetRadiusDp,
                getString(titleRes), getString(bodyRes));
        mark.setOnClosed(() -> {
            if (coachMark == mark) {
                coachMark = null;
            }
        });
        coachMark = mark;
        content.addView(mark, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        PocketSounds.play(this, PocketSounds.OPEN);
        return true;
    }

    /** Runs r now, or once the boot sequence on this screen has finished. */
    protected void runAfterBoot(Runnable r) {
        if (bootOverlay != null && !bootOverlay.isFinished()) {
            afterBoot.add(r);
        }
        else {
            r.run();
        }
    }

    protected boolean isBooting() {
        return bootOverlay != null && !bootOverlay.isFinished();
    }

    /**
     * Left mid-boot (another screen opened on top, or the app went to the background): stop it
     * here, silently, and let the next console screen that comes up play it from the start.
     */
    private void abandonBoot() {
        com.limelight.pocket.ui.BootOverlay overlay = bootOverlay;
        if (overlay == null) {
            return;
        }
        bootOverlay = null;
        afterBoot.clear();
        // Left mid-boot: it doesn't come back on the next screen (it would replay every time a
        // section opens)
        PocketSounds.releaseBoot();
        ViewGroup parent = (ViewGroup) overlay.getParent();
        if (parent != null) {
            parent.removeView(overlay);
        }
        View root = findViewById(R.id.pocketRoot);
        if (root != null) {
            root.setAlpha(1f);
            root.setScaleX(1f);
            root.setScaleY(1f);
        }
        entrancePlayed = false;
    }

    private void dismissIntro() {
        if (bootOverlay != null) {
            bootOverlay.skip();
        }
    }

    // ---- Console panels (options, game settings, connection test) -------------------------

    private StreamMenuView consolePanel;
    private int swallowKeyUp = -1;

    private StreamMenuView consolePanel() {
        if (consolePanel == null) {
            consolePanel = new StreamMenuView(this);
            consolePanel.setConfirmLabel(getString(R.string.pocket_sm_confirm));
            // Options sit at the right, like a console's options menu over its home screen
            consolePanel.setPlacement(true, 336);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // The screen behind is blurred while the panel is open: acrylic, it shows through
                consolePanel.setBaseColor(0xC714141A);
            }
            else {
                consolePanel.setBaseColor(0xF514141A);
            }
            consolePanel.setListener(() -> {
                PocketUi.setBackdropBlur(blurTarget(), false);
                onConsolePanelClosed();
            });
            ViewGroup content = findViewById(android.R.id.content);
            content.addView(consolePanel, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        consolePanel.bringToFront();
        return consolePanel;
    }

    /** The screen behind a panel, softly blurred while the panel is open. */
    private View blurTarget() {
        // The whole screen (art and all) blurs behind the acrylic panel
        View root = findViewById(R.id.pocketRoot);
        if (root != null && root != consolePanel) {
            return root;
        }
        View main = findViewById(R.id.pocketMain);
        if (main != null) {
            return main;
        }
        ViewGroup content = findViewById(android.R.id.content);
        View first = content != null && content.getChildCount() > 0 ? content.getChildAt(0) : null;
        return first != consolePanel ? first : null;
    }

    public boolean isConsolePanelShowing() {
        return consolePanel != null && consolePanel.isShowing();
    }

    protected final void focusConsolePanel() {
        if (!isConsolePanelShowing()) return;
        consolePanel.setFocusableInTouchMode(true);
        consolePanel.requestFocus();
    }

    private final MenuKeyOwnership consolePanelKeys = new MenuKeyOwnership();

    protected final boolean dispatchConsolePanelKey(KeyEvent event) {
        boolean showing = isConsolePanelShowing();
        if (!consolePanelKeys.consume(event, showing)) return false;
        lastInputWasButton = true;
        if (showing) consolePanel.handleKey(event);
        return true;
    }

    protected void onConsolePanelClosed() { }

    /** A glass side panel driven by the controller, like the in-stream menu. */
    public void showConsolePanel(String title, String subtitle, String stats, List<StreamMenuView.Item> items) {
        PocketUi.setBackdropBlur(blurTarget(), true);
        consolePanel().show(title != null ? title : "", subtitle != null ? subtitle : "", stats, items,
                hintA(this, R.string.pocket_hint_select), hintB(this, R.string.pocket_hint_back),
                new Hint("\u25c0\u25b6", 0xFFFFFFFF, getString(R.string.pocket_hint_change)));
    }

    public void pushConsolePage(String title, List<StreamMenuView.Item> items) {
        if (consolePanel != null && consolePanel.isShowing()) {
            consolePanel.push(title, items);
        }
    }

    public void replaceConsolePage(List<StreamMenuView.Item> original, List<StreamMenuView.Item> items) {
        if (consolePanel != null) consolePanel.replaceCurrentPage(original, items);
    }

    /** Opens the console panel with a whole view as its page (the Headphones Center). */
    public void showConsoleView(String title, String subtitle, android.view.View content) {
        PocketUi.setBackdropBlur(blurTarget(), true);
        consolePanel().showView(title != null ? title : "", subtitle != null ? subtitle : "", content,
                hintA(this, R.string.pocket_hint_select), hintB(this, R.string.pocket_hint_back));
    }

    /** Pushes a whole view as a sub page of the open console panel. */
    public void pushConsoleView(String title, android.view.View content) {
        if (consolePanel != null && consolePanel.isShowing()) consolePanel.pushView(title, content);
    }

    public boolean isConsoleViewCurrent(android.view.View content) {
        return consolePanel != null && consolePanel.isCurrentView(content);
    }

    public boolean consoleHoldsView(android.view.View content) {
        return consolePanel != null && consolePanel.holdsView(content);
    }

    public boolean popConsolePage() {
        return consolePanel != null && consolePanel.popPage();
    }

    public boolean isConsolePageCurrent(List<StreamMenuView.Item> items) {
        return consolePanel != null && consolePanel.isCurrentPage(items);
    }

    public boolean refreshConsolePage(List<StreamMenuView.Item> original, List<StreamMenuView.Item> items) {
        return consolePanel != null && consolePanel.refreshLivePage(original, items);
    }

    public void hideConsolePanel() {
        if (consolePanel != null) {
            consolePanel.hide();
        }
    }

    /**
     * Shows this screen's options for a card (its context menu) as a console panel instead of
     * Android's plain menu. extra items go first.
     */
    protected void showOptionsFor(View anchor, String subtitle, List<StreamMenuView.Item> extra) {
        final String[] header = {null};
        final Menu base = new PopupMenu(this, anchor != null ? anchor : getWindow().getDecorView()).getMenu();
        ContextMenu menu = (ContextMenu) java.lang.reflect.Proxy.newProxyInstance(ContextMenu.class.getClassLoader(),
                new Class<?>[]{ContextMenu.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setHeaderTitle":
                            header[0] = args[0] instanceof Integer ? getString((Integer) args[0]) : String.valueOf(args[0]);
                            return proxy;
                        case "setHeaderIcon":
                        case "setHeaderView":
                            return proxy;
                        case "clearHeader":
                            header[0] = null;
                            return null;
                        default:
                            return method.invoke(base, args);
                    }
                });
        try {
            onCreateContextMenu(menu, anchor, null);
        } catch (RuntimeException e) {
            CrashReporter.saveCaught(this, e);
            return;
        }
        List<StreamMenuView.Item> items = new ArrayList<>();
        if (extra != null) {
            items.addAll(extra);
        }
        addMenuItems(base, items);
        if (!items.isEmpty()) {
            showConsolePanel(header[0], subtitle, null, items);
        }
    }

    private void addMenuItems(Menu menu, List<StreamMenuView.Item> out) {
        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            if (!item.isVisible()) {
                continue;
            }
            final String title = String.valueOf(item.getTitle());
            if (item.hasSubMenu()) {
                final SubMenu sub = item.getSubMenu();
                out.add(StreamMenuView.Item.submenu(title, () -> {
                    List<StreamMenuView.Item> page = new ArrayList<>();
                    addMenuItems(sub, page);
                    pushConsolePage(title, page);
                }));
            }
            else if (item.isCheckable()) {
                out.add(StreamMenuView.Item.action(title, () -> onContextItemSelected(item))
                        .withValue(getString(item.isChecked() ? R.string.pocket_on : R.string.pocket_off)));
            }
            else {
                out.add(StreamMenuView.Item.action(title, () -> onContextItemSelected(item)));
            }
        }
    }

    // ---- Blocking card (waking a PC, ...) ---------------------------------------------------

    private com.limelight.pocket.ui.ResumeOverlay blockingCard;
    private Runnable blockingCancel;

    public void showBlockingCard(String caption, String title, String subtitle, long durationMs, Runnable onCancel) {
        hideBlockingCard();
        blockingCard = new com.limelight.pocket.ui.ResumeOverlay(this);
        blockingCard.bind(caption, title, subtitle, hintB(this, R.string.pocket_hint_cancel));
        ViewGroup content = findViewById(android.R.id.content);
        content.addView(blockingCard, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        blockingCard.start(durationMs);
        blockingCancel = onCancel;
    }

    public void hideBlockingCard() {
        if (blockingCard != null) {
            blockingCard.dismiss();
            blockingCard = null;
        }
        blockingCancel = null;
        blockingConfirm = null;
        blockingAlways = null;
    }

    /** Y on the switch question: switch, and never ask again. */
    private Runnable blockingAlways;

    /**
     * Another game is running on the PC: one question, quit it and play this one? A switches,
     * B cancels, Y switches and stops asking. With "Switch games without asking" on, it just
     * switches (the PC closes the running game and starts the new one).
     */
    public void askSwitchGame(String running, String next, final Runnable switchNow) {
        if (PocketPrefs.switchWithoutAsking(this)) {
            switchNow.run();
            return;
        }
        // The "Starting" screen steps aside for the question (it held back every button, so the
        // question couldn't be answered and the screen stayed stuck there)
        dropLaunchLayer();
        hideBlockingCard();
        blockingCard = new com.limelight.pocket.ui.ResumeOverlay(this);
        String runningName = running != null && !running.isEmpty() ? running : getString(R.string.pocket_switch_other);
        blockingCard.bind(getString(R.string.pocket_switch_caption, runningName),
                getString(R.string.pocket_switch_title, next),
                getString(R.string.pocket_switch_sub, runningName),
                new Hint("A", ContextCompat.getColor(this, R.color.pocket_btn_a), getString(R.string.pocket_switch_yes)),
                hintY(this, R.string.pocket_switch_always),
                hintB(this, R.string.pocket_hint_cancel));
        blockingCard.hideProgress();
        ViewGroup content = findViewById(android.R.id.content);
        content.addView(blockingCard, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        blockingCard.start(1);
        PocketSounds.play(this, PocketSounds.OPEN);
        blockingCancel = null;
        blockingConfirm = () -> {
            hideBlockingCard();
            switchNow.run();
        };
        blockingAlways = () -> {
            hideBlockingCard();
            PocketPrefs.setSwitchWithoutAsking(this, true);
            Toast.makeText(this, R.string.pocket_switch_always_on, Toast.LENGTH_LONG).show();
            switchNow.run();
        };
    }

    private Runnable blockingConfirm;

    /** A blocking card where A does something (e.g. "Start anyway") and B cancels. */
    public void showWaitCard(String caption, String title, String subtitle, long durationMs,
                             Runnable onCancel, Runnable onConfirm) {
        dropLaunchLayer();
        hideBlockingCard();
        blockingCard = new com.limelight.pocket.ui.ResumeOverlay(this);
        blockingCard.bind(caption, title, subtitle, hintA(this, R.string.pocket_hint_start_anyway),
                hintB(this, R.string.pocket_hint_cancel));
        ViewGroup content = findViewById(android.R.id.content);
        content.addView(blockingCard, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        blockingCard.start(durationMs);
        blockingCancel = onCancel;
        blockingConfirm = onConfirm;
    }

    public boolean isBlockingCardShowing() {
        return blockingCard != null;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isConsolePanelShowing() && (event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
            float hx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            float hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            float x = Math.abs(hx) > 0.5f ? hx : event.getAxisValue(MotionEvent.AXIS_X);
            float y = Math.abs(hy) > 0.5f ? hy : event.getAxisValue(MotionEvent.AXIS_Y);
            consolePanel.onStick(x, y);
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private final HorizonBackKey.Press controlBackPress = new HorizonBackKey.Press();
    private final HorizonBackKey.Press normalBPress = new HorizonBackKey.Press();

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        lastInputWasButton = true;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    userNavigated = true;
                    break;
                default:
                    break;
            }
        }
        if (launchLayer != null && blockingCard == null) {
            if (streamDispatchTicket != 0 && (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B
                    || event.getKeyCode() == KeyEvent.KEYCODE_BACK)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    swallowKeyUp = event.getKeyCode();
                    cancelStreamDispatch(streamDispatchTicket);
                }
                return true;
            }
            return true; // a game is launching: buttons wait (but never a question on top of it)
        }
        if (quickPanel != null) {
            return quickPanel.onKey(event);
        }
        if (coachMark != null) {
            int k = event.getKeyCode();
            if (k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN) {
                // Any button closes a tip (and does nothing else)
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    coachMark.dismiss();
                    swallowKeyUp = k;
                }
                return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && !isConsolePanelShowing()) {
            // (an open panel pulses its own hints)
            pulseHint(findViewById(R.id.pocketHintBar), event.getKeyCode());
        }
        if (blockingCard != null) {
            int k = event.getKeyCode();
            if (blockingAlways != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && k == KeyEvent.KEYCODE_BUTTON_Y) {
                Runnable always = blockingAlways;
                swallowKeyUp = k;
                PocketSounds.play(this, PocketSounds.SELECT);
                always.run();
                return true;
            }
            if (blockingConfirm != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && (k == KeyEvent.KEYCODE_BUTTON_A || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                Runnable confirm = blockingConfirm;
                swallowKeyUp = k;
                PocketSounds.play(this, PocketSounds.SELECT);
                confirm.run();
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && (k == KeyEvent.KEYCODE_BUTTON_B || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE)) {
                Runnable cancel = blockingCancel;
                hideBlockingCard();
                swallowKeyUp = k;
                PocketSounds.play(this, PocketSounds.BACK);
                if (cancel != null) {
                    cancel.run();
                }
            }
            return true;
        }
        if (dispatchConsolePanelKey(event)) return true;
        if (swallowKeyUp >= 0 && event.getKeyCode() == swallowKeyUp) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                swallowKeyUp = -1;
                return true;
            }
            if (event.getRepeatCount() > 0) {
                return true;
            }
            swallowKeyUp = -1; // a fresh press
        }
        if (bootOverlay != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                dismissIntro();
            }
            return true;
        }
        if (pendingResume != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_BUTTON_A:
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        launchPendingResume();
                        break;
                    case KeyEvent.KEYCODE_BUTTON_B:
                    case KeyEvent.KEYCODE_BACK:
                    case KeyEvent.KEYCODE_ESCAPE:
                        swallowKeyUp = event.getKeyCode();
                        cancelResume();
                        break;
                    default:
                        break;
                }
            }
            return true;
        }
        boolean m1 = PocketPrefs.matchesHotkey(menuHotkey, event);
        if (m1 || PocketPrefs.matchesHotkey(m2Hotkey, event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0 && !hotkeyDown) {
                    hotkeyDown = true;
                    // M1 / M2 here: what they are set to do on the home screens
                    runLauncherShortcut(ButtonActions.getLauncher(this, m1 ? ButtonActions.BUTTON_MENU : ButtonActions.BUTTON_M2));
                }
            }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                hotkeyDown = false;
            }
            return true;
        }
        if (com.limelight.pocket.global.GlobalButtons.enabled(this)
                && com.limelight.pocket.global.GlobalButtons.get(this,
                        com.limelight.pocket.global.GlobalButtons.BACK,
                        com.limelight.pocket.global.GlobalButtons.TAP)
                        == com.limelight.pocket.global.GlobalButtons.Action.CONTROL
                && PocketPrefs.matchesBack(PocketPrefs.getBackKey(this),event)) {
            if (controlBackPress.accept(event)) {
                com.limelight.pocket.global.ControlCenterActivity.open(this);
            }
            return true;
        }
        controlBackPress.clear();
        if (heroFocus != null && heroFocus.handleKey(event)) {
            return true;
        }

        int keyCode = event.getKeyCode();
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        // Consume B directly: Android must not synthesize a BACK fallback from an unhandled B.
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (normalBPress.accept(event)) {
                PocketSounds.play(this, PocketSounds.BACK);
                onBackPressed();
            }
            return true;
        }
        if (up && keyCode == KeyEvent.KEYCODE_BACK) {
            PocketSounds.play(this, PocketSounds.BACK);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_START:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    startDownAt = event.getEventTime();
                }
                if (up) {
                    // On a game: play it now (A opens its page). Held, or anywhere else: settings
                    boolean held = startDownAt > 0 && event.getEventTime() - startDownAt >= 600;
                    startDownAt = 0;
                    if (held || !onQuickPlay()) {
                        onMenuButton();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (up) {
                    onButtonX();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    selectDownAt = event.getEventTime();
                }
                if (up) {
                    if (selectDownAt > 0 && event.getEventTime() - selectDownAt >= 550) {
                        openQuickResume();
                    }
                    else {
                        onSelectButton();
                    }
                    selectDownAt = 0;
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                if (up) {
                    onShoulder(-1);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (up) {
                    onShoulder(1);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (up) {
                    onTrigger(-1);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (up) {
                    onTrigger(1);
                }
                return true;
        }
        if (super.dispatchKeyEvent(event)) {
            return true;
        }
        // Nothing on screen took the move: Pocket Nexus moves the focus itself, so Android
        // doesn't play its own navigation click (it ignores the views' sound settings)
        return event.getAction() == KeyEvent.ACTION_DOWN && moveFocusQuietly(keyCode);
    }

    private final android.graphics.Rect focusRect = new android.graphics.Rect();

    private boolean moveFocusQuietly(int keyCode) {
        int direction;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                direction = View.FOCUS_LEFT;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                direction = View.FOCUS_RIGHT;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                direction = View.FOCUS_UP;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                direction = View.FOCUS_DOWN;
                break;
            default:
                return false;
        }
        View root = getWindow().getDecorView();
        View focused = root.findFocus();
        if (focused == null || !(root instanceof ViewGroup)) {
            return false; // nothing focused yet: Android picks the first view
        }
        try {
            if (root.dispatchUnhandledMove(focused, direction)) {
                return true;
            }
            View next = focused.focusSearch(direction);
            if (next == null || next == focused) {
                return false;
            }
            focused.getFocusedRect(focusRect);
            ((ViewGroup) root).offsetDescendantRectToMyCoords(focused, focusRect);
            ((ViewGroup) root).offsetRectIntoDescendantCoords(next, focusRect);
            return next.requestFocus(direction, focusRect);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- Status bar ---------------------------------------------------------------------

    private TextView musicChip;
    private String musicShown;

    /**
     * What's playing in any music app (YouTube Music, Spotify...) as a small chip in the status
     * bar, like a console's music card; pressing it (touch) pauses or plays. Nothing when
     * nothing plays or the notification access isn't given.
     */
    private void updateMusicChip() {
        View link = findViewById(R.id.pocketLinkChip);
        if (link == null || !(link.getParent() instanceof LinearLayout)) {
            return;
        }
        android.media.session.MediaController mc = com.limelight.pocket.device.HandheldTools.playing(this);
        String line = mc != null ? com.limelight.pocket.device.HandheldTools.songLine(mc) : null;
        if (line == null) {
            if (musicChip != null && musicChip.getVisibility() == View.VISIBLE) {
                musicChip.setVisibility(View.GONE);
            }
            musicShown = null;
            return;
        }
        if (musicChip == null) {
            LinearLayout bar = (LinearLayout) link.getParent();
            float d = getResources().getDisplayMetrics().density;
            musicChip = new TextView(this);
            musicChip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f);
            musicChip.setTextColor(0xE6FFFFFF);
            musicChip.setTypeface(com.limelight.pocket.ui.PocketFonts.medium());
            musicChip.setSingleLine(true);
            musicChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            musicChip.setMaxWidth(Math.round(220 * d));
            musicChip.setPadding(Math.round(10 * d), Math.round(3 * d), Math.round(10 * d), Math.round(3 * d));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0x26FFFFFF);
            bg.setCornerRadius(12 * d);
            musicChip.setBackground(bg);
            musicChip.setOnClickListener(v -> {
                android.media.session.MediaController now = com.limelight.pocket.device.HandheldTools.playing(this);
                if (now != null) {
                    now.getTransportControls().pause();
                    updateMusicChip();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Math.round(12 * d));
            bar.addView(musicChip, bar.indexOfChild(link), lp);
        }
        if (!line.equals(musicShown)) {
            musicShown = line;
            musicChip.setText("♪  " + line);
            if (musicChip.getVisibility() != View.VISIBLE) {
                musicChip.setVisibility(View.VISIBLE);
                if (PocketPrefs.animationsEnabled(this)) {
                    musicChip.setAlpha(0f);
                    musicChip.animate().alpha(1f).setDuration(260).start();
                }
            }
        }
    }

    private void updateClock() {
        TextView clock = findViewById(R.id.pocketClock);
        if (clock != null) {
            clock.setText(DateFormat.getTimeFormat(this).format(new Date()));
            updateWeatherChip();
        }
    }

    private final DockMode.Listener dockListener = docked -> {
        if (!isFinishing()) {
            applyDockLook();
        }
    };
    private android.widget.ImageView dockChip;

    /** Docked on a TV: room at the screen's edges (TVs crop them), and a TV icon by the clock. */
    private void applyDockLook() {
        boolean docked = DockMode.isDocked(this);
        float d = getResources().getDisplayMetrics().density;
        View content = findViewById(android.R.id.content);
        if (content != null) {
            // Drawn a little smaller, from the middle, so nothing is cut at the edges (the layout
            // itself stays as it is: padding would squeeze it and push things over each other)
            float m = DockMode.safeMarginDp(this) * d;
            float w = Math.max(1, getResources().getDisplayMetrics().widthPixels);
            float s = Math.max(0.85f, 1f - 2f * m / w);
            content.setPadding(0, 0, 0, 0);
            content.setPivotX(w / 2f);
            content.setPivotY(getResources().getDisplayMetrics().heightPixels / 2f);
            content.setScaleX(s);
            content.setScaleY(s);
        }
        TextView clock = findViewById(R.id.pocketClock);
        if (clock == null || !(clock.getParent() instanceof LinearLayout)) {
            return;
        }
        if (dockChip == null && docked) {
            LinearLayout bar = (LinearLayout) clock.getParent();
            dockChip = new android.widget.ImageView(this);
            dockChip.setImageDrawable(com.limelight.pocket.ui.PocketIcons.drawable("DESKTOP", Math.round(17 * d), 0xFFFFFFFF));
            dockChip.setContentDescription(getString(R.string.pocket_dock_docked));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(19 * d), Math.round(19 * d));
            lp.setMarginStart(Math.round(12 * d));
            bar.addView(dockChip, bar.indexOfChild(clock), lp);
        }
        if (dockChip != null) {
            dockChip.setVisibility(docked ? View.VISIBLE : View.GONE);
        }
    }

    private LinearLayout weatherChip;
    private com.limelight.pocket.home.WidgetCards.SkyIcon weatherIcon;
    private TextView weatherText;
    private long weatherShown = -1;

    /** The weather next to the clock (its sky drawn, the temperature), when it's known. */
    private void updateWeatherChip() {
        TextView clock = findViewById(R.id.pocketClock);
        if (clock == null || !(clock.getParent() instanceof LinearLayout)) {
            return;
        }
        if (!PocketPrefs.prefs(this).getBoolean("pocket_status_weather", true)) {
            if (weatherChip != null) {
                weatherChip.setVisibility(View.GONE);
            }
            return;
        }
        com.limelight.pocket.home.Weather.refresh(this, false, n -> {
            if (!isFinishing()) {
                updateWeatherChip();
            }
        });
        com.limelight.pocket.home.Weather.Now now = com.limelight.pocket.home.Weather.cached(this);
        if (now == null) {
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        if (weatherChip == null) {
            LinearLayout bar = (LinearLayout) clock.getParent();
            weatherChip = new LinearLayout(this);
            weatherChip.setOrientation(LinearLayout.HORIZONTAL);
            weatherChip.setGravity(android.view.Gravity.CENTER_VERTICAL);
            weatherIcon = new com.limelight.pocket.home.WidgetCards.SkyIcon(this);
            weatherChip.addView(weatherIcon, new LinearLayout.LayoutParams(Math.round(19 * d), Math.round(19 * d)));
            weatherText = new TextView(this);
            weatherText.setTypeface(com.limelight.pocket.ui.PocketFonts.semibold());
            weatherText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
            weatherText.setTextColor(0xFFFFFFFF);
            weatherText.setShadowLayer(8 * d, 0, 0, 0x99000000);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.setMarginStart(Math.round(4 * d));
            weatherChip.addView(weatherText, tl);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(Math.round(12 * d));
            bar.addView(weatherChip, bar.indexOfChild(clock), lp);
        }
        weatherChip.setVisibility(View.VISIBLE);
        if (now.at != weatherShown) {
            weatherShown = now.at;
            weatherIcon.set(now.sky, now.day);
            weatherText.setText(now.temp + "°");
        }
    }

    private void updateBattery() {
        TextView battery = findViewById(R.id.pocketBattery);
        if (battery != null && batteryPct >= 0) {
            battery.setText(batteryPct + "%");
            int color = ContextCompat.getColor(this,
                    batteryPct <= 15 && !charging ? R.color.pocket_off : R.color.pocket_text);
            battery.setTextColor(color);
            // A drawn battery next to the number (fill to the level, a bolt while charging)
            com.limelight.pocket.ui.BatteryGlyph glyph;
            Drawable[] current = battery.getCompoundDrawablesRelative();
            if (current[0] instanceof com.limelight.pocket.ui.BatteryGlyph) {
                glyph = (com.limelight.pocket.ui.BatteryGlyph) current[0];
            }
            else {
                glyph = new com.limelight.pocket.ui.BatteryGlyph(getResources().getDisplayMetrics().density);
                battery.setCompoundDrawablesRelative(glyph, null, null, null);
                battery.setCompoundDrawablePadding(dp(5));
                battery.setGravity(android.view.Gravity.CENTER_VERTICAL);
            }
            glyph.set(batteryPct, charging, color);
        }
    }

    public static boolean isWifiConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        Network net = cm.getActiveNetwork();
        if (net == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    protected void updateLinkChip() {
        TextView chip = findViewById(R.id.pocketLinkChip);
        if (chip == null) {
            return;
        }
        int icon;
        int text;
        int bg;
        int color;
        if (UsbLink.isAnyUsbLinkUp()) {
            icon = R.drawable.ic_usb;
            text = R.string.pocket_link_usb;
            bg = R.drawable.pocket_chip_usb_bg;
            color = R.color.pocket_bg;
        }
        else if (isWifiConnected(this)) {
            icon = R.drawable.ic_wifi;
            text = R.string.pocket_link_wifi;
            bg = R.drawable.pocket_chip_bg;
            color = R.color.pocket_text;
        }
        else {
            icon = R.drawable.ic_link_off;
            text = R.string.pocket_link_offline;
            bg = R.drawable.pocket_chip_bg;
            color = R.color.pocket_text_dim;
        }
        // Frameless, like a console's status corner: just the icon (and "USB" when that's the link)
        boolean usb = icon == R.drawable.ic_usb;
        chip.setText(usb ? getString(text) : "");
        chip.setContentDescription(getString(text));
        chip.setBackground(null);
        if (usb) {
            color = R.color.pocket_accent;
        }
        else if (color == R.color.pocket_bg) {
            color = R.color.pocket_text;
        }
        int tint = ContextCompat.getColor(this, color);
        chip.setTextColor(tint);
        Drawable d = ContextCompat.getDrawable(this, icon);
        if (d != null) {
            d = d.mutate();
            int size = dp(17);
            d.setBounds(0, 0, size, size);
            d.setTint(tint);
            chip.setCompoundDrawablesRelative(d, null, null, null);
            chip.setCompoundDrawablePadding(usb ? dp(5) : 0);
        }
    }

    // ---- Hint bar -----------------------------------------------------------------------

    public static Hint hintA(Context c, int label) {
        return new Hint("A", ContextCompat.getColor(c, R.color.pocket_btn_a), c.getString(label));
    }

    public static Hint hintB(Context c, int label) {
        return new Hint("B", ContextCompat.getColor(c, R.color.pocket_btn_b), c.getString(label));
    }

    public static Hint hintX(Context c, int label) {
        return new Hint("X", ContextCompat.getColor(c, R.color.pocket_btn_x), c.getString(label));
    }

    public static Hint hintY(Context c, int label) {
        return new Hint("Y", ContextCompat.getColor(c, R.color.pocket_btn_y), c.getString(label));
    }

    public static Hint hintStart(Context c, int label) {
        return new Hint("☰", 0xFFFFFFFF, c.getString(label));
    }

    public static Hint hintSelect(Context c, int label) {
        return new Hint("\u29C9", 0xFFFFFFFF, c.getString(label));
    }

    public static Hint hintShoulders(Context c, int label) {
        return new Hint("L1 R1", 0xFFFFFFFF, c.getString(label));
    }

    protected void setHints(Hint... hints) {
        ViewGroup bar = findViewById(R.id.pocketHintBar);
        if (bar != null) {
            fillHintBar(this, bar, hints);
        }
    }

    public static void fillHintBar(Context context, ViewGroup bar, Hint... hints) {
        bar.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;
        for (Hint hint : hints) {
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemLp.setMarginStart((int) (13 * density));
            item.setLayoutParams(itemLp);

            // The button: a small white key with a dark letter (console style, no colours)
            TextView glyph = new TextView(context);
            boolean pill = hint.glyph.length() > 1 && !hint.glyph.equals("◀▶");
            int size = Math.round(15 * density);
            glyph.setLayoutParams(new LinearLayout.LayoutParams(
                    pill || hint.glyph.length() > 1 ? ViewGroup.LayoutParams.WRAP_CONTENT : size, size));
            if (pill || hint.glyph.length() > 1) {
                glyph.setPadding(Math.round(5 * density), 0, Math.round(5 * density), 0);
            }
            glyph.setGravity(Gravity.CENTER);
            glyph.setIncludeFontPadding(false);
            glyph.setText(hint.glyph);
            glyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, hint.glyph.length() > 1 ? 7.5f : 8.5f);
            glyph.setTextColor(0xFF0B0B0D);
            glyph.setTypeface(com.limelight.pocket.ui.PocketFonts.bold());
            glyph.setLetterSpacing(0.02f);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            // A round button, filled (no outline ring)
            bg.setCornerRadius(size / 2f);
            bg.setColor(0xEBFFFFFF);
            glyph.setBackground(bg);
            glyph.setTag("hint:" + hint.glyph);
            item.addView(glyph);

            TextView label = new TextView(context);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.setMarginStart(Math.round(5 * density));
            label.setLayoutParams(labelLp);
            label.setText(hint.label);
            label.setTypeface(com.limelight.pocket.ui.PocketFonts.medium());
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            label.setTextColor(0xC7FFFFFF);
            label.setShadowLayer(6 * density, 0, 0, 0x99000000);
            item.addView(label);

            bar.addView(item);
        }
    }

    /**
     * The profile button: a round icon button like its neighbours, which grows into a pill with
     * the profile's name when one is active.
     */
    public static void styleProfileButton(TextView button, String profileName) {
        if (button == null) {
            return;
        }
        Context c = button.getContext();
        float density = c.getResources().getDisplayMetrics().density;
        Drawable icon = ContextCompat.getDrawable(c, R.drawable.ic_profiles);
        if (icon != null) {
            icon = androidx.core.graphics.drawable.DrawableCompat.wrap(icon.mutate());
            androidx.core.graphics.drawable.DrawableCompat.setTintList(icon,
                    ContextCompat.getColorStateList(c, R.color.pocket_row_text));
            int size = Math.round(16 * density);
            icon.setBounds(0, 0, size, size);
            button.setCompoundDrawablesRelative(icon, null, null, null);
        }
        boolean named = profileName != null && !profileName.isEmpty();
        button.setText(named ? profileName : "");
        button.setCompoundDrawablePadding(named ? Math.round(6 * density) : 0);
        int side = Math.round((named ? 11 : 9) * density);
        button.setPaddingRelative(side, 0, named ? Math.round(13 * density) : side, 0);
    }

    protected int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    protected boolean isStatusRunning() {
        return statusRunning;
    }

    /** Fade+rise entrance used when a screen's content appears. */
    // ---- Motion: entrance, button presses, launching a game ------------------------------

    private boolean entrancePlayed;
    private boolean pausedSinceLaunch;
    private android.widget.FrameLayout launchLayer;
    /** The launch screen is up as a cover (from a game's page): the next start goes straight on. */
    private boolean launchCoverActive;
    /** It came up just before this onResume (which mustn't take it away). */
    private boolean launchCoverFresh;
    private static String armedCover;
    private static long armedAt;

    /** A game's page is about to hand "Play" back: the screen under it covers itself at once. */
    public static void armLaunchCover(String title) {
        armedCover = title;
        armedAt = android.os.SystemClock.uptimeMillis();
    }

    /**
     * The "Starting" screen straight away over this one (no card zoom): a game's page faded
     * into it, or the card had no art worth growing. The stream's own copy then carries on.
     */
    protected void showLaunchCover(String title, boolean fadeIn) {
        if (launchLayer != null) {
            return;
        }
        final ViewGroup content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        launchLayer = new android.widget.FrameLayout(this);
        launchLayer.setClickable(true);
        com.limelight.pocket.ui.LaunchOverlay cover = new com.limelight.pocket.ui.LaunchOverlay(this, title, null).asCover();
        launchLayer.addView(cover, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(launchLayer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (fadeIn && PocketPrefs.animationsEnabled(this)) {
            launchLayer.setAlpha(0f);
            launchLayer.animate().alpha(1f).setDuration(240).start();
        }
        launchCoverActive = true;
        pausedSinceLaunch = false;
        // If nothing starts after all (the PC is off, an error), the screen comes back
        statusHandler.postDelayed(() -> {
            if (!pausedSinceLaunch && launchLayer != null) {
                launchCoverActive = false;
                returnFromLaunch();
            }
        }, 6000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (armedCover != null && android.os.SystemClock.uptimeMillis() - armedAt < 5000 && data != null
                && GamePageActivity.ACTION_PLAY.equals(data.getStringExtra(GamePageActivity.RESULT_ACTION))) {
            // Straight from the page's "Starting" to the stream's: the library never shows between
            String title = armedCover;
            armedCover = null;
            showLaunchCover(title, false);
            launchCoverFresh = true;
        }
        armedCover = null;
    }

    /**
     * The screen arriving: the status bar drops in, the hero rises line by line, the cards
     * sweep in from the right one after another and the button bar comes up last.
     */
    protected void playEntrance() {
        playEntrance(false);
    }

    /**
     * fromBoot: the boot's logo and wordmark have just docked into the status bar's brand mark, so
     * those stay where they are and only the rest of the status bar arrives.
     */
    protected void playEntrance(boolean fromBoot) {
        ViewGroup main = findViewById(R.id.pocketMain);
        if (main == null || !PocketPrefs.animationsEnabled(this)) {
            return;
        }
        long delay = 0;
        for (int i = 0; i < main.getChildCount(); i++) {
            View child = main.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            androidx.recyclerview.widget.RecyclerView list = findRecycler(child);
            if (i == 0) {
                if (fromBoot && child instanceof ViewGroup && child.findViewById(R.id.pocketBrandLogo) != null) {
                    ViewGroup row = (ViewGroup) child;
                    for (int j = 0; j < row.getChildCount(); j++) {
                        View part = row.getChildAt(j);
                        int id = part.getId();
                        if (id != R.id.pocketBrandLogo && id != R.id.pocketBrand && part.getVisibility() == View.VISIBLE) {
                            com.limelight.pocket.ui.PocketMotion.arrive(part, delay + 60, 0, -14, 1f);
                        }
                    }
                }
                else {
                    com.limelight.pocket.ui.PocketMotion.arrive(child, delay, 0, -14, 1f);
                }
            }
            else if (child.getId() == R.id.pocketHero && child instanceof ViewGroup) {
                // Lines rise from below their place: let them show outside the hero while they do
                ((ViewGroup) child).setClipChildren(false);
                ((ViewGroup) child).setClipToPadding(false);
                com.limelight.pocket.ui.PocketMotion.cascade((ViewGroup) child, delay, 55, 0, 14, 6);
                delay += 60;
            }
            else if (list != null) {
                com.limelight.pocket.ui.PocketMotion.cascade(list, delay, 45, 56, 0, 8);
                delay += 120;
            }
            else if (child.getId() == R.id.pocketSettingsBody && child instanceof ViewGroup) {
                ViewGroup body = (ViewGroup) child;
                View tabs = body.findViewById(R.id.pocketTabs);
                ViewGroup rows = body.findViewById(R.id.pocketRows);
                if (tabs != null) {
                    com.limelight.pocket.ui.PocketMotion.arrive(tabs, delay, -22, 0, 0.98f);
                    if (tabs instanceof ViewGroup) {
                        com.limelight.pocket.ui.PocketMotion.cascade((ViewGroup) tabs, delay + 60, 35, -10, 0, 8);
                    }
                }
                com.limelight.pocket.ui.PocketMotion.cascade(rows, delay + 80, 30, 0, 14, 10);
                delay += 120;
            }
            else {
                com.limelight.pocket.ui.PocketMotion.arrive(child, delay, 0, 18, 1f);
            }
            delay += 70;
        }
    }

    private static androidx.recyclerview.widget.RecyclerView findRecycler(View view) {
        if (view instanceof androidx.recyclerview.widget.RecyclerView) {
            return (androidx.recyclerview.widget.RecyclerView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                androidx.recyclerview.widget.RecyclerView found = findRecycler(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The button glyph in the hint bar gives a small press when its button is pressed. */
    public static void pulseHint(View root, int keyCode) {
        String glyph;
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                glyph = "A";
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                glyph = "B";
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                glyph = "X";
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                glyph = "Y";
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                glyph = "\u2630";
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
                glyph = "L1 R1";
                break;
            default:
                return;
        }
        if (root == null) {
            return;
        }
        View hit = root.findViewWithTag("hint:" + glyph);
        if (hit != null && hit.isShown()) {
            com.limelight.pocket.ui.PocketMotion.pop(hit, 0.82f);
        }
    }

    /**
     * Launching a game: the selected card's art grows to fill the screen, softening into the
     * backdrop of the "Starting" screen, while the library falls back behind it. Then the
     * stream screen takes over exactly where this ends, so it reads as one motion.
     */
    public void playLaunchZoom(final Runnable start) {
        if (launchLayer != null) {
            if (launchCoverActive) {
                // The "Starting" screen is already up (from the game's page): straight on
                launchCoverActive = false;
                start.run();
            }
            return; // already launching: a second press must not start the game twice
        }
        View focus = getCurrentFocus();
        View artView = focus != null ? focus.findViewById(R.id.grid_image) : null;
        if (!PocketPrefs.animationsEnabled(this)) {
            start.run();
            return;
        }
        android.graphics.drawable.Drawable shown = artView instanceof android.widget.ImageView
                ? ((android.widget.ImageView) artView).getDrawable() : null;
        boolean placeholder = shown == null || artView.getWidth() == 0
                || (shown instanceof android.graphics.drawable.BitmapDrawable
                && com.limelight.pocket.ui.HeroArt.isPlaceholder(((android.graphics.drawable.BitmapDrawable) shown).getBitmap()));
        if (placeholder) {
            // Nothing worth growing (a drawn card, an icon): the "Starting" screen fades up instead
            showLaunchCover(com.limelight.pocket.ui.LaunchOverlay.lastTitle(), true);
            launchCoverActive = false;
            statusHandler.postDelayed(() -> {
                pausedSinceLaunch = false;
                start.run();
            }, 250);
            return;
        }
        final android.widget.ImageView art = (android.widget.ImageView) artView;
        final ViewGroup content = findViewById(android.R.id.content);
        final View main = findViewById(R.id.pocketMain);
        int[] at = new int[2];
        int[] origin = new int[2];
        art.getLocationInWindow(at);
        content.getLocationInWindow(origin);
        float scale = 1f;
        for (View v = art; v != null && v != content; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            scale *= v.getScaleX();
        }
        final float fl = at[0] - origin[0];
        final float ft = at[1] - origin[1];
        final float fw = art.getWidth() * scale;
        final float fh = art.getHeight() * scale;
        final float d = getResources().getDisplayMetrics().density;
        final int w = content.getWidth();
        final int h = content.getHeight();

        launchLayer = new android.widget.FrameLayout(this);
        launchLayer.setClickable(true);
        content.addView(launchLayer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final View black = new View(this);
        black.setBackgroundColor(0xFF000000);
        black.setAlpha(0f);
        launchLayer.addView(black, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final android.widget.ImageView zoom = new android.widget.ImageView(this);
        Drawable.ConstantState state = art.getDrawable().getConstantState();
        zoom.setImageDrawable(state != null ? state.newDrawable(getResources()) : art.getDrawable());
        zoom.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        final float[] radius = {14 * d};
        zoom.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius[0]);
            }
        });
        zoom.setClipToOutline(true);
        launchLayer.addView(zoom, new ViewGroup.LayoutParams(Math.round(fw), Math.round(fh)));
        final View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE6000000, 0x99000000, 0x33000000}));
        shade.setAlpha(0f);
        launchLayer.addView(shade, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final boolean mono = !PocketPrefs.colorBackgroundArt(this);
        final android.view.animation.Interpolator curve = new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f);
        // Ends a touch larger than the screen, where the "Starting" screen's slow zoom begins
        final float over = 0.05f;
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(560);
        anim.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            float f = curve.getInterpolation(t);
            float l = fl + (-w * over - fl) * f;
            float top = ft + (-h * over - ft) * f;
            float r = fl + fw + (w * (1 + over) - fl - fw) * f;
            float b = ft + fh + (h * (1 + over) - ft - fh) * f;
            zoom.layout(Math.round(l), Math.round(top), Math.round(r), Math.round(b));
            radius[0] = 14 * d * (1 - f);
            zoom.invalidateOutline();
            black.setAlpha(Math.min(1f, t * 2f));
            float soften = Math.max(0f, (t - 0.35f) / 0.65f);
            zoom.setAlpha(1f - 0.5f * soften);
            shade.setAlpha(soften);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                float blur = 36f * soften;
                zoom.setRenderEffect(blur > 0.5f
                        ? android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.CLAMP) : null);
            }
            if (mono) {
                android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
                m.setSaturation(1f - soften);
                zoom.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
            }
            if (main != null) {
                main.setScaleX(1f - 0.06f * f);
                main.setScaleY(1f - 0.06f * f);
                main.setAlpha(1f - f);
            }
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                pausedSinceLaunch = false;
                start.run();
                // If the stream screen never came up (it failed right away), come back
                statusHandler.postDelayed(() -> {
                    if (!pausedSinceLaunch && launchLayer != null) {
                        returnFromLaunch();
                    }
                }, 4500);
            }
        });
        zoom.layout(Math.round(fl), Math.round(ft), Math.round(fl + fw), Math.round(ft + fh));
        PocketSounds.play(this, PocketSounds.OPEN);
        anim.start();
    }

    /** Takes the "Starting" layer away now (a question needs the screen instead). */
    protected void dropLaunchLayer() {
        if (launchLayer != null) {
            launchCoverActive = false;
            returnFromLaunch();
        }
    }

    /** Back from a game: the library settles forward again as the launch layer fades. */
    private void returnFromLaunch() {
        final android.widget.FrameLayout layer = launchLayer;
        launchLayer = null;
        View main = findViewById(R.id.pocketMain);
        if (layer != null) {
            layer.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                ViewGroup parent = (ViewGroup) layer.getParent();
                if (parent != null) {
                    parent.removeView(layer);
                }
            }).start();
        }
        if (main != null) {
            main.animate().cancel();
            main.setScaleX(0.94f);
            main.setScaleY(0.94f);
            main.animate().setStartDelay(60).alpha(1f).scaleX(1f).scaleY(1f).setDuration(620)
                    .setInterpolator(com.limelight.pocket.ui.Spring.SOFT)
                    .withEndAction(() -> main.animate().setStartDelay(0)).start();
        }
    }

    public static void reveal(View view, int delayMs) {
        if (view == null) {
            return;
        }
        if (!PocketPrefs.animationsEnabled(view.getContext())) {
            view.setAlpha(1f);
            view.setTranslationY(0);
            return;
        }
        view.setAlpha(0f);
        view.animate().alpha(1f).setStartDelay(delayMs).setDuration(180)
                .setInterpolator(PocketUi.EASE).start();
    }
}
