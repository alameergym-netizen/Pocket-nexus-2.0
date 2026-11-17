package com.limelight.pocket.apps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.global.AppHistory;
import com.limelight.pocket.home.HomeItem;
import com.limelight.pocket.home.HomeStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Apps installed and removed while Pocket Nexus runs: a removed app leaves everything at once
 * (the Android section, Home, Quick Resume, the dock), a new game joins the Android section by
 * itself, and every screen showing apps builds itself again — no need to leave and come back.
 */
public final class AppWatcher {
    private AppWatcher() {}

    /** New games join the Android section by themselves (on unless turned off). */
    public static final String KEY_AUTO_ADD = "pocket_apps_auto_add";

    public interface Listener {
        /** On the main thread: an app came or went. */
        void onAppsChanged();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static boolean started;

    public static void listen(Listener l) {
        synchronized (LISTENERS) {
            if (!LISTENERS.contains(l)) {
                LISTENERS.add(l);
            }
        }
    }

    public static void stop(Listener l) {
        synchronized (LISTENERS) {
            LISTENERS.remove(l);
        }
    }

    /** Starts watching (once, for as long as Pocket Nexus runs). */
    public static synchronized void start(Context context) {
        if (started) {
            return;
        }
        started = true;
        final Context app = context.getApplicationContext();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                Uri data = intent.getData();
                String pkg = data != null ? data.getSchemeSpecificPart() : null;
                if (pkg == null || pkg.equals(app.getPackageName())) {
                    return;
                }
                boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                String action = intent.getAction();
                if (Intent.ACTION_PACKAGE_REMOVED.equals(action) || Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)) {
                    if (!replacing) {
                        removed(app, pkg);
                    }
                }
                else if (Intent.ACTION_PACKAGE_ADDED.equals(action) && !replacing) {
                    added(app, pkg);
                }
                notifyChanged();
            }
        };
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
            }
            else {
                app.registerReceiver(r, f);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void notifyChanged() {
        MAIN.post(() -> {
            List<Listener> copy;
            synchronized (LISTENERS) {
                copy = new ArrayList<>(LISTENERS);
            }
            for (Listener l : copy) {
                try {
                    l.onAppsChanged();
                } catch (RuntimeException ignored) {
                }
            }
        });
    }

    /** Gone from the handheld: gone from Pocket Nexus too. */
    private static void removed(Context c, String pkg) {
        AndroidLibrary.remove(c, pkg);
        AppHistory.forget(c, pkg);
        for (HomeItem p : HomeStore.pins(c)) {
            if ((HomeItem.ANDROID.equals(p.kind) || HomeItem.SHORTCUT.equals(p.kind)) && pkg.equals(p.a)) {
                HomeStore.unpin(c, p);
            }
        }
    }

    /** A new game: into the Android section (its art is looked up when it's first shown). */
    private static void added(Context c, String pkg) {
        if (!PocketPrefs.prefs(c).getBoolean(KEY_AUTO_ADD, true) || !AndroidLibrary.isSetUp(c)) {
            return;
        }
        try {
            PackageManager pm = c.getPackageManager();
            if (pm.getLaunchIntentForPackage(pkg) == null) {
                return;
            }
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            int type = AndroidAppsActivity.typeOf(pkg, label, ai);
            if (type == AndroidAppsActivity.TYPE_GAME || type == AndroidAppsActivity.TYPE_EMULATOR) {
                Set<String> now = new HashSet<>(AndroidLibrary.packages(c));
                now.add(pkg);
                AndroidLibrary.set(c, now);
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
        }
    }

    /** True when this app is still on the handheld. */
    public static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
