package org.pepsoft.worldpainter.exporting;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.MC118AnvilChunk;
import org.pepsoft.worldpainter.DefaultPlugin;

import java.awt.*;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.minecraft.Material.AIR;
import static org.pepsoft.minecraft.Material.GRASS;
import static org.pepsoft.minecraft.Material.STONE;
import static org.pepsoft.minecraft.Material.WATER;

public class ChunkInteriorHollowerTest {
    @BeforeClass
    public static void init() {
        ExportTestSupport.ensureReady();
    }

    @Test
    public void hollowWithThicknessTwoKeepsDoubleShell() throws Exception {
        final WorldRegion region = new WorldRegion(0, 0, 0, 64, DefaultPlugin.JAVA_ANVIL_26_1);
        final MC118AnvilChunk chunk = new MC118AnvilChunk(0, 0, 0, 64);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= 19; y++) {
                    chunk.setMaterial(x, y, z, STONE);
                }
                chunk.setMaterial(x, 20, z, GRASS);
            }
        }
        region.addChunk(chunk);

        ChunkInteriorHollower.hollowRegion(region, new Rectangle(0, 0, 16, 16), 0, 64, 2, null);

        assertEquals(AIR, region.getMaterialAt(8, 8, 10));
        assertEquals(AIR, region.getMaterialAt(8, 8, 18));
        assertEquals(STONE, region.getMaterialAt(8, 8, 19));
        assertEquals(GRASS, region.getMaterialAt(8, 8, 20));
    }

    @Test
    public void hollowPeelRemovesFullyEnclosedInteriorWhenThicknessZero() throws Exception {
        final WorldRegion region = new WorldRegion(0, 0, 0, 64, DefaultPlugin.JAVA_ANVIL_26_1);
        final MC118AnvilChunk chunk = new MC118AnvilChunk(0, 0, 0, 64);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= 19; y++) {
                    chunk.setMaterial(x, y, z, STONE);
                }
                chunk.setMaterial(x, 20, z, GRASS);
            }
        }
        region.addChunk(chunk);

        ChunkInteriorHollower.hollowRegion(region, new Rectangle(0, 0, 16, 16), 0, 64, 0, null);

        assertEquals(AIR, region.getMaterialAt(8, 8, 10));
        assertEquals(GRASS, region.getMaterialAt(8, 8, 20));
    }

    @Test
    public void hollowHollowsStoneUnderWaterAndKeepsOceanConnected() throws Exception {
        final WorldRegion region = new WorldRegion(0, 0, 0, 64, DefaultPlugin.JAVA_ANVIL_26_1);
        final MC118AnvilChunk chunk = new MC118AnvilChunk(0, 0, 0, 64);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= 25; y++) {
                    chunk.setMaterial(x, y, z, WATER);
                }
                for (int y = 0; y <= 15; y++) {
                    if (x >= 4 && x <= 11 && z >= 4 && z <= 11) {
                        chunk.setMaterial(x, y, z, STONE);
                    }
                }
                chunk.setMaterial(x, 16, z, GRASS);
            }
        }
        region.addChunk(chunk);

        ChunkInteriorHollower.hollowRegion(region, new Rectangle(0, 0, 16, 16), 0, 64, 2, null);

        assertEquals(WATER, region.getMaterialAt(0, 10, 0));
        assertEquals(AIR, region.getMaterialAt(8, 8, 8));
        assertEquals(STONE, region.getMaterialAt(8, 8, 15));
        assertEquals(AIR, region.getMaterialAt(8, 8, 14));
        assertEquals(GRASS, region.getMaterialAt(8, 8, 16));
    }

    @Test
    public void turboSeedMatchesFullSeedOnSolidTerrainBox() throws Exception {
        final WorldRegion full = buildSolidTerrainBox();
        ChunkInteriorHollower.hollowRegion(full, new Rectangle(0, 0, 16, 16), 0, 64, 2, null, false);
        final WorldRegion turbo = buildSolidTerrainBox();
        ChunkInteriorHollower.hollowRegion(turbo, new Rectangle(0, 0, 16, 16), 0, 64, 2, null, true);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 64; y++) {
                    assertEquals("Mismatch at " + x + "," + y + "," + z,
                            full.getMaterialAt(x, y, z), turbo.getMaterialAt(x, y, z));
                }
            }
        }
    }

    private static WorldRegion buildSolidTerrainBox() {
        final WorldRegion region = new WorldRegion(0, 0, 0, 64, DefaultPlugin.JAVA_ANVIL_26_1);
        final MC118AnvilChunk chunk = new MC118AnvilChunk(0, 0, 0, 64);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= 19; y++) {
                    chunk.setMaterial(x, y, z, STONE);
                }
                chunk.setMaterial(x, 20, z, GRASS);
            }
        }
        region.addChunk(chunk);
        return region;
    }
}
