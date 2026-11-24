package com.limelight.pocket;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * A check of the way to the PC, before playing: the Wi-Fi (its band, its signal, how fast the
 * handheld's link is) and, for each PC, how long a reply takes and how much that wobbles (the
 * two things a stream feels most), with what to do about it and a bit rate the link can carry.
 */
public final class ConnectionCheck {

    public static final class Pc {
        public final String name;
        public final int avgMs;
        public final int jitterMs;
        public final int lost;
        public final int tries;

        Pc(String name, int avgMs, int jitterMs, int lost, int tries) {
            this.name = name;
            this.avgMs = avgMs;
            this.jitterMs = jitterMs;
            this.lost = lost;
            this.tries = tries;
        }
    }

    public static final class Result {
        public boolean wifi;
        public boolean usb;
        /** Through a VPN (Tailscale, ZeroTier...): playing away from home. */
        public boolean vpn;
        public int bandGhz10; // 24, 50, 60 (2.4, 5, 6 GHz)
        public int rssi;
        public int signal; // 0..4
        public int linkMbps;
        public final List<Pc> pcs = new ArrayList<>();
        /** Conservative starting bitrate, not measured throughput; zero when evidence is insufficient. */
        public int suggestedMbps;
        public final List<String> advice = new ArrayList<>();
    }

    public interface Done {
        void onResult(Result r);
    }

    private ConnectionCheck() {
    }

    public static void run(Context context, final List<ComputerDetails> pcs, final Done done) {
        final Context c = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Result r = new Result();
            wifi(c, r);
            for (ComputerDetails pc : pcs) {
                if (pc.activeAddress == null || pc.activeAddress.address == null) {
                    continue;
                }
                r.pcs.add(ping(pc.name, pc.activeAddress.address, pc.activeAddress.port > 0 ? pc.activeAddress.port : 47989));
            }
            advise(c, r);
            main.post(() -> done.onResult(r));
        }, "pocket-connection-check").start();
    }

    @SuppressWarnings("deprecation")
    private static void wifi(Context c, Result r) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc != null) {
                r.wifi = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                r.usb = nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || nc.hasTransport(NetworkCapabilities.TRANSPORT_USB);
                r.vpn = nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            }
        }
        WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
        if (wm == null || !r.wifi) {
            return;
        }
        WifiInfo info = wm.getConnectionInfo();
        if (info == null) {
            return;
        }
        int f = info.getFrequency();
        r.bandGhz10 = f >= 5925 ? 60 : f >= 4900 ? 50 : f > 0 ? 24 : 0;
        r.rssi = info.getRssi();
        r.signal = WifiManager.calculateSignalLevel(r.rssi, 5);
        r.linkMbps = Math.max(info.getLinkSpeed(), android.os.Build.VERSION.SDK_INT >= 29 ? info.getRxLinkSpeedMbps() : 0);
    }

    /** Ten knocks on the PC's door: how long each answer took, and how many never came. */
    private static Pc ping(String name, String host, int port) {
        int tries = 10;
        List<Long> times = new ArrayList<>();
        int lost = 0;
        for (int i = 0; i < tries; i++) {
            long t0 = System.nanoTime();
            try (Socket s = new Socket()) {
                s.setTcpNoDelay(true);
                s.connect(new InetSocketAddress(host, port), 1500);
                times.add((System.nanoTime() - t0) / 1_000_000L);
            } catch (Exception e) {
                lost++;
            }
            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                break;
            }
        }
        if (times.isEmpty()) {
            return new Pc(name, -1, -1, lost, tries);
        }
        long sum = 0;
        for (long t : times) {
            sum += t;
        }
        double avg = sum / (double) times.size();
        double var = 0;
        for (long t : times) {
            var += (t - avg) * (t - avg);
        }
        int jitter = (int) Math.round(Math.sqrt(var / times.size()));
        return new Pc(name, (int) Math.round(avg), jitter, lost, tries);
    }

    private static void advise(Context c, Result r) {
        if (r.usb) {
            r.advice.add(c.getString(R.string.pocket_cc_usb_good));
            // A wired interface is not a throughput measurement.
        }
        else if (r.wifi) {
            if (r.bandGhz10 == 24) {
                r.advice.add(c.getString(R.string.pocket_cc_24ghz));
            }
            if (r.signal <= 1) {
                r.advice.add(c.getString(R.string.pocket_cc_weak));
            }
            // PHY link rate alone cannot establish available streaming bandwidth.
        }
        else {
            r.advice.add(c.getString(R.string.pocket_cc_no_network));
        }
        if (r.vpn) {
            // Away from home: the internet in between carries less than the home network
            r.advice.add(c.getString(R.string.pocket_cc_vpn));
            // VPN transport does not establish whether the peer is direct or relayed.
        }
        for (Pc pc : r.pcs) {
            if (pc.avgMs < 0) {
                r.advice.add(c.getString(R.string.pocket_cc_pc_silent, pc.name));
            }
            else if (pc.jitterMs > 12 || pc.lost > 1) {
                r.advice.add(c.getString(R.string.pocket_cc_pc_unsteady, pc.name));
            }
            else if (pc.avgMs > 25) {
                r.advice.add(c.getString(R.string.pocket_cc_pc_slow, pc.name));
            }
        }
        // Only recommend a profile when one host was tested, so measurements from
        // different computers are never combined into a fictitious connection.
        if (r.pcs.size() == 1) {
            Pc pc = r.pcs.get(0);
            r.suggestedMbps = com.limelight.pocket.streaming.HorizonLinkPolicy.suggestMbps(
                    r.usb, r.wifi, r.vpn, r.signal, r.bandGhz10, r.linkMbps,
                    pc.tries, pc.lost, pc.avgMs, pc.jitterMs);
        }
        if (r.advice.isEmpty()) {
            r.advice.add(c.getString(R.string.pocket_cc_all_good));
        }
    }
}
