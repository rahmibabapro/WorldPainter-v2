/*
 * WorldPainter surface smoothing using 2x2x2 sub-voxel patterns (inspired by Axiom HDVoxelMap).
 */
package org.pepsoft.worldpainter.exporting;

import org.pepsoft.minecraft.Direction;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;

import java.util.HashMap;
import java.util.Map;

import static org.pepsoft.minecraft.Constants.*;
import static org.pepsoft.minecraft.Material.*;

/**
 * Converts fractional terrain height into slab/stair surface blocks using 2x2x2
 * sub-voxel occupancy patterns.
 */
public final class SurfaceSmoother {
    private SurfaceSmoother() {
    }

    /**
     * Attempt to replace a surface material with a smoothed slab or stair variant.
     *
     * @return The smoothed material, or {@code null} if smoothing does not apply.
     */
    public static Material smoothSurfaceMaterial(Dimension dimension, int worldX, int worldY, int intHeight, Material surfaceMaterial) {
        return smoothSurfaceMaterial(dimension, null, worldX, worldY, intHeight, surfaceMaterial);
    }

    public static Material smoothSurfaceMaterial(Dimension dimension, ChunkHeightSnapshot heightSnapshot, int worldX, int worldY, int intHeight, Material surfaceMaterial) {
        final SmoothableBlockFamily family = getFamily(surfaceMaterial);
        if (family == null) {
            return null;
        }
        int bits = (heightSnapshot != null)
                ? computeVoxel222(heightSnapshot, worldX, worldY, intHeight)
                : computeVoxel222(dimension, worldX, worldY, intHeight);
        if (bits == 0) {
            return null;
        }
        bits = snapToNearestValidVoxel222(bits);
        final Material smoothed = createFromVoxel222(bits, family.full(), family.stair(), family.slab());
        return (smoothed != null) ? smoothed : null;
    }

    /**
     * Partial blocks (slabs/stairs) exported underwater should carry water inside the same cell.
     */
    public static Material waterlogUnderwater(Material material) {
        if ((material != null) && material.hasProperty(WATERLOGGED)) {
            return material.withProperty(WATERLOGGED, true);
        }
        return material;
    }

    static int computeVoxel222(ChunkHeightSnapshot heightSnapshot, int worldX, int worldY, int intHeight) {
        final float h00 = heightSnapshot.getHeightAt(worldX, worldY);
        final float h10 = heightSnapshot.getHeightAt(worldX + 1, worldY);
        final float h01 = heightSnapshot.getHeightAt(worldX, worldY + 1);
        final float h11 = heightSnapshot.getHeightAt(worldX + 1, worldY + 1);
        return computeVoxel222FromHeights(h00, h10, h01, h11, intHeight);
    }

    static int computeVoxel222(Dimension dimension, int worldX, int worldY, int intHeight) {
        final float h00 = dimension.getHeightAt(worldX, worldY);
        final float h10 = dimension.getHeightAt(worldX + 1, worldY);
        final float h01 = dimension.getHeightAt(worldX, worldY + 1);
        final float h11 = dimension.getHeightAt(worldX + 1, worldY + 1);
        return computeVoxel222FromHeights(h00, h10, h01, h11, intHeight);
    }

    private static int computeVoxel222FromHeights(float h00, float h10, float h01, float h11, int intHeight) {
        if (isMissingHeight(h00) || isMissingHeight(h10) || isMissingHeight(h01) || isMissingHeight(h11)) {
            return 255;
        }

        int bits = 0;
        // Bit order matches Axiom HDVoxelMap: bottom Y half first, then top Y half.
        final int[] bitValues = {128, 64, 32, 16, 8, 4, 2, 1};
        int bitIndex = 0;
        for (int dy = 0; dy < 2; dy++) {
            for (int dz = 0; dz < 2; dz++) {
                for (int dx = 0; dx < 2; dx++) {
                    final float u = dx * 0.5f + 0.25f;
                    final float v = dz * 0.5f + 0.25f;
                    final float height = bilinear(h00, h10, h01, h11, u, v);
                    final boolean exactIntegerHeight = Math.abs(height - intHeight) < (1f / 256f);
                    if (exactIntegerHeight || height > intHeight + dy * 0.5f) {
                        bits |= bitValues[bitIndex];
                    }
                    bitIndex++;
                }
            }
        }
        return bits;
    }

    static int snapToNearestValidVoxel222(int bits) {
        if (bits == 0 || bits == 255) {
            return bits;
        }
        int minError = Integer.MAX_VALUE;
        int closestValid = 255;
        for (int voxel : VALID_VOXEL_222) {
            final int different = (bits ^ voxel) & 255;
            int error = Integer.bitCount(different) * 3;
            error *= 16;
            error += 8 - Integer.bitCount(voxel & 255);
            if (error < minError) {
                minError = error;
                closestValid = voxel;
            }
        }
        return closestValid;
    }

    /**
     * Port of Axiom {@code HDVoxelMap.createFromVoxel222}, adapted to WorldPainter materials.
     */
    static Material createFromVoxel222(int voxel, Material full, Material stair, Material slab) {
        if (voxel == 0) {
            return AIR;
        } else if (voxel == 255) {
            return full;
        } else if ((voxel & 240) == 240) {
            final int upperCount = Integer.bitCount(voxel & 15);
            if (upperCount == 0) {
                return slab.withProperty(TYPE, "bottom");
            } else if (upperCount == 1) {
                stair = stair.withProperty(HALF, "bottom").withProperty(SHAPE, "outer_left");
                final Direction stairFacing;
                if ((voxel & 1) != 0) {
                    stairFacing = Direction.SOUTH;
                } else if ((voxel & 2) != 0) {
                    stairFacing = Direction.EAST;
                } else if ((voxel & 4) != 0) {
                    stairFacing = Direction.WEST;
                } else {
                    stairFacing = Direction.NORTH;
                }
                return stair.withProperty(FACING, stairFacing);
            } else if (upperCount == 2) {
                stair = stair.withProperty(HALF, "bottom").withProperty(SHAPE, "straight");
                Direction stairFacing = null;
                if ((voxel & 1) != 0) {
                    if ((voxel & 2) != 0) {
                        stairFacing = Direction.EAST;
                    } else if ((voxel & 4) != 0) {
                        stairFacing = Direction.SOUTH;
                    }
                } else if ((voxel & 8) != 0) {
                    if ((voxel & 2) != 0) {
                        stairFacing = Direction.NORTH;
                    } else if ((voxel & 4) != 0) {
                        stairFacing = Direction.WEST;
                    }
                }
                return (stairFacing != null) ? stair.withProperty(FACING, stairFacing) : null;
            } else if (upperCount == 3) {
                stair = stair.withProperty(HALF, "bottom").withProperty(SHAPE, "inner_left");
                final Direction stairFacing;
                if ((voxel & 1) == 0) {
                    stairFacing = Direction.NORTH;
                } else if ((voxel & 2) == 0) {
                    stairFacing = Direction.WEST;
                } else if ((voxel & 4) == 0) {
                    stairFacing = Direction.EAST;
                } else {
                    stairFacing = Direction.SOUTH;
                }
                return stair.withProperty(FACING, stairFacing);
            }
        } else if ((voxel & 15) == 15) {
            final int lowerCount = Integer.bitCount(voxel & 240);
            if (lowerCount == 0) {
                return slab.withProperty(TYPE, "top");
            } else if (lowerCount == 1) {
                stair = stair.withProperty(HALF, "top").withProperty(SHAPE, "outer_left");
                final Direction stairFacing;
                if ((voxel & 16) != 0) {
                    stairFacing = Direction.SOUTH;
                } else if ((voxel & 32) != 0) {
                    stairFacing = Direction.EAST;
                } else if ((voxel & 64) != 0) {
                    stairFacing = Direction.WEST;
                } else {
                    stairFacing = Direction.NORTH;
                }
                return stair.withProperty(FACING, stairFacing);
            } else if (lowerCount == 2) {
                stair = stair.withProperty(HALF, "top").withProperty(SHAPE, "straight");
                Direction stairFacing = null;
                if ((voxel & 16) != 0) {
                    if ((voxel & 32) != 0) {
                        stairFacing = Direction.EAST;
                    } else if ((voxel & 64) != 0) {
                        stairFacing = Direction.SOUTH;
                    }
                } else if ((voxel & 128) != 0) {
                    if ((voxel & 32) != 0) {
                        stairFacing = Direction.NORTH;
                    } else if ((voxel & 64) != 0) {
                        stairFacing = Direction.WEST;
                    }
                }
                return (stairFacing != null) ? stair.withProperty(FACING, stairFacing) : null;
            } else if (lowerCount == 3) {
                stair = stair.withProperty(HALF, "top").withProperty(SHAPE, "inner_left");
                final Direction stairFacing;
                if ((voxel & 16) == 0) {
                    stairFacing = Direction.NORTH;
                } else if ((voxel & 32) == 0) {
                    stairFacing = Direction.WEST;
                } else if ((voxel & 64) == 0) {
                    stairFacing = Direction.EAST;
                } else {
                    stairFacing = Direction.SOUTH;
                }
                return stair.withProperty(FACING, stairFacing);
            }
        }
        return null;
    }

    static SmoothableBlockFamily getFamily(Material material) {
        if (material == null) {
            return null;
        }
        if (material.name != null) {
            final SmoothableBlockFamily byName = FAMILY_BY_ANY.get(material.name);
            if (byName != null) {
                return byName;
            }
        }
        if (material.blockType == BLK_STONE && material.data != 0) {
            final SmoothableBlockFamily byStoneVariant = FAMILY_BY_STONE_DATA.get(material.data);
            if (byStoneVariant != null) {
                return byStoneVariant;
            }
        }
        return FAMILY_BY_BLOCK_TYPE.get(material.blockType);
    }

    private static boolean isMissingHeight(float height) {
        return height < -1.0e30f || ExportHeightSnapshot.isMissing(height);
    }

    private static float bilinear(float h00, float h10, float h01, float h11, float u, float v) {
        return h00 * (1 - u) * (1 - v)
                + h10 * u * (1 - v)
                + h01 * (1 - u) * v
                + h11 * u * v;
    }

    private static void registerFamily(String fullName, String stairName, String slabName) {
        final Material full = Material.get(fullName);
        final Material stair = Material.get(stairName);
        final Material slab = Material.get(slabName);
        final SmoothableBlockFamily family = new SmoothableBlockFamily(full, stair, slab);
        FAMILY_BY_FULL.put(fullName, family);
        FAMILY_BY_ANY.put(fullName, family);
        FAMILY_BY_ANY.put(stairName, family);
        FAMILY_BY_ANY.put(slabName, family);
    }

    private static void registerFamily(String fullName, String stairName, String slabName, int... legacyBlockTypes) {
        registerFamily(fullName, stairName, slabName);
        final SmoothableBlockFamily family = FAMILY_BY_FULL.get(fullName);
        for (int blockType : legacyBlockTypes) {
            FAMILY_BY_BLOCK_TYPE.put(blockType, family);
        }
    }

    private static void registerStoneVariant(int legacyData, String fullName, String stairName, String slabName) {
        registerFamily(fullName, stairName, slabName);
        FAMILY_BY_STONE_DATA.put(legacyData, FAMILY_BY_FULL.get(fullName));
    }

    public record SmoothableBlockFamily(Material full, Material stair, Material slab) {
    }

    private static final Map<String, SmoothableBlockFamily> FAMILY_BY_FULL = new HashMap<>();
    private static final Map<String, SmoothableBlockFamily> FAMILY_BY_ANY = new HashMap<>();
    private static final Map<Integer, SmoothableBlockFamily> FAMILY_BY_BLOCK_TYPE = new HashMap<>();
    private static final Map<Integer, SmoothableBlockFamily> FAMILY_BY_STONE_DATA = new HashMap<>();

    static final int[] VALID_VOXEL_222 = {
            0, 255, 240, 15, 248, 244, 242, 241, 252, 250, 245, 243, 247, 251, 253, 254,
            143, 79, 47, 31, 207, 175, 95, 63, 127, 191, 223, 239
    };

    static {
        registerFamily(MC_STONE, "minecraft:stone_stairs", MC_STONE_SLAB, BLK_STONE);
        registerStoneVariant(DATA_STONE_GRANITE, MC_GRANITE, "minecraft:granite_stairs", "minecraft:granite_slab");
        registerStoneVariant(DATA_STONE_DIORITE, MC_DIORITE, "minecraft:diorite_stairs", "minecraft:diorite_slab");
        registerStoneVariant(DATA_STONE_ANDESITE, MC_ANDESITE, "minecraft:andesite_stairs", "minecraft:andesite_slab");
        registerFamily(MC_COBBLESTONE, MC_COBBLESTONE_STAIRS, MC_COBBLESTONE_SLAB, BLK_COBBLESTONE, BLK_MOSSY_COBBLESTONE);
        registerFamily(MC_MOSSY_COBBLESTONE, MC_COBBLESTONE_STAIRS, MC_COBBLESTONE_SLAB);
        registerFamily(MC_SANDSTONE, MC_SANDSTONE_STAIRS, MC_SANDSTONE_SLAB, BLK_SANDSTONE);
        registerFamily(MC_SMOOTH_SANDSTONE, MC_SANDSTONE_STAIRS, MC_SANDSTONE_SLAB);
        registerFamily(MC_CUT_SANDSTONE, MC_SANDSTONE_STAIRS, MC_SANDSTONE_SLAB);
        registerFamily(MC_RED_SANDSTONE, MC_RED_SANDSTONE_STAIRS, MC_RED_SANDSTONE_SLAB, BLK_RED_SANDSTONE);
        registerFamily(MC_SMOOTH_RED_SANDSTONE, MC_RED_SANDSTONE_STAIRS, MC_RED_SANDSTONE_SLAB);
        registerFamily(MC_CUT_RED_SANDSTONE, MC_RED_SANDSTONE_STAIRS, MC_RED_SANDSTONE_SLAB);
        registerFamily(MC_BRICKS, MC_BRICK_STAIRS, MC_BRICK_SLAB, BLK_BRICKS);
        registerFamily(MC_STONE_BRICKS, MC_STONE_BRICK_STAIRS, MC_STONE_BRICK_SLAB, BLK_STONE_BRICKS);
        registerFamily(MC_MOSSY_STONE_BRICKS, MC_STONE_BRICK_STAIRS, MC_STONE_BRICK_SLAB);
        registerFamily(MC_OAK_PLANKS, MC_OAK_STAIRS, MC_OAK_SLAB, BLK_WOODEN_PLANK);
        registerFamily(MC_SPRUCE_PLANKS, MC_SPRUCE_STAIRS, MC_SPRUCE_SLAB);
        registerFamily(MC_BIRCH_PLANKS, MC_BIRCH_STAIRS, MC_BIRCH_SLAB);
        registerFamily(MC_JUNGLE_PLANKS, MC_JUNGLE_STAIRS, MC_JUNGLE_SLAB);
        registerFamily(MC_ACACIA_PLANKS, MC_ACACIA_STAIRS, MC_ACACIA_SLAB);
        registerFamily(MC_DARK_OAK_PLANKS, MC_DARK_OAK_STAIRS, MC_DARK_OAK_SLAB);
        registerFamily(MC_QUARTZ_BLOCK, MC_QUARTZ_STAIRS, MC_QUARTZ_SLAB);
        registerFamily(MC_SMOOTH_QUARTZ, MC_QUARTZ_STAIRS, MC_QUARTZ_SLAB);
        registerFamily(MC_PURPUR_BLOCK, MC_PURPUR_STAIRS, MC_PURPUR_SLAB, BLK_PURPUR_BLOCK);
        registerFamily(MC_NETHER_BRICKS, MC_NETHER_BRICK_STAIRS, MC_NETHER_BRICK_SLAB, BLK_NETHER_BRICK);
        registerFamily(MC_DEEPSLATE, "minecraft:deepslate_stairs", "minecraft:deepslate_slab");
        registerFamily("minecraft:cobbled_deepslate", "minecraft:cobbled_deepslate_stairs", "minecraft:cobbled_deepslate_slab");
        registerFamily("minecraft:polished_deepslate", "minecraft:polished_deepslate_stairs", "minecraft:polished_deepslate_slab");
        registerFamily("minecraft:deepslate_bricks", "minecraft:deepslate_brick_stairs", "minecraft:deepslate_brick_slab");
        registerFamily("minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab");
        registerFamily(MC_BLACKSTONE, "minecraft:blackstone_stairs", "minecraft:blackstone_slab");
        registerFamily("minecraft:polished_blackstone", "minecraft:polished_blackstone_stairs", "minecraft:polished_blackstone_slab");
        registerFamily("minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_brick_stairs", "minecraft:polished_blackstone_brick_slab");
        registerFamily(MC_ANDESITE, "minecraft:andesite_stairs", "minecraft:andesite_slab");
        registerFamily(MC_POLISHED_ANDESITE, "minecraft:polished_andesite_stairs", "minecraft:polished_andesite_slab");
        registerFamily(MC_DIORITE, "minecraft:diorite_stairs", "minecraft:diorite_slab");
        registerFamily(MC_POLISHED_DIORITE, "minecraft:polished_diorite_stairs", "minecraft:polished_diorite_slab");
        registerFamily(MC_GRANITE, "minecraft:granite_stairs", "minecraft:granite_slab");
        registerFamily(MC_POLISHED_GRANITE, "minecraft:polished_granite_stairs", "minecraft:polished_granite_slab");
        registerFamily(MC_TUFF, "minecraft:tuff_stairs", "minecraft:tuff_slab");
        registerFamily(MC_END_STONE, "minecraft:end_stone_stairs", "minecraft:end_stone_slab", BLK_END_STONE);
        registerFamily(MC_PRISMARINE, "minecraft:prismarine_stairs", "minecraft:prismarine_slab");
        registerFamily(MC_PRISMARINE_BRICKS, "minecraft:prismarine_brick_stairs", "minecraft:prismarine_brick_slab");
        registerFamily(MC_DARK_PRISMARINE, "minecraft:dark_prismarine_stairs", "minecraft:dark_prismarine_slab");
    }
}
