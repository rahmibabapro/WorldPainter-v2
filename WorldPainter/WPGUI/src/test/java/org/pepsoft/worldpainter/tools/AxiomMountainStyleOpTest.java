package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.util.undo.UndoManager;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.SnowDepth;
import org.pepsoft.worldpainter.layers.Void;
import org.pepsoft.worldpainter.layers.exporters.FrostExporter.FrostSettings;

import static org.junit.Assert.*;

public class AxiomMountainStyleOpTest {
    @Test public void rejectsDisabledUndoBeforeChangingTheWorld() {
        Fixture fixture = fixture(false);
        assertThrows(IllegalStateException.class, () -> AxiomMountainStyleOp.apply(fixture.dimension, null));
        assertOriginal(fixture.dimension);
    }

    @Test public void successfulNativeOperationIsOneUndoAndPreservesOuterEventInhibition() throws Exception {
        Fixture fixture = fixture(true);
        fixture.dimension.setEventsInhibited(true);
        try {
            AxiomMountainStyleOp.apply(fixture.dimension, null);
            assertTrue(fixture.dimension.isEventsInhibited());
            assertNotSame(Terrain.MUD, fixture.dimension.getTerrainAt(64, 64));
            assertTrue(fixture.undo.undo());
            assertOriginal(fixture.dimension);
        } finally {
            fixture.dimension.setEventsInhibited(false);
        }
    }

    @Test public void runtimeFailureAfterSurfacePaintingRollsBackCellsAndSettings() {
        Fixture fixture = fixture(true);
        FrostSettings original = new FrostSettings();
        original.setMode(FrostSettings.MODE_FLAT);
        fixture.dimension.setLayerSettings(Frost.INSTANCE, original);
        assertThrows(IllegalStateException.class, () -> AxiomMountainStyleOp.apply(fixture.dimension, new Receiver() {
            @Override public void setProgress(float progress) {
                if (progress >= 0.5f) throw new IllegalStateException("simulated runtime failure");
            }
        }));
        assertOriginal(fixture.dimension);
        assertSame(original, fixture.dimension.getLayerSettings(Frost.INSTANCE));
        assertFalse(fixture.dimension.isEventsInhibited());
    }

    @Test public void cancellationDuringSnowRollsBackTheEarlierTerrainPass() {
        Fixture fixture = fixture(true);
        assertThrows(ProgressReceiver.OperationCancelled.class,
                () -> AxiomMountainStyleOp.apply(fixture.dimension, new Receiver() {
                    @Override public void setProgress(float progress) throws OperationCancelled {
                        if (progress > 0.6f) throw new OperationCancelled("cancel snow");
                    }
                }));
        assertOriginal(fixture.dimension);
        assertFalse(fixture.dimension.isEventsInhibited());
    }

    @Test public void nativeTextureAndSnowBothRespectProtectedAndNoDataCells() throws Exception {
        Fixture fixture = fixture(true);
        Dimension dimension = fixture.dimension;
        dimension.setBitLayerValueAt(ReadOnly.INSTANCE, 16, 16, true);
        dimension.setBitLayerValueAt(NotPresent.INSTANCE, 48, 48, true);
        dimension.setBitLayerValueAt(NotPresentBlock.INSTANCE, 80, 80, true);
        dimension.setBitLayerValueAt(Void.INSTANCE, 90, 90, true);
        dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, 100, 100, true);
        AxiomMountainStyleOp.apply(dimension, null);
        for (int[] p : new int[][] {{16, 16}, {31, 31}, {48, 48}, {63, 63}, {80, 80}, {90, 90}, {100, 100}}) {
            assertSame(Terrain.MUD, dimension.getTerrainAt(p[0], p[1]));
            assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, p[0], p[1]));
            assertEquals(0, dimension.getLayerValueAt(SnowDepth.INSTANCE, p[0], p[1]));
        }
        assertNotSame(Terrain.MUD, dimension.getTerrainAt(0, 0));
    }

    private Fixture fixture(boolean undoEnabled) {
        World2 world = new World2(DefaultPlugin.JAVA_ANVIL_1_18, -64, 320);
        TileFactory factory = TileFactoryFactory.createFlatTileFactory(0, Terrain.MUD, -64, 320, 210, 0, false, false);
        Dimension dimension = new Dimension(world, "Native texture", 0, factory, Dimension.Anchor.NORMAL_DETAIL);
        Tile tile = factory.createTile(0, 0);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            tile.setTerrain(x, y, Terrain.MUD);
            tile.setBitLayerValue(Frost.INSTANCE, x, y, false);
        }
        dimension.addTile(tile);
        UndoManager undo = new UndoManager(10);
        if (undoEnabled) dimension.registerUndoManager(undo);
        return new Fixture(dimension, undo);
    }

    private void assertOriginal(Dimension dimension) {
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            assertSame(Terrain.MUD, dimension.getTerrainAt(x, y));
            assertEquals(210, dimension.getHeightAt(x, y), 0);
            assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, x, y));
            assertEquals(0, dimension.getLayerValueAt(SnowDepth.INSTANCE, x, y));
        }
    }

    private record Fixture(Dimension dimension, UndoManager undo) { }

    private static class Receiver implements ProgressReceiver {
        @Override public void setProgress(float progress) throws OperationCancelled { }
        @Override public void checkForCancellation() throws OperationCancelled { }
        @Override public void setMessage(String message) { }
        @Override public void reset() { }
        @Override public void done() { }
        @Override public void exceptionThrown(Throwable failure) { }
        @Override public void subProgressStarted(SubProgressReceiver receiver) { }
    }
}
