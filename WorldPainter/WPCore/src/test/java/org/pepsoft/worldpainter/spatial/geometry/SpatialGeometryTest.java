package org.pepsoft.worldpainter.spatial.geometry;

import org.junit.Test;
import org.pepsoft.worldpainter.spatial.geometry.SpatialGeometry.*;

import java.util.List;

import static org.junit.Assert.*;

public class SpatialGeometryTest {

    @Test
    public void testPolygonContainment() {
        // Square polygon from (0,0) to (10,10)
        SpatialPolygon poly = new SpatialPolygon(List.of(
                new SpatialPoint(0, 0),
                new SpatialPoint(10, 0),
                new SpatialPoint(10, 10),
                new SpatialPoint(0, 10)
        ));

        assertTrue(poly.contains(5, 5));
        assertTrue(poly.contains(1, 1));
        assertFalse(poly.contains(15, 5));
        assertFalse(poly.contains(-2, 5));
    }

    @Test
    public void testCorridorDistanceAndContainment() {
        // Line from (0, 0) to (100, 0), width 10 (half-width 5)
        SpatialSpline road = new SpatialSpline(List.of(
                new SpatialPoint(0, 0),
                new SpatialPoint(100, 0)
        ));
        SpatialCorridor corridor = new SpatialCorridor(road, 5.0);

        assertTrue(corridor.contains(50, 2));
        assertTrue(corridor.contains(50, -4));
        assertFalse(corridor.contains(50, 10)); // Outside 5-block width

        assertEquals(0.0, corridor.distanceTo(50, 0), 0.01);
        assertEquals(3.0, corridor.distanceTo(50, 3), 0.01);
    }
}
