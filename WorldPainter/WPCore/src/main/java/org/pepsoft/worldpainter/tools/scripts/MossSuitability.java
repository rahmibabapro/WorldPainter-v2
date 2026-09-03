package org.pepsoft.worldpainter.tools.scripts;

/**
 * Small, deterministic terrain rule shared by the Axiom surface passes. Mountain moss is a damp,
 * sheltered middle-mountain texture; rolling-plains moss retains its measured pattern but never
 * becomes a colour that can appear on bare cliffs. Heights are WorldPainter heights, and negative
 * Y is north (as in {@link SmoothSnow}).
 */
public final class MossSuitability {
    public static final float MIN_HEIGHT = 80.0f;
    public static final float MAX_HEIGHT = 120.0f;
    public static final float MAX_SLOPE_DEGREES = 30.0f;

    private MossSuitability() {
    }

    /**
     * Returns a continuous suitability from zero to one. {@code dx}/{@code dy} are central
     * height gradients, {@code north}/{@code south} are one-cell height differences, and a
     * positive concavity means that the cell sits below its four-block neighbourhood.
     */
    public static float coverage(float height, float dx, float dy, float north, float south, float concavity) {
        if (! Float.isFinite(height) || height <= MIN_HEIGHT || height >= MAX_HEIGHT) {
            return 0.0f;
        }
        // A four-block edge band prevents a visible horizontal vegetation line at 80 or 120.
        final float elevation = Math.min(smoothStep((height - MIN_HEIGHT) / 4.0f),
                smoothStep((MAX_HEIGHT - height) / 4.0f));
        return clamp(elevation * surfaceCoverage(dx, dy, north, south, concavity));
    }

    /**
     * The rolling-plains blueprint carries its own moss distribution, so it keeps the same
     * slope/aspect/moisture protection without importing the mountain-only 80–120 height band.
     */
    public static boolean acceptsRollingPlainsMoss(float dx, float dy, float north, float south, float concavity) {
        return surfaceCoverage(dx, dy, north, south, concavity) >= 0.50f;
    }

    private static float surfaceCoverage(float dx, float dy, float north, float south, float concavity) {
        final float slopeDegrees = (float) Math.toDegrees(Math.atan(Math.hypot(dx, dy)));
        if (slopeDegrees >= MAX_SLOPE_DEGREES) {
            return 0.0f;
        }
        // Moderate slopes retain soil and moisture well. The last eight degrees fade to bare rock.
        final float slope = (slopeDegrees <= 8.0f) ? 0.85f
                : (slopeDegrees <= 20.0f) ? 0.85f + 0.15f * smoothStep((slopeDegrees - 8.0f) / 12.0f)
                : (slopeDegrees <= 24.0f) ? 1.0f - 0.20f * smoothStep((slopeDegrees - 20.0f) / 4.0f)
                : 0.80f * (1.0f - smoothStep((slopeDegrees - 24.0f) / 6.0f));
        // Concave benches retain moisture; exposed convex ridges shed it. The limit keeps this
        // a subtle preference, not a second hard terrain boundary.
        final float relief = 1.0f + 0.30f * clamp(concavity / 3.0f, -1.0f, 1.0f);
        // Aspect only matters once a surface is actually sloped. North/NE faces stay a little
        // wetter; south-facing faces receive a slightly stronger drying penalty.
        final float aspect;
        if (slopeDegrees < 5.0f) {
            aspect = 1.0f;
        } else {
            final float northness = clamp((south - north) / 8.0f, -1.0f, 1.0f);
            aspect = northness >= 0.0f ? 1.0f + 0.12f * northness : 1.0f + 0.18f * northness;
        }
        return clamp(slope * relief * aspect);
    }

    /** A source moss material is retained only where its target cell is genuinely suitable. */
    public static boolean acceptsSourceMoss(float height, float dx, float dy, float north, float south, float concavity) {
        // The source blueprint already supplies coherent moss patches. This threshold only
        // removes unsuitable cells; it does not add independent speckle to those patches.
        return coverage(height, dx, dy, north, south, concavity) >= 0.50f;
    }

    private static float smoothStep(float value) {
        value = clamp(value, 0.0f, 1.0f);
        return value * value * (3.0f - 2.0f * value);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value) {
        return clamp(value, 0.0f, 1.0f);
    }
}
