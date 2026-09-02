package org.pepsoft.worldpainter.memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Experimental direct {@link ByteBuffer} short grid. Not used by {@code Tile} storage.
 * Enable exploration only via {@code -Dorg.pepsoft.worldpainter.offHeapTiles=true} in callers that opt in.
 */
public final class OffHeapShortGrid implements AutoCloseable {
    private final ByteBuffer buffer;
    private final ShortBuffer shorts;
    private final int length;
    private boolean closed;

    public OffHeapShortGrid(int length) {
        this.length = length;
        this.buffer = ByteBuffer.allocateDirect(length * Short.BYTES).order(ByteOrder.nativeOrder());
        this.shorts = buffer.asShortBuffer();
    }

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("org.pepsoft.worldpainter.offHeapTiles", "false"));
    }

    public int length() {
        return length;
    }

    public short get(int index) {
        ensureOpen();
        return shorts.get(index);
    }

    public void set(int index, short value) {
        ensureOpen();
        shorts.put(index, value);
    }

    public void copyFrom(short[] heap) {
        ensureOpen();
        shorts.clear();
        shorts.put(heap, 0, Math.min(heap.length, length));
        shorts.clear();
    }

    public void copyTo(short[] heap) {
        ensureOpen();
        shorts.clear();
        shorts.get(heap, 0, Math.min(heap.length, length));
        shorts.clear();
    }

    @Override
    public void close() {
        closed = true;
        // Direct buffers are GC'd; explicit clear helps signal unused
        buffer.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("OffHeapShortGrid closed");
        }
    }
}
