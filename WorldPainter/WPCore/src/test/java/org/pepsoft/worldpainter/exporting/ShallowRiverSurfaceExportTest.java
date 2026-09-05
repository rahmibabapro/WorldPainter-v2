package org.pepsoft.worldpainter.exporting;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Direction;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.tools.scripts.ShallowRiverCarver;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.*;
import static org.pepsoft.minecraft.Material.FACING;
import static org.pepsoft.minecraft.Material.HALF;
import static org.pepsoft.minecraft.Material.SHAPE;
import static org.pepsoft.minecraft.Material.TYPE;
import static org.pepsoft.minecraft.Material.WATERLOGGED;

/**
 * Uses the real chunk factory, including its height snapshot and top-layer pass.
 * A river must first carve a shallow heightmap and select granite in the channel;
 * smoothing cannot turn grass/dirt into stairs or repair an already deep trench.
 */
public class ShallowRiverSurfaceExportTest {
    @BeforeClass
    public static void initialiseExport() {
        // Do not load or change the user's on-disk WorldPainter configuration.
        if (Configuration.getInstance() == null) {
            Configuration.setInstance(new Configuration());
        }
        ExportTestSupport.ensureReady();
    }

    @Test
    public void shallowGraniteFloorExportsWaterloggedBottomSlab() {
        final Fixture fixture = create(Terrain.GRANITE, 64.25f, 64.25f, 64.25f, 64.25f, 65, true);
        final Material surface = fixture.chunk.getMaterial(X, 64, Z);
        assertEquals("minecraft:granite_slab", surface.name);
        assertEquals("bottom", surface.getProperty(TYPE));
        assertTrue(surface.getProperty(WATERLOGGED));
        assertEquals(MC_WATER, fixture.chunk.getMaterial(X, 65, Z).name);
        assertEquals(MC_GRANITE, fixture.chunk.getMaterial(X, 63, Z).name);
    }

    @Test
    public void graniteBankRisingEastExportsWaterloggedEastStair() {
        final Fixture fixture = create(Terrain.GRANITE, 64f, 65f, 64f, 65f, 65, true);
        assertWaterloggedStair(fixture.chunk.getMaterial(X, 64, Z), Direction.EAST);
    }

    @Test
    public void graniteBankRisingSouthExportsWaterloggedSouthStair() {
        final Fixture fixture = create(Terrain.GRANITE, 64f, 64f, 65f, 65f, 65, true);
        assertWaterloggedStair(fixture.chunk.getMaterial(X, 64, Z), Direction.SOUTH);
    }

    @Test
    public void graniteAtWaterlineStillReceivesWaterlogging() {
        final Fixture fixture = create(Terrain.GRANITE, 64.25f, 64.25f, 64.25f, 64.25f, 64, true);
        final Material surface = fixture.chunk.getMaterial(X, 64, Z);
        assertEquals("minecraft:granite_slab", surface.name);
        assertTrue("Water at the partial's own Y must fill its empty half", surface.getProperty(WATERLOGGED));
        assertTrue(fixture.chunk.getMaterial(X, 65, Z).empty);
    }

    @Test
    public void dryGraniteBankIsNotWaterlogged() {
        final Fixture fixture = create(Terrain.GRANITE, 64.25f, 64.25f, 64.25f, 64.25f, 62, true);
        final Material surface = fixture.chunk.getMaterial(X, 64, Z);
        assertEquals("minecraft:granite_slab", surface.name);
        assertFalse(Boolean.TRUE.equals(surface.getProperty(WATERLOGGED)));
    }

    @Test
    public void dryGrassAndDirtStayFullBlocksWithoutCobblestoneSubstitution() {
        for (Terrain terrain : new Terrain[] { Terrain.GRASS, Terrain.DIRT }) {
            final Fixture fixture = create(terrain, 64.25f, 65.25f, 64.25f, 65.25f, 62, true);
            final Fixture reference = create(terrain, 64.25f, 65.25f, 64.25f, 65.25f, 62, false);
            assertEquals(terrain == Terrain.GRASS ? MC_GRASS_BLOCK : MC_DIRT,
                    fixture.chunk.getMaterial(X, 64, Z).name);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 62; y <= 66; y++) {
                        assertEquals("Enabling smoothing must preserve the original grass/dirt export",
                                reference.chunk.getMaterial(x, y, z), fixture.chunk.getMaterial(x, y, z));
                        assertFalse("Grass river banks must never be replaced with cobblestone",
                                fixture.chunk.getMaterial(x, y, z).name.contains("cobblestone"));
                    }
                }
            }
        }
    }

    @Test
    public void smoothingRequiresExplicitDimensionSettingAndKeepsItUnchanged() {
        final Fixture fixture = create(Terrain.GRANITE, 64.25f, 64.25f, 64.25f, 64.25f, 65, false);
        assertEquals(MC_GRANITE, fixture.chunk.getMaterial(X, 64, Z).name);
        assertEquals(Dimension.SurfaceSmoothing.NONE, fixture.dimension.getSurfaceSmoothing());
        assertEquals(MC_WATER, fixture.chunk.getMaterial(X, 65, Z).name);
    }

    @Test
    public void subVoxelSmoothingDoesNotDoubleDepthOrChangeHeightmap() {
        for (boolean smoothing : new boolean[] { false, true }) {
            final Fixture fixture = create(Terrain.GRANITE, 64.25f, 64.25f, 64.25f, 64.25f, 65, smoothing);
            assertEquals(64.25f, fixture.dimension.getHeightAt(X, Z), 0f);
            assertEquals(65, fixture.dimension.getWaterLevelAt(X, Z));
            assertEquals(Terrain.GRANITE, fixture.dimension.getTerrainAt(X, Z));
            assertEquals(MC_GRANITE, fixture.chunk.getMaterial(X, 63, Z).name);
            assertFalse(fixture.chunk.getMaterial(X, 64, Z).empty);
            assertFalse(MC_WATER.equals(fixture.chunk.getMaterial(X, 64, Z).name));
            assertEquals(MC_WATER, fixture.chunk.getMaterial(X, 65, Z).name);
            assertTrue("No extra water column may be introduced above the planned surface",
                    fixture.chunk.getMaterial(X, 66, Z).empty);
            assertEquals(1, countWaterBlocks(fixture.chunk));
        }
    }

    @Test
    public void blueprintStyleCarverExportsShallowWaterloggedGraniteDetailsAndPreservesPlains() {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension, 5, 12, 1.10,
                true, true, 1337, null);
        final int[] xs = new int[89], zs = new int[89];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = 20 + i;
            zs[i] = 64;
        }
        assertTrue(carver.getLastRejection(), carver.addPath(xs, zs));
        final ShallowRiverCarver.Result result = carver.apply();
        assertTrue("The configured carver must select some actual granite terrain", result.graniteCells() > 0);

        final WorldPainterChunkFactory factory = new WorldPainterChunkFactory(dimension, Collections.emptyMap(),
                TestData.PLATFORM, TestData.MAX_HEIGHT);
        int granitePartials = 0, waterloggedGranitePartials = 0, wetColumns = 0;
        // Cover both sides of the waterline, multiple chunk boundaries and dry plains.
        for (int chunkX = 1; chunkX <= 6; chunkX++) {
            for (int chunkZ = 2; chunkZ <= 5; chunkZ++) {
                final Chunk chunk = factory.createChunk(chunkX, chunkZ).chunk;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int fullWaterBlocks = 0;
                        for (int y = TestData.MIN_HEIGHT; y < TestData.MAX_HEIGHT; y++) {
                            final Material material = chunk.getMaterial(x, y, z);
                            if (MC_WATER.equals(material.name)) fullWaterBlocks++;
                            if ("minecraft:granite_slab".equals(material.name)
                                    || "minecraft:granite_stairs".equals(material.name)) {
                                granitePartials++;
                                if (Boolean.TRUE.equals(material.getProperty(WATERLOGGED))) {
                                    waterloggedGranitePartials++;
                                }
                            }
                        }
                        assertTrue("A shallow river must not export a deep water column at "
                                        + ((chunkX << 4) + x) + "," + ((chunkZ << 4) + z),
                                fullWaterBlocks <= 2);
                        if (fullWaterBlocks > 0) wetColumns++;

                        final int worldX = (chunkX << 4) + x, worldZ = (chunkZ << 4) + z;
                        if (worldZ < 48 || worldZ > 80) {
                            assertEquals(100f, dimension.getHeightAt(worldX, worldZ), 0f);
                            assertEquals(62, dimension.getWaterLevelAt(worldX, worldZ));
                            assertEquals(Terrain.GRASS, dimension.getTerrainAt(worldX, worldZ));
                            assertEquals(MC_GRASS_BLOCK, chunk.getMaterial(x, 100, z).name);
                        }
                    }
                }
            }
        }
        assertTrue("The end-to-end export must contain actual river water", wetColumns > 0);
        assertTrue("Granite must export as partial blocks, not only full terrain cubes", granitePartials > 0);
        assertTrue("The river's submerged partial blocks must contain water", waterloggedGranitePartials > 0);
        assertEquals(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS, dimension.getSurfaceSmoothing());
    }

    @Test
    public void streamAndNaturalExportAtLeastThreeContinuousFullWaterColumns() {
        for (double[] preset : new double[][] { { 3, 6, 0.85 }, { 5, 12, 1.10 } }) {
            final ExportedRiver river = exportRiver(preset[0], preset[1], preset[2], false);
            int minimumWetWidth = Integer.MAX_VALUE;
            for (int x = 24; x <= 104; x++) {
                final int waterY = river.dimension.getWaterLevelAt(x, 64);
                int continuous = 0;
                for (int z = 64; z < 80 && isFullWater(river.material(x, waterY, z)); z++) continuous++;
                for (int z = 63; z >= 48 && isFullWater(river.material(x, waterY, z)); z--) continuous++;
                minimumWetWidth = Math.min(minimumWetWidth, continuous);
                for (int z = 63; z <= 65; z++) {
                    assertTrue("Full-water inner corridor missing at " + x + "," + z
                                    + ", preset width=" + preset[0] + ", cross-section width=" + continuous,
                            isFullWater(river.material(x, waterY, z)));
                }
            }
            System.out.println("River export cross-section: startWidth=" + preset[0]
                    + ", minimum continuous full-water width=" + minimumWetWidth);
            assertTrue("Waterlogged shore fragments do not count as the traversable wet corridor", minimumWetWidth >= 3);
        }
    }

    @Test
    public void exportedDryOuterBanksContainNoFluidAndRemainGrass() {
        final ExportedRiver river = exportRiver(5, 12, 1.10, true);
        int dryBankFluids = 0;
        for (int x = 24; x <= 104; x++) {
            for (int z = 40; z <= 88; z++) {
                final int terrainY = river.dimension.getIntHeightAt(x, z);
                final int waterY = river.dimension.getWaterLevelAt(x, z);
                if (waterY < terrainY) {
                    for (int y = terrainY; y <= terrainY + 2; y++) {
                        if (containsFluid(river.material(x, y, z))) dryBankFluids++;
                    }
                }
                if (z <= 48 || z >= 80) {
                    assertEquals("Dry plains must not receive granite shore paint", Terrain.GRASS,
                            river.dimension.getTerrainAt(x, z));
                    assertEquals(MC_GRASS_BLOCK, river.material(x, terrainY, z).name);
                    assertFalse(containsFluid(river.material(x, terrainY, z)));
                }
            }
        }
        System.out.println("River export dry-bank fluid cells=" + dryBankFluids);
        assertEquals("Smoothing must not introduce fluid above a dry bank's stored water plane", 0, dryBankFluids);
    }

    @Test
    public void waterloggedGraniteDetailsHaveSolidSupportAndConnectToActualRiverWater() {
        final ExportedRiver river = exportRiver(5, 12, 1.10, false);
        final Set<Voxel> fluid = new HashSet<>(), partials = new HashSet<>(), visited = new HashSet<>();
        final ArrayDeque<Voxel> queue = new ArrayDeque<>();
        int unsupportedPartials = 0;
        for (int x = 16; x < 112; x++) {
            for (int z = 48; z < 80; z++) {
                for (int y = 96; y <= 102; y++) {
                    final Material material = river.material(x, y, z);
                    final Voxel voxel = new Voxel(x, y, z);
                    if (containsFluid(material)) fluid.add(voxel);
                    if (isFullWater(material)) {
                        visited.add(voxel);
                        queue.add(voxel);
                    }
                    if (isGranitePartial(material) && Boolean.TRUE.equals(material.getProperty(WATERLOGGED))) {
                        partials.add(voxel);
                        final Material support = river.material(x, y - 1, z);
                        if (!support.solid || SurfaceSmoother.isSmoothedSurfacePartial(support)) unsupportedPartials++;
                    }
                }
            }
        }
        // Necessary voxel-connectivity check, not a substitute for Minecraft fluid
        // ticks or precise collision/face occlusion of every stair shape.
        while (!queue.isEmpty()) {
            final Voxel voxel = queue.remove();
            for (int[] offset : FLUID_NEIGHBOURS) {
                final Voxel adjacent = new Voxel(voxel.x + offset[0], voxel.y + offset[1], voxel.z + offset[2]);
                if (fluid.contains(adjacent) && visited.add(adjacent)) queue.add(adjacent);
            }
        }
        final long disconnected = partials.stream().filter(voxel -> !visited.contains(voxel)).count();
        System.out.println("River export waterlogged granite partials=" + partials.size()
                + ", unsupported=" + unsupportedPartials + ", disconnected from full water=" + disconnected);
        assertFalse("The reference-style river must actually export granite partial details", partials.isEmpty());
        assertEquals("No hovering granite shelf may be used to hold water", 0, unsupportedPartials);
        assertEquals("Random waterlogged islands are not a continuous shoreline", 0, disconnected);
    }

    @Test
    public void straightRiverHasNoLateralAirOpeningBesideExportedFluid() {
        for (boolean slope : new boolean[] { false, true }) {
            final ExportedRiver river = exportRiver(5, 12, 1.10, slope);
            int lateralAirOpenings = 0;
            for (int x = 24; x <= 104; x++) {
                for (int z = 48; z < 80; z++) {
                    for (int y = 94; y <= 102; y++) {
                        if (!containsFluid(river.material(x, y, z))) continue;
                        if (river.material(x, y, z - 1).empty) lateralAirOpenings++;
                        if (river.material(x, y, z + 1).empty) lateralAirOpenings++;
                    }
                }
            }
            // Ignore longitudinal steps: a downhill rapid intentionally faces an
            // air/water cell along X. An exposed +/-Z side is an unintended bank opening.
            System.out.println("River export lateral air openings: slope=" + slope + ", count=" + lateralAirOpenings);
            assertEquals("Channel fluid must be contained laterally by a bank or another fluid voxel", 0, lateralAirOpenings);
        }
    }

    @Test
    public void riverMouthExportsAtExistingSeaSurfaceWithoutSunkenWaterOrChangedSeaBed() {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 128, 128), 101);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                if (x >= 98) {
                    dimension.setHeightAt(x, z, 98);
                    dimension.setWaterLevelAt(x, z, 100);
                    dimension.setTerrainAt(x, z, Terrain.SAND);
                } else {
                    final float valleyHeight = x < 60 ? 100.75f - (x - 20) * .02f
                            : x < 86 ? 99.45f : 99.70f;
                    dimension.setHeightAt(x, z, Math.abs(z - 64) <= 2
                            ? valleyHeight : Math.max(100.25f, valleyHeight));
                }
            }
        }
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension, 5, 12, 1.10,
                true, true, 1337, null);
        assertTrue(carver.getLastRejection(), carver.addPath(new int[] {20, 108}, new int[] {64, 64}));
        carver.apply();
        final ExportedRiver river = exportDimension(dimension);
        for (int x = 86; x <= 105; x++) {
            for (int z = 63; z <= 65; z++) {
                assertEquals("Outlet must meet the real sea level", 100, dimension.getWaterLevelAt(x, z));
                assertTrue("No sunken mouth at " + x + "," + z, isFullWater(river.material(x, 100, z)));
                assertFalse("No raised water at the mouth", containsFluid(river.material(x, 101, z)));
                if (x >= 98) {
                    assertEquals(98f, dimension.getHeightAt(x, z), 0f);
                    assertEquals(Terrain.SAND, dimension.getTerrainAt(x, z));
                    assertEquals(MC_SAND, river.material(x, 98, z).name);
                }
            }
        }
    }

    @Test
    public void adaptiveCutAndFillExportShallowContinuousWaterAndDryNaturalShoulders() {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        for (int z=0; z<128; z++) {
            dimension.setHeightAt(63,z,103);
            dimension.setHeightAt(64,z,103);
        }
        for (int x=48; x<=50; x++) for (int z=63; z<=65; z++) dimension.setHeightAt(x,z,98.5f);
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,5,12,1.10,true,true,1337,null);
        carver.enableTerrainAdaptation();
        final boolean accepted = carver.addPath(new int[] {20,108}, new int[] {64,64});
        assertTrue(carver.getLastRejection(),accepted);
        final ShallowRiverCarver.Result result = carver.apply();
        assertTrue(result.raisedCells()>0);
        assertTrue(result.maximumCut()>1.85);
        final ExportedRiver river = exportDimension(dimension);
        for (int x=24; x<=104; x++) {
            final int waterY=dimension.getWaterLevelAt(x,64);
            for (int z=63; z<=65; z++) {
                assertTrue("Continuous water after cut/fill",isFullWater(river.material(x,waterY,z)));
                assertFalse("No high floating water",containsFluid(river.material(x,waterY+1,z)));
                assertFalse("No deep water trench",isFullWater(river.material(x,waterY-2,z)));
            }
            for (int z : new int[] {44,84}) {
                assertEquals("Outside the local shoulder stays untouched",x==63 || x==64 ? 103f : 100f,
                        dimension.getHeightAt(x,z),0);
                assertFalse(containsFluid(river.material(x,dimension.getIntHeightAt(x,z),z)));
            }
        }
    }

    @Test
    public void adaptiveDownhillCrossSlopeKeepsDryBanksSolidAtTheWaterSurface() {
        assertAdaptiveDownhillBanks(Terrain.GRASS);
    }

    @Test
    public void adaptiveRockyDownhillCrossSlopeKeepsSmoothedBanksWatertight() {
        assertAdaptiveDownhillBanks(Terrain.GRANITE);
    }

    private static void assertAdaptiveDownhillBanks(Terrain terrain) {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        for (int x=0; x<128; x++) for (int z=0; z<128; z++) {
            final double crossSlope=0.09*(z-64)*Math.min(1,Math.max(0,(100-x)/8.0));
            dimension.setHeightAt(x,z,(float) (101 - 0.10 * Math.min(x,92) + crossSlope));
            dimension.setWaterLevelAt(x,z,0);
            dimension.setTerrainAt(x,z,terrain);
        }
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension,5,12,1.10,true,true,1337,null);
        carver.enableTerrainAdaptation();
        final boolean accepted=carver.addPath(new int[] {20,108},new int[] {64,64});
        assertTrue(carver.getLastRejection(),accepted);
        final ShallowRiverCarver.Result result=carver.apply();
        assertTrue(result.maximumFill()<=2.0);
        final ExportedRiver river=exportDimension(dimension);
        int checkedDryBanks=0;
        for (int x=24; x<=104; x++) {
            for (int z=63; z<=65; z++) {
                final int water=dimension.getWaterLevelAt(x,z);
                assertTrue("Three full water columns through each downstep",isFullWater(river.material(x,water,z)));
                assertFalse("Shallow depth maintained through downsteps",isFullWater(river.material(x,water-2,z)));
            }
            for (int z=48; z<=80; z++) {
                final int water=dimension.getWaterLevelAt(x,z);
                if (water<=dimension.getIntHeightAt(x,z)) continue;
                for (int[] offset : new int[][] {{-1,0},{1,0},{0,-1},{0,1}}) {
                    final int nx=x+offset[0], nz=z+offset[1];
                    if (dimension.getWaterLevelAt(nx,nz)>dimension.getIntHeightAt(nx,nz)) continue;
                    final Material bank=river.material(nx,water,nz);
                    assertTrue("Dry bank must contain water at "+nx+","+water+","+nz+": "+bank,
                            bank.solid && !bank.empty && !SurfaceSmoother.isSmoothedSurfacePartial(bank));
                    assertFalse("Dry banks must not create separate water sources",containsFluid(bank));
                    checkedDryBanks++;
                }
            }
        }
        assertTrue("Exercise real banks, not only the centre",checkedDryBanks>30);
    }

    private static ExportedRiver exportRiver(double startWidth, double endWidth, double depth, boolean slope) {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        dimension.setSurfaceSmoothing(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS);
        if (slope) {
            for (int x = 0; x < 128; x++) {
                for (int z = 0; z < 128; z++) dimension.setHeightAt(x, z, 102f - x / 32f);
            }
        }
        final ShallowRiverCarver carver = new ShallowRiverCarver(dimension, startWidth, endWidth, depth,
                true, true, 1337, null);
        final boolean accepted = carver.addPath(new int[] { 20, 108 }, new int[] { 64, 64 });
        assertTrue(carver.getLastRejection(), accepted);
        carver.apply();
        return exportDimension(dimension);
    }

    private static ExportedRiver exportDimension(Dimension dimension) {
        final WorldPainterChunkFactory factory = new WorldPainterChunkFactory(dimension, Collections.emptyMap(),
                TestData.PLATFORM, TestData.MAX_HEIGHT);
        final Map<ChunkCoordinate, Chunk> chunks = new HashMap<>();
        for (int chunkX = 1; chunkX <= 6; chunkX++) {
            for (int chunkZ = 2; chunkZ <= 5; chunkZ++) {
                chunks.put(new ChunkCoordinate(chunkX, chunkZ), factory.createChunk(chunkX, chunkZ).chunk);
            }
        }
        return new ExportedRiver(dimension, chunks);
    }

    private static boolean isFullWater(Material material) { return MC_WATER.equals(material.name); }

    private static boolean containsFluid(Material material) {
        return isFullWater(material) || (material.hasProperty(WATERLOGGED)
                && Boolean.TRUE.equals(material.getProperty(WATERLOGGED)));
    }

    private static boolean isGranitePartial(Material material) {
        return "minecraft:granite_slab".equals(material.name) || "minecraft:granite_stairs".equals(material.name);
    }

    private static final int[][] FLUID_NEIGHBOURS = { {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1} };

    private record Voxel(int x, int y, int z) {}
    private record ChunkCoordinate(int x, int z) {}
    private record ExportedRiver(Dimension dimension, Map<ChunkCoordinate, Chunk> chunks) {
        Material material(int x, int y, int z) {
            final Chunk chunk = chunks.get(new ChunkCoordinate(x >> 4, z >> 4));
            if (chunk == null) throw new IllegalArgumentException("Test export does not cover " + x + "," + z);
            return chunk.getMaterial(x & 15, y, z & 15);
        }
    }

    private static int countWaterBlocks(Chunk chunk) {
        int count = 0;
        for (int y = TestData.MIN_HEIGHT; y < TestData.MAX_HEIGHT; y++) {
            if (MC_WATER.equals(chunk.getMaterial(X, y, Z).name)) {
                count++;
            }
        }
        return count;
    }

    private static void assertWaterloggedStair(Material material, Direction direction) {
        assertEquals("minecraft:granite_stairs", material.name);
        assertEquals("bottom", material.getProperty(HALF));
        assertEquals("straight", material.getProperty(SHAPE));
        assertEquals(direction, material.getProperty(FACING));
        assertTrue(material.getProperty(WATERLOGGED));
    }

    private static Fixture create(Terrain terrain, float h00, float h10, float h01, float h11,
                                  int waterLevel, boolean smoothing) {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 16, 16), 64);
        dimension.setSurfaceSmoothing(smoothing
                ? Dimension.SurfaceSmoothing.SLABS_AND_STAIRS : Dimension.SurfaceSmoothing.NONE);
        final Tile tile = dimension.getTile(0, 0);
        final float[][] heights = { { h00, h10 }, { h01, h11 } };
        // Sample an interior granite surface rather than a dry grass seam.
        // The new seam guard intentionally leaves such boundaries full height.
        for (int dz = -1; dz < 2; dz++) {
            for (int dx = -1; dx < 2; dx++) {
                tile.setHeight(X + dx, Z + dz, heights[Math.max(0, dz)][Math.max(0, dx)]);
                tile.setTerrain(X + dx, Z + dz, terrain);
                tile.setWaterLevel(X + dx, Z + dz, waterLevel);
            }
        }
        final WorldPainterChunkFactory factory = new WorldPainterChunkFactory(dimension, Collections.emptyMap(),
                TestData.PLATFORM, TestData.MAX_HEIGHT);
        final Chunk chunk = factory.createChunk(0, 0).chunk;
        return new Fixture(dimension, chunk);
    }

    private static final int X = 8, Z = 8;

    private record Fixture(Dimension dimension, Chunk chunk) {
    }
}
