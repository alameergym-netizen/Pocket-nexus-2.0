package com.limelight.pocket.core;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class HorizonStreamSelectionTest {
    private static void select(String host) {
        HorizonLaunchRequests.shared().beginPc(HorizonLaunchTarget.pc(host, 1), android.os.SystemClock.elapsedRealtime());
    }

    @Test public void delayedActivityAndSurfaceRetainTheOriginalSelection() {
        select("first-host");
        Intent pending = new Intent();
        HorizonStreamSelection.stamp(pending);
        long captured = HorizonStreamSelection.capture(pending);
        assertTrue(HorizonStreamSelection.current(captured));
        select("second-host");
        assertFalse(HorizonStreamSelection.current(captured));
        assertFalse(HorizonStreamSelection.current(HorizonStreamSelection.capture(pending)));
        HorizonStreamSelection.stamp(pending);
        assertTrue(HorizonStreamSelection.current(HorizonStreamSelection.capture(pending)));
    }

    @Test public void intentParcelKeepsItsOwnerAndResumeRefreshesOnlyTransientFields() {
        Intent pending = new Intent().putExtra("host", "owner-pc").putExtra("app", 17);
        HorizonStreamSelection.stamp(pending);
        Parcel parcel = Parcel.obtain();
        Intent copy;
        try {
            pending.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            copy = Intent.CREATOR.createFromParcel(parcel);
        } finally { parcel.recycle(); }
        assertEquals(HorizonStreamSelection.capture(pending), HorizonStreamSelection.capture(copy));
        Bundle saved = copy.getExtras();
        HorizonStreamSelection.clear(saved);
        assertEquals(2, saved.size());
        assertEquals("owner-pc", saved.getString("host"));
        assertEquals(17, saved.getInt("app"));
        select("resumed-host");
        Intent resumed = new Intent().putExtras(saved);
        HorizonStreamSelection.stamp(resumed);
        assertTrue(HorizonStreamSelection.current(HorizonStreamSelection.capture(resumed)));
        assertFalse(HorizonStreamSelection.current(HorizonStreamSelection.capture(copy)));
    }

    @Test public void legacyDirectStartStillExpiresWhenAnotherSelectionWins() {
        long legacy = HorizonStreamSelection.capture(new Intent());
        assertTrue(HorizonStreamSelection.current(legacy));
        select("legacy-successor");
        assertFalse(HorizonStreamSelection.current(legacy));
    }

    @Test public void incompleteOrForeignProcessOwnershipIsRejected() {
        Intent pending = new Intent();
        HorizonStreamSelection.stamp(pending);
        Bundle extras = pending.getExtras();
        for (String key : extras.keySet()) {
            Intent incomplete = new Intent(pending);
            incomplete.removeExtra(key);
            assertFalse(HorizonStreamSelection.current(HorizonStreamSelection.capture(incomplete)));
        }
        assertFalse(HorizonStreamSelection.current(-1));
    }
}
