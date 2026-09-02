package org.pepsoft.worldpainter.spatial.module;

import org.junit.Test;
import org.pepsoft.worldpainter.spatial.module.ModulePort.PortType;

import static org.junit.Assert.*;

public class WorldModulePrefabTest {

    @Test
    public void testPortCompatibility() {
        // Port facing East (90 deg) and Port facing West (270 deg)
        ModulePort eastRoad = new ModulePort("road-east", PortType.ROAD, 50, 25, 0f, 90.0);
        ModulePort westRoad = new ModulePort("road-west", PortType.ROAD, 0, 25, 0f, 270.0);
        ModulePort riverPort = new ModulePort("river-in", PortType.RIVER, 25, 0, 0f, 0.0);

        assertTrue(eastRoad.isCompatibleWith(westRoad));
        assertFalse(eastRoad.isCompatibleWith(riverPort)); // Incompatible port type
    }

    @Test
    public void testPrefabTerrainPlacementAndBlending() {
        int worldW = 64, worldH = 64;
        float[][] worldHeights = new float[worldW][worldH];
        for (int x = 0; x < worldW; x++) {
            for (int z = 0; z < worldH; z++) {
                worldHeights[x][z] = 50.0f; // Surrounding terrain at height 50
            }
        }

        // Module of size 20x20 with 4 block blend margin at target elevation 70.0
        int mw = 20, mh = 20;
        float[][] patch = new float[mw][mh]; // Flat patch
        WorldModulePrefab prefab = new WorldModulePrefab("village_01", "Medieval Village", mw, mh, 4, patch);

        prefab.applyToTerrain(worldHeights, worldW, worldH, 10, 10, 70.0f);

        // Core of module (e.g. wx=20, wz=20 -> lx=10, lz=10, distToEdge=9 > 4) must be exact 70.0
        assertEquals(70.0f, worldHeights[20][20], 0.01f);

        // Boundary edge (e.g. wx=10, wz=20 -> lx=0, lz=10, distToEdge=0) must remain natural 50.0
        assertEquals(50.0f, worldHeights[10][20], 0.01f);

        // Blend margin (e.g. wx=12, wz=20 -> lx=2, lz=10, distToEdge=2) must be blended between 50 and 70
        assertTrue("Blended height must be between 50 and 70: " + worldHeights[12][20],
                worldHeights[12][20] > 50.0f && worldHeights[12][20] < 70.0f);

        // Outside module (wx=5, wz=5) must be untouched 50.0
        assertEquals(50.0f, worldHeights[5][5], 0.01f);
    }
}
