package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class RiverDrainageGraphTest {
    @Test public void yNetworkHasOneSharedTrunkAndNoDoubleCounting() {
        var g=new RiverDrainageGraph(new int[]{2,2,3,-1},new double[]{64,64,64,64},()->{});
        assertEquals(192,g.contributingArea(2),0);assertEquals(256,g.contributingArea(3),0);
        assertEquals(2,g.strahler(2));assertEquals(2,g.strahler(3));
        assertArrayEquals(new boolean[]{true,true,true,true},g.select(new int[]{0,1,0},()->{}));
        for(int i=0;i<4;i++)assertEquals(3,g.basin(i));
        assertTrue(g.width(3,64,24)>g.width(0,64,24));
    }
    @Test public void eightHeadwatersFormThirdLevelConfluence() {
        var g=new RiverDrainageGraph(new int[]{8,8,9,9,10,10,11,11,12,12,13,13,14,14,-1},
                new double[]{1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},()->{});
        assertEquals(4,g.strahler(14));assertEquals(15,g.contributingArea(14),0);
    }
    @Test public void separateBasinsStaySeparateAndInputsAreCopied() {
        int[] receivers={1,-1,3,-1};double[] local={64,64,64,64};
        var g=new RiverDrainageGraph(receivers,local,()->{});
        receivers[0]=3;local[0]=10000;
        assertEquals(1,g.basin(0));assertEquals(3,g.basin(2));
        assertEquals(128,g.contributingArea(1),0);
        assertArrayEquals(new boolean[]{true,true,false,false},g.select(new int[]{0},()->{}));
    }
    @Test public void topologyStoresSharedTrunkExactlyOnce() {
        var g=new RiverDrainageGraph(new int[]{2,2,3,4,-1},new double[]{1,1,1,1,1},()->{});
        var network=RiverNetworkTopology.build(g,new int[]{0,1},()->{});
        assertEquals(4,network.nodes().size());assertEquals(3,network.reaches().size());
        assertEquals(1,network.reaches().stream().filter(r->r.cells().contains(3)).count());
        assertEquals(1,network.nodes().stream().filter(n->n.kind()==RiverNetworkTopology.Kind.JUNCTION).count());
        var withoutBranch=RiverNetworkTopology.build(g,new int[]{0},()->{});
        assertEquals(1,withoutBranch.reaches().size());
        assertEquals(java.util.List.of(0,2,3,4),withoutBranch.reaches().get(0).cells());
        assertEquals(5,g.contributingArea(4),0);
        assertThrows(UnsupportedOperationException.class,()->network.nodes().clear());
        assertThrows(UnsupportedOperationException.class,()->network.reaches().get(0).cells().clear());
    }
    @Test public void widthDependsOnAreaNotSamplingResolution() {
        var fine=new RiverDrainageGraph(new int[]{1,2,3,-1},new double[]{16,16,16,16},()->{});
        var coarse=new RiverDrainageGraph(new int[]{-1},new double[]{64},()->{});
        assertEquals(fine.width(3,16,24),coarse.width(0,16,24),0);
        assertEquals(24,coarse.width(0,.01,24),0);
    }
    @Test public void invalidGraphsAndCancellationCannotBecomeValidNetworks() {
        assertThrows(IllegalArgumentException.class,()->new RiverDrainageGraph(new int[]{1,0},new double[]{1,1},()->{}));
        assertThrows(IllegalArgumentException.class,()->new RiverDrainageGraph(new int[]{2},new double[]{1},()->{}));
        assertThrows(IllegalArgumentException.class,()->new RiverDrainageGraph(new int[]{-1},new double[]{Double.NaN},()->{}));
        assertThrows(CancellationException.class,()->new RiverDrainageGraph(new int[]{-1},new double[]{1},()->{throw new CancellationException();}));
    }
}
