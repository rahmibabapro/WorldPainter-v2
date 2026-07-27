package org.pepsoft.worldpainter.threedeeview;

import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.threedeeview.Tile3DRenderer.LayerVisibilityMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory (and optional on-disk) cache for rendered 3D tile images.
 */
public class Tile3DRenderCache {
    public Tile3DRenderCache() {
        this(false);
    }

    public Tile3DRenderCache(boolean diskCacheEnabled) {
        this.diskCacheEnabled = diskCacheEnabled;
        Path cacheDir = null;
        if (diskCacheEnabled) {
            try {
                cacheDir = Files.createTempDirectory("worldpainter-3d-cache");
            } catch (IOException e) {
                logger.warn("Could not create 3D disk cache directory: {}", e.getMessage());
            }
        }
        this.diskCacheDir = cacheDir;
    }

    public static final class CacheKey {
        public CacheKey(int tileX, int tileY, int rotation, LayerVisibilityMode layerVisibility, Set<Layer> hiddenLayers,
                        Tile3DRendererOptions options, long renderRevision) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.rotation = rotation;
            this.layerVisibility = layerVisibility;
            this.hiddenLayers = hiddenLayers;
            this.options = options;
            this.renderRevision = renderRevision;
        }

        static CacheKey of(Tile tile, int rotation, LayerVisibilityMode layerVisibility, Set<Layer> hiddenLayers,
                           Tile3DRendererOptions options) {
            return new CacheKey(tile.getX(), tile.getY(), rotation, layerVisibility, hiddenLayers, options, tile.getRenderRevision());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CacheKey)) {
                return false;
            }
            CacheKey that = (CacheKey) o;
            return tileX == that.tileX
                    && tileY == that.tileY
                    && rotation == that.rotation
                    && renderRevision == that.renderRevision
                    && layerVisibility == that.layerVisibility
                    && Objects.equals(hiddenLayers, that.hiddenLayers)
                    && options.optionsHash(layerVisibility, hiddenLayers) == that.options.optionsHash(that.layerVisibility, that.hiddenLayers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tileX, tileY, rotation, renderRevision, layerVisibility, hiddenLayers,
                    options.optionsHash(layerVisibility, hiddenLayers));
        }

        long diskHash() {
            return Objects.hash(tileX, tileY, rotation, renderRevision, layerVisibility, hiddenLayers,
                    options.optionsHash(layerVisibility, hiddenLayers));
        }

        private final int tileX, tileY, rotation;
        private final LayerVisibilityMode layerVisibility;
        private final Set<Layer> hiddenLayers;
        private final Tile3DRendererOptions options;
        private final long renderRevision;
    }

    public synchronized BufferedImage get(CacheKey key) {
        BufferedImage image = memoryCache.get(key);
        if (image != null) {
            return image;
        }
        if (diskCacheEnabled && diskCacheDir != null) {
            Path file = diskCacheDir.resolve(Long.toHexString(key.diskHash()) + ".png");
            if (Files.isRegularFile(file)) {
                try {
                    image = ImageIO.read(file.toFile());
                    if (image != null) {
                        putMemory(key, image);
                        return image;
                    }
                } catch (IOException e) {
                    logger.debug("Failed to read 3D tile from disk cache: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    public synchronized void put(CacheKey key, BufferedImage image) {
        putMemory(key, image);
        if (diskCacheEnabled && diskCacheDir != null && image != null) {
            Path file = diskCacheDir.resolve(Long.toHexString(key.diskHash()) + ".png");
            try {
                ImageIO.write(image, "png", file.toFile());
            } catch (IOException e) {
                logger.debug("Failed to write 3D tile to disk cache: {}", e.getMessage());
            }
        }
    }

    public synchronized void invalidateTile(int tileX, int tileY) {
        Iterator<Map.Entry<CacheKey, BufferedImage>> iterator = memoryCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<CacheKey, BufferedImage> entry = iterator.next();
            if (entry.getKey().tileX == tileX && entry.getKey().tileY == tileY) {
                memoryBytes -= estimateImageBytes(entry.getValue());
                iterator.remove();
            }
        }
    }

    public synchronized void clear() {
        memoryCache.clear();
        memoryBytes = 0L;
    }

    private void putMemory(CacheKey key, BufferedImage image) {
        if (image == null) {
            return;
        }
        final long imageBytes = estimateImageBytes(image);
        BufferedImage previous = memoryCache.remove(key);
        if (previous != null) {
            memoryBytes -= estimateImageBytes(previous);
        }
        while ((! memoryCache.isEmpty()) && (memoryBytes + imageBytes > MAX_MEMORY_BYTES)) {
            Map.Entry<CacheKey, BufferedImage> eldest = memoryCache.entrySet().iterator().next();
            memoryCache.remove(eldest.getKey());
            memoryBytes -= estimateImageBytes(eldest.getValue());
        }
        memoryCache.put(key, image);
        memoryBytes += imageBytes;
    }

    static long estimateImageBytes(BufferedImage image) {
        if (image == null) {
            return 0L;
        }
        final DataBuffer buffer = image.getRaster().getDataBuffer();
        return (long) buffer.getSize() * (long) DataBuffer.getDataTypeSize(buffer.getDataType()) / 8L;
    }

    private final boolean diskCacheEnabled;
    private final Path diskCacheDir;
    private final Map<CacheKey, BufferedImage> memoryCache = new LinkedHashMap<>(64, 0.75f, true);
    private long memoryBytes;

    /** ~192 MB in-memory budget for rendered 3D tiles (replaces fixed 512-entry cap). */
    private static final long MAX_MEMORY_BYTES = 192L * 1024L * 1024L;
    private static final Logger logger = LoggerFactory.getLogger(Tile3DRenderCache.class);
}
