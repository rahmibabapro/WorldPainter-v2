package org.pepsoft.worldpainter.spatial.module;

import java.io.Serializable;

/**
 * Connection port on a World Module (e.g. road-west, river-in, gate).
 */
public class ModulePort implements Serializable {
    public enum PortType { ROAD, RIVER, TRAIL, GATEWAY }

    public final String portId;
    public final PortType portType;
    public final int relX, relZ;
    public final float relY;
    public final double headingDegrees; // Facing angle (0=North, 90=East, 180=South, 270=West)

    public ModulePort(String portId, PortType portType, int relX, int relZ, float relY, double headingDegrees) {
        this.portId = portId;
        this.portType = portType;
        this.relX = relX;
        this.relZ = relZ;
        this.relY = relY;
        this.headingDegrees = headingDegrees;
    }

    public boolean isCompatibleWith(ModulePort other) {
        if (other == null || this.portType != other.portType) return false;
        // Angles should be approximately opposite (diff ~ 180 degrees)
        double diff = Math.abs(this.headingDegrees - other.headingDegrees);
        double normalizedDiff = Math.abs(180.0 - diff);
        return normalizedDiff <= 15.0; // Within 15 degree tolerance
    }

    private static final long serialVersionUID = 1L;
}
