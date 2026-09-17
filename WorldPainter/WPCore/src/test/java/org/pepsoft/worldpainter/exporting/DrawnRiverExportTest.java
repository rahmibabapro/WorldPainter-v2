package org.pepsoft.worldpainter.exporting;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.CustomAnnotationLayer;
import org.pepsoft.worldpainter.tools.scripts.DrawnRiverSession;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.util.undo.UndoManager;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.*;
import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.MC_WATER;

public class DrawnRiverExportTest {
    @Test public void gradedDrawnRidgeExportsWaterInsteadOfTheBlockingRidge() {
        if(Configuration.getInstance()==null)Configuration.setInstance(new Configuration());ExportTestSupport.ensureReady();
        var d=TestData.createDimension(new Rectangle(0,0,128,128),100);
        d.setSurfaceSmoothing(Dimension.SurfaceSmoothing.NONE);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
            d.setHeightAt(x,y,x>=50&&x<=70?104:100);
            d.setWaterLevelAt(x,y,0);d.setTerrainAt(x,y,Terrain.GRASS);
        }
        var layer=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
        for(int x=20;x<=100;x++)d.setBitLayerValueAt(layer,x,64,true);
        d.registerUndoManager(new UndoManager(8));
        try {
            var session=new DrawnRiverSession(d,layer,5,5,1.1,true,()->false);
            var preview=session.search(true);
            assertTrue(preview.messages().toString(),preview.canApply());
            session.apply();
            var factory=new WorldPainterChunkFactory(d,Collections.emptyMap(),TestData.PLATFORM,TestData.MAX_HEIGHT);
            Map<Integer,Chunk> chunks=new HashMap<>();
            for(int x=22;x<=98;x++) {
                var chunk=chunks.computeIfAbsent(x>>4,c->factory.createChunk(c,4).chunk);
                assertEquals("Water missing at "+x,MC_WATER,chunk.getMaterial(x&15,100,0).name);
            }
            assertEquals(104,d.getHeightAt(60,110),0);
            assertEquals(0,d.getWaterLevelAt(60,110));
        } finally {d.unregisterUndoManager();}
    }
    @Test public void yJunctionExportsContinuousWaterAcrossFourChunksWithoutChangingDryTerrain(){
        if(Configuration.getInstance()==null)Configuration.setInstance(new Configuration());ExportTestSupport.ensureReady();
        var d=TestData.createDimension(new Rectangle(0,0,128,128),100);
        d.setSurfaceSmoothing(Dimension.SurfaceSmoothing.NONE);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++){d.setHeightAt(x,y,100+(127-y)*.001f);d.setWaterLevelAt(x,y,0);d.setTerrainAt(x,y,Terrain.GRASS);}
        var layer=new CustomAnnotationLayer("River Path","drawing",Color.BLUE);
        for(int i=0;i<=30;i++){d.setBitLayerValueAt(layer,64-i,64-i,true);d.setBitLayerValueAt(layer,64+i,64-i,true);d.setBitLayerValueAt(layer,64,64+i,true);}
        d.registerUndoManager(new UndoManager(10));
        try {
            var session=new DrawnRiverSession(d,layer,3,12,.85,true,()->false);var preview=session.search();
            assertEquals(preview.messages().toString(),3,preview.courses().size());session.apply();
            long revision=d.getChangeNo();var factory=new WorldPainterChunkFactory(d,Collections.emptyMap(),TestData.PLATFORM,TestData.MAX_HEIGHT);
            Map<Long,Chunk> chunks=new HashMap<>();
            for(var course:preview.courses())for(var p:course.centreline()) {
                long key=((long)(p.x()>>4)<<32)^((p.y()>>4)&0xffffffffL);
                var chunk=chunks.computeIfAbsent(key,k->factory.createChunk(p.x()>>4,p.y()>>4).chunk);
                assertEquals("Dry centre at "+p,MC_WATER,chunk.getMaterial(p.x()&15,100,p.y()&15).name);
            }
            for(int y=62;y<=66;y++)for(int x=63;x<=65;x++) {
                final int xx=x,yy=y;long key=((long)(x>>4)<<32)^((y>>4)&0xffffffffL);
                var chunk=chunks.computeIfAbsent(key,k->factory.createChunk(xx>>4,yy>>4).chunk);
                assertEquals("Junction gap",MC_WATER,chunk.getMaterial(x&15,100,y&15).name);
            }
            assertEquals(100+107*.001f,d.getHeightAt(20,20),1.0/256);assertEquals(Terrain.GRASS,d.getTerrainAt(20,20));
            assertEquals(revision,d.getChangeNo());
        }finally{d.unregisterUndoManager();}
    }
}
