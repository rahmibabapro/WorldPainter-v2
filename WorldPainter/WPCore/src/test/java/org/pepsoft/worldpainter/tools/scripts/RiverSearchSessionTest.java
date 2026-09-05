package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.ReadOnly;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.pepsoft.util.undo.UndoManager;
import static org.junit.Assert.*;

public class RiverSearchSessionTest {
    @Test public void overviewFindsValleyBetweenLegacySamplesWithoutWriting() {
        Dimension d=terrain();
        long before=d.getChangeNo();
        RiverTerrainSurvey survey=new RiverTerrainSurvey(d,()->{},null);survey.scan();
        assertEquals(8,survey.step);
        boolean found=false;
        for(int i=0;i<survey.heights.length;i++)if(survey.ys[i]==67&&survey.heights[i]<101)found=true;
        assertTrue(found);assertEquals(before,d.getChangeNo());
    }
    @Test public void protectedMinimumIsNotARepresentative() {
        Dimension d=terrain();d.setHeightAt(40,67,0);d.setBitLayerValueAt(ReadOnly.INSTANCE,40,67,true);
        RiverTerrainSurvey survey=new RiverTerrainSurvey(d,()->{},null);survey.scan();
        for(int i=0;i<survey.heights.length;i++)assertFalse(survey.xs[i]==40&&survey.ys[i]==67);
    }
    @Test public void cancellationLeavesWorldUntouched() {
        Dimension d=terrain();long before=d.getChangeNo();
        var session=new RiverSearchSession(d,settings(),()->true,null,null);
        assertEquals(RiverSearchResult.Status.CANCELLED,session.search().status());
        assertEquals(before,d.getChangeNo());assertThrows(IllegalStateException.class,session::apply);
    }
    @Test public void deadlineIsSharedAndDoesNotChangeTerrain() {
        Dimension d=terrain();long before=d.getChangeNo();AtomicLong time=new AtomicLong();
        var session=new RiverSearchSession(d,settings(),()->false,null,null,()->time.addAndGet(121_000_000_000L));
        assertEquals(RiverSearchResult.Status.BUDGET_EXHAUSTED,session.search().status());
        assertEquals(before,d.getChangeNo());
    }
    @Test public void changedWorldInvalidatesSearch() {
        Dimension d=terrain();var session=new RiverSearchSession(d,settings(),()->false,null,null);
        d.setHeightAt(10,10,103);
        assertEquals(RiverSearchResult.Status.STALE_WORLD,session.search().status());
        assertThrows(IllegalStateException.class,session::apply);
    }
    @Test(timeout=20_000) public void knownOpenValleyProducesDetachedPreview() {
        Dimension d=terrain();long before=d.getChangeNo();
        var oracle=new ShallowRiverCarver(d,3,6,.85,true,true,733,null);
        oracle.enableTerrainPreservation();
        assertTrue(oracle.getLastRejection(),oracle.addPath(new int[]{192,12},new int[]{67,67}));
        var session=new RiverSearchSession(d,settings(),()->false,null,null);
        var result=session.search();
        assertTrue(result.toString(),result.canApply());
        assertFalse(result.cells().isEmpty());assertEquals(before,d.getChangeNo());
        assertThrows(UnsupportedOperationException.class,()->result.cells().clear());
        assertThrows(UnsupportedOperationException.class,()->result.courses().get(0).centreline().clear());
        for(var c:result.cells())assertTrue(c.originalHeight()-c.bedHeight()<=1.851);
    }
    private static RiverSearchSession.Settings settings(){return new RiverSearchSession.Settings(1,3,6,.85,true,733,120_000);}
    @Test public void applyHasOneUndoStepAndCannotBeRepeated() {
        Dimension d=terrain();d.registerUndoManager(new UndoManager(10));
        try {
            var session=new RiverSearchSession(d,settings(),()->false,null,null);
            var result=session.search();assertTrue(result.canApply());
            session.apply();
            assertThrows(IllegalStateException.class,session::apply);
            assertTrue(d.undoChanges());
            for(var c:result.cells()) {
                assertEquals(c.originalHeight(),d.getHeightAt(c.x(),c.y()),0);
                assertEquals(0,d.getWaterLevelAt(c.x(),c.y()));
                assertEquals(Terrain.GRASS,d.getTerrainAt(c.x(),c.y()));
            }
        } finally {d.unregisterUndoManager();}
    }
    @Test public void completedPreviewIsRejectedAfterAnEdit() {
        Dimension d=terrain();var session=new RiverSearchSession(d,settings(),()->false,null,null);
        assertTrue(session.search().canApply());d.setHeightAt(0,0,106);
        assertTrue(session.isStale());assertThrows(IllegalStateException.class,session::apply);
    }
    @Test public void completedSearchIsDeterministicWithFixedSeed() {
        Dimension d=terrain();
        var a=new RiverSearchSession(d,settings(),()->false,null,null).search();
        var b=new RiverSearchSession(d,settings(),()->false,null,null).search();
        assertEquals(a.courses(),b.courses());assertEquals(a.cells(),b.cells());
    }
    @Test public void noOutletDoesNotFillAProtectedBasin() {
        Dimension d=terrain();
        for(Tile t:d.getTiles())for(int y=0;y<128;y++)for(int x=0;x<128;x++)
            t.setBitLayerValue(ReadOnly.INSTANCE,x,y,true);
        long before=d.getChangeNo();var result=new RiverSearchSession(d,settings(),()->false,null,null).search();
        assertEquals(RiverSearchResult.Status.NO_FEASIBLE_OUTLET,result.status());
        assertEquals(before,d.getChangeNo());assertFalse(result.canApply());
    }
    private static Dimension terrain() {
        var factory=TestData.createTileFactory(100);
        var world=new World2(TestData.PLATFORM,TestData.MIN_HEIGHT,TestData.MAX_HEIGHT);
        var d=new Dimension(world,"Search V2",733,factory,Dimension.Anchor.NORMAL_DETAIL);
        for(int tx=0;tx<3;tx++) {
            Tile t=factory.createTile(tx,0);
            for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
                t.setHeight(x,y,Math.abs(y-67)<=4?100:104);
                t.setWaterLevel(x,y,0);t.setTerrain(x,y,Terrain.GRASS);
            }
            d.addTile(t);
        }
        return d;
    }
}
