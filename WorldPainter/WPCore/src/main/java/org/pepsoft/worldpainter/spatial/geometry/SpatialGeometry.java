package org.pepsoft.worldpainter.spatial.geometry;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.*;

public final class SpatialGeometry {
    private SpatialGeometry() {}

    public static class SpatialPoint implements Serializable {
        public final double x, z, y;
        public SpatialPoint(double x, double z) { this(x, z, 0.0); }
        public SpatialPoint(double x, double z, double y) { this.x = x; this.z = z; this.y = y; }
        public double distance(SpatialPoint o) {
            double dx = x - o.x, dz = z - o.z;
            return Math.sqrt(dx * dx + dz * dz);
        }
        private static final long serialVersionUID = 1L;
    }

    public static class SpatialSpline implements Serializable {
        private final List<SpatialPoint> controlPoints = new ArrayList<>();
        public SpatialSpline(List<SpatialPoint> points) {
            if (points != null) this.controlPoints.addAll(points);
        }
        public List<SpatialPoint> getPoints() { return Collections.unmodifiableList(controlPoints); }
        public int size() { return controlPoints.size(); }
        public double getTotalLength() {
            double len = 0;
            for (int i = 0; i < controlPoints.size() - 1; i++) {
                len += controlPoints.get(i).distance(controlPoints.get(i + 1));
            }
            return len;
        }
        private static final long serialVersionUID = 1L;
    }

    public static class SpatialPolygon implements Serializable {
        private final List<SpatialPoint> vertices = new ArrayList<>();
        public SpatialPolygon(List<SpatialPoint> vertices) {
            if (vertices != null) this.vertices.addAll(vertices);
        }
        public List<SpatialPoint> getVertices() { return Collections.unmodifiableList(vertices); }
        public boolean contains(double px, double pz) {
            boolean inside = false;
            int n = vertices.size();
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = vertices.get(i).x, zi = vertices.get(i).z;
                double xj = vertices.get(j).x, zj = vertices.get(j).z;
                if (((zi > pz) != (zj > pz)) && (px < (xj - xi) * (pz - zi) / (zj - zi) + xi)) {
                    inside = !inside;
                }
            }
            return inside;
        }
        private static final long serialVersionUID = 1L;
    }

    public static class SpatialCorridor implements Serializable {
        public final SpatialSpline centerLine;
        public final double halfWidth;
        public SpatialCorridor(SpatialSpline centerLine, double halfWidth) {
            this.centerLine = centerLine;
            this.halfWidth = Math.max(0.5, halfWidth);
        }
        public boolean contains(double px, double pz) {
            return distanceTo(px, pz) <= halfWidth;
        }
        public double distanceTo(double px, double pz) {
            double minDist = Double.MAX_VALUE;
            List<SpatialPoint> pts = centerLine.getPoints();
            for (int i = 0; i < pts.size() - 1; i++) {
                minDist = Math.min(minDist, distToSegment(px, pz, pts.get(i), pts.get(i + 1)));
            }
            return minDist;
        }
        private double distToSegment(double px, double pz, SpatialPoint a, SpatialPoint b) {
            double dx = b.x - a.x, dz = b.z - a.z;
            double lenSq = dx * dx + dz * dz;
            if (lenSq == 0) return a.distance(new SpatialPoint(px, pz));
            double t = Math.max(0, Math.min(1, ((px - a.x) * dx + (pz - a.z) * dz) / lenSq));
            double projX = a.x + t * dx;
            double projZ = a.z + t * dz;
            double diffX = px - projX, diffZ = pz - projZ;
            return Math.sqrt(diffX * diffX + diffZ * diffZ);
        }
        private static final long serialVersionUID = 1L;
    }
}
