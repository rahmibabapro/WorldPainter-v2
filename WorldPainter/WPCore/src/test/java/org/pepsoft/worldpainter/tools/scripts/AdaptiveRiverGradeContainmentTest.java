package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.ReadOnly;

import static org.junit.Assert.*;

/** Sloping fitted channels need real dry-bank closure at rasterised water steps. */
public class AdaptiveRiverGradeContainmentTest {
    @Test
    public void connectedDownstreamStepsAndTheirDryBanksAreFittedWithinOriginalBudgets() {
        final Dimension dimension = slope();
        final long before = fingerprint(dimension);
        final float[][] heights = heights(dimension);
        final ShallowRiverCarver carver = carver(dimension, null);

        final boolean accepted = add(carver);
        assertTrue(carver.getLastRejection(), accepted);
        assertEquals("Planning must not touch the world", before, fingerprint(dimension));
        final ShallowRiverCarver.Result result = carver.apply();

        assertEquals(1, result.paths());
        assertTrue(result.maximumFill() <= 2.0);
        assertTrue(result.maximumCut() <= 4.85 + 1.0 / 256);
        int drops = 0, previous = dimension.getWaterLevelAt(24, 64), raisedDry = 0;
        for (int x = 24; x <= 104; x++) {
            final int water = dimension.getWaterLevelAt(x, 64);
            assertTrue("Water must descend monotonically", water <= previous);
            assertTrue("A smooth slope must not become a waterfall", previous - water <= 1);
            if (water < previous) drops++;
            previous = water;
            for (int y = 63; y <= 65; y++) {
                assertEquals("One cross-section water level", water, dimension.getWaterLevelAt(x, y));
                assertTrue(water > dimension.getIntHeightAt(x, y));
                assertTrue(water - dimension.getHeightAt(x, y) <= 1.10 + 1.0 / 256);
            }
        }
        assertTrue("Fixture must actually exercise downstream steps", drops >= 4);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                final double delta = dimension.getHeightAt(x, y) - heights[y][x];
                assertTrue(delta <= 2.0);
                assertTrue(delta >= -4.85 - 1.0 / 256);
                if (dimension.getWaterLevelAt(x, y) == 0 && delta > 0.05) raisedDry++;
                if (x < 20 || x > 108 || Math.abs(y - 64) > 18) {
                    assertEquals("Local bank fitting must not escape the footprint", heights[y][x], dimension.getHeightAt(x, y), 0);
                }
                if (dimension.getWaterLevelAt(x, y) > dimension.getIntHeightAt(x, y)
                        && dimension.getWaterLevelAt(x, y) > Math.round(heights[y][x])) {
                    for (int[] offset : NEIGHBOURS) {
                        final int nx = x + offset[0], ny = y + offset[1];
                        if (dimension.getWaterLevelAt(nx, ny) <= dimension.getIntHeightAt(nx, ny)) {
                            assertTrue("A fitted water step must have a real dry bank at " + nx + "," + ny,
                                    dimension.getIntHeightAt(nx, ny) >= dimension.getWaterLevelAt(x, y));
                        }
                    }
                }
            }
        }
        assertTrue("The low dry shore must actually be filled", raisedDry > 0);
    }

    @Test
    public void bankBeyondFillBudgetIsRejectedWithoutAnyWrites() {
        final Dimension dimension = slope();
        // An open side trench cannot be repaired within two blocks of ORIGINAL
        // terrain. It must not be accepted merely because it has a pending bank.
        for (int x = 44; x <= 74; x++) {
            for (int y = 66; y <= 80; y++) dimension.setHeightAt(x, y, 88);
        }
        final long before = fingerprint(dimension);
        final ShallowRiverCarver carver = carver(dimension, null);
        assertFalse(add(carver));
        assertFalse(carver.getLastRejection().isEmpty());
        assertEquals(0, carver.apply().changedCells());
        assertEquals(before, fingerprint(dimension));
    }

    @Test
    public void aProtectedHoleIsNotFilledToMakeTheSlopingChannelPass() {
        final Dimension dimension = slope();
        dimension.setBitLayerValueAt(ReadOnly.INSTANCE, 56, 64, true);
        final long before = fingerprint(dimension);
        final ShallowRiverCarver carver = carver(dimension, null);
        assertFalse(add(carver));
        assertEquals(0, carver.apply().changedCells());
        assertEquals(before, fingerprint(dimension));
        assertTrue(dimension.getBitLayerValueAt(ReadOnly.INSTANCE, 56, 64));
    }

    @Test
    public void cancellationRestoresBankRepairsAlongWithTheShallowBed() {
        final Dimension dimension = slope();
        final long before = fingerprint(dimension);
        final boolean[] applying = { false };
        final ScriptProgress progress = new ScriptProgress(null, null) {
            @Override public void checkForCancel() { }
            @Override public void setProgress(double progress) {
                if (applying[0] && progress > 0) throw new ScriptingContext.InterruptedException();
            }
        };
        final ShallowRiverCarver carver = carver(dimension, progress);
        final boolean accepted = add(carver);
        assertTrue(carver.getLastRejection(), accepted);
        applying[0] = true;
        assertThrows(ScriptingContext.InterruptedException.class, carver::apply);
        assertEquals(before, fingerprint(dimension));
    }

    private static boolean add(ShallowRiverCarver carver) {
        return carver.addPath(new int[] { 20, 108 }, new int[] { 64, 64 });
    }

    private static ShallowRiverCarver carver(Dimension dimension, ScriptProgress progress) {
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension, 5, 12, 1.10, true, true, 733, progress);
        carver.enableTerrainAdaptation();
        return carver;
    }

    private static Dimension slope() {
        final TileFactory factory = TestData.createTileFactory(100);
        final Dimension dimension = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "Adaptive river steps", 733, factory, Dimension.Anchor.NORMAL_DETAIL);
        final Tile tile = factory.createTile(0, 0);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                final double crossSlope = 0.09 * (y - 64) * Math.min(1, Math.max(0, (100 - x) / 8.0));
                tile.setHeight(x, y, (float) (101 - 0.10 * Math.min(x, 92) + crossSlope));
                tile.setWaterLevel(x, y, 0);
                tile.setTerrain(x, y, Terrain.GRASS);
            }
        }
        dimension.addTile(tile);
        return dimension;
    }

    private static float[][] heights(Dimension dimension) {
        final float[][] result = new float[128][128];
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) result[y][x] = dimension.getHeightAt(x, y);
        return result;
    }

    private static long fingerprint(Dimension dimension) {
        long hash = 1;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                hash = 31 * hash + Float.floatToIntBits(dimension.getHeightAt(x, y));
                hash = 31 * hash + dimension.getWaterLevelAt(x, y);
                hash = 31 * hash + dimension.getTerrainAt(x, y).ordinal();
            }
        }
        return hash;
    }

    private static final int[][] NEIGHBOURS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
}
