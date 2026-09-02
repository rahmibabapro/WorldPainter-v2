package org.pepsoft.worldpainter.buildgraph;

import org.junit.Test;
import java.util.Map;

import static org.junit.Assert.*;

public class PlacementPointTest {

    @Test
    public void testPointAttributesAndSlope() {
        // Horizontal surface (normal pointing straight up: 0, 1, 0)
        PlacementPoint flat = new PlacementPoint(10f, 64f, 10f, 0f, 1f, 0f, 0.8f, 2.5f, 1234L,
                Map.of("moisture", 0.75f, "biomeTag", "forest"));

        assertEquals(0.0, flat.getSlopeDegrees(), 0.01);
        assertEquals(0.8f, flat.density, 0.001f);
        assertEquals(0.75f, (float) flat.getAttribute("moisture"), 0.001f);
        assertEquals("forest", flat.getAttribute("biomeTag"));

        // 45 degree slope (normal: 0.7071, 0.7071, 0)
        PlacementPoint sloped = new PlacementPoint(20f, 80f, 20f, 0.7071f, 0.7071f, 0f, 1.0f, 1.0f, 55L);
        assertEquals(45.0, sloped.getSlopeDegrees(), 0.1);
    }
}
