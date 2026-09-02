package org.pepsoft.minecraft.compression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Pluggable zlib (MCA-compatible) chunk compressor. Default uses JDK {@link Deflater}
 * at a tunable level; native libdeflate can be selected when available.
 */
public interface ChunkCompressor {
    /**
     * Compress {@code raw} as zlib-wrapped deflate (Minecraft region version 2).
     */
    byte[] compressZlib(byte[] raw, int offset, int length) throws IOException;

    default byte[] compressZlib(byte[] raw) throws IOException {
        return compressZlib(raw, 0, raw.length);
    }

    String name();

    static ChunkCompressor get() {
        return ChunkCompressors.getDefault();
    }

    /**
     * JDK Deflater at {@code level} (0–9). Level 1 is the export fast path.
     */
    static ChunkCompressor jdk(int level) {
        return new JdkChunkCompressor(level);
    }
}
