package com.limelight.pocket.streaming;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HorizonStreamBenchmarkTest {
    @Test public void calculatesNearestRankForObservedDecodeTimes() {
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark();
        for (int i = 1; i <= 100; i++) benchmark.recordDecode(i * 1_000_000L);
        HorizonStreamBenchmark.Percentiles decode = benchmark.finish().decode;
        assertEquals(100, decode.samples);
        assertEquals(50_000, decode.p50Micros);
        assertEquals(95_000, decode.p95Micros);
        assertEquals(99_000, decode.p99Micros);
    }

    @Test public void outputUsesIntervalsAndCapsLongPausesForDisplay() {
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark();
        benchmark.recordOutput(5_000_000);
        benchmark.recordOutput(4_000_000);
        benchmark.recordOutput(21_001_000);
        benchmark.recordOutput(8_000_000_000L);
        HorizonStreamBenchmark.Percentiles output = benchmark.finish().output;
        assertEquals(2, output.samples);
        assertEquals(16_250, output.p50Micros);
        assertEquals(2_000_000, output.p95Micros);
    }

    @Test public void missingFramesRemainUnavailableAndFinishedRunCannotBeChanged() {
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark();
        benchmark.recordDecode(-1);
        benchmark.recordOutput(5);
        benchmark.finish();
        benchmark.recordDecode(300);
        benchmark.recordOutput(9);
        HorizonStreamBenchmark.Result result = benchmark.finish();
        assertEquals(0, result.decode.samples);
        assertEquals(-1, result.decode.p99Micros);
        assertEquals(0, result.output.samples);
        assertEquals(-1, result.output.p99Micros);
    }
}
