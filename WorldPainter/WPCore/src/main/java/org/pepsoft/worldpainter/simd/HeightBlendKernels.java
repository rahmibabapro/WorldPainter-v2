package org.pepsoft.worldpainter.simd;

/**
 * Height lerp kernels for brush tools. Uses unrolled scalar loops (Java 17 portable);
 * enable {@code -Dorg.pepsoft.worldpainter.simd=true} (default true on v2).
 */
public final class HeightBlendKernels {
    private HeightBlendKernels() {
    }

    public static boolean enabled() {
        String prop = System.getProperty("org.pepsoft.worldpainter.simd");
        if (prop != null) {
            return Boolean.parseBoolean(prop);
        }
        return true;
    }

    /**
     * {@code out[i] = strength[i] * target[i] + (1 - strength[i]) * current[i]} for {@code length} elements.
     */
    public static void blend(float[] current, float[] target, float[] strength, float[] out, int length) {
        if (! enabled()) {
            for (int i = 0; i < length; i++) {
                float s = strength[i];
                out[i] = s * target[i] + (1.0f - s) * current[i];
            }
            return;
        }
        int i = 0;
        for (; i + 3 < length; i += 4) {
            float s0 = strength[i];
            float s1 = strength[i + 1];
            float s2 = strength[i + 2];
            float s3 = strength[i + 3];
            out[i] = s0 * target[i] + (1.0f - s0) * current[i];
            out[i + 1] = s1 * target[i + 1] + (1.0f - s1) * current[i + 1];
            out[i + 2] = s2 * target[i + 2] + (1.0f - s2) * current[i + 2];
            out[i + 3] = s3 * target[i + 3] + (1.0f - s3) * current[i + 3];
        }
        for (; i < length; i++) {
            float s = strength[i];
            out[i] = s * target[i] + (1.0f - s) * current[i];
        }
    }

    /** In-place single sample (hot path convenience). */
    public static float blendOne(float current, float target, float strength) {
        return strength * target + (1.0f - strength) * current;
    }
}
