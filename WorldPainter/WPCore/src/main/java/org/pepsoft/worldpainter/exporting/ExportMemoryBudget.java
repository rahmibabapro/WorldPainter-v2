package org.pepsoft.worldpainter.exporting;

import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.slf4j.Logger;

import static org.pepsoft.worldpainter.exporting.WorldExportSettings.Step.LIGHTING;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Estimates safe export parallelism from heap headroom, loaded world size, and export options.
 */
public final class ExportMemoryBudget {
    /** Matches {@code NewWorldDialog.ESTIMATED_TILE_DATA_SIZE} (KB per tile). */
    private static final long ESTIMATED_TILE_BYTES = 81L * 1024L;
    private static final long MIN_EXPORT_MEMORY_RESERVE = 768_000_000L;
    /** Bytes per concurrently exported region without hollow (legacy export budget ~280 MB). */
    private static final long PLAIN_REGION_PARALLEL_BYTES = 300_000_000L;
    /** Extra bytes charged only when hollow is enabled (ExteriorGrid + peak chunk edits). */
    private static final long HOLLOW_PARALLEL_EXTRA_BYTES = 360_000_000L;
    private static final long CHUNK_POOL_BYTES_PER_THREAD = 72_000_000L;
    /** Plain (non-hollow) export can use more chunk workers; hollow stays conservative. */
    private static final int MAX_PLAIN_CHUNK_THREADS = 8;
    private static final int MAX_HOLLOW_CHUNK_THREADS = 4;
    private static final int MAX_PLAIN_IN_FLIGHT = 6;
    private static final int MAX_HOLLOW_IN_FLIGHT = 2;
    private static final int HEIGHT_REFERENCE = 256;
    /** Leave headroom for GC spikes and JVM overhead beyond the estimate. */
    private static final float PLAIN_MEMORY_SAFETY_FACTOR = 0.72f;
    /** Hollow needs extra margin because peak usage often exceeds the per-region estimate. */
    private static final float HOLLOW_MEMORY_SAFETY_FACTOR = 0.48f;
    /** Hollow stays single-region below this heap unless forced low-memory mode is active. */
    private static final long HOLLOW_MULTI_REGION_HEAP_THRESHOLD = 32L * 1024L * 1024L * 1024L;

    private final int exportThreadCount;
    private final int chunkThreadCount;
    private final int maxInFlightRegions;
    private final long availableBytes;
    private final long perRegionBytes;

    private ExportMemoryBudget(int exportThreadCount, int chunkThreadCount, int maxInFlightRegions, long availableBytes, long perRegionBytes) {
        this.exportThreadCount = exportThreadCount;
        this.chunkThreadCount = chunkThreadCount;
        this.maxInFlightRegions = maxInFlightRegions;
        this.availableBytes = availableBytes;
        this.perRegionBytes = perRegionBytes;
    }

    public int getExportThreadCount() {
        return exportThreadCount;
    }

    public int getChunkThreadCount() {
        return chunkThreadCount;
    }

    public int getMaxInFlightRegions() {
        return maxInFlightRegions;
    }

    public long getAvailableBytes() {
        return availableBytes;
    }

    public long getPerRegionBytes() {
        return perRegionBytes;
    }

    public boolean isLowHeadroom() {
        return availableBytes < 2_147_483_648L; // 2 GB
    }

    public boolean isCriticalHeadroom() {
        return availableBytes < 1_073_741_824L; // 1 GB
    }

    /**
     * Force a lower export thread cap for the current thread (e.g. OOM retry).
     */
    public static void setForcedMaxExportThreads(Integer maxThreads) {
        if (maxThreads == null) {
            forcedMaxExportThreads.remove();
        } else {
            forcedMaxExportThreads.set(maxThreads);
        }
    }

    public static void clearForcedMaxExportThreads() {
        forcedMaxExportThreads.remove();
    }

    /**
     * Force single-region export and a single chunk-generation thread (OOM retry).
     */
    public static void setForcedLowMemoryMode(boolean enabled) {
        if (enabled) {
            forcedLowMemoryMode.set(Boolean.TRUE);
        } else {
            forcedLowMemoryMode.remove();
        }
    }

    public static void clearForcedLowMemoryMode() {
        forcedLowMemoryMode.remove();
    }

    /**
     * Compute export parallelism for the given dimension and settings.
     *
     * @param dimension     Surface (or combined) dimension being exported
     * @param regionCount   Number of Minecraft regions to export
     * @param settings      World export settings (hollow, turbo, etc.)
     * @return Thread and in-flight region limits
     */
    public static ExportMemoryBudget compute(Dimension dimension, int regionCount, WorldExportSettings settings) {
        final Runtime runtime = Runtime.getRuntime();
        final boolean hollow = settings != null && settings.isHollowInterior();
        final boolean turboPlain = WorldExportSettings.isTurboExport(settings) && (! hollow);
        // Hollow / non-turbo need a fresher heap reading; plain turbo skips a full GC pause.
        if (! turboPlain) {
            runtime.gc();
        }
        final long maxMemory = runtime.maxMemory();
        final long memoryInUse = runtime.totalMemory() - runtime.freeMemory();
        final long worldFootprint = estimateWorldFootprint(dimension);
        final long reserve = Math.max(MIN_EXPORT_MEMORY_RESERVE, (long) (maxMemory * 0.10));
        final long rawAvailable = Math.max(maxMemory - memoryInUse - worldFootprint - reserve, 0L);

        final int minHeight = dimension.getMinHeight();
        final int maxHeight = dimension.getMaxHeight();
        final int heightSpan = Math.max(maxHeight - minHeight, HEIGHT_REFERENCE);
        final float heightFactor = heightSpan / (float) HEIGHT_REFERENCE;

        final float safetyFactor = hollow ? HOLLOW_MEMORY_SAFETY_FACTOR : PLAIN_MEMORY_SAFETY_FACTOR;
        final long available = (long) (rawAvailable * safetyFactor);
        final long parallelRegionBytes = parallelRegionBytes(heightFactor, hollow);

        final int processors = runtime.availableProcessors();
        int requested = processors;
        final String sysProp = System.getProperty("org.pepsoft.worldpainter.threads");
        final Integer configProp = (Configuration.getInstance() != null) ? Configuration.getInstance().getMaxThreadCount() : null;
        if (sysProp != null) {
            requested = Integer.parseInt(sysProp);
        } else if (configProp != null) {
            requested = configProp;
        }
        final Integer forced = forcedMaxExportThreads.get();
        if (forced != null) {
            requested = Math.min(requested, forced);
        }
        final boolean forcedLowMemory = Boolean.TRUE.equals(forcedLowMemoryMode.get());
        if (forcedLowMemory) {
            requested = 1;
        }

        int chunkThreads;
        if (forcedLowMemory) {
            chunkThreads = 1;
        } else if (hollow) {
            final int desired = Math.min(Math.max(2, processors / 2), MAX_HOLLOW_CHUNK_THREADS);
            final long heapAfterOneRegion = Math.max(available - parallelRegionBytes, 0L);
            chunkThreads = Math.max(1, Math.min(desired, (int) Math.max(Math.min(heapAfterOneRegion / CHUNK_POOL_BYTES_PER_THREAD, MAX_HOLLOW_CHUNK_THREADS), 1)));
        } else {
            chunkThreads = Math.max(1, Math.min(processors / 2 + 1, MAX_PLAIN_CHUNK_THREADS));
        }
        final long chunkPoolOverhead = chunkThreads * CHUNK_POOL_BYTES_PER_THREAD;
        final long budgetable = Math.max(available - chunkPoolOverhead, 0L);

        int maxInFlight = parallelRegionBytes > 0
                ? Math.max((int) (budgetable / parallelRegionBytes), 1)
                : 1;
        if (forcedLowMemory) {
            maxInFlight = 1;
        } else if (hollow) {
            maxInFlight = 1;
            if (maxMemory >= HOLLOW_MULTI_REGION_HEAP_THRESHOLD && budgetable >= 3L * parallelRegionBytes) {
                maxInFlight = Math.min(2, Math.max((int) (budgetable / parallelRegionBytes), 1));
            }
            if (maxMemory >= 48L * 1024L * 1024L * 1024L && budgetable >= 5L * parallelRegionBytes) {
                maxInFlight = Math.min(MAX_HOLLOW_IN_FLIGHT, Math.max((int) (budgetable / parallelRegionBytes), 1));
            }
        } else {
            final int legacyCap = (int) Math.max((maxMemory - memoryInUse - MIN_EXPORT_MEMORY_RESERVE) / parallelRegionBytes, 1);
            maxInFlight = Math.min(Math.max(maxInFlight, legacyCap), MAX_PLAIN_IN_FLIGHT);
        }

        final int exportThreads = Math.max(Math.min(Math.min(Math.min(requested, maxInFlight), processors), regionCount), 1);
        final int cappedInFlight = Math.max(Math.min(maxInFlight, exportThreads), 1);

        final ExportMemoryBudget budget = new ExportMemoryBudget(exportThreads, chunkThreads, cappedInFlight, available, parallelRegionBytes);
        logger.info("Export memory budget: available={} MB (raw {} MB), worldFootprint={} MB, perRegion={} MB, hollow={}, deferredSkylight={}, "
                        + "exportThreads={}, chunkThreads={}, maxInFlight={}",
                available / 1_048_576L,
                rawAvailable / 1_048_576L,
                worldFootprint / 1_048_576L,
                parallelRegionBytes / 1_048_576L,
                hollow,
                usesDeferredSkylightPass(settings),
                exportThreads,
                chunkThreads,
                cappedInFlight);
        if (exportThreads < requested) {
            logger.info("Capped export threads from {} to {} (memory budget)", requested, exportThreads);
        }
        return budget;
    }

    /**
     * Memory charged per concurrently exported region. Turbo no longer runs a skylight pass, so plain turbo can keep
     * multiple regions in flight on 16 GB systems without charging extra lighting buffers.
     */
    private static long parallelRegionBytes(float heightFactor, boolean hollow) {
        long bytes = (long) (PLAIN_REGION_PARALLEL_BYTES * heightFactor);
        if (hollow) {
            bytes += (long) (HOLLOW_PARALLEL_EXTRA_BYTES * heightFactor);
        }
        return bytes;
    }

    private static boolean usesDeferredSkylightPass(WorldExportSettings settings) {
        if (settings == null) {
            return false;
        }
        final var skipped = settings.getStepsToSkip();
        return (skipped != null) && skipped.contains(LIGHTING);
    }

    private static long estimateWorldFootprint(Dimension dimension) {
        if (dimension == null) {
            return 0L;
        }
        return (long) dimension.getTileCount() * ESTIMATED_TILE_BYTES;
    }

    private static final ThreadLocal<Integer> forcedMaxExportThreads = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> forcedLowMemoryMode = new ThreadLocal<>();
    private static final Logger logger = getLogger(ExportMemoryBudget.class);
}
