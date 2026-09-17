package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import static org.junit.Assert.*;

public class RiverCrossSectionTest {
    @Test public void longitudinalMountainGradeIsNotBankObstruction() {
        assertEquals(0, RiverCrossSection.penalty(0,0,1,0,3,.85,
                (x,y)->sample(100+x*.75,false)),0);
    }
    @Test public void crossSlopeRemainsPenalised() {
        assertTrue(RiverCrossSection.penalty(0,0,0,1,3,.85,
                (x,y)->sample(100+x*.75,false))>0);
    }
    @Test public void diagonalGradeAndReversedDirectionAgree() {
        double forward=RiverCrossSection.penalty(0,0,1,1,3,.85,
                (x,y)->sample(100+(x+y)*.5,false));
        double reverse=RiverCrossSection.penalty(0,0,-1,-1,3,.85,
                (x,y)->sample(100+(x+y)*.5,false));
        assertEquals(0,forward,0);assertEquals(forward,reverse,0);
    }
    @Test public void obstructionInsideSectionCannotBeSkipped() {
        assertEquals(1000,RiverCrossSection.penalty(0,0,1,0,3,.85,
                (x,y)->sample(100,y==1)),0);
    }
    @Test public void ridgeInsideSectionCannotBeSkipped() {
        assertTrue(RiverCrossSection.penalty(0,0,1,0,3,.85,
                (x,y)->sample(y==1?105:100,false))>0);
    }
    private static RiverTerrainSurvey.Sample sample(double height,boolean blocked) {
        return new RiverTerrainSurvey.Sample((float)height,0,blocked);
    }
}
