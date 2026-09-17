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
    /** Neighbour may be at most this many blocks higher than the current cell. */
    static final double MAX_STEP_UP = 4;
    /** Path may not climb more than this above the lowest cell seen so far. */
    static final double MAX_CLIMB_FROM_TROUGH = 4;
    /** Tiny forced stubs (e.g. 8 blocks) are not Apply-ready unless a real lake forms. */
    static final int MIN_APPLY_LAKE_CELLS = 32;
    public record Settings(int count, double startWidth, double endWidth, double depth,
                           boolean smoothBanks, long seed, long budgetMillis, int seaLevel,
                           boolean waterlineBankDetail) {
        public Settings {
            if (count<1||count>12||!Double.isFinite(startWidth)||!Double.isFinite(endWidth)
                    ||!Double.isFinite(depth)||startWidth<2||endWidth<startWidth||endWidth>64
                    ||depth<.65||depth>5||budgetMillis<1||budgetMillis>600_000
                    ||seaLevel<-2048||seaLevel>2032)
                throw new IllegalArgumentException("Invalid river search settings");
        }
        public Settings(int count, double startWidth, double endWidth, double depth,
                        boolean smoothBanks, long seed, long budgetMillis) {
            this(count, startWidth, endWidth, depth, smoothBanks, seed, budgetMillis, 62, true);
        }
        public Settings(int count, double startWidth, double endWidth, double depth,
                        boolean smoothBanks, long seed, long budgetMillis, int seaLevel) {
            this(count, startWidth, endWidth, depth, smoothBanks, seed, budgetMillis, seaLevel, true);
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
    private int candidates, rejected;
    private long expandedNodes, edgeSamples;
    private String reason="";
    private final Map<String,Integer> rejectionReasons=new LinkedHashMap<>();
    private final List<Course> accepted=new ArrayList<>();
    /** Drainage / downhill seeds kept when full-width validation fails — used for forced recovery. */
    private final List<List<Point>> emergencySeeds=new ArrayList<>();
    private boolean forcedCorridor;

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
    public boolean usedForcedCorridor() {return forcedCorridor;}
    public String dirtBedSummary() {
        return plan==null?"":plan.dirtBedSummary();
    }
    public String waterlineBankSummary() {
        return plan==null?"":plan.waterlineBankSummary();
    }

    public RiverSearchResult search() {
        if(ran) throw new IllegalStateException("Search already run");
        ran=true; searching=true; started=clock.getAsLong(); notified=started;
        Status status;
        try {
            control.checkForCancel();
            if(dimension.getTiles().isEmpty())throw new Stopped(Status.NO_FEASIBLE_OUTLET,"Dünyada arazi yok.");
            survey=new RiverTerrainSurvey(dimension,control::checkForCancel,avoid,settings.seaLevel);
            survey.scan();
            plan=newPlan();
            Iterable<List<Point>> paths=drainage();
            List<Course> validated=new ArrayList<>();
            for(List<Point> path:paths) {
                control.checkForCancel(); candidates++;
                if(nearExisting(path,validated)) continue;
                ValidatedPath candidate=findValidatedPath(path);
                ShallowRiverCarver probe=candidate==null?null:candidate.plan;
                if(candidate!=null)path=candidate.path;
                if(probe!=null) {
                    validated.add(toCourse(path,probe,candidate.startWidth(),candidate.endWidth()));
                    // Keep a validated incumbent before further expensive work.
                    boolean thinOnly=candidate.startWidth()<=2.01&&settings.startWidth>2.01;
                    commitBest(validated,thinOnly);
                    // Do not spend the remaining budget on variants after a complete
                    // set of substantial, fully validated courses has been found.
                    if(accepted.size()==settings.count&&accepted.stream().allMatch(c->length(c.centreline())>=preferredLength()))break;
                } else {
                    rememberEmergencySeed(path);
                }
            }
            status=accepted.size()==settings.count ? Status.FOUND
                    : accepted.isEmpty()?Status.NO_FEASIBLE_OUTLET:Status.PARTIAL;
        } catch(Stopped stop) {status=stop.status;reason=stop.getMessage();}
        finally {searching=false;}
        // Budget / candidate exhaustion must not leave Apply dead when a carveable corridor exists.
        if(accepted.isEmpty()&&status!=Status.CANCELLED&&status!=Status.STALE_WORLD&&survey!=null) {
            try {
                if(ensureForcedCourse()) {
                    forcedCorridor=true;
                    if(status==Status.NO_FEASIBLE_OUTLET||status==Status.FOUND) {
                        status=accepted.size()==settings.count?Status.FOUND:Status.PARTIAL;
                    }
                    reason=(reason==null||reason.isBlank()?"":reason+" ")
                            +"Zorunlu koridor üretildi (ince yatak / aşağı akış).";
                }
            } catch(Stopped stop) {
                if(status!=Status.BUDGET_EXHAUSTED) status=stop.status;
                reason=stop.getMessage();
            }
        }
        if(accepted.isEmpty()&&!rejectionReasons.isEmpty()) reason=appendRejectionSummary(reason);
        if(isStale()) status=Status.STALE_WORLD;
        int lakeCells=plan==null?0:plan.getLakeCellCount();
        List<ShallowRiverCarver.PreviewCell> cells=plan==null?List.of():plan.previewCells();
        result=new RiverSearchResult(status,settings.count,accepted,cells,
                new Diagnostics(survey==null?0:survey.step,survey==null?0:survey.sampled,candidates,rejected,
                        elapsedMillis(),estimatedBytes,reason,settings.seaLevel,lakeCells,forcedCorridor));
        LOGGER.info("River search V2: status={}, routes={}/{}, forced={}, sea={}, lakes={}, diagnostics={}",
                status,accepted.size(),settings.count,forcedCorridor,settings.seaLevel,lakeCells,result.diagnostics());
        RiverSearchLog.write(settings,result,forcedCorridor,null);
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
        return newPlan(settings.startWidth,settings.endWidth,settings.depth);
    }
    /** Narrower bed used only when the requested preset cannot clear a real valley. */
    private ShallowRiverCarver newThinPlan() {
        return newPlan(2,thinEndWidth(),Math.max(0.65,Math.min(settings.depth,0.85)));
    }
    private double thinEndWidth() {
        // Cap hard at 4 so recovery can fit mountain/narrow valleys even when the
        // UI preset asks for a wide lowland channel.
        return Math.max(2,Math.min(4,settings.endWidth));
    }
    private ShallowRiverCarver newPlan(double startWidth,double endWidth,double depth) {
        var p=new ShallowRiverCarver(dimension,startWidth,endWidth,depth,
                settings.smoothBanks,true,settings.seed,control);
        p.enableTerrainPreservation();
        // A coherent bed is an invariant of newly planned rivers, not a style toggle.
        p.setLowerInteriorDirt(true);
        p.setWaterlineBankDetail(settings.waterlineBankDetail());
        return p;
    }
    private ShallowRiverCarver validate(List<Point> path) {return validate(path,newPlan());}
    private ShallowRiverCarver validateThin(List<Point> path) {return validate(path,newThinPlan());}
    private ShallowRiverCarver validate(List<Point> path,ShallowRiverCarver p) {
        control.checkForCancel();
        int[] x=path.stream().mapToInt(Point::x).toArray(),y=path.stream().mapToInt(Point::y).toArray();
        if(p.addPath(x,y)&&p.isFootprintAllowed(avoid)) return p;
        rejected++;
        String rejection=p.getLastRejection();
        rejectionReasons.merge(rejectionCategory(rejection),1,Integer::sum);
        reason=path.get(0)+" → "+path.get(path.size()-1)+": "+rejection;
        return null;
    }
    private Course toCourse(List<Point> path,ShallowRiverCarver probe,double startWidth,double endWidth) {
        double cost=0;
        List<Point> lake=new ArrayList<>();
        int lakeWater=Integer.MIN_VALUE;
        for(var cell:probe.previewCells()) {
            cost+=Math.max(0,cell.originalHeight()-cell.bedHeight());
            if(cell.waterLevel()>Math.round(cell.originalHeight()) && cell.bedHeight()>=cell.originalHeight()-0.05f) {
                lake.add(new Point(cell.x(),cell.y()));
                lakeWater=cell.waterLevel();
            }
        }
        return new Course(path,startWidth,endWidth,cost,lake,lakeWater);
    }

    /**
     * Validate the whole headwater first. If one local ridge/cross-slope makes
     * that route impossible, continue below that obstruction in the same
     * valley. This turns a rejected headwater into a shorter valid tributary
     * without moving a hand-selected source or changing any safety limit.
     */
    private ValidatedPath findValidatedPath(List<Point> original) {
        List<Point> path=original.size()>=3?smoothCentreline(densifyRoute(original)):original;
        for(int sourceAttempt=0;sourceAttempt<3;sourceAttempt++) {
            ShallowRiverCarver probe=validate(path);
            if(probe!=null)return attachLake(path,probe,settings.startWidth,settings.endWidth);
            probe=validateThin(path);
            if(probe!=null)return attachLake(path,probe,2,thinEndWidth());
            List<Point> previous=path;
            for(int width:new int[]{16,32,64}) {
                List<Point> refined=refine(path,width);
                if(refined==null||refined.equals(previous))continue;
                previous=refined;probe=validate(refined);
                if(probe!=null)return attachLake(refined,probe,settings.startWidth,settings.endWidth);
                probe=validateThin(refined);
                if(probe!=null)return attachLake(refined,probe,2,thinEndWidth());
            }
            int cut=downstreamOfFailure(path,reason);
            if(cut<=0||cut>=path.size()-2)break;
            List<Point> suffix=List.copyOf(path.subList(cut,path.size()));
            if(length(suffix)<64)break;
            path=suffix;
        }
        // Limited uphill escapes from several seeds before flooding a bowl lake.
        ValidatedPath escaped=tryOutletEscapes(original);
        if(escaped!=null) return escaped;
        // Do not hide a walkable outlet behind a pond: if any climb-limited
        // downhill from the valley reaches painted water / sea, skip the lake.
        if(outletReachableWithinClimb(original)) return null;
        return validateWithLake(original);
    }

    /** Try downhill routes from headwater, sink, midpoints — river over pond. */
    private ValidatedPath tryOutletEscapes(List<Point> original) {
        if(original==null||original.isEmpty()) return null;
        LinkedHashSet<Point> seeds=new LinkedHashSet<>();
        seeds.add(original.get(0));
        seeds.add(lowest(original));
        seeds.add(original.get(original.size()-1));
        if(original.size()>5) seeds.add(original.get(original.size()/2));
        if(original.size()>9) seeds.add(original.get(original.size()*3/4));
        for(Point seed:seeds) {
            control.checkForCancel();
            List<Point> escape=buildDownhillRoute(seed);
            if(escape==null||!reachesSea(escape)||length(escape)<64) continue;
            ShallowRiverCarver probe=validateThin(escape);
            if(probe!=null) return new ValidatedPath(escape,probe,2,thinEndWidth());
            probe=validate(escape);
            if(probe!=null) return new ValidatedPath(escape,probe,settings.startWidth,settings.endWidth);
        }
        return null;
    }

    private boolean outletReachableWithinClimb(List<Point> original) {
        if(original==null||original.isEmpty()) return false;
        for(Point seed:List.of(original.get(0),lowest(original),original.get(original.size()-1))) {
            List<Point> escape=buildDownhillRoute(seed);
            if(escape!=null&&reachesSea(escape)&&length(escape)>=64) return true;
        }
        return false;
    }
    /**
     * If the valley cannot safely cut through to sea, keep a contained lake
     * at the sink and a shallow reach above it.
     */
    private ValidatedPath validateWithLake(List<Point> original) {
        if(original==null||original.size()<2||survey==null) return null;
        Point sink=lowest(original);
        var lake=RiverLakePlanner.plan(survey,sink,settings.seaLevel,avoid,control::checkForCancel);
        if(lake==null) {
            sink=original.get(original.size()-1);
            lake=RiverLakePlanner.plan(survey,sink,settings.seaLevel,avoid,control::checkForCancel);
        }
        if(lake==null) return null;
        List<Point> river=trimBeforeLake(original,lake.cells());
        if(river.size()<2) {
            Point a=lake.cells().get(0),b=lake.cells().get(Math.min(lake.cells().size()-1,1));
            if(a.equals(b)&&lake.cells().size()>2)b=lake.cells().get(2);
            river=List.of(a,b);
        }
        ShallowRiverCarver probe=newThinPlan();
        if(river.size()>=2) {
            int[] x=river.stream().mapToInt(Point::x).toArray(),y=river.stream().mapToInt(Point::y).toArray();
            if(!probe.addPath(x,y)) probe=newThinPlan();
        }
        if(!probe.addLake(lake.cells(),lake.water())||!probe.isFootprintAllowed(avoid)) return null;
        return new ValidatedPath(river,probe,2,thinEndWidth());
    }
    private ValidatedPath attachLake(List<Point> path,ShallowRiverCarver probe,double startWidth,double endWidth) {
        if(reachesSea(path)) return new ValidatedPath(path,probe,startWidth,endWidth);
        // Prefer extending to an outlet over dropping a depression lake on a valid carve.
        List<Point> extended=extendPathToOutlet(path);
        if(extended!=null&&reachesSea(extended)) {
            ShallowRiverCarver p2=validateThin(extended);
            if(p2!=null) return new ValidatedPath(extended,p2,2,thinEndWidth());
            p2=validate(extended);
            if(p2!=null) return new ValidatedPath(extended,p2,startWidth,endWidth);
            // Outlet is walkable — do not flood a lake over a failed carve.
            return new ValidatedPath(path,probe,startWidth,endWidth);
        }
        Point sink=lowest(path);
        var lake=RiverLakePlanner.plan(survey,sink,settings.seaLevel,avoid,control::checkForCancel);
        if(lake!=null) probe.addLake(lake.cells(),lake.water());
        return new ValidatedPath(path,probe,startWidth,endWidth);
    }

    /** Continue from the path end / sink until painted water or sea within climb limits. */
    private List<Point> extendPathToOutlet(List<Point> path) {
        if(path==null||path.size()<2||reachesSea(path)) return path;
        for(Point seed:List.of(path.get(path.size()-1),lowest(path))) {
            List<Point> tail=buildDownhillRoute(seed);
            if(tail==null||!reachesSea(tail)||tail.size()<2) continue;
            List<Point> merged=new ArrayList<>(path);
            Point last=merged.get(merged.size()-1);
            for(Point p:tail) {
                if(p.equals(last)) continue;
                if(merged.size()>=2&&p.equals(merged.get(merged.size()-2))) continue;
                merged.add(p);last=p;
            }
            List<Point> smooth=smoothCentreline(densifyRoute(merged));
            if(reachesSea(smooth)&&length(smooth)>=64) return smooth;
        }
        return null;
    }
    private boolean reachesSea(List<Point> path) {
        if(path==null||path.isEmpty()||survey==null) return false;
        Point end=path.get(path.size()-1);
        return isOutlet(survey.sample(end.x(),end.y()),end.x(),end.y());
    }
    private Point lowest(List<Point> path) {
        Point best=path.get(0);
        float bestH=survey.sample(best.x(),best.y()).height();
        for(Point p:path) {
            float h=survey.sample(p.x(),p.y()).height();
            if(Float.isFinite(h)&&(h<bestH||!Float.isFinite(bestH))) {best=p;bestH=h;}
        }
        return best;
    }
    private static List<Point> trimBeforeLake(List<Point> path,List<Point> lake) {
        Set<Long> in=new HashSet<>();
        for(Point p:lake) in.add((((long)p.x())<<32)|(p.y()&0xffffffffL));
        int cut=path.size();
        for(int i=0;i<path.size();i++) {
            Point p=path.get(i);
            if(in.contains((((long)p.x())<<32)|(p.y()&0xffffffffL))) {cut=i;break;}
        }
        if(cut<2) return path.subList(0,Math.min(2,path.size()));
        return List.copyOf(path.subList(0,cut));
    }
    private void rememberEmergencySeed(List<Point> path) {
        if(path==null||path.size()<2||length(path)<32||emergencySeeds.size()>=48) return;
        emergencySeeds.add(path);
    }
    /**
     * After timeout or exhausted candidates, still try to produce one Apply-ready
     * corridor. Runs with searching=false so the shared millis budget does not
     * immediately abort recovery. Never writes terrain; never crosses avoid/protected.
     */
    private boolean ensureForcedCourse() {
        if(listener!=null)listener.update(0.99,"Zorunlu koridor deneniyor…");
        List<Course> recovered=new ArrayList<>();
        for(List<Point> seed:List.copyOf(emergencySeeds)) {
            control.checkForCancel();
            ValidatedPath candidate=findValidatedPath(seed);
            if(candidate!=null) offerRecovered(recovered,toCourse(candidate.path(),candidate.plan(),candidate.startWidth(),candidate.endWidth()));
            else {
                List<Point> downhill=buildDownhillRoute(seed.get(0));
                if(downhill!=null) {
                    rememberEmergencySeed(downhill);
                    offerForcedDownhill(recovered,downhill);
                }
            }
            if(recovered.size()>=settings.count) break;
        }
        if(recovered.isEmpty()) {
            for(Point source:emergencyHeadwaters(12)) {
                control.checkForCancel();
                List<Point> downhill=buildDownhillRoute(source);
                if(downhill==null) continue;
                ValidatedPath candidate=findValidatedPath(downhill);
                if(candidate!=null) offerRecovered(recovered,toCourse(candidate.path(),candidate.plan(),candidate.startWidth(),candidate.endWidth()));
                else offerForcedDownhill(recovered,downhill);
                if(recovered.size()>=settings.count) break;
            }
        }
        if(recovered.isEmpty()) {
            reason=(reason==null||reason.isBlank()?"":reason+" ")
                    +"Minik saplama kabul edilmedi (en az ~64 blok veya anlamlı göl).";
            return false;
        }
        commitBest(recovered,true);
        if(accepted.isEmpty()) {
            reason=(reason==null||reason.isBlank()?"":reason+" ")
                    +"Minik saplama kabul edilmedi (en az ~64 blok veya anlamlı göl).";
            return false;
        }
        return true;
    }
    private void offerForcedDownhill(List<Course> recovered,List<Point> downhill) {
        if(downhill==null||downhill.size()<2) return;
        if(reachesSea(downhill)) {
            ShallowRiverCarver probe=validateThin(downhill);
            if(probe!=null) offerRecovered(recovered,toCourse(downhill,probe,2,thinEndWidth()));
            else {
                probe=validate(downhill);
                if(probe!=null) offerRecovered(recovered,toCourse(downhill,probe,settings.startWidth,settings.endWidth));
            }
            // Walkable sea outlet: never replace a failed carve with a depression lake.
            return;
        }
        // Try extending / alternate seeds before accepting a pond.
        ValidatedPath escaped=tryOutletEscapes(downhill);
        if(escaped!=null) {
            offerRecovered(recovered,toCourse(escaped.path(),escaped.plan(),escaped.startWidth(),escaped.endWidth()));
            return;
        }
        if(outletReachableWithinClimb(downhill)) return;
        ValidatedPath lake=validateWithLake(downhill);
        if(lake!=null) offerRecovered(recovered,toCourse(lake.path(),lake.plan(),lake.startWidth(),lake.endWidth()));
    }
    private void offerRecovered(List<Course> recovered,Course course) {
        if(isSubstantial(course)) recovered.add(course);
    }
    private List<Point> emergencyHeadwaters(int limit) {
        List<Point> sources=new ArrayList<>();
        if(survey==null) return sources;
        int inset=(int)Math.ceil(settings.endWidth/2)+3;
        record Ranked(int i,double score){}
        PriorityQueue<Ranked> heap=new PriorityQueue<>(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparingInt(Ranked::i));
        for(int i=0;i<survey.heights.length;i++) {
            if((i&2047)==0)control.checkForCancel();
            if(!Float.isFinite(survey.heights[i])||survey.wet[i]) continue;
            if(survey.edge(survey.xs[i],survey.ys[i])<inset+survey.step) continue;
            heap.add(new Ranked(i,survey.heights[i]+valleySupport(i)*8));
        }
        while(!heap.isEmpty()&&sources.size()<limit) {
            Ranked r=heap.remove();
            sources.add(new Point(survey.xs[r.i],survey.ys[r.i]));
        }
        return sources;
    }
    /** Descent with limited saddle climb: prefer down, stay straight, allow +4 / trough+4. */
    private List<Point> buildDownhillRoute(Point start) {
        if(start==null||survey==null) return null;
        if(avoid!=null&&avoid.test(start.x(),start.y())) return null;
        List<Point> forward=new ArrayList<>();
        forward.add(start);
        Set<Long> visited=new HashSet<>();
        visited.add((((long)start.x())<<32)^(start.y()&0xffffffffL));
        int x=start.x(),y=start.y();
        int prevDx=0,prevDy=0;
        float trough=survey.sample(x,y).height();
        int maxSteps=Math.max(4096,Math.min(65_536,
                (survey.maxX-survey.minX+1)+(survey.maxY-survey.minY+1)*8));
        for(int step=0;step<maxSteps;step++) {
            control.checkForCancel();
            var here=survey.sample(x,y);
            if(here.blocked()) break;
            if(isOutlet(here,x,y)) break;
            if(Float.isFinite(here.height())) trough=Math.min(trough,here.height());
            int bestX=x,bestY=y;
            double bestScore=Double.POSITIVE_INFINITY;
            boolean moved=false;
            for(int[] d:RiverTerrainSurvey.DIRECTIONS) {
                int nx=x+d[0],ny=y+d[1];
                long key=(((long)nx)<<32)^(ny&0xffffffffL);
                if(visited.contains(key)) continue;
                if(avoid!=null&&avoid.test(nx,ny)) continue;
                var sample=survey.sample(nx,ny);
                if(sample.blocked()||!Float.isFinite(sample.height())) continue;
                double rise=sample.height()-here.height();
                if(rise>MAX_STEP_UP+0.001) continue;
                if(sample.height()>trough+MAX_CLIMB_FROM_TROUGH+0.001) continue;
                double escape=0;
                for(int[] d2:RiverTerrainSurvey.DIRECTIONS) {
                    var n2=survey.sample(nx+d2[0],ny+d2[1]);
                    if(n2.blocked()||!Float.isFinite(n2.height())) continue;
                    if(n2.height()<sample.height()-0.01f) escape-=40;
                    if(isOutlet(n2,nx+d2[0],ny+d2[1])) escape-=800;
                }
                // Medium-range downhill look-ahead: stop micro-zigzag on flats.
                var ahead=survey.sample(nx+d[0]*6,ny+d[1]*6);
                double look=0;
                if(!ahead.blocked()&&Float.isFinite(ahead.height()))
                    look=(ahead.height()-sample.height())*8;
                double turn=0;
                if(prevDx!=0||prevDy!=0) {
                    int dot=prevDx*d[0]+prevDy*d[1];
                    if(dot<=0) turn=80;           // reverse / hard corner
                    else if(d[0]!=prevDx||d[1]!=prevDy) turn=18; // soft bend
                }
                // Tiny noise only for true ties — never dominate geometry.
                double score=rise*1000+sample.height()+escape+look+turn+((tie(nx^ny)&0xf)/256.0);
                if(score<bestScore) {
                    bestScore=score; bestX=nx; bestY=ny; moved=true;
                }
            }
            if(!moved) break;
            prevDx=bestX-x; prevDy=bestY-y;
            x=bestX;y=bestY;
            visited.add((((long)x)<<32)^(y&0xffffffffL));
            forward.add(new Point(x,y));
            var arrived=survey.sample(x,y);
            if(isOutlet(arrived,x,y)) break;
        }
        if(forward.size()<2) return null;
        return smoothCentreline(densifyRoute(forward));
    }
    /** Painted water, configured sea Y, or (when sea Y is set) the map rim. */
    private boolean isOutlet(RiverTerrainSurvey.Sample sample,int x,int y) {
        if(sample==null||sample.blocked()) return false;
        if(sample.wet()) return true;
        if(settings.seaLevel>0&&sample.height()<=settings.seaLevel) return true;
        // seaLevel<=0: painted water only — do not treat the dry rim as ocean.
        return settings.seaLevel>0&&survey.edge(x,y)<=2;
    }
    private boolean isSubstantial(Course c) {
        if(c==null) return false;
        double min=survey==null?64:preferredLength();
        if(reachesSea(c.centreline())&&length(c.centreline())>=min) return true;
        // Lake-only Apply: sealed worlds without an outlet. If painted water / sea
        // exists on the map, a depression pond must not unlock Apply instead of a river.
        if(c.lakeCells()!=null&&c.lakeCells().size()>=MIN_APPLY_LAKE_CELLS) {
            if(reachesSea(c.centreline())) return length(c.centreline())>=32;
            return !mapHasOutletWater();
        }
        return false;
    }
    private boolean mapHasOutletWater() {
        if(settings.seaLevel>0) return true;
        if(survey==null||survey.wet==null) return false;
        for(boolean w:survey.wet) if(w) return true;
        return false;
    }
    private static List<Point> densifyRoute(List<Point> sparse) {
        List<Point> dense=new ArrayList<>();
        dense.add(sparse.get(0));
        for(int i=1;i<sparse.size();i++) {
            Point a=sparse.get(i-1),b=sparse.get(i);
            int steps=Math.max(1,(int)Math.ceil(distance(a,b)));
            for(int s=1;s<=steps;s++) {
                int x=(int)Math.round(a.x()+(b.x()-a.x())*(s/(double)steps));
                int y=(int)Math.round(a.y()+(b.y()-a.y())*(s/(double)steps));
                Point last=dense.get(dense.size()-1);
                if(last.x()!=x||last.y()!=y) dense.add(new Point(x,y));
            }
        }
        return List.copyOf(dense);
    }

    /**
     * Collapse stair-steps / coils with line-of-sight shortcuts that stay within
     * the climb contract, then re-densify into a clean centreline.
     */
    private List<Point> smoothCentreline(List<Point> path) {
        if(path==null||path.size()<3) return path;
        List<Point> anchors=new ArrayList<>();
        anchors.add(path.get(0));
        int i=0;
        while(i<path.size()-1) {
            control.checkForCancel();
            int best=i+1;
            int limit=Math.min(path.size()-1,i+96);
            for(int j=limit;j>i+1;j--) {
                if(clearFlow(path.get(i),path.get(j))) {best=j;break;}
            }
            anchors.add(path.get(best));
            i=best;
        }
        List<Point> dense=densifyRoute(anchors);
        return dense.size()>=2?dense:path;
    }

    /** Bresenham corridor is walkable under step/trough climb limits. */
    private boolean clearFlow(Point a,Point b) {
        if(a.equals(b)) return true;
        int x0=a.x(),y0=a.y(),x1=b.x(),y1=b.y();
        int dx=Math.abs(x1-x0),dy=Math.abs(y1-y0);
        int sx=x0<x1?1:-1,sy=y0<y1?1:-1;
        int err=dx-dy;
        var start=survey.sample(x0,y0);
        if(start.blocked()||!Float.isFinite(start.height())) return false;
        float trough=start.height();
        float prev=start.height();
        int x=x0,y=y0;
        while(true) {
            if(avoid!=null&&avoid.test(x,y)) return false;
            var s=survey.sample(x,y);
            if(s.blocked()||!Float.isFinite(s.height())) return false;
            double rise=s.height()-prev;
            if(rise>MAX_STEP_UP+0.001) return false;
            trough=Math.min(trough,s.height());
            if(s.height()>trough+MAX_CLIMB_FROM_TROUGH+0.001) return false;
            // Reject wild sideways ridges relative to the segment endpoints.
            float endH=survey.sample(x1,y1).height();
            float ceiling=(float)(Math.max(start.height(),endH)+MAX_CLIMB_FROM_TROUGH);
            if(s.height()>ceiling+0.001f) return false;
            if(x==x1&&y==y1) return true;
            int e2=2*err;
            if(e2>-dy){err-=dy;x+=sx;}
            if(e2<dx){err+=dx;y+=sy;}
            prev=s.height();
        }
    }

    private static int downstreamOfFailure(List<Point> path,String rejection) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("hücre=(-?\\d+),(-?\\d+)").matcher(rejection);
        if(!m.find())return -1;
        Point failure=new Point(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)));
        int nearest=0;double distance=Double.POSITIVE_INFINITY;
        for(int i=0;i<path.size();i++) {
            double d=distance(path.get(i),failure);
            if(d<distance){distance=d;nearest=i;}
        }
        // Advance by a real coarse cell so the same obstruction is not retried.
        return Math.min(path.size()-1,nearest+Math.max(1,path.size()/32));
    }

    private static String rejectionCategory(String value) {
        if(value==null||value.isBlank())return "Bilinmeyen doğrulama reddi";
        int detail=value.indexOf(" [");
        return detail<0?value:value.substring(0,detail);
    }

    private String appendRejectionSummary(String base) {
        String top=rejectionReasons.entrySet().stream()
                .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
                .limit(3).map(e->e.getValue()+"× "+e.getKey())
                .collect(java.util.stream.Collectors.joining("; "));
        return (base==null||base.isBlank()?"":base+" ")+"En sık retler: "+top;
    }
    private void commitBest(List<Course> choices,boolean allowShort) {
        if(choices==null||choices.isEmpty()) return;
        double preferred=preferredLength();
        List<Course> ranked=new ArrayList<>(choices);
        // Compare excavation per block of river, not total volume: total
        // volume would always reward the shortest coastal fragment.
        ranked.sort(Comparator
                .comparingInt((Course c)->reachesSea(c.centreline())?0:1)
                .thenComparingInt((Course c)->c.lakeCells()==null||c.lakeCells().isEmpty()?0:1)
                .thenComparingDouble((Course c)->Math.rint(c.excavation()/Math.max(1,length(c.centreline()))*16)/16)
                .thenComparing(Comparator.comparingDouble((Course c)->length(c.centreline())).reversed()));
        boolean preferLong=!allowShort&&ranked.stream().anyMatch(c->length(c.centreline())>=preferred&&reachesSea(c.centreline()));
        boolean preferSea=ranked.stream().anyMatch(c->reachesSea(c.centreline())&&isSubstantial(c));
        boolean thinOnly=allowShort||ranked.stream().anyMatch(c->c.startWidth()<=2.01&&settings.startWidth>2.01);
        ShallowRiverCarver next=thinOnly?newThinPlan():newPlan();
        List<Course> selected=selectInto(next,ranked,preferLong,preferSea);
        // Never drop a short but carveable valley just because a longer peer failed re-add.
        if(selected.isEmpty()&&preferLong) selected=selectInto(next=thinOnly?newThinPlan():newPlan(),ranked,false,preferSea);
        if(selected.isEmpty()&&preferSea) selected=selectInto(next=thinOnly?newThinPlan():newPlan(),ranked,false,true);
        if(selected.isEmpty()&&!thinOnly) selected=selectInto(next=newThinPlan(),ranked,false,false);
        // Never replace a good incumbent with an empty rebuild.
        if(selected.isEmpty()) return;
        plan=next;accepted.clear();accepted.addAll(selected);
    }
    private List<Course> selectInto(ShallowRiverCarver next,List<Course> ranked,boolean preferLong,boolean preferSea) {
        double preferred=preferredLength();
        List<Course> selected=new ArrayList<>();
        for(Course c:ranked) {
            control.checkForCancel();
            if(selected.size()==settings.count) break;
            if(!isSubstantial(c)) continue;
            if(preferSea&&!reachesSea(c.centreline())) continue;
            if(preferLong&&length(c.centreline())<preferred) continue;
            if(nearExisting(c.centreline(),selected)) continue;
            int[] x=c.centreline().stream().mapToInt(Point::x).toArray(),y=c.centreline().stream().mapToInt(Point::y).toArray();
            boolean carved=c.centreline().size()>=2&&next.addPath(x,y);
            boolean hasLake=!c.lakeCells().isEmpty()&&c.lakeWater()>Integer.MIN_VALUE;
            if(!carved&&!hasLake) continue;
            if(hasLake&&!next.addLake(c.lakeCells(),c.lakeWater())) continue;
            if(next.isFootprintAllowed(avoid)) selected.add(c);
        }
        return selected;
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
    private Iterable<List<Point>> drainage() {
        int n=survey.heights.length;
        float[] filled=survey.heights.clone(); int[] parent=new int[n],order=new int[n],accum=new int[n];
        Arrays.fill(parent,-1); boolean[] seen=new boolean[n];
        PriorityQueue<Integer> heap=new PriorityQueue<>(Comparator.<Integer>comparingDouble(i->filled[i]).thenComparingInt(i->i));
        int inset=(int)Math.ceil(settings.endWidth/2)+3;
        for(int i=0;i<n;i++) {
            if((i&1023)==0) control.checkForCancel();
            if(!Float.isFinite(filled[i])) continue;
            double edge=survey.edge(survey.xs[i],survey.ys[i]);
            // seaLevel<=0: only painted water seeds drainage — dry rim is not an ocean.
            boolean edgeOutlet=settings.seaLevel>0&&edge>=inset&&edge<inset+survey.step
                    &&clearancePenalty(new Point(survey.xs[i],survey.ys[i]),settings.endWidth/2)<.01;
            if(survey.wet[i]||edgeOutlet){seen[i]=true;heap.add(i);}
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
        double[] scores=new double[n];
        RankedRiverSources best=new RankedRiverSources(n,(a,b)->{
            int c=Double.compare(scores[a],scores[b]);
            if(c==0)c=Long.compare(tie(a),tie(b));
            return c==0?Integer.compare(a,b):c;
        });
        for(int i=0;i<n;i++) {
            if((i&1023)==0)control.checkForCancel();
            if(!seen[i]||survey.wet[i]||lengths[i]<64||survey.edge(survey.xs[i],survey.ys[i])<inset+survey.step*2)continue;
            int downstream=parent[i];
            if(downstream<0)continue;
            if(sectionPenalty(new Point(survey.xs[i],survey.ys[i]),
                    survey.xs[downstream]-(double)survey.xs[i],survey.ys[downstream]-(double)survey.ys[i],settings.startWidth/2)>.01)continue;
            scores[i]=lengths[i]*Math.log1p(accum[i])*(1+8*valleySupport(i));
            best.add(i);
        }
        // Expand just one path per request. Every source is removed once;
        // rejected headwaters do not suppress nearby, potentially valid valleys.
        return ()->new Iterator<>() {
            public boolean hasNext(){control.checkForCancel();return !best.isEmpty();}
            public List<Point> next() {
                int i=best.remove();List<Point> path=new ArrayList<>();
                while(i>=0) {
                    control.checkForCancel();
                    if(path.size()>=20_000)throw new Stopped(Status.BUDGET_EXHAUSTED,"Rota uzunluğu güvenlik sınırına ulaşıldı.");
                    path.add(new Point(survey.xs[i],survey.ys[i]));i=parent[i];
                }
                // Coarse D8 drainage often stair-steps on flats; straighten first.
                return smoothCentreline(densifyRoute(List.copyOf(path)));
            }
        };
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
        Map<Point,Double> troughs=new HashMap<>();
        float startH=survey.sample(start.x(),start.y()).height();
        open.add(new Node(start,0,distance(start,goal)));costs.put(start,0d);troughs.put(start,(double)startH);
        int expanded=0;
        while(!open.isEmpty()&&expanded++<12_000&&costs.size()<96_000) {
            control.checkForCancel();Node node=open.remove();Point p=node.p;
            // Cumulative work is telemetry, not live memory. Each corridor still
            // has bounded nodes/maps; exhausting earlier valleys must not stop
            // unrelated candidates while the shared deadline has time remaining.
            expandedNodes++;
            if(node.g>costs.get(p))continue;
            if(distance(p,goal)<=3 && clearEdge(p,goal)) {
                List<Point> result=new ArrayList<>();result.add(goal);
                for(Point q=p;q!=null;q=parents.get(q))result.add(q);
                Collections.reverse(result);
                return smoothCentreline(densifyRoute(result));
            }
            var current=survey.sample(p.x(),p.y());
            double trough=troughs.getOrDefault(p,(double)current.height());
            Point from=parents.get(p);
            for(int[] d:RiverTerrainSurvey.DIRECTIONS) {
                Point next=new Point(p.x()+d[0]*2,p.y()+d[1]*2);
                if(corridorDistance(next,coarse)>width||!clearEdge(p,next))continue;
                var s=survey.sample(next.x(),next.y());
                if(s.blocked()||!Float.isFinite(s.height()))continue;
                double rise=s.height()-current.height();
                if(rise>MAX_STEP_UP+0.001)continue;
                if(s.height()>trough+MAX_CLIMB_FROM_TROUGH+0.001)continue;
                double uphill=Math.max(0,rise);
                double turn=0;
                if(from!=null) {
                    int pdx=Integer.signum(p.x()-from.x()),pdy=Integer.signum(p.y()-from.y());
                    int ndx=Integer.signum(d[0]),ndy=Integer.signum(d[1]);
                    int dot=pdx*ndx+pdy*ndy;
                    if(dot<=0) turn=14;
                    else if(pdx!=ndx||pdy!=ndy) turn=3.5;
                }
                // The carver's excavated wet core is 60% of the visual radius.
                // Scoring the complete visual bank as wet bed rejects exactly
                // the raised valley sides which should contain the river.
                double wetRadius=Math.max(1.5,settings.endWidth*.30);
                double cost=node.g+distance(p,next)+uphill*32+turn
                        +sectionPenalty(next,next.x()-(double)p.x(),next.y()-(double)p.y(),wetRadius)*32;
                if(cost<costs.getOrDefault(next,Double.POSITIVE_INFINITY)) {
                    costs.put(next,cost);parents.put(next,p);
                    troughs.put(next,Math.min(trough,s.height()));
                    open.add(new Node(next,cost,cost+distance(next,goal)));
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
    private double sectionPenalty(Point p,double dx,double dy,double radius) {
        return RiverCrossSection.penalty(p.x(),p.y(),dx,dy,radius,settings.depth,survey::sample);
    }
    private boolean clearEdge(Point a,Point b) {
        int steps=Math.max(1,(int)Math.ceil(distance(a,b)));
        for(int i=0;i<=steps;i++) {
            edgeSamples++;
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
    private record ValidatedPath(List<Point> path,ShallowRiverCarver plan,double startWidth,double endWidth){}
    private double preferredLength() {
        return Math.min(512,Math.max(64,Math.max((long)survey.maxX-survey.minX,(long)survey.maxY-survey.minY)/4.0));
    }
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
