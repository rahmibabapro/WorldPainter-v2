package org.pepsoft.worldpainter.terrain.hydrology;

import org.junit.Test;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.SubProgressReceiver;

import static org.junit.Assert.*;

public class HydrologyErosionLabTest {

    @Test
    public void testValleyChannelsFlow() throws OperationCancelled {
        int width = 32;
        int height = 32;
        float[][] heights = new float[width][height];

        // Create a V-shaped valley: center x=16 is lowest (50), outer edges are high (100)
        for (int x = 0; x < width; x++) {
            float distFromCenter = Math.abs(x - 16);
            for (int z = 0; z < height; z++) {
                // Slope downwards towards south (z increases) and towards center (x=16)
                heights[x][z] = 50.0f + (distFromCenter * 3.0f) - (z * 0.5f);
            }
        }

        HydrologyConfig config = new HydrologyConfig();
        config.setDropletCount(10000);
        config.setSeed(12345L);

        HydrologyResult result = HydrologyErosionLab.process(heights, width, height, config, null);

        assertNotNull(result);
        assertEquals(width, result.width);
        assertEquals(height, result.height);

        // Center valley (x=16) should have significantly higher flow accumulation than outer ridge (x=0)
        float valleyFlow = result.getFlow(16, 28);
        float ridgeFlow = result.getFlow(1, 28);

        assertTrue("Valley center should accumulate much more flow than ridge: valley=" + valleyFlow + ", ridge=" + ridgeFlow,
                valleyFlow > ridgeFlow * 2.0f);
    }

    @Test
    public void testDeterministicSeed() throws OperationCancelled {
        int width = 24;
        int height = 24;
        float[][] heights = new float[width][height];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                heights[x][z] = 60.0f + (x * 0.5f) - (z * 0.3f);
            }
        }

        HydrologyConfig config = new HydrologyConfig();
        config.setDropletCount(5000);
        config.setSeed(99999L);

        HydrologyResult run1 = HydrologyErosionLab.process(heights, width, height, config, null);
        HydrologyResult run2 = HydrologyErosionLab.process(heights, width, height, config, null);

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                assertEquals("Flow accumulation must be deterministic", run1.getFlow(x, z), run2.getFlow(x, z), 0.0001f);
                assertEquals("Eroded height must be deterministic", run1.getHeight(x, z), run2.getHeight(x, z), 0.0001f);
            }
        }
    }

    @Test
    public void testProgressCancellation() {
        int width = 32;
        int height = 32;
        float[][] heights = new float[width][height];

        HydrologyConfig config = new HydrologyConfig();
        config.setDropletCount(50000);

        ProgressReceiver cancellingReceiver = new ProgressReceiver() {
            @Override public void setProgress(float progress) {}
            @Override public void setMessage(String message) {}
            @Override public void checkForCancellation() throws OperationCancelled {
                throw new OperationCancelled("Cancelled for test");
            }
            @Override public void reset() {}
            @Override public void done() {}
            @Override public void exceptionThrown(Throwable t) {}
            @Override public void subProgressStarted(SubProgressReceiver subProgressReceiver) {}
        };

        try {
            HydrologyErosionLab.process(heights, width, height, config, cancellingReceiver);
            fail("Operation should have thrown OperationCancelled");
        } catch (OperationCancelled e) {
            // Expected
        }
    }
}
