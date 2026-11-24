package com.limelight.pocket.global;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.home.HomeActivity;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.home.HomeStore;

/**
 * Back and Guide in every game, not just in a stream: a press, a double press or a hold opens
 * Pocket Nexus's Home, Quick Resume (jump to another game) or the radial menu, over any app.
 * Android lets only an accessibility service see the buttons while another app is in front, so
 * this runs inside Pocket Nexus's (the one that already passes the keyboard to the PC); it
 * reads nothing on the screen, only which app is in front. Pocket Nexus's own screens are left
 * alone (they handle the buttons themselves), and a plain Back still goes back.
 *
 * The ring and Quick Resume are a screen of their own over the game ({@link GlobalMenuActivity}):
 * while one is open every press goes to it (the game gets none), except the button that opened
 * it, whose release picks the slice the stick points at, as in a stream.
 */
public final class GlobalButtonEngine {


    /** The running one (the service's), for what only the service can do. */
    private static GlobalButtonEngine instance;

    private final AccessibilityService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String front;

    /** The button being pressed now (-1: none), and how far its press has got. */
    private int down = -1;
    private int downKey;
    private int downDevice, downScan;
    private long downTime;
    private boolean held;
    private int pendingTap = -1;
    private int pendingKey;
    private long lastUp;

    // (Anonymous classes: they read the service, which a lambda here couldn't before it's set)
    private final Runnable holdFired = new Runnable() {
        @Override
        public void run() {
            if (down < 0) {
                return;
            }
            held = true;
            perform(down, downKey, GlobalButtons.get(service, down, GlobalButtons.HOLD));
        }
    };

    private final Runnable tapFired = new Runnable() {
        @Override
        public void run() {
            int button = pendingTap;
            pendingTap = -1;
            if (button >= 0) {
                perform(button, pendingKey, GlobalButtons.get(service, button, GlobalButtons.TAP));
            }
        }
    };

    public GlobalButtonEngine(AccessibilityService service) {
        this.service = service;
        instance = this;
        // Messages (Discord, WhatsApp...) pop up over every game; Guide answers them
        com.limelight.pocket.device.PocketNotifications.setMessageListener(n -> {
            boolean streaming = com.limelight.Game.instance != null && com.limelight.Game.instance.hasWindowFocus();
            MessageBanner.show(service, n, !streaming);
        });
    }

    /** A button of ours is held down right now (Quick Switch waits for its release). */
    static boolean buttonHeld() {
        GlobalButtonEngine e = instance;
        return e != null && e.down >= 0;
    }

    /** Guide went down while a message was showing: its release answers it. */
    private boolean bannerDown;

    /** Which app is in front (from the service's window events), and the apps used last. */
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null) {
            return;
        }
        String pkg = event.getPackageName().toString();
        String cls = event.getClassName() != null ? event.getClassName().toString() : "";
        // The keyboard, the system's own bits (the volume panel...) and our menu over the game
        // aren't "the app in front"
        if (pkg.equals("com.android.systemui") || pkg.contains("inputmethod") || pkg.contains("keyboard")
                || cls.contains("SoftInputWindow") || cls.endsWith("GlobalMenuActivity")
                || cls.startsWith("android.widget.") || cls.startsWith("android.app.Dialog")) {
            return;
        }
        if (!pkg.equals(front)) {
            front = pkg;
            // Remembered for Quick Resume only once it stayed in front a moment (not an app
            // that flashes up and goes: a sign-in, a share sheet, a launcher passing through)
            main.removeCallbacks(remember);
            pendingSeen = pkg;
            main.postDelayed(remember, 2500);
        }
    }

    private String pendingSeen;
    private final Runnable remember = new Runnable() {
        @Override
        public void run() {
            String p = pendingSeen;
            if (p != null && p.equals(front)) {
                try {
                    AppHistory.seen(service, p);
                } catch (RuntimeException ignored) {
                }
            }
        }
    };

    public String foreground() {
        return front;
    }

    private boolean ourAppInFront() {
        return front != null && front.equals(service.getPackageName());
    }

    private int buttonOf(KeyEvent event) {
        if (com.limelight.pocket.PocketPrefs.matchesBack(
                com.limelight.pocket.PocketPrefs.getBackKey(service), event)) return GlobalButtons.BACK;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_MODE:
            case KeyEvent.KEYCODE_HOME:
                return GlobalButtons.GUIDE;
            default:
                return -1;
        }
    }

    /** True when this button does something of ours (else it's left to the game entirely). */
    private boolean handles(int button) {
        for (int g = 0; g < GlobalButtons.GESTURE_COUNT; g++) {
            GlobalButtons.Action a = GlobalButtons.get(service, button, g);
            if (a != GlobalButtons.Action.PASS && a != GlobalButtons.Action.NONE) {
                return true;
            }
        }
        return false;
    }

    /** A key from the service's filter: true when it was used here (nobody else gets it). */
    public boolean onKeyEvent(KeyEvent event) {
        if (GlobalMenuActivity.isOpen()) {
            return keyWhileOpen(event);
        }
        // A message on screen: Guide answers it (as the PS button opens a PS5's)
        if (buttonOf(event) == GlobalButtons.GUIDE
                && (bannerDown || MessageBanner.canAnswer())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    bannerDown = true;
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP && bannerDown) {
                bannerDown = false;
                MessageBanner.consumeGuide(service);
                return true;
            }
        }
        if (!GlobalButtons.enabled(service)) {
            return false;
        }
        int button = buttonOf(event);
        if (button < 0 || !handles(button)) {
            return false;
        }
        if (ourAppInFront()) {
            // Pocket Nexus's own screens keep Back; Guide works the same as over a game (its
            // radial menu and Quick Resume, to switch while docked), unless M1 / M2 is set to it
            if (button != GlobalButtons.GUIDE || event.getKeyCode() != KeyEvent.KEYCODE_BUTTON_MODE
                    || com.limelight.pocket.PocketPrefs.matchesHotkey(com.limelight.pocket.PocketPrefs.getMenuHotkey(service), event)
                    || com.limelight.pocket.PocketPrefs.matchesHotkey(com.limelight.pocket.PocketPrefs.getM2Hotkey(service), event)
                    || (com.limelight.Game.instance != null && com.limelight.Game.instance.hasWindowFocus())) {
                return false;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) {
                return true;
            }
            down = button;
            downKey = event.getKeyCode();
            downDevice = event.getDeviceId();
            downScan = event.getScanCode();
            downTime = event.getDownTime();
            held = false;
            main.removeCallbacks(holdFired);
            GlobalButtons.Action hold = GlobalButtons.get(service, button, GlobalButtons.HOLD);
            if (hold != GlobalButtons.Action.NONE) {
                main.postDelayed(holdFired, GlobalButtons.holdMs(service));
            }
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_UP || down != button
                || downKey != event.getKeyCode() || downDevice != event.getDeviceId()
                || downScan != event.getScanCode() || downTime != event.getDownTime()) {
            return false;
        }
        main.removeCallbacks(holdFired);
        down = -1;
        if (event.isCanceled()) return true;
        if (held) {
            return true;
        }
        long now = SystemClock.uptimeMillis();
        GlobalButtons.Action twice = GlobalButtons.get(service, button, GlobalButtons.DOUBLE);
        if (twice != GlobalButtons.Action.NONE && twice != GlobalButtons.Action.PASS) {
            if (pendingTap == button && now - lastUp <= GlobalButtons.doubleMs(service)) {
                main.removeCallbacks(tapFired);
                pendingTap = -1;
                perform(button, event.getKeyCode(), twice);
            }
            else {
                // A second press may be coming: the first waits for it
                main.removeCallbacks(tapFired);
                if (pendingTap >= 0 && pendingTap != button) {
                    tapFired.run();
                }
                pendingTap = button;
                pendingKey = event.getKeyCode();
                lastUp = now;
                main.postDelayed(tapFired, GlobalButtons.doubleMs(service));
            }
            return true;
        }
        perform(button, event.getKeyCode(), GlobalButtons.get(service, button, GlobalButtons.TAP));
        return true;
    }

    /**
     * While the ring or Quick Resume is open: the button that opened it picks on release (held
     * open) or closes it (pressed again); everything else goes to the menu, none to the game.
     */
    private boolean keyWhileOpen(KeyEvent event) {
        int button = buttonOf(event);
        if (button < 0 || event.getKeyCode() == KeyEvent.KEYCODE_BACK && down < 0) {
            return false; // the menu's own (Back closes it there)
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0 && down < 0) {
                down = button;
                held = false;
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean wasHeld = held && down == button;
            down = -1;
            held = false;
            main.removeCallbacks(holdFired);
            if (wasHeld) {
                GlobalMenuActivity.triggerReleased();
            }
            else {
                GlobalMenuActivity.dismiss();
            }
        }
        return true;
    }

    /** Does what the button was set to (PASS: what it does anyway). */
    private void perform(int button, int keyCode, GlobalButtons.Action a) {
        if (a == GlobalButtons.Action.PASS) {
            if (button == GlobalButtons.BACK) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            }
            else if (keyCode == KeyEvent.KEYCODE_HOME) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            }
            return;
        }
        run(service, service, a, front);
    }

    /** An action from the ring (or anywhere): the service does what only it can, if it's on. */
    /** Sleep: the screen off, as the power button does (needs the service; Android 9+). */
    static boolean lockScreen() {
        GlobalButtonEngine e = instance;
        if (e == null || e.service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        return e.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
    }

    static void runAction(Context c, GlobalButtons.Action a, String front) {
        GlobalButtonEngine e = instance;
        run(c, e != null ? e.service : null, a, front != null ? front : e != null ? e.front : null);
    }

    private static void run(Context c, AccessibilityService s, GlobalButtons.Action a, String front) {
        switch (a) {
            case NONE:
            case PASS:
                return;
            case HOME:
                openPocket(c, null);
                return;
            case QUICK_RESUME:
                GlobalMenuActivity.show(c, GlobalMenuActivity.MODE_QUICK, front);
                return;
            case RADIAL:
                GlobalMenuActivity.show(c, GlobalMenuActivity.MODE_RADIAL, front);
                return;
            case LAST_GAME:
                lastGame(c, front);
                return;
            case SETTINGS:
                openPocket(c, HomeActivity.ACTION_SETTINGS);
                return;
            case SEARCH:
                start(c, com.limelight.pocket.search.SearchActivity.class);
                return;
            case FILES:
                start(c, com.limelight.pocket.files.FilesActivity.class);
                return;
            case BROWSER:
                start(c, com.limelight.pocket.browser.BrowserActivity.class);
                return;
            case MUSIC:
                start(c, com.limelight.pocket.music.MusicActivity.class);
                return;
            case CONTROL:
                ControlCenterActivity.open(c);
                return;
            case QUICK_SWITCH:
                GlobalMenuActivity.show(c, GlobalMenuActivity.MODE_SWITCH, front);
                return;
            case REPLY: {
                com.limelight.pocket.device.PocketNotifications.Note n =
                        com.limelight.pocket.device.PocketNotifications.lastMessage();
                if (n != null && n.canReply()) {
                    MessageBanner.hide(false);
                    QuickReplyActivity.open(c, n);
                }
                else {
                    Toast.makeText(c, R.string.pocket_message_none, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            case PLAY_PAUSE:
                mediaKey(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                return;
            case NEXT_TRACK:
                mediaKey(c, KeyEvent.KEYCODE_MEDIA_NEXT);
                return;
            case VOLUME_UP:
            case VOLUME_DOWN: {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, a == GlobalButtons.Action.VOLUME_UP
                            ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                }
                return;
            }
            default:
                break;
        }
        // The rest only the service can do
        if (s == null) {
            Toast.makeText(c, R.string.pocket_gb_needs_service, Toast.LENGTH_SHORT).show();
            return;
        }
        switch (a) {
            case RECENTS:
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                break;
            case ANDROID_HOME:
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                break;
            case BACK:
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                break;
            case SCREENSHOT:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
                }
                break;
            case NOTIFICATIONS:
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
                break;
            case QUICK_SETTINGS:
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
                break;
            default:
                break;
        }
    }

    private static void mediaKey(Context c, int code) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    /** One of Pocket Nexus's tools over the game. */
    private static void start(Context c, Class<?> screen) {
        try {
            c.startActivity(new Intent(c, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(c, R.string.pocket_gb_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    /** Pocket Nexus's Home in front (the game stays where it is, behind), doing action there. */
    private static void openPocket(Context c, String action) {
        Intent i = new Intent(c, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (action != null) {
            i.putExtra(HomeActivity.EXTRA_ACTION, action);
        }
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(c, R.string.pocket_gb_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    /** The app used before the one in front (back and forth between two games). */
    private static void lastGame(final Context c, final String now) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String pkg = null;
            HomeItem pick = null;
            try {
                for (String p : AppHistory.recent(c)) {
                    if (!p.equals(now)) {
                        pkg = p;
                        break;
                    }
                }
                if (pkg == null) {
                    long best = 0;
                    for (HomeStore.Entry e : HomeStore.load(c)) {
                        boolean inFront = now != null && HomeItem.ANDROID.equals(e.item.kind) && now.equals(e.item.a);
                        if (!inFront && e.lastUsed > best) {
                            best = e.lastUsed;
                            pick = e.item;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
            }
            final String toApp = pkg;
            final HomeItem item = pick;
            main.post(() -> {
                if (toApp != null && resumeApp(c, toApp)) {
                    return;
                }
                if (item != null) {
                    launchItem(c, item);
                }
                else {
                    openPocket(c, null);
                }
            });
        }, "pocket-last-game").start();
    }

    /** Brings an app back as it was (its task to the front). */
    static boolean resumeApp(Context c, String pkg) {
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            return false;
        }
        try {
            c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            AppHistory.seen(c, pkg);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A game picked in Quick Resume that isn't open: an Android game starts; others via Home. */
    static void launchItem(Context c, HomeItem item) {
        if (HomeItem.ANDROID.equals(item.kind)) {
            try {
                HomeStore.record(c, item);
            } catch (RuntimeException ignored) {
            }
            if (resumeApp(c, item.a)) {
                return;
            }
        }
        Intent i = new Intent(c, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(HomeActivity.EXTRA_ACTION, HomeActivity.ACTION_LAUNCH)
                .putExtra(HomeActivity.EXTRA_ITEM, item.toLine());
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(c, R.string.pocket_gb_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    public void destroy() {
        main.removeCallbacksAndMessages(null);
        com.limelight.pocket.device.PocketNotifications.setMessageListener(null);
        MessageBanner.hide(false);
        if (instance == this) {
            instance = null;
        }
    }
}
