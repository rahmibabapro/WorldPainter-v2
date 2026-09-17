package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.ToDoubleBiFunction;

/**
 * Lateral corridor search for drawn strokes (8 then 16). Does not widen cut budget.
 */
public final class DrawnRiverRoutePlanner {
    public record Route(List<DrawnRiverGraph.Pixel> pixels, int corridorWidth, double cost) {}

    private final Dimension dimension;
    private final ToDoubleBiFunction<Integer, Integer> heightAt;
    private final Runnable check;

    public DrawnRiverRoutePlanner(Dimension dimension, ToDoubleBiFunction<Integer, Integer> heightAt, Runnable check) {
        this.dimension = Objects.requireNonNull(dimension);
        this.heightAt = Objects.requireNonNull(heightAt);
        this.check = Objects.requireNonNull(check);
    }

    public DrawnRiverRoutePlanner(Dimension dimension, Runnable check) {
        this(dimension, (x, y) -> dimension.getHeightAt(x, y), check);
    }

    /**
     * Try to refine {@code coarse} inside corridor half-widths 8, 16, 24, 32.
     * Endpoints stay fixed unless {@code moveEnd} allows relocating the outlet/junction.
     */
    public Route refine(List<DrawnRiverGraph.Pixel> coarse, double depth, boolean pinStart, boolean pinEnd,
                 DrawnRiverGraph.Pixel preferredEnd) {
        if (coarse == null || coarse.size() < 2) return null;
        for (int width : new int[]{8, 16, 24, 32}) {
            check.run();
            Route r = astar(coarse, width, depth, pinStart, pinEnd, preferredEnd);
            if (r != null) return r;
        }
        return null;
    }

    /** Search real wet sea/lake/river within radius (no auto lake). */
    public DrawnRiverGraph.Pixel findOutlet(DrawnRiverGraph.Pixel from, int radius) {
        DrawnRiverGraph.Pixel best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        int r = Math.max(1, radius);
        for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            check.run();
            int x = from.x() + dx, y = from.y() + dy;
            if (!dimension.isTilePresent(x >> 7, y >> 7)) continue;
            if (blocked(x, y)) continue;
            float h = dimension.getHeightAt(x, y);
            if (!Float.isFinite(h)) continue;
            int w = dimension.getWaterLevelAt(x, y);
            if (w <= Math.round(h)) continue;
            double d = Math.hypot(dx, dy);
            if (d < bestDist) {
                bestDist = d;
                best = new DrawnRiverGraph.Pixel(x, y);
            }
        }
        return best;
    }

    private Route astar(List<DrawnRiverGraph.Pixel> coarse, int width, double depth,
                        boolean pinStart, boolean pinEnd, DrawnRiverGraph.Pixel preferredEnd) {
        DrawnRiverGraph.Pixel start = coarse.get(0);
        DrawnRiverGraph.Pixel goal = preferredEnd != null ? preferredEnd : coarse.get(coarse.size() - 1);
        if (pinStart) start = coarse.get(0);
        if (pinEnd) goal = coarse.get(coarse.size() - 1);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator
                .comparingDouble(Node::f).thenComparingInt(n -> n.p.x()).thenComparingInt(n -> n.p.y()));
        Map<DrawnRiverGraph.Pixel, Double> costs = new HashMap<>();
        Map<DrawnRiverGraph.Pixel, DrawnRiverGraph.Pixel> parents = new HashMap<>();
        open.add(new Node(start, 0, hypot(start, goal)));
        costs.put(start, 0d);
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < 100_000 && costs.size() < 300_000) {
            check.run();
            Node node = open.remove();
            DrawnRiverGraph.Pixel p = node.p;
            if (node.g > costs.getOrDefault(p, Double.POSITIVE_INFINITY)) continue;
            if (hypot(p, goal) <= 2.5) {
                List<DrawnRiverGraph.Pixel> path = new ArrayList<>();
                path.add(goal.equals(p) ? p : goal);
                for (DrawnRiverGraph.Pixel q = p; q != null; q = parents.get(q)) {
                    if (path.isEmpty() || !path.get(path.size() - 1).equals(q)) path.add(q);
                }
                java.util.Collections.reverse(path);
                if (path.size() < 2) return null;
                return new Route(densify(path), width, node.g);
            }
            double ph = heightAt.applyAsDouble(p.x(), p.y());
            for (int[] d : DIRS) {
                DrawnRiverGraph.Pixel next = new DrawnRiverGraph.Pixel(p.x() + d[0], p.y() + d[1]);
                if (corridorDistance(next, coarse) > width + 0.01) continue;
                if (!dimension.isTilePresent(next.x() >> 7, next.y() >> 7) || blocked(next.x(), next.y())) continue;
                double nh = heightAt.applyAsDouble(next.x(), next.y());
                if (!Double.isFinite(nh)) continue;
                double rise = nh - ph;
                double uphill = Math.max(0, rise);
                double deviation = corridorDistance(next, coarse);
                double cutHint = Math.max(0, nh - (ph - depth));
                double cost = node.g + 1 + uphill * 16 + (rise > 2.0 ? (rise - 2.0) * 40 : 0) + deviation * 0.25 + cutHint * 2;
                if (cost < costs.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    costs.put(next, cost);
                    parents.put(next, p);
                    open.add(new Node(next, cost, cost + hypot(next, goal)));
                }
            }
        }
        return null;
    }

    private static List<DrawnRiverGraph.Pixel> densify(List<DrawnRiverGraph.Pixel> path) {
        List<DrawnRiverGraph.Pixel> out = new ArrayList<>();
        out.add(path.get(0));
        for (int i = 1; i < path.size(); i++) {
            DrawnRiverGraph.Pixel a = path.get(i - 1), b = path.get(i);
            int steps = Math.max(1, (int) Math.ceil(hypot(a, b)));
            for (int s = 1; s <= steps; s++) {
                int x = a.x() + (b.x() - a.x()) * s / steps;
                int y = a.y() + (b.y() - a.y()) * s / steps;
                DrawnRiverGraph.Pixel p = new DrawnRiverGraph.Pixel(x, y);
                if (!out.get(out.size() - 1).equals(p)) out.add(p);
            }
        }
        return out;
    }

    private static double corridorDistance(DrawnRiverGraph.Pixel p, List<DrawnRiverGraph.Pixel> coarse) {
        double best = Double.POSITIVE_INFINITY;
        for (DrawnRiverGraph.Pixel c : coarse) best = Math.min(best, hypot(p, c));
        return best;
    }

    private boolean blocked(int x, int y) {
        return dimension.getBitLayerValueAt(ReadOnly.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(NotPresent.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(NotPresentBlock.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y);
    }

    private static double hypot(DrawnRiverGraph.Pixel a, DrawnRiverGraph.Pixel b) {
        return Math.hypot(a.x() - (double) b.x(), a.y() - (double) b.y());
    }

    private record Node(DrawnRiverGraph.Pixel p, double g, double f) {}

    private static final int[][] DIRS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    };
}
