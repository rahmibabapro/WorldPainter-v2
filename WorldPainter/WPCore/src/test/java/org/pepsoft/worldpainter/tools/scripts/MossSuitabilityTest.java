package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MossSuitabilityTest {
    @Test
    public void mossUsesSlopeOnlyWithAHardThirtyDegreeCliffCutoff() {
        // Absolute Y no longer gates moss — low-ceiling maps still get suitable benches.
        assertTrue(MossSuitability.acceptsSourceMoss(40.0f, 0, 0, 0, 0, 0));
        assertTrue(MossSuitability.acceptsSourceMoss(100.0f, 0, 0, 0, 0, 0));
        assertTrue(MossSuitability.acceptsSourceMoss(200.0f, 0, 0, 0, 0, 0));
        final float twentyTwoDegrees = (float) Math.tan(Math.toRadians(22.0));
        final float thirtyDegrees = (float) Math.tan(Math.toRadians(30.0));
        assertTrue(MossSuitability.acceptsSourceMoss(100.0f, twentyTwoDegrees, 0, 0, 0, 0));
        assertFalse(MossSuitability.acceptsSourceMoss(100.0f, thirtyDegrees, 0, 0, 0, 0));
    }

    @Test
    public void concaveNorthFacingBenchesAreMoreSuitableThanExposedSouthRidges() {
        final float bench = MossSuitability.coverage(100.0f, 0.25f, 0.25f, -1.0f, 1.0f, 2.0f);
        final float ridge = MossSuitability.coverage(100.0f, 0.25f, 0.25f, 1.0f, -1.0f, -2.0f);
        assertTrue(bench > ridge);
    }

    @Test
    public void rollingPlainsMossKeepsTheSourcePatternButNotOnCliffs() {
        assertTrue(MossSuitability.acceptsRollingPlainsMoss(0, 0, 0, 0, 0));
        final float thirtyDegrees = (float) Math.tan(Math.toRadians(30.0));
        assertFalse(MossSuitability.acceptsRollingPlainsMoss(thirtyDegrees, 0, 0, 0, 0));
    }
}
