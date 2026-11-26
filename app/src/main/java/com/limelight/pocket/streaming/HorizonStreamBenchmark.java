package com.limelight.pocket.streaming;

/** On-demand client timing capture with fixed storage and a single owning decoder. */
public final class HorizonStreamBenchmark {
    private final Histogram decode = new Histogram();
    private final Histogram output = new Histogram();
    private static final int MAX_SEQUENCE_ADVANCE = 16_384;
    private static final int MAX_REORDERED_FRAMES = 240;
    private final double longOutputGapNs;
    private long firstOutputNs, lastOutputNs;
    private int longOutputGaps;
    private int lastFrameNumber, receivedFrames, sequenceBreaks;
    private long missingFrames;
    private boolean finished;

    public HorizonStreamBenchmark(float requestedFps) {
        longOutputGapNs = requestedFps > 0 && !Float.isInfinite(requestedFps) ? 2_000_000_000.0 / requestedFps : 0;
    }

    public synchronized void recordDecode(long nanoseconds) {
        if (!finished) decode.record(nanoseconds);
    }

    public synchronized void recordOutput(long nanoseconds) {
        if (finished || nanoseconds <= lastOutputNs) return;
        if (lastOutputNs == 0) {
            firstOutputNs = nanoseconds;
        } else {
            long gap = nanoseconds - lastOutputNs;
            output.record(gap);
            if (longOutputGapNs > 0 && gap > longOutputGapNs) longOutputGaps++;
        }
        lastOutputNs = nanoseconds;
    }

    public synchronized void recordIncomingFrame(int frameNumber) {
        if (finished) return;
        if (receivedFrames != 0) {
            // Java int subtraction preserves adjacent values across the 32-bit sequence wrap.
            int advance = frameNumber - lastFrameNumber;
            if (advance <= 0 && advance >= -MAX_REORDERED_FRAMES) return;
            if (advance < 0 || advance > MAX_SEQUENCE_ADVANCE) sequenceBreaks++;
            else missingFrames += advance - 1;
        }
        receivedFrames++;
        lastFrameNumber = frameNumber;
    }

    public synchronized Result finish() {
        finished = true;
        Percentiles outputSummary = output.summary();
        double framesPerSecond = outputSummary.samples == 0 ? -1
                : outputSummary.samples * 1_000_000_000.0 / (lastOutputNs - firstOutputNs);
        return new Result(decode.summary(), outputSummary, framesPerSecond,
                outputSummary.samples == 0 || longOutputGapNs == 0 ? -1 : longOutputGaps,
                receivedFrames, missingFrames, sequenceBreaks);
    }

    public static final class Result {
        public final Percentiles decode;
        public final Percentiles output;

        public final double outputFramesPerSecond;
        public final int longOutputGaps, receivedFrames, sequenceBreaks;
        public final long missingFrames;

        private Result(Percentiles decode, Percentiles output, double outputFramesPerSecond, int longOutputGaps,
                       int receivedFrames, long missingFrames, int sequenceBreaks) {
            this.decode = decode;
            this.output = output;
            this.outputFramesPerSecond = outputFramesPerSecond;
            this.longOutputGaps = longOutputGaps;
            this.receivedFrames = receivedFrames;
            this.missingFrames = missingFrames;
            this.sequenceBreaks = sequenceBreaks;
        }

        public double observedLossPercent() {
            // A single frame, or frames separated only by a sequence reset, give no measurable span.
            if (receivedFrames - sequenceBreaks < 2) return -1;
            return 100.0 * missingFrames / (receivedFrames + missingFrames);
        }
    }

    public static final class Percentiles {
        public final int samples;
        public final int p50Micros, p95Micros, p99Micros;

        private Percentiles(int samples, int p50Micros, int p95Micros, int p99Micros) {
            this.samples = samples;
            this.p50Micros = p50Micros;
            this.p95Micros = p95Micros;
            this.p99Micros = p99Micros;
        }
    }

    private static final class Histogram {
        private static final int BUCKET_US = 250;
        private static final int BUCKET_NS = BUCKET_US * 1000;
        private final int[] buckets = new int[8001];
        private int samples;

        void record(long nanos) {
            if (nanos < 0) return;
            long bucket = nanos / BUCKET_NS + (nanos % BUCKET_NS == 0 ? 0 : 1);
            buckets[(int) Math.min(bucket, buckets.length - 1)]++;
            samples++;
        }

        Percentiles summary() {
            return new Percentiles(samples, percentile(50), percentile(95), percentile(99));
        }

        private int percentile(int percentile) {
            if (samples == 0) return -1;
            long rank = (samples * (long) percentile + 99) / 100;
            int count = 0;
            for (int i = 0; i < buckets.length; i++) {
                count += buckets[i];
                if (count >= rank) return i * BUCKET_US;
            }
            return -1;
        }
    }
}
