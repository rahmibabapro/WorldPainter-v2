package org.pepsoft.worldpainter.view;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Multi-resolution mipmap cache for zoomed-out viewport tiles.
 * LOD levels are derived from a LOD0 image; LRU-bounded. Not a guarantee of any FPS target.
 */
public class TilePyramidCache {
    public static final int MAX_LOD = 4; // LOD 0 (1:1) .. 4 (1:16)

    private final int maxCachedTiles;
    private final LinkedHashMap<String, BufferedImage[]> pyramidMap;
    private final ReentrantLock lock = new ReentrantLock();

    public TilePyramidCache(int maxCachedTiles) {
        this.maxCachedTiles = Math.max(64, maxCachedTiles);
        this.pyramidMap = new LinkedHashMap<>(256, 0.75f, true);
    }

    /**
     * Map WorldPainter zoom (0 = 1:1, -1 = 1:2, -2 = 1:4, …) to pyramid LOD index.
     */
    public static int selectLodForZoom(int zoom) {
        if (zoom >= 0) {
            return 0;
        }
        return Math.min(MAX_LOD, -zoom);
    }

    /** Legacy double zoom helper used by unit tests. */
    public static int selectLodForZoom(double zoom) {
        if (zoom >= 0.75) return 0;
        if (zoom >= 0.35) return 1;
        if (zoom >= 0.18) return 2;
        if (zoom >= 0.08) return 3;
        return 4;
    }

    public void putTileImage(int tileX, int tileZ, BufferedImage lod0Image) {
        if (lod0Image == null) {
            return;
        }
        final String key = key(tileX, tileZ);
        final BufferedImage[] pyramid = new BufferedImage[MAX_LOD + 1];
        pyramid[0] = lod0Image;

        final int w = lod0Image.getWidth();
        final int h = lod0Image.getHeight();
        for (int lod = 1; lod <= MAX_LOD; lod++) {
            final int targetW = Math.max(1, w >> lod);
            final int targetH = Math.max(1, h >> lod);
            final BufferedImage downsampled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = downsampled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(lod0Image, 0, 0, targetW, targetH, null);
            g.dispose();
            pyramid[lod] = downsampled;
        }

        lock.lock();
        try {
            pyramidMap.put(key, pyramid);
            while (pyramidMap.size() > maxCachedTiles) {
                final Iterator<Map.Entry<String, BufferedImage[]>> it = pyramidMap.entrySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                } else {
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public BufferedImage getTileImage(int tileX, int tileZ, int lod) {
        lock.lock();
        try {
            final BufferedImage[] pyramid = pyramidMap.get(key(tileX, tileZ));
            if (pyramid == null) {
                return null;
            }
            final int clampedLod = Math.max(0, Math.min(MAX_LOD, lod));
            return pyramid[clampedLod];
        } finally {
            lock.unlock();
        }
    }

    public void invalidate(int tileX, int tileZ) {
        lock.lock();
        try {
            pyramidMap.remove(key(tileX, tileZ));
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            pyramidMap.clear();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return pyramidMap.size();
        } finally {
            lock.unlock();
        }
    }

    private static String key(int tileX, int tileZ) {
        return tileX + "," + tileZ;
    }
}
