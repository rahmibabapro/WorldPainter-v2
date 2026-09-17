package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.layers.CustomAnnotationLayer;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.tools.scripts.RiverDrawingLayerNames;

import java.awt.Color;

import static org.pepsoft.worldpainter.painting.PaintFactory.createLayerPaintId;

/**
 * Ensures the fixed BIT annotation pens for drawn-river networks:
 * River Path, River Mini, River Outlet, River Continue.
 */
public final class RiverPathSupport {
    public static final String RIVER_PATH_LAYER_NAME = RiverDrawingLayerNames.PATH;
    public static final String RIVER_MINI_LAYER_NAME = RiverDrawingLayerNames.MINI;
    public static final String RIVER_OUTLET_LAYER_NAME = RiverDrawingLayerNames.OUTLET;
    public static final String RIVER_CONTINUE_LAYER_NAME = RiverDrawingLayerNames.CONTINUE;

    private static final Color PATH_COLOR = new Color(0x22, 0x88, 0xFF);
    private static final Color MINI_COLOR = new Color(0x2E, 0xC4, 0x5A);
    private static final Color OUTLET_COLOR = new Color(0xE0, 0x2B, 0x2B);
    private static final Color CONTINUE_COLOR = new Color(0xE8, 0xA0, 0x20);

    private RiverPathSupport() {
    }

    public static CustomLayer findRiverPathLayer(App app) {
        return findNamedBitLayer(app, RIVER_PATH_LAYER_NAME);
    }

    public static CustomLayer findNamedBitLayer(App app, String name) {
        for (CustomLayer layer : app.getCustomLayers()) {
            if (name.equals(layer.getName()) && layer.getDataSize() == Layer.DataSize.BIT) {
                return layer;
            }
        }
        return null;
    }

    /** Creates or reuses the River Path layer. Returns null if the world is missing. */
    public static CustomLayer ensureRiverPathLayer(App app) {
        return ensureNamedLayer(app, RIVER_PATH_LAYER_NAME,
                "Nehir merkez hattı / River centreline", PATH_COLOR);
    }

    public static CustomLayer ensureRiverMiniLayer(App app) {
        return ensureNamedLayer(app, RIVER_MINI_LAYER_NAME,
                "Dağ deresi: 1 blok başlar, birleşince kalınlaşır / Mini mountain trickle", MINI_COLOR);
    }

    public static CustomLayer ensureRiverOutletLayer(App app) {
        return ensureNamedLayer(app, RIVER_OUTLET_LAYER_NAME,
                "Çıkış ağzı işaretleri (birden fazla olabilir) / Outlet mouth markers", OUTLET_COLOR);
    }

    public static CustomLayer ensureRiverContinueLayer(App app) {
        return ensureNamedLayer(app, RIVER_CONTINUE_LAYER_NAME,
                "Kenardan/devam eden kalın gövde / Thick continuation from map edge", CONTINUE_COLOR);
    }

    /** Creates all four pens so they appear on the layer palette. */
    public static void ensureAllRiverDrawingLayers(App app) {
        ensureRiverPathLayer(app);
        ensureRiverMiniLayer(app);
        ensureRiverOutletLayer(app);
        ensureRiverContinueLayer(app);
    }

    private static CustomLayer ensureNamedLayer(App app, String name, String description, Color color) {
        if (app.getWorld() == null) {
            return null;
        }
        CustomLayer existing = findNamedBitLayer(app, name);
        if (existing != null) {
            return existing;
        }
        final CustomAnnotationLayer layer = new CustomAnnotationLayer(name, description, color);
        app.registerCustomLayer(layer, false);
        return layer;
    }

    public static void selectRiverPathLayerForPainting(App app) {
        selectLayerForPainting(app, ensureRiverPathLayer(app));
    }

    public static void selectRiverMiniLayerForPainting(App app) {
        selectLayerForPainting(app, ensureRiverMiniLayer(app));
    }

    public static void selectRiverOutletLayerForPainting(App app) {
        selectLayerForPainting(app, ensureRiverOutletLayer(app));
    }

    public static void selectRiverContinueLayerForPainting(App app) {
        selectLayerForPainting(app, ensureRiverContinueLayer(app));
    }

    private static void selectLayerForPainting(App app, CustomLayer layer) {
        if (layer == null) {
            return;
        }
        app.selectPaint(createLayerPaintId(layer));
        app.activateRiverPathDrawingTool();
        app.activateDockPanel("layers");
    }
}
