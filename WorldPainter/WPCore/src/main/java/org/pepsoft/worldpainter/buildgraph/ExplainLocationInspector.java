package org.pepsoft.worldpainter.buildgraph;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.layers.Layer;

import java.util.*;

/**
 * Reads height, terrain, water, slope and active layer values at a world coordinate.
 * Scaffolding for a future "Explain This Location" UI — not a full procedural-rule decomposer.
 */
public final class ExplainLocationInspector {

    public static class LocationExplanation {
        public final int worldX;
        public final int worldY;
        public final float height;
        public final Terrain terrain;
        public final int waterLevel;
        public final double slopeDegrees;
        public final Map<String, Integer> activeLayers;

        public LocationExplanation(int worldX, int worldY, float height, Terrain terrain, int waterLevel, double slopeDegrees, Map<String, Integer> activeLayers) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.height = height;
            this.terrain = terrain;
            this.waterLevel = waterLevel;
            this.slopeDegrees = slopeDegrees;
            this.activeLayers = Collections.unmodifiableMap(activeLayers);
        }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT, "Location (%d, %d):%n", worldX, worldY));
            sb.append(String.format(Locale.ROOT, "  Height: %.2f (Water: %d)%n", height, waterLevel));
            sb.append(String.format(Locale.ROOT, "  Terrain: %s (Slope: %.1f deg)%n", terrain != null ? terrain.getName() : "None", slopeDegrees));
            if (!activeLayers.isEmpty()) {
                sb.append("  Active Layers:\n");
                activeLayers.forEach((name, val) -> sb.append(String.format(Locale.ROOT, "    - %s: %d%n", name, val)));
            } else {
                sb.append("  Active Layers: None\n");
            }
            return sb.toString();
        }
    }

    private ExplainLocationInspector() {}

    public static LocationExplanation explain(int worldX, int worldY, Dimension dimension) {
        if (dimension == null) {
            return new LocationExplanation(worldX, worldY, 0f, null, 0, 0.0, Collections.emptyMap());
        }

        float height = dimension.getHeightAt(worldX, worldY);
        Terrain terrain = dimension.getTerrainAt(worldX, worldY);
        int waterLevel = dimension.getWaterLevelAt(worldX, worldY);
        double slope = dimension.getSlope(worldX, worldY);

        Map<String, Integer> layers = new LinkedHashMap<>();
        for (Layer layer : dimension.getAllLayers(false)) {
            int val = dimension.getLayerValueAt(layer, worldX, worldY);
            if (val > 0) {
                layers.put(layer.getName(), val);
            }
        }

        return new LocationExplanation(worldX, worldY, height, terrain, waterLevel, slope, layers);
    }
}
