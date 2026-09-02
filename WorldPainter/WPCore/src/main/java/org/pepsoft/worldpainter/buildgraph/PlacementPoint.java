package org.pepsoft.worldpainter.buildgraph;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Universal spatial contract inspired by UE5 PCG Points.
 * Carries position, surface slope normal, density, radius, and procedural attributes.
 */
public class PlacementPoint implements Serializable {
    public final float x, y, z;
    public final float normalX, normalY, normalZ;
    public final float density;
    public final float radius;
    public final long seed;
    private final Map<String, Object> attributes;

    public PlacementPoint(float x, float y, float z, float normalX, float normalY, float normalZ,
                          float density, float radius, long seed) {
        this(x, y, z, normalX, normalY, normalZ, density, radius, seed, Collections.emptyMap());
    }

    public PlacementPoint(float x, float y, float z, float normalX, float normalY, float normalZ,
                          float density, float radius, long seed, Map<String, Object> attributes) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
        this.density = density;
        this.radius = radius;
        this.seed = seed;
        this.attributes = (attributes != null && !attributes.isEmpty()) ? new HashMap<>(attributes) : Collections.emptyMap();
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public double getSlopeDegrees() {
        // Dot product with up-vector (0, 1, 0)
        double cosAngle = Math.max(-1.0, Math.min(1.0, normalY));
        return Math.toDegrees(Math.acos(cosAngle));
    }

    private static final long serialVersionUID = 1L;
}
