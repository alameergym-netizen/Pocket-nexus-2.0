package com.limelight.pocket.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.os.PowerManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HorizonPowerObservationsTest {
    @Test public void thermalSafetyStillReceivesCriticalStatusWhenBatterySaverReadFails() {
        PowerManager manager = mock(PowerManager.class);
        when(manager.isPowerSaveMode()).thenThrow(new IllegalStateException());
        when(manager.getCurrentThermalStatus()).thenReturn(PowerManager.THERMAL_STATUS_CRITICAL);
        HorizonPower.Observations observed = HorizonPower.read(manager);
        assertNull(observed.batterySaver);
        assertEquals(PowerManager.THERMAL_STATUS_CRITICAL, observed.thermal);
    }

    @Test public void batterySaverSurvivesAnUnavailableThermalReading() {
        PowerManager manager = mock(PowerManager.class);
        when(manager.isPowerSaveMode()).thenReturn(true);
        when(manager.getCurrentThermalStatus()).thenThrow(new IllegalStateException());
        HorizonPower.Observations observed = HorizonPower.read(manager);
        assertEquals(Boolean.TRUE, observed.batterySaver);
        assertEquals(-1, observed.thermal);
    }
}
