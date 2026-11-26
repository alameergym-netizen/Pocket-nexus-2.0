package com.limelight.pocket.apps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Package changed" broadcasts arrive often (system apps switching a service or receiver on or
 * off, especially just after boot). Each one used to rebuild Home and every Quick Access button,
 * which could swallow a tap landing at that moment; only changes to what a launcher shows count.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class AppWatcherTest {
    @Before public void registerForTheFreshRobolectricApplication() throws Exception {
        // Earlier full-Activity tests can start this process-wide watcher. Robolectric
        // replaces the Application/receiver registry between tests, while application
        // class statics can survive in the SDK sandbox. Match that fresh lifetime;
        // all assertions below still use real broadcasts and the production receiver.
        java.lang.reflect.Field started = AppWatcher.class.getDeclaredField("started");
        started.setAccessible(true);
        started.setBoolean(null, false);
    }

    @Test public void onlyLauncherChangesCountAsSomethingShownChanging() {
        assertFalse(AppWatcher.changesWhatIsShown(null, null));
        assertFalse(AppWatcher.changesWhatIsShown("a.Main#1", "a.Main#1"));
        assertTrue(AppWatcher.changesWhatIsShown(null, "a.Main#1"));
        assertTrue(AppWatcher.changesWhatIsShown("a.Main#1", null));
        assertTrue(AppWatcher.changesWhatIsShown("a.Main#1", "a.Alias#2"));
    }

    @Test public void internalChangesDoNotRebuildButLauncherChangesDo() {
        Application app = RuntimeEnvironment.getApplication();
        ShadowPackageManager pm = shadowOf(app.getPackageManager());
        PackageInfo game = new PackageInfo();
        game.packageName = "test.game";
        game.applicationInfo = new ApplicationInfo();
        game.applicationInfo.packageName = "test.game";
        pm.installPackage(game);
        launcher(pm, new ComponentName("test.game", "test.game.Main"));

        AtomicInteger rebuilt = new AtomicInteger();
        AppWatcher.Listener listener = rebuilt::incrementAndGet;
        AppWatcher.start(app);
        AppWatcher.listen(listener);
        try {
            changed(app, "test.game");
            assertEquals("a part inside the app changed: nothing on screen did", 0, rebuilt.get());
            changed(app, "test.unknown");
            assertEquals("an app with no launcher entry changed: nothing on screen did", 0, rebuilt.get());

            launcher(pm, new ComponentName("test.game", "test.game.Alias"));
            changed(app, "test.game");
            assertEquals("a new launcher entry is shown, so screens rebuild once", 1, rebuilt.get());
            changed(app, "test.game");
            assertEquals("the same entries again rebuild nothing", 1, rebuilt.get());
        } finally {
            AppWatcher.stop(listener);
        }
    }

    private static void launcher(ShadowPackageManager pm, ComponentName activity) {
        pm.addActivityIfNotPresent(activity);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);
        pm.addIntentFilterForActivity(activity, filter);
    }

    private static void changed(Application app, String pkg) {
        app.sendBroadcast(new Intent(Intent.ACTION_PACKAGE_CHANGED, Uri.parse("package:" + pkg)));
        shadowOf(Looper.getMainLooper()).idle();
    }
}
