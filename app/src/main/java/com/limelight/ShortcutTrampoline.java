package com.limelight;

import static com.limelight.utils.ServerHelper.getSecondaryDisplay;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ShortcutTrampoline extends AppCompatActivity {
    private PreferenceConfiguration prefConfig;
    private String uuidString;
    private NvApp app;
    private ArrayList<Intent> intentStack = new ArrayList<>();

    private int wakeHostTries = 10;
    private SpinnerDialog blockingLoadSpinner;

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean bound, polling;
    private volatile boolean stopped, discoveryHandled;
    private volatile long shortcutRequest;
    private final com.limelight.pocket.core.HorizonLaunchRequests requests =
            com.limelight.pocket.core.HorizonLaunchRequests.shared();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean updateQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private final Runnable deadline = () -> { if (!stopped && !isFinishing()) finish(); };

    private boolean ownsShortcut() {
        return !stopped && requests.current(shortcutRequest, android.os.SystemClock.elapsedRealtime());
    }

    private void dismissLoading() {
        if (blockingLoadSpinner != null) {
            blockingLoadSpinner.dismiss();
            blockingLoadSpinner = null;
        }
    }

    /** Only the UI thread owns binding/polling transitions. */
    private void releaseBinding() {
        if (polling && managerBinder != null) managerBinder.stopPolling();
        polling = false;
        managerBinder = null;
        if (bound) {
            bound = false;
            unbindService(serviceConnection);
        }
    }

    private void dispatchStack() {
        if (!ownsShortcut() || isFinishing() || isDestroyed()
                || !requests.claim(shortcutRequest, android.os.SystemClock.elapsedRealtime())) return;
        main.removeCallbacks(deadline);
        finish();
        startActivities(intentStack.toArray(new Intent[]{}));
    }

    private void submitWork(Runnable task) {
        try { worker.execute(task); }
        catch (java.util.concurrent.RejectedExecutionException ignored) { /* screen already left */ }
    }

    private static final String TAG = "ShortcutTrampoline";

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    (ComputerManagerService.ComputerManagerBinder) binder;
            if (!ownsShortcut()) return;
            managerBinder = localBinder;
            submitWork(() -> {
                localBinder.waitForReady();
                if (!ownsShortcut() || Thread.currentThread().isInterrupted()) return;
                ComputerDetails computer = localBinder.getComputer(uuidString);
                if (computer == null) {
                    main.post(() -> {
                        if (!ownsShortcut() || managerBinder != localBinder) return;
                        dismissLoading();
                        releaseBinding();
                        Dialog.displayDialog(ShortcutTrampoline.this,
                                getResources().getString(R.string.conn_error_title),
                                getResources().getString(R.string.scut_pc_not_found), true);
                    });
                    return;
                }
                localBinder.invalidateStateForComputer(computer.uuid);
                main.post(() -> {
                    if (!ownsShortcut() || managerBinder != localBinder || discoveryHandled) return;
                    polling = true;
                    localBinder.startPolling(details -> {
                        if (!ownsShortcut() || discoveryHandled || !details.uuid.equalsIgnoreCase(uuidString)
                                || !updateQueued.compareAndSet(false, true)) return;
                        // The initial update can arrive synchronously on UI. Never send WOL there.
                        submitWork(() -> {
                            try {
                                if (!ownsShortcut() || discoveryHandled) return;
                                if (details.state != ComputerDetails.State.ONLINE
                                        && details.macAddress != null && --wakeHostTries >= 0) {
                                    try {
                                        WakeOnLanSender.sendWolPacket(computer);
                                        if (ownsShortcut()) localBinder.invalidateStateForComputer(computer.uuid);
                                        return;
                                    } catch (IOException e) {
                                        Log.w(TAG, "Shortcut wake failed", e);
                                    }
                                }
                                if (details.state != ComputerDetails.State.UNKNOWN) {
                                    main.post(() -> handleComputerReady(localBinder, details));
                                }
                            } finally {
                                updateQueued.set(false);
                            }
                        });
                    });
                });
            });
        }

        @Override public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
            polling = false;
            if (ownsShortcut() && !discoveryHandled) finish();
        }
    };

    private void handleComputerReady(ComputerManagerService.ComputerManagerBinder localBinder,
                                     ComputerDetails details) {
        if (!ownsShortcut() || discoveryHandled || managerBinder != localBinder || isFinishing()) return;
        if (details.state == ComputerDetails.State.UNKNOWN) return;
        discoveryHandled = true;
        dismissLoading();
        try {
            if (details.state == ComputerDetails.State.ONLINE && details.pairState == PairingManager.PairState.PAIRED) {
                if (app != null) {
                    final Intent startIntent = ServerHelper.createStartIntent(this, app, details,
                            localBinder, prefConfig.useVirtualDisplay);
                    if (details.runningGameId == 0 || details.runningGameId == app.getAppId()
                            || Objects.equals(details.runningGameUUID, app.getAppUUID())) {
                        intentStack.add(startIntent);
                        dispatchStack();
                    } else {
                        UiHelper.displayQuitConfirmationDialog(this, () -> {
                            if (!ownsShortcut()) return;
                            intentStack.add(startIntent);
                            dispatchStack();
                        }, this::finish);
                    }
                } else {
                    Intent pc = new Intent(this, PcView.class);
                    pc.setAction(Intent.ACTION_MAIN);
                    pc.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intentStack.add(pc);
                    Intent list = new Intent(getIntent());
                    list.setClass(this, AppView.class);
                    list.removeExtra(com.limelight.pocket.pclib.PcLaunchTicket.EXTRA_TOKEN);
                    intentStack.add(list);
                    if (details.runningGameId != 0) {
                        intentStack.add(ServerHelper.createStartIntent(this,
                                new NvApp(null, null, details.runningGameId, false),
                                details, localBinder, prefConfig.useVirtualDisplay));
                    }
                    dispatchStack();
                }
            } else if (details.state == ComputerDetails.State.OFFLINE) {
                Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.error_pc_offline), true);
            } else if (details.pairState != PairingManager.PairState.PAIRED) {
                Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_not_paired), true);
            }
        } finally {
            releaseBinding();
        }
    }

    protected boolean validateHostInput(String hostUUID, String hostName) {
        // Validate PC UUID/Name
        if (hostUUID == null && hostName == null) {
            Dialog.displayDialog(ShortcutTrampoline.this,
                    getResources().getString(R.string.conn_error_title),
                    getResources().getString(R.string.scut_invalid_uuid),
                    true);
            return false;
        }

        if (hostUUID != null && !hostUUID.isEmpty()) {
            try {
                UUID.fromString(hostUUID);
            } catch (IllegalArgumentException ex) {
                Dialog.displayDialog(ShortcutTrampoline.this,
                        getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_invalid_uuid),
                        true);
                return false;
            }
        } else {
            // UUID is null, so fallback to Name
            if (hostName == null || hostName.isEmpty()) {
                Dialog.displayDialog(ShortcutTrampoline.this,
                        getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_invalid_uuid),
                        true);
                return false;
            }
        }

        return true;
    }


    protected boolean validateAppInput(String appUUID, String appIDStr, String appName) {
        if (appUUID == null && appIDStr == null && appName == null) {
            // We're just going to the AppView
            return false;
        }

        if (appUUID != null && !appUUID.isEmpty()) {
            try {
                UUID.fromString(appUUID);
            } catch (IllegalArgumentException ex) {
                Dialog.displayDialog(ShortcutTrampoline.this,
                        getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_invalid_app_id),
                        true);
                return false;
            }
        } else {
            // Validate App ID (if provided)
            if (appIDStr != null && !appIDStr.isEmpty()) {
                try {
                    Integer.parseInt(appIDStr);
                } catch (NumberFormatException ex) {
                    Dialog.displayDialog(ShortcutTrampoline.this,
                            getResources().getString(R.string.conn_error_title),
                            getResources().getString(R.string.scut_invalid_app_id),
                            true);
                    return false;
                }
            }
        }

        return true;
    }

    private Map<String, String> parseArtFileData(Uri fileUri) {
        if (fileUri == null) {
            return null;
        }

        Map<String, String> artData = new HashMap<>();

        try (InputStream inputStream = getContentResolver().openInputStream(fileUri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) {
                    continue; // Skip comments and empty lines
                }

                if (!line.startsWith("[")) {
                    throw new IOException("Invalid .art file format");
                }

                int separatorIndex = line.indexOf(' ');
                if (separatorIndex > 0 && separatorIndex < line.length() - 1) {
                    String key = line.substring(0, separatorIndex).trim();
                    String value = line.substring(separatorIndex + 1).trim();
                    if (key.endsWith("]")) {
                        key = key.substring(1, key.length() - 1);
                        artData.put(key, value);
                    } else {
                        throw new IOException("Invalid .art file format");
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading .art file", e);
            Dialog.displayDialog(ShortcutTrampoline.this,
                    getResources().getString(R.string.conn_error_title),
                    "Error reading .art file: " + e.getMessage(),
                    true);
        }
        return artData;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefConfig = PreferenceConfiguration.readPreferences(this);

        UiHelper.notifyNewRootView(this);
        ComputerDatabaseManager dbManager = new ComputerDatabaseManager(this);
        ComputerDetails _computer = null;

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri dataUri = intent.getData();

        String hostUUID = null;
        String hostName = null;
        String appName = null;
        String appUUID = null;
        String appIDStr = null;

        if (Intent.ACTION_VIEW.equals(action) && dataUri != null) {
            Map<String, String> artData = parseArtFileData(dataUri);

            if (artData != null) {
                hostUUID = artData.get(ShortcutHelper.KEY_HOST_UUID);
                hostName = artData.get(ShortcutHelper.KEY_HOST_NAME);
                appName = artData.get(ShortcutHelper.KEY_APP_NAME);
                appUUID = artData.get(ShortcutHelper.KEY_APP_UUID);
                appIDStr = artData.get(ShortcutHelper.KEY_APP_ID);
            }
        }

        {
            // PC arguments, both are optional, but at least one must be provided
            if (hostUUID == null) {
                hostUUID = getIntent().getStringExtra(AppView.UUID_EXTRA);
            }
            if (hostName == null) {
                hostName = getIntent().getStringExtra(AppView.NAME_EXTRA);
            }

            // App arguments, all optional, but one must be provided in order to start an app
            if (appUUID == null) {
                appUUID = getIntent().getStringExtra(Game.EXTRA_APP_UUID);
            }
            if (appIDStr == null) {
                appIDStr = getIntent().getStringExtra(Game.EXTRA_APP_ID);
            }
            if (appName == null) {
                appName = getIntent().getStringExtra(Game.EXTRA_APP_NAME);
            }
        }

        if (!validateHostInput(hostUUID, hostName)) {
            // Invalid input, so just return
//            finish();
            return;
        }

        if (hostUUID == null || hostUUID.isEmpty()) {
            // Use hostName to find the corresponding UUID
            _computer = dbManager.getComputerByName(hostName);

            if (_computer == null) {
                Dialog.displayDialog(ShortcutTrampoline.this,
                        getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_pc_not_found),
                        true);
//                    finish();
                return;
            }

            hostUUID = _computer.uuid;
        }

        uuidString = hostUUID;

        // Set the AppView UUID intent
        setIntent(new Intent(getIntent()).putExtra(AppView.UUID_EXTRA, uuidString));

        if (validateAppInput(appUUID, appIDStr, appName)) {
            // If app data came from .art file or was determined by appNameString from extras
            if (appUUID != null && !appUUID.isEmpty()) {
                app = new NvApp(appName, // appName can be null if only UUID is provided
                        appUUID,
                        -1, // App ID is not strictly needed if UUID is present
                        getIntent().getBooleanExtra(Game.EXTRA_APP_HDR, false)); // HDR info still from intent
            } else if (appIDStr != null && !appIDStr.isEmpty()) {
                int appID = Integer.parseInt(appIDStr);
                app = new NvApp(appName, // appName can be null if only App ID is provided
                        null,
                        appID,
                        getIntent().getBooleanExtra(Game.EXTRA_APP_HDR, false)); // HDR info still from intent
            } else if (appName != null && !appName.isEmpty()) {
                // Use appNameString (from .art file or intent extra) to find the corresponding AppId and AppUUID
                try {
                    int appID = -1;
                    String appUuidFromFile = null;
                    String rawAppList = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));

                    if (rawAppList.isEmpty()) {
                        Dialog.displayDialog(ShortcutTrampoline.this,
                                getResources().getString(R.string.conn_error_title),
                                getResources().getString(R.string.scut_invalid_app_id) + " (applist cache empty or unreadable)",
                                true);
//                    finish();
                        return;
                    }
                    List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(rawAppList));

                    for (NvApp _app : applist) {
                        if (_app.getAppName().equalsIgnoreCase(appName)) {
                            appID = _app.getAppId();
                            appUuidFromFile = _app.getAppUUID();
                            break;
                        }
                    }
                    if (appID < 0 && appUuidFromFile == null) { // Need at least one
                        Dialog.displayDialog(ShortcutTrampoline.this,
                                getResources().getString(R.string.conn_error_title),
                                getResources().getString(R.string.scut_invalid_app_id) + " (app not found in cache)",
                                true);
//                    finish();
                        return;
                    }
                    // Update intent with found app ID and UUID if they weren't originally there
                    Intent currentIntent = getIntent();
                    if (currentIntent.getStringExtra(Game.EXTRA_APP_ID) == null && appID != -1) {
                        currentIntent.putExtra(Game.EXTRA_APP_ID, String.valueOf(appID));
                    }
                    if (currentIntent.getStringExtra(Game.EXTRA_APP_UUID) == null && appUuidFromFile != null) {
                        currentIntent.putExtra(Game.EXTRA_APP_UUID, appUuidFromFile);
                    }
                    app = new NvApp(
                            appName,
                            appUuidFromFile,
                            appID,
                            getIntent().getBooleanExtra(Game.EXTRA_APP_HDR, false));
                } catch (IOException | XmlPullParserException e) {
                    Log.e(TAG, "Error processing app list from cache", e);
                    Dialog.displayDialog(ShortcutTrampoline.this,
                            getResources().getString(R.string.conn_error_title),
                            getResources().getString(R.string.scut_invalid_app_id) + " (error parsing applist cache)",
                            true);
//                finish();
                    return;
                }
            }
        }

        shortcutRequest = requests.beginPc(
                com.limelight.pocket.core.HorizonLaunchTarget.pcShortcut(uuidString,
                        app == null ? null : app.getAppUUID(), app == null ? 0 : app.getAppId(),
                        app == null ? null : app.getAppName()), android.os.SystemClock.elapsedRealtime());
        if (shortcutRequest == 0) { finish(); return; }

        // Bind once; cleanup remains valid even if readiness never arrives.
        bound = bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
        if (!bound) { requests.cancel(shortcutRequest); finish(); return; }
        main.postDelayed(deadline, 120000L);
        blockingLoadSpinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.conn_establishing_title),
                getResources().getString(R.string.applist_connect_msg), true);
    }

    @Override protected void onPause() {
        stopped = true;
        requests.cancel(shortcutRequest);
        main.removeCallbacks(deadline);
        worker.shutdownNow();
        releaseBinding();
        super.onPause();
    }

    @Override protected void onStop() {
        super.onStop();
        dismissLoading();
        Dialog.closeDialogs();
        finish();
    }

    @Override protected void onDestroy() {
        stopped = true;
        requests.cancel(shortcutRequest);
        main.removeCallbacks(deadline);
        worker.shutdownNow();
        releaseBinding();
        super.onDestroy();
    }
}
