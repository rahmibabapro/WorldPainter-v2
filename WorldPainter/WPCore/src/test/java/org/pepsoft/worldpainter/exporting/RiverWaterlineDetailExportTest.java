package org.pepsoft.worldpainter.exporting;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Direction;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Constants;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.RiverWaterlineDetail;
import org.pepsoft.worldpainter.layers.renderers.BitLayerRenderer;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.MC_WATER;
import static org.pepsoft.minecraft.Material.FACING;
import static org.pepsoft.minecraft.Material.HALF;
import static org.pepsoft.minecraft.Material.TYPE;
import static org.pepsoft.minecraft.Material.WATERLOGGED;

/** Dry top-waterline bank lip export via RiverWaterlineDetail (independent of wet granite detail). */
public class RiverWaterlineDetailExportTest {
    @BeforeClass public static void initialiseExport() {
        if (Configuration.getInstance() == null) Configuration.setInstance(new Configuration());
        ExportTestSupport.ensureReady();
    }

    @Test public void markedDryLipExportsWaterloggedBottomStairFacingWater() {
        final int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        final Direction[] facings = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
        for (int i = 0; i < dirs.length; i++) {
            final Dimension d = flat(64f, Terrain.GRASS);
            final int wx = 8, wz = 8;
            final int bx = wx + dirs[i][0], bz = wz + dirs[i][1];
            d.setHeightAt(wx, wz, 63f);
            d.setWaterLevelAt(wx, wz, 64);
            d.setTerrainAt(wx, wz, Terrain.GRANITE);
            d.setHeightAt(bx, bz, 64f);
            d.setWaterLevelAt(bx, bz, 0);
            d.setTerrainAt(bx, bz, Terrain.GRASS);
            // Land behind bank stays at waterline so stair is chosen.
            d.setHeightAt(bx + dirs[i][0], bz + dirs[i][1], 65f);
            markWaterline(d, bx, bz);
            final Material stair = export(d).material(bx, 64, bz);
            assertEquals("dir " + i, "minecraft:mud_brick_stairs", stair.name);
            assertEquals(facings[i], stair.getProperty(FACING));
            assertEquals("bottom", stair.getProperty(HALF));
            assertTrue(Boolean.TRUE.equals(stair.getProperty(WATERLOGGED)));
            assertEquals(MC_WATER, export(d).material(wx, 64, wz).name);
            assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(export(d).material(wx, 63, wz)));
        }
    }

    @Test public void enclosedPocketExportsBottomSlab() {
        final Dimension d = flat(64f, Terrain.GRASS);
        d.setHeightAt(8, 8, 63f);
        d.setWaterLevelAt(8, 8, 64);
        d.setTerrainAt(8, 8, Terrain.GRANITE);
        d.setHeightAt(7, 9, 63f);
        d.setWaterLevelAt(7, 9, 64);
        d.setTerrainAt(7, 9, Terrain.GRANITE);
        // Bank with two cardinal wet sides → slab.
        d.setHeightAt(8, 9, 64f);
        d.setWaterLevelAt(8, 9, 0);
        d.setTerrainAt(8, 9, Terrain.GRASS);
        markWaterline(d, 8, 9);
        final Material slab = export(d).material(8, 64, 9);
        assertEquals("minecraft:mud_brick_slab", slab.name);
        assertEquals("bottom", slab.getProperty(TYPE));
        assertTrue(Boolean.TRUE.equals(slab.getProperty(WATERLOGGED)));
    }

    @Test public void staleMarkerWithoutWetNeighbourKeepsFullBlock() {
        final Dimension d = flat(64f, Terrain.GRASS);
        markWaterline(d, 8, 8);
        final Material bank = export(d).material(8, 64, 8);
        assertEquals("minecraft:grass_block", bank.name);
        assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(bank));
        assertFalse(Boolean.TRUE.equals(bank.getProperty(WATERLOGGED)));
    }

    @Test public void unmarkedDryBankStaysFullEvenBesideWater() {
        final Dimension d = flat(64f, Terrain.GRASS);
        d.setHeightAt(8, 8, 63f);
        d.setWaterLevelAt(8, 8, 64);
        d.setTerrainAt(8, 8, Terrain.GRANITE);
        d.setHeightAt(9, 8, 64f);
        d.setWaterLevelAt(9, 8, 0);
        final Material bank = export(d).material(9, 64, 8);
        assertEquals("minecraft:grass_block", bank.name);
        assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(bank));
    }

    @Test public void openCardinalDepressionForcesFullBlockDespiteMarker() {
        final Dimension d = flat(64f, Terrain.GRASS);
        d.setHeightAt(8, 8, 63f);
        d.setWaterLevelAt(8, 8, 64);
        d.setTerrainAt(8, 8, Terrain.GRANITE);
        d.setHeightAt(9, 8, 64f);
        d.setWaterLevelAt(9, 8, 0);
        d.setTerrainAt(9, 8, Terrain.GRASS);
        d.setHeightAt(10, 8, 65f);
        // Open air/depression on the north face of the marked lip.
        d.setHeightAt(9, 7, 60f);
        d.setWaterLevelAt(9, 7, 0);
        markWaterline(d, 9, 8);
        final Material bank = export(d).material(9, 64, 8);
        assertEquals("minecraft:grass_block", bank.name);
        assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(bank));
        assertFalse(Boolean.TRUE.equals(bank.getProperty(WATERLOGGED)));
        assertFalse(SurfaceSmoother.isWaterlineSideSealed(d, null, 9, 8, 64));
    }

    @Test public void lowerNeighbourWaterDoesNotSealFaceAtWaterline() {
        // River W=64; neighbour depression has water only to 62 → air at Y=64.
        final Dimension d = flat(64f, Terrain.GRASS);
        d.setHeightAt(8, 8, 63f);
        d.setWaterLevelAt(8, 8, 64);
        d.setTerrainAt(8, 8, Terrain.GRANITE);
        d.setHeightAt(9, 8, 64f);
        d.setWaterLevelAt(9, 8, 0);
        d.setTerrainAt(9, 8, Terrain.GRASS);
        d.setHeightAt(10, 8, 65f);
        d.setHeightAt(9, 7, 60f);
        d.setWaterLevelAt(9, 7, 62);
        markWaterline(d, 9, 8);
        assertFalse(SurfaceSmoother.isWaterlineSideSealed(d, null, 9, 8, 64));
        final Material bank = export(d).material(9, 64, 8);
        assertEquals("minecraft:grass_block", bank.name);
        assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(bank));
        assertFalse(Boolean.TRUE.equals(bank.getProperty(WATERLOGGED)));
    }

    @Test public void layerIsSystemPassthroughWithoutExporter() {
        assertTrue(Constants.SYSTEM_LAYERS.contains(RiverWaterlineDetail.INSTANCE));
        assertNull(RiverWaterlineDetail.INSTANCE.getExporterType());
        assertTrue(RiverWaterlineDetail.INSTANCE.getRenderer() instanceof BitLayerRenderer);
    }

    @Test public void waterlineAndWetGraniteMarkersDoNotCrossApply() {
        // Wet granite marker on dry lip must not activate waterline path.
        final Dimension wetOnly = flat(64f, Terrain.GRASS);
        wetOnly.setHeightAt(8, 8, 63f);
        wetOnly.setWaterLevelAt(8, 8, 64);
        wetOnly.setTerrainAt(8, 8, Terrain.GRANITE);
        wetOnly.setHeightAt(9, 8, 64f);
        wetOnly.setBitLayerValueAt(org.pepsoft.worldpainter.layers.RiverSurfaceDetail.INSTANCE, 9, 8, true);
        assertEquals("minecraft:grass_block", export(wetOnly).material(9, 64, 8).name);

        // Waterline marker on wet granite must not replace wet surface path when under water.
        final Dimension dryOnly = flat(63.25f, Terrain.GRANITE);
        dryOnly.setWaterLevelAt(8, 8, 64);
        dryOnly.setBitLayerValueAt(RiverWaterlineDetail.INSTANCE, 8, 8, true);
        // Under water → waterline branch skipped; without RiverSurfaceDetail stays full granite.
        assertEquals("minecraft:granite", export(dryOnly).material(8, 63, 8).name);
    }

    private static Dimension flat(float height, Terrain terrain) {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.NONE);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            dimension.setHeightAt(x, y, height);
            dimension.setWaterLevelAt(x, y, 0);
            dimension.setTerrainAt(x, y, terrain);
        }
        return dimension;
    }

    private static void markWaterline(Dimension dimension, int x, int y) {
        dimension.setBitLayerValueAt(RiverWaterlineDetail.INSTANCE, x, y, true);
    }

    private static WorldPainterChunkFactory factory(Dimension dimension) {
        return new WorldPainterChunkFactory(dimension, Collections.emptyMap(), TestData.PLATFORM, TestData.MAX_HEIGHT);
    }

    private static Exported export(Dimension dimension) {
        return new Exported(factory(dimension), new HashMap<>());
    }

    private record Exported(WorldPainterChunkFactory factory, Map<Long, Chunk> chunks) {
        Material material(int x, int y, int z) {
            final int cx = x >> 4, cz = z >> 4;
            final long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
            return chunks.computeIfAbsent(key, ignored -> factory.createChunk(cx, cz).chunk)
                    .getMaterial(x & 15, y, z & 15);
        }
    }
}
