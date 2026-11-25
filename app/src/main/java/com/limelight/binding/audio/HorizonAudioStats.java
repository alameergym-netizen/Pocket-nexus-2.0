package com.limelight.binding.audio;

/** One PCM callback thread records delivery events. Diagnostic readers take an on-demand sample. */
public final class HorizonAudioStats {
    private static final int MAX_PENDING_MS = 40;
    private volatile int highestQueueMs = -1;
    private volatile long droppedBlocks;
    private volatile long partialWrites;
    private volatile long writeErrors;
    private volatile long submittedSamples;

    public boolean allowWrite(int pendingMs) {
        if (pendingMs > highestQueueMs) highestQueueMs = pendingMs;
        if (pendingMs < MAX_PENDING_MS) return true;
        droppedBlocks++;
        return false;
    }

    public boolean completeWrite(int expected, int written) {
        if (written > 0 && written <= expected) submittedSamples += written;
        if (written == expected) return true;
        if (written < 0 || written > expected) writeErrors++;
        else partialWrites++;
        return false;
    }

    public Snapshot snapshot() {
        return new Snapshot(highestQueueMs, droppedBlocks, partialWrites, writeErrors, submittedSamples);
    }

    public static final class Snapshot {
        public final int highestQueueMs;
        public final long droppedBlocks, partialWrites, writeErrors;
        private final long submittedSamples;

        private Snapshot(int highestQueueMs, long droppedBlocks, long partialWrites, long writeErrors, long submittedSamples) {
            this.highestQueueMs = highestQueueMs;
            this.droppedBlocks = droppedBlocks;
            this.partialWrites = partialWrites;
            this.writeErrors = writeErrors;
            this.submittedSamples = submittedSamples;
        }

        /** Estimates backlog from AudioTrack's monotonic position and samples actually written. */
        public double queuedAudioMs(int channels, int sampleRate, long presentedFrames, long timestampNs, long nowNs) {
            if (channels <= 0 || sampleRate <= 0 || submittedSamples < channels || presentedFrames < 0
                    || timestampNs <= 0) return -1;
            long ageNs = nowNs - timestampNs;
            long submittedFrames = submittedSamples / channels;
            if (ageNs < 0 || ageNs > 1_000_000_000L || presentedFrames > submittedFrames) return -1;
            double queuedMs = (submittedFrames - presentedFrames) * 1000.0 / sampleRate - ageNs / 1_000_000.0;
            // Discard inconsistent counters across track or transport resets.
            return queuedMs > 2000 ? -1 : Math.max(0, queuedMs);
        }
    }
}
