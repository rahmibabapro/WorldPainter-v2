package org.pepsoft.worldpainter.exporting;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.exporting.HeightMapExporter.Format;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

public class HeightMapSizeCheckTest {

    @Test
    public void smallWorldFitsSingleBuffer() {
        final Dimension dim = tinyDimension(2, 2);
        assertTrue(HeightMapSizeCheck.fitsInJavaArray(dim));
        assertTrue(HeightMapSizeCheck.canAllocateSingleBuffer(dim, Format.INTEGER_LOW_RESOLUTION, 8));
    }

    @Test
    public void byteAwareEstimateGrowsWithBpp() {
        final Dimension dim = tinyDimension(4, 4);
        final long px = HeightMapSizeCheck.pixelCount(dim);
        assertEquals(px * 1, HeightMapSizeCheck.estimatedBytes(dim, Format.INTEGER_LOW_RESOLUTION, 8));
        assertEquals(px * 2, HeightMapSizeCheck.estimatedBytes(dim, Format.INTEGER_LOW_RESOLUTION, 16));
        assertEquals(px * 4, HeightMapSizeCheck.estimatedBytes(dim, Format.INTEGER_LOW_RESOLUTION, 32));
        assertEquals(px * 4, HeightMapSizeCheck.estimatedBytes(dim, Format.FLOAT_ONE_TO_ONE, 32));
    }

    @Test
    public void describeTooLargeMentionsDimensions() {
        final Dimension dim = tinyDimension(3, 3);
        final String msg = HeightMapSizeCheck.describeTooLarge(dim, Format.INTEGER_HIGH_RESOLUTION, 32);
        assertTrue(msg.contains("tiles"));
        assertTrue(msg.toLowerCase(java.util.Locale.ROOT).contains("tiff"));
    }

    private static Dimension tinyDimension(int tilesX, int tilesZ) {
        ExportTestSupport.ensureReady();
        final var config = org.pepsoft.worldpainter.Configuration.getInstance();
        final World2 world = new World2(config.getDefaultPlatform(), 1L,
                TileFactoryFactory.createNoiseTileFactory(1L, Terrain.GRASS, config.getDefaultPlatform().minZ,
                        config.getDefaultMaxHeight(), 62, 62, true, true, 20f, 1.0));
        final Dimension dimension = world.getDimension(NORMAL_DETAIL);
        for (int x = 0; x < tilesX; x++) {
            for (int z = 0; z < tilesZ; z++) {
                dimension.addTile(dimension.getTileFactory().createTile(x, z));
            }
        }
        return dimension;
    }
}
