package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Layer;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed BIT annotation layer names for drawn-river pens. Kept in WPCore so the
 * session can discover layers without depending on the GUI.
 */
public final class RiverDrawingLayerNames {
    public static final String PATH = "River Path";
    /** Mountain trickle: starts ~1 block and widens as tributaries merge. */
    public static final String MINI = "River Mini";
    /** Explicit mouth marker(s); drawn paths flow toward these pixels. */
    public static final String OUTLET = "River Outlet";
    /** Thick trunk continuing onto the map from the edge (or mid-map main stem). */
    public static final String CONTINUE = "River Continue";

    public static boolean isPathLike(String name) {
        return PATH.equals(name) || MINI.equals(name) || CONTINUE.equals(name);
    }

    public static boolean isKnown(String name) {
        return isPathLike(name) || OUTLET.equals(name);
    }

    /** BIT drawing pens present on the dimension (Path/Mini/Continue/Outlet). */
    public static List<Layer> findOn(Dimension dimension) {
        List<Layer> found = new ArrayList<>();
        if (dimension == null) return found;
        for (Layer layer : dimension.getAllLayers(false)) {
            if (layer != null && isKnown(layer.getName()) && layer.getDataSize() == Layer.DataSize.BIT) {
                found.add(layer);
            }
        }
        return found;
    }

    public static boolean marked(Tile tile, int x, int y, List<Layer> pens) {
        if (tile == null || pens == null || pens.isEmpty()) return false;
        for (Layer pen : pens) {
            if (pen != null && tile.hasLayer(pen) && tile.getBitLayerValue(pen, x, y)) {
                return true;
            }
        }
        return false;
    }

    private RiverDrawingLayerNames() {
    }
}
