package org.pepsoft.worldpainter.tools;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.util.undo.UndoManager;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.SnowDepth;
import org.pepsoft.worldpainter.layers.exporters.FrostExporter.FrostSettings;
import org.pepsoft.worldpainter.objects.GenericObject;
import org.pepsoft.worldpainter.tools.scripts.AxiomTextureProfile;

import static org.junit.Assert.*;

public class AxiomBlueprintTextureOpTest {
    @Test public void roughSummitNeverLeavesWhiteTerrainWithoutSnow() throws Exception {
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)
            dimension.setHeightAt(x,y,(float)(190+Math.sin(x*Math.PI/4)));
        AxiomBlueprintTextureOp.applyProfiles(world,dimension,profile,null,false,null,System.nanoTime());
        int white=0;
        for(int y=8;y<120;y++)for(int x=8;x<120;x++) {
            float h=dimension.getHeightAt(x,y);
            var material=dimension.getTerrainAt(x,y).getMaterial(world.getPlatform(),0L,x,y,h,Math.round(h));
            if(material.name.equals("minecraft:birch_wood")) {
                white++;
                assertTrue("Missing snow over white at "+x+","+y,dimension.getBitLayerValueAt(Frost.INSTANCE,x,y));
                assertTrue(dimension.getLayerValueAt(SnowDepth.INSTANCE,x,y)>0);
            }
        }
        assertTrue(white>0);
    }
    @Test public void exactSnowLineAndRejectedSlopeUseTheRealCombinedOperation() throws Exception {
        // Large high plateau + low bench + steep face so dry p80 lands on the plateau (not vanilla 150).
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)
            dimension.setHeightAt(x,y,x<40?90:x<100?130:
                    (float)(130+(x-100)*Math.tan(Math.toRadians(31))));
        final org.pepsoft.worldpainter.tools.scripts.WorldHeightBands bands =
                org.pepsoft.worldpainter.tools.scripts.WorldHeightBands.from(dimension);
        assertTrue("Expected snow line on the plateau, got " + bands.snowLine(),
                bands.snowLine() >= 120f && bands.snowLine() <= 130.01f);
        AxiomBlueprintTextureOp.applyProfiles(world,dimension,profile,null,false,null,System.nanoTime());
        boolean whiteAtBoundary=false;
        for(int y=8;y<120;y++)for(int x=8;x<120;x++) {
            if(x>=36&&x<44||x>=96&&x<104)continue;
            float h=dimension.getHeightAt(x,y);
            var material=dimension.getTerrainAt(x,y).getMaterial(world.getPlatform(),0L,x,y,h,Math.round(h));
            if(x<36) {
                assertNotEquals("minecraft:birch_wood",material.name);
                assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE,x,y));
                assertEquals(0,dimension.getLayerValueAt(SnowDepth.INSTANCE,x,y));
            } else if(x<96&&material.name.equals("minecraft:birch_wood")) {
                whiteAtBoundary=true;
                assertTrue(dimension.getBitLayerValueAt(Frost.INSTANCE,x,y));
                assertTrue(dimension.getLayerValueAt(SnowDepth.INSTANCE,x,y)>0);
            } else if(x>=104) {
                assertSame(Terrain.STONE,dimension.getTerrainAt(x,y));
                assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE,x,y));
                assertEquals(0,dimension.getLayerValueAt(SnowDepth.INSTANCE,x,y));
            }
        }
        assertTrue("White terrain must be possible at the world snow line",whiteAtBoundary);
    }
    @Before
    public void setUp() throws Exception {
        for (int i = 0; i < saved.length; i++) {
            saved[i] = Terrain.getCustomMaterial(i);
            Terrain.setCustomMaterial(i, null);
        }
        world = new World2(DefaultPlugin.JAVA_ANVIL_1_18, -64, 320);
        final TileFactory factory = TileFactoryFactory.createFlatTileFactory(0, Terrain.GRASS, -64, 320, 210, 0, false, false);
        dimension = new Dimension(world, "Surface", 0, factory, Dimension.Anchor.NORMAL_DETAIL);
        final Tile tile = factory.createTile(0, 0);
        // The default theme paints high tiles with Deep Snow; this fixture deliberately
        // starts with plain grass so rollback is compared with the actual initial data.
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                tile.setTerrain(x, y, Terrain.GRASS);
                tile.setBitLayerValue(Frost.INSTANCE, x, y, false);
            }
        }
        dimension.addTile(tile);
        undo = new UndoManager(10);
        dimension.registerUndoManager(undo);
        frostBefore = new FrostSettings();
        frostBefore.setMode(FrostSettings.MODE_FLAT);
        dimension.setLayerSettings(Frost.INSTANCE, frostBefore);
        final int size = 32;
        final Material[] blocks = new Material[size * size * 10];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                blocks[x + (y + 8 * size) * size] = Material.STONE;
                blocks[x + (y + 9 * size) * size] = ((x / 4) & 1) == 0
                        ? Material.get("minecraft:birch_wood", "axis", "y") : Material.get("minecraft:acacia_wood", "axis", "y");
            }
        }
        profile = AxiomTextureProfile.analyze(new GenericObject("snow-mask", size, size, 10, blocks), null);
        final Material[] plainsBlocks = new Material[size * size * 10];
        final Material grass = Material.get("minecraft:grass_block", "snowy", "false");
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                plainsBlocks[x + (y + 8 * size) * size] = Material.STONE;
                plainsBlocks[x + (y + 9 * size) * size] = grass;
            }
        }
        plainsProfile = AxiomTextureProfile.analyze(new GenericObject("rolling-plains", size, size, 10, plainsBlocks), null);
        final Material[] mixedPlainsBlocks = new Material[size * size * 10];
        final Material paleMoss = Material.get("minecraft:pale_moss_block");
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                mixedPlainsBlocks[x + (y + 8 * size) * size] = Material.STONE;
                mixedPlainsBlocks[x + (y + 9 * size) * size] = ((x + y) & 3) == 0 ? paleMoss : grass;
            }
        }
        mixedPlainsProfile = AxiomTextureProfile.analyze(new GenericObject("native-mixed-plains", size, size, 10,
                mixedPlainsBlocks), null);
    }

    @After
    public void tearDown() {
        for (int i = 0; i < saved.length; i++) {
            Terrain.setCustomMaterial(i, saved[i]);
        }
    }

    @Test
    public void oneUndoRestoresTerrainAndBothSnowLayers() throws Exception {
        final AxiomBlueprintTextureOp.Result result = AxiomBlueprintTextureOp.applyProfile(world, dimension, profile,
                false, null, System.nanoTime());
        assertTrue(result.snow().snowCovered() > 0);
        boolean sawBirchTerrain = false, sawLayeredSnow = false;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                final Material material = dimension.getTerrainAt(x, y).getMaterial(world.getPlatform(), 0, x, y, 210, 210);
                sawBirchTerrain |= material.name.equals("minecraft:birch_wood");
                sawLayeredSnow |= dimension.getBitLayerValueAt(Frost.INSTANCE, x, y)
                        && dimension.getLayerValueAt(SnowDepth.INSTANCE, x, y) > 0;
                assertEquals(210, dimension.getHeightAt(x, y), 0);
            }
        }
        assertTrue("Measured birch surface should remain beneath the snow layer", sawBirchTerrain);
        assertTrue("White source mask should still produce explicit snow layers", sawLayeredSnow);
        assertTrue(undo.undo());
        assertOriginalCells();
    }

    @Test
    public void profileEntryRejectsWrongWorldAndMissingUndoBeforeAllocatingTerrains() throws Exception {
        final World2 unrelated = new World2(DefaultPlugin.JAVA_ANVIL_1_18, -64, 320);
        assertThrows(IllegalArgumentException.class, () -> AxiomBlueprintTextureOp.applyProfile(unrelated,
                dimension, profile, false, null, System.nanoTime()));
        dimension.unregisterUndoManager();
        assertThrows(IllegalStateException.class, () -> AxiomBlueprintTextureOp.applyProfile(world,
                dimension, profile, false, null, System.nanoTime()));
        assertOriginalCells();
        for (int i = 0; i < saved.length; i++) {
            assertNull(world.getMixedMaterial(i));
            assertNull(Terrain.getCustomMaterial(i));
        }
    }

    @Test
    public void scriptEntryMayRunInsideAnAlreadyInhibitedDimension() throws Exception {
        dimension.setEventsInhibited(true);
        try {
            AxiomBlueprintTextureOp.applyProfile(world, dimension, profile, false, null, System.nanoTime());
            assertTrue(dimension.isEventsInhibited());
        } finally {
            dimension.setEventsInhibited(false);
        }
    }

    @Test
    public void plainsProfileOverlaysGentleSlopesAtAnyHeightAndStillUsesOneUndo() throws Exception {
        // Flat land gets grass by slope; a 45-degree ramp must keep mountain/rock treatment.
        for (int y = 16; y < 112; y++) {
            for (int x = 16; x < 48; x++) {
                dimension.setHeightAt(x, y, 100);
            }
            for (int x = 64; x < 96; x++) {
                dimension.setHeightAt(x, y, 100 + x - 64);
            }
        }
        final AxiomBlueprintTextureOp.Result result = AxiomBlueprintTextureOp.applyProfiles(world, dimension, profile,
                plainsProfile, false, null, System.nanoTime());
        assertNotNull(result.plainsTransfer());

        final Material flat = dimension.getTerrainAt(32, 64).getMaterial(world.getPlatform(), 0, 32, 64, 100, 100);
        final Material steep = dimension.getTerrainAt(80, 64).getMaterial(world.getPlatform(), 0, 80, 64, 116, 116);
        assertEquals("minecraft:grass_block", flat.name);
        assertNotEquals("A 45-degree ramp must not receive plains texture", "minecraft:grass_block", steep.name);
        assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, 32, 64));
        assertEquals(0, dimension.getLayerValueAt(SnowDepth.INSTANCE, 32, 64));
        assertTrue(undo.undo());
        assertOriginalCells();
    }

    @Test
    public void flatPlainsUseOnlyTheMeasuredGrassState() throws Exception {
        for (int y = 16; y < 112; y++) {
            for (int x = 16; x < 48; x++) {
                dimension.setHeightAt(x, y, 100);
            }
        }
        final AxiomBlueprintTextureOp.Result result = AxiomBlueprintTextureOp.applyProfiles(world, dimension, profile,
                mixedPlainsProfile, false, null, System.nanoTime());
        final Terrain plainsTerrain = result.terrains().getMixedTerrain();
        assertNotNull(plainsTerrain);
        assertSame(plainsTerrain, dimension.getTerrainAt(32, 64));
        final MixedMaterial mix = world.getMixedMaterial(plainsTerrain.getCustomTerrainIndex());
        assertEquals(MixedMaterial.Mode.SIMPLE, mix.getMode());
        assertEquals("minecraft:grass_block", mix.getSingleMaterial().name);
        assertEquals(1, mix.getRows().length);
        assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, 32, 64));
        assertEquals(0, dimension.getLayerValueAt(SnowDepth.INSTANCE, 32, 64));
        assertTrue(undo.undo());
        assertOriginalCells();
    }

    @Test
    public void steepTerrainIsStoneAndWhiteSummitsAreSnowyOnlyFromWorldSnowLine() throws Exception {
        for (int y = 16; y < 112; y++) {
            for (int x = 16; x < 40; x++) {
                dimension.setHeightAt(x, y, 90);
            }
            for (int x = 40; x < 100; x++) {
                dimension.setHeightAt(x, y, 130);
            }
            for (int x = 100; x < 112; x++) {
                dimension.setHeightAt(x, y, 60 + (x - 80) * 2);
            }
        }
        // Fill the rest so sampling is dominated by the 90/130 relief above.
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                if (y >= 16 && y < 112 && x >= 16 && x < 112) continue;
                dimension.setHeightAt(x, y, 90);
            }
        }
        AxiomBlueprintTextureOp.applyProfiles(world, dimension, profile, null, false, null, System.nanoTime());

        boolean sawSnowyWhite = false;
        for (int y = 24; y < 104; y++) {
            for (int x = 20; x < 36; x++) {
                final Material material = dimension.getTerrainAt(x, y).getMaterial(world.getPlatform(), 0L, x, y, 90, 90);
                assertNotEquals("minecraft:birch_wood", material.name);
                assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, x, y));
                assertEquals(0, dimension.getLayerValueAt(SnowDepth.INSTANCE, x, y));
            }
            for (int x = 56; x < 88; x++) {
                final Material material = dimension.getTerrainAt(x, y).getMaterial(world.getPlatform(), 0L, x, y, 130, 130);
                if (material.name.equals("minecraft:birch_wood")) {
                    sawSnowyWhite = true;
                    assertTrue(dimension.getBitLayerValueAt(Frost.INSTANCE, x, y));
                    assertTrue(dimension.getLayerValueAt(SnowDepth.INSTANCE, x, y) >= 1);
                }
            }
            for (int x = 104; x < 110; x++) {
                assertSame(Terrain.STONE, dimension.getTerrainAt(x, y));
                assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, x, y));
                assertEquals(0, dimension.getLayerValueAt(SnowDepth.INSTANCE, x, y));
            }
        }
        assertTrue("A valid flat summit should retain at least some source-white terrain", sawSnowyWhite);
        assertTrue(undo.undo());
        assertOriginalCells();
    }

    @Test
    public void cancellingDuringSnowRollsBackCellsDefinitionsAndFrostSettings() throws Exception {
        final ProgressReceiver progress = new ProgressReceiver() {
            @Override public void setProgress(float value) throws OperationCancelled {
                if (value > 0.90f) throw new OperationCancelled("cancel during snow");
            }
            @Override public void checkForCancellation() { }
            @Override public void setMessage(String message) { }
            @Override public void reset() { }
            @Override public void done() { }
            @Override public void exceptionThrown(Throwable t) { }
            @Override public void subProgressStarted(SubProgressReceiver subProgressReceiver) { }
        };
        try {
            AxiomBlueprintTextureOp.applyProfiles(world, dimension, profile, plainsProfile, false, progress, System.nanoTime());
            fail("Expected cancellation");
        } catch (ProgressReceiver.OperationCancelled expected) {
            assertOriginalCells();
            assertSame(frostBefore, dimension.getLayerSettings(Frost.INSTANCE));
            assertFalse(dimension.isEventsInhibited());
            for (int i = 0; i < saved.length; i++) {
                assertNull(world.getMixedMaterial(i));
                assertNull(Terrain.getCustomMaterial(i));
            }
        }
    }

    private void assertOriginalCells() {
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                assertSame(Terrain.GRASS, dimension.getTerrainAt(x, y));
                assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, x, y));
                assertEquals(0, dimension.getLayerValueAt(SnowDepth.INSTANCE, x, y));
            }
        }
    }

    private final MixedMaterial[] saved = new MixedMaterial[Terrain.CUSTOM_TERRAIN_COUNT];
    private World2 world;
    private Dimension dimension;
    private UndoManager undo;
    private FrostSettings frostBefore;
    private AxiomTextureProfile profile;
    private AxiomTextureProfile plainsProfile;
    private AxiomTextureProfile mixedPlainsProfile;
}
