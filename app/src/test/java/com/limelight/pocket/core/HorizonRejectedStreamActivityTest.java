package com.limelight.pocket.core;

import android.app.Application;
import android.content.Intent;
import android.os.SystemClock;

import com.limelight.Game;
import com.limelight.pocket.apps.HomeScreen;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class HorizonRejectedStreamActivityTest {
    @Test public void rejectedQueuedActivityKeepsTheCurrentOwnerAndCleansUpWithoutInitializingAStream() {
        Game previous = Game.instance;
        boolean wasStreaming = HomeScreen.streaming;
        Game current = Robolectric.buildActivity(Game.class).get();
        HorizonStreamObserver observer = HorizonStreamObserver.instance();
        long ticket = observer.beginAttempt(HorizonLaunchTarget.pc("current-owner", 2));
        observer.connected(ticket);
        Game.instance = current;
        HomeScreen.streaming = true;
        try {
            Intent queued = new Intent();
            HorizonStreamSelection.stamp(queued);
            HorizonLaunchRequests.shared().beginPc(HorizonLaunchTarget.pc("new-selection", 3), SystemClock.elapsedRealtime());
            ActivityController<Game> rejected = Robolectric.buildActivity(Game.class, queued).create();
            assertTrue(rejected.get().isFinishing());
            rejected.start().resume();
            rejected.get().onWindowFocusChanged(true);
            rejected.pause().stop().destroy();
            assertSame(current, Game.instance);
            assertTrue(HomeScreen.streaming);
            assertTrue(observer.isRunning(ticket));
        } finally {
            Game.instance = previous;
            HomeScreen.streaming = wasStreaming;
            observer.ended(ticket);
        }
    }
}
