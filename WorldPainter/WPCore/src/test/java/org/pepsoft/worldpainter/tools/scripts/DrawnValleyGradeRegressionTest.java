package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import java.awt.Rectangle;
import java.awt.Color;
import org.pepsoft.worldpainter.layers.CustomAnnotationLayer;
import org.pepsoft.util.undo.UndoManager;
import static org.junit.Assert.*;

public class DrawnValleyGradeRegressionTest {
    @Test public void shortReverseStrokeInsideTrunkIsNotASecondSource() {
        Dimension d=ridge();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
            d.setHeightAt(x,y,y>=100?88:100);d.setWaterLevelAt(x,y,90);
        }
        var drawing=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
        for(int y=30;y<=110;y++)d.setBitLayerValueAt(drawing,64,y,true);
        for(int i=0;i<=8;i++)d.setBitLayerValueAt(drawing,64+i/4,52+i,true);
        long revision=d.getChangeNo();
        var p=new DrawnRiverSession(d,drawing,5,12,1.1,true,()->false).searchDeepValley();
        assertTrue(p.messages().toString(),p.canApply());
        assertEquals(p.messages().toString(),1,p.sources());
        assertEquals(1,p.courses().size());
        assertEquals(revision,d.getChangeNo());
    }
    @Test public void unfinishedOrLeakingNetworkCannotApply() {
        Dimension d=ridge();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)d.setHeightAt(x,y,100);
        d.setHeightAt(19,64,98);
        long revision=d.getChangeNo();
        var c=new ShallowRiverCarver(d,5,5,1.1,true,true,42,null);
        c.enableTerrainPreservation();c.beginNetworkPlan();
        int[] water=new int[161];java.util.Arrays.fill(water,100);
        assertTrue(c.getLastRejection(),c.addProfiledPath(new int[]{20,100},new int[]{64,64},5,5,water,false));
        try {c.apply();fail("Unvalidated network applied");}catch(IllegalStateException expected) { }
        assertFalse(c.finishNetworkPlan());
        try {c.apply();fail("Leaking network applied");}catch(IllegalStateException expected) { }
        assertEquals(revision,d.getChangeNo());
    }
    @Test public void deepPreviewClosesShortGapWithoutWritingDrawingLayer() {
        Dimension d=ridge();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
            d.setHeightAt(x,y,y>=100?88:100);d.setWaterLevelAt(x,y,90);
        }
        var drawing=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
        for(int y=30;y<=110;y++)d.setBitLayerValueAt(drawing,64,y,true);
        for(int i=0;i<=28;i++)d.setBitLayerValueAt(drawing,95-i,45+i,true);
        long revision=d.getChangeNo();
        var s=new DrawnRiverSession(d,drawing,5,12,1.1,true,()->false);
        var p=s.searchDeepValley();
        assertEquals(p.messages().toString(),3,p.courses().size());
        assertFalse(p.repairs().isEmpty());
        assertEquals(revision,d.getChangeNo());
        for(var cell:p.repairs())assertFalse(d.getBitLayerValueAt(drawing,cell.x(),cell.y()));
    }
    @Test public void deepValleySolvesJunctionAsSharedLevelAndWidensTowardsSea() {
        Dimension d=ridge();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
            d.setHeightAt(x,y,y>=100?88:(y>=55&&y<=70?112:100));
            d.setWaterLevelAt(x,y,90);
        }
        var drawing=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
        for(int i=0;i<=30;i++) {
            d.setBitLayerValueAt(drawing,64-i,64-i,true);
            d.setBitLayerValueAt(drawing,64+i,64-i,true);
        }
        for(int y=64;y<=110;y++)d.setBitLayerValueAt(drawing,64,y,true);
        long revision=d.getChangeNo();
        var s=new DrawnRiverSession(d,drawing,5,12,1.1,true,()->false);
        var p=s.searchDeepValley();
        assertEquals(p.messages().toString(),3,p.courses().size());
        assertEquals(revision,d.getChangeNo());
        assertTrue(s.terrainAdjustmentPlan().summary().maxCut()>6);
        assertTrue(s.terrainAdjustmentPlan().summary().maxCut()<=16);
        assertEquals(5*Math.sqrt(2),p.courses().getFirst().startWidth(),1e-9);
        d.registerUndoManager(new UndoManager(8));
        try {
            s.apply();
            assertEquals(90,d.getWaterLevelAt(64,110));
            for(var c:p.courses()) {
                int previous=Integer.MAX_VALUE;
                for(var point:c.centreline()) {
                    int water=d.getWaterLevelAt(point.x(),point.y());
                    assertTrue(water<=previous);previous=water;
                    assertTrue(water>d.getIntHeightAt(point.x(),point.y()));
                }
            }
            assertTrue(d.undoChanges());
            assertEquals(112,d.getHeightAt(64,64),0);
        } finally {d.unregisterUndoManager();}
    }
    @Test public void prepareEscalatesToJointDeepWhenPreserveCannotFinish() {
        Dimension d=ridge();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
            d.setHeightAt(x,y,y>=100?88:(y>=55&&y<=70?112:100));
            d.setWaterLevelAt(x,y,90);
        }
        var drawing=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
        for(int i=0;i<=30;i++) {
            d.setBitLayerValueAt(drawing,64-i,64-i,true);
            d.setBitLayerValueAt(drawing,64+i,64-i,true);
        }
        for(int y=64;y<=110;y++)d.setBitLayerValueAt(drawing,64,y,true);
        long revision=d.getChangeNo();
        var preserved=new DrawnRiverSession(d,drawing,5,12,1.1,true,()->false).search(false);
        var s=new DrawnRiverSession(d,drawing,5,12,1.1,true,()->false);
        var p=s.prepare();
        assertEquals(revision,d.getChangeNo());
        assertEquals(p.messages().toString(),3,p.courses().size());
        assertEquals(DrawnRiverSession.Stage.DEEP_TERRAIN,p.stageReached());
        assertTrue(p.courses().size()>=preserved.courses().size());
        assertTrue(s.terrainAdjustmentPlan().summary().maxCut()>6);
        assertTrue(s.terrainAdjustmentPlan().summary().maxCut()<=16);
        assertTrue(p.maxExtraCut()<=16);
        assertTrue(p.identity().contains("nehri-hazırla"));
        assertTrue(DrawnRiverSession.keepsPreservedArms(p, preserved));
    }
    @Test public void shortTributaryMayMeetAValidatedDownstreamStep() {
        Dimension d=ridge();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)d.setHeightAt(x,y,x<68?100:99);
        ShallowRiverCarver c=new ShallowRiverCarver(d,5,12,1.1,true,true,42,null);
        c.enableTerrainPreservation();
        assertTrue(c.getLastRejection(),c.addPath(new int[]{64,100},new int[]{64,64},12,12));
        assertTrue(c.getLastRejection(),c.addJoiningPath(new int[]{64,64},new int[]{62,64},5,12));
    }
    @Test public void valleyGradeUsesSeaSurfaceRatherThanSeabed() {
        Dimension d=ridge();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
            d.setHeightAt(x,y,x<100?20-x*.2f:-1);
            d.setWaterLevelAt(x,y,0);
        }
        int[] xs={20,120},ys={64,64};
        var shallow=TerrainAdjustmentPlan.alongCentreline(d,TerrainAdjustmentPlan.Mode.STRONG,xs,ys,12,6,()->{});
        for(int y=0;y<128;y++)for(int x=100;x<128;x++)d.setHeightAt(x,y,-30);
        var deep=TerrainAdjustmentPlan.alongCentreline(d,TerrainAdjustmentPlan.Mode.STRONG,xs,ys,12,6,()->{});
        for(int y=48;y<=80;y++)for(int x=20;x<100;x++)
            assertEquals("Seabed changed upstream grade at "+x+","+y,shallow.deltaAt(x,y),deep.deltaAt(x,y),0);
        assertEquals(0,deep.deltaAt(110,64),0);
    }
    private Dimension ridge() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        for (int y=0;y<128;y++) for(int x=0;x<128;x++) {
            d.setHeightAt(x,y,(x>=50&&x<=70)?104:100);
            d.setWaterLevelAt(x,y,0);
            d.setTerrainAt(x,y,Terrain.GRASS);
        }
        return d;
    }
    @Test public void strongGradingRecoversDrawnRidgeWithoutLoweringLowReach() {
        Dimension d=ridge();
        int[] xs={20,100}, ys={64,64};
        ShallowRiverCarver before=new ShallowRiverCarver(d,5,5,1.1,true,true,42,null);
        before.enableTerrainPreservation();
        assertFalse(before.addPath(xs,ys));
        long revision=d.getChangeNo();
        TerrainAdjustmentPlan grade=TerrainAdjustmentPlan.alongCentreline(d,TerrainAdjustmentPlan.Mode.STRONG,xs,ys,5,4,()->{});
        assertEquals(0,grade.deltaAt(20,64),0);
        assertTrue(grade.deltaAt(60,64)<-2);
        ShallowRiverCarver after=new ShallowRiverCarver(d,5,5,1.1,true,true,42,null);
        after.enableTerrainPreservation();
        after.setPlanningHeightOverlay((x,y)->grade.adjustedHeight(x,y,d.getHeightAt(x,y)));
        assertTrue(after.getLastRejection(),after.addPath(xs,ys));
        assertEquals(revision,d.getChangeNo());
        assertEquals(0,grade.deltaAt(60,110),0);
        assertTrue(grade.summary().maxCut()<=6);
    }
    @Test public void overlappingEarthworkIsIdempotent() {
        TerrainAdjustmentPlan p=new TerrainAdjustmentPlan(TerrainAdjustmentPlan.Mode.STRONG);
        p.put(40,40,100,-2);p.put(40,40,100,-2);
        assertEquals(-2,p.deltaAt(40,40),0);
        TerrainAdjustmentPlan other=new TerrainAdjustmentPlan(TerrainAdjustmentPlan.Mode.STRONG);
        other.put(40,40,100,-1);
        p.merge(other);p.merge(other);
        assertEquals(-2,p.deltaAt(40,40),0);
    }
    @Test public void strongSessionProducesAndAppliesDrawnRidgeWithUndo() {
        Dimension d=ridge();
        CustomAnnotationLayer drawing=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
        for(int x=20;x<=100;x++) d.setBitLayerValueAt(drawing,x,64,true);
        DrawnRiverSession s=new DrawnRiverSession(d,drawing,5,5,1.1,true,()->false);
        long revision=d.getChangeNo();
        var preview=s.search(true);
        assertTrue(preview.messages().toString(),preview.canApply());
        assertFalse(s.terrainAdjustmentPlan().isEmpty());
        assertEquals(revision,d.getChangeNo());
        d.registerUndoManager(new UndoManager(8));
        try {
            s.apply();
            assertTrue(d.getHeightAt(60,64)<102);
            assertEquals(100,d.getHeightAt(20,100),0);
            assertTrue(d.undoChanges());
            assertEquals(104,d.getHeightAt(60,64),0);
            assertEquals(Terrain.GRASS,d.getTerrainAt(60,64));
        } finally {d.unregisterUndoManager();}
    }
    @Test public void descendingStreamDoesNotRequireWholeMountainAtOutletLevel() {
        Dimension d=ridge();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)d.setHeightAt(x,y,110-x*.1f);
        assertEquals(0,TerrainAdjustmentPlan.requiredExtraCut(d,new int[]{20,100},new int[]{64,64},5,1.1,()->{}),.001);
    }
    @Test public void recoveredTrunkAlsoRecoversPreviouslySkippedBranches() {
        Dimension d=ridge();
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)d.setHeightAt(x,y,(y>=75&&y<=80)||(y>=40&&y<=43)?104:100);
        CustomAnnotationLayer drawing=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
        for(int i=0;i<=30;i++) {
            d.setBitLayerValueAt(drawing,64-i,64-i,true);
            d.setBitLayerValueAt(drawing,64+i,64-i,true);
            d.setBitLayerValueAt(drawing,64,64+i,true);
        }
        DrawnRiverSession s=new DrawnRiverSession(d,drawing,5,10,1.1,true,()->false);
        var p=s.search(true);
        assertEquals(p.messages().toString(),3,p.courses().size());
        assertEquals(0,p.rejected());
    }
    @Test public void cancelledApplicationRestoresGrading() {
        Dimension d=ridge();
        CustomAnnotationLayer drawing=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
        for(int x=20;x<=100;x++)d.setBitLayerValueAt(drawing,x,64,true);
        java.util.concurrent.atomic.AtomicBoolean applying=new java.util.concurrent.atomic.AtomicBoolean();
        DrawnRiverSession s=new DrawnRiverSession(d,drawing,5,5,1.1,true,
                ()->applying.get() && d.getHeightAt(50,64)<104);
        var p=s.search(true);assertTrue(p.messages().toString(),p.canApply());
        d.registerUndoManager(new UndoManager(8));
        try {
            applying.set(true);
            assertThrows(java.util.concurrent.CancellationException.class,s::apply);
            for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
                assertEquals((x>=50&&x<=70)?104:100,d.getHeightAt(x,y),0);
                assertEquals(0,d.getWaterLevelAt(x,y));
            }
        } finally {d.unregisterUndoManager();}
    }
    @Test public void impossibleRidgeDoesNotLeaveAnApplicableEarthworksPlan() {
        Dimension d=ridge();
        for(int y=0;y<128;y++)for(int x=50;x<=70;x++)d.setHeightAt(x,y,120);
        CustomAnnotationLayer drawing=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
        for(int x=20;x<=100;x++)d.setBitLayerValueAt(drawing,x,64,true);
        DrawnRiverSession s=new DrawnRiverSession(d,drawing,5,5,1.1,true,()->false);
        var p=s.search(true);
        assertFalse(p.canApply());
        assertTrue(s.terrainAdjustmentPlan().isEmpty());
        assertEquals(120,d.getHeightAt(60,64),0);
    }
}
