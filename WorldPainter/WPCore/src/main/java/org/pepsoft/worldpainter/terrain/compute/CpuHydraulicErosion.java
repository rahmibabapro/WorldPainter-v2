package org.pepsoft.worldpainter.terrain.compute;

/**
 * Simple thermal/hydraulic-style slope equalization (CPU). Used as GPU fallback.
 */
public final class CpuHydraulicErosion implements TerrainComputeOp {
    @Override
    public String name() {
        return "cpu-hydraulic";
    }

    @Override
    public void apply(float[] heights, int width, int height, ComputeParams params) {
        float[] next = new float[heights.length];
        System.arraycopy(heights, 0, next, 0, heights.length);
        float strength = params.strength;
        for (int iter = 0; iter < params.iterations; iter++) {
            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    int i = x + y * width;
                    float h = heights[i];
                    float sum = heights[i - 1] + heights[i + 1] + heights[i - width] + heights[i + width];
                    float avg = sum * 0.25f;
                    next[i] = h + (avg - h) * strength;
                }
            }
            System.arraycopy(next, 0, heights, 0, heights.length);
        }
    }
}
