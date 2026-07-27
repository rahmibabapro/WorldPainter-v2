package org.pepsoft.worldpainter.util;

import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.exporting.ExportMemoryBudget;
import org.pepsoft.worldpainter.exporting.WorldExportSettings;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Utility methods for working with threads and concurrency.
 */
public final class ThreadUtils {
    private ThreadUtils() {
        // Don't instantiate
    }

    /**
     * Choose a thread count taking into account the number of processor cores and the number of jobs.
     *
     * @param operation The name of the operation for use in the log entry.
     * @param jobCount  The number of jobs to be executed.
     * @return The number of threads to use.
     */
    public static int chooseThreadCount(String operation, int jobCount) {
        final Runtime runtime = Runtime.getRuntime();
        final int threadCount;
        final String sysProp = System.getProperty("org.pepsoft.worldpainter.threads");
        if (sysProp != null) {
            threadCount = Math.max(Math.min(Integer.parseInt(sysProp), jobCount), 1);
            logger.info("Using " + threadCount + " thread(s) for " + operation + " (max. thread count source: org.pepsoft.worldpainter.threads advanced setting set to " + sysProp + ")");
        } else {
            threadCount = Math.max(Math.min(runtime.availableProcessors(), jobCount), 1);
            logger.info("Using " + threadCount + " thread(s) for " + operation + " (max. thread count source: logical processors: " + runtime.availableProcessors() + ")");
        }
        return threadCount;
    }

    /**
     * Choose a thread count for region export, respecting available heap and hollow-export overhead.
     */
    public static int chooseThreadCountForExport(String operation, int regionCount) {
        return chooseThreadCountForExport(operation, regionCount, false);
    }

    /**
     * Legacy export thread count (no dimension context). Prefer {@link #planExport(Dimension, int, WorldExportSettings)}.
     */
    public static int chooseThreadCountForExport(String operation, int regionCount, boolean hollowInterior) {
        final Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        final long totalMemory = runtime.totalMemory();
        final long freeMemory = runtime.freeMemory();
        final long memoryInUse = totalMemory - freeMemory;
        final long maxMemory = runtime.maxMemory();
        final long maxMemoryAvailable = maxMemory - memoryInUse;
        final long memoryPerRegion = REQUIRED_MEMORY_PER_REGION_EXPORT
                + (hollowInterior ? HOLLOW_EXTRA_MEMORY_PER_REGION : 0L);
        final int maxThreadsByMem = (int) Math.max((maxMemoryAvailable - EXPORT_MEMORY_RESERVE) / memoryPerRegion, 1);

        int threadCount;
        final String sysProp = System.getProperty("org.pepsoft.worldpainter.threads");
        final Integer configProp = (Configuration.getInstance() != null) ? Configuration.getInstance().getMaxThreadCount() : null;
        if (sysProp != null) {
            threadCount = Integer.parseInt(sysProp);
        } else if (configProp != null) {
            threadCount = configProp;
        } else {
            threadCount = runtime.availableProcessors();
        }

        final int capped = Math.max(Math.min(Math.min(Math.min(threadCount, maxThreadsByMem), runtime.availableProcessors()), regionCount), 1);
        mostRecentThreadCount = capped;
        logger.info("Using {} thread(s) for {} (legacy budget, hollow={})", capped, operation, hollowInterior);
        return capped;
    }

    /**
     * Plan export threads using full world + settings memory budget.
     */
    public static ExportMemoryBudget planExport(Dimension dimension, int regionCount, WorldExportSettings settings) {
        final ExportMemoryBudget budget = ExportMemoryBudget.compute(dimension, regionCount, settings);
        mostRecentThreadCount = budget.getExportThreadCount();
        return budget;
    }

    /**
     * Chunk-generation pool size that shares the heap with export workers.
     */
    public static int chooseChunkThreadCountForExport(int exportThreadCount) {
        final int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(Math.min(exportThreadCount, processors / 2 + 1), 8));
    }

    /**
     * Chunk threads from a full export budget (preferred).
     */
    public static int chooseChunkThreadCountForExport(ExportMemoryBudget budget) {
        return budget.getChunkThreadCount();
    }

    /**
     * Get the value most recently returned by export thread planning, if any.
     */
    public static Integer getMostRecentThreadCount() {
        return mostRecentThreadCount;
    }

    /** Approximate bytes per concurrently exported region (chunk buffers + region file). */
    public static final long REQUIRED_MEMORY_PER_REGION_EXPORT = 280_000_000L;

    /** Additional bytes per region when interior hollowing is enabled. */
    public static final long HOLLOW_EXTRA_MEMORY_PER_REGION = 120_000_000L;

    /** Headroom reserved for the loaded world, UI and GC during export. */
    private static final long EXPORT_MEMORY_RESERVE = 768_000_000L;

    private static final Logger logger = getLogger(ThreadUtils.class);
    private static volatile Integer mostRecentThreadCount;
}
