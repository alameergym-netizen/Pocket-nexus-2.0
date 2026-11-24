package com.limelight.binding.audio;

/** One PCM callback thread records delivery events. Diagnostic readers take an on-demand sample. */
public final class HorizonAudioStats {
    private static final int MAX_PENDING_MS = 40;
    private volatile int highestQueueMs = -1;
    private volatile long droppedBlocks;
    private volatile long partialWrites;
    private volatile long writeErrors;

    public boolean allowWrite(int pendingMs) {
        if (pendingMs > highestQueueMs) highestQueueMs = pendingMs;
        if (pendingMs < MAX_PENDING_MS) return true;
        droppedBlocks++;
        return false;
    }

    public boolean completeWrite(int expected, int written) {
        if (written == expected) return true;
        if (written < 0 || written > expected) writeErrors++;
        else partialWrites++;
        return false;
    }

    public Snapshot snapshot() {
        return new Snapshot(highestQueueMs, droppedBlocks, partialWrites, writeErrors);
    }

    public static final class Snapshot {
        public final int highestQueueMs;
        public final long droppedBlocks, partialWrites, writeErrors;

        private Snapshot(int highestQueueMs, long droppedBlocks, long partialWrites, long writeErrors) {
            this.highestQueueMs = highestQueueMs;
            this.droppedBlocks = droppedBlocks;
            this.partialWrites = partialWrites;
            this.writeErrors = writeErrors;
        }
    }
}
