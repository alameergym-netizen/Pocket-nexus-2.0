package com.limelight.pocket;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;

import com.limelight.pocket.streaming.HorizonAdaptiveMemory;

/**
 * Adaptive stream: picks a starting bitrate that the current Wi-Fi link can actually carry.
 * (Lowering the quality during a stream is done by Game, which reconnects at a lower bitrate
 * while the game keeps running on the PC; the result is remembered here for a while.)
 */
public final class PocketAdaptive {
    private static final HorizonAdaptiveMemory MEMORY = new HorizonAdaptiveMemory();

    private PocketAdaptive() { }

    public static final int MIN_KBPS = 5000;

    /** Away from home (a VPN such as Tailscale, or mobile data): the most a stream starts with. */
    public static final String KEY_AWAY_KBPS = "pocket_away_kbps";
    public static final int[] AWAY_STEPS = {0, 10000, 15000, 20000, 30000, 40000};

    public static int awayKbps(Context context) {
        return PocketPrefs.prefs(context).getInt(KEY_AWAY_KBPS, 20000);
    }

    /** Playing away from home now: through a VPN or on mobile data (not the home Wi-Fi). */
    public static boolean away(Context context) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities nc = cm != null ? cm.getNetworkCapabilities(cm.getActiveNetwork()) : null;
            return nc != null && (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                    || nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static HorizonAdaptiveMemory.Scope activeScope(Context context, String pcUuid) {
        if (Build.VERSION.SDK_INT < 23 || pcUuid == null) return null;
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = manager != null ? manager.getActiveNetwork() : null;
            NetworkCapabilities capabilities = network != null ? manager.getNetworkCapabilities(network) : null;
            if (capabilities == null) return null;
            HorizonAdaptiveMemory.Route route;
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) route = HorizonAdaptiveMemory.Route.VPN;
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) route = HorizonAdaptiveMemory.Route.CELLULAR;
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) route = HorizonAdaptiveMemory.Route.ETHERNET;
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) route = HorizonAdaptiveMemory.Route.WIFI;
            else return null;
            return HorizonAdaptiveMemory.scope(pcUuid, network.getNetworkHandle(), route);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void remember(Context context, String pcUuid, HorizonAdaptiveMemory.Scope scope, int kbps) {
        if (scope != null && scope.equals(activeScope(context, pcUuid))) {
            MEMORY.learn(scope, kbps, SystemClock.elapsedRealtime());
        }
    }

    public static void clearLearnedCap(HorizonAdaptiveMemory.Scope scope) {
        MEMORY.clear(scope);
    }

    public static void clearLearnedCaps() {
        MEMORY.clearAll();
    }

    /** The bitrate to start a Wi-Fi stream with, never above what the player chose. */
    public static int startBitrate(Context context, int configuredKbps, HorizonAdaptiveMemory.Scope scope) {
        // Home and away: away from home, the internet in between carries less than the home Wi-Fi
        int awayCap = awayKbps(context);
        if (awayCap > 0 && away(context)) {
            configuredKbps = Math.min(configuredKbps, awayCap);
        }
        if (!PocketPrefs.adaptiveStream(context)) {
            return configuredKbps;
        }
        int result = configuredKbps;
        if (scope == null || scope.route == HorizonAdaptiveMemory.Route.WIFI) {
            result = wifiStartCap(context, result);
        }
        int learned = MEMORY.cap(scope, SystemClock.elapsedRealtime());
        if (learned > 0) result = Math.min(result, learned);
        return Math.max(Math.min(MIN_KBPS, configuredKbps), Math.min(configuredKbps, result));
    }

    private static int wifiStartCap(Context context, int result) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            @SuppressWarnings("deprecation")
            WifiInfo info = wifi != null ? wifi.getConnectionInfo() : null;
            if (info != null && info.getNetworkId() != -1) {
                int linkMbps = info.getLinkSpeed();
                int rssi = info.getRssi();
                if (linkMbps > 0) {
                    // Real throughput is about half the link rate, and the stream needs headroom
                    result = (int) Math.min(result, linkMbps * 450L);
                }
                if (rssi < -75) {
                    result = Math.min(result, 20000);
                }
                else if (rssi < -67) {
                    result = Math.min(result, 40000);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return result;
    }

    /** One step down from the current bitrate (about two thirds), or the same if at the floor. */
    public static int stepDown(int currentKbps) {
        int next = (int) (currentKbps * 0.65f) / 1000 * 1000;
        return Math.max(MIN_KBPS, next);
    }
}
