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
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Terrain fitting is opt-in; water depth remains shallow even when incision/fill budgets grow. */
public class AdaptiveShallowRiverCarverTest {
    @Test
    public void strictBudgetsRemainDefaultAndAdaptationMustBeExplicit() {
        final Dimension dimension = flat();
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = create(dimension, 5, 12, DEPTH, null);
        assertFalse(carver.isTerrainAdaptationEnabled());
        assertEquals(DEPTH + 0.75, carver.getMaximumCut(), 0.000001);
        assertEquals(0, carver.getMaximumFill(), 0.000001);

        carver.enableTerrainAdaptation();

        assertTrue(carver.isTerrainAdaptationEnabled());
        assertEquals(DEPTH + 3.75, carver.getMaximumCut(), 0.000001);
        assertEquals(2, carver.getMaximumFill(), 0.000001);
        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void smallTwoBlockWideRidgeRejectsStrictButAdaptsWithoutDeepWater() {
        final Dimension dimension = fixture(true, false);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver strict = create(dimension, 5, 12, DEPTH, null);
        assertFalse("Strict legacy budget must not force a channel through the ridge", add(strict));
        assertEquals(before, snapshot(dimension));

        final ShallowRiverCarver adaptive = adaptive(dimension, 5, 12, DEPTH, null);
        assertAccepted(adaptive);
        assertEquals("Planning must be read-only even when adapting terrain", before, snapshot(dimension));
        final ShallowRiverCarver.Result result = adaptive.apply();

        assertEquals(1, result.paths());
        assertTrue("The ridge should actually be fitted, not skipped", dimension.getHeightAt(63, 64) < 101);
        assertTrue(result.maximumCut() > DEPTH + 0.75);
        assertShallowThreeWide(dimension, DEPTH);
        assertBudgetsAndFootprint(before, dimension, adaptive, result, 12);
    }

    @Test
    public void isolatedDepressionsAreFilledWithoutDraggingTheWholeRiverDown() {
        final Dimension dimension = fixture(false, true);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver adaptive = adaptive(dimension, 5, 12, DEPTH, null);
        assertAccepted(adaptive);
        assertEquals(before, snapshot(dimension));
        final ShallowRiverCarver.Result result = adaptive.apply();

        assertTrue("The isolated pit must be fitted with bounded fill", dimension.getHeightAt(49, 64) > 98.5f);
        assertEquals("An isolated depression must not lower the downstream water profile", 100,
                dimension.getWaterLevelAt(80, 64));
        assertTrue(result.raisedCells() > 0);
        assertTrue(result.maximumFill() > 0);
        assertShallowThreeWide(dimension, DEPTH);
        assertBudgetsAndFootprint(before, dimension, adaptive, result, 12);
    }

    @Test
    public void simultaneousCutAndFillStayLocalAndReportActualStoredDeltas() {
        final Dimension dimension = fixture(true, true);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver adaptive = adaptive(dimension, 5, 12, DEPTH, null);
        assertAccepted(adaptive);
        assertEquals(before, snapshot(dimension));
        final ShallowRiverCarver.Result result = adaptive.apply();

        assertTrue(result.maximumCut() > DEPTH + 0.75);
        assertTrue(result.raisedCells() > 0);
        assertShallowThreeWide(dimension, DEPTH);
        assertBudgetsAndFootprint(before, dimension, adaptive, result, 12);
        assertEquals("Never globally enable slab/stair export while fitting a river",
                Dimension.SurfaceSmoothing.NONE, dimension.getSurfaceSmoothing());
    }

    @Test
    public void nominalThreeBlockProfileKeepsThreeActualWetColumnsAfterAdaptation() {
        final Dimension dimension = fixture(true, true);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver adaptive = adaptive(dimension, 3, 3, 0.85, null);
        assertAccepted(adaptive);
        final ShallowRiverCarver.Result result = adaptive.apply();

        assertShallowThreeWide(dimension, 0.85);
        assertBudgetsAndFootprint(before, dimension, adaptive, result, 3);
    }

    @Test
    public void cancellationAfterBothRaisingAndCuttingRestoresEveryOriginalField() {
        final Dimension dimension = fixture(true, true);
        final Snapshot before = snapshot(dimension);
        final boolean[] applying = { false }, sawCut = { false }, sawFill = { false };
        final ScriptProgress cancellation = new ScriptProgress(null, null) {
            @Override
            public void checkForCancel() {
                if (!applying[0]) return;
                sawCut[0] |= dimension.getHeightAt(20, 64) < 100;
                sawFill[0] |= dimension.getHeightAt(49, 64) > 98.5f;
                if (sawCut[0] && sawFill[0]) throw new ScriptingContext.InterruptedException();
            }
        };
        final ShallowRiverCarver adaptive = adaptive(dimension, 5, 12, DEPTH, cancellation);
        assertAccepted(adaptive);
        assertEquals(before, snapshot(dimension));
        applying[0] = true;

        assertThrows(ScriptingContext.InterruptedException.class, adaptive::apply);

        assertTrue("Cancel only after a real cut write", sawCut[0]);
        assertTrue("Cancel only after a real fill write", sawFill[0]);
        assertEquals("Rollback must include raised/cut heights, water, terrain, biome and layer flags",
                before, snapshot(dimension));
    }

    @Test
    public void protectedAndOldRiverCellsStillRejectWithoutPartialWrites() {
        for (Layer layer : PROTECTED_LAYERS) {
            final Dimension dimension = fixture(true, true);
            dimension.setBitLayerValueAt(layer, 64, 64, true);
            final Snapshot before = snapshot(dimension);
            final ShallowRiverCarver adaptive = adaptive(dimension, 5, 12, DEPTH, null);

            assertFalse("Terrain adaptation cannot override " + layer.getName(), add(adaptive));
            assertFalse(adaptive.getLastRejection().isEmpty());
            final ShallowRiverCarver.Result result = adaptive.apply();

            assertEquals(0, result.paths());
            assertEquals(0, result.changedCells());
            assertEquals(0, result.raisedCells());
            assertEquals(before, snapshot(dimension));
        }
    }

    @Test
    public void anActualHighCliffIsNotForcedThroughTheAdaptiveBudget() {
        final Dimension dimension = flat();
        for (int y = 0; y < 128; y++) {
            for (int x = 62; x <= 65; x++) dimension.setHeightAt(x, y, 109);
        }
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver adaptive = adaptive(dimension, 5, 12, DEPTH, null);
        assertFalse(add(adaptive));
        assertEquals(0, adaptive.apply().changedCells());
        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void adaptationCannotChangeBudgetsAfterAPathWasPlanned() {
        final Dimension dimension = flat();
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = create(dimension, 5, 12, DEPTH, null);
        assertTrue(add(carver));

        assertThrows(IllegalStateException.class, carver::enableTerrainAdaptation);

        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void aUserEditInThePlannedFillAreaInvalidatesTheWholeApply() {
        final Dimension dimension = fixture(true, true);
        final ShallowRiverCarver adaptive = adaptive(dimension, 5, 12, DEPTH, null);
        assertAccepted(adaptive);
        dimension.setHeightAt(49, 64, 98.75f);
        dimension.setTerrainAt(49, 64, Terrain.STONE);
        dimension.setLayerValueAt(Biome.INSTANCE, 49, 64, 3);
        final Snapshot edited = snapshot(dimension);

        assertThrows(IllegalStateException.class, adaptive::apply);

        assertEquals("Adaptive filling must not overwrite newer user edits", edited, snapshot(dimension));
    }

    private static ShallowRiverCarver create(Dimension dimension, double startWidth, double endWidth,
                                             double depth, ScriptProgress progress) {
        return new ShallowRiverCarver(dimension, startWidth, endWidth, depth, true, true, 733L, progress);
    }

    private static ShallowRiverCarver adaptive(Dimension dimension, double startWidth, double endWidth,
                                               double depth, ScriptProgress progress) {
        final ShallowRiverCarver carver = create(dimension, startWidth, endWidth, depth, progress);
        carver.enableTerrainAdaptation();
        return carver;
    }

    private static void assertAccepted(ShallowRiverCarver carver) {
        final boolean accepted = add(carver);
        assertTrue(carver.getLastRejection(), accepted);
    }

    private static boolean add(ShallowRiverCarver carver) {
        final int[] xs = new int[89], ys = new int[89];
        for (int i = 0; i < xs.length; i++) xs[i] = 20 + i;
        Arrays.fill(ys, 64);
        return carver.addPath(xs, ys);
    }

    private static Dimension fixture(boolean ridge, boolean pits) {
        final Dimension dimension = flat();
        if (ridge) {
            for (int y = 0; y < 128; y++) {
                dimension.setHeightAt(63, y, 103);
                dimension.setHeightAt(64, y, 103);
            }
        }
        if (pits) {
            for (int x = 48; x <= 50; x++) {
                for (int y = 63; y <= 65; y++) dimension.setHeightAt(x, y, 98.5f);
            }
            // A low bank patch additionally exercises dry-shoulder raising.
            for (int x = 72; x <= 74; x++) {
                for (int y = 68; y <= 70; y++) dimension.setHeightAt(x, y, 98.5f);
            }
        }
        return dimension;
    }

    private static Dimension flat() {
        final TileFactory factory = TestData.createTileFactory(100);
        final Dimension dimension = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "Adaptive river test", 733L, factory, Dimension.Anchor.NORMAL_DETAIL);
        final Tile tile = factory.createTile(0, 0);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                tile.setHeight(x, y, 100);
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

    private static void assertShallowThreeWide(Dimension dimension, double depth) {
        int previousWater = Integer.MAX_VALUE;
        for (int x = 24; x <= 104; x++) {
            final int water = dimension.getWaterLevelAt(x, 64);
            assertTrue("Downstream water cannot rise at X=" + x, water <= previousWater);
            previousWater = water;
            for (int y = 63; y <= 65; y++) {
                assertEquals("One common section water plane", water, dimension.getWaterLevelAt(x, y));
                assertTrue("Three actual wet blocks, not only half slabs, at " + x + "," + y,
                        water > dimension.getIntHeightAt(x, y));
                assertTrue("More incision must not increase water depth at " + x + "," + y,
                        water - dimension.getHeightAt(x, y) <= depth + EPSILON);
            }
        }
    }

    private static void assertBudgetsAndFootprint(Snapshot before, Dimension dimension, ShallowRiverCarver carver,
                                                ShallowRiverCarver.Result result, double maximumWidth) {
        long raised = 0;
        double maximumCut = 0, maximumFill = 0;
        final double footprintRadius = Math.max(2.5, maximumWidth / 2) + 12;
        for (Cell original : before.cells) {
            final float actual = dimension.getHeightAt(original.x, original.y);
            final double delta = actual - original.height;
            assertTrue("Excessive fill at " + original.x + "," + original.y + ": " + delta,
                    delta <= carver.getMaximumFill() + EPSILON);
            assertTrue("Excessive cut at " + original.x + "," + original.y + ": " + -delta,
                    -delta <= carver.getMaximumCut() + EPSILON);
            if (delta > 0) raised++;
            maximumFill = Math.max(maximumFill, delta);
            maximumCut = Math.max(maximumCut, -delta);
            final double distance = Math.hypot(original.x - Math.max(20, Math.min(108, original.x)), original.y - 64);
            if (distance > footprintRadius) assertEquals("12-block feather limit exceeded", original, cell(dimension, original.x, original.y));
        }
        assertEquals("Fill count must describe actual stored height changes", raised, result.raisedCells());
        assertEquals(maximumFill, result.maximumFill(), EPSILON);
        assertEquals(maximumCut, result.maximumCut(), EPSILON);
    }

    private static Snapshot snapshot(Dimension dimension) {
        final List<Cell> cells = new ArrayList<>(16384);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) cells.add(cell(dimension, x, y));
        }
        return new Snapshot(cells, dimension.getSurfaceSmoothing(), dimension.getTiles().size());
    }

    private static Cell cell(Dimension dimension, int x, int y) {
        int bits = 0;
        for (int i = 0; i < PROTECTED_LAYERS.length; i++) {
            if (dimension.getBitLayerValueAt(PROTECTED_LAYERS[i], x, y)) bits |= 1 << i;
        }
        return new Cell(x, y, dimension.getHeightAt(x, y), dimension.getWaterLevelAt(x, y),
                dimension.getTerrainAt(x, y), dimension.getLayerValueAt(Biome.INSTANCE, x, y), bits,
                dimension.getBitLayerValueAt(Frost.INSTANCE, x, y));
    }

    private record Cell(int x, int y, float height, int water, Terrain terrain, int biome,
                        int protectedBits, boolean frost) {}
    private record Snapshot(List<Cell> cells, Dimension.SurfaceSmoothing smoothing, int tiles) {
        @Override public String toString() { return "Snapshot[cells=" + cells.size() + ",hash=" + cells.hashCode() + "]"; }
    }

    private static final Layer[] PROTECTED_LAYERS = { FloodWithLava.INSTANCE, Void.INSTANCE,
            NotPresent.INSTANCE, NotPresentBlock.INSTANCE, ReadOnly.INSTANCE, River.INSTANCE };
    private static final double DEPTH = 1.10;
    private static final double EPSILON = 1.0 / 256.0;
}
