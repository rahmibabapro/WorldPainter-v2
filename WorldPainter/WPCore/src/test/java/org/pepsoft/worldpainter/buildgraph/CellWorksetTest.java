package org.pepsoft.worldpainter.buildgraph;

import org.junit.Test;
import java.awt.Rectangle;

import static org.junit.Assert.*;

public class CellWorksetTest {

    @Test
    public void testCellBoundsAndHalo() {
        // Cell (1, 2) with 128x128 core and 16 block halo
        CellWorkset workset = new CellWorkset(1, 2, 128, 128, 16);

        Rectangle core = workset.getCoreBounds();
        assertEquals(128, core.x);
        assertEquals(256, core.y);
        assertEquals(128, core.width);
        assertEquals(128, core.height);

        Rectangle padded = workset.getPaddedBounds();
        assertEquals(112, padded.x); // 128 - 16
        assertEquals(240, padded.y); // 256 - 16
        assertEquals(160, padded.width); // 128 + 32
        assertEquals(160, padded.height); // 128 + 32

        assertTrue(workset.isInCore(150, 300));
        assertFalse(workset.isInCore(120, 250)); // In halo, not core
        assertTrue(padded.contains(120, 250));
    }
}
