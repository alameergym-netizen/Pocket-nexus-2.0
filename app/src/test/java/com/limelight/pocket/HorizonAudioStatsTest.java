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
}
