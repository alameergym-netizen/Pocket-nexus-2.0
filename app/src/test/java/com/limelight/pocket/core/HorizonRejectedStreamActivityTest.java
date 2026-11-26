package com.limelight.pocket.core;

import android.app.Application;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

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
            // Android can deliver queued input while a finished Activity is being removed.
            assertTrue(rejected.get().handleKeyDown(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)));
            assertTrue(rejected.get().handleKeyUp(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A)));
            assertTrue(rejected.get().handleKeyMultiple(new KeyEvent(0, "text", 0, 0)));
            MotionEvent motion = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 0, 0, 0);
            try { assertTrue(rejected.get().handleMotionEvent(null, motion)); }
            finally { motion.recycle(); }
            rejected.get().onBackPressed();
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
