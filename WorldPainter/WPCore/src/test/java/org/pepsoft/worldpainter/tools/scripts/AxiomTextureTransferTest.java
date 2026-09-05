package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Void;
import org.pepsoft.worldpainter.objects.GenericObject;

import java.util.*;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

public class AxiomTextureTransferTest {
    @Test
    public void bothQuiltingAndNativeMixProtectReadOnlyChunksAndLava() throws Exception {
        for (boolean mixed : new boolean[] {false, true}) {
            final Dimension target = world(0, 0, 1, 1, 110, false);
            target.setBitLayerValueAt(ReadOnly.INSTANCE, 32, 32, true);
            target.setBitLayerValueAt(FloodWithLava.INSTANCE, 80, 80, true);
            final AxiomTextureTransfer.SnowMaskSink snow = (x, y, white) -> {
                assertFalse("Protected cells must not reach snow-mask callbacks",
                        (x >= 32 && x < 48 && y >= 32 && y < 48) || (x == 80 && y == 80));
            };
            if (mixed) {
                AxiomTextureTransfer.applyMixedTerrain(target, Terrain.STONE, (x, y, h, slope) -> true, null, snow);
            } else {
                AxiomTextureTransfer.apply(target, stripes(), Map.of(Material.STONE, Terrain.STONE,
                        Material.DIRT, Terrain.DIRT), 44, null, snow);
            }
            for (int y = 32; y < 48; y++) for (int x = 32; x < 48; x++) {
                assertSame(Terrain.GRASS, target.getTerrainAt(x, y));
            }
            assertSame(Terrain.GRASS, target.getTerrainAt(80, 80));
            assertNotSame(Terrain.GRASS, target.getTerrainAt(0, 0));
        }
    }

    @Test
    public void extractsStructuralBlocksAndRetainsDistinctStates() throws Exception {
        final int size = 16, height = 8;
        final Material[] blocks = new Material[size * size * height];
        final Material basalt = Material.get("minecraft:basalt", "axis", "x");
        final Material smooth = Material.get("minecraft:smooth_basalt");
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            set(blocks, size, size, x, y, 2, x < 8 ? basalt : smooth);
            set(blocks, size, size, x, y, 7, Material.AIR); // Explicit selected air is not a surface.
            set(blocks, size, size, x, y, 4, Material.get("minecraft:spruce_leaves"));
            set(blocks, size, size, x, y, 3, Material.get("minecraft:dark_prismarine_stairs"));
        }
        final AxiomTextureProfile profile = AxiomTextureProfile.analyze(new GenericObject("test", size, size, height, blocks), null);
        assertEquals(256, profile.getSummary().structuralColumns());
        assertEquals(2, profile.getSummary().uniqueStates());
        assertEquals(2, profile.getSummary().minimumSurfaceHeight());
        assertEquals(2, profile.getSummary().maximumSurfaceHeight());
        assertEquals(basalt, profile.getSurfaceMaterial(0, 0));
        assertEquals(smooth, profile.getSurfaceMaterial(15, 15));
        assertFalse(profile.getMaterials().contains(Material.get("minecraft:dark_prismarine_stairs")));
        assertEquals(512, profile.getSummary().excludedOverlayCounts().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    public void reconstructsIdenticalReliefWithExactMaterialStatesAtNegativeCoordinates() throws Exception {
        final int size = 128, depth = 24;
        final Material a = Material.get("minecraft:acacia_wood", "axis", "x");
        final Material b = Material.get("minecraft:acacia_wood", "axis", "z");
        final Material[] blocks = new Material[size * size * depth];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) set(blocks, size, size, x, y, relief(x, y), ((x / 5 + y / 7) & 1) == 0 ? a : b);
        final AxiomTextureProfile profile = AxiomTextureProfile.analyze(new GenericObject("relief", size, size, depth, blocks), null);
        final Dimension target = world(-1, -1, 1, 1, 190, false);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) target.setHeightAt(x - 128, y - 128, 190 + relief(x, y));
        final Map<Material, Terrain> mapping = Map.of(a, Terrain.CUSTOM_1, b, Terrain.CUSTOM_2);
        final AxiomTextureTransfer.Summary result = AxiomTextureTransfer.apply(target, profile, mapping, 1, null);
        assertTrue(result.identityReconstruction());
        assertEquals(16384, result.paintedCells());
        assertEquals(0, result.globalRatioTotalVariation(), 0);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) assertSame(mapping.get(profile.getSurfaceMaterial(x, y)), target.getTerrainAt(x - 128, y - 128));
    }

    @Test
    public void quiltingPreservesPatchesProtectsWaterAndIsIndependentOfTileInsertionOrder() throws Exception {
        final AxiomTextureProfile profile = stripes();
        final Map<Material, Terrain> mapping = Map.of(Material.STONE, Terrain.STONE, Material.DIRT, Terrain.DIRT);
        final Dimension first = world(-1, 0, 2, 1, 96, false), second = world(-1, 0, 2, 1, 96, true);
        for (Dimension d : List.of(first, second)) {
            for (int y = 10; y < 18; y++) for (int x = -20; x < -8; x++) d.setWaterLevelAt(x, y, 100);
            d.getTileForEditing(-1, 0).setBitLayerValue(Void.INSTANCE, 2, 2, true);
            d.getTileForEditing(-1, 0).setBitLayerValue(NotPresentBlock.INSTANCE, 3, 2, true);
            d.getTileForEditing(0, 0).setBitLayerValue(NotPresent.INSTANCE, 100, 100, true);
        }
        final AxiomTextureTransfer.Summary result = AxiomTextureTransfer.apply(first, profile, mapping, 98765, null);
        final AxiomTextureTransfer.Summary repeat = AxiomTextureTransfer.apply(second, profile, mapping, 98765, null);
        assertFalse(result.identityReconstruction());
        assertEquals(result.actualMaterialCounts(), repeat.actualMaterialCounts());
        assertEquals(256L * 16 * Integer.BYTES, result.ribbonBytes());
        long pairs = 0, equal = 0, seamPairs = 0, seamEqual = 0;
        for (int y = 0; y < 128; y++) for (int x = -128; x < 128; x++) {
            final Terrain terrain = first.getTerrainAt(x, y);
            assertSame("determinism at " + x + "," + y, terrain, second.getTerrainAt(x, y));
            if (x > -128 && terrain != Terrain.GRASS && first.getTerrainAt(x - 1, y) != Terrain.GRASS) {
                pairs++; if (terrain == first.getTerrainAt(x - 1, y)) equal++;
                if (x == 0) { seamPairs++; if (terrain == first.getTerrainAt(x - 1, y)) seamEqual++; }
            }
        }
        // Equal-probability iid noise has adjacency 0.5. Source stripes have adjacency ~0.9.
        assertTrue("Neighbourhoods were lost: " + (double) equal / pairs, (double) equal / pairs > 0.80);
        assertTrue("Artificial WorldPainter tile seam", (double) seamEqual / seamPairs > 0.65);
        assertSame(Terrain.GRASS, first.getTerrainAt(-15, 12));
        assertSame(Terrain.GRASS, first.getTerrainAt(-126, 2));
        assertSame(Terrain.GRASS, first.getTerrainAt(-125, 2));
        assertSame(Terrain.GRASS, first.getTerrainAt(100, 100));
        assertEquals(96, first.getHeightAt(-15, 12), 0);
        assertEquals(100, first.getWaterLevelAt(-15, 12));
        assertEquals(96, result.targetMinimumHeight(), 0);
        assertEquals(96, result.targetMaximumHeight(), 0); // Not the world's legal -64..320 limits.
    }

    @Test
    public void filteredTransferKeepsItsEligibilityInTheBoundedRibbon() throws Exception {
        final AxiomTextureProfile profile = stripes();
        final Dimension target = world(0, 0, 1, 1, 110, false);
        final Map<Material, Terrain> mapping = Map.of(Material.STONE, Terrain.STONE, Material.DIRT, Terrain.DIRT);
        final AxiomTextureTransfer.Summary result = AxiomTextureTransfer.apply(target, profile, mapping, 123L, null, null,
                (x, y, height, slopeDegrees) -> x < 64 && y < 80);

        assertEquals(64L * 80L, result.paintedCells());
        assertEquals(0, result.untexturedCells());
        assertEquals(128L * 16L * (Integer.BYTES + 1L), result.ribbonBytes());
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                if (x < 64 && y < 80) {
                    assertNotSame("selected cell at " + x + ',' + y, Terrain.GRASS, target.getTerrainAt(x, y));
                } else {
                    assertSame("outside filtered pass at " + x + ',' + y, Terrain.GRASS, target.getTerrainAt(x, y));
                }
            }
        }
    }

    @Test
    public void nativeMixedTerrainFillUsesOneTerrainWithoutDonorPatches() throws Exception {
        final Dimension target = world(0, 0, 1, 1, 110, false);
        final long[] snowCallbacks = {0};
        final AxiomTextureTransfer.MixedTerrainSummary result = AxiomTextureTransfer.applyMixedTerrain(target,
                Terrain.CUSTOM_1, (x, y, height, slopeDegrees) -> x < 64 && y < 80, null,
                (x, y, sourceSnow) -> {
                    assertFalse(sourceSnow);
                    snowCallbacks[0]++;
                });

        assertEquals(64L * 80L, result.selectedCells());
        assertEquals(64L * 80L, result.changedCells());
        assertEquals(64L * 80L, snowCallbacks[0]);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                assertSame(x < 64 && y < 80 ? Terrain.CUSTOM_1 : Terrain.GRASS, target.getTerrainAt(x, y));
            }
        }
    }

    @Test
    public void rollingPlainsFilterUsesBothTheSnowLineAndSlopeTransition() {
        final AxiomTextureTransfer.TargetCellFilter filter = AxiomTextureTransfer.rollingPlainsFilter(160, 8, 18, 30, 42L);
        assertTrue(filter.accept(0, 0, 120, 5));
        assertFalse(filter.accept(0, 0, 160, 0));
        assertFalse(filter.accept(0, 0, 120, 30));
        assertEquals(filter.accept(15, 21, 157, 24), filter.accept(15, 21, 157, 24));
    }

    @Test
    public void sourceWhitesRemainMeasuredTerrainAndAlsoCreateSnowMask() throws Exception {
        final int size = 32, depth = 14;
        final Material snow = Material.get("minecraft:snow_block");
        final Material[] blocks = new Material[size * size * depth];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            set(blocks, size, size, x, y, 10, Material.DIRT);
            set(blocks, size, size, x, y, 11, snow);
        }
        final AxiomTextureProfile profile = AxiomTextureProfile.analyze(new GenericObject("snow", size, size, depth, blocks), null);
        final Map<Material, Terrain> mapping = Map.of(Material.DIRT, Terrain.DIRT, snow, Terrain.DEEP_SNOW);
        final Dimension low = world(0, 0, 1, 1, 169, false);
        final long[] lowMask = {0};
        final AxiomTextureTransfer.Summary result = AxiomTextureTransfer.apply(low, profile, mapping, 0, null, (x, y, sourceSnow) -> { if (sourceSnow) lowMask[0]++; });
        assertEquals("surface " + result.actualMaterialCounts(), 16384L, result.actualMaterialCounts().get(AxiomTextureProfile.materialKey(snow)).longValue());
        assertFalse(result.actualMaterialCounts().containsKey(AxiomTextureProfile.materialKey(Material.DIRT)));
        assertEquals(16384L, lowMask[0]); // The separate absolute-height pass removes snow below 160.
        assertEquals(16384L, result.sourceSnowCells());
        assertTrue(profile.getMaterials().contains(snow));
        assertTrue(profile.isSnowMask(0, 0));
        assertEquals(Material.DIRT, profile.getUndercoatMaterial(0, 0));
        assertSame(Terrain.DEEP_SNOW, low.getTerrainAt(0, 0));
        final Dimension high = world(0, 0, 1, 1, 210, false);
        final AxiomTextureTransfer.Summary highResult = AxiomTextureTransfer.apply(high, profile, mapping, 0, null);
        assertEquals(16384L, highResult.actualMaterialCounts().get(AxiomTextureProfile.materialKey(snow)).longValue());
        assertSame(Terrain.DEEP_SNOW, high.getTerrainAt(0, 0));
        assertTrue(AxiomTextureProfile.isSnowMaskMaterial(Material.get("minecraft:birch_wood")));
        assertTrue(AxiomTextureProfile.isSnowMaskMaterial(Material.get("minecraft:diorite")));
        assertFalse(AxiomTextureProfile.isSnowMaskMaterial(Material.get("minecraft:pale_oak_wood")));
        assertFalse(AxiomTextureProfile.isSnowMaskMaterial(Material.get("minecraft:light_gray_wool")));
    }

    @Test
    public void whiteSourceFallsBackBelow150OrOnAThirtyDegreeCliff() throws Exception {
        final int size = 32, depth = 14;
        final Material white = Material.get("minecraft:birch_wood", "axis", "y");
        final Material[] blocks = new Material[size * size * depth];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            set(blocks, size, size, x, y, 10, Material.STONE);
            set(blocks, size, size, x, y, 11, white);
        }
        final AxiomTextureProfile profile = AxiomTextureProfile.analyze(new GenericObject("summit white", size, size, depth, blocks), null);
        final Map<Material, Terrain> mapping = Map.of(white, Terrain.DEEP_SNOW, Material.STONE, Terrain.STONE);

        final Dimension low = world(0, 0, 1, 1, 149, false);
        final long[] lowMask = {0};
        AxiomTextureTransfer.apply(low, profile, mapping, 5L, null, (x, y, sourceSnow) -> { if (sourceSnow) lowMask[0]++; });
        assertEquals(0, lowMask[0]);
        assertSame(Terrain.STONE, low.getTerrainAt(64, 64));

        final Dimension steep = world(0, 0, 1, 1, 170, false);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) steep.setHeightAt(x, y, 100 + x);
        final long[] steepMask = {0};
        AxiomTextureTransfer.apply(steep, profile, mapping, 5L, null, (x, y, sourceSnow) -> { if (sourceSnow) steepMask[0]++; });
        // Four border columns have no outside neighbour to measure; the interior is a 45-degree
        // ramp and must never retain white/snow.
        for (int y = 8; y < 120; y++) for (int x = 8; x < 120; x++) {
            assertSame(Terrain.STONE, steep.getTerrainAt(x, y));
        }
        assertSame(Terrain.STONE, steep.getTerrainAt(64, 64));

        assertTrue(AxiomTextureTransfer.steepRockFilter(30).accept(0, 0, 65, 30));
        assertFalse(AxiomTextureTransfer.steepRockFilter(30).accept(0, 0, 65, 29.99f));
    }

    @Test
    public void steepOrOutOfBandMossUsesNearbyMeasuredNonMossTerrain() throws Exception {
        final int size = 32, depth = 20;
        final Material moss = Material.get("minecraft:moss_block");
        final Material[] blocks = new Material[size * size * depth];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            set(blocks, size, size, x, y, 10, ((x + y) & 1) == 0 ? moss : Material.STONE);
        }
        final AxiomTextureProfile profile = AxiomTextureProfile.analyze(new GenericObject("moss", size, size, depth, blocks), null);
        final Dimension target = world(0, 0, 1, 1, 100, false);
        // A 45-degree eastward slope remains inside the height band at x=8, but must reject moss.
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) target.setHeightAt(x, y, 92 + x);
        final Map<Material, Terrain> mapping = Map.of(moss, Terrain.MOSS, Material.STONE, Terrain.STONE);
        final AxiomTextureTransfer.Summary result = AxiomTextureTransfer.apply(target, profile, mapping, 7L, null);
        assertTrue(result.mossRedirectedCells() > 0);
        for (int y = 8; y < 120; y++) for (int x = 8; x < 20; x++) {
            assertNotSame("moss at steep target " + x + "," + y, Terrain.MOSS, target.getTerrainAt(x, y));
        }
    }

    @Test
    public void rollingPlainsMossRetainsMeasuredPatchesOutsideTheMountainHeightBand() throws Exception {
        final int size = 32, depth = 14;
        final Material moss = Material.get("minecraft:moss_block");
        final Material[] blocks = new Material[size * size * depth];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            set(blocks, size, size, x, y, 10, ((x / 4 + y / 4) & 1) == 0 ? moss : Material.STONE);
        }
        final AxiomTextureProfile profile = AxiomTextureProfile.analyze(new GenericObject("plains moss", size, size, depth, blocks), null);
        final Dimension target = world(0, 0, 1, 1, 60, false);
        final Map<Material, Terrain> mapping = Map.of(moss, Terrain.MOSS, Material.STONE, Terrain.STONE);
        final AxiomTextureTransfer.Summary result = AxiomTextureTransfer.apply(target, profile, mapping, 9L, null, null,
                (x, y, height, slopeDegrees) -> true, AxiomTextureTransfer.MossPolicy.ROLLING_PLAINS);
        assertTrue(result.actualMaterialCounts().getOrDefault(AxiomTextureProfile.materialKey(moss), 0L) > 0);
        assertEquals(0, result.mossRedirectedCells());
    }

    @Test
    public void thinSnowMarksGroundButSnowOnFoliageDoesNotAndPottedPlantsAreNotGround() throws Exception {
        final int size = 16, depth = 7;
        final Material[] blocks = new Material[size * size * depth];
        final Material snow = Material.get("minecraft:snow", "layers", "3");
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            set(blocks, size, size, x, y, 2, Material.STONE);
            if (x < 8) set(blocks, size, size, x, y, 3, snow);
            else if (x < 12) {
                set(blocks, size, size, x, y, 3, Material.get("minecraft:spruce_leaves"));
                set(blocks, size, size, x, y, 4, snow);
            } else set(blocks, size, size, x, y, 3, Material.get("minecraft:potted_spruce_sapling"));
        }
        final AxiomTextureProfile profile = AxiomTextureProfile.analyze(new GenericObject("snow layers", size, size, depth, blocks), null);
        assertEquals(256, profile.getSummary().structuralColumns());
        assertEquals(128, profile.getSummary().sourceSnowMaskColumns());
        assertEquals(Set.of(Material.STONE), profile.getMaterials());
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            assertEquals(2, profile.getSurfaceHeight(x, y));
            assertEquals(x < 8, profile.isSnowMask(x, y));
            assertEquals(Material.STONE, profile.getUndercoatMaterial(x, y));
        }
    }

    @Test
    public void diamondBlocksAreDiscardedAndUseTheActualGroundDirectlyBelow() throws Exception {
        final int size = 16, depth = 5;
        final Material[] blocks = new Material[size * size * depth];
        final Material cyanTerracotta = Material.get("minecraft:cyan_terracotta");
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                set(blocks, size, size, x, y, 1, Material.STONE);
                set(blocks, size, size, x, y, 2, cyanTerracotta);
                set(blocks, size, size, x, y, 3, Material.get("minecraft:diamond_block"));
            }
        }
        final AxiomTextureProfile profile = AxiomTextureProfile.analyze(new GenericObject("diamond marker", size, size, depth, blocks), null);
        assertEquals(cyanTerracotta, profile.getSurfaceMaterial(0, 0));
        assertEquals(cyanTerracotta, profile.getUndercoatMaterial(15, 15));
        assertFalse(profile.getMaterials().contains(Material.get("minecraft:diamond_block")));
        assertEquals(256L, profile.getSummary().excludedOverlayCounts().get("minecraft:diamond_block").longValue());
    }

    private static AxiomTextureProfile stripes() throws Exception {
        final int size = 64, depth = 12;
        final Material[] blocks = new Material[size * size * depth];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) set(blocks, size, size, x, y, 10, ((x / 8 + y / 16) & 1) == 0 ? Material.STONE : Material.DIRT);
        return AxiomTextureProfile.analyze(new GenericObject("stripes", size, size, depth, blocks), null);
    }

    private static Dimension world(int tileX, int tileY, int width, int length, int height, boolean reverse) {
        final TileFactory factory = TestData.createTileFactory(height);
        final Dimension dimension = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT), "Surface", 0, factory, NORMAL_DETAIL);
        for (int i = 0; i < width * length; i++) {
            final int at = reverse ? width * length - i - 1 : i;
            final Tile tile = factory.createTile(tileX + at % width, tileY + at / width);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) { tile.setWaterLevel(x, y, 0); tile.setTerrain(x, y, Terrain.GRASS); }
            dimension.addTile(tile);
        }
        return dimension;
    }

    private static int relief(int x, int y) { return 4 + x / 16 + y / 32; }
    private static void set(Material[] data, int width, int length, int x, int y, int z, Material material) { data[x + y * width + z * width * length] = material; }
}
