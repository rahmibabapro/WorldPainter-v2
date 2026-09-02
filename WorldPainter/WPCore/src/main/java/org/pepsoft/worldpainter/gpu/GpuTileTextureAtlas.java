package org.pepsoft.worldpainter.gpu;

import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CPU-side tile texture cache that the LWJGL viewport uploads from.
 * Stores ARGB {@code int[128*128]} buffers keyed by tile coords; LRU capped.
 */
public final class GpuTileTextureAtlas {
    public static final int TILE_PIXELS = 128 * 128;

    private final int maxEntries;
    private final LinkedHashMap<Long, int[]> lru;

    public GpuTileTextureAtlas(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.lru = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
                return size() > GpuTileTextureAtlas.this.maxEntries;
            }
        };
    }

    public static long key(int tileX, int tileY) {
        return (((long) tileX) << 32) ^ (tileY & 0xffffffffL);
    }

    public synchronized void put(int tileX, int tileY, int[] argb) {
        Objects.requireNonNull(argb, "argb");
        if (argb.length != TILE_PIXELS) {
            throw new IllegalArgumentException("expected " + TILE_PIXELS + " pixels");
        }
        lru.put(key(tileX, tileY), argb.clone());
    }

    public synchronized int[] get(int tileX, int tileY) {
        return lru.get(key(tileX, tileY));
    }

    public synchronized void invalidate(int tileX, int tileY) {
        lru.remove(key(tileX, tileY));
    }

    public synchronized void invalidateAll() {
        lru.clear();
    }

    public synchronized int size() {
        return lru.size();
    }

    public synchronized void putImage(Point tile, int[] argb) {
        put(tile.x, tile.y, argb);
    }
}
