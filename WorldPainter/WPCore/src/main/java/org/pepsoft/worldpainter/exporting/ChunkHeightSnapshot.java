package org.pepsoft.worldpainter.exporting;

import org.pepsoft.worldpainter.Dimension;

/**
 * 18×18 height grid (1-block halo) for a chunk, filled once and reused by surface smoothing and steep-terrain cover.
 */
public final class ChunkHeightSnapshot {
    public static final int GRID_SIZE = 18;

    private ChunkHeightSnapshot(int originWorldX, int originWorldY, float[] grid) {
        this.originWorldX = originWorldX;
        this.originWorldY = originWorldY;
        this.grid = grid;
    }

    public static ChunkHeightSnapshot create(Dimension dimension, int chunkX, int chunkZ) {
        final int originWorldX = (chunkX << 4) - 1;
        final int originWorldY = (chunkZ << 4) - 1;
        final float[] grid = new float[GRID_SIZE * GRID_SIZE];
        int index = 0;
        for (int dy = 0; dy < GRID_SIZE; dy++) {
            for (int dx = 0; dx < GRID_SIZE; dx++) {
                grid[index++] = dimension.getHeightAt(originWorldX + dx, originWorldY + dy);
            }
        }
        return new ChunkHeightSnapshot(originWorldX, originWorldY, grid);
    }

    public float getHeightAt(int worldX, int worldY) {
        final int dx = worldX - originWorldX;
        final int dy = worldY - originWorldY;
        if ((dx >= 0) && (dx < GRID_SIZE) && (dy >= 0) && (dy < GRID_SIZE)) {
            return grid[dy * GRID_SIZE + dx];
        }
        return ExportHeightSnapshot.MISSING;
    }

    public int getIntHeightAt(int worldX, int worldY, int defaultHeight) {
        final float height = getHeightAt(worldX, worldY);
        return ExportHeightSnapshot.isMissing(height) ? defaultHeight : Math.round(height);
    }

    private final int originWorldX, originWorldY;
    private final float[] grid;
}
