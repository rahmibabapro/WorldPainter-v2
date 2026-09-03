package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MossSuitabilityTest {
    @Test
    public void mossHasAn80To120BandAndAHardThirtyDegreeCliffCutoff() {
        assertFalse(MossSuitability.acceptsSourceMoss(79.9f, 0, 0, 0, 0, 0));
        assertTrue(MossSuitability.acceptsSourceMoss(100.0f, 0, 0, 0, 0, 0));
        assertFalse(MossSuitability.acceptsSourceMoss(120.1f, 0, 0, 0, 0, 0));
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
    public void rollingPlainsMossKeepsTheSourcePatternBelowTheMountainBandButNotOnCliffs() {
        assertTrue(MossSuitability.acceptsRollingPlainsMoss(0, 0, 0, 0, 0));
        final float thirtyDegrees = (float) Math.tan(Math.toRadians(30.0));
        assertFalse(MossSuitability.acceptsRollingPlainsMoss(thirtyDegrees, 0, 0, 0, 0));
    }
}
