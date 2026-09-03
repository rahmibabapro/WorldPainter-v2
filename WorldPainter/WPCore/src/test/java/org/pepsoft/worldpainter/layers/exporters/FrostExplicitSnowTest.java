package org.pepsoft.worldpainter.layers.exporters;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.Box;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.exporting.MinecraftWorld;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.SnowDepth;
import org.pepsoft.worldpainter.layers.renderers.NibbleLayerRenderer;
import org.pepsoft.worldpainter.objects.MinecraftWorldObject;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Material.*;

public class FrostExplicitSnowTest {
    @Test
    public void exportsOneFourAndEightLayersWithoutChangingStoneOrCreatingIce() {
        final Fixture fixture = fixture(3, 180);
        final int[] depths = {1, 4, 8};
        for (int x = 0; x < depths.length; x++) {
            setSnow(fixture.dimension, x, depths[x], true);
        }
        export(fixture, new FrostExporter.FrostSettings());
        for (int x = 0; x < depths.length; x++) {
            assertEquals(STONE, fixture.world.getMaterialAt(x, 0, 180));
            assertEquals(SNOW.withProperty(LAYERS, depths[x]), fixture.world.getMaterialAt(x, 0, 181));
            assertEquals("minecraft:snow", fixture.world.getMaterialAt(x, 0, 181).name);
            assertEquals(AIR, fixture.world.getMaterialAt(x, 0, 182));
        }
        assertNoSnowBlocksOrIce(fixture);
    }

    @Test
    public void explicitDepthIsAuthoritativeEvenOverThickerExistingSnow() {
        final Fixture fixture = fixture(1, 180);
        setSnow(fixture.dimension, 0, 1, true);
        fixture.world.setMaterialAt(0, 0, 181, SNOW.withProperty(LAYERS, 8));
        export(fixture, new FrostExporter.FrostSettings());
        assertEquals(SNOW.withProperty(LAYERS, 1), fixture.world.getMaterialAt(0, 0, 181));
    }

    @Test
    public void explicitLayersSitAboveMeasuredWhiteBlueprintMaterials() {
        final Fixture fixture = fixture(3, 180);
        final Material birch = Material.get("minecraft:birch_wood", "axis", "y");
        final Material[] bases = {birch, DIORITE, SNOW_BLOCK};
        final int[] depths = {2, 5, 8};
        for (int x = 0; x < bases.length; x++) {
            fixture.world.setMaterialAt(x, 0, 180, bases[x]);
            setSnow(fixture.dimension, x, depths[x], true);
        }
        export(fixture, new FrostExporter.FrostSettings());
        for (int x = 0; x < bases.length; x++) {
            assertEquals(bases[x], fixture.world.getMaterialAt(x, 0, 180));
            assertEquals(SNOW.withProperty(LAYERS, depths[x]), fixture.world.getMaterialAt(x, 0, 181));
        }
    }

    @Test
    public void waterLavaWaterloggedBlocksAndShorelineRemainProtected() {
        final Fixture fixture = fixture(4, 180);
        for (int x = 0; x < 4; x++) {
            setSnow(fixture.dimension, x, 8, true);
        }
        fixture.world.setMaterialAt(0, 0, 181, STATIONARY_WATER);
        fixture.world.setMaterialAt(1, 0, 181, STATIONARY_LAVA);
        fixture.dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, 1, 0, true);
        final Material wetSupport = Material.get("minecraft:stone_stairs")
                .withProperty("half", "top").withProperty("waterlogged", "true");
        fixture.world.setMaterialAt(2, 0, 180, wetSupport);
        fixture.dimension.setWaterLevelAt(3, 0, 180);
        export(fixture, new FrostExporter.FrostSettings());
        assertEquals(STATIONARY_WATER, fixture.world.getMaterialAt(0, 0, 181));
        assertEquals(STATIONARY_LAVA, fixture.world.getMaterialAt(1, 0, 181));
        assertEquals(wetSupport, fixture.world.getMaterialAt(2, 0, 180));
        assertEquals(AIR, fixture.world.getMaterialAt(2, 0, 181));
        assertEquals(AIR, fixture.world.getMaterialAt(3, 0, 181));
        assertNoSnowBlocksOrIce(fixture);
    }

    @Test
    public void explicitDepthRequiresPaintedFrost() {
        final Fixture fixture = fixture(1, 180);
        setSnow(fixture.dimension, 0, 8, false);
        export(fixture, new FrostExporter.FrostSettings());
        assertEquals(AIR, fixture.world.getMaterialAt(0, 0, 181));
    }

    @Test
    public void snowStaysAtTerrainTopAndDoesNotPaintOrReplaceTreeCanopies() {
        final Fixture fixture = fixture(3, 180);
        for (int x = 0; x < 3; x++) {
            setSnow(fixture.dimension, x, 4, true);
        }
        fixture.world.setMaterialAt(0, 0, 185, LEAVES_OAK);
        fixture.world.setMaterialAt(1, 0, 181, LEAVES_OAK);
        fixture.world.setMaterialAt(2, 0, 180, LEAVES_OAK);
        export(fixture, new FrostExporter.FrostSettings());
        assertEquals(SNOW.withProperty(LAYERS, 4), fixture.world.getMaterialAt(0, 0, 181));
        assertEquals(LEAVES_OAK, fixture.world.getMaterialAt(0, 0, 185));
        assertEquals(AIR, fixture.world.getMaterialAt(0, 0, 186));
        assertEquals(LEAVES_OAK, fixture.world.getMaterialAt(1, 0, 181));
        assertEquals(AIR, fixture.world.getMaterialAt(1, 0, 182));
        assertEquals(AIR, fixture.world.getMaterialAt(2, 0, 181));
    }

    @Test
    public void zeroDepthRetainsLegacyFreezingThinSnowAndCanopyBehaviour() {
        final Fixture fixture = fixture(3, 180);
        for (int x = 0; x < 3; x++) {
            setSnow(fixture.dimension, x, 0, true);
        }
        fixture.world.setMaterialAt(1, 0, 181, STATIONARY_WATER);
        fixture.world.setMaterialAt(2, 0, 185, LEAVES_OAK);
        final FrostExporter.FrostSettings settings = new FrostExporter.FrostSettings();
        settings.setMode(FrostExporter.FrostSettings.MODE_FLAT);
        export(fixture, settings);
        assertEquals(SNOW.withProperty(LAYERS, 1), fixture.world.getMaterialAt(0, 0, 181));
        assertEquals(ICE, fixture.world.getMaterialAt(1, 0, 181));
        assertEquals(SNOW, fixture.world.getMaterialAt(2, 0, 186));
    }

    @Test
    public void globalFrostWithoutPaintIgnoresExplicitDepthAndUsesLegacySettings() {
        final Fixture fixture = fixture(1, 180);
        setSnow(fixture.dimension, 0, 8, false);
        final FrostExporter.FrostSettings settings = new FrostExporter.FrostSettings();
        settings.setFrostEverywhere(true);
        settings.setMode(FrostExporter.FrostSettings.MODE_FLAT);
        export(fixture, settings);
        assertEquals(SNOW.withProperty(LAYERS, 1), fixture.world.getMaterialAt(0, 0, 181));
    }

    @Test
    public void checksActualSupportingBlockAndWorldHeightBounds() {
        final Fixture fixture = fixture(4, 180);
        for (int x = 0; x < 4; x++) {
            setSnow(fixture.dimension, x, 8, true);
        }
        fixture.world.setMaterialAt(0, 0, 180, AIR);
        fixture.dimension.setHeightAt(1, 0, TestData.MAX_HEIGHT - 1);
        fixture.world.setMaterialAt(1, 0, TestData.MAX_HEIGHT - 1, STONE);
        fixture.dimension.setHeightAt(2, 0, TestData.MAX_HEIGHT - 2);
        fixture.world.setMaterialAt(2, 0, TestData.MAX_HEIGHT - 2, STONE);
        fixture.dimension.setHeightAt(3, 0, TestData.MIN_HEIGHT);
        fixture.dimension.setWaterLevelAt(3, 0, TestData.MIN_HEIGHT);
        fixture.world.setMaterialAt(3, 0, TestData.MIN_HEIGHT, STONE);
        export(fixture, new FrostExporter.FrostSettings());
        assertEquals(AIR, fixture.world.getMaterialAt(0, 0, 181));
        assertEquals(STONE, fixture.world.getMaterialAt(1, 0, TestData.MAX_HEIGHT - 1));
        assertEquals(SNOW.withProperty(LAYERS, 8), fixture.world.getMaterialAt(2, 0, TestData.MAX_HEIGHT - 1));
        assertEquals(AIR, fixture.world.getMaterialAt(3, 0, TestData.MIN_HEIGHT + 1));
    }

    @Test
    public void snowDepthIsInvisibleAndKeepsSingletonAcrossSerialization() throws Exception {
        assertEquals(Layer.DataSize.NIBBLE, SnowDepth.INSTANCE.getDataSize());
        assertEquals(0, SnowDepth.INSTANCE.getDefaultValue());
        final NibbleLayerRenderer renderer = (NibbleLayerRenderer) SnowDepth.INSTANCE.getRenderer();
        assertNotNull(renderer);
        assertEquals(0x7391ba, renderer.getPixelColour(12, 34, 0x7391ba, 8));
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(SnowDepth.INSTANCE);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            assertSame(SnowDepth.INSTANCE, input.readObject());
        }
    }

    private static Fixture fixture(int width, int height) {
        final Rectangle area = new Rectangle(0, 0, width, 1);
        final Dimension dimension = TestData.createDimension(area, height);
        final MinecraftWorldObject backing = new MinecraftWorldObject("Explicit snow test",
                new Box(0, width, 0, 1, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT), TestData.MAX_HEIGHT, 0);
        final MinecraftWorld world = (MinecraftWorld) Proxy.newProxyInstance(MinecraftWorld.class.getClassLoader(),
                new Class<?>[] {MinecraftWorld.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getMinHeight")) {
                        return TestData.MIN_HEIGHT;
                    }
                    if (method.getName().equals("setMaterialAt")) {
                        final int z = (Integer) arguments[2];
                        assertTrue("Export wrote below world minimum", z >= TestData.MIN_HEIGHT);
                        assertTrue("Export wrote beyond world maximum", z < TestData.MAX_HEIGHT);
                    }
                    return method.invoke(backing, arguments);
                });
        for (int x = 0; x < width; x++) {
            world.setMaterialAt(x, 0, height, STONE);
        }
        return new Fixture(dimension, world, area);
    }

    private static void setSnow(Dimension dimension, int x, int depth, boolean frost) {
        dimension.setLayerValueAt(SnowDepth.INSTANCE, x, 0, depth);
        dimension.setBitLayerValueAt(Frost.INSTANCE, x, 0, frost);
    }

    private static void export(Fixture fixture, FrostExporter.FrostSettings settings) {
        new FrostExporter(fixture.dimension, TestData.PLATFORM, settings)
                .addFeatures(fixture.area, fixture.area, fixture.world);
    }

    private static void assertNoSnowBlocksOrIce(Fixture fixture) {
        for (int x = 0; x < fixture.area.width; x++) {
            for (int z = TestData.MIN_HEIGHT; z < TestData.MAX_HEIGHT; z++) {
                final Material material = fixture.world.getMaterialAt(x, 0, z);
                assertNotEquals("minecraft:snow_block", material.name);
                assertNotEquals("minecraft:ice", material.name);
            }
        }
    }

    private record Fixture(Dimension dimension, MinecraftWorld world, Rectangle area) {
    }
}
