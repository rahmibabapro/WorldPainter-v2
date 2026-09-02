package org.pepsoft.worldpainter.terrain.hydrology.river;

import java.io.Serializable;
import java.util.*;

/**
 * Quantizes continuous analytical water elevation into stable Minecraft source pools, rapids, and waterfalls.
 */
public final class StairStepRiverQuantizer implements Serializable {

    public enum SectionType {
        CALM_SOURCE_POOL,
        ROCKY_RAPID,
        VERTICAL_WATERFALL
    }

    public static class QuantizedWaterBlock implements Serializable {
        public final int x, z, y;
        public final SectionType sectionType;
        public final String blockState;

        public QuantizedWaterBlock(int x, int z, int y, SectionType sectionType, String blockState) {
            this.x = x;
            this.z = z;
            this.y = y;
            this.sectionType = sectionType;
            this.blockState = blockState;
        }

        private static final long serialVersionUID = 1L;
    }

    private StairStepRiverQuantizer() {}

    public static QuantizedWaterBlock quantize(int x, int z, float continuousWaterY, float localSlope, WaterExportMode mode) {
        int baseY = (int) Math.floor(continuousWaterY);
        float frac = continuousWaterY - baseY;

        if (mode == WaterExportMode.VANILLA_STABLE_STAIR_STEP) {
            if (localSlope > 0.40f) {
                // Vertical waterfall
                return new QuantizedWaterBlock(x, z, baseY, SectionType.VERTICAL_WATERFALL, "minecraft:water[level=0]");
            } else if (localSlope > 0.08f) {
                // Rocky rapid: source block contained in rocky channel
                return new QuantizedWaterBlock(x, z, baseY, SectionType.ROCKY_RAPID, "minecraft:water[level=0]");
            } else {
                // Calm pool: standard source block
                return new QuantizedWaterBlock(x, z, baseY, SectionType.CALM_SOURCE_POOL, "minecraft:water[level=0]");
            }
        } else if (mode == WaterExportMode.VANILLA_FLOW_LEVELS || mode == WaterExportMode.PLUGIN_FROZEN_FLOW) {
            if (localSlope > 0.40f) {
                // Falling water
                return new QuantizedWaterBlock(x, z, baseY, SectionType.VERTICAL_WATERFALL, "minecraft:water[level=8]");
            }
            int level = (frac <= 0.05f) ? 0 : Math.max(1, Math.min(7, 8 - Math.round(frac * 8.0f)));
            return new QuantizedWaterBlock(x, z, baseY, SectionType.ROCKY_RAPID, "minecraft:water[level=" + level + "]");
        } else {
            // Modded layered
            int layers = Math.max(1, Math.min(8, Math.round(frac * 8.0f)));
            return new QuantizedWaterBlock(x, z, baseY, SectionType.CALM_SOURCE_POOL, "minecraft:water_layer[layers=" + layers + "]");
        }
    }

    private static final long serialVersionUID = 1L;
}
