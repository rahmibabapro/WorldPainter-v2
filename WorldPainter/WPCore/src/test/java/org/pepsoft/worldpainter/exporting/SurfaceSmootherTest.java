package org.pepsoft.worldpainter.exporting;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.*;
import static org.pepsoft.minecraft.Material.*;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

public class SurfaceSmootherTest {
    @Test
    public void flatSurfaceProducesFullBlockPattern() {
        final Dimension dimension = createDimensionWithHeights(64f, 64f, 64f, 64f);
        assertEquals(255, SurfaceSmoother.computeVoxel222(dimension, 0, 0, 64));
    }

    @Test
    public void fractionalHeightProducesBottomSlabPattern() {
        final Dimension dimension = createDimensionWithHeights(64.25f, 64.25f, 64.25f, 64.25f);
        assertEquals(240, SurfaceSmoother.computeVoxel222(dimension, 0, 0, 64));
    }

    @Test
    public void slopeProducesStairOrSlabMaterial() {
        final Dimension dimension = createDimensionWithHeights(64f, 65f, 64f, 65f);
        final Material smoothed = SurfaceSmoother.smoothSurfaceMaterial(dimension, 0, 0, 64, COBBLESTONE);
        assertNotNull(smoothed);
        assertNotSame(COBBLESTONE, smoothed);
        assertTrue(smoothed.name.endsWith("_stairs") || smoothed.name.endsWith("_slab"));
    }

    @Test
    public void grassBlockHasNoSmoothingFamily() {
        final Material grass = Material.get(MC_GRASS_BLOCK);
        assertNull(SurfaceSmoother.getFamily(grass));
        final Dimension dimension = createDimensionWithHeights(64f, 65f, 64f, 65f);
        assertNull(SurfaceSmoother.smoothSurfaceMaterial(dimension, 0, 0, 64, grass));
    }

    @Test
    public void createFromVoxel222BottomSlab() {
        final Material slab = SurfaceSmoother.createFromVoxel222(
                240, COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        assertNotNull(slab);
        assertEquals("bottom", slab.getProperty(TYPE));
    }

    @Test
    public void legacyGraniteUsesGraniteSlabFamily() {
        assertNotNull(SurfaceSmoother.getFamily(GRANITE));
        final Dimension dimension = createDimensionWithHeights(64f, 65f, 64f, 65f);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        final Material smoothed = SurfaceSmoother.smoothSurfaceMaterial(dimension, 0, 0, 64, GRANITE);
        assertNotNull(smoothed);
        assertTrue(smoothed.name.contains("granite"));
    }

    @Test
    public void stoneMixSlopeCanSmoothWhenEnabled() {
        final Dimension dimension = createDimensionWithHeights(64f, 65f, 64f, 65f);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        final Material stoneMixSurface = Terrain.STONE_MIX.getMaterial(TestData.WORLD.getPlatform(), TestData.SEED, 0, 0, 64, 64);
        final Material smoothed = SurfaceSmoother.smoothSurfaceMaterial(dimension, 0, 0, 64, stoneMixSurface);
        assertNotNull(smoothed);
        assertTrue(smoothed.name.endsWith("_stairs") || smoothed.name.endsWith("_slab"));
    }

    @Test
    public void underwaterSlabIsWaterlogged() {
        final Material slab = SurfaceSmoother.createFromVoxel222(
                240, COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        final Material waterlogged = SurfaceSmoother.waterlogUnderwater(slab);
        assertTrue(waterlogged.getProperty(WATERLOGGED));
    }

    @Test
    public void underwaterFullBlockIsNotWaterlogged() {
        assertSame(COBBLESTONE, SurfaceSmoother.waterlogUnderwater(COBBLESTONE));
    }

    private static Dimension createDimensionWithHeights(float h00, float h10, float h01, float h11) {
        final TileFactory tileFactory = TestData.createTileFactory(64);
        final Dimension dimension = new Dimension(TestData.WORLD, "Surface", TestData.SEED, tileFactory, NORMAL_DETAIL);
        final Tile tile = tileFactory.createTile(0, 0);
        tile.setHeight(0, 0, h00);
        tile.setHeight(1, 0, h10);
        tile.setHeight(0, 1, h01);
        tile.setHeight(1, 1, h11);
        dimension.addTile(tile);
        return dimension;
    }
}
