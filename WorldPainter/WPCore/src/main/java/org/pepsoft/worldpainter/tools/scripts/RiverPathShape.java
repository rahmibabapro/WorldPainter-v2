package org.pepsoft.worldpainter.tools.scripts;

import java.util.ArrayList;
import java.util.List;

/**
 * A small, deterministic geometric proposal, never a terrain edit. The router
 * must validate the COMPLETE shaped channel before using it and retain its
 * original valid route when the valley has no room for this refinement.
 */
final class RiverPathShape {
    static int[][] refine(int[] xs, int[] ys, double maxOffset, long seed, ScriptProgress progress) {
        if (xs.length != ys.length || xs.length < 2 || !Double.isFinite(maxOffset) || maxOffset < 0) {
            throw new IllegalArgumentException("Invalid river shape");
        }
        final double[] arc = new double[xs.length];
        for (int i = 1; i < xs.length; i++) {
            check(progress);
            arc[i] = arc[i - 1] + Math.hypot((double) xs[i] - xs[i - 1], (double) ys[i] - ys[i - 1]);
        }
        final double length = arc[arc.length - 1];
        if (length < 16 || maxOffset == 0) return new int[][] { xs.clone(), ys.clone() };
        // Six-block stations suppress grid chatter without introducing a dense
        // one-block staircase of changing cross-section normals.
        final int count = (int) Math.min(20_000, Math.ceil(length / 6) + 1);
        final double[] originalX = new double[count], originalY = new double[count];
        int segment = 1;
        for (int i = 0; i < count; i++) {
            check(progress);
            final double s = length * i / (count - 1);
            while (segment < arc.length - 1 && arc[segment] < s) segment++;
            final double t = (s - arc[segment - 1]) / Math.max(0.001, arc[segment] - arc[segment - 1]);
            originalX[i] = xs[segment - 1] + ((double) xs[segment] - xs[segment - 1]) * t;
            originalY[i] = ys[segment - 1] + ((double) ys[segment] - ys[segment - 1]) * t;
        }
        double[] smoothX = originalX.clone(), smoothY = originalY.clone();
        for (int pass = 0; pass < 2; pass++) {
            final double[] nextX = smoothX.clone(), nextY = smoothY.clone();
            for (int i = 1; i < count - 1; i++) {
                check(progress);
                nextX[i] = (smoothX[i - 1] + 2 * smoothX[i] + smoothX[i + 1]) / 4;
                nextY[i] = (smoothY[i - 1] + 2 * smoothY[i] + smoothY[i + 1]) / 4;
            }
            smoothX = nextX; smoothY = nextY;
        }
        final double phase = ((seed ^ (seed >>> 32)) & 0xffffL) / 65536.0 * Math.PI * 2;
        final double wavelength = 64 + ((seed >>> 16) & 31);
        final List<int[]> result = new ArrayList<>();
        result.add(new int[] { xs[0], ys[0] });
        for (int i = 1; i < count - 1; i++) {
            check(progress);
            final double s = length * i / (count - 1);
            final double fade = smoothstep(Math.min(1, s / 18)) * smoothstep(Math.min(1, (length - s) / 18));
            final double ax = originalX[i + 1] - originalX[i - 1], ay = originalY[i + 1] - originalY[i - 1];
            final double tangent = Math.max(0.001, Math.hypot(ax, ay));
            final double wave = maxOffset * 0.55 * fade * (0.65 * Math.sin(s * 2 * Math.PI / wavelength + phase)
                    + 0.35 * Math.sin(s * 2 * Math.PI / (wavelength * 1.73) - phase));
            double dx = smoothX[i] - originalX[i] - ay / tangent * wave;
            double dy = smoothY[i] - originalY[i] + ax / tangent * wave;
            final double displacement = Math.hypot(dx, dy), limit = maxOffset * fade;
            if (displacement > limit) { dx *= limit / displacement; dy *= limit / displacement; }
            final int x = (int) Math.round(originalX[i] + dx), y = (int) Math.round(originalY[i] + dy);
            final int[] previous = result.get(result.size() - 1);
            if (previous[0] != x || previous[1] != y) result.add(new int[] { x, y });
        }
        final int last = xs.length - 1;
        final int[] previous = result.get(result.size() - 1);
        if (previous[0] != xs[last] || previous[1] != ys[last]) result.add(new int[] { xs[last], ys[last] });
        final int[][] shaped = new int[2][result.size()];
        for (int i = 0; i < result.size(); i++) { shaped[0][i] = result.get(i)[0]; shaped[1][i] = result.get(i)[1]; }
        return shaped;
    }

    private static double smoothstep(double t) { return t * t * (3 - 2 * t); }
    private static void check(ScriptProgress progress) { if (progress != null) progress.checkForCancel(); }
    private RiverPathShape() { }
}
