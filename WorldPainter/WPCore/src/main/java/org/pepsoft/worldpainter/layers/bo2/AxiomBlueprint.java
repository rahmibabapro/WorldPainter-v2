package org.pepsoft.worldpainter.layers.bo2;

import com.google.common.collect.ImmutableList;
import org.jnbt.*;
import org.pepsoft.minecraft.Entity;
import org.pepsoft.minecraft.Material;
import org.pepsoft.minecraft.TileEntity;
import org.pepsoft.util.AttributeKey;
import org.pepsoft.util.PackedArrayCube;
import org.pepsoft.worldpainter.objects.AbstractObject;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static java.util.stream.Collectors.toMap;
import static org.pepsoft.minecraft.Constants.*;
import static org.pepsoft.minecraft.Material.AIR;

/**
 * An Axiom editor blueprint ({@code .bp} file).
 *
 * <p>Format specification derived from Axiom's {@code BlueprintIo} (magic {@code 0x0AE5BB36},
 * header NBT, PNG thumbnail, gzip-compressed block NBT with {@code BlockRegion} sections).
 *
 * <p>Section block data uses Minecraft's {@code PalettedContainer} packing (Y-major index
 * {@code y*256 + z*16 + x}). WorldPainter's {@link PackedArrayCube} exposes the same index via
 * {@code getValue(x, z, y)} — matching {@code MC118AnvilChunk} — not {@code getValue(x, y, z)}.
 */
public final class AxiomBlueprint extends AbstractObject implements Bo2ObjectProvider {
    private static final int MAGIC = 182827830; // 0x0AE5BB36

    private AxiomBlueprint(String name, int width, int length, int height, Material[] blocks,
                           List<Entity> entities, List<TileEntity> tileEntities) {
        this.name = name;
        this.width = width;
        this.length = length;
        this.height = height;
        this.blocks = blocks;
        this.entities = entities;
        this.tileEntities = tileEntities;
        // Keep auto X/Z (horizontal) centering; plant 1 block into the terrain (UI "Y axis" = offset.z).
        final Point3i auto = guestimateOffset();
        final Point3i offset = (auto != null)
                ? new Point3i(auto.x, auto.y, -1)
                : new Point3i(0, 0, -1);
        setAttribute(ATTRIBUTE_OFFSET, offset);
        guessManageWaterlogged();
    }

    @Override
    public WPObject getObject() {
        return this;
    }

    @Override
    public List<WPObject> getAllObjects() {
        return Collections.singletonList(this);
    }

    @Override
    public void setSeed(long seed) {
        // Do nothing
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Point3i getDimensions() {
        return new Point3i(width, length, height);
    }

    @Override
    public Material getMaterial(int x, int y, int z) {
        final Material material = blocks[index(x, y, z)];
        return (material != null) ? material : AIR;
    }

    @Override
    public boolean getMask(int x, int y, int z) {
        final Material material = blocks[index(x, y, z)];
        if (getAttribute(ATTRIBUTE_IGNORE_AIR)) {
            return (material != null) && (material != AIR);
        } else {
            return material != null;
        }
    }

    @Override
    public Set<Material> getAllMaterials() {
        final Set<Material> materials = new HashSet<>();
        for (Material material : blocks) {
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }

    @Override
    public List<Entity> getEntities() {
        return entities;
    }

    @Override
    public List<TileEntity> getTileEntities() {
        return tileEntities;
    }

    @Override
    public Map<String, Serializable> getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Map<String, Serializable> attributes) {
        this.attributes = attributes;
    }

    @Override
    public <T extends Serializable> void setAttribute(AttributeKey<T> key, T value) {
        if (value != null) {
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            attributes.put(key.key, value);
        } else if (attributes != null) {
            attributes.remove(key.key);
            if (attributes.isEmpty()) {
                attributes = null;
            }
        }
    }

    @Override
    public AxiomBlueprint clone() {
        final AxiomBlueprint clone = (AxiomBlueprint) super.clone();
        if (attributes != null) {
            clone.attributes = new HashMap<>(attributes);
        }
        return clone;
    }

    public static AxiomBlueprint load(File file) throws IOException {
        String name = file.getName();
        if (name.toLowerCase().endsWith(".bp")) {
            name = name.substring(0, name.length() - 3).trim();
        }
        final AxiomBlueprint blueprint = load(name, new FileInputStream(file));
        blueprint.setAttribute(ATTRIBUTE_FILE, file);
        return blueprint;
    }

    @SuppressWarnings("unchecked")
    public static AxiomBlueprint load(String fallBackName, InputStream inputStream) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(inputStream))) {
            final int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Not a valid Axiom blueprint (bad magic number: " + magic + ")");
            }

            in.readInt(); // header byte length (redundant; NBT is self-delimiting)
            // Do not close NBTInputStream: it would close the shared blueprint stream.
            final CompoundTag headerTag = (CompoundTag) new NBTInputStream(in).readTag();

            final int headerVersion = getInt(headerTag, "Version", 0);
            final boolean containsAir = getBoolean(headerTag, "ContainsAir", false);
            final StringTag nameTag = (StringTag) headerTag.getTag("Name");
            final String objectName = (nameTag != null) ? nameTag.getValue() : fallBackName;

            final int thumbnailLength = in.readInt();
            if (in.skipBytes(thumbnailLength) != thumbnailLength) {
                throw new IOException("Unexpected end of file while reading blueprint thumbnail");
            }

            in.readInt(); // compressed block data byte length (redundant)
            final CompoundTag blockDataTag;
            try (NBTInputStream nbtIn = new NBTInputStream(new GZIPInputStream(in))) {
                blockDataTag = (CompoundTag) nbtIn.readTag();
            }

            final ListTag<CompoundTag> blockRegionsTag = (ListTag<CompoundTag>) blockDataTag.getTag("BlockRegion");
            if (blockRegionsTag == null) {
                throw new IllegalArgumentException("BlockRegion tag missing from Axiom blueprint " + objectName);
            }

            // First pass: collect blocks in absolute Minecraft coordinates
            final Map<Point3i, Material> absoluteBlocks = new HashMap<>();
            int minMcX = Integer.MAX_VALUE, minMcY = Integer.MAX_VALUE, minMcZ = Integer.MAX_VALUE;
            int maxMcX = Integer.MIN_VALUE, maxMcY = Integer.MIN_VALUE, maxMcZ = Integer.MIN_VALUE;

            for (CompoundTag regionTag : blockRegionsTag.getValue()) {
                final int chunkX = getInt(regionTag, "X", 0);
                final int chunkY = getInt(regionTag, "Y", 0);
                final int chunkZ = getInt(regionTag, "Z", 0);
                final CompoundTag blockStatesTag = (CompoundTag) regionTag.getTag("BlockStates");
                if (blockStatesTag == null) {
                    continue;
                }
                final SectionData section = parseSection(blockStatesTag);
                // Minecraft Y is up. PackedArrayCube args are (x, z, y) — see MC118AnvilChunk.
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        for (int localX = 0; localX < 16; localX++) {
                            final Material material = section.getMaterial(localX, localZ, localY);
                            if (isEmptyMaterial(material, headerVersion, containsAir)) {
                                continue;
                            }
                            final int mcX = chunkX * 16 + localX;
                            final int mcY = chunkY * 16 + localY;
                            final int mcZ = chunkZ * 16 + localZ;
                            absoluteBlocks.put(new Point3i(mcX, mcY, mcZ), material);
                            minMcX = Math.min(minMcX, mcX);
                            minMcY = Math.min(minMcY, mcY);
                            minMcZ = Math.min(minMcZ, mcZ);
                            maxMcX = Math.max(maxMcX, mcX);
                            maxMcY = Math.max(maxMcY, mcY);
                            maxMcZ = Math.max(maxMcZ, mcZ);
                        }
                    }
                }
            }

            if (absoluteBlocks.isEmpty()) {
                throw new IllegalArgumentException("Axiom blueprint " + objectName + " contains no blocks");
            }

            // Normalise to WorldPainter coordinates (x, y horizontal; z vertical)
            final int width = maxMcX - minMcX + 1;
            final int length = maxMcZ - minMcZ + 1;
            final int height = maxMcY - minMcY + 1;
            final Material[] blocks = new Material[width * length * height];
            for (Map.Entry<Point3i, Material> entry : absoluteBlocks.entrySet()) {
                final Point3i mc = entry.getKey();
                final int x = mc.x - minMcX;
                final int y = mc.z - minMcZ;
                final int z = mc.y - minMcY;
                blocks[x + y * width + z * width * length] = entry.getValue();
            }

            // Block entities — keep Minecraft axis order (exporter remaps at export time)
            final List<TileEntity> tileEntities = new ArrayList<>();
            final ListTag<CompoundTag> blockEntitiesTag = (ListTag<CompoundTag>) blockDataTag.getTag("BlockEntities");
            if (blockEntitiesTag != null) {
                for (CompoundTag blockEntityTag : blockEntitiesTag.getValue()) {
                    final int mcX = getInt(blockEntityTag, "x", 0);
                    final int mcY = getInt(blockEntityTag, "y", 0);
                    final int mcZ = getInt(blockEntityTag, "z", 0);
                    if (! absoluteBlocks.containsKey(new Point3i(mcX, mcY, mcZ))) {
                        continue;
                    }
                    final CompoundTag tagCopy = copyBlockEntityTag(blockEntityTag);
                    final TileEntity tileEntity = TileEntity.fromNBT(tagCopy);
                    tileEntity.setX(mcX - minMcX);
                    tileEntity.setY(mcY - minMcY);
                    tileEntity.setZ(mcZ - minMcZ);
                    tileEntities.add(tileEntity);
                }
            }

            // Entities — relative Pos stays in Minecraft axis order
            final List<Entity> entities = new ArrayList<>();
            final ListTag<CompoundTag> entitiesTag = (ListTag<CompoundTag>) blockDataTag.getTag("Entities");
            if (entitiesTag != null) {
                for (CompoundTag entityTag : entitiesTag.getValue()) {
                    double[] relPos = null;
                    if (entityTag.getTag("Pos") instanceof ListTag) {
                        final List<DoubleTag> posTags = ((ListTag<DoubleTag>) entityTag.getTag("Pos")).getValue();
                        final double mcX = posTags.get(0).getValue();
                        final double mcY = posTags.get(1).getValue();
                        final double mcZ = posTags.get(2).getValue();
                        relPos = new double[] {mcX - minMcX, mcY - minMcY, mcZ - minMcZ};
                    }
                    entities.add(Entity.fromNBT(entityTag, relPos));
                }
            }

            return new AxiomBlueprint(objectName, width, length, height, blocks,
                    entities.isEmpty() ? null : ImmutableList.copyOf(entities),
                    tileEntities.isEmpty() ? null : ImmutableList.copyOf(tileEntities));
        }
    }

    private int index(int x, int y, int z) {
        return x + y * width + z * width * length;
    }

    private static CompoundTag copyBlockEntityTag(CompoundTag blockEntityTag) {
        final CompoundTag tagCopy = new CompoundTag("", new HashMap<>(blockEntityTag.getValue()));
        final StringTag idTag = (StringTag) tagCopy.getTag("id");
        if (idTag != null) {
            tagCopy.setTag("id", null);
            tagCopy.setTag(TAG_ID_, new StringTag(TAG_ID_, idTag.getValue()));
        }
        tagCopy.setTag("x", null);
        tagCopy.setTag("y", null);
        tagCopy.setTag("z", null);
        return tagCopy;
    }

    @SuppressWarnings("unchecked")
    private static SectionData parseSection(CompoundTag blockStatesTag) {
        final ListTag<CompoundTag> paletteList = (ListTag<CompoundTag>) blockStatesTag.getTag(TAG_PALETTE_);
        if (paletteList == null) {
            throw new IllegalArgumentException("BlockStates palette tag missing");
        }
        final Material[] palette = new Material[paletteList.getValue().size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = decodePaletteEntry(paletteList.getValue().get(i));
        }
        final LongArrayTag dataTag = (LongArrayTag) blockStatesTag.getTag(TAG_DATA_);
        if (dataTag != null) {
            return new SectionData(new PackedArrayCube<>(16, dataTag.getValue(), palette, 4, false, Material.class));
        } else if (palette.length == 1) {
            return new SectionData(palette[0] == null ? AIR : palette[0]);
        } else {
            throw new IllegalArgumentException("BlockStates data tag missing");
        }
    }

    private static Material decodePaletteEntry(CompoundTag blockSpecTag) {
        final String name = ((StringTag) blockSpecTag.getTag(TAG_NAME)).getValue();
        final CompoundTag propertiesTag = (CompoundTag) blockSpecTag.getTag(TAG_PROPERTIES);
        final Map<String, String> properties;
        if (propertiesTag != null) {
            properties = propertiesTag.getValue().entrySet().stream()
                    .collect(toMap(Map.Entry::getKey, entry -> ((StringTag) entry.getValue()).getValue()));
        } else {
            properties = null;
        }
        return Material.get(name, properties);
    }

    /**
     * Axiom empty-cell marker is {@code structure_void} (header version ≤ 1) or {@code void_air}
     * (version ≥ 2). Regular {@code air} is only treated as empty when {@code ContainsAir} is false.
     */
    private static boolean isEmptyMaterial(Material material, int headerVersion, boolean containsAir) {
        if (material == null) {
            return true;
        }
        if (headerVersion <= 1) {
            if (MC_STRUCTURE_VOID.equals(material.name)) {
                return true;
            }
        } else if (MC_VOID_AIR.equals(material.name)) {
            return true;
        }
        if ((material == AIR) || MC_AIR.equals(material.name)) {
            return ! containsAir;
        }
        return false;
    }

    private static int getInt(CompoundTag tag, String key, int defaultValue) {
        final Tag valueTag = tag.getTag(key);
        if (valueTag instanceof IntTag) {
            return ((IntTag) valueTag).getValue();
        } else if (valueTag instanceof ByteTag) {
            return ((ByteTag) valueTag).intValue();
        }
        return defaultValue;
    }

    private static boolean getBoolean(CompoundTag tag, String key, boolean defaultValue) {
        final Tag valueTag = tag.getTag(key);
        if (valueTag instanceof ByteTag) {
            return ((ByteTag) valueTag).getValue() != 0;
        }
        return defaultValue;
    }

    private static final class SectionData {
        private final PackedArrayCube<Material> cube;
        private final Material singleMaterial;

        SectionData(PackedArrayCube<Material> cube) {
            this.cube = cube;
            this.singleMaterial = null;
        }

        SectionData(Material singleMaterial) {
            this.cube = null;
            this.singleMaterial = singleMaterial;
        }

        /**
         * @param x Minecraft local X
         * @param z Minecraft local Z (PackedArrayCube second arg)
         * @param y Minecraft local Y (PackedArrayCube third arg)
         */
        Material getMaterial(int x, int z, int y) {
            if (cube != null) {
                return cube.getValue(x, z, y);
            }
            return singleMaterial;
        }
    }

    private String name;
    private Map<String, Serializable> attributes;
    private final int width, length, height;
    private final Material[] blocks;
    private final List<Entity> entities;
    private final List<TileEntity> tileEntities;

    public static final AttributeKey<Boolean> ATTRIBUTE_IGNORE_AIR = new AttributeKey<>("AxiomBlueprint.ignoreAir", true);

    private static final long serialVersionUID = 1L;
}
