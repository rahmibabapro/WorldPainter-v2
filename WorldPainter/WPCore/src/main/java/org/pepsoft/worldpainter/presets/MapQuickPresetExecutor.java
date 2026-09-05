package org.pepsoft.worldpainter.presets;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.Void;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * Native implementations of quick map presets (faster than equivalent JS global ops).
 */
public final class MapQuickPresetExecutor {
    private MapQuickPresetExecutor() {
    }

    public static void apply(Dimension dimension, MapQuickPreset preset, float slopeThreshold, ProgressReceiver progressReceiver) throws OperationCancelled {
        switch (preset) {
            case GRASS_BASE_STEEP_ROCK -> applyGrassBaseSteepRock(dimension, slopeThreshold, progressReceiver);
            case STONE_BASE -> fillTerrain(dimension, Terrain.STONE, progressReceiver);
            case DRY_WORLD -> removeWater(dimension, progressReceiver);
            case REALISTIC_SNOW -> throw new UnsupportedOperationException("Realistic snow is applied via the bundled Snowify script");
            default -> throw new InternalError();
        }
    }

    public static void applyGrassBaseSteepRock(Dimension dimension, float slopeThreshold, ProgressReceiver progressReceiver) throws OperationCancelled {
        fillTerrain(dimension, Terrain.GRASS, progressReceiver);
        applySteepSlopeTerrain(dimension, slopeThreshold, Terrain.ROCK, progressReceiver);
    }

    public static void fillTerrain(Dimension dimension, Terrain terrain, ProgressReceiver progressReceiver) throws OperationCancelled {
        dimension.visitTilesForEditing().andDo(tile -> {
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    if (tile.getTerrain(x, y) != terrain) {
                        tile.setTerrain(x, y, terrain);
                    }
                }
            }
        }, progressReceiver);
    }

    public static void applySteepSlopeTerrain(Dimension dimension, float slopeThreshold, Terrain terrain, ProgressReceiver progressReceiver) throws OperationCancelled {
        dimension.visitTilesForEditing().andDo(tile -> {
            final int worldTileX = tile.getX() << TILE_SIZE_BITS;
            final int worldTileY = tile.getY() << TILE_SIZE_BITS;
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    final int worldX = worldTileX | x;
                    final int worldY = worldTileY | y;
                    if (dimension.getSlope(worldX, worldY) >= slopeThreshold && tile.getTerrain(x, y) != terrain) {
                        tile.setTerrain(x, y, terrain);
                    }
                }
            }
        }, progressReceiver);
    }

    public static void applyTerrainOnSteepSlopes(Dimension dimension, Terrain terrain, int degreesAbove, ProgressReceiver progressReceiver) throws OperationCancelled {
        final float slopeThreshold = (float) Math.tan(Math.toRadians(degreesAbove));
        applySteepSlopeTerrain(dimension, slopeThreshold, terrain, progressReceiver);
    }

    /** Stone Mix on slopes at or above the threshold; grass on gentler slopes.
     * Stone Mix uses stone (not deepslate) below y=0 as well. */
    public static void applyStoneMixGrassSlopeSplit(Dimension dimension, int degreesThreshold, ProgressReceiver progressReceiver) throws OperationCancelled {
        if (dimension == null || degreesThreshold < 0 || degreesThreshold > 90) {
            throw new IllegalArgumentException("A dimension and a slope threshold from 0 to 90 degrees are required.");
        }
        final float slopeThreshold = (float) Math.tan(Math.toRadians(degreesThreshold));
        final int totalTiles = Math.max(1, dimension.getTiles().size());
        int completed = 0;
        if (progressReceiver != null) progressReceiver.checkForCancellation();
        for (Tile source : dimension.getTiles()) {
            final Tile tile = dimension.getTileForEditing(source.getX(), source.getY());
            if (tile == null) continue;
            final int worldTileX = tile.getX() << TILE_SIZE_BITS;
            final int worldTileY = tile.getY() << TILE_SIZE_BITS;
            final boolean hasMissingSurface = tile.hasLayer(Void.INSTANCE)
                    || tile.hasLayer(NotPresent.INSTANCE) || tile.hasLayer(NotPresentBlock.INSTANCE);
            for (int x = 0; x < TILE_SIZE; x++) {
                if (progressReceiver != null && (x & 7) == 0) progressReceiver.checkForCancellation();
                for (int y = 0; y < TILE_SIZE; y++) {
                    if (!Float.isFinite(tile.getHeight(x, y))
                            || tile.getBitLayerValue(ReadOnly.INSTANCE, x, y)
                            || tile.getBitLayerValue(Void.INSTANCE, x, y)
                            || tile.getBitLayerValue(NotPresent.INSTANCE, x, y)
                            || tile.getBitLayerValue(NotPresentBlock.INSTANCE, x, y)
                            || tile.getBitLayerValue(FloodWithLava.INSTANCE, x, y)) continue;
                    final int worldX = worldTileX | x;
                    final int worldY = worldTileY | y;
                    final float slope = (hasMissingSurface || x == 0 || x == TILE_SIZE - 1 || y == 0 || y == TILE_SIZE - 1)
                            ? slopeWithSurfaceNeighbours(dimension, worldX, worldY, tile.getHeight(x, y))
                            : tile.getSlope(x, y);
                    if (!Float.isFinite(slope)) continue;
                    final Terrain target = slope >= slopeThreshold
                            ? Terrain.STONE_MIX
                            : Terrain.GRASS;
                    if (tile.getTerrain(x, y) != target) {
                        tile.setTerrain(x, y, target);
                    }
                }
            }
            if (progressReceiver != null) progressReceiver.setProgress(++completed / (float) totalTiles);
        }
    }

    /** Same four-direction slope, substituting the centre only for no-data neighbours. */
    private static float slopeWithSurfaceNeighbours(Dimension dimension, int x, int y, float height) {
        return Math.max(Math.max(Math.abs(surfaceHeight(dimension, x + 1, y, height) - surfaceHeight(dimension, x - 1, y, height)) / 2,
                        Math.abs(surfaceHeight(dimension, x, y + 1, height) - surfaceHeight(dimension, x, y - 1, height)) / 2),
                Math.max(Math.abs(surfaceHeight(dimension, x + 1, y + 1, height) - surfaceHeight(dimension, x - 1, y - 1, height)) / (float) Math.sqrt(8),
                        Math.abs(surfaceHeight(dimension, x - 1, y + 1, height) - surfaceHeight(dimension, x + 1, y - 1, height)) / (float) Math.sqrt(8)));
    }

    private static float surfaceHeight(Dimension dimension, int x, int y, float center) {
        final Tile tile = dimension.getTile(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS);
        final int localX = x & (TILE_SIZE - 1), localY = y & (TILE_SIZE - 1);
        if (tile == null || tile.getBitLayerValue(Void.INSTANCE, localX, localY)
                || tile.getBitLayerValue(NotPresent.INSTANCE, localX, localY)
                || tile.getBitLayerValue(NotPresentBlock.INSTANCE, localX, localY)) return center;
        final float height = tile.getHeight(localX, localY);
        return Float.isFinite(height) ? height : center;
    }

    private static void removeWater(Dimension dimension, ProgressReceiver progressReceiver) throws OperationCancelled {
        // Water storage is unsigned relative to minHeight; -1 wraps in minY=0 worlds.
        final int dryLevel = Math.max(dimension.getMinHeight(), -1);
        dimension.visitTilesForEditing().andDo(tile -> {
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    if (tile.getWaterLevel(x, y) >= 1) {
                        tile.setWaterLevel(x, y, dryLevel);
                    }
                }
            }
        }, progressReceiver);
    }
}
