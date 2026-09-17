package org.pepsoft.worldpainter.tools.scripts;

import java.util.function.BiFunction;

/** Ranking only: the carver remains responsible for the complete wet footprint. */
final class RiverCrossSection {
    private RiverCrossSection() { }

    static double penalty(int x, int y, double dx, double dy, double radius, double depth,
                          BiFunction<Integer, Integer, RiverTerrainSurvey.Sample> samples) {
        double length = Math.hypot(dx, dy);
        if (length == 0) return 1000;
        var centre = samples.apply(x, y);
        if (centre.blocked()) return 1000;
        double nx = -dy / length, ny = dx / length;
        double halfWidth = Math.max(1.5, radius);
        int steps = (int) Math.ceil(halfWidth);
        double penalty = 0;
        // Sample the interior as well as both edges. A clear edge must not
        // hide a protected cell or a ridge between the bank and centreline.
        for (int sign : new int[]{-1, 1}) for (int i = 1; i <= steps; i++) {
            double offset = sign * Math.min(i, halfWidth);
            var side = samples.apply((int) Math.round(x + nx * offset), (int) Math.round(y + ny * offset));
            if (side.blocked()) return 1000;
            penalty += Math.max(0, Math.abs(side.height() - centre.height()) - (depth + .10));
        }
        return penalty;
    }
}
