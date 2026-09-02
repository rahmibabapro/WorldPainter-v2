package org.pepsoft.worldpainter.terrain.hydrology.river;

import org.pepsoft.worldpainter.spatial.geometry.SpatialGeometry.SpatialPoint;
import org.pepsoft.worldpainter.terrain.hydrology.river.RiverNetwork.RiverReach;

/**
 * Generates smooth multi-layer water profiles (8-step sub-meter water layers) along descending river slopes.
 */
public final class LayeredRiverFlowCarver {
    private LayeredRiverFlowCarver() {}

    public static WaterLayerState[][] computeRiverWaterGrid(int w, int h, RiverReach reach, float startWaterY, float endWaterY) {
        WaterLayerState[][] grid = new WaterLayerState[w][h];
        int x0 = (int) reach.start.x, z0 = (int) reach.start.z;
        int x1 = (int) reach.end.x, z1 = (int) reach.end.z;
        float radius = reach.width / 2.0f;

        int minX = Math.max(0, (int) Math.floor(Math.min(x0, x1) - radius));
        int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(x0, x1) + radius));
        int minZ = Math.max(0, (int) Math.floor(Math.min(z0, z1) - radius));
        int maxZ = Math.min(h - 1, (int) Math.ceil(Math.max(z0, z1) + radius));

        double dx = x1 - x0, dz = z1 - z0;
        double lenSq = dx * dx + dz * dz;
        if (lenSq == 0) return grid;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double t = Math.max(0, Math.min(1, ((x - x0) * dx + (z - z0) * dz) / lenSq));
                double projX = x0 + t * dx;
                double projZ = z0 + t * dz;
                double dist = Math.sqrt((x - projX) * (x - projX) + (z - projZ) * (z - projZ));

                if (dist <= radius) {
                    // Continuous water elevation smoothly graded from startWaterY to endWaterY
                    float continuousY = (float) (startWaterY + t * (endWaterY - startWaterY));
                    grid[x][z] = WaterLayerState.fromContinuousElevation(continuousY);
                }
            }
        }
        return grid;
    }
}
