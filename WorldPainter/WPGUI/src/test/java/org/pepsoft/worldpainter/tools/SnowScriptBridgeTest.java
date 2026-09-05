package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.Annotations;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.themes.SimpleTheme;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.*;

/** Actual Nashorn scripts and Java engine, without an App singleton or a Swing window. */
public class SnowScriptBridgeTest {
    @Test public void missingOptionalBindingsUseSafeAnnotationDryRunDefaults() throws Exception {
        final Dimension dimension = dimension(210);
        dimension.setLayerValueAt(Annotations.INSTANCE, 8, 8, 4);
        final ScriptEngine engine = engine(dimension, "{}");
        engine.eval(source("snow/snowify.js"));
        assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, 8, 8));
        assertEquals(Terrain.STONE, dimension.getTerrainAt(8, 8));
        assertEquals(Boolean.TRUE, engine.get("dryRun"));
    }

    @Test public void snowifyAppliesOnlyAnnotationFourWithNullProgress() throws Exception {
        final Dimension dimension = dimension(210);
        dimension.setLayerValueAt(Annotations.INSTANCE, 8, 8, 4);
        final ScriptEngine engine = engine(dimension, "{dryRun:false,addHeight:false}");
        engine.eval(source("snow/snowify.js"));
        assertTrue(dimension.getBitLayerValueAt(Frost.INSTANCE, 8, 8));
        assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, 9, 8));
        assertEquals(210, dimension.getHeightAt(8, 8), 0);
    }

    @Test public void legacyGlobalDoesNotNeedAppAndHonoursFalseStringBiomeOption() throws Exception {
        final Dimension dimension = dimension(120);
        engine(dimension, "{useBiomeLayer:'false'}").eval(source("globals/global_realistic_snow.js"));
        assertTrue(dimension.getBitLayerValueAt(Frost.INSTANCE, 8, 8));
        assertEquals(4, dimension.getLayerValueAt(Biome.INSTANCE, 8, 8));
        assertEquals(Terrain.STONE, dimension.getTerrainAt(8, 8));
        assertEquals(120, dimension.getHeightAt(8, 8), 0);
    }

    @Test public void scriptsRejectInvalidNumbersAndFlagsBeforeChangingTheWorld() throws Exception {
        for (String script : new String[] { "snow/snowify.js", "globals/global_realistic_snow.js" }) {
            for (String params : new String[] { "{snowLineHeight:NaN}", "{fullSnowHeight:Infinity}",
                    "{clearLowSnow:'maybe'}", "{snowLineHeight:200,fullSnowHeight:100}" }) {
                final Dimension dimension = dimension(210);
                final ScriptEngine engine = engine(dimension, params);
                assertThrows(script + params, ScriptException.class, () -> engine.eval(source(script)));
                assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, 8, 8));
                assertEquals(Terrain.STONE, dimension.getTerrainAt(8, 8));
            }
        }
    }

    @Test public void smallDialogParametersMatchTheActualSnowifySchema() throws Exception {
        final Map<String, Object> params = SnowToolsDialog.parameters(160, 190, 8, 55, false, true);
        final String script = source("snow/snowify.js");
        for (String name : params.keySet()) assertTrue("Unknown dialog parameter " + name, script.contains("script.param." + name + ".type="));
        assertEquals("annotation", params.get("targetMode"));
        assertEquals(4, params.get("annotationValue"));
        assertEquals(Boolean.FALSE, params.get("addHeight"));
        assertEquals(Boolean.TRUE, params.get("dryRun"));
        assertFalse(params.containsKey("mA"));
        assertFalse(params.containsKey("maxlayers"));
        assertThrows(IllegalArgumentException.class, () -> SnowToolsDialog.parameters(160, 190, 16, 55, false, true));
        assertThrows(IllegalArgumentException.class, () -> SnowToolsDialog.parameters(190, 160, 8, 55, false, true));
        assertThrows(IllegalArgumentException.class, () -> SnowToolsDialog.parameters(Double.NaN, 190, 8, 55, false, true));
    }

    private static ScriptEngine engine(Dimension dimension, String params) throws Exception {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        engine.put("dimension", dimension);
        engine.eval("var params=" + params + ";function print(message){}");
        return engine;
    }

    private static String source(String script) throws Exception {
        try (InputStream in = SnowScriptBridgeTest.class.getResourceAsStream("/org/pepsoft/worldpainter/scripts/" + script)) {
            assertNotNull(script, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Dimension dimension(int height) {
        final World2 world = new World2(DefaultPlugin.JAVA_ANVIL_1_19, -64, 320);
        final HeightMapTileFactory factory = TileFactoryFactory.createFlatTileFactory(0, Terrain.STONE, -64, 320, height, 0, false, false);
        // The default height theme paints permafrost, stone mix and Frost at
        // altitude even when STONE was passed as its lowland terrain. These
        // script tests need an explicitly snow-free, uniform initial surface.
        factory.setTheme(SimpleTheme.createSingleTerrain(Terrain.STONE, -64, 320, 0));
        final Dimension dimension = new Dimension(world, "Snow bridge", 0, factory, Dimension.Anchor.NORMAL_DETAIL);
        final Tile tile = factory.createTile(0, 0);
        for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) tile.setLayerValue(Biome.INSTANCE, x, y, 4);
        dimension.addTile(tile);
        assertEquals("Fixture surface must not depend on the default altitude theme", Terrain.STONE, dimension.getTerrainAt(8, 8));
        assertFalse("Fixture must not begin with factory-generated Frost", dimension.getBitLayerValueAt(Frost.INSTANCE, 8, 8));
        return dimension;
    }
}
