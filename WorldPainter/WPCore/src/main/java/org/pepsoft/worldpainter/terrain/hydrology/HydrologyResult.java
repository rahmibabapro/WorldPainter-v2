package org.pepsoft.worldpainter.terrain.hydrology;

/**
 * Output masks and data from Hydrology analysis.
 */
public class HydrologyResult {
    public final int width;
    public final int height;
    public final float[][] flowAccumulation;
    public final float[][] sedimentDeposit;
    public final float[][] modifiedHeightmap;

    public HydrologyResult(int width, int height, float[][] flowAccumulation, float[][] sedimentDeposit, float[][] modifiedHeightmap) {
        this.width = width;
        this.height = height;
        this.flowAccumulation = flowAccumulation;
        this.sedimentDeposit = sedimentDeposit;
        this.modifiedHeightmap = modifiedHeightmap;
    }

    public float getFlow(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= height) return 0f;
        return flowAccumulation[x][z];
    }

    public float getSediment(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= height) return 0f;
        return sedimentDeposit[x][z];
    }

    public float getHeight(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= height) return 0f;
        return modifiedHeightmap[x][z];
    }
}
