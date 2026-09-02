package org.pepsoft.worldpainter.terrain.hydrology.river;

import org.junit.Test;
import org.pepsoft.worldpainter.terrain.hydrology.river.StairStepRiverQuantizer.*;

import static org.junit.Assert.*;

public class StairStepRiverQuantizerTest {

    @Test
    public void testVanillaStableStairStepProducesSafeSourceBlocks() {
        // Flat pool (slope 0.01) -> CALM_SOURCE_POOL with water[level=0]
        QuantizedWaterBlock pool = StairStepRiverQuantizer.quantize(10, 10, 64.5f, 0.01f, WaterExportMode.VANILLA_STABLE_STAIR_STEP);
        assertEquals(64, pool.y);
        assertEquals(SectionType.CALM_SOURCE_POOL, pool.sectionType);
        assertEquals("minecraft:water[level=0]", pool.blockState);

        // Rocky rapid (slope 0.15) -> ROCKY_RAPID with water[level=0]
        QuantizedWaterBlock rapid = StairStepRiverQuantizer.quantize(10, 10, 64.5f, 0.15f, WaterExportMode.VANILLA_STABLE_STAIR_STEP);
        assertEquals(SectionType.ROCKY_RAPID, rapid.sectionType);
        assertEquals("minecraft:water[level=0]", rapid.blockState);

        // Vertical waterfall (slope 0.60) -> VERTICAL_WATERFALL
        QuantizedWaterBlock fall = StairStepRiverQuantizer.quantize(10, 10, 64.5f, 0.60f, WaterExportMode.VANILLA_STABLE_STAIR_STEP);
        assertEquals(SectionType.VERTICAL_WATERFALL, fall.sectionType);
    }

    @Test
    public void testPluginFrozenFlowProducesExactFlowLevels() {
        // Continuous water 64.75 (3/4 full) -> level=2 (8 - 6 = 2)
        QuantizedWaterBlock flow = StairStepRiverQuantizer.quantize(10, 10, 64.75f, 0.05f, WaterExportMode.PLUGIN_FROZEN_FLOW);
        assertEquals(64, flow.y);
        assertEquals("minecraft:water[level=2]", flow.blockState);

        // Steep drop waterfall -> level=8 (falling)
        QuantizedWaterBlock fall = StairStepRiverQuantizer.quantize(10, 10, 64.75f, 0.50f, WaterExportMode.PLUGIN_FROZEN_FLOW);
        assertEquals("minecraft:water[level=8]", fall.blockState);
    }
}
