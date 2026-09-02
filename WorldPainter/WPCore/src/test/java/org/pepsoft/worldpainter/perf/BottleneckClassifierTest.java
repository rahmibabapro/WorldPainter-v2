package org.pepsoft.worldpainter.perf;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BottleneckClassifierTest {
    @Test
    public void classifiesUiWhenPaintAndPanHot() {
        BottleneckKind kind = BottleneckClassifier.classify(
                Map.of("paintBrush", 40L, "panZoomFrame", 20L),
                Map.of("peakHeap", 512L));
        assertEquals(BottleneckKind.UI, kind);
    }

    @Test
    public void classifiesDeflateWhenShareDominates() {
        BottleneckKind kind = BottleneckClassifier.classify(
                Map.of("exportFull", 10_000L, "deflateShare", 55L, "chunkFactoryShare", 20L),
                Map.of("peakHeap", 1024L));
        assertEquals(BottleneckKind.DEFLATE, kind);
    }

    @Test
    public void classifiesHeapWhenPeakHuge() {
        BottleneckKind kind = BottleneckClassifier.classify(
                Map.of("exportFull", 1000L),
                Map.of("peakHeap", 8000L));
        assertEquals(BottleneckKind.HEAP, kind);
    }

    @Test
    public void writesMarkdownReport() throws Exception {
        Path out = Files.createTempFile("wp-baseline-", ".md");
        BaselineReport report = new PerformanceBaseline()
                .label("unit")
                .recordMs("exportTurbo", 1234L)
                .recordMs("paintBrush", 5L)
                .recordMb("peakHeap", 900L)
                .notes("synthetic")
                .write(out);
        assertTrue(Files.size(out) > 40);
        String text = Files.readString(out);
        assertTrue(text.contains("Bottleneck"));
        assertTrue(text.contains(report.getBottleneck().name()));
        Files.deleteIfExists(out);
    }
}
