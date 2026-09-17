package org.pepsoft.worldpainter.tools.scripts;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Temporary drawing-mask cleanup before topology extraction. Never writes the
 * user's River Path layer.
 */
public final class DrawnRiverNormalizer {
    public enum IssueKind {
        SMALL_GAP_REPAIRED, JUNCTION_REGION, REAL_LOOP, MULTI_OUTLET, UNRESOLVED
    }

    public record Diagnostic(IssueKind kind, Rectangle bounds, List<DrawnRiverGraph.Pixel> highlight, String advice) {
        public Diagnostic {
            highlight = highlight == null ? List.of() : List.copyOf(highlight);
            advice = advice == null ? "" : advice;
        }
        public String message() {
            return advice + (bounds == null ? "" : " @(" + bounds.x + "," + bounds.y
                    + " " + bounds.width + "x" + bounds.height + ")");
        }
    }

    public record JunctionRegion(Set<DrawnRiverGraph.Pixel> cells, DrawnRiverGraph.Pixel representative) {
        public JunctionRegion {
            cells = Set.copyOf(cells);
            Objects.requireNonNull(representative);
        }
    }

    public record Outcome(Set<DrawnRiverGraph.Pixel> mask, Set<DrawnRiverGraph.Pixel> repairs,
                   List<JunctionRegion> regions, List<Diagnostic> diagnostics) {
        public Outcome {
            mask = Set.copyOf(mask);
            repairs = Set.copyOf(repairs);
            regions = List.copyOf(regions);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static final int[][] CARDINAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final int MAX_GAP_CELLS = 4;
    private static final int MAX_GAP_BOX = 3;
    private static final int MAX_JUNCTION_DIAMETER = 8;

    static Outcome normalize(Collection<DrawnRiverGraph.Pixel> input, Predicate<DrawnRiverGraph.Pixel> blocked,
                             Set<DrawnRiverGraph.Edge> excludedEdges, Runnable check) {
        Set<DrawnRiverGraph.Pixel> mask = new HashSet<>(input);
        Set<DrawnRiverGraph.Pixel> repairs = new HashSet<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (mask.isEmpty()) {
            return new Outcome(mask, repairs, List.of(), diagnostics);
        }

        repairSmallGaps(mask, repairs, diagnostics, blocked, check);
        DrawnRiverGraph.thin(mask, check);

        List<JunctionRegion> regions = new ArrayList<>();
        breakLocalJunctionCycles(mask, regions, diagnostics, excludedEdges, check);
        DrawnRiverGraph.thin(mask, check);

        return new Outcome(mask, repairs, regions, diagnostics);
    }

    private static void repairSmallGaps(Set<DrawnRiverGraph.Pixel> mask, Set<DrawnRiverGraph.Pixel> repairs,
                                        List<Diagnostic> diagnostics, Predicate<DrawnRiverGraph.Pixel> blocked,
                                        Runnable check) {
        // Only empty cells next to the stroke — never flood the drawing's bounding box.
        List<DrawnRiverGraph.Pixel> seeds = new ArrayList<>(mask);
        Collections.sort(seeds);
        Set<DrawnRiverGraph.Pixel> considered = new HashSet<>();
        for (DrawnRiverGraph.Pixel p : seeds) {
            check.run();
            if (!mask.contains(p)) continue;
            for (int[] d : CARDINAL) {
                DrawnRiverGraph.Pixel start = new DrawnRiverGraph.Pixel(p.x() + d[0], p.y() + d[1]);
                if (mask.contains(start) || !considered.add(start)) continue;
                List<DrawnRiverGraph.Pixel> hole = growTinyEnclosedHole(start, mask, considered, check);
                if (hole == null) continue;
                if (blocked != null) {
                    boolean bad = false;
                    for (DrawnRiverGraph.Pixel h : hole) if (blocked.test(h)) { bad = true; break; }
                    if (bad) continue;
                }
                if (countExtendingArms(mask, hole, check) < 3) continue;

                Set<DrawnRiverGraph.Pixel> before = new HashSet<>(mask);
                Set<DrawnRiverGraph.Pixel> endsBefore = endpoints(mask);
                mask.addAll(hole);
                DrawnRiverGraph.thin(mask, check);
                Set<DrawnRiverGraph.Pixel> endsAfter = endpoints(mask);
                if (!endsAfter.containsAll(endsBefore)) {
                    mask.clear();
                    mask.addAll(before);
                    continue;
                }
                repairs.addAll(hole);
                int minX = hole.stream().mapToInt(DrawnRiverGraph.Pixel::x).min().orElse(0);
                int maxX = hole.stream().mapToInt(DrawnRiverGraph.Pixel::x).max().orElse(0);
                int minY = hole.stream().mapToInt(DrawnRiverGraph.Pixel::y).min().orElse(0);
                int maxY = hole.stream().mapToInt(DrawnRiverGraph.Pixel::y).max().orElse(0);
                diagnostics.add(new Diagnostic(IssueKind.SMALL_GAP_REPAIRED,
                        new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1), List.copyOf(hole),
                        "Birleşim boşluğu geçici maskede kapatıldı"));
            }
        }
    }

    /**
     * 4-connected empty blob starting at {@code seed}, aborting as soon as it cannot be a ≤4 / ≤3×3 hole.
     * Enclosed iff every cardinal neighbour is in the hole or the mask.
     */
    private static List<DrawnRiverGraph.Pixel> growTinyEnclosedHole(DrawnRiverGraph.Pixel seed,
                                                                    Set<DrawnRiverGraph.Pixel> mask,
                                                                    Set<DrawnRiverGraph.Pixel> considered,
                                                                    Runnable check) {
        Set<DrawnRiverGraph.Pixel> hole = new HashSet<>();
        ArrayDeque<DrawnRiverGraph.Pixel> q = new ArrayDeque<>();
        hole.add(seed);
        q.add(seed);
        int minX = seed.x(), maxX = seed.x(), minY = seed.y(), maxY = seed.y();
        boolean tooBig = false;
        while (!q.isEmpty()) {
            check.run();
            DrawnRiverGraph.Pixel p = q.remove();
            for (int[] d : CARDINAL) {
                DrawnRiverGraph.Pixel n = new DrawnRiverGraph.Pixel(p.x() + d[0], p.y() + d[1]);
                if (mask.contains(n) || hole.contains(n)) continue;
                int nMinX = Math.min(minX, n.x()), nMaxX = Math.max(maxX, n.x());
                int nMinY = Math.min(minY, n.y()), nMaxY = Math.max(maxY, n.y());
                considered.add(n);
                if (hole.size() + 1 > MAX_GAP_CELLS || nMaxX - nMinX + 1 > MAX_GAP_BOX || nMaxY - nMinY + 1 > MAX_GAP_BOX) {
                    tooBig = true;
                    continue;
                }
                hole.add(n);
                minX = nMinX;
                maxX = nMaxX;
                minY = nMinY;
                maxY = nMaxY;
                q.add(n);
            }
        }
        if (tooBig) return null;
        for (DrawnRiverGraph.Pixel p : hole) {
            for (int[] d : CARDINAL) {
                DrawnRiverGraph.Pixel n = new DrawnRiverGraph.Pixel(p.x() + d[0], p.y() + d[1]);
                if (!hole.contains(n) && !mask.contains(n)) return null;
            }
        }
        return new ArrayList<>(hole);
    }

    /**
     * Independent arms = mask components that leave the 1-cell ring around the hole.
     * Hole-adjacent ring pixels alone are not arms (a closed 3×3 has four contacts and zero arms).
     */
    private static int countExtendingArms(Set<DrawnRiverGraph.Pixel> mask, List<DrawnRiverGraph.Pixel> hole,
                                          Runnable check) {
        Set<DrawnRiverGraph.Pixel> holeSet = new HashSet<>(hole);
        Set<DrawnRiverGraph.Pixel> local = new HashSet<>(holeSet);
        for (DrawnRiverGraph.Pixel h : hole) {
            check.run();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                DrawnRiverGraph.Pixel n = new DrawnRiverGraph.Pixel(h.x() + dx, h.y() + dy);
                if (mask.contains(n)) local.add(n);
            }
        }
        Set<DrawnRiverGraph.Pixel> seeds = new HashSet<>();
        for (DrawnRiverGraph.Pixel p : local) {
            if (holeSet.contains(p)) continue;
            for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(p, mask)) {
                if (!local.contains(n)) seeds.add(n);
            }
        }
        Set<DrawnRiverGraph.Pixel> seen = new HashSet<>();
        int arms = 0;
        for (DrawnRiverGraph.Pixel start : seeds) {
            check.run();
            if (!seen.add(start)) continue;
            arms++;
            ArrayDeque<DrawnRiverGraph.Pixel> q = new ArrayDeque<>();
            q.add(start);
            int walked = 0;
            while (!q.isEmpty() && walked < 256) {
                check.run();
                DrawnRiverGraph.Pixel p = q.remove();
                walked++;
                for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(p, mask)) {
                    if (local.contains(n) || !seen.add(n)) continue;
                    q.add(n);
                }
            }
        }
        return arms;
    }

    private static void breakLocalJunctionCycles(Set<DrawnRiverGraph.Pixel> mask, List<JunctionRegion> regions,
                                                 List<Diagnostic> diagnostics,
                                                 Set<DrawnRiverGraph.Edge> excludedEdges, Runnable check) {
        Set<DrawnRiverGraph.Edge> excluded = excludedEdges == null ? Set.of() : excludedEdges;
        List<DrawnRiverGraph.Pixel> ordered = new ArrayList<>(mask);
        Collections.sort(ordered);
        Set<DrawnRiverGraph.Pixel> visited = new HashSet<>();
        for (DrawnRiverGraph.Pixel first : ordered) {
            check.run();
            if (!visited.add(first)) continue;
            List<DrawnRiverGraph.Pixel> component = new ArrayList<>();
            ArrayDeque<DrawnRiverGraph.Pixel> q = new ArrayDeque<>();
            q.add(first);
            long edges = 0;
            while (!q.isEmpty()) {
                check.run();
                DrawnRiverGraph.Pixel p = q.remove();
                component.add(p);
                List<DrawnRiverGraph.Pixel> ns = DrawnRiverGraph.neighboursPublic(p, mask, excluded);
                edges += ns.size();
                for (DrawnRiverGraph.Pixel n : ns) if (visited.add(n)) q.add(n);
            }
            if (component.size() < 3 || edges / 2 == component.size() - 1) continue;

            // Only break compact cycles (junction rings), never large network loops.
            List<DrawnRiverGraph.Pixel> cycle = findShortCycle(component, mask, excluded, 8, check);
            if (cycle == null) continue;
            Set<DrawnRiverGraph.Pixel> blob = new HashSet<>(cycle);
            ArrayDeque<DrawnRiverGraph.Pixel> grow = new ArrayDeque<>(cycle);
            while (!grow.isEmpty()) {
                check.run();
                DrawnRiverGraph.Pixel p = grow.remove();
                for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(p, mask, excluded)) {
                    if (blob.contains(n)) continue;
                    if (chebyshevDiameter(blob, n) > MAX_JUNCTION_DIAMETER) continue;
                    if (DrawnRiverGraph.neighboursPublic(n, mask, excluded).size() >= 3 || cycle.contains(n)) {
                        blob.add(n);
                        grow.add(n);
                    }
                }
            }
            if (chebyshevSpan(blob) > MAX_JUNCTION_DIAMETER) continue;

            List<DrawnRiverGraph.Pixel> ports = new ArrayList<>();
            for (DrawnRiverGraph.Pixel p : blob) {
                for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(p, mask, excluded)) {
                    if (!blob.contains(n)) ports.add(n);
                }
            }
            // Real loops (closed rings without arms) stay REAL_LOOP; junction rings have ≥3 exits.
            if (ports.size() < 3) continue;
            if (!hasCycleIn(blob, mask, excluded)) continue;

            DrawnRiverGraph.Pixel rep = pickRepresentative(blob, ports);
            Set<DrawnRiverGraph.Pixel> keep = spanningTreeKeepingPorts(blob, mask, ports, excluded, check);
            if (keep.size() < 2) continue;
            for (DrawnRiverGraph.Pixel p : blob) {
                if (!keep.contains(p)) mask.remove(p);
            }
            pruneToTree(keep, mask, ports, excluded, check);

            regions.add(new JunctionRegion(new HashSet<>(keep), rep));
            int minX = keep.stream().mapToInt(DrawnRiverGraph.Pixel::x).min().orElse(0);
            int maxX = keep.stream().mapToInt(DrawnRiverGraph.Pixel::x).max().orElse(0);
            int minY = keep.stream().mapToInt(DrawnRiverGraph.Pixel::y).min().orElse(0);
            int maxY = keep.stream().mapToInt(DrawnRiverGraph.Pixel::y).max().orElse(0);
            diagnostics.add(new Diagnostic(IssueKind.JUNCTION_REGION,
                    new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1),
                    List.copyOf(keep),
                    "Birleşim bölgesi yerelleştirildi"));
        }
    }

    private static List<DrawnRiverGraph.Pixel> findShortCycle(List<DrawnRiverGraph.Pixel> component,
                                                              Set<DrawnRiverGraph.Pixel> mask,
                                                              Set<DrawnRiverGraph.Edge> excluded,
                                                              int maxLen, Runnable check) {
        Set<DrawnRiverGraph.Pixel> set = new HashSet<>(component);
        for (DrawnRiverGraph.Pixel start : component) {
            check.run();
            List<DrawnRiverGraph.Pixel> path = new ArrayList<>();
            Set<DrawnRiverGraph.Pixel> onPath = new HashSet<>();
            List<DrawnRiverGraph.Pixel> found = dfsCycle(start, null, start, set, mask, excluded, path, onPath, maxLen, check);
            if (found != null) return found;
        }
        return null;
    }

    private static List<DrawnRiverGraph.Pixel> dfsCycle(DrawnRiverGraph.Pixel current, DrawnRiverGraph.Pixel parent,
                                                        DrawnRiverGraph.Pixel origin, Set<DrawnRiverGraph.Pixel> component,
                                                        Set<DrawnRiverGraph.Pixel> mask, Set<DrawnRiverGraph.Edge> excluded,
                                                        List<DrawnRiverGraph.Pixel> path,
                                                        Set<DrawnRiverGraph.Pixel> onPath, int maxLen, Runnable check) {
        check.run();
        path.add(current);
        onPath.add(current);
        for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(current, mask, excluded)) {
            if (!component.contains(n) || n.equals(parent)) continue;
            if (n.equals(origin) && path.size() >= 3 && path.size() <= maxLen) {
                return List.copyOf(path);
            }
            if (onPath.contains(n) || path.size() >= maxLen) continue;
            List<DrawnRiverGraph.Pixel> found = dfsCycle(n, current, origin, component, mask, excluded, path, onPath, maxLen, check);
            if (found != null) return found;
        }
        path.remove(path.size() - 1);
        onPath.remove(current);
        return null;
    }

    private static int chebyshevSpan(Set<DrawnRiverGraph.Pixel> cells) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (DrawnRiverGraph.Pixel p : cells) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        return Math.max(maxX - minX, maxY - minY);
    }

    private static int chebyshevDiameter(Set<DrawnRiverGraph.Pixel> cells, DrawnRiverGraph.Pixel extra) {
        int minX = extra.x(), maxX = extra.x(), minY = extra.y(), maxY = extra.y();
        for (DrawnRiverGraph.Pixel p : cells) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        return Math.max(maxX - minX, maxY - minY);
    }

    private static void pruneToTree(Set<DrawnRiverGraph.Pixel> keep, Set<DrawnRiverGraph.Pixel> mask,
                                    List<DrawnRiverGraph.Pixel> ports, Set<DrawnRiverGraph.Edge> excludedEdges,
                                    Runnable check) {
        Set<DrawnRiverGraph.Pixel> attachments = new HashSet<>();
        for (DrawnRiverGraph.Pixel port : ports) {
            for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(port, mask, excludedEdges)) {
                if (keep.contains(n)) attachments.add(n);
            }
        }
        while (hasCycleIn(keep, mask, excludedEdges)) {
            check.run();
            DrawnRiverGraph.Pixel drop = null;
            for (DrawnRiverGraph.Pixel p : keep.stream().sorted(Comparator.reverseOrder()).toList()) {
                if (attachments.contains(p)) continue;
                mask.remove(p);
                keep.remove(p);
                if (portsConnected(attachments, keep, mask, excludedEdges, check)) {
                    drop = p;
                    break;
                }
                keep.add(p);
                mask.add(p);
            }
            if (drop == null) break;
        }
    }

    private static boolean portsConnected(Set<DrawnRiverGraph.Pixel> attachments, Set<DrawnRiverGraph.Pixel> keep,
                                          Set<DrawnRiverGraph.Pixel> mask, Set<DrawnRiverGraph.Edge> excluded,
                                          Runnable check) {
        if (attachments.isEmpty()) return true;
        DrawnRiverGraph.Pixel start = attachments.iterator().next();
        if (!keep.contains(start)) return false;
        Set<DrawnRiverGraph.Pixel> seen = new HashSet<>();
        ArrayDeque<DrawnRiverGraph.Pixel> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty()) {
            check.run();
            DrawnRiverGraph.Pixel p = q.remove();
            for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(p, mask, excluded)) {
                if (!keep.contains(n) || !seen.add(n)) continue;
                q.add(n);
            }
        }
        return seen.containsAll(attachments);
    }

    private static boolean hasCycleIn(Set<DrawnRiverGraph.Pixel> subset, Set<DrawnRiverGraph.Pixel> mask,
                                      Set<DrawnRiverGraph.Edge> excluded) {
        long edges = 0;
        for (DrawnRiverGraph.Pixel p : subset) {
            for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(p, mask, excluded)) {
                if (subset.contains(n)) edges++;
            }
        }
        return edges / 2 > subset.size() - 1;
    }

    private static DrawnRiverGraph.Pixel pickRepresentative(Set<DrawnRiverGraph.Pixel> blob,
                                                            List<DrawnRiverGraph.Pixel> ports) {
        DrawnRiverGraph.Pixel best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        List<DrawnRiverGraph.Pixel> ordered = new ArrayList<>(blob);
        Collections.sort(ordered);
        for (DrawnRiverGraph.Pixel p : ordered) {
            double sum = 0;
            for (DrawnRiverGraph.Pixel port : ports) {
                sum += Math.hypot(p.x() - port.x(), p.y() - port.y());
            }
            if (sum < bestScore) {
                bestScore = sum;
                best = p;
            }
        }
        return best != null ? best : ordered.get(0);
    }

    private static Set<DrawnRiverGraph.Pixel> spanningTreeKeepingPorts(Set<DrawnRiverGraph.Pixel> blob,
                                                                       Set<DrawnRiverGraph.Pixel> mask,
                                                                       List<DrawnRiverGraph.Pixel> ports,
                                                                       Set<DrawnRiverGraph.Edge> excludedEdges,
                                                                       Runnable check) {
        Set<DrawnRiverGraph.Pixel> attachments = new HashSet<>();
        for (DrawnRiverGraph.Pixel port : ports) {
            for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(port, mask, excludedEdges)) {
                if (blob.contains(n)) attachments.add(n);
            }
        }
        if (attachments.isEmpty()) attachments.addAll(blob);
        DrawnRiverGraph.Pixel start = attachments.stream().min(Comparator.naturalOrder()).orElseThrow();
        Set<DrawnRiverGraph.Pixel> tree = new HashSet<>();
        tree.add(start);
        boolean grew;
        do {
            check.run();
            grew = false;
            DrawnRiverGraph.Pixel add = null;
            int bestKey = Integer.MAX_VALUE;
            for (DrawnRiverGraph.Pixel p : tree) {
                for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(p, mask, excludedEdges)) {
                    if (!blob.contains(n) || tree.contains(n)) continue;
                    int key = n.x() * 31 + n.y();
                    if (key < bestKey) {
                        bestKey = key;
                        add = n;
                    }
                }
            }
            if (add != null) {
                tree.add(add);
                grew = true;
            }
        } while (grew && !tree.containsAll(attachments));

        for (DrawnRiverGraph.Pixel att : attachments) {
            if (tree.contains(att)) continue;
            List<DrawnRiverGraph.Pixel> path = bfsPath(start, att, blob, mask, excludedEdges, check);
            if (path != null) tree.addAll(path);
        }
        return tree;
    }

    private static List<DrawnRiverGraph.Pixel> bfsPath(DrawnRiverGraph.Pixel start, DrawnRiverGraph.Pixel goal,
                                                       Set<DrawnRiverGraph.Pixel> blob, Set<DrawnRiverGraph.Pixel> mask,
                                                       Set<DrawnRiverGraph.Edge> excludedEdges, Runnable check) {
        ArrayDeque<DrawnRiverGraph.Pixel> q = new ArrayDeque<>();
        Map<DrawnRiverGraph.Pixel, DrawnRiverGraph.Pixel> parent = new HashMap<>();
        q.add(start);
        parent.put(start, null);
        while (!q.isEmpty()) {
            check.run();
            DrawnRiverGraph.Pixel p = q.remove();
            if (p.equals(goal)) {
                List<DrawnRiverGraph.Pixel> path = new ArrayList<>();
                for (DrawnRiverGraph.Pixel c = goal; c != null; c = parent.get(c)) path.add(c);
                Collections.reverse(path);
                return path;
            }
            for (DrawnRiverGraph.Pixel n : DrawnRiverGraph.neighboursPublic(p, mask, excludedEdges)) {
                if (!blob.contains(n) || parent.containsKey(n)) continue;
                parent.put(n, p);
                q.add(n);
            }
        }
        return null;
    }

    private static Set<DrawnRiverGraph.Pixel> endpoints(Set<DrawnRiverGraph.Pixel> mask) {
        Set<DrawnRiverGraph.Pixel> ends = new HashSet<>();
        for (DrawnRiverGraph.Pixel p : mask) {
            if (DrawnRiverGraph.neighboursPublic(p, mask).size() == 1) ends.add(p);
        }
        return ends;
    }

    private DrawnRiverNormalizer() { }
}
