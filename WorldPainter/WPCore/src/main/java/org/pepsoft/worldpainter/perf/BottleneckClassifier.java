package org.pepsoft.worldpainter.perf;

import java.util.Map;
import java.util.Objects;

/**
 * Classifies the dominant Phase-0 bottleneck from timing/memory metrics.
 *
 * <p>Metric keys (ms): {@code paintBrush}, {@code panZoomFrame}, {@code exportTurbo},
 * {@code exportFull}, {@code deflateShare}, {@code chunkFactoryShare}.
 * Memory keys (MB): {@code peakHeap}, {@code usedHeap}.
 */
public final class BottleneckClassifier {
    private BottleneckClassifier() {
    }

    public static BottleneckKind classify(Map<String, Long> metricsMs, Map<String, Long> metricsMb) {
        Objects.requireNonNull(metricsMs, "metricsMs");
        long paint = metricsMs.getOrDefault("paintBrush", 0L);
        long pan = metricsMs.getOrDefault("panZoomFrame", 0L);
        long turbo = metricsMs.getOrDefault("exportTurbo", 0L);
        long full = metricsMs.getOrDefault("exportFull", 0L);
        long deflateShare = metricsMs.getOrDefault("deflateShare", 0L);
        long chunkShare = metricsMs.getOrDefault("chunkFactoryShare", 0L);
        long peakHeap = metricsMb != null ? metricsMb.getOrDefault("peakHeap", 0L) : 0L;

        boolean uiHot = paint >= 16 || pan >= 12; // >~60–80 FPS budget
        boolean exportHot = Math.max(turbo, full) > 0;
        boolean heapHot = peakHeap >= 6_000; // 6 GB+ working set while editing/exporting
        boolean deflateHot = deflateShare > 0 && deflateShare >= chunkShare && deflateShare >= 35;
        boolean cpuHot = chunkShare >= 40;

        int flags = 0;
        if (uiHot) flags++;
        if (deflateHot) flags++;
        if (cpuHot) flags++;
        if (heapHot) flags++;

        if (flags >= 2) {
            return BottleneckKind.MIXED;
        }
        if (uiHot && ! exportHot) {
            return BottleneckKind.UI;
        }
        if (heapHot) {
            return BottleneckKind.HEAP;
        }
        if (deflateHot) {
            return BottleneckKind.DEFLATE;
        }
        if (cpuHot || exportHot) {
            return BottleneckKind.EXPORT_CPU;
        }
        if (uiHot) {
            return BottleneckKind.UI;
        }
        return BottleneckKind.UNKNOWN;
    }
}
