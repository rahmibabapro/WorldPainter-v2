package org.pepsoft.worldpainter.spatial.road;

import java.io.Serializable;

/**
 * Geometric and engineering profile for road/trail infrastructure.
 */
public class RoadProfile implements Serializable {
    public final float roadWidth;
    public final float shoulderWidth;
    public final float maxGradePercent;
    public final float batterSlopeDegrees;
    public final String surfaceMaterial;

    public RoadProfile(float roadWidth, float shoulderWidth, float maxGradePercent, float batterSlopeDegrees, String surfaceMaterial) {
        this.roadWidth = Math.max(1.0f, roadWidth);
        this.shoulderWidth = Math.max(0.5f, shoulderWidth);
        this.maxGradePercent = maxGradePercent;
        this.batterSlopeDegrees = batterSlopeDegrees;
        this.surfaceMaterial = surfaceMaterial != null ? surfaceMaterial : "minecraft:dirt_path";
    }

    public float getTotalCorridorWidth() {
        return roadWidth + (shoulderWidth * 2.0f);
    }

    private static final long serialVersionUID = 1L;
}
