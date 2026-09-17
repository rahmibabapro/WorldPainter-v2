package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import java.util.*;
import java.util.function.Supplier;

/** Detached, sea-connected basin proposal. Junction elevations are constraints
 * shared by every reach, not a side effect of which branch is carved first. */
final class DrawnRiverJointPlanner {
    record ReachPlan(DrawnRiverGraph.Reach reach,double endWidth,boolean joining,
                     List<TerrainAdjustmentPlan.GradeSample> profile) { }
    record Result(ShallowRiverCarver carver,TerrainAdjustmentPlan terrain,
                  List<RiverSearchResult.Course> courses,List<String> messages,int rejected) { }

    static Result plan(Dimension d,DrawnRiverGraph.Result graph,double depth,boolean smooth,Supplier<ShallowRiverCarver> factory,Runnable check) {
        var messages=new ArrayList<String>();
        var accepted=new ArrayList<ReachPlan>();
        var terrain=new TerrainAdjustmentPlan(TerrainAdjustmentPlan.Mode.DEEP);
        ShallowRiverCarver committed=factory.get();
        int rejected=0;
        for(var basin:graph.basins()) {
            check.run();
            if(basin.downstreamFirst().isEmpty()) {
                messages.add("Boş havza atlandı (çıkış kalemini yalnız açık uçlara koyun).");
                continue;
            }
            var root=basin.outlet()!=null?basin.outlet():basin.downstreamFirst().getFirst().pixels().getLast();
            boolean wetOutlet = d.getWaterLevelAt(root.x(),root.y()) > d.getIntHeightAt(root.x(),root.y());
            Set<Long> edgeKeys=new HashSet<>();
            if(basin.edgeOutlet()&&basin.outlet()!=null)
                edgeKeys.add(TerrainAdjustmentPlan.key(basin.outlet().x(),basin.outlet().y()));
            List<ReachPlan> basinPlans=profiles(d,basin,depth,check);
            var candidateTerrain=new TerrainAdjustmentPlan(TerrainAdjustmentPlan.Mode.DEEP);
            candidateTerrain.setEdgeOutlets(edgeKeys);
            candidateTerrain.merge(terrain);
            for(var rp:basinPlans)candidateTerrain.merge(TerrainAdjustmentPlan.alongProfile(
                    d,TerrainAdjustmentPlan.Mode.DEEP,rp.profile,rp.endWidth,depth,smooth,edgeKeys,check));
            candidateTerrain.resolveBankFloors(d,check);
            var candidate=new ArrayList<>(accepted);candidate.addAll(basinPlans);
            var trial=factory.get();
            if(basin.edgeOutlet()&&basin.outlet()!=null)
                trial.setEdgeOutlet(basin.outlet().x(),basin.outlet().y());
            trial.beginNetworkPlan();
            trial.setPlanningHeightOverlay((x,y)->candidateTerrain.adjustedHeight(x,y,d.getHeightAt(x,y)));
            String failure=null;
            for(var rp:candidate) {
                check.run();
                var pixels=rp.reach.pixels();
                boolean ok=trial.addProfiledPath(pixels.stream().mapToInt(DrawnRiverGraph.Pixel::x).toArray(),
                        pixels.stream().mapToInt(DrawnRiverGraph.Pixel::y).toArray(),rp.reach.width(),rp.endWidth,
                        rp.profile.stream().mapToInt(TerrainAdjustmentPlan.GradeSample::water).toArray(),rp.joining);
                if(!ok){failure="Kol "+pixels.getFirst()+": "+trial.getLastRejection();break;}
            }
            if(failure==null&&!trial.finishNetworkPlan())failure="Tam ağ: "+trial.getLastRejection();
            if(failure==null)for(var rp:candidate) {
                int previous=Integer.MAX_VALUE;
                for(var p:rp.reach.pixels()) {
                    check.run();int water=trial.plannedWaterLevelAt(p.x(),p.y());
                    if(water>previous){failure="Birleşmiş yatakta ters su basamağı: "+p+" ("+previous+" → "+water+")";break;}
                    if(!trial.plannedWetAt(p.x(),p.y())&&d.getWaterLevelAt(p.x(),p.y())<=d.getIntHeightAt(p.x(),p.y())) {
                        failure="Birleşmiş yatakta kuru merkez: "+p;break;
                    }
                    previous=water;
                }
                if(failure!=null)break;
            }
            if(failure!=null) {
                messages.add("Havza ortak doğrulaması: "+failure);
                rejected+=basin.downstreamFirst().size();
            } else {
                committed=trial;accepted=candidate;terrain=candidateTerrain;
                String outletDesc = wetOutlet ? "Denize/göle bağlı havza: " : "Doğal vadi/çıkış havzası: ";
                messages.add(outletDesc+basin.sources()+" kaynak, "+basin.junctions()+" birleşim, "+basinPlans.size()+" parça doğrulandı.");
            }
        }
        var courses=new ArrayList<RiverSearchResult.Course>();
        for(var rp:accepted)courses.add(new RiverSearchResult.Course(rp.reach.pixels().stream()
                .map(p->new RiverSearchResult.Point(p.x(),p.y())).toList(),rp.reach.width(),rp.endWidth,0));
        return new Result(committed,terrain,List.copyOf(courses),List.copyOf(messages),rejected);
    }

    private static List<ReachPlan> profiles(Dimension d,DrawnRiverGraph.Basin basin,Runnable check) {
        return profiles(d, basin, 3.0, check);
    }

    private static List<ReachPlan> profiles(Dimension d,DrawnRiverGraph.Basin basin,double depth,Runnable check) {
        var reaches=basin.downstreamFirst();
        var outgoing=new HashMap<DrawnRiverGraph.Pixel,DrawnRiverGraph.Reach>();
        for(var r:reaches)outgoing.put(r.pixels().getFirst(),r);
        var base=new LinkedHashMap<DrawnRiverGraph.Reach,List<TerrainAdjustmentPlan.GradeSample>>();
        var endWidths=new HashMap<DrawnRiverGraph.Reach,Double>();
        var caps=new HashMap<DrawnRiverGraph.Pixel,Integer>();
        for(var r:reaches) {
            var receiver=outgoing.get(r.pixels().getLast());
            double endWidth=receiver==null?r.width():Math.max(r.width(),receiver.width());
            endWidths.put(r,endWidth);
            var samples=TerrainAdjustmentPlan.gradeSamples(d,
                    r.pixels().stream().mapToInt(DrawnRiverGraph.Pixel::x).toArray(),
                    r.pixels().stream().mapToInt(DrawnRiverGraph.Pixel::y).toArray(),
                    r.width(),endWidth,receiver!=null,check);
            if(receiver==null) {
                int rootWater=d.getWaterLevelAt(r.pixels().getLast().x(),r.pixels().getLast().y());
                int rootHeight=d.getIntHeightAt(r.pixels().getLast().x(),r.pixels().getLast().y());
                int sea=rootWater>rootHeight?rootWater:Math.max(d.getMinHeight(),(int)Math.floor(rootHeight-depth));
                int previous=Integer.MAX_VALUE;
                for(int i=0;i<samples.size();i++) {
                    check.run();var p=samples.get(i);int water=p.water();
                    int radius=(int)Math.ceil(p.width()/2+1);
                    boolean touching=false;
                    for(int dy=-radius;dy<=radius&&!touching;dy++)for(int dx=-radius;dx<=radius;dx++) {
                        if(dx*dx+dy*dy>radius*radius)continue;
                        int x=(int)Math.round(p.x())+dx,y=(int)Math.round(p.y())+dy;
                        if(d.isTilePresent(x>>7,y>>7)&&d.getWaterLevelAt(x,y)==rootWater&&rootWater>d.getIntHeightAt(x,y)){touching=true;break;}
                    }
                    if(touching)water=rootWater;
                    water=Math.max(sea,Math.min(previous,water));previous=water;
                    samples.set(i,new TerrainAdjustmentPlan.GradeSample(p.x(),p.y(),p.nx(),p.ny(),water,p.width()));
                }
            }
            base.put(r,samples);
        }
        // Accumulate the upstream water ceilings once through the directed tree.
        for(int i=reaches.size()-1;i>=0;i--) {
            var r=reaches.get(i);var samples=base.get(r);
            var start=r.pixels().getFirst();var end=r.pixels().getLast();
            caps.merge(start,samples.getFirst().water(),Math::min);
            caps.merge(end,Math.min(caps.get(start),samples.getLast().water()),Math::min);
        }
        var result=new ArrayList<ReachPlan>();
        // Lowering a junction to make a stepped terminal approach feasible must
        // propagate back to every incoming reach and down the receiving reach.
        for(int round=0;round<128;round++) {
            boolean changed=false;result.clear();
            for(var r:reaches) {
                check.run();
                var original=base.get(r);var start=r.pixels().getFirst();var end=r.pixels().getLast();
                boolean joining=outgoing.containsKey(end);
                int startWater=caps.get(start),endWater=caps.get(end);
                if(!joining) {
                    int rootWater=d.getWaterLevelAt(end.x(),end.y());
                    int rootHeight=d.getIntHeightAt(end.x(),end.y());
                    endWater=rootWater>rootHeight?rootWater:Math.max(d.getMinHeight(),(int)Math.floor(rootHeight-depth));
                }
                else if(endWater>startWater){endWater=startWater;caps.put(end,endWater);changed=true;}
                double width=endWidths.get(r);
                int n=original.size();int[] levels=new int[n];double[] distance=new double[n];
                for(int i=0;i<n;i++) {
                    var p=original.get(i);
                    levels[i]=Math.max(endWater,Math.min(startWater,p.water()));
                    if(i>0)distance[i]=distance[i-1]+Math.hypot(p.x()-original.get(i-1).x(),p.y()-original.get(i-1).y());
                }
                // One shared water plane across the junction footprint.
                for(int i=0;i<n;i++) {
                    if(r.contributors()>1 && distance[i]<=r.width()/2+1)levels[i]=startWater;
                    if(joining && distance[n-1]-distance[i]<=width/2+1)levels[i]=endWater;
                }
                levels[n-1]=endWater;
                for(int i=1;i<n;i++)levels[i]=Math.min(levels[i-1],levels[i]);
                RiverWaterProfile.spreadDrops(levels,distance,Math.max(3,width/2+1));
                if(levels[0]<startWater){caps.put(start,levels[0]);changed=true;}
                var samples=new ArrayList<TerrainAdjustmentPlan.GradeSample>(n);
                for(int i=0;i<n;i++) {
                    var p=original.get(i);
                    samples.add(new TerrainAdjustmentPlan.GradeSample(p.x(),p.y(),p.nx(),p.ny(),levels[i],p.width()));
                }
                result.add(new ReachPlan(r,width,joining,List.copyOf(samples)));
            }
            // Thick tributaries can touch before their one-pixel drawing node.
            // Match a later tributary to the earlier, downstream-first sibling
            // at those contacts. Ancestor reaches retain intentional steps.
            for(int later=1;later<result.size();later++) {
                var rp=result.get(later);var samples=base.get(rp.reach);
                int[] ceilings=rp.profile.stream().mapToInt(TerrainAdjustmentPlan.GradeSample::water).toArray();
                for(int earlier=0;earlier<later;earlier++) {
                    var receiver=result.get(earlier);
                    if(ancestor(receiver.reach,rp.reach,outgoing))continue;
                    for(int i=0;i<rp.profile.size();i++) {
                        check.run();var p=rp.profile.get(i);
                        double closest=Double.POSITIVE_INFINITY;Integer receiving=null;int receivingIndex=-1;
                        for(int qi=0;qi<receiver.profile.size();qi++) {
                            var q=receiver.profile.get(qi);
                            double reach=Math.max(1.5,p.width()*.36)+Math.max(1.5,q.width()*.36);
                            if(Math.abs(p.x()-q.x())>reach||Math.abs(p.y()-q.y())>reach)continue;
                            double distance=Math.hypot(p.x()-q.x(),p.y()-q.y());
                            if(distance<=reach&&distance<closest){closest=distance;receiving=q.water();receivingIndex=qi;}
                        }
                        if(receiving!=null) {
                            ceilings[i]=Math.min(ceilings[i],receiving);
                            var receiverBase=base.get(receiver.reach);
                            var q=receiverBase.get(receivingIndex);
                            if(p.water()<q.water()) {
                                receiverBase.set(receivingIndex,new TerrainAdjustmentPlan.GradeSample(q.x(),q.y(),q.nx(),q.ny(),p.water(),q.width()));
                                changed=true;
                            }
                        }
                    }
                }
                for(int i=0;i<samples.size();i++) {
                    var p=samples.get(i);
                    if(ceilings[i]<p.water()) {
                        samples.set(i,new TerrainAdjustmentPlan.GradeSample(p.x(),p.y(),p.nx(),p.ny(),ceilings[i],p.width()));
                        changed=true;
                    }
                }
                int last=ceilings[ceilings.length-1];
                if(last<caps.get(rp.reach.pixels().getLast()))caps.put(rp.reach.pixels().getLast(),last);
            }
            if(!changed)return List.copyOf(result);
        }
        throw new IllegalStateException("Ortak birleşim su profili yakınsamadı; dünya değiştirilmedi.");
    }

    private static boolean ancestor(DrawnRiverGraph.Reach candidate,DrawnRiverGraph.Reach child,
                                    Map<DrawnRiverGraph.Pixel,DrawnRiverGraph.Reach> outgoing) {
        var current=outgoing.get(child.pixels().getLast());
        for(int i=0;current!=null&&i<=outgoing.size();i++) {
            if(current==candidate)return true;
            current=outgoing.get(current.pixels().getLast());
        }
        return false;
    }
}
