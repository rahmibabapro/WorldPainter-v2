package org.pepsoft.minecraft.compression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the process-wide {@link ChunkCompressor} (libdeflate plugin or JDK fast path).
 */
public final class ChunkCompressors {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkCompressors.class);
    private static volatile ChunkCompressor DEFAULT;

    private ChunkCompressors() {
    }

    public static ChunkCompressor getDefault() {
        ChunkCompressor local = DEFAULT;
        if (local == null) {
            synchronized (ChunkCompressors.class) {
                local = DEFAULT;
                if (local == null) {
                    int level = JdkChunkCompressor.configuredLevel();
                    local = LibdeflateChunkCompressor.tryLoad(level)
                            .orElseGet(() -> ChunkCompressor.jdk(level));
                    DEFAULT = local;
                    LOGGER.info("Chunk compressor: {}", local.name());
                }
            }
        }
        return local;
    }

    /** Test hook. */
    public static void resetForTests() {
        synchronized (ChunkCompressors.class) {
            DEFAULT = null;
        }
    }
}
