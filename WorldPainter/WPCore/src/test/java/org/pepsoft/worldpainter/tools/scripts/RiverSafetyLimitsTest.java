package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;

import static org.junit.Assert.*;

/** Malformed scripts must not wrap coordinates or expand an unbounded geometry plan. */
public class RiverSafetyLimitsTest {
    @Test public void coordinateLimitIsRejectedBeforeTheExpandedIntegerLoopCanWrap() {
        for (int x : new int[] { Integer.MAX_VALUE - 8, Integer.MIN_VALUE + 8 }) {
            final Dimension dimension = world(false);
            dimension.addTile(tile(dimension.getTileFactory(), x >> 7));
            final ShallowRiverCarver plan = plan(dimension);
            assertFalse(plan.addPath(new int[] { x, x }, new int[] { 32, 72 }));
            assertTrue(plan.getLastRejection().contains("koordinat"));
            final ShallowRiverRouter router = new ShallowRiverRouter(dimension, plan, 5, 12, 1.10, true, 733, null, null);
            assertFalse(router.findFromSource(x, 32));
            assertEquals(0, plan.apply().changedCells());
            assertEquals(100f, dimension.getHeightAt(x, 32), 0);
        }
    }

    @Test public void excessiveInputPointsAreRejectedBeforeSampling() {
        final Dimension dimension = world(false);
        final ShallowRiverCarver plan = plan(dimension);
        assertFalse(plan.addPath(new int[65537], new int[65537]));
        assertTrue(plan.getLastRejection().contains("nokta"));
        assertEquals(0, plan.apply().changedCells());
    }

    @Test public void manyIndividuallyLegalLongSegmentsCannotAllocateAnUnboundedDensifiedPath() {
        final Dimension dimension = world(false);
        dimension.addTile(tile(dimension.getTileFactory(), 64));
        final ShallowRiverCarver plan = plan(dimension);
        final int[] xs = new int[12], ys = new int[12];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = (i & 1) == 0 ? 20 : 8212;
            ys[i] = 64;
        }
        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> plan.addPath(xs, ys));
        assertTrue(failure.getMessage().contains("örnekleme"));
        assertEquals(0, plan.getAcceptedPaths());
        assertEquals(0, plan.apply().changedCells());
        assertEquals(100f, dimension.getHeightAt(20, 64), 0);
        assertEquals(100f, dimension.getHeightAt(8212, 64), 0);
    }

    @Test public void nonFiniteTerrainIsMissingDataNotAZeroHeightRiverSource() {
        final Dimension dimension = world(true);
        final ShallowRiverCarver plan = plan(dimension);
        assertFalse(plan.addPath(new int[] { 64, 108 }, new int[] { 64, 64 }));
        final ShallowRiverRouter router = new ShallowRiverRouter(dimension, plan, 5, 12, 1.10, true, 733, null, null);
        assertFalse(router.findFromSource(64, 64));
        assertEquals(0, plan.apply().changedCells());
        assertEquals(100f, dimension.getTile(0, 0).getHeight(64, 64), 0);
    }

    private static ShallowRiverCarver plan(Dimension dimension) {
        final ShallowRiverCarver result = new ShallowRiverCarver(dimension, 5, 12, 1.10, true, true, 733, null);
        result.enableTerrainPreservation();
        return result;
    }

    private static Dimension world(boolean nonFinite) {
        final TileFactory factory = TestData.createTileFactory(100);
        final Dimension result = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "River safety", 733, factory, Dimension.Anchor.NORMAL_DETAIL) {
            @Override public float getHeightAt(int x, int y) {
                return nonFinite && x == 64 ? Float.NaN : super.getHeightAt(x, y);
            }
        };
        result.addTile(tile(factory, 0));
        return result;
    }

    private static Tile tile(TileFactory factory, int tileX) {
        final Tile result = factory.createTile(tileX, 0);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            result.setHeight(x, y, 100);
            result.setWaterLevel(x, y, 0);
            result.setTerrain(x, y, Terrain.GRASS);
        }
        return result;
    }
}
