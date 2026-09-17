package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.tools.scripts.*;
import java.util.List;
import static org.junit.Assert.*;

public class RiverSearchDialogTest {
    @Test public void cancelledPreviewStopsBeforeReadingTerrain() {
        assertThrows(java.util.concurrent.CancellationException.class,
                ()->RiverSearchDialog.renderPreview(null,result(RiverSearchResult.Status.FOUND),()->true));
    }
    @Test public void cancellationDuringRasterisationLeavesWorldUntouched() {
        var world=new World2(DefaultPlugin.JAVA_ANVIL_1_18,-64,320);
        var factory=TileFactoryFactory.createFlatTileFactory(1,Terrain.GRASS,-64,320,100,0,false,false);
        var d=new Dimension(world,"Preview",1,factory,Dimension.Anchor.NORMAL_DETAIL);
        d.addTile(factory.createTile(0,0));long before=d.getChangeNo();
        var r=new RiverSearchResult(RiverSearchResult.Status.FOUND,1,List.of(),
                List.of(new ShallowRiverCarver.PreviewCell(64,64,100,99,100)),
                new RiverSearchResult.Diagnostics(8,16384,1,0,10,1024,""));
        var checks=new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(java.util.concurrent.CancellationException.class,
                ()->RiverSearchDialog.renderPreview(d,r,()->checks.incrementAndGet()>12));
        assertEquals(13,checks.get());assertEquals(before,d.getChangeNo());
    }
    @Test public void budgetAndNoOutletHaveDifferentMessages() {
        var a=result(RiverSearchResult.Status.BUDGET_EXHAUSTED);
        var b=result(RiverSearchResult.Status.NO_FEASIBLE_OUTLET);
        assertTrue(RiverSearchDialog.statusText(a).contains("tüm alternatifler"));
        assertTrue(RiverSearchDialog.statusText(b).contains("İncelenen adaylarda"));
        assertTrue(RiverSearchDialog.statusText(a).contains("deniz Y="));
        assertTrue(RiverSearchDialog.statusText(a).contains("göl hücre="));
        assertNotEquals(RiverSearchDialog.statusText(a),RiverSearchDialog.statusText(b));
        assertTrue(RiverSearchDialog.seaGoalLabel(0).contains("mevcut su"));
        assertEquals("deniz Y=62",RiverSearchDialog.seaGoalLabel(62));
    }
    @Test public void previewRendererDoesNotWriteTerrainOrLayers() {
        var world=new World2(DefaultPlugin.JAVA_ANVIL_1_18,-64,320);
        var factory=TileFactoryFactory.createFlatTileFactory(1,Terrain.GRASS,-64,320,100,0,false,false);
        var d=new Dimension(world,"Preview",1,factory,Dimension.Anchor.NORMAL_DETAIL);
        d.addTile(factory.createTile(0,0));long before=d.getChangeNo();
        var result=new RiverSearchResult(RiverSearchResult.Status.FOUND,1,List.of(),
                List.of(new ShallowRiverCarver.PreviewCell(64,64,100,99,100)),
                new RiverSearchResult.Diagnostics(8,16384,1,0,10,1024,""));
        var image=RiverSearchDialog.renderPreview(d,result);
        assertEquals(640,image.getWidth());assertEquals(480,image.getHeight());
        assertEquals(before,d.getChangeNo());assertEquals(100,d.getHeightAt(64,64),0);
        assertEquals(0,d.getWaterLevelAt(64,64));
    }
    private RiverSearchResult result(RiverSearchResult.Status status) {
        return new RiverSearchResult(status,1,List.of(),List.of(),new RiverSearchResult.Diagnostics(8,10,2,2,120000,1024,""));
    }
}
