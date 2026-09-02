package org.pepsoft.worldpainter.spatial.analysis;

/**
 * QGIS-style raster topographic terrain analysis algorithms.
 */
public final class TerrainAnalysisWorkbench {
    private TerrainAnalysisWorkbench() {}

    public static float[][] computeSlope(float[][] h, int w, int height) {
        float[][] slope = new float[w][height];
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < height - 1; z++) {
                float dz_dx = ((h[x+1][z-1] + 2*h[x+1][z] + h[x+1][z+1]) - (h[x-1][z-1] + 2*h[x-1][z] + h[x-1][z+1])) / 8.0f;
                float dz_dy = ((h[x-1][z+1] + 2*h[x][z+1] + h[x+1][z+1]) - (h[x-1][z-1] + 2*h[x][z-1] + h[x+1][z-1])) / 8.0f;
                float rise = (float) Math.sqrt(dz_dx * dz_dx + dz_dy * dz_dy);
                slope[x][z] = (float) Math.toDegrees(Math.atan(rise));
            }
        }
        return slope;
    }

    public static float[][] computeAspect(float[][] h, int w, int height) {
        float[][] aspect = new float[w][height];
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < height - 1; z++) {
                float dz_dx = ((h[x+1][z-1] + 2*h[x+1][z] + h[x+1][z+1]) - (h[x-1][z-1] + 2*h[x-1][z] + h[x-1][z+1])) / 8.0f;
                float dz_dy = ((h[x-1][z+1] + 2*h[x][z+1] + h[x+1][z+1]) - (h[x-1][z-1] + 2*h[x][z-1] + h[x+1][z-1])) / 8.0f;
                if (dz_dx == 0 && dz_dy == 0) {
                    aspect[x][z] = -1.0f; // Flat
                } else {
                    float deg = (float) Math.toDegrees(Math.atan2(dz_dy, -dz_dx));
                    if (deg < 0) deg += 360f;
                    aspect[x][z] = deg;
                }
            }
        }
        return aspect;
    }

    public static float[][] computeCurvature(float[][] h, int w, int height) {
        float[][] curvature = new float[w][height];
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < height - 1; z++) {
                // Laplacian second derivative: positive for ridge/crest, negative for valley/trough
                float laplacian = (h[x+1][z] + h[x-1][z] + h[x][z+1] + h[x][z-1]) - (4.0f * h[x][z]);
                curvature[x][z] = -laplacian;
            }
        }
        return curvature;
    }

    public static float[][] computeRuggedness(float[][] h, int w, int height) {
        float[][] tri = new float[w][height];
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < height - 1; z++) {
                float center = h[x][z];
                float sumSq = 0f;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        float diff = h[x+dx][z+dz] - center;
                        sumSq += diff * diff;
                    }
                }
                tri[x][z] = (float) Math.sqrt(sumSq / 8.0f);
            }
        }
        return tri;
    }
}
