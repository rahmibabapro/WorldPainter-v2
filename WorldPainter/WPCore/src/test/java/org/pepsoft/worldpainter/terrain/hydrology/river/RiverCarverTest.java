package org.pepsoft.worldpainter.terrain.hydrology.river;

import org.junit.Test;
import org.pepsoft.worldpainter.spatial.geometry.SpatialGeometry.SpatialPoint;
import org.pepsoft.worldpainter.terrain.hydrology.river.RiverNetwork.RiverReach;

import static org.junit.Assert.*;

public class RiverCarverTest {

    @Test
    public void testPowerLawWidthAndDepthScalesWithArea() {
        float smallWidth = RiverCarver.computeChannelWidth(5.0f, 0.05f, 2.0f, 30.0f);
        float largeWidth = RiverCarver.computeChannelWidth(500.0f, 0.01f, 2.0f, 30.0f);

        assertTrue("Downstream large drainage area must produce wider river: small=" + smallWidth + ", large=" + largeWidth,
                largeWidth > smallWidth * 2.0f);
    }

    @Test
    public void testReachCarvingLowersTerrainAlongLine() {
        int w = 32, h = 32;
        float[][] heights = new float[w][h];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                heights[x][z] = 64.0f;
            }
        }

        // Reach running through center (x: 0..32, z: 16) with width 6, depth 3
        RiverReach reach = new RiverReach(1, new SpatialPoint(0, 16), new SpatialPoint(32, 16), 1, 20f, 0.02f, 6.0f, 3.0f);
        RiverCarver.carveReach(heights, w, h, reach);

        // Center line (z=16) should be lowered by ~depth (64 - 3 = 61)
        assertEquals(61.0f, heights[16][16], 0.1f);
        // Bank edge (z=14) should be partially carved (e.g. 62-63)
        assertTrue(heights[16][14] < 64.0f && heights[16][14] > 61.0f);
        // Far outside corridor (z=5) should be untouched at 64.0
        assertEquals(64.0f, heights[16][5], 0.01f);
    }
}
