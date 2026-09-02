package org.pepsoft.worldpainter.terrain.compute;

/**
 * Pluggable terrain compute op (erosion, filters). GPU implementations must provide CPU fallback.
 */
public interface TerrainComputeOp {
    String name();

    /**
     * Apply in-place to a height grid ({@code width * height} floats, row-major).
     */
    void apply(float[] heights, int width, int height, ComputeParams params);

    final class ComputeParams {
        public final int iterations;
        public final float strength;

        public ComputeParams(int iterations, float strength) {
            this.iterations = Math.max(1, iterations);
            this.strength = strength;
        }
    }
}
