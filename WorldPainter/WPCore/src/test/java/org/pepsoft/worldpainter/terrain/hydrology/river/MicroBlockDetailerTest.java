package org.pepsoft.worldpainter.terrain.hydrology.river;

import org.junit.Test;

import static org.junit.Assert.*;

public class MicroBlockDetailerTest {

    @Test
    public void testSnapToHalfBlock() {
        assertEquals(64.0f, MicroBlockDetailer.snapToHalfBlock(64.1f), 0.01f);
        assertEquals(64.5f, MicroBlockDetailer.snapToHalfBlock(64.4f), 0.01f);
        assertEquals(64.5f, MicroBlockDetailer.snapToHalfBlock(64.6f), 0.01f);
        assertEquals(65.0f, MicroBlockDetailer.snapToHalfBlock(64.9f), 0.01f);
    }

    @Test
    public void testOctantMaskCalculation() {
        // Empty block (fraction 0.0) -> 0 filled
        boolean[] empty = MicroBlockDetailer.computeOctantSolidMask(0.0f);
        assertEquals(8, empty.length);
        assertFalse(empty[0]);

        // Half block (fraction 0.5) -> 4 filled sub-voxels
        boolean[] half = MicroBlockDetailer.computeOctantSolidMask(0.5f);
        assertTrue(half[0]);
        assertTrue(half[3]);
        assertFalse(half[4]);

        // Full block (fraction 1.0) -> all 8 filled
        boolean[] full = MicroBlockDetailer.computeOctantSolidMask(1.0f);
        for (boolean b : full) {
            assertTrue(b);
        }
    }
}
