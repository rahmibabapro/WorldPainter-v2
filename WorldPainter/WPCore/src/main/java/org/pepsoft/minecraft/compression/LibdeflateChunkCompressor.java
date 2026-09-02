package org.pepsoft.minecraft.compression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional native libdeflate hook. This build ships without a bundled native library;
 * when {@code -Dorg.pepsoft.worldpainter.libdeflate=} points at a future JNI adapter
 * class name implementing {@link ChunkCompressor}, it will be used. Otherwise empty.
 */
public final class LibdeflateChunkCompressor {
    private static final Logger LOGGER = LoggerFactory.getLogger(LibdeflateChunkCompressor.class);
    private static final AtomicReference<Optional<ChunkCompressor>> CACHE = new AtomicReference<>();

    private LibdeflateChunkCompressor() {
    }

    public static Optional<ChunkCompressor> tryLoad(int level) {
        Optional<ChunkCompressor> cached = CACHE.get();
        if (cached != null) {
            return cached;
        }
        Optional<ChunkCompressor> loaded = load(level);
        CACHE.compareAndSet(null, loaded);
        return CACHE.get();
    }

    private static Optional<ChunkCompressor> load(int level) {
        String className = System.getProperty("org.pepsoft.worldpainter.libdeflate.compressor");
        if (className == null || className.isBlank()) {
            LOGGER.debug("No libdeflate compressor class configured; using JDK Deflater");
            return Optional.empty();
        }
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getConstructor(int.class).newInstance(level);
            if (instance instanceof ChunkCompressor compressor) {
                LOGGER.info("Using pluggable compressor {} (level {})", compressor.name(), level);
                return Optional.of(compressor);
            }
            LOGGER.warn("{} does not implement ChunkCompressor", className);
            return Optional.empty();
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to load libdeflate compressor {}: {}", className, e.toString());
            return Optional.empty();
        }
    }
}
