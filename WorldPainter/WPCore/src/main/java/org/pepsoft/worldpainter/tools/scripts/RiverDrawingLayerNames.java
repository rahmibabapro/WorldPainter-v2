package org.pepsoft.worldpainter.tools.scripts;

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

    private RiverDrawingLayerNames() {
    }
}
