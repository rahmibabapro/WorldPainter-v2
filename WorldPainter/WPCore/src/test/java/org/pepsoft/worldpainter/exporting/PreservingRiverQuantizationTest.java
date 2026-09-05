package org.pepsoft.worldpainter.exporting;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.SnowDepth;
import org.pepsoft.worldpainter.tools.scripts.ShallowRiverCarver;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.MC_GRANITE;
import static org.pepsoft.minecraft.Constants.MC_WATER;
import static org.pepsoft.minecraft.Material.WATERLOGGED;

/** Water ceilings must use the same original surface voxel as the real exporter. */
public class PreservingRiverQuantizationTest {
    @BeforeClass public static void initialiseExport() {
        if (Configuration.getInstance() == null) Configuration.setInstance(new Configuration());
        ExportTestSupport.ensureReady();
    }

    @Test public void fractionalCrossSectionIsFeasibleWithoutAnExtraBlockOfExcavation() {
        final Dimension dimension = terrain(0, 0, 0, true);
        final Cell[] before = snapshot(dimension, 0, 0);
        // Former floor-based ceiling: low bank 99.9 -> W99. The higher
        // wet core at 100.4 then requires at least 2.05 blocks of incision.
        final int formerCeiling = (int) Math.floor(dimension.getHeightAt(64, 62));
        assertTrue(dimension.getHeightAt(64, 65) - formerCeiling + .65 > 1.85);
        final ShallowRiverCarver plan = preserving(dimension);
        accept(plan, 0, 0);
        assertArrayEquals("Planning must not edit the world", before, snapshot(dimension, 0, 0));
        final ShallowRiverCarver.Result result = plan.apply();
        assertBounds(dimension, before, result, 0, 0);
        for (int x = 24; x <= 104; x++) for (int y = 63; y <= 65; y++) {
            assertEquals(100, dimension.getWaterLevelAt(x, y));
            assertTrue(dimension.getWaterLevelAt(x, y) > dimension.getIntHeightAt(x, y));
        }
    }

    @Test public void theSameQuantizationContractWorksAtNegativeCoordinatesAndElevations() {
        final Dimension dimension = terrain(-128, -128, -128, true);
        final Cell[] before = snapshot(dimension, -128, -128);
        final ShallowRiverCarver plan = preserving(dimension);
        accept(plan, -128, -128);
        assertArrayEquals(before, snapshot(dimension, -128, -128));
        assertBounds(dimension, before, plan.apply(), -128, -128);
        assertEquals(-28, dimension.getWaterLevelAt(-64, -64));
        assertTrue(dimension.getWaterLevelAt(-64, -64) > dimension.getIntHeightAt(-64, -64));
    }

    @Test public void insufficientOriginalBankStillRejectsWithoutFillingOrChangingTerrain() {
        final Dimension dimension = terrain(0, 0, 0, true);
        // This rounds DOWN to Y99, so it cannot contain a Y100 water face.
        for (int x = 0; x < 128; x++) dimension.setHeightAt(x, 61, 99.49f);
        final Cell[] before = snapshot(dimension, 0, 0);
        final ShallowRiverCarver plan = preserving(dimension);
        assertFalse("A real low bank must not be treated as the fractional false negative",
                plan.addPath(new int[] { 20, 108 }, new int[] { 64, 64 }));
        assertEquals(0, plan.apply().changedCells());
        assertArrayEquals(before, snapshot(dimension, 0, 0));
        assertEquals(0, plan.getMaximumFill(), 0);
        assertEquals(1.85, plan.getMaximumCut(), 0.000001);
    }

    @Test public void actualExportKeepsRoundedDryGraniteBanksFullAndWetDetailSupported() {
        for (boolean crossSection : new boolean[] { false, true }) {
            final Dimension dimension = terrain(0, 0, 0, crossSection);
            final Cell[] before = snapshot(dimension, 0, 0);
            final ShallowRiverCarver plan = preserving(dimension);
            accept(plan, 0, 0);
            assertBounds(dimension, before, plan.apply(), 0, 0);
            final Cell[] carved = snapshot(dimension, 0, 0);
            final Exported exported = new Exported(new WorldPainterChunkFactory(dimension, Collections.emptyMap(),
                    TestData.PLATFORM, TestData.MAX_HEIGHT), new HashMap<>());
            int sealedDryFaces = 0, supportedWetPartials = 0;
            for (int x = 20; x <= 108; x++) for (int y = 60; y <= 68; y++) {
                final int height = dimension.getIntHeightAt(x, y), water = dimension.getWaterLevelAt(x, y);
                if (water <= height) continue;
                assertEquals("Continuous full-water corridor", MC_WATER, exported.material(x, water, y).name);
                assertTrue("Water cannot be above the original exported full surface",
                        water <= Math.round(before[y * 128 + x].height));
                int waterBlocks = 0;
                for (int z = height + 1; z <= water; z++) {
                    if (MC_WATER.equals(exported.material(x, z, y).name)) waterBlocks++;
                }
                assertTrue("No unexpectedly deep water column", waterBlocks <= 2);
                final Material bed = exported.material(x, height, y);
                if (SurfaceSmoother.isSmoothedSurfacePartial(bed)) {
                    supportedWetPartials++;
                    assertTrue(Boolean.TRUE.equals(bed.getProperty(WATERLOGGED)));
                    final Material below = exported.material(x, height - 1, y);
                    assertTrue(below.solid);
                    assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(below));
                }
                for (int[] offset : CARDINALS) {
                    final int nx = x + offset[0], ny = y + offset[1];
                    if (dimension.getWaterLevelAt(nx, ny) > dimension.getIntHeightAt(nx, ny)) continue;
                    sealedDryFaces++;
                    final Material bankAtWater = exported.material(nx, water, ny);
                    assertEquals("An untouched bank must present its whole granite voxel to water at " + nx + "," + ny,
                            MC_GRANITE, bankAtWater.name);
                    assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(bankAtWater));
                    assertFalse(Boolean.TRUE.equals(bankAtWater.getProperty(WATERLOGGED)));
                    assertEquals(before[ny * 128 + nx], carved[ny * 128 + nx]);
                }
            }
            assertTrue("Fixture must contain actual dry bank faces", sealedDryFaces > 100);
            assertTrue("Wet bed smoothing must remain active", supportedWetPartials > 0);
            assertArrayEquals("Export is read only", carved, snapshot(dimension, 0, 0));
            assertEquals(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS, dimension.getSurfaceSmoothing());
        }
    }

    private static void assertBounds(Dimension dimension, Cell[] before, ShallowRiverCarver.Result result,
                                     int startX, int startY) {
        assertEquals(0, result.raisedCells());
        assertEquals(0, result.maximumFill(), 0);
        assertTrue(result.changedCells() > 100);
        assertTrue(result.maximumCut() <= 1.85);
        final Cell[] after = snapshot(dimension, startX, startY);
        for (int i = 0; i < before.length; i++) {
            assertTrue("No terrain fill", after[i].height <= before[i].height);
            assertTrue("Maximum cut unchanged", before[i].height - after[i].height <= 1.85);
            if (after[i].water <= Math.round(after[i].height)) {
                assertEquals("Every dry cell must remain exactly original", before[i], after[i]);
            } else {
                assertTrue("Shallow water depth unchanged", after[i].water - after[i].height <= 1.1 + 1.0 / 256.0);
                assertTrue(after[i].water <= Math.round(before[i].height));
            }
        }
    }

    private static ShallowRiverCarver preserving(Dimension dimension) {
        final ShallowRiverCarver plan = new ShallowRiverCarver(dimension, 6, 6, 1.1, true, true, 1337, null);
        plan.enableTerrainPreservation();
        return plan;
    }

    private static void accept(ShallowRiverCarver plan, int startX, int startY) {
        final boolean accepted = plan.addPath(new int[] { 20 + startX, 108 + startX },
                new int[] { 64 + startY, 64 + startY });
        assertTrue(plan.getLastRejection(), accepted);
    }

    private static Dimension terrain(int startX, int startY, int heightOffset, boolean crossSection) {
        final Dimension dimension = TestData.createDimension(new Rectangle(startX, startY, 128, 128), 100 + heightOffset);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            final float height = crossSection ? (y == 62 ? 99.9f : y == 65 ? 100.4f : 100.2f) : 100.75f;
            dimension.setHeightAt(startX + x, startY + y, height + heightOffset);
            dimension.setWaterLevelAt(startX + x, startY + y, TestData.MIN_HEIGHT);
            dimension.setTerrainAt(startX + x, startY + y, Terrain.GRANITE);
            dimension.setLayerValueAt(Biome.INSTANCE, startX + x, startY + y, 4);
        }
        return dimension;
    }

    private static Cell[] snapshot(Dimension dimension, int startX, int startY) {
        final Cell[] result = new Cell[128 * 128];
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            final int wx = startX + x, wy = startY + y;
            result[y * 128 + x] = new Cell(dimension.getHeightAt(wx, wy), dimension.getWaterLevelAt(wx, wy),
                    dimension.getTerrainAt(wx, wy), dimension.getLayerValueAt(Biome.INSTANCE, wx, wy),
                    dimension.getBitLayerValueAt(Frost.INSTANCE, wx, wy), dimension.getLayerValueAt(SnowDepth.INSTANCE, wx, wy));
        }
        return result;
    }

    private record Cell(float height, int water, Terrain terrain, int biome, boolean frost, int snowDepth) { }
    private record Exported(WorldPainterChunkFactory factory, Map<Long, Chunk> chunks) {
        Material material(int x, int y, int z) {
            final int chunkX = x >> 4, chunkZ = z >> 4;
            final long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
            final Chunk chunk = chunks.computeIfAbsent(key, ignored -> factory.createChunk(chunkX, chunkZ).chunk);
            return chunk.getMaterial(x & 15, y, z & 15);
        }
    }
    private static final int[][] CARDINALS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
}
