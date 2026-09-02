package org.pepsoft.worldpainter.buildgraph;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.buildgraph.ExplainLocationInspector.LocationExplanation;
import org.pepsoft.worldpainter.exporting.ExportTestSupport;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
import static org.pepsoft.worldpainter.Generator.DEFAULT;

public class ExplainLocationInspectorTest {

    @Test
    public void testExplainLocationDecomposition() {
        ExportTestSupport.ensureReady();
        final Configuration config = Configuration.getInstance();
        final World2 world = new World2(config.getDefaultPlatform(), 42L,
                TileFactoryFactory.createNoiseTileFactory(42L, Terrain.GRASS, config.getDefaultPlatform().minZ, config.getDefaultMaxHeight(), 62, 62, true, true, 20f, 1.0));
        final Dimension dim = world.getDimension(NORMAL_DETAIL);
        dim.setGenerator(new org.pepsoft.minecraft.SeededGenerator(DEFAULT, 42L));
        dim.addTile(dim.getTileFactory().createTile(0, 0));

        // Set terrain and height
        Tile tile = dim.getTile(0, 0);
        tile.setHeight(10, 10, 95.5f);
        tile.setTerrain(10, 10, Terrain.SAND);

        LocationExplanation explanation = ExplainLocationInspector.explain(10, 10, dim);
        assertNotNull(explanation);
        assertEquals(10, explanation.worldX);
        assertEquals(10, explanation.worldY);
        assertEquals(95.5f, explanation.height, 0.01f);
        assertEquals(Terrain.SAND, explanation.terrain);

        String summary = explanation.formatSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Height: 95.50"));
        assertTrue(summary.contains("Terrain: Sand"));
    }
}
