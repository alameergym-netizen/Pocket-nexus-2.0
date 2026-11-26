package com.limelight.pocket.device;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.limelight.R;
import com.limelight.pocket.PocketMic;
import com.limelight.pocket.global.GlobalButtons;
import com.limelight.pocket.retro.RetroLibrary;

/** Read current grants; open Android's own settings only on an explicit user action. */
public final class HorizonPermissions {
    private HorizonPermissions() {}

    public enum Capability {
        GLOBAL_BUTTONS(R.string.horizon_perm_buttons, R.string.horizon_perm_buttons_desc),
        BRIGHTNESS(R.string.horizon_perm_brightness, R.string.horizon_perm_brightness_desc),
        ROM_FILES(R.string.horizon_perm_files, R.string.horizon_perm_files_desc),
        NOTIFICATIONS(R.string.horizon_perm_notifications, R.string.horizon_perm_notifications_desc),
        QUIET_MODE(R.string.horizon_perm_quiet, R.string.horizon_perm_quiet_desc),
        MICROPHONE(R.string.horizon_perm_mic, R.string.horizon_perm_mic_desc),
        BLUETOOTH_DETAILS(R.string.horizon_perm_bluetooth, R.string.horizon_perm_bluetooth_desc);

        public final int title, description;
        Capability(int title, int description) {
            this.title = title;
            this.description = description;
        }
    }

    public static int status(Context context, Capability capability) {
        try {
            boolean granted;
            switch (capability) {
                case GLOBAL_BUTTONS:
                    granted = GlobalButtons.serviceOn(context);
                    break;
                case BRIGHTNESS:
                    granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context);
                    break;
                case ROM_FILES:
                    granted = RetroLibrary.hasAccess(context);
                    break;
                case NOTIFICATIONS:
                    granted = PocketNotifications.allowed(context);
                    break;
                case QUIET_MODE:
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return R.string.horizon_perm_unavailable;
                    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm == null) return R.string.horizon_perm_unavailable;
                    granted = nm.isNotificationPolicyAccessGranted();
                    break;
                case MICROPHONE:
                    granted = PocketMic.hasPermission(context);
                    break;
                case BLUETOOTH_DETAILS:
                    String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ? Manifest.permission.BLUETOOTH_CONNECT : Manifest.permission.BLUETOOTH;
                    granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
                    break;
                default:
                    return R.string.horizon_perm_unavailable;
            }
            return granted ? R.string.horizon_perm_granted : R.string.horizon_perm_missing;
        } catch (RuntimeException e) {
            return R.string.horizon_perm_unavailable;
        }
    }

    static Intent settingsIntent(Context context, Capability capability) {
        Uri ownPackage = Uri.parse("package:" + context.getPackageName());
        switch (capability) {
            case GLOBAL_BUTTONS: return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            case BRIGHTNESS:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, ownPackage) : null;
            case QUIET_MODE:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) : null;
            case MICROPHONE:
            case BLUETOOTH_DETAILS:
                return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ownPackage);
            default: return null; // existing storage and notification helpers own their entry points
        }
    }

    public static void open(Activity activity, Capability capability) {
        try {
            switch (capability) {
                case ROM_FILES:
                    // Reuse the current ROM access flow. Do not scan, change roots, or grant access ourselves.
                    if (RetroLibrary.hasAccess(activity)) {
                        Intent i = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                                ? new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:" + activity.getPackageName()))
                                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(i);
                    } else {
                        RetroLibrary.requestAccess(activity);
                    }
                    return;
                case NOTIFICATIONS:
                    PocketNotifications.askForAccess(activity);
                    return;
                default:
                    Intent intent = settingsIntent(activity, capability);
                    if (intent != null) {
                        activity.startActivity(intent);
                        return;
                    }
            }
        } catch (RuntimeException ignored) { }
        Toast.makeText(activity, R.string.horizon_perm_unavailable, Toast.LENGTH_SHORT).show();
    }
}
