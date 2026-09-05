package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.junit.Assume;
import org.pepsoft.worldpainter.*;
import static org.junit.Assert.*;

/** Opt-in 8K fixture avoids adding a large allocation to every ordinary unit-test run. */
public class RiverSearchLargeWorldTest {
    @Test(timeout=150_000) public void twoKValleyBetweenLegacySamples() {check(2048);}
    @Test(timeout=180_000) public void eightKValleyBetweenLegacySamples() {
        Assume.assumeTrue(Boolean.getBoolean("worldpainter.test.largeRiver"));check(8192);
    }
    private void check(int size) {
        var factory=TileFactoryFactory.createFlatTileFactory(733,Terrain.GRASS,-64,320,104,0,false,false);
        var world=new World2(DefaultPlugin.JAVA_ANVIL_1_18,-64,320);
        var d=new Dimension(world,"Large valley",733,factory,Dimension.Anchor.NORMAL_DETAIL);
        int valleyY=size/2+3;
        for(int ty=0;ty<size/128;ty++)for(int tx=0;tx<size/128;tx++) {
            Tile tile=factory.createTile(tx,ty);
            for(int y=0;y<128;y++)if(Math.abs(ty*128+y-valleyY)<=4)
                for(int x=0;x<128;x++)tile.setHeight(x,y,100);
            d.addTile(tile);
        }
        long before=d.getChangeNo();
        var settings=new RiverSearchSession.Settings(1,3,6,.85,true,733,120_000);
        var result=new RiverSearchSession(d,settings,()->false,null,null).search();
        assertTrue(result.diagnostics().toString(),result.canApply());
        assertEquals(8,result.diagnostics().overviewStep());assertEquals((long)size*size,result.diagnostics().sampledCells());
        assertTrue(result.diagnostics().estimatedSearchBytes()<=256L*1024*1024);
        assertEquals(before,d.getChangeNo());
    }
}
