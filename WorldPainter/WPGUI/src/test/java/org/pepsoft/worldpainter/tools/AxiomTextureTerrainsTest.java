package org.pepsoft.worldpainter.tools;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.DefaultPlugin;
import org.pepsoft.worldpainter.MixedMaterial;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.World2;

import java.util.List;

import static org.junit.Assert.*;

public class AxiomTextureTerrainsTest {
    @Before
    public void savePalette() {
        for (int index = 0; index < originalPalette.length; index++) {
            originalPalette[index] = Terrain.getCustomMaterial(index);
            Terrain.setCustomMaterial(index, null);
        }
        world = new World2(DefaultPlugin.JAVA_ANVIL_1_18, -64, 320);
    }

    @After
    public void restorePalette() {
        for (int index = 0; index < originalPalette.length; index++) {
            Terrain.setCustomMaterial(index, originalPalette[index]);
        }
    }

    @Test
    public void preservesExactStatesBasaltAndBareGrassWithoutPreparationMutation() {
        final List<Material> materials = List.of(Material.get("minecraft:basalt").withProperty("axis", "x"),
                Material.get("minecraft:basalt").withProperty("axis", "y"),
                Material.get("minecraft:smooth_basalt"), Material.GRASS_BLOCK);
        final AxiomTextureTerrains prepared = AxiomTextureTerrains.prepare(world, materials, false);
        assertEmptyPalettes();
        assertEquals(4, prepared.getCreatedMaterials().size());
        prepared.install();
        for (Material material : materials) {
            final Terrain terrain = prepared.getTerrains().get(material);
            final int index = terrain.getCustomTerrainIndex();
            assertEquals(MixedMaterial.Mode.SIMPLE, world.getMixedMaterial(index).getMode());
            assertEquals(material, world.getMixedMaterial(index).getSingleMaterial());
            assertEquals(material, terrain.getMaterial(DefaultPlugin.JAVA_ANVIL_1_18, 12, 2, 3, 90, 90));
            assertEquals(material, terrain.getMaterial(DefaultPlugin.JAVA_ANVIL_1_18, 12, 2, 3, 89, 90));
            assertNull(terrain.getSurfaceObject(DefaultPlugin.JAVA_ANVIL_1_18, 12, 2, 3, 0));
        }
        prepared.rollback();
        assertEmptyPalettes();
    }

    @Test
    public void reservesAndReusesNativeBlobMixInTheSameTransaction() {
        final MixedMaterial mix = blobs("Axiom plains", Material.GRASS_BLOCK, 56,
                Material.get("minecraft:pale_moss_block"), 22,
                Material.get("minecraft:moss_block"), 18);
        final AxiomTextureTerrains prepared = AxiomTextureTerrains.prepare(world, List.of(Material.BASALT), mix, false);
        assertEmptyPalettes();
        assertNotNull(prepared.getMixedTerrain());
        prepared.install();

        final int mixIndex = prepared.getMixedTerrain().getCustomTerrainIndex();
        assertSame(mix, world.getMixedMaterial(mixIndex));
        assertSame(mix, Terrain.getCustomMaterial(mixIndex));
        assertEquals(MixedMaterial.Mode.BLOBS, world.getMixedMaterial(mixIndex).getMode());

        // A matching recipe with another UUID/name is safe to reuse; a name alone never was.
        final MixedMaterial equivalent = blobs("Different user label", Material.GRASS_BLOCK, 56,
                Material.get("minecraft:pale_moss_block"), 22,
                Material.get("minecraft:moss_block"), 18);
        final AxiomTextureTerrains repeated = AxiomTextureTerrains.prepare(world, List.of(Material.BASALT), equivalent, true);
        assertTrue(repeated.getCreatedMaterials().isEmpty());
        assertEquals(prepared.getMixedTerrain(), repeated.getMixedTerrain());
        repeated.install();
        repeated.rollback();
        prepared.rollback();
        assertEmptyPalettes();
    }

    @Test
    public void reusesByExactMaterialRatherThanNameAndRepeatedRunsUseNoNewSlots() {
        final Material smoothBasalt = Material.get("minecraft:smooth_basalt");
        final MixedMaterial original = simple("Unrelated user name", smoothBasalt);
        final MixedMaterial misleading = simple("Axiom dag: minecraft:basalt{axis=y}", Material.DIRT);
        world.setMixedMaterial(5, original);
        Terrain.setCustomMaterial(5, original);
        world.setMixedMaterial(7, misleading);
        Terrain.setCustomMaterial(7, misleading);
        final List<Material> materials = List.of(smoothBasalt, Material.BASALT);
        final AxiomTextureTerrains first = AxiomTextureTerrains.prepare(world, materials, true);
        assertEquals(Terrain.getCustomTerrain(5), first.getTerrains().get(smoothBasalt));
        assertNotEquals(Terrain.getCustomTerrain(7), first.getTerrains().get(Material.BASALT));
        first.install();
        final AxiomTextureTerrains repeated = AxiomTextureTerrains.prepare(world, materials, true);
        assertTrue(repeated.getCreatedMaterials().isEmpty());
        assertEquals(first.getTerrains(), repeated.getTerrains());
        repeated.install();
        repeated.rollback();
        first.rollback();
        assertSame(original, world.getMixedMaterial(5));
        assertSame(original, Terrain.getCustomMaterial(5));
        assertSame(misleading, world.getMixedMaterial(7));
        assertSame(misleading, Terrain.getCustomMaterial(7));
    }

    @Test
    public void activePaletteWinsOverStaleWorldAndRollbackRestoresBoth() {
        final Material smoothBasalt = Material.get("minecraft:smooth_basalt");
        final MixedMaterial stale = simple("Old saved terrain", Material.DIRT);
        final MixedMaterial current = simple("Live edited terrain", smoothBasalt);
        world.setMixedMaterial(3, stale);
        Terrain.setCustomMaterial(3, current);
        final AxiomTextureTerrains prepared = AxiomTextureTerrains.prepare(world, List.of(smoothBasalt), true);
        assertEquals(Terrain.getCustomTerrain(3), prepared.getTerrains().get(smoothBasalt));
        assertTrue(prepared.getCreatedMaterials().isEmpty());
        assertSame(stale, world.getMixedMaterial(3));
        prepared.install();
        assertSame(current, world.getMixedMaterial(3));
        assertSame(current, Terrain.getCustomMaterial(3));
        prepared.rollback();
        prepared.rollback();
        assertSame(stale, world.getMixedMaterial(3));
        assertSame(current, Terrain.getCustomMaterial(3));
    }

    @Test
    public void capacityFailureDoesNotModifyEitherPalette() {
        final MixedMaterial occupied = simple("Existing terrain", Material.DIRT);
        // Union is occupied: saved-only and live-only slots both need protection.
        for (int index = 0; index < Terrain.CUSTOM_TERRAIN_COUNT - 1; index++) {
            if ((index & 1) == 0) {
                world.setMixedMaterial(index, occupied);
            } else {
                Terrain.setCustomMaterial(index, occupied);
            }
        }
        final long previousChanges = world.getChangeNo();
        assertThrows(IllegalStateException.class, () -> AxiomTextureTerrains.prepare(world,
                List.of(Material.BASALT, Material.get("minecraft:smooth_basalt")), true));
        assertEquals(previousChanges, world.getChangeNo());
        for (int index = 0; index < Terrain.CUSTOM_TERRAIN_COUNT - 1; index++) {
            assertSame((index & 1) == 0 ? occupied : null, world.getMixedMaterial(index));
            assertSame((index & 1) == 0 ? null : occupied, Terrain.getCustomMaterial(index));
        }
        assertNull(world.getMixedMaterial(Terrain.CUSTOM_TERRAIN_COUNT - 1));
        assertNull(Terrain.getCustomMaterial(Terrain.CUSTOM_TERRAIN_COUNT - 1));
    }

    @Test
    public void headlessInstallDoesNotOverwriteConflictingLivePalette() {
        final MixedMaterial saved = simple("Headless basalt", Material.BASALT);
        final MixedMaterial live = simple("Other world's grass", Material.GRASS_BLOCK);
        world.setMixedMaterial(0, saved);
        Terrain.setCustomMaterial(0, live);
        final AxiomTextureTerrains prepared = AxiomTextureTerrains.prepare(world, List.of(Material.BASALT), false);
        assertEquals(Terrain.getCustomTerrain(1), prepared.getTerrains().get(Material.BASALT));
        prepared.install();
        assertSame(saved, world.getMixedMaterial(0));
        assertSame(live, Terrain.getCustomMaterial(0));
        prepared.rollback();
        assertSame(saved, world.getMixedMaterial(0));
        assertSame(live, Terrain.getCustomMaterial(0));
        assertNull(world.getMixedMaterial(1));
        assertNull(Terrain.getCustomMaterial(1));
    }

    @Test
    public void changedReservationFailsBeforeAnyInstallation() {
        final AxiomTextureTerrains prepared = AxiomTextureTerrains.prepare(world,
                List.of(Material.BASALT, Material.get("minecraft:smooth_basalt")), false);
        final MixedMaterial intervening = simple("Intervening live change", Material.DIRT);
        Terrain.setCustomMaterial(1, intervening);
        assertThrows(IllegalStateException.class, prepared::install);
        assertNull(world.getMixedMaterial(0));
        assertNull(world.getMixedMaterial(1));
        assertNull(Terrain.getCustomMaterial(0));
        assertSame(intervening, Terrain.getCustomMaterial(1));
        prepared.rollback();
        assertSame(intervening, Terrain.getCustomMaterial(1));
    }

    @Test
    public void exceptionAfterWorldMutationRestoresAlreadyInstalledSlots() {
        world = new World2(DefaultPlugin.JAVA_ANVIL_1_18, -64, 320) {
            @Override
            public void setMixedMaterial(int index, MixedMaterial material) {
                super.setMixedMaterial(index, material);
                if (material != null && ++installations == 2) {
                    throw new IllegalStateException("Simulated property listener failure after mutation");
                }
            }

            private int installations;
        };
        final AxiomTextureTerrains prepared = AxiomTextureTerrains.prepare(world, List.of(Material.BASALT),
                blobs("Plains mix", Material.GRASS_BLOCK, 56, Material.get("minecraft:pale_moss_block"), 22,
                        Material.get("minecraft:moss_block"), 18), false);
        assertThrows(IllegalStateException.class, prepared::install);
        assertEmptyPalettes();
        prepared.rollback();
        assertEmptyPalettes();
    }

    private void assertEmptyPalettes() {
        for (int index = 0; index < Terrain.CUSTOM_TERRAIN_COUNT; index++) {
            assertNull(world.getMixedMaterial(index));
            assertNull(Terrain.getCustomMaterial(index));
        }
    }

    private static MixedMaterial simple(String name, Material material) {
        return new MixedMaterial(name, new MixedMaterial.Row(material, 1, 1.0f), -1, null);
    }

    private static MixedMaterial blobs(String name, Material first, int firstCount, Material second, int secondCount,
                                       Material third, int thirdCount) {
        return new MixedMaterial(name, new MixedMaterial.Row[] {
                new MixedMaterial.Row(first, firstCount, 1.0f),
                new MixedMaterial.Row(second, secondCount, 1.0f),
                new MixedMaterial.Row(third, thirdCount, 1.0f)
        }, -1, null, 1.25f);
    }

    private final MixedMaterial[] originalPalette = new MixedMaterial[Terrain.CUSTOM_TERRAIN_COUNT];
    private World2 world;
}
