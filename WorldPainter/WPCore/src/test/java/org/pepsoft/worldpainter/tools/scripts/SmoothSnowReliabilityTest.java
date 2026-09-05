package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.util.undo.UndoManager;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.*;
import org.pepsoft.worldpainter.layers.exporters.FrostExporter.FrostSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

public class SmoothSnowReliabilityTest {
    @Test public void invalidProfileValuesFailBeforeAnyMutation() {
        final Dimension d = world(210, 0, 0, 1, 1);
        final List<Cell> before = snapshot(d);
        for (float bad : new float[] { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> apply(d, bad, 190, 8, 25, 55, .15f, false, false, null));
            assertThrows(IllegalArgumentException.class, () -> apply(d, 160, bad, 8, 25, 55, .15f, false, false, null));
            assertThrows(IllegalArgumentException.class, () -> apply(d, 160, 190, 8, bad, 55, .15f, false, false, null));
            assertThrows(IllegalArgumentException.class, () -> apply(d, 160, 190, 8, 25, bad, .15f, false, false, null));
            assertThrows(IllegalArgumentException.class, () -> apply(d, 160, 190, 8, 25, 55, bad, false, false, null));
            assertThrows(IllegalArgumentException.class, () -> SmoothSnow.applySummitBlueprintMask(d, 733, null, bad, 190, 30));
        }
        assertThrows(IllegalArgumentException.class, () -> apply(d, 190, 160, 8, 25, 55, .15f, false, false, null));
        assertThrows(IllegalArgumentException.class, () -> apply(d, 160, 190, 9, 25, 55, .15f, false, false, null));
        assertThrows(IllegalArgumentException.class, () -> apply(d, 160, 190, 8, 25, 91, .15f, false, false, null));
        assertEquals(before, snapshot(d));
        assertNull(d.getLayerSettings(Frost.INSTANCE));
    }

    @Test public void allSnowEntrypointsPreserveProtectedWaterLavaAndCeilingCells() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            final Dimension d = world(210, 0, 0, 1, 1);
            for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) {
                d.setBitLayerValueAt(Frost.INSTANCE, x, y, true);
                d.setLayerValueAt(SnowDepth.INSTANCE, x, y, 7);
            }
            d.setBitLayerValueAt(org.pepsoft.worldpainter.layers.Void.INSTANCE, 8, 8, true);
            d.setBitLayerValueAt(NotPresentBlock.INSTANCE, 24, 8, true);
            d.setBitLayerValueAt(ReadOnly.INSTANCE, 40, 8, true);
            d.setBitLayerValueAt(NotPresent.INSTANCE, 64, 8, true);
            d.setBitLayerValueAt(FloodWithLava.INSTANCE, 88, 8, true);
            d.setWaterLevelAt(104, 8, 211);
            d.setWaterLevelAt(105, 8, 209);
            d.setHeightAt(112, 8, d.getMaxHeight() - 1);
            final int[] protectedX = { 8, 24, 40, 64, 88, 104, 105, 112 };
            final List<Cell> protectedBefore = new ArrayList<>();
            for (int x : protectedX) protectedBefore.add(cell(d, x, 8));
            if (mode == 0) apply(d, 500, 550, 8, 25, 55, .15f, false, false, null);
            else if (mode == 1) SmoothSnow.applySummitBlueprintMask(d, 733, null, 500, 550, 30);
            else SmoothSnow.applyRealistic(d, 500, 550, 4, 2.5f, true, 12, true, false, null);
            for (int i = 0; i < protectedX.length; i++) {
                assertEquals("Mode " + mode + " changed protected column " + protectedX[i],
                        protectedBefore.get(i), cell(d, protectedX[i], 8));
            }
            assertFalse(d.getBitLayerValueAt(Frost.INSTANCE, 8, 24));
            assertEquals(0, d.getLayerValueAt(SnowDepth.INSTANCE, 8, 24));
        }
    }

    @Test public void clearingLowSnowAlsoClearsOrphanedExplicitDepth() {
        final Dimension d = world(100, 0, 0, 1, 1);
        d.setLayerValueAt(SnowDepth.INSTANCE, 8, 8, 6);
        assertFalse(d.getBitLayerValueAt(Frost.INSTANCE, 8, 8));
        assertEquals(1, apply(d, 160, 190, 8, 25, 55, .15f, false, false, null).cleared());
        assertEquals(0, d.getLayerValueAt(SnowDepth.INSTANCE, 8, 8));
    }

    @Test public void dryRunHasTheSameDecisionCountButNoWorldOrSettingsWrites() {
        final Dimension dry = world(210, 0, 0, 1, 1), actual = world(210, 0, 0, 1, 1);
        final FrostSettings settings = new FrostSettings();
        settings.setMode(FrostSettings.MODE_RANDOM);
        dry.setLayerSettings(Frost.INSTANCE, settings);
        final List<Cell> before = snapshot(dry);
        final SmoothSnow.Result dryResult = apply(dry, 160, 190, 8, 25, 55, .15f, true, true, null);
        final SmoothSnow.Result actualResult = apply(actual, 160, 190, 8, 25, 55, .15f, true, false, null);
        assertEquals(actualResult, dryResult);
        assertEquals(before, snapshot(dry));
        assertSame(settings, dry.getLayerSettings(Frost.INSTANCE));
        assertEquals(FrostSettings.MODE_RANDOM, settings.getMode());
    }

    @Test public void normalPassIsRepeatableAndIdempotentWithoutOptionalHeightEdits() {
        final Dimension first = world(175, -1, -1, 2, 2), second = world(175, -1, -1, 2, 2);
        final SmoothSnow.Result result = apply(first, 160, 190, 8, 25, 55, .15f, false, false, null);
        assertEquals(result, apply(second, 160, 190, 8, 25, 55, .15f, false, false, null));
        assertEquals(snapshot(first), snapshot(second));
        final List<Cell> afterFirst = snapshot(first);
        apply(first, 160, 190, 8, 25, 55, .15f, false, false, null);
        assertEquals(afterFirst, snapshot(first));
    }

    @Test public void repeatingBlueprintSnowDoesNotErodeTransitionDepthAgain() throws Exception {
        final Dimension d = world(180, -1, -1, 2, 2);
        for (Tile tile : d.getTiles()) for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) {
            tile.setBitLayerValue(Frost.INSTANCE, x, y, true);
        }
        final SmoothSnow.Result first = SmoothSnow.applyBlueprintMask(d, 733, null);
        final List<Cell> beforeSecond = snapshot(d);
        final SmoothSnow.Result second = SmoothSnow.applyBlueprintMask(d, 733, null);
        assertEquals(first.snowCovered(), second.snowCovered());
        assertEquals("Neighbour depth must use the final mask on the first pass", beforeSecond, snapshot(d));
    }

    @Test public void optionalHeightDoesNotFeedBackIntoCoverageAcrossTileEdges() {
        final Dimension plain = world(175, -1, -1, 2, 2), raised = world(175, -1, -1, 2, 2);
        for (Tile tile : plain.getTiles()) for (int tx = 0; tx < 128; tx++) for (int ty = 0; ty < 128; ty++) {
            final int x = tile.getX() * 128 + tx, y = tile.getY() * 128 + ty;
            final float height = (float) (175 + Math.sin(x * .3) * .7 + Math.cos(y * .4) * .4);
            plain.setHeightAt(x, y, height);
            raised.setHeightAt(x, y, height);
        }
        final SmoothSnow.Result expected = apply(plain, 160, 190, 8, 3, 20, .8f, false, false, null);
        final SmoothSnow.Result actual = apply(raised, 160, 190, 8, 3, 20, .8f, true, false, null);
        assertEquals(expected, actual);
        int changedHeights = 0;
        for (Tile tile : plain.getTiles()) for (int tx = 0; tx < 128; tx++) for (int ty = 0; ty < 128; ty++) {
            final int x = tile.getX() * 128 + tx, y = tile.getY() * 128 + ty;
            assertEquals("Coverage used already-raised neighbours at " + x + "," + y,
                    plain.getBitLayerValueAt(Frost.INSTANCE, x, y), raised.getBitLayerValueAt(Frost.INSTANCE, x, y));
            if (raised.getHeightAt(x, y) > plain.getHeightAt(x, y)) changedHeights++;
        }
        assertTrue(changedHeights > 0);
    }

    @Test public void cancellationBeforeTheFirstWriteLeavesEverythingUntouched() {
        final Dimension d = world(210, 0, 0, 1, 1);
        final List<Cell> before = snapshot(d);
        final ScriptProgress progress = new ScriptProgress(new ScriptingContext(false), null) {
            @Override public void checkForCancel() { throw new ScriptingContext.InterruptedException(); }
        };
        assertThrows(ScriptingContext.InterruptedException.class,
                () -> apply(d, 160, 190, 8, 25, 55, .15f, true, false, progress));
        assertEquals(before, snapshot(d));
    }

    @Test public void optionalHeightLeavesRoomForSnowAtTheWorldCeilingWithoutLoweringTerrain() {
        final int maxHeight = TestData.MAX_HEIGHT;
        final Dimension d = world(maxHeight - 2, 0, 0, 1, 1);
        d.setHeightAt(8, 8, maxHeight - 2 + .375f);
        d.setHeightAt(12, 8, maxHeight - 2 + .5f);
        d.setHeightAt(16, 8, maxHeight - 1);
        final Cell roundedCeiling = cell(d, 12, 8), ceiling = cell(d, 16, 8);
        apply(d, 160, 190, 8, 25, 55, .15f, true, false, null);
        assertTrue("The optional height operation should still add the available fraction",
                d.getHeightAt(8, 8) > maxHeight - 2 + .375f);
        assertEquals(maxHeight - 2, d.getIntHeightAt(8, 8));
        assertTrue(d.getHeightAt(8, 8) <= maxHeight - 1.5f - 1f / 256f);
        assertTrue(d.getBitLayerValueAt(Frost.INSTANCE, 8, 8));
        assertEquals("Already-rounded ceiling terrain must not be lowered", roundedCeiling, cell(d, 12, 8));
        assertEquals(ceiling, cell(d, 16, 8));
    }

    @Test public void cancellationPropagatesToTheOwnersSingleUndoBoundary() {
        final Dimension d = world(210, 0, 0, 1, 1);
        final List<Cell> before = snapshot(d);
        d.registerUndoManager(new UndoManager(10));
        d.rememberChanges();
        final ScriptProgress progress = new ScriptProgress(new ScriptingContext(false), null) {
            @Override public void checkForCancel() {
                if (d.getBitLayerValueAt(Frost.INSTANCE, 0, 0)) throw new ScriptingContext.InterruptedException();
            }
        };
        try {
            assertThrows(ScriptingContext.InterruptedException.class,
                    () -> apply(d, 160, 190, 8, 25, 55, .15f, true, false, progress));
            assertTrue(d.undoChanges());
            d.clearRedo();
            assertEquals(before, snapshot(d));
        } finally { d.unregisterUndoManager(); }
    }

    @Test(timeout = 5000) public void sparseBlueprintWorldDoesNotTraverseAbsentTileRows() throws Exception {
        final Dimension d = world(210, 0, 0, 1, 1);
        final Tile other = d.getTileFactory().createTile(0, 10000);
        for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) {
            other.setBitLayerValue(Frost.INSTANCE, x, y, true);
            d.setBitLayerValueAt(Frost.INSTANCE, x, y, true);
        }
        d.addTile(other);
        assertEquals(32768, SmoothSnow.applyBlueprintMask(d, 733, null).checked());
        assertEquals(2, d.getTiles().size());
    }

    @Test public void legacyFormulaAndBiomeOptionArePreserved() {
        final Dimension d = world(120, 0, 0, 1, 1);
        for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) {
            d.setHeightAt(x, y, new int[] { 90, 105, 115, 120 }[x / 32]);
        }
        SmoothSnow.applyRealistic(d, 90, 120, 4, 2.5f, false, 12, true, false, null);
        assertFalse(d.getBitLayerValueAt(Frost.INSTANCE, 16, 16));
        assertFalse(d.getBitLayerValueAt(Frost.INSTANCE, 48, 16));
        assertTrue(d.getBitLayerValueAt(Frost.INSTANCE, 80, 16));
        assertTrue(d.getBitLayerValueAt(Frost.INSTANCE, 112, 16));
        assertEquals(4, d.getLayerValueAt(Biome.INSTANCE, 112, 16));
        SmoothSnow.applyRealistic(d, 90, 120, 4, 2.5f, true, 12, true, false, null);
        assertEquals(12, d.getLayerValueAt(Biome.INSTANCE, 112, 16));
        assertEquals(Terrain.STONE, d.getTerrainAt(112, 16));
        assertEquals(120, d.getHeightAt(112, 16), 0);
    }

    @Test public void legacySettingsAreValidatedBeforeClearingExistingSnow() {
        final Dimension d = world(100, 0, 0, 1, 1);
        d.setBitLayerValueAt(Frost.INSTANCE, 8, 8, true);
        final List<Cell> before = snapshot(d);
        assertThrows(IllegalArgumentException.class, () -> SmoothSnow.applyRealistic(d, 90, 120, Float.NaN, 2.5f, true, 12, true, false, null));
        assertThrows(IllegalArgumentException.class, () -> SmoothSnow.applyRealistic(d, 90, 120, 4, Float.POSITIVE_INFINITY, true, 12, true, false, null));
        assertThrows(IllegalArgumentException.class, () -> SmoothSnow.applyRealistic(d, 120, 90, 4, 2.5f, true, 12, true, false, null));
        assertThrows(IllegalArgumentException.class, () -> SmoothSnow.applyRealistic(d, 90, 120, 4, 2.5f, true, 256, true, false, null));
        assertEquals(before, snapshot(d));
    }

    private static SmoothSnow.Result apply(Dimension d, float line, float full, int layers, float start, float reject,
                                           float boost, boolean height, boolean dry, ScriptProgress progress) {
        return SmoothSnow.applyScript(d, false, 4, line, full, layers, start, reject, boost,
                733, true, height, dry, Terrain.DEEP_SNOW, progress);
    }

    private static Dimension world(int height, int firstX, int firstY, int width, int length) {
        final TileFactory factory = TestData.createTileFactory(height);
        final Dimension d = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "Snow reliability", 733, factory, Dimension.Anchor.NORMAL_DETAIL);
        for (int tx = firstX; tx < firstX + width; tx++) for (int ty = firstY; ty < firstY + length; ty++) {
            final Tile tile = factory.createTile(tx, ty);
            for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) {
                tile.setTerrain(x, y, Terrain.STONE);
                tile.setWaterLevel(x, y, 0);
                tile.setLayerValue(Biome.INSTANCE, x, y, 4);
                tile.setLayerValue(Annotations.INSTANCE, x, y, 4);
            }
            d.addTile(tile);
        }
        return d;
    }

    private static List<Cell> snapshot(Dimension d) {
        final List<Cell> cells = new ArrayList<>();
        d.getTiles().stream().sorted(Comparator.comparingInt(Tile::getX).thenComparingInt(Tile::getY)).forEach(tile -> {
            for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) {
                cells.add(cell(d, tile.getX() * 128 + x, tile.getY() * 128 + y));
            }
        });
        return cells;
    }

    private static Cell cell(Dimension d, int x, int y) {
        return new Cell(x, y, d.getHeightAt(x, y), d.getWaterLevelAt(x, y), d.getTerrainAt(x, y),
                d.getBitLayerValueAt(Frost.INSTANCE, x, y), d.getLayerValueAt(SnowDepth.INSTANCE, x, y),
                d.getLayerValueAt(Biome.INSTANCE, x, y), d.getLayerValueAt(Annotations.INSTANCE, x, y));
    }
    private record Cell(int x, int y, float height, int water, Terrain terrain, boolean frost, int snow, int biome, int annotation) {}
}
