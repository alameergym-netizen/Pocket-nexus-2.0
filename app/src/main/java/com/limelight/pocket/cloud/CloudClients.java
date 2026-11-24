package com.limelight.pocket.cloud;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;

import com.limelight.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The ways to play a cloud service, best first: its official app (when it's installed), another
 * full app made for it (XStreaming for Xbox Cloud Gaming), Chrome (a real browser: Google's
 * sign-in and the microphone work, as in Chrome itself), or Pocket Nexus's own built-in player
 * (quick, full screen, with the controller menu; no Google sign-in, which Google allows only in a
 * browser). The one picked is kept per service.
 */
public final class CloudClients {
    private CloudClients() {}

    public static final int OFFICIAL = 0;
    public static final int APP = 1;
    public static final int BROWSER = 2;
    public static final int BUILT_IN = 3;

    private static final String KEY = "pocket_cloud_client_";
    private static final String CHROME = "com.android.chrome";

    /** Other full apps for a service (open source ones, with their own extras). */
    private static String[] altPackages(CloudService s) {
        if (s == CloudService.XCLOUD) {
            return new String[]{"com.xstreaming"};
        }
        return new String[0];
    }

    public static final class Client {
        public final int kind;
        /** The app's package (official / another app / the browser), null for the built-in one. */
        public final String pkg;
        public final String label;

        Client(int kind, String pkg, String label) {
            this.kind = kind;
            this.pkg = pkg;
            this.label = label;
        }

        public String id() {
            return kind + ":" + (pkg != null ? pkg : "");
        }
    }

    /** Every way this service can be played on the handheld now, best first. */
    public static List<Client> available(Context c, CloudService s) {
        List<Client> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        for (String p : s.officialPackages) {
            if (pm.getLaunchIntentForPackage(p) != null) {
                out.add(new Client(OFFICIAL, p, c.getString(R.string.pocket_cloud_with_official, label(pm, p))));
                break;
            }
        }
        for (String p : altPackages(s)) {
            if (pm.getLaunchIntentForPackage(p) != null) {
                out.add(new Client(APP, p, c.getString(R.string.pocket_cloud_with_app, label(pm, p))));
            }
        }
        String browser = browser(c);
        if (browser != null) {
            out.add(new Client(BROWSER, browser, c.getString(R.string.pocket_cloud_with_browser, label(pm, browser))));
        }
        out.add(builtIn(c, s));
        return out;
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** A browser that can open a tab for an app (Chrome first), or null. */
    static String browser(Context c) {
        try {
            List<String> all = new ArrayList<>();
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"));
            for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(web, 0)) {
                if (ri.activityInfo != null) {
                    all.add(ri.activityInfo.packageName);
                }
            }
            if (all.contains(CHROME)) {
                return CHROME;
            }
            return CustomTabsClient.getPackageName(c, all, true);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The way this service is played: the one picked (while it's still there), else the best. */
    public static Client preferred(Context c, CloudService s) {
        List<Client> all = available(c, s);
        String want = prefs(c).getString(KEY + s.id, null);
        if (want != null) {
            for (Client cl : all) {
                if (cl.id().equals(want)) {
                    return cl;
                }
            }
        }
        if (s == CloudService.XCLOUD && BetterXcloud.enabled(c)) {
            // Better xCloud lives in the built-in player: that one, unless another was picked
            return all.get(all.size() - 1);
        }
        return all.get(0);
    }

    public static void setPreferred(Context c, CloudService s, Client cl) {
        prefs(c).edit().putString(KEY + s.id, cl.id()).apply();
    }

    /** Kept apart (and read fresh): the built-in players run in processes of their own. */
    @SuppressWarnings("deprecation")
    private static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("pocket_cloud_clients", Context.MODE_MULTI_PROCESS);
    }

    /** Pocket Nexus's own player, or Chrome (when Google's sign-in is needed). */
    public static Client builtIn(Context c) {
        return new Client(BUILT_IN, null, c.getString(R.string.pocket_cloud_with_builtin));
    }

    /** The built-in player, named with Better xCloud for Xbox Cloud Gaming when that's on. */
    static Client builtIn(Context c, CloudService s) {
        boolean bx = s == CloudService.XCLOUD && BetterXcloud.enabled(c);
        return new Client(BUILT_IN, null, c.getString(bx ? R.string.pocket_cloud_with_builtin_bx
                : R.string.pocket_cloud_with_builtin));
    }

    public static Client browserClient(Context c) {
        String b = browser(c);
        return b != null ? new Client(BROWSER, b, c.getString(R.string.pocket_cloud_with_browser,
                label(c.getPackageManager(), b))) : null;
    }

    /** Plays the service (url: a game's own page to start straight in it; null: the service). */
    public static HorizonCloudDispatch.Result play(Activity a, CloudService s, String url) {
        if (a == null || s == null || a.isFinishing() || a.isDestroyed()) return HorizonCloudDispatch.Result.REJECTED;
        return play(a, s, preferred(a, s), url);
    }

    public static HorizonCloudDispatch.Result play(Activity a, CloudService s, Client cl, String url) {
        if (a == null || s == null || cl == null || a.isFinishing() || a.isDestroyed()) {
            return HorizonCloudDispatch.Result.REJECTED;
        }
        if (!HorizonCloudDispatch.claim(com.limelight.pocket.core.HorizonLaunchRequests.shared(),
                s.id, url, android.os.SystemClock.elapsedRealtime())) {
            return HorizonCloudDispatch.Result.REJECTED;
        }
        String target = url != null && !url.isEmpty() ? url : s.url;
        try {
            switch (cl.kind) {
                case OFFICIAL:
                case APP: {
                    // The game's link in the app when it takes links, else the app itself
                    Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(target)).setPackage(cl.pkg)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (url != null && view.resolveActivity(a.getPackageManager()) != null) {
                        a.startActivity(view);
                        return HorizonCloudDispatch.Result.EXTERNAL_LINK_DISPATCHED;
                    }
                    Intent launch = a.getPackageManager().getLaunchIntentForPackage(cl.pkg);
                    if (launch != null) {
                        a.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        return HorizonCloudDispatch.Result.EXTERNAL_APP_DISPATCHED;
                    }
                    break;
                }
                case BROWSER: {
                    CustomTabsIntent tab = new CustomTabsIntent.Builder()
                            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                            .setDefaultColorSchemeParams(new CustomTabColorSchemeParams.Builder()
                                    .setToolbarColor(0xFF0B0B0F)
                                    .setNavigationBarColor(0xFF000000)
                                    .build())
                            .setShowTitle(false)
                            .setUrlBarHidingEnabled(true)
                            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                            .setInstantAppsEnabled(false)
                            .build();
                    if (cl.pkg != null) {
                        tab.intent.setPackage(cl.pkg);
                    }
                    tab.launchUrl(a, Uri.parse(target));
                    return HorizonCloudDispatch.Result.BROWSER_DISPATCHED;
                }
                default:
                    break;
            }
            // Pocket Nexus's own player
            a.startActivity(s.playerIntent(a).putExtra(CloudPlayerActivity.EXTRA_URL, url));
            return HorizonCloudDispatch.Result.INTERNAL_PLAYER_DISPATCHED;
        } catch (RuntimeException e) {
            Toast.makeText(a, R.string.pocket_cloud_cant_open, Toast.LENGTH_LONG).show();
            return HorizonCloudDispatch.Result.REJECTED;
        }
    }
}
