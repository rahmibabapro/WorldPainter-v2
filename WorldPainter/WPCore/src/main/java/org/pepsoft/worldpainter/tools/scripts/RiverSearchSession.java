package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.tools.scripts.RiverSearchResult.*;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** One bounded read-only search followed by an explicit, revision-checked apply. */
public final class RiverSearchSession {
    public static final long DEFAULT_BUDGET_MILLIS = 120_000;
    public record Settings(int count, double startWidth, double endWidth, double depth,
                           boolean smoothBanks, long seed, long budgetMillis) {
        public Settings {
            if (count<1||count>12||!Double.isFinite(startWidth)||!Double.isFinite(endWidth)
                    ||!Double.isFinite(depth)||startWidth<2||endWidth<startWidth||endWidth>64
                    ||depth<.65||depth>5||budgetMillis<1||budgetMillis>600_000)
                throw new IllegalArgumentException("Invalid river search settings");
        }
    }
    @FunctionalInterface public interface Listener {void update(double fraction,String stage);}
    private final Dimension dimension;
    private final World2 world;
    private final long revision, worldRevision;
    private final Settings settings;
    private final BooleanSupplier cancelled;
    private final Listener listener;
    private final BiPredicate<Integer,Integer> avoid;
    private final LongSupplier clock;
    private final Control control = new Control();
    private RiverTerrainSurvey survey;
    private ShallowRiverCarver plan;
    private RiverSearchResult result;
    private long started, notified, estimatedBytes;
    private boolean searching, ran, applied;
    private int candidates, rejected, expandedNodes, edgeSamples;
    private String reason="";
    private final List<Course> accepted=new ArrayList<>();

    public RiverSearchSession(Dimension dimension,Settings settings,BooleanSupplier cancelled,
                              Listener listener,BiPredicate<Integer,Integer> avoid) {
        this(dimension,settings,cancelled,listener,avoid,System::nanoTime);
    }
    RiverSearchSession(Dimension dimension,Settings settings,BooleanSupplier cancelled,
                       Listener listener,BiPredicate<Integer,Integer> avoid,LongSupplier clock) {
        this.dimension=Objects.requireNonNull(dimension); this.world=dimension.getWorld();
        this.settings=Objects.requireNonNull(settings); this.cancelled=cancelled;
        this.listener=listener; this.avoid=avoid; this.clock=clock;
        revision=dimension.getChangeNo(); worldRevision=world.getChangeNo();
    }
    public boolean isStale() {return dimension.getWorld()!=world || dimension.getChangeNo()!=revision || world.getChangeNo()!=worldRevision;}

    public RiverSearchResult search() {
        if(ran) throw new IllegalStateException("Search already run");
        ran=true; searching=true; started=clock.getAsLong(); notified=started;
        Status status;
        try {
            control.checkForCancel();
            survey=new RiverTerrainSurvey(dimension,control::checkForCancel,avoid);
            survey.scan();
            plan=newPlan();
            List<List<Point>> paths=drainage();
            List<Course> validated=new ArrayList<>();
            for(List<Point> path:paths) {
                control.checkForCancel(); candidates++;
                if(nearExisting(path,validated)) continue;
                ShallowRiverCarver probe=validate(path);
                if(probe==null) {
                    for(int width:new int[]{16,32,64}) {
                        List<Point> refined=refine(path,width);
                        if(refined==null) continue;
                        probe=validate(refined);
                        if(probe!=null) {path=refined;break;}
                    }
                }
                if(probe!=null) {
                    double cost=0;
                    for(var cell:probe.previewCells()) cost+=Math.max(0,cell.originalHeight()-cell.bedHeight());
                    validated.add(new Course(path,settings.startWidth,settings.endWidth,cost));
                    // Keep a validated incumbent before further expensive work.
                    commitBest(validated);
                }
            }
            status=accepted.size()==settings.count ? Status.FOUND
                    : accepted.isEmpty()?Status.NO_FEASIBLE_OUTLET:Status.PARTIAL;
            if(paths.size()==MAX_CANDIDATES && accepted.size()<settings.count) {
                status=Status.BUDGET_EXHAUSTED; reason="Aday sınırına ulaşıldı; bütün vadiler doğrulanamadı.";
            }
        } catch(Stopped stop) {status=stop.status;reason=stop.getMessage();}
        catch(IllegalArgumentException invalid) {status=Status.NO_FEASIBLE_OUTLET;reason=invalid.getMessage();}
        finally {searching=false;}
        if(isStale()) status=Status.STALE_WORLD;
        List<ShallowRiverCarver.PreviewCell> cells=plan==null?List.of():plan.previewCells();
        result=new RiverSearchResult(status,settings.count,accepted,cells,
                new Diagnostics(survey==null?0:survey.step,survey==null?0:survey.sampled,candidates,rejected,
                        elapsedMillis(),estimatedBytes,reason));
        LOGGER.info("River search V2: status={}, routes={}/{}, diagnostics={}",status,accepted.size(),settings.count,result.diagnostics());
        return result;
    }

    /** One undo frame; no writes are permitted before this explicit call. */
    public ShallowRiverCarver.Result apply() {
        if(result==null||!result.canApply()||applied) throw new IllegalStateException("Uygulanabilir nehir önizlemesi yok.");
        if(isStale()) throw new IllegalStateException("Dünya değişti; yeniden rota arayın.");
        if(!dimension.isUndoAvailable()) throw new IllegalStateException("Nehir uygulamadan önce Undo etkin olmalı.");
        // Search revision checks are no longer valid once our own writes begin.
        control.applying=true;
        boolean ownsEvents=!dimension.isEventsInhibited();
        dimension.rememberChanges();
        if(ownsEvents) dimension.setEventsInhibited(true);
        try {
            if(!plan.isFootprintAllowed(avoid)) throw new IllegalStateException("Nehir korunan alana giriyor.");
            var appliedResult=plan.apply(); applied=true; return appliedResult;
        } finally {
            dimension.armSavePoint();
            if(ownsEvents) dimension.setEventsInhibited(false);
        }
    }

    private ShallowRiverCarver newPlan() {
        var p=new ShallowRiverCarver(dimension,settings.startWidth,settings.endWidth,settings.depth,
                settings.smoothBanks,true,settings.seed,control);
        p.enableTerrainPreservation(); return p;
    }
    private ShallowRiverCarver validate(List<Point> path) {
        control.checkForCancel();
        var p=newPlan();
        int[] x=path.stream().mapToInt(Point::x).toArray(),y=path.stream().mapToInt(Point::y).toArray();
        if(p.addPath(x,y)&&p.isFootprintAllowed(avoid)) return p;
        rejected++;reason=path.get(0)+" → "+path.get(path.size()-1)+": "+p.getLastRejection();return null;
    }
    private void commitBest(List<Course> choices) {
        double preferred=Math.min(512,Math.max(64,Math.max((long)survey.maxX-survey.minX,(long)survey.maxY-survey.minY)/4.0));
        boolean hasLongCourse=choices.stream().anyMatch(c->length(c.centreline())>=preferred);
        // Compare excavation per block of river, not total volume: total
        // volume would always reward the shortest coastal fragment.
        choices.sort(Comparator.comparingDouble((Course c)->Math.rint(c.excavation()/Math.max(1,length(c.centreline()))*16)/16)
                .thenComparing(Comparator.comparingDouble((Course c)->length(c.centreline())).reversed()));
        ShallowRiverCarver next=newPlan(); List<Course> selected=new ArrayList<>();
        for(Course c:choices) {
            control.checkForCancel();
            if(selected.size()==settings.count) break;
            if(hasLongCourse&&length(c.centreline())<preferred)continue;
            if(nearExisting(c.centreline(),selected)) continue;
            int[] x=c.centreline().stream().mapToInt(Point::x).toArray(),y=c.centreline().stream().mapToInt(Point::y).toArray();
            if(next.addPath(x,y)&&next.isFootprintAllowed(avoid)) selected.add(c);
        }
        // Swap only complete validated states; a timeout cannot leak half a new plan.
        plan=next;accepted.clear();accepted.addAll(selected);
    }
    private boolean nearExisting(List<Point> path,List<Course> existing) {
        Point source=path.get(0);
        for(Course c:existing) {
            if(distance(source,c.centreline().get(0))<settings.endWidth*4) return true;
            int shared=0;
            for(int i=0;i<path.size();i+=4) if(corridorDistance(path.get(i),c.centreline())<settings.endWidth) shared++;
            if(shared>Math.max(1,(path.size()+3)/4)*.75) return true;
        }
        return false;
    }

    /** Priority-flood on analysis arrays only; uphill spill paths still fail real terrain validation. */
    private List<List<Point>> drainage() {
        int n=survey.heights.length;
        float[] filled=survey.heights.clone(); int[] parent=new int[n],order=new int[n],accum=new int[n];
        Arrays.fill(parent,-1); boolean[] seen=new boolean[n];
        PriorityQueue<Integer> heap=new PriorityQueue<>(Comparator.<Integer>comparingDouble(i->filled[i]).thenComparingInt(i->i));
        int inset=(int)Math.ceil(settings.endWidth/2)+3;
        for(int i=0;i<n;i++) {
            if((i&1023)==0) control.checkForCancel();
            if(!Float.isFinite(filled[i])) continue;
            double edge=survey.edge(survey.xs[i],survey.ys[i]);
            if(survey.wet[i]||(edge>=inset&&edge<inset+survey.step
                    && clearancePenalty(new Point(survey.xs[i],survey.ys[i]),settings.endWidth/2)<.01)) {seen[i]=true;heap.add(i);}
        }
        // Conservative bookkeeping including boxed heap and bounded fine searches/plans.
        estimatedBytes=survey.estimatedBytes()+n*64L+128L*1024*1024;
        if(estimatedBytes>256L*1024*1024) throw new Stopped(Status.BUDGET_EXHAUSTED,"Arama bellek bütçesine ulaşıldı.");
        int count=0;
        while(!heap.isEmpty()) {
            control.checkForCancel(); int i=heap.remove(); order[count++]=i;accum[i]=1;
            for(int[] d:RiverTerrainSurvey.DIRECTIONS) {
                int j=survey.neighbour(i,d[0],d[1]); if(j<0||seen[j])continue;
                seen[j]=true;parent[j]=i;filled[j]=Math.max(filled[i],filled[j]);heap.add(j);
            }
        }
        for(int k=count-1;k>=0;k--) {int i=order[k];if(parent[i]>=0)accum[parent[i]]+=accum[i];}
        double[] lengths=new double[n];
        for(int k=0;k<count;k++) {
            int i=order[k],p=parent[i];
            if(p>=0)lengths[i]=lengths[p]+Math.hypot(survey.xs[i]-survey.xs[p],survey.ys[i]-survey.ys[p]);
        }
        // Score supported headwaters, not a large lowland catchment alone.
        Comparator<Integer> rank=Comparator.<Integer>comparingDouble(i->lengths[i]*Math.log1p(accum[i])*(1+8*valleySupport(i)))
                .thenComparingLong(i->tie(i));
        PriorityQueue<Integer> best=new PriorityQueue<>(rank);
        for(int i=0;i<n;i++) {
            if((i&1023)==0)control.checkForCancel();
            if(!seen[i]||survey.wet[i]||lengths[i]<64||survey.edge(survey.xs[i],survey.ys[i])<inset+survey.step*2)continue;
            if(clearancePenalty(new Point(survey.xs[i],survey.ys[i]),settings.startWidth/2)>.01)continue;
            best.add(i);if(best.size()>MAX_CANDIDATES*8)best.remove();
        }
        List<Integer> sources=new ArrayList<>(best);sources.sort(rank.reversed());
        List<List<Point>> paths=new ArrayList<>();
        for(int start:sources) {
            control.checkForCancel();
            Point source=new Point(survey.xs[start],survey.ys[start]);
            if(paths.stream().anyMatch(p->distance(source,p.get(0))<Math.max(64,settings.endWidth*4)
                    && Math.abs(survey.sample(source.x(),source.y()).height()
                    -survey.sample(p.get(0).x(),p.get(0).y()).height())<.5))continue;
            List<Point> path=new ArrayList<>(); int i=start;
            while(i>=0&&path.size()<20_000) {path.add(new Point(survey.xs[i],survey.ys[i]));i=parent[i];}
            if(i>=0)continue;
            paths.add(List.copyOf(path));if(paths.size()==MAX_CANDIDATES)break;
        }
        return paths;
    }

    private double valleySupport(int i) {
        double sum=0;int count=0;
        for(int[] d:RiverTerrainSurvey.DIRECTIONS) {
            int n=survey.neighbour(i,d[0],d[1]);
            if(n>=0){sum+=survey.heights[n];count++;}
        }
        return count==0?0:Math.max(0,sum/count-survey.heights[i]);
    }

    /** 2-block A* in successively wider corridors. Original source/outlet stay fixed. */
    private List<Point> refine(List<Point> coarse,int width) {
        Point start=coarse.get(0),goal=coarse.get(coarse.size()-1);
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::f).thenComparingInt(n->n.p.x()).thenComparingInt(n->n.p.y()));
        Map<Point,Double> costs=new HashMap<>();Map<Point,Point> parents=new HashMap<>();
        open.add(new Node(start,0,distance(start,goal)));costs.put(start,0d);
        int expanded=0;
        while(!open.isEmpty()&&expanded++<12_000&&costs.size()<96_000) {
            control.checkForCancel();Node node=open.remove();Point p=node.p;
            if(++expandedNodes>360_000)throw new Stopped(Status.BUDGET_EXHAUSTED,"İnce arama düğüm sınırına ulaşıldı.");
            if(node.g>costs.get(p))continue;
            if(distance(p,goal)<=3 && clearEdge(p,goal)) {
                List<Point> result=new ArrayList<>();result.add(goal);
                for(Point q=p;q!=null;q=parents.get(q))result.add(q);
                Collections.reverse(result);return result;
            }
            var current=survey.sample(p.x(),p.y());
            for(int[] d:RiverTerrainSurvey.DIRECTIONS) {
                Point next=new Point(p.x()+d[0]*2,p.y()+d[1]*2);
                if(corridorDistance(next,coarse)>width||!clearEdge(p,next))continue;
                var s=survey.sample(next.x(),next.y());
                double uphill=Math.max(0,s.height()-current.height());
                double cost=node.g+distance(p,next)+uphill*32+clearancePenalty(next,settings.endWidth/2)*32;
                if(cost<costs.getOrDefault(next,Double.POSITIVE_INFINITY)) {
                    costs.put(next,cost);parents.put(next,p);open.add(new Node(next,cost,cost+distance(next,goal)));
                }
            }
        }
        return null;
    }
    private double clearancePenalty(Point p,double radius) {
        var centre=survey.sample(p.x(),p.y());
        if(centre.blocked())return 1000;
        double penalty=0;int r=(int)Math.ceil(Math.max(1.5,radius));
        for(int[] d:RiverTerrainSurvey.DIRECTIONS) {
            var side=survey.sample(p.x()+d[0]*r,p.y()+d[1]*r);
            if(side.blocked())return 1000;
            // A ranking heuristic only. The carver validates oriented sections.
            penalty+=Math.max(0,Math.abs(side.height()-centre.height())-(settings.depth+.10));
        }
        return penalty;
    }
    private boolean clearEdge(Point a,Point b) {
        int steps=Math.max(1,(int)Math.ceil(distance(a,b)));
        for(int i=0;i<=steps;i++) {
            if(++edgeSamples>24_000_000)throw new Stopped(Status.BUDGET_EXHAUSTED,"İnce arama örnek sınırına ulaşıldı.");
            int x=(int)Math.round(a.x()+(b.x()-a.x())*i/(double)steps),y=(int)Math.round(a.y()+(b.y()-a.y())*i/(double)steps);
            if(survey.sample(x,y).blocked())return false;
        }
        return true;
    }
    private static double corridorDistance(Point p,List<Point> line) {
        double best=Double.POSITIVE_INFINITY;
        for(int i=1;i<line.size();i++) {
            Point a=line.get(i-1),b=line.get(i);double dx=b.x()-(double)a.x(),dy=b.y()-(double)a.y();
            double t=Math.max(0,Math.min(1,((p.x()-a.x())*dx+(p.y()-a.y())*dy)/Math.max(1,dx*dx+dy*dy)));
            best=Math.min(best,Math.hypot(p.x()-a.x()-dx*t,p.y()-a.y()-dy*t));
        }
        return best;
    }
    private long tie(int i) {long n=i^settings.seed;n^=n>>>33;n*=0xff51afd7ed558ccdL;return n^(n>>>33);}
    private static double distance(Point a,Point b){return Math.hypot(a.x()-(double)b.x(),a.y()-(double)b.y());}
    private static double length(List<Point> p){double n=0;for(int i=1;i<p.size();i++)n+=distance(p.get(i-1),p.get(i));return n;}
    private long elapsedMillis(){return Math.max(0,(clock.getAsLong()-started)/1_000_000);}
    private record Node(Point p,double g,double f){}
    private static final int MAX_CANDIDATES=64;
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(RiverSearchSession.class);
    private static final class Stopped extends RuntimeException {
        final Status status;Stopped(Status status,String message){super(message);this.status=status;}
    }
    private final class Control extends ScriptProgress {
        boolean applying;
        Control(){super(null,null);}
        @Override public void checkForCancel() {
            if(cancelled!=null&&cancelled.getAsBoolean())throw new Stopped(Status.CANCELLED,"Arama iptal edildi.");
            if(!applying&&dimension.getChangeNo()!=revision)throw new Stopped(Status.STALE_WORLD,"Dünya değişti; yeniden arayın.");
            if(searching&&elapsedMillis()>=settings.budgetMillis)throw new Stopped(Status.BUDGET_EXHAUSTED,"Arama süresi doldu; doğrulanmış sonuçlar korundu.");
            long now=clock.getAsLong();
            if(now-notified>=100_000_000) {
                notified=now;
                if(!applying&&isStale())throw new Stopped(Status.STALE_WORLD,"Dünya değişti; yeniden arayın.");
                if(listener!=null)listener.update(Math.min(.99,elapsedMillis()/(double)settings.budgetMillis),"Vadi analizi: "+candidates+" aday");
            }
        }
        @Override public void setProgress(double value){checkForCancel();}
    }
}
