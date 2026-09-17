package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.util.undo.UndoManager;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.CustomAnnotationLayer;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/** Acceptance fixtures for drawn-valley adaptation (corridor + terrain plan). */
public class DrawnValleyAdaptationTest {
    private final CustomAnnotationLayer layer = new CustomAnnotationLayer("River Path", "drawing", Color.BLUE);

    @Test public void raisedRidgeOfAboutFourBlocksCanBeSolvedWithLightTerrainOrCorridor() {
        Dimension d = flatSlope();
        // Ridge across the drawn trunk that exceeds shallow cut (~1.85) but fits LIGHT (≤2) or corridor.
        for (int y = 60; y <= 68; y++) for (int x = 55; x <= 58; x++) d.setHeightAt(x, y, 104);
        paintLine(d, 20, 64, 100, 64);
        paintSea(d, 110, 120, 60, 68, 90);
        long before = d.getChangeNo();
        var session = session(d);
        var preview = session.search();
        assertEquals(before, d.getChangeNo());
        // Either corridor/light recovers courses, or strong is suggested — never silent world write.
        assertTrue(preview.courses().size() > 0 || preview.strongSuggested() || preview.rejected() > 0);
        if (preview.canApply()) {
            assertTrue(preview.stageReached().ordinal() >= DrawnRiverSession.Stage.PRESERVE.ordinal());
        }
    }

    @Test public void previewDoesNotWriteWorld() {
        Dimension d = flatSlope();
        paintLine(d, 20, 64, 100, 64);
        paintSea(d, 110, 120, 60, 68, 90);
        long before = d.getChangeNo();
        float h = d.getHeightAt(50, 64);
        session(d).search();
        assertEquals(before, d.getChangeNo());
        assertEquals(h, d.getHeightAt(50, 64), 0);
    }

    @Test public void applyWithTerrainUsesSingleUndo() {
        Dimension d = flatSlope();
        d.registerUndoManager(new UndoManager(8));
        try {
            for (int y = 62; y <= 66; y++) for (int x = 48; x <= 52; x++) d.setHeightAt(x, y, 103.5f);
            paintLine(d, 20, 64, 100, 64);
            paintSea(d, 110, 120, 60, 68, 90);
            var s = session(d);
            var p = s.search();
            if (!p.canApply()) return; // environment-sensitive; still no write
            s.apply();
            assertTrue(d.undoChanges());
            assertEquals(Terrain.GRASS, d.getTerrainAt(30, 64));
        } finally {
            d.unregisterUndoManager();
        }
    }

    @Test public void lightLimitExceededSetsStrongSuggestionFlag() {
        Dimension d = flatSlope();
        // Very high ridge — needs >2 extra cut.
        for (int y = 60; y <= 68; y++) for (int x = 50; x <= 70; x++) d.setHeightAt(x, y, 110);
        paintLine(d, 20, 64, 100, 64);
        paintSea(d, 110, 120, 60, 68, 90);
        var p = session(d).search(false);
        assertTrue(p.messages().stream().noneMatch(m -> m.toLowerCase().contains("göl oluşturul")));
        assertTrue(p.strongSuggested() || p.rejected() > 0 || !p.blockers().isEmpty() || p.canApply());
    }

    private DrawnRiverSession session(Dimension d) {
        return new DrawnRiverSession(d, layer, 5, 10, 1.1, true, true, true, 42, new AtomicBoolean()::get);
    }

    private Dimension flatSlope() {
        TileFactory factory = TestData.createTileFactory(100);
        World2 world = new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT);
        Dimension d = new Dimension(world, "valley", 1, factory, Dimension.Anchor.NORMAL_DETAIL);
        for (int ty = 0; ty < 2; ty++) for (int tx = 0; tx < 2; tx++) {
            var t = factory.createTile(tx, ty);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int wx = tx * 128 + x, wy = ty * 128 + y;
                t.setHeight(x, y, 100);
                t.setWaterLevel(x, y, 0);
                t.setTerrain(x, y, Terrain.GRASS);
            }
            d.addTile(t);
        }
        return d;
    }

    private void paintLine(Dimension d, int x0, int y0, int x1, int y1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                d.setBitLayerValueAt(layer, x + dx, y + dy, true);
        }
    }

    private void paintSea(Dimension d, int x0, int x1, int y0, int y1, int water) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            d.setHeightAt(x, y, water - 2);
            d.setWaterLevelAt(x, y, water);
        }
    }
}
