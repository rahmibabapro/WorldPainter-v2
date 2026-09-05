package org.pepsoft.worldpainter.exporting;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.MC118AnvilChunk;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.Box;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.SnowDepth;
import org.pepsoft.worldpainter.layers.exporters.FrostExporter;
import org.pepsoft.worldpainter.objects.MinecraftWorldObject;

import java.awt.Rectangle;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Material.*;

/** First-pass smoothing must not remove the supporting face needed by second-pass snow. */
public class ExplicitSnowSurfaceSupportTest {
    @BeforeClass
    public static void initialiseExport() {
        if (Configuration.getInstance() == null) {
            Configuration.setInstance(new Configuration());
        }
        ExportTestSupport.ensureReady();
    }

    @Test
    public void explicitSnowKeepsSolidStoneAndExportsActualLayers() {
        final Fixture fixture = create(4, true, 62);
        final Material surface = fixture.chunk.getMaterial(8, 180, 8);
        assertEquals(STONE, surface);
        final MinecraftWorldObject world = new MinecraftWorldObject("Snow support integration",
                new Box(0, 16, 0, 16, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT), TestData.MAX_HEIGHT, 0);
        world.addChunk(fixture.chunk);
        final Rectangle area = new Rectangle(8, 8, 1, 1);
        new FrostExporter(fixture.dimension, TestData.PLATFORM, new FrostExporter.FrostSettings())
                .addFeatures(area, area, world);
        assertEquals(STONE, world.getMaterialAt(8, 8, 180));
        assertEquals(SNOW.withProperty(LAYERS, 4), world.getMaterialAt(8, 8, 181));
        assertEquals("minecraft:snow", world.getMaterialAt(8, 8, 181).name);
    }

    @Test
    public void zeroDepthAndMissingFrostKeepOriginalSmoothing() {
        assertBottomSlab(create(0, true, 62).chunk.getMaterial(8, 180, 8));
        assertBottomSlab(create(4, false, 62).chunk.getMaterial(8, 180, 8));
        assertBottomSlab(create(0, false, 62).chunk.getMaterial(8, 180, 8));
    }

    @Test
    public void protectedWetCellsKeepTheirWaterloggedSmoothing() {
        final Material material = create(4, true, 181).chunk.getMaterial(8, 180, 8);
        assertBottomSlab(material);
        assertTrue(material.getProperty(WATERLOGGED));
    }

    private static void assertBottomSlab(Material material) {
        assertEquals("minecraft:stone_slab", material.name);
        assertEquals("bottom", material.getProperty(TYPE));
    }

    private static Fixture create(int depth, boolean frost, int waterLevel) {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 2, 2), 180);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        final Tile tile = dimension.getTile(0, 0);
        // Test an interior stone surface, not the intentionally full world edge
        // or an equal-height grass/stone seam.
        for (int y = 7; y <= 9; y++) {
            for (int x = 7; x <= 9; x++) {
                tile.setHeight(x, y, 180.25f);
                tile.setTerrain(x, y, Terrain.STONE);
                tile.setWaterLevel(x, y, waterLevel);
            }
        }
        tile.setLayerValue(SnowDepth.INSTANCE, 8, 8, depth);
        tile.setBitLayerValue(Frost.INSTANCE, 8, 8, frost);
        final MC118AnvilChunk chunk = new MC118AnvilChunk(0, 0, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT);
        final WorldPainterChunkFactory factory = new WorldPainterChunkFactory(dimension, Collections.emptyMap(),
                TestData.PLATFORM, TestData.MAX_HEIGHT);
        factory.applyTopLayer(tile, chunk, 8, 8, TestData.MIN_HEIGHT, false);
        return new Fixture(dimension, chunk);
    }

    private record Fixture(Dimension dimension, MC118AnvilChunk chunk) {
    }
}
