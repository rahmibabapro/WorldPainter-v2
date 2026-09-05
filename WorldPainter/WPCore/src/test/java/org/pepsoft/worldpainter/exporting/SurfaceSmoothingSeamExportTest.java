package org.pepsoft.worldpainter.exporting;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Direction;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.RiverSurfaceDetail;

import java.awt.Rectangle;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.*;
import static org.pepsoft.minecraft.Material.*;

/** Actual chunk exports, including snapshot halos and the surface-material pass. */
public class SurfaceSmoothingSeamExportTest {
    @BeforeClass
    public static void initialiseExport() {
        if (Configuration.getInstance() == null) {
            Configuration.setInstance(new Configuration());
        }
        ExportTestSupport.ensureReady();
    }

    @Test
    public void fourCardinalSlopesExportMatchingUprightStairs() {
        for (int i = 0; i < OFFSETS.length; i++) {
            final Dimension dimension = createSurface(8, 8, 64, OFFSETS[i][0], OFFSETS[i][1], 62);
            final Material surface = exportSurface(dimension, 8, 8);
            assertEquals("minecraft:granite_stairs", surface.name);
            assertEquals(FACINGS[i], surface.getProperty(FACING));
            assertEquals("bottom", surface.getProperty(HALF));
            assertEquals("straight", surface.getProperty(SHAPE));
            assertFalse(Boolean.TRUE.equals(surface.getProperty(WATERLOGGED)));
        }
    }

    @Test
    public void dryGrassAndDirtSeamsAreFlushInAllFourDirections() {
        for (Terrain terrain : new Terrain[] { Terrain.GRASS, Terrain.DIRT }) {
            for (int[] offset : OFFSETS) {
                final Dimension dimension = createSurface(8, 8, 64.25f, 0, 0, 62);
                dimension.setTerrainAt(8 + offset[0], 8 + offset[1], terrain);
                // The seam belongs to the same exported voxel even if float heights differ.
                dimension.setHeightAt(8 + offset[0], 8 + offset[1], 64.375f);
                final Chunk chunk = factory(dimension).createChunk(0, 0).chunk;
                assertEquals("Rock must retain a full supporting top next to " + terrain,
                        MC_GRANITE, chunk.getMaterial(8, 64, 8).name);
                assertEquals(terrain == Terrain.GRASS ? MC_GRASS_BLOCK : MC_DIRT,
                        chunk.getMaterial(8 + offset[0], 64, 8 + offset[1]).name);
                assertEquals(MC_GRANITE, chunk.getMaterial(8, 63, 8).name);
                assertTrue(chunk.getMaterial(8, 65, 8).empty);
                assertEquals(64.25f, dimension.getHeightAt(8, 8), 0f);
                assertEquals(Terrain.GRANITE, dimension.getTerrainAt(8, 8));
                assertEquals(62, dimension.getWaterLevelAt(8, 8));
            }
        }
    }

    @Test
    public void flatIntegerPlateauStaysFullAndFractionalInteriorRemainsBottomSlab() {
        final Dimension integer = createSurface(8, 8, 64, 0, 0, 62);
        assertEquals(MC_GRANITE, exportSurface(integer, 8, 8).name);
        final Dimension fractional = createSurface(8, 8, 64.25f, 0, 0, 62);
        final Material material = exportSurface(fractional, 8, 8);
        assertEquals("minecraft:granite_slab", material.name);
        assertEquals("bottom", material.getProperty(TYPE));
    }

    @Test
    public void submergedSlabAndAllFourStairFacingsStayWaterloggedWithSolidSupport() {
        for (int i = -1; i < OFFSETS.length; i++) {
            final Dimension dimension = i < 0
                    ? createSurface(8, 8, 64.25f, 0, 0, 65)
                    : createSurface(8, 8, 64, OFFSETS[i][0], OFFSETS[i][1], 65);
            final Chunk chunk = factory(dimension).createChunk(0, 0).chunk;
            final Material surface = chunk.getMaterial(8, 64, 8);
            assertEquals(i < 0 ? "minecraft:granite_slab" : "minecraft:granite_stairs", surface.name);
            assertTrue(surface.getProperty(WATERLOGGED));
            if (i < 0) {
                assertEquals("bottom", surface.getProperty(TYPE));
            } else {
                assertEquals(FACINGS[i], surface.getProperty(FACING));
                assertEquals("bottom", surface.getProperty(HALF));
            }
            assertEquals(MC_GRANITE, chunk.getMaterial(8, 63, 8).name);
            assertEquals(MC_WATER, chunk.getMaterial(8, 65, 8).name);
            assertTrue(chunk.getMaterial(8, 66, 8).empty);
        }
    }

    @Test
    public void underwaterRoundedContourBridgesAcrossChunkAndTileHalos() {
        for (int x : new int[] { 15, 127 }) {
            final Dimension dimension = createSurface(x, 8, 64.10f, 0, 0, 66);
            for (int dz = -1; dz <= 1; dz++) dimension.setHeightAt(x + 1, 8 + dz, 64.60f);
            final Chunk lowerChunk = factory(dimension).createChunk(x >> 4, 0).chunk;
            final Material lower = lowerChunk.getMaterial(x & 15, 64, 8);
            assertEquals("The lower contour cell must bridge the one-Y slab seam",
                    "minecraft:granite_stairs", lower.name);
            assertEquals(Direction.EAST, lower.getProperty(FACING));
            assertEquals("straight", lower.getProperty(SHAPE));
            assertEquals("bottom", lower.getProperty(HALF));
            assertTrue(lower.getProperty(WATERLOGGED));
            assertEquals(MC_GRANITE, lowerChunk.getMaterial(x & 15, 63, 8).name);

            final Chunk higherChunk = factory(dimension).createChunk((x + 1) >> 4, 0).chunk;
            final Material higher = higherChunk.getMaterial((x + 1) & 15, 65, 8);
            assertEquals("minecraft:granite_slab", higher.name);
            assertEquals("bottom", higher.getProperty(TYPE));
            assertTrue(higher.getProperty(WATERLOGGED));
            assertEquals(MC_GRANITE, higherChunk.getMaterial((x + 1) & 15, 64, 8).name);
            assertEquals(MC_WATER, higherChunk.getMaterial((x + 1) & 15, 66, 8).name);
        }
    }

    @Test
    public void mudBrickShorelineDetailUsesChunkAndTileNeighbourHalos() {
        for (int x : new int[] { 15, 127 }) {
            final Dimension dimension = createSurface(x, 8, 63.25f, 0, 0, 64);
            dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.NONE);
            dimension.setHeightAt(x + 1, 8, 64.25f);
            dimension.setWaterLevelAt(x + 1, 8, 62);
            dimension.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, 8, true);
            final Material detail = exportSurface(dimension, x, 8);
            assertTrue("Wet shoreline at world X=" + x + " must use mud-brick detail: " + detail,
                    "minecraft:mud_brick_slab".equals(detail.name)
                            || "minecraft:mud_brick_stairs".equals(detail.name));
            assertTrue(detail.getProperty(WATERLOGGED));
            assertEquals("bottom", detail.getProperty(detail.name.endsWith("_slab") ? TYPE : HALF));
            assertEquals(MC_GRANITE, exportSurface(dimension, x + 1, 8).name);
        }
    }

    @Test
    public void submergedGraniteIsNotBlockedByDryNonsmoothableNeighbour() {
        final Dimension dimension = createSurface(8, 8, 64.25f, 0, 0, 65);
        dimension.setTerrainAt(9, 8, Terrain.GRASS);
        dimension.setWaterLevelAt(9, 8, 62);
        final Material surface = exportSurface(dimension, 8, 8);
        assertEquals("minecraft:granite_slab", surface.name);
        assertTrue(surface.getProperty(WATERLOGGED));
    }

    @Test
    public void chunkAndTileBoundaryHalosProduceIdenticalStairsAndFlushSeams() {
        for (int x : new int[] { 15, 16, 127, 128 }) {
            for (int slopeX : new int[] { -1, 1 }) {
                final Dimension slope = createSurface(x, 8, 64, slopeX, 0, 62);
                final Material surface = exportSurface(slope, x, 8);
                assertEquals("minecraft:granite_stairs", surface.name);
                assertEquals(slopeX < 0 ? Direction.WEST : Direction.EAST, surface.getProperty(FACING));

                final Dimension seam = createSurface(x, 8, 64.25f, 0, 0, 62);
                seam.setTerrainAt(x + slopeX, 8, Terrain.GRASS);
                assertEquals(MC_GRANITE, exportSurface(seam, x, 8).name);
                assertEquals(MC_GRASS_BLOCK, exportSurface(seam, x + slopeX, 8).name);
            }
        }
    }

    @Test
    public void intentionalCliffAndMissingWorldHaloRemainFullWithoutAirOrTopPartials() {
        final Dimension cliff = createSurface(8, 8, 64.25f, 0, 0, 62);
        cliff.setHeightAt(9, 8, 67);
        final Chunk cliffChunk = factory(cliff).createChunk(0, 0).chunk;
        assertEquals(MC_GRANITE, cliffChunk.getMaterial(8, 64, 8).name);
        assertEquals(MC_GRANITE, cliffChunk.getMaterial(8, 63, 8).name);

        final Dimension edge = TestData.createDimension(new Rectangle(0, 0, 128, 128), 64);
        edge.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        for (int z = 126; z <= 127; z++) {
            for (int x = 126; x <= 127; x++) {
                edge.setHeightAt(x, z, 64.25f);
                edge.setTerrainAt(x, z, Terrain.GRANITE);
            }
        }
        assertEquals(MC_GRANITE, exportSurface(edge, 127, 127).name);
    }

    @Test
    public void smoothingSettingAndGrassSubsurfaceStayUntouched() {
        final Dimension dimension = createSurface(8, 8, 64.25f, 0, 0, 62);
        dimension.setTerrainAt(9, 8, Terrain.GRASS);
        final Chunk chunk = factory(dimension).createChunk(0, 0).chunk;
        assertEquals(MC_GRASS_BLOCK, chunk.getMaterial(9, 64, 8).name);
        // Terrain.GRASS uses GRASS_BLOCK for both topMaterial and topLayerMaterial
        // in the raw factory pass. Buried-grass conversion happens later; this
        // regression checks that smoothing does not change the original column.
        assertEquals(Terrain.GRASS.getMaterial(TestData.PLATFORM, dimension.getSeed(), 9, 8, 63, 64),
                chunk.getMaterial(9, 63, 8));
        assertEquals(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS, dimension.getSurfaceSmoothing());
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.NONE);
        final Chunk reference = factory(dimension).createChunk(0, 0).chunk;
        assertEquals(MC_GRANITE, reference.getMaterial(8, 64, 8).name);
        for (int y = TestData.MIN_HEIGHT; y <= 64; y++) {
            assertEquals("Smoothing must preserve the entire original grass column at Y=" + y,
                    reference.getMaterial(9, y, 8), chunk.getMaterial(9, y, 8));
        }
        assertEquals(Dimension.SurfaceSmoothing.NONE, dimension.getSurfaceSmoothing());
    }

    private static Dimension createSurface(int centreX, int centreZ, float height, int slopeX, int slopeZ, int waterLevel) {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 256, 128), 64);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                dimension.setHeightAt(centreX + dx, centreZ + dz, height + dx * slopeX + dz * slopeZ);
                dimension.setTerrainAt(centreX + dx, centreZ + dz, Terrain.GRANITE);
                dimension.setWaterLevelAt(centreX + dx, centreZ + dz, waterLevel);
            }
        }
        return dimension;
    }

    private static Material exportSurface(Dimension dimension, int x, int z) {
        final Chunk chunk = factory(dimension).createChunk(x >> 4, z >> 4).chunk;
        return chunk.getMaterial(x & 15, dimension.getIntHeightAt(x, z), z & 15);
    }

    private static WorldPainterChunkFactory factory(Dimension dimension) {
        return new WorldPainterChunkFactory(dimension, Collections.emptyMap(), TestData.PLATFORM, TestData.MAX_HEIGHT);
    }

    private static final int[][] OFFSETS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    private static final Direction[] FACINGS = { Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH };
}
