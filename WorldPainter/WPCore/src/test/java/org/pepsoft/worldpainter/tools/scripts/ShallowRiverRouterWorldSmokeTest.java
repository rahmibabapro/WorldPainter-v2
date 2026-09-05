package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Assume;
import org.junit.Test;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.WorldIO;
import org.pepsoft.worldpainter.exporting.ExportTestSupport;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.River;
import org.pepsoft.worldpainter.layers.Void;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

/**
 * Opt-in real-world route measurement. Supply a backup through
 * {@code -Dworldpainter.test.riverWorld=<backup.world>} and optionally override
 * {@code -Dworldpainter.test.riverSeed=<long>} (default: the UI seed 1337).
 *
 * <p>This test ONLY loads the supplied file and builds a plan. It never applies
 * that plan, exports Minecraft data, saves a world, or loads the on-disk profile.
 * No private world data belongs in the repository. Without the property this
 * test is skipped before platform/configuration initialisation.
 */
public class ShallowRiverRouterWorldSmokeTest {
    @Test
    public void automaticNaturalRiverFindsARealWorldRouteWithoutChangingAnyInput() throws Exception {
        final String configured = System.getProperty("worldpainter.test.riverWorld");
        Assume.assumeTrue("Opt in with -Dworldpainter.test.riverWorld=<backup.world>",
                configured != null && !configured.isBlank());
        final Path input = Path.of(configured).toRealPath();
        assertTrue("The supplied backup must be a readable regular file", Files.isRegularFile(input) && Files.isReadable(input));
        final FileFingerprint fileBefore = fileFingerprint(input);

        // In-memory configuration prevents ExportTestSupport from reading or
        // migrating the user's actual %APPDATA% WorldPainter profile.
        if (Configuration.getInstance() == null) Configuration.setInstance(new Configuration());
        ExportTestSupport.ensureReady();
        final long loadStart = System.nanoTime();
        final WorldIO io = new WorldIO();
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(input))) {
            io.load(stream);
        }
        final double loadSeconds = secondsSince(loadStart);
        final World2 world = io.getWorld();
        assertNotNull("WorldIO must load an actual world", world);
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        assertNotNull("The world must contain the normal detail/surface dimension", dimension);

        final long seed = Long.getLong("worldpainter.test.riverSeed", 1337L);
        final double startWidth = Double.parseDouble(System.getProperty("worldpainter.test.riverStartWidth", "5"));
        final double endWidth = Double.parseDouble(System.getProperty("worldpainter.test.riverEndWidth", "12"));
        final double depth = Double.parseDouble(System.getProperty("worldpainter.test.riverDepth", "1.10"));
        final WorldFingerprint before = worldFingerprint(world, dimension);
        final long routeStart = System.nanoTime();
        int added = 0;
        int totalRounds = 0;
        ShallowRiverCarver plan = null;
        ShallowRiverRouter router = null;
        try {
            final double[] widths = { endWidth, Math.max(startWidth, Math.round(endWidth * 2 / 3)), startWidth };
            double previousWidth = Double.POSITIVE_INFINITY;
            for (double attemptedEndWidth : widths) {
                if (attemptedEndWidth >= previousWidth) continue;
                previousWidth = attemptedEndWidth;
                plan = new ShallowRiverCarver(dimension,
                        startWidth, attemptedEndWidth, depth, true, true, seed, null);
                if (Boolean.getBoolean("worldpainter.test.riverPreserveTerrain")) plan.enableTerrainPreservation();
                else plan.enableTerrainAdaptation();
                router = new ShallowRiverRouter(dimension, plan,
                        startWidth, attemptedEndWidth, depth, true, seed, null, null);
                router.enableMountainCourseSelection();
                System.out.printf(Locale.ROOT,
                        "River world smoke input=%s, bytes=%d, sha256=%s, world=%s, tiles=%d, extent=%s, "
                                + "dimensionSeed=%d, minecraftSeed=%d, routerSeed=%d, loadSeconds=%.3f, "
                                + "width=%.2f->%.2f, depth=%.2f, maxCut=%.2f, maxFill=%.2f, preserveTerrain=%s%n",
                        input, fileBefore.size, fileBefore.sha256, world.getName(), dimension.getTileCount(),
                        dimension.getExtent(), dimension.getSeed(), dimension.getMinecraftSeed(), seed, loadSeconds,
                        startWidth, attemptedEndWidth, depth, plan.getMaximumCut(), plan.getMaximumFill(),
                        plan.isTerrainPreservationEnabled());
                int rounds = 0;
                while (added < 1 && rounds < 6 && ((rounds == 0) || router.canContinueAutomaticSearch())) {
                    rounds++;
                    totalRounds++;
                    added += router.findAutomatic(1 - added);
                }
                if (added > 0) break;
            }
        } finally {
            final double routeSeconds = secondsSince(routeStart);
            final WorldFingerprint after = worldFingerprint(world, dimension);
            final FileFingerprint fileAfter = fileFingerprint(input);
            assertNotNull(plan);
            assertNotNull(router);
            System.out.printf(Locale.ROOT,
                    "River world smoke routeSeconds=%.3f, rounds=%d, accepted=%d, summary=%s%n"
                            + "River world smoke readOnlyBefore=%s, readOnlyAfter=%s, fileUnchanged=%s%n",
                    routeSeconds, totalRounds, plan.getAcceptedPaths(), router.getSummary(), before, after, fileBefore.equals(fileAfter));
            assertEquals("Route planning must leave every tracked world change counter and sampled cell unchanged", before, after);
            assertEquals("The supplied backup file must remain byte-for-byte unchanged", fileBefore, fileAfter);
        }
        assertEquals("A real-world route must be found within the bounded search: " + router.getSummary(), 1, added);
        assertEquals(1, plan.getAcceptedPaths());
        // Deliberately no plan application or save, even after a successful route.
    }

    private static WorldFingerprint worldFingerprint(World2 world, Dimension dimension) {
        final List<Tile> tiles = new ArrayList<>(dimension.getTiles());
        tiles.sort(Comparator.comparingInt(Tile::getX).thenComparingInt(Tile::getY));
        long hash = 0xcbf29ce484222325L;
        int samples = 0;
        for (Tile tile : tiles) {
            hash = mix(hash, tile.getX());
            hash = mix(hash, tile.getY());
            // A fixed 8-block sample grid plus each tile's far edge, while the
            // dimension/world counters cover writes between sampled positions.
            for (int gy = 0; gy <= 16; gy++) {
                for (int gx = 0; gx <= 16; gx++) {
                    final int x = Math.min(127, gx * 8), y = Math.min(127, gy * 8);
                    hash = mix(hash, Float.floatToIntBits(tile.getHeight(x, y)));
                    hash = mix(hash, tile.getWaterLevel(x, y));
                    hash = mix(hash, tile.getTerrain(x, y).ordinal());
                    hash = mix(hash, tile.getLayerValue(Biome.INSTANCE, x, y));
                    int layerBits = 0;
                    for (int i = 0; i < OBSERVED_BITS.length; i++) {
                        if (tile.getBitLayerValue(OBSERVED_BITS[i], x, y)) layerBits |= 1 << i;
                    }
                    hash = mix(hash, layerBits);
                    samples++;
                }
            }
        }
        return new WorldFingerprint(world.getChangeNo(), dimension.getChangeNo(), tiles.size(), samples,
                Long.toUnsignedString(hash, 16), dimension.getSurfaceSmoothing());
    }

    private static FileFingerprint fileFingerprint(Path path) throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] buffer = new byte[65536];
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            for (int length; (length = input.read(buffer)) >= 0;) {
                if (length > 0) digest.update(buffer, 0, length);
            }
        }
        return new FileFingerprint(Files.size(path), Files.getLastModifiedTime(path),
                HexFormat.of().formatHex(digest.digest()));
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }

    private static double secondsSince(long started) {
        return (System.nanoTime() - started) / 1_000_000_000.0;
    }

    private record FileFingerprint(long size, FileTime modified, String sha256) {}
    private record WorldFingerprint(long worldChanges, long dimensionChanges, int tiles, int samples,
                                    String sampledHash, Dimension.SurfaceSmoothing smoothing) {}

    private static final Layer[] OBSERVED_BITS = { FloodWithLava.INSTANCE, Void.INSTANCE,
            NotPresent.INSTANCE, NotPresentBlock.INSTANCE, ReadOnly.INSTANCE, River.INSTANCE, Frost.INSTANCE };
}
