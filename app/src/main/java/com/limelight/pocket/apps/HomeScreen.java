package com.limelight.pocket.apps;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;

import com.limelight.PcView;
import com.limelight.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pocket Nexus as the device's home screen. Android starts this when Home is pressed; it brings
 * the Pocket Nexus home screen forward (or the stream, while one is running) and goes away.
 */
public class HomeScreen extends Activity {

    /** True while a stream is open (set by the stream itself). */
    public static volatile boolean streaming;

    /** True when Pocket Nexus is the device's home app. */
    public static boolean isDefault(Context context) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo info = context.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    && context.getPackageName().equals(info.activityInfo.packageName);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Android's "Default home app" page (where Pocket Nexus can be picked, or another launcher). */
    public static void openChooser(Activity from) {
        try {
            from.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (RuntimeException e) {
            try {
                from.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Opens an installed Android launcher even when Pocket Nexus is the default. */
    public static void openOtherLauncher(Activity from) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            PackageManager pm = from.getPackageManager();
            List<ComponentName> launchers = otherLaunchers(from, pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY));
            ResolveInfo resolved = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            ComponentName preferred = resolved != null && resolved.activityInfo != null
                    ? new ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name) : null;
            if (launchers.contains(preferred)) {
                from.startActivity(home.setComponent(preferred));
                return;
            }
            if (!launchers.isEmpty()) {
                Intent first = new Intent(home).setComponent(launchers.get(0));
                if (launchers.size() == 1) {
                    from.startActivity(first);
                } else {
                    Intent[] others = new Intent[launchers.size() - 1];
                    for (int i = 1; i < launchers.size(); i++) {
                        others[i - 1] = new Intent(home).setComponent(launchers.get(i));
                    }
                    Intent chooser = Intent.createChooser(first, from.getString(R.string.horizon_other_launcher));
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, others);
                    from.startActivity(chooser);
                }
                return;
            }
        } catch (RuntimeException ignored) {
            // OEM launchers may be hidden or may stop accepting external start requests.
        }
        openChooser(from);
    }

    static List<ComponentName> otherLaunchers(Context context, List<ResolveInfo> entries) {
        Set<ComponentName> launchers = new LinkedHashSet<>();
        for (ResolveInfo resolution : entries) {
            ActivityInfo info = resolution.activityInfo;
            if (info == null || !info.enabled || !info.exported || info.packageName == null || info.name == null
                    || (info.applicationInfo != null && !info.applicationInfo.enabled)
                    || context.getPackageName().equals(info.packageName) || "android".equals(info.packageName)
                    || "com.android.settings.FallbackHome".equals(info.name)
                    || (info.permission != null && context.checkCallingOrSelfPermission(info.permission)
                    != PackageManager.PERMISSION_GRANTED)) continue;
            launchers.add(new ComponentName(info.packageName, info.name));
        }
        return new ArrayList<>(launchers);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, PcView.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        if (streaming) {
            // Like tapping the app's icon: the task comes back as it was (the stream on top)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
        else {
            // Home: Pocket Nexus's Home, whatever screen was open above it
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(PcView.EXTRA_SHOW_HOME, true);
        }
        try {
            startActivity(intent);
            overridePendingTransition(0, 0);
        } catch (RuntimeException ignored) {
        }
        finish();
    }
}
