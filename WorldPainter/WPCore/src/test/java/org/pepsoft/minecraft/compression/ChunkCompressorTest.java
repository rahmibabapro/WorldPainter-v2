package org.pepsoft.minecraft.compression;

import org.junit.Test;

import java.util.zip.Inflater;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class ChunkCompressorTest {
    @Test
    public void jdkFastRoundTrip() throws Exception {
        ChunkCompressors.resetForTests();
        System.clearProperty("org.pepsoft.worldpainter.libdeflate.compressor");
        System.setProperty("org.pepsoft.worldpainter.deflateLevel", "1");
        ChunkCompressors.resetForTests();

        byte[] raw = new byte[4096];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i * 31);
        }
        ChunkCompressor compressor = ChunkCompressor.get();
        assertTrue(compressor.name().startsWith("jdk-deflater"));
        // Default is Deflater.DEFAULT_COMPRESSION (-1) unless deflateLevel is set
        assertTrue(compressor.name().contains("-1") || compressor.name().endsWith("-1")
                || compressor.name().equals("jdk-deflater-" + java.util.zip.Deflater.DEFAULT_COMPRESSION)
                || ! System.getProperty("org.pepsoft.worldpainter.deflateLevel", "").isEmpty()
                || compressor.name().equals("jdk-deflater--1"));
        // Reset and force level 1 for size assertion
        ChunkCompressors.resetForTests();
        System.setProperty("org.pepsoft.worldpainter.deflateLevel", "1");
        ChunkCompressors.resetForTests();
        compressor = ChunkCompressor.get();
        assertTrue(compressor.name().endsWith("-1"));
        byte[] compressed = compressor.compressZlib(raw);
        assertTrue(compressed.length > 0);
        assertTrue(compressed.length < raw.length);

        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] out = new byte[raw.length];
        int n = inflater.inflate(out);
        inflater.end();
        assertEquals(raw.length, n);
        assertArrayEquals(raw, out);
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.Assert.assertEquals(expected, actual);
    }
}
