/*
 * WorldPainter surface smoothing using 2x2x2 sub-voxel patterns (inspired by Axiom HDVoxelMap).
 */
package org.pepsoft.worldpainter.exporting;

import org.pepsoft.minecraft.Direction;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.MixedMaterial;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;

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
        // Deepslate (and cobbled/polished/brick/tile variants) must stay full blocks —
        // stair/slab smoothing causes export and underwater bugs.
        if (isDeepslateTerrain(surfaceMaterial)) {
            return null;
        }
        final SmoothableBlockFamily family = getFamily(surfaceMaterial);
        if (family == null) {
            return null;
        }
        // An original full dry bank can hold water at its own block Y. Cutting
        // that block into a slab/stair would open a lateral fluid face even
        // though the heightmap is unchanged. Preserve only that boundary voxel;
        // submerged granite still follows the normal smoothing/waterlogging path.
        if (hasAdjacentFullWater(dimension, heightSnapshot, worldX, worldY, intHeight)) {
            return null;
        }
        // Grass/dirt (and other full, non-smoothable surface materials) cannot
        // share a half-block top with the adjoining rock. Keep the rock flush at
        // equal-height dry seams; submerged granite still needs partial blocks.
        if (hasDryFullBlockSeam(dimension, heightSnapshot, worldX, worldY, intHeight)) {
            return null;
        }
        // Keep the immutable height halo for seam-safe geometry, but retain the
        // Dimension as fluid context. Snapshot-only dispatch used to lose the
        // wet/dry distinction needed by underwater contour bridges.
        int bits = computeVoxel222(dimension, heightSnapshot, worldX, worldY, intHeight);
        if (bits == 0) {
            return null;
        }
        bits = snapToNearestValidVoxel222(bits);
        // Never replace the surface with air — that leaves dry holes and underwater air pockets.
        if (bits == 0) {
            return null;
        }
        final Material smoothed = createFromVoxel222(bits, family.full(), family.stair(), family.slab());
        // Never emit air: keep the full block when the pattern cannot be expressed as a stair/slab.
        if ((smoothed == null) || smoothed.empty) {
            return null;
        }
        return smoothed;
    }

    /**
     * Whether this material is a stair/slab from a registered smoothable family (e.g. left by a previous
     * export/merge with surface smoothing). These are not flagged {@link Material#terrain} in the materials DB.
     */
    public static boolean isSmoothedSurfacePartial(Material material) {
        if ((material == null) || (material.name == null)) {
            return false;
        }
        if (! (material.name.endsWith("_stairs") || material.name.endsWith("_slab"))) {
            return false;
        }
        return getFamily(material) != null;
    }

    /**
     * Whether fluid occupies the voxel at {@code blockY} given the column's water level.
     * Includes the water-surface band ({@code waterLevel == blockY}) so stairs/slabs at sea
     * level are waterlogged — not only columns where water sits strictly above terrain.
     */
    public static boolean fluidOccupiesBlock(int blockY, int waterLevel) {
        return waterLevel >= blockY;
    }

    /**
     * Partial blocks (slabs/stairs) that support {@code waterlogged} should carry water when
     * fluid occupies that cell (at or below water level).
     */
    public static Material waterlogUnderwater(Material material) {
        if ((material != null) && material.hasProperty(WATERLOGGED)) {
            return material.withProperty(WATERLOGGED, true);
        }
        return material;
    }

    /**
     * Waterlog a stair/slab when fluid occupies {@code blockY}; otherwise leave the material unchanged.
     */
    public static Material waterlogIfFluidOccupies(Material material, int blockY, int waterLevel) {
        if (fluidOccupiesBlock(blockY, waterLevel)) {
            return waterlogUnderwater(material);
        }
        return material;
    }

    /**
     * Whether this is the wet, terrain-supported side of an actual shoreline.
     * The generated river may render that one-cell fringe with mud-brick
     * slab/stair geometry, without cutting the original dry grass bank or
     * extending the water plane into it.
     */
    static boolean isWetShorelineEdge(Dimension dimension, ChunkHeightSnapshot snapshot,
                                      int worldX, int worldY, int intHeight) {
        if (dimension == null || dimension.getWaterLevelAt(worldX, worldY) <= intHeight
                || dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, worldX, worldY)) return false;
        final int waterLevel = dimension.getWaterLevelAt(worldX, worldY);
        final float centre = heightAt(dimension, snapshot, worldX, worldY);
        if (isMissingHeight(centre)) return false;
        for (int[] offset : CARDINAL_NEIGHBOURS) {
            final int x = worldX + offset[0], y = worldY + offset[1];
            final float height = heightAt(dimension, snapshot, x, y);
            if (isMissingHeight(height) || height - centre > 1.5f || Math.round(height) < waterLevel
                    || dimension.getWaterLevelAt(x, y) >= waterLevel) continue;
            if (dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(NotPresent.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(NotPresentBlock.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(ReadOnly.INSTANCE, x, y)) continue;
            return true;
        }
        return false;
    }

    /** Whether this wet surface participates in a required one-block rounded-height bridge. */
    static boolean hasWetRoundedContourNeighbour(Dimension dimension, ChunkHeightSnapshot snapshot,
                                                 int worldX, int worldY, int intHeight) {
        if (dimension == null || dimension.getWaterLevelAt(worldX, worldY) <= intHeight) return false;
        for (int[] offset : CARDINAL_NEIGHBOURS) {
            final int x = worldX + offset[0], y = worldY + offset[1];
            final float height = heightAt(dimension, snapshot, x, y);
            if (isMissingHeight(height)) continue;
            final int neighbourHeight = Math.round(height);
            if (Math.abs(neighbourHeight - intHeight) == 1
                    && dimension.getWaterLevelAt(x, y) > neighbourHeight
                    && !dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y)) return true;
        }
        return false;
    }

    static int computeVoxel222(ChunkHeightSnapshot heightSnapshot, int worldX, int worldY, int intHeight) {
        return computeVoxel222(null, heightSnapshot, worldX, worldY, intHeight);
    }

    static int computeVoxel222(Dimension dimension, int worldX, int worldY, int intHeight) {
        return computeVoxel222(dimension, null, worldX, worldY, intHeight);
    }

    private static int computeVoxel222(Dimension dimension, ChunkHeightSnapshot snapshot, int worldX, int worldY, int intHeight) {
        final float[] heights = new float[9];
        int index = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final float height = heightAt(dimension, snapshot, worldX + dx, worldY + dz);
                if (isMissingHeight(height)) return 255;
                heights[index++] = height;
            }
        }
        final float centre = heights[4];
        for (float height : heights) {
            // One surface voxel cannot turn a genuine multi-block cliff into a
            // ramp. Retaining its full block avoids fragile ledges and holes.
            if (Math.abs(height - centre) > 1.5f) return 255;
        }

        // Symmetric uphill stencil: a lower cell turns into a ramp facing its
        // higher neighbour, in all four directions. The previous +X/+Z-only
        // quadrant made west/north slopes slabs but east/south slopes stairs.
        // Opposing equal rises cancel instead of arbitrarily choosing an axis.
        final float riseX = Math.max(0, heights[5] - centre) - Math.max(0, heights[3] - centre);
        final float riseZ = Math.max(0, heights[7] - centre) - Math.max(0, heights[1] - centre);
        if (Math.abs(riseX) < HEIGHT_EPSILON && Math.abs(riseZ) < HEIGHT_EPSILON
                && Math.abs(centre - intHeight) < HEIGHT_EPSILON) return 255;
        final float uphillOffset = (Math.abs(riseX) + Math.abs(riseZ)) * 0.5f;

        int bits = 0;
        // Bit order: bottom Y half first, then top Y half; within each half dz then dx.
        // Corners: bit8/128=NW, bit4/64=NE, bit2/32=SW, bit1/16=SE
        // (N=-Z, S=+Z, W=-X, E=+X — Minecraft / WorldPainter axes).
        //
        // WorldPainter places the surface at round(height). Bottom half fills for terrain
        // above intHeight-0.5 (so heights that round up still form a slab, not air).
        // Top half fills only above intHeight+0.5 so gentle slopes still become stairs.
        int bitIndex = 0;
        for (int dy = 0; dy < 2; dy++) {
            for (int dz = 0; dz < 2; dz++) {
                for (int dx = 0; dx < 2; dx++) {
                    final float u = dx * 0.5f - 0.25f;
                    final float v = dz * 0.5f - 0.25f;
                    final float height = centre + uphillOffset + riseX * u + riseZ * v;
                    // dy=0 → intHeight-0.5; dy=1 → intHeight+0.5
                    final float threshold = intHeight - 0.5f + dy;
                    if (height > threshold) {
                        bits |= VOXEL_BITS[bitIndex];
                    }
                    bitIndex++;
                }
            }
        }
        // round(height) chooses the Minecraft block Y. Two almost identical
        // fractional heights on opposite sides of the .5 boundary would both
        // otherwise become bottom slabs in consecutive block layers: e.g.
        // 64.10 -> slab at Y=64 and 64.60 -> slab at Y=65. That creates a full
        // block ledge in an extremely gentle (and especially visible underwater)
        // grade. Give the LOWER contour cell elevated corners towards neighbours
        // which round into the next block layer. It then becomes an upright
        // straight/inner/outer stair and bridges the contour in half-block steps.
        // Flat fractional interiors have no higher rounded neighbour and remain
        // slabs. The 1.5-block cliff guard above still keeps real cliffs whole.
        if ((bits & 240) == 240 && (bits & 15) != 15) {
            final int originalTop = bits & 15;
            final int top = (bits | roundedContourCorners(dimension, worldX, worldY, heights, intHeight)) & 15;
            // Opposite diagonal elevated corners cannot be represented by one
            // vanilla stair. The old nearest-pattern tie produced an arbitrary
            // triangular notch. Keep the pre-contour slab instead of inventing
            // an asymmetric corner or displacing shallow surface water.
            bits = (bits & 240) | ((top == 6 || top == 9) ? originalTop : top);
        }
        return bits;
    }

    private static int roundedContourCorners(Dimension dimension, int worldX, int worldY,
                                             float[] heights, int intHeight) {
        int corners = 0;
        // Row-major stencil: NW,N,NE,W,C,E,SW,S,SE. A cardinal higher
        // neighbour raises both touching corners; a diagonal raises its one
        // corner. This also yields the correct inner/outer corner topology.
        final boolean submerged = dimension != null
                && fluidOccupiesBlock(intHeight, dimension.getWaterLevelAt(worldX, worldY));
        if (higherContour(dimension, worldX, worldY, heights[0], -1, -1, intHeight, submerged)
                || higherContour(dimension, worldX, worldY, heights[1], 0, -1, intHeight, submerged)
                || higherContour(dimension, worldX, worldY, heights[3], -1, 0, intHeight, submerged)) corners |= 8; // NW
        if (higherContour(dimension, worldX, worldY, heights[1], 0, -1, intHeight, submerged)
                || higherContour(dimension, worldX, worldY, heights[2], 1, -1, intHeight, submerged)
                || higherContour(dimension, worldX, worldY, heights[5], 1, 0, intHeight, submerged)) corners |= 4; // NE
        if (higherContour(dimension, worldX, worldY, heights[3], -1, 0, intHeight, submerged)
                || higherContour(dimension, worldX, worldY, heights[6], -1, 1, intHeight, submerged)
                || higherContour(dimension, worldX, worldY, heights[7], 0, 1, intHeight, submerged)) corners |= 2; // SW
        if (higherContour(dimension, worldX, worldY, heights[5], 1, 0, intHeight, submerged)
                || higherContour(dimension, worldX, worldY, heights[7], 0, 1, intHeight, submerged)
                || higherContour(dimension, worldX, worldY, heights[8], 1, 1, intHeight, submerged)) corners |= 1; // SE
        return corners;
    }

    private static boolean higherContour(Dimension dimension, int worldX, int worldY, float height,
                                         int dx, int dy, int intHeight, boolean submerged) {
        final int neighbourHeight = Math.round(height);
        if (neighbourHeight <= intHeight) return false;
        // An underwater floor must not ramp up into a dry bank merely because
        // the bank occupies the next rounded Y. Only another fluid-filled
        // surface cell belongs to the same underwater contour. Dry terrain has
        // no such restriction; snapshot-only callers retain geometric behaviour.
        return !submerged || dimension == null
                || fluidOccupiesBlock(neighbourHeight, dimension.getWaterLevelAt(worldX + dx, worldY + dy));
    }

    private static boolean hasAdjacentFullWater(Dimension dimension, ChunkHeightSnapshot snapshot,
                                                int worldX, int worldY, int intHeight) {
        if (dimension.getWaterLevelAt(worldX, worldY) >= intHeight) return false;
        for (int[] offset : CARDINAL_NEIGHBOURS) {
            final int x = worldX + offset[0], y = worldY + offset[1];
            if (dimension.getWaterLevelAt(x, y) < intHeight) continue;
            final float height = heightAt(dimension, snapshot, x, y);
            // A stored water plane buried in terrain is not an adjacent water
            // voxel. Use the same rounded terrain surface as the chunk factory.
            if (isMissingHeight(height) || Math.round(height) >= intHeight) continue;
            if (dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(NotPresent.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(NotPresentBlock.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(ReadOnly.INSTANCE, x, y)) continue;
            return true;
        }
        return false;
    }

    private static boolean hasDryFullBlockSeam(Dimension dimension, ChunkHeightSnapshot snapshot,
                                              int worldX, int worldY, int intHeight) {
        if (dimension.getWaterLevelAt(worldX, worldY) >= intHeight) return false;
        final Terrain currentTerrain = dimension.getTerrainAt(worldX, worldY);
        for (int[] offset : CARDINAL_NEIGHBOURS) {
            final int x = worldX + offset[0], y = worldY + offset[1];
            final float height = heightAt(dimension, snapshot, x, y);
            if (isMissingHeight(height) || Math.round(height) != intHeight
                    || dimension.getWaterLevelAt(x, y) >= intHeight) continue;
            final Terrain terrain = dimension.getTerrainAt(x, y);
            if (terrain == null || (terrain == currentTerrain && !terrain.isCustom())) continue;
            // Resolve the neighbour's actual terrain surface, including ordinary
            // mixed custom terrains. Layer exporters are deliberately not run
            // here; they may independently replace terrain in later passes.
            int layerOffset = 0;
            if (terrain.isCustom() && dimension.getTopLayerAnchor() == Dimension.LayerAnchor.TERRAIN) {
                final MixedMaterial mixed = Terrain.getCustomMaterial(terrain.getCustomTerrainIndex());
                if (mixed != null && mixed.getMode() == MixedMaterial.Mode.LAYERED) {
                    layerOffset = -(intHeight - mixed.getPatternHeight() + 1);
                }
            }
            final Material material = layerOffset != 0
                    ? terrain.getMaterial(dimension.getWorld().getPlatform(), dimension.getSeed(),
                            x, y, intHeight + layerOffset, intHeight + layerOffset)
                    : terrain.getMaterial(dimension.getWorld().getPlatform(), dimension.getSeed(), x, y, height, intHeight);
            if (material.solid && (getFamily(material) == null || isDeepslateTerrain(material))) return true;
        }
        return false;
    }

    private static float heightAt(Dimension dimension, ChunkHeightSnapshot snapshot, int x, int y) {
        return snapshot != null ? snapshot.getHeightAt(x, y) : dimension.getHeightAt(x, y);
    }

    private static final float HEIGHT_EPSILON = 1f / 256f;
    private static final int[] VOXEL_BITS = {128, 64, 32, 16, 8, 4, 2, 1};
    private static final int[][] CARDINAL_NEIGHBOURS = { {-1, 0}, {1, 0}, {0, -1}, {0, 1} };

    /**
     * Snap to a terrain-safe slab/stair pattern. Empty (0) and top-only / upside-down
     * patterns are excluded so the surface never becomes air or a floating top slab.
     */
    static int snapToNearestValidVoxel222(int bits) {
        if (bits == 0 || bits == 255) {
            return bits;
        }
        int minError = Integer.MAX_VALUE;
        int closestValid = 255;
        for (int voxel : SURFACE_VALID_VOXEL_222) {
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
                // outer_left + facing puts the single elevated corner correctly:
                // SE→SOUTH, SW→WEST, NE→EAST, NW→NORTH
                stair = stair.withProperty(HALF, "bottom").withProperty(SHAPE, "outer_left");
                return stair.withProperty(FACING, facingForOuterCorner(voxel & 15));
            } else if (upperCount == 2) {
                stair = stair.withProperty(HALF, "bottom").withProperty(SHAPE, "straight");
                final Direction stairFacing = facingForStraightHalf(voxel & 15);
                return (stairFacing != null) ? stair.withProperty(FACING, stairFacing) : null;
            } else if (upperCount == 3) {
                // inner_left + facing: missing SE→NORTH, SW→EAST, NE→WEST, NW→SOUTH
                stair = stair.withProperty(HALF, "bottom").withProperty(SHAPE, "inner_left");
                return stair.withProperty(FACING, facingForInnerCorner(voxel & 15));
            }
        } else if ((voxel & 15) == 15) {
            final int lowerCount = Integer.bitCount(voxel & 240);
            if (lowerCount == 0) {
                return slab.withProperty(TYPE, "top");
            } else if (lowerCount == 1) {
                stair = stair.withProperty(HALF, "top").withProperty(SHAPE, "outer_left");
                // Lower bits 16/32/64/128 map to the same SE/SW/NE/NW corners as 1/2/4/8.
                return stair.withProperty(FACING, facingForOuterCorner((voxel >> 4) & 15));
            } else if (lowerCount == 2) {
                stair = stair.withProperty(HALF, "top").withProperty(SHAPE, "straight");
                final Direction stairFacing = facingForStraightHalf((voxel >> 4) & 15);
                return (stairFacing != null) ? stair.withProperty(FACING, stairFacing) : null;
            } else if (lowerCount == 3) {
                stair = stair.withProperty(HALF, "top").withProperty(SHAPE, "inner_left");
                return stair.withProperty(FACING, facingForInnerCorner((voxel >> 4) & 15));
            }
        }
        return null;
    }

    public static SmoothableBlockFamily getFamily(Material material) {
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

    /** Retexture an already validated surface shape without recalculating its geometry. */
    static Material retextureSurface(Material material, SmoothableBlockFamily target) {
        if (material == null || target == null) return material;
        final Material result;
        if (material.name != null && material.name.endsWith("_slab")) {
            result = target.slab().withProperty(TYPE, material.getProperty(TYPE));
        } else if (material.name != null && material.name.endsWith("_stairs")) {
            result = target.stair()
                    .withProperty(HALF, material.getProperty(HALF))
                    .withProperty(SHAPE, material.getProperty(SHAPE))
                    .withProperty(FACING, material.getProperty(FACING));
        } else {
            result = target.full();
        }
        return Boolean.TRUE.equals(material.getProperty(WATERLOGGED)) && result.hasProperty(WATERLOGGED)
                ? result.withProperty(WATERLOGGED, true) : result;
    }

    /**
     * Deepslate and related terrain full blocks (cobbled / polished / bricks / tiles).
     * These must not be converted to stairs or slabs during surface smoothing.
     */
    static boolean isDeepslateTerrain(Material material) {
        if ((material == null) || (material.name == null)) {
            return false;
        }
        final String name = material.name;
        if (! name.contains("deepslate")) {
            return false;
        }
        // Stairs/slabs are partials from older exports — not terrain full blocks to smooth from.
        return ! (name.endsWith("_stairs") || name.endsWith("_slab"));
    }

    /**
     * Facing for {@code outer_left} when exactly one of the four horizontal corners is set.
     * Corner bits: 1=SE, 2=SW, 4=NE, 8=NW.
     */
    private static Direction facingForOuterCorner(int cornerBits) {
        if ((cornerBits & 1) != 0) {
            return Direction.SOUTH;
        } else if ((cornerBits & 2) != 0) {
            return Direction.WEST;
        } else if ((cornerBits & 4) != 0) {
            return Direction.EAST;
        }
        return Direction.NORTH;
    }

    /**
     * Facing for {@code straight} when two adjacent corners form a cardinal half-block.
     * Returns {@code null} for diagonal pairs that are not a valid straight stair.
     */
    private static Direction facingForStraightHalf(int cornerBits) {
        if ((cornerBits & 1) != 0) {
            if ((cornerBits & 2) != 0) {
                return Direction.SOUTH; // SE+SW
            } else if ((cornerBits & 4) != 0) {
                return Direction.EAST; // SE+NE
            }
        } else if ((cornerBits & 8) != 0) {
            if ((cornerBits & 2) != 0) {
                return Direction.WEST; // NW+SW
            } else if ((cornerBits & 4) != 0) {
                return Direction.NORTH; // NW+NE
            }
        }
        return null;
    }

    /**
     * Facing for {@code inner_left} when three corners are set (one missing).
     */
    private static Direction facingForInnerCorner(int cornerBits) {
        if ((cornerBits & 1) == 0) {
            return Direction.NORTH; // missing SE
        } else if ((cornerBits & 2) == 0) {
            return Direction.EAST; // missing SW
        } else if ((cornerBits & 4) == 0) {
            return Direction.WEST; // missing NE
        }
        return Direction.SOUTH; // missing NW
    }

    private static boolean isMissingHeight(float height) {
        return height < -1.0e30f || ExportHeightSnapshot.isMissing(height);
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

    /** All Axiom-style patterns (kept for reference / direct createFromVoxel222 tests). */
    static final int[] VALID_VOXEL_222 = {
            0, 255, 240, 15, 248, 244, 242, 241, 252, 250, 245, 243, 247, 251, 253, 254,
            143, 79, 47, 31, 207, 175, 95, 63, 127, 191, 223, 239
    };

    /**
     * Patterns safe for terrain surfaces: full block, bottom slab, and bottom-half stairs.
     * Excludes empty (air) and top-only / upside-down shapes that leave gaps under water.
     */
    static final int[] SURFACE_VALID_VOXEL_222 = {
            255, 240, 248, 244, 242, 241, 252, 250, 245, 243, 247, 251, 253, 254
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
        // Deepslate families are registered only so legacy deepslate stairs/slabs are recognized
        // by isSmoothedSurfacePartial during merge. smoothSurfaceMaterial never creates them
        // (see isDeepslateTerrain).
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
        registerFamily("minecraft:mud_bricks", "minecraft:mud_brick_stairs", "minecraft:mud_brick_slab");
        // Plain end stone has no stair/slab in Minecraft; use brick variants as a safe substitute.
        registerFamily(MC_END_STONE, "minecraft:end_stone_brick_stairs", "minecraft:end_stone_brick_slab", BLK_END_STONE);
        registerFamily(MC_PRISMARINE, "minecraft:prismarine_stairs", "minecraft:prismarine_slab");
        registerFamily(MC_PRISMARINE_BRICKS, "minecraft:prismarine_brick_stairs", "minecraft:prismarine_brick_slab");
        registerFamily(MC_DARK_PRISMARINE, "minecraft:dark_prismarine_stairs", "minecraft:dark_prismarine_slab");
    }
}
