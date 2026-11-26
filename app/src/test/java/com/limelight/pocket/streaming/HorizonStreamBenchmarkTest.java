package com.limelight.pocket.streaming;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HorizonStreamBenchmarkTest {
    @Test public void calculatesNearestRankForObservedDecodeTimes() {
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark(60);
        for (int i = 1; i <= 100; i++) benchmark.recordDecode(i * 1_000_000L);
        HorizonStreamBenchmark.Percentiles decode = benchmark.finish().decode;
        assertEquals(100, decode.samples);
        assertEquals(50_000, decode.p50Micros);
        assertEquals(95_000, decode.p95Micros);
        assertEquals(99_000, decode.p99Micros);
    }

    @Test public void outputUsesIntervalsAndCapsLongPausesForDisplay() {
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark(60);
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
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark(60);
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
    @Test public void measuresOutputRateAndGapsFromActualTimeBetweenSubmissions() {
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark(60);
        benchmark.recordOutput(1_000_000);
        benchmark.recordOutput(17_000_000);
        benchmark.recordOutput(33_000_000);
        benchmark.recordOutput(81_000_000);
        HorizonStreamBenchmark.Result result = benchmark.finish();
        assertEquals(37.5, result.outputFramesPerSecond, 0.0001);
        assertEquals(1, result.longOutputGaps);
    }

    @Test public void countsOnlyGapsBetweenUniqueFramesAndSurvivesSequenceWrap() {
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark(60);
        for (int number : new int[]{-2, -1, -1, 0, 3, 2, 2, 4}) benchmark.recordIncomingFrame(number);
        HorizonStreamBenchmark.Result result = benchmark.finish();
        assertEquals(6, result.receivedFrames);
        assertEquals(1, result.missingFrames);
        assertEquals(0, result.sequenceBreaks);
        assertEquals(100 / 7.0, result.observedLossPercent(), 0.0001);
    }

    @Test public void resolvesAHoleAcrossSignedWrapWithoutCountingDuplicatesOrPreCaptureFrames() {
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark(120);
        for (int frame : new int[]{Integer.MAX_VALUE - 1, Integer.MIN_VALUE + 1, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE - 2}) {
            benchmark.recordIncomingFrame(frame);
        }
        HorizonStreamBenchmark.Result result = benchmark.finish();
        assertEquals(4, result.receivedFrames);
        assertEquals(0, result.missingFrames);
        assertEquals(0, result.sequenceBreaks);
        assertEquals(0, result.observedLossPercent(), 0.0001);
    }

    @Test public void fillsOldestTrackedGapAndDiscardsItsHistoryOnDiscontinuity() {
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark(60);
        benchmark.recordIncomingFrame(9);
        benchmark.recordIncomingFrame(250);
        benchmark.recordIncomingFrame(10);
        benchmark.recordIncomingFrame(10);
        int reset = 250 + (1 << 20);
        benchmark.recordIncomingFrame(reset);
        benchmark.recordIncomingFrame(reset - 2);
        HorizonStreamBenchmark.Result result = benchmark.finish();
        assertEquals(4, result.receivedFrames);
        assertEquals(239, result.missingFrames);
        assertEquals(1, result.sequenceBreaks);
    }

    @Test public void abruptSequenceJumpsDoNotBecomeMillionsOfDroppedFrames() {
        HorizonStreamBenchmark benchmark = new HorizonStreamBenchmark(120);
        benchmark.recordIncomingFrame(731);
        benchmark.recordIncomingFrame(2_000_000);
        HorizonStreamBenchmark.Result result = benchmark.finish();
        assertEquals(1, result.sequenceBreaks);
        assertEquals(0, result.missingFrames);
        assertEquals(-1, result.observedLossPercent(), 0.01);
    }
}
