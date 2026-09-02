package org.pepsoft.worldpainter.view;

import org.junit.Test;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

public class TilePyramidCacheTest {

    @Test
    public void testLodSelectionByWpZoom() {
        assertEquals(0, TilePyramidCache.selectLodForZoom(0));
        assertEquals(1, TilePyramidCache.selectLodForZoom(-1));
        assertEquals(2, TilePyramidCache.selectLodForZoom(-2));
        assertEquals(4, TilePyramidCache.selectLodForZoom(-8));
    }

    @Test
    public void testLodSelectionByZoomScale() {
        assertEquals(0, TilePyramidCache.selectLodForZoom(1.0));
        assertEquals(0, TilePyramidCache.selectLodForZoom(0.8));
        assertEquals(1, TilePyramidCache.selectLodForZoom(0.5));
        assertEquals(2, TilePyramidCache.selectLodForZoom(0.25));
        assertEquals(3, TilePyramidCache.selectLodForZoom(0.1));
        assertEquals(4, TilePyramidCache.selectLodForZoom(0.05));
    }

    @Test
    public void testPyramidGenerationAndDownsampling() {
        TilePyramidCache cache = new TilePyramidCache(100);
        // Create 128x128 dummy tile
        BufferedImage tile = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        cache.putTileImage(0, 0, tile);

        // LOD 0 -> 128x128
        BufferedImage lod0 = cache.getTileImage(0, 0, 0);
        assertNotNull(lod0);
        assertEquals(128, lod0.getWidth());
        assertEquals(128, lod0.getHeight());

        // LOD 1 -> 64x64
        BufferedImage lod1 = cache.getTileImage(0, 0, 1);
        assertNotNull(lod1);
        assertEquals(64, lod1.getWidth());
        assertEquals(64, lod1.getHeight());

        // LOD 2 -> 32x32
        BufferedImage lod2 = cache.getTileImage(0, 0, 2);
        assertNotNull(lod2);
        assertEquals(32, lod2.getWidth());
        assertEquals(32, lod2.getHeight());

        // LOD 4 -> 8x8
        BufferedImage lod4 = cache.getTileImage(0, 0, 4);
        assertNotNull(lod4);
        assertEquals(8, lod4.getWidth());
        assertEquals(8, lod4.getHeight());

        // Test invalidation
        cache.invalidate(0, 0);
        assertNull(cache.getTileImage(0, 0, 0));
    }
}
