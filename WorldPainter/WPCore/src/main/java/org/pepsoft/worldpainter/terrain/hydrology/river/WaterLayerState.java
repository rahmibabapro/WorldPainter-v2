package org.pepsoft.worldpainter.terrain.hydrology.river;

import java.io.Serializable;

/**
 * 8-step layered water representation matching Minecraft fluid levels and snow-layer mechanics.
 */
public final class WaterLayerState implements Serializable {
    public final int baseY;
    public final int layers; // 1..8 where 8 is a full block (1.0m) and 1 is 1/8m (0.125m)
    public final int vanillaWaterLevel; // 0..7 where 0 is full block and 7 is 1/8th block

    public WaterLayerState(int baseY, int layers) {
        this.baseY = baseY;
        this.layers = Math.max(1, Math.min(8, layers));
        // Vanilla water: level=0 is full source (8/8), level=7 is shallowest (1/8)
        this.vanillaWaterLevel = (this.layers == 8) ? 0 : (8 - this.layers);
    }

    public static WaterLayerState fromContinuousElevation(float elevation) {
        int base = (int) Math.floor(elevation);
        float frac = elevation - base;
        if (frac <= 0.001f) {
            // Full block below
            return new WaterLayerState(base - 1, 8);
        }
        int l = Math.max(1, Math.min(8, Math.round(frac * 8.0f)));
        return new WaterLayerState(base, l);
    }

    public String getVanillaBlockState() {
        if (layers == 8) {
            return "minecraft:water[level=0]";
        }
        return "minecraft:water[level=" + vanillaWaterLevel + "]";
    }

    public String getLayeredBlockState() {
        return "minecraft:water_layer[layers=" + layers + "]";
    }

    public float getContinuousHeight() {
        return baseY + (layers / 8.0f);
    }

    private static final long serialVersionUID = 1L;
}
