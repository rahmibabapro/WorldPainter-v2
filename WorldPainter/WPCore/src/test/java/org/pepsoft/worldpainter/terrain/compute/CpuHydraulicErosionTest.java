package org.pepsoft.worldpainter.terrain.compute;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CpuHydraulicErosionTest {
    @Test
    public void smoothsSpike() {
        int w = 5, h = 5;
        float[] heights = new float[w * h];
        heights[2 + 2 * w] = 100f;
        new CpuHydraulicErosion().apply(heights, w, h, new TerrainComputeOp.ComputeParams(5, 0.5f));
        assertTrue(heights[2 + 2 * w] < 100f);
        assertTrue(heights[2 + 2 * w] > 0f);
    }

    @Test
    public void gpuFallbackRuns() {
        float[] heights = new float[]{0, 1, 0, 1, 5, 1, 0, 1, 0};
        new GpuHydraulicErosion().apply(heights, 3, 3, new TerrainComputeOp.ComputeParams(2, 0.4f));
        assertTrue(heights[4] < 5f);
    }
}
