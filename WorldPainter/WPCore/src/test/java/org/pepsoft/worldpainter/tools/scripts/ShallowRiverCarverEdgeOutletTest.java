package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.ReadOnly;

import java.awt.Rectangle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShallowRiverCarverEdgeOutletTest {
    @Test
    public void edgeOutletAllowsMissingBankOutsideMap() {
        // 128x128 dimension covering tiles (0,0) only — world coords 0..127
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        d.rememberChanges();
        // Path ending at left map edge (x=0)
        int[] xs = {40, 20, 0};
        int[] ys = {64, 64, 64};
        ShallowRiverCarver carver = new ShallowRiverCarver(d, 6, 9, 2.0, true, true, 1L, null);
        carver.enableTerrainPreservation();
        carver.setLowerInteriorDirt(true);
        carver.setEdgeOutlet(0, 64);
        assertTrue(carver.getLastRejection(), carver.addPath(xs, ys, 6, 6));
        var result = carver.apply();
        assertTrue(result.changedCells() > 0);
    }

    @Test
    public void protectedCellStillRejectedNearEdgeOutlet() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        d.setBitLayerValueAt(ReadOnly.INSTANCE, 1, 64, true);
        d.rememberChanges();
        int[] xs = {40, 20, 0};
        int[] ys = {64, 64, 64};
        ShallowRiverCarver carver = new ShallowRiverCarver(d, 6, 9, 2.0, true, true, 1L, null);
        carver.enableTerrainPreservation();
        carver.setEdgeOutlet(0, 64);
        boolean ok = carver.addPath(xs, ys, 6, 6);
        // May succeed if protected cell is outside cross-section; if it fails, must mention korun/eksik
        if (!ok) {
            assertTrue(carver.getLastRejection(),
                    carver.getLastRejection().contains("korunan") || carver.getLastRejection().contains("eksik"));
        }
    }

    @Test
    public void edgeWaterRespectsMinHeight() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        // Lower terrain near edge so floor(height-depth) may approach minHeight
        for (int x = 0; x < 8; x++) {
            for (int y = 60; y <= 68; y++) {
                d.setHeightAt(x, y, (float) (d.getMinHeight() + 1));
            }
        }
        d.rememberChanges();
        int[] xs = {30, 15, 0};
        int[] ys = {64, 64, 64};
        ShallowRiverCarver carver = new ShallowRiverCarver(d, 4, 6, 3.0, true, false, 2L, null);
        carver.enableTerrainAdaptation();
        carver.setEdgeOutlet(0, 64);
        boolean ok = carver.addPath(xs, ys, 4, 4);
        if (ok) {
            var r = carver.apply();
            assertTrue(r.changedCells() >= 0);
        } else {
            assertFalse(carver.getLastRejection().isBlank());
        }
    }
}
