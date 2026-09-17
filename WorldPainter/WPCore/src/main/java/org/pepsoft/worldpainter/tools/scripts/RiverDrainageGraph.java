package org.pepsoft.worldpainter.tools.scripts;

import java.util.Arrays;
import java.util.Objects;

/** Immutable analysis graph. An outlet here is an analysis root, not permission to carve. */
public final class RiverDrainageGraph {
    public static final int MAX_CELLS=1_048_576;
    private final int[] downstream, basin, order, strahler;
    private final double[] area;

    public RiverDrainageGraph(int[] receivers,double[] localArea,Runnable cancellation) {
        Objects.requireNonNull(receivers);Objects.requireNonNull(localArea);
        Objects.requireNonNull(cancellation);
        int n=receivers.length;
        if(n>MAX_CELLS||localArea.length!=n)throw new IllegalArgumentException("Invalid drainage grid size");
        downstream=receivers.clone();area=localArea.clone();
        basin=new int[n];order=new int[n];strahler=new int[n];
        int[] indegree=new int[n],maxOrder=new int[n],maxCount=new int[n];
        for(int i=0;i<n;i++) {
            if((i&1023)==0)cancellation.run();
            int next=downstream[i];
            if(next< -1||next>=n||next==i||!Double.isFinite(area[i])||area[i]<0)
                throw new IllegalArgumentException("Invalid drainage cell: "+i);
            if(next>=0)indegree[next]++;
        }
        int head=0,tail=0;
        for(int i=0;i<n;i++)if(indegree[i]==0)order[tail++]=i;
        while(head<tail) {
            if((head&1023)==0)cancellation.run();
            int i=order[head++];
            strahler[i]=Math.max(1,maxOrder[i]+(maxCount[i]>=2?1:0));
            int next=downstream[i];
            if(next<0)continue;
            area[next]+=area[i];
            if(!Double.isFinite(area[next]))throw new IllegalArgumentException("Drainage area overflow");
            if(strahler[i]>maxOrder[next]){maxOrder[next]=strahler[i];maxCount[next]=1;}
            else if(strahler[i]==maxOrder[next])maxCount[next]++;
            if(--indegree[next]==0)order[tail++]=next;
        }
        if(tail!=n)throw new IllegalArgumentException("Drainage graph contains a cycle");
        for(int k=n-1;k>=0;k--) {
            if((k&1023)==0)cancellation.run();
            int i=order[k];basin[i]=downstream[i]<0?i:basin[downstream[i]];
        }
    }
    public int size(){return downstream.length;}
    public int downstream(int cell){return downstream[cell];}
    public int basin(int cell){return basin[cell];}
    public int strahler(int cell){return strahler[cell];}
    /** Contributing area in block squared, never a coarse-cell count. */
    public double contributingArea(int cell){return area[cell];}
    public double width(int cell,double sourceThreshold,double maximumWidth) {
        if(!Double.isFinite(sourceThreshold)||sourceThreshold<=0||!Double.isFinite(maximumWidth)
                ||maximumWidth<3||maximumWidth>64)throw new IllegalArgumentException("Invalid hydraulic geometry");
        return Math.min(maximumWidth,Math.max(3,3*Math.sqrt(area[cell]/sourceThreshold)));
    }
    /** Each shared downstream cell is selected once, even with many headwaters. */
    public boolean[] select(int[] sources,Runnable cancellation) {
        boolean[] selected=new boolean[size()];
        for(int source:sources) {
            if(source<0||source>=size())throw new IllegalArgumentException("Invalid source");
            for(int i=source;i>=0&&!selected[i];i=downstream[i]) {
                cancellation.run();selected[i]=true;
            }
        }
        return selected;
    }
    int[] upstreamFirst(){return Arrays.copyOf(order,order.length);}
}
