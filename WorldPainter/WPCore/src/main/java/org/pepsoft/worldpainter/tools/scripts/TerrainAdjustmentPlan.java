package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Detached terrain grading plan for drawn-river valley adaptation.
 * Not serialised into .world files. Channel carving stays separate.
 */
public final class TerrainAdjustmentPlan {
    public enum Mode { LIGHT, STRONG, DEEP }

    public record Limits(double maxCut, double maxFill, int maxBankDistance, int maxCentreDeviation) {
        public static Limits of(Mode mode) {
            if(mode==Mode.DEEP)return new Limits(16.0,2.0,64,32);
            return mode == Mode.STRONG
                    ? new Limits(6.0, 2.0, 64, 32)
                    : new Limits(2.0, 0.5, 32, 16);
        }
        public int bankDistance(double localWidth) {
            int mult = maxBankDistance >= 64 ? 4 : 2;
            return Math.min(maxBankDistance, Math.max(4, (int) Math.ceil(localWidth * mult)));
        }
    }

    public record Summary(Mode mode, int cells, double maxCut, double maxFill, double maxAbsDelta) {}

    private final Mode mode;
    private final Map<Long, Float> original = new LinkedHashMap<>();
    private final Map<Long, Float> delta = new LinkedHashMap<>();
    private final Map<Long,Float> bankFloors=new LinkedHashMap<>();
    private final Set<Long> wetMask=new java.util.HashSet<>();
    private final Map<Long,Integer> wetLevels=new LinkedHashMap<>();
    private final Set<Long> edgeOutlets=new java.util.HashSet<>();

    public TerrainAdjustmentPlan(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
    }

    /** Optional marked map-edge mouths (keys = x<<32|y). Softens DEEP bank ring toward missing tiles. */
    public void setEdgeOutlets(Set<Long> outlets) {
        edgeOutlets.clear();
        if (outlets != null) edgeOutlets.addAll(outlets);
    }

    public boolean isNearEdgeOutlet(int x, int y) {
        if (edgeOutlets.isEmpty()) return false;
        for (long k : edgeOutlets) {
            int ox = (int) (k >> 32), oy = (int) k;
            int dx = x - ox, dy = y - oy;
            if (dx * dx + dy * dy <= 16) return true; // r≈4
        }
        return false;
    }

    public Mode mode() { return mode; }
    public Limits limits() { return Limits.of(mode); }
    public boolean isEmpty() { return delta.isEmpty(); }
    public Set<Long> cells() { return Collections.unmodifiableSet(delta.keySet()); }

    public float originalHeight(int x, int y, float fallback) {
        Float o = original.get(key(x, y));
        return o != null ? o : fallback;
    }

    public float deltaAt(int x, int y) {
        return delta.getOrDefault(key(x, y), 0f);
    }

    public float adjustedHeight(int x, int y, float liveHeight) {
        long k = key(x, y);
        if (!delta.containsKey(k)) return liveHeight;
        return original.get(k) + delta.get(k);
    }

    public void put(int x, int y, float originalHeight, float proposedDelta) {
        Limits lim = limits();
        float d = proposedDelta;
        if (d < 0) d = (float) Math.max(d, -lim.maxCut());
        else d = (float) Math.min(d, lim.maxFill());
        // Tile.setHeight stores 1/256-block fixed point. Plan that exact height
        // so applying the grade cannot invalidate the channel's own snapshot.
        d = (float)(Math.floor((originalHeight + d) * 256.0) / 256.0) - originalHeight;
        if (Math.abs(d) < 1e-4f) return;
        long k = key(x, y);
        original.putIfAbsent(k, originalHeight);
        // Proposals describe an absolute change from the original terrain, not
        // an additional excavation for every overlapping cross-section/branch.
        delta.merge(k, d, (a, b) -> a < 0 || b < 0 ? Math.min(a, b) : Math.max(a, b));
        float total = delta.get(k);
        if (total < 0) total = (float) Math.max(total, -lim.maxCut());
        else total = (float) Math.min(total, lim.maxFill());
        if (Math.abs(total) < 1e-4f) {
            delta.remove(k);
            original.remove(k);
        } else delta.put(k, total);
    }

    public Summary summary() {
        double maxCut = 0, maxFill = 0, maxAbs = 0;
        for (float d : delta.values()) {
            if (d < 0) maxCut = Math.max(maxCut, -d);
            else maxFill = Math.max(maxFill, d);
            maxAbs = Math.max(maxAbs, Math.abs(d));
        }
        return new Summary(mode, delta.size(), maxCut, maxFill, maxAbs);
    }

    /** Soft edge: delta fades to 0 at corridor rim (outside cells never stored). */
    public static float edgeFade(double distFromCentre, double halfWidth) {
        if (halfWidth <= 1e-6) return 0f;
        double t = Math.min(1, Math.max(0, distFromCentre / halfWidth));
        double w = 1 - t * t * (3 - 2 * t);
        return (float) w;
    }

    /**
     * Target slope ≤ 1 vertical / 3 horizontal from centre cut.
     * Returns cut magnitude at lateral distance.
     */
    public static float valleyCutProfile(float centreCut, double lateralDist) {
        if (centreCut <= 0) return 0f;
        double reach = centreCut * 3.0;
        if (lateralDist >= reach) return 0f;
        float raw = (float) (centreCut * (1.0 - lateralDist / reach));
        return Math.max(0f, raw);
    }

    public boolean editable(Dimension dimension, int x, int y) {
        if (dimension == null || !dimension.isTilePresent(x >> 7, y >> 7)) return false;
        if (dimension.getBitLayerValueAt(ReadOnly.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(NotPresent.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(NotPresentBlock.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y)) {
            return false;
        }
        // Existing open water is not reshaped.
        float h = dimension.getHeightAt(x, y);
        if (!Float.isFinite(h)) return false;
        return dimension.getWaterLevelAt(x, y) <= Math.round(h);
    }

    public void apply(Dimension dimension) {
        apply(dimension, () -> {});
    }

    void apply(Dimension dimension, Runnable check) {
        for (var e : delta.entrySet()) {
            check.run();
            long k = e.getKey();
            int x = (int) (k >> 32), y = (int) k;
            float base = original.getOrDefault(k, dimension.getHeightAt(x, y));
            float next = base + e.getValue();
            next = Math.max(dimension.getMinHeight(), Math.min(dimension.getMaxHeight() - 1, next));
            dimension.setHeightAt(x, y, next);
        }
    }

    void restore(Dimension dimension) {
        for (var e : original.entrySet()) {
            long k=e.getKey();
            dimension.setHeightAt((int)(k>>32),(int)k,e.getValue());
        }
    }

    public void merge(TerrainAdjustmentPlan other) {
        if (other == null) return;
        other.bankFloors.forEach((k,v)->bankFloors.merge(k,v,Math::max));
        wetMask.addAll(other.wetMask);
        edgeOutlets.addAll(other.edgeOutlets);
        other.wetLevels.forEach(wetLevels::putIfAbsent);
        for (var e : other.delta.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 32), y = (int) k;
            put(x, y, other.original.get(k), e.getValue());
        }
    }

    void resolveBankFloors(Dimension dimension,Runnable check) {
        // The first (downstream) wet reach owns overlapping junction cells,
        // exactly as addJoiningPath. A later outer grading envelope must not
        // excavate underneath that already chosen common bed.
        for(var e:wetLevels.entrySet()) {
            check.run();long k=e.getKey();int x=(int)(k>>32),y=(int)k;
            if(!editable(dimension,x,y))continue;
            float live=dimension.getHeightAt(x,y);
            float target=e.getValue();
            delta.remove(k);original.remove(k);put(x,y,live,target-live);
        }
        for(var e:bankFloors.entrySet()) {
            check.run();long k=e.getKey();
            if(wetMask.contains(k))continue;
            int x=(int)(k>>32),y=(int)k;
            if(!editable(dimension,x,y))continue;
            float live=dimension.getHeightAt(x,y);
            float target=Math.max(adjustedHeight(x,y,live),e.getValue());
            delta.remove(k);original.remove(k);
            put(x,y,live,target-live);
        }
    }

    public static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /**
     * Build a LIGHT/STRONG valley trough along a centreline so shallow carve fits.
     * Does not write the world.
     */
    public static TerrainAdjustmentPlan alongCentreline(Dimension dimension, Mode mode,
                                                        int[] xs, int[] ys, double localWidth,
                                                        double neededExtraCut, Runnable check) {
        TerrainAdjustmentPlan plan = new TerrainAdjustmentPlan(mode);
        if (xs == null || ys == null || xs.length < 2 || xs.length != ys.length) return plan;
        return alongProfile(dimension,mode,gradeSamples(dimension,xs,ys,localWidth,check),localWidth,check);
    }

    static TerrainAdjustmentPlan alongProfile(Dimension dimension, Mode mode, List<GradeSample> samples,
                                              double localWidth,Runnable check) {
        return alongProfile(dimension,mode,samples,localWidth,1.1,true,Set.of(),check);
    }

    static TerrainAdjustmentPlan alongProfile(Dimension dimension,Mode mode,List<GradeSample> samples,
                                              double localWidth,double depth,boolean smooth,Runnable check) {
        return alongProfile(dimension,mode,samples,localWidth,depth,smooth,Set.of(),check);
    }

    static TerrainAdjustmentPlan alongProfile(Dimension dimension,Mode mode,List<GradeSample> samples,
                                              double localWidth,double depth,boolean smooth,
                                              Set<Long> edgeOutlets,Runnable check) {
        TerrainAdjustmentPlan plan=new TerrainAdjustmentPlan(mode);
        plan.setEdgeOutlets(edgeOutlets);
        Limits lim = plan.limits();
        int bank = lim.bankDistance(localWidth);
        double core = Math.max(1.5, localWidth / 2.0);
        double radius = core + bank;
        // Rasterise the continuous corridor, with the same nearest-segment
        // ownership as the channel. Cross-section stamps leave diagonal holes
        // and their minimum overlap imports a distant downhill level at bends.
        Map<Long, GradeAssignment> assigned = new LinkedHashMap<>();
        Map<Long,GradeAssignment> channelAssigned=new LinkedHashMap<>();
        for (int i=0;i<samples.size()-1;i++) {
            GradeSample a=samples.get(i), b=samples.get(i+1);
            double dx=b.x-a.x, dy=b.y-a.y, len2=dx*dx+dy*dy;
            for(int y=(int)Math.floor(Math.min(a.y,b.y)-radius);y<=Math.ceil(Math.max(a.y,b.y)+radius);y++)
                for(int x=(int)Math.floor(Math.min(a.x,b.x)-radius);x<=Math.ceil(Math.max(a.x,b.x)+radius);x++) {
                    check.run();
                    double raw=len2==0?0:((x-a.x)*dx+(y-a.y)*dy)/len2;
                    double t=Math.max(0,Math.min(1,raw));
                    double distance=Math.hypot(x-a.x-dx*t,y-a.y-dy*t);
                    if(distance>radius)continue;
                    long k=key(x,y);
                    boolean clipped=(i==0&&raw<0&&-raw*Math.sqrt(len2)>.5)
                            ||(i==samples.size()-2&&raw>1&&(raw-1)*Math.sqrt(len2)>.5);
                    double channelRadius=Math.max(1.5,(a.width+(b.width-a.width)*t)/2);
                    var assignment=new GradeAssignment(distance,t<.5?a.water:b.water,clipped,channelRadius);
                    if(distance<=channelRadius+.5) {
                        var oldChannel=channelAssigned.get(k);
                        if(oldChannel==null||oldChannel.distance>distance)channelAssigned.put(k,assignment);
                    }
                    GradeAssignment old=assigned.get(k);
                    if(old==null||old.distance>distance)assigned.put(k,assignment);
                    if(assigned.size()>20_000_000)throw new IllegalArgumentException("Vadi koridoru çalışma verisi sınırına ulaştı; daha küçük bir havza seçin.");
                }
        }
        Map<Long,Float> targets=new LinkedHashMap<>();
        for(var e:assigned.entrySet()) {
            check.run();
            GradeAssignment a=e.getValue();
            int x=(int)(e.getKey()>>32), y=(int)(long)e.getKey();
            if(a.clipped || !plan.editable(dimension,x,y))continue;
            float live=dimension.getHeightAt(x,y);
            double outsideBed=Math.max(0,a.distance-a.core);
            int localBank=lim.bankDistance(a.core*2);
            if(outsideBed>localBank)continue;
            float change=(float)(a.water+outsideBed/3.0-live);
            // Do not build a raised embankment throughout the outer corridor.
            // Fill is reserved for the channel and the explicit supporting
            // boundary voxels below; outer terrain only receives feathered cut.
            if(mode!=Mode.DEEP||outsideBed>0)change=Math.min(0,change);
            change*=edgeFade(outsideBed,localBank);
            targets.put(e.getKey(),live+change);
        }
        if(mode==Mode.DEEP) {
            // A downstream dry edge is NOT an outlet unless the session marked it
            // as an edge mouth. Marked edge outlets may lack a full supporting
            // bank ring toward missing tiles; inland wet cells still get banks.
            for(var e:channelAssigned.entrySet()) {
                check.run();
                GradeAssignment wet=e.getValue();
                if(wet.clipped||!wetAssignment(wet,depth,smooth))continue;
                plan.wetMask.add(e.getKey());
                plan.wetLevels.put(e.getKey(),wet.water);
                int x=(int)(e.getKey()>>32),y=(int)(long)e.getKey();
                boolean edgeMouth=plan.isNearEdgeOutlet(x,y);
                for(int[] offset:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                    int nx=x+offset[0],ny=y+offset[1];long nk=key(nx,ny);
                    GradeAssignment neighbour=channelAssigned.get(nk);
                    if(neighbour!=null&&!neighbour.clipped&&wetAssignment(neighbour,depth,smooth))continue;
                    if(!plan.editable(dimension,nx,ny)) {
                        if(edgeMouth)continue; // water leaves the map; no bank required off-world
                        continue;
                    }
                    plan.bankFloors.merge(nk,(float)wet.water,Math::max);
                    targets.merge(nk,(float)wet.water,Math::max);
                }
            }
        }
        for(var e:targets.entrySet()) {
            check.run();
            int x=(int)(e.getKey()>>32),y=(int)(long)e.getKey();
            float live=dimension.getHeightAt(x,y);
            plan.put(x,y,live,e.getValue()-live);
        }
        return plan;
    }

    private static boolean wetAssignment(GradeAssignment a,double depth,boolean smooth) {
        double bed=a.water-RiverWaterProfile.sectionDepth(a.distance,a.core,depth,!smooth);
        return a.water>Math.round((float)(Math.ceil(bed*256)/256));
    }

    /** Additional cut required by the local downstream profile, not a flat
     * water plane at the final endpoint across the entire mountain stream. */
    static double requiredExtraCut(Dimension dimension, int[] xs, int[] ys,
                                   double width, double depth, Runnable check) {
        double worst = 0;
        int radius = (int) Math.ceil(Math.max(1.5, width / 2));
        for (GradeSample p : gradeSamples(dimension, xs, ys, width, check)) {
            for (int s = -radius; s <= radius; s++) {
                check.run();
                int x = (int)Math.round(p.x + p.nx*s), y = (int)Math.round(p.y + p.ny*s);
                if (!dimension.isTilePresent(x >> 7, y >> 7)) continue;
                float h = dimension.getHeightAt(x,y);
                if (dimension.getWaterLevelAt(x,y) > Math.round(h)) continue;
                worst = Math.max(worst, RiverWaterProfile.requiredCut(h, p.water, depth) - (depth + .75));
            }
        }
        return Math.max(0, worst);
    }

    record GradeSample(double x, double y, double nx, double ny, int water,double width) {}
    private record GradeAssignment(double distance, int water, boolean clipped,double core) {}

    static List<GradeSample> gradeSamples(Dimension d, int[] xs, int[] ys,
                                                 double width, Runnable check) {
        return gradeSamples(d,xs,ys,width,width,false,check);
    }

    static List<GradeSample> gradeSamples(Dimension d,int[] xs,int[] ys,double width,double endWidth,boolean joining,Runnable check) {
        List<GradeSample> result = new ArrayList<>();
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{xs[0],ys[0]});
        int water = Integer.MAX_VALUE;
        for (int i=1; i<xs.length; i++) {
            double dx=(double)xs[i]-xs[i-1], dy=(double)ys[i]-ys[i-1];
            double length=Math.hypot(dx,dy);
            if(length==0) continue;
            if(length*2+points.size()>20_000_000) throw new IllegalArgumentException("Vadi profili çok büyük; daha küçük bir çizim seçin.");
            int steps=(int)Math.ceil(length*2);
            for(int j=1;j<=steps;j++) {
                check.run();
                double x=xs[i-1]+dx*j/steps, y=ys[i-1]+dy*j/steps;
                points.add(new double[]{x,y});
            }
        }
        // Match the carver's centred tangent at raster bends, rather than
        // changing the cross-section abruptly with each input segment.
        double[] distanceToEnd=new double[points.size()];
        for(int i=points.size()-2;i>=0;i--)distanceToEnd[i]=distanceToEnd[i+1]+Math.hypot(points.get(i+1)[0]-points.get(i)[0],points.get(i+1)[1]-points.get(i)[1]);
        for(int i=0;i<points.size();i++) {
                check.run();
                double[] p=points.get(i), a=points.get(Math.max(0,i-1)), b=points.get(Math.min(points.size()-1,i+1));
                double length=Math.max(.001,Math.hypot(b[0]-a[0],b[1]-a[1]));
                double nx=-(b[1]-a[1])/length, ny=(b[0]-a[0])/length;
                double x=p[0], y=p[1];
                double t=joining?Math.max(0,Math.min(1,1-distanceToEnd[i]/(4*endWidth))):i/(double)Math.max(1,points.size()-1);
                double localWidth=width+(endWidth-width)*t*t*(3-2*t);
                int radius=(int)Math.ceil(Math.max(1.5,localWidth/2));
                int cx=(int)Math.round(x), cy=(int)Math.round(y);
                int upper=Integer.MAX_VALUE;
                if(d.isTilePresent(cx>>7,cy>>7)) {
                    float ch=d.getHeightAt(cx,cy);
                    int cw=d.getWaterLevelAt(cx,cy);
                    upper=cw>Math.round(ch)?cw:RiverWaterProfile.dryWaterCeiling(ch);
                }
                for(int s=-radius;s<=radius;s++) {
                    int bx=(int)Math.round(x+nx*s), by=(int)Math.round(y+ny*s);
                    if(!d.isTilePresent(bx>>7,by>>7)) continue;
                    float h=d.getHeightAt(bx,by);
                    if(!Float.isFinite(h)) continue;
                    int w=d.getWaterLevelAt(bx,by);
                    // A wet bank is receiving water, not a low seabed sample.
                    // Its fixed level is handled at the outlet, as in carving.
                    if(w<=Math.round(h))upper=Math.min(upper,RiverWaterProfile.dryWaterCeiling(h));
                }
                if(upper==Integer.MAX_VALUE) continue;
                water=Math.min(water,upper);
                result.add(new GradeSample(x,y,nx,ny,water,localWidth));
        }
        int[] levels=new int[result.size()];
        double[] chainage=new double[result.size()];
        for(int i=0;i<result.size();i++) {
            levels[i]=result.get(i).water;
            if(i>0)chainage[i]=chainage[i-1]+Math.hypot(result.get(i).x-result.get(i-1).x,result.get(i).y-result.get(i-1).y);
        }
        RiverWaterProfile.spreadDrops(levels,chainage,RiverWaterProfile.gradeStepSpacing(width));
        for(int i=0;i<result.size();i++) {
            GradeSample p=result.get(i);
            result.set(i,new GradeSample(p.x,p.y,p.nx,p.ny,levels[i],p.width));
        }
        return result;
    }
}
