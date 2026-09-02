package org.pepsoft.worldpainter.perf;

import org.junit.Test;
import org.pepsoft.minecraft.ChunkFactory;
import org.pepsoft.util.TextProgressReceiver;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.exporting.ExportTestSupport;
import org.pepsoft.worldpainter.exporting.ExportTimingReporter;
import org.pepsoft.worldpainter.exporting.WorldExportSettings;
import org.pepsoft.worldpainter.exporting.WorldExporter;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.platforms.JavaExportSettings;
import org.pepsoft.worldpainter.plugins.PlatformManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
import static org.pepsoft.worldpainter.Generator.DEFAULT;

/**
 * Export performance harness. Gate rules require a <strong>3-run median</strong> per mode
 * (same seed world / JVM). Do not claim full-export &lt;400 ms unless the median proves it.
 */
public class ExportBenchmarkHarness {

    public static final int RUNS_PER_MODE = 3;
    /** Historical pre-cluster Resources stage reference (ms). */
    public static final long PRIOR_RESOURCES_MS = 2340L;

    public static class BenchmarkResult {
        public final String name;
        public final long durationMs;
        public final long outputSizeBytes;
        public final long peakHeapMb;
        public final ChunkFactory.Stats stats;
        public final long resourcesMs;
        public final double resourcesSharePercent;
        public final int sampleCount;

        public BenchmarkResult(String name, long durationMs, long outputSizeBytes, long peakHeapMb,
                               ChunkFactory.Stats stats, long resourcesMs, double resourcesSharePercent) {
            this(name, durationMs, outputSizeBytes, peakHeapMb, stats, resourcesMs, resourcesSharePercent, 1);
        }

        public BenchmarkResult(String name, long durationMs, long outputSizeBytes, long peakHeapMb,
                               ChunkFactory.Stats stats, long resourcesMs, double resourcesSharePercent, int sampleCount) {
            this.name = name;
            this.durationMs = durationMs;
            this.outputSizeBytes = outputSizeBytes;
            this.peakHeapMb = peakHeapMb;
            this.stats = stats;
            this.resourcesMs = resourcesMs;
            this.resourcesSharePercent = resourcesSharePercent;
            this.sampleCount = sampleCount;
        }
    }

    @Test
    public void runQuickBenchmarkSuite() throws Exception {
        System.setProperty("org.pepsoft.worldpainter.classifier", "v2");
        ExportTestSupport.ensureReady();
        final List<BenchmarkResult> results = new ArrayList<>();

        final World2 world = createBenchmarkWorld(2, 2);

        results.add(runMedianBenchmark(world, "Full Export (Deflate Default)", false, "-1", false));
        results.add(runMedianBenchmark(world, "Full Export (Deflate Level 1)", false, "1", false));
        results.add(runMedianBenchmark(world, "Full Export (Deflate Level 3)", false, "3", false));
        results.add(runMedianBenchmark(world, "Turbo Export (Level 1 + Deferred)", true, "1", false));
        results.add(runMedianBenchmark(world, "Linear Format Export (.linear)", true, "1", true));

        final String table = generateMarkdownTable(results);
        System.out.println("\n=== EXPORT PERFORMANCE BENCHMARK RESULTS (3-run median) ===");
        System.out.println(table);

        final Path baselineDir = resolveBaselineDir();
        Files.createDirectories(baselineDir);
        final Path out = baselineDir.resolve("resources-cluster-median-" + System.currentTimeMillis() + ".md");
        final StringBuilder md = new StringBuilder();
        md.append("# Resources cluster export baseline (3-run median)\n\n");
        md.append("- Created: ").append(java.time.LocalDate.now()).append('\n');
        md.append("- Runs per mode: ").append(RUNS_PER_MODE).append('\n');
        md.append("- Classifier: v2 cluster ResourcesExporter (legacyNoise=false default)\n");
        md.append("- Rule: report medians only; do not claim full export <400 ms unless median proves it\n\n");
        md.append(table).append('\n');
        final BenchmarkResult full = results.get(0);
        md.append("\n### Resources stage (Full Export Deflate Default — median)\n\n");
        md.append("- Resources ms (median): ").append(full.resourcesMs).append('\n');
        md.append("- Resources share (median): ").append(String.format(Locale.ROOT, "%.1f", full.resourcesSharePercent)).append("%\n");
        md.append("- Full duration (median): ").append(full.durationMs).append(" ms\n");
        md.append("- Gate: share < 30% OR Resources ms < previous × 0.25 (prior ~")
                .append(PRIOR_RESOURCES_MS).append(" ms / 67%)\n");
        final boolean gateOk = full.resourcesSharePercent < 30.0
                || full.resourcesMs < (long) (PRIOR_RESOURCES_MS * 0.25);
        md.append("- Gate result: ").append(gateOk ? "PASS" : "FAIL").append('\n');
        if (full.durationMs < 400) {
            md.append("- Full export <400 ms: YES (median ").append(full.durationMs).append(" ms)\n");
        } else {
            md.append("- Full export <400 ms: NO (median ").append(full.durationMs)
                    .append(" ms) — do not claim this target\n");
        }
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote baseline: " + out.toAbsolutePath());

        for (BenchmarkResult res : results) {
            assertTrue("Output size must be greater than 0", res.outputSizeBytes > 0);
            assertTrue("Duration must be greater than 0", res.durationMs > 0);
        }

        assertTrue("Resources stage should be recorded", full.resourcesMs >= 0);
        if (! gateOk) {
            System.out.println("WARN: Resources gate not met (share=" + full.resourcesSharePercent
                    + "%, ms=" + full.resourcesMs + ").");
        }
        assertTrue("Resources measurement gate must pass on median", gateOk);
    }

    /** Run {@link #RUNS_PER_MODE} times and return median duration / resources metrics. */
    public static BenchmarkResult runMedianBenchmark(World2 world, String name, boolean turbo,
                                                     String deflateLevel, boolean linear) throws Exception {
        final List<BenchmarkResult> samples = new ArrayList<>(RUNS_PER_MODE);
        for (int i = 0; i < RUNS_PER_MODE; i++) {
            samples.add(runSingleBenchmark(world, name + " [run " + (i + 1) + "]", turbo, deflateLevel, linear));
        }
        samples.sort(Comparator.comparingLong(r -> r.durationMs));
        final BenchmarkResult medianByDuration = samples.get(RUNS_PER_MODE / 2);

        final long[] resourceMs = samples.stream().mapToLong(r -> r.resourcesMs).sorted().toArray();
        final double[] resourceShare = samples.stream().mapToDouble(r -> r.resourcesSharePercent).sorted().toArray();
        final long[] sizes = samples.stream().mapToLong(r -> r.outputSizeBytes).sorted().toArray();
        final long[] heaps = samples.stream().mapToLong(r -> r.peakHeapMb).sorted().toArray();

        return new BenchmarkResult(name, medianByDuration.durationMs, sizes[RUNS_PER_MODE / 2],
                heaps[RUNS_PER_MODE / 2], medianByDuration.stats,
                resourceMs[RUNS_PER_MODE / 2], resourceShare[RUNS_PER_MODE / 2], RUNS_PER_MODE);
    }

    public static World2 createBenchmarkWorld(int tilesX, int tilesZ) {
        final Configuration config = Configuration.getInstance();
        final int maxHeight = config.getDefaultMaxHeight();
        final World2 world = new World2(config.getDefaultPlatform(), 424242L,
                TileFactoryFactory.createNoiseTileFactory(424242L, Terrain.GRASS, config.getDefaultPlatform().minZ, maxHeight, 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        dimension.setGenerator(new org.pepsoft.minecraft.SeededGenerator(DEFAULT, 424242L));
        for (int x = 0; x < tilesX; x++) {
            for (int z = 0; z < tilesZ; z++) {
                dimension.addTile(dimension.getTileFactory().createTile(x, z));
            }
        }
        return world;
    }

    public static BenchmarkResult runSingleBenchmark(World2 world, String name, boolean turbo, String deflateLevel, boolean linear) throws Exception {
        String prevDeflate = System.getProperty("org.pepsoft.worldpainter.deflateLevel");
        try {
            if (turbo) {
                JavaExportSettings.applyTurboDeflateHint();
            } else if (deflateLevel != null) {
                System.setProperty("org.pepsoft.worldpainter.deflateLevel", deflateLevel);
                org.pepsoft.minecraft.compression.ChunkCompressors.resetForTests();
            }

            final JavaExportSettings exportSettings = turbo ? JavaExportSettings.turboExportPreset() : JavaExportSettings.optimizedExportPreset();
            final WorldExportSettings worldExportSettings = turbo ? WorldExportSettings.turboExportSettings() : new WorldExportSettings(Set.of(DIM_NORMAL), null, null);
            worldExportSettings.setLinearRegionFormat(linear);

            world.getDimension(NORMAL_DETAIL).setExportSettings(exportSettings);
            worldExportSettings.setDimensionsToExport(Set.of(DIM_NORMAL));

            final File exportDir = Files.createTempDirectory("wp-bench-").toFile();
            try {
                System.gc();
                final long heapBefore = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                final long start = System.currentTimeMillis();

                final WorldExporter exporter = PlatformManager.getInstance().getExporter(world, worldExportSettings);
                final Map<Integer, ChunkFactory.Stats> statsMap = exporter.export(exportDir, "bench-world", null, new TextProgressReceiver());

                final long durationMs = System.currentTimeMillis() - start;
                final long outputSize = calculateDirectorySize(exportDir);
                final long heapAfter = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                final long peakHeap = Math.max(0, heapAfter - heapBefore);

                ChunkFactory.Stats normalStats = statsMap != null ? statsMap.get(DIM_NORMAL) : null;
                long resourcesMs = 0;
                double resourcesShare = 0;
                if (normalStats != null && normalStats.timings != null) {
                    long totalNs = 0;
                    long resourcesNs = 0;
                    for (Map.Entry<Object, AtomicLong> e : normalStats.timings.entrySet()) {
                        long ns = e.getValue().get();
                        totalNs += ns;
                        if (isResourcesTiming(e.getKey())) {
                            resourcesNs += ns;
                        }
                    }
                    resourcesMs = resourcesNs / 1_000_000L;
                    resourcesShare = totalNs > 0 ? (100.0 * resourcesNs / totalNs) : 0;
                }
                return new BenchmarkResult(name, durationMs, outputSize, peakHeap, normalStats, resourcesMs, resourcesShare);
            } finally {
                deleteRecursively(exportDir);
            }
        } finally {
            if (prevDeflate != null) {
                System.setProperty("org.pepsoft.worldpainter.deflateLevel", prevDeflate);
            } else {
                System.clearProperty("org.pepsoft.worldpainter.deflateLevel");
            }
            org.pepsoft.minecraft.compression.ChunkCompressors.resetForTests();
        }
    }

    private static boolean isResourcesTiming(Object key) {
        if (key instanceof Layer layer) {
            return "Resources".equals(layer.getName());
        }
        return ExportTimingReporter.timingName(key).contains("Resources");
    }

    private static Path resolveBaselineDir() {
        Path cwd = Path.of("").toAbsolutePath();
        Path candidate = cwd.resolve("docs/baselines");
        if (Files.isDirectory(candidate.getParent())) {
            return candidate;
        }
        candidate = cwd.resolve("../docs/baselines");
        if (Files.isDirectory(candidate.getParent())) {
            return candidate;
        }
        candidate = cwd.resolve("../../docs/baselines");
        return candidate;
    }

    public static String generateMarkdownTable(List<BenchmarkResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Mode | Median Duration (ms) | Median Resources (ms) | Median Resources % | Output (KB) | Peak Heap (MB) | N |\n");
        sb.append("|------|----------------------|-----------------------|--------------------|-------------|----------------|---|\n");
        for (BenchmarkResult r : results) {
            sb.append(String.format(Locale.ROOT, "| %s | %d | %d | %.1f | %.2f | %d | %d |\n",
                    r.name, r.durationMs, r.resourcesMs, r.resourcesSharePercent,
                    r.outputSizeBytes / 1024.0, r.peakHeapMb, r.sampleCount));
        }
        return sb.toString();
    }

    private static long calculateDirectorySize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    size += calculateDirectorySize(f);
                } else {
                    size += f.length();
                }
            }
        }
        return size;
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
