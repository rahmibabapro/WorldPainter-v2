package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Terrain;
import java.awt.Rectangle;
import java.util.*;
import static org.junit.Assert.*;

public class RiverWaterlinePlanTest {
    @Test public void cardinalDryLipAtWaterlineIsAccepted() {
        var d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        // Wet channel at y=64, dry banks at y=63 and y=65 with surface Y=100.
        for (int x = 40; x <= 80; x++) {
            d.setHeightAt(x, 64, 98);
            d.setWaterLevelAt(x, 64, 100);
            d.setHeightAt(x, 63, 100);
            d.setTerrainAt(x, 63, Terrain.GRASS);
            d.setWaterLevelAt(x, 63, 0);
            d.setHeightAt(x, 65, 100);
            d.setTerrainAt(x, 65, Terrain.GRASS);
            d.setWaterLevelAt(x, 65, 0);
        }
        List<RiverWaterlinePlan.WetSeed> seeds = new ArrayList<>();
        for (int x = 40; x <= 80; x++) {
            seeds.add(new RiverWaterlinePlan.WetSeed(x, 64, 100, true, false, false));
        }
        var result = RiverWaterlinePlan.create(d, seeds, Set.of(), null, () -> {});
        assertFalse(result.accepted().isEmpty());
        assertTrue(result.accepted().contains(RiverWaterlinePlan.key(50, 63)));
        assertTrue(result.accepted().contains(RiverWaterlinePlan.key(50, 65)));
        assertEquals(RiverWaterlinePlan.SkipReason.NONE, result.primarySkipReason());
    }

    @Test public void highWallAboveWaterlineIsRejected() {
        var d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        d.setHeightAt(50, 64, 98);
        d.setWaterLevelAt(50, 64, 100);
        d.setHeightAt(50, 63, 104); // cliff
        d.setWaterLevelAt(50, 63, 0);
        var result = RiverWaterlinePlan.create(d,
                List.of(new RiverWaterlinePlan.WetSeed(50, 64, 100, true, false, false)),
                Set.of(), null, () -> {});
        assertFalse(result.accepted().contains(RiverWaterlinePlan.key(50, 63)));
    }

    @Test public void mouthGuardExcludesLip() {
        var d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        d.setHeightAt(50, 64, 98);
        d.setWaterLevelAt(50, 64, 100);
        d.setHeightAt(50, 63, 100);
        d.setWaterLevelAt(50, 63, 0);
        var result = RiverWaterlinePlan.create(d,
                List.of(new RiverWaterlinePlan.WetSeed(50, 64, 100, true, false, false)),
                Set.of(RiverWaterlinePlan.key(50, 63)), null, () -> {});
        assertFalse(result.accepted().contains(RiverWaterlinePlan.key(50, 63)));
    }

    @Test public void diagonalOnlyContactIsNotEnough() {
        var d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        d.setHeightAt(50, 64, 98);
        d.setWaterLevelAt(50, 64, 100);
        // Only diagonal dry cell at waterline — no cardinal neighbour of wet seed.
        d.setHeightAt(51, 65, 100);
        d.setWaterLevelAt(51, 65, 0);
        var result = RiverWaterlinePlan.create(d,
                List.of(new RiverWaterlinePlan.WetSeed(50, 64, 100, true, false, false)),
                Set.of(), null, () -> {});
        assertFalse(result.accepted().contains(RiverWaterlinePlan.key(51, 65)));
    }
}
