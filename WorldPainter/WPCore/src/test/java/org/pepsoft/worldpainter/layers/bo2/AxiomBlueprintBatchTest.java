package org.pepsoft.worldpainter.layers.bo2;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.io.ByteArrayInputStream;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.MC_OAK_LEAVES;
import static org.pepsoft.minecraft.Constants.MC_OAK_LOG;
import static org.pepsoft.minecraft.Material.AIR;
import static org.pepsoft.worldpainter.Configuration.DEFAULT_PLATFORM;
import static org.pepsoft.worldpainter.layers.bo2.AxiomBlueprintTest.buildSyntheticBlueprint;

/**
 * Tests for {@link AxiomBlueprintBatch} using the synthetic .bp fixture from {@link AxiomBlueprintTest}.
 */
public class AxiomBlueprintBatchTest {

    @Test
    public void applyYOffsetAdjustsVerticalOffset() throws Exception {
        final AxiomBlueprint blueprint = loadFixture();
        assertEquals(-1, blueprint.getOffset().z);

        final AxiomBlueprint raised = AxiomBlueprintBatch.applyYOffset(blueprint, 5);
        assertEquals(4, raised.getOffset().z);
        assertEquals(-1, blueprint.getOffset().z); // original unchanged

        final AxiomBlueprint lowered = AxiomBlueprintBatch.applyYOffset(blueprint, -3);
        assertEquals(-4, lowered.getOffset().z);
    }

    @Test
    public void rotate90SwapsHorizontalDimensions() throws Exception {
        final AxiomBlueprint blueprint = loadFixture();
        final Point3i dims = blueprint.getDimensions();
        assertEquals(1, dims.x);
        assertEquals(1, dims.y);
        assertEquals(4, dims.z);

        final WPObject rotated = AxiomBlueprintBatch.rotate90(blueprint, DEFAULT_PLATFORM);
        final Point3i rotDims = rotated.getDimensions();
        // 1×1 footprint stays 1×1 after 90°; height unchanged
        assertEquals(dims.y, rotDims.x);
        assertEquals(dims.x, rotDims.y);
        assertEquals(dims.z, rotDims.z);

        assertEquals(blueprint.getMaterial(0, 0, 0), rotated.getMaterial(0, 0, 0));
        assertEquals(blueprint.getMaterial(0, 0, 3), rotated.getMaterial(0, 0, 3));
    }

    @Test
    public void rotate180Then180RestoresMaterials() throws Exception {
        final AxiomBlueprint blueprint = loadFixture();
        final WPObject once = AxiomBlueprintBatch.rotate180(blueprint, DEFAULT_PLATFORM);
        final WPObject twice = new org.pepsoft.worldpainter.objects.RotatedObject(once, 2, DEFAULT_PLATFORM);

        final Point3i dims = blueprint.getDimensions();
        for (int z = 0; z < dims.z; z++) {
            for (int y = 0; y < dims.y; y++) {
                for (int x = 0; x < dims.x; x++) {
                    assertEquals(blueprint.getMaterial(x, y, z), twice.getMaterial(x, y, z));
                    assertEquals(blueprint.getMask(x, y, z), twice.getMask(x, y, z));
                }
            }
        }
    }

    @Test
    public void rotate90Compose270MatchesOriginal() throws Exception {
        final AxiomBlueprint blueprint = loadFixture();
        final WPObject rotated = AxiomBlueprintBatch.rotate90(blueprint, DEFAULT_PLATFORM);
        final WPObject restored = new org.pepsoft.worldpainter.objects.RotatedObject(rotated, 3, DEFAULT_PLATFORM);

        final Point3i dims = blueprint.getDimensions();
        assertEquals(dims, restored.getDimensions());
        for (int z = 0; z < dims.z; z++) {
            for (int y = 0; y < dims.y; y++) {
                for (int x = 0; x < dims.x; x++) {
                    assertEquals(blueprint.getMaterial(x, y, z), restored.getMaterial(x, y, z));
                }
            }
        }
    }

    @Test
    public void mirrorPreservesDimensions() throws Exception {
        final AxiomBlueprint blueprint = loadFixture();
        final WPObject mirrored = AxiomBlueprintBatch.mirror(blueprint, false, DEFAULT_PLATFORM);
        assertEquals(blueprint.getDimensions(), mirrored.getDimensions());
        assertEquals(blueprint.getMaterial(0, 0, 0), mirrored.getMaterial(0, 0, 0));
        assertEquals(blueprint.getMaterial(0, 0, 3), mirrored.getMaterial(0, 0, 3));
    }

    @Test
    public void replaceAirSubstitutesAirMaterial() throws Exception {
        final AxiomBlueprint blueprint = loadFixture();
        final Material stone = Material.STONE;
        final WPObject replaced = AxiomBlueprintBatch.replaceAir(blueprint, stone);

        assertEquals(Material.get(MC_OAK_LOG), replaced.getMaterial(0, 0, 0));
        assertEquals(Material.get(MC_OAK_LEAVES), replaced.getMaterial(0, 0, 3));
        // Empty intermediate cells decode as AIR
        assertEquals(AIR, blueprint.getMaterial(0, 0, 1));
        assertEquals(stone, replaced.getMaterial(0, 0, 1));
        assertEquals(stone, replaced.getMaterial(0, 0, 2));
    }

    private static AxiomBlueprint loadFixture() throws Exception {
        final Material trunk = Material.get(MC_OAK_LOG);
        final Material leaves = Material.get(MC_OAK_LEAVES);
        final byte[] bp = buildSyntheticBlueprint(trunk, leaves);
        return AxiomBlueprint.load("axis-test", new ByteArrayInputStream(bp));
    }
}
