package org.pepsoft.minecraft.linear;

import com.github.luben.zstd.Zstd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Writes/reads Minecraft Linear region files ({@code r.X.Z.linear}) for AgeOfMC / LinearPaper forks.
 * Format matches LinearRegionFileFormatTools v1 (signature {@code 0xc3ff13183cca9d9a}).
 *
 * <p>Chunk slots hold <strong>uncompressed NBT</strong> bytes (same as reference {@code linear.py}
 * {@code Chunk.raw_chunk} / {@code nbtlib.File.parse}). The whole region body is then Zstd-compressed.
 */
public final class LinearRegionFile {
    public static final long LINEAR_SIGNATURE = 0xc3ff13183cca9d9aL;
    public static final byte LINEAR_VERSION = 1;
    public static final int REGION_DIMENSION = 32;
    public static final int SLOT_COUNT = REGION_DIMENSION * REGION_DIMENSION;

    private final byte[][] chunks = new byte[SLOT_COUNT][];
    private final int[] timestamps = new int[SLOT_COUNT];
    private final int regionX;
    private final int regionZ;

    public LinearRegionFile(int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
    }

    /**
     * @param uncompressedNbt raw NBT payload (not zlib-wrapped)
     */
    public void putChunk(int localX, int localZ, byte[] uncompressedNbt, int timestampSeconds) {
        if (localX < 0 || localX >= REGION_DIMENSION || localZ < 0 || localZ >= REGION_DIMENSION) {
            throw new IllegalArgumentException("chunk out of region: " + localX + "," + localZ);
        }
        int index = localX + localZ * REGION_DIMENSION;
        chunks[index] = Objects.requireNonNull(uncompressedNbt, "chunk").clone();
        timestamps[index] = timestampSeconds;
    }

    public byte[] getChunk(int localX, int localZ) {
        byte[] data = chunks[localX + localZ * REGION_DIMENSION];
        return data != null ? data.clone() : null;
    }

    public void write(Path destination, int compressionLevel) throws IOException {
        ByteArrayOutputStream completeRegion = new ByteArrayOutputStream(SLOT_COUNT * 8 + 4096);
        DataOutputStream headerOut = new DataOutputStream(completeRegion);
        int chunkCount = 0;
        long newest = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (chunks[i] != null) {
                headerOut.writeInt(chunks[i].length);
                headerOut.writeInt(timestamps[i]);
                chunkCount++;
                newest = Math.max(newest, Integer.toUnsignedLong(timestamps[i]));
            } else {
                headerOut.writeInt(0);
                headerOut.writeInt(0);
            }
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (chunks[i] != null) {
                completeRegion.write(chunks[i]);
            }
        }

        byte[] uncompressed = completeRegion.toByteArray();
        byte[] compressed = Zstd.compress(uncompressed, compressionLevel);

        ByteArrayOutputStream fileBytes = new ByteArrayOutputStream(32 + compressed.length + 8);
        DataOutputStream out = new DataOutputStream(fileBytes);
        out.writeLong(LINEAR_SIGNATURE);
        out.writeByte(LINEAR_VERSION);
        out.writeLong(newest);
        out.writeByte(compressionLevel);
        out.writeShort(chunkCount);
        out.writeInt(compressed.length);
        out.writeLong(0L); // reserved hash
        out.write(compressed);
        out.writeLong(LINEAR_SIGNATURE);
        out.flush();

        Path wip = destination.resolveSibling(destination.getFileName().toString() + ".wip");
        Files.write(wip, fileBytes.toByteArray());
        try {
            Files.move(wip, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(wip, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Load a Linear region for round-trip / compatibility checks.
     */
    public static LinearRegionFile read(Path linearFile, int regionX, int regionZ) throws IOException {
        byte[] file = Files.readAllBytes(linearFile);
        if (file.length < 40) {
            throw new IOException("Linear file too small: " + linearFile);
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(file));
        long sig = in.readLong();
        if (sig != LINEAR_SIGNATURE) {
            throw new IOException("Invalid Linear header signature");
        }
        byte version = in.readByte();
        if (version != LINEAR_VERSION && version != 2) {
            throw new IOException("Unsupported Linear version: " + version);
        }
        in.readLong(); // newest timestamp
        in.readByte(); // compression level
        in.readShort(); // chunk count
        int compressedLen = in.readInt();
        in.readLong(); // reserved hash
        byte[] compressed = in.readNBytes(compressedLen);
        long footer = in.readLong();
        if (footer != LINEAR_SIGNATURE) {
            throw new IOException("Invalid Linear footer signature");
        }

        long decompressedSize = Zstd.decompressedSize(compressed);
        byte[] body;
        if (decompressedSize > 0 && decompressedSize <= Integer.MAX_VALUE) {
            body = Zstd.decompress(compressed, (int) decompressedSize);
        } else {
            try (com.github.luben.zstd.ZstdInputStream zin =
                         new com.github.luben.zstd.ZstdInputStream(new ByteArrayInputStream(compressed))) {
                body = zin.readAllBytes();
            }
        }
        DataInputStream bodyIn = new DataInputStream(new ByteArrayInputStream(body));
        int[] sizes = new int[SLOT_COUNT];
        int[] stamps = new int[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            sizes[i] = bodyIn.readInt();
            stamps[i] = bodyIn.readInt();
        }
        LinearRegionFile region = new LinearRegionFile(regionX, regionZ);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (sizes[i] > 0) {
                byte[] nbt = bodyIn.readNBytes(sizes[i]);
                int x = i % REGION_DIMENSION;
                int z = i / REGION_DIMENSION;
                region.putChunk(x, z, nbt, stamps[i]);
            }
        }
        return region;
    }

    public int getRegionX() {
        return regionX;
    }

    public int getRegionZ() {
        return regionZ;
    }

    public static Path regionPath(Path regionDir, int regionX, int regionZ) {
        return regionDir.resolve("r." + regionX + "." + regionZ + ".linear");
    }
}
