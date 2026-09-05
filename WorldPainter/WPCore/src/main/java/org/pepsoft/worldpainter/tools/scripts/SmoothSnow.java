package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Annotations;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.SnowDepth;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.Void;
import org.pepsoft.worldpainter.layers.exporters.FrostExporter.FrostSettings;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * Three final mask rows are retained so edge thickness does not depend on scan order
     * or erode again when the same snow pass is repeated.
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
        validateProfile(dimension, snowLineHeight, fullSnowHeight, 8, 0, slopeReject, 0.15f);
        return applyBlueprintMask(dimension, seed, progress, snowLineHeight, fullSnowHeight, 0.0f, slopeReject, true);
    }

    private static Result applyBlueprintMask(Dimension dimension, long seed, ProgressReceiver progress,
                                             float snowLineHeight, float fullSnowHeight,
                                             float slopeStart, float slopeReject, boolean continuousCoverage)
            throws OperationCancelled {
        validateProfile(dimension, snowLineHeight, fullSnowHeight, 8, slopeStart, slopeReject, 0.15f);
        final List<Tile> tiles = orderedTiles(dimension);
        if (tiles.isEmpty()) {
            return new Result(0, 0, 0, 0);
        }
        final int startX = dimension.getLowestX() * 128;
        final int startY = dimension.getLowestY() * 128;
        final int width = Math.multiplyExact(dimension.getWidth(), 128);
        final List<Integer> occupiedTileRows = tiles.stream().map(Tile::getY).distinct().sorted().toList();
        if (width > 1_048_576) {
            throw new IllegalArgumentException("World is too widely scattered for the snow mask ribbon");
        }
        if (progress != null) {
            progress.setProgress(0);
            progress.checkForCancellation();
        }
        final boolean[][] rows = new boolean[3][width + 2];
        final SnowProfile profile = new SnowProfile(seed, snowLineHeight, fullSnowHeight,
                slopeStart, slopeReject, continuousCoverage);
        readMaskRow(dimension, rows[0], startX - 1, startY - 1, profile, progress);
        readMaskRow(dimension, rows[1], startX - 1, startY, profile, progress);
        readMaskRow(dimension, rows[2], startX - 1, startY + 1, profile, progress);
        long checked = 0, covered = 0, cleared = 0;
        int previousY = startY - 1, processedRows = 0;
        for (int tileY : occupiedTileRows) {
          for (int localY = 0; localY < 128; localY++) {
            final int y = tileY * 128 + localY;
            final int row = processedRows;
            final boolean[] previous = rows[row % 3], current = rows[(row + 1) % 3], next = rows[(row + 2) % 3];
            if (y != previousY + 1) {
                // Sparse worlds need no traversal of millions of absent rows.
                readMaskRow(dimension, previous, startX - 1, y - 1, profile, progress);
                readMaskRow(dimension, current, startX - 1, y, profile, progress);
                readMaskRow(dimension, next, startX - 1, y + 1, profile, progress);
            }
            Tile tile = null;
            for (int column = 0; column < width; column++) {
                final int x = startX + column;
                if ((column & 127) == 0) {
                    if (progress != null) progress.checkForCancellation();
                    tile = dimension.getTile(x >> 7, y >> 7);
                }
                if (tile == null) {
                    continue;
                }
                checked++;
                final int tx = x & 127, ty = y & 127;
                final float height = tile.getHeight(tx, ty);
                if (!eligible(dimension, tile, tx, ty)) {
                    continue;
                }
                final boolean sourceWhite = tile.getBitLayerValue(Frost.INSTANCE, tx, ty);
                final float density = sourceWhite
                        ? coverage(height, getSlopeDegrees(dimension, x, y), northFacingFactor(dimension, x, y, 0.15f),
                        seed, x, y, snowLineHeight, fullSnowHeight, slopeStart, slopeReject) : 0;
                // Coherent thresholding keeps sparse transition snow in small islands instead
                // of choosing an unrelated random material at every block.
                final boolean snow = current[column + 1];
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
            if (row + 1 < occupiedTileRows.size() * 128L) {
                readMaskRow(dimension, previous, startX - 1, y + 2, profile, progress);
            }
            previousY = y;
            processedRows++;
            if (progress != null) {
                progress.setProgress(processedRows / (float) (occupiedTileRows.size() * 128L));
                progress.checkForCancellation();
            }
          }
        }
        if (covered > 0 || cleared > 0) {
            ensureSmoothFrost(dimension);
            final FrostSettings settings = ((FrostSettings) dimension.getLayerSettings(Frost.INSTANCE)).clone();
            settings.setFrostEverywhere(false);
            dimension.setLayerSettings(Frost.INSTANCE, settings);
        }
        return new Result(checked, covered, cleared, 0);
    }

    private static void readMaskRow(Dimension dimension, boolean[] row, int startX, int y,
                                    SnowProfile profile, ProgressReceiver progress) throws OperationCancelled {
        for (int i = 0; i < row.length; i++) {
            if (progress != null && (i & 127) == 0) progress.checkForCancellation();
            final int x = startX + i;
            final Tile tile = dimension.getTile(x >> 7, y >> 7);
            row[i] = false;
            if (tile == null || !tile.getBitLayerValue(Frost.INSTANCE, x & 127, y & 127)
                    || !eligible(dimension, tile, x & 127, y & 127)) continue;
            final float density = coverage(tile.getHeight(x & 127, y & 127), getSlopeDegrees(dimension, x, y),
                    northFacingFactor(dimension, x, y, 0.15f), profile.seed, x, y,
                    profile.snowLine, profile.fullSnow, profile.slopeStart, profile.slopeReject);
            row[i] = density > 0 && (profile.continuous || coherentThreshold(profile.seed, x, y) < density);
        }
    }

    private record SnowProfile(long seed, float snowLine, float fullSnow,
                               float slopeStart, float slopeReject, boolean continuous) {}

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

    /** Unambiguous Nashorn entry point, including when no progress object is supplied. */
    public static Result applyScript(Dimension dimension, boolean annotationsOnly, int annotationValue,
                                    float snowLineHeight, float fullSnowHeight, int maxSnowLayers,
                                    float slopeStart, float slopeReject, float northFacingBoost,
                                    long seed, boolean clearLowSnow, boolean addHeight,
                                    boolean dryRun, Terrain fullSnowTerrain, ScriptProgress progress) {
        return apply(dimension, annotationsOnly, annotationValue, snowLineHeight, fullSnowHeight, maxSnowLayers,
                slopeStart, slopeReject, northFacingBoost, seed, clearLowSnow, addHeight, dryRun, fullSnowTerrain, progress);
    }

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
        validateProfile(dimension, snowLineHeight, fullSnowHeight, maxSnowLayers,
                slopeStart, slopeReject, northFacingBoost);
        if (annotationValue < 0 || annotationValue > 15) {
            throw new IllegalArgumentException("Annotation value must be between 0 and 15");
        }
        final List<Tile> tiles = orderedTiles(dimension);
        final long total = tiles.size() * 128L * 128L;
        if (total == 0) {
            return new Result(0, 0, 0, 0);
        }
        if (progress != null) progress.report(0);
        final OriginalHeightWindow originalHeights = addHeight && !dryRun ? new OriginalHeightWindow(dimension) : null;
        long checked = 0, snowCovered = 0, cleared = 0, deepSnow = 0;
        for (Tile tile : tiles) {
                final int startX = tile.getX() * 128;
                final int startY = tile.getY() * 128;
                final HeightTile heightTile = originalHeights != null ? originalHeights.open(tile) : null;
                for (int tx = 0; tx < 128; tx++) {
                    final int x = startX + tx;
                    for (int ty = 0; ty < 128; ty++) {
                        final int y = startY + ty;
                        checked++;
                        if (progress != null && (checked & 1023) == 0) progress.report((double) checked / total);
                        if (!eligible(dimension, tile, tx, ty)) continue;
                        if (annotationsOnly && dimension.getLayerValueAt(Annotations.INSTANCE, x, y) != annotationValue) {
                            continue;
                        }
                        final float height = dimension.getHeightAt(x, y);
                        final float coverage = coverage(height, getSlopeDegrees(dimension, heightTile, x, y),
                                northFacingFactor(dimension, heightTile, x, y, northFacingBoost),
                                seed, x, y, snowLineHeight, fullSnowHeight, slopeStart, slopeReject);
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
                        } else if (clearLowSnow && height < snowLineHeight
                                && (dimension.getBitLayerValueAt(Frost.INSTANCE, x, y)
                                || dimension.getLayerValueAt(SnowDepth.INSTANCE, x, y) != 0)) {
                            cleared++;
                            if (! dryRun) {
                                dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, false);
                                dimension.setLayerValueAt(SnowDepth.INSTANCE, x, y, 0);
                            }
                        }
                    }
                }
                if (progress != null) {
                    progress.report((double) checked / total);
                }
        }
        if (!dryRun && (snowCovered > 0 || cleared > 0)) {
            ensureSmoothFrost(dimension);
        }
        return new Result(checked, snowCovered, cleared, deepSnow);
    }

    /**
     * The legacy Global Realistic Snow formula, evaluated in Java over present tiles.
     * This preserves its height-squared threshold and eight-neighbour slope units;
     * it is deliberately not replaced with the mountain profile's noise or angles.
     * As with {@link #apply}, the caller owns the undo/rollback transaction.
     */
    public static Result applyRealistic(Dimension dimension, float snowLineHeight, float fullSnowHeight,
                                        float slopeLimit, float waterBuffer, boolean paintBiome, int biome,
                                        boolean clearLowSnow, boolean dryRun, ScriptProgress progress) {
        validateProfile(dimension, snowLineHeight, fullSnowHeight, 8, 0, 90, 0);
        if (!Float.isFinite(slopeLimit) || slopeLimit <= 0 || !Float.isFinite(waterBuffer) || waterBuffer < 0) {
            throw new IllegalArgumentException("Slope limit must be finite and positive; water buffer must be finite and non-negative");
        }
        if (biome < 0 || biome > 255) throw new IllegalArgumentException("Biome id must be between 0 and 255");
        final List<Tile> tiles = orderedTiles(dimension);
        final long total = tiles.size() * 16384L;
        long checked = 0, covered = 0, cleared = 0;
        if (progress != null) { progress.checkForCancel(); progress.setProgress(0); }
        for (Tile tile : tiles) {
            final int startX = tile.getX() * 128, startY = tile.getY() * 128;
            for (int tx = 0; tx < 128; tx++) {
                for (int ty = 0; ty < 128; ty++) {
                    checked++;
                    if (progress != null && (checked & 1023) == 0) {
                        progress.checkForCancel();
                        progress.setProgress(checked / (double) total);
                    }
                    if (!eligible(dimension, tile, tx, ty)) continue;
                    final int x = startX + tx, y = startY + ty;
                    final float height = tile.getHeight(tx, ty);
                    float slope = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx != 0 || dy != 0) slope = Math.max(slope,
                                    Math.abs(height - heightAtOrCenter(dimension, x + dx, y + dy, height)));
                        }
                    }
                    final float elevation = clamp((height - snowLineHeight) / (fullSnowHeight - snowLineHeight));
                    final float nearWater = height <= tile.getWaterLevel(tx, ty) + waterBuffer ? 0.25f : 1;
                    final boolean snow = height >= snowLineHeight
                            && elevation * elevation * (1 - clamp(slope / slopeLimit)) * nearWater > 0.5f;
                    if (snow) {
                        covered++;
                        if (!dryRun) {
                            dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, true);
                            if (paintBiome) dimension.setLayerValueAt(Biome.INSTANCE, x, y, biome);
                        }
                    } else if (clearLowSnow && (tile.getBitLayerValue(Frost.INSTANCE, tx, ty)
                            || tile.getLayerValue(SnowDepth.INSTANCE, tx, ty) != 0)) {
                        cleared++;
                        if (!dryRun) {
                            dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, false);
                            dimension.setLayerValueAt(SnowDepth.INSTANCE, x, y, 0);
                        }
                    }
                }
            }
        }
        if (progress != null) { progress.setProgress(1); progress.checkForCancel(); }
        return new Result(checked, covered, cleared, 0);
    }

    private static void validateProfile(Dimension dimension, float snowLine, float fullSnow, int maxLayers,
                                        float slopeStart, float slopeReject, float northBoost) {
        if (dimension == null) throw new IllegalArgumentException("An open dimension is required");
        if (!Float.isFinite(snowLine) || !Float.isFinite(fullSnow) || !Float.isFinite(fullSnow - snowLine)
                || fullSnow <= snowLine) {
            throw new IllegalArgumentException("Finite full snow height must be above finite snow line height");
        }
        if (!Float.isFinite(slopeStart) || !Float.isFinite(slopeReject)
                || slopeStart < 0 || slopeReject > 90 || slopeReject <= slopeStart) {
            throw new IllegalArgumentException("Snow slopes must be finite, increasing, and between 0 and 90 degrees");
        }
        if (!Float.isFinite(northBoost) || northBoost < 0) {
            throw new IllegalArgumentException("North-facing boost must be finite and non-negative");
        }
        if (maxLayers < 1 || maxLayers > 8) throw new IllegalArgumentException("Maximum snow layers must be between 1 and 8");
    }

    private static List<Tile> orderedTiles(Dimension dimension) {
        final List<Tile> tiles = new ArrayList<>(dimension.getTiles());
        // Validate before touching the first tile, including the one-cell halo.
        for (Tile tile : tiles) {
            final long x = (long) tile.getX() * 128, y = (long) tile.getY() * 128;
            if (x <= Integer.MIN_VALUE || y <= Integer.MIN_VALUE
                    || x + 128 > Integer.MAX_VALUE || y + 128 > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Snow tile coordinates exceed the safe sampling range");
            }
        }
        tiles.sort(Comparator.comparingInt(Tile::getX).thenComparingInt(Tile::getY));
        return tiles;
    }

    private static boolean eligible(Dimension dimension, Tile tile, int x, int y) {
        final float height = tile.getHeight(x, y);
        return Float.isFinite(height) && height > tile.getWaterLevel(x, y) + 1.0f
                && tile.getIntHeight(x, y) < dimension.getMaxHeight() - 1
                && !tile.getBitLayerValue(Void.INSTANCE, x, y)
                && !tile.getBitLayerValue(NotPresent.INSTANCE, x, y)
                && !tile.getBitLayerValue(NotPresentBlock.INSTANCE, x, y)
                && !tile.getBitLayerValue(ReadOnly.INSTANCE, x, y)
                && !tile.getBitLayerValue(FloodWithLava.INSTANCE, x, y);
    }

    /** One 130x130 tile and original left/top borders; never a second world heightmap. */
    private static final class OriginalHeightWindow {
        OriginalHeightWindow(Dimension dimension) { this.dimension = dimension; }

        HeightTile open(Tile tile) {
            if (previousX == null || previousX != tile.getX()) {
                leftEdges = previousX != null && (long) previousX + 1 == tile.getX() ? rightEdges : Map.of();
                rightEdges = new HashMap<>();
                previousY = null;
                previousX = tile.getX();
            }
            final HeightTile result = new HeightTile(tile.getX() * 128, tile.getY() * 128);
            for (int y = -1; y <= 128; y++) {
                for (int x = -1; x <= 128; x++) {
                    result.values[(y + 1) * 130 + x + 1] = heightAtOrCenter(dimension,
                            result.x + x, result.y + y, Float.NaN);
                }
            }
            final float[] left = leftEdges.get(tile.getY());
            for (int i = 0; i < 128; i++) {
                if (left != null) result.values[(i + 1) * 130] = left[i];
                if (previousY != null && (long) previousY + 1 == tile.getY()) result.values[i + 1] = bottom[i];
            }
            final float[] right = new float[128];
            bottom = new float[128];
            for (int i = 0; i < 128; i++) {
                right[i] = result.height(result.x + 127, result.y + i);
                bottom[i] = result.height(result.x + i, result.y + 127);
            }
            rightEdges.put(tile.getY(), right);
            previousY = tile.getY();
            return result;
        }

        private final Dimension dimension;
        private Integer previousX, previousY;
        private Map<Integer, float[]> leftEdges = Map.of(), rightEdges = new HashMap<>();
        private float[] bottom;
    }

    private static final class HeightTile {
        HeightTile(int x, int y) { this.x = x; this.y = y; }
        float height(int x, int y) { return values[(y - this.y + 1) * 130 + x - this.x + 1]; }
        final int x, y;
        final float[] values = new float[130 * 130];
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
        return getSlopeDegrees(dimension, null, x, y);
    }

    private static float getSlopeDegrees(Dimension dimension, HeightTile original, int x, int y) {
        final float center = heightAtOrCenter(dimension, original, x, y, Float.NaN);
        final float dx = (heightAtOrCenter(dimension, original, x + 1, y, center) - heightAtOrCenter(dimension, original, x - 1, y, center)) / 2.0f;
        final float dy = (heightAtOrCenter(dimension, original, x, y + 1, center) - heightAtOrCenter(dimension, original, x, y - 1, center)) / 2.0f;
        return (float) Math.toDegrees(Math.atan(Math.sqrt(dx * dx + dy * dy)));
    }

    private static float northFacingFactor(Dimension dimension, int x, int y, float boost) {
        return northFacingFactor(dimension, null, x, y, boost);
    }

    private static float northFacingFactor(Dimension dimension, HeightTile original, int x, int y, float boost) {
        if (boost == 0.0f) {
            return 1.0f;
        }
        final float center = heightAtOrCenter(dimension, original, x, y, Float.NaN);
        // In WorldPainter coordinates negative Y is north. A north-facing downslope receives more snow.
        final float north = heightAtOrCenter(dimension, original, x, y - 1, center) - center;
        final float south = heightAtOrCenter(dimension, original, x, y + 1, center) - center;
        return aspectMultiplier(north, south, boost);
    }

    static float aspectMultiplier(float northHeightDelta, float southHeightDelta, float boost) {
        // A flat surface is neutral; the old [0,1] clamp gave it the full southern penalty.
        final float facingNorth = Math.max(-1.0f, Math.min(1.0f, (southHeightDelta - northHeightDelta) / 8.0f));
        return Math.max(0.1f, 1.0f + facingNorth * boost);
    }

    private static float heightAtOrCenter(Dimension dimension, int x, int y, float center) {
        final Tile tile = dimension.getTile(x >> 7, y >> 7);
        if (tile == null || tile.getBitLayerValue(Void.INSTANCE, x & 127, y & 127)
                || tile.getBitLayerValue(NotPresent.INSTANCE, x & 127, y & 127)
                || tile.getBitLayerValue(NotPresentBlock.INSTANCE, x & 127, y & 127)) return center;
        final float height = tile.getHeight(x & 127, y & 127);
        return Float.isFinite(height) ? height : center;
    }

    private static float heightAtOrCenter(Dimension dimension, HeightTile original, int x, int y, float center) {
        if (original == null) return heightAtOrCenter(dimension, x, y, center);
        final float height = original.height(x, y);
        return Float.isFinite(height) ? height : center;
    }

    private static void addSmoothHeight(Dimension dimension, int x, int y, float coverage, int maxSnowLayers) {
        final float currentHeight = dimension.getHeightAt(x, y);
        final float maximumDepth = maxSnowLayers / 8.0f;
        final float targetFraction = Math.min(0.875f, coverage * maximumDepth);
        // The rounded supporting block must leave one valid voxel above it for
        // the exported snow. Only the optional addition is capped; never lower
        // the user's existing terrain at the world's ceiling.
        final float highestSnowSupport = dimension.getMaxHeight() - 1.5f - 1f / 256f;
        final float targetHeight = Math.min(highestSnowSupport, (float) Math.floor(currentHeight)
                + Math.max(currentHeight - (float) Math.floor(currentHeight), targetFraction));
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
