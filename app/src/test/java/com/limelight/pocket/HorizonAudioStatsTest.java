package com.limelight.pocket;

import com.limelight.binding.audio.HorizonAudioStats;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonAudioStatsTest {
    @Test public void skipsOnlyBlocksAtOrAboveTheExistingQueueLimit() {
        HorizonAudioStats stats = new HorizonAudioStats();
        assertEquals(-1, stats.snapshot().highestQueueMs);
        assertTrue(stats.allowWrite(-1));
        assertTrue(stats.allowWrite(39));
        assertFalse(stats.allowWrite(40));
        assertFalse(stats.allowWrite(75));
        assertTrue(stats.allowWrite(0));
        HorizonAudioStats.Snapshot snapshot = stats.snapshot();
        assertEquals(75, snapshot.highestQueueMs);
        assertEquals(2, snapshot.droppedBlocks);
    }

    @Test public void fullPartialAndFailedWritesAreDistinguished() {
        HorizonAudioStats stats = new HorizonAudioStats();
        assertTrue(stats.completeWrite(960, 960));
        assertFalse(stats.completeWrite(960, 0));
        assertFalse(stats.completeWrite(960, 480));
        assertFalse(stats.completeWrite(960, -6));
        HorizonAudioStats.Snapshot snapshot = stats.snapshot();
        assertEquals(0, snapshot.droppedBlocks);
        assertEquals(2, snapshot.partialWrites);
        assertEquals(1, snapshot.writeErrors);
        assertEquals(0, new HorizonAudioStats().snapshot().writeErrors);
    }

    @Test public void playbackBacklogCountsOnlyAcceptedCompleteAndPartialPcmWrites() {
        HorizonAudioStats stats = new HorizonAudioStats();
        stats.completeWrite(960, 960);
        stats.completeWrite(960, 480);
        stats.completeWrite(960, -6);
        stats.completeWrite(960, 1000);
        HorizonAudioStats.Snapshot snapshot = stats.snapshot();
        long now = 8_000_000_000L;
        // 720 submitted stereo frames, minus 240 presented, minus 2 ms since the timestamp.
        assertEquals(8, snapshot.queuedAudioMs(2, 48000, 240, now - 2_000_000, now), 0.001);
        assertEquals(0, snapshot.queuedAudioMs(2, 48000, 240, now - 20_000_000, now), 0.001);
    }

    @Test public void staleFutureAndInconsistentPlaybackTimestampsAreUnavailable() {
        HorizonAudioStats stats = new HorizonAudioStats();
        long now = 8_000_000_000L;
        assertEquals(-1, stats.snapshot().queuedAudioMs(2, 48000, 0, now, now), 0.001);
        stats.completeWrite(960, 960);
        HorizonAudioStats.Snapshot snapshot = stats.snapshot();
        assertEquals(-1, snapshot.queuedAudioMs(2, 48000, 481, now, now), 0.001);
        assertEquals(-1, snapshot.queuedAudioMs(2, 48000, 240, now + 1, now), 0.001);
        assertEquals(-1, snapshot.queuedAudioMs(2, 48000, 240, now - 1_000_000_001L, now), 0.001);
        assertEquals(-1, snapshot.queuedAudioMs(0, 48000, 240, now, now), 0.001);
    }
}
