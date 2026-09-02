package org.pepsoft.worldpainter.terrain.hydrology.river;

import java.io.Serializable;

/**
 * 2x2x2 Octant micro-block detailer enabling smooth 0.5m sub-steps and gravel transitions for rivers.
 */
public final class MicroBlockDetailer implements Serializable {

    public static class OctantSubBlock {
        public final int x, y, z; // Voxel coordinates
        public final int subX, subY, subZ; // 0 or 1 within the 2x2x2 octant
        public final String materialId;

        public OctantSubBlock(int x, int y, int z, int subX, int subY, int subZ, String materialId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.subX = subX;
            this.subY = subY;
            this.subZ = subZ;
            this.materialId = materialId;
        }
    }

    private MicroBlockDetailer() {}

    /**
     * Computes the 2x2x2 sub-block step height for a continuous float height.
     * Returns height in 0.5m half-block increments.
     */
    public static float snapToHalfBlock(float continuousHeight) {
        return Math.round(continuousHeight * 2.0f) / 2.0f;
    }

    /**
     * Determines which 2x2x2 octants are solid vs water for a given continuous bank slope.
     */
    public static boolean[] computeOctantSolidMask(float fractionalHeight) {
        boolean[] solid = new boolean[8]; // [subX + 2*subZ + 4*subY]
        // 0.0 -> bottom half empty
        // 0.5 -> bottom 4 sub-voxels solid (subY=0)
        // 1.0 -> all 8 sub-voxels solid
        int filledCount = (int) Math.round(fractionalHeight * 8.0f);
        for (int i = 0; i < 8; i++) {
            solid[i] = (i < filledCount);
        }
        return solid;
    }

    private static final long serialVersionUID = 1L;
}
