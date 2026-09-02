package org.pepsoft.minecraft.compression;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Buffers chunk NBT then zlib-compresses with {@link ChunkCompressor} on close
 * (replaces {@link java.util.zip.DeflaterOutputStream} for MCA writes).
 */
public final class FastDeflaterOutputStream extends FilterOutputStream {
    private final GrowableByteArray buffer = new GrowableByteArray(8192);
    private boolean closed;

    public FastDeflaterOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        ensureOpen();
        buffer.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        buffer.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        byte[] compressed = ChunkCompressor.get().compressZlib(buffer.array(), 0, buffer.size());
        out.write(compressed);
        out.close();
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("stream closed");
        }
    }

    private static final class GrowableByteArray {
        private byte[] buf;
        private int size;

        GrowableByteArray(int initial) {
            buf = new byte[initial];
        }

        void write(int b) {
            ensure(1);
            buf[size++] = (byte) b;
        }

        void write(byte[] b, int off, int len) {
            ensure(len);
            System.arraycopy(b, off, buf, size, len);
            size += len;
        }

        byte[] array() {
            return buf;
        }

        int size() {
            return size;
        }

        private void ensure(int more) {
            int need = size + more;
            if (need > buf.length) {
                int newCap = Math.max(buf.length * 2, need);
                byte[] n = new byte[newCap];
                System.arraycopy(buf, 0, n, 0, size);
                buf = n;
            }
        }
    }
}
