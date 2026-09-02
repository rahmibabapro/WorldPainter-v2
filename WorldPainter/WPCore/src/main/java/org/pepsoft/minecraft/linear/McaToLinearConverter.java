package org.pepsoft.minecraft.linear;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Converts an Anvil {@code .mca} region file to Linear {@code .linear} for AgeOfMC servers.
 *
 * <p>Slot payloads are <strong>uncompressed NBT</strong> (LinearRegionFileFormatTools convention).
 * Do not zlib-wrap chunks before {@link LinearRegionFile#putChunk}.
 */
public final class McaToLinearConverter {
    private McaToLinearConverter() {
    }

    public static void convert(Path mcaFile, Path outputLinear, int zstdLevel) throws IOException {
        try (org.pepsoft.minecraft.RegionFile region =
                     new org.pepsoft.minecraft.RegionFile(mcaFile.toFile(), true)) {
            String name = mcaFile.getFileName().toString();
            String[] parts = name.split("\\.");
            if (parts.length < 4 || ! "r".equals(parts[0])) {
                throw new IOException("Not a region file name: " + name);
            }
            int regionX = Integer.parseInt(parts[1]);
            int regionZ = Integer.parseInt(parts[2]);
            LinearRegionFile linear = new LinearRegionFile(regionX, regionZ);
            int now = (int) (System.currentTimeMillis() / 1000L);
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    if (! region.containsChunk(x, z)) {
                        continue;
                    }
                    try (java.io.DataInputStream in = region.getChunkDataInputStream(x, z)) {
                        if (in == null) {
                            continue;
                        }
                        // Inflated NBT bytes — Linear slots store raw NBT, not zlib.
                        byte[] nbt = in.readAllBytes();
                        linear.putChunk(x, z, nbt, now);
                    }
                }
            }
            Files.createDirectories(outputLinear.getParent() != null ? outputLinear.getParent() : Path.of("."));
            linear.write(outputLinear, zstdLevel);
        }
    }
}
