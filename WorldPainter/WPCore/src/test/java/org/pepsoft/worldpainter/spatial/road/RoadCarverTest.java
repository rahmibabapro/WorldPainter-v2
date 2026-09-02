package org.pepsoft.worldpainter.spatial.road;

import org.junit.Test;
import org.pepsoft.worldpainter.spatial.geometry.SpatialGeometry.SpatialPoint;
import org.pepsoft.worldpainter.spatial.geometry.SpatialGeometry.SpatialSpline;

import java.util.List;

import static org.junit.Assert.*;

public class RoadCarverTest {

    @Test
    public void testCutAndFillAlongRoad() {
        int w = 32, h = 32;
        float[][] heights = new float[w][h];

        // Hill on left (height 80), ditch on right (height 50)
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                heights[x][z] = (x < 16) ? 80.0f : 50.0f;
            }
        }

        // Road passing horizontally at z=16, constant target height 65.0
        SpatialSpline road = new SpatialSpline(List.of(
                new SpatialPoint(0, 16, 65.0),
                new SpatialPoint(32, 16, 65.0)
        ));

        // Road width 4 (half 2), shoulder 2 (total half 4)
        RoadProfile profile = new RoadProfile(4.0f, 2.0f, 10.0f, 30.0f, "minecraft:gravel");
        RoadCarver.gradeRoad(heights, w, h, road, profile);

        // Center road at hill (x=8, z=16) must be CUT from 80 -> 65
        assertEquals(65.0f, heights[8][16], 0.01f);

        // Center road at ditch (x=24, z=16) must be FILLED from 50 -> 65
        assertEquals(65.0f, heights[24][16], 0.01f);

        // Shoulder at (x=8, z=17.5 - dist 1.5 < 2) must be 65
        assertEquals(65.0f, heights[8][17], 0.01f);

        // Untouched outside corridor (z=5)
        assertEquals(80.0f, heights[8][5], 0.01f);
        assertEquals(50.0f, heights[24][5], 0.01f);
    }
}
