package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.MixedMaterial;
import org.pepsoft.worldpainter.objects.GenericObject;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AxiomTextureMixTest {
    @Test
    public void grassOnlyPlainsRejectsEveryStoneAndMossSourceState() throws Exception {
        final int size = 16, depth = 10;
        final Material grass = Material.get("minecraft:grass_block", "snowy", "false");
        final Material[] blocks = new Material[size * size * depth];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                final int index = x + y * size;
                blocks[index + 7 * size * size] = Material.STONE;
                blocks[index + 8 * size * size] = index % 3 == 0 ? Material.COBBLESTONE
                        : index % 3 == 1 ? Material.get("minecraft:pale_moss_block") : grass;
            }
        }
        final AxiomTextureProfile profile = AxiomTextureProfile.analyze(
                new GenericObject("mixed flat source", size, size, depth, blocks), null);
        final AxiomTextureMix.Result result = AxiomTextureMix.createGrassOnly("Flat grass", profile);
        assertEquals(MixedMaterial.Mode.SIMPLE, result.material().getMode());
        assertEquals(grass, result.material().getSingleMaterial());
        assertEquals(1, result.material().getRows().length);
        assertEquals(grass, result.material().getMaterial(17L, 100, -100, 90.0f));
    }

    @Test
    public void usesMeasuredStructuralCountsAsWorldPainterBlobRows() throws Exception {
        final int size = 32, depth = 12;
        final Material grass = Material.get("minecraft:grass_block", "snowy", "false");
        final Material paleMoss = Material.get("minecraft:pale_moss_block");
        final Material moss = Material.get("minecraft:moss_block");
        final Material[] blocks = new Material[size * size * depth];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                final int index = x + y * size;
                blocks[index + 8 * size * size] = Material.STONE;
                blocks[index + 9 * size * size] = index < 512 ? grass : index < 768 ? paleMoss : moss;
            }
        }
        final AxiomTextureProfile profile = AxiomTextureProfile.analyze(
                new GenericObject("measured plains", size, size, depth, blocks), null);

        final AxiomTextureMix.Result result = AxiomTextureMix.create("Test plains Blob mix", profile);
        assertEquals(MixedMaterial.Mode.BLOBS, result.material().getMode());
        assertEquals(AxiomTextureMix.DEFAULT_BLOB_SCALE, result.material().getScale(), 0.0f);
        final Map<Material, Integer> rows = new HashMap<>();
        for (MixedMaterial.Row row : result.material().getRows()) {
            rows.put(row.material, row.occurrence);
            assertEquals(1.0f, row.scale, 0.0f);
        }
        assertEquals(512, rows.get(grass).intValue());
        assertEquals(256, rows.get(paleMoss).intValue());
        assertEquals(256, rows.get(moss).intValue());
        assertEquals(512L, result.sourceCounts().get(AxiomTextureProfile.materialKey(grass)).longValue());
        assertEquals(0.5, result.sourceRatios().get(AxiomTextureProfile.materialKey(grass)), 0.0);
        // The native terrain remains coordinate/seed driven; it is not an imported 16x16 patch.
        assertTrue(result.material().getMaterial(42L, 0, 0, 110.0f) != null);
        assertTrue(result.material().getMaterial(42L, 23, 71, 113.0f) != null);
    }
}
