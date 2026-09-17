package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * MCP-friendly single-stroke river: corridor refine + outlet extend, with
 * preserve-first then optional terrain-adaptation fallback for dry maps.
 */
public final class BridgeRiverOps {
    public enum Mode { PRESERVE, ADAPT, AUTO }

    public record Result(boolean success, String mode, int paths, long changedCells,
                         String rejection, List<DrawnRiverGraph.Pixel> pathUsed) {}

    private BridgeRiverOps() {}

    public static Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) return Mode.AUTO;
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "PRESERVE", "PRESERVATION" -> Mode.PRESERVE;
            case "ADAPT", "ADAPTATION" -> Mode.ADAPT;
            default -> Mode.AUTO;
        };
    }

    /** Prefer higher start → lower end so AI point order is less brittle. */
    public static List<DrawnRiverGraph.Pixel> orderDownhill(Dimension dim, List<DrawnRiverGraph.Pixel> points) {
        if (points == null || points.size() < 2) return points;
        DrawnRiverGraph.Pixel a = points.get(0);
        DrawnRiverGraph.Pixel b = points.get(points.size() - 1);
        double ha = DrawnRiverGraph.windowedHeight(a, q -> {
            if (!dim.isTilePresent(q.x() >> 7, q.y() >> 7)) return Double.NaN;
            float h = dim.getHeightAt(q.x(), q.y());
            return Float.isFinite(h) ? h : Double.NaN;
        });
        double hb = DrawnRiverGraph.windowedHeight(b, q -> {
            if (!dim.isTilePresent(q.x() >> 7, q.y() >> 7)) return Double.NaN;
            float h = dim.getHeightAt(q.x(), q.y());
            return Float.isFinite(h) ? h : Double.NaN;
        });
        if (!Double.isFinite(ha) || !Double.isFinite(hb) || ha >= hb) return densify(points);
        List<DrawnRiverGraph.Pixel> rev = new ArrayList<>(points.size());
        for (int i = points.size() - 1; i >= 0; i--) rev.add(points.get(i));
        return densify(rev);
    }

    public static List<DrawnRiverGraph.Pixel> densify(List<DrawnRiverGraph.Pixel> points) {
        if (points == null || points.size() < 2) return points;
        List<DrawnRiverGraph.Pixel> out = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            DrawnRiverGraph.Pixel a = points.get(i);
            DrawnRiverGraph.Pixel b = points.get(i + 1);
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(a.x() - b.x(), a.y() - b.y())));
            for (int s = 0; s < steps; s++) {
                out.add(new DrawnRiverGraph.Pixel(
                        a.x() + (b.x() - a.x()) * s / steps,
                        a.y() + (b.y() - a.y()) * s / steps));
            }
        }
        out.add(points.get(points.size() - 1));
        return out;
    }

    public static Result carve(Dimension dim, List<DrawnRiverGraph.Pixel> inputPoints,
                               double width, double depth, boolean smooth, boolean granite, Mode mode) {
        Objects.requireNonNull(dim, "dim");
        if (inputPoints == null || inputPoints.size() < 2) {
            return new Result(false, null, 0, 0, "At least 2 points required", List.of());
        }
        if (width < 2) width = 2;
        if (width > 64) width = 64;
        if (depth < 0.65) depth = 0.65;
        if (depth > 5) depth = 5;

        List<DrawnRiverGraph.Pixel> points = orderDownhill(dim, inputPoints);
        Mode effective = mode == null ? Mode.AUTO : mode;
        List<Mode> attempts = switch (effective) {
            case PRESERVE -> List.of(Mode.PRESERVE);
            case ADAPT -> List.of(Mode.ADAPT);
            case AUTO -> List.of(Mode.PRESERVE, Mode.ADAPT);
        };

        String lastReject = null;
        List<DrawnRiverGraph.Pixel> lastPath = points;
        for (Mode attempt : attempts) {
            double[] depths = attempt == Mode.ADAPT
                    ? new double[]{depth, Math.min(5.0, depth + 0.75), Math.min(5.0, depth + 1.5)}
                    : new double[]{depth};
            for (double tryDepth : depths) {
                Result r = attemptCarve(dim, points, width, tryDepth, smooth, granite, attempt);
                if (r.success()) return r;
                lastReject = r.rejection();
                if (r.pathUsed() != null && !r.pathUsed().isEmpty()) lastPath = r.pathUsed();
            }
        }
        return new Result(false, effective.name().toLowerCase(Locale.ROOT), 0, 0, lastReject, lastPath);
    }

    private static Result attemptCarve(Dimension dim, List<DrawnRiverGraph.Pixel> points,
                                       double width, double depth, boolean smooth, boolean granite, Mode mode) {
        DrawnRiverRoutePlanner router = new DrawnRiverRoutePlanner(dim, () -> {});
        ShallowRiverCarver carver = new ShallowRiverCarver(dim, width, width * 1.5, depth, smooth, granite, dim.getSeed(), null);
        if (mode == Mode.PRESERVE) {
            carver.enableTerrainPreservation();
            carver.setLowerInteriorDirt(true);
        } else {
            carver.enableTerrainAdaptation();
        }
        carver.setPlanningCellLimit(20_000_000);

        List<List<DrawnRiverGraph.Pixel>> candidates = new ArrayList<>();
        candidates.add(points);

        DrawnRiverRoutePlanner.Route refined = router.refine(points, depth, false, false, null);
        if (refined != null && refined.pixels() != null && refined.pixels().size() >= 2) {
            candidates.add(refined.pixels());
        }

        DrawnRiverGraph.Pixel tip = points.get(points.size() - 1);
        for (int radius : new int[]{128, 256, 384}) {
            DrawnRiverGraph.Pixel outlet = router.findOutlet(tip, radius);
            if (outlet == null) continue;
            List<DrawnRiverGraph.Pixel> extended = new ArrayList<>(points);
            if (!extended.get(extended.size() - 1).equals(outlet)) extended.add(outlet);
            DrawnRiverRoutePlanner.Route toOut = router.refine(extended, depth, true, true, outlet);
            candidates.add(toOut != null ? toOut.pixels() : densify(extended));
        }

        String last = null;
        for (List<DrawnRiverGraph.Pixel> path : candidates) {
            if (path == null || path.size() < 2) continue;
            int[] xs = path.stream().mapToInt(DrawnRiverGraph.Pixel::x).toArray();
            int[] ys = path.stream().mapToInt(DrawnRiverGraph.Pixel::y).toArray();
            // Fresh carver per attempt — rejected paths leave internal state.
            carver = new ShallowRiverCarver(dim, width, width * 1.5, depth, smooth, granite, dim.getSeed(), null);
            if (mode == Mode.PRESERVE) {
                carver.enableTerrainPreservation();
                carver.setLowerInteriorDirt(true);
            } else {
                carver.enableTerrainAdaptation();
            }
            carver.setPlanningCellLimit(20_000_000);
            if (!carver.addPath(xs, ys, width, width)) {
                last = carver.getLastRejection();
                continue;
            }
            var carved = carver.apply();
            return new Result(true, mode.name().toLowerCase(Locale.ROOT), carved.paths(), carved.changedCells(),
                    null, path);
        }
        return new Result(false, mode.name().toLowerCase(Locale.ROOT), 0, 0, last, points);
    }

    public static Map<String, Object> toJson(Result r) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("success", r.success());
        if (r.mode() != null) json.put("mode", r.mode());
        if (r.success()) {
            json.put("paths", r.paths());
            json.put("changedCells", r.changedCells());
        } else {
            json.put("error", r.rejection() != null ? "Carver rejected: " + r.rejection() : "River carve failed");
        }
        return json;
    }
}
