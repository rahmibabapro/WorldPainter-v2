package org.pepsoft.worldpainter.terrain.hydrology.river;

import org.pepsoft.worldpainter.terrain.hydrology.river.RiverNetwork.RiverReach;

/**
 * Carves hydraulic geometry (valleys, riverbeds, banks) into terrain heightmaps.
 */
public final class RiverCarver {

    private RiverCarver() {}

    /**
     * Hydraulic geometry power-law width calculation:
     * width = clamp(minW, kW * (area ^ a) * (slope ^ b), maxW)
     */
    public static float computeChannelWidth(float drainageArea, float slope, float minWidth, float maxWidth) {
        float rawWidth = 2.0f * (float) Math.pow(Math.max(1.0f, drainageArea), 0.4) * (float) Math.pow(Math.max(0.01f, slope), -0.1);
        return Math.max(minWidth, Math.min(maxWidth, rawWidth));
    }

    /**
     * Hydraulic geometry power-law depth calculation:
     * depth = clamp(minD, kD * (area ^ c) * (slope ^ d), maxD)
     */
    public static float computeChannelDepth(float drainageArea, float slope, float minDepth, float maxDepth) {
        float rawDepth = 0.8f * (float) Math.pow(Math.max(1.0f, drainageArea), 0.3) * (float) Math.pow(Math.max(0.01f, slope), -0.05);
        return Math.max(minDepth, Math.min(maxDepth, rawDepth));
    }

    /**
     * Carves a reach cross-section into a heightmap buffer.
     */
    public static void carveReach(float[][] heights, int w, int h, RiverReach reach) {
        int x0 = (int) reach.start.x, z0 = (int) reach.start.z;
        int x1 = (int) reach.end.x, z1 = (int) reach.end.z;
        int radius = (int) Math.ceil(reach.width / 2.0f);

        int minX = Math.max(0, Math.min(x0, x1) - radius);
        int maxX = Math.min(w - 1, Math.max(x0, x1) + radius);
        int minZ = Math.max(0, Math.min(z0, z1) - radius);
        int maxZ = Math.min(h - 1, Math.max(z0, z1) + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dist = distToSegment(x, z, x0, z0, x1, z1);
                if (dist <= reach.width / 2.0f) {
                    float factor = 1.0f - (float) (dist / (reach.width / 2.0f));
                    // Parabolic riverbed cross-section
                    float carveAmount = reach.depth * factor * factor;
                    heights[x][z] = Math.max(0f, heights[x][z] - carveAmount);
                }
            }
        }
    }

    private static double distToSegment(double px, double pz, double x1, double z1, double x2, double z2) {
        double dx = x2 - x1, dz = z2 - z1;
        double lenSq = dx * dx + dz * dz;
        if (lenSq == 0) return Math.sqrt((px - x1) * (px - x1) + (pz - z1) * (pz - z1));
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (pz - z1) * dz) / lenSq));
        double projX = x1 + t * dx;
        double projZ = z1 + t * dz;
        double diffX = px - projX, diffZ = pz - projZ;
        return Math.sqrt(diffX * diffX + diffZ * diffZ);
    }
}
