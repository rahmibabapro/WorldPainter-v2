package org.pepsoft.worldpainter.memory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OffHeapShortGridTest {
    @Test
    public void roundTrip() {
        try (OffHeapShortGrid grid = new OffHeapShortGrid(4)) {
            grid.set(0, (short) 1);
            grid.set(1, (short) 2);
            grid.set(2, (short) 3);
            grid.set(3, (short) 4);
            assertEquals(2, grid.get(1));
            short[] heap = new short[4];
            grid.copyTo(heap);
            assertEquals(3, heap[2]);
        }
    }
}
