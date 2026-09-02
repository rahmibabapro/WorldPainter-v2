package org.pepsoft.worldpainter.layers.bo2;

import org.jnbt.*;
import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.PackedArrayCube;

import javax.vecmath.Point3i;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.*;
import static org.pepsoft.minecraft.Material.AIR;

/**
 * Regression test for Axiom {@code .bp} axis mapping: Minecraft Y must become WorldPainter Z
 * (vertical), not be swapped with Z.
 */
public class AxiomBlueprintTest {
    private static final int MAGIC = 182827830; // 0x0AE5BB36

    @Test
    public void loadMapsMinecraftYToWorldPainterZ() throws Exception {
        final Material trunk = Material.get(MC_OAK_LOG);
        final Material leaves = Material.get(MC_OAK_LEAVES);
        final byte[] bp = buildSyntheticBlueprint(trunk, leaves);

        final AxiomBlueprint blueprint = AxiomBlueprint.load("axis-test", new ByteArrayInputStream(bp));

        final Point3i dims = blueprint.getDimensions();
        assertEquals("width (MC X)", 1, dims.x);
        assertEquals("length (MC Z)", 1, dims.y);
        assertEquals("height (MC Y -> WP Z)", 4, dims.z);

        assertTrue(blueprint.getMask(0, 0, 0));
        assertEquals(trunk, blueprint.getMaterial(0, 0, 0));

        assertTrue(blueprint.getMask(0, 0, 3));
        assertEquals(leaves, blueprint.getMaterial(0, 0, 3));

        // Intermediate cells are empty (void_air skipped)
        assertFalse(blueprint.getMask(0, 0, 1));
        assertFalse(blueprint.getMask(0, 0, 2));
        assertEquals(AIR, blueprint.getMaterial(0, 0, 1));

        // Default vertical (UI "Y axis") offset plants the object 1 block into the terrain
        assertEquals(-1, blueprint.getOffset().z);
    }

    @Test
    public void loadRealAgacBlueprintIsUprightTree() throws Exception {
        final java.io.File file = new java.io.File(
                System.getProperty("user.home") + "/Documents/world painter/agac/aga/agac.bp");
        if (! file.isFile()) {
            return; // optional fixture; skip when not present
        }
        final AxiomBlueprint blueprint = AxiomBlueprint.load(file);
        final Point3i dims = blueprint.getDimensions();
        // Correct decode: ~15 x 16 x 18 with a thin trunk and widening canopy
        assertTrue("expected tree height around 18, got " + dims.z, dims.z >= 15 && dims.z <= 25);
        assertTrue("expected length around 16, got " + dims.y, dims.y >= 10 && dims.y <= 20);
        assertTrue("expected width around 15, got " + dims.x, dims.x >= 10 && dims.x <= 20);

        int bottomLayer = 0;
        for (int x = 0; x < dims.x; x++) {
            for (int y = 0; y < dims.y; y++) {
                if (blueprint.getMask(x, y, 0)) {
                    bottomLayer++;
                }
            }
        }
        // Trunk footing should be sparse; corrupted Y/Z swap yields 15+ blocks on the bottom layer
        assertTrue("bottom layer should be a thin trunk, got " + bottomLayer + " blocks", bottomLayer <= 4);
    }

    /**
     * Build a minimal Version=2 blueprint: one section at (0,0,0) with oak_log at local Y=0
     * and oak_leaves at local Y=3 (same X/Z).
     */
    static byte[] buildSyntheticBlueprint(Material trunk, Material leaves) throws Exception {
        final Material voidAir = Material.get(MC_VOID_AIR);

        final PackedArrayCube<Material> cube = new PackedArrayCube<>(16, 4, false, Material.class);
        cube.fill(voidAir);
        cube.setValue(0, 0, 0, trunk);   // PackedArrayCube (x, z, y) = MC (x, y=0, z)
        cube.setValue(0, 0, 3, leaves);  // MC y=3

        final PackedArrayCube<Material>.PackedData packed = cube.pack();

        final Map<String, Tag> header = new HashMap<>();
        header.put("Version", new IntTag("Version", 2));
        header.put("Name", new StringTag("Name", "axis-test"));
        header.put("ContainsAir", new ByteTag("ContainsAir", (byte) 0));
        header.put("BlockCount", new IntTag("BlockCount", 2));
        final byte[] headerBytes = writeUncompressedNbt(new CompoundTag("", header));

        final List<CompoundTag> paletteEntries = new ArrayList<>();
        for (Material material : packed.palette) {
            final Map<String, Tag> entry = new HashMap<>();
            entry.put(TAG_NAME, new StringTag(TAG_NAME, material.name));
            if (material.getProperties() != null) {
                final Map<String, Tag> props = new HashMap<>();
                for (Map.Entry<String, String> prop : material.getProperties().entrySet()) {
                    props.put(prop.getKey(), new StringTag(prop.getKey(), prop.getValue()));
                }
                entry.put(TAG_PROPERTIES, new CompoundTag(TAG_PROPERTIES, props));
            }
            paletteEntries.add(new CompoundTag("", entry));
        }

        final Map<String, Tag> blockStates = new HashMap<>();
        blockStates.put(TAG_PALETTE_, new ListTag<>(TAG_PALETTE_, CompoundTag.class, paletteEntries));
        blockStates.put(TAG_DATA_, new LongArrayTag(TAG_DATA_, packed.data));

        final Map<String, Tag> region = new HashMap<>();
        region.put("X", new IntTag("X", 0));
        region.put("Y", new IntTag("Y", 0));
        region.put("Z", new IntTag("Z", 0));
        region.put("BlockStates", new CompoundTag("BlockStates", blockStates));

        final Map<String, Tag> blockRoot = new HashMap<>();
        blockRoot.put("DataVersion", new IntTag("DataVersion", 4556));
        blockRoot.put("BlockRegion", new ListTag<>("BlockRegion", CompoundTag.class,
                Collections.singletonList(new CompoundTag("", region))));
        blockRoot.put("BlockEntities", new ListTag<>("BlockEntities", CompoundTag.class, Collections.emptyList()));
        blockRoot.put("Entities", new ListTag<>("Entities", CompoundTag.class, Collections.emptyList()));
        final byte[] compressedBlocks = writeCompressedNbt(new CompoundTag("", blockRoot));

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(MAGIC);
            out.writeInt(headerBytes.length);
            out.write(headerBytes);
            out.writeInt(0); // no thumbnail
            out.writeInt(compressedBlocks.length);
            out.write(compressedBlocks);
        }
        return baos.toByteArray();
    }

    private static byte[] writeUncompressedNbt(CompoundTag tag) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (NBTOutputStream nbtOut = new NBTOutputStream(baos)) {
            nbtOut.writeTag(tag);
        }
        return baos.toByteArray();
    }

    private static byte[] writeCompressedNbt(CompoundTag tag) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (NBTOutputStream nbtOut = new NBTOutputStream(new GZIPOutputStream(baos))) {
            nbtOut.writeTag(tag);
        }
        return baos.toByteArray();
    }
}
