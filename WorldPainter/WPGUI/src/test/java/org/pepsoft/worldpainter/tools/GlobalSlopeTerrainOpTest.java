package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.util.undo.UndoManager;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.Void;
import org.pepsoft.worldpainter.presets.MapQuickPreset;
import org.pepsoft.worldpainter.presets.MapQuickPresetExecutor;

import static org.junit.Assert.*;

public class GlobalSlopeTerrainOpTest {
    @Test public void disabledUndoAndInvalidThresholdFailBeforePainting() {
        Fixture fixture = fixture(-64, 320, false);
        assertThrows(IllegalStateException.class, () -> GlobalSlopeTerrainOp.apply(fixture.dimension, 45, null));
        assertThrows(IllegalArgumentException.class, () -> GlobalSlopeTerrainOp.apply(fixture.dimension, -1, null));
        assertThrows(IllegalArgumentException.class, () -> GlobalSlopeTerrainOp.apply(fixture.dimension, 91, null));
        assertOriginal(fixture.dimension);
    }

    @Test public void successIsOneUndoAndPreservesOuterEventInhibition() throws Exception {
        Fixture fixture = fixture(-64, 320, true);
        fixture.dimension.setEventsInhibited(true);
        try {
            GlobalSlopeTerrainOp.apply(fixture.dimension, 45, null);
            assertTrue(fixture.dimension.isEventsInhibited());
            assertSame(Terrain.GRASS, fixture.dimension.getTerrainAt(64, 64));
            assertTrue(fixture.undo.undo());
            assertOriginal(fixture.dimension);
        } finally {
            fixture.dimension.setEventsInhibited(false);
        }
    }

    @Test public void runtimeFailureAfterPaintingRollsBackAndReleasesOwnedEvents() {
        Fixture fixture = fixture(-64, 320, true);
        assertThrows(IllegalStateException.class, () -> GlobalSlopeTerrainOp.apply(fixture.dimension, 45, new Receiver() {
            @Override public void setProgress(float progress) {
                assertSame(Terrain.GRASS, fixture.dimension.getTerrainAt(64, 64));
                throw new IllegalStateException("failure after painting");
            }
        }));
        assertOriginal(fixture.dimension);
        assertFalse(fixture.dimension.isEventsInhibited());
    }

    @Test public void midTileCancellationRollsBackTheAlreadyPaintedRows() {
        Fixture fixture = fixture(-64, 320, true);
        assertThrows(ProgressReceiver.OperationCancelled.class,
                () -> GlobalSlopeTerrainOp.apply(fixture.dimension, 45, new Receiver() {
                    private int checks;
                    @Override public void checkForCancellation() throws OperationCancelled {
                        if (++checks == 4) {
                            assertSame(Terrain.GRASS, fixture.dimension.getTerrainAt(0, 0));
                            assertSame(Terrain.MUD, fixture.dimension.getTerrainAt(127, 127));
                            throw new OperationCancelled("cancel during tile");
                        }
                    }
                }));
        assertOriginal(fixture.dimension);
        assertFalse(fixture.dimension.isEventsInhibited());
    }

    @Test public void nativeSlopePreservesProtectedCellsButStillPaintsOrdinaryUnderwaterTerrain() throws Exception {
        Fixture fixture = fixture(-64, 320, true);
        Dimension dimension = fixture.dimension;
        dimension.setBitLayerValueAt(ReadOnly.INSTANCE, 32, 32, true);
        dimension.setBitLayerValueAt(NotPresent.INSTANCE, 64, 64, true);
        dimension.setBitLayerValueAt(NotPresentBlock.INSTANCE, 90, 90, true);
        dimension.setBitLayerValueAt(Void.INSTANCE, 100, 100, true);
        dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, 110, 110, true);
        dimension.setWaterLevelAt(10, 10, 120);
        GlobalSlopeTerrainOp.apply(dimension, 45, null);
        for (int[] p : new int[][] {{32, 32}, {47, 47}, {64, 64}, {79, 79}, {90, 90}, {100, 100}, {110, 110}}) {
            assertSame(Terrain.MUD, dimension.getTerrainAt(p[0], p[1]));
        }
        assertSame(Terrain.GRASS, dimension.getTerrainAt(10, 10));
        assertEquals(120, dimension.getWaterLevelAt(10, 10));
        assertSame(Terrain.GRASS, dimension.getTerrainAt(0, 0));
    }

    @Test public void dryWorldNativePresetCannotWrapWaterInLegacyWorlds() throws Exception {
        for (int maxHeight : new int[] {256, 512}) {
            Fixture fixture = fixture(0, maxHeight, false);
            MapQuickPresetExecutor.apply(fixture.dimension, MapQuickPreset.DRY_WORLD, 1, null);
            assertEquals(0, fixture.dimension.getWaterLevelAt(64, 64));
            assertSame(Terrain.MUD, fixture.dimension.getTerrainAt(64, 64));
        }
    }

    private Fixture fixture(int minHeight, int maxHeight, boolean undoEnabled) {
        World2 world = new World2(DefaultPlugin.JAVA_ANVIL_1_18, minHeight, maxHeight);
        TileFactory factory = TileFactoryFactory.createFlatTileFactory(0, Terrain.MUD,
                minHeight, maxHeight, 100, 10, false, false);
        Dimension dimension = new Dimension(world, "Slope test", 0, factory, Dimension.Anchor.NORMAL_DETAIL);
        Tile tile = factory.createTile(0, 0);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) tile.setTerrain(x, y, Terrain.MUD);
        dimension.addTile(tile);
        UndoManager undo = new UndoManager(10);
        if (undoEnabled) dimension.registerUndoManager(undo);
        return new Fixture(dimension, undo);
    }

    private void assertOriginal(Dimension dimension) {
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            assertSame(Terrain.MUD, dimension.getTerrainAt(x, y));
            assertEquals(100, dimension.getHeightAt(x, y), 0);
            assertEquals(10, dimension.getWaterLevelAt(x, y));
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
