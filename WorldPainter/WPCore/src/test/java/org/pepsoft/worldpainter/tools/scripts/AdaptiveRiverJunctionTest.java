package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.River;
import org.pepsoft.worldpainter.layers.Void;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Accepted geometry is committed to the plan only after the prospective junction passes validation. */
public class AdaptiveRiverJunctionTest {
    @Test
    public void sameLevelCrossingIsAcceptedAndNeitherPlanMutatesTheWorldBeforeApply() {
        final Dimension dimension = terrain(0);
        final Snapshot original = snapshot(dimension);
        final ShallowRiverCarver plan = plan(dimension);
        assertAccepted(plan, horizontal(64));
        assertEquals(original, snapshot(dimension));
        assertAccepted(plan, vertical(false));
        assertEquals("Both accepted paths must remain read-only until apply", original, snapshot(dimension));

        final ShallowRiverCarver.Result result = plan.apply();

        assertEquals(2, result.paths());
        assertEquals(0, result.rejectedPaths());
        assertWetThreeWide(dimension, horizontal(64), 100);
        assertWetThreeWide(dimension, vertical(false), 100);
        assertTrue("Crossing must not subtract depth twice", dimension.getHeightAt(64, 64) >= 100 - DEPTH - EPSILON);
        assertEarthworksLimits(original, dimension);
    }

    @Test
    public void identicalOverlappingPathsDoNotDoubleCarveOrRepaint() {
        final Dimension expected = terrain(0), actual = terrain(0);
        final ShallowRiverCarver reference = plan(expected), combined = plan(actual);
        assertAccepted(reference, horizontal(64));
        reference.apply();
        final Snapshot original = snapshot(actual);

        assertAccepted(combined, horizontal(64));
        assertAccepted(combined, horizontal(64));
        assertEquals(original, snapshot(actual));
        final ShallowRiverCarver.Result result = combined.apply();

        assertEquals(2, result.paths());
        assertEquals("Duplicate accepted route must leave the same terrain, water and biome as one route",
                snapshot(expected), snapshot(actual));
    }

    @Test
    public void northToSouthLowerRiverCannotBorrowAnotherRoutesDownstreamException() {
        assertDifferentLevelCrossingRejected(false);
    }

    @Test
    public void southToNorthLowerRiverCannotBorrowAnotherRoutesDownstreamException() {
        assertDifferentLevelCrossingRejected(true);
    }

    @Test
    public void rejectedCrossingDoesNotPoisonALaterValidPathOrAnyEarlierBankRepairs() {
        final Dimension expected = terrain(-1), actual = terrain(-1);
        final ShallowRiverCarver reference = plan(expected), combined = plan(actual);
        assertAccepted(reference, horizontal(64));
        assertAccepted(reference, horizontal(100));
        reference.apply();
        final Snapshot original = snapshot(actual);

        assertAccepted(combined, horizontal(64));
        assertFalse("Cross-route water levels must not be conflated", add(combined, vertical(false)));
        assertEquals(1, combined.getAcceptedPaths());
        assertEquals(original, snapshot(actual));
        assertAccepted(combined, horizontal(100));
        assertEquals(original, snapshot(actual));
        final ShallowRiverCarver.Result result = combined.apply();

        assertEquals(2, result.paths());
        assertEquals(1, result.rejectedPaths());
        assertEquals("A rejected candidate may not change the previously accepted proposal map",
                snapshot(expected), snapshot(actual));
    }

    @Test
    public void protectedCrossingRejectsWithoutChangingAnAlreadyAcceptedRoute() {
        final Dimension expected = terrain(0), actual = terrain(0);
        expected.setBitLayerValueAt(Void.INSTANCE, 64, 30, true);
        actual.setBitLayerValueAt(Void.INSTANCE, 64, 30, true);
        final ShallowRiverCarver reference = plan(expected), combined = plan(actual);
        assertAccepted(reference, horizontal(64));
        reference.apply();
        final Snapshot original = snapshot(actual);
        assertAccepted(combined, horizontal(64));

        assertFalse(add(combined, vertical(false)));
        assertEquals(original, snapshot(actual));
        final ShallowRiverCarver.Result result = combined.apply();

        assertEquals(1, result.paths());
        assertEquals(1, result.rejectedPaths());
        assertEquals(snapshot(expected), snapshot(actual));
        assertTrue(actual.getBitLayerValueAt(Void.INSTANCE, 64, 30));
    }

    private static void assertDifferentLevelCrossingRejected(boolean fromSouth) {
        // The entire first river and its shoulder lie in the Y=100 plateau.
        // The second river starts on a long Y=99 plateau, then keeps water at
        // Y=99 through the crossing. Each river is individually feasible, but
        // their one-block cross-route level mismatch is not a same-route step.
        final int lowSide = fromSouth ? 1 : -1;
        final Dimension expected = terrain(lowSide), actual = terrain(lowSide), secondOnly = terrain(lowSide);
        final ShallowRiverCarver reference = plan(expected), independentSecond = plan(secondOnly), combined = plan(actual);
        assertAccepted(reference, horizontal(64));
        reference.apply();
        assertAccepted(independentSecond, vertical(fromSouth));
        independentSecond.apply();
        assertEquals(100, expected.getWaterLevelAt(64, 64));
        assertEquals(99, secondOnly.getWaterLevelAt(64, 64));
        final Snapshot original = snapshot(actual);
        assertAccepted(combined, horizontal(64));
        assertEquals(original, snapshot(actual));

        final boolean accepted = add(combined, vertical(fromSouth));

        assertFalse("Different-route water levels must not borrow local path-progress ordering", accepted);
        assertFalse("A rejected hydraulic junction must report a reason", combined.getLastRejection().isBlank());
        assertEquals(1, combined.getAcceptedPaths());
        assertEquals("A rejected second path must not apply any earthworks", original, snapshot(actual));
        final ShallowRiverCarver.Result result = combined.apply();
        assertEquals(1, result.paths());
        assertEquals(1, result.rejectedPaths());
        assertEquals("A late junction rejection must restore the exact first-only planned outcome",
                snapshot(expected), snapshot(actual));
    }

    private static ShallowRiverCarver plan(Dimension dimension) {
        final ShallowRiverCarver plan = new ShallowRiverCarver(dimension, 5, 12, DEPTH, true, true, 733L, null);
        plan.enableTerrainAdaptation();
        return plan;
    }

    private static Path horizontal(int y) { return new Path(new int[] { 20, 108 }, new int[] { y, y }); }
    private static Path vertical(boolean fromSouth) {
        return new Path(new int[] { 64, 64 }, fromSouth ? new int[] { 108, 20 } : new int[] { 20, 108 });
    }
    private static boolean add(ShallowRiverCarver plan, Path path) { return plan.addPath(path.xs, path.ys); }
    private static void assertAccepted(ShallowRiverCarver plan, Path path) {
        final boolean accepted = add(plan, path);
        assertTrue(plan.getLastRejection(), accepted);
    }

    private static Dimension terrain(int lowSide) {
        final TileFactory factory = TestData.createTileFactory(100);
        final Dimension dimension = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "Adaptive junction test", 733L, factory, Dimension.Anchor.NORMAL_DETAIL);
        final Tile tile = factory.createTile(0, 0);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                tile.setHeight(x, y, lowSide < 0 && y <= 44 || lowSide > 0 && y >= 84 ? 99 : 100);
                tile.setWaterLevel(x, y, 0);
                tile.setTerrain(x, y, Terrain.GRASS);
                tile.setLayerValue(Biome.INSTANCE, x, y, 4);
                tile.setBitLayerValue(Frost.INSTANCE, x, y, x % 7 == 0 && y % 5 == 0);
            }
        }
        dimension.addTile(tile);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.NONE);
        return dimension;
    }

    private static void assertWetThreeWide(Dimension dimension, Path path, int water) {
        final boolean vertical = path.xs[0] == path.xs[1];
        for (int along = 24; along <= 104; along++) {
            for (int across = -1; across <= 1; across++) {
                final int x = vertical ? 64 + across : along, y = vertical ? along : path.ys[0] + across;
                assertEquals("One consistent water level through the junction", water, dimension.getWaterLevelAt(x, y));
                assertTrue("No missing wet corridor cell at " + x + "," + y, water > dimension.getIntHeightAt(x, y));
                assertTrue(water - dimension.getHeightAt(x, y) <= DEPTH + EPSILON);
            }
        }
    }

    private static void assertEarthworksLimits(Snapshot before, Dimension dimension) {
        for (Cell original : before.cells) {
            final double change = dimension.getHeightAt(original.x, original.y) - original.height;
            assertTrue("Combined paths cannot stack fill", change <= 2 + EPSILON);
            assertTrue("Combined paths cannot stack excavation", -change <= DEPTH + 3.75 + EPSILON);
        }
    }

    private static Snapshot snapshot(Dimension dimension) {
        final List<Cell> cells = new ArrayList<>(16384);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int flags = 0;
                for (int index = 0; index < OBSERVED_LAYERS.length; index++) {
                    if (dimension.getBitLayerValueAt(OBSERVED_LAYERS[index], x, y)) flags |= 1 << index;
                }
                cells.add(new Cell(x, y, dimension.getHeightAt(x, y), dimension.getWaterLevelAt(x, y),
                        dimension.getTerrainAt(x, y), dimension.getLayerValueAt(Biome.INSTANCE, x, y), flags));
            }
        }
        return new Snapshot(cells, dimension.getSurfaceSmoothing(), dimension.getTileCount());
    }

    private record Path(int[] xs, int[] ys) {}
    private record Cell(int x, int y, float height, int water, Terrain terrain, int biome, int flags) {}
    private record Snapshot(List<Cell> cells, Dimension.SurfaceSmoothing smoothing, int tileCount) {
        @Override public String toString() { return "Snapshot[cells=" + cells.size() + ",hash=" + cells.hashCode() + "]"; }
    }
    private static final Layer[] OBSERVED_LAYERS = { Void.INSTANCE, NotPresent.INSTANCE, NotPresentBlock.INSTANCE,
            ReadOnly.INSTANCE, River.INSTANCE, FloodWithLava.INSTANCE, Frost.INSTANCE };
    private static final double DEPTH = 1.10;
    private static final double EPSILON = 1.0 / 256.0;
}
