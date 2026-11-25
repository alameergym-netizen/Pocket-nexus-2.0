package com.limelight.pocket.device;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HorizonDisplaySceneTest {
    @Test public void saverCapsAnExplicitBrightnessButDoesNotIncreaseIt() {
        assertEquals(45, HorizonDisplayScene.brightness(80, 45, true, false));
        assertEquals(30, HorizonDisplayScene.brightness(30, 45, true, false));
        assertEquals(0, HorizonDisplayScene.brightness(0, 45, true, false));
    }

    @Test public void manualInputAndUnknownPowerUseTheCurrentPreference() {
        assertEquals(80, HorizonDisplayScene.brightness(80, 45, true, true));
        assertEquals(80, HorizonDisplayScene.brightness(80, 45, false, false));
        assertEquals(80, HorizonDisplayScene.brightness(80, 45, null, false));
    }

    @Test public void disabledAndUnknownStoredOptionsHaveNoEffect() {
        assertEquals(80, HorizonDisplayScene.brightness(80, 0, true, false));
        assertEquals(80, HorizonDisplayScene.brightness(80, 79, true, false));
        assertEquals(0, HorizonDisplayScene.limitIndex(-1));
    }
}
