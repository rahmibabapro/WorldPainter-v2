package org.pepsoft.worldpainter.simd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HeightBlendKernelsTest {
    @Test
    public void blendMatchesScalarFormula() {
        float[] current = {10, 20, 30, 40, 50};
        float[] target = {0, 0, 0, 0, 0};
        float[] strength = {0, 0.25f, 0.5f, 0.75f, 1f};
        float[] out = new float[5];
        HeightBlendKernels.blend(current, target, strength, out, 5);
        for (int i = 0; i < 5; i++) {
            float expected = strength[i] * target[i] + (1 - strength[i]) * current[i];
            assertEquals(expected, out[i], 1e-5f);
        }
        assertEquals(15f, HeightBlendKernels.blendOne(20f, 0f, 0.25f), 1e-5f);
    }
}
