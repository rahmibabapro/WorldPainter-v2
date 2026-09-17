package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DrawnRiverGraphOutletPolicyTest {
    private static Set<DrawnRiverGraph.Pixel> straightLine(int x0, int y0, int x1, int y1) {
        Set<DrawnRiverGraph.Pixel> p = new HashSet<>();
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int s = 0; s <= steps; s++) {
            p.add(new DrawnRiverGraph.Pixel(
                    x0 + (x1 - x0) * s / Math.max(1, steps),
                    y0 + (y1 - y0) * s / Math.max(1, steps)));
        }
        return p;
    }

    @Test
    public void swPreferenceOrientsTowardSouthwestTip() {
        // NE tip higher, SW tip lower — but single-cell noise on NE tip is a pit.
        ToDoubleFunction<DrawnRiverGraph.Pixel> height = c -> {
            if (c.x() == 90 && c.y() == 10) return 5; // noisy pit at NE tip
            return 50 + (90 - c.x()) * 0.1 + (90 - c.y()) * 0.1; // SW tip lower overall
        };
        var drawing = straightLine(10, 90, 90, 10);
        var policy = DrawnRiverGraph.OutletPolicy.of(
                DrawnRiverGraph.Drain.SW, null,
                p -> DrawnRiverGraph.windowedHeight(p, height),
                p -> false);
        var r = DrawnRiverGraph.build(drawing, height, p -> false, 3, 12, null, Set.of(), policy, () -> {});
        assertEquals(1, r.basins().size());
        assertEquals(new DrawnRiverGraph.Pixel(10, 90), r.basins().get(0).outlet());
    }

    @Test
    public void windowedHeightIgnoresSingleCellPit() {
        ToDoubleFunction<DrawnRiverGraph.Pixel> raw = c -> (c.x() == 50 && c.y() == 50) ? 1 : 100;
        double w = DrawnRiverGraph.windowedHeight(new DrawnRiverGraph.Pixel(50, 50), raw);
        assertTrue(w > 50);
        assertEquals(100, w, 0.01);
    }

    @Test
    public void edgeTipBeatsInteriorPit() {
        // Interior tip slightly lower; edge bonus should still win.
        var drawing = straightLine(0, 50, 80, 50);
        ToDoubleFunction<DrawnRiverGraph.Pixel> height = c -> c.x() == 80 ? 48 : 50;
        var policy = DrawnRiverGraph.OutletPolicy.of(
                DrawnRiverGraph.Drain.AUTO, null,
                p -> DrawnRiverGraph.windowedHeight(p, height, 3),
                p -> p.x() == 0);
        var r = DrawnRiverGraph.build(drawing, height, p -> false, 3, 12, null, Set.of(), policy, () -> {});
        assertEquals(1, r.basins().size());
        assertEquals(new DrawnRiverGraph.Pixel(0, 50), r.basins().get(0).outlet());
        assertTrue(r.basins().get(0).edgeOutlet());
    }

    @Test
    public void manualOverrideBeatsEverything() {
        var drawing = straightLine(0, 0, 0, 60);
        ToDoubleFunction<DrawnRiverGraph.Pixel> height = c -> 100 - c.y(); // south tip lowest
        var override = new DrawnRiverGraph.Pixel(0, 0); // north tip
        var policy = DrawnRiverGraph.OutletPolicy.of(
                DrawnRiverGraph.Drain.S, override, height, p -> false);
        var r = DrawnRiverGraph.build(drawing, height, p -> false, 3, 12, null, Set.of(), policy, () -> {});
        assertEquals(1, r.basins().size());
        assertEquals(new DrawnRiverGraph.Pixel(0, 0), r.basins().get(0).outlet());
    }

    @Test
    public void multiWetWithDrainDoesNotSkipBasin() {
        Set<DrawnRiverGraph.Pixel> drawing = new HashSet<>(straightLine(0, 0, 0, 40));
        drawing.addAll(straightLine(0, 20, 40, 20));
        // Both north tip (0,0) and east tip (40,20) are wet.
        var policy = DrawnRiverGraph.OutletPolicy.of(
                DrawnRiverGraph.Drain.E, null, c -> 100.0, p -> false);
        var r = DrawnRiverGraph.build(drawing, c -> 100.0,
                p -> (p.x() == 0 && p.y() == 0) || (p.x() == 40 && p.y() == 20),
                3, 12, null, Set.of(), policy, () -> {});
        assertFalse(r.basins().isEmpty());
        assertNotNull(r.basins().get(0).outlet());
        assertEquals(new DrawnRiverGraph.Pixel(40, 20), r.basins().get(0).outlet());
    }

    @Test
    public void autoMultiWetStillRejected() {
        Set<DrawnRiverGraph.Pixel> drawing = new HashSet<>(straightLine(0, 0, 0, 40));
        drawing.addAll(straightLine(0, 20, 40, 20));
        var r = DrawnRiverGraph.build(drawing, c -> 100.0,
                p -> (p.x() == 0 && p.y() == 0) || (p.x() == 40 && p.y() == 20),
                3, 12, null, Set.of(), DrawnRiverGraph.OutletPolicy.auto(), () -> {});
        assertTrue(r.basins().isEmpty());
        assertTrue(r.diagnostics().stream()
                .anyMatch(d -> d.kind() == DrawnRiverNormalizer.IssueKind.MULTI_OUTLET));
    }
}
