package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.tools.scripts.RiverSearchResult.Point;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Closed-basin flood to the first spill. Does not carve a spillway or write DEM.
 */
final class RiverLakePlanner {
    static final int MAX_CELLS = 8_192;
    static final int MAX_RADIUS = 64;
    static final int MAX_RISE = 24;

    record Lake(List<Point> cells, int water) {}

    static Lake plan(RiverTerrainSurvey survey, Point seed, int seaLevel,
                     BiPredicate<Integer, Integer> avoid, Runnable check) {
        if (survey == null || seed == null) return null;
        var start = survey.sample(seed.x(), seed.y());
        if (start.blocked() || !Float.isFinite(start.height()) || start.wet()
                || (seaLevel > 0 && start.height() <= seaLevel)) {
            return null;
        }
        if (avoid != null && avoid.test(seed.x(), seed.y())) return null;
        int seedH = Math.round(start.height());
        Set<Long> hold = Set.of();
        int holdWater = -1;
        int maxW = Math.min(seedH + MAX_RISE, 2032);
        for (int water = seedH + 1; water <= maxW; water++) {
            check.run();
            Flood flood = flood(survey, seed, water, seaLevel, avoid, check);
            if (flood == null) break;
            if (flood.escaped) {
                if (hold.size() >= 4 && holdWater > seedH) {
                    return toLake(hold, holdWater);
                }
                return null;
            }
            if (flood.cells.size() >= 4) {
                hold = flood.cells;
                holdWater = water;
            }
        }
        if (hold.size() >= 4 && holdWater > seedH) return toLake(hold, holdWater);
        return null;
    }

    private static Lake toLake(Set<Long> keys, int water) {
        List<Point> cells = new ArrayList<>(keys.size());
        for (long key : keys) cells.add(new Point((int) (key >> 32), (int) key));
        cells.sort(java.util.Comparator.comparingInt(Point::x).thenComparingInt(Point::y));
        return new Lake(List.copyOf(cells), water);
    }

    private static Flood flood(RiverTerrainSurvey survey, Point seed, int water, int seaLevel,
                               BiPredicate<Integer, Integer> avoid, Runnable check) {
        Set<Long> cells = new HashSet<>();
        ArrayDeque<Point> q = new ArrayDeque<>();
        q.add(seed);
        cells.add(key(seed.x(), seed.y()));
        boolean escaped = false;
        while (!q.isEmpty()) {
            check.run();
            if (cells.size() > MAX_CELLS) return null;
            Point p = q.remove();
            if (Math.hypot(p.x() - seed.x(), p.y() - seed.y()) > MAX_RADIUS) return null;
            for (int[] d : CARDINAL) {
                int nx = p.x() + d[0], ny = p.y() + d[1];
                if (avoid != null && avoid.test(nx, ny)) continue;
                var s = survey.sample(nx, ny);
                if (s.blocked() || !Float.isFinite(s.height())) {
                    escaped = true;
                    continue;
                }
                if (s.wet() || (seaLevel > 0 && s.height() <= seaLevel) || survey.edge(nx, ny) <= 0) {
                    escaped = true;
                    continue;
                }
                if (s.height() >= water) continue;
                if (cells.add(key(nx, ny))) q.add(new Point(nx, ny));
            }
        }
        return new Flood(cells, escaped);
    }

    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
    private record Flood(Set<Long> cells, boolean escaped) {}
    private static final int[][] CARDINAL = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
}
