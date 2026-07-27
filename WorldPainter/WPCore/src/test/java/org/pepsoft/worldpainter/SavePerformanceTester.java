package org.pepsoft.worldpainter;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.TextProgressReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Benchmark WorldPainter save/load performance: duration, tile count, peak heap.
 *
 * <p>Usage: {@code SavePerformanceTester <world-file>}
 */
public class SavePerformanceTester extends AbstractTool {
    public static void main(String[] args) throws IOException, UnloadableWorldException {
        if (args.length < 1) {
            System.err.println("Usage: SavePerformanceTester <world-file>");
            System.exit(1);
        }

        initialisePlatform();
        final File worldFile = new File(args[0]);
        final WorldIO worldIO = new WorldIO();
        final long loadStart = System.nanoTime();
        worldIO.load(new FileInputStream(worldFile));
        final long loadNanos = System.nanoTime() - loadStart;
        final World2 world = worldIO.getWorld();
        final int tileCount = world.getDimensions().stream().mapToInt(Dimension::getTileCount).sum();
        logger.info("Loaded {} ({} tiles) in {} ms", world.getName(), tileCount, loadNanos / 1_000_000L);

        final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        memoryBean.gc();
        final long heapBefore = memoryBean.getHeapMemoryUsage().getUsed();

        final File legacyOut = File.createTempFile("wp-save-legacy-", ".world");
        final File compartmentOut = File.createTempFile("wp-save-compartment-", ".world");
        try {
            final Configuration config = Configuration.getInstance();
            config.setCompartmentalisedWorldFormat(false);
            final long legacyStart = System.nanoTime();
            new WorldIO(world).save(new FileOutputStream(legacyOut));
            final long legacyNanos = System.nanoTime() - legacyStart;
            logger.info("Legacy save: {} ms, {} bytes", legacyNanos / 1_000_000L, legacyOut.length());

            config.setCompartmentalisedWorldFormat(true);
            final long compartmentStart = System.nanoTime();
            new WorldIO(world).save(new FileOutputStream(compartmentOut));
            final long compartmentNanos = System.nanoTime() - compartmentStart;
            logger.info("Compartmentalised save: {} ms, {} bytes", compartmentNanos / 1_000_000L, compartmentOut.length());

            final long peakHeap = memoryBean.getHeapMemoryUsage().getUsed() - heapBefore;
            logger.info("Peak heap delta during save: {} MB", peakHeap / 1_048_576L);
            logger.info("Summary: tiles={}, legacySaveMs={}, compartmentSaveMs={}",
                    tileCount, legacyNanos / 1_000_000L, compartmentNanos / 1_000_000L);
        } finally {
            if (! legacyOut.delete()) {
                logger.warn("Could not delete {}", legacyOut);
            }
            if (! compartmentOut.delete()) {
                logger.warn("Could not delete {}", compartmentOut);
            }
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(SavePerformanceTester.class);
}
