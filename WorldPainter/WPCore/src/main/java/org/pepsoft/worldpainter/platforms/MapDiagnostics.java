package org.pepsoft.worldpainter.platforms;

import org.pepsoft.minecraft.JavaLevel;
import org.pepsoft.worldpainter.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.platforms.WorldStorageLayout.getRegionDir;

/**
 * Diagnostic logging and user-facing summaries for map import/merge operations.
 */
public final class MapDiagnostics {
    private MapDiagnostics() {
    }

    public static Summary analyse(File worldDir, JavaLevel level, Platform selectedPlatform) {
        final WorldStorageLayout.Layout layout = WorldStorageLayout.detect(worldDir, level);
        final int dataVersion = level.getDataVersion();
        final int overworldRegions = countRegionFiles(getRegionDir(worldDir, DIM_NORMAL, layout));
        final int netherRegions = countRegionFiles(getRegionDir(worldDir, DIM_NETHER, layout));
        final int endRegions = countRegionFiles(getRegionDir(worldDir, DIM_END, layout));
        final WorldStorageLayout.Layout expectedLayout = WorldStorageLayout.layoutForPlatform(selectedPlatform);
        final boolean layoutMismatch = layout != expectedLayout;
        return new Summary(worldDir, layout, dataVersion, overworldRegions, netherRegions, endRegions,
                selectedPlatform, expectedLayout, layoutMismatch);
    }

    public static void log(Summary summary) {
        logger.info("Map diagnostics: path={}, layout={}, dataVersion={}, regions=[overworld={}, nether={}, end={}], platform={}, expectedLayout={}, layoutMismatch={}",
                summary.worldDir.getAbsolutePath(), summary.layout, summary.dataVersion,
                summary.overworldRegionFiles, summary.netherRegionFiles, summary.endRegionFiles,
                summary.selectedPlatform.displayName, summary.expectedLayout, summary.layoutMismatch);
    }

    public static String userMessage(Summary summary) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Detected storage layout: ").append(summary.layout).append('\n');
        sb.append("Data version: ").append(summary.dataVersion).append('\n');
        sb.append("Region files: overworld=").append(summary.overworldRegionFiles)
                .append(", nether=").append(summary.netherRegionFiles)
                .append(", end=").append(summary.endRegionFiles).append('\n');
        if (summary.layoutMismatch) {
            sb.append("WARNING: This map uses the ").append(summary.layout)
                    .append(" layout but the selected platform (").append(summary.selectedPlatform.displayName)
                    .append(") expects ").append(summary.expectedLayout).append(".\n");
            sb.append("If you upgraded this world in Minecraft 26.1+, select the Minecraft 26.1 platform before import/merge.\n");
            sb.append("Recommended: merge before upgrading in Minecraft, or re-import after upgrading.\n");
        }
        if (summary.layout == WorldStorageLayout.Layout.V26_1 && summary.overworldRegionFiles == 0) {
            sb.append("WARNING: No overworld region files found at the expected 26.1 path.\n");
            sb.append("The map may have been partially optimised or the wrong layout was detected.\n");
        }
        return sb.toString();
    }

    private static int countRegionFiles(File regionDir) {
        if (! regionDir.isDirectory()) {
            return 0;
        }
        final File[] files = regionDir.listFiles((dir, name) -> name.endsWith(".mca"));
        return (files != null) ? files.length : 0;
    }

    private static final Logger logger = LoggerFactory.getLogger(MapDiagnostics.class);

    public static final class Summary {
        public final File worldDir;
        public final WorldStorageLayout.Layout layout;
        public final int dataVersion;
        public final int overworldRegionFiles;
        public final int netherRegionFiles;
        public final int endRegionFiles;
        public final Platform selectedPlatform;
        public final WorldStorageLayout.Layout expectedLayout;
        public final boolean layoutMismatch;

        Summary(File worldDir, WorldStorageLayout.Layout layout, int dataVersion,
                int overworldRegionFiles, int netherRegionFiles, int endRegionFiles,
                Platform selectedPlatform, WorldStorageLayout.Layout expectedLayout, boolean layoutMismatch) {
            this.worldDir = worldDir;
            this.layout = layout;
            this.dataVersion = dataVersion;
            this.overworldRegionFiles = overworldRegionFiles;
            this.netherRegionFiles = netherRegionFiles;
            this.endRegionFiles = endRegionFiles;
            this.selectedPlatform = selectedPlatform;
            this.expectedLayout = expectedLayout;
            this.layoutMismatch = layoutMismatch;
        }
    }
}
