package org.pepsoft.worldpainter.layers.renderers;

/** Snow Depth is auxiliary data; the accompanying Frost layer already shows snow coverage. */
public final class SnowDepthRenderer implements NibbleLayerRenderer {
    @Override
    public int getPixelColour(int x, int y, int underlyingColour, int value) {
        return underlyingColour;
    }
}
