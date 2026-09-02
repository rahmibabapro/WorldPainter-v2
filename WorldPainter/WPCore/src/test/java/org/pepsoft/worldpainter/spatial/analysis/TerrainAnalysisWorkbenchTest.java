package org.pepsoft.worldpainter.spatial.analysis;

import org.junit.Test;

import static org.junit.Assert.*;

public class TerrainAnalysisWorkbenchTest {

    @Test
    public void testSlopeOnFlatAndIncline() {
        int w = 16, h = 16;
        float[][] flat = new float[w][h];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                flat[x][z] = 64.0f;
            }
        }
        float[][] flatSlope = TerrainAnalysisWorkbench.computeSlope(flat, w, h);
        assertEquals(0.0f, flatSlope[8][8], 0.01f);

        // 45 degree slope: rise of 1 block per 1 block
        float[][] incline = new float[w][h];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                incline[x][z] = x * 1.0f;
            }
        }
        float[][] inclineSlope = TerrainAnalysisWorkbench.computeSlope(incline, w, h);
        assertEquals(45.0f, inclineSlope[8][8], 0.5f);
    }

    @Test
    public void testCurvatureIdentifiesRidgeVsValley() {
        int w = 16, h = 16;
        float[][] ridge = new float[w][h];
        float[][] valley = new float[w][h];

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                // Ridge: peak at center x=8
                ridge[x][z] = 100.0f - Math.abs(x - 8) * 5.0f;
                // Valley: trough at center x=8
                valley[x][z] = 50.0f + Math.abs(x - 8) * 5.0f;
            }
        }

        float[][] ridgeCurv = TerrainAnalysisWorkbench.computeCurvature(ridge, w, h);
        float[][] valleyCurv = TerrainAnalysisWorkbench.computeCurvature(valley, w, h);

        // Ridge has positive curvature (crest), Valley has negative curvature (trough)
        assertTrue("Ridge peak should have positive curvature: " + ridgeCurv[8][8], ridgeCurv[8][8] > 0);
        assertTrue("Valley trough should have negative curvature: " + valleyCurv[8][8], valleyCurv[8][8] < 0);
    }
}
