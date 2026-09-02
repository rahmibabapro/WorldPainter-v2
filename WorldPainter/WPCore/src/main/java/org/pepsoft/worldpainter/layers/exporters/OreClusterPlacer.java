package org.pepsoft.worldpainter.layers.exporters;

import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.HeightMap;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Resources;
import org.pepsoft.worldpainter.layers.Void;

import java.util.Map;
import java.util.Random;

import static java.lang.Math.floor;
import static org.pepsoft.minecraft.Constants.MC_DEEPSLATE;
import static org.pepsoft.minecraft.Constants.MC_DIRT;
import static org.pepsoft.minecraft.Constants.MC_GOLD_ORE;
import static org.pepsoft.minecraft.Constants.MC_GRAVEL;
import static org.pepsoft.minecraft.Constants.MC_ANDESITE;
import static org.pepsoft.minecraft.Constants.MC_BASALT;
import static org.pepsoft.minecraft.Constants.MC_BLACKSTONE;
import static org.pepsoft.minecraft.Constants.MC_DIORITE;
import static org.pepsoft.minecraft.Constants.MC_END_STONE;
import static org.pepsoft.minecraft.Constants.MC_GRANITE;
import static org.pepsoft.minecraft.Constants.MC_NETHERRACK;
import static org.pepsoft.minecraft.Constants.MC_STONE;
import static org.pepsoft.minecraft.Constants.MC_TUFF;
import static org.pepsoft.minecraft.Material.NETHER_GOLD_ORE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

/**
 * Vanilla-style ore/dirt pocket placer: few ellipsoid clusters per chunk instead of
 * per-block 3D Perlin scans. Dramatically reduces CPU for {@link ResourcesExporter}.
 */
public final class OreClusterPlacer {
    /** Scales promillage chance → attempts per chunk column-average. Tuned vs legacy density tests. */
    private static final double ATTEMPTS_PER_PROMILLE = 0.30;
    private static final int MIN_ORE_RADIUS = 1;
    private static final int MAX_ORE_RADIUS = 3;
    private static final int MIN_BLOB_RADIUS = 2;
    private static final int MAX_BLOB_RADIUS = 5;

    private OreClusterPlacer() {
    }

    public static void render(Tile tile, Chunk chunk, Dimension dimension, HeightMap minHeightField,
                              Material[] activeMaterials, int[] minLevels, int[] maxLevels,
                              int[] chancePromille, long[] seedOffsets, int minimumLevel,
                              boolean coverSteepTerrain, boolean nether,
                              Map<String, Material> oreToDeepslate, int worldMinZ, int worldMaxZ) {
        final int chunkX = chunk.getxPos();
        final int chunkZ = chunk.getzPos();
        final int xOffset = (chunkX & 7) << 4;
        final int zOffset = (chunkZ & 7) << 4;

        // Average resources intensity in this chunk (cheap 4x4 sample)
        int resourcesSum = 0;
        int samples = 0;
        for (int sx = 0; sx < 16; sx += 4) {
            for (int sz = 0; sz < 16; sz += 4) {
                final int localX = xOffset + sx;
                final int localY = zOffset + sz;
                if (tile.getBitLayerValue(Void.INSTANCE, localX, localY)) {
                    continue;
                }
                resourcesSum += Math.max(minimumLevel, tile.getLayerValue(Resources.INSTANCE, localX, localY));
                samples++;
            }
        }
        if (samples == 0 || resourcesSum == 0) {
            return;
        }
        final double resourcesScale = resourcesSum / (double) (samples * 8.0); // ~0..2 for typical levels

        for (int i = 0; i < activeMaterials.length; i++) {
            final int chance = chancePromille[i];
            if (chance <= 0) {
                continue;
            }
            final Material material = activeMaterials[i];
            final boolean blob = material.isNamedOneOf(MC_DIRT, MC_GRAVEL);
            final long seed = dimension.getSeed() ^ seedOffsets[i] ^ (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL) ^ (i * 0x9E3779B97F4A7C15L);
            final Random random = new Random(seed);

            int attempts = (int) Math.round(chance * resourcesScale * ATTEMPTS_PER_PROMILLE);
            if (blob) {
                attempts = Math.max(attempts, chance > 0 ? 1 : 0);
            }
            attempts = Math.min(attempts, blob ? 48 : 64);
            if (attempts <= 0) {
                continue;
            }

            final int minY = Math.max(minLevels[i], worldMinZ);
            final int maxY = Math.min(maxLevels[i], worldMaxZ);
            if (minY > maxY) {
                continue;
            }

            for (int a = 0; a < attempts; a++) {
                final int lx = random.nextInt(16);
                final int lz = random.nextInt(16);
                final int localX = xOffset + lx;
                final int localY = zOffset + lz;
                if (tile.getBitLayerValue(Void.INSTANCE, localX, localY)) {
                    continue;
                }
                final int resourcesValue = Math.max(minimumLevel, tile.getLayerValue(Resources.INSTANCE, localX, localY));
                if (resourcesValue <= 0) {
                    continue;
                }
                // Scale attempt survival by local intensity
                if (random.nextInt(8) >= Math.min(resourcesValue, 8)) {
                    continue;
                }

                final int worldX = tile.getX() * TILE_SIZE + localX;
                final int worldY = tile.getY() * TILE_SIZE + localY;
                final int terrainheight = tile.getIntHeight(localX, localY);
                final int topLayerDepth = dimension.getTopLayerDepth(worldX, worldY, terrainheight);
                int subsurfaceMaxHeight = terrainheight - topLayerDepth;
                if (coverSteepTerrain) {
                    subsurfaceMaxHeight = Math.min(subsurfaceMaxHeight,
                            Math.min(Math.min(dimension.getIntHeightAt(worldX - 1, worldY, Integer.MAX_VALUE),
                                            dimension.getIntHeightAt(worldX + 1, worldY, Integer.MAX_VALUE)),
                                    Math.min(dimension.getIntHeightAt(worldX, worldY - 1, Integer.MAX_VALUE),
                                            dimension.getIntHeightAt(worldX, worldY + 1, Integer.MAX_VALUE))));
                }
                final int floorZ = (minHeightField != null)
                        ? (int) floor(minHeightField.getHeight(worldX, worldY))
                        : worldMinZ;
                final int high = Math.min(subsurfaceMaxHeight, maxY);
                final int low = Math.max(floorZ, minY);
                if (high < low) {
                    continue;
                }

                final int cy = low + random.nextInt(high - low + 1);
                final int radius = blob
                        ? MIN_BLOB_RADIUS + random.nextInt(MAX_BLOB_RADIUS - MIN_BLOB_RADIUS + 1)
                        : MIN_ORE_RADIUS + random.nextInt(MAX_ORE_RADIUS - MIN_ORE_RADIUS + 1);
                final int radiusY = Math.max(1, blob ? radius : (radius + 1) / 2);

                placeEllipsoid(chunk, lx, cy, lz, radius, radiusY, material, nether, oreToDeepslate,
                        low, high);
            }
        }
    }

    private static void placeEllipsoid(Chunk chunk, int cx, int cy, int cz, int radius, int radiusY,
                                       Material material, boolean nether, Map<String, Material> oreToDeepslate,
                                       int low, int high) {
        final int r2 = radius * radius;
        final int ry2 = radiusY * radiusY;
        for (int dx = -radius; dx <= radius; dx++) {
            final int x = cx + dx;
            if (x < 0 || x > 15) {
                continue;
            }
            for (int dz = -radius; dz <= radius; dz++) {
                final int z = cz + dz;
                if (z < 0 || z > 15) {
                    continue;
                }
                for (int dy = -radiusY; dy <= radiusY; dy++) {
                    final int y = cy + dy;
                    if (y < low || y > high) {
                        continue;
                    }
                    if ((dx * dx * ry2 + dy * dy * r2 + dz * dz * ry2) > (r2 * ry2)) {
                        continue;
                    }
                    setOre(chunk, x, y, z, material, nether, oreToDeepslate);
                }
            }
        }
    }

    private static void setOre(Chunk chunk, int x, int y, int z, Material material, boolean nether,
                               Map<String, Material> oreToDeepslate) {
        final Material existing = chunk.getMaterial(x, y, z);
        // Only replace stone-like / already-ore host blocks (vanilla ore pocket behaviour)
        if (! isReplaceableHost(existing)) {
            return;
        }
        if (existing.isNamed(MC_DEEPSLATE) && oreToDeepslate.containsKey(material.name)) {
            chunk.setMaterial(x, y, z, oreToDeepslate.get(material.name));
        } else if (nether && material.isNamed(MC_GOLD_ORE)) {
            chunk.setMaterial(x, y, z, NETHER_GOLD_ORE);
        } else {
            chunk.setMaterial(x, y, z, material);
        }
    }

    private static boolean isReplaceableHost(Material existing) {
        if (existing == null) {
            return false;
        }
        return existing.isNamedOneOf(MC_STONE, MC_DEEPSLATE, MC_NETHERRACK, MC_GRANITE, MC_DIORITE, MC_ANDESITE,
                MC_TUFF, MC_BASALT, MC_BLACKSTONE, MC_END_STONE)
                || existing.name.contains("_ore")
                || existing.name.contains("ancient_debris");
    }
}
