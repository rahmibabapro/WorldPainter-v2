package org.pepsoft.worldpainter.layers.renderers;

/** Auxiliary export metadata must not obscure the terrain or water overlay. */
public final class RiverWaterlineDetailRenderer implements BitLayerRenderer {
    @Override public int getPixelColour(int x, int y, int underlyingColour, boolean value) {
        return underlyingColour;
    }
}
