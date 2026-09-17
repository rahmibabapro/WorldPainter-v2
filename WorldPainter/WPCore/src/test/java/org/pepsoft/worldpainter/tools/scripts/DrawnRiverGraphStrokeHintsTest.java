package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DrawnRiverGraphStrokeHintsTest {
    private static Set<DrawnRiverGraph.Pixel> line(int x0, int y0, int x1, int y1) {
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
    public void markedOutletsSplitNetworkTowardEachMouth() {
        // Y fork: stem (50,50)-(50,0), left arm to (10,50), right arm to (90,50)
        Set<DrawnRiverGraph.Pixel> drawing = new HashSet<>();
        drawing.addAll(line(50, 0, 50, 50));
        drawing.addAll(line(50, 50, 10, 50));
        drawing.addAll(line(50, 50, 90, 50));
        var left = new DrawnRiverGraph.Pixel(10, 50);
        var right = new DrawnRiverGraph.Pixel(90, 50);
        var hints = new DrawnRiverGraph.StrokeHints(Set.of(left, right), Set.of(), Set.of());
        ToDoubleFunction<DrawnRiverGraph.Pixel> h = p -> 100;
        var r = DrawnRiverGraph.build(drawing, h, p -> false, 3, 12, null, Set.of(),
                DrawnRiverGraph.OutletPolicy.auto(), hints, () -> {});
        assertEquals(2, r.basins().size());
        Set<DrawnRiverGraph.Pixel> outlets = new HashSet<>();
        for (var b : r.basins()) outlets.add(b.outlet());
        assertTrue(outlets.contains(left));
        assertTrue(outlets.contains(right));
        // Stem tip (50,0) should flow toward one of the mouths — never be an outlet.
        for (var b : r.basins()) {
            assertTrue(!b.outlet().equals(new DrawnRiverGraph.Pixel(50, 0)));
        }
    }

    @Test
    public void miniSourceStartsNearOneBlockWide() {
        var drawing = line(0, 0, 40, 0);
        var head = new DrawnRiverGraph.Pixel(0, 0);
        var hints = new DrawnRiverGraph.StrokeHints(Set.of(new DrawnRiverGraph.Pixel(40, 0)),
                Set.of(head), Set.of());
        var r = DrawnRiverGraph.build(drawing, p -> 50 - p.x() * 0.01, p -> false, 5, 12, null, Set.of(),
                DrawnRiverGraph.OutletPolicy.auto(), hints, () -> {});
        assertEquals(1, r.basins().size());
        double w = r.basins().get(0).downstreamFirst().get(0).width();
        assertEquals(DrawnRiverGraph.MINI_WIDTH, w, 0.15);
    }

    @Test
    public void continuationEdgeTipIsNotPreferredAsOutlet() {
        var drawing = line(0, 20, 60, 20);
        var edgeTip = new DrawnRiverGraph.Pixel(0, 20);
        var inlandTip = new DrawnRiverGraph.Pixel(60, 20);
        // Inland tip is slightly lower — without hints edge would win; with continuation at edge,
        // inland should win.
        ToDoubleFunction<DrawnRiverGraph.Pixel> height = p -> p.x() == 60 ? 40 : 50;
        var hints = new DrawnRiverGraph.StrokeHints(Set.of(), Set.of(), Set.of(edgeTip));
        var policy = DrawnRiverGraph.OutletPolicy.of(DrawnRiverGraph.Drain.AUTO, null, height, p -> p.x() == 0);
        var r = DrawnRiverGraph.build(drawing, height, p -> false, 3, 12, null, Set.of(), policy, hints, () -> {});
        assertEquals(1, r.basins().size());
        assertEquals(inlandTip, r.basins().get(0).outlet());
    }

    @Test
    public void continuationSourceIsThick() {
        var drawing = line(0, 0, 30, 0);
        var head = new DrawnRiverGraph.Pixel(0, 0);
        var hints = new DrawnRiverGraph.StrokeHints(Set.of(new DrawnRiverGraph.Pixel(30, 0)),
                Set.of(), Set.of(head));
        var r = DrawnRiverGraph.build(drawing, p -> 40, p -> false, 4, 16, null, Set.of(),
                DrawnRiverGraph.OutletPolicy.auto(), hints, () -> {});
        double w = r.basins().get(0).downstreamFirst().get(0).width();
        assertTrue("continuation should be thick, got " + w, w >= DrawnRiverGraph.CONTINUATION_MIN_WIDTH - 0.01);
    }

    @Test
    public void seedContributionHelpers() {
        var mini = new DrawnRiverGraph.Pixel(1, 1);
        var cont = new DrawnRiverGraph.Pixel(2, 2);
        var path = new DrawnRiverGraph.Pixel(3, 3);
        var hints = new DrawnRiverGraph.StrokeHints(Set.of(), Set.of(mini), Set.of(cont));
        assertEquals(1.0, DrawnRiverGraph.seedContribution(path, hints, 5, 12), 1e-9);
        assertEquals(Math.pow(1.0 / 5, 2), DrawnRiverGraph.seedContribution(mini, hints, 5, 12), 1e-9);
        double contSeed = DrawnRiverGraph.seedContribution(cont, hints, 5, 16);
        assertEquals(Math.pow(Math.max(5 * 2.5, 8) / 5, 2), contSeed, 1e-9);
    }

    @Test
    public void snapMarkedOutletsOntoEnds() {
        var ends = List.of(new DrawnRiverGraph.Pixel(0, 0), new DrawnRiverGraph.Pixel(10, 0));
        var component = new HashSet<>(ends);
        component.add(new DrawnRiverGraph.Pixel(5, 0));
        var marks = Set.of(new DrawnRiverGraph.Pixel(0, 1)); // 1 block from left end
        var snapped = DrawnRiverGraph.snapMarkedOutlets(marks, List.copyOf(component), ends);
        assertEquals(List.of(new DrawnRiverGraph.Pixel(0, 0)), snapped);
    }
}
