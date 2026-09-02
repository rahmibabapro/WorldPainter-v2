package org.pepsoft.worldpainter.terrain.hydrology.river;

import org.junit.Test;
import org.pepsoft.worldpainter.spatial.geometry.SpatialGeometry.SpatialPoint;
import org.pepsoft.worldpainter.terrain.hydrology.river.RiverNetwork.RiverReach;

import static org.junit.Assert.*;

public class RiverNetworkTest {

    @Test
    public void testRiverNetworkReachAndStrahlerOrder() {
        RiverNetwork net = new RiverNetwork();

        // 1st order headwater reach
        net.addReach(new RiverReach(1, new SpatialPoint(0, 0), new SpatialPoint(50, 0), 1, 10f, 0.05f, 3.0f, 1.2f));
        // 2nd order downstream reach
        net.addReach(new RiverReach(2, new SpatialPoint(50, 0), new SpatialPoint(150, 0), 2, 50f, 0.02f, 6.0f, 2.5f));

        assertEquals(2, net.size());
        assertEquals(2, net.getMaxStrahlerOrder());
        assertEquals(150.0, net.getTotalLength(), 0.01);
    }
}
