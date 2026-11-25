package com.limelight.pocket.streaming;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HorizonDecodeTimestampsTest {
    @Test public void consumesTimingRecordsByPresentationTimeInOutputOrder() {
        HorizonDecodeTimestamps clock = new HorizonDecodeTimestamps();
        clock.record(101, 10_001);
        clock.record(105, 10_002);
        clock.record(103, 10_003);
        assertEquals(10_003, clock.take(103));
        assertEquals(10_001, clock.take(101));
        assertEquals(HorizonDecodeTimestamps.MISSING, clock.take(103));
        assertEquals(10_002, clock.take(105));
    }

    @Test public void repeatedFramesWithoutOutputKeepOnlyABoundedWindow() {
        HorizonDecodeTimestamps clock = new HorizonDecodeTimestamps();
        for (int frame = 0; frame < 1200; frame++) clock.record(frame, frame + 2000);

        assertEquals(HorizonDecodeTimestamps.MISSING, clock.take(0));
        assertEquals(HorizonDecodeTimestamps.MISSING, clock.take(943));
        assertEquals(2944, clock.take(944));
        assertEquals(3199, clock.take(1199));
    }

    @Test public void clearingARecoveredDecoderPreventsOldTimingFromBeingReused() {
        HorizonDecodeTimestamps clock = new HorizonDecodeTimestamps();
        clock.record(90, 5_000);
        clock.clear();
        assertEquals(HorizonDecodeTimestamps.MISSING, clock.take(90));
        clock.record(90, 23_000);
        assertEquals(23_000, clock.take(90));
    }
}
