package com.limelight.pocket.streaming;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.cloud.HorizonCloudDispatch;
import com.limelight.pocket.core.HorizonLaunchRequests;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.List;

/** Official external routes for playing games from a user's own console. */
public enum HorizonRemotePlay {
    XBOX("xbox-remote", R.string.horizon_remote_xbox, null, "https://www.xbox.com/remoteplay"),
    PLAYSTATION("playstation-remote", R.string.horizon_remote_playstation, "com.playstation.remoteplay",
            "https://play.google.com/store/apps/details?id=com.playstation.remoteplay");

    private final String id;
    private final int title;
    private final String packageName;
    private final Uri officialPage;

    HorizonRemotePlay(String id, int title, String packageName, String officialPage) {
        this.id = id;
        this.title = title;
        this.packageName = packageName;
        this.officialPage = Uri.parse(officialPage);
    }

    Intent intent(Context context) {
        if (packageName != null) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch != null) return launch;
        }
        return new Intent(Intent.ACTION_VIEW, officialPage).addCategory(Intent.CATEGORY_BROWSABLE);
    }

    public static void show(ConsoleActivity activity) {
        List<StreamMenuView.Item> items = new ArrayList<>();
        for (HorizonRemotePlay provider : values()) {
            int mode = provider.packageName == null ? R.string.horizon_remote_browser
                    : provider.intent(activity).getData() == null ? R.string.horizon_remote_app
                    : R.string.horizon_remote_get_app;
            items.add(StreamMenuView.Item.action(activity.getString(provider.title),
                    () -> provider.open(activity)).withValue(activity.getString(mode)));
        }
        activity.showConsolePanel(activity.getString(R.string.horizon_remote_title),
                activity.getString(R.string.horizon_remote_subtitle), null, items);
    }

    private void open(ConsoleActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Intent target = intent(activity);
        if (!HorizonCloudDispatch.claim(HorizonLaunchRequests.shared(), id,
                target.getDataString(), SystemClock.elapsedRealtime())) return;
        try {
            activity.startActivity(target);
            activity.hideConsolePanel();
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.horizon_remote_unavailable, Toast.LENGTH_LONG).show();
        }
    }
}
