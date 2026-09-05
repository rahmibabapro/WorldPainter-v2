package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Frost;

import java.awt.Rectangle;

import static org.junit.Assert.*;

public class TerrainPreservingRiverCarverTest {
    @Test public void preservationReplacesEarthworksWithZeroFillAndShallowCutBudget() {
        final ShallowRiverCarver plan = plan(terrain(),true,null);
        plan.enableTerrainAdaptation();
        plan.enableTerrainPreservation();
        assertTrue(plan.isTerrainPreservationEnabled());
        assertFalse(plan.isTerrainAdaptationEnabled());
        assertEquals(0,plan.getMaximumFill(),0);
        assertEquals(1.85,plan.getMaximumCut(),0.00001);
    }

    @Test public void roughDryShouldersStayExactlyOriginalAndOnlyWetBedChanges() {
        final Dimension d=terrain();
        final Cell[] before=snapshot(d);
        final ShallowRiverCarver plan=plan(d,true,null);
        accept(plan);
        assertArrayEquals("Planning is read only",before,snapshot(d));
        final ShallowRiverCarver.Result result=plan.apply();
        assertEquals(0,result.raisedCells());
        assertEquals(0,result.maximumFill(),0);
        assertTrue(result.maximumCut()<=1.85);
        assertTrue(result.changedCells()>100);
        assertDryUnchangedAndCutsBounded(d,before);
        for(int x=24;x<=104;x++) for(int y=63;y<=65;y++) {
            assertTrue("Three actual wet columns",d.getWaterLevelAt(x,y)>d.getIntHeightAt(x,y));
        }
    }

    @Test public void preservationWillNotCutARidgeOrFillAPitToForceTheOldPath() {
        final Dimension d=terrain();
        for(int y=0;y<128;y++) d.setHeightAt(64,y,104);
        final Cell[] before=snapshot(d);
        final ShallowRiverCarver plan=plan(d,true,null);
        assertFalse(plan.addPath(new int[]{20,108},new int[]{64,64}));
        assertEquals(0,plan.apply().changedCells());
        assertArrayEquals(before,snapshot(d));
    }

    @Test public void lateralCliffCannotBecomeAnArtificialBench() {
        final Dimension d=terrain();
        for(int y=0;y<128;y++) for(int x=0;x<128;x++) d.setHeightAt(x,y,100+(y-64)*2);
        final Cell[] before=snapshot(d);
        final ShallowRiverCarver plan=plan(d,true,null);
        assertFalse(plan.addPath(new int[]{20,108},new int[]{64,64}));
        assertEquals(0,plan.apply().changedCells());
        assertArrayEquals(before,snapshot(d));
    }

    @Test public void smallNaturalRiffleCanShallowTheBedWithoutExceedingTheCutBudget() {
        final Dimension d=terrain();
        d.setHeightAt(64,64,100.83203125f);
        final Cell[] before=snapshot(d);
        final ShallowRiverCarver plan=plan(d,true,null);
        accept(plan);
        final ShallowRiverCarver.Result result=plan.apply();
        final double actualDepth=d.getWaterLevelAt(64,64)-d.getHeightAt(64,64);
        assertTrue("The nominal depth is a maximum, not compulsory extra incision",actualDepth<1.1);
        assertTrue(actualDepth>=0.65-1.0/256);
        assertTrue(result.maximumCut()<=1.85);
        assertEquals(0,result.maximumFill(),0);
        assertDryUnchangedAndCutsBounded(d,before);
    }

    @Test public void fractionalGroundDoesNotPutWaterAboveTheOriginalExportedSurface() {
        final Dimension d=terrain();
        for(int y=0;y<128;y++) for(int x=0;x<128;x++) d.setHeightAt(x,y,100.75f);
        final Cell[] before=snapshot(d);
        final ShallowRiverCarver plan=plan(d,true,null);
        accept(plan);
        plan.apply();
        assertEquals("Original full surface voxel is at round(100.75), not floor(100.75)",
                Math.round(before[64*128+64].height),d.getWaterLevelAt(64,64));
        assertDryUnchangedAndCutsBounded(d,before);
    }

    @Test public void smoothSettingOnlyChangesTheWetBowl() {
        final Dimension smooth=terrain(),linear=terrain();
        final Cell[] before=snapshot(smooth);
        final ShallowRiverCarver curved=plan(smooth,true,null),straight=plan(linear,false,null);
        accept(curved); accept(straight);
        curved.apply(); straight.apply();
        assertDryUnchangedAndCutsBounded(smooth,before);
        assertDryUnchangedAndCutsBounded(linear,before);
        boolean differs=false;
        for(int y=0;y<128;y++) for(int x=0;x<128;x++) {
            if(smooth.getHeightAt(x,y)!=linear.getHeightAt(x,y)) differs=true;
        }
        assertTrue("Smoothing must remain a functional wet-bed shape option",differs);
    }

    @Test public void cancelledApplicationRestoresWetWritesAndNeverTouchesDryTerrain() {
        final Dimension d=terrain();
        final Cell[] before=snapshot(d);
        final boolean[] applying={false},sawWrite={false};
        final ScriptProgress progress=new ScriptProgress(null,null) {
            @Override public void checkForCancel() {
                if(applying[0] && d.getHeightAt(20,64)<before[64*128+20].height) {
                    sawWrite[0]=true;
                    throw new ScriptingContext.InterruptedException();
                }
            }
            @Override public void setProgress(double value) {}
        };
        final ShallowRiverCarver plan=plan(d,true,progress);
        accept(plan);
        applying[0]=true;
        assertThrows(ScriptingContext.InterruptedException.class,plan::apply);
        assertTrue(sawWrite[0]);
        assertArrayEquals(before,snapshot(d));
    }

    @Test public void modeCannotChangeAfterAcceptedPath() {
        final ShallowRiverCarver plan=plan(terrain(),true,null);
        accept(plan);
        assertThrows(IllegalStateException.class,plan::enableTerrainPreservation);
        assertThrows(IllegalStateException.class,plan::enableTerrainAdaptation);
    }

    @Test public void broadOldTerraceEnvelopeIsAbsentFromPreservingMode() {
        final Dimension old=terrain(),preserved=terrain();
        final Cell[] before=snapshot(old);
        final ShallowRiverCarver earthworks=plan(old,true,null),narrow=plan(preserved,true,null);
        earthworks.enableTerrainAdaptation();
        accept(earthworks); accept(narrow);
        earthworks.apply(); narrow.apply();
        int oldDryChanges=0,newDryChanges=0;
        for(int y=0;y<128;y++) for(int x=0;x<128;x++) {
            final Cell original=before[y*128+x];
            if(old.getWaterLevelAt(x,y)<=old.getIntHeightAt(x,y) && old.getHeightAt(x,y)!=original.height) oldDryChanges++;
            if(preserved.getWaterLevelAt(x,y)<=preserved.getIntHeightAt(x,y)
                    && preserved.getHeightAt(x,y)!=original.height) newDryChanges++;
        }
        assertTrue("The fixture must reproduce the old broad earthworks",oldDryChanges>500);
        assertEquals("The new default must not draw a flattened dry strip",0,newDryChanges);
        System.out.printf("River dry-terrain changes on identical rough banks: previous=%d, preserving=%d%n",
                oldDryChanges,newDryChanges);
    }

    private static void assertDryUnchangedAndCutsBounded(Dimension d,Cell[] before) {
        final Cell[] after=snapshot(d);
        int dry=0;
        for(int y=0;y<128;y++) for(int x=0;x<128;x++) {
            final int i=y*128+x;
            assertTrue("No terrain raises",after[i].height<=before[i].height);
            assertTrue("No deep incision",before[i].height-after[i].height<=1.85);
            if(after[i].water<=Math.round(after[i].height)) {
                assertEquals("Dry terrain changed at "+x+","+y,before[i],after[i]);
                dry++;
            }
            if(y<58 || y>70) assertEquals("No broad shoulder flattening",before[i],after[i]);
        }
        assertTrue(dry>15000);
    }

    private static void accept(ShallowRiverCarver plan) {
        final boolean accepted=plan.addPath(new int[]{20,108},new int[]{64,64});
        assertTrue(plan.getLastRejection(),accepted);
    }

    private static ShallowRiverCarver plan(Dimension d,boolean smooth,ScriptProgress progress) {
        final ShallowRiverCarver plan=new ShallowRiverCarver(d,5,12,1.1,smooth,true,1337,progress);
        plan.enableTerrainPreservation();
        return plan;
    }

    private static Dimension terrain() {
        final Dimension d=TestData.createDimension(new Rectangle(0,0,128,128),100);
        for(int y=0;y<128;y++) for(int x=0;x<128;x++) {
            final float height=Math.abs(y-64)<=6 ? 100 : 100+Math.abs(y-64)/3f+(x%5)*0.125f;
            d.setHeightAt(x,y,height);
            d.setWaterLevelAt(x,y,0);
            d.setTerrainAt(x,y,(x+y)%3==0?Terrain.GRANITE:Terrain.GRASS);
            d.setLayerValueAt(Biome.INSTANCE,x,y,4);
            d.setBitLayerValueAt(Frost.INSTANCE,x,y,x%5==0);
        }
        return d;
    }

    private static Cell[] snapshot(Dimension d) {
        final Cell[] result=new Cell[128*128];
        for(int y=0;y<128;y++) for(int x=0;x<128;x++) result[y*128+x]=new Cell(
                d.getHeightAt(x,y),d.getWaterLevelAt(x,y),d.getTerrainAt(x,y),
                d.getLayerValueAt(Biome.INSTANCE,x,y),d.getBitLayerValueAt(Frost.INSTANCE,x,y));
        return result;
    }
    private record Cell(float height,int water,Terrain terrain,int biome,boolean frost) {}
}
