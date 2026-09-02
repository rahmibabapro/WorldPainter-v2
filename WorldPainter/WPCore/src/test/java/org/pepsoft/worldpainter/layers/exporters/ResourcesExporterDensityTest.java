package org.pepsoft.worldpainter.layers.exporters;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.minecraft.MC118AnvilChunk;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.exporting.ExportTestSupport;
import org.pepsoft.worldpainter.layers.Resources;
import org.pepsoft.worldpainter.layers.exporters.ResourcesExporter.ResourcesExporterSettings;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Material.COAL;
import static org.pepsoft.minecraft.Material.IRON_ORE;
import static org.pepsoft.minecraft.Material.STONE;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
import static org.pepsoft.worldpainter.layers.exporters.ResourcesExporter.ResourcesExporterSettings.defaultSettings;

/**
 * Density regression: cluster placer must produce non-empty ore and stay within a band vs legacy Perlin.
 */
public class ResourcesExporterDensityTest {
    private static final long SEED = 424242L;
    private static final double MIN_RATIO = 0.15;
    private static final double MAX_RATIO = 6.0;

    @BeforeClass
    public static void init() {
        System.setProperty("org.pepsoft.worldpainter.classifier", "v2");
        ExportTestSupport.ensureReady();
    }

    @Test
    public void clusterProducesOreAndMatchesLegacyBand() {
        final Map<Material, Integer> legacy = countMaterials(true);
        final Map<Material, Integer> cluster = countMaterials(false);

        final int legacyCoal = legacy.getOrDefault(COAL, 0);
        final int clusterCoal = cluster.getOrDefault(COAL, 0);
        final int legacyIron = legacy.getOrDefault(IRON_ORE, 0);
        final int clusterIron = cluster.getOrDefault(IRON_ORE, 0);

        assertTrue("Legacy path must place some coal (got " + legacyCoal + ")", legacyCoal > 0);
        assertTrue("Cluster path must place some coal (got " + clusterCoal + ")", clusterCoal > 0);
        assertTrue("Legacy path must place some iron (got " + legacyIron + ")", legacyIron > 0);
        assertTrue("Cluster path must place some iron (got " + clusterIron + ")", clusterIron > 0);

        assertRatioInBand("coal", legacyCoal, clusterCoal);
        assertRatioInBand("iron", legacyIron, clusterIron);

        // Must not obliterate the entire stone volume
        final int stoneLeft = cluster.getOrDefault(STONE, 0);
        assertTrue("Cluster must leave substantial stone (got " + stoneLeft + ")", stoneLeft > 10_000);
    }

    @Test
    public void syspropForcesLegacyNoise() {
        final String prev = System.getProperty("org.pepsoft.worldpainter.resources.legacyNoise");
        try {
            System.setProperty("org.pepsoft.worldpainter.resources.legacyNoise", "true");
            final World2 world = createWorld();
            final Dimension dimension = world.getDimension(NORMAL_DETAIL);
            final ResourcesExporterSettings settings = defaultSettings(
                    world.getPlatform(), dimension.getAnchor(), dimension.getMinHeight(), dimension.getMaxHeight());
            settings.setLegacyNoise(false); // settings say cluster, sysprop wins
            final ResourcesExporter exporter = new ResourcesExporter(dimension, world.getPlatform(), settings);
            // Reflect via render path: if sysprop works, behaviour matches explicit legacy
            final Map<Material, Integer> forced = countWithExporter(world, exporter);
            System.setProperty("org.pepsoft.worldpainter.resources.legacyNoise", "false");
            final ResourcesExporterSettings clusterSettings = defaultSettings(
                    world.getPlatform(), dimension.getAnchor(), dimension.getMinHeight(), dimension.getMaxHeight());
            clusterSettings.setLegacyNoise(false);
            final Map<Material, Integer> cluster = countWithExporter(world,
                    new ResourcesExporter(dimension, world.getPlatform(), clusterSettings));
            // Forced-legacy and cluster should differ for at least one ore (different algorithms)
            assertNotEquals("Sysprop legacyNoise=true should not use identical cluster placement",
                    forced.getOrDefault(COAL, 0), cluster.getOrDefault(COAL, 0));
        } finally {
            if (prev != null) {
                System.setProperty("org.pepsoft.worldpainter.resources.legacyNoise", prev);
            } else {
                System.clearProperty("org.pepsoft.worldpainter.resources.legacyNoise");
            }
        }
    }

    private static void assertRatioInBand(String name, int legacy, int cluster) {
        final double ratio = cluster / (double) legacy;
        assertTrue(name + " cluster/legacy ratio " + ratio + " out of band [" + MIN_RATIO + "," + MAX_RATIO + "]"
                        + " (legacy=" + legacy + ", cluster=" + cluster + ")",
                ratio >= MIN_RATIO && ratio <= MAX_RATIO);
    }

    private static Map<Material, Integer> countMaterials(boolean legacyNoise) {
        final String prev = System.getProperty("org.pepsoft.worldpainter.resources.legacyNoise");
        try {
            System.clearProperty("org.pepsoft.worldpainter.resources.legacyNoise");
            final World2 world = createWorld();
            final Dimension dimension = world.getDimension(NORMAL_DETAIL);
            final ResourcesExporterSettings settings = defaultSettings(
                    world.getPlatform(), dimension.getAnchor(), dimension.getMinHeight(), dimension.getMaxHeight());
            settings.setLegacyNoise(legacyNoise);
            return countWithExporter(world, new ResourcesExporter(dimension, world.getPlatform(), settings));
        } finally {
            if (prev != null) {
                System.setProperty("org.pepsoft.worldpainter.resources.legacyNoise", prev);
            } else {
                System.clearProperty("org.pepsoft.worldpainter.resources.legacyNoise");
            }
        }
    }

    private static Map<Material, Integer> countWithExporter(World2 world, ResourcesExporter exporter) {
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        final Tile tile = dimension.getTile(0, 0);
        assertNotNull(tile);
        final int minHeight = dimension.getMinHeight();
        final int maxHeight = dimension.getMaxHeight();
        final Map<Material, Integer> totals = new HashMap<>();
        // One WP tile = 8x8 chunks
        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                final MC118AnvilChunk chunk = new MC118AnvilChunk(cx, cz, minHeight, maxHeight);
                fillStone(chunk, minHeight, 60);
                exporter.render(tile, chunk);
                tally(chunk, minHeight, maxHeight, totals);
            }
        }
        return totals;
    }

    private static World2 createWorld() {
        final Configuration config = Configuration.getInstance();
        final Platform platform = config.getDefaultPlatform();
        final int maxHeight = config.getDefaultMaxHeight();
        final World2 world = new World2(platform, SEED,
                TileFactoryFactory.createNoiseTileFactory(SEED, Terrain.GRASS, platform.minZ, maxHeight, 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        dimension.addTile(dimension.getTileFactory().createTile(0, 0));
        // Ensure Resources is present at default minimum intensity
        final Tile tile = dimension.getTileForEditing(0, 0);
        for (int x = 0; x < Constants.TILE_SIZE; x++) {
            for (int y = 0; y < Constants.TILE_SIZE; y++) {
                tile.setLayerValue(Resources.INSTANCE, x, y, 8);
            }
        }
        return world;
    }

    private static void fillStone(MC118AnvilChunk chunk, int minY, int maxYInclusive) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxYInclusive; y++) {
                    chunk.setMaterial(x, y, z, STONE);
                }
            }
        }
    }

    private static void tally(MC118AnvilChunk chunk, int minY, int maxY, Map<Material, Integer> totals) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    final Material mat = chunk.getMaterial(x, y, z);
                    if (mat != null) {
                        totals.merge(mat, 1, Integer::sum);
                    }
                }
            }
        }
    }
}
