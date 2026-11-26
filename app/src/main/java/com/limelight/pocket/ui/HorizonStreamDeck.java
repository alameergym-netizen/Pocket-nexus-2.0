package com.limelight.pocket.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.RecentGames;
import com.limelight.pocket.ui.HorizonStreamCards.Host;
import com.limelight.pocket.ui.HorizonStreamCards.Link;
import com.limelight.pocket.ui.HorizonStreamCards.Reach;
import com.limelight.pocket.ui.HorizonStreamCards.Route;
import com.limelight.pocket.ui.HorizonStreamCards.Session;
import com.limelight.pocket.ui.HorizonStreamCards.SessionKind;
import com.limelight.pocket.ui.HorizonStreamCards.Transport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nexus Horizon's three streaming cards (host, session, network) for Home, the Library and
 * Nexus Control. They show only what was observed (see {@link HorizonStreamCards}); each opens
 * the place that changes it: the Streaming section, the running game, Android's network panel.
 */
public final class HorizonStreamDeck extends LinearLayout {
    /** What a card does when chosen. */
    public interface Actions {
        void openHosts();

        /** The host reported this game running; null: nothing to resume. */
        void resume(Host running);

        void openNetwork();
    }

    private final float d;
    private final boolean compact;
    private final Actions actions;
    private final Card host;
    private final Card session;
    private final Card network;
    private Host running;
    private boolean stacked;
    /** Where the controller goes past the cards: up (the games) and back toward the start. */
    private Runnable edgeUp;
    private Runnable edgeStart;
    private Runnable edgeDown;
    private boolean slim;

    public HorizonStreamDeck(Context context, boolean compact, Actions actions) {
        super(context);
        this.d = context.getResources().getDisplayMetrics().density;
        this.compact = compact;
        this.actions = actions;
        setId(R.id.horizon_deck);
        setClipChildren(false);
        setClipToPadding(false);
        setContentDescription(context.getString(R.string.horizon_deck_cards));
        host = new Card(context, R.id.horizon_deck_host, "DESKTOP", R.string.horizon_deck_host);
        session = new Card(context, R.id.horizon_deck_session, "PLAY", R.string.horizon_deck_session);
        network = new Card(context, R.id.horizon_deck_network, "WIFI", R.string.horizon_deck_network);
        host.setOnClickListener(v -> {
            PocketSounds.play(getContext(), PocketSounds.OPEN);
            actions.openHosts();
        });
        session.setOnClickListener(v -> {
            PocketSounds.play(getContext(), PocketSounds.SELECT);
            if (running != null) actions.resume(running);
            else actions.openHosts();
        });
        network.setOnClickListener(v -> {
            PocketSounds.play(getContext(), PocketSounds.OPEN);
            actions.openNetwork();
        });
        arrange(false);
    }

    /** Side by side (a row of three) or stacked (three slim rows, for narrow room). */
    public void arrange(boolean stacked) {
        arrange(stacked, stacked);
    }

    /**
     * {@code stacked}: one above another, else side by side. {@code slim}: each card one line
     * (its kind and its answer; the detail is still read out), else a small two-line card.
     */
    public void arrange(boolean stacked, boolean slim) {
        this.stacked = stacked;
        this.slim = slim;
        setOrientation(stacked ? VERTICAL : HORIZONTAL);
        removeAllViews();
        Card[] cards = {host, session, network};
        for (int i = 0; i < cards.length; i++) {
            cards[i].shape(slim);
            LayoutParams lp = stacked ? new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                if (stacked) lp.topMargin = dp(6);
                else lp.setMarginStart(dp(8));
            }
            addView(cards[i], lp);
        }
    }

    public boolean stacked() {
        return stacked;
    }

    public boolean slim() {
        return slim;
    }

    /**
     * What lies beyond the cards for the controller: {@code up} above them, {@code start} on the
     * reading-start side (null: the focus stays on the cards). Between cards it moves normally.
     */
    public void setEdges(Runnable up, Runnable start) {
        setEdges(up, start, null);
    }

    /** As above, with {@code down} below the cards (null: the focus stays). */
    public void setEdges(Runnable up, Runnable start, Runnable down) {
        edgeUp = up;
        edgeStart = start;
        edgeDown = down;
    }

    /** Whether the view is one of the cards (screens that steer the focus themselves ask). */
    public boolean owns(View v) {
        return v == host || v == session || v == network;
    }

    /** The cards in order (the host first). */
    private View[] cards() {
        return new View[]{host, session, network};
    }

    /** A controller key on a card: A chooses it, the D-pad stops at the edges or leaves by them. */
    boolean key(View card, int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.getAction() == KeyEvent.ACTION_UP) card.performClick();
            return true;
        }
        View[] all = cards();
        int at = card == all[0] ? 0 : card == all[1] ? 1 : 2;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int toStart = rtl ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        int toEnd = rtl ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        int back = stacked ? KeyEvent.KEYCODE_DPAD_UP : toStart;
        int forward = stacked ? KeyEvent.KEYCODE_DPAD_DOWN : toEnd;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (code == back && at > 0) {
            if (down) all[at - 1].requestFocus();
            return true;
        }
        if (code == forward && at < all.length - 1) {
            if (down) all[at + 1].requestFocus();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (down && edgeUp != null) edgeUp.run();
            return true;
        }
        if (code == toStart) {
            if (down && edgeStart != null) edgeStart.run();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down && edgeDown != null) edgeDown.run();
            return true;
        }
        // Past the last card: nothing there
        return code == toEnd;
    }

    /** The card the controller lands on first. */
    public View first() {
        return host;
    }

    /** Fills the cards (main thread). */
    public void show(List<Host> hosts, Link link) {
        Context c = getContext();
        long now = SystemClock.elapsedRealtime();
        Host featured = HorizonStreamCards.featured(hosts, now);
        if (featured == null) {
            host.fill(c.getString(R.string.horizon_deck_no_host), c.getString(R.string.horizon_deck_pair_hint),
                    Tone.IDLE);
        }
        else {
            Reach reach = featured.reachAt(now);
            String state;
            Tone tone;
            if (reach == Reach.ONLINE) {
                state = c.getString(R.string.horizon_deck_online);
                String route = route(c, featured.route);
                if (route != null) state = c.getString(R.string.horizon_deck_joined, state, route);
                tone = Tone.GOOD;
            }
            else if (reach == Reach.OFFLINE) {
                state = c.getString(R.string.horizon_deck_offline);
                tone = Tone.BAD;
            }
            else {
                state = featured.observedAt > 0
                        ? c.getString(R.string.horizon_deck_seen, DateUtils.getRelativeTimeSpanString(
                                featured.observedAt, now, DateUtils.MINUTE_IN_MILLIS))
                        : c.getString(R.string.horizon_deck_not_checked);
                tone = Tone.IDLE;
            }
            int others = hosts.size() - 1;
            if (others > 0) {
                state = c.getString(R.string.horizon_deck_joined, state,
                        c.getResources().getQuantityString(R.plurals.horizon_deck_more_pcs, others, others));
            }
            host.fill(featured.name, state, tone);
        }

        Session s = HorizonStreamCards.session(hosts, now);
        running = s.kind == SessionKind.RUNNING ? s.host : null;
        switch (s.kind) {
            case RUNNING:
                session.fill(s.host.runningName != null ? s.host.runningName : c.getString(R.string.horizon_deck_game_running),
                        c.getString(R.string.horizon_deck_running_on, s.host.name), Tone.GOOD);
                break;
            case IDLE:
                session.fill(c.getString(R.string.horizon_deck_idle), last(c, s.last), Tone.IDLE);
                break;
            case UNKNOWN:
                session.fill(c.getString(R.string.horizon_deck_waiting), last(c, s.last), Tone.IDLE);
                break;
            default:
                session.fill(c.getString(R.string.horizon_deck_idle), c.getString(R.string.horizon_deck_pair_hint),
                        Tone.IDLE);
                break;
        }

        String netValue;
        List<String> details = new ArrayList<>();
        Tone netTone = Tone.GOOD;
        switch (link.transport) {
            case WIFI:
                String band = HorizonStreamCards.band(link.bandGhz10);
                netValue = band != null ? c.getString(R.string.horizon_deck_wifi_band, band)
                        : c.getString(R.string.horizon_deck_wifi);
                if (link.signal >= 0) details.add(c.getString(R.string.horizon_deck_signal, link.signal));
                if (link.linkMbps > 0) details.add(c.getString(R.string.horizon_deck_link, link.linkMbps));
                if (link.signal >= 0 && link.signal <= 1) netTone = Tone.BAD;
                break;
            case WIRED:
                netValue = c.getString(R.string.horizon_deck_wired);
                break;
            case CELLULAR:
                netValue = c.getString(R.string.horizon_deck_cellular);
                break;
            case OTHER:
                netValue = c.getString(R.string.horizon_deck_connected);
                break;
            default:
                netValue = c.getString(R.string.horizon_deck_no_network);
                netTone = Tone.BAD;
                break;
        }
        if (link.vpn) details.add(c.getString(R.string.horizon_deck_vpn));
        if (details.isEmpty()) {
            details.add(c.getString(link.transport == Transport.NONE ? R.string.horizon_deck_no_network_hint
                    : R.string.horizon_deck_no_details));
        }
        network.fill(netValue, TextUtils.join(" · ", details), netTone);
    }

    private static String last(Context c, Host last) {
        return last != null && last.lastGame != null ? c.getString(R.string.horizon_deck_last, last.lastGame)
                : c.getString(R.string.horizon_deck_none_yet);
    }

    private static String route(Context c, Route route) {
        switch (route) {
            case LOCAL: return c.getString(R.string.horizon_deck_route_local);
            case REMOTE: return c.getString(R.string.horizon_deck_route_remote);
            case MANUAL: return c.getString(R.string.horizon_deck_route_manual);
            default: return null;
        }
    }

    // ---- Where the numbers come from ------------------------------------------------------------

    /** What Android reports about the network in use (no measurement, nothing sent). */
    @SuppressWarnings("deprecation")
    public static Link readLink(Context context) {
        Context c = context.getApplicationContext();
        Transport transport = Transport.NONE;
        boolean vpn = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= 23) {
                Network active = cm.getActiveNetwork();
                NetworkCapabilities nc = active != null ? cm.getNetworkCapabilities(active) : null;
                if (nc != null) {
                    vpn = nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = Transport.WIFI;
                    else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                            || (Build.VERSION.SDK_INT >= 31 && nc.hasTransport(NetworkCapabilities.TRANSPORT_USB))) {
                        transport = Transport.WIRED;
                    }
                    else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = Transport.CELLULAR;
                    else transport = Transport.OTHER;
                }
            }
            else if (cm != null) {
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info != null && info.isConnected()) {
                    int type = info.getType();
                    transport = type == ConnectivityManager.TYPE_WIFI ? Transport.WIFI
                            : type == ConnectivityManager.TYPE_ETHERNET ? Transport.WIRED
                            : type == ConnectivityManager.TYPE_MOBILE ? Transport.CELLULAR : Transport.OTHER;
                    vpn = type == ConnectivityManager.TYPE_VPN;
                }
            }
        } catch (RuntimeException unavailable) {
            transport = Transport.NONE;
        }
        int band = 0;
        int signal = -1;
        int link = -1;
        if (transport == Transport.WIFI) {
            try {
                WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                if (info != null) {
                    int f = info.getFrequency();
                    band = f >= 5925 ? 60 : f >= 4900 ? 50 : f > 0 ? 24 : 0;
                    int rssi = info.getRssi();
                    signal = rssi > -127 && rssi < 0 ? WifiManager.calculateSignalLevel(rssi, 5) : -1;
                    link = info.getLinkSpeed();
                }
            } catch (RuntimeException unavailable) {
                // The numbers stay unknown
            }
        }
        return new Link(transport, vpn, band, signal, link);
    }

    /**
     * The saved PCs as hosts, with the game played last on each from the handheld (blocking:
     * reads the database and each PC's cached game list). {@code live} and {@code seen} are what
     * a poll reported (may be empty: Library and Nexus Control then use Home's observations).
     */
    public static List<Host> hosts(Context context, List<ComputerDetails> saved,
                                   Map<String, ComputerDetails> live, Map<String, Long> seen) {
        List<Host> out = new ArrayList<>();
        if (saved == null) {
            return out;
        }
        for (ComputerDetails pc : saved) {
            if (pc == null || pc.uuid == null) {
                continue;
            }
            String lastGame = null;
            long lastPlayed = 0;
            String runningName = null;
            ComputerDetails now = live != null ? live.get(pc.uuid) : null;
            Long at = seen != null ? seen.get(pc.uuid) : null;
            try {
                List<RecentGames.Game> games = RecentGames.forPc(context, pc.uuid, 500);
                if (!games.isEmpty() && games.get(0).lastPlayed > 0) {
                    lastGame = games.get(0).app.getAppName();
                    lastPlayed = games.get(0).lastPlayed;
                }
                if (now != null && now.runningGameId != 0) {
                    for (RecentGames.Game g : games) {
                        if (g.app.getAppId() == now.runningGameId) {
                            runningName = g.app.getAppName();
                            break;
                        }
                    }
                }
            } catch (RuntimeException unreadable) {
                // No history to show for this PC
            }
            if (now != null && at != null && at > 0) {
                Reach reach = now.state == ComputerDetails.State.ONLINE ? Reach.ONLINE
                        : now.state == ComputerDetails.State.OFFLINE ? Reach.OFFLINE : Reach.UNKNOWN;
                Route route = HorizonStreamCards.route(address(now.activeAddress), address(now.localAddress),
                        address(now.remoteAddress), address(now.manualAddress));
                Host observed = new Host(pc.uuid, name(now, pc), reach, reach == Reach.UNKNOWN ? 0 : at, route,
                        reach == Reach.ONLINE ? now.runningGameId : 0, runningName, lastGame, lastPlayed);
                if (observed.observedAt > 0) HorizonStreamCards.observe(observed);
                out.add(observed);
            }
            else {
                out.add(new Host(pc.uuid, name(null, pc), Reach.UNKNOWN, 0, Route.UNKNOWN, 0, null,
                        lastGame, lastPlayed));
            }
        }
        return live == null || live.isEmpty() ? HorizonStreamCards.withObservations(out) : out;
    }

    private static String name(ComputerDetails live, ComputerDetails saved) {
        if (live != null && live.name != null && !live.name.trim().isEmpty()) return live.name;
        return saved.name;
    }

    private static String address(ComputerDetails.AddressTuple t) {
        return t != null ? t.address : null;
    }

    /** Request code for the network panel (its result is not used). */
    public static final int REQUEST_NETWORK_PANEL = 0x4E50;

    /**
     * Android's own network panel (Wi-Fi settings before Android 10). Settings only opens its
     * panels for a caller it can name, so an activity asks for a result.
     */
    public static void openNetworkPanel(Context context) {
        Intent panel = Build.VERSION.SDK_INT >= 29
                ? new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                : new Intent(Settings.ACTION_WIFI_SETTINGS);
        try {
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).startActivityForResult(panel, REQUEST_NETWORK_PANEL);
            }
            else {
                context.startActivity(panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (RuntimeException none) {
            try {
                context.startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)
                        .addFlags(context instanceof android.app.Activity ? 0 : Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
                // Nothing to open on this device
            }
        }
    }

    private int dp(int v) {
        return Math.round(v * d);
    }

    // ---- A card ---------------------------------------------------------------------------------

    private enum Tone { GOOD, BAD, IDLE }

    private final class Card extends LinearLayout {
        private final ImageView glyph;
        private final View dot;
        private final TextView kicker;
        private final TextView value;
        private final TextView detail;
        private final LinearLayout words;
        private final String kind;

        Card(Context c, int id, String icon, int kindRes) {
            super(c);
            setId(id);
            kind = c.getString(kindRes);
            setFocusable(true);
            setClickable(true);
            setGravity(Gravity.CENTER_VERTICAL);
            if (Build.VERSION.SDK_INT >= 26) setDefaultFocusHighlightEnabled(false);
            glyph = new ImageView(c);
            glyph.setImageDrawable(PocketIcons.drawable(icon, dp(18), 0xF2FFFFFF));
            glyph.setScaleType(ImageView.ScaleType.CENTER);
            words = new LinearLayout(c);
            words.setOrientation(VERTICAL);
            LinearLayout top = new LinearLayout(c);
            top.setGravity(Gravity.CENTER_VERTICAL);
            dot = new View(c);
            top.addView(dot, new LayoutParams(dp(7), dp(7)));
            kicker = text(c, 10f, PocketFonts.bold(), HorizonStreamDeck.this.accentText());
            kicker.setText(kind);
            kicker.setAllCaps(true);
            kicker.setLetterSpacing(0.12f);
            LayoutParams kl = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            kl.setMarginStart(dp(6));
            top.addView(kicker, kl);
            words.addView(top);
            value = text(c, compact ? 13f : 15f, PocketFonts.semibold(), 0xFFFFFFFF);
            LayoutParams vl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vl.topMargin = dp(3);
            words.addView(value, vl);
            detail = text(c, 11f, PocketFonts.medium(), 0xC4FFFFFF);
            LayoutParams dl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dl.topMargin = dp(2);
            words.addView(detail, dl);
            setBackground(face(false));
            setOnFocusChangeListener((v, has) -> {
                setBackground(face(has));
                float s = has ? 1.04f : 1f;
                if (PocketPrefs.animationsEnabled(getContext())) {
                    animate().scaleX(s).scaleY(s).setDuration(Spring.UI_DURATION_MS).setInterpolator(Spring.UI_CURVE).start();
                }
                else {
                    setScaleX(s);
                    setScaleY(s);
                }
                if (has) PocketSounds.play(getContext(), PocketSounds.NAV);
            });
            setOnKeyListener(HorizonStreamDeck.this::key);
        }

        /** Stacked: one slim row (the detail is still read out); side by side: a small card. */
        void shape(boolean slim) {
            removeAllViews();
            setOrientation(HORIZONTAL);
            int pad = dp(slim ? 9 : 12);
            setPadding(pad, dp(slim ? 6 : 10), pad, dp(slim ? 6 : 10));
            setMinimumHeight(dp(slim ? 40 : 64));
            addView(glyph, new LayoutParams(dp(slim ? 26 : 32), dp(slim ? 26 : 32)));
            GradientDrawable well = new GradientDrawable();
            well.setCornerRadius(dp(slim ? 8 : 10));
            well.setColor(0x24FFFFFF);
            glyph.setBackground(well);
            LayoutParams wl = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wl.setMarginStart(dp(slim ? 9 : 11));
            addView(words, wl);
            detail.setVisibility(slim ? GONE : VISIBLE);
            if (slim) {
                // Kicker and value share the row: the kind first, the answer after it
                words.setOrientation(HORIZONTAL);
                words.setGravity(Gravity.CENTER_VERTICAL);
                LayoutParams vl = (LayoutParams) value.getLayoutParams();
                vl.width = 0;
                vl.weight = 1f;
                vl.topMargin = 0;
                vl.setMarginStart(dp(8));
                value.setLayoutParams(vl);
            }
            else {
                words.setOrientation(VERTICAL);
                words.setGravity(Gravity.NO_GRAVITY);
                LayoutParams vl = (LayoutParams) value.getLayoutParams();
                vl.width = ViewGroup.LayoutParams.MATCH_PARENT;
                vl.weight = 0f;
                vl.topMargin = dp(3);
                vl.setMarginStart(0);
                value.setLayoutParams(vl);
            }
        }

        void fill(String v, String more, Tone tone) {
            value.setText(v);
            detail.setText(more);
            GradientDrawable lamp = new GradientDrawable();
            lamp.setShape(GradientDrawable.OVAL);
            lamp.setColor(tone == Tone.GOOD ? 0xFF4ADE80 : tone == Tone.BAD ? 0xFFF87171 : 0xFF9CA3AF);
            dot.setBackground(lamp);
            setContentDescription(getContext().getString(R.string.horizon_deck_card_description, kind, v, more));
        }

        private GradientDrawable face(boolean focused) {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{focused ? 0xF0263446 : 0xD81A2432, focused ? 0xF0182230 : 0xD8111822});
            bg.setCornerRadius(dp(14));
            bg.setStroke(focused ? dp(2) : Math.max(1, dp(1)), focused ? HorizonStreamDeck.this.accentText() : 0x3DFFFFFF);
            return bg;
        }
    }

    private int accentText() {
        int accent = PocketTheme.accent(getContext());
        // A pale accent reads on the dark glass; a dark one falls back to white
        return luminance(accent) > 0.18 ? accent : 0xFFFFFFFF;
    }

    /** WCAG relative luminance of an opaque colour. */
    static double luminance(int color) {
        double r = channel((color >> 16) & 0xFF), g = channel((color >> 8) & 0xFF), b = channel(color & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static TextView text(Context c, float sp, android.graphics.Typeface face, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }
}
