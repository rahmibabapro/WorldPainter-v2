package org.pepsoft.worldpainter.terrain.hydrology.river;

import org.junit.Test;
import org.pepsoft.worldpainter.spatial.geometry.SpatialGeometry.SpatialPoint;
import org.pepsoft.worldpainter.terrain.hydrology.river.RiverNetwork.RiverReach;

import static org.junit.Assert.*;

public class LayeredRiverFlowTest {

    @Test
    public void testContinuousWaterElevationTo8LayerMapping() {
        // 64.875m -> baseY=64, layers=7, vanilla water level=1 (7/8ths full)
        WaterLayerState state7 = WaterLayerState.fromContinuousElevation(64.875f);
        assertEquals(64, state7.baseY);
        assertEquals(7, state7.layers);
        assertEquals(1, state7.vanillaWaterLevel);
        assertEquals("minecraft:water[level=1]", state7.getVanillaBlockState());
        assertEquals("minecraft:water_layer[layers=7]", state7.getLayeredBlockState());

        // 64.500m -> baseY=64, layers=4, vanilla water level=4 (4/8ths half full)
        WaterLayerState state4 = WaterLayerState.fromContinuousElevation(64.500f);
        assertEquals(64, state4.baseY);
        assertEquals(4, state4.layers);
        assertEquals(4, state4.vanillaWaterLevel);

        // 64.125m -> baseY=64, layers=1, vanilla water level=7 (1/8th shallow)
        WaterLayerState state1 = WaterLayerState.fromContinuousElevation(64.125f);
        assertEquals(64, state1.baseY);
        assertEquals(1, state1.layers);
        assertEquals(7, state1.vanillaWaterLevel);
    }

    @Test
    public void testSmoothRiverSlopeLayers() {
        int w = 32, h = 32;
        // Reach flowing from x=0 (water Y=65.0) down to x=32 (water Y=64.0)
        RiverReach reach = new RiverReach(1, new SpatialPoint(0, 16), new SpatialPoint(32, 16), 1, 20f, 0.03f, 6.0f, 2.0f);
        WaterLayerState[][] grid = LayeredRiverFlowCarver.computeRiverWaterGrid(w, h, reach, 65.0f, 64.0f);

        // Near start (x=4): continuous Y ~ 64.875 -> layers = 7
        assertNotNull(grid[4][16]);
        assertEquals(64, grid[4][16].baseY);
        assertTrue("Expected upper layers near start: " + grid[4][16].layers, grid[4][16].layers >= 6);

        // At midpoint (x=16): continuous Y ~ 64.5 -> layers = 4
        assertNotNull(grid[16][16]);
        assertEquals(64, grid[16][16].baseY);
        assertEquals(4, grid[16][16].layers);

        // Near end (x=28): continuous Y ~ 64.125 -> layers = 1
        assertNotNull(grid[28][16]);
        assertEquals(64, grid[28][16].baseY);
        assertTrue("Expected shallow layers near end: " + grid[28][16].layers, grid[28][16].layers <= 2);
    }
}
