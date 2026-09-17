package org.pepsoft.worldpainter.tools.scripts;

import java.util.List;

/** Immutable, detached description. Applying remains the owning session's responsibility. */
public record RiverSearchResult(Status status, int requested, List<Course> courses,
                                List<ShallowRiverCarver.PreviewCell> cells, Diagnostics diagnostics) {
    public RiverSearchResult {
        courses = List.copyOf(courses);
        cells = List.copyOf(cells);
    }
    public enum Status { FOUND, PARTIAL, BUDGET_EXHAUSTED, NO_FEASIBLE_OUTLET, CANCELLED, STALE_WORLD }
    public record Point(int x, int y) { }
    public record Course(List<Point> centreline, double startWidth, double endWidth, double excavation,
                         List<Point> lakeCells, int lakeWater) {
        public Course(List<Point> centreline, double startWidth, double endWidth, double excavation) {
            this(centreline, startWidth, endWidth, excavation, List.of(), Integer.MIN_VALUE);
        }
        public Course {
            centreline = List.copyOf(centreline);
            lakeCells = lakeCells == null ? List.of() : List.copyOf(lakeCells);
        }
    }
    public record Diagnostics(int overviewStep, long sampledCells, int candidates, int rejected,
                              long elapsedMillis, long estimatedSearchBytes, String reason,
                              int seaLevel, int lakeCells, boolean forced) {
        public Diagnostics(int overviewStep, long sampledCells, int candidates, int rejected,
                           long elapsedMillis, long estimatedSearchBytes, String reason) {
            this(overviewStep, sampledCells, candidates, rejected, elapsedMillis, estimatedSearchBytes, reason, 62, 0, false);
        }
    }
    public boolean canApply() {
        if (courses.isEmpty()) return false;
        if (!(status == Status.FOUND || status == Status.PARTIAL || status == Status.BUDGET_EXHAUSTED)) {
            return false;
        }
        // Tiny forced stubs (e.g. 8 blocks) must not unlock Apply.
        // Lake unlocks Apply only for sealed-basin results (no short river claim).
        for (Course c : courses) {
            double len = centrelineLength(c);
            if (c.lakeCells() != null && c.lakeCells().size() >= 32 && len < 64) return true;
            if (len >= 64) return true;
        }
        return false;
    }
    private static double centrelineLength(Course c) {
        List<Point> p = c.centreline();
        double n = 0;
        for (int i = 1; i < p.size(); i++) {
            n += Math.hypot(p.get(i).x() - (double) p.get(i - 1).x(), p.get(i).y() - (double) p.get(i - 1).y());
        }
        return n;
    }
}
