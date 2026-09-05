package org.pepsoft.worldpainter.exporting.delta;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.exporting.ExportTestSupport;
import org.pepsoft.worldpainter.exporting.delta.DeltaExportCalculator.DeltaPlan;

import java.awt.Point;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
import static org.pepsoft.worldpainter.Generator.DEFAULT;

public class DeltaExportCalculatorTest {

    @Test
    public void testDeltaDetectsOnlyModifiedTiles() {
        ExportTestSupport.ensureReady();
        final Configuration config = Configuration.getInstance();
        final World2 world = new World2(config.getDefaultPlatform(), 42L,
                TileFactoryFactory.createNoiseTileFactory(42L, Terrain.GRASS, config.getDefaultPlatform().minZ, config.getDefaultMaxHeight(), 62, 62, true, true, 20f, 1.0));
        final Dimension dim = world.getDimension(NORMAL_DETAIL);
        dim.setGenerator(new org.pepsoft.minecraft.SeededGenerator(DEFAULT, 42L));

        // Create 3x3 tiles
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                dim.addTile(dim.getTileFactory().createTile(x, y));
            }
        }

        // Save base manifest of all 9 tiles
        ExportManifest manifest = new ExportManifest("World", "platform");
        for (Tile t : dim.getTiles()) {
            manifest.putTileHash(0, t.getX(), t.getY(), TileFingerprinter.computeTileFingerprint(t));
        }

        // Test 1: No changes -> 0 dirty tiles
        DeltaPlan planNoChange = DeltaExportCalculator.computeDelta(world, manifest, 0);
        assertEquals(9, planNoChange.totalTiles);
        assertEquals(0, planNoChange.dirtyTiles.size());
        assertEquals(9, planNoChange.unchangedTiles);
        assertEquals(100.0, planNoChange.getSavedPercentage(), 0.01);

        // Test 2: Modify tile at (1, 1)
        Tile centerTile = dim.getTile(1, 1);
        // Deliberately not on the old four/eight-cell sampling grid.
        centerTile.setHeight(1, 1, 120.0f);

        // Without border expansion: exactly (1,1) is dirty
        DeltaPlan planModified = DeltaExportCalculator.computeDelta(world, manifest, 0);
        assertEquals(1, planModified.dirtyTiles.size());
        assertTrue(planModified.dirtyTiles.contains(new Point(1, 1)));
        assertEquals(8, planModified.unchangedTiles);

        // With 1-tile border expansion: center and all 8 neighbors are dirty (total 9)
        DeltaPlan planExpanded = DeltaExportCalculator.computeDelta(world, manifest, 1);
        assertEquals(9, planExpanded.dirtyTiles.size());
    }
}
