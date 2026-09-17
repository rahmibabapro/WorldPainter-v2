package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.ReadOnly;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.pepsoft.util.undo.UndoManager;
import static org.junit.Assert.*;
import java.nio.file.Path;
import java.util.List;

public class RiverSearchSessionTest {
    @Test public void failedHeadwaterCanAdvanceBelowItsLocalObstruction() throws Exception {
        var method=RiverSearchSession.class.getDeclaredMethod("downstreamOfFailure",List.class,String.class);
        method.setAccessible(true);
        var path=List.of(new RiverSearchResult.Point(0,0),new RiverSearchResult.Point(8,0),
                new RiverSearchResult.Point(16,0),new RiverSearchResult.Point(24,0));
        assertEquals(2,method.invoke(null,path,"ret [hücre=8,0; arazi=100]"));
        assertEquals(-1,method.invoke(null,path,"yapısal ret"));
    }
    @Test(timeout=20000) public void earlierCorridorsDoNotConsumeTheNextValleysMemoryBudget() throws Exception {
        Dimension d=terrain();long before=d.getChangeNo();
        var session=new RiverSearchSession(d,settings(),()->false,null,null);
        // Reproduce the accumulated work from many already discarded corridors.
        var nodes=RiverSearchSession.class.getDeclaredField("expandedNodes");nodes.setAccessible(true);
        var samples=RiverSearchSession.class.getDeclaredField("edgeSamples");samples.setAccessible(true);
        nodes.setLong(session,360_000);samples.setLong(session,24_000_000);
        var result=session.search();
        assertTrue(result.toString(),result.canApply());
        assertTrue(samples.getLong(session)>24_000_000);
        assertEquals(before,d.getChangeNo());
    }
    @Test public void programmingFailureIsNotReportedAsNoOutlet() {
        Dimension d=terrain();long before=d.getChangeNo();
        var session=new RiverSearchSession(d,settings(),()->false,null,(x,y)->{
            throw new IllegalArgumentException("broken obstacle provider");
        });
        assertThrows(IllegalArgumentException.class,session::search);
        assertEquals(before,d.getChangeNo());
        assertThrows(IllegalStateException.class,session::apply);
    }
    @Test(timeout=20000) public void avoidedStripIsNeverIncludedInPreview() {
        Dimension d=terrain();long before=d.getChangeNo();
        var result=new RiverSearchSession(d,settings(),()->false,null,(x,y)->x>=120&&x<=135).search();
        for(var cell:result.cells())assertFalse(cell.x()>=120&&cell.x()<=135);
        assertEquals(before,d.getChangeNo());
    }
    @Test(timeout=20000) public void missingTileIsNeverBridgedByPreview() {
        Dimension d=terrain();d.removeTile(1,0);long before=d.getChangeNo();
        var result=new RiverSearchSession(d,settings(),()->false,null,null).search();
        for(var cell:result.cells())assertTrue(d.isTilePresent(cell.x()>>7,cell.y()>>7));
        for(var course:result.courses())for(int i=1;i<course.centreline().size();i++) {
            var a=course.centreline().get(i-1);var b=course.centreline().get(i);
            assertFalse((a.x()<128&&b.x()>=256)||(b.x()<128&&a.x()>=256));
        }
        assertEquals(before,d.getChangeNo());
    }
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
        for(var c:result.cells())assertTrue(c.originalHeight()-c.bedHeight()<=4.01);
    }
    @Test(timeout=20_000) public void widePresetStillYieldsApplyableCorridorInNarrowValley() {
        Dimension d=terrain();long before=d.getChangeNo();
        var wide=new RiverSearchSession.Settings(1,8,20,1.40,true,733,120_000);
        var session=new RiverSearchSession(d,wide,()->false,null,null);
        var result=session.search();
        assertTrue(result.toString(),result.canApply());
        assertFalse(result.courses().isEmpty());
        assertEquals(before,d.getChangeNo());
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
    @Test(timeout=20_000) public void mountainValleyReachesSeaLevel() {
        Dimension d=mountainToSea();long before=d.getChangeNo();
        var settings=new RiverSearchSession.Settings(1,3,6,.85,true,733,120_000,62);
        var result=new RiverSearchSession(d,settings,()->false,null,null).search();
        assertTrue(result.toString(),result.canApply());
        assertFalse(result.courses().isEmpty());
        var end=result.courses().get(0).centreline();
        boolean reachedSea=false;
        for(var p:end) {
            if(d.getWaterLevelAt(p.x(),p.y())>Math.round(d.getHeightAt(p.x(),p.y()))
                    || d.getHeightAt(p.x(),p.y())<=62 || p.x()<40) {reachedSea=true;break;}
        }
        assertTrue("path should reach the western sea, last="+end.get(end.size()-1)+" "+result,reachedSea);
        assertEquals(before,d.getChangeNo());
        Path copy=Path.of("target","river-search-selftest-sea.ndjson");
        assertNotNull(RiverSearchLog.write(settings,result,false,copy));
    }
    @Test(timeout=20_000) public void paintedSeaPrefersRiverOverDepressionLake() {
        Dimension d=bowlAndOutlet(3);long before=d.getChangeNo();
        var settings=new RiverSearchSession.Settings(1,3,6,1.40,true,733,120_000,0);
        var result=new RiverSearchSession(d,settings,()->false,null,null).search();
        assertTrue(result.toString(),result.canApply());
        assertTrue("should reach painted water, not only flood a bowl "+result,
                result.courses().stream().anyMatch(c->{
                    var end=c.centreline().get(c.centreline().size()-1);
                    return d.getWaterLevelAt(end.x(),end.y())>Math.round(d.getHeightAt(end.x(),end.y()))
                            ||c.centreline().stream().anyMatch(p->
                            d.getWaterLevelAt(p.x(),p.y())>Math.round(d.getHeightAt(p.x(),p.y())));
                }));
        // A tiny centreline + big lake is the failure mode the user reported.
        for(var c:result.courses()) {
            if(!c.lakeCells().isEmpty()&&c.lakeCells().size()>=32) {
                double len=0;
                for(int i=1;i<c.centreline().size();i++) {
                    var a=c.centreline().get(i-1);var b=c.centreline().get(i);
                    len+=Math.hypot(b.x()-a.x(),b.y()-a.y());
                }
                assertTrue("lake must not replace a river when sea is reachable; len="+len+" "+result,len>=64);
            }
        }
        assertEquals(before,d.getChangeNo());
    }
    @Test(timeout=20_000) public void closedBasinFormsContainedLake() {
        Dimension d=closedBasin();long before=d.getChangeNo();
        var settings=new RiverSearchSession.Settings(1,3,6,.85,true,733,120_000,0);
        var result=new RiverSearchSession(d,settings,()->false,null,null).search();
        assertTrue(result.toString(),result.canApply());
        assertTrue("expected lake cells, got "+result.diagnostics().lakeCells()+" "+result,
                result.diagnostics().lakeCells()>0);
        assertEquals(before,d.getChangeNo());
        Path copy=Path.of("target","river-search-selftest-lake.ndjson");
        assertNotNull(RiverSearchLog.write(settings,result,result.diagnostics().forced(),copy));
    }
    @Test(timeout=20_000) public void bowlWithThreeBlockSaddleReachesPaintedWater() throws Exception {
        Dimension d=bowlAndOutlet(3);long before=d.getChangeNo();
        var settings=new RiverSearchSession.Settings(1,3,6,1.40,true,733,120_000,0);
        var session=new RiverSearchSession(d,settings,()->false,null,null);
        var survey=new RiverTerrainSurvey(d,()->{},null,0);
        survey.scan();
        var surveyField=RiverSearchSession.class.getDeclaredField("survey");surveyField.setAccessible(true);
        surveyField.set(session,survey);
        var build=RiverSearchSession.class.getDeclaredMethod("buildDownhillRoute",RiverSearchResult.Point.class);
        build.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<RiverSearchResult.Point> downhill=(List<RiverSearchResult.Point>)build.invoke(session,new RiverSearchResult.Point(48,40));
        assertNotNull(downhill);
        double downLen=0;
        for(int i=1;i<downhill.size();i++) {
            var a=downhill.get(i-1);var b=downhill.get(i);
            downLen+=Math.hypot(b.x()-a.x(),b.y()-a.y());
        }
        assertTrue("downhill should clear the +3 saddle toward water, len="+downLen
                +" last="+downhill.get(downhill.size()-1),downLen>=64);
        boolean crossedSaddle=false,enteredValley=false,downhillWet=false;
        for(var p:downhill) {
            if(p.y()>=54&&p.y()<=58&&Math.abs(p.x()-48)<=10) crossedSaddle=true;
            if(p.y()>80&&Math.abs(p.x()-48)<=10) enteredValley=true;
            if(d.getWaterLevelAt(p.x(),p.y())>Math.round(d.getHeightAt(p.x(),p.y()))) downhillWet=true;
        }
        assertTrue("buildDownhillRoute must reach painted water over +3; last="
                +downhill.get(downhill.size()-1)+" len="+downLen+" size="+downhill.size()
                +" saddle="+crossedSaddle+" valley="+enteredValley
                +" lastH="+d.getHeightAt(downhill.get(downhill.size()-1).x(),downhill.get(downhill.size()-1).y()),
                downhillWet);
        var result=session.search();
        if(result.canApply()) {
            assertFalse(result.courses().isEmpty());
            var course=result.courses().get(0);
            double len=0;
            for(int i=1;i<course.centreline().size();i++) {
                var a=course.centreline().get(i-1);var b=course.centreline().get(i);
                len+=Math.hypot(b.x()-a.x(),b.y()-a.y());
            }
            assertTrue("Apply-ready +3 route must be long, got "+len+" "+result,len>=64);
            boolean reachedWet=false;
            for(var p:course.centreline()) {
                if(d.getWaterLevelAt(p.x(),p.y())>Math.round(d.getHeightAt(p.x(),p.y()))) {reachedWet=true;break;}
            }
            assertTrue("Apply-ready +3 route must reach painted water "+result,reachedWet);
        }
        assertEquals(before,d.getChangeNo());
    }
    @Test(timeout=20_000) public void sixteenBlockRidgeDoesNotClimbToDistantWater() throws Exception {
        Dimension d=bowlAndOutlet(16);long before=d.getChangeNo();
        var settings=new RiverSearchSession.Settings(1,3,6,.85,true,733,120_000,0);
        var session=new RiverSearchSession(d,settings,()->false,null,null);
        var result=session.search();
        var build=RiverSearchSession.class.getDeclaredMethod("buildDownhillRoute",RiverSearchResult.Point.class);
        build.setAccessible(true);
        var surveyField=RiverSearchSession.class.getDeclaredField("survey");surveyField.setAccessible(true);
        assertNotNull(surveyField.get(session));
        @SuppressWarnings("unchecked")
        List<RiverSearchResult.Point> downhill=(List<RiverSearchResult.Point>)build.invoke(session,new RiverSearchResult.Point(48,40));
        if(downhill!=null) {
            boolean crossedRidge=false;
            for(var p:downhill) {
                if(p.y()>=56&&p.y()<=58&&Math.abs(p.x()-48)<=10
                        &&d.getHeightAt(p.x(),p.y())>=43) {crossedRidge=true;break;}
            }
            assertFalse("must not climb the +16 ridge "+downhill.size(),crossedRidge);
            boolean reachedWet=false;
            for(var p:downhill) {
                if(d.getWaterLevelAt(p.x(),p.y())>Math.round(d.getHeightAt(p.x(),p.y()))) {reachedWet=true;break;}
            }
            assertFalse("must not reach distant water over +16 ridge",reachedWet);
        }
        // Acceptable outcomes: contained lake Apply, or no Apply — never a tiny stub.
        if(result.canApply()) {
            assertTrue("Apply-ready closed basin needs a real lake, lakes="+result.diagnostics().lakeCells()+" "+result,
                    result.diagnostics().lakeCells()>=32
                            ||result.courses().stream().anyMatch(c->c.lakeCells().size()>=32));
        } else {
            assertTrue(result.courses().isEmpty()||!result.canApply());
        }
        assertEquals(before,d.getChangeNo());
    }
    @Test(timeout=20_000) public void gentleFlatSlopeKeepsStraightCentreline() throws Exception {
        Dimension d=gentleWestSlope();
        var settings=new RiverSearchSession.Settings(1,3,6,.85,true,733,120_000,0);
        var session=new RiverSearchSession(d,settings,()->false,null,null);
        var survey=new RiverTerrainSurvey(d,()->{},null,0);
        survey.scan();
        var surveyField=RiverSearchSession.class.getDeclaredField("survey");surveyField.setAccessible(true);
        surveyField.set(session,survey);
        var build=RiverSearchSession.class.getDeclaredMethod("buildDownhillRoute",RiverSearchResult.Point.class);
        build.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<RiverSearchResult.Point> path=(List<RiverSearchResult.Point>)build.invoke(session,new RiverSearchResult.Point(200,64));
        assertNotNull(path);
        assertTrue("path too short: "+path.size(),path.size()>=40);
        double len=0;
        int hardTurns=0;
        int prevDx=0,prevDy=0;
        for(int i=1;i<path.size();i++) {
            var a=path.get(i-1);var b=path.get(i);
            int dx=Integer.signum(b.x()-a.x()),dy=Integer.signum(b.y()-a.y());
            len+=Math.hypot(b.x()-a.x(),b.y()-a.y());
            if(prevDx!=0||prevDy!=0) {
                int dot=prevDx*dx+prevDy*dy;
                if(dot<=0) hardTurns++;
            }
            if(dx!=0||dy!=0){prevDx=dx;prevDy=dy;}
        }
        var start=path.get(0);var end=path.get(path.size()-1);
        double chord=Math.hypot(end.x()-start.x(),end.y()-start.y());
        assertTrue("expected mostly westbound; last="+end+" len="+len,end.x()<start.x()-40);
        assertTrue("path should not meander far; len="+len+" chord="+chord,len<=chord*1.35+8);
        assertTrue("too many hard turns ("+hardTurns+") on a gentle flat, size="+path.size(),
                hardTurns<=Math.max(6,path.size()/25));
    }
    private static Dimension gentleWestSlope() {
        var factory=TestData.createTileFactory(70);
        var world=new World2(TestData.PLATFORM,TestData.MIN_HEIGHT,TestData.MAX_HEIGHT);
        var d=new Dimension(world,"Gentle west",733,factory,Dimension.Anchor.NORMAL_DETAIL);
        for(int tx=0;tx<2;tx++) {
            Tile t=factory.createTile(tx,0);
            for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
                int wx=tx*128+x;
                float h=40f+(wx*0.04f);
                int water=0;
                if(wx<18){h=28f;water=30;}
                t.setHeight(x,y,h);
                t.setWaterLevel(x,y,water);
                t.setTerrain(x,y,Terrain.GRASS);
            }
            d.addTile(t);
        }
        return d;
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
    private static Dimension mountainToSea() {
        var factory=TestData.createTileFactory(110);
        var world=new World2(TestData.PLATFORM,TestData.MIN_HEIGHT,TestData.MAX_HEIGHT);
        var d=new Dimension(world,"Sea flow",733,factory,Dimension.Anchor.NORMAL_DETAIL);
        for(int tx=0;tx<2;tx++) {
            Tile t=factory.createTile(tx,0);
            for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
                int wx=tx*128+x;
                float h=110;
                if(y<3||y>124||wx>250) h=200;
                else if(wx<36) h=50;
                else if(Math.abs(y-64)<=6) h=50+(wx-36)*0.22f;
                t.setHeight(x,y,h);
                t.setWaterLevel(x,y,62);
                t.setTerrain(x,y,Terrain.GRASS);
            }
            d.addTile(t);
        }
        return d;
    }
    private static Dimension closedBasin() {
        var factory=TestData.createTileFactory(95);
        var world=new World2(TestData.PLATFORM,TestData.MIN_HEIGHT,TestData.MAX_HEIGHT);
        var d=new Dimension(world,"Closed basin",733,factory,Dimension.Anchor.NORMAL_DETAIL);
        Tile t=factory.createTile(0,0);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
            boolean hole=x>=36&&x<=92&&y>=36&&y<=92;
            t.setHeight(x,y,hole?80:95);
            t.setWaterLevel(x,y,0);
            t.setTerrain(x,y,Terrain.GRASS);
        }
        d.addTile(t);
        return d;
    }
    /** Local bowl, then a saddle/ridge of {@code rise} blocks, then a valley to painted water. */
    private static Dimension bowlAndOutlet(int rise) {
        var factory=TestData.createTileFactory(50);
        var world=new World2(TestData.PLATFORM,TestData.MIN_HEIGHT,TestData.MAX_HEIGHT);
        var d=new Dimension(world,"Bowl saddle "+rise,733,factory,Dimension.Anchor.NORMAL_DETAIL);
        for(int ty=0;ty<2;ty++) {
            Tile t=factory.createTile(0,ty);
            for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
                int wx=x,wy=ty*128+y;
                float h=80f;
                int water=0;
                boolean corridor=Math.abs(wx-48)<=10;
                if(wy<3||wy>252||wx<3||wx>124) h=90;
                else if(wy>=180&&corridor) {h=26f;water=28;}
                else if(corridor&&wy>=40&&wy<54) {
                    // Bowl floor open to the south — no sealed rim on the exit bearing.
                    h=27f+(54-wy)*0.05f;
                }
                else if(corridor&&wy>=54&&wy<=58) h=27+rise;
                else if(corridor&&wy>58&&wy<180) h=Math.max(27f,27+Math.min(rise,4)-(wy-58)*0.02f);
                else if(Math.hypot(wx-48,wy-40)<=14) {
                    // Sealed sides/north of the bowl only.
                    h=27+Math.min(rise,4);
                }
                t.setHeight(x,y,h);
                t.setWaterLevel(x,y,water);
                t.setTerrain(x,y,Terrain.GRASS);
            }
            d.addTile(t);
        }
        return d;
    }
}
