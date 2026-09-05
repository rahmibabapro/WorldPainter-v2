package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.Annotations;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.Void;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Real shipped resources, no App window or runner rollback assumptions. */
public class BundledGlobalAndRoadScriptsTest {
    private static final String REMOVE_ONE = "globals/global_remove_water_ge1.js";
    private static final String WATER_ZERO = "globals/global_set_water_level_0.js";
    private static final String REMOVE_STONE = "globals/global_remove_water_ge0_make_stone.js";
    private static final String SLOPE = "globals/global_ops_stone_grass_45deg.js";
    private static final String ROAD = "roads/road_flatten.js";
    private static final String[] GLOBALS = {REMOVE_ONE, WATER_ZERO, REMOVE_STONE, SLOPE};

    @Test public void removeOneKeepsZeroAndNegativeLevelsAndDoesNotTouchTerrain() throws Exception {
        Dimension dimension = dimension(-64, 320, 0);
        dimension.setWaterLevelAt(1, 1, -5);
        dimension.setWaterLevelAt(2, 2, 0);
        execute(REMOVE_ONE, dimension, Map.of(), new RecordingProgress());
        assertEquals(-5, dimension.getWaterLevelAt(1, 1));
        assertEquals(0, dimension.getWaterLevelAt(2, 2));
        assertEquals(-1, dimension.getWaterLevelAt(3, 3));
        assertSame(Terrain.GRASS, dimension.getTerrainAt(3, 3));
    }

    @Test public void removeStoneStillPaintsOriginallyDryTerrain() throws Exception {
        Dimension dimension = dimension(-64, 320, 0);
        dimension.setWaterLevelAt(1, 1, -5);
        dimension.setWaterLevelAt(2, 2, 0);
        execute(REMOVE_STONE, dimension, Map.of(), new RecordingProgress());
        assertEquals(-5, dimension.getWaterLevelAt(1, 1));
        assertEquals(-1, dimension.getWaterLevelAt(2, 2));
        assertSame(Terrain.STONE, dimension.getTerrainAt(1, 1));
        assertSame(Terrain.STONE, dimension.getTerrainAt(2, 2));
    }

    @Test public void waterZeroKeepsItsExactMeaningInTheInjectedDimension() throws Exception {
        Dimension dimension = dimension(-64, 320, 0);
        dimension.setWaterLevelAt(1, 1, -5);
        RecordingProgress progress = new RecordingProgress();
        execute(WATER_ZERO, dimension, Map.of(), progress);
        assertEquals(0, dimension.getWaterLevelAt(1, 1));
        assertEquals(0, dimension.getWaterLevelAt(100, 100));
        assertEquals(1, progress.lastProgress, 0);
        assertTrue(progress.checks > 10);
    }

    @Test public void removalNeverWrapsMinusOneIntoAnUnsignedWaterCurtain() throws Exception {
        for (String script : new String[] {REMOVE_ONE, REMOVE_STONE}) {
            for (int maxHeight : new int[] {256, 512}) {
                Dimension dimension = dimension(0, maxHeight, 0);
                execute(script, dimension, Map.of(), null);
                assertEquals(0, dimension.getWaterLevelAt(10, 10));
                assertTrue(dimension.getWaterLevelAt(10, 10) <= dimension.getHeightAt(10, 10));
            }
        }
    }

    @Test public void allGlobalsPreserveReadOnlyAbsentVoidAndLava() throws Exception {
        for (String script : GLOBALS) {
            Dimension dimension = dimension(-64, 320, 0);
            protect(dimension);
            for (int[] point : protectedPoints()) dimension.setTerrainAt(point[0], point[1], Terrain.DIRT);
            execute(script, dimension, Map.of(), null);
            for (int[] point : protectedPoints()) {
                assertEquals(script, 10, dimension.getWaterLevelAt(point[0], point[1]));
                assertSame(script, Terrain.DIRT, dimension.getTerrainAt(point[0], point[1]));
            }
        }
    }

    @Test(timeout = 30000) public void globalsWalkPresentTilesNotTheHugeSparseBoundingBox() throws Exception {
        for (String script : GLOBALS) {
            Dimension dimension = dimension(-64, 320, -1, 2000);
            RecordingProgress progress = new RecordingProgress();
            ScriptEngine engine = execute(script, dimension, Map.of(), progress);
            assertEquals(script, 32768, ((Number) engine.get("processed")).intValue());
            assertEquals(2, dimension.getTiles().size());
            assertFalse(dimension.isTilePresent(0, 0));
            assertEquals(1, progress.lastProgress, 0);
        }
    }

    @Test public void allGlobalsCheckCancellationBeforeTheFirstWrite() throws Exception {
        for (String script : GLOBALS) {
            Dimension dimension = dimension(-64, 320, 0);
            RecordingProgress progress = new RecordingProgress();
            progress.cancelAtCheck = 1;
            assertThrows(Exception.class, () -> execute(script, dimension, Map.of(), progress));
            assertOriginal(dimension);
        }
    }

    @Test public void cancellationIsPolledInsideATileNotOnlyAfterIt() throws Exception {
        Dimension dimension = dimension(-64, 320, 0);
        RecordingProgress progress = new RecordingProgress();
        progress.cancelAtCheck = 4;
        assertThrows(Exception.class, () -> execute(WATER_ZERO, dimension, Map.of(), progress));
        assertEquals(0, dimension.getWaterLevelAt(0, 0));
        assertEquals(10, dimension.getWaterLevelAt(127, 127));
        // Direct invocation streams edits. The host, not this script, owns rollback.
    }

    @Test public void slopeRejectsInvalidNumbersBeforeWritingAndDoesNotInventEdgeCliffs() throws Exception {
        for (Object invalid : new Object[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 91}) {
            Dimension dimension = dimension(-64, 320, 0);
            assertThrows(ScriptException.class, () -> execute(SLOPE, dimension, Map.of("slopeAngleDeg", invalid), null));
            assertOriginal(dimension);
        }
        Dimension dimension = dimension(-64, 320, -1);
        dimension.setHeightAt(-60, 60, 110);
        execute(SLOPE, dimension, Map.of("slopeAngleDeg", 45), null);
        assertSame(Terrain.GRASS, dimension.getTerrainAt(-128, 0));
        assertSame(Terrain.GRASS, dimension.getTerrainAt(-1, 127));
        assertSame(Terrain.STONE_MIX, dimension.getTerrainAt(-59, 60));
    }

    @Test public void roadUsesTheSuppliedDimensionAndPreservesTheOriginalMean() throws Exception {
        Dimension dimension = dimension(-64, 320, 0);
        roadSource(dimension, 60, 60, 110);
        roadSource(dimension, 62, 60, 90);
        RecordingProgress progress = new RecordingProgress();
        ScriptEngine engine = execute(ROAD, dimension, roadParams(2), progress);
        assertEquals(100, dimension.getHeightAt(61, 60), 0);
        assertEquals(110, dimension.getHeightAt(59, 60), 0);
        assertEquals(90, dimension.getHeightAt(63, 60), 0);
        assertEquals(100, dimension.getHeightAt(55, 55), 0);
        assertTrue(((Number) engine.get("pointsToBeFlattenedLength")).intValue() > 0);
        assertEquals(1, progress.lastProgress, 0);
    }

    @Test public void roadValidatesMaskAndDistanceBeforeWritingOrOpeningAnyApp() throws Exception {
        for (Map<String, Object> params : new Map[] {
                Map.of("distance", 2, "layerMask", "", "terrainMask", "", "fileMask", ""),
                Map.of("distance", Double.NaN, "terrainMask", "Grass"),
                Map.of("distance", -1, "terrainMask", "Grass"),
                Map.of("distance", 0.5, "terrainMask", "Grass"),
                Map.of("distance", 2, "layerMask", "Does not exist"),
                Map.of("distance", 2, "terrainMask", "Does not exist"),
                Map.of("distance", 2, "terrainMask", "Grass", "roadLayer", "Does not exist")}) {
            Dimension dimension = dimension(-64, 320, 0);
            assertThrows(ScriptException.class, () -> execute(ROAD, dimension, params, null));
            assertOriginal(dimension);
        }
    }

    @Test public void roadPreservesProtectedTargetsAndResolvesOptionalBitAndNumericLayers() throws Exception {
        Dimension dimension = dimension(-64, 320, 0);
        protect(dimension);
        roadSource(dimension, 30, 30, 110);
        roadSource(dimension, 62, 62, 110);
        roadSource(dimension, 89, 90, 110);
        roadSource(dimension, 99, 100, 110);
        roadSource(dimension, 109, 110, 110);
        dimension.setLayerValueAt(Annotations.INSTANCE, 1, 1, 1);
        dimension.setBitLayerValueAt(Frost.INSTANCE, 2, 2, true);
        Map<String, Object> params = roadParams(3);
        params.put("roadLayer", "Annotations");
        params.put("roadSlab", "Frost");
        execute(ROAD, dimension, params, null);
        for (int[] point : protectedPoints()) {
            assertEquals(100, dimension.getHeightAt(point[0], point[1]), 0);
            assertEquals(0, dimension.getLayerValueAt(Annotations.INSTANCE, point[0], point[1]));
        }
        assertEquals(110, dimension.getHeightAt(30, 29), 0);
        assertEquals(8, dimension.getLayerValueAt(Annotations.INSTANCE, 30, 29));
    }

    @Test public void roadCancellationDuringPlanningDoesNotApplyPartialHeights() throws Exception {
        Dimension dimension = dimension(-64, 320, 0);
        roadSource(dimension, 1, 1, 110);
        RecordingProgress progress = new RecordingProgress();
        progress.cancelAtCheck = 4;
        assertThrows(Exception.class, () -> execute(ROAD, dimension, roadParams(3), progress));
        assertEquals(100, dimension.getHeightAt(2, 1), 0);
        assertEquals(110, dimension.getHeightAt(1, 1), 0);
    }

    @Test(timeout = 30000) public void roadIncludesPresentNegativeEdgesWithoutScanningEmptyTiles() throws Exception {
        Dimension dimension = dimension(-64, 320, -1, 2000);
        roadSource(dimension, -128, 0, 110);
        ScriptEngine engine = execute(ROAD, dimension, roadParams(1), null);
        assertEquals(32768, ((Number) engine.get("scanned")).intValue());
        assertEquals(110, dimension.getHeightAt(-127, 0), 0);
        assertEquals(2, dimension.getTiles().size());
        assertFalse(dimension.isTilePresent(-2, 0));
    }

    private ScriptEngine execute(String script, Dimension dimension, Map<String, Object> params,
                                 RecordingProgress progress) throws Exception {
        ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        engine.put("world", dimension.getWorld());
        engine.put("dimension", dimension);
        engine.put("params", params);
        engine.put("progress", progress);
        engine.getContext().setWriter(new StringWriter());
        try (InputStream input = getClass().getResourceAsStream("/org/pepsoft/worldpainter/scripts/" + script)) {
            assertNotNull(input);
            engine.eval(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        return engine;
    }

    private Map<String, Object> roadParams(int distance) {
        return new HashMap<>(Map.of("distance", distance, "terrainMask", "Dirt", "layerMask", "",
                "fileMask", "", "roadLayer", "", "roadSlab", ""));
    }

    private void roadSource(Dimension dimension, int x, int y, float height) {
        dimension.setTerrainAt(x, y, Terrain.DIRT);
        dimension.setHeightAt(x, y, height);
    }

    private Dimension dimension(int minHeight, int maxHeight, int... tileXs) {
        World2 world = new World2(DefaultPlugin.JAVA_ANVIL_1_18, minHeight, maxHeight);
        TileFactory factory = TileFactoryFactory.createFlatTileFactory(0, Terrain.GRASS,
                minHeight, maxHeight, 100, 10, false, false);
        Dimension dimension = new Dimension(world, "Script test", 0, factory, Dimension.Anchor.NORMAL_DETAIL);
        for (int tileX : tileXs) {
            Tile tile = factory.createTile(tileX, 0);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) tile.setTerrain(x, y, Terrain.GRASS);
            dimension.addTile(tile);
        }
        return dimension;
    }

    private void protect(Dimension dimension) {
        dimension.setBitLayerValueAt(ReadOnly.INSTANCE, 32, 32, true);
        dimension.setBitLayerValueAt(NotPresent.INSTANCE, 64, 64, true);
        dimension.setBitLayerValueAt(NotPresentBlock.INSTANCE, 90, 90, true);
        dimension.setBitLayerValueAt(Void.INSTANCE, 100, 100, true);
        dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, 110, 110, true);
    }

    private int[][] protectedPoints() {
        return new int[][] {{32, 32}, {47, 47}, {64, 64}, {79, 79}, {90, 90}, {100, 100}, {110, 110}};
    }

    private void assertOriginal(Dimension dimension) {
        for (Tile tile : dimension.getTiles()) for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            assertEquals(100, tile.getHeight(x, y), 0);
            assertEquals(10, tile.getWaterLevel(x, y));
            assertSame(Terrain.GRASS, tile.getTerrain(x, y));
        }
    }

    /** Public so Nashorn can invoke its methods exactly like ScriptProgress. */
    public static class RecordingProgress {
        public int checks;
        public int cancelAtCheck = Integer.MAX_VALUE;
        public double lastProgress;
        public void checkForCancel() {
            if (++checks >= cancelAtCheck) throw new IllegalStateException("test cancellation");
        }
        public void setProgress(double value) {
            assertTrue(Double.isFinite(value));
            assertTrue(value >= lastProgress && value >= 0 && value <= 1);
            lastProgress = value;
        }
    }
}
