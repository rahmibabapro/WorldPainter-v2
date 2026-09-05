package org.pepsoft.worldpainter.exporting.delta;

import org.junit.Test;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Annotations;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.NotPresent;

import static org.junit.Assert.*;

public class TileFingerprinterTest {
    @Test
    public void detectsOffGridHeightAndRestoration() {
        Tile tile = tile();
        long before = hash(tile);
        tile.setHeight(1, 1, 63.125f);
        assertNotEquals(before, hash(tile));
        tile.setHeight(1, 1, 64);
        assertEquals(before, hash(tile));
    }

    @Test
    public void detectsOffGridTerrain() {
        Tile tile = tile();
        long before = hash(tile);
        tile.setTerrain(127, 127, Terrain.STONE);
        assertNotEquals(before, hash(tile));
    }

    @Test
    public void detectsOffGridWater() {
        Tile tile = tile();
        long before = hash(tile);
        tile.setWaterLevel(3, 5, 65);
        assertNotEquals(before, hash(tile));
    }

    @Test
    public void detectsExistingBitLayerEdit() {
        Tile tile = tile();
        tile.setBitLayerValue(Frost.INSTANCE, 0, 0, true);
        long before = hash(tile);
        tile.setBitLayerValue(Frost.INSTANCE, 1, 1, true);
        assertNotEquals(before, hash(tile));
        tile.setBitLayerValue(Frost.INSTANCE, 1, 1, false);
        assertEquals(before, hash(tile));
    }

    @Test
    public void detectsExistingNibbleLayerEdit() {
        Tile tile = tile();
        tile.setLayerValue(Annotations.INSTANCE, 0, 0, 4);
        long before = hash(tile);
        tile.setLayerValue(Annotations.INSTANCE, 127, 127, 4);
        assertNotEquals(before, hash(tile));
    }

    @Test
    public void detectsExistingByteLayerEdit() {
        Tile tile = tile();
        tile.setLayerValue(Biome.INSTANCE, 0, 0, 1);
        long before = hash(tile);
        tile.setLayerValue(Biome.INSTANCE, 3, 5, 2);
        assertNotEquals(before, hash(tile));
    }

    @Test
    public void detectsLastChunkBit() {
        Tile tile = tile();
        tile.setBitLayerValue(NotPresent.INSTANCE, 0, 0, true);
        long before = hash(tile);
        tile.setBitLayerValue(NotPresent.INSTANCE, 127, 127, true);
        assertNotEquals(before, hash(tile));
    }

    private static long hash(Tile tile) {
        return TileFingerprinter.computeTileFingerprint(tile);
    }

    private static Tile tile() {
        Tile tile = new Tile(-1, 2, -64, 320);
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                tile.setHeight(x, y, 64);
                tile.setTerrain(x, y, Terrain.GRASS);
                tile.setWaterLevel(x, y, 62);
            }
        }
        return tile;
    }
}
