package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.CustomAnnotationLayer;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.util.undo.UndoManager;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class TerrainAdjustmentPlanTest {
    @Test public void lightLimitsClampCutAndLeaveOutsideCorridorUntouched() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        for(int y=0;y<128;y++)for(int x=35;x<=45;x++)d.setHeightAt(x,y,104);
        int[] xs = new int[40], ys = new int[40];
        for (int i = 0; i < 40; i++) { xs[i] = 20 + i; ys[i] = 64; }
        var plan = TerrainAdjustmentPlan.alongCentreline(d, TerrainAdjustmentPlan.Mode.LIGHT, xs, ys, 8, 3.5, () -> {});
        assertFalse(plan.isEmpty());
        assertTrue(plan.summary().maxCut() <= 2.01);
        // Far from centreline: no delta
        assertEquals(0f, plan.deltaAt(20, 100), 0);
    }

    @Test public void strongAllowsDeeperCutThanLight() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        for(int y=0;y<128;y++)for(int x=45;x<=55;x++)d.setHeightAt(x,y,105);
        int[] xs = {30, 50, 70}, ys = {64, 64, 64};
        var light = TerrainAdjustmentPlan.alongCentreline(d, TerrainAdjustmentPlan.Mode.LIGHT, xs, ys, 10, 5, () -> {});
        var strong = TerrainAdjustmentPlan.alongCentreline(d, TerrainAdjustmentPlan.Mode.STRONG, xs, ys, 10, 5, () -> {});
        assertTrue(strong.summary().maxCut() + 0.01 >= light.summary().maxCut());
        assertTrue(strong.summary().maxCut() <= 6.01);
    }

    @Test public void applyWritesOnlyPlanCellsAndIsUndoableViaCaller() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        d.registerUndoManager(new UndoManager(4));
        try {
            float before = d.getHeightAt(40, 64);
            var plan = new TerrainAdjustmentPlan(TerrainAdjustmentPlan.Mode.LIGHT);
            plan.put(40, 64, before, -1.5f);
            d.rememberChanges();
            plan.apply(d);
            assertEquals(before - 1.5f, d.getHeightAt(40, 64), 0.01f);
            assertTrue(d.undoChanges());
            assertEquals(before, d.getHeightAt(40, 64), 0.01f);
        } finally {
            d.unregisterUndoManager();
        }
    }

    @Test public void readonlyCellsAreNotEditable() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        d.setBitLayerValueAt(ReadOnly.INSTANCE, 10, 10, true);
        var plan = new TerrainAdjustmentPlan(TerrainAdjustmentPlan.Mode.LIGHT);
        assertFalse(plan.editable(d, 10, 10));
    }
}
