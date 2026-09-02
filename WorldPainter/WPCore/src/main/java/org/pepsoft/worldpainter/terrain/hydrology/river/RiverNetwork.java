package org.pepsoft.worldpainter.terrain.hydrology.river;

import org.pepsoft.worldpainter.spatial.geometry.SpatialGeometry.SpatialPoint;

import java.io.Serializable;
import java.util.*;

/**
 * Directed topological River Network graph carrying Strahler order, discharge, and hydraulic geometry.
 */
public class RiverNetwork implements Serializable {

    public static class RiverReach implements Serializable {
        public final int id;
        public final SpatialPoint start;
        public final SpatialPoint end;
        public final int strahlerOrder;
        public final float drainageArea;
        public final float slope;
        public final float width;
        public final float depth;

        public RiverReach(int id, SpatialPoint start, SpatialPoint end, int strahlerOrder,
                          float drainageArea, float slope, float width, float depth) {
            this.id = id;
            this.start = start;
            this.end = end;
            this.strahlerOrder = strahlerOrder;
            this.drainageArea = drainageArea;
            this.slope = slope;
            this.width = width;
            this.depth = depth;
        }

        public double getLength() {
            return start.distance(end);
        }

        private static final long serialVersionUID = 1L;
    }

    private final List<RiverReach> reaches = new ArrayList<>();

    public void addReach(RiverReach reach) {
        if (reach != null) {
            reaches.add(reach);
        }
    }

    public List<RiverReach> getReaches() {
        return Collections.unmodifiableList(reaches);
    }

    public int size() {
        return reaches.size();
    }

    public int getMaxStrahlerOrder() {
        return reaches.stream().mapToInt(r -> r.strahlerOrder).max().orElse(1);
    }

    public double getTotalLength() {
        return reaches.stream().mapToDouble(RiverReach::getLength).sum();
    }

    private static final long serialVersionUID = 1L;
}
