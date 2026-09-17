package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BridgeRiverOpsTest {
    @Test
    public void orderDownhillFlipsAscendingEndpoints() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        for (int dy = -12; dy <= 12; dy++) for (int dx = -12; dx <= 12; dx++) {
            if (dx * dx + dy * dy > 144) continue;
            d.setHeightAt(10 + dx, 10 + dy, 40f);
            d.setHeightAt(80 + dx, 80 + dy, 20f);
        }
        var ordered = BridgeRiverOps.orderDownhill(d, List.of(
                new DrawnRiverGraph.Pixel(80, 80),
                new DrawnRiverGraph.Pixel(10, 10)));
        assertEquals(10, ordered.get(0).x());
        assertEquals(10, ordered.get(0).y());
        assertEquals(80, ordered.get(ordered.size() - 1).x());
    }

    @Test
    public void adaptCarveSucceedsOnFlatTestDimension() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        d.rememberChanges();
        var result = BridgeRiverOps.carve(d,
                List.of(new DrawnRiverGraph.Pixel(20, 20),
                        new DrawnRiverGraph.Pixel(60, 40),
                        new DrawnRiverGraph.Pixel(100, 80)),
                6, 3, true, true, BridgeRiverOps.Mode.ADAPT);
        assertTrue(result.rejection(), result.success());
        assertEquals("adapt", result.mode());
        assertTrue(result.changedCells() > 0);
        assertNotNull(result.pathUsed());
    }

    @Test
    public void autoSucceedsOnFlatMap() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        d.rememberChanges();
        var result = BridgeRiverOps.carve(d,
                List.of(new DrawnRiverGraph.Pixel(16, 16),
                        new DrawnRiverGraph.Pixel(48, 32),
                        new DrawnRiverGraph.Pixel(96, 64)),
                6, 3, true, true, BridgeRiverOps.Mode.AUTO);
        assertTrue(result.rejection(), result.success());
        assertTrue(result.changedCells() > 0);
        assertTrue("mode=" + result.mode(), "preserve".equals(result.mode()) || "adapt".equals(result.mode()));
    }
}
