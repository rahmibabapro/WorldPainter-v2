package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class DrawnRiverGraphTest {
    static Set<DrawnRiverGraph.Pixel> yDrawing() {
        Set<DrawnRiverGraph.Pixel> p = new HashSet<>();
        for (int i = 0; i <= 30; i++) {
            p.add(new DrawnRiverGraph.Pixel(64 - i, 64 - i));
            p.add(new DrawnRiverGraph.Pixel(64 + i, 64 - i));
            p.add(new DrawnRiverGraph.Pixel(64, 64 + i));
        }
        return p;
    }

    /** Three arms around a missing centre cell — historically rejected as a closed loop. */
    static Set<DrawnRiverGraph.Pixel> threeArmJunctionWithOneCellGap() {
        Set<DrawnRiverGraph.Pixel> p = new HashSet<>();
        // Ring around missing (64,64)
        p.add(new DrawnRiverGraph.Pixel(64, 63));
        p.add(new DrawnRiverGraph.Pixel(65, 64));
        p.add(new DrawnRiverGraph.Pixel(64, 65));
        p.add(new DrawnRiverGraph.Pixel(63, 64));
        for (int i = 1; i <= 25; i++) {
            p.add(new DrawnRiverGraph.Pixel(64, 63 - i)); // north
            p.add(new DrawnRiverGraph.Pixel(65 + i, 64)); // east
            p.add(new DrawnRiverGraph.Pixel(64, 65 + i)); // south
        }
        return p;
    }

    static Set<DrawnRiverGraph.Pixel> threeArmJunctionSolidCentre() {
        Set<DrawnRiverGraph.Pixel> p = threeArmJunctionWithOneCellGap();
        p.add(new DrawnRiverGraph.Pixel(64, 64));
        return p;
    }

    private DrawnRiverGraph.Result build(Collection<DrawnRiverGraph.Pixel> p) {
        return DrawnRiverGraph.build(p, c -> 200 - c.y(), c -> false, 3, 24, () -> {});
    }

    @Test
    public void yPreservesBothSourcesAndOneSharedTrunk() {
        var r = build(yDrawing());
        assertEquals(1, r.basins().size());
        var b = r.basins().get(0);
        assertEquals(2, b.sources());
        assertEquals(1, b.junctions());
        assertEquals(3, b.downstreamFirst().size());
        assertEquals(3 * Math.sqrt(2), b.downstreamFirst().get(0).width(), .001);
        assertEquals(new DrawnRiverGraph.Pixel(64, 94), b.downstreamFirst().get(0).pixels().getLast());
        Set<String> edges = new HashSet<>();
        for (var reach : b.downstreamFirst())
            for (int i = 1; i < reach.pixels().size(); i++)
                assertTrue(edges.add(reach.pixels().get(i - 1) + "/" + reach.pixels().get(i)));
    }

    @Test
    public void oneCellJunctionGapMatchesSolidCentreTopology() {
        var gapped = build(threeArmJunctionWithOneCellGap());
        var solid = build(threeArmJunctionSolidCentre());
        assertEquals("gap should resolve: " + gapped.warnings(), 1, gapped.basins().size());
        assertEquals(1, solid.basins().size());
        assertEquals(solid.basins().get(0).sources(), gapped.basins().get(0).sources());
        assertEquals(solid.basins().get(0).junctions(), gapped.basins().get(0).junctions());
        assertEquals(solid.basins().get(0).downstreamFirst().size(), gapped.basins().get(0).downstreamFirst().size());
        assertTrue(gapped.repairs().contains(new DrawnRiverGraph.Pixel(64, 64)));
        assertTrue(gapped.diagnostics().stream()
                .anyMatch(d -> d.kind() == DrawnRiverNormalizer.IssueKind.SMALL_GAP_REPAIRED));
        // South tip is the outlet (lowest height); two upstream arms + shared trunk.
        assertEquals(2, gapped.basins().get(0).sources());
        assertEquals(1, gapped.basins().get(0).junctions());
        assertEquals(3, gapped.basins().get(0).downstreamFirst().size());
    }

    @Test
    public void disconnectedStrokeIsNotDiscarded() {
        var p = yDrawing();
        for (int y = 0; y < 10; y++) p.add(new DrawnRiverGraph.Pixel(200, y));
        assertEquals(2, build(p).basins().size());
    }

    @Test
    public void thickStrokeRetainsBranchTopology() {
        Set<DrawnRiverGraph.Pixel> p = new HashSet<>();
        for (var c : yDrawing())
            for (int x = -2; x <= 2; x++)
                for (int y = -2; y <= 2; y++)
                    p.add(new DrawnRiverGraph.Pixel(c.x() + x, c.y() + y));
        var r = build(p);
        assertEquals(r.warnings().toString(), 1, r.basins().size());
        assertEquals(2, r.basins().get(0).sources());
    }

    @Test
    public void inputOrderDoesNotMatter() {
        List<DrawnRiverGraph.Pixel> p = new ArrayList<>(yDrawing());
        var expected = build(p);
        Collections.shuffle(p, new Random(17));
        assertEquals(expected, build(p));
    }

    @Test
    public void loopsAreExplainedNotSilentlyCut() {
        Set<DrawnRiverGraph.Pixel> p = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            p.add(new DrawnRiverGraph.Pixel(i, 0));
            p.add(new DrawnRiverGraph.Pixel(i, 9));
            p.add(new DrawnRiverGraph.Pixel(0, i));
            p.add(new DrawnRiverGraph.Pixel(9, i));
        }
        var r = build(p);
        assertTrue(r.basins().isEmpty());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.kind() == DrawnRiverNormalizer.IssueKind.REAL_LOOP));
        assertTrue(r.warnings().get(0).toLowerCase().contains("döngü"));
        assertTrue(r.diagnostics().get(0).bounds().width >= 9);
    }

    @Test
    public void invalidLoopDoesNotBlockSeparateValidBasin() {
        Set<DrawnRiverGraph.Pixel> p = new HashSet<>(yDrawing());
        for (int i = 0; i < 10; i++) {
            p.add(new DrawnRiverGraph.Pixel(300 + i, 0));
            p.add(new DrawnRiverGraph.Pixel(300 + i, 9));
            p.add(new DrawnRiverGraph.Pixel(300, i));
            p.add(new DrawnRiverGraph.Pixel(309, i));
        }
        var r = build(p);
        assertEquals(1, r.basins().size());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.kind() == DrawnRiverNormalizer.IssueKind.REAL_LOOP));
    }

    @Test
    public void nearbyParallelRiversAreNotMerged() {
        Set<DrawnRiverGraph.Pixel> p = new HashSet<>();
        for (int y = 0; y <= 40; y++) {
            p.add(new DrawnRiverGraph.Pixel(10, y));
            p.add(new DrawnRiverGraph.Pixel(14, y)); // 3 empty cells between — not a ≤3×3 junction gap with 3 arms
        }
        var r = build(p);
        assertEquals(2, r.basins().size());
        assertTrue(r.repairs().isEmpty());
    }

    @Test
    public void excludedEdgeOpensLargeLoopIntoAPath() {
        Set<DrawnRiverGraph.Pixel> p = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            p.add(new DrawnRiverGraph.Pixel(i, 0));
            p.add(new DrawnRiverGraph.Pixel(i, 9));
            p.add(new DrawnRiverGraph.Pixel(0, i));
            p.add(new DrawnRiverGraph.Pixel(9, i));
        }
        assertTrue(build(p).basins().isEmpty());
        Set<DrawnRiverGraph.Edge> exclude = Set.of(
                DrawnRiverGraph.Edge.of(new DrawnRiverGraph.Pixel(2, 0), new DrawnRiverGraph.Pixel(3, 0)));
        var opened = DrawnRiverGraph.build(p, c -> 200 - c.y(), c -> false, 3, 24, null, exclude, () -> {});
        assertEquals(opened.warnings().toString(), 1, opened.basins().size());
        assertTrue(opened.diagnostics().stream().noneMatch(d -> d.kind() == DrawnRiverNormalizer.IssueKind.REAL_LOOP));
        assertTrue(opened.skeleton().contains(new DrawnRiverGraph.Pixel(2, 0)));
        assertTrue(opened.skeleton().contains(new DrawnRiverGraph.Pixel(3, 0)));
    }

    @Test
    public void closedThreeByThreeRingIsRealLoopNotGapFill() {
        Set<DrawnRiverGraph.Pixel> p = new HashSet<>();
        for (int x = 0; x <= 2; x++) for (int y = 0; y <= 2; y++) {
            if (x == 1 && y == 1) continue;
            p.add(new DrawnRiverGraph.Pixel(x, y));
        }
        var r = build(p);
        assertTrue(r.repairs().isEmpty());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.kind() == DrawnRiverNormalizer.IssueKind.REAL_LOOP));
        assertTrue(r.basins().isEmpty());
        assertTrue("ring must not collapse to a point: " + r.skeleton(), r.skeleton().size() >= 4);
    }

    @Test
    public void distantStrokesDoNotScanBoundingBox() {
        Set<DrawnRiverGraph.Pixel> p = Set.of(
                new DrawnRiverGraph.Pixel(0, 0),
                new DrawnRiverGraph.Pixel(2048, 2048));
        AtomicInteger checks = new AtomicInteger();
        DrawnRiverGraph.build(p, c -> 200 - c.y(), c -> false, 3, 24, () -> {
            if (checks.incrementAndGet() > 50_000)
                throw new CancellationException("bounding-box scan exceeded 50000 checks");
        });
        assertTrue("checks=" + checks.get(), checks.get() <= 50_000);
        assertTrue("checks=" + checks.get(), checks.get() < 5_000);
    }

    @Test
    public void cancellationIsCheckedInsideThinning() {
        assertThrows(CancellationException.class, () ->
                DrawnRiverGraph.build(yDrawing(), p -> 1, p -> false, 3, 24, () -> {
                    throw new CancellationException();
                }));
    }
}
