package org.pepsoft.worldpainter.gpu;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GpuTileTextureAtlasTest {
    @Test
    public void lruPutGetInvalidate() {
        GpuTileTextureAtlas atlas = new GpuTileTextureAtlas(2);
        int[] a = new int[GpuTileTextureAtlas.TILE_PIXELS];
        a[0] = 0xff0000;
        int[] b = new int[GpuTileTextureAtlas.TILE_PIXELS];
        b[0] = 0x00ff00;
        atlas.put(0, 0, a);
        atlas.put(1, 0, b);
        assertEquals(0xff0000, atlas.get(0, 0)[0]);
        atlas.put(2, 0, a); // evict eldest
        assertEquals(2, atlas.size());
        atlas.invalidate(1, 0);
        assertNull(atlas.get(1, 0));
    }
}
