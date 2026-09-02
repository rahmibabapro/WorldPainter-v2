package org.pepsoft.worldpainter.perf;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured Phase-0 baseline record (paint, pan/zoom, export timings, heap).
 */
public final class BaselineReport {
    private final Instant createdAt;
    private final String label;
    private final BottleneckKind bottleneck;
    private final Map<String, Long> metricsMs;
    private final Map<String, Long> metricsMb;
    private final String notes;

    public BaselineReport(String label, BottleneckKind bottleneck,
                          Map<String, Long> metricsMs, Map<String, Long> metricsMb, String notes) {
        this.createdAt = Instant.now();
        this.label = Objects.requireNonNull(label, "label");
        this.bottleneck = Objects.requireNonNullElse(bottleneck, BottleneckKind.UNKNOWN);
        this.metricsMs = new LinkedHashMap<>(metricsMs != null ? metricsMs : Map.of());
        this.metricsMb = new LinkedHashMap<>(metricsMb != null ? metricsMb : Map.of());
        this.notes = notes != null ? notes : "";
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getLabel() {
        return label;
    }

    public BottleneckKind getBottleneck() {
        return bottleneck;
    }

    public Map<String, Long> getMetricsMs() {
        return Map.copyOf(metricsMs);
    }

    public Map<String, Long> getMetricsMb() {
        return Map.copyOf(metricsMb);
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# WorldPainter v2 performance baseline\n\n");
        sb.append("- Created: ").append(createdAt).append('\n');
        sb.append("- Label: ").append(label).append('\n');
        sb.append("- Bottleneck: **").append(bottleneck).append("**\n\n");
        sb.append("## Timings (ms)\n\n");
        for (Map.Entry<String, Long> e : metricsMs.entrySet()) {
            sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        sb.append("\n## Memory (MB)\n\n");
        for (Map.Entry<String, Long> e : metricsMb.entrySet()) {
            sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        if (! notes.isEmpty()) {
            sb.append("\n## Notes\n\n").append(notes).append('\n');
        }
        return sb.toString();
    }

    public void writeMarkdown(Path path) throws IOException {
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(toMarkdown());
        }
    }
}
