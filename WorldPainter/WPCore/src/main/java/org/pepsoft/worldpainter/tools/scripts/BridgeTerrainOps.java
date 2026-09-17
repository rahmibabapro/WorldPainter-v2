package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.layers.Layer;

/**
 * Pure terrain helpers used by the MCP bridge (testable without Swing).
 */
public final class BridgeTerrainOps {
    public static final int MAX_PAINT_CELLS = 512 * 512;
    public static final int MAX_SCULPT_RADIUS = 512;
    public static final int MAX_SCRIPT_CHARS = 100_000;

    private BridgeTerrainOps() {}

    /** WorldPainter storey slope = rise/run; UI filters use degrees via tan. */
    public static float degreesToSlope(double degrees) {
        if (!Double.isFinite(degrees) || degrees <= 0) return 0f;
        double clamped = Math.min(89.0, degrees);
        return (float) Math.tan(Math.toRadians(clamped));
    }

    public static float slopeToDegrees(float slope) {
        if (!Float.isFinite(slope) || slope <= 0) return 0f;
        return (float) Math.toDegrees(Math.atan(slope));
    }

    public static int sculpt(Dimension dim, int cx, int cy, int radius, double amount, String mode) {
        if (radius <= 0) radius = 50;
        if (radius > MAX_SCULPT_RADIUS) throw new IllegalArgumentException("radius exceeds " + MAX_SCULPT_RADIUS);
        String m = mode == null ? "RAISE" : mode.toUpperCase(java.util.Locale.ROOT);
        int r2 = radius * radius;
        int modified = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int dist2 = dx * dx + dy * dy;
                if (dist2 > r2) continue;
                int x = cx + dx, y = cy + dy;
                if (!dim.isTilePresent(x >> 7, y >> 7)) continue;
                float h = dim.getHeightAt(x, y);
                if (!Float.isFinite(h)) continue;
                double d = Math.sqrt(dist2) / radius;
                double factor = 0.5 * (1.0 + Math.cos(Math.PI * d));
                float newH = h;
                switch (m) {
                    case "RAISE" -> newH = (float) (h + amount * factor);
                    case "LOWER" -> newH = (float) Math.max(dim.getMinHeight(), h - amount * factor);
                    case "SMOOTH" -> {
                        float avg = (dim.getHeightAt(x - 1, y) + dim.getHeightAt(x + 1, y)
                                + dim.getHeightAt(x, y - 1) + dim.getHeightAt(x, y + 1)) / 4f;
                        newH = (float) (h + (avg - h) * factor * 0.5);
                    }
                    case "PLATEAU" -> newH = (float) (h + (amount - h) * factor);
                    default -> throw new IllegalArgumentException("Unknown sculpt mode: " + mode);
                }
                if (newH != h) {
                    dim.setHeightAt(x, y, newH);
                    modified++;
                }
            }
        }
        return modified;
    }

    public static int paint(Dimension dim, int minX, int maxX, int minY, int maxY,
                            Terrain terrain, Layer layer,
                            double minHeight, double maxHeight,
                            double minSlopeDegrees, double maxSlopeDegrees) {
        long area = (long) (maxX - minX + 1) * (maxY - minY + 1);
        if (area > MAX_PAINT_CELLS) {
            throw new IllegalArgumentException("Paint bbox exceeds " + MAX_PAINT_CELLS + " cells");
        }
        float minSlope = degreesToSlope(minSlopeDegrees);
        float maxSlope = degreesToSlope(Math.max(minSlopeDegrees, maxSlopeDegrees));
        if (maxSlopeDegrees >= 89.5) maxSlope = Float.MAX_VALUE;
        int modified = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!dim.isTilePresent(x >> 7, y >> 7)) continue;
                float h = dim.getHeightAt(x, y);
                if (h < minHeight || h > maxHeight) continue;
                float slope = dim.getSlope(x, y);
                if (slope < minSlope || slope > maxSlope) continue;
                if (terrain != null) {
                    dim.setTerrainAt(x, y, terrain);
                    modified++;
                }
                if (layer != null) {
                    dim.setBitLayerValueAt(layer, x, y, true);
                    modified++;
                }
            }
        }
        return modified;
    }
}
