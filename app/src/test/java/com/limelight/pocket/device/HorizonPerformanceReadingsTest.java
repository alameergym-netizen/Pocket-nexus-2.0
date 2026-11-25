package com.limelight.pocket.device;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HorizonPerformanceReadingsTest {
    @Rule public final TemporaryFolder fixture = new TemporaryFolder();

    @Test public void observesNumericCpuOrderingCurrentFallbackAndReadableFrequencySteps() throws IOException {
        File cpu = fixture.newFolder("cpufreq");
        File cluster = fixture.newFolder("cpufreq", "policy10");
        fixture.newFolder("cpufreq", "policy2");
        fixture.newFolder("cpufreq", "other1");
        entry(cluster, "cpuinfo_cur_freq", "2000000");
        entry(cluster, "related_cpus", "5 6 7");
        entry(cluster, "scaling_governor", "schedutil");
        entry(cluster, "scaling_available_frequencies", "2000000 800000 800000");
        HorizonPerformanceReadings value = HorizonPerformanceReadings.sample(cpu, fixture.newFolder(), fixture.newFolder());
        assertEquals(2, value.cpu.size());
        assertEquals("policy2", value.cpu.get(0).name);
        HorizonPerformanceReadings.Frequency policy = value.cpu.get(1);
        assertEquals("5 6 7", policy.cores);
        assertEquals("schedutil", policy.governor);
        assertEquals(2_000_000, policy.current);
        assertEquals(1000, policy.divisor);
        assertArrayEquals(new long[]{800_000, 2_000_000}, policy.steps);
    }

    @Test public void keepsGpuUnitsAndCoolingStateIndependentWhenKernelValuesAreMissing() throws IOException {
        File gpu = fixture.newFolder("kgsl");
        File devfreq = fixture.newFolder("kgsl", "devfreq");
        entry(gpu, "max_gpuclk", "820000000");
        entry(devfreq, "cur_freq", "500000000");
        File thermal = fixture.newFolder("thermal");
        File fan = fixture.newFolder("thermal", "cooling_device3");
        File unrelated = fixture.newFolder("thermal", "cooling_device1");
        entry(fan, "type", "pwm-fan");
        entry(fan, "cur_state", "0");
        entry(fan, "max_state", "4");
        entry(unrelated, "type", "thermal-cpufreq-0");
        HorizonPerformanceReadings value = HorizonPerformanceReadings.sample(fixture.newFolder(), gpu, thermal);
        assertTrue(value.cpu.isEmpty());
        assertEquals(500_000_000, value.gpu.current);
        assertEquals(1_000_000, value.gpu.divisor);
        assertEquals(HorizonPerformanceReadings.MISSING, value.gpu.maximum);
        assertEquals(1, value.fans.size());
        assertEquals(0, value.fans.get(0).state);
        assertEquals(4, value.fans.get(0).maximum);
        entry(fan, "cur_state", "5");
        assertEquals(HorizonPerformanceReadings.MISSING,
                HorizonPerformanceReadings.sample(fixture.newFolder(), gpu, thermal).fans.get(0).state);
    }

    private static void entry(File folder, String name, String value) throws IOException {
        Files.write(new File(folder, name).toPath(), value.getBytes(StandardCharsets.US_ASCII));
    }
}
