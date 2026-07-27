/*
 * Fork branding for WorldPainter v2.
 */
package org.pepsoft.worldpainter;

import static org.pepsoft.minecraft.Constants.DEFAULT_WATER_LEVEL;

/**
 * Product identity for the WorldPainter v2 fork. Keeps config, window titles and
 * packaged executables separate from the original WorldPainter installation.
 */
public final class Branding {
    private Branding() {
    }

    /** Display name shown in window titles and about dialog. */
    public static final String PRODUCT_NAME = "WorldPainter v2";

    /** Configuration classifier; stored under {@code WorldPainter [V2]} on Windows. */
    public static final String CLASSIFIER = "v2";

    /** Fat-jar / jpackage input artifact base name (without {@code .jar}). */
    public static final String PACKAGE_JAR_NAME = "WorldPainter-v2";

    /**
     * Apply fork defaults before {@link Configuration} paths are resolved.
     * Does not override an explicit {@code -Dorg.pepsoft.worldpainter.classifier=...}.
     */
    public static void applyDefaults() {
        if (System.getProperty("org.pepsoft.worldpainter.classifier") == null) {
            System.setProperty("org.pepsoft.worldpainter.classifier", CLASSIFIER);
        }
    }

    /**
     * Default look and feel for new v2 installations.
     * Use stock WorldPainter system L&amp;F (not the old FlatLaf / dark modern UI).
     */
    public static Configuration.LookAndFeel defaultLookAndFeel() {
        if (CLASSIFIER.equals(System.getProperty("org.pepsoft.worldpainter.classifier"))) {
            return Configuration.LookAndFeel.SYSTEM;
        }
        return null;
    }

    /** v2 fork: do not allocate a default world at startup (saves tiles + undo RAM). */
    public static boolean startWithoutDefaultWorld() {
        return CLASSIFIER.equals(System.getProperty("org.pepsoft.worldpainter.classifier"));
    }

    /** Default undo depth for new v2 installs (lower than upstream 100 to limit snapshot RAM). */
    public static int defaultUndoLevels() {
        return CLASSIFIER.equals(System.getProperty("org.pepsoft.worldpainter.classifier")) ? 30 : 100;
    }

    public static boolean isV2() {
        return CLASSIFIER.equals(System.getProperty("org.pepsoft.worldpainter.classifier"));
    }

    /** Create New World defaults for v2. */
    public static int defaultNewWorldTerrainLevel() {
        return isV2() ? 0 : 58;
    }

    public static int defaultNewWorldWaterLevel() {
        return isV2() ? 0 : DEFAULT_WATER_LEVEL;
    }

    public static boolean defaultNewWorldBeaches() {
        return ! isV2();
    }

    /**
     * Default new-world size in tiles. v2 uses an even count so block (0, 0) is the map centre.
     */
    public static int defaultNewWorldWidthTiles() {
        return isV2() ? 6 : 5;
    }

    public static int defaultNewWorldHeightTiles() {
        return defaultNewWorldWidthTiles();
    }
}
