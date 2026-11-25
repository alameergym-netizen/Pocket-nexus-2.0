package com.limelight.pocket.streaming;

/** On-demand client timing capture with fixed storage and a single owning decoder. */
public final class HorizonStreamBenchmark {
    private final Histogram decode = new Histogram();
    private final Histogram output = new Histogram();
    private long lastOutputNs;
    private boolean finished;

    public synchronized void recordDecode(long nanoseconds) {
        if (!finished) decode.record(nanoseconds);
    }

    public synchronized void recordOutput(long nanoseconds) {
        if (finished || nanoseconds <= lastOutputNs) return;
        if (lastOutputNs != 0) output.record(nanoseconds - lastOutputNs);
        lastOutputNs = nanoseconds;
    }

    public synchronized Result finish() {
        finished = true;
        return new Result(decode.summary(), output.summary());
    }

    public static final class Result {
        public final Percentiles decode;
        public final Percentiles output;

        private Result(Percentiles decode, Percentiles output) {
            this.decode = decode;
            this.output = output;
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
