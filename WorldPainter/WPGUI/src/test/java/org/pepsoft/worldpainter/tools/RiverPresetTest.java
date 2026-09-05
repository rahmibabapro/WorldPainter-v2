package org.pepsoft.worldpainter.tools;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.tools.RiverPreset.Mode.*;

/** Tests the UI-to-script contract without constructing a Swing window or modifying a world. */
public class RiverPresetTest {
    @Test
    public void exposesTheExactFullChannelWidthAndDepthMatrix() {
        assertEquals(5, RiverPreset.values().length);
        assertPreset(RiverPreset.STREAM, 1, 3, 6, 0.85, false);
        assertPreset(RiverPreset.NATURAL_RIVER, 2, 5, 12, 1.10, false);
        assertPreset(RiverPreset.WIDE_RIVER, 3, 8, 20, 1.40, false);
        assertPreset(RiverPreset.CANYON, 4, 4, 10, 2.0, false);
        assertPreset(RiverPreset.WATERFALL_MOUNTAIN, 5, 3, 8, 1.0, false);
    }

    @Test
    public void naturalAndWideRiversHaveDifferentScriptPresetsInEveryMode() {
        for (RiverPreset.Mode mode : RiverPreset.Mode.values()) {
            final Map<String, Object> natural = RiverPreset.NATURAL_RIVER.parameters(mode, 2, 62, false, true);
            final Map<String, Object> wide = RiverPreset.WIDE_RIVER.parameters(mode, 2, 62, false, true);
            assertNotEquals(mode.toString(), natural.get("presetId"), wide.get("presetId"));
            assertNotEquals(mode.toString(), natural, wide);
        }
        assertTrue(RiverPreset.WIDE_RIVER.sourceWidth > RiverPreset.NATURAL_RIVER.sourceWidth);
        assertTrue(RiverPreset.WIDE_RIVER.maximumWidth > RiverPreset.NATURAL_RIVER.maximumWidth);
        assertTrue(RiverPreset.WIDE_RIVER.depth > RiverPreset.NATURAL_RIVER.depth);
    }

    @Test
    public void everyModePreservesBothUserBooleanOverridesForEveryPreset() {
        for (RiverPreset preset : RiverPreset.values()) {
            for (RiverPreset.Mode mode : RiverPreset.Mode.values()) {
                for (boolean waterfalls : new boolean[] {false, true}) {
                    for (boolean smoothBanks : new boolean[] {false, true}) {
                        final Map<String, Object> params = preset.parameters(mode, 1, 62, waterfalls, smoothBanks);
                        final String context = preset.name() + "/" + mode;
                        assertEquals(context, waterfalls, params.get("enableWaterfalls"));
                        assertEquals(context, smoothBanks, params.get("bankSmoothing"));
                        assertEquals(context, preset.id, params.get("presetId"));
                    }
                }
            }
        }
    }

    @Test
    public void allPresetsDisableExtraBranchesAndEnableBlueprintGraniteByDefault() {
        for (RiverPreset preset : RiverPreset.values()) {
            for (RiverPreset.Mode mode : RiverPreset.Mode.values()) {
                final Map<String, Object> params = preset.parameters(mode, 3, 62, false, true);
                assertEquals(Boolean.TRUE, params.get("shallowGraniteDetail"));
                if (mode == WAYPOINTS) {
                    assertEquals(0, params.get("tributaryCount"));
                } else {
                    assertEquals(Boolean.TRUE, params.get("disableBranching"));
                    assertEquals(0, params.get("riverLayoutPreset"));
                    assertEquals(1, params.get("styleProfile"));
                }
            }
        }
    }

    @Test
    public void automaticModeClearsAllManualSourcesAndForwardsRequestedCount() {
        for (RiverPreset preset : RiverPreset.values()) {
            for (int count : new int[] {1, 6, 12}) {
                final Map<String, Object> params = preset.parameters(AUTO, count, -20, false, true);
                assertEquals(1, params.get("riverMode"));
                assertEquals(count, params.get("modeRiverCount"));
                assertEquals("", params.get("manualSourceTerrain"));
                assertEquals("", params.get("manualSourceLayer"));
                assertEquals("", params.get("manualStartCoords"));
                assertEquals(-20.0, params.get("deltaSeaLevel"));
                assertFalse(params.containsKey("riverLayer"));
            }
        }
    }

    @Test
    public void sourceModeSelectsPaintedSourceTerrainWithoutAutomaticCountOrStaleCoordinates() {
        for (RiverPreset preset : RiverPreset.values()) {
            final Map<String, Object> params = preset.parameters(SOURCES, 12, 1000, false, true);
            assertEquals(0, params.get("riverMode"));
            assertEquals(1, params.get("modeRiverCount"));
            assertEquals(RiverTerrainSupport.RIVER_SOURCE_TERRAIN_NAME, params.get("manualSourceTerrain"));
            assertEquals("", params.get("manualSourceLayer"));
            assertEquals("", params.get("manualStartCoords"));
            assertEquals(1000.0, params.get("deltaSeaLevel"));
            assertFalse(params.containsKey("linkSparseWaypoints"));
        }
    }

    @Test
    public void waypointModeEnablesSparsePathLinkingAndUsesThePresetChannelDimensions() {
        for (RiverPreset preset : RiverPreset.values()) {
            final Map<String, Object> params = preset.parameters(WAYPOINTS, 4, 87, false, true);
            assertEquals(RiverPathSupport.RIVER_PATH_LAYER_NAME, params.get("riverLayer"));
            assertEquals(Boolean.TRUE, params.get("linkSparseWaypoints"));
            assertEquals(preset.maximumWidth, params.get("riverWidth"));
            assertEquals(preset.depth, params.get("riverDepth"));
            assertEquals(87, params.get("waterLevel"));
            assertEquals(0.25, params.get("tributaryRandomness"));
            assertFalse(params.containsKey("riverMode"));
            assertFalse(params.containsKey("manualSourceTerrain"));
        }
    }

    @Test
    public void everySubmittedParameterIsDeclaredWithItsMatchingTypeInTheSelectedScript() throws IOException {
        final Map<String, String> sourceTypes = scriptParameterTypes("river_script.js");
        final Map<String, String> lineTypes = scriptParameterTypes("river_from_line.js");
        for (RiverPreset preset : RiverPreset.values()) {
            for (RiverPreset.Mode mode : RiverPreset.Mode.values()) {
                final Map<String, String> declaredTypes = mode == WAYPOINTS ? lineTypes : sourceTypes;
                for (Map.Entry<String, Object> entry : preset.parameters(mode, 3, 62, false, true).entrySet()) {
                    final String context = preset.name() + "/" + mode + "/" + entry.getKey();
                    final String declaredType = declaredTypes.get(entry.getKey());
                    assertNotNull("Missing script.param declaration: " + context, declaredType);
                    final Object value = entry.getValue();
                    final boolean compatible = switch (declaredType) {
                        case "boolean" -> value instanceof Boolean;
                        case "integer" -> value instanceof Integer;
                        case "float" -> value instanceof Number;
                        case "string" -> value instanceof String;
                        default -> false;
                    };
                    assertTrue("Incompatible script parameter type " + declaredType + ": " + context, compatible);
                }
            }
        }
    }

    @Test
    public void preferencesFallBackToNaturalRiverWithoutRejectingValidSavedPresets() {
        assertSame(RiverPreset.NATURAL_RIVER, RiverPreset.fromPreference(null));
        assertSame(RiverPreset.NATURAL_RIVER, RiverPreset.fromPreference(""));
        assertSame(RiverPreset.NATURAL_RIVER, RiverPreset.fromPreference("obsolete-preset"));
        for (RiverPreset preset : RiverPreset.values()) {
            assertSame(preset, RiverPreset.fromPreference(preset.name()));
        }
    }

    @Test
    public void descriptionsPromiseOnlyBoundedWetChannelEditsWithoutSurroundingEarthworks() {
        for (RiverPreset preset : RiverPreset.values()) {
            final String description = preset.description();
            assertTrue(description.contains("Yalnız ıslak kanalda"));
            assertTrue(description.contains(String.valueOf(preset.depth + 0.75)));
            assertTrue(description.contains("dolgu yapılmaz"));
            assertTrue(description.contains("Kuru kıyı ve yamaçlar aynen korunur"));
            assertFalse(description.contains("12 blokta"));
            assertFalse(description.contains("2 blok dolgu"));
        }
    }

    @Test
    public void countIsValidatedAndParameterMapsDoNotLeakChangesBetweenCalls() {
        for (RiverPreset.Mode mode : RiverPreset.Mode.values()) {
            assertThrows(IllegalArgumentException.class,
                    () -> RiverPreset.NATURAL_RIVER.parameters(mode, 0, 62, false, true));
            assertThrows(IllegalArgumentException.class,
                    () -> RiverPreset.NATURAL_RIVER.parameters(mode, 13, 62, false, true));
        }
        final Map<String, Object> first = RiverPreset.STREAM.parameters(AUTO, 1, 62, false, true);
        first.put("presetId", 99);
        first.put("manualSourceTerrain", "old-painted-source");
        final Map<String, Object> next = RiverPreset.STREAM.parameters(AUTO, 1, 62, false, true);
        assertEquals(1, next.get("presetId"));
        assertEquals("", next.get("manualSourceTerrain"));
    }

    private static void assertPreset(RiverPreset preset, int id, int sourceWidth, int maximumWidth,
                                     double depth, boolean waterfalls) {
        assertEquals(preset.name(), id, preset.id);
        assertEquals(preset.name(), sourceWidth, preset.sourceWidth);
        assertEquals(preset.name(), maximumWidth, preset.maximumWidth);
        assertEquals(preset.name(), depth, preset.depth, 0.0);
        assertEquals(preset.name(), waterfalls, preset.waterfalls);
    }

    private static Map<String, String> scriptParameterTypes(String fileName) throws IOException {
        final String path = "/org/pepsoft/worldpainter/scripts/rivers/" + fileName;
        try (InputStream input = RiverPresetTest.class.getResourceAsStream(path)) {
            assertNotNull("Missing bundled script: " + path, input);
            final String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            final Matcher matcher = Pattern.compile(
                    "(?m)^\\s*//\\s*script\\.param\\.([A-Za-z0-9_]+)\\.type\\s*=\\s*([A-Za-z]+)\\s*$")
                    .matcher(script);
            final Map<String, String> result = new HashMap<>();
            while (matcher.find()) {
                assertNull("Duplicate parameter declaration in " + fileName + ": " + matcher.group(1),
                        result.put(matcher.group(1), matcher.group(2)));
            }
            assertFalse("No parameter declarations in " + path, result.isEmpty());
            return result;
        }
    }
}
