package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.SnowDepth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmoothSnowTest {
    @Test
    public void blueprintMaskProducesLayersWithoutChangingRockOrHeightAcrossTileEdges() throws Exception {
        final Dimension dimension = snowDimension(210);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 128; x++) {
                dimension.setHeightAt(x, y, 210.125f);
                dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, x >= 16 && x < 112);
            }
        }
        final SmoothSnow.Result result = SmoothSnow.applyBlueprintMask(dimension, 733L, null);
        assertEquals(96L * 256L, result.snowCovered());
        assertEquals(0, result.deepSnow());
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 128; x++) {
                assertEquals(Terrain.STONE, dimension.getTerrainAt(x, y));
                assertEquals(210.125f, dimension.getHeightAt(x, y), 0);
                final int depth = dimension.getLayerValueAt(SnowDepth.INSTANCE, x, y);
                assertEquals(x >= 16 && x < 112, dimension.getBitLayerValueAt(Frost.INSTANCE, x, y));
                if (x >= 16 && x < 112) {
                    assertTrue(depth >= 1 && depth <= 8);
                } else {
                    assertEquals(0, depth);
                }
            }
        }
        assertEquals(dimension.getLayerValueAt(SnowDepth.INSTANCE, 64, 127),
                dimension.getLayerValueAt(SnowDepth.INSTANCE, 64, 128));
    }

    @Test
    public void blueprintSnowStartsAt160SparselyAndDoesNotAddWhiteTerrain() throws Exception {
        final Dimension low = snowDimension(159), line = snowDimension(160), repeat = snowDimension(160);
        assertEquals(0, SmoothSnow.applyBlueprintMask(low, 733L, null).snowCovered());
        final SmoothSnow.Result first = SmoothSnow.applyBlueprintMask(line, 733L, null);
        final SmoothSnow.Result second = SmoothSnow.applyBlueprintMask(repeat, 733L, null);
        final double fraction = first.snowCovered() / 32768.0;
        assertTrue("Expected sparse 8% snow, measured " + fraction, fraction > 0.04 && fraction < 0.13);
        assertEquals(first, second);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 128; x++) {
                assertEquals(Terrain.STONE, line.getTerrainAt(x, y));
                assertEquals(line.getLayerValueAt(SnowDepth.INSTANCE, x, y), repeat.getLayerValueAt(SnowDepth.INSTANCE, x, y));
                assertTrue(line.getLayerValueAt(SnowDepth.INSTANCE, x, y) <= 1);
            }
        }
    }

    @Test
    public void summitWhiteSnowStartsAt150AndCoversEveryRetainedMaskCell() throws Exception {
        final Dimension low = snowDimension(149), start = snowDimension(150), full = snowDimension(190);
        assertEquals(0, SmoothSnow.applySummitBlueprintMask(low, 733L, null, 150, 190, 30).snowCovered());
        assertEquals(32768L, SmoothSnow.applySummitBlueprintMask(start, 733L, null, 150, 190, 30).snowCovered());
        assertEquals(32768L, SmoothSnow.applySummitBlueprintMask(full, 733L, null, 150, 190, 30).snowCovered());
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 128; x++) {
                assertFalse(low.getBitLayerValueAt(Frost.INSTANCE, x, y));
                assertEquals(0, low.getLayerValueAt(SnowDepth.INSTANCE, x, y));
                assertTrue(start.getBitLayerValueAt(Frost.INSTANCE, x, y));
                assertTrue(start.getLayerValueAt(SnowDepth.INSTANCE, x, y) >= 1);
                assertTrue(full.getLayerValueAt(SnowDepth.INSTANCE, x, y) >= start.getLayerValueAt(SnowDepth.INSTANCE, x, y));
            }
        }
    }

    private static Dimension snowDimension(int height) {
        final TileFactory factory = TestData.createTileFactory(height);
        final Dimension dimension = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "Snow", 0, factory, Dimension.Anchor.NORMAL_DETAIL);
        for (int ty = 0; ty < 2; ty++) {
            final Tile tile = factory.createTile(0, ty);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    tile.setHeight(x, y, height);
                    tile.setWaterLevel(x, y, 0);
                    tile.setTerrain(x, y, Terrain.STONE);
                    tile.setBitLayerValue(Frost.INSTANCE, x, y, true);
                }
            }
            dimension.addTile(tile);
        }
        return dimension;
    }

    @Test
    public void flatAspectIsNeutralAndNorthSouthAreSymmetric() {
        assertEquals(1.0f, SmoothSnow.aspectMultiplier(0, 0, 0.15f), 0.0f);
        assertEquals(1.15f, SmoothSnow.aspectMultiplier(-4, 4, 0.15f), 0.00001f);
        assertEquals(0.85f, SmoothSnow.aspectMultiplier(4, -4, 0.15f), 0.00001f);
        assertEquals(2.0f, SmoothSnow.aspectMultiplier(-2, 2, 0.15f)
                + SmoothSnow.aspectMultiplier(2, -2, 0.15f), 0.00001f);
    }

    @Test
    public void heightCoverageStartsAt160AndFinishesAt190() {
        assertEquals(0.0f, SmoothSnow.heightCoverage(159.99f, 160.0f, 190.0f), 0.00001f);
        assertEquals(0.08f, SmoothSnow.heightCoverage(160.0f, 160.0f, 190.0f), 0.00001f);
        assertEquals(1.0f, SmoothSnow.heightCoverage(190.0f, 160.0f, 190.0f), 0.00001f);
        assertTrue(SmoothSnow.heightCoverage(175.0f, 160.0f, 190.0f) > 0.08f);
        assertTrue(SmoothSnow.heightCoverage(175.0f, 160.0f, 190.0f) < 1.0f);
    }

    @Test
    public void steepSlopeRejectsSnowAndResultIsDeterministic() {
        final float flat = SmoothSnow.coverage(210.0f, 0.0f, 1.0f, 170210L, 128, -64, 170.0f, 210.0f, 25.0f, 55.0f);
        final float steep = SmoothSnow.coverage(210.0f, 55.0f, 1.0f, 170210L, 128, -64, 170.0f, 210.0f, 25.0f, 55.0f);
        assertEquals(1.0f, flat, 0.00001f);
        assertEquals(0.0f, steep, 0.00001f);
        assertEquals(flat, SmoothSnow.coverage(210.0f, 0.0f, 1.0f, 170210L, 128, -64, 170.0f, 210.0f, 25.0f, 55.0f), 0.0f);
    }
}
