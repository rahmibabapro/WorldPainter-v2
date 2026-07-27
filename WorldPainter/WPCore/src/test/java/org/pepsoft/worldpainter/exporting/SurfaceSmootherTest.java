package org.pepsoft.worldpainter.exporting;

import org.junit.Test;
import org.pepsoft.minecraft.Direction;
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
        // Above the integer but below +0.5 → bottom slab (classic Axiom-style)
        final Dimension dimension = createDimensionWithHeights(64.25f, 64.25f, 64.25f, 64.25f);
        assertEquals(240, SurfaceSmoother.computeVoxel222(dimension, 0, 0, 64));
    }

    @Test
    public void heightBelowIntegerProducesBottomSlabInRoundCell() {
        // round(63.75)=64; must fill bottom half instead of empty/air
        final Dimension dimension = createDimensionWithHeights(63.75f, 63.75f, 63.75f, 63.75f);
        assertEquals(240, SurfaceSmoother.computeVoxel222(dimension, 0, 0, 64));
    }

    @Test
    public void slopeProducesStairOrSlabMaterial() {
        final Dimension dimension = createDimensionWithHeights(64f, 65f, 64f, 65f);
        final Material smoothed = SurfaceSmoother.smoothSurfaceMaterial(dimension, 0, 0, 64, COBBLESTONE);
        assertNotNull(smoothed);
        assertNotSame(COBBLESTONE, smoothed);
        assertTrue(smoothed.name.endsWith("_stairs") || smoothed.name.endsWith("_slab"));
        assertFalse(smoothed.empty);
    }

    @Test
    public void eastRisingSlopeFacesEast() {
        // Higher toward +X (east): straight stair should face EAST
        final Material stair = SurfaceSmoother.createFromVoxel222(
                240 | 4 | 1, // bottom full + NE + SE (east half elevated)
                COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        assertNotNull(stair);
        assertTrue(stair.name.endsWith("_stairs"));
        assertEquals("straight", stair.getProperty(SHAPE));
        assertEquals(Direction.EAST, stair.getProperty(FACING));
    }

    @Test
    public void southRisingSlopeFacesSouth() {
        final Material stair = SurfaceSmoother.createFromVoxel222(
                240 | 1 | 2, // bottom full + SE + SW (south half elevated)
                COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        assertNotNull(stair);
        assertEquals("straight", stair.getProperty(SHAPE));
        assertEquals(Direction.SOUTH, stair.getProperty(FACING));
    }

    @Test
    public void outerCornerSouthwestFacesWest() {
        final Material stair = SurfaceSmoother.createFromVoxel222(
                240 | 2, // bottom full + SW only
                COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        assertNotNull(stair);
        assertEquals("outer_left", stair.getProperty(SHAPE));
        assertEquals(Direction.WEST, stair.getProperty(FACING));
    }

    @Test
    public void innerCornerMissingSouthwestFacesEast() {
        final Material stair = SurfaceSmoother.createFromVoxel222(
                240 | 1 | 4 | 8, // bottom full + all top except SW
                COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        assertNotNull(stair);
        assertEquals("inner_left", stair.getProperty(SHAPE));
        assertEquals(Direction.EAST, stair.getProperty(FACING));
    }

    @Test
    public void sparseOccupancyDoesNotSnapToAir() {
        // Single bottom corner used to snap to 0 (AIR) and punch holes underwater
        assertEquals(240, SurfaceSmoother.snapToNearestValidVoxel222(128));
        assertEquals(240, SurfaceSmoother.snapToNearestValidVoxel222(16));
        final Material smoothed = SurfaceSmoother.createFromVoxel222(
                SurfaceSmoother.snapToNearestValidVoxel222(128),
                COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        assertNotNull(smoothed);
        assertFalse(smoothed.empty);
    }

    @Test
    public void steepLocalDropDoesNotProduceAirSurface() {
        // High at this cell, much lower neighbors — must not return AIR
        final Dimension dimension = createDimensionWithHeights(64.8f, 63.2f, 63.2f, 63.2f);
        final Material smoothed = SurfaceSmoother.smoothSurfaceMaterial(dimension, 0, 0, 64, COBBLESTONE);
        if (smoothed != null) {
            assertFalse("smoothed surface must not be air", smoothed.empty);
        }
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
    public void deepslateSurfaceSmoothingIsSkipped() {
        assertTrue(SurfaceSmoother.isDeepslateTerrain(Material.get(MC_DEEPSLATE)));
        assertTrue(SurfaceSmoother.isDeepslateTerrain(Material.get("minecraft:cobbled_deepslate")));
        assertTrue(SurfaceSmoother.isDeepslateTerrain(Material.get("minecraft:polished_deepslate")));
        assertFalse(SurfaceSmoother.isDeepslateTerrain(Material.get("minecraft:deepslate_stairs")));
        final Dimension dimension = createDimensionWithHeights(64f, 65f, 64f, 65f);
        assertNull(SurfaceSmoother.smoothSurfaceMaterial(dimension, 0, 0, 64, Material.get(MC_DEEPSLATE)));
        assertNull(SurfaceSmoother.smoothSurfaceMaterial(dimension, 0, 0, 64, Material.get("minecraft:cobbled_deepslate")));
        assertNull(SurfaceSmoother.smoothSurfaceMaterial(dimension, 0, 0, 64, DEEPSLATE_Y));
    }

    @Test
    public void deepslateStairsAreRecognizedAsSmoothedSurfacePartials() {
        // Families stay registered for merge of legacy deepslate partials; new smoothing never creates them.
        assertNotNull(SurfaceSmoother.getFamily(Material.get(MC_DEEPSLATE)));
        final Material deepslateStairs = Material.get("minecraft:deepslate_stairs");
        assertTrue(SurfaceSmoother.isSmoothedSurfacePartial(deepslateStairs));
        assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(Material.get(MC_DEEPSLATE)));
        assertFalse(SurfaceSmoother.isSmoothedSurfacePartial(COBBLESTONE));
    }

    @Test
    public void deepslateUnderwaterPartialIsWaterlogged() {
        final Material deepslateSlab = Material.get("minecraft:deepslate_slab");
        final Material waterlogged = SurfaceSmoother.waterlogUnderwater(deepslateSlab);
        assertTrue(waterlogged.hasProperty(WATERLOGGED));
        assertTrue(waterlogged.getProperty(WATERLOGGED));
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

    @Test
    public void underwaterStairIsWaterlogged() {
        final Material stair = SurfaceSmoother.createFromVoxel222(
                240 | 4 | 1,
                COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        final Material waterlogged = SurfaceSmoother.waterlogUnderwater(stair);
        assertTrue(waterlogged.getProperty(WATERLOGGED));
        assertEquals(Direction.EAST, waterlogged.getProperty(FACING));
    }

    @Test
    public void fluidOccupiesBlockAtAndBelowWaterLevel() {
        assertTrue(SurfaceSmoother.fluidOccupiesBlock(62, 62));
        assertTrue(SurfaceSmoother.fluidOccupiesBlock(61, 62));
        assertFalse(SurfaceSmoother.fluidOccupiesBlock(63, 62));
    }

    @Test
    public void stairAtWaterLevelIsWaterlogged() {
        // Sea-level surface band: waterLevel == intHeight must waterlog (was skipped when only >).
        final Material stair = SurfaceSmoother.createFromVoxel222(
                240 | 4 | 1,
                COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        final Material atWaterLine = SurfaceSmoother.waterlogIfFluidOccupies(stair, 62, 62);
        assertTrue(atWaterLine.hasProperty(WATERLOGGED));
        assertTrue(atWaterLine.getProperty(WATERLOGGED));
        assertEquals(Direction.EAST, atWaterLine.getProperty(FACING));
    }

    @Test
    public void slabAtWaterLevelIsWaterlogged() {
        final Material slab = SurfaceSmoother.createFromVoxel222(
                240, COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        final Material atWaterLine = SurfaceSmoother.waterlogIfFluidOccupies(slab, 64, 64);
        assertTrue(atWaterLine.getProperty(WATERLOGGED));
    }

    @Test
    public void stairAboveWaterLevelIsNotWaterlogged() {
        final Material stair = SurfaceSmoother.createFromVoxel222(
                240 | 4 | 1,
                COBBLESTONE, Material.get(MC_COBBLESTONE_STAIRS), Material.get(MC_COBBLESTONE_SLAB));
        final Material dry = SurfaceSmoother.waterlogIfFluidOccupies(stair, 63, 62);
        assertSame(stair, dry);
        assertFalse(Boolean.TRUE.equals(dry.getProperty(WATERLOGGED)));
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
