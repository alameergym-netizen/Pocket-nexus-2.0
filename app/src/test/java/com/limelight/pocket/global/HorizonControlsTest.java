package com.limelight.pocket.global;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonControlsTest {
    @Test public void allLayoutsPreserveEveryTile() {
        for (int i = 0; i < 3; i++) {
            boolean[] seen = new boolean[6];
            for (int id : HorizonControls.order(i)) {
                assertTrue(id >= 0 && id < 6);
                assertFalse(seen[id]);
                seen[id] = true;
            }
            for (boolean value : seen) { assertTrue(value); }
        }
    }
    @Test public void invalidLayoutFallsBackAndArraysAreCopies() {
        int[] order = HorizonControls.order(-1);
        order[0] = 5;
        assertEquals(0, HorizonControls.order(0)[0]);
        assertEquals(0, HorizonControls.layoutIndex(99));
    }
    @Test public void focusTintRemainsOpaqueAndLight() {
        assertEquals(0xFFCCCCCC, HorizonControls.focusColor(0xFF000000));
        assertEquals(0xFFFFFFFF, HorizonControls.focusColor(0xFFFFFFFF));
        assertEquals(0xFFFFCCCC, HorizonControls.focusColor(0x00FF0000));
    }
    @Test public void levelsAreBounded() {
        assertEquals(0f, HorizonControls.clampLevel(-0.1f), 0f);
        assertEquals(0.5f, HorizonControls.clampLevel(0.5f), 0f);
        assertEquals(1f, HorizonControls.clampLevel(1.2f), 0f);
        assertEquals(0f, HorizonControls.clampLevel(Float.NaN), 0f);
    }
    @Test public void holdDurationIsBounded() {
        assertEquals(350, HorizonControls.holdMs(1));
        assertEquals(480, HorizonControls.holdMs(480));
        assertEquals(1000, HorizonControls.holdMs(99999));
    }
}
