package org.pepsoft.minecraft.linear;

import org.junit.Test;
import org.pepsoft.minecraft.RegionFile;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LinearRegionFileTest {
    @Test
    public void writesLinearFileWithSignature() throws Exception {
        Path dir = Files.createTempDirectory("wp-linear");
        Path out = LinearRegionFile.regionPath(dir, 0, 0);
        LinearRegionFile region = new LinearRegionFile(0, 0);
        byte[] nbt = fakeNbtPayload();
        region.putChunk(1, 2, nbt, 12345);
        region.write(out, 1);
        assertTrue(Files.size(out) > 40);
        byte[] bytes = Files.readAllBytes(out);
        assertTrue((bytes[0] & 0xff) == 0xc3);
        assertTrue((bytes[1] & 0xff) == 0xff);
        int n = bytes.length;
        assertTrue((bytes[n - 8] & 0xff) == 0xc3);
        Files.deleteIfExists(out);
        Files.deleteIfExists(dir);
    }

    @Test
    public void roundTripPreservesUncompressedNbt() throws Exception {
        Path dir = Files.createTempDirectory("wp-linear-rt");
        Path out = LinearRegionFile.regionPath(dir, 0, 0);
        byte[] nbt = fakeNbtPayload();
        LinearRegionFile written = new LinearRegionFile(0, 0);
        written.putChunk(3, 4, nbt, 99);
        written.write(out, 1);

        LinearRegionFile read = LinearRegionFile.read(out, 0, 0);
        byte[] roundTrip = read.getChunk(3, 4);
        assertNotNull(roundTrip);
        assertArrayEquals(nbt, roundTrip);

        Files.deleteIfExists(out);
        Files.deleteIfExists(dir);
    }

    @Test
    public void mcaToLinearPreservesNbtEquality() throws Exception {
        Path dir = Files.createTempDirectory("wp-mca-linear");
        Path mca = dir.resolve("r.0.0.mca");
        byte[] nbt = fakeNbtPayload();

        try (RegionFile region = new RegionFile(mca.toFile(), false)) {
            try (DataOutputStream out = region.getChunkDataOutputStream(5, 6)) {
                out.write(nbt);
            }
        }

        Path linear = dir.resolve("r.0.0.linear");
        McaToLinearConverter.convert(mca, linear, 1);
        LinearRegionFile loaded = LinearRegionFile.read(linear, 0, 0);
        byte[] fromLinear = loaded.getChunk(5, 6);
        assertNotNull(fromLinear);
        assertArrayEquals("Linear slot must store uncompressed NBT equal to MCA payload", nbt, fromLinear);
        // Ensure we did not store zlib (zlib magic 0x78 is common but NBT compound starts with 0x0a)
        assertTrue("payload should look like NBT compound tag", fromLinear[0] == 0x0a || Arrays.equals(nbt, fromLinear));

        Files.deleteIfExists(linear);
        Files.deleteIfExists(mca);
        Files.deleteIfExists(dir);
    }

    /** Minimal named empty compound NBT: TAG_Compound "" then TAG_End. */
    private static byte[] fakeNbtPayload() {
        return new byte[]{
                0x0a, // TAG_Compound
                0x00, 0x00, // empty name
                0x00  // TAG_End
        };
    }
}
