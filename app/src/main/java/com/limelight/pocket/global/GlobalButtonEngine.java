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

/**
 * Back and Guide in every game, not just in a stream: a press, a double press or a hold opens
 * Pocket Nexus's Home, Quick Resume (jump to another game) or the radial menu, over any app.
 * Android lets only an accessibility service see the buttons while another app is in front, so
 * this runs inside Pocket Nexus's (the one that already passes the keyboard to the PC); it
 * reads nothing on the screen, only which app is in front. Pocket Nexus's own screens are left
 * alone (they handle the buttons themselves), and a plain Back still goes back.
 */
public final class GlobalButtonEngine implements GlobalOverlay.Host {

    private static final long HOLD_MS = 480;

    private final AccessibilityService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private GlobalOverlay overlay;
    private String front;

    /** The button being pressed now (-1: none), and how far its press has got. */
    private int down = -1;
    private int downKey;
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
        overlay = new GlobalOverlay(service, this);
    }

    /** Which app is in front (from the service's window events). */
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null) {
            String pkg = event.getPackageName().toString();
            // The keyboard and the system's own bits (the volume panel...) aren't "the app in front"
            if (!pkg.equals("com.android.systemui") && !pkg.contains("inputmethod") && !pkg.contains("keyboard")) {
                front = pkg;
            }
        }
    }

    @Override
    public String foreground() {
        return front;
    }

    private boolean ourAppInFront() {
        return front != null && front.equals(service.getPackageName());
    }

    private static int buttonOf(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                return GlobalButtons.BACK;
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
        if (overlay != null && overlay.showing()) {
            return overlay.handleKey(event);
        }
        if (!GlobalButtons.enabled(service) || ourAppInFront()) {
            return false;
        }
        int button = buttonOf(event.getKeyCode());
        if (button < 0 || !handles(button)) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) {
                return true;
            }
            down = button;
            downKey = event.getKeyCode();
            held = false;
            main.removeCallbacks(holdFired);
            GlobalButtons.Action hold = GlobalButtons.get(service, button, GlobalButtons.HOLD);
            if (hold != GlobalButtons.Action.NONE) {
                main.postDelayed(holdFired, HOLD_MS);
            }
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_UP || down != button) {
            return true;
        }
        main.removeCallbacks(holdFired);
        down = -1;
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
        run(a);
    }

    @Override
    public void run(GlobalButtons.Action a) {
        switch (a) {
            case NONE:
            case PASS:
                return;
            case HOME:
                openPocket(null);
                return;
            case QUICK_RESUME:
                if (overlay != null) {
                    overlay.showQuickResume();
                }
                return;
            case RADIAL:
                if (overlay != null) {
                    overlay.showRadial();
                }
                return;
            case LAST_GAME:
                lastGame();
                return;
            case SETTINGS:
                openPocket(HomeActivity.ACTION_SETTINGS);
                return;
            case RECENTS:
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                return;
            case ANDROID_HOME:
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                return;
            case BACK:
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                return;
            case SCREENSHOT:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
                }
                return;
            case NOTIFICATIONS:
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
                return;
            case QUICK_SETTINGS:
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
                return;
            case PLAY_PAUSE:
                mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                return;
            case NEXT_TRACK:
                mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
                return;
            case VOLUME_UP:
            case VOLUME_DOWN: {
                AudioManager am = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, a == GlobalButtons.Action.VOLUME_UP
                            ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                }
                return;
            }
            default:
                break;
        }
    }

    private void mediaKey(int code) {
        AudioManager am = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    /** Pocket Nexus's Home in front (the game stays where it is, behind), doing action there. */
    private void openPocket(String action) {
        Intent i = new Intent(service, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (action != null) {
            i.putExtra(HomeActivity.EXTRA_ACTION, action);
        }
        try {
            service.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(service, R.string.pocket_gb_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    /** The game played before the one in front (back and forth between two games). */
    private void lastGame() {
        final String now = front;
        new Thread(() -> {
            HomeItem pick = null;
            long best = 0;
            try {
                for (com.limelight.pocket.home.HomeStore.Entry e : com.limelight.pocket.home.HomeStore.load(service)) {
                    boolean inFront = now != null && HomeItem.ANDROID.equals(e.item.kind) && now.equals(e.item.a);
                    if (!inFront && e.lastUsed > best) {
                        best = e.lastUsed;
                        pick = e.item;
                    }
                }
            } catch (RuntimeException ignored) {
            }
            final HomeItem item = pick;
            main.post(() -> {
                if (item != null) {
                    launch(item);
                }
                else {
                    openPocket(null);
                }
            });
        }, "pocket-last-game").start();
    }

    /** A game picked in Quick Resume: an Android game comes back as it was; others start. */
    @Override
    public void launch(HomeItem item) {
        if (HomeItem.ANDROID.equals(item.kind)) {
            Intent launch = service.getPackageManager().getLaunchIntentForPackage(item.a);
            if (launch != null) {
                try {
                    com.limelight.pocket.home.HomeStore.record(service, item);
                    service.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (RuntimeException ignored) {
                }
            }
        }
        Intent i = new Intent(service, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(HomeActivity.EXTRA_ACTION, HomeActivity.ACTION_LAUNCH)
                .putExtra(HomeActivity.EXTRA_ITEM, item.toLine());
        try {
            service.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(service, R.string.pocket_gb_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    public void destroy() {
        main.removeCallbacksAndMessages(null);
        if (overlay != null) {
            overlay.hide();
        }
    }
}
