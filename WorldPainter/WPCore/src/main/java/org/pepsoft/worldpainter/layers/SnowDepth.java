package org.pepsoft.worldpainter.layers;

/**
 * Explicit surface snow thickness used together with {@link Frost}. Zero leaves the existing
 * Frost export behaviour unchanged; values one through eight request minecraft:snow layers.
 * This layer carries data only: Frost provides both its visible overlay and its exporter.
 */
public final class SnowDepth extends Layer {
    private SnowDepth() {
        super("org.pepsoft.worldpainter.v2.SnowDepth", "Snow Depth",
                "Explicit surface snow layers (1-8); requires Frost", DataSize.NIBBLE, true, 61);
    }

    private Object readResolve() {
        return INSTANCE;
    }

    public static final SnowDepth INSTANCE = new SnowDepth();

    private static final long serialVersionUID = 2026090301L;
}
