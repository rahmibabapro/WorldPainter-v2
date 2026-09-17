package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import static org.junit.Assert.*;

public class RiverWaterProfileTest {
    @Test public void dropsAreSpreadByPhysicalDistanceAndReceivingLevelIsFixed() {
        int[] levels={6,6,6,6,2,2,0};
        int[] original=levels.clone();
        double[] distance={0,.5,1,2,3,4,5};
        RiverWaterProfile.spreadDrops(levels,distance,2);
        assertEquals(0,levels[levels.length-1]);
        for(int i=0;i<levels.length;i++) {
            assertTrue(levels[i]<=original[i]);
            for(int j=i+1;j<levels.length;j++) {
                assertTrue(levels[i]>=levels[j]);
                if(distance[j]-distance[i]<=2)assertTrue(levels[i]-levels[j]<=1);
            }
        }
    }
    @Test public void reportedStartingSectionRemainsUnsafe() {
        double height = 68.80078125;
        assertEquals(2.45078125, RiverWaterProfile.requiredCut(height, 67, .65), 1e-9);
        assertTrue(RiverWaterProfile.exceedsCut(height, 67, .65, 1.10 + .75));
    }
    @Test public void supportedWaterPlaneDoesNotRequireExcessiveCut() {
        assertFalse(RiverWaterProfile.exceedsCut(68.80078125, 68, .65, 1.85));
    }
    @Test public void fractionalAndNegativeTerrainUseExportRounding() {
        assertEquals(69, RiverWaterProfile.dryWaterCeiling(68.80078125));
        assertEquals(-12, RiverWaterProfile.dryWaterCeiling(-12.25));
    }
    @Test public void toleranceDoesNotRelaxSafetyLimit() {
        assertFalse(RiverWaterProfile.exceedsCut(68.2005, 67, .65, 1.85));
        assertTrue(RiverWaterProfile.exceedsCut(68.202, 67, .65, 1.85));
    }
}
