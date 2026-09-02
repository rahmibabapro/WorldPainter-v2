package org.pepsoft.worldpainter.perf;

import org.pepsoft.worldpainter.exporting.ExportTimingReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Collects Phase-0 timings and writes a bottleneck-labelled markdown report.
 */
public final class PerformanceBaseline {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceBaseline.class);

    private final Map<String, Long> metricsMs = new LinkedHashMap<>();
    private final Map<String, Long> metricsMb = new LinkedHashMap<>();
    private String label = "baseline";
    private String notes = "";

    public PerformanceBaseline label(String label) {
        this.label = label;
        return this;
    }

    public PerformanceBaseline notes(String notes) {
        this.notes = notes;
        return this;
    }

    public PerformanceBaseline recordMs(String key, long ms) {
        metricsMs.put(key, ms);
        return this;
    }

    public PerformanceBaseline recordMb(String key, long mb) {
        metricsMb.put(key, mb);
        return this;
    }

    public PerformanceBaseline captureHeap(String key) {
        metricsMb.put(key, ExportTimingReporter.peakHeapMb());
        return this;
    }

    public long timeMs(String key, Runnable action) {
        long start = System.nanoTime();
        action.run();
        long ms = (System.nanoTime() - start) / 1_000_000L;
        metricsMs.put(key, ms);
        return ms;
    }

    public <T> T timeMs(String key, Callable<T> action) throws Exception {
        long start = System.nanoTime();
        T result = action.call();
        long ms = (System.nanoTime() - start) / 1_000_000L;
        metricsMs.put(key, ms);
        return result;
    }

    public BaselineReport build() {
        BottleneckKind kind = BottleneckClassifier.classify(metricsMs, metricsMb);
        BaselineReport report = new BaselineReport(label, kind, metricsMs, metricsMb, notes);
        LOGGER.info("Performance baseline [{}]: bottleneck={}", label, kind);
        return report;
    }

    public BaselineReport write(Path markdownPath) throws Exception {
        BaselineReport report = build();
        report.writeMarkdown(markdownPath);
        LOGGER.info("Wrote baseline report to {}", markdownPath.toAbsolutePath());
        return report;
    }
}
