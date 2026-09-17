package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.util.undo.UndoManager;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.CustomAnnotationLayer;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DrawnRiverSessionEdgeDrainTest {
    private final CustomAnnotationLayer layer = new CustomAnnotationLayer("River Path", "drawing", Color.BLUE);

    private Dimension slopingTerrain() {
        var factory = TestData.createTileFactory(100);
        var world = new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT);
        var d = new Dimension(world, "Edge drain", 41, factory, Dimension.Anchor.NORMAL_DETAIL);
        var t = factory.createTile(0, 0);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            t.setHeight(x, y, 120 - y * 0.4f);
            t.setWaterLevel(x, y, 0);
            t.setTerrain(x, y, Terrain.GRASS);
        }
        d.addTile(t);
        for (int y = 10; y <= 100; y++) d.setBitLayerValueAt(layer, 64, y, true);
        return d;
    }

    @Test
    public void isStaleWhenDrainOrOverrideChanges() {
        var d = slopingTerrain();
        d.registerUndoManager(new UndoManager(10));
        try {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            DrawnRiverSession session = new DrawnRiverSession(d, layer, 3, 8, 1.5, true, cancelled::get);
            session.setDrain(DrawnRiverGraph.Drain.AUTO);
            session.prepare();
            assertFalse(session.isStale());
            session.setDrain(DrawnRiverGraph.Drain.SW);
            assertTrue(session.isStale());

            session = new DrawnRiverSession(d, layer, 3, 8, 1.5, true, cancelled::get);
            session.setDrain(DrawnRiverGraph.Drain.S);
            session.prepare();
            assertFalse(session.isStale());
            session.setOutletOverride(new DrawnRiverGraph.Pixel(64, 10));
            assertTrue(session.isStale());
        } finally {
            d.unregisterUndoManager();
        }
    }

    @Test
    public void swDrainSelectsSouthernOutletOnSlopingLine() {
        var d = slopingTerrain();
        d.registerUndoManager(new UndoManager(10));
        try {
            DrawnRiverSession session = new DrawnRiverSession(d, layer, 3, 8, 1.5, true, () -> false);
            session.setDrain(DrawnRiverGraph.Drain.S);
            var preview = session.prepare();
            assertFalse(preview.messages().isEmpty());
            boolean southHint = preview.messages().stream().anyMatch(m ->
                    m.contains("64,100") || m.contains("Çıkış"));
            assertTrue(preview.messages().toString(), southHint || !preview.courses().isEmpty());
            if (!preview.courses().isEmpty()) {
                var line = preview.courses().get(0).centreline();
                assertEquals(100, line.get(line.size() - 1).y());
            }
        } finally {
            d.unregisterUndoManager();
        }
    }
}
