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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Read-only, deterministic extraction of ALL strokes. No DEM filling or world writes. */
public final class DrawnRiverGraph {
    public record Pixel(int x, int y) implements Comparable<Pixel> {
        public int compareTo(Pixel p) { int c = Integer.compare(x, p.x); return c != 0 ? c : Integer.compare(y, p.y); }
    }

    /** Undirected skeleton edge for optional preview exclusion. */
    public record Edge(Pixel a, Pixel b) {
        public Edge {
            if (a.compareTo(b) > 0) {
                Pixel t = a;
                a = b;
                b = t;
            }
        }
        public static Edge of(Pixel u, Pixel v) { return new Edge(u, v); }
    }

    /** Preferred network drain direction for outlet selection. */
    public enum Drain {
        AUTO, SW, S, W, SE, N, E, NE, NW;

        public static Drain parse(String raw) {
            if (raw == null || raw.isBlank()) return AUTO;
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "SW", "SOL_ALT", "SOUTHWEST" -> SW;
                case "S", "ALT", "SOUTH" -> S;
                case "W", "SOL", "WEST" -> W;
                case "SE", "SAG_ALT", "SOUTHEAST" -> SE;
                case "N", "UST", "NORTH" -> N;
                case "E", "SAG", "EAST" -> E;
                case "NE", "SAG_UST", "NORTHEAST" -> NE;
                case "NW", "SOL_UST", "NORTHWEST" -> NW;
                default -> AUTO;
            };
        }

        /** Unit-ish projection: higher means more toward this drain. */
        double projection(Pixel p, Rectangle bounds) {
            if (this == AUTO || bounds.width <= 0 || bounds.height <= 0) return 0;
            double nx = bounds.width <= 1 ? 0.5 : (p.x - bounds.x) / (double) (bounds.width - 1);
            double ny = bounds.height <= 1 ? 0.5 : (p.y - bounds.y) / (double) (bounds.height - 1);
            return switch (this) {
                case S -> ny;
                case N -> 1 - ny;
                case E -> nx;
                case W -> 1 - nx;
                case SE -> (nx + ny) * 0.5;
                case SW -> ((1 - nx) + ny) * 0.5;
                case NE -> (nx + (1 - ny)) * 0.5;
                case NW -> ((1 - nx) + (1 - ny)) * 0.5;
                default -> 0;
            };
        }
    }

    /**
     * Outlet selection policy. When {@code windowedHeight}/{@code atWorldEdge} are null,
     * falls back to raw height and no edge bonus (legacy AUTO behaviour).
     */
    public record OutletPolicy(Drain drain, Pixel override,
                               ToDoubleFunction<Pixel> windowedHeight,
                               Predicate<Pixel> atWorldEdge) {
        public OutletPolicy {
            drain = drain == null ? Drain.AUTO : drain;
        }

        public static OutletPolicy auto() {
            return new OutletPolicy(Drain.AUTO, null, null, null);
        }

        public static OutletPolicy of(Drain drain, Pixel override,
                                      ToDoubleFunction<Pixel> windowedHeight,
                                      Predicate<Pixel> atWorldEdge) {
            return new OutletPolicy(drain, override, windowedHeight, atWorldEdge);
        }
    }

    /**
     * Pen annotations from River Mini / Outlet / Continue layers.
     * Path pixels are the default skeleton; mini and continue are also skeleton.
     */
    public record StrokeHints(Set<Pixel> markedOutlets, Set<Pixel> miniPixels,
                              Set<Pixel> continuationPixels) {
        public StrokeHints {
            markedOutlets = markedOutlets == null ? Set.of() : Set.copyOf(markedOutlets);
            miniPixels = miniPixels == null ? Set.of() : Set.copyOf(miniPixels);
            continuationPixels = continuationPixels == null ? Set.of() : Set.copyOf(continuationPixels);
        }

        public static StrokeHints none() {
            return new StrokeHints(Set.of(), Set.of(), Set.of());
        }

        public boolean isEmpty() {
            return markedOutlets.isEmpty() && miniPixels.isEmpty() && continuationPixels.isEmpty();
        }
    }

    /** Minimum channel width for River Mini source tips (carver allows ≥1). */
    public static final double MINI_WIDTH = 1.0;
    /** Continuation trunks start at least this wide (capped by maximumWidth). */
    public static final double CONTINUATION_MIN_WIDTH = 8.0;

    public record Reach(List<Pixel> pixels, double width, int contributors) {
        public Reach { pixels = List.copyOf(pixels); }
    }

    public record Basin(List<Reach> downstreamFirst, int sources, int junctions,
                        Pixel outlet, boolean edgeOutlet) {
        public Basin {
            downstreamFirst = List.copyOf(downstreamFirst);
        }

        /** Backward-compatible constructor used by older call sites/tests. */
        public Basin(List<Reach> downstreamFirst, int sources, int junctions) {
            this(downstreamFirst, sources, junctions, null, false);
        }
    }

    public record Result(List<Basin> basins, List<String> warnings, Set<Pixel> skeleton,
                         List<DrawnRiverNormalizer.Diagnostic> diagnostics,
                         List<DrawnRiverNormalizer.JunctionRegion> junctionRegions,
                         Set<Pixel> repairs,
                         List<String> orientationNotes) {
        public Result {
            basins = List.copyOf(basins);
            warnings = List.copyOf(warnings);
            skeleton = Set.copyOf(skeleton);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
            junctionRegions = junctionRegions == null ? List.of() : List.copyOf(junctionRegions);
            repairs = repairs == null ? Set.of() : Set.copyOf(repairs);
            orientationNotes = orientationNotes == null ? List.of() : List.copyOf(orientationNotes);
        }

        /** Legacy 6-arg constructor. */
        public Result(List<Basin> basins, List<String> warnings, Set<Pixel> skeleton,
                      List<DrawnRiverNormalizer.Diagnostic> diagnostics,
                      List<DrawnRiverNormalizer.JunctionRegion> junctionRegions,
                      Set<Pixel> repairs) {
            this(basins, warnings, skeleton, diagnostics, junctionRegions, repairs, List.of());
        }
    }

    public static final double EDGE_BONUS = 25.0;
    public static final double DRAIN_BONUS_SCALE = 24.0;
    public static final int WINDOW_RADIUS = 12;
    public static final double WINDOW_PERCENTILE = 0.20;
    private static final int[][] OFFSETS = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

    public static Result build(Collection<Pixel> input, ToDoubleFunction<Pixel> height,
                               java.util.function.Predicate<Pixel> receivingWater,
                               double sourceWidth, double maximumWidth, Runnable check) {
        return build(input, height, receivingWater, sourceWidth, maximumWidth, null, Set.of(),
                OutletPolicy.auto(), StrokeHints.none(), check);
    }

    public static Result build(Collection<Pixel> input, ToDoubleFunction<Pixel> height,
                               java.util.function.Predicate<Pixel> receivingWater,
                               double sourceWidth, double maximumWidth,
                               Predicate<Pixel> blockedCells,
                               Set<Edge> excludedEdges,
                               Runnable check) {
        return build(input, height, receivingWater, sourceWidth, maximumWidth, blockedCells,
                excludedEdges, OutletPolicy.auto(), StrokeHints.none(), check);
    }

    public static Result build(Collection<Pixel> input, ToDoubleFunction<Pixel> height,
                               java.util.function.Predicate<Pixel> receivingWater,
                               double sourceWidth, double maximumWidth,
                               Predicate<Pixel> blockedCells,
                               Set<Edge> excludedEdges,
                               OutletPolicy policy,
                               Runnable check) {
        return build(input, height, receivingWater, sourceWidth, maximumWidth, blockedCells,
                excludedEdges, policy, StrokeHints.none(), check);
    }

    public static Result build(Collection<Pixel> input, ToDoubleFunction<Pixel> height,
                               java.util.function.Predicate<Pixel> receivingWater,
                               double sourceWidth, double maximumWidth,
                               Predicate<Pixel> blockedCells,
                               Set<Edge> excludedEdges,
                               OutletPolicy policy,
                               StrokeHints strokeHints,
                               Runnable check) {
        if (!Double.isFinite(sourceWidth) || !Double.isFinite(maximumWidth) || sourceWidth < 1
                || maximumWidth < sourceWidth || maximumWidth > 128)
            throw new IllegalArgumentException("Geçersiz ağ genişliği");
        if (input.size() > 20_000_000)
            throw new IllegalArgumentException("Çizim 20.000.000 hücreyi aşıyor; daha küçük bir ağ seçin.");
        OutletPolicy pol = policy == null ? OutletPolicy.auto() : policy;
        StrokeHints hints = strokeHints == null ? StrokeHints.none() : strokeHints;
        ToDoubleFunction<Pixel> elev = pol.windowedHeight() != null ? pol.windowedHeight() : height;
        Predicate<Pixel> edge = pol.atWorldEdge() != null ? pol.atWorldEdge() : p -> false;

        Set<Edge> excluded = excludedEdges == null || excludedEdges.isEmpty() ? Set.of() : Set.copyOf(excludedEdges);
        DrawnRiverNormalizer.Outcome norm = DrawnRiverNormalizer.normalize(
                input, blockedCells, excluded, check);
        Set<Pixel> pixels = new HashSet<>(norm.mask());
        List<DrawnRiverNormalizer.Diagnostic> diagnostics = new ArrayList<>(norm.diagnostics());

        List<Pixel> ordered = new ArrayList<>(pixels);
        Collections.sort(ordered);
        Set<Pixel> visited = new HashSet<>();
        List<Basin> basins = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> orientationNotes = new ArrayList<>();

        for (Pixel first : ordered) {
            check.run();
            if (!visited.add(first)) continue;
            List<Pixel> component = new ArrayList<>();
            ArrayDeque<Pixel> queue = new ArrayDeque<>();
            queue.add(first);
            long edgeCount = 0;
            while (!queue.isEmpty()) {
                check.run();
                Pixel p = queue.remove();
                component.add(p);
                List<Pixel> neighbours = neighbours(p, pixels, excluded);
                edgeCount += neighbours.size();
                for (Pixel n : neighbours) if (visited.add(n)) queue.add(n);
            }
            if (component.size() < 2) {
                warnings.add("Tek çizim noktası: " + first.x + "," + first.y);
                diagnostics.add(new DrawnRiverNormalizer.Diagnostic(
                        DrawnRiverNormalizer.IssueKind.UNRESOLVED, boundsOf(component), List.copyOf(component),
                        "Tek çizim noktası"));
                continue;
            }
            if (edgeCount / 2 != component.size() - 1) {
                Rectangle bb = boundsOf(component);
                List<Pixel> highlight = cycleHighlight(component, pixels, excluded, check);
                diagnostics.add(new DrawnRiverNormalizer.Diagnostic(
                        DrawnRiverNormalizer.IssueKind.REAL_LOOP, bb, highlight,
                        "Kapalı döngü / belirsiz birleşim; bu çizim grubu uygulanmayacak"));
                warnings.add(diagnostics.get(diagnostics.size() - 1).message());
                continue;
            }
            List<Pixel> ends = component.stream().filter(p -> neighbours(p, pixels, excluded).size() == 1).toList();
            List<Pixel> wetEnds = ends.stream().filter(receivingWater).toList();
            Rectangle componentBounds = boundsOf(component);

            List<Pixel> forcedOutlets = snapMarkedOutlets(hints.markedOutlets(), component, ends);
            if (forcedOutlets.isEmpty() && !hints.markedOutlets().isEmpty()) {
                warnings.add("Çıkış kalemi bu grubun açık uçlarına yakın değil; otomatik çıkış kullanıldı.");
            }
            if (forcedOutlets.isEmpty() && pol.override() != null) {
                Pixel near = nearestEnd(ends, pol.override(), 2);
                if (near != null) forcedOutlets = List.of(near);
            }

            if (!forcedOutlets.isEmpty()) {
                extractBasinsForOutlets(component, pixels, excluded, forcedOutlets, elev, edge, receivingWater,
                        sourceWidth, maximumWidth, hints, basins, warnings, orientationNotes, diagnostics, check);
                continue;
            }

            if (ends.isEmpty()) {
                diagnostics.add(new DrawnRiverNormalizer.Diagnostic(
                        DrawnRiverNormalizer.IssueKind.UNRESOLVED, boundsOf(component), List.copyOf(component),
                        "Açık uç yok; çizimi kontrol edin"));
                warnings.add(diagnostics.get(diagnostics.size() - 1).message());
                continue;
            }

            Pixel outlet = selectOutlet(ends, wetEnds, elev, edge, pol, componentBounds, hints, warnings, diagnostics);
            if (outlet == null) continue;
            extractBasinsForOutlets(component, pixels, excluded, List.of(outlet), elev, edge, receivingWater,
                    sourceWidth, maximumWidth, hints, basins, warnings, orientationNotes, diagnostics, check);
        }
        return new Result(basins, warnings, pixels, diagnostics, norm.regions(), norm.repairs(), orientationNotes);
    }

    /**
     * Multi-sink BFS from the given outlets, then one {@link Basin} per outlet.
     * Continuations and Mini tips are inflows (user starts); BFS roots are mouths only.
     */
    private static void extractBasinsForOutlets(
            List<Pixel> component, Set<Pixel> pixels, Set<Edge> excluded,
            List<Pixel> outlets, ToDoubleFunction<Pixel> elev, Predicate<Pixel> edge,
            Predicate<Pixel> receivingWater, double sourceWidth, double maximumWidth,
            StrokeHints hints, List<Basin> basins, List<String> warnings,
            List<String> orientationNotes, List<DrawnRiverNormalizer.Diagnostic> diagnostics,
            Runnable check) {
        Set<Pixel> componentSet = new HashSet<>(component);
        Map<Pixel, Pixel> downstream = new HashMap<>();
        Map<Pixel, Pixel> nearestOutlet = new HashMap<>();
        ArrayDeque<Pixel> queue = new ArrayDeque<>();
        for (Pixel o : outlets) {
            if (!componentSet.contains(o)) continue;
            queue.add(o);
            downstream.put(o, null);
            nearestOutlet.put(o, o);
        }
        if (nearestOutlet.isEmpty()) return;

        while (!queue.isEmpty()) {
            check.run();
            Pixel p = queue.remove();
            for (Pixel n : neighbours(p, pixels, excluded)) {
                if (!componentSet.contains(n) || downstream.containsKey(n)) continue;
                downstream.put(n, p);
                nearestOutlet.put(n, nearestOutlet.get(p));
                queue.add(n);
            }
        }

        Map<Pixel, List<Pixel>> territory = new HashMap<>();
        for (Pixel p : component) {
            Pixel o = nearestOutlet.get(p);
            if (o != null) territory.computeIfAbsent(o, k -> new ArrayList<>()).add(p);
        }

        for (Pixel outlet : outlets) {
            check.run();
            List<Pixel> cells = territory.get(outlet);
            if (cells == null || cells.isEmpty()) continue;
            Set<Pixel> cellSet = new HashSet<>(cells);
            boolean isEdgeOutlet = edge.test(outlet) && !receivingWater.test(outlet);
            if (!receivingWater.test(outlet)) {
                warnings.add("Açık uç " + outlet.x + "," + outlet.y
                        + (isEdgeOutlet ? " kenar ağzı olarak seçildi" : " çıkış olarak önerildi")
                        + (hints.markedOutlets().contains(outlet) || nearAny(hints.markedOutlets(), outlet, 2)
                        ? " (çıkış kalemi)" : "")
                        + "; deniz/göl bağlantısı doğrulanmadı.");
            }
            orientationNotes.add("Çıkış " + outlet.x + "," + outlet.y
                    + (isEdgeOutlet ? " (kenar)" : "")
                    + (outlets.size() > 1 ? " [" + outlets.size() + " ağız]" : "")
                    + ".");

            Map<Pixel, Integer> incoming = new HashMap<>();
            Map<Pixel, Integer> contributions = new HashMap<>();
            List<Pixel> order = new ArrayList<>();
            ArrayDeque<Pixel> bfs = new ArrayDeque<>();
            bfs.add(outlet);
            Set<Pixel> seen = new HashSet<>();
            seen.add(outlet);
            while (!bfs.isEmpty()) {
                check.run();
                Pixel p = bfs.remove();
                order.add(p);
                for (Pixel n : neighbours(p, pixels, excluded)) {
                    if (!cellSet.contains(n) || !seen.add(n)) continue;
                    if (!Objects.equals(downstream.get(n), p)) continue;
                    bfs.add(n);
                }
            }
            for (Pixel p : order) {
                int up = 0;
                for (Pixel n : neighbours(p, pixels, excluded)) {
                    if (cellSet.contains(n) && Objects.equals(downstream.get(n), p)) up++;
                }
                incoming.put(p, up);
            }
            int sources = 0, junctions = 0;
            for (int i = order.size() - 1; i >= 0; i--) {
                check.run();
                Pixel p = order.get(i);
                int in = incoming.getOrDefault(p, 0);
                if (in == 0) {
                    contributions.put(p, Math.max(1, (int) Math.round(
                            seedContribution(p, hints, sourceWidth, maximumWidth) * 1000)));
                    sources++;
                } else if (in > 1) junctions++;
                Pixel next = downstream.get(p);
                if (next != null && cellSet.contains(next)) {
                    contributions.merge(next, contributions.getOrDefault(p, 0), Integer::sum);
                }
            }

            List<Reach> reaches = new ArrayList<>();
            for (Pixel end : order) {
                check.run();
                if (!end.equals(outlet) && incoming.getOrDefault(end, 0) == 1) continue;
                for (Pixel upstream : neighbours(end, pixels, excluded)) {
                    if (!cellSet.contains(upstream) || !end.equals(downstream.get(upstream))) continue;
                    List<Pixel> line = new ArrayList<>();
                    line.add(end);
                    Pixel p = upstream;
                    while (true) {
                        check.run();
                        line.add(p);
                        if (incoming.getOrDefault(p, 0) != 1) break;
                        Pixel current = p;
                        p = neighbours(p, pixels, excluded).stream()
                                .filter(n -> cellSet.contains(n) && current.equals(downstream.get(n)))
                                .findFirst().orElse(null);
                        if (p == null) break;
                    }
                    Collections.reverse(line);
                    if (line.size() < 2) continue;
                    Pixel head = line.get(0);
                    int raw = contributions.getOrDefault(head, 1000);
                    double seed = raw / 1000.0;
                    double width = Math.min(maximumWidth, sourceWidth * Math.sqrt(Math.max(seed, 1e-6)));
                    // Mini tips: force floor near MINI_WIDTH even when sourceWidth is larger.
                    if (nearPreferredSource(head, hints.miniPixels(), SOURCE_SNAP)
                            && !nearPreferredSource(head, hints.continuationPixels(), SOURCE_SNAP)) {
                        width = Math.min(maximumWidth, Math.max(MINI_WIDTH, sourceWidth * Math.sqrt(seed)));
                    }
                    reaches.add(new Reach(line, width, Math.max(1, (int) Math.round(seed))));
                    Pixel tip = line.get(line.size() - 1);
                    double hHead = elev.applyAsDouble(head);
                    double hTip = elev.applyAsDouble(tip);
                    if (Double.isFinite(hHead) && Double.isFinite(hTip) && hTip > hHead + 0.5) {
                        orientationNotes.add("Kol " + head + " → " + tip
                                + " yukarı akıyor; çıkış seçimini gözden geçirin.");
                    }
                }
            }
            if (reaches.isEmpty()) {
                warnings.add("Çıkış " + outlet.x + "," + outlet.y
                        + " için kol üretilemedi (muhtemelen orta hatta işaret; yalnız uçlara Outlet koyun).");
                continue;
            }
            basins.add(new Basin(reaches, sources, junctions, outlet, isEdgeOutlet));
        }
        if (outlets.size() > 1) {
            orientationNotes.add("Çıkış kalemi: " + outlets.size()
                    + " ağız; ağ her ağza en yakın yoldan yönlendirildi.");
        }
    }

    /** Contribution seed relative to sourceWidth (width ≈ sourceWidth * sqrt(seed)). */
    static double seedContribution(Pixel leaf, StrokeHints hints, double sourceWidth, double maximumWidth) {
        if (sourceWidth <= 0) return 1;
        if (nearPreferredSource(leaf, hints.continuationPixels(), SOURCE_SNAP)) {
            double base = Math.min(maximumWidth, Math.max(sourceWidth * 2.5, CONTINUATION_MIN_WIDTH));
            return Math.pow(base / sourceWidth, 2);
        }
        if (nearPreferredSource(leaf, hints.miniPixels(), SOURCE_SNAP)) {
            return Math.pow(MINI_WIDTH / sourceWidth, 2);
        }
        return 1.0;
    }

    /** Snap distance when matching Mini / Continue paint onto skeleton tips. */
    static final int SOURCE_SNAP = 4;

    /**
     * Mini and Continue pens mark inflows (user-selected starts), never preferred mouths.
     * Marks within {@link #SOURCE_SNAP} of a tip count — users often paint a short blob.
     */
    static boolean isPreferredSourceTip(Pixel tip, StrokeHints hints) {
        if (tip == null || hints == null || hints.isEmpty()) return false;
        return nearPreferredSource(tip, hints.miniPixels(), SOURCE_SNAP)
                || nearPreferredSource(tip, hints.continuationPixels(), SOURCE_SNAP);
    }

    static boolean nearPreferredSource(Pixel tip, Set<Pixel> marks, int maxDist) {
        if (tip == null || marks == null || marks.isEmpty()) return false;
        if (marks.contains(tip)) return true;
        return nearAny(marks, tip, maxDist);
    }

    private static boolean nearAny(Set<Pixel> set, Pixel p, int maxDist) {
        for (Pixel q : set) {
            if (Math.hypot(q.x - p.x, q.y - p.y) <= maxDist) return true;
        }
        return false;
    }

    /**
     * Snap outlet-pen marks onto degree-1 ends only. Mid-path marks are ignored —
     * treating them as mouths partitions the tree into empty basins and crashes prepare.
     */
    static List<Pixel> snapMarkedOutlets(Set<Pixel> marks, List<Pixel> component, List<Pixel> ends) {
        if (marks == null || marks.isEmpty() || ends == null || ends.isEmpty()) return List.of();
        Set<Pixel> snapped = new HashSet<>();
        for (Pixel mark : marks) {
            Pixel onEnd = nearestEnd(ends, mark, 4);
            if (onEnd != null) snapped.add(onEnd);
        }
        List<Pixel> ordered = new ArrayList<>(snapped);
        Collections.sort(ordered);
        return ordered;
    }

    private static Pixel selectOutlet(List<Pixel> ends, List<Pixel> wetEnds,
                                      ToDoubleFunction<Pixel> elev, Predicate<Pixel> edge,
                                      OutletPolicy pol, Rectangle bounds,
                                      StrokeHints hints,
                                      List<String> warnings,
                                      List<DrawnRiverNormalizer.Diagnostic> diagnostics) {
        if (pol.override() != null) {
            Pixel near = nearestEnd(ends, pol.override(), 2);
            if (near != null) {
                if (wetEnds.size() > 1) {
                    warnings.add("Birden fazla ıslak uç vardı; manuel çıkış " + near.x + "," + near.y + " seçildi.");
                }
                return near;
            }
            warnings.add("Manuel çıkış " + pol.override().x + "," + pol.override().y
                    + " bu havzanın uçlarına yakın değil; otomatik seçim kullanıldı.");
        }

        if (wetEnds.size() == 1) return wetEnds.get(0);

        if (wetEnds.size() > 1) {
            if (pol.drain() == Drain.AUTO && pol.override() == null) {
                Rectangle bb = boundsOf(wetEnds);
                diagnostics.add(new DrawnRiverNormalizer.Diagnostic(
                        DrawnRiverNormalizer.IssueKind.MULTI_OUTLET, bb, List.copyOf(wetEnds),
                        "Birden fazla su çıkışı; çıkış belirsiz, çizimi ayırın"));
                warnings.add(diagnostics.get(diagnostics.size() - 1).message());
                return null;
            }
            Pixel chosen = scorePick(wetEnds, elev, edge, pol.drain(), bounds, hints);
            warnings.add("Birden fazla ıslak uç; " + pol.drain() + " tercihiyle "
                    + chosen.x + "," + chosen.y + " seçildi.");
            return chosen;
        }

        return scorePick(ends, elev, edge, pol.drain(), bounds, hints);
    }

    private static Pixel scorePick(List<Pixel> candidates, ToDoubleFunction<Pixel> elev,
                                   Predicate<Pixel> edge, Drain drain, Rectangle bounds,
                                   StrokeHints hints) {
        StrokeHints h = hints == null ? StrokeHints.none() : hints;
        // User Mini/Continue tips are starts: never prefer them as mouths when another end exists.
        List<Pixel> nonSource = new ArrayList<>();
        for (Pixel p : candidates) {
            if (!isPreferredSourceTip(p, h)) nonSource.add(p);
        }
        List<Pixel> pool = nonSource.isEmpty() ? candidates : nonSource;
        return pool.stream()
                .min(Comparator
                        .comparingDouble((Pixel p) -> {
                            double height = elev.applyAsDouble(p);
                            if (!Double.isFinite(height)) height = Double.POSITIVE_INFINITY;
                            double bonus = (edge.test(p) ? EDGE_BONUS : 0)
                                    + (drain == Drain.AUTO ? 0 : drain.projection(p, bounds) * DRAIN_BONUS_SCALE);
                            // Last-resort pool (all tips are Mini/Continue): still avoid edge Continue mouths.
                            double inflowPenalty = (h.continuationPixels().contains(p) && edge.test(p)) ? 1000 : 0;
                            return height - bonus + inflowPenalty;
                        })
                        .thenComparing(Comparator.naturalOrder()))
                .orElseThrow();
    }

    private static Pixel nearestEnd(List<Pixel> ends, Pixel target, int maxDist) {
        Pixel best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Pixel e : ends) {
            double d = Math.hypot(e.x - target.x, e.y - target.y);
            if (d <= maxDist && d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * 20th-percentile height in a disk of {@link #WINDOW_RADIUS}. Falls back to raw height
     * when fewer than 3 finite samples are available.
     */
    public static double windowedHeight(Pixel center, ToDoubleFunction<Pixel> rawHeight, int radius) {
        List<Double> samples = new ArrayList<>();
        int r = Math.max(1, radius);
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r * r) continue;
                double h = rawHeight.applyAsDouble(new Pixel(center.x + dx, center.y + dy));
                if (Double.isFinite(h)) samples.add(h);
            }
        }
        if (samples.isEmpty()) return Double.NaN;
        if (samples.size() < 3) return rawHeight.applyAsDouble(center);
        Collections.sort(samples);
        int idx = Math.min(samples.size() - 1, Math.max(0, (int) Math.floor((samples.size() - 1) * WINDOW_PERCENTILE)));
        return samples.get(idx);
    }

    public static double windowedHeight(Pixel center, ToDoubleFunction<Pixel> rawHeight) {
        return windowedHeight(center, rawHeight, WINDOW_RADIUS);
    }

    private static Rectangle boundsOf(Collection<Pixel> cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Pixel p : cells) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        if (minX == Integer.MAX_VALUE) return new Rectangle(0, 0, 0, 0);
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Prefer a short cycle walk for UI highlight; fall back to component bounds corners. */
    private static List<Pixel> cycleHighlight(List<Pixel> component, Set<Pixel> mask, Set<Edge> excluded, Runnable check) {
        Set<Pixel> set = new HashSet<>(component);
        for (Pixel start : component) {
            check.run();
            List<Pixel> path = findCycleFrom(start, set, mask, excluded, check);
            if (path != null && path.size() >= 3) return path;
        }
        return List.copyOf(component.subList(0, Math.min(component.size(), 32)));
    }

    private static List<Pixel> findCycleFrom(Pixel start, Set<Pixel> component, Set<Pixel> mask,
                                             Set<Edge> excluded, Runnable check) {
        ArrayDeque<Pixel> stack = new ArrayDeque<>();
        Map<Pixel, Pixel> parent = new HashMap<>();
        stack.add(start);
        parent.put(start, null);
        while (!stack.isEmpty()) {
            check.run();
            Pixel p = stack.removeLast();
            for (Pixel n : neighbours(p, mask, excluded)) {
                if (!component.contains(n)) continue;
                if (!parent.containsKey(n)) {
                    parent.put(n, p);
                    stack.add(n);
                } else if (!Objects.equals(parent.get(p), n)) {
                    List<Pixel> cycle = new ArrayList<>();
                    cycle.add(n);
                    for (Pixel c = p; c != null && !c.equals(n); c = parent.get(c)) cycle.add(c);
                    if (cycle.size() >= 3) return cycle;
                }
            }
        }
        return null;
    }

    /** Zhang-Suen thinning; simultaneous deletion within each subpass preserves branches. */
    static void thin(Set<Pixel> pixels, Runnable check) {
        boolean changed;
        do {
            changed = false;
            for (int pass = 0; pass < 2; pass++) {
                List<Pixel> remove = new ArrayList<>();
                for (Pixel p : pixels) {
                    check.run();
                    boolean[] n = new boolean[8];
                    int count = 0, transitions = 0;
                    for (int i = 0; i < 8; i++) {
                        n[i] = pixels.contains(offset(p, i));
                        if (n[i]) count++;
                    }
                    if (count < 2 || count > 6) continue;
                    for (int i = 0; i < 8; i++) if (!n[i] && n[(i + 1) % 8]) transitions++;
                    if (transitions != 1) continue;
                    if (pass == 0 ? !(n[0] && n[2] && n[4]) && !(n[2] && n[4] && n[6])
                            : !(n[0] && n[2] && n[6]) && !(n[0] && n[4] && n[6])) remove.add(p);
                }
                if (!remove.isEmpty()) {
                    pixels.removeAll(remove);
                    changed = true;
                }
            }
        } while (changed);
    }

    static List<Pixel> neighboursPublic(Pixel p, Set<Pixel> mask) {
        return neighbours(p, mask, Set.of());
    }

    static List<Pixel> neighboursPublic(Pixel p, Set<Pixel> mask, Set<Edge> excluded) {
        return neighbours(p, mask, excluded);
    }

    private static Pixel offset(Pixel p, int i) {
        return new Pixel(p.x + OFFSETS[i][0], p.y + OFFSETS[i][1]);
    }

    private static List<Pixel> neighbours(Pixel p, Set<Pixel> mask, Set<Edge> excluded) {
        List<Pixel> result = new ArrayList<>(4);
        for (int i = 0; i < 8; i++) {
            Pixel n = offset(p, i);
            if (!mask.contains(n)) continue;
            if ((i & 1) != 0 && (mask.contains(offset(p, (i + 7) % 8)) || mask.contains(offset(p, (i + 1) % 8))))
                continue;
            if (excluded != null && !excluded.isEmpty() && excluded.contains(Edge.of(p, n))) continue;
            result.add(n);
        }
        return result;
    }

    private DrawnRiverGraph() { }
}
