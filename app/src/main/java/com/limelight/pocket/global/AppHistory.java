package com.limelight.pocket.global;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.home.HomeItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The apps used last, in order, for Quick Resume: every app that came to the front (whoever
 * opened it: Pocket Nexus, another launcher, a notification), as seen by Pocket Nexus's
 * accessibility service, and what Pocket Nexus last started in each (an emulator's game, so it
 * shows as that game with its art, not as the emulator).
 */
public final class AppHistory {
    private AppHistory() {}

    private static final String KEY = "pocket_app_history";
    private static final String ITEM = "pocket_app_item_";
    private static final int MAX = 16;

    /** Packages that are never "an app you were using" (the system's own screens, launchers). */
    private static Set<String> skip;

    private static synchronized Set<String> skip(Context c) {
        if (skip != null) {
            return skip;
        }
        Set<String> s = new HashSet<>();
        s.add(c.getPackageName());
        s.add("com.android.systemui");
        s.add("android");
        s.add("com.android.settings");
        s.add("com.google.android.permissioncontroller");
        s.add("com.android.permissioncontroller");
        s.add("com.android.packageinstaller");
        s.add("com.google.android.packageinstaller");
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(home, 0)) {
                if (ri.activityInfo != null) {
                    s.add(ri.activityInfo.packageName);
                }
            }
        } catch (RuntimeException ignored) {
        }
        skip = s;
        return s;
    }

    /** An app came to the front. */
    public static void seen(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty() || skip(c).contains(pkg) || pkg.contains("inputmethod")
                || pkg.contains("keyboard")) {
            return;
        }
        SharedPreferences p = PocketPrefs.prefs(c);
        List<String> list = list(p);
        if (!list.isEmpty() && list.get(0).equals(pkg)) {
            return;
        }
        if (c.getPackageManager().getLaunchIntentForPackage(pkg) == null) {
            return; // not an app with a launcher icon (a system dialog, an overlay)
        }
        list.remove(pkg);
        list.add(0, pkg);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        p.edit().putString(KEY, android.text.TextUtils.join("\n", list)).apply();
    }

    /** Pocket Nexus started this game in this app (an emulator, or the game itself). */
    public static void started(Context c, String pkg, HomeItem item) {
        if (pkg == null || item == null) {
            return;
        }
        PocketPrefs.prefs(c).edit().putString(ITEM + pkg, item.toLine()).apply();
        seen(c, pkg);
    }

    /** The game Pocket Nexus last started in this app, or null. */
    public static HomeItem itemFor(Context c, String pkg) {
        return HomeItem.parse(PocketPrefs.prefs(c).getString(ITEM + pkg, null));
    }

    /** The apps used last, newest first (still installed). */
    public static List<String> recent(Context c) {
        List<String> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        for (String pkg : list(PocketPrefs.prefs(c))) {
            if (pm.getLaunchIntentForPackage(pkg) != null) {
                out.add(pkg);
            }
        }
        return out;
    }

    /** Forgets an app (closed from Quick Resume, or uninstalled). */
    public static void forget(Context c, String pkg) {
        SharedPreferences p = PocketPrefs.prefs(c);
        List<String> list = list(p);
        if (list.remove(pkg)) {
            p.edit().putString(KEY, android.text.TextUtils.join("\n", list)).apply();
        }
    }

    private static List<String> list(SharedPreferences p) {
        List<String> out = new ArrayList<>();
        String s = p.getString(KEY, "");
        for (String line : s.split("\n")) {
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }
}
