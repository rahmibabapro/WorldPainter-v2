package org.pepsoft.worldpainter.layers.exporters;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.MC118AnvilChunk;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.exporting.ExportTestSupport;
import org.pepsoft.worldpainter.layers.Resources;
import org.pepsoft.worldpainter.layers.exporters.ResourcesExporter.ResourcesExporterSettings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Material.COAL;
import static org.pepsoft.minecraft.Material.DIAMOND_ORE;
import static org.pepsoft.minecraft.Material.IRON_ORE;
import static org.pepsoft.minecraft.Material.STONE;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
import static org.pepsoft.worldpainter.layers.exporters.ResourcesExporter.ResourcesExporterSettings.defaultSettings;

/**
 * Golden-chunk / determinism regression for ResourcesExporter (cluster + legacyNoise).
 */
public class ResourcesExporterGoldenChunkTest {
    private static final long SEED = 424242L;
    /** Chunk (0,0) within tile (0,0). */
    private static final int GOLDEN_CHUNK_X = 0;
    private static final int GOLDEN_CHUNK_Z = 0;
    private static final int FILL_MAX_Y = 60;

    @BeforeClass
    public static void init() {
        System.setProperty("org.pepsoft.worldpainter.classifier", "v2");
        ExportTestSupport.ensureReady();
    }

    @Test
    public void clusterIsDeterministicAcrossTwoRuns() {
        final String prev = System.getProperty("org.pepsoft.worldpainter.resources.legacyNoise");
        try {
            System.clearProperty("org.pepsoft.worldpainter.resources.legacyNoise");
            final Fingerprint a = fingerprint(false);
            final Fingerprint b = fingerprint(false);
            assertEquals("Cluster ore counts must be deterministic", a.counts, b.counts);
            assertEquals("Cluster golden-chunk fingerprint must be deterministic", a.chunkFingerprint, b.chunkFingerprint);
            assertTrue("Cluster must place coal", a.counts.getOrDefault(COAL.name, 0) > 0);
            assertTrue("Cluster must place iron", a.counts.getOrDefault(IRON_ORE.name, 0) > 0);
            assertTrue("Cluster must leave stone", a.counts.getOrDefault(STONE.name, 0) > 5_000);
            // Not saturated: ores should be a small fraction of stone volume
            final int ores = a.counts.getOrDefault(COAL.name, 0) + a.counts.getOrDefault(IRON_ORE.name, 0)
                    + a.counts.getOrDefault(DIAMOND_ORE.name, 0);
            assertTrue("Cluster must not saturate chunk with ore (ores=" + ores + ")", ores < 50_000);
        } finally {
            restoreProp(prev);
        }
    }

    @Test
    public void legacyNoiseIsDeterministicAcrossTwoRuns() {
        final String prev = System.getProperty("org.pepsoft.worldpainter.resources.legacyNoise");
        try {
            System.clearProperty("org.pepsoft.worldpainter.resources.legacyNoise");
            final Fingerprint a = fingerprint(true);
            final Fingerprint b = fingerprint(true);
            assertEquals("Legacy ore counts must be deterministic", a.counts, b.counts);
            assertEquals("Legacy golden-chunk fingerprint must be deterministic", a.chunkFingerprint, b.chunkFingerprint);
            assertTrue("Legacy must place coal", a.counts.getOrDefault(COAL.name, 0) > 0);
        } finally {
            restoreProp(prev);
        }
    }

    @Test
    public void clusterAndLegacyFingerprintsDiffer() {
        final String prev = System.getProperty("org.pepsoft.worldpainter.resources.legacyNoise");
        try {
            System.clearProperty("org.pepsoft.worldpainter.resources.legacyNoise");
            final Fingerprint cluster = fingerprint(false);
            final Fingerprint legacy = fingerprint(true);
            assertNotEquals("Cluster and legacy must produce different golden-chunk content",
                    cluster.chunkFingerprint, legacy.chunkFingerprint);
        } finally {
            restoreProp(prev);
        }
    }

    @Test
    public void respectsMaterialMaxLevelBoundary() {
        final String prev = System.getProperty("org.pepsoft.worldpainter.resources.legacyNoise");
        try {
            System.clearProperty("org.pepsoft.worldpainter.resources.legacyNoise");
            final World2 world = createWorld();
            final Dimension dimension = world.getDimension(NORMAL_DETAIL);
            final ResourcesExporterSettings settings = defaultSettings(
                    world.getPlatform(), dimension.getAnchor(), dimension.getMinHeight(), dimension.getMaxHeight());
            settings.setLegacyNoise(false);
            // Diamond max level is typically 15 — ensure nothing above maxLevel for diamond
            final int diamondMax = settings.getMaxLevel(DIAMOND_ORE);
            final ResourcesExporter exporter = new ResourcesExporter(dimension, world.getPlatform(), settings);
            final Tile tile = dimension.getTile(0, 0);
            final MC118AnvilChunk chunk = new MC118AnvilChunk(GOLDEN_CHUNK_X, GOLDEN_CHUNK_Z,
                    dimension.getMinHeight(), dimension.getMaxHeight());
            fillStone(chunk, dimension.getMinHeight(), FILL_MAX_Y);
            exporter.render(tile, chunk);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = diamondMax + 1; y <= FILL_MAX_Y; y++) {
                        final Material mat = chunk.getMaterial(x, y, z);
                        assertFalse("Diamond must not appear above maxLevel=" + diamondMax + " at y=" + y,
                                mat != null && mat.isNamed(DIAMOND_ORE.name));
                    }
                }
            }
        } finally {
            restoreProp(prev);
        }
    }

    private static Fingerprint fingerprint(boolean legacyNoise) {
        final World2 world = createWorld();
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        final ResourcesExporterSettings settings = defaultSettings(
                world.getPlatform(), dimension.getAnchor(), dimension.getMinHeight(), dimension.getMaxHeight());
        // Freeze seed offsets for reproducibility across defaultSettings Random
        freezeSeedOffsets(settings);
        settings.setLegacyNoise(legacyNoise);
        final ResourcesExporter exporter = new ResourcesExporter(dimension, world.getPlatform(), settings);
        final Tile tile = dimension.getTile(0, 0);
        final int minHeight = dimension.getMinHeight();
        final int maxHeight = dimension.getMaxHeight();
        final Map<String, Integer> totals = new TreeMap<>();
        String goldenFp = null;
        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                final MC118AnvilChunk chunk = new MC118AnvilChunk(cx, cz, minHeight, maxHeight);
                fillStone(chunk, minHeight, FILL_MAX_Y);
                exporter.render(tile, chunk);
                tally(chunk, minHeight, maxHeight, totals);
                if (cx == GOLDEN_CHUNK_X && cz == GOLDEN_CHUNK_Z) {
                    goldenFp = hashChunk(chunk, minHeight, FILL_MAX_Y);
                }
            }
        }
        assertNotNull(goldenFp);
        return new Fingerprint(totals, goldenFp);
    }

    private static void freezeSeedOffsets(ResourcesExporterSettings settings) {
        long offset = 1L;
        for (Material material : settings.getMaterials()) {
            settings.setSeedOffset(material, offset++);
        }
    }

    private static World2 createWorld() {
        final Configuration config = Configuration.getInstance();
        final Platform platform = config.getDefaultPlatform();
        final int maxHeight = config.getDefaultMaxHeight();
        final World2 world = new World2(platform, SEED,
                TileFactoryFactory.createNoiseTileFactory(SEED, Terrain.GRASS, platform.minZ, maxHeight, 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        dimension.addTile(dimension.getTileFactory().createTile(0, 0));
        final Tile tile = dimension.getTileForEditing(0, 0);
        for (int x = 0; x < Constants.TILE_SIZE; x++) {
            for (int y = 0; y < Constants.TILE_SIZE; y++) {
                tile.setLayerValue(Resources.INSTANCE, x, y, 8);
                tile.setHeight(x, y, 62);
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

    private static void tally(MC118AnvilChunk chunk, int minY, int maxY, Map<String, Integer> totals) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    final Material mat = chunk.getMaterial(x, y, z);
                    if (mat != null) {
                        totals.merge(mat.name, 1, Integer::sum);
                    }
                }
            }
        }
    }

    private static String hashChunk(MC118AnvilChunk chunk, int minY, int maxYInclusive) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int y = minY; y <= maxYInclusive; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        final Material mat = chunk.getMaterial(x, y, z);
                        final String name = mat != null ? mat.name : "air";
                        md.update(name.getBytes(StandardCharsets.UTF_8));
                        md.update((byte) (y & 0xff));
                        md.update((byte) x);
                        md.update((byte) z);
                    }
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void restoreProp(String prev) {
        if (prev != null) {
            System.setProperty("org.pepsoft.worldpainter.resources.legacyNoise", prev);
        } else {
            System.clearProperty("org.pepsoft.worldpainter.resources.legacyNoise");
        }
    }

    private static final class Fingerprint {
        final Map<String, Integer> counts;
        final String chunkFingerprint;

        Fingerprint(Map<String, Integer> counts, String chunkFingerprint) {
            this.counts = counts;
            this.chunkFingerprint = chunkFingerprint;
        }
    }
}
