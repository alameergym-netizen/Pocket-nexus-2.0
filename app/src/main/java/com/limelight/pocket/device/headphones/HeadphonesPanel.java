package com.limelight.pocket.device.headphones;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.device.headphones.HeadphonesDiscovery.Candidate;
import com.limelight.pocket.ui.StreamMenuView;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Cell;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Level;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Mode;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Phase;
import com.limelight.pocket.ui.headphones.HeadphonesCenterView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Headphones Center: the native view ({@link HeadphonesCenterView}) on a page of the Nexus
 * menu (console panel, Nexus Control, or the in-stream menu), never an external companion app.
 *
 * <p>Transport rules kept from the tested Lab v0.2 panel: one Bluetooth session at a time (the
 * shared discovery worker and socket lease), the model is confirmed by readback before any write,
 * ANC is sent as the earbud-selected level (no level forced), nothing is written unless the user
 * picks it, reads refresh every 15 s while the page is visible and in front, and every read or
 * write is cancelled as soon as the page leaves the screen or the window loses focus.
 */
public final class HeadphonesPanel {
    private interface Host {
        void show(View content);

        /** The page is open (possibly under the device chooser). */
        boolean holds(View content);

        /** The page itself is the visible page. */
        boolean current(View content);

        void push(String title, List<StreamMenuView.Item> rows);

        void pop();
    }

    // Passive Home reads and panel reads share the same discovery worker and socket lease.
    private static final long REFRESH_MS = 15000;
    private final Activity activity;
    private Host host;
    private final HeadphonesCenterView view;
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<Candidate> candidates = Collections.emptyList();
    private BluetoothDevice selected;
    private String selectedName;
    private HeadphonesBluetooth.Session session;
    private HeadphonesReadback.Battery battery;
    private HeadphonesReadback.Noise noise;
    /** A read confirmed the model and reported the noise mode; writes are allowed from here. */
    private boolean ready;
    private boolean reading, stopped, auto = true, writing, suspended, cancelled;
    private boolean discoveryAttempted, manualChoosing;
    private int busyRetries;
    private long nextDiscovery, nextRead, readAt;
    private String discoveryError;
    private Phase phase = Phase.DETECTING;
    /** What is being applied: the selection sent, or one picked during a read (sent after it). */
    private HeadphonesNoiseControl.Selection pending, queued;

    private HeadphonesPanel(Activity activity) {
        this.activity = activity;
        this.view = new HeadphonesCenterView(activity, wideFor(activity));
        view.setListener(new HeadphonesCenterView.Listener() {
            @Override
            public void onMode(Mode mode) {
                ask(selectionOf(mode));
            }

            @Override
            public void onLevel(Level level) {
                ask(selectionOf(level));
            }

            @Override
            public void onRefresh() {
                if (session != null) {
                    cancelled = true;
                    session.cancel();
                    auto = false;
                    queued = null;
                } else if (selected == null) {
                    discover();
                } else {
                    auto = true;
                    start(null);
                }
                publish();
            }

            @Override
            public void onChooseDevice() {
                showChooser();
            }

            @Override
            public void onBluetoothSettings() {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (RuntimeException ignored) {
                }
            }

            @Override
            public void onAllowNearbyDevices() {
                if (Build.VERSION.SDK_INT >= 31) {
                    activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 7013);
                }
            }
        });
    }

    /** Two panes when the menu card can be wide enough (it widens to the screen, up to 660 dp). */
    private static boolean wideFor(Activity a) {
        return a.getResources().getConfiguration().screenWidthDp - 24 >= 520;
    }

    // ---- Opening ---------------------------------------------------------------------------

    public static void open(ConsoleActivity activity) {
        openConsole(activity, false);
    }

    public static void openHome(ConsoleActivity activity) {
        openConsole(activity, true);
    }

    private static void openConsole(ConsoleActivity activity, boolean root) {
        HeadphonesPanel panel = new HeadphonesPanel(activity);
        String title = activity.getString(R.string.horizon_headphones_center);
        panel.host = new Host() {
            public void show(View content) {
                if (root) activity.showConsoleView(title, activity.getString(R.string.horizon_headphones_subtitle), content);
                else activity.pushConsoleView(title, content);
            }

            public boolean holds(View content) {
                return activity.consoleHoldsView(content);
            }

            public boolean current(View content) {
                return activity.isConsoleViewCurrent(content);
            }

            public void push(String t, List<StreamMenuView.Item> rows) {
                activity.pushConsolePage(t, rows);
            }

            public void pop() {
                activity.popConsolePage();
            }
        };
        panel.begin();
    }

    public static void open(Activity activity, StreamMenuView menu) {
        openMenu(activity, menu, false);
    }

    public static void openRoot(Activity activity, StreamMenuView menu) {
        openMenu(activity, menu, true);
    }

    private static void openMenu(Activity activity, StreamMenuView menu, boolean root) {
        HeadphonesPanel panel = new HeadphonesPanel(activity);
        String title = activity.getString(R.string.horizon_headphones_center);
        panel.host = new Host() {
            public void show(View content) {
                if (root) menu.showView(title, activity.getString(R.string.horizon_headphones_subtitle), content,
                        ConsoleActivity.hintA(activity, R.string.pocket_hint_select),
                        ConsoleActivity.hintB(activity, R.string.pocket_hint_back));
                else menu.pushView(title, content);
            }

            public boolean holds(View content) {
                return menu.holdsView(content);
            }

            public boolean current(View content) {
                return menu.isCurrentView(content);
            }

            public void push(String t, List<StreamMenuView.Item> rows) {
                menu.push(t, rows);
            }

            public void pop() {
                menu.popPage();
            }
        };
        panel.begin();
    }

    private void begin() {
        host.show(view);
        publish();
        discover();
        main.postDelayed(tick, 150);
    }

    // ---- Lifecycle -------------------------------------------------------------------------

    private boolean pageAlive() {
        return !stopped && !activity.isFinishing() && !activity.isDestroyed() && host.holds(view);
    }

    private boolean alive() {
        return pageAlive() && activity.hasWindowFocus();
    }

    private void stop() {
        stopped = true;
        main.removeCallbacks(tick);
        if (session != null) session.cancel();
        session = null;
        ready = false;
        queued = null;
    }

    private boolean permitted() {
        return Build.VERSION.SDK_INT >= 31 && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ---- Discovery and reads ---------------------------------------------------------------

    private void discover() {
        if (stopped || reading || session != null) return;
        if (!permitted()) {
            phase = Build.VERSION.SDK_INT < 31 ? Phase.UNSUPPORTED : Phase.PERMISSION;
            publish();
            return;
        }
        discoveryAttempted = true;
        reading = true;
        ready = false;
        selected = null;
        selectedName = null;
        candidates = Collections.emptyList();
        battery = null;
        noise = null;
        readAt = 0;
        discoveryError = null;
        phase = Phase.DETECTING;
        publish();
        Context app = activity.getApplicationContext();
        HeadphonesDiscovery.WORKER.execute(() -> {
            HeadphonesDiscovery.Inventory inventory = HeadphonesDiscovery.read(app);
            main.post(() -> {
                reading = false;
                if (!alive()) {
                    discoveryAttempted = false;
                    return;
                }
                candidates = inventory.candidates;
                nextDiscovery = SystemClock.elapsedRealtime() + REFRESH_MS;
                discoveryError = inventory.error;
                if (inventory.error != null) {
                    phase = inventory.error.startsWith("Bluetooth is off") ? Phase.BLUETOOTH_OFF : Phase.UNREACHABLE;
                } else {
                    Candidate automatic = inventory.automatic();
                    if (automatic != null && !manualChoosing) {
                        choose(automatic);
                        return;
                    }
                    phase = candidates.isEmpty() ? Phase.NOT_PAIRED : Phase.CHOOSE;
                }
                publish();
            });
        });
    }

    private void choose(Candidate c) {
        if (!alive() || reading || session != null) return;
        selected = c.device;
        selectedName = c.name;
        manualChoosing = false;
        auto = true;
        ready = false;
        busyRetries = 0;
        battery = null;
        noise = null;
        readAt = 0;
        start(null);
    }

    /** A read (selection null) or one write followed by a read. Writes need a confirmed model. */
    private void start(HeadphonesNoiseControl.Selection selection) {
        if (!alive() || selected == null || reading || session != null || (selection != null && !ready)) return;
        if (!permitted()) {
            ready = false;
            phase = Phase.PERMISSION;
            publish();
            return;
        }
        if (selection != null) busyRetries = 0;
        cancelled = false;
        writing = selection != null;
        pending = selection;
        ready = false;
        phase = writing ? Phase.APPLYING : Phase.READING;
        session = writing ? HeadphonesBluetooth.startNoiseChange(activity, selected, selection)
                : HeadphonesBluetooth.start(activity, selected);
        publish();
    }

    /** The user picked a mode or level: sent now, or right after the read in flight. */
    private void ask(HeadphonesNoiseControl.Selection selection) {
        if (selection == null || selected == null || pending != null || queued != null) return;
        if (session != null && !writing) {
            queued = selection;
            publish();
            return;
        }
        start(selection);
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            if (!pageAlive()) {
                stop();
                return;
            }
            if (!activity.hasWindowFocus()) {
                if (!suspended) {
                    suspended = true;
                    if (session != null) session.cancel();
                    session = null;
                    ready = false;
                    writing = false;
                    pending = null;
                    queued = null;
                    nextRead = 0;
                    publish();
                }
                main.postDelayed(this, 500);
                return;
            }
            if (suspended) {
                suspended = false;
                publish();
                if (selected == null && !reading) discover();
            }
            if (permitted() && selected == null && !reading && !manualChoosing
                    && (!discoveryAttempted || (auto && SystemClock.elapsedRealtime() >= nextDiscovery))) discover();
            if (session != null) {
                HeadphonesBluetooth.Result r = session.result();
                if (r.status != HeadphonesBluetooth.Status.READING) finished(r);
            } else if (auto && !reading && selected != null && SystemClock.elapsedRealtime() >= nextRead) {
                start(null);
            }
            // Fast while a session is open; a slow beat otherwise
            main.postDelayed(this, session != null || reading ? 150 : 1000);
        }
    };

    private void finished(HeadphonesBluetooth.Result r) {
        boolean wasWrite = writing;
        session = null;
        writing = false;
        pending = null;
        boolean observed = r.noise != null && (r.status == HeadphonesBluetooth.Status.OBSERVED
                || r.status == HeadphonesBluetooth.Status.PARTIAL);
        if (wasWrite) {
            // The write's own readback is the truth until the follow-up read
            if (r.noise != null) noise = r.noise;
            phase = r.status == HeadphonesBluetooth.Status.MODEL_UNCONFIRMED ? Phase.UNVERIFIED
                    : r.status == HeadphonesBluetooth.Status.PERMISSION_REQUIRED ? Phase.PERMISSION : Phase.READING;
        } else {
            if (r.battery != null) battery = r.battery;
            noise = r.noise;
            ready = observed;
            if (observed) {
                busyRetries = 0;
                readAt = SystemClock.elapsedRealtime();
                phase = Phase.LIVE;
            } else if (r.status == HeadphonesBluetooth.Status.BUSY && busyRetries++ < 2) {
                phase = Phase.READING;
            } else {
                auto = false;
                phase = phaseOf(r.status);
            }
        }
        nextRead = SystemClock.elapsedRealtime() + (wasWrite ? 750
                : r.status == HeadphonesBluetooth.Status.BUSY ? 1000 : REFRESH_MS);
        if (wasWrite && !cancelled) auto = true;
        HeadphonesNoiseControl.Selection next = queued;
        queued = null;
        publish();
        if (next != null && ready && !cancelled) start(next);
    }

    private static Phase phaseOf(HeadphonesBluetooth.Status s) {
        switch (s) {
            case MODEL_UNCONFIRMED: return Phase.UNVERIFIED;
            case PERMISSION_REQUIRED: return Phase.PERMISSION;
            case OBSERVED:
            case PARTIAL: return Phase.LIVE;
            default: return Phase.UNREACHABLE;
        }
    }

    // ---- The view ----------------------------------------------------------------------------

    private void publish() {
        if (stopped || activity.isFinishing() || activity.isDestroyed()) return;
        view.bind(state());
    }

    HeadphonesCenterModel.State state() {
        Phase shown = suspended ? Phase.PAUSED : phase;
        if (!suspended && !permitted()) shown = Build.VERSION.SDK_INT < 31 ? Phase.UNSUPPORTED : Phase.PERMISSION;
        HeadphonesNoiseControl.Selection inFlight = pending != null ? pending : queued;
        if (inFlight != null && !suspended) shown = Phase.APPLYING;
        // Between reads the last good values stay live; a read in progress doesn't blank them
        if (shown == Phase.READING && readAt > 0 && noise != null) shown = Phase.LIVE;
        Mode mode = noise == null ? null : HeadphonesCenterModel.modeOf(noise.mode.name());
        Level level = noise == null ? null : HeadphonesCenterModel.levelOf(noise.rawLevel);
        Cell left = battery == null ? null : new Cell(battery.left, Boolean.TRUE.equals(battery.leftCharging));
        Cell right = battery == null ? null : new Cell(battery.right, Boolean.TRUE.equals(battery.rightCharging));
        Cell box = battery == null ? null : new Cell(battery.caseLevel, Boolean.TRUE.equals(battery.caseCharging));
        return new HeadphonesCenterModel.State(shown, selectedName, left, right, box, mode, level,
                inFlight == null ? null : targetOf(inFlight), readAt);
    }

    /** Commands during a read wait for it, so the panel reports them as applying. */
    private static Object targetOf(HeadphonesNoiseControl.Selection s) {
        switch (s) {
            case AWARENESS: return Mode.AWARENESS;
            case OFF: return Mode.OFF;
            case ANC_AUTO: return Mode.ANC;
            case ADAPTIVE: return Level.ADAPTIVE;
            case LIGHT: return Level.LIGHT;
            case BALANCED: return Level.BALANCED;
            case DEEP: return Level.DEEP;
            default: return null;
        }
    }

    /** ANC from the main row is the earbud-selected level: no level is forced. */
    static HeadphonesNoiseControl.Selection selectionOf(Mode m) {
        switch (m) {
            case AWARENESS: return HeadphonesNoiseControl.Selection.AWARENESS;
            case OFF: return HeadphonesNoiseControl.Selection.OFF;
            case ANC: return HeadphonesNoiseControl.Selection.ANC_AUTO;
            default: return null;
        }
    }

    static HeadphonesNoiseControl.Selection selectionOf(Level l) {
        switch (l) {
            case ADAPTIVE: return HeadphonesNoiseControl.Selection.ADAPTIVE;
            case LIGHT: return HeadphonesNoiseControl.Selection.LIGHT;
            case BALANCED: return HeadphonesNoiseControl.Selection.BALANCED;
            case DEEP: return HeadphonesNoiseControl.Selection.DEEP;
            default: return null;
        }
    }

    // ---- Choosing among paired devices -------------------------------------------------------

    private void showChooser() {
        if (session != null || reading || !host.current(view)) return;
        List<StreamMenuView.Item> rows = new ArrayList<>();
        if (candidates.isEmpty()) {
            rows.add(StreamMenuView.Item.note(activity.getString(R.string.horizon_hp_phase_not_paired)));
        }
        for (Candidate c : candidates) {
            String suffix = "";
            try {
                String addr = c.device.getAddress();
                suffix = " · " + addr.substring(Math.max(0, addr.length() - 5));
            } catch (RuntimeException ignored) {
            }
            rows.add(StreamMenuView.Item.repeatable(c.name + suffix, () -> {
                host.pop();
                choose(c);
            }).withValue(activity.getString(c.available ? R.string.horizon_hp_device_audio
                    : R.string.horizon_hp_device_paired)));
        }
        rows.add(StreamMenuView.Item.repeatable(activity.getString(R.string.horizon_hp_device_search), () -> {
            host.pop();
            manualChoosing = false;
            selected = null;
            discover();
        }));
        host.push(activity.getString(R.string.horizon_hp_choose_title), rows);
    }
}
