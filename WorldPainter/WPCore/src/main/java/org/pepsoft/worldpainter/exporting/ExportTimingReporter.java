package org.pepsoft.worldpainter.exporting;

import org.pepsoft.minecraft.ChunkFactory;
import org.pepsoft.worldpainter.layers.Layer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
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
