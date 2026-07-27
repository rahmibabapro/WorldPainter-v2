package org.pepsoft.worldpainter.platforms;

import org.pepsoft.minecraft.JavaLevel;
import org.pepsoft.worldpainter.Platform;

import java.io.File;
import java.io.IOException;

import static org.pepsoft.minecraft.Constants.DATA_VERSION_MC_26_1;
import static org.pepsoft.worldpainter.Constants.*;

/**
 * Resolves Minecraft world storage paths for legacy (pre-26.1) and 26.1+ dimension layouts.
 */
public final class WorldStorageLayout {
    public enum Layout {
        LEGACY,
        V26_1
    }

    private WorldStorageLayout() {
    }

    public static Layout detect(File worldDir) {
        if (new File(worldDir, "dimensions/minecraft/overworld/region").isDirectory()) {
            return Layout.V26_1;
        }
        if (new File(worldDir, "region").isDirectory()) {
            return Layout.LEGACY;
        }
        final File levelDat = new File(worldDir, "level.dat");
        if (levelDat.isFile()) {
            try {
                if (JavaLevel.load(levelDat).getDataVersion() >= DATA_VERSION_MC_26_1) {
                    return Layout.V26_1;
                }
            } catch (IOException ignored) {
                // Fall through to legacy default
            }
        }
        return Layout.LEGACY;
    }

    public static Layout detect(File worldDir, JavaLevel level) {
        if (new File(worldDir, "dimensions/minecraft/overworld/region").isDirectory()) {
            return Layout.V26_1;
        }
        if (level != null && level.getDataVersion() >= DATA_VERSION_MC_26_1) {
            return Layout.V26_1;
        }
        return detect(worldDir);
    }

    public static Layout layoutForPlatform(Platform platform) {
        return Layout.V26_1;
    }

    public static File getDimensionRoot(File worldDir, int dimension, Layout layout) {
        if (layout == Layout.V26_1) {
            return new File(worldDir, "dimensions/minecraft/" + dimensionPathName(dimension));
        }
        switch (dimension) {
            case DIM_NORMAL:
                return worldDir;
            case DIM_NETHER:
                return new File(worldDir, "DIM-1");
            case DIM_END:
                return new File(worldDir, "DIM1");
            default:
                throw new IllegalArgumentException("Dimension " + dimension + " not supported");
        }
    }

    public static File getRegionDir(File worldDir, int dimension, Layout layout) {
        return new File(getDimensionRoot(worldDir, dimension, layout), "region");
    }

    public static File getEntitiesDir(File worldDir, int dimension, Layout layout) {
        return new File(getDimensionRoot(worldDir, dimension, layout), "entities");
    }

    public static int dimensionFromPathName(String pathName) {
        switch (pathName) {
            case "overworld":
                return DIM_NORMAL;
            case "the_nether":
                return DIM_NETHER;
            case "the_end":
                return DIM_END;
            default:
                return -1;
        }
    }

    public static String dimensionPathName(int dimension) {
        switch (dimension) {
            case DIM_NORMAL:
                return "overworld";
            case DIM_NETHER:
                return "the_nether";
            case DIM_END:
                return "the_end";
            default:
                throw new IllegalArgumentException("Dimension " + dimension + " not supported");
        }
    }

    public static boolean isDimensionBeingMerged(String fileName, int dimension) {
        if (dimension == DIM_NORMAL) {
            return fileName.equalsIgnoreCase("region") || fileName.equalsIgnoreCase("entities");
        }
        if (dimension == DIM_NETHER) {
            return fileName.equalsIgnoreCase("DIM-1");
        }
        if (dimension == DIM_END) {
            return fileName.equalsIgnoreCase("DIM1");
        }
        return false;
    }

    public static boolean isV26DimensionBeingMerged(String pathName, int dimension) {
        return dimensionFromPathName(pathName) == dimension;
    }
}
