package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.util.undo.UndoManager;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.HeightMapTileFactory;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.SnowDepth;
import org.pepsoft.worldpainter.layers.exporters.FrostExporter.FrostSettings;
import org.pepsoft.worldpainter.themes.SimpleTheme;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_19;

public class ScriptEditTransactionTest {
    @Test public void nashornFailureRollsBackOnlyItsOwnEditsAndPreservesPriorDirtyUserEdit() throws Exception {
        try (Fixture f = fixture()) {
            f.dimension.setHeightAt(2, 2, 104);
            assertTrue(f.undo.isDirty());
            final ScriptEngine engine = ScriptExecution.createEngine("js");
            assertNotNull(engine);
            engine.put("dimension", f.dimension);
            assertThrows(ScriptException.class, () -> {
                try (ScriptEditTransaction transaction = new ScriptEditTransaction(f.dimension, true)) {
                    engine.eval("dimension.setHeightAt(9,9,91); dimension.setWaterLevelAt(9,9,97); throw new Error('failure');");
                    transaction.commit();
                }
            });
            assertEquals(104, f.dimension.getHeightAt(2, 2), 0);
            assertEquals(100, f.dimension.getHeightAt(9, 9), 0);
            assertEquals(0, f.dimension.getWaterLevelAt(9, 9));
            assertFalse(f.dimension.isEventsInhibited());
            assertFalse("Failed edits must not be recoverable through Redo", f.undo.redo());
            assertTrue("Earlier user edit is still in Undo history", f.undo.undo());
            assertEquals(100, f.dimension.getHeightAt(2, 2), 0);
        }
    }

    @Test public void failureBeforeFirstWriteDoesNotUndoPriorUserEdit() {
        try (Fixture f = fixture()) {
            f.dimension.setHeightAt(2, 2, 104);
            try (ScriptEditTransaction ignored = new ScriptEditTransaction(f.dimension, true)) {
                assertFalse(f.undo.isDirty());
            }
            assertEquals(104, f.dimension.getHeightAt(2, 2), 0);
            assertEquals(100, f.dimension.getHeightAt(9, 9), 0);
            assertFalse(f.dimension.isEventsInhibited());
        }
    }

    @Test public void cancellationAfterTheLastScriptWriteRollsBackBeforeHostCommit() throws Exception {
        try (Fixture f = fixture()) {
            f.dimension.setHeightAt(2, 2, 104);
            final ScriptingContext context = new ScriptingContext(false);
            final ScriptEngine engine = ScriptExecution.createEngine("js");
            assertNotNull(engine);
            engine.put("dimension", f.dimension);
            engine.put("afterLastWrite", (Runnable) context::interrupt);
            assertThrows(ScriptingContext.InterruptedException.class, () -> {
                try (ScriptEditTransaction transaction = new ScriptEditTransaction(f.dimension, true)) {
                    context.checkForInterrupt();
                    // The final write has no subsequent progress callback. Eval
                    // returns normally even though Abort arrived before commit.
                    engine.eval("dimension.setHeightAt(9,9,91); dimension.setWaterLevelAt(9,9,97); afterLastWrite.run();");
                    context.checkGoCalled(null);
                    context.checkForInterrupt();
                    transaction.commit();
                }
            });
            assertEquals(104, f.dimension.getHeightAt(2, 2), 0);
            assertEquals(100, f.dimension.getHeightAt(9, 9), 0);
            assertEquals(0, f.dimension.getWaterLevelAt(9, 9));
            assertFalse(f.dimension.isEventsInhibited());
            assertFalse("Cancelled work cannot reappear through Redo", f.undo.redo());
            assertTrue("The older user edit remains independently undoable", f.undo.undo());
            assertEquals(100, f.dimension.getHeightAt(2, 2), 0);
        }
    }

    @Test public void lateCancellationMustNotRevertAnAlreadyCommittedNativeTransaction() throws Exception {
        try (Fixture f = fixture()) {
            f.dimension.setHeightAt(2, 2, 104);
            final ScriptingContext context = new ScriptingContext(false);
            final ScriptEngine engine = ScriptExecution.createEngine("js");
            assertNotNull(engine);
            engine.put("dimension", f.dimension);
            engine.put("afterNativeCommit", (Runnable) context::interrupt);
            try (ScriptEditTransaction transaction = new ScriptEditTransaction(f.dimension, false)) {
                context.checkForInterrupt();
                engine.eval("dimension.rememberChanges(); dimension.setHeightAt(9,9,91);"
                        + "dimension.armSavePoint(); afterNativeCommit.run();");
                context.checkGoCalled(null);
                // Native operations own their final cancellation/commit check.
                // Do not treat a later Abort as a failed host-owned transaction.
                transaction.commit();
            }
            assertTrue(context.isInterrupted());
            assertEquals(104, f.dimension.getHeightAt(2, 2), 0);
            assertEquals(91, f.dimension.getHeightAt(9, 9), 0);
            assertFalse(f.dimension.isEventsInhibited());
            assertTrue(f.undo.undo());
            assertEquals(100, f.dimension.getHeightAt(9, 9), 0);
            assertEquals(104, f.dimension.getHeightAt(2, 2), 0);
        }
    }

    @Test public void nestedNativeRollbackCannotUndoAnEarlierUserFrameAgain() {
        try (Fixture f = fixture()) {
            f.dimension.setHeightAt(2, 2, 104);
            try (ScriptEditTransaction ignored = new ScriptEditTransaction(f.dimension, true)) {
                f.dimension.rememberChanges();
                f.dimension.setHeightAt(9, 9, 91);
                assertTrue(f.dimension.undoChanges());
                f.dimension.clearRedo();
                assertFalse(f.undo.isDirty());
            }
            assertEquals(104, f.dimension.getHeightAt(2, 2), 0);
            assertEquals(100, f.dimension.getHeightAt(9, 9), 0);
            assertFalse(f.undo.redo());
        }
    }

    @Test public void manualSetterRollbackLeavesTheSameFrameSafeForHostRollback() {
        try (Fixture f = fixture()) {
            f.dimension.setHeightAt(2, 2, 104);
            try (ScriptEditTransaction ignored = new ScriptEditTransaction(f.dimension, true)) {
                f.dimension.setHeightAt(9, 9, 91);
                f.dimension.setHeightAt(9, 9, 100);
                assertTrue("Setter restoration is still an edited undo buffer", f.undo.isDirty());
            }
            assertEquals(104, f.dimension.getHeightAt(2, 2), 0);
            assertEquals(100, f.dimension.getHeightAt(9, 9), 0);
        }
    }

    @Test public void failureRestoresOriginalFrostSettingsIncludingAbsentSettings() {
        for (boolean hadSettings : new boolean[] { false, true }) {
            try (Fixture f = fixture()) {
                final FrostSettings original = hadSettings ? new FrostSettings() : null;
                if (original != null) original.setMode(FrostSettings.MODE_RANDOM);
                f.dimension.setLayerSettings(Frost.INSTANCE, original);
                try (ScriptEditTransaction ignored = new ScriptEditTransaction(f.dimension, true)) {
                    final FrostSettings changed = new FrostSettings();
                    changed.setMode(FrostSettings.MODE_SMOOTH);
                    f.dimension.setLayerSettings(Frost.INSTANCE, changed);
                    f.dimension.setBitLayerValueAt(Frost.INSTANCE, 9, 9, true);
                }
                assertSame(original, f.dimension.getLayerSettings(Frost.INSTANCE));
                if (original != null) assertEquals(FrostSettings.MODE_RANDOM, original.getMode());
                assertFalse(f.dimension.getBitLayerValueAt(Frost.INSTANCE, 9, 9));
            }
        }
    }

    @Test public void successfulScriptIsOneUndoStepAndSubsequentUserEditIsSeparate() {
        try (Fixture f = fixture()) {
            final int originalBiome = f.dimension.getLayerValueAt(Biome.INSTANCE, 9, 9);
            try (ScriptEditTransaction transaction = new ScriptEditTransaction(f.dimension, true)) {
                f.dimension.setHeightAt(9, 9, 100.375f);
                f.dimension.setTerrainAt(9, 9, Terrain.GRASS);
                f.dimension.setWaterLevelAt(9, 9, 101);
                f.dimension.setBitLayerValueAt(Frost.INSTANCE, 9, 9, true);
                f.dimension.setLayerValueAt(SnowDepth.INSTANCE, 9, 9, 5);
                f.dimension.setLayerValueAt(Biome.INSTANCE, 9, 9, 12);
                transaction.commit();
            }
            assertTrue(f.dimension.getBitLayerValueAt(Frost.INSTANCE, 9, 9));
            f.dimension.setHeightAt(2, 2, 104);
            assertTrue(f.undo.undo());
            assertEquals(100, f.dimension.getHeightAt(2, 2), 0);
            assertTrue("First Undo must preserve the completed script", f.dimension.getBitLayerValueAt(Frost.INSTANCE, 9, 9));
            assertTrue(f.undo.undo());
            assertEquals(100, f.dimension.getHeightAt(9, 9), 0);
            assertEquals(Terrain.STONE, f.dimension.getTerrainAt(9, 9));
            assertEquals(0, f.dimension.getWaterLevelAt(9, 9));
            assertFalse(f.dimension.getBitLayerValueAt(Frost.INSTANCE, 9, 9));
            assertEquals(0, f.dimension.getLayerValueAt(SnowDepth.INSTANCE, 9, 9));
            assertEquals(originalBiome, f.dimension.getLayerValueAt(Biome.INSTANCE, 9, 9));
        }
    }

    @Test public void alreadyInhibitedEventsRemainOwnedByOuterCaller() {
        try (Fixture f = fixture()) {
            f.dimension.setEventsInhibited(true);
            try {
                try (ScriptEditTransaction ignored = new ScriptEditTransaction(f.dimension, true)) {
                    f.dimension.setHeightAt(9, 9, 91);
                }
                assertTrue(f.dimension.isEventsInhibited());
                assertEquals(100, f.dimension.getHeightAt(9, 9), 0);
            } finally { f.dimension.setEventsInhibited(false); }
        }
    }

    @Test public void repeatedCloseDoesNotUndoLaterUserChanges() {
        try (Fixture f = fixture()) {
            final ScriptEditTransaction transaction = new ScriptEditTransaction(f.dimension, true);
            f.dimension.setHeightAt(9, 9, 91);
            transaction.close();
            f.dimension.setHeightAt(2, 2, 104);
            transaction.close();
            assertEquals(104, f.dimension.getHeightAt(2, 2), 0);
        }
    }

    @Test public void untrustedScriptsKeepManualUndoSemanticsOnFailure() {
        try (Fixture f = fixture()) {
            try (ScriptEditTransaction ignored = new ScriptEditTransaction(f.dimension, false)) {
                f.dimension.setHeightAt(9, 9, 91);
            }
            assertEquals(91, f.dimension.getHeightAt(9, 9), 0);
            assertFalse(f.dimension.isEventsInhibited());
            assertTrue(f.undo.undo());
            assertEquals(100, f.dimension.getHeightAt(9, 9), 0);
        }
    }

    @Test public void automaticRollbackRequiresUndoBeforeStartingAnyEdit() {
        final Dimension dimension = dimension();
        assertThrows(IllegalStateException.class, () -> new ScriptEditTransaction(dimension, true));
        assertFalse(dimension.isEventsInhibited());
        assertEquals(100, dimension.getHeightAt(9, 9), 0);
        try (ScriptEditTransaction transaction = new ScriptEditTransaction(null, true)) {
            transaction.commit();
        }
    }

    private static Fixture fixture() {
        final Dimension dimension = dimension();
        final UndoManager undo = new UndoManager(10);
        dimension.registerUndoManager(undo);
        dimension.armSavePoint();
        return new Fixture(dimension, undo);
    }

    private static Dimension dimension() {
        final World2 world = new World2(JAVA_ANVIL_1_19, -64, 320);
        final HeightMapTileFactory factory = TileFactoryFactory.createFlatTileFactory(0, Terrain.STONE, -64, 320, 100, 0, false, false);
        // Default factory height/theme rules can silently replace Stone with
        // Coarse Dirt or pre-paint Frost. Pin the undo fixture to one terrain.
        factory.setTheme(SimpleTheme.createSingleTerrain(Terrain.STONE, -64, 320, 0));
        final Dimension dimension = new Dimension(world, "Host transaction", 0, factory, Dimension.Anchor.NORMAL_DETAIL);
        dimension.addTile(factory.createTile(0, 0));
        return dimension;
    }

    private record Fixture(Dimension dimension, UndoManager undo) implements AutoCloseable {
        @Override public void close() { dimension.unregisterUndoManager(); }
    }
}
