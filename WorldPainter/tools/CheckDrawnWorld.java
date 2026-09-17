import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.*;
import org.pepsoft.worldpainter.plugins.WPPluginManager;
import org.pepsoft.worldpainter.tools.scripts.*;
import java.io.*;
import java.util.*;

/** Reproduction against a saved copy. verify-deep applies/exports IN MEMORY,
 * checks Undo and never saves or overwrites the input world. */
class CheckDrawnWorld {
    public static void main(String[] args) throws Exception {
        Configuration.setInstance(new Configuration());
        WPPluginManager.initialise(UUID.randomUUID(), null);
        WorldIO io=new WorldIO();
        try(var input=new FileInputStream(args[0])) {io.load(input);}
        World2 w=io.getWorld();
        Dimension d=w.getDimension(Dimension.Anchor.NORMAL_DETAIL);
        System.out.println("WORLD="+w.getName()+" TILES="+d.getTiles().size()+" HEIGHT="+d.getMinHeight()+".."+d.getMaxHeight());
        Layer drawing=null;
        for(Layer layer:d.getAllLayers(false)) {
            System.out.println("LAYER="+layer.getName()+" TYPE="+layer.getDataSize());
            if(layer.getName().equals("River Path"))drawing=layer;
        }
        if(drawing==null)throw new IllegalStateException("River Path missing");
        final List<DrawnRiverGraph.Pixel> mask=new ArrayList<>();
        for(var tile:d.getTiles())for(int y=0;y<128;y++)for(int x=0;x<128;x++)
            if(tile.getBitLayerValue(drawing,x,y))mask.add(new DrawnRiverGraph.Pixel(tile.getX()*128+x,tile.getY()*128+y));
        System.out.println("DRAWING_PIXELS="+mask.size());
        double source=args.length>2?Double.parseDouble(args[2]):5;
        double maximum=args.length>3?Double.parseDouble(args[3]):24;
        double depth=args.length>4?Double.parseDouble(args[4]):1.1;
        long start=System.nanoTime();
        var graph=DrawnRiverGraph.build(mask,p->d.getHeightAt(p.x(),p.y()),
                p->d.getWaterLevelAt(p.x(),p.y())>d.getIntHeightAt(p.x(),p.y()),source,maximum,()->{});
        System.out.println("GRAPH basins="+graph.basins().size()+" repairs="+graph.repairs().size()+" ms="+(System.nanoTime()-start)/1_000_000);
        for(var diagnostic:graph.diagnostics())System.out.println("GRAPH_DIAG="+diagnostic.message());
        for(var b:graph.basins())System.out.println("BASIN sources="+b.sources()+" junctions="+b.junctions()+" reaches="+b.downstreamFirst().size());
        for(var b:graph.basins()) {
            var end=b.downstreamFirst().getFirst().pixels().getLast();
            double closest=Double.POSITIVE_INFINITY;DrawnRiverGraph.Pixel target=null;
            for(var other:graph.basins())if(other!=b)for(var r:other.downstreamFirst())for(var p:r.pixels()) {
                double distance=Math.hypot(end.x()-p.x(),end.y()-p.y());
                if(distance<closest){closest=distance;target=p;}
            }
            System.out.println("NEAREST_JOIN "+end+" -> "+target+" distance="+closest);
        }
        if(args.length>1&&args[1].equals("profile")) {
            var estimate=TerrainAdjustmentPlan.class.getDeclaredMethod("requiredExtraCut",Dimension.class,int[].class,int[].class,double.class,double.class,Runnable.class);
            estimate.setAccessible(true);
            for(var b:graph.basins())for(var reach:b.downstreamFirst()) {
                int[] xs=reach.pixels().stream().mapToInt(DrawnRiverGraph.Pixel::x).toArray();
                int[] ys=reach.pixels().stream().mapToInt(DrawnRiverGraph.Pixel::y).toArray();
                double extra=(double)estimate.invoke(null,d,xs,ys,reach.width(),depth,(Runnable)()->{});
                double low=Double.POSITIVE_INFINITY;
                for(var p:reach.pixels())low=Math.min(low,d.getHeightAt(p.x(),p.y()));
                System.out.println("PROFILE="+reach.pixels().getFirst()+" -> "+reach.pixels().getLast()+" width="+reach.width()+" contribution="+reach.contributors()+" minHeight="+low+" endHeight="+d.getHeightAt(xs[xs.length-1],ys[ys.length-1])+" extraCut="+extra);
            }
            return;
        }
        if(args.length>1&&args[1].equals("gradecell")) {
            var planner=Class.forName("org.pepsoft.worldpainter.tools.scripts.DrawnRiverJointPlanner");
            var method=planner.getDeclaredMethod("profiles",Dimension.class,DrawnRiverGraph.Basin.class,Runnable.class);method.setAccessible(true);
            var gradeMethod=TerrainAdjustmentPlan.class.getDeclaredMethod("alongProfile",Dimension.class,TerrainAdjustmentPlan.Mode.class,List.class,double.class,double.class,boolean.class,Runnable.class);gradeMethod.setAccessible(true);
            var wetField=TerrainAdjustmentPlan.class.getDeclaredField("wetLevels");wetField.setAccessible(true);
            for(var basin:graph.basins())if(basin.sources()>1) {
                var plans=(List<?>)method.invoke(null,d,basin,(Runnable)()->{});
                for(var rp:plans) {
                    var rt=rp.getClass().getDeclaredMethod("reach");rt.setAccessible(true);
                    var pt=rp.getClass().getDeclaredMethod("profile");pt.setAccessible(true);
                    var wt=rp.getClass().getDeclaredMethod("endWidth");wt.setAccessible(true);
                    var reach=(DrawnRiverGraph.Reach)rt.invoke(rp);
                    var samples=(List<?>)pt.invoke(rp);
                    var grade=(TerrainAdjustmentPlan)gradeMethod.invoke(null,d,TerrainAdjustmentPlan.Mode.DEEP,samples,(double)wt.invoke(rp),depth,true,(Runnable)()->{});
                    System.out.println("GRADE_CELL="+reach.pixels().getFirst()+" width="+reach.width()+"->"+wt.invoke(rp)+" at18,-11="+grade.adjustedHeight(18,-11,d.getHeightAt(18,-11))+" wetLevel="+((Map<?,?>)wetField.get(grade)).get(TerrainAdjustmentPlan.key(18,-11)));
                }
            }
            return;
        }
        if(args.length>1&&args[1].equals("grades")) {
            for(var b:graph.basins()) {
                var reach=b.downstreamFirst().get(0);
                int[] xs=reach.pixels().stream().mapToInt(DrawnRiverGraph.Pixel::x).toArray();
                int[] ys=reach.pixels().stream().mapToInt(DrawnRiverGraph.Pixel::y).toArray();
                var grade=TerrainAdjustmentPlan.alongCentreline(d,TerrainAdjustmentPlan.Mode.STRONG,xs,ys,reach.width(),6,()->{});
                var c=new ShallowRiverCarver(d,reach.width(),reach.width(),depth,true,true,42,null);
                c.enableTerrainPreservation();c.setLowerInteriorDirt(true);
                c.setPlanningHeightOverlay((x,y)->grade.adjustedHeight(x,y,d.getHeightAt(x,y)));
                boolean ok=c.addPath(xs,ys);
                System.out.println("ROOT="+reach.pixels().getFirst()+" -> "+reach.pixels().getLast()+" width="+reach.width()+" GRADE="+grade.summary()+" OK="+ok+" ERROR="+c.getLastRejection());
            }
            return;
        }
        if(args.length<2||args[1].equals("inspect"))return;
        if(args[1].equals("inspect-joined")) {
            var s=new DrawnRiverSession(d,drawing,source,maximum,depth,true,()->false);
            var deadline=DrawnRiverSession.class.getDeclaredField("deadline");deadline.setAccessible(true);deadline.setLong(s,System.nanoTime()+300_000_000_000L);
            var join=DrawnRiverSession.class.getDeclaredMethod("connectCloseDrawingEnds",DrawnRiverGraph.Result.class,List.class);join.setAccessible(true);
            graph=(DrawnRiverGraph.Result)join.invoke(s,graph,new ArrayList<>(mask));
            for(var b:graph.basins())for(var r:b.downstreamFirst()) {
                double length=0;
                for(int i=1;i<r.pixels().size();i++)length+=Math.hypot(r.pixels().get(i).x()-r.pixels().get(i-1).x(),r.pixels().get(i).y()-r.pixels().get(i-1).y());
                System.out.println("JOINED="+r.pixels().getFirst()+" -> "+r.pixels().getLast()+" n="+r.pixels().size()+" length="+length+" contributors="+r.contributors()+" width="+r.width());
                if(length<30)System.out.println("PIXELS="+r.pixels());
            }
            return;
        }
        long revision=d.getChangeNo();
        System.out.println("SEARCH_START mode="+args[1]+" sourceWidth="+source+" maxWidth="+maximum+" depth="+depth);
        var session=new DrawnRiverSession(d,drawing,source,maximum,depth,true,()->false);
        var result=(args[1].equals("prepare")||args[1].equals("verify-prepare"))?session.prepare()
                :(args[1].equals("deep")||args[1].equals("verify-deep"))?session.searchDeepValley():session.search(args[1].equals("strong"));
        System.out.println("IDENTITY="+result.identity());
        System.out.println("RESULT canApply="+result.canApply()+" courses="+result.courses().size()
                +" sources="+result.sources()+" junctions="+result.junctions()
                +" skipped="+result.skippedGroups()+" rejected="+result.rejected()
                +" stage="+result.stageReached()+" maxExtraCut="+result.maxExtraCut()
                +" seconds="+(System.nanoTime()-start)/1_000_000_000.0);
        for(String message:result.messages())System.out.println("MESSAGE="+message);
        for(String message:result.blockers())System.out.println("BLOCKER="+message);
        boolean previewUnchanged=revision==d.getChangeNo();
        int exportedSamples=0;boolean undoVerified=false;
        System.out.println("PREVIEW_UNCHANGED="+previewUnchanged);
        if(args[1].equals("verify-deep")||args[1].equals("verify-prepare")) {
            if(!result.canApply())throw new AssertionError("No verified network");
            if(!previewUnchanged)throw new AssertionError("Preview changed the world");
            long before=terrainHash(d);
            var allowed=new HashSet<Long>(session.terrainAdjustmentPlan().cells());
            for(var p:result.cells())allowed.add(TerrainAdjustmentPlan.key(p.x(),p.y()));
            long outsideBefore=outsideHash(d,allowed);
            var sea=new HashMap<Long,Integer>();
            for(var tile:d.getTiles())for(int y=0;y<128;y++)for(int x=0;x<128;x++)
                if(tile.getWaterLevel(x,y)>tile.getIntHeight(x,y))
                    sea.put(TerrainAdjustmentPlan.key(tile.getX()*128+x,tile.getY()*128+y),tile.getWaterLevel(x,y));
            d.registerUndoManager(new org.pepsoft.util.undo.UndoManager(8));
            try {
                session.apply();
                if(outsideBefore!=outsideHash(d,allowed))throw new AssertionError("Change outside preview footprint");
                for(var e:sea.entrySet())if(d.getWaterLevelAt((int)(e.getKey()>>32),(int)(long)e.getKey())!=e.getValue())
                    throw new AssertionError("Existing sea/lake level changed");
                System.out.println("FOOTPRINT_AND_EXISTING_WATER_VERIFIED=true");
                var factory=new org.pepsoft.worldpainter.exporting.WorldPainterChunkFactory(d,Map.of(),w.getPlatform(),d.getMaxHeight());
                var chunks=new HashMap<Long,org.pepsoft.minecraft.Chunk>();
                int count=0;
                for(var course:result.courses()) {
                    int previous=Integer.MAX_VALUE;
                    for(var p:course.centreline()) {
                        int water=d.getWaterLevelAt(p.x(),p.y());
                        if(water>previous)throw new AssertionError("Uphill water at "+p);
                        previous=water;
                        long key=((long)(p.x()>>4)<<32)|((p.y()>>4)&0xffffffffL);
                        var chunk=chunks.computeIfAbsent(key,k->factory.createChunk(p.x()>>4,p.y()>>4).chunk);
                        var material=chunk.getMaterial(p.x()&15,water,p.y()&15);
                        if(!material.name.equals("minecraft:water")&&!material.containsWater())
                            throw new AssertionError("Missing exported water at "+p+" Y="+water+": "+material);
                        count++;
                    }
                }
                System.out.println("EXPORT_VERIFIED waterSamples="+count+" chunks="+chunks.size());
                exportedSamples=count;
                if(!d.undoChanges())throw new AssertionError("Undo missing");
                if(before!=terrainHash(d))throw new AssertionError("Undo did not restore terrain/water/materials");
                System.out.println("UNDO_VERIFIED=true");
                undoVerified=true;
            } finally {d.unregisterUndoManager();}
        }
        if(args.length>5) {
            var report=new StringBuilder();
            report.append("world=").append(w.getName()).append("\ninputPixels=").append(mask.size())
                    .append("\nmode=").append(args[1]).append("\nsourceWidth=").append(source)
                    .append("\nmaximumWidth=").append(maximum).append("\ndepth=").append(depth)
                    .append("\nidentity=").append(result.identity())
                    .append("\ncanApply=").append(result.canApply()).append("\ncourses=").append(result.courses().size())
                    .append("\nsources=").append(result.sources()).append("\njunctions=").append(result.junctions())
                    .append("\nskipped=").append(result.skippedGroups()).append("\nmaxExtraCut=").append(result.maxExtraCut())
                    .append("\nstage=").append(result.stageReached())
                    .append("\nrejected=").append(result.rejected()).append("\npreviewUnchanged=").append(previewUnchanged)
                    .append("\nexportedWaterSamples=").append(exportedSamples).append("\nundoVerified=").append(undoVerified).append('\n');
            if(exportedSamples>0)report.append("outsideFootprintUnchanged=true\nexistingWaterLevelsUnchanged=true\n");
            for(String message:result.messages())report.append(message).append('\n');
            for(String blocker:result.blockers())report.append("BLOCKER: ").append(blocker).append('\n');
            java.nio.file.Files.writeString(java.nio.file.Path.of(args[5]),report,
                    java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
        }
    }

    private static long terrainHash(Dimension d) {
        long hash=17;
        var tiles=new ArrayList<>(d.getTiles());
        tiles.sort(Comparator.comparingInt(Tile::getX).thenComparingInt(Tile::getY));
        for(var tile:tiles)for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
            hash=hash*31+Float.floatToIntBits(tile.getHeight(x,y));
            hash=hash*31+tile.getWaterLevel(x,y);
            hash=hash*31+tile.getTerrain(x,y).hashCode();
            hash=hash*31+(tile.getBitLayerValue(RiverSurfaceDetail.INSTANCE,x,y)?1:0);
            hash=hash*31+(tile.getBitLayerValue(RiverWaterlineDetail.INSTANCE,x,y)?1:0);
        }
        return hash;
    }

    private static long outsideHash(Dimension d,Set<Long> allowed) {
        long hash=17;
        var tiles=new ArrayList<>(d.getTiles());tiles.sort(Comparator.comparingInt(Tile::getX).thenComparingInt(Tile::getY));
        for(var tile:tiles)for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
            if(allowed.contains(TerrainAdjustmentPlan.key(tile.getX()*128+x,tile.getY()*128+y)))continue;
            hash=hash*31+Float.floatToIntBits(tile.getHeight(x,y));
            hash=hash*31+tile.getWaterLevel(x,y);hash=hash*31+tile.getTerrain(x,y).hashCode();
            hash=hash*31+(tile.getBitLayerValue(RiverSurfaceDetail.INSTANCE,x,y)?1:0);
            hash=hash*31+(tile.getBitLayerValue(RiverWaterlineDetail.INSTANCE,x,y)?1:0);
        }
        return hash;
    }
}
