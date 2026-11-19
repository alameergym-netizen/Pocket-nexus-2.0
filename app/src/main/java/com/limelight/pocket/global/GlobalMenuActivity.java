package com.limelight.pocket.global;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketArt;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.home.HomeActivity;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.home.HomeStore;
import com.limelight.pocket.ui.QuickSwitchView;
import com.limelight.pocket.ui.RadialMenuView;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The radial menu and Quick Resume over any game, the same as in a stream: a see-through screen
 * of its own on top of the game (which stays in sight behind, blurred where Android can, and
 * gets none of the presses while it's open), with the stream's very ring (its springs, sounds
 * and stick) and a row of the games and apps used last to jump between.
 */
public class GlobalMenuActivity extends Activity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_FRONT = "front";
    public static final String MODE_RADIAL = "radial";
    public static final String MODE_QUICK = "quick";
    public static final String MODE_SWITCH = "switch";

    /** The one open now (the button service tells it when the held button is let go). */
    private static GlobalMenuActivity open;

    public static boolean isOpen() {
        return open != null;
    }

    /** The button that opened the ring was let go: the slice it points at is chosen. */
    static void triggerReleased() {
        GlobalMenuActivity a = open;
        if (a != null && a.radial != null && a.radial.isShowing() && a.radial.getSelected() >= 0) {
            a.chooseRadial();
        }
        else if (a != null && a.strip != null) {
            a.chooseSwitch();
        }
    }

    /** The button that opened it was pressed again: closes, as B does. */
    static void dismiss() {
        GlobalMenuActivity a = open;
        if (a == null) {
            return;
        }
        if (a.radial != null && a.radial.isShowing()) {
            a.radial.hide();
            a.finishSoon(170);
        }
        else if (a.strip != null) {
            // Quick Switch: each press moves on, like Tab with Alt held
            a.strip.move(1);
            a.armAuto();
        }
        else {
            a.closeQuick();
        }
    }

    public static void show(Context c, String mode, String front) {
        try {
            c.startActivity(new Intent(c, GlobalMenuActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra(EXTRA_MODE, mode)
                    .putExtra(EXTRA_FRONT, front));
        } catch (RuntimeException ignored) {
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    /** For what happens after this screen is gone (an action picked in the ring). */
    private static final Handler AFTER = new Handler(Looper.getMainLooper());
    /** Nothing left on screen (the ring closed, Quick Resume gone) but still here: it goes. */
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (finishing) {
                return;
            }
            boolean ringGone = radial != null && !radial.isShowing();
            boolean quickGone = quick != null && quick.isClosed();
            boolean stripGone = strip != null && strip.isClosed();
            boolean nothing = radial == null && quick == null && strip == null;
            if (ringGone || quickGone || stripGone || nothing) {
                if (++idleTicks >= 2) {
                    finishNow();
                    return;
                }
            }
            else {
                idleTicks = 0;
            }
            main.postDelayed(this, 300);
        }
    };
    private int idleTicks;
    private FrameLayout root;
    private RadialMenuView radial;
    private QuickSwitchView quick;
    private com.limelight.pocket.ui.AppSwitchStrip strip;
    private static final long AUTO_MS = 1300;
    /** Quick Switch switches by itself a moment after the last press (not while one is held). */
    private final Runnable autoPick = new Runnable() {
        @Override
        public void run() {
            if (strip == null || finishing) {
                return;
            }
            if (GlobalButtonEngine.buttonHeld()) {
                strip.stopCountdown();
                main.postDelayed(this, 200);
                return;
            }
            chooseSwitch();
        }
    };
    private GlobalButtons.Action[] slots;
    private String front;
    private final List<HomeItem> quickItems = new ArrayList<>();
    private final List<String> quickPackages = new ArrayList<>();
    private boolean finishing;
    private float blur;
    private int stickDir;
    private final Runnable stickRepeat = new Runnable() {
        @Override
        public void run() {
            if (quick != null && stickDir != 0) {
                quick.move(stickDir);
                main.postDelayed(this, 170);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConsoleActivity.applyFullscreenWindow(this);
        overridePendingTransition(0, 0);
        root = new FrameLayout(this);
        setContentView(root);
        open = this;
        startBlur();
        handle(getIntent());
        main.postDelayed(watchdog, 600);
        // It arrives (no pop): a quick fade
        if (PocketPrefs.animationsEnabled(this)) {
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(140).start();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        open = this;
        ConsoleActivity.applyFullscreenWindow(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Left for another app (Home, a notification...): nothing stays over it
        if (!isFinishing()) {
            finishNow();
        }
    }

    @Override
    protected void onDestroy() {
        if (open == this) {
            open = null;
        }
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** The game behind, frosted (Android 12 and up, where the device can). */
    private void startBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        WindowManager wm = getSystemService(WindowManager.class);
        if (wm == null || !wm.isCrossWindowBlurEnabled() || !PocketPrefs.animationsEnabled(this)) {
            return;
        }
        // Set once (changing it every frame makes the window lay out again and again: a stutter)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        setBlur(26 * getResources().getDisplayMetrics().density);
    }

    /** The blur off before the window goes, so none of it can linger over the game. */
    private void clearBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || blur <= 0) {
            return;
        }
        try {
            setBlur(0);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        } catch (RuntimeException ignored) {
        }
    }

    private void setBlur(float px) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        blur = px;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.setBlurBehindRadius(Math.round(px));
        getWindow().setAttributes(lp);
    }

    private void handle(Intent intent) {
        front = intent.getStringExtra(EXTRA_FRONT);
        String mode = intent.getStringExtra(EXTRA_MODE);
        root.removeAllViews();
        radial = null;
        quick = null;
        strip = null;
        main.removeCallbacks(autoPick);
        if (MODE_QUICK.equals(mode)) {
            showQuick();
        }
        else if (MODE_SWITCH.equals(mode)) {
            showSwitch();
        }
        else {
            showRadial();
        }
    }

    // ---- The ring ---------------------------------------------------------------------------

    private void showRadial() {
        radial = new RadialMenuView(this);
        root.addView(radial, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        slots = new GlobalButtons.Action[GlobalButtons.RADIAL_SLOTS];
        String[] icons = new String[slots.length];
        String[] labels = new String[slots.length];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = GlobalButtons.radial(this, i);
            boolean none = slots[i] == GlobalButtons.Action.NONE || slots[i] == GlobalButtons.Action.PASS;
            icons[i] = GlobalButtons.icon(slots[i]);
            labels[i] = none ? "" : getString(slots[i].label);
        }
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        String below = appName(front);
        radial.showItems(icons, labels, time, below != null ? below : getString(R.string.pocket_brand_title));
    }

    private void chooseRadial() {
        if (radial == null || finishing) {
            return;
        }
        final int s = radial.getSelected();
        if (s < 0) {
            radial.hide();
            finishSoon(170);
            return;
        }
        final GlobalButtons.Action a = slots[s];
        radial.confirm();
        if (a == GlobalButtons.Action.QUICK_RESUME) {
            // Straight on to Quick Resume, in this same screen
            main.postDelayed(() -> {
                root.removeAllViews();
                radial = null;
                showQuick();
            }, 200);
            return;
        }
        finishSoon(200);
        // After the menu is gone (a screenshot shouldn't have it in it)
        // (On a handler of its own: this screen is gone by then, its own callbacks with it)
        final Context app = getApplicationContext();
        final String inFront = front;
        AFTER.postDelayed(() -> GlobalButtonEngine.runAction(app, a, inFront), 300);
    }

    // ---- Quick Resume -----------------------------------------------------------------------

    private void showQuick() {
        quick = new QuickSwitchView(this);
        root.addView(quick, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // The same button hints as everywhere else (drawn glyphs), bottom right
        android.widget.LinearLayout hints = com.limelight.pocket.ui.ConsoleScreen.hintBar(this);
        com.limelight.pocket.ConsoleActivity.fillHintBar(this, hints,
                com.limelight.pocket.ConsoleActivity.hintA(this, R.string.pocket_gb_hint_switch),
                com.limelight.pocket.ConsoleActivity.hintX(this, R.string.pocket_hint_close),
                com.limelight.pocket.ConsoleActivity.hintY(this, R.string.pocket_gb_hint_home),
                com.limelight.pocket.ConsoleActivity.hintB(this, R.string.pocket_hint_back));
        float dd = getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM | android.view.Gravity.END);
        hl.rightMargin = Math.round(40 * dd);
        hl.bottomMargin = Math.round(18 * dd);
        hints.setAlpha(0f);
        root.addView(hints, hl);
        quick.attachHints(hints);
        quick.open();
        PocketSounds.play(this, PocketSounds.OPEN);
        final Context app = getApplicationContext();
        final String now = front;
        final int artW = Math.round(170 * getResources().getDisplayMetrics().density);
        new Thread(() -> {
            final List<QuickSwitchView.Card> cards = new ArrayList<>();
            final List<HomeItem> items = new ArrayList<>();
            final List<String> pkgs = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            PackageManager pm = app.getPackageManager();
            List<String> recent = AppHistory.recent(app);
            if (now != null && !now.equals(app.getPackageName()) && recent.contains(now)) {
                recent.remove(now);
                recent.add(0, now);
            }
            for (String pkg : recent) {
                if (cards.size() >= 10) {
                    break;
                }
                HomeItem item = AppHistory.itemFor(app, pkg);
                String label = label(pm, pkg);
                boolean isNow = pkg.equals(now);
                String title = item != null ? item.name : label;
                String sub = item != null && !HomeItem.ANDROID.equals(item.kind) ? label
                        : app.getString(isNow ? R.string.pocket_gb_sub_now : R.string.pocket_gb_sub_resume);
                QuickSwitchView.Card c = new QuickSwitchView.Card("pkg:" + pkg, title, sub, isNow);
                HomeItem artItem = item != null ? item : new HomeItem(HomeItem.ANDROID, label, pkg, "", "", 0);
                c.art = cover(app, artItem, artW);
                if (c.art == null) {
                    c.icon = icon(pm, pkg);
                    c.tint = tintOf(c.icon);
                }
                cards.add(c);
                items.add(item);
                pkgs.add(pkg);
                seen.add(artItem.key());
            }
            // Then what Pocket Nexus played last that isn't open (started afresh)
            try {
                List<HomeStore.Entry> played = new ArrayList<>();
                for (HomeStore.Entry e : HomeStore.load(app)) {
                    if (e.lastUsed > 0) {
                        played.add(e);
                    }
                }
                java.util.Collections.sort(played, (x, y) -> Long.compare(y.lastUsed, x.lastUsed));
                for (HomeStore.Entry e : played) {
                    if (cards.size() >= 12) {
                        break;
                    }
                    if (!seen.add(e.item.key())) {
                        continue;
                    }
                    QuickSwitchView.Card c = new QuickSwitchView.Card("item:" + e.item.key(), e.item.name,
                            app.getString(R.string.pocket_gb_sub_play), false);
                    c.art = cover(app, e.item, artW);
                    cards.add(c);
                    items.add(e.item);
                    pkgs.add(null);
                }
            } catch (RuntimeException ignored) {
            }
            main.post(() -> {
                if (quick == null || isFinishing()) {
                    return;
                }
                quickItems.clear();
                quickItems.addAll(items);
                quickPackages.clear();
                quickPackages.addAll(pkgs);
                // On the one before the game in front: the usual jump
                int focus = cards.size() > 1 && cards.get(0).now ? 1 : 0;
                quick.setCards(cards, focus);
            });
        }, "pocket-quick").start();
    }

    // ---- Quick Switch -----------------------------------------------------------------------

    private void showSwitch() {
        strip = new com.limelight.pocket.ui.AppSwitchStrip(this);
        root.addView(strip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        strip.open();
        PocketSounds.play(this, PocketSounds.OPEN);
        final Context app = getApplicationContext();
        final String now = front;
        new Thread(() -> {
            final List<com.limelight.pocket.ui.AppSwitchStrip.App> apps = new ArrayList<>();
            PackageManager pm = app.getPackageManager();
            List<String> recent = AppHistory.recent(app);
            if (now != null && recent.contains(now)) {
                recent.remove(now);
            }
            if (now != null) {
                recent.add(0, now);
            }
            Set<String> seen = new HashSet<>();
            for (String pkg : recent) {
                if (apps.size() >= 8 || !seen.add(pkg)) {
                    continue;
                }
                if (pm.getLaunchIntentForPackage(pkg) == null && !pkg.equals(app.getPackageName())) {
                    continue;
                }
                Bitmap ic = icon(pm, pkg);
                if (ic == null) {
                    continue;
                }
                HomeItem item = AppHistory.itemFor(app, pkg);
                String name = item != null ? item.name : label(pm, pkg);
                apps.add(new com.limelight.pocket.ui.AppSwitchStrip.App(pkg, name, ic, pkg.equals(now)));
            }
            main.post(() -> {
                if (strip == null || isFinishing()) {
                    return;
                }
                if (apps.size() < 2) {
                    // Nothing else open: say so a moment, then go
                    strip.setApps(apps, 0);
                    PocketSounds.play(this, PocketSounds.ERROR);
                    main.postDelayed(this::closeSwitch, 700);
                    return;
                }
                strip.setApps(apps, apps.get(0).now ? 1 : 0);
                armAuto();
            });
        }, "pocket-switch").start();
    }

    private void armAuto() {
        if (strip == null) {
            return;
        }
        main.removeCallbacks(autoPick);
        strip.countdown(AUTO_MS);
        main.postDelayed(autoPick, AUTO_MS);
    }

    private void chooseSwitch() {
        main.removeCallbacks(autoPick);
        if (strip == null || finishing) {
            return;
        }
        final com.limelight.pocket.ui.AppSwitchStrip.App a = strip.selectedApp();
        if (a == null || a.now) {
            closeSwitch();
            return;
        }
        PocketSounds.play(this, PocketSounds.SELECT);
        strip.close(() -> {
            GlobalButtonEngine.resumeApp(getApplicationContext(), a.pkg);
            finishNow();
        });
    }

    private void closeSwitch() {
        main.removeCallbacks(autoPick);
        if (strip == null) {
            finishSoon(0);
            return;
        }
        PocketSounds.play(this, PocketSounds.CLOSE);
        strip.close(this::finishNow);
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return pkg;
        }
    }

    private String appName(String pkg) {
        if (pkg == null) {
            return null;
        }
        HomeItem item = AppHistory.itemFor(this, pkg);
        return item != null ? item.name : label(getPackageManager(), pkg);
    }

    private static Bitmap cover(Context c, HomeItem item, int width) {
        String uuid = HomeActivity.artUuid(item);
        if (uuid == null) {
            return null;
        }
        File f = PocketArt.file(c, uuid, HomeActivity.artId(item), PocketArt.COVER);
        if (!f.exists()) {
            return null;
        }
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= width) {
                sample *= 2;
            }
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), d);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap icon(PackageManager pm, String pkg) {
        try {
            Drawable dr = pm.getApplicationIcon(pkg);
            if (dr instanceof BitmapDrawable && ((BitmapDrawable) dr).getBitmap() != null) {
                return ((BitmapDrawable) dr).getBitmap();
            }
            int s = 192;
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            dr.setBounds(0, 0, s, s);
            dr.draw(c);
            return b;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    /** The icon's own colour (its average), darkened a little, for its card. */
    private static int tintOf(Bitmap icon) {
        if (icon == null) {
            return 0xFF2A2D36;
        }
        try {
            Bitmap one = Bitmap.createScaledBitmap(icon, 1, 1, true);
            int c = one.getPixel(0, 0);
            int r = (int) (((c >> 16) & 0xFF) * 0.8f);
            int g = (int) (((c >> 8) & 0xFF) * 0.8f);
            int b = (int) ((c & 0xFF) * 0.8f);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        } catch (RuntimeException e) {
            return 0xFF2A2D36;
        }
    }

    private void chooseQuick() {
        if (quick == null || finishing) {
            return;
        }
        final int i = quick.selected();
        if (i < 0) {
            closeQuick();
            return;
        }
        final String pkg = quickPackages.get(i);
        final HomeItem item = quickItems.get(i);
        PocketSounds.play(this, PocketSounds.SELECT);
        if (pkg != null && pkg.equals(front)) {
            closeQuick(); // the game in front: back to it
            return;
        }
        quick.close(() -> {
            if (pkg != null) {
                // Back to where it was (its task comes to the front as it is)
                GlobalButtonEngine.resumeApp(this, pkg);
            }
            else if (item != null) {
                GlobalButtonEngine.launchItem(getApplicationContext(), item);
            }
            finishNow();
        });
    }

    /** X: closes the chosen app (one in the background; the one in front goes Home first). */
    private void closeApp() {
        if (quick == null || finishing) {
            return;
        }
        int i = quick.selected();
        if (i < 0 || quickPackages.get(i) == null) {
            return;
        }
        final String pkg = quickPackages.get(i);
        PocketSounds.play(this, PocketSounds.TOGGLE_OFF);
        AppHistory.forget(this, pkg);
        final Context app = getApplicationContext();
        if (pkg.equals(front)) {
            // Out of the way first (only an app that isn't on screen can be closed)
            quick.close(() -> {
                Intent home = new Intent(this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                try {
                    startActivity(home);
                } catch (RuntimeException ignored) {
                }
                main.postDelayed(() -> kill(app, pkg), 900);
                finishNow();
            });
            return;
        }
        kill(app, pkg);
        // The row without it
        root.removeAllViews();
        showQuick();
    }

    static void kill(Context c, String pkg) {
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14 lets an app close only its own; there, Android's Force stop does it
            try {
                c.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
            }
            return;
        }
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(pkg);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void closeQuick() {
        if (quick == null) {
            finishSoon(0);
            return;
        }
        PocketSounds.play(this, PocketSounds.CLOSE);
        quick.close(this::finishNow);
    }

    // ---- Closing ----------------------------------------------------------------------------

    private void finishSoon(long delay) {
        if (finishing) {
            return;
        }
        main.postDelayed(this::finishNow, delay);
    }

    private void finishNow() {
        if (finishing) {
            return;
        }
        finishing = true;
        if (open == this) {
            open = null;
        }
        main.removeCallbacks(watchdog);
        clearBlur();
        finish();
        overridePendingTransition(0, 0);
    }

    // ---- Buttons and the sticks -------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event);
        }
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean first = down && event.getRepeatCount() == 0;
        if (radial != null && radial.isShowing()) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down) {
                        radial.onDirection(0);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) {
                        radial.onDirection(2);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) {
                        radial.onDirection(4);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) {
                        radial.onDirection(6);
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    if (first) {
                        radial.rotate(-1);
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    if (first) {
                        radial.rotate(1);
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (first) {
                        chooseRadial();
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (first) {
                        radial.hide();
                        finishSoon(170);
                    }
                    return true;
                default:
                    return true;
            }
        }
        if (strip != null) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_BUTTON_L1:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_BUTTON_R1:
                    if (down) {
                        strip.move(code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_BUTTON_L1 ? -1 : 1);
                        armAuto();
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (first) {
                        chooseSwitch();
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (first) {
                        closeSwitch();
                    }
                    return true;
                default:
                    return true;
            }
        }
        if (quick != null) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_BUTTON_L1:
                    if (down) {
                        quick.move(-1);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_BUTTON_R1:
                    if (down) {
                        quick.move(1);
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (first) {
                        chooseQuick();
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_X:
                    if (first) {
                        closeApp();
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_Y:
                    if (first) {
                        PocketSounds.play(this, PocketSounds.SELECT);
                        quick.close(() -> {
                            GlobalButtonEngine.runAction(getApplicationContext(), GlobalButtons.Action.HOME, front);
                            finishNow();
                        });
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (first) {
                        closeQuick();
                    }
                    return true;
                default:
                    return true;
            }
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if ((ev.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                || ev.getAction() != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(ev);
        }
        float lx = ev.getAxisValue(MotionEvent.AXIS_X);
        float ly = ev.getAxisValue(MotionEvent.AXIS_Y);
        float rx = ev.getAxisValue(MotionEvent.AXIS_Z);
        float ry = ev.getAxisValue(MotionEvent.AXIS_RZ);
        float hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (radial != null && radial.isShowing()) {
            // Either stick (the one pushed further), or the D-pad as a hat
            if (Math.abs(hx) > 0.5f || Math.abs(hy) > 0.5f) {
                radial.onStick(Math.signum(hx), Math.signum(hy));
            }
            else if (Math.hypot(rx, ry) > Math.hypot(lx, ly)) {
                radial.onStick(rx, ry);
            }
            else {
                radial.onStick(lx, ly);
            }
            return true;
        }
        if (strip != null) {
            float x = Math.abs(hx) > 0.5f ? hx : lx;
            int dir = x > 0.6f ? 1 : x < -0.6f ? -1 : 0;
            if (dir != stickDir) {
                stickDir = dir;
                if (dir != 0) {
                    strip.move(dir);
                    armAuto();
                }
            }
            return true;
        }
        if (quick != null) {
            float x = Math.abs(hx) > 0.5f ? hx : lx;
            int dir = x > 0.6f ? 1 : x < -0.6f ? -1 : 0;
            if (dir != stickDir) {
                stickDir = dir;
                main.removeCallbacks(stickRepeat);
                if (dir != 0) {
                    quick.move(dir);
                    main.postDelayed(stickRepeat, 380);
                }
            }
            return true;
        }
        return true;
    }
}
