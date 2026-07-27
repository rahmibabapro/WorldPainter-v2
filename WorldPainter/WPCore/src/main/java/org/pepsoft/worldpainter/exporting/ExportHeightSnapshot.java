package org.pepsoft.worldpainter.exporting;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;

import java.awt.*;
import java.util.Map;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_MASK;

/**
 * Read-only height snapshot of tiles used during export, avoiding synchronized {@link Tile#getHeight(int, int)} contention.
 */
public final class ExportHeightSnapshot {
    private ExportHeightSnapshot(int minTileX, int minTileY, int tilesWide, int tilesHigh, float[][] tileHeights) {
        this.minTileX = minTileX;
        this.minTileY = minTileY;
        this.tilesWide = tilesWide;
        this.tilesHigh = tilesHigh;
        this.tileHeights = tileHeights;
    }

    /**
     * Capture heights from the given tiles (typically the region halo set from export).
     */
    public static ExportHeightSnapshot capture(Map<Point, Tile> tiles) {
        if ((tiles == null) || tiles.isEmpty()) {
            return null;
        }
        int minTileX = Integer.MAX_VALUE, minTileY = Integer.MAX_VALUE;
        int maxTileX = Integer.MIN_VALUE, maxTileY = Integer.MIN_VALUE;
        for (Point coords : tiles.keySet()) {
            minTileX = Math.min(minTileX, coords.x);
            minTileY = Math.min(minTileY, coords.y);
            maxTileX = Math.max(maxTileX, coords.x);
            maxTileY = Math.max(maxTileY, coords.y);
        }
        final int tilesWide = maxTileX - minTileX + 1;
        final int tilesHigh = maxTileY - minTileY + 1;
        final float[][] heights = new float[tilesWide * tilesHigh][];
        for (Map.Entry<Point, Tile> entry : tiles.entrySet()) {
            final Point coords = entry.getKey();
            final int index = (coords.y - minTileY) * tilesWide + (coords.x - minTileX);
            heights[index] = snapshotTileHeights(entry.getValue());
        }
        return new ExportHeightSnapshot(minTileX, minTileY, tilesWide, tilesHigh, heights);
    }

    public float getHeightAt(int worldX, int worldY) {
        final int tileX = worldX >> TILE_SIZE_BITS;
        final int tileY = worldY >> TILE_SIZE_BITS;
        final float[] tileHeightMap = tileHeightsAt(tileX, tileY);
        if (tileHeightMap == null) {
            return MISSING;
        }
        return tileHeightMap[(worldX & TILE_SIZE_MASK) | ((worldY & TILE_SIZE_MASK) << TILE_SIZE_BITS)];
    }

    public int getIntHeightAt(int worldX, int worldY, int defaultHeight) {
        final float height = getHeightAt(worldX, worldY);
        return isMissing(height) ? defaultHeight : Math.round(height);
    }

    private float[] tileHeightsAt(int tileX, int tileY) {
        if ((tileX < minTileX) || (tileX >= minTileX + tilesWide) || (tileY < minTileY) || (tileY >= minTileY + tilesHigh)) {
            return null;
        }
        return tileHeights[(tileY - minTileY) * tilesWide + (tileX - minTileX)];
    }

    private static float[] snapshotTileHeights(Tile tile) {
        final float[] result = new float[TILE_SIZE * TILE_SIZE];
        synchronized (tile) {
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    result[x | (y << TILE_SIZE_BITS)] = tile.getHeight(x, y);
                }
            }
        }
        return result;
    }

    public static boolean isMissing(float height) {
        return height < -1.0e30f;
    }

    public static final float MISSING = -Float.MAX_VALUE;

    private final int minTileX, minTileY, tilesWide, tilesHigh;
    private final float[][] tileHeights;
}
