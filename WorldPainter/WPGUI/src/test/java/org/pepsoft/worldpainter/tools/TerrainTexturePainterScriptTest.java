package org.pepsoft.worldpainter.tools;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.Void;

import javax.imageio.ImageIO;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Executes the shipped texture script, including parameter resolution and its real tile walk. */
public class TerrainTexturePainterScriptTest {
    @Rule public final TemporaryFolder files = new TemporaryFolder();

    @Test public void emptyOptionalFiltersWorkAndDryRunDoesNotChangeTerrain() throws Exception {
        Dimension dimension = dimension(0);
        Map<String, Object> params = parameters();
        params.put("dryRun", true);
        ScriptEngine engine = execute(dimension, params);
        assertEquals(16384, ((Number) engine.get("painted")).intValue());
        assertAllGrass(dimension);
    }

    @Test public void omittedDryRunKeepsTheSafeDeclaredDefault() throws Exception {
        Dimension dimension = dimension(0);
        Map<String, Object> params = parameters();
        params.remove("dryRun");
        execute(dimension, params);
        assertAllGrass(dimension);
    }

    @Test public void sourceTerrainFilterUsesItsValueInsteadOfTheParameterName() throws Exception {
        Dimension dimension = dimension(0);
        dimension.setTerrainAt(15, 25, Terrain.DIRT);
        Map<String, Object> params = parameters();
        params.put("terrainFilter", "Dirt");
        ScriptEngine engine = execute(dimension, params);
        assertEquals(1, ((Number) engine.get("painted")).intValue());
        assertSame(Terrain.STONE, dimension.getTerrainAt(15, 25));
        assertSame(Terrain.GRASS, dimension.getTerrainAt(16, 25));
    }

    @Test public void paintedLayerFilterResolvesWithoutOpeningAnAppWindow() throws Exception {
        Dimension dimension = dimension(0);
        dimension.setBitLayerValueAt(Frost.INSTANCE, 50, 50, true);
        Map<String, Object> params = parameters();
        params.put("layerFilter", "Frost");
        ScriptEngine engine = execute(dimension, params);
        assertEquals(1, ((Number) engine.get("painted")).intValue());
        assertSame(Terrain.STONE, dimension.getTerrainAt(50, 50));
        assertSame(Terrain.GRASS, dimension.getTerrainAt(51, 50));
    }

    @Test(timeout = 30000) public void visitsOnlyPresentTilesAndIncludesEdgesAndNegativeCoordinates() throws Exception {
        Dimension dimension = dimension(-1, 2000);
        ScriptEngine engine = execute(dimension, parameters());
        assertEquals(2 * 16384, ((Number) engine.get("checked")).intValue());
        assertEquals(2 * 16384, ((Number) engine.get("painted")).intValue());
        assertSame(Terrain.STONE, dimension.getTerrainAt(-128, 0));
        assertSame(Terrain.STONE, dimension.getTerrainAt(-1, 127));
        assertSame(Terrain.STONE, dimension.getTerrainAt(2000 * 128 + 127, 127));
        assertFalse(dimension.isTilePresent(0, 0));
        assertEquals(2, dimension.getTiles().size());
    }

    @Test public void protectsReadOnlyNoDataAndLavaInsteadOfPaintingTheirStoredHeights() throws Exception {
        Dimension dimension = dimension(0);
        dimension.setBitLayerValueAt(ReadOnly.INSTANCE, 32, 32, true);
        dimension.setBitLayerValueAt(NotPresent.INSTANCE, 64, 64, true);
        dimension.setBitLayerValueAt(NotPresentBlock.INSTANCE, 90, 90, true);
        dimension.setBitLayerValueAt(Void.INSTANCE, 100, 100, true);
        dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, 110, 110, true);
        execute(dimension, parameters());
        for (int[] coordinate : new int[][] {{32, 32}, {47, 47}, {64, 64}, {79, 79}, {90, 90}, {100, 100}, {110, 110}}) {
            assertSame(Terrain.GRASS, dimension.getTerrainAt(coordinate[0], coordinate[1]));
        }
        assertSame(Terrain.STONE, dimension.getTerrainAt(0, 0));
    }

    @Test public void rejectsNonFiniteOrInvalidNumbersBeforePainting() throws Exception {
        for (Map.Entry<String, Object> invalid : Map.<String, Object>of("textureScale", Double.POSITIVE_INFINITY,
                "minHeight", Double.NaN, "sideSlope", 91, "seed", Double.NaN).entrySet()) {
            Dimension dimension = dimension(0);
            Map<String, Object> params = parameters();
            params.put(invalid.getKey(), invalid.getValue());
            assertThrows(ScriptException.class, () -> execute(dimension, params));
            assertAllGrass(dimension);
        }
    }

    @Test public void sameSeedDitheringRepeatsWithoutFurtherChanges() throws Exception {
        Dimension dimension = dimension(-1);
        Map<String, Object> params = parameters();
        params.put("palette", "#777777=Stone;#999999=Dirt");
        params.put("dither", true);
        params.put("texture", image(0xff808080));
        execute(dimension, params);
        ScriptEngine repeated = execute(dimension, params);
        assertEquals(0, ((Number) repeated.get("painted")).intValue());
    }

    @Test public void refusesUnconfiguredCustomTerrainBeforePainting() throws Exception {
        MixedMaterial saved = Terrain.getCustomMaterial(0);
        try {
            Terrain.setCustomMaterial(0, null);
            Dimension dimension = dimension(0);
            Map<String, Object> params = parameters();
            params.put("palette", "#777777=" + Terrain.CUSTOM_1);
            assertThrows(ScriptException.class, () -> execute(dimension, params));
            assertAllGrass(dimension);
        } finally {
            Terrain.setCustomMaterial(0, saved);
        }
    }

    private ScriptEngine execute(Dimension dimension, Map<String, Object> params) throws Exception {
        ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        engine.put("world", dimension.getWorld());
        engine.put("dimension", dimension);
        engine.put("params", params);
        engine.put("progress", null);
        engine.getContext().setWriter(new StringWriter());
        try (InputStream input = getClass().getResourceAsStream(
                "/org/pepsoft/worldpainter/scripts/globals/terrain_texture_painter.js")) {
            assertNotNull(input);
            engine.eval(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        return engine;
    }

    private Map<String, Object> parameters() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("texture", image(0xff777777));
        params.put("palette", "#777777=Stone");
        params.put("terrainFilter", "");
        params.put("layerFilter", "");
        params.put("minHeight", -2048); params.put("maxHeight", 2048);
        params.put("minSlope", 0); params.put("maxSlope", 90); params.put("sideSlope", 35);
        params.put("textureScale", 1.0); params.put("offsetX", 0); params.put("offsetY", 0);
        params.put("repeatTexture", true); params.put("dither", false); params.put("seed", 1337);
        params.put("dryRun", false);
        return params;
    }

    private File image(int colour) throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, colour);
        File file = files.newFile();
        assertTrue(ImageIO.write(image, "png", file));
        return file;
    }

    private Dimension dimension(int... tileXs) {
        World2 world = new World2(DefaultPlugin.JAVA_ANVIL_1_18, -64, 320);
        TileFactory factory = TileFactoryFactory.createFlatTileFactory(0, Terrain.GRASS, -64, 320, 100, 0, false, false);
        Dimension dimension = new Dimension(world, "Texture test", 0, factory, Dimension.Anchor.NORMAL_DETAIL);
        for (int tileX : tileXs) {
            Tile tile = factory.createTile(tileX, 0);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) tile.setTerrain(x, y, Terrain.GRASS);
            dimension.addTile(tile);
        }
        return dimension;
    }

    private void assertAllGrass(Dimension dimension) {
        for (Tile tile : dimension.getTiles()) for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            assertSame(Terrain.GRASS, tile.getTerrain(x, y));
        }
    }
}
