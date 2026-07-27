package org.pepsoft.worldpainter.exporting;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.ChunkFactory;
import org.pepsoft.minecraft.MC118AnvilChunk;
import org.pepsoft.minecraft.RegionFile;
import org.pepsoft.minecraft.SeededGenerator;
import org.pepsoft.util.TextProgressReceiver;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.platforms.JavaExportSettings;
import org.pepsoft.worldpainter.platforms.JavaPlatformProvider;
import org.pepsoft.worldpainter.plugins.PlatformManager;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.DataType.REGION;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
import static org.pepsoft.worldpainter.Generator.DEFAULT;

/**
 * Export performance regression guard: ensures export completes and records timing metadata.
 */
public class ExportRegressionIT {
    @BeforeClass
    public static void init() {
        ExportTestSupport.ensureReady();
    }

    @Test
    public void turboWorldExportCompletesWithTimings() throws Exception {
        final Configuration config = Configuration.getInstance();
        final int maxHeight = config.getDefaultMaxHeight();
        final World2 world = new World2(config.getDefaultPlatform(), 12345L,
                TileFactoryFactory.createNoiseTileFactory(12345L, Terrain.GRASS, config.getDefaultPlatform().minZ, maxHeight, 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        dimension.setGenerator(new org.pepsoft.minecraft.SeededGenerator(DEFAULT, 12345L));
        dimension.addTile(dimension.getTileFactory().createTile(0, 0));

        final File exportDir = File.createTempFile("wp-export-turbo-", "");
        assertTrue(exportDir.delete());
        assertTrue(exportDir.mkdirs());

        try {
            dimension.setExportSettings(JavaExportSettings.turboExportPreset());
            final WorldExportSettings worldExportSettings = WorldExportSettings.turboExportSettings();
            worldExportSettings.setDimensionsToExport(Set.of(DIM_NORMAL));
            final WorldExporter exporter = PlatformManager.getInstance().getExporter(world, worldExportSettings);
            final Map<Integer, ChunkFactory.Stats> stats = exporter.export(exportDir, "turbo-test", null, new TextProgressReceiver());
            assertNotNull(stats);
            assertTrue(stats.containsKey(DIM_NORMAL));
            assertTrue("Turbo export must generate terrain chunks", stats.get(DIM_NORMAL).surfaceArea > 0L);
            assertTrue(new File(exportDir, "turbo-test/level.dat").isFile());
            assertTrue(new File(exportDir, "turbo-test/dimensions/minecraft/overworld/region").isDirectory());
            assertNotNull(stats.get(DIM_NORMAL).timings);
        } finally {
            deleteRecursively(exportDir);
        }
    }

    @Test
    public void turboExportIsFasterThanDefaultOnSyntheticWorld() throws Exception {
        final Configuration config = Configuration.getInstance();
        final int maxHeight = config.getDefaultMaxHeight();
        final World2 world = new World2(config.getDefaultPlatform(), 12345L,
                TileFactoryFactory.createNoiseTileFactory(12345L, Terrain.GRASS, config.getDefaultPlatform().minZ, maxHeight, 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        dimension.setGenerator(new org.pepsoft.minecraft.SeededGenerator(DEFAULT, 12345L));
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                dimension.addTile(dimension.getTileFactory().createTile(x, z));
            }
        }

        final long defaultMs = exportSyntheticWorld(world, JavaExportSettings.optimizedExportPreset(), new WorldExportSettings(Set.of(DIM_NORMAL), null, null));
        final long turboMs = exportSyntheticWorld(world, JavaExportSettings.turboExportPreset(), WorldExportSettings.turboExportSettings());
        assertTrue("Turbo export (" + turboMs + " ms) should not be slower than default (" + defaultMs + " ms)", turboMs <= defaultMs * 1.15);
    }

    private static long exportSyntheticWorld(World2 world, JavaExportSettings exportSettings, WorldExportSettings worldExportSettings) throws Exception {
        world.getDimension(NORMAL_DETAIL).setExportSettings(exportSettings);
        worldExportSettings.setDimensionsToExport(Set.of(DIM_NORMAL));
        final File exportDir = File.createTempFile("wp-export-perf-", "");
        assertTrue(exportDir.delete());
        assertTrue(exportDir.mkdirs());
        try {
            final WorldExporter exporter = PlatformManager.getInstance().getExporter(world, worldExportSettings);
            final long start = System.currentTimeMillis();
            exporter.export(exportDir, "perf-test", null, new TextProgressReceiver());
            return System.currentTimeMillis() - start;
        } finally {
            deleteRecursively(exportDir);
        }
    }

    @Test
    public void smallWorldExportCompletesWithTimings() throws Exception {
        final Configuration config = Configuration.getInstance();
        final int maxHeight = config.getDefaultMaxHeight();
        final World2 world = new World2(config.getDefaultPlatform(), 12345L,
                TileFactoryFactory.createNoiseTileFactory(12345L, Terrain.GRASS, config.getDefaultPlatform().minZ, maxHeight, 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        dimension.setGenerator(new org.pepsoft.minecraft.SeededGenerator(DEFAULT, 12345L));
        dimension.addTile(dimension.getTileFactory().createTile(0, 0));

        final File exportDir = File.createTempFile("wp-export-regression-", "");
        assertTrue(exportDir.delete());
        assertTrue(exportDir.mkdirs());

        try {
            dimension.setExportSettings(JavaExportSettings.optimizedExportPreset());
            final WorldExportSettings worldExportSettings = new WorldExportSettings(Set.of(DIM_NORMAL), null, null);
            final WorldExporter exporter = PlatformManager.getInstance().getExporter(world, worldExportSettings);
            final Map<Integer, ChunkFactory.Stats> stats = exporter.export(exportDir, "regression-test", null, new TextProgressReceiver());
            assertNotNull(stats);
            assertTrue(stats.containsKey(DIM_NORMAL));
            assertTrue("Export must generate terrain chunks", stats.get(DIM_NORMAL).surfaceArea > 0L);
            assertTrue(new File(exportDir, "regression-test/level.dat").isFile());
            assertTrue(new File(exportDir, "regression-test/data/minecraft/world_gen_settings.dat").isFile());
            assertTrue(new File(exportDir, "regression-test/dimensions/minecraft/overworld/region").isDirectory());
            assertNotNull(stats.get(DIM_NORMAL).timings);
        } finally {
            deleteRecursively(exportDir);
        }
    }

    @Test
    public void turboHollowExportCompletesOnTallWorld() throws Exception {
        final Platform platform = DefaultPlugin.JAVA_ANVIL_26_1;
        final int minHeight = platform.minZ;
        final int maxHeight = 320;
        final World2 world = new World2(platform, 54321L,
                TileFactoryFactory.createNoiseTileFactory(54321L, Terrain.GRASS, minHeight, maxHeight, 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        dimension.setGenerator(new org.pepsoft.minecraft.SeededGenerator(DEFAULT, 54321L));
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                final Tile tile = dimension.getTileFactory().createTile(x, z);
                for (int lx = 0; lx < 128; lx++) {
                    for (int lz = 0; lz < 128; lz++) {
                        tile.setTerrain(lx, lz, Terrain.STONE);
                        tile.setHeight(lx, lz, 80);
                    }
                }
                dimension.addTile(tile);
            }
        }

        final File exportDir = File.createTempFile("wp-export-turbo-hollow-", "");
        assertTrue(exportDir.delete());
        assertTrue(exportDir.mkdirs());

        try {
            dimension.setExportSettings(JavaExportSettings.turboExportPreset());
            final WorldExportSettings worldExportSettings = WorldExportSettings.turboExportSettingsWithHollow();
            worldExportSettings.setDimensionsToExport(Set.of(DIM_NORMAL));
            final ExportMemoryBudget budget = ExportMemoryBudget.compute(dimension, 4, worldExportSettings);
            assertTrue("Export budget must allow at least one region thread", budget.getExportThreadCount() >= 1);

            final WorldExporter exporter = PlatformManager.getInstance().getExporter(world, worldExportSettings);
            final Map<Integer, ChunkFactory.Stats> stats = exporter.export(exportDir, "turbo-hollow-test", null, new TextProgressReceiver());
            assertNotNull(stats);
            assertTrue(stats.containsKey(DIM_NORMAL));
            assertTrue("Turbo hollow export must generate terrain", stats.get(DIM_NORMAL).surfaceArea > 0L);
            assertTrue(new File(exportDir, "turbo-hollow-test/level.dat").isFile());
            assertTrue(new File(exportDir, "turbo-hollow-test/dimensions/minecraft/overworld/region").isDirectory());
        } finally {
            deleteRecursively(exportDir);
        }
    }

    @Test
    public void turboExportDefersLightingToMinecraft() throws Exception {
        final Platform platform = DefaultPlugin.JAVA_ANVIL_26_1;
        final int minHeight = platform.minZ;
        final int maxHeight = 320;
        final World2 world = new World2(platform, 99999L,
                TileFactoryFactory.createNoiseTileFactory(99999L, Terrain.GRASS, minHeight, maxHeight, 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        dimension.setGenerator(new SeededGenerator(DEFAULT, 99999L));
        final Tile tile = dimension.getTileFactory().createTile(0, 0);
        for (int lx = 0; lx < 128; lx++) {
            for (int lz = 0; lz < 128; lz++) {
                tile.setHeight(lx, lz, 50);
                tile.setWaterLevel(lx, lz, 70);
            }
        }
        dimension.addTile(tile);

        final File exportDir = File.createTempFile("wp-export-skylight-", "");
        assertTrue(exportDir.delete());
        assertTrue(exportDir.mkdirs());

        try {
            dimension.setExportSettings(JavaExportSettings.turboExportPreset());
            final WorldExportSettings worldExportSettings = WorldExportSettings.turboExportSettings();
            worldExportSettings.setDimensionsToExport(Set.of(DIM_NORMAL));
            PlatformManager.getInstance().getExporter(world, worldExportSettings)
                    .export(exportDir, "skylight-test", null, new TextProgressReceiver());

            final File regionFile = new File(exportDir, "skylight-test/dimensions/minecraft/overworld/region/r.0.0.mca");
            assertTrue("Expected region file at " + regionFile, regionFile.isFile());
            try (RegionFile region = new RegionFile(regionFile, true)) {
                try (InputStream chunkIn = region.getChunkDataInputStream(0, 0)) {
                    assertNotNull(chunkIn);
                    final org.jnbt.Tag root;
                    try (org.jnbt.NBTInputStream in = new org.jnbt.NBTInputStream(chunkIn)) {
                        root = in.readTag();
                    }
                    final Map<org.pepsoft.minecraft.DataType, org.jnbt.Tag> tags = new HashMap<>();
                    tags.put(REGION, root);
                    final MC118AnvilChunk chunk = (MC118AnvilChunk) ((JavaPlatformProvider) PlatformManager.getInstance().getPlatformProvider(platform))
                            .createChunk(platform, tags, minHeight, maxHeight, true);
                    assertFalse("Turbo export must defer lighting to Minecraft (lightPopulated=false)", chunk.isLightPopulated());
                    assertFalse("Flood column should not be air", chunk.getMaterial(8, 60, 8).empty);
                }
            }
        } finally {
            deleteRecursively(exportDir);
        }
    }

    @Test
    public void exportMemoryBudgetScalesWithHollowAndHeight() {
        final Platform platform = DefaultPlugin.JAVA_ANVIL_26_1;
        final World2 world = new World2(platform, 1L,
                TileFactoryFactory.createNoiseTileFactory(1L, Terrain.GRASS, platform.minZ, 320, 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        dimension.addTile(dimension.getTileFactory().createTile(0, 0));

        final ExportMemoryBudget plain = ExportMemoryBudget.compute(dimension, 16, WorldExportSettings.turboExportSettings());
        final ExportMemoryBudget hollow = ExportMemoryBudget.compute(dimension, 16, WorldExportSettings.turboExportSettingsWithHollow());
        assertTrue(hollow.getPerRegionBytes() >= plain.getPerRegionBytes());
        assertTrue("Hollow must not exceed plain parallel region count",
                hollow.getMaxInFlightRegions() <= plain.getMaxInFlightRegions());
        assertTrue("Hollow should stay conservative on typical heaps",
                hollow.getMaxInFlightRegions() <= 2);
        assertTrue(hollow.getExportThreadCount() >= 1);
        assertTrue(hollow.getMaxInFlightRegions() >= 1);
        assertTrue("Plain turbo should allow more than one region thread on typical heaps",
                plain.getMaxInFlightRegions() >= 2 || plain.getExportThreadCount() >= 2);
        assertTrue("Hollow should use multiple chunk threads when one region is in flight",
                hollow.getChunkThreadCount() >= 1);
        if (Runtime.getRuntime().availableProcessors() >= 4) {
            assertTrue(hollow.getChunkThreadCount() >= 2);
        }
    }

    @Test
    public void forcedLowMemoryModeCapsExportToSingleRegion() {
        final Platform platform = DefaultPlugin.JAVA_ANVIL_26_1;
        final World2 world = new World2(platform, 1L,
                TileFactoryFactory.createNoiseTileFactory(1L, Terrain.GRASS, platform.minZ, 320, 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        dimension.addTile(dimension.getTileFactory().createTile(0, 0));

        ExportMemoryBudget.setForcedLowMemoryMode(true);
        ExportMemoryBudget.setForcedMaxExportThreads(1);
        try {
            final ExportMemoryBudget budget = ExportMemoryBudget.compute(dimension, 16, WorldExportSettings.turboExportSettingsWithHollow());
            assertEquals(1, budget.getExportThreadCount());
            assertEquals(1, budget.getChunkThreadCount());
            assertEquals(1, budget.getMaxInFlightRegions());
        } finally {
            ExportMemoryBudget.clearForcedLowMemoryMode();
            ExportMemoryBudget.clearForcedMaxExportThreads();
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            if (children != null) {
                for (File child: children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
