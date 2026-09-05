package org.pepsoft.worldpainter.exporting;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.RiverSurfaceDetail;
import org.pepsoft.worldpainter.tools.scripts.ShallowRiverCarver;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.MC_GRANITE;
import static org.pepsoft.minecraft.Constants.MC_WATER;
import static org.pepsoft.minecraft.Material.HALF;
import static org.pepsoft.minecraft.Material.TYPE;
import static org.pepsoft.minecraft.Material.WATERLOGGED;

/** Original dry banks must seal water without editing or flattening the heightmap. */
public class TerrainPreservingRiverExportTest {
    @BeforeClass
    public static void initialiseExport() {
        if (Configuration.getInstance() == null) {
            Configuration.setInstance(new Configuration());
        }
        ExportTestSupport.ensureReady();
    }

    @Test
    public void equalWaterLevelKeepsTheOriginalDryGraniteBlockInAllFourDirections() {
        for (int[] offset : CARDINALS) {
            final Dimension dimension = bankSurface(8, 8);
            final int wetX = 8 + offset[0], wetZ = 8 + offset[1];
            dimension.setHeightAt(wetX, wetZ, 63.25f);
            dimension.setWaterLevelAt(wetX, wetZ, 64);
            final SurfaceSnapshot before = new SurfaceSnapshot(dimension, 256, 128);
            final Exported exported = export(dimension);
            assertEquals(MC_WATER, exported.material(wetX, 64, wetZ).name);
            assertEquals("A half-height dry bank would open a lateral water face",
                    MC_GRANITE, exported.material(8, 64, 8).name);
            assertEquals(MC_GRANITE, exported.material(8, 63, 8).name);
            assertTrue(exported.material(8, 65, 8).empty);
            assertFalse(hasWater(exported.material(8, 64, 8)));
            before.assertUnchanged(dimension);
        }
    }

    @Test
    public void sameGuardWorksWithoutTheChunkHeightSnapshot() {
        final Dimension dimension = bankSurface(8, 8);
        dimension.setHeightAt(9, 8, 63.25f);
        dimension.setWaterLevelAt(9, 8, 64);
        assertNull(SurfaceSmoother.smoothSurfaceMaterial(dimension, 8, 8, 64,
                Material.get(MC_GRANITE)));
        assertEquals(64.25f, dimension.getHeightAt(8, 8), 0f);
    }

    @Test
    public void waterBelowTheBankDoesNotDisableOrdinaryDrySlabs() {
        final Dimension dimension = bankSurface(8, 8);
        for (int z = 6; z <= 10; z++) {
            for (int x = 6; x <= 10; x++) dimension.setHeightAt(x, z, 63.75f);
        }
        dimension.setHeightAt(9, 8, 62.375f);
        dimension.setWaterLevelAt(9, 8, 63);
        final Exported exported = export(dimension);
        assertEquals(MC_WATER, exported.material(9, 63, 8).name);
        final Material bank = exported.material(8, 64, 8);
        assertEquals("minecraft:granite_slab", bank.name);
        assertEquals("bottom", bank.getProperty(TYPE));
        assertFalse(hasWater(bank));
    }

    @Test
    public void buriedWaterPlaneAndDiagonalWaterDoNotCreateFalseBankGuards() {
        final Dimension buried = bankSurface(8, 8);
        // Stored water at the neighbour's own terrain surface is not a full
        // water voxel. Do not expand this narrow guard to an arbitrary wet mask.
        buried.setWaterLevelAt(9, 8, 64);
        assertEquals("minecraft:granite_slab", export(buried).material(8, 64, 8).name);

        final Dimension diagonal = bankSurface(8, 8);
        diagonal.setHeightAt(9, 9, 63.25f);
        diagonal.setWaterLevelAt(9, 9, 64);
        assertEquals("Only face-adjacent full water requires this dry-bank seal",
                "minecraft:granite_slab", export(diagonal).material(8, 64, 8).name);
    }

    @Test
    public void voidAndLavaNeighboursAreNotMistakenForFullWater() {
        for (boolean lava : new boolean[] { false, true }) {
            final Dimension dimension = bankSurface(8, 8);
            dimension.setHeightAt(9, 8, 63.25f);
            dimension.setWaterLevelAt(9, 8, 64);
            if (lava) {
                dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, 9, 8, true);
            } else {
                dimension.setBitLayerValueAt(org.pepsoft.worldpainter.layers.Void.INSTANCE, 9, 8, true);
            }
            assertEquals("This guard is for actual exported full water, not any stored plane",
                    "minecraft:granite_slab", export(dimension).material(8, 64, 8).name);
        }
    }

    @Test
    public void bankSealUsesTheNeighbourHaloAcrossChunkAndTileBoundaries() {
        for (int x : new int[] { 15, 16, 127, 128 }) {
            for (int direction : new int[] { -1, 1 }) {
                final Dimension dimension = bankSurface(x, 8);
                dimension.setHeightAt(x + direction, 8, 63.25f);
                dimension.setWaterLevelAt(x + direction, 8, 64);
                final Exported exported = export(dimension);
                assertEquals(MC_WATER, exported.material(x + direction, 64, 8).name);
                assertEquals("Dry bank at world X=" + x + ", neighbour direction=" + direction,
                        MC_GRANITE, exported.material(x, 64, 8).name);
                assertEquals(64.25f, dimension.getHeightAt(x, 8), 0f);
            }
        }
    }

    @Test
    public void skippedNeighbourChunksDoNotPretendToExportFullWater() {
        for (boolean readOnly : new boolean[] { false, true }) {
            final Dimension dimension = bankSurface(15, 8);
            dimension.setHeightAt(16, 8, 63.25f);
            dimension.setWaterLevelAt(16, 8, 64);
            if (readOnly) {
                dimension.setBitLayerValueAt(ReadOnly.INSTANCE, 16, 8, true);
            } else {
                dimension.setBitLayerValueAt(NotPresent.INSTANCE, 16, 8, true);
            }
            final WorldPainterChunkFactory factory = factory(dimension);
            assertNull(factory.createChunk(1, 0));
            final Material bank = factory.createChunk(0, 0).chunk.getMaterial(15, 64, 8);
            assertEquals("minecraft:granite_slab", bank.name);
        }
    }

    @Test
    public void wetGraniteStillUsesSupportedWaterloggedPartialsBesideTheFullDryBank() {
        final Dimension dimension = bankSurface(8, 8);
        dimension.setHeightAt(9, 8, 63.25f);
        dimension.setWaterLevelAt(9, 8, 64);
        final Exported exported = export(dimension);
        final Material wet = exported.material(9, 63, 8);
        assertTrue("The wet river floor must still receive sub-voxel detail", isGranitePartial(wet));
        assertTrue(wet.getProperty(WATERLOGGED));
        assertEquals(MC_GRANITE, exported.material(9, 62, 8).name);
        assertEquals(MC_WATER, exported.material(9, 64, 8).name);
        assertEquals(MC_GRANITE, exported.material(8, 64, 8).name);
        assertUpright(wet);
    }

    @Test
    public void markedWetShorelineExportsWaterloggedMudBrickDetailInAllFourDirections() {
        for (int[] offset : CARDINALS) {
            final Dimension dimension = bankSurface(8, 8);
            dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.NONE);
            final int wetX = 8 + offset[0], wetZ = 8 + offset[1];
            for (int z = wetZ - 1; z <= wetZ + 1; z++) {
                for (int x = wetX - 1; x <= wetX + 1; x++) {
                    dimension.setHeightAt(x, z, 63.25f);
                    dimension.setWaterLevelAt(x, z, 64);
                    dimension.setTerrainAt(x, z, Terrain.GRANITE);
                }
            }
            // Restore the real, unmodified dry bank on one side of the wet fringe.
            dimension.setHeightAt(8, 8, 64.25f);
            dimension.setWaterLevelAt(8, 8, 0);
            dimension.setTerrainAt(8, 8, Terrain.GRANITE);
            dimension.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, wetX, wetZ, true);
            assertTrue(SurfaceSmoother.isWetShorelineEdge(dimension,
                    ChunkHeightSnapshot.create(dimension, wetX >> 4, wetZ >> 4), wetX, wetZ, 63));

            final Exported exported = export(dimension);
            final Material detail = exported.material(wetX, 63, wetZ);
            assertTrue("Mud-brick shoreline must use a slab/stair, not a full block: " + detail,
                    "minecraft:mud_brick_slab".equals(detail.name)
                            || "minecraft:mud_brick_stairs".equals(detail.name));
            assertTrue(detail.getProperty(WATERLOGGED));
            assertUpright(detail);
            assertEquals(MC_WATER, exported.material(wetX, 64, wetZ).name);
            assertEquals("The original dry bank must stay full granite",
                    MC_GRANITE, exported.material(8, 64, 8).name);
        }
    }

    @Test
    public void staleDetailMarkerDoesNotCreateMudBricksAwayFromARealWetShoreline() {
        final Dimension interior = bankSurface(8, 8);
        for (int z = 6; z <= 10; z++) {
            for (int x = 6; x <= 10; x++) {
                interior.setHeightAt(x, z, 63.25f);
                interior.setWaterLevelAt(x, z, 64);
            }
        }
        interior.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, 8, 8, true);
        assertFalse(SurfaceSmoother.isWetShorelineEdge(interior,
                ChunkHeightSnapshot.create(interior, 0, 0), 8, 8, 63));
        assertTrue(isGranitePartial(export(interior).material(8, 63, 8)));

        final Dimension dry = bankSurface(8, 8);
        dry.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, 8, 8, true);
        assertFalse(SurfaceSmoother.isWetShorelineEdge(dry,
                ChunkHeightSnapshot.create(dry, 0, 0), 8, 8, 64));
        assertFalse(export(dry).material(8, 64, 8).name.startsWith("minecraft:mud_brick"));
    }

    @Test
    public void terrainPreservingFlatRiverCutsOnlyTheWetChannelAndExportsThreeWideWater() {
        checkTerrainPreservingRiver(false);
    }

    @Test
    public void terrainPreservingGentleSlopeCutsOnlyTheWetChannelAndExportsThreeWideWater() {
        checkTerrainPreservingRiver(true);
    }

    private static void checkTerrainPreservingRiver(boolean slope) {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                dimension.setTerrainAt(x, z, Terrain.GRANITE);
                dimension.setHeightAt(x, z, slope ? 102f - x / 64f : 100f);
            }
        }
        final SurfaceSnapshot original = new SurfaceSnapshot(dimension, 128, 128);
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension, 5, 12, 1.10,
                true, true, 1337, null);
        carver.enableTerrainPreservation();
        assertEquals(0, carver.getMaximumFill(), 0);
        assertEquals(1.85, carver.getMaximumCut(), 0.000001);
        assertTrue(carver.getLastRejection(), carver.addPath(new int[] { 20, 108 }, new int[] { 64, 64 }));
        original.assertUnchanged(dimension);
        final ShallowRiverCarver.Result result = carver.apply();
        assertEquals(0, result.raisedCells());
        assertEquals(0, result.maximumFill(), 0);
        assertTrue(result.maximumCut() <= 1.85 + HEIGHT_QUANTUM);
        int wetCells = 0, dryCells = 0;
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                final int i = z * 128 + x;
                assertTrue("No original terrain may be raised at " + x + "," + z,
                        dimension.getHeightAt(x, z) <= original.heights[i]);
                assertTrue("The narrow river must not exceed the shallow excavation budget",
                        original.heights[i] - dimension.getHeightAt(x, z) <= 1.85 + HEIGHT_QUANTUM);
                if (dimension.getWaterLevelAt(x, z) > dimension.getIntHeightAt(x, z)) {
                    wetCells++;
                } else {
                    dryCells++;
                    assertEquals("A dry bank must retain its exact original height at " + x + "," + z,
                            original.heights[i], dimension.getHeightAt(x, z), 0f);
                    assertEquals(original.water[i], dimension.getWaterLevelAt(x, z));
                    assertEquals(original.terrains[i], dimension.getTerrainAt(x, z));
                }
            }
        }
        assertTrue(wetCells > 200);
        assertTrue("The narrow river must leave most of this real tile untouched", dryCells > 12000);

        int wetContourEdges = 0, markedShoreCells = 0;
        for (int z = 1; z < 127; z++) {
            for (int x = 1; x < 127; x++) {
                if (dimension.getWaterLevelAt(x, z) <= dimension.getIntHeightAt(x, z)) continue;
                if (SurfaceSmoother.isWetShorelineEdge(dimension, null, x, z,
                        dimension.getIntHeightAt(x, z))) {
                    markedShoreCells++;
                    assertEquals(Terrain.GRANITE, dimension.getTerrainAt(x, z));
                    assertTrue("Every generated wet shoreline cell must opt into local detail",
                            dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, z));
                }
                for (int[] offset : new int[][] { { 1, 0 }, { 0, 1 } }) {
                    final int nx = x + offset[0], nz = z + offset[1];
                    if (dimension.getWaterLevelAt(nx, nz) <= dimension.getIntHeightAt(nx, nz)
                            || Math.abs(dimension.getIntHeightAt(nx, nz) - dimension.getIntHeightAt(x, z)) != 1) continue;
                    wetContourEdges++;
                    assertEquals("Dirt must not interrupt an underwater rounded-height transition",
                            Terrain.GRANITE, dimension.getTerrainAt(x, z));
                    assertEquals("Dirt must not interrupt an underwater rounded-height transition",
                            Terrain.GRANITE, dimension.getTerrainAt(nx, nz));
                    assertTrue(dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, z));
                    assertTrue(dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, nx, nz));
                }
            }
        }
        if (slope) assertTrue("The sloping fixture must exercise at least one wet rounded-height contour",
                wetContourEdges > 0);
        assertTrue("The real carver fixture must contain marked wet shoreline cells", markedShoreCells > 0);

        final SurfaceSnapshot carved = new SurfaceSnapshot(dimension, 128, 128);
        final Exported exported = export(dimension);
        int partials = 0, mudBrickEdges = 0, sealedBanks = 0, exportedContourBridges = 0;
        for (int x = 24; x <= 104; x++) {
            final int waterY = dimension.getWaterLevelAt(x, 64);
            for (int z = 63; z <= 65; z++) {
                assertEquals("A waterlogged shelf does not count as the minimum three-wide full-water core",
                        MC_WATER, exported.material(x, waterY, z).name);
            }
            for (int z = 50; z <= 78; z++) {
                final int groundY = dimension.getIntHeightAt(x, z);
                final Material surface = exported.material(x, groundY, z);
                if (SurfaceSmoother.isWetShorelineEdge(dimension, null, x, z, groundY)
                        && dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, z)) {
                    mudBrickEdges++;
                    final boolean contourBridge = SurfaceSmoother.hasWetRoundedContourNeighbour(
                            dimension, null, x, z, groundY);
                    assertTrue("Marked wet shoreline must use the mud-brick family: " + surface,
                            "minecraft:mud_bricks".equals(surface.name)
                                    || "minecraft:mud_brick_slab".equals(surface.name)
                                    || "minecraft:mud_brick_stairs".equals(surface.name));
                    if ("minecraft:mud_bricks".equals(surface.name)) {
                        assertTrue("Only a required one-block wet contour may retain a full shoreline block",
                                contourBridge);
                    } else {
                        assertTrue(surface.getProperty(WATERLOGGED));
                        assertUpright(surface);
                    }
                }
                if (dimension.getWaterLevelAt(x, z) >= groundY && isGranitePartial(surface)) {
                    partials++;
                    assertTrue(surface.getProperty(WATERLOGGED));
                    assertUpright(surface);
                    final Material support = exported.material(x, groundY - 1, z);
                    assertTrue(support.solid);
                    assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(support));
                }
                if (dimension.getWaterLevelAt(x, z) < groundY) {
                    assertFalse("Original dry banks must not gain waterlogged fragments", hasWater(surface));
                    for (int[] offset : CARDINALS) {
                        if (MC_WATER.equals(exported.material(x + offset[0], groundY, z + offset[1]).name)) {
                            sealedBanks++;
                            assertEquals("An original dry bank next to actual water must retain its whole voxel",
                                    MC_GRANITE, surface.name);
                        }
                    }
                }
            }
        }
        assertTrue("Wet granite detail must survive the dry-bank guard", partials > 0);
        assertTrue("Wet shoreline must contain exported mud-brick detail", mudBrickEdges > 0);
        assertTrue("The integration fixture must exercise actual equal-level dry-bank water faces", sealedBanks > 0);
        for (int z = 1; z < 127; z++) {
            for (int x = 1; x < 127; x++) {
                if (dimension.getWaterLevelAt(x, z) <= dimension.getIntHeightAt(x, z)) continue;
                for (int[] offset : new int[][] { { 1, 0 }, { 0, 1 } }) {
                    final int nx = x + offset[0], nz = z + offset[1];
                    if (dimension.getWaterLevelAt(nx, nz) <= dimension.getIntHeightAt(nx, nz)) continue;
                    final int firstHeight = dimension.getIntHeightAt(x, z), secondHeight = dimension.getIntHeightAt(nx, nz);
                    if (Math.abs(firstHeight - secondHeight) != 1) continue;
                    final int lowX = firstHeight < secondHeight ? x : nx;
                    final int lowZ = firstHeight < secondHeight ? z : nz;
                    final int lowY = Math.min(firstHeight, secondHeight);
                    final Material bridge = exported.material(lowX, lowY, lowZ);
                    assertTrue("A wet rounded contour must export a granite or shoreline mud-brick stair/full bridge, not another slab: "
                                    + lowX + "," + lowZ + " -> " + bridge,
                            MC_GRANITE.equals(bridge.name) || "minecraft:granite_stairs".equals(bridge.name)
                                    || "minecraft:mud_bricks".equals(bridge.name)
                                    || "minecraft:mud_brick_stairs".equals(bridge.name));
                    if (bridge.name.endsWith("_stairs")) {
                        assertEquals("bottom", bridge.getProperty(HALF));
                        assertTrue(bridge.getProperty(WATERLOGGED));
                    }
                    exportedContourBridges++;
                }
            }
        }
        if (slope) assertTrue("The real carver/export path must exercise rounded contour bridges",
                exportedContourBridges > 0);
        carved.assertUnchanged(dimension);
        assertEquals(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS, dimension.getSurfaceSmoothing());
    }

    private static Dimension bankSurface(int x, int z) {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 256, 128), 64);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                dimension.setHeightAt(x + dx, z + dz, 64.25f);
                dimension.setTerrainAt(x + dx, z + dz, Terrain.GRANITE);
            }
        }
        return dimension;
    }

    private static WorldPainterChunkFactory factory(Dimension dimension) {
        return new WorldPainterChunkFactory(dimension, Collections.emptyMap(), TestData.PLATFORM, TestData.MAX_HEIGHT);
    }

    private static Exported export(Dimension dimension) {
        return new Exported(factory(dimension), new HashMap<>());
    }

    private static boolean isGranitePartial(Material material) {
        return "minecraft:granite_slab".equals(material.name) || "minecraft:granite_stairs".equals(material.name);
    }

    private static boolean hasWater(Material material) {
        return MC_WATER.equals(material.name) || Boolean.TRUE.equals(material.getProperty(WATERLOGGED));
    }

    private static void assertUpright(Material material) {
        assertEquals("bottom", material.getProperty(material.name.endsWith("_slab") ? TYPE : HALF));
    }

    private record Exported(WorldPainterChunkFactory factory, Map<Long, Chunk> chunks) {
        Material material(int x, int y, int z) {
            final int chunkX = x >> 4, chunkZ = z >> 4;
            final long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
            final Chunk chunk = chunks.computeIfAbsent(key, ignored -> factory.createChunk(chunkX, chunkZ).chunk);
            return chunk.getMaterial(x & 15, y, z & 15);
        }
    }

    private static final class SurfaceSnapshot {
        SurfaceSnapshot(Dimension dimension, int width, int length) {
            this.width = width;
            this.length = length;
            heights = new float[width * length];
            water = new int[width * length];
            terrains = new Terrain[width * length];
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    final int i = z * width + x;
                    heights[i] = dimension.getHeightAt(x, z);
                    water[i] = dimension.getWaterLevelAt(x, z);
                    terrains[i] = dimension.getTerrainAt(x, z);
                }
            }
        }

        void assertUnchanged(Dimension dimension) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    final int i = z * width + x;
                    assertEquals("Original height at " + x + "," + z, heights[i], dimension.getHeightAt(x, z), 0f);
                    assertEquals(water[i], dimension.getWaterLevelAt(x, z));
                    assertEquals(terrains[i], dimension.getTerrainAt(x, z));
                }
            }
        }

        final int width, length;
        final float[] heights;
        final int[] water;
        final Terrain[] terrains;
    }

    private static final float HEIGHT_QUANTUM = 1f / 256f;
    private static final int[][] CARDINALS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
}
