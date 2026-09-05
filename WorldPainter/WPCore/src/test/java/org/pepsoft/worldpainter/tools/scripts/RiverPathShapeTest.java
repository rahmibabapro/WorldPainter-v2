package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class RiverPathShapeTest {
    @Test public void straightRoutesGetGentleBroadVariationWithoutMovingEndpoints() {
        int[][] p = RiverPathShape.refine(new int[]{-120, 280}, new int[]{64,64}, 3, 733, null);
        assertEquals(-120, p[0][0]); assertEquals(64, p[1][0]);
        assertEquals(280, p[0][p[0].length-1]); assertEquals(64, p[1][p[1].length-1]);
        assertTrue(Arrays.stream(p[1]).distinct().count() > 1);
        for (int y : p[1]) assertTrue(Math.abs(y - 64) <= 3);
        assertTrue("Small broad bends, not angular zigzags", maxTurn(p) < Math.toRadians(40));
    }

    @Test public void rightAngleIsSoftenedInsideABoundedCorridor() {
        int[][] p = RiverPathShape.refine(new int[]{0,80,80}, new int[]{0,0,80}, 3, 21, null);
        assertTrue(maxTurn(p) < Math.toRadians(85));
        for (int i=0; i<p[0].length; i++) {
            double first = segmentDistance(p[0][i],p[1][i],0,0,80,0);
            double second = segmentDistance(p[0][i],p[1][i],80,0,80,80);
            assertTrue(Math.min(first,second) <= 3.71);
        }
    }

    @Test public void sameSeedIsRepeatableAndOtherSeedsOnlyChangeTheBoundedProposal() {
        int[] xs={0,400},ys={0,0};
        int[][] first=RiverPathShape.refine(xs,ys,3,733,null);
        int[][] again=RiverPathShape.refine(xs,ys,3,733,null);
        int[][] other=RiverPathShape.refine(xs,ys,3,59000,null);
        assertTrue(Arrays.deepEquals(first,again)); assertFalse(Arrays.deepEquals(first,other));
        assertArrayEquals(new int[]{0,400},xs); assertArrayEquals(new int[]{0,0},ys);
    }

    @Test public void tinyAndDisabledPathsStayExactButAreNotAliased() {
        int[] xs={1,5}, ys={-3,-7};
        int[][] p=RiverPathShape.refine(xs,ys,3,5,null);
        assertArrayEquals(xs,p[0]); assertArrayEquals(ys,p[1]); assertNotSame(xs,p[0]);
        int[][] disabled=RiverPathShape.refine(new int[]{0,80},new int[]{0,0},0,5,null);
        assertEquals(2,disabled[0].length);
    }

    private static double maxTurn(int[][] p) {
        double max=0;
        for(int i=1;i<p[0].length-1;i++) {
            double ax=p[0][i]-p[0][i-1], ay=p[1][i]-p[1][i-1];
            double bx=p[0][i+1]-p[0][i], by=p[1][i+1]-p[1][i];
            max=Math.max(max,Math.acos(Math.max(-1,Math.min(1,(ax*bx+ay*by)/(Math.hypot(ax,ay)*Math.hypot(bx,by))))));
        }
        return max;
    }
    private static double segmentDistance(double x,double y,double ax,double ay,double bx,double by) {
        double dx=bx-ax,dy=by-ay;
        double t=Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)));
        return Math.hypot(x-ax-t*dx,y-ay-t*dy);
    }
}
