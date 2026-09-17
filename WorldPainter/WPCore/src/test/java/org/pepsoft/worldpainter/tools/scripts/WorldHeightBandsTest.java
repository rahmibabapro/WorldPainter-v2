package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.HeightMapTileFactory;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.heightMaps.ConstantHeightMap;
import org.pepsoft.worldpainter.themes.SimpleTheme;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
import static org.pepsoft.worldpainter.Terrain.GRASS;

public class WorldHeightBandsTest {
    @Test
    public void percentileHelpers() {
        List<Float> values = new ArrayList<>();
        for (int i = 0; i <= 100; i++) values.add((float) i);
        assertEquals(90f, WorldHeightBands.percentile(values, 0.90f), 0.01f);
        assertEquals(95f, WorldHeightBands.percentile(values, 0.95f), 0.01f);
    }

    @Test
    public void akendorfLikeReliefUsesP90P95NotVanilla160() {
        // Flat 70 with a high ridge at 110 — snow must start below 160 on the ridge band.
        var factory = new HeightMapTileFactory(1L, new ConstantHeightMap(70f),
                TestData.MIN_HEIGHT, TestData.MAX_HEIGHT, false,
                SimpleTheme.createSingleTerrain(GRASS, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT, 0));
        Dimension d = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "bands", 1L, factory, NORMAL_DETAIL);
        d.addTile(factory.createTile(0, 0));
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            d.setWaterLevelAt(x, y, 0);
            d.setHeightAt(x, y, x >= 100 ? 110f : 70f);
        }
        WorldHeightBands bands = WorldHeightBands.from(d, 8);
        assertTrue("snowLine should be below vanilla 160, got " + bands.snowLine(), bands.snowLine() < 160f);
        assertTrue("snowLine should sit on the high ridge band, got " + bands.snowLine(), bands.snowLine() >= 100f);
        assertTrue(bands.fullSnow() > bands.snowLine());
        assertTrue(bands.fullSnow() - bands.snowLine() >= WorldHeightBands.MIN_SNOW_SPAN - 0.01f);
        assertTrue(bands.sampleMax() >= 100f);
    }

    @Test
    public void enforcesMinimumSnowSpan() {
        var factory = new HeightMapTileFactory(1L, new ConstantHeightMap(50f),
                TestData.MIN_HEIGHT, TestData.MAX_HEIGHT, false,
                SimpleTheme.createSingleTerrain(GRASS, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT, 0));
        Dimension d = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "flat", 1L, factory, NORMAL_DETAIL);
        d.addTile(factory.createTile(0, 0));
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            d.setWaterLevelAt(x, y, 0);
            d.setHeightAt(x, y, 50f);
        }
        WorldHeightBands bands = WorldHeightBands.from(d, 16);
        assertEquals(WorldHeightBands.MIN_SNOW_SPAN, bands.fullSnow() - bands.snowLine(), 0.01f);
    }
}
