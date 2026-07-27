package org.pepsoft.worldpainter.presets;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
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
        final float slopeThreshold = (float) Math.tan(Math.toRadians(degreesThreshold));
        dimension.visitTilesForEditing().andDo(tile -> {
            final int worldTileX = tile.getX() << TILE_SIZE_BITS;
            final int worldTileY = tile.getY() << TILE_SIZE_BITS;
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    final int worldX = worldTileX | x;
                    final int worldY = worldTileY | y;
                    final Terrain target = dimension.getSlope(worldX, worldY) >= slopeThreshold
                            ? Terrain.STONE_MIX
                            : Terrain.GRASS;
                    if (tile.getTerrain(x, y) != target) {
                        tile.setTerrain(x, y, target);
                    }
                }
            }
        }, progressReceiver);
    }

    private static void removeWater(Dimension dimension, ProgressReceiver progressReceiver) throws OperationCancelled {
        dimension.visitTilesForEditing().andDo(tile -> {
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    if (tile.getWaterLevel(x, y) >= 1) {
                        tile.setWaterLevel(x, y, -1);
                    }
                }
            }
        }, progressReceiver);
    }
}
