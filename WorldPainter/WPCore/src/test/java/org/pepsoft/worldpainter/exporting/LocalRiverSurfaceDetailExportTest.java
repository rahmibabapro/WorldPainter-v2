package org.pepsoft.worldpainter.exporting;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Direction;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.undo.UndoManager;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.RiverSurfaceDetail;
import org.pepsoft.worldpainter.layers.renderers.BitLayerRenderer;
import org.pepsoft.worldpainter.tools.scripts.ShallowRiverCarver;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.MC_GRANITE;
import static org.pepsoft.minecraft.Constants.MC_WATER;
import static org.pepsoft.minecraft.Material.FACING;
import static org.pepsoft.minecraft.Material.HALF;
import static org.pepsoft.minecraft.Material.TYPE;
import static org.pepsoft.minecraft.Material.WATERLOGGED;

/** Actual chunk export of local river detail while the world-wide preference stays unchanged. */
public class LocalRiverSurfaceDetailExportTest {
    @BeforeClass public static void initialiseExport() {
        if (Configuration.getInstance() == null) Configuration.setInstance(new Configuration());
        ExportTestSupport.ensureReady();
    }

    @Test public void wetGraniteMarkerProducesSupportedWaterloggedSlabWithGlobalSmoothingOff() {
        final Dimension dimension = terrain(63.25f, Terrain.GRANITE, Dimension.SurfaceSmoothing.NONE);
        dimension.setWaterLevelAt(8, 8, 64);
        mark(dimension, 8, 8);
        final Cell[] before = snapshot(dimension);
        final Exported exported = export(dimension);
        final Material slab = exported.material(8, 63, 8);
        assertEquals("minecraft:granite_slab", slab.name);
        assertEquals("bottom", slab.getProperty(TYPE));
        assertTrue(Boolean.TRUE.equals(slab.getProperty(WATERLOGGED)));
        assertEquals(MC_GRANITE, exported.material(8, 62, 8).name);
        assertEquals(MC_WATER, exported.material(8, 64, 8).name);
        assertEquals(Dimension.SurfaceSmoothing.NONE, dimension.getSurfaceSmoothing());
        assertArrayEquals(before, snapshot(dimension));
    }

    @Test public void realShorelineStairsUseMudBricksAndFaceEachUphillDirection() {
        final int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        final Direction[] facings = { Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH };
        for (int i = 0; i < directions.length; i++) {
            final Dimension dimension = terrain(63f, Terrain.GRANITE, Dimension.SurfaceSmoothing.NONE);
            dimension.setHeightAt(8 + directions[i][0], 8 + directions[i][1], 63.75f);
            dimension.setWaterLevelAt(8, 8, 64);
            mark(dimension, 8, 8);
            final Exported exported = export(dimension);
            final Material stair = exported.material(8, 63, 8);
            assertEquals("minecraft:mud_brick_stairs", stair.name);
            assertEquals(facings[i], stair.getProperty(FACING));
            assertEquals("bottom", stair.getProperty(HALF));
            assertTrue(Boolean.TRUE.equals(stair.getProperty(WATERLOGGED)));
            assertEquals(MC_GRANITE, exported.material(8, 62, 8).name);
            assertEquals(MC_WATER, exported.material(8, 64, 8).name);
        }
    }

    @Test public void absentMarkerRetainsOriginalFullBlockBehaviourWhenSmoothingIsOff() {
        final Dimension dimension = terrain(63.25f, Terrain.GRANITE, Dimension.SurfaceSmoothing.NONE);
        dimension.setWaterLevelAt(8, 8, 64);
        assertFalse(dimension.getTile(0, 0).hasLayer(RiverSurfaceDetail.INSTANCE));
        assertEquals(MC_GRANITE, export(dimension).material(8, 63, 8).name);
        assertEquals(Dimension.SurfaceSmoothing.NONE, dimension.getSurfaceSmoothing());
    }

    @Test public void staleMarkersOnDryNonGraniteOrLavaHaveNoEffectInEitherGlobalMode() {
        for (Dimension.SurfaceSmoothing mode : Dimension.SurfaceSmoothing.values()) {
            for (int scenario = 0; scenario < 5; scenario++) {
                final Terrain surface = switch (scenario) {
                    case 0 -> Terrain.GRASS;
                    case 1 -> Terrain.DIRT;
                    case 2 -> Terrain.STONE;
                    default -> Terrain.GRANITE;
                };
                final Dimension plain = terrain(63.25f, surface, mode), marked = terrain(63.25f, surface, mode);
                if (scenario != 3) {
                    plain.setWaterLevelAt(8, 8, 64);
                    marked.setWaterLevelAt(8, 8, 64);
                }
                if (scenario == 4) {
                    plain.setBitLayerValueAt(FloodWithLava.INSTANCE, 8, 8, true);
                    marked.setBitLayerValueAt(FloodWithLava.INSTANCE, 8, 8, true);
                }
                mark(marked, 8, 8);
                assertChunkEquals(factory(plain).createChunk(0, 0).chunk, factory(marked).createChunk(0, 0).chunk);
                assertEquals(mode, marked.getSurfaceSmoothing());
            }
        }
    }

    @Test public void markerDoesNotAlterAlreadyEnabledGlobalSmoothingOutput() {
        final Dimension plain = terrain(63.25f, Terrain.GRANITE, Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        final Dimension marked = terrain(63.25f, Terrain.GRANITE, Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        plain.setWaterLevelAt(8, 8, 64);
        marked.setWaterLevelAt(8, 8, 64);
        mark(marked, 8, 8);
        assertChunkEquals(factory(plain).createChunk(0, 0).chunk, factory(marked).createChunk(0, 0).chunk);
    }

    @Test public void localDetailPreservesFullDryGraniteAndGrassBankInterfaces() {
        for (Terrain bankTerrain : new Terrain[] { Terrain.GRANITE, Terrain.GRASS }) {
            final Dimension dimension = terrain(64.25f, bankTerrain, Dimension.SurfaceSmoothing.NONE);
            dimension.setHeightAt(8, 8, 63.25f);
            dimension.setTerrainAt(8, 8, Terrain.GRANITE);
            dimension.setWaterLevelAt(8, 8, 64);
            mark(dimension, 8, 8);
            mark(dimension, 9, 8); // Even a stale marker must not lower the dry bank.
            final Cell[] before = snapshot(dimension);
            final Exported exported = export(dimension);
            assertTrue(SurfaceSmoother.isSmoothedSurfacePartial(exported.material(8, 63, 8)));
            final Material bank = exported.material(9, 64, 8);
            assertTrue(bank.solid);
            assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(bank));
            assertFalse(Boolean.TRUE.equals(bank.getProperty(WATERLOGGED)));
            assertEquals(bankTerrain == Terrain.GRANITE ? MC_GRANITE : "minecraft:grass_block", bank.name);
            assertArrayEquals(before, snapshot(dimension));
        }
    }

    @Test public void genuineCliffGuardStillKeepsFullGraniteRatherThanForcingAnUnsafeStair() {
        final Dimension dimension = terrain(63.25f, Terrain.GRANITE, Dimension.SurfaceSmoothing.NONE);
        dimension.setHeightAt(9, 8, 66);
        dimension.setWaterLevelAt(8, 8, 64);
        mark(dimension, 8, 8);
        assertEquals(MC_GRANITE, export(dimension).material(8, 63, 8).name);
    }

    @Test public void markerSurvivesTileSerializationAndHasANoOpRenderer() throws Exception {
        final Dimension dimension = terrain(63.25f, Terrain.GRANITE, Dimension.SurfaceSmoothing.NONE);
        mark(dimension, 8, 8);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(dimension.getTile(0, 0)); }
        final Tile restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (Tile) input.readObject();
        }
        assertTrue(restored.getBitLayerValue(RiverSurfaceDetail.INSTANCE, 8, 8));
        assertTrue(restored.getLayers().stream().anyMatch(layer -> layer == RiverSurfaceDetail.INSTANCE));
        assertNull(RiverSurfaceDetail.INSTANCE.getExporterType());
        final BitLayerRenderer renderer = (BitLayerRenderer) RiverSurfaceDetail.INSTANCE.getRenderer();
        assertNotNull("Auxiliary BIT layers still need a renderer for 2D/3D views", renderer);
        assertEquals(0x123456, renderer.getPixelColour(8, 8, 0x123456, true));
        assertEquals(0x123456, renderer.getPixelColour(8, 8, 0x123456, false));
    }

    @Test public void markerIsAnInternalLayerExcludedFromEyedropperAndInfoCandidates() {
        final Dimension dimension = terrain(63.25f, Terrain.GRANITE, Dimension.SurfaceSmoothing.NONE);
        mark(dimension, 8, 8);
        final Map<org.pepsoft.worldpainter.layers.Layer, Integer> visible =
                new HashMap<>(dimension.getLayersAt(8, 8));
        assertTrue("Fixture must expose the stored marker before the UI system-layer filter",
                visible.containsKey(RiverSurfaceDetail.INSTANCE));
        assertTrue("Eyedropper and InfoPanel both derive their hidden layers from SYSTEM_LAYERS",
                org.pepsoft.worldpainter.Constants.SYSTEM_LAYERS.contains(RiverSurfaceDetail.INSTANCE));

        visible.keySet().removeAll(org.pepsoft.worldpainter.Constants.SYSTEM_LAYERS);

        assertFalse("Discrete internal metadata must never reach Eyedropper's selectable-layer branch",
                visible.containsKey(RiverSurfaceDetail.INSTANCE));
    }

    @Test public void markerIsPartOfNormalTileUndoWithoutChangingGlobalPreference() {
        final Dimension dimension = terrain(63.25f, Terrain.GRANITE, Dimension.SurfaceSmoothing.NONE);
        final UndoManager undo = new UndoManager(10);
        dimension.registerUndoManager(undo);
        try {
            dimension.rememberChanges();
            mark(dimension, 8, 8);
            assertTrue(dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, 8, 8));
            assertTrue(dimension.undoChanges());
            assertFalse(dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, 8, 8));
            assertEquals(Dimension.SurfaceSmoothing.NONE, dimension.getSurfaceSmoothing());
        } finally { dimension.unregisterUndoManager(); }
    }

    @Test public void generatedRiverReallyExportsLocalGranitePartialsWithTheWorldPreferenceOff() {
        final Dimension dimension = terrain(100f, Terrain.GRASS, Dimension.SurfaceSmoothing.NONE);
        final ShallowRiverCarver plan = new ShallowRiverCarver(dimension, 5, 12, 1.1, true, true, 1337, null);
        plan.enableTerrainPreservation();
        final boolean accepted = plan.addPath(new int[] { 20, 108 }, new int[] { 64, 64 });
        assertTrue(plan.getLastRejection(), accepted);
        final ShallowRiverCarver.Result result = plan.apply();
        assertTrue(result.graniteCells() > 0);
        int markers = 0;
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            if (!dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, y)) continue;
            markers++;
            assertEquals(Terrain.GRANITE, dimension.getTerrainAt(x, y));
            assertTrue(dimension.getWaterLevelAt(x, y) > dimension.getIntHeightAt(x, y));
        }
        assertEquals(result.graniteCells(), markers);
        final Cell[] beforeExport = snapshot(dimension);
        final Exported exported = export(dimension);
        int partials = 0, granitePartials = 0, mudPartials = 0;
        for (int x = 24; x <= 104; x++) for (int y = 59; y <= 69; y++) {
            if (!dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, y)) continue;
            final int height = dimension.getIntHeightAt(x, y);
            final Material material = exported.material(x, height, y);
            if (SurfaceSmoother.isSmoothedSurfacePartial(material)) {
                partials++;
                assertTrue(material.name.startsWith("minecraft:granite_")
                        || material.name.startsWith("minecraft:mud_brick_"));
                if (material.name.startsWith("minecraft:granite_")) granitePartials++;
                if (material.name.startsWith("minecraft:mud_brick_")) mudPartials++;
                assertTrue(Boolean.TRUE.equals(material.getProperty(WATERLOGGED)));
                final Material support = exported.material(x, height - 1, y);
                assertTrue(support.solid);
                assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(support));
            }
        }
        assertTrue("Granit detayı must not silently become only full blocks when global smoothing is off", partials > 0);
        assertTrue("The channel interior must retain granite partials", granitePartials > 0);
        assertTrue("The real generated shoreline must contain mud-brick partials", mudPartials > 0);
        assertEquals(Dimension.SurfaceSmoothing.NONE, dimension.getSurfaceSmoothing());
        assertEquals(Terrain.GRASS, dimension.getTerrainAt(64, 32));
        assertEquals(100, dimension.getHeightAt(64, 32), 0);
        assertArrayEquals(beforeExport, snapshot(dimension));
    }

    private static Dimension terrain(float height, Terrain terrain, Dimension.SurfaceSmoothing smoothing) {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        dimension.setSurfaceSmoothing(smoothing);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            dimension.setHeightAt(x, y, height);
            dimension.setWaterLevelAt(x, y, 0);
            dimension.setTerrainAt(x, y, terrain);
        }
        return dimension;
    }
    private static void mark(Dimension dimension, int x, int y) {
        dimension.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, y, true);
    }
    private static WorldPainterChunkFactory factory(Dimension dimension) {
        return new WorldPainterChunkFactory(dimension, Collections.emptyMap(), TestData.PLATFORM, TestData.MAX_HEIGHT);
    }
    private static Exported export(Dimension dimension) { return new Exported(factory(dimension), new HashMap<>()); }
    private static void assertChunkEquals(Chunk expected, Chunk actual) {
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) for (int y = TestData.MIN_HEIGHT; y < 68; y++) {
            assertEquals("Unrelated exported voxel changed at " + x + "," + y + "," + z,
                    expected.getMaterial(x, y, z), actual.getMaterial(x, y, z));
        }
    }
    private static Cell[] snapshot(Dimension dimension) {
        final Cell[] cells = new Cell[16384];
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            cells[y * 128 + x] = new Cell(dimension.getHeightAt(x, y), dimension.getWaterLevelAt(x, y),
                    dimension.getTerrainAt(x, y), dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, y));
        }
        return cells;
    }
    private record Cell(float height, int water, Terrain terrain, boolean riverDetail) { }
    private record Exported(WorldPainterChunkFactory factory, Map<Long, Chunk> chunks) {
        Material material(int x, int y, int z) {
            final int cx = x >> 4, cz = z >> 4;
            final long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
            return chunks.computeIfAbsent(key, ignored -> factory.createChunk(cx, cz).chunk).getMaterial(x & 15, y, z & 15);
        }
    }
}
