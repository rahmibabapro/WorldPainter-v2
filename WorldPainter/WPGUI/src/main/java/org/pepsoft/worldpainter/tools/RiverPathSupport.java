package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.layers.CustomAnnotationLayer;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.layers.Layer;

import java.awt.Color;

import static org.pepsoft.worldpainter.painting.PaintFactory.createLayerPaintId;

/**
 * Ensures the fixed "River Path" BIT layer exists for waypoint painting (Mode 2).
 */
public final class RiverPathSupport {
    public static final String RIVER_PATH_LAYER_NAME = "River Path";

    private RiverPathSupport() {
    }

    public static CustomLayer findRiverPathLayer(App app) {
        for (CustomLayer layer : app.getCustomLayers()) {
            if (RIVER_PATH_LAYER_NAME.equals(layer.getName())
                    && (layer.getDataSize() == Layer.DataSize.BIT)) {
                return layer;
            }
        }
        return null;
    }

    /**
     * Creates or reuses the River Path layer. Returns null if the world is missing.
     */
    public static CustomLayer ensureRiverPathLayer(App app) {
        if (app.getWorld() == null) {
            return null;
        }
        CustomLayer existing = findRiverPathLayer(app);
        if (existing != null) {
            return existing;
        }
        final CustomAnnotationLayer layer = new CustomAnnotationLayer(
                RIVER_PATH_LAYER_NAME,
                "Nehir rota noktaları / River path waypoints",
                new Color(0x22, 0x88, 0xFF));
        app.registerCustomLayer(layer, false);
        return layer;
    }

    public static void selectRiverPathLayerForPainting(App app) {
        final CustomLayer layer = ensureRiverPathLayer(app);
        if (layer == null) {
            return;
        }
        app.selectPaint(createLayerPaintId(layer));
        app.activateDockPanel("layers");
    }
}
