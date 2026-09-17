package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.minecraft.Direction;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.exporting.SurfaceSmoother;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.pepsoft.minecraft.Material.FACING;
import static org.pepsoft.minecraft.Material.HALF;
import static org.pepsoft.minecraft.Material.SHAPE;
import static org.pepsoft.minecraft.Material.TYPE;
import static org.pepsoft.minecraft.Material.WATERLOGGED;

/**
 * Detached dry top-waterline bank lip planner. Does not write the world.
 * Selects dry Y=W cells beside open river water for waterlogged bottom
 * stair/slab export via {@code RiverWaterlineDetail}.
 */
final class RiverWaterlinePlan {
    enum SkipReason {
        NONE, NO_CANDIDATE, GUARD, PROTECTED, HIGH_WALL, LEAK_RISK, UNSUPPORTED_MATERIAL, GEOMETRY
    }

    record WetSeed(int x, int y, int water, boolean wetCore, boolean clippedEndpoint, boolean lake) {}

    record Proposal(int x, int y, int water, Material material) {}

    record Result(Set<Long> accepted, Map<Long, Proposal> proposals, int rejected, SkipReason primarySkipReason) {
        Result {
            accepted = accepted == null ? Set.of() : Set.copyOf(accepted);
            proposals = proposals == null ? Map.of() : Map.copyOf(proposals);
            primarySkipReason = primarySkipReason == null ? SkipReason.NONE : primarySkipReason;
        }
        static Result empty(SkipReason reason) {
            return new Result(Set.of(), Map.of(), 0, reason);
        }
    }

    static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }

    private static final int[][] CARDINAL = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /** Convenience for tests: live dimension columns. */
    static Result create(Dimension dimension, List<WetSeed> wetSeeds, Set<Long> guards,
                         BiPredicate<Integer, Integer> avoid, Runnable check) {
        return create(SurfaceSmoother.columnsOf(dimension, null), dimension, wetSeeds, guards, avoid, check);
    }

    static Result create(SurfaceSmoother.WaterlineColumns columns, Dimension terrainSource,
                         List<WetSeed> wetSeeds, Set<Long> guards,
                         BiPredicate<Integer, Integer> avoid, Runnable check) {
        if (columns == null || wetSeeds == null || wetSeeds.isEmpty()) {
            return Result.empty(SkipReason.NO_CANDIDATE);
        }
        Set<Long> wetKeys = new HashSet<>();
        Map<Long, Integer> wetWater = new LinkedHashMap<>();
        for (WetSeed seed : wetSeeds) {
            check.run();
            if (!seed.wetCore || seed.clippedEndpoint || seed.lake) continue;
            wetKeys.add(key(seed.x, seed.y));
            wetWater.put(key(seed.x, seed.y), seed.water);
        }
        if (wetKeys.isEmpty()) return Result.empty(SkipReason.NO_CANDIDATE);

        Map<Long, Proposal> draft = new LinkedHashMap<>();
        int rejected = 0;
        SkipReason primary = SkipReason.NO_CANDIDATE;
        for (WetSeed seed : wetSeeds) {
            check.run();
            if (!seed.wetCore || seed.clippedEndpoint || seed.lake) continue;
            int W = seed.water;
            for (int[] d : CARDINAL) {
                int bx = seed.x + d[0], by = seed.y + d[1];
                long bk = key(bx, by);
                if (draft.containsKey(bk) || wetKeys.contains(bk)) continue;
                SkipReason fail = classify(columns, terrainSource, bx, by, W, d, guards, avoid, wetKeys, wetWater);
                if (fail != SkipReason.NONE) {
                    rejected++;
                    if (primary == SkipReason.NO_CANDIDATE || primary == SkipReason.NONE) primary = fail;
                    continue;
                }
                Material material = chooseMaterial(columns, terrainSource, bx, by, W, d);
                if (material == null) {
                    rejected++;
                    if (primary == SkipReason.NO_CANDIDATE || primary == SkipReason.NONE) {
                        primary = SkipReason.UNSUPPORTED_MATERIAL;
                    }
                    continue;
                }
                draft.put(bk, new Proposal(bx, by, W, material));
            }
        }

        // Corner / neighbour consistency: drop candidates that would open a leak
        // once neighbours are considered together.
        Set<Long> accepted = new LinkedHashSet<>();
        Map<Long, Proposal> finalProps = new LinkedHashMap<>();
        boolean changed;
        do {
            changed = false;
            List<Long> keys = new ArrayList<>(draft.keySet());
            for (Long bk : keys) {
                check.run();
                Proposal p = draft.get(bk);
                if (p == null) continue;
                if (!leakSafe(columns, p, wetKeys, wetWater, draft.keySet(), guards)) {
                    draft.remove(bk);
                    rejected++;
                    changed = true;
                    if (primary == SkipReason.NO_CANDIDATE || primary == SkipReason.NONE) {
                        primary = SkipReason.LEAK_RISK;
                    }
                }
            }
        } while (changed);

        for (var e : draft.entrySet()) {
            accepted.add(e.getKey());
            finalProps.put(e.getKey(), e.getValue());
        }
        SkipReason reason = accepted.isEmpty()
                ? (primary == SkipReason.NONE ? SkipReason.NO_CANDIDATE : primary)
                : SkipReason.NONE;
        return new Result(accepted, finalProps, rejected, reason);
    }

    private static SkipReason classify(SurfaceSmoother.WaterlineColumns columns, Dimension terrainSource,
                                       int x, int y, int W, int[] fromWater,
                                       Set<Long> guards, BiPredicate<Integer, Integer> avoid,
                                       Set<Long> wetKeys, Map<Long, Integer> wetWater) {
        if (guards != null && guards.contains(key(x, y))) return SkipReason.GUARD;
        if (avoid != null && avoid.test(x, y)) return SkipReason.PROTECTED;
        if (!columns.tilePresent(x, y) || columns.blocked(x, y)) return SkipReason.PROTECTED;
        float h = columns.height(x, y);
        if (!Float.isFinite(h)) return SkipReason.PROTECTED;
        int ih = Math.round(h);
        // Top lip only: surface exactly at waterline. Higher walls stay full.
        if (ih > W) return SkipReason.HIGH_WALL;
        if (ih < W) return SkipReason.GEOMETRY;
        // Must be dry column (or water at/below surface).
        if (columns.water(x, y) > ih) return SkipReason.GEOMETRY;
        Objects.requireNonNull(fromWater);
        Objects.requireNonNull(wetKeys);
        Objects.requireNonNull(wetWater);
        Objects.requireNonNull(terrainSource);
        return SkipReason.NONE;
    }

    private static Material chooseMaterial(SurfaceSmoother.WaterlineColumns columns, Dimension terrainSource,
                                           int x, int y, int W, int[] towardBankFromWater) {
        int wx = -towardBankFromWater[0], wy = -towardBankFromWater[1];
        Direction facingWater;
        if (wx < 0) facingWater = Direction.WEST;
        else if (wx > 0) facingWater = Direction.EAST;
        else if (wy < 0) facingWater = Direction.NORTH;
        else facingWater = Direction.SOUTH;

        Terrain terrain = terrainSource.getTerrainAt(x, y);
        SurfaceSmoother.SmoothableBlockFamily family = familyFor(terrain);
        if (family == null) return null;

        int lx = x - wx, ly = y - wy; // landward
        float landH = columns.height(lx, ly);
        int wetSides = 0;
        for (int[] d : CARDINAL) {
            int nx = x + d[0], ny = y + d[1];
            float nh = columns.height(nx, ny);
            if (!Float.isFinite(nh)) continue;
            if (columns.water(nx, ny) > Math.round(nh)) wetSides++;
        }
        Material result;
        if (wetSides >= 2 && (!(Float.isFinite(landH)) || Math.round(landH) <= W)) {
            result = family.slab().withProperty(TYPE, "bottom");
        } else if (Float.isFinite(landH) && Math.round(landH) > W) {
            result = family.stair()
                    .withProperty(HALF, "bottom")
                    .withProperty(SHAPE, "straight")
                    .withProperty(FACING, facingWater);
        } else if (Float.isFinite(landH) && Math.round(landH) >= W) {
            result = family.stair()
                    .withProperty(HALF, "bottom")
                    .withProperty(SHAPE, "straight")
                    .withProperty(FACING, facingWater);
        } else {
            return null;
        }
        if (result.hasProperty(WATERLOGGED)) result = result.withProperty(WATERLOGGED, true);
        return result;
    }

    private static boolean leakSafe(SurfaceSmoother.WaterlineColumns columns, Proposal p, Set<Long> wetKeys,
                                    Map<Long, Integer> wetWater, Set<Long> candidates, Set<Long> guards) {
        for (int[] d : CARDINAL) {
            int nx = p.x + d[0], ny = p.y + d[1];
            long nk = key(nx, ny);
            if (wetKeys.contains(nk)) continue;
            if (candidates.contains(nk)) continue;
            if (guards != null && guards.contains(nk)) continue;
            if (!columns.tilePresent(nx, ny) || columns.blocked(nx, ny)) return false;
            float nh = columns.height(nx, ny);
            if (!Float.isFinite(nh)) return false;
            int nih = Math.round(nh);
            int nw = columns.water(nx, ny);
            if (nih >= p.water) continue; // solid land at/above waterline
            if (SurfaceSmoother.fluidOccupiesBlock(p.water, nw)) continue; // water reaches Y=W
            return false; // depression / air gap at waterline
        }
        return SurfaceSmoother.isDryWaterlineEdge(columns, p.x, p.y, p.water)
                && SurfaceSmoother.isWaterlineSideSealed(columns, p.x, p.y, p.water);
    }

    static SurfaceSmoother.SmoothableBlockFamily familyFor(Terrain terrain) {
        if (terrain == null) return null;
        if (terrain == Terrain.DIRT || terrain == Terrain.GRASS || terrain == Terrain.BARE_GRASS
                || terrain == Terrain.PERMADIRT || terrain == Terrain.PODZOL
                || terrain == Terrain.MYCELIUM) {
            return SurfaceSmoother.getFamily(Material.get("minecraft:mud_bricks"));
        }
        if (terrain == Terrain.GRANITE) {
            return SurfaceSmoother.getFamily(Material.get("minecraft:granite"));
        }
        if (terrain == Terrain.STONE || terrain == Terrain.ROCK || terrain == Terrain.COBBLESTONE) {
            return SurfaceSmoother.getFamily(Material.get("minecraft:cobblestone"));
        }
        return SurfaceSmoother.getFamily(Material.get("minecraft:stone"));
    }
}
