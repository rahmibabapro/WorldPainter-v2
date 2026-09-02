package org.pepsoft.worldpainter.exporting;

import org.pepsoft.worldpainter.Dimension;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

/**
 * Preflight checks for height-map export (#535). Accounts for bytes-per-sample, not only pixel count.
 */
public final class HeightMapSizeCheck {
    /** Soft limit before preferring tiled TIFF streaming (256 MiB raster). */
    public static final long MAX_SINGLE_BUFFER_BYTES = 256L * 1024L * 1024L;
    /** Hard limit: Java DataBuffer int index. */
    public static final long MAX_PIXELS = Integer.MAX_VALUE;

    private HeightMapSizeCheck() {
    }

    public static long pixelCount(Dimension dimension) {
        final long w = (long) dimension.getWidth() * TILE_SIZE;
        final long h = (long) dimension.getHeight() * TILE_SIZE;
        if (w <= 0 || h <= 0) {
            return 0;
        }
        return w * h;
    }

    public static int bytesPerSample(HeightMapExporter.Format format, int bitsRequired) {
        return switch (format) {
            case FLOAT_NORMALISED, FLOAT_ONE_TO_ONE -> 4;
            case INTEGER_LOW_RESOLUTION, INTEGER_HIGH_RESOLUTION -> {
                if (bitsRequired > 16) {
                    yield 4;
                } else if (bitsRequired > 8) {
                    yield 2;
                } else {
                    yield 1;
                }
            }
        };
    }

    public static long estimatedBytes(Dimension dimension, HeightMapExporter.Format format, int bitsRequired) {
        return pixelCount(dimension) * bytesPerSample(format, bitsRequired);
    }

    public static boolean fitsInJavaArray(Dimension dimension) {
        final long pixels = pixelCount(dimension);
        return pixels > 0 && pixels <= MAX_PIXELS;
    }

    /** True when a single {@link java.awt.image.BufferedImage} is acceptable. */
    public static boolean canAllocateSingleBuffer(Dimension dimension, HeightMapExporter.Format format, int bitsRequired) {
        if (! fitsInJavaArray(dimension)) {
            return false;
        }
        final long bytes = estimatedBytes(dimension, format, bitsRequired);
        final long free = Runtime.getRuntime().maxMemory() - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        return bytes <= MAX_SINGLE_BUFFER_BYTES && bytes <= Math.max(64L * 1024L * 1024L, free / 2);
    }

    public static String describeTooLarge(Dimension dimension, HeightMapExporter.Format format, int bitsRequired) {
        final long w = (long) dimension.getWidth() * TILE_SIZE;
        final long h = (long) dimension.getHeight() * TILE_SIZE;
        final long pixels = pixelCount(dimension);
        final int bpp = bytesPerSample(format, bitsRequired);
        final long bytes = estimatedBytes(dimension, format, bitsRequired);
        return String.format(
                "Height map is too large for a single in-memory image.%n"
                        + "Size: %d × %d pixels (%d tiles × %d).%n"
                        + "Estimated buffer: %.1f GiB (%d bytes/sample).%n"
                        + "Use TIFF export (tiled streaming) or export a smaller region.",
                w, h, dimension.getWidth() * dimension.getHeight(), TILE_SIZE,
                bytes / (1024.0 * 1024.0 * 1024.0), bpp);
    }
}
