package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.CustomAnnotationLayer;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.util.undo.UndoManager;
import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class DrawnRiverSessionTest {
    private final CustomAnnotationLayer layer=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
    private Dimension terrain(){
        var factory=TestData.createTileFactory(100);var world=new World2(TestData.PLATFORM,TestData.MIN_HEIGHT,TestData.MAX_HEIGHT);
        var d=new Dimension(world,"Drawn river",733,factory,Dimension.Anchor.NORMAL_DETAIL);
        var t=factory.createTile(0,0);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++){t.setHeight(x,y,100+(127-y)*.001f);t.setWaterLevel(x,y,0);t.setTerrain(x,y,Terrain.GRASS);}
        d.addTile(t);for(var p:DrawnRiverGraphTest.yDrawing())d.setBitLayerValueAt(layer,p.x(),p.y(),true);
        return d;
    }
    private DrawnRiverSession session(Dimension d,AtomicBoolean cancel){return new DrawnRiverSession(d,layer,3,12,.85,true,cancel::get);}
    @Test public void yNetworkPreviewsWithoutWritingAndAppliesWithOneUndo(){
        var d=terrain();d.registerUndoManager(new UndoManager(10));long before=d.getChangeNo();
        try {
            var s=session(d,new AtomicBoolean());var r=s.search();assertEquals(before,d.getChangeNo());
            assertEquals(r.messages().toString(),3,r.courses().size());assertTrue(r.canApply());
            var water=new java.util.HashMap<String,Integer>();
            for(var c:r.cells())assertNull("Duplicate planned cell",water.put(c.x()+","+c.y(),c.waterLevel()));
            for(int y=63;y<=66;y++)for(int x=63;x<=65;x++)assertEquals("Junction gap at "+x+","+y,Integer.valueOf(100),water.get(x+","+y));
            s.apply();assertThrows(IllegalStateException.class,s::apply);assertTrue(d.undoChanges());
            for(var c:r.cells()){assertEquals(c.originalHeight(),d.getHeightAt(c.x(),c.y()),0);assertEquals(0,d.getWaterLevelAt(c.x(),c.y()));assertEquals(Terrain.GRASS,d.getTerrainAt(c.x(),c.y()));}
        }finally{d.unregisterUndoManager();}
    }
    @Test public void changedDrawingInvalidatesPreview(){var d=terrain();var s=session(d,new AtomicBoolean());s.search();d.setBitLayerValueAt(layer,10,10,true);assertTrue(s.isStale());assertThrows(IllegalStateException.class,s::apply);}
    @Test public void cancelDoesNotWrite(){var d=terrain();long before=d.getChangeNo();assertThrows(CancellationException.class,()->session(d,new AtomicBoolean(true)).search());assertEquals(before,d.getChangeNo());}
    @Test public void cancellationAfterPreviewPreventsAnyWrite(){var d=terrain();d.registerUndoManager(new UndoManager(10));try{var stop=new AtomicBoolean();var s=session(d,stop);var r=s.search();stop.set(true);assertThrows(CancellationException.class,s::apply);for(var c:r.cells())assertEquals(c.originalHeight(),d.getHeightAt(c.x(),c.y()),0);}finally{d.unregisterUndoManager();}}
    @Test public void rejectsRaisedSourceWithoutIncreasingCutBudget(){var d=terrain();for(int y=25;y<=44;y++)for(int x=25;x<=43;x++)d.setHeightAt(x,y,103);long before=d.getChangeNo();var r=session(d,new AtomicBoolean()).search();assertTrue(r.rejected()>0);assertEquals(before,d.getChangeNo());for(var c:r.cells())assertTrue(c.originalHeight()-c.bedHeight()<=.85+.75+1+.01);}
    @Test public void prepareSkipsDeepWhenPreserveCompletesTheY(){
        var d=terrain();long before=d.getChangeNo();
        var r=session(d,new AtomicBoolean()).prepare();
        assertEquals(before,d.getChangeNo());
        assertEquals(r.messages().toString(),3,r.courses().size());
        assertNotEquals(DrawnRiverSession.Stage.DEEP_TERRAIN,r.stageReached());
        assertTrue(r.messages().stream().anyMatch(m->m.contains("Derin kazı gerekmedi")));
        assertTrue(r.identity().contains("nehri-hazırla"));
    }
    @Test public void excludedEdgesInvalidatePreparedPreview(){
        var d=terrain();var s=session(d,new AtomicBoolean());
        assertTrue(s.prepare().canApply());
        s.setExcludedEdges(Set.of(DrawnRiverGraph.Edge.of(
                new DrawnRiverGraph.Pixel(64,64),new DrawnRiverGraph.Pixel(64,65))));
        assertTrue(s.isStale());
        assertThrows(IllegalStateException.class,s::apply);
    }
    @Test public void prepareBudgetIsFiveMinutesWithTwoMinutePreserveCap() {
        assertEquals(300_000_000_000L, DrawnRiverSession.PREPARE_BUDGET_NS);
        assertEquals(120_000_000_000L, DrawnRiverSession.PRESERVE_CAP_NS);
        assertTrue(DrawnRiverSession.identity().contains("nehri-hazırla"));
    }
    @Test public void cancelDoesNotWriteOnPrepare(){
        var d=terrain();long before=d.getChangeNo();
        assertThrows(CancellationException.class,()->session(d,new AtomicBoolean(true)).prepare());
        assertEquals(before,d.getChangeNo());
    }
    @Test public void seaAmbiguousStrokeDoesNotBlockPreparedY(){
        var d=terrain();
        for(int y=100;y<=110;y++)for(int x=10;x<=20;x++){d.setHeightAt(x,y,80);d.setWaterLevelAt(x,y,90);}
        for(int x=12;x<=18;x++)d.setBitLayerValueAt(layer,x,105,true);
        long before=d.getChangeNo();
        var r=session(d,new AtomicBoolean()).prepare();
        assertEquals(before,d.getChangeNo());
        assertEquals(r.messages().toString(),3,r.courses().size());
        assertTrue(r.skippedGroups()>=1);
        assertTrue(r.diagnostics().stream().anyMatch(diag->diag.kind()==DrawnRiverNormalizer.IssueKind.MULTI_OUTLET));
        assertNotEquals(DrawnRiverSession.Stage.DEEP_TERRAIN,r.stageReached());
    }
    @Test public void protectedTrunkBlocksItsDependentBranches(){var d=terrain();for(int x=0;x<128;x++)d.setBitLayerValueAt(ReadOnly.INSTANCE,x,80,true);long before=d.getChangeNo();var r=session(d,new AtomicBoolean()).search();assertFalse(r.canApply());assertEquals(before,d.getChangeNo());}
    @Test public void extraPiecesDoNotCountAsKeepingADroppedArm(){
        var kept=new RiverSearchResult.Course(List.of(p(10,10),p(10,20)),5,8,0);
        var extra=new RiverSearchResult.Course(List.of(p(80,80),p(80,90),p(80,100)),5,8,0);
        var other=new RiverSearchResult.Course(List.of(p(40,40),p(40,50)),5,8,0);
        assertTrue(DrawnRiverSession.keepsPreservedArms(List.of(kept,extra),List.of(kept)));
        assertFalse(DrawnRiverSession.keepsPreservedArms(List.of(extra,other),List.of(kept)));
        assertTrue(DrawnRiverSession.keepsPreservedArms(List.of(kept),List.of(kept)));
    }
    @Test public void unexpectedDeepFailureKeepsPreviousValidatedResult(){
        var d=terrain();
        for(int x=20;x<=100;x++)d.setBitLayerValueAt(layer,x,10,true);
        for(int x=50;x<=70;x++)d.setHeightAt(x,10,120);
        long before=d.getChangeNo();
        var s=session(d,new AtomicBoolean());
        var preserved=new DrawnRiverSession(d,layer,3,12,.85,true,()->false).search(false);
        assertTrue(preserved.canApply());
        assertTrue(preserved.terrainRejected()>0);
        var r=s.prepare((graph,drawing)->{throw new IllegalStateException("beklenmeyen");});
        assertEquals(before,d.getChangeNo());
        assertEquals(preserved.courses().size(),r.courses().size());
        assertNotEquals(DrawnRiverSession.Stage.DEEP_TERRAIN,r.stageReached());
        assertTrue(r.messages().stream().anyMatch(m->m.contains("hesap hatası")));
        assertTrue(r.canApply());
        assertTrue(DrawnRiverSession.keepsPreservedArms(r,preserved));
    }
    private static RiverSearchResult.Point p(int x,int y){return new RiverSearchResult.Point(x,y);}
}
