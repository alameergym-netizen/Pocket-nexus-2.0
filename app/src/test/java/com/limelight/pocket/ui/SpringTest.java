package com.limelight.pocket.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpringTest {
    @Test public void scrolledCoordinatesSettleAtSixtyAndOneTwentyHertz() {
        for (int fps : new int[] {60, 120}) {
            for (float target : new float[] {1, 100, 1000, 3000, 8000, 20000, -3000, -20000}) {
                Spring spring = new Spring(560f, 0.84f, 0f);
                spring.target = target;
                settle(spring, fps);
                assertEquals(target, spring.value, 0f);
                assertEquals(0f, spring.velocity, 0f);
                assertFalse(spring.step(1f / fps));
            }
        }
    }

    @Test public void nearbyRowsAndMidFlightRetargetKeepTheirExactDestination() {
        for (int fps : new int[] {60, 120}) {
            Spring spring = new Spring(560f, 0.84f, 20000f);
            spring.target = 20350f;
            for (int i = 0; i < 8; i++) spring.step(1f / fps);
            assertTrue(spring.value > 20000f);
            assertTrue(spring.value < 20350f);
            spring.target = 20000.25f;
            settle(spring, fps);
            assertEquals(20000.25f, spring.value, 0f);
            spring.target = 0f;
            settle(spring, fps);
            assertEquals(0f, spring.value, 0f);
        }
    }

    @Test public void normalizedMotionKeepsTheExistingSmallCoordinateThreshold() {
        Spring spring = new Spring(560f, 0.84f, 0f);
        spring.target = 1f;
        assertTrue(spring.step(1f / 60));
        assertTrue(spring.value > 0f && spring.value < 0.5f);
        settle(spring, 60);
        assertEquals(1f, spring.value, 0f);
    }

    private static void settle(Spring spring, int fps) {
        for (int i = 0; i < fps * 3 && spring.step(1f / fps); i++) { }
        assertTrue("Spring must stop scheduling redraws at " + spring.target + " / " + fps + "Hz", spring.isAtRest());
    }
}
