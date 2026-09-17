package org.pepsoft.worldpainter.tools.scripts;

import java.util.*;

/** Detached source/junction/outlet topology. It is NOT a validated terrain edit plan. */
public record RiverNetworkTopology(List<Node> nodes,List<Reach> reaches) {
    public RiverNetworkTopology {nodes=List.copyOf(nodes);reaches=List.copyOf(reaches);}
    public enum Kind { SOURCE, JUNCTION, OUTLET }
    public record Node(int cell,int basin,Kind kind) { }
    public record Reach(int from,int to,int basin,List<Integer> cells) {
        public Reach {cells=List.copyOf(cells);}
    }
    public static RiverNetworkTopology build(RiverDrainageGraph graph,int[] sources,Runnable check) {
        boolean[] selected=graph.select(sources,check);
        int[] incoming=new int[graph.size()];
        for(int i=0;i<graph.size();i++) {
            if((i&1023)==0)check.run();
            if(selected[i]&&graph.downstream(i)>=0)incoming[graph.downstream(i)]++;
        }
        List<Node> nodes=new ArrayList<>(); List<Reach> reaches=new ArrayList<>();
        for(int i=0;i<graph.size();i++) {
            if((i&1023)==0)check.run();
            if(!selected[i])continue;
            if(graph.downstream(i)<0)nodes.add(new Node(i,graph.basin(i),Kind.OUTLET));
            else if(incoming[i]==0)nodes.add(new Node(i,graph.basin(i),Kind.SOURCE));
            else if(incoming[i]>1)nodes.add(new Node(i,graph.basin(i),Kind.JUNCTION));
        }
        for(Node node:nodes) {
            if(node.kind==Kind.OUTLET)continue;
            List<Integer> cells=new ArrayList<>();cells.add(node.cell);
            int next=graph.downstream(node.cell);
            while(true) {
                check.run();cells.add(next);
                if(graph.downstream(next)<0||incoming[next]!=1)break;
                next=graph.downstream(next);
            }
            reaches.add(new Reach(node.cell,next,node.basin,cells));
        }
        return new RiverNetworkTopology(nodes,reaches);
    }
}
