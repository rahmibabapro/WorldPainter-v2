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
    public record Course(List<Point> centreline, double startWidth, double endWidth, double excavation) {
        public Course { centreline = List.copyOf(centreline); }
    }
    public record Diagnostics(int overviewStep, long sampledCells, int candidates, int rejected,
                              long elapsedMillis, long estimatedSearchBytes, String reason) { }
    public boolean canApply() {
        return !courses.isEmpty() && (status == Status.FOUND || status == Status.PARTIAL
                || status == Status.BUDGET_EXHAUSTED);
    }
}
