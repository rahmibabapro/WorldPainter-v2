package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.TestData;
import java.awt.Rectangle;
import java.util.*;
import static org.junit.Assert.*;

public class RiverDirtBedPlanTest {
    @Test public void broadPatchAcrossTileBoundaryIsLoweredAsOneRegion() {
        for(int width:new int[]{1,3,8}) {
            var d=TestData.createDimension(new Rectangle(0,0,256,256),100);
            var cells=fixture(width,false);
            long revision=d.getChangeNo();
            var result=RiverDirtBedPlan.create(d,cells,()->{});
            assertEquals(width*6,result.lowered().size());
            assertEquals(result.lowered(),RiverDirtBedPlan.create(d,cells,()->{}).lowered());
            assertEquals(revision,d.getChangeNo());
        }
    }
    @Test public void ineligibleFringeDoesNotPoisonInteriorPatch() {
        var d=TestData.createDimension(new Rectangle(0,0,256,256),100);
        var cells=fixture(3,true);
        var result=RiverDirtBedPlan.create(d,cells,()->{});
        // The single ineligible dirt cell is excluded; the rest of the bed still lowers.
        assertFalse(result.lowered().isEmpty());
        assertFalse(result.lowered().contains(RiverDirtBedPlan.key(126,123)));
        assertEquals(3*6-1,result.lowered().size());
    }
    @Test public void interiorDirtNextToFlatDetailStillLowers() {
        // Flat mid-channel beds have granite detail borders that export as full
        // blocks, not stairs. Those must still unlock the -1 correction.
        Map<Long,RiverDirtBedPlan.Cell> cells=new LinkedHashMap<>();
        for(int y=10;y<=14;y++)for(int x=10;x<=14;x++) {
            boolean edge=x==10||x==14||y==10||y==14;
            cells.put(RiverDirtBedPlan.key(x,y),new RiverDirtBedPlan.Cell(x,y,99f,100,
                    !edge,edge,!edge,0));
        }
        var result=RiverDirtBedPlan.create(TestData.createDimension(new Rectangle(0,0,128,128),100),cells,()->{});
        assertEquals(9,result.lowered().size());
        assertEquals(0,result.skippedRegions());
    }
    @Test public void differentRouteIdsSameWaterStillLowerAsOneBed() {
        Map<Long,RiverDirtBedPlan.Cell> cells=new LinkedHashMap<>();
        for(int y=20;y<=26;y++)for(int x=20;x<=28;x++) {
            boolean edge=x==20||x==28||y==20||y==26;
            int route=x<=24?0:1;
            cells.put(RiverDirtBedPlan.key(x,y),new RiverDirtBedPlan.Cell(x,y,99f,100,
                    !edge,edge,!edge,route,
                    edge?RiverDirtBedPlan.Role.INTERIOR_GRANITE:RiverDirtBedPlan.Role.INTERIOR_DIRT));
        }
        var result=RiverDirtBedPlan.create(TestData.createDimension(new Rectangle(0,0,128,128),100),cells,()->{});
        assertEquals(7*5,result.lowered().size());
        assertEquals(RiverDirtBedPlan.SkipReason.NONE,result.primarySkipReason());
    }
    @Test public void differentWaterIsSoftBoundaryNotWholeRegionPoison() {
        Map<Long,RiverDirtBedPlan.Cell> cells=new LinkedHashMap<>();
        for(int y=30;y<=36;y++)for(int x=30;x<=40;x++) {
            boolean edge=x==30||x==40||y==30||y==36;
            int water=x<=35?100:101;
            cells.put(RiverDirtBedPlan.key(x,y),new RiverDirtBedPlan.Cell(x,y,99f,water,
                    !edge,edge,!edge,0,
                    edge?RiverDirtBedPlan.Role.WET_SHORE:RiverDirtBedPlan.Role.INTERIOR_DIRT));
        }
        var result=RiverDirtBedPlan.create(TestData.createDimension(new Rectangle(0,0,128,128),100),cells,()->{});
        // Left water=100 interior still lowers; right water band is a separate region.
        assertTrue(result.lowered().size()>=5*5);
        assertTrue(result.lowered().contains(RiverDirtBedPlan.key(32,33)));
        assertTrue(result.lowered().contains(RiverDirtBedPlan.key(37,33)));
    }
    @Test public void protectedLinkNeighbourDoesNotPoisonInterior() {
        Map<Long,RiverDirtBedPlan.Cell> cells=new LinkedHashMap<>();
        for(int y=40;y<=46;y++)for(int x=40;x<=48;x++) {
            boolean edge=x==40||x==48||y==40||y==46;
            boolean link=x==44&&y==43;
            RiverDirtBedPlan.Role role=link?RiverDirtBedPlan.Role.PROTECTED_LINK
                    :(edge?RiverDirtBedPlan.Role.INTERIOR_GRANITE:RiverDirtBedPlan.Role.INTERIOR_DIRT);
            cells.put(RiverDirtBedPlan.key(x,y),new RiverDirtBedPlan.Cell(x,y,99f,100,
                    role==RiverDirtBedPlan.Role.INTERIOR_DIRT,
                    role==RiverDirtBedPlan.Role.INTERIOR_GRANITE,
                    role==RiverDirtBedPlan.Role.INTERIOR_DIRT,0,role));
        }
        var result=RiverDirtBedPlan.create(TestData.createDimension(new Rectangle(0,0,128,128),100),cells,()->{});
        assertFalse(result.lowered().contains(RiverDirtBedPlan.key(44,43)));
        assertTrue(result.lowered().contains(RiverDirtBedPlan.key(42,43)));
        assertTrue(result.lowered().size()>=20);
    }
    @Test public void mudBrickShoreHaloDoesNotBlockLongDirtStrip() {
        Map<Long,RiverDirtBedPlan.Cell> cells=new LinkedHashMap<>();
        for(int y=50;y<=54;y++)for(int x=50;x<=70;x++) {
            boolean shore=y==50||y==54;
            boolean dirt=y>=51&&y<=53;
            cells.put(RiverDirtBedPlan.key(x,y),new RiverDirtBedPlan.Cell(x,y,99f,100,
                    dirt,shore,dirt,0,
                    shore?RiverDirtBedPlan.Role.WET_SHORE:RiverDirtBedPlan.Role.INTERIOR_DIRT));
        }
        var result=RiverDirtBedPlan.create(TestData.createDimension(new Rectangle(0,0,128,128),100),cells,()->{});
        assertEquals(21*3,result.lowered().size());
        assertEquals(RiverDirtBedPlan.SkipReason.NONE,result.primarySkipReason());
    }
    @Test public void cancellationIsPropagatedWithoutWorldWrites() {
        var d=TestData.createDimension(new Rectangle(0,0,256,256),100);
        long revision=d.getChangeNo();
        assertThrows(java.util.concurrent.CancellationException.class,()->
                RiverDirtBedPlan.create(d,fixture(8,false),()->{throw new java.util.concurrent.CancellationException();}));
        assertEquals(revision,d.getChangeNo());
    }
    private static Map<Long,RiverDirtBedPlan.Cell> fixture(int width,boolean blocked) {
        Map<Long,RiverDirtBedPlan.Cell> cells=new LinkedHashMap<>();
        for(int y=122;y<=129;y++)for(int x=125;x<=126+width;x++) {
            boolean dirt=x>125&&x<126+width&&y>122&&y<129;
            cells.put(RiverDirtBedPlan.key(x,y),new RiverDirtBedPlan.Cell(x,y,99.1f,100,
                    dirt,!dirt,!(blocked&&x==126&&y==123),0));
        }
        return cells;
    }
}
