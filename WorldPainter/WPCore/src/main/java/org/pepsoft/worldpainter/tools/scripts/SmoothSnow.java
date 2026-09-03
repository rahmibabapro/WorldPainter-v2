package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Annotations;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.SnowDepth;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.Void;
import org.pepsoft.worldpainter.layers.exporters.FrostExporter.FrostSettings;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;

/**
 * Fast, deterministic snow coverage pass shared by bundled scripts. The pass is deliberately
 * tile-local: it never keeps a world-sized JavaScript map or a second height map in memory.
 */
public final class SmoothSnow {
    /** Default mountain profile: a sparse start at Y=160 and full cover from Y=190. */
    public static final float DEFAULT_SNOW_LINE_HEIGHT = 160.0f;
    public static final float DEFAULT_FULL_SNOW_HEIGHT = 190.0f;

    private SmoothSnow() {
    }

    /**
     * Converts the blueprint's transferred white-region mask (temporarily stored as Frost)
     * into actual 1..8-layer snow. Does not paint white terrain or change the height field.
     * Three original mask rows are retained so edge thickness does not depend on scan order.
     */
    public static Result applyBlueprintMask(Dimension dimension, long seed, ProgressReceiver progress)
            throws OperationCancelled {
        return applyBlueprintMask(dimension, seed, progress, DEFAULT_SNOW_LINE_HEIGHT, DEFAULT_FULL_SNOW_HEIGHT,
                25.0f, 55.0f, false);
    }

    /**
     * Summit-white mode: every valid mask cell receives at least one snow layer. The layer depth
     * still changes smoothly from the start height to the full-snow height, but there are no
     * sparse holes in the white terrain.
     */
    public static Result applySummitBlueprintMask(Dimension dimension, long seed, ProgressReceiver progress,
                                                  float snowLineHeight, float fullSnowHeight, float slopeReject)
            throws OperationCancelled {
        if (fullSnowHeight <= snowLineHeight || slopeReject <= 0.0f || slopeReject > 90.0f) {
            throw new IllegalArgumentException("Invalid summit snow limits");
        }
        return applyBlueprintMask(dimension, seed, progress, snowLineHeight, fullSnowHeight, 0.0f, slopeReject, true);
    }

    private static Result applyBlueprintMask(Dimension dimension, long seed, ProgressReceiver progress,
                                             float snowLineHeight, float fullSnowHeight,
                                             float slopeStart, float slopeReject, boolean continuousCoverage)
            throws OperationCancelled {
        if (dimension.getTiles().isEmpty()) {
            return new Result(0, 0, 0, 0);
        }
        final int startX = dimension.getLowestX() * 128;
        final int startY = dimension.getLowestY() * 128;
        final int width = Math.multiplyExact(dimension.getWidth(), 128);
        final int length = Math.multiplyExact(dimension.getHeight(), 128);
        if (width > 1_048_576) {
            throw new IllegalArgumentException("World is too widely scattered for the snow mask ribbon");
        }
        final boolean[][] rows = new boolean[3][width + 2];
        readMaskRow(dimension, rows[0], startX - 1, startY - 1);
        readMaskRow(dimension, rows[1], startX - 1, startY);
        readMaskRow(dimension, rows[2], startX - 1, startY + 1);
        long checked = 0, covered = 0, cleared = 0;
        for (int row = 0; row < length; row++) {
            final int y = startY + row;
            final boolean[] previous = rows[row % 3], current = rows[(row + 1) % 3], next = rows[(row + 2) % 3];
            Tile tile = null;
            for (int column = 0; column < width; column++) {
                final int x = startX + column;
                if ((column & 127) == 0) {
                    tile = dimension.getTile(x >> 7, y >> 7);
                }
                if (tile == null) {
                    continue;
                }
                checked++;
                final int tx = x & 127, ty = y & 127;
                final float height = tile.getHeight(tx, ty);
                if (! Float.isFinite(height) || (height <= tile.getWaterLevel(tx, ty) + 1.0f)
                        || tile.getBitLayerValue(Void.INSTANCE, tx, ty)
                        || tile.getBitLayerValue(NotPresent.INSTANCE, tx, ty)
                        || tile.getBitLayerValue(NotPresentBlock.INSTANCE, tx, ty)) {
                    continue;
                }
                final boolean sourceWhite = current[column + 1];
                final float density = sourceWhite
                        ? coverage(height, getSlopeDegrees(dimension, x, y), northFacingFactor(dimension, x, y, 0.15f),
                        seed, x, y, snowLineHeight, fullSnowHeight, slopeStart, slopeReject) : 0;
                // Coherent thresholding keeps sparse transition snow in small islands instead
                // of choosing an unrelated random material at every block.
                final boolean snow = density > 0 && (continuousCoverage || coherentThreshold(seed, x, y) < density);
                int depth = 0;
                if (snow) {
                    int neighbours = 0;
                    for (int dx = 0; dx < 3; dx++) {
                        if (previous[column + dx]) neighbours++;
                        if (current[column + dx]) neighbours++;
                        if (next[column + dx]) neighbours++;
                    }
                    final int densityDepth = Math.max(1, Math.min(8, (int) Math.ceil(density * 8)));
                    if (continuousCoverage) {
                        // Explicit summit snow must cover every retained white surface. The
                        // neighbour cap thins edges naturally but never removes their first layer.
                        depth = Math.max(1, Math.min(densityDepth, Math.max(1, neighbours - 1)));
                    } else {
                        final int geometryDepth = (int) Math.floor((height + 0.5f - tile.getIntHeight(tx, ty)) * 8) + 1;
                        depth = Math.max(1, Math.min(8, Math.min(geometryDepth,
                                Math.min(densityDepth, Math.max(1, neighbours - 1)))));
                    }
                    covered++;
                } else if (sourceWhite) {
                    cleared++;
                }
                if ((tile.getBitLayerValue(Frost.INSTANCE, tx, ty) != snow)
                        || (tile.getLayerValue(SnowDepth.INSTANCE, tx, ty) != depth)) {
                    final Tile editing = dimension.getTileForEditing(x >> 7, y >> 7);
                    editing.setBitLayerValue(Frost.INSTANCE, tx, ty, snow);
                    editing.setLayerValue(SnowDepth.INSTANCE, tx, ty, depth);
                }
            }
            if (row + 1 < length) {
                readMaskRow(dimension, previous, startX - 1, y + 2);
            }
            if (progress != null) {
                progress.setProgress((row + 1) / (float) length);
                progress.checkForCancellation();
            }
        }
        ensureSmoothFrost(dimension);
        final FrostSettings settings = ((FrostSettings) dimension.getLayerSettings(Frost.INSTANCE)).clone();
        settings.setFrostEverywhere(false);
        dimension.setLayerSettings(Frost.INSTANCE, settings);
        return new Result(checked, covered, cleared, 0);
    }

    private static void readMaskRow(Dimension dimension, boolean[] row, int startX, int y) {
        for (int i = 0; i < row.length; i++) {
            row[i] = dimension.getBitLayerValueAt(Frost.INSTANCE, startX + i, y);
        }
    }

    private static float coherentThreshold(long seed, int x, int y) {
        final int value = Math.min(255, (int) (valueNoise(seed ^ 0x5eaf131aL, x, y, 8) * 256));
        return NOISE_QUANTILES[value];
    }

    private static float[] createNoiseQuantiles() {
        // Empirical CDF gives the interpolated field uniform ranks: 8% coverage remains
        // approximately 8%, rather than falling to nearly zero after smoothing the noise.
        final int[] histogram = new int[256];
        final int samples = 65536;
        for (int i = 0; i < samples; i++) {
            final float sample = valueNoise(731919L, (i & 255) * 13, (i >>> 8) * 17, 8);
            histogram[Math.min(255, (int) (sample * 256))]++;
        }
        final float[] result = new float[256];
        int sum = 0;
        for (int i = 0; i < result.length; i++) {
            result[i] = (sum + histogram[i] * 0.5f) / samples;
            sum += histogram[i];
        }
        return result;
    }

    private static final float[] NOISE_QUANTILES = createNoiseQuantiles();

    public static Result apply(Dimension dimension, boolean annotationsOnly, int annotationValue,
                               float snowLineHeight, float fullSnowHeight, int maxSnowLayers,
                               float slopeStart, float slopeReject, float northFacingBoost,
                               long seed, boolean clearLowSnow, boolean addHeight,
                               boolean dryRun, Terrain fullSnowTerrain, ScriptProgress progress) {
        try {
            return applyInternal(dimension, annotationsOnly, annotationValue, snowLineHeight, fullSnowHeight, maxSnowLayers,
                    slopeStart, slopeReject, northFacingBoost, seed, clearLowSnow, addHeight, dryRun, fullSnowTerrain,
                    (progress != null) ? fraction -> {
                        progress.setProgress(fraction);
                        progress.checkForCancel();
                    } : null);
        } catch (OperationCancelled e) {
            // ScriptProgress converts cancellation into its unchecked interruption exception.
            throw new IllegalStateException("Unexpected checked cancellation", e);
        }
    }

    public static Result apply(Dimension dimension, boolean annotationsOnly, int annotationValue,
                               float snowLineHeight, float fullSnowHeight, int maxSnowLayers,
                               float slopeStart, float slopeReject, float northFacingBoost,
                               long seed, boolean clearLowSnow, boolean addHeight,
                               boolean dryRun, Terrain fullSnowTerrain, ProgressReceiver progress) throws OperationCancelled {
        return applyInternal(dimension, annotationsOnly, annotationValue, snowLineHeight, fullSnowHeight, maxSnowLayers,
                slopeStart, slopeReject, northFacingBoost, seed, clearLowSnow, addHeight, dryRun, fullSnowTerrain,
                (progress != null) ? fraction -> {
                    progress.setProgress((float) fraction);
                    progress.checkForCancellation();
                } : null);
    }

    private static Result applyInternal(Dimension dimension, boolean annotationsOnly, int annotationValue,
                                        float snowLineHeight, float fullSnowHeight, int maxSnowLayers,
                                        float slopeStart, float slopeReject, float northFacingBoost,
                                        long seed, boolean clearLowSnow, boolean addHeight,
                                        boolean dryRun, Terrain fullSnowTerrain, ProgressReporter progress) throws OperationCancelled {
        if (fullSnowHeight <= snowLineHeight) {
            throw new IllegalArgumentException("Full snow height must be above snow line height");
        }
        if (slopeStart < 0.0f || slopeReject <= slopeStart) {
            throw new IllegalArgumentException("Slope rejection must be above slope start");
        }
        if (maxSnowLayers < 1 || maxSnowLayers > 8) {
            throw new IllegalArgumentException("Maximum snow layers must be between 1 and 8");
        }

        final int lowestTileX = dimension.getLowestX();
        final int lowestTileY = dimension.getLowestY();
        final int highestTileX = lowestTileX + dimension.getWidth();
        final int highestTileY = lowestTileY + dimension.getHeight();
        long total = 0L;
        for (int tileX = lowestTileX; tileX < highestTileX; tileX++) {
            for (int tileY = lowestTileY; tileY < highestTileY; tileY++) {
                if (dimension.isTilePresent(tileX, tileY)) {
                    total += 128L * 128L;
                }
            }
        }
        if (total == 0) {
            return new Result(0, 0, 0, 0);
        }

        long checked = 0, snowCovered = 0, cleared = 0, deepSnow = 0;
        for (int tileX = lowestTileX; tileX < highestTileX; tileX++) {
            for (int tileY = lowestTileY; tileY < highestTileY; tileY++) {
                if (! dimension.isTilePresent(tileX, tileY)) {
                    continue;
                }
                final int startX = tileX * 128;
                final int startY = tileY * 128;
                for (int x = startX; x < startX + 128; x++) {
                    for (int y = startY; y < startY + 128; y++) {
                        checked++;
                        if (annotationsOnly && dimension.getLayerValueAt(Annotations.INSTANCE, x, y) != annotationValue) {
                            continue;
                        }
                        final float height = dimension.getHeightAt(x, y);
                        final int waterLevel = dimension.getWaterLevelAt(x, y);
                        // Do not turn submerged ground or waterline cells into snow/ice.
                        final boolean dryLand = height > waterLevel + 1.0f;
                        final float coverage = dryLand
                                ? coverage(height, getSlopeDegrees(dimension, x, y), northFacingFactor(dimension, x, y, northFacingBoost),
                                seed, x, y, snowLineHeight, fullSnowHeight, slopeStart, slopeReject)
                                : 0.0f;
                        final boolean placeSnow = coverage > 0.0f && deterministicRandom(seed, x, y, 11) < coverage;
                        if (placeSnow) {
                            snowCovered++;
                            if (coverage >= 0.90f && fullSnowTerrain != null) {
                                deepSnow++;
                                if (! dryRun) {
                                    dimension.setTerrainAt(x, y, fullSnowTerrain);
                                }
                            }
                            if (! dryRun) {
                                dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, true);
                                if (addHeight) {
                                    addSmoothHeight(dimension, x, y, coverage, maxSnowLayers);
                                }
                            }
                        } else if (clearLowSnow && height < snowLineHeight && dimension.getBitLayerValueAt(Frost.INSTANCE, x, y)) {
                            cleared++;
                            if (! dryRun) {
                                dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, false);
                            }
                        }
                    }
                }
                if (progress != null) {
                    progress.report((double) checked / total);
                }
            }
        }
        if (! dryRun) {
            ensureSmoothFrost(dimension);
        }
        return new Result(checked, snowCovered, cleared, deepSnow);
    }

    /** Visible at the snow line (8%), complete at the full-snow height. */
    static float heightCoverage(float height, float snowLineHeight, float fullSnowHeight) {
        if (height < snowLineHeight) {
            return 0.0f;
        }
        return 0.08f + 0.92f * smoothStep((height - snowLineHeight) / (fullSnowHeight - snowLineHeight));
    }

    static float coverage(float height, float slopeDegrees, float northFactor, long seed, int x, int y,
                          float snowLineHeight, float fullSnowHeight, float slopeStart, float slopeReject) {
        final float elevation = heightCoverage(height, snowLineHeight, fullSnowHeight);
        if (elevation == 0.0f) {
            return 0.0f;
        }
        final float slope = 1.0f - smoothStep((slopeDegrees - slopeStart) / (slopeReject - slopeStart));
        // Above the full-snow height, calm terrain must really be fully covered. Only exposed
        // rock remains clear; weather/aspect variation belongs to the transition band below it.
        if (height >= fullSnowHeight) {
            return slope;
        }
        // Large, softly interpolated patches prevent one-cell salt-and-pepper snow.
        final float weather = 0.82f + 0.36f * valueNoise(seed, x, y, 48);
        return clamp(elevation * slope * northFactor * weather);
    }

    private static float getSlopeDegrees(Dimension dimension, int x, int y) {
        final float center = dimension.getHeightAt(x, y);
        final float dx = (heightAtOrCenter(dimension, x + 1, y, center) - heightAtOrCenter(dimension, x - 1, y, center)) / 2.0f;
        final float dy = (heightAtOrCenter(dimension, x, y + 1, center) - heightAtOrCenter(dimension, x, y - 1, center)) / 2.0f;
        return (float) Math.toDegrees(Math.atan(Math.sqrt(dx * dx + dy * dy)));
    }

    private static float northFacingFactor(Dimension dimension, int x, int y, float boost) {
        if (boost == 0.0f) {
            return 1.0f;
        }
        final float center = dimension.getHeightAt(x, y);
        // In WorldPainter coordinates negative Y is north. A north-facing downslope receives more snow.
        final float north = heightAtOrCenter(dimension, x, y - 1, center) - center;
        final float south = heightAtOrCenter(dimension, x, y + 1, center) - center;
        return aspectMultiplier(north, south, boost);
    }

    static float aspectMultiplier(float northHeightDelta, float southHeightDelta, float boost) {
        // A flat surface is neutral; the old [0,1] clamp gave it the full southern penalty.
        final float facingNorth = Math.max(-1.0f, Math.min(1.0f, (southHeightDelta - northHeightDelta) / 8.0f));
        return Math.max(0.1f, 1.0f + facingNorth * boost);
    }

    private static float heightAtOrCenter(Dimension dimension, int x, int y, float center) {
        return dimension.isTilePresent(x >> 7, y >> 7) ? dimension.getHeightAt(x, y) : center;
    }

    private static void addSmoothHeight(Dimension dimension, int x, int y, float coverage, int maxSnowLayers) {
        final float currentHeight = dimension.getHeightAt(x, y);
        final float maximumDepth = maxSnowLayers / 8.0f;
        final float targetFraction = Math.min(0.875f, coverage * maximumDepth);
        final float targetHeight = (float) Math.floor(currentHeight) + Math.max(currentHeight - (float) Math.floor(currentHeight), targetFraction);
        if (targetHeight > currentHeight) {
            dimension.setHeightAt(x, y, targetHeight);
        }
    }

    private static void ensureSmoothFrost(Dimension dimension) {
        FrostSettings settings = (FrostSettings) dimension.getLayerSettings(Frost.INSTANCE);
        if (settings == null) {
            settings = new FrostSettings();
        } else {
            // Do not mutate the original settings object; callers may need it for rollback.
            settings = settings.clone();
        }
        settings.setMode(FrostSettings.MODE_SMOOTH);
        dimension.setLayerSettings(Frost.INSTANCE, settings);
    }

    private static float smoothStep(float value) {
        value = clamp(value);
        return value * value * (3.0f - 2.0f * value);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float valueNoise(long seed, int x, int y, int scale) {
        final int cellX = Math.floorDiv(x, scale), cellY = Math.floorDiv(y, scale);
        final float localX = Math.floorMod(x, scale) / (float) scale;
        final float localY = Math.floorMod(y, scale) / (float) scale;
        final float top = lerp(deterministicRandom(seed, cellX, cellY, 1), deterministicRandom(seed, cellX + 1, cellY, 1), smoothStep(localX));
        final float bottom = lerp(deterministicRandom(seed, cellX, cellY + 1, 1), deterministicRandom(seed, cellX + 1, cellY + 1, 1), smoothStep(localX));
        return lerp(top, bottom, smoothStep(localY));
    }

    private static float lerp(float first, float second, float t) {
        return first + (second - first) * t;
    }

    private static float deterministicRandom(long seed, int x, int y, int salt) {
        long value = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ salt;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (value >>> 40) / (float) (1 << 24);
    }

    public record Result(long checked, long snowCovered, long cleared, long deepSnow) {
    }

    @FunctionalInterface
    private interface ProgressReporter {
        void report(double fraction) throws OperationCancelled;
    }
}
