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
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.River;
import org.pepsoft.worldpainter.layers.Void;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Exercises the shared carver against real WorldPainter height, water and terrain
 * storage. In particular, wetness uses the same rounded ground level as export,
 * not floor(height), which could hide dry gaps in a floating-point river plan.
 */
public class ShallowRiverCarverTest {
    @Test
    public void planningIsReadOnlyUntilApply() {
        final Dimension dimension = flat(100);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = carver(dimension, true, true);

        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));

        assertEquals("Planning must not carve, flood or repaint the user's world", before, snapshot(dimension));
        final ShallowRiverCarver.Result result = carver.apply();
        assertEquals(1, result.paths());
        assertEquals(0, result.rejectedPaths());
        assertTrue(result.changedCells() > 0);
        assertNotEquals(before, snapshot(dimension));
    }

    @Test
    public void flatNaturalRiverIsShallowAndContinuouslyWetAtExportPrecision() {
        final Dimension dimension = flat(100);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        final ShallowRiverCarver.Result result = carver.apply();

        for (int x = 24; x <= 104; x++) {
            assertWet(dimension, x, 64);
            assertTrue("No raised water column at " + x, dimension.getWaterLevelAt(x, 64) <= 100);
            assertTrue("Blueprint-style channel must remain shallow at " + x,
                    dimension.getWaterLevelAt(x, 64) - dimension.getHeightAt(x, 64) <= DEPTH + 0.75);
        }
        assertMaximumCut(dimension, 100, DEPTH + 0.75);
        assertTrue("Reported cut is excavation from original terrain, not only water depth",
                result.maximumCut() <= DEPTH + 0.75 + HEIGHT_EPSILON);
    }

    @Test
    public void fractionalGroundStillProducesWetCellsWithoutDeepTrenching() {
        final Dimension dimension = flat(100);
        setAllHeights(dimension, (x, y) -> 100.49f);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        carver.apply();

        for (int x = 24; x <= 104; x++) {
            assertWet(dimension, x, 64);
        }
        assertMaximumCut(dimension, 100.49f, DEPTH + 0.75);
    }

    @Test
    public void smoothedBanksHaveNoMultiBlockVerticalWallOnFlatTerrain() {
        final Dimension dimension = flat(100);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        carver.apply();

        for (int x = 28; x <= 100; x++) {
            for (int y = 48; y < 80; y++) {
                final double step = Math.abs(dimension.getHeightAt(x, y + 1) - dimension.getHeightAt(x, y));
                assertTrue("Bank wall at " + x + "," + y + " has step " + step, step <= 1.01);
            }
        }
    }

    @Test
    public void dryGrassOutsideTheBankCorridorIsUntouched() {
        final Dimension dimension = flat(100);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        carver.apply();

        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                if (y < 48 || y > 80 || x < 4 || x > 124) {
                    assertEquals(100f, dimension.getHeightAt(x, y), 0f);
                    assertEquals(0, dimension.getWaterLevelAt(x, y));
                    assertEquals(Terrain.GRASS, dimension.getTerrainAt(x, y));
                }
            }
        }
    }

    @Test
    public void graniteDetailSuppliesStairCapableBedWithoutGlobalSmoothingChanges() {
        final Dimension dimension = flat(100);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.NONE);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        final ShallowRiverCarver.Result result = carver.apply();

        assertTrue(result.graniteCells() > 0);
        assertEquals(Terrain.GRANITE, dimension.getTerrainAt(64, 64));
        assertEquals(Terrain.GRASS, dimension.getTerrainAt(64, 32));
        assertEquals("A river operation must not silently change the world's export mode",
                Dimension.SurfaceSmoothing.NONE, dimension.getSurfaceSmoothing());
    }

    @Test
    public void disablingGraniteExposesSoilOnlyInWetBedAndNeverIntroducesStairMaterial() {
        final Dimension dimension = flat(100);
        final ShallowRiverCarver carver = carver(dimension, true, false);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        final ShallowRiverCarver.Result result = carver.apply();

        assertEquals(0, result.graniteCells());
        for (Tile tile : dimension.getTiles()) {
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    assertEquals(tile.getWaterLevel(x, y) > Math.round(tile.getHeight(x, y))
                            ? Terrain.DIRT : Terrain.GRASS, tile.getTerrain(x, y));
                }
            }
        }
    }

    @Test
    public void gentleDownhillPathKeepsWaterMonotoneAndLimitsActualExcavation() {
        final Dimension dimension = flat(100);
        setAllHeights(dimension, (x, y) -> (float) (100.0 - x * 0.04));
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        carver.apply();

        int previousWater = Integer.MAX_VALUE;
        for (int x = 24; x <= 104; x++) {
            assertWet(dimension, x, 64);
            final int water = dimension.getWaterLevelAt(x, 64);
            assertTrue("Water cannot climb along the downstream path", water <= previousWater);
            assertTrue("Gentle terrain must not turn into multi-block waterfalls",
                    previousWater == Integer.MAX_VALUE || previousWater - water <= 1);
            previousWater = water;
        }
        assertMaximumCut(before, dimension, DEPTH + 0.75);
    }

    @Test
    public void sparseDiagonalWaypointsStayConnectedAcrossZeroAndTileBoundaries() {
        final Dimension dimension = terrain(-1, 1, -1, 1, 100);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(new int[] { -100, 0, 160 }, new int[] { -100, 0, 160 }));
        carver.apply();

        for (int position = -96; position <= 156; position++) {
            assertWet(dimension, position, position);
            // Diagonal-only contact would still leave an orthogonal gap in water.
            assertTrue("Diagonal river must connect edge-to-edge at " + position,
                    isWet(dimension, position + 1, position) || isWet(dimension, position, position + 1));
        }
        assertEquals("Planning may not create extra tiles", 9, dimension.getTiles().size());
        assertMaximumCut(dimension, 100, DEPTH + 0.75);
    }

    @Test
    public void sameSeedAndInputProduceIdenticalTerrainWaterAndSummary() {
        final Dimension first = flat(100), second = flat(100);
        final ShallowRiverCarver firstCarver = carver(first, true, true);
        final ShallowRiverCarver secondCarver = carver(second, true, true);
        assertTrue(firstCarver.addPath(horizontalXs(), horizontalYs()));
        assertTrue(secondCarver.addPath(horizontalXs(), horizontalYs()));

        assertEquals(firstCarver.apply(), secondCarver.apply());
        assertEquals(snapshot(first), snapshot(second));
    }

    @Test
    public void overlappingPathsDoNotSubtractDepthTwice() {
        final Dimension dimension = flat(100);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        assertTrue(carver.addPath(new int[] { 64, 64 }, new int[] { 20, 108 }));

        final ShallowRiverCarver.Result result = carver.apply();

        assertEquals(3, result.paths());
        assertWet(dimension, 64, 64);
        assertMaximumCut(dimension, 100, DEPTH + 0.75);
        assertTrue(result.maximumCut() <= DEPTH + 0.75 + HEIGHT_EPSILON);
    }

    @Test
    public void changedGroundInvalidatesThePlanBeforeAnyApplyMutation() {
        final Dimension dimension = flat(100);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        dimension.setHeightAt(64, 64, 101f);
        final Snapshot afterUserEdit = snapshot(dimension);

        assertThrows(IllegalStateException.class, carver::apply);

        assertEquals("A stale plan must not overwrite the user's newer changes", afterUserEdit, snapshot(dimension));
    }

    @Test
    public void changedTerrainPaletteInvalidatesThePlanBeforeAnyApplyMutation() {
        final Dimension dimension = flat(100);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        dimension.setTerrainAt(64, 64, Terrain.STONE);
        final Snapshot afterUserEdit = snapshot(dimension);

        assertThrows(IllegalStateException.class, carver::apply);

        assertEquals(afterUserEdit, snapshot(dimension));
    }

    @Test
    public void changedBiomeInvalidatesThePlanBeforeAnyApplyMutation() {
        final Dimension dimension = flat(100);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        dimension.setLayerValueAt(Biome.INSTANCE, 64, 64, 2);
        final Snapshot afterUserEdit = snapshot(dimension);

        assertThrows(IllegalStateException.class, carver::apply);

        assertEquals(afterUserEdit, snapshot(dimension));
    }

    @Test
    public void cancellationAfterWritingRestoresHeightWaterTerrainAndBiome() {
        final Dimension dimension = flat(100);
        final Snapshot before = snapshot(dimension);
        final boolean[] applying = { false };
        final boolean[] sawMutation = { false };
        final ScriptProgress cancellation = new ScriptProgress(null, null) {
            @Override
            public void checkForCancel() {
                if (applying[0] && dimension.getHeightAt(20, 64) < 100f) {
                    sawMutation[0] = true;
                    throw new ScriptingContext.InterruptedException();
                }
            }
        };
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                5, 12, DEPTH, true, true, 733L, cancellation);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        applying[0] = true;

        assertThrows(ScriptingContext.InterruptedException.class, carver::apply);

        assertTrue("Cancellation must occur after real terrain writes", sawMutation[0]);
        assertEquals("A cancelled apply must restore every changed field", before, snapshot(dimension));
    }

    @Test
    public void newlyProtectedCellsInvalidateTheWholePlanBeforeApplyMutation() {
        final Layer[] protectedLayers = { FloodWithLava.INSTANCE, Void.INSTANCE, NotPresent.INSTANCE,
                NotPresentBlock.INSTANCE, ReadOnly.INSTANCE, River.INSTANCE };
        for (Layer layer : protectedLayers) {
            final Dimension dimension = flat(100);
            final ShallowRiverCarver carver = carver(dimension, true, true);
            assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
            dimension.setBitLayerValueAt(layer, 64, 64, true);
            final Snapshot afterUserEdit = snapshot(dimension);

            assertThrows("Plan must revalidate " + layer.getName(), IllegalStateException.class, carver::apply);

            assertEquals(afterUserEdit, snapshot(dimension));
        }
    }

    @Test
    public void actualStreamNaturalAndWideDepthsRemainWetWithoutMultiBlockExcavation() {
        for (double depth : new double[] { 0.85, 1.10, 1.40 }) {
            for (float height : new float[] { 100f, 100.49f }) {
                final Dimension dimension = flat(100);
                setAllHeights(dimension, (x, y) -> height);
                final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                        5, 12, depth, true, true, 733L, null);
                assertTrue(carver.addPath(horizontalXs(), horizontalYs()));

                final ShallowRiverCarver.Result result = carver.apply();

                for (int x = 24; x <= 104; x++) {
                    assertWet(dimension, x, 64);
                }
                assertMaximumCut(dimension, height, depth + 0.75);
                assertTrue(result.maximumCut() <= depth + 0.75 + HEIGHT_EPSILON);
            }
        }
    }

    @Test
    public void narrowAndNaturalProfilesGuaranteeThreeWetBlocksHorizontallyAndVertically() {
        for (double[] profile : new double[][] { { 3, 3, 0.85 }, { 5, 12, 1.10 } }) {
            for (boolean vertical : new boolean[] { false, true }) {
                for (boolean downhill : new boolean[] { false, true }) {
                    final Dimension dimension = flat(100);
                    setAllHeights(dimension, (x, y) -> (float) (100.20
                            - (downhill ? (vertical ? y : x) * 0.025 : 0)));
                    final Snapshot before = snapshot(dimension);
                    final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                            profile[0], profile[1], profile[2], true, true, 733L, null);
                    final boolean accepted = carver.addPath(vertical ? horizontalYs() : horizontalXs(),
                            vertical ? horizontalXs() : horizontalYs());
                    assertTrue("A gentle, unobstructed three-wide river should be feasible: " + carver.getLastRejection(), accepted);
                    carver.apply();

                    for (int position = 24; position <= 104; position++) {
                        final int centreX = vertical ? 64 : position, centreY = vertical ? position : 64;
                        final int expectedWater = dimension.getWaterLevelAt(centreX, centreY);
                        for (int offset = -1; offset <= 1; offset++) {
                            final int x = centreX + (vertical ? offset : 0);
                            final int y = centreY + (vertical ? 0 : offset);
                            assertWet(dimension, x, y);
                            assertEquals("A river section must not have separate lateral water heights",
                                    expectedWater, dimension.getWaterLevelAt(x, y));
                        }
                    }
                    assertMaximumCut(before, dimension, profile[2] + 0.75);
                }
            }
        }
    }

    @Test
    public void diagonalProfilesGuaranteeThreeWetNormalSamplesAcrossTileAndZeroBoundaries() {
        for (double[] profile : new double[][] { { 3, 3, 0.85 }, { 5, 12, 1.10 } }) {
            for (boolean downhill : new boolean[] { false, true }) {
                final Dimension dimension = terrain(-1, 1, -1, 1, 100);
                setAllHeights(dimension, (x, y) -> (float) (100.20 - (downhill ? (x + y) * 0.0125 : 0)));
                final Snapshot before = snapshot(dimension);
                final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                        profile[0], profile[1], profile[2], true, true, 733L, null);
                assertTrue(carver.addPath(new int[] { -100, 0, 160 }, new int[] { -100, 0, 160 }));
                carver.apply();

                for (int position = -96; position <= 156; position++) {
                    final int expectedWater = dimension.getWaterLevelAt(position, position);
                    for (int offset = -1; offset <= 1; offset++) {
                        assertWet(dimension, position + offset, position - offset);
                        assertEquals("Diagonal cross-section must retain one water level",
                                expectedWater, dimension.getWaterLevelAt(position + offset, position - offset));
                    }
                    // No corner-only joins: both stair-stepped raster edges stay wet.
                    assertWet(dimension, position + 1, position);
                    assertWet(dimension, position, position + 1);
                }
                assertMaximumCut(before, dimension, profile[2] + 0.75);
                assertEquals(9, dimension.getTiles().size());
            }
        }
    }

    @Test
    public void bankWaterNeverUsesALateralCellsOwnHeightOrLeaksIntoTheDryShoulder() {
        final Dimension dimension = flat(100);
        setAllHeights(dimension, (x, y) -> (float) (100.30 - x * 0.025
                + Math.min(0.24, Math.abs(y - 64) * 0.04)));
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                5, 5, 1.10, true, true, 733L, null);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        carver.apply();

        for (int x = 24; x <= 104; x++) {
            final int sectionWater = dimension.getWaterLevelAt(x, 64);
            for (int y = 48; y <= 80; y++) {
                if (Math.abs(y - 64) <= 2) {
                    if (dimension.getWaterLevelAt(x, y) != 0) {
                        assertEquals("No separate water step may be painted along a bank at " + x + "," + y,
                                sectionWater, dimension.getWaterLevelAt(x, y));
                    }
                } else {
                    assertEquals("The dry shoulder must keep its original water plane at " + x + "," + y,
                            0, dimension.getWaterLevelAt(x, y));
                    assertFalse(isWet(dimension, x, y));
                }
            }
        }
        assertMaximumCut(before, dimension, 1.85);
    }

    @Test
    public void localFlattenReducesBothBanksWithoutMovingTerrainOutsideSixteenBlocks() {
        final Dimension smoothed = flat(100), unSmoothed = flat(100);
        final HeightFunction lowBankRidge = (x, y) -> Math.abs(y - 64) >= 5 ? 101.5f : 100f;
        setAllHeights(smoothed, lowBankRidge);
        setAllHeights(unSmoothed, lowBankRidge);
        final Snapshot before = snapshot(smoothed);
        final ShallowRiverCarver smoothCarver = new ShallowRiverCarver(smoothed,
                5, 5, 1.10, true, true, 733L, null);
        final ShallowRiverCarver plainCarver = new ShallowRiverCarver(unSmoothed,
                5, 5, 1.10, false, true, 733L, null);
        assertTrue(smoothCarver.addPath(horizontalXs(), horizontalYs()));
        assertTrue(plainCarver.addPath(horizontalXs(), horizontalYs()));
        smoothCarver.apply();
        plainCarver.apply();

        for (int x = 32; x <= 96; x++) {
            for (int sign : new int[] { -1, 1 }) {
                final int outerBank = 64 + sign * 6;
                assertTrue("Flatten must actually soften the outer " + (sign < 0 ? "north" : "south") + " bank",
                        smoothed.getHeightAt(x, outerBank) < unSmoothed.getHeightAt(x, outerBank) - 0.05);
            }
            for (int y = 48; y < 80; y++) {
                assertTrue("The low bank ridge must not remain a multi-block wall at " + x + "," + y,
                        Math.abs(smoothed.getHeightAt(x, y + 1) - smoothed.getHeightAt(x, y)) <= 1.01);
            }
        }
        for (Cell original : before.cells()) {
            final double outsideDistance = Math.hypot(original.x() - Math.max(20, Math.min(108, original.x())),
                    original.y() - 64);
            if (outsideDistance > 16.0) {
                assertEquals(original.height(), smoothed.getHeightAt(original.x(), original.y()), 0f);
                assertEquals(original.water(), smoothed.getWaterLevelAt(original.x(), original.y()));
                assertEquals(original.terrain(), smoothed.getTerrainAt(original.x(), original.y()));
                assertEquals(original.biome(), smoothed.getLayerValueAt(Biome.INSTANCE, original.x(), original.y()));
            }
        }
        assertMaximumCut(before, smoothed, 1.85);
    }

    @Test
    public void wideningProfileDoesNotLeaveDisconnectedShoreWaterPockets() {
        final Dimension dimension = flat(100);
        setAllHeights(dimension, (x, y) -> (float) (100.20 - x * 0.02
                + Math.min(0.22, Math.abs(y - 64) * 0.022)));
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                3, 12, 1.10, true, true, 733L, null);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        carver.apply();

        for (int x = 24; x <= 104; x++) {
            final int sectionWater = dimension.getWaterLevelAt(x, 64);
            for (int sign : new int[] { -1, 1 }) {
                boolean dryBankReached = false;
                for (int distance = 0; distance <= 16; distance++) {
                    final int y = 64 + sign * distance;
                    final boolean wet = isWet(dimension, x, y);
                    if (wet) {
                        assertFalse("An isolated shore-water cell reappeared beyond the dry bank at " + x + "," + y,
                                dryBankReached);
                        assertEquals("The bank's water must share the channel's section level",
                                sectionWater, dimension.getWaterLevelAt(x, y));
                    } else {
                        dryBankReached = true;
                    }
                }
            }
        }
    }

    @Test
    public void extendedLocalFlattenDoesNotModifyProtectedCellsOutsideTheWetCorridor() {
        final Dimension dimension = flat(100);
        setAllHeights(dimension, (x, y) -> Math.abs(y - 64) >= 5 ? 101.5f : 100f);
        final Layer[] layers = { Void.INSTANCE, FloodWithLava.INSTANCE, NotPresentBlock.INSTANCE };
        for (int index = 0; index < layers.length; index++) {
            dimension.setBitLayerValueAt(layers[index], 48 + index * 16, 72, true);
        }
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                5, 5, 1.10, true, true, 733L, null);
        assertTrue("A protected outer bank need not block an otherwise safe inner route",
                carver.addPath(horizontalXs(), horizontalYs()));
        carver.apply();

        for (int index = 0; index < layers.length; index++) {
            final int x = 48 + index * 16;
            assertEquals(101.5f, dimension.getHeightAt(x, 72), 0f);
            assertEquals(0, dimension.getWaterLevelAt(x, 72));
            assertEquals(Terrain.GRASS, dimension.getTerrainAt(x, 72));
            assertTrue(dimension.getBitLayerValueAt(layers[index], x, 72));
        }
        for (int x = 24; x <= 104; x++) {
            assertWet(dimension, x, 64);
        }
    }

    @Test
    public void appliedPlanCannotBeAppliedOrExtendedAgain() {
        final Dimension dimension = flat(100);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        carver.apply();
        final Snapshot afterApply = snapshot(dimension);

        assertThrows(IllegalStateException.class, carver::apply);
        assertThrows(IllegalStateException.class, () -> carver.addPath(horizontalXs(), horizontalYs()));
        assertEquals(afterApply, snapshot(dimension));
    }

    @Test
    public void malformedPathsAreRejectedWithoutChangingWorld() {
        final Dimension dimension = flat(100);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = carver(dimension, true, true);

        assertFalse(carver.addPath(null, null));
        assertFalse(carver.addPath(new int[] { 20, 30 }, new int[] { 64 }));
        assertFalse(carver.addPath(new int[] { 20 }, new int[] { 64 }));
        assertFalse(carver.addPath(new int[] { 20, 20 }, new int[] { 64, 64 }));

        final ShallowRiverCarver.Result result = carver.apply();
        assertEquals(0, result.paths());
        assertEquals(4, result.rejectedPaths());
        assertEquals(0, result.changedCells());
        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void unsafeGeometrySettingsAreRejectedBeforePlanning() {
        final Dimension dimension = flat(100);
        final Snapshot before = snapshot(dimension);
        final double[][] badGeometry = {
                { Double.NaN, 12, DEPTH }, { 5, Double.POSITIVE_INFINITY, DEPTH },
                { 5, 12, Double.NaN }, { 0, 12, DEPTH }, { 8, 4, DEPTH },
                { 5, 65, DEPTH }, { 5, 12, 0.5 }, { 5, 12, 6 }
        };
        for (double[] geometry : badGeometry) {
            assertThrows(IllegalArgumentException.class, () -> new ShallowRiverCarver(dimension,
                    geometry[0], geometry[1], geometry[2], true, true, 733L, null));
        }
        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void sharpHumpIsRejectedWithoutCuttingATrenchOrApplyingPartialPath() {
        final Dimension dimension = flat(100);
        setAllHeights(dimension, (x, y) -> x >= 56 && x <= 72 ? 112f : 100f);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = carver(dimension, true, true);

        assertFalse("A +12 block ridge is not a shallow route", carver.addPath(horizontalXs(), horizontalYs()));
        assertEquals(before, snapshot(dimension));
        final ShallowRiverCarver.Result result = carver.apply();
        assertEquals(0, result.paths());
        assertEquals(1, result.rejectedPaths());
        assertEquals(0, result.changedCells());
        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void rejectedPathDoesNotDiscardAnEarlierAcceptedPath() {
        final Dimension dimension = flat(100);
        dimension.setBitLayerValueAt(River.INSTANCE, 64, 96, true);
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        final int[] rejectedY = new int[horizontalXs().length];
        java.util.Arrays.fill(rejectedY, 96);
        assertFalse(carver.addPath(horizontalXs(), rejectedY));
        final ShallowRiverCarver.Result result = carver.apply();

        assertEquals(1, result.paths());
        assertEquals(1, result.rejectedPaths());
        assertWet(dimension, 64, 64);
        assertEquals(100f, dimension.getHeightAt(64, 96), 0f);
        assertEquals(0, dimension.getWaterLevelAt(64, 96));
        assertTrue(dimension.getBitLayerValueAt(River.INSTANCE, 64, 96));
    }

    @Test
    public void pathThroughProtectedCellsIsRejectedWithoutChangingTheirLayers() {
        final Layer[] protectedLayers = {
                FloodWithLava.INSTANCE, Void.INSTANCE, NotPresent.INSTANCE,
                NotPresentBlock.INSTANCE, ReadOnly.INSTANCE, River.INSTANCE
        };
        for (Layer layer : protectedLayers) {
            final Dimension dimension = flat(100);
            dimension.setBitLayerValueAt(layer, 64, 64, true);
            final Snapshot before = snapshot(dimension);
            final ShallowRiverCarver carver = carver(dimension, true, true);

            assertFalse("Path must not overwrite " + layer.getName(), carver.addPath(horizontalXs(), horizontalYs()));
            assertEquals(0, carver.apply().changedCells());
            assertEquals("Protected path mutated for " + layer.getName(), before, snapshot(dimension));
        }
    }

    @Test
    public void obliqueWetCoreCannotSkipProtectedRasterCellsBetweenCrossSectionSamples() {
        for (Layer layer : new Layer[] { Void.INSTANCE, FloodWithLava.INSTANCE, NotPresentBlock.INSTANCE }) {
            final Dimension dimension = flat(100);
            // For (20,20)->(25,26), this cell is only 0.512 blocks
            // from the centreline but is missed by every sampled normal.
            dimension.setBitLayerValueAt(layer, 24, 24, true);
            final Snapshot before = snapshot(dimension);
            final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                    3, 3, 0.85, true, true, 733L, null);

            assertFalse("An oblique channel must not silently leave " + layer.getName() + " inside its wet core",
                    carver.addPath(new int[] { 20, 25 }, new int[] { 20, 26 }));
            assertEquals(before, snapshot(dimension));
            assertEquals(0, carver.apply().changedCells());
            assertEquals(before, snapshot(dimension));
        }
    }

    @Test
    public void protectedCellBeyondTheFinalClippedCapDoesNotRejectAnOtherwiseSafeRoute() {
        final Dimension dimension = flat(100);
        dimension.setBitLayerValueAt(Void.INSTANCE, 81, 64, true);
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                3, 3, 0.85, true, true, 733L, null);

        final boolean accepted = carver.addPath(new int[] { 20, 80 }, new int[] { 64, 64 });

        assertTrue("Only the winning, unclipped core may reject a protected cell: " + carver.getLastRejection(), accepted);
        carver.apply();
        assertWet(dimension, 80, 64);
        assertEquals(100f, dimension.getHeightAt(81, 64), 0f);
        assertEquals(0, dimension.getWaterLevelAt(81, 64));
        assertEquals(Terrain.GRASS, dimension.getTerrainAt(81, 64));
        assertTrue(dimension.getBitLayerValueAt(Void.INSTANCE, 81, 64));
    }

    @Test
    public void terminalLakeKeepsItsExistingBedMaterialAndWaterLevel() {
        final Dimension dimension = flat(100);
        for (int x = 98; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                dimension.setHeightAt(x, y, 98f);
                dimension.setTerrainAt(x, y, Terrain.SAND);
                dimension.setWaterLevelAt(x, y, 100);
            }
        }
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue("An existing lake is a valid outlet", carver.addPath(horizontalXs(), horizontalYs()));
        carver.apply();

        for (int x = 98; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                assertEquals(98f, dimension.getHeightAt(x, y), 0f);
                assertEquals(100, dimension.getWaterLevelAt(x, y));
                assertEquals(Terrain.SAND, dimension.getTerrainAt(x, y));
            }
        }
        assertWet(dimension, 97, 64);
        assertWet(dimension, 98, 64);
    }

    @Test
    public void changedLakeOutletInvalidatesThePlanEvenThoughLakeHasNoCarvingProposal() {
        final Dimension dimension = flat(100);
        for (int x = 98; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                dimension.setHeightAt(x, y, 98f);
                dimension.setTerrainAt(x, y, Terrain.SAND);
                dimension.setWaterLevelAt(x, y, 100);
            }
        }
        final ShallowRiverCarver carver = carver(dimension, true, true);
        assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
        dimension.setWaterLevelAt(100, 64, 102);
        final Snapshot afterUserEdit = snapshot(dimension);

        assertThrows(IllegalStateException.class, carver::apply);

        assertEquals(afterUserEdit, snapshot(dimension));
    }

    @Test
    public void enclosedShallowDepressionUsesTheLakesActualLevelInsteadOfBuryingTheMouth() {
        final Dimension dimension = coastalOutletDimension(100, false);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                5, 12, 1.10, true, true, 733L, null);

        final boolean accepted = carver.addPath(horizontalXs(), horizontalYs());

        assertTrue("An enclosed shallow depression can safely share the receiving lake level: "
                + carver.getLastRejection(), accepted);
        assertEquals("Outlet planning must stay read-only", before, snapshot(dimension));
        final ShallowRiverCarver.Result result = carver.apply();

        int previousWater = Integer.MAX_VALUE;
        for (int x = 20; x <= 108; x++) {
            assertWet(dimension, x, 64);
            final int water = dimension.getWaterLevelAt(x, 64);
            assertTrue("The river cannot flow uphill into its lake at x=" + x, water <= previousWater);
            assertTrue("No channel section may sink below its connected receiving lake", water >= 100);
            previousWater = water;
            if (x < 98) {
                assertTrue("Backwater correction must not create a deep submerged trench at x=" + x,
                        water - dimension.getHeightAt(x, 64) <= 1.10 + HEIGHT_EPSILON);
            }
        }
        for (int x = 60; x < 98; x++) {
            for (int offset = -1; offset <= 1; offset++) {
                assertWet(dimension, x, 64 + offset);
                assertEquals("The last land sections must join the lake at its real water height",
                        100, dimension.getWaterLevelAt(x, 64 + offset));
            }
        }
        assertExistingLakeUnchanged(before, dimension, 98);
        assertMaximumCut(before, dimension, 1.85);
        assertTrue(result.maximumCut() <= 1.85 + HEIGHT_EPSILON);
    }

    @Test
    public void receivingLakeHigherThanItsContainingBanksRejectsWithoutPartialCarving() {
        final Dimension dimension = coastalOutletDimension(102, false);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                5, 12, 1.10, true, true, 733L, null);

        assertFalse("A high lake must not flood land which cannot contain its level",
                carver.addPath(horizontalXs(), horizontalYs()));
        assertEquals(before, snapshot(dimension));
        final ShallowRiverCarver.Result result = carver.apply();

        assertEquals(0, result.paths());
        assertEquals(1, result.rejectedPaths());
        assertEquals(0, result.changedCells());
        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void lakeBackwaterCannotSpillAcrossAnOpenLowOuterBank() {
        final Dimension dimension = coastalOutletDimension(100, true);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                5, 12, 1.10, true, true, 733L, null);

        assertFalse("Backwater needs enclosing banks, not just a sufficiently low channel floor",
                carver.addPath(horizontalXs(), horizontalYs()));
        assertEquals(before, snapshot(dimension));
        assertEquals(0, carver.apply().changedCells());
        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void cancellingTheLakeAdjustedPlanRestoresItsLandAndKeepsTheLakeUnchanged() {
        final Dimension dimension = coastalOutletDimension(100, false);
        final Snapshot before = snapshot(dimension);
        final boolean[] applying = { false }, observedWrite = { false };
        final ScriptProgress cancellation = new ScriptProgress(null, null) {
            @Override
            public void checkForCancel() {
                if (applying[0] && dimension.getWaterLevelAt(70, 64) == 100) {
                    observedWrite[0] = true;
                    throw new ScriptingContext.InterruptedException();
                }
            }
        };
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                5, 12, 1.10, true, true, 733L, cancellation);
        final boolean accepted = carver.addPath(horizontalXs(), horizontalYs());
        assertTrue(carver.getLastRejection(), accepted);
        applying[0] = true;

        assertThrows(ScriptingContext.InterruptedException.class, carver::apply);

        assertTrue("Cancellation should happen after corrected-mouth terrain has actually been written", observedWrite[0]);
        assertEquals("Rollback must restore height, water, terrain, biome and preserve every original lake cell",
                before, snapshot(dimension));
    }

    @Test
    public void shallowMouthConnectsToExistingSeaTwoBlocksBesideItsFinalWaypoint() {
        final Dimension dimension = flat(100);
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                if (x >= 78 && y >= 66) {
                    dimension.setHeightAt(x, y, 98f);
                    dimension.setWaterLevelAt(x, y, 100);
                    dimension.setTerrainAt(x, y, Terrain.SAND);
                } else {
                    dimension.setHeightAt(x, y,
                            x >= 60 && x <= 80 && Math.abs(y - 64) <= 1 ? 99.45f : 100.25f);
                }
            }
        }
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                5, 5, 1.10, true, true, 733L, null);
        final boolean accepted = carver.addPath(new int[] { 20, 80 }, new int[] { 64, 64 });
        assertTrue("A safe adjacent side outlet should connect: " + carver.getLastRejection(), accepted);
        assertEquals(before, snapshot(dimension));
        carver.apply();

        for (int x = 24; x <= 80; x++) {
            assertWet(dimension, x, 64);
            assertEquals(100, dimension.getWaterLevelAt(x, 64));
        }
        assertWet(dimension, 80, 65);
        assertEquals("The short sideways bridge must match the real sea level", 100, dimension.getWaterLevelAt(80, 65));
        assertWet(dimension, 80, 66);
        for (Cell original : before.cells()) {
            if (original.water() > Math.round(original.height())) {
                assertEquals(original.height(), dimension.getHeightAt(original.x(), original.y()), 0f);
                assertEquals(original.water(), dimension.getWaterLevelAt(original.x(), original.y()));
                assertEquals(original.terrain(), dimension.getTerrainAt(original.x(), original.y()));
                assertEquals(original.biome(), dimension.getLayerValueAt(Biome.INSTANCE, original.x(), original.y()));
            }
        }
        assertMaximumCut(before, dimension, 1.85);
    }

    @Test
    public void differentLevelSideWaterAwayFromTheEndpointRejectsWithoutMutation() {
        for (boolean hasTerminalLake : new boolean[] { false, true }) {
            final Dimension dimension = flat(100);
            setAllHeights(dimension, (x, y) -> 99.25f);
            for (int x = 50; x <= 56; x++) {
                for (int y = 66; y <= 70; y++) {
                    dimension.setHeightAt(x, y, 98f);
                    dimension.setWaterLevelAt(x, y, 100);
                    dimension.setTerrainAt(x, y, Terrain.SAND);
                }
            }
            if (hasTerminalLake) {
                for (int x = 98; x < 128; x++) {
                    for (int y = 0; y < 128; y++) {
                        dimension.setHeightAt(x, y, 97f);
                        dimension.setWaterLevelAt(x, y, 99);
                        dimension.setTerrainAt(x, y, Terrain.SAND);
                    }
                }
            }
            final Snapshot before = snapshot(dimension);
            final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                    5, 5, 1.10, true, true, 733L, null);

            assertFalse("A side lake at Y=100 must not border the new Y=99 channel",
                    carver.addPath(new int[] { 20, hasTerminalLake ? 108 : 80 }, new int[] { 64, 64 }));
            assertEquals(before, snapshot(dimension));
            assertEquals(0, carver.apply().changedCells());
            assertEquals(before, snapshot(dimension));
        }
    }

    @Test
    public void highAndLowWorldCoordinatesRemainWithinDimensionLimits() {
        for (int height : new int[] { TestData.MIN_HEIGHT + 4, TestData.MAX_HEIGHT - 2 }) {
            final Dimension dimension = flat(height);
            // The default dry water plane must remain below this low test terrain.
            for (Tile tile : dimension.getTiles()) {
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        tile.setWaterLevel(x, y, TestData.MIN_HEIGHT);
                    }
                }
            }
            final ShallowRiverCarver carver = carver(dimension, true, true);
            assertTrue(carver.addPath(horizontalXs(), horizontalYs()));
            carver.apply();

            assertWet(dimension, 64, 64);
            for (Tile tile : dimension.getTiles()) {
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        assertTrue(tile.getHeight(x, y) >= dimension.getMinHeight());
                        assertTrue(tile.getHeight(x, y) < dimension.getMaxHeight());
                        assertTrue(tile.getWaterLevel(x, y) >= dimension.getMinHeight());
                        assertTrue(tile.getWaterLevel(x, y) < dimension.getMaxHeight());
                    }
                }
            }
        }
    }

    @Test
    public void missingTilesRejectPathWithoutCreatingTilesOrMutatingKnownGround() {
        final Dimension dimension = flat(100);
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = carver(dimension, true, true);

        assertFalse(carver.addPath(new int[] { 20, 180 }, new int[] { 64, 64 }));
        assertEquals(0, carver.apply().changedCells());
        assertEquals(1, dimension.getTiles().size());
        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void sBendAndRightAngleStayConnectedWithoutRepeatedlyDeepeningTheirCorners() {
        final int[] sinXs = horizontalXs(), sinYs = new int[sinXs.length];
        for (int index = 0; index < sinYs.length; index++) {
            sinYs[index] = 64 + (int) Math.round(12 * Math.sin(index * 2 * Math.PI / (sinYs.length - 1)));
        }
        final int[][][] paths = {
                { sinXs, sinYs },
                { new int[] { 20, 64, 64 }, new int[] { 32, 32, 104 } }
        };
        for (int[][] path : paths) {
            final Dimension dimension = flat(100);
            final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                    5, 8, 1.10, true, true, 733L, null);
            final boolean accepted = carver.addPath(path[0], path[1]);
            assertTrue(carver.getLastRejection(), accepted);
            final ShallowRiverCarver.Result result = carver.apply();

            for (int segment = 1; segment < path[0].length; segment++) {
                final int dx = path[0][segment] - path[0][segment - 1];
                final int dy = path[1][segment] - path[1][segment - 1];
                final int samples = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) * 2));
                for (int sample = 0; sample <= samples; sample++) {
                    final int x = (int) Math.round(path[0][segment - 1] + dx * sample / (double) samples);
                    final int y = (int) Math.round(path[1][segment - 1] + dy * sample / (double) samples);
                    assertWet(dimension, x, y);
                }
            }
            assertAllWetCellsConnectedInSingleTile(dimension, path[0][0], path[1][0]);
            assertMaximumCut(dimension, 100, 1.85);
            assertTrue(result.maximumCut() <= 1.85 + HEIGHT_EPSILON);
        }
    }

    @Test
    public void downhillEndpointsDoNotRetainAnOlderSegmentsRoundedWaterOrCarvingCap() {
        final Dimension dimension = flat(100);
        setAllHeights(dimension, (x, y) -> (float) (100.25 - x * 0.025));
        final Snapshot before = snapshot(dimension);
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                5, 12, 1.10, true, true, 733L, null);
        final boolean accepted = carver.addPath(new int[] { 20, 80 }, new int[] { 64, 64 });
        assertTrue(carver.getLastRejection(), accepted);
        carver.apply();

        for (int x = 20; x <= 80; x++) {
            assertWet(dimension, x, 64);
        }
        for (Cell original : before.cells()) {
            if (original.x() < 20 || original.x() > 80) {
                final String location = "Beyond route endpoint at " + original.x() + "," + original.y();
                assertEquals(location, original.height(), dimension.getHeightAt(original.x(), original.y()), 0f);
                assertEquals(location, original.water(), dimension.getWaterLevelAt(original.x(), original.y()));
                assertEquals(location, original.terrain(), dimension.getTerrainAt(original.x(), original.y()));
                assertEquals(location, original.biome(), dimension.getLayerValueAt(Biome.INSTANCE, original.x(), original.y()));
            }
        }
        assertMaximumCut(before, dimension, 1.85);
    }

    @Test
    public void eightKilometreStripPlansAndAppliesOneRiverWithMeasuredWorkAndCancellationChecks() {
        // This is 64 tiles / 8192 x 128, NOT an 8192 x 8192 world. Avoid a
        // million-cell object snapshot: all original values are constant here.
        final Dimension dimension = terrain(0, 63, 0, 0, 100);
        final int[] xs = { 32, 8159 }, ys = { 64, 64 };
        final int[] cancelledChecks = { 0 };
        final ScriptProgress cancellation = new ScriptProgress(null, null) {
            @Override
            public void checkForCancel() {
                if (++cancelledChecks[0] >= 256) {
                    throw new ScriptingContext.InterruptedException();
                }
            }
        };
        final ShallowRiverCarver cancelled = new ShallowRiverCarver(dimension,
                5, 12, 1.10, true, true, 733L, cancellation);
        assertThrows(ScriptingContext.InterruptedException.class, () -> cancelled.addPath(xs, ys));
        assertEquals(256, cancelledChecks[0]);
        for (Tile tile : dimension.getTiles()) {
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    assertEquals("Cancelled planning must not carve", 100f, tile.getHeight(x, y), 0f);
                    assertEquals(0, tile.getWaterLevel(x, y));
                    assertEquals(Terrain.GRASS, tile.getTerrain(x, y));
                }
            }
        }

        final long[] checks = { 0 }, progressUpdates = { 0 };
        final double[] lastProgress = { 0 };
        final ScriptProgress progress = new ScriptProgress(null, null) {
            @Override
            public void checkForCancel() {
                checks[0]++;
            }

            @Override
            public void setProgress(double fraction) {
                assertTrue(fraction >= lastProgress[0] && fraction <= 1.0);
                lastProgress[0] = fraction;
                progressUpdates[0]++;
            }
        };
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,
                5, 12, 1.10, true, true, 733L, progress);
        final long planningStart = System.nanoTime();
        final boolean accepted = carver.addPath(xs, ys);
        final long planningNanos = System.nanoTime() - planningStart;
        assertTrue(carver.getLastRejection(), accepted);
        final long planningChecks = checks[0];
        final long applyStart = System.nanoTime();
        final ShallowRiverCarver.Result result = carver.apply();
        final long applyNanos = System.nanoTime() - applyStart;

        assertEquals(1, result.paths());
        assertEquals(0, result.rejectedPaths());
        assertEquals(64, dimension.getTiles().size());
        assertTrue(planningChecks > 0);
        assertTrue("Applying a long river must remain cancellable", checks[0] > planningChecks);
        assertTrue("Applying a long river must report progress", progressUpdates[0] > 1);
        assertEquals(1.0, lastProgress[0], 0.0);
        for (int x = xs[0]; x <= xs[1]; x++) {
            for (int offset = -1; offset <= 1; offset++) {
                assertWet(dimension, x, 64 + offset);
            }
        }
        assertMaximumCut(dimension, 100, 1.85);
        for (Tile tile : dimension.getTiles()) {
            for (int x = 0; x < 128; x++) {
                for (int y : new int[] { 0, 127 }) {
                    assertEquals(100f, tile.getHeight(x, y), 0f);
                    assertEquals(0, tile.getWaterLevel(x, y));
                    assertEquals(Terrain.GRASS, tile.getTerrain(x, y));
                }
            }
        }
        // Informational local measurement only: no machine-dependent time limit.
        System.out.printf(java.util.Locale.ROOT,
                "ShallowRiver 8192x128 strip: plan=%.1f ms, apply=%.1f ms, changed=%d, maxCut=%.4f, planChecks=%d, applyChecks=%d%n",
                planningNanos / 1_000_000.0, applyNanos / 1_000_000.0, result.changedCells(), result.maximumCut(),
                planningChecks, checks[0] - planningChecks);
    }

    private static void assertAllWetCellsConnectedInSingleTile(Dimension dimension, int startX, int startY) {
        final boolean[] visited = new boolean[128 * 128];
        final java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        queue.add(startY * 128 + startX);
        visited[startY * 128 + startX] = true;
        int connected = 0;
        while (!queue.isEmpty()) {
            final int position = queue.removeFirst(), x = position % 128, y = position / 128;
            assertWet(dimension, x, y);
            connected++;
            for (int[] direction : CARDINAL_DIRECTIONS) {
                final int nextX = x + direction[0], nextY = y + direction[1];
                if (nextX < 0 || nextX >= 128 || nextY < 0 || nextY >= 128) continue;
                final int next = nextY * 128 + nextX;
                if (!visited[next] && isWet(dimension, nextX, nextY)) {
                    visited[next] = true;
                    queue.addLast(next);
                }
            }
        }
        int allWet = 0;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                if (isWet(dimension, x, y)) allWet++;
            }
        }
        assertEquals("Every river-water cell must belong to the same edge-connected channel", allWet, connected);
    }

    private static ShallowRiverCarver carver(Dimension dimension, boolean smoothBanks, boolean granite) {
        return new ShallowRiverCarver(dimension, 5.0, 12.0, DEPTH, smoothBanks, granite, 733L, null);
    }

    private static Dimension coastalOutletDimension(int lakeLevel, boolean openBank) {
        final Dimension dimension = flat(100);
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                if (x >= 98) {
                    dimension.setHeightAt(x, y, 98f);
                    dimension.setWaterLevelAt(x, y, lakeLevel);
                    dimension.setTerrainAt(x, y, Terrain.SAND);
                    dimension.setLayerValueAt(Biome.INSTANCE, x, y, 0);
                } else {
                    // A low but enclosed channel dips below the lake's Y=100
                    // before recovering to a 99.70 shore. Greedy min(previous,
                    // localHeight) used to lock its surface at Y=99 permanently.
                    final float centre = x < 60 ? (float) (100.75 - (x - 20) * 0.02)
                            : x < 86 ? 99.45f : 99.70f;
                    final float height = Math.abs(y - 64) <= 2 ? centre
                            : openBank && x >= 60 ? 99.25f : Math.max(100.25f, centre);
                    dimension.setHeightAt(x, y, height);
                }
            }
        }
        return dimension;
    }

    private static void assertExistingLakeUnchanged(Snapshot before, Dimension dimension, int firstLakeX) {
        for (Cell original : before.cells()) {
            if (original.x() >= firstLakeX) {
                final String message = "Receiving lake must stay unchanged at " + original.x() + "," + original.y();
                assertEquals(message, original.height(), dimension.getHeightAt(original.x(), original.y()), 0f);
                assertEquals(message, original.water(), dimension.getWaterLevelAt(original.x(), original.y()));
                assertEquals(message, original.terrain(), dimension.getTerrainAt(original.x(), original.y()));
                assertEquals(message, original.biome(), dimension.getLayerValueAt(Biome.INSTANCE, original.x(), original.y()));
            }
        }
    }

    private static int[] horizontalXs() {
        final int[] coordinates = new int[89];
        for (int index = 0; index < coordinates.length; index++) {
            coordinates[index] = 20 + index;
        }
        return coordinates;
    }

    private static int[] horizontalYs() {
        final int[] coordinates = new int[89];
        java.util.Arrays.fill(coordinates, 64);
        return coordinates;
    }

    private static void assertWet(Dimension dimension, int x, int y) {
        assertTrue("Dry exported gap at " + x + "," + y + ": ground=" + dimension.getHeightAt(x, y)
                        + ", water=" + dimension.getWaterLevelAt(x, y), isWet(dimension, x, y));
    }

    private static boolean isWet(Dimension dimension, int x, int y) {
        return dimension.getWaterLevelAt(x, y) > Math.round(dimension.getHeightAt(x, y));
    }

    private static Dimension flat(int height) {
        return terrain(0, 0, 0, 0, height);
    }

    private static Dimension terrain(int minTileX, int maxTileX, int minTileY, int maxTileY, int height) {
        final TileFactory factory = TestData.createTileFactory(height);
        final Dimension dimension = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "Shallow river", 733L, factory, Dimension.Anchor.NORMAL_DETAIL);
        for (int ty = minTileY; ty <= maxTileY; ty++) {
            for (int tx = minTileX; tx <= maxTileX; tx++) {
                final Tile tile = factory.createTile(tx, ty);
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        tile.setHeight(x, y, height);
                        tile.setTerrain(x, y, Terrain.GRASS);
                        tile.setWaterLevel(x, y, Math.min(0, height - 1));
                    }
                }
                dimension.addTile(tile);
            }
        }
        return dimension;
    }

    private static void setAllHeights(Dimension dimension, HeightFunction function) {
        for (Tile tile : dimension.getTiles()) {
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    tile.setHeight(x, y, function.height(tile.getX() * 128 + x, tile.getY() * 128 + y));
                }
            }
        }
    }

    private static void assertMaximumCut(Dimension dimension, float originalHeight, double limit) {
        for (Tile tile : dimension.getTiles()) {
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    final double cut = originalHeight - tile.getHeight(x, y);
                    assertTrue("Unexpected terrain raising", cut >= -HEIGHT_EPSILON);
                    assertTrue("Excessive excavation: " + cut, cut <= limit + HEIGHT_EPSILON);
                }
            }
        }
    }

    private static void assertMaximumCut(Snapshot original, Dimension dimension, double limit) {
        for (Cell cell : original.cells()) {
            final double cut = cell.height() - dimension.getHeightAt(cell.x(), cell.y());
            assertTrue("Unexpected terrain raising", cut >= -HEIGHT_EPSILON);
            assertTrue("Excessive excavation at " + cell.x() + "," + cell.y() + ": " + cut,
                    cut <= limit + HEIGHT_EPSILON);
        }
    }

    private static Snapshot snapshot(Dimension dimension) {
        final List<Tile> tiles = new ArrayList<>(dimension.getTiles());
        tiles.sort(java.util.Comparator.comparingInt(Tile::getX).thenComparingInt(Tile::getY));
        final List<Cell> cells = new ArrayList<>(tiles.size() * 16384);
        final Layer[] protectedLayers = { FloodWithLava.INSTANCE, Void.INSTANCE, NotPresent.INSTANCE,
                NotPresentBlock.INSTANCE, ReadOnly.INSTANCE, River.INSTANCE };
        for (Tile tile : tiles) {
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int layerBits = 0;
                    for (int index = 0; index < protectedLayers.length; index++) {
                        if (tile.getBitLayerValue(protectedLayers[index], x, y)) {
                            layerBits |= 1 << index;
                        }
                    }
                    cells.add(new Cell(tile.getX() * 128 + x, tile.getY() * 128 + y,
                            tile.getHeight(x, y), tile.getWaterLevel(x, y), tile.getTerrain(x, y),
                            tile.getLayerValue(Biome.INSTANCE, x, y), layerBits));
                }
            }
        }
        return new Snapshot(cells, dimension.getSurfaceSmoothing());
    }

    private interface HeightFunction {
        float height(int x, int y);
    }

    private record Cell(int x, int y, float height, int water, Terrain terrain, int biome, int layerBits) {
    }

    private record Snapshot(List<Cell> cells, Dimension.SurfaceSmoothing smoothing) {
        @Override
        public String toString() {
            return "Snapshot[cells=" + cells.size() + ", hash=" + cells.hashCode() + ", smoothing=" + smoothing + "]";
        }
    }

    private static final double DEPTH = 1.25;
    private static final double HEIGHT_EPSILON = 1.0 / 256.0;
    private static final int[][] CARDINAL_DIRECTIONS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
}
