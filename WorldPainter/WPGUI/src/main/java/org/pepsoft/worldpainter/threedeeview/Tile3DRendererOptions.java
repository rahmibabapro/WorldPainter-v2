package org.pepsoft.worldpainter.threedeeview;

import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.threedeeview.Tile3DRenderer.LayerVisibilityMode;

import java.util.Objects;
import java.util.Set;

/**
 * Options controlling 3D tile rendering quality and performance.
 */
public final class Tile3DRendererOptions {
    public static Tile3DRendererOptions preview() {
        return new Tile3DRendererOptions(false, false, false);
    }

    public static Tile3DRendererOptions export() {
        return new Tile3DRendererOptions(false, true, false);
    }

    public Tile3DRendererOptions(boolean fastMode, boolean antialiasing, boolean diskCacheEnabled) {
        this.fastMode = fastMode;
        this.antialiasing = antialiasing;
        this.diskCacheEnabled = diskCacheEnabled;
    }

    public boolean isFastMode() {
        return fastMode;
    }

    public Tile3DRendererOptions withFastMode(boolean fastMode) {
        return new Tile3DRendererOptions(fastMode, antialiasing, diskCacheEnabled);
    }

    public boolean isAntialiasing() {
        return antialiasing;
    }

    public boolean isDiskCacheEnabled() {
        return diskCacheEnabled;
    }

    public Tile3DRendererOptions withDiskCacheEnabled(boolean diskCacheEnabled) {
        return new Tile3DRendererOptions(fastMode, antialiasing, diskCacheEnabled);
    }

    public int optionsHash(LayerVisibilityMode layerVisibility, Set<Layer> hiddenLayers) {
        return Objects.hash(fastMode, antialiasing, diskCacheEnabled, layerVisibility, hiddenLayers);
    }

    public boolean equalsOptions(Tile3DRendererOptions other) {
        return other != null && fastMode == other.fastMode && antialiasing == other.antialiasing && diskCacheEnabled == other.diskCacheEnabled;
    }

    private final boolean fastMode, antialiasing, diskCacheEnabled;
}
