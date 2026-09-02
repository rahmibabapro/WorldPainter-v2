package org.pepsoft.worldpainter.exporting;

import org.pepsoft.minecraft.ChunkFactory;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.perf.BottleneckKind;
import org.pepsoft.worldpainter.perf.BottleneckClassifier;
import org.pepsoft.worldpainter.perf.PerformanceBaseline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.pepsoft.minecraft.ChunkFactory.Stage;

/**
 * Formats {@link ChunkFactory.Stats#timings} for baseline comparison and regression tracking.
 */
public final class ExportTimingReporter {
    private ExportTimingReporter() {
    }

    public static void logStats(Logger logger, String label, ChunkFactory.Stats stats) {
        if (stats == null) {
            return;
        }
        logger.info("{}: total {} ms, land {} m², water {} m²",
                label, stats.time, stats.landArea, stats.waterArea);
        logTimings(logger, label, stats.timings);
        logger.info("{}: peak heap {} MB", label, peakHeapMb());
        BottleneckKind kind = classifyFromStats(stats);
        logger.info("{}: inferred bottleneck {}", label, kind);
    }

    /**
     * Build a Phase-0 markdown report from export stats (and optional UI timings already in {@code extraMs}).
     */
    public static Path writeBaselineReport(String label, ChunkFactory.Stats stats, Path output,
                                           Map<String, Long> extraMs) throws Exception {
        PerformanceBaseline baseline = new PerformanceBaseline().label(label);
        if (stats != null) {
            baseline.recordMs("exportFull", stats.time);
            Map<String, Long> shares = stageSharePercents(stats.timings);
            long maxShare = shares.values().stream().mapToLong(Long::longValue).max().orElse(0L);
            baseline.recordMs("chunkFactoryShare", maxShare);
            Long deflate = firstPresentOrNull(shares, "deflate", "COMPRESS", "Compress", "REGION");
            if (deflate != null) {
                baseline.recordMs("deflateShare", deflate);
            }
        }
        if (extraMs != null) {
            extraMs.forEach(baseline::recordMs);
        }
        baseline.captureHeap("peakHeap");
        baseline.write(output);
        return output;
    }

    private static Long firstPresentOrNull(Map<String, Long> map, String... keys) {
        for (String key : keys) {
            Long v = map.get(key);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    public static BottleneckKind classifyFromStats(ChunkFactory.Stats stats) {
        Map<String, Long> ms = new LinkedHashMap<>();
        Map<String, Long> mb = new LinkedHashMap<>();
        if (stats != null) {
            ms.put("exportFull", stats.time);
            Map<String, Long> shares = stageSharePercents(stats.timings);
            long max = shares.values().stream().mapToLong(Long::longValue).max().orElse(0L);
            ms.put("chunkFactoryShare", max);
        }
        mb.put("peakHeap", peakHeapMb());
        return BottleneckClassifier.classify(ms, mb);
    }

    private static Map<String, Long> stageSharePercents(Map<Object, AtomicLong> timings) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (timings == null || timings.isEmpty()) {
            return out;
        }
        long totalNs = timings.values().stream().mapToLong(AtomicLong::get).sum();
        if (totalNs <= 0) {
            return out;
        }
        for (Map.Entry<Object, AtomicLong> e : timings.entrySet()) {
            out.put(timingName(e.getKey()), Math.round(100.0 * e.getValue().get() / totalNs));
        }
        return out;
    }

    public static void logTimings(Logger logger, String label, Map<Object, AtomicLong> timings) {
        if ((timings == null) || timings.isEmpty()) {
            logger.info("{}: no stage timings recorded", label);
            return;
        }
        long totalNs = timings.values().stream().mapToLong(AtomicLong::get).sum();
        final List<Map.Entry<Object, AtomicLong>> sorted = new ArrayList<>(timings.entrySet());
        sorted.sort(Comparator.comparingLong(e -> -e.getValue().get()));
        logger.info("{}: stage breakdown ({} ms total):", label, totalNs / 1_000_000);
        for (Map.Entry<Object, AtomicLong> entry : sorted) {
            long ns = entry.getValue().get();
            logger.info("  {}: {} ms ({}%)",
                    timingName(entry.getKey()),
                    ns / 1_000_000,
                    String.format("%.1f", totalNs > 0 ? (100.0 * ns / totalNs) : 0.0));
        }
    }

    public static String timingName(Object key) {
        if (key instanceof Stage stage) {
            return stage.getName();
        }
        if (key instanceof Layer layer) {
            return "Layer: " + layer.getName();
        }
        return String.valueOf(key);
    }

    public static long peakHeapMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
}
