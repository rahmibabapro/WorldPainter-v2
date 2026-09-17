package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.exporting.SurfaceSmoother;
import java.util.*;

/** Detached, single-pass correction of connected dirt patches. No world writes. */
final class RiverDirtBedPlan {
    enum Role { INTERIOR_DIRT, INTERIOR_GRANITE, WET_SHORE, PROTECTED_LINK }
    enum SkipReason {
        NONE, NO_DIRT, WORLD_FLOOR, NO_SUPPORT, GEOMETRY_FAILED, PROTECTED
    }

    record Cell(int x, int y, float height, int water, boolean dirt, boolean detail,
                boolean eligible, int route, Role role) {
        Cell(int x, int y, float height, int water, boolean dirt, boolean detail,
             boolean eligible, int route) {
            this(x, y, height, water, dirt, detail, eligible, route,
                    dirt ? Role.INTERIOR_DIRT
                            : (detail ? Role.INTERIOR_GRANITE : Role.PROTECTED_LINK));
        }
    }

    record Result(Set<Long> lowered, int skippedRegions, int candidateDirt,
                  SkipReason primarySkipReason) {
        Result {
            lowered = lowered == null ? Set.of() : Set.copyOf(lowered);
            primarySkipReason = primarySkipReason == null ? SkipReason.NONE : primarySkipReason;
        }
        Result(Set<Long> lowered, int skippedRegions) {
            this(lowered, skippedRegions, 0, SkipReason.NONE);
        }
        static Result empty(SkipReason reason) {
            return new Result(Set.of(), 0, 0, reason);
        }
    }

    static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    static Result create(Dimension dimension, Map<Long, Cell> cells, Runnable check) {
        Set<Long> seen = new HashSet<>(), lowered = new HashSet<>();
        View view = new View(dimension, cells, lowered);
        int skipped = 0, candidateDirt = 0;
        SkipReason primary = SkipReason.NO_DIRT;
        for (Cell c : cells.values()) {
            if (c.dirt && c.eligible) candidateDirt++;
        }
        if (candidateDirt == 0) {
            return new Result(Set.of(), 0, 0, SkipReason.NO_DIRT);
        }
        for (var seed : cells.entrySet()) {
            check.run();
            // Only interior (eligible) dirt seeds a region. Fringe / mouth / junction
            // dirt must not enter the flood-fill or poison an otherwise safe bed.
            if (!seed.getValue().dirt || !seed.getValue().eligible || !seen.add(seed.getKey())) continue;
            Set<Long> region = new LinkedHashSet<>(), shoreBorders = new HashSet<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(seed.getKey());
            boolean safe = true, supported = false;
            SkipReason fail = SkipReason.NONE;
            while (!queue.isEmpty()) {
                check.run();
                long k = queue.remove();
                Cell c = cells.get(k);
                region.add(k);
                if (c.height - 1 < dimension.getMinHeight()) {
                    safe = false;
                    fail = SkipReason.WORLD_FLOOR;
                }
                for (int[] d : DIRECTIONS) {
                    long nk = key(c.x + d[0], c.y + d[1]);
                    Cell n = cells.get(nk);
                    // Soft walls: plan edge, different water, or protected link.
                    // Route id alone never poisons or splits same-water beds.
                    if (n == null || n.water != c.water || n.role == Role.PROTECTED_LINK) {
                        continue;
                    }
                    if (n.dirt) {
                        if (n.eligible) {
                            if (seen.add(nk)) queue.add(nk);
                        }
                        // Ineligible fringe dirt is a soft wall: stay out, do not poison.
                        continue;
                    }
                    if (n.detail || n.role == Role.INTERIOR_GRANITE || n.role == Role.WET_SHORE) {
                        if (n.role == Role.WET_SHORE) shoreBorders.add(nk);
                        // Wet granite/detail fringe is enough support. Requiring an
                        // export stair/slab made flat mid-channel beds skip the -1.
                        if (Math.round(c.height) >= Math.round(n.height)) supported = true;
                    }
                    // Unexpected cell types are soft walls too (no poison).
                }
            }
            if (!safe) {
                skipped++;
                if (primary == SkipReason.NO_DIRT || primary == SkipReason.NONE) primary = fail;
                continue;
            }
            if (!supported) {
                skipped++;
                if (primary == SkipReason.NO_DIRT || primary == SkipReason.NONE) {
                    primary = SkipReason.NO_SUPPORT;
                }
                continue;
            }
            // Collect wet-shore cells in the one-block halo — only these must keep
            // exact shore material. Interior granite may adapt to the new bed.
            for (long k : region) {
                Cell c = cells.get(k);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    long nk = key(c.x + dx, c.y + dy);
                    Cell n = cells.get(nk);
                    if (n != null && n.role == Role.WET_SHORE) shoreBorders.add(nk);
                }
            }
            Map<Long, Material> before = new HashMap<>();
            view.shoreMode = true;
            for (long k : shoreBorders) {
                check.run();
                before.put(k, surface(view, cells.get(k)));
            }
            lowered.addAll(region);
            boolean geometryOk = true;
            for (long k : shoreBorders) {
                check.run();
                if (!Objects.equals(before.get(k), surface(view, cells.get(k)))) {
                    geometryOk = false;
                    break;
                }
            }
            view.shoreMode = false;
            if (!geometryOk) {
                lowered.removeAll(region);
                skipped++;
                if (primary == SkipReason.NO_DIRT || primary == SkipReason.NONE) {
                    primary = SkipReason.GEOMETRY_FAILED;
                }
            }
        }
        SkipReason reason = lowered.isEmpty()
                ? (primary == SkipReason.NONE ? SkipReason.NO_DIRT : primary)
                : SkipReason.NONE;
        return new Result(Set.copyOf(lowered), skipped, candidateDirt, reason);
    }

    private static Material surface(View view, Cell c) {
        return SurfaceSmoother.riverSurfaceMaterial(view, null, c.x, c.y, Math.round(c.height),
                Material.get("minecraft:granite"));
    }

    private static final class View extends Dimension {
        private final Dimension source;
        private final Map<Long, Cell> cells;
        private final Set<Long> lowered;
        /** When true, ignore dirt −1 so shoreline geometry stays bank-anchored. */
        boolean shoreMode;

        View(Dimension source, Map<Long, Cell> cells, Set<Long> lowered) {
            super(source.getWorld(), "River preview", source.getSeed(), source.getTileFactory(), source.getAnchor(), false);
            this.source = source;
            this.cells = cells;
            this.lowered = lowered;
        }

        @Override public float getHeightAt(int x, int y) {
            long k = key(x, y);
            Cell c = cells.get(k);
            if (c == null) return source.getHeightAt(x, y);
            if (shoreMode) return c.height;
            return c.height - (lowered.contains(k) ? 1 : 0);
        }

        @Override public int getWaterLevelAt(int x, int y) {
            Cell c = cells.get(key(x, y));
            return c == null ? source.getWaterLevelAt(x, y) : c.water;
        }

        @Override public Terrain getTerrainAt(int x, int y) {
            Cell c = cells.get(key(x, y));
            if (c == null) return source.getTerrainAt(x, y);
            if (c.role == RiverDirtBedPlan.Role.INTERIOR_DIRT) return Terrain.DIRT;
            return Terrain.GRANITE;
        }

        @Override public boolean getBitLayerValueAt(Layer layer, int x, int y) {
            return source.getBitLayerValueAt(layer, x, y);
        }
    }
}
