package org.pepsoft.minecraft.compression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * JDK zlib compressor for MCA chunks. Default matches upstream {@link Deflater} behaviour
 * ({@link Deflater#DEFAULT_COMPRESSION}). Fast path: {@code -Dorg.pepsoft.worldpainter.deflateLevel=1}.
 */
public final class JdkChunkCompressor implements ChunkCompressor {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdkChunkCompressor.class);
    private final int level;

    public JdkChunkCompressor(int level) {
        if (level == Deflater.DEFAULT_COMPRESSION) {
            this.level = Deflater.DEFAULT_COMPRESSION;
        } else {
            this.level = Math.max(Deflater.NO_COMPRESSION, Math.min(Deflater.BEST_COMPRESSION, level));
        }
    }

    @Override
    public byte[] compressZlib(byte[] raw, int offset, int length) throws IOException {
        Deflater deflater = new Deflater(level);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, length / 2));
            try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
                dos.write(raw, offset, length);
            }
            return baos.toByteArray();
        } finally {
            deflater.end();
        }
    }

    @Override
    public String name() {
        return "jdk-deflater-" + level;
    }

    static int configuredLevel() {
        String prop = System.getProperty("org.pepsoft.worldpainter.deflateLevel");
        if (prop != null) {
            try {
                return Integer.parseInt(prop.trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid org.pepsoft.worldpainter.deflateLevel={}, using DEFAULT_COMPRESSION", prop);
            }
        }
        return Deflater.DEFAULT_COMPRESSION;
    }
}
