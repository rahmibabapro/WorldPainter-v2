package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.tools.scripts.SmoothSnow;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class AxiomMountainSnowScriptTest {
    @Test public void omittedBooleansAndMissingProgressKeepDryRunSafe() throws Exception {
        Dimension dimension = dimension();
        ScriptEngine engine = execute(dimension, parameters(), false);
        assertEquals(Boolean.TRUE, engine.get("dryRun"));
        assertTrue(((SmoothSnow.Result) engine.get("result")).snowCovered() > 0);
        assertOriginal(dimension);
    }

    @Test public void explicitApplyWithNullProgressUsesUnambiguousBridge() throws Exception {
        Dimension dimension = dimension();
        Map<String, Object> params = parameters();
        params.put("dryRun", false);
        ScriptEngine engine = execute(dimension, params, true);
        assertTrue(((SmoothSnow.Result) engine.get("result")).snowCovered() > 0);
        assertTrue(dimension.getTile(0, 0).hasLayer(Frost.INSTANCE));
    }

    @Test public void invalidExplicitBooleanCannotSilentlyBecomeApplyMode() throws Exception {
        for (String name : new String[] {"dryRun", "clearLowSnow", "addHeight"}) {
            Dimension dimension = dimension();
            Map<String, Object> params = parameters();
            params.put(name, "invalid");
            assertThrows(ScriptException.class, () -> execute(dimension, params, false));
            assertOriginal(dimension);
        }
    }

    @Test public void nonFiniteAndFractionalValuesAreRejectedBeforeJavaCoercion() throws Exception {
        for (Map.Entry<String, Object> invalid : Map.<String, Object>of(
                "snowLineHeight", Double.NaN, "fullSnowHeight", Double.POSITIVE_INFINITY,
                "maxSnowLayers", 2.5, "seed", Double.NaN).entrySet()) {
            Dimension dimension = dimension();
            Map<String, Object> params = parameters();
            params.put(invalid.getKey(), invalid.getValue());
            assertThrows(ScriptException.class, () -> execute(dimension, params, false));
            assertOriginal(dimension);
        }
    }

    private ScriptEngine execute(Dimension dimension, Map<String, Object> params, boolean nullProgress) throws Exception {
        ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        engine.put("dimension", dimension);
        engine.put("params", params);
        if (nullProgress) engine.put("progress", null);
        engine.getContext().setWriter(new StringWriter());
        try (InputStream input = getClass().getResourceAsStream(
                "/org/pepsoft/worldpainter/scripts/globals/axiom_mountain_smooth_snow.js")) {
            assertNotNull(input);
            engine.eval(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        return engine;
    }

    private Map<String, Object> parameters() {
        return new HashMap<>(Map.of("snowLineHeight", 160, "fullSnowHeight", 190,
                "maxSnowLayers", 8, "slopeStart", 25, "slopeReject", 55,
                "northFacingBoost", 0.15, "seed", 160190, "snowTerrain", "Deep Snow"));
    }

    private Dimension dimension() {
        World2 world = new World2(DefaultPlugin.JAVA_ANVIL_1_18, -64, 320);
        TileFactory factory = TileFactoryFactory.createFlatTileFactory(0, Terrain.GRASS, -64, 320, 220, 0, false, false);
        Dimension dimension = new Dimension(world, "Axiom snow wrapper", 0, factory, Dimension.Anchor.NORMAL_DETAIL);
        Tile tile = factory.createTile(0, 0);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            tile.setTerrain(x, y, Terrain.GRASS);
            tile.setBitLayerValue(Frost.INSTANCE, x, y, false);
        }
        dimension.addTile(tile);
        return dimension;
    }

    private void assertOriginal(Dimension dimension) {
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            assertSame(Terrain.GRASS, dimension.getTerrainAt(x, y));
            assertEquals(220, dimension.getHeightAt(x, y), 0);
            assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, x, y));
        }
    }
}
