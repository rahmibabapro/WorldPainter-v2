package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;

import java.awt.Rectangle;

import static org.junit.Assert.*;

public class BridgeTerrainOpsTest {
    @Test public void fortyFiveDegreesMapsToUnitSlope() {
        assertEquals(1.0, BridgeTerrainOps.degreesToSlope(45), 1e-4);
        assertEquals(0.0, BridgeTerrainOps.degreesToSlope(0), 0);
        assertTrue(BridgeTerrainOps.degreesToSlope(60) > 1.0);
    }

    @Test public void sculptRaiseIncreasesCentreAndLeavesFarCell() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            d.setHeightAt(x, y, 100);
            d.setTerrainAt(x, y, Terrain.GRASS);
        }
        int n = BridgeTerrainOps.sculpt(d, 64, 64, 20, 30, "RAISE");
        assertTrue(n > 0);
        assertTrue(d.getHeightAt(64, 64) > 120);
        assertEquals(100, d.getHeightAt(10, 10), 0);
    }

    @Test public void paintRespectsSlopeDegreesLikeDefaultFilter() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            d.setHeightAt(x, y, 100);
            d.setTerrainAt(x, y, Terrain.GRASS);
        }
        for (int y = 40; y <= 80; y++) for (int x = 60; x <= 68; x++) d.setHeightAt(x, y, 100 + (x - 60) * 8);
        int steep = BridgeTerrainOps.paint(d, 0, 127, 0, 127, Terrain.STONE, null, 0, 512, 45, 90);
        assertTrue(steep > 0);
        assertEquals(Terrain.STONE, d.getTerrainAt(64, 60));
        assertEquals(Terrain.GRASS, d.getTerrainAt(20, 20));
    }

    @Test public void paintBboxLimitRejectsHugeRegion() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        assertThrows(IllegalArgumentException.class, () ->
                BridgeTerrainOps.paint(d, 0, 2000, 0, 2000, Terrain.STONE, null, 0, 512, 0, 90));
    }
}
