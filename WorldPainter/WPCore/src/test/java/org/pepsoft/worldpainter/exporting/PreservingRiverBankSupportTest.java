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
import org.pepsoft.worldpainter.tools.scripts.ShallowRiverCarver;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.MC_GRANITE;
import static org.pepsoft.minecraft.Constants.MC_WATER;
import static org.pepsoft.minecraft.Material.WATERLOGGED;

/** Fractional diagonal raster boundaries need local supported water planes, not dry fills. */
public class PreservingRiverBankSupportTest {
    @BeforeClass public static void initialiseExport() {
        if (Configuration.getInstance() == null) Configuration.setInstance(new Configuration());
        ExportTestSupport.ensureReady();
    }

    @Test public void obliqueFortyLevelValleyClosesItsActualDryRasterEdgesWithoutEarthworks() {
        final Fixture fixture = valley(true);
        final Dimension dimension = fixture.dimension;
        assertEquals(95.578125, dimension.getHeightAt(95, 87), .01);
        assertEquals(95.49609375, dimension.getHeightAt(96, 87), .01);
        final Cell[] original = snapshot(fixture);
        final ShallowRiverCarver plan = plan(dimension);
        assertAccepted(plan, fixture);
        assertArrayEquals("Support refinement is read-only planning", original, snapshot(fixture));
        final ShallowRiverCarver.Result result = plan.apply();
        verifyBounds(fixture, original, result);
        // Before refinement this wet edge chose W96 beside a dry Y95 voxel.
        // The bank must remain untouched, while the touching water is lowered.
        assertTrue(dimension.getWaterLevelAt(95, 87) <= 95);
        assertEquals(original[87 * fixture.width + 96], cell(dimension, 96, 87));

        final Cell[] carved = snapshot(fixture);
        final Exported export = new Exported(new WorldPainterChunkFactory(dimension, Collections.emptyMap(),
                TestData.PLATFORM, TestData.MAX_HEIGHT), new HashMap<>());
        int sealed = 0, wetPartials = 0;
        for (int y = 72; y <= 104; y++) for (int x = 80; x <= 112; x++) {
            final int water = dimension.getWaterLevelAt(x, y), ground = dimension.getIntHeightAt(x, y);
            if (water <= ground) continue;
            assertEquals(MC_WATER, export.material(x, water, y).name);
            final Material floor = export.material(x, ground, y);
            if (SurfaceSmoother.isSmoothedSurfacePartial(floor)) {
                wetPartials++;
                assertTrue(Boolean.TRUE.equals(floor.getProperty(WATERLOGGED)));
                final Material support = export.material(x, ground - 1, y);
                assertTrue(support.solid);
                assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(support));
            }
            for (int[] offset : CARDINAL) {
                final int nx = x + offset[0], ny = y + offset[1];
                if (dimension.getWaterLevelAt(nx, ny) > dimension.getIntHeightAt(nx, ny)) continue;
                sealed++;
                assertEquals("No lateral air/slab gap at actual dry bank", MC_GRANITE,
                        export.material(nx, water, ny).name);
                assertEquals(original[ny * fixture.width + nx], cell(dimension, nx, ny));
            }
        }
        assertTrue("The former failing diagonal area must exercise bank support", sealed > 10);
        assertTrue("Underwater detail must not be disabled by the support repair", wetPartials > 0);
        assertArrayEquals(carved, snapshot(fixture));
    }

    @Test public void moreThanOneHundredDownhillWaterStepsAreBatchedRatherThanOnePassPerStep() {
        final Fixture fixture = valley(false);
        final Cell[] original = snapshot(fixture);
        final ShallowRiverCarver plan = plan(fixture.dimension);
        assertAccepted(plan, fixture);
        assertArrayEquals(original, snapshot(fixture));
        final ShallowRiverCarver.Result result = plan.apply();
        verifyBounds(fixture, original, result);
        final Set<Integer> levels = new HashSet<>();
        for (int x = fixture.sourceX; x < fixture.outletX - 2; x++) {
            assertTrue("Long valley may not have a dry centreline gap at " + x,
                    fixture.dimension.getWaterLevelAt(x, 64) > fixture.dimension.getIntHeightAt(x, 64));
            levels.add(fixture.dimension.getWaterLevelAt(x, 64));
        }
        assertTrue("Exercise many more water levels than the four bounded refinement passes", levels.size() > 100);
        assertEquals(0, fixture.dimension.getWaterLevelAt(fixture.outletX, fixture.outletY));
    }

    private static void verifyBounds(Fixture fixture, Cell[] original, ShallowRiverCarver.Result result) {
        assertTrue(result.changedCells() > 100);
        assertEquals(0, result.raisedCells());
        assertEquals(0, result.maximumFill(), 0);
        assertTrue(result.maximumCut() <= 1.85);
        for (int y = 0; y < fixture.height; y++) for (int x = 0; x < fixture.width; x++) {
            final Cell before = original[y * fixture.width + x], after = cell(fixture.dimension, x, y);
            if (before.water > Math.round(before.height) || after.water <= Math.round(after.height)) {
                assertEquals("Existing water and every dry field stay untouched at " + x + "," + y, before, after);
            } else {
                assertTrue(after.height <= before.height);
                assertTrue(before.height - after.height <= 1.85);
                assertTrue(after.water - after.height <= 1.1 + 1.0 / 256);
                assertTrue(after.water <= Math.round(before.height));
            }
        }
    }

    private static Fixture valley(boolean oblique) {
        final int width = oblique ? 512 : 1024, height = oblique ? 384 : 128;
        final int sourceX = 20, sourceY = 64, outletX = oblique ? 460 : 998, outletY = oblique ? 210 : 64;
        final int sea = oblique ? 62 : 0, drop = oblique ? 40 : 110;
        final double dx = outletX - sourceX, dy = outletY - sourceY, length = Math.hypot(dx, dy);
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, width, height), 100);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            final double along = ((x - sourceX) * dx + (y - sourceY) * dy) / length;
            final double across = Math.abs((x - sourceX) * dy - (y - sourceY) * dx) / length;
            final boolean wet = along >= length - 2;
            final double ground = sea + .375 + drop * Math.max(0, (length - 2 - along) / (length - 2))
                    + Math.min(100, Math.max(0, across - 8) * .6);
            dimension.setHeightAt(x, y, wet ? sea - 2 : (float) ground);
            dimension.setWaterLevelAt(x, y, wet ? sea : 0);
            dimension.setTerrainAt(x, y, Terrain.GRANITE);
            dimension.setLayerValueAt(Biome.INSTANCE, x, y, 4);
            dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, false);
        }
        return new Fixture(dimension, width, height, sourceX, sourceY, outletX, outletY);
    }

    private static ShallowRiverCarver plan(Dimension dimension) {
        final ShallowRiverCarver plan = new ShallowRiverCarver(dimension, 5, 12, 1.1, true, true, 733, null);
        plan.enableTerrainPreservation();
        return plan;
    }

    private static void assertAccepted(ShallowRiverCarver plan, Fixture fixture) {
        final boolean accepted = plan.addPath(new int[] { fixture.sourceX, fixture.outletX },
                new int[] { fixture.sourceY, fixture.outletY });
        assertTrue(plan.getLastRejection(), accepted);
    }

    private static Cell[] snapshot(Fixture fixture) {
        final Cell[] result = new Cell[fixture.width * fixture.height];
        for (int y = 0; y < fixture.height; y++) for (int x = 0; x < fixture.width; x++) {
            result[y * fixture.width + x] = cell(fixture.dimension, x, y);
        }
        return result;
    }

    private static Cell cell(Dimension dimension, int x, int y) {
        return new Cell(dimension.getHeightAt(x, y), dimension.getWaterLevelAt(x, y), dimension.getTerrainAt(x, y),
                dimension.getLayerValueAt(Biome.INSTANCE, x, y), dimension.getBitLayerValueAt(Frost.INSTANCE, x, y));
    }
    private record Fixture(Dimension dimension, int width, int height, int sourceX, int sourceY, int outletX, int outletY) { }
    private record Cell(float height, int water, Terrain terrain, int biome, boolean frost) { }
    private record Exported(WorldPainterChunkFactory factory, Map<Long, Chunk> chunks) {
        Material material(int x, int y, int z) {
            final int cx = x >> 4, cz = z >> 4;
            final long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
            return chunks.computeIfAbsent(key, ignored -> factory.createChunk(cx, cz).chunk).getMaterial(x & 15, y, z & 15);
        }
    }
    private static final int[][] CARDINAL = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
}
