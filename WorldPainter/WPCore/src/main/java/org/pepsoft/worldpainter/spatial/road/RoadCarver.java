package org.pepsoft.worldpainter.spatial.road;

import org.pepsoft.worldpainter.spatial.geometry.SpatialGeometry.SpatialPoint;
import org.pepsoft.worldpainter.spatial.geometry.SpatialGeometry.SpatialSpline;

import java.util.List;

/**
 * Applies engineering cut-and-fill grading and batter slopes along road splines.
 */
public final class RoadCarver {
    private RoadCarver() {}

    public static void gradeRoad(float[][] heights, int w, int h, SpatialSpline spline, RoadProfile profile) {
        if (spline == null || spline.size() < 2) return;

        float halfRoad = profile.roadWidth / 2.0f;
        float totalHalf = halfRoad + profile.shoulderWidth;
        List<SpatialPoint> pts = spline.getPoints();

        for (int i = 0; i < pts.size() - 1; i++) {
            SpatialPoint a = pts.get(i);
            SpatialPoint b = pts.get(i + 1);

            int minX = Math.max(0, (int) Math.floor(Math.min(a.x, b.x) - totalHalf));
            int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(a.x, b.x) + totalHalf));
            int minZ = Math.max(0, (int) Math.floor(Math.min(a.z, b.z) - totalHalf));
            int maxZ = Math.min(h - 1, (int) Math.ceil(Math.max(a.z, b.z) + totalHalf));

            double dx = b.x - a.x, dz = b.z - a.z;
            double lenSq = dx * dx + dz * dz;
            if (lenSq == 0) continue;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double t = Math.max(0, Math.min(1, ((x - a.x) * dx + (z - a.z) * dz) / lenSq));
                    double projX = a.x + t * dx;
                    double projZ = a.z + t * dz;
                    double dist = Math.sqrt((x - projX) * (x - projX) + (z - projZ) * (z - projZ));

                    if (dist <= totalHalf) {
                        float targetY = (float) (a.y + t * (b.y - a.y));
                        if (dist <= halfRoad) {
                            // Flat road surface
                            heights[x][z] = targetY;
                        } else {
                            // Shoulder batter slope transition
                            float factor = (float) ((dist - halfRoad) / profile.shoulderWidth);
                            heights[x][z] = targetY * (1.0f - factor) + heights[x][z] * factor;
                        }
                    }
                }
            }
        }
    }
}
