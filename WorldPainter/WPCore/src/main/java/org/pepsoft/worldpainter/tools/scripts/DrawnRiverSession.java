package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Version;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Detached drawn-river network plan. {@link #prepare()} is the shared
 * preserve-then-joint-deep entry. World writes only on {@link #apply()}.
 */
public final class DrawnRiverSession {
    public static final long PREPARE_BUDGET_NS = 300_000_000_000L;
    public static final long PRESERVE_CAP_NS = 120_000_000_000L;

    public enum Stage { PRESERVE, CORRIDOR, LIGHT_TERRAIN, STRONG_TERRAIN, DEEP_TERRAIN }

    public enum UiClass {
        OK, UNRESOLVED_DRAWING, TERRAIN_UNSUITABLE, AMBIGUOUS_OUTLET
    }

    public record Preview(List<ShallowRiverCarver.PreviewCell> cells,
                          List<RiverSearchResult.Course> courses,
                          List<DrawnRiverGraph.Pixel> suggestedCentreline,
                          List<TerrainAdjustmentPlan.Summary> terrainSummaries,
                          List<String> messages,
                          List<String> blockers,
                          Set<DrawnRiverGraph.Pixel> drawing,
                          Set<DrawnRiverGraph.Pixel> cutFillCells,
                          Set<DrawnRiverGraph.Pixel> repairs,
                          List<DrawnRiverNormalizer.Diagnostic> diagnostics,
                          int sources, int junctions, int rejectedGroups, int terrainRejected,
                          Stage stageReached,
                          boolean strongSuggested,
                          UiClass uiClass,
                          double maxExtraCut,
                          String identity) {
        public Preview {
            cells = List.copyOf(cells);
            courses = List.copyOf(courses);
            suggestedCentreline = suggestedCentreline == null ? List.of() : List.copyOf(suggestedCentreline);
            terrainSummaries = terrainSummaries == null ? List.of() : List.copyOf(terrainSummaries);
            messages = List.copyOf(messages);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            drawing = Set.copyOf(drawing);
            cutFillCells = cutFillCells == null ? Set.of() : Set.copyOf(cutFillCells);
            repairs = repairs == null ? Set.of() : Set.copyOf(repairs);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
            if (maxExtraCut < 0) {
                maxExtraCut = terrainSummaries.stream().mapToDouble(TerrainAdjustmentPlan.Summary::maxCut).max().orElse(0);
            }
            identity = identity == null || identity.isBlank() ? DrawnRiverSession.identity() : identity;
        }
        public boolean canApply() { return !courses.isEmpty() && !cells.isEmpty(); }
        /** Backward-compatible alias for rejected drawing groups + terrain rejects. */
        public int rejected() { return rejectedGroups + terrainRejected; }
        public int skippedGroups() { return rejectedGroups; }
    }

    public static String identity() {
        return Version.VERSION + " (" + Version.BUILD + ") · nehri-hazırla/joint-16";
    }

    public DrawnRiverSession(Dimension dimension, Layer layer, double sourceWidth, double maximumWidth,
                             double depth, boolean smooth, BooleanSupplier cancelled) {
        this(dimension, layer, sourceWidth, maximumWidth, depth, smooth, true, dimension.getSeed(), cancelled);
    }
    public DrawnRiverSession(Dimension dimension, Layer layer, double sourceWidth, double maximumWidth,
                             double depth, boolean smooth, boolean granite, long seed, BooleanSupplier cancelled) {
        this(dimension, layer, sourceWidth, maximumWidth, depth, smooth, granite, true, seed, cancelled);
    }
    public DrawnRiverSession(Dimension dimension, Layer layer, double sourceWidth, double maximumWidth,
                             double depth, boolean smooth, boolean granite, boolean waterlineBankDetail,
                             long seed, BooleanSupplier cancelled) {
        this.dimension = dimension;
        this.layer = layer;
        this.sourceWidth = sourceWidth;
        this.maximumWidth = maximumWidth;
        this.depth = depth;
        this.smooth = smooth;
        this.granite = granite;
        this.waterlineBankDetail = waterlineBankDetail;
        this.seed = seed;
        this.cancelled = cancelled;
        revision = dimension.getChangeNo();
        if (layer.getDataSize() != Layer.DataSize.BIT) throw new IllegalArgumentException("Çizim katmanı BIT olmalı.");
    }

    public Preview search() { return search(false); }

    /**
     * Shared menu/script entry: preserve (≤120 s) then joint deep valley with
     * remaining time of a single 5-minute budget. Does not reset the clock.
     */
    public Preview prepare() {
        return prepare(this::finishDeep);
    }

    Preview prepare(DeepCompute deepStage) {
        preview = null;
        applied = false;
        computing = true;
        excludedAtPreview = excludedEdges;
        drainAtPreview = drain;
        overrideAtPreview = outletOverride;
        long started = System.nanoTime();
        deadline = started + PREPARE_BUDGET_NS;
        preserveCap = started + PRESERVE_CAP_NS;
        try {
            List<DrawnRiverGraph.Pixel> drawing = collectDrawing();
            var graph = buildGraph(drawing);
            graph = connectCloseDrawingEnds(graph, drawing);
            graph = collapseCoveredSpurs(graph);
            if (drawing.isEmpty()) return finishPreserve(graph, drawing, false);

            Preview preserved = null;
            ShallowRiverCarver preservePlan = null;
            TerrainAdjustmentPlan preserveTerrain = null;
            preservePhase = true;
            try {
                preserved = finishPreserve(graph, drawing, false);
                preservePlan = plan;
                preserveTerrain = terrainPlan;
            } catch (PhaseBudgetExceeded e) {
                preserved = preview;
                preservePlan = plan;
                preserveTerrain = terrainPlan;
                if (preserved != null) {
                    preserved = restorePreserved(preserved, preservePlan, preserveTerrain,
                            List.of("Koruma aşaması 120 sn doldu; kalan süreyle ortak vadi deneniyor. Sayaç sıfırlanmadı."));
                }
            } finally {
                preservePhase = false;
            }
            if (preserved != null && preserveComplete(preserved, graph)) {
                return restorePreserved(preserved, preservePlan, preserveTerrain,
                        List.of("Derin kazı gerekmedi; araziyi koruyan çözüm bütün uygulanabilir kolları tamamladı."));
            }

            Preview deep;
            try {
                deep = deepStage.compute(graph, drawing);
            } catch (RuntimeException e) {
                if (preserved != null && preserved.canApply()) {
                    String why = e instanceof CancellationException
                            ? "Ortak vadi tamamlanamadı; önceki doğrulanmış sonuç korunuyor. Dünya değiştirilmedi."
                            : "Ortak vadi hesap hatası; önceki doğrulanmış sonuç korunuyor. Dünya değiştirilmedi.";
                    return restorePreserved(preserved, preservePlan, preserveTerrain,
                            List.of(String.valueOf(e.getMessage()), why));
                }
                throw e;
            }
            if (keepsPreservedArms(deep, preserved)) {
                preview = deep;
                return preview;
            }
            if (preserved != null && preserved.canApply()) {
                return restorePreserved(preserved, preservePlan, preserveTerrain,
                        List.of("Ortak vadi önceki doğrulanmış kolları korumadı; önceki sonuç korunuyor."));
            }
            preview = deep;
            return preview;
        } finally {
            computing = false;
        }
    }

    /** Explicit larger earthworks proposal; the ordinary modes retain their
     * original limits. Still detached until the caller explicitly applies it. */
    public Preview searchDeepValley() {
        preview = null;
        applied = false;
        computing = true;
        excludedAtPreview = excludedEdges;
        drainAtPreview = drain;
        overrideAtPreview = outletOverride;
        deadline = System.nanoTime() + PREPARE_BUDGET_NS;
        preserveCap = deadline;
        try {
            var drawing = collectDrawing();
            var graph = buildGraph(drawing);
            graph = connectCloseDrawingEnds(graph, drawing);
            graph = collapseCoveredSpurs(graph);
            return finishDeep(graph, drawing);
        } finally {
            computing = false;
        }
    }

    public Preview search(boolean strong) {
        preview = null;
        applied = false;
        computing = true;
        excludedAtPreview = excludedEdges;
        drainAtPreview = drain;
        overrideAtPreview = outletOverride;
        deadline = System.nanoTime() + (strong ? PREPARE_BUDGET_NS : PRESERVE_CAP_NS);
        preserveCap = deadline;
        try {
            List<DrawnRiverGraph.Pixel> drawing = collectDrawing();
            var graph = buildGraph(drawing);
            return finishPreserve(graph, drawing, strong);
        } finally {
            computing = false;
        }
    }

    private DrawnRiverGraph.Result buildGraph(List<DrawnRiverGraph.Pixel> drawing) {
        var hints = collectStrokeHints();
        return DrawnRiverGraph.build(drawing,
                p -> dimension.getHeightAt(p.x(), p.y()),
                p -> dimension.getWaterLevelAt(p.x(), p.y()) > dimension.getIntHeightAt(p.x(), p.y()),
                sourceWidth, maximumWidth, this::blockedCell, excludedEdges,
                DrawnRiverGraph.OutletPolicy.of(drain, outletOverride, this::windowedHeightAt, this::atWorldEdge),
                hints,
                this::check);
    }

    private double windowedHeightAt(DrawnRiverGraph.Pixel p) {
        return DrawnRiverGraph.windowedHeight(p, q -> {
            if (!dimension.isTilePresent(q.x() >> 7, q.y() >> 7)) return Double.NaN;
            float h = dimension.getHeightAt(q.x(), q.y());
            return Float.isFinite(h) ? h : Double.NaN;
        });
    }

    /** True when a neighbouring tile is missing (map boundary or unloaded). */
    private boolean atWorldEdge(DrawnRiverGraph.Pixel p) {
        if (!dimension.isTilePresent(p.x() >> 7, p.y() >> 7)) return false;
        for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int nx = p.x() + o[0], ny = p.y() + o[1];
            if (!dimension.isTilePresent(nx >> 7, ny >> 7)) return true;
        }
        return false;
    }

    private static boolean preserveComplete(Preview preserved, DrawnRiverGraph.Result graph) {
        if (preserved == null || !preserved.canApply()) return false;
        int applicable = graph.basins().stream().mapToInt(b -> b.downstreamFirst().size()).sum();
        return preserved.terrainRejected() == 0 && preserved.courses().size() >= applicable;
    }

    /** Next network must keep every previously validated arm, not merely add pieces. */
    static boolean keepsPreservedArms(Preview next, Preview previous) {
        if (next == null || !next.canApply()) return false;
        if (previous == null || !previous.canApply()) return true;
        return keepsPreservedArms(next.courses(), previous.courses());
    }

    static boolean keepsPreservedArms(List<RiverSearchResult.Course> next, List<RiverSearchResult.Course> previous) {
        if (next == null || next.isEmpty()) return false;
        if (previous == null || previous.isEmpty()) return true;
        Set<DrawnRiverGraph.Pixel> covered = new HashSet<>();
        for (var course : next) for (var p : course.centreline())
            covered.add(new DrawnRiverGraph.Pixel(p.x(), p.y()));
        for (var course : previous) {
            if (course.centreline().isEmpty()) return false;
            var start = course.centreline().getFirst();
            if (!nearCovered(covered, start.x(), start.y(), 2)) return false;
        }
        return true;
    }

    private static boolean nearCovered(Set<DrawnRiverGraph.Pixel> covered, int x, int y, int radius) {
        for (int dy = -radius; dy <= radius; dy++)
            for (int dx = -radius; dx <= radius; dx++)
                if (covered.contains(new DrawnRiverGraph.Pixel(x + dx, y + dy))) return true;
        return false;
    }

    private Preview restorePreserved(Preview preserved, ShallowRiverCarver preservePlan,
                                     TerrainAdjustmentPlan preserveTerrain, List<String> extraLines) {
        var extra = new ArrayList<>(preserved.messages());
        extra.addAll(extraLines);
        plan = preservePlan;
        terrainPlan = preserveTerrain;
        preview = withMessages(preserved, extra, preserved.blockers());
        return preview;
    }

    private static Preview withMessages(Preview p, List<String> messages, List<String> blockers) {
        return new Preview(p.cells(), p.courses(), p.suggestedCentreline(), p.terrainSummaries(),
                messages, blockers, p.drawing(), p.cutFillCells(), p.repairs(), p.diagnostics(),
                p.sources(), p.junctions(), p.rejectedGroups(), p.terrainRejected(),
                p.stageReached(), p.strongSuggested(), p.uiClass(), p.maxExtraCut(), p.identity());
    }

    private Preview finishDeep(DrawnRiverGraph.Result graph, List<DrawnRiverGraph.Pixel> drawing) {
        terrainPlan = new TerrainAdjustmentPlan(TerrainAdjustmentPlan.Mode.DEEP);
        Set<Long> edgeKeys = new HashSet<>();
        for (var basin : graph.basins()) {
            if (basin.edgeOutlet() && basin.outlet() != null)
                edgeKeys.add(TerrainAdjustmentPlan.key(basin.outlet().x(), basin.outlet().y()));
        }
        terrainPlan.setEdgeOutlets(edgeKeys);
        var result = DrawnRiverJointPlanner.plan(dimension, graph, depth, smooth, this::makeCarver, this::check);
        plan = result.carver();
        terrainPlan = result.terrain();
        var messages = new ArrayList<>(graph.warnings());
        messages.addAll(graph.orientationNotes());
        messages.add("Ortak vadi: ek arazi kazısı en fazla 16 blok, dolgu en fazla 2 blok. Kanal kazısı ayrıca doğrulanır. Dünya değişmedi.");
        messages.add("Kolların su kotları ortak çözüldü; genişlik çizilen kaynak katkısıyla büyür. Bu bir fiziksel debi simülasyonu değildir.");
        messages.addAll(result.messages());
        messages.add(plan.dirtBedSummary());
        messages.add(plan.waterlineBankSummary());
        var summary = terrainPlan.summary();
        messages.add("Önerilen arazi: " + summary.cells() + " hücre; azami ek kazı=" + summary.maxCut()
                + "; azami dolgu=" + summary.maxFill());
        var cutFill = new HashSet<DrawnRiverGraph.Pixel>();
        for (long k : terrainPlan.cells()) cutFill.add(new DrawnRiverGraph.Pixel((int) (k >> 32), (int) k));
        var suggested = result.courses().stream().flatMap(c -> c.centreline().stream())
                .map(p -> new DrawnRiverGraph.Pixel(p.x(), p.y())).toList();
        int invalid = countInvalid(graph);
        check();
        preview = new Preview(plan.previewCells(), result.courses(), suggested,
                terrainPlan.isEmpty() ? List.of() : List.of(summary),
                messages, List.of(), Set.copyOf(drawing), cutFill, graph.repairs(), graph.diagnostics(),
                graph.basins().stream().mapToInt(DrawnRiverGraph.Basin::sources).sum(),
                graph.basins().stream().mapToInt(DrawnRiverGraph.Basin::junctions).sum(), invalid, result.rejected(),
                Stage.DEEP_TERRAIN, false, result.courses().isEmpty() ? UiClass.TERRAIN_UNSUITABLE : UiClass.OK,
                summary.maxCut(), identity());
        return preview;
    }

    private DrawnRiverGraph.Result connectCloseDrawingEnds(DrawnRiverGraph.Result graph,List<DrawnRiverGraph.Pixel> drawing) {
        if(!excludedEdges.isEmpty())return graph;
        var mask=new HashSet<>(drawing);var added=new HashSet<DrawnRiverGraph.Pixel>();
        for(var basin:graph.basins()) {
            if(basin.downstreamFirst().isEmpty())continue;
            var end=basin.downstreamFirst().getFirst().pixels().getLast();
            if(dimension.getWaterLevelAt(end.x(),end.y())>dimension.getIntHeightAt(end.x(),end.y()))continue;
            DrawnRiverGraph.Pixel target=null;double closest=4.000001;
            for(var other:graph.basins()) {
                if(other==basin||other.downstreamFirst().isEmpty())continue;
                var root=other.downstreamFirst().getFirst().pixels().getLast();
                if(dimension.getWaterLevelAt(root.x(),root.y())<=dimension.getIntHeightAt(root.x(),root.y()))continue;
                for(var reach:other.downstreamFirst())for(var p:reach.pixels()) {
                    check();double distance=Math.hypot(p.x()-end.x(),p.y()-end.y());
                    if(distance<closest){closest=distance;target=p;}
                }
            }
            if(target==null)continue;
            var bridge=densifyLine(end,target);
            if(bridge.stream().anyMatch(p->!dimension.isTilePresent(p.x()>>7,p.y()>>7)||blockedCell(p)))continue;
            for(var p:bridge)if(mask.add(p))added.add(p);
        }
        if(added.isEmpty())return graph;
        var rebuilt=DrawnRiverGraph.build(mask,p->dimension.getHeightAt(p.x(),p.y()),
                p->dimension.getWaterLevelAt(p.x(),p.y())>dimension.getIntHeightAt(p.x(),p.y()),
                sourceWidth,maximumWidth,this::blockedCell,excludedEdges,
                DrawnRiverGraph.OutletPolicy.of(drain,outletOverride,this::windowedHeightAt,this::atWorldEdge),
                collectStrokeHints(),
                this::check);
        long beforeLoops=graph.diagnostics().stream().filter(d->d.kind()==DrawnRiverNormalizer.IssueKind.REAL_LOOP).count();
        long afterLoops=rebuilt.diagnostics().stream().filter(d->d.kind()==DrawnRiverNormalizer.IssueKind.REAL_LOOP).count();
        if(afterLoops>beforeLoops||rebuilt.basins().size()>=graph.basins().size())return graph;
        var repairs=new HashSet<>(rebuilt.repairs());repairs.addAll(added);
        var warnings=new ArrayList<>(rebuilt.warnings());
        warnings.add("Çizimde en fazla 4 blokluk kopuk uç tamamlandı: "+added.size()+" hücre. Mor önizleme; çizim katmanı değiştirilmedi.");
        return new DrawnRiverGraph.Result(rebuilt.basins(),warnings,rebuilt.skeleton(),rebuilt.diagnostics(),
                rebuilt.junctionRegions(),repairs,rebuilt.orientationNotes());
    }

    private DrawnRiverGraph.Result collapseCoveredSpurs(DrawnRiverGraph.Result graph) {
        if(!excludedEdges.isEmpty())return graph;
        var mask=new HashSet<>(graph.skeleton());int covered=0;
        for(var basin:graph.basins()) {
            var outgoing=new HashMap<DrawnRiverGraph.Pixel,DrawnRiverGraph.Reach>();
            for(var r:basin.downstreamFirst())outgoing.put(r.pixels().getFirst(),r);
            for(var r:basin.downstreamFirst()) {
                check();if(r.contributors()!=1)continue;
                var parent=outgoing.get(r.pixels().getLast());if(parent==null)continue;
                double length=0;
                for(int i=1;i<r.pixels().size();i++)length+=Math.hypot(r.pixels().get(i).x()-r.pixels().get(i-1).x(),r.pixels().get(i).y()-r.pixels().get(i-1).y());
                if(length>2*sourceWidth)continue;
                double radius=Math.max(1.5,parent.width()/2);
                boolean inside=true;
                for(var p:r.pixels()) {
                    double nearest=Double.POSITIVE_INFINITY;
                    for(int i=1;i<parent.pixels().size();i++) {
                        var a=parent.pixels().get(i-1);var b=parent.pixels().get(i);
                        double dx=b.x()-a.x(),dy=b.y()-a.y(),len=dx*dx+dy*dy;
                        double t=len==0?0:Math.max(0,Math.min(1,((p.x()-a.x())*dx+(p.y()-a.y())*dy)/len));
                        nearest=Math.min(nearest,Math.hypot(p.x()-a.x()-dx*t,p.y()-a.y()-dy*t));
                    }
                    // Use the exported wet footprint, not just the inner 60%
                    // core: the rounded rim also contains a full water voxel.
                    double bed=-RiverWaterProfile.sectionDepth(nearest,radius,depth,!smooth);
                    if(Math.round((float)(Math.ceil(bed*256)/256))>=0){inside=false;break;}
                }
                if(inside) {
                    mask.removeAll(r.pixels().subList(0,r.pixels().size()-1));covered++;
                }
            }
        }
        if(covered==0)return graph;
        var rebuilt=DrawnRiverGraph.build(mask,p->dimension.getHeightAt(p.x(),p.y()),
                p->dimension.getWaterLevelAt(p.x(),p.y())>dimension.getIntHeightAt(p.x(),p.y()),
                sourceWidth,maximumWidth,this::blockedCell,excludedEdges,
                DrawnRiverGraph.OutletPolicy.of(drain,outletOverride,this::windowedHeightAt,this::atWorldEdge),
                collectStrokeHints(),
                this::check);
        var warnings=new ArrayList<>(graph.warnings());
        warnings.add("Ana ıslak yatağın tamamıyla içinde kalan "+covered+" kısa çizgi ayrı kaynak sayılmadı; ana kanal korunur.");
        var repairs=new HashSet<>(graph.repairs());repairs.addAll(rebuilt.repairs());
        return new DrawnRiverGraph.Result(rebuilt.basins(),warnings,rebuilt.skeleton(),rebuilt.diagnostics(),
                rebuilt.junctionRegions(),repairs,rebuilt.orientationNotes());
    }

    private Preview finishPreserve(DrawnRiverGraph.Result graph, List<DrawnRiverGraph.Pixel> drawing, boolean strong) {
        TerrainAdjustmentPlan.Mode mode = strong ? TerrainAdjustmentPlan.Mode.STRONG : TerrainAdjustmentPlan.Mode.LIGHT;
        terrainPlan = new TerrainAdjustmentPlan(mode);
        List<String> messages = new ArrayList<>(graph.warnings());
        messages.addAll(graph.orientationNotes());
        List<String> blockers = new ArrayList<>();
        messages.add("Genişlik, çizilen kaynak katkısından hesaplandı; fiziksel debi/havza ölçümü değildir.");
        int rejectedGroups = 0;
        for (var d : graph.diagnostics()) {
            if (d.kind() == DrawnRiverNormalizer.IssueKind.REAL_LOOP
                    || d.kind() == DrawnRiverNormalizer.IssueKind.MULTI_OUTLET
                    || d.kind() == DrawnRiverNormalizer.IssueKind.UNRESOLVED) {
                blockers.add(d.message());
                rejectedGroups++;
            } else if (d.kind() == DrawnRiverNormalizer.IssueKind.SMALL_GAP_REPAIRED
                    || d.kind() == DrawnRiverNormalizer.IssueKind.JUNCTION_REGION) {
                messages.add(d.message());
            }
        }
        if (drawing.isEmpty()) {
            messages.add("River Path / Mini / Continue üzerinde çizim yok.");
            preview = new Preview(List.of(), List.of(), List.of(), List.of(), messages, blockers,
                    Set.copyOf(drawing), Set.of(), Set.of(), graph.diagnostics(),
                    0, 0, 0, 0, Stage.PRESERVE, false, UiClass.UNRESOLVED_DRAWING, 0, identity());
            return preview;
        }

        DrawnRiverRoutePlanner router = new DrawnRiverRoutePlanner(dimension, this::check);
        // Pass A: no terrain overlay.
        PassResult passA = runPass(graph, router, null, mode, false);
        Stage reached = passA.stage;
        boolean needsStrong = passA.needsStrong;

        // A recovered trunk exposes upstream branches that were previously
        // skipped. Grade those in subsequent full rebuilds under ONE deadline.
        Map<DrawnRiverGraph.Reach, TerrainAdjustmentPlan> grades = new LinkedHashMap<>();
        PassResult pass = passA;
        int rounds = graph.basins().stream().mapToInt(b -> b.downstreamFirst().size()).sum() + 1;
        for (int round=0; round<rounds && !pass.failed.isEmpty(); round++) {
            boolean added = false;
            for (FailedReach fr : pass.failed) {
                check();
                if (grades.containsKey(fr.reach)) continue;
                double extra = estimateExtraCut(fr.path, fr.reach.width());
                double lim = TerrainAdjustmentPlan.Limits.of(mode).maxCut();
                if (!strong && extra > lim + 0.05) {
                    needsStrong = true;
                    blockers.add("Kol " + fr.path.get(0) + ": hafif arazi sınırı yetersiz (~"
                            + String.format("%.1f", extra) + " blok).");
                    continue;
                }
                int[] xs = fr.path.stream().mapToInt(DrawnRiverGraph.Pixel::x).toArray();
                int[] ys = fr.path.stream().mapToInt(DrawnRiverGraph.Pixel::y).toArray();
                TerrainAdjustmentPlan grade = TerrainAdjustmentPlan.alongCentreline(
                        dimension, mode, xs, ys, fr.reach.width(), Math.max(extra, 0.5), this::check);
                if (!grade.isEmpty()) { grades.put(fr.reach, grade); added = true; }
            }
            if (!added) break;
            terrainPlan = new TerrainAdjustmentPlan(mode);
            for (TerrainAdjustmentPlan grade : grades.values()) terrainPlan.merge(grade);
            PassResult passB = runPass(graph, router, terrainPlan, mode, true);
            if (!passB.failed.isEmpty()) {
                messages.add("Vadi doğrulaması " + (round + 1) + ": " + passB.courses.size()
                        + " parça kabul, " + passB.rejected + " parça red/bağımlı.");
                for (String detail : passB.messages) if (detail.startsWith("Kol ")) messages.add("Düzenleme sonrası: " + detail);
            }
            if (passB.acceptedReaches.containsAll(pass.acceptedReaches)) {
                pass = passB;
                reached = strong ? Stage.STRONG_TERRAIN : Stage.LIGHT_TERRAIN;
            } else {
                messages.add("Ek vadi düzenlemesi doğrulanmış kolları bozdu; uygulanmayacak.");
                break;
            }
            needsStrong = needsStrong || passB.needsStrong;
        }

        // Never apply orange earthworks belonging only to rejected branches.
        terrainPlan = new TerrainAdjustmentPlan(mode);
        for (var e : grades.entrySet()) if (pass.acceptedReaches.contains(e.getKey())) terrainPlan.merge(e.getValue());
        if (!terrainPlan.isEmpty()) {
            PassResult verified = runPass(graph, router, terrainPlan, mode, true);
            if (verified.acceptedReaches.containsAll(pass.acceptedReaches)) pass = verified;
            else { terrainPlan = new TerrainAdjustmentPlan(mode); pass = passA; }
        } else pass = passA;
        reached = terrainPlan.isEmpty() ? pass.stage : (strong ? Stage.STRONG_TERRAIN : Stage.LIGHT_TERRAIN);

        plan = pass.carver;
        messages.addAll(pass.messages);
        blockers.addAll(pass.blockers);
        if (pass.courses.isEmpty()) {
            blockers.add("Uygulanabilir güzergâh bulunamadı.");
            if (!strong) messages.add("Güçlü arazi önerisini deneyebilirsiniz (ayrı hesap, 5 dk).");
        }
        check();
        if (!plan.isFootprintAllowed(null)) throw new IllegalStateException("Plan korunan alanla çakışıyor.");
        messages.add(plan.dirtBedSummary());
        messages.add(plan.waterlineBankSummary());
        if (!terrainPlan.isEmpty()) {
            var s = terrainPlan.summary();
            messages.add("Arazi düzenlemesi (" + s.mode() + "): " + s.cells() + " hücre; max kazı="
                    + String.format("%.2f", s.maxCut()) + ", max dolgu=" + String.format("%.2f", s.maxFill()));
        }
        if (needsStrong && !strong && !preservePhase) {
            messages.add("Bazı kollar için Güçlü arazi önerisi gerekebilir.");
        }

        Set<DrawnRiverGraph.Pixel> cutFill = new HashSet<>();
        for (long k : terrainPlan.cells()) cutFill.add(new DrawnRiverGraph.Pixel((int) (k >> 32), (int) k));

        UiClass ui = classifyUi(graph, pass, rejectedGroups);
        double cut = terrainPlan.isEmpty() ? 0 : terrainPlan.summary().maxCut();
        preview = new Preview(plan.previewCells(), pass.courses, pass.suggested,
                terrainPlan.isEmpty() ? List.of() : List.of(terrainPlan.summary()),
                messages, blockers, Set.copyOf(drawing), cutFill, graph.repairs(), graph.diagnostics(),
                pass.sources, pass.junctions, rejectedGroups, pass.rejected, reached, needsStrong && !strong, ui,
                cut, identity());
        return preview;
    }

    /** Optional preview-only edge exclusion; does not write River Path. Rebuilds on next search. */
    public void setExcludedEdges(Set<DrawnRiverGraph.Edge> edges) {
        if (computing) return;
        excludedEdges = edges == null ? Set.of() : Set.copyOf(edges);
        if (preview == null) excludedAtPreview = excludedEdges;
    }

    public Set<DrawnRiverGraph.Edge> excludedEdges() { return excludedEdges; }

    /** Preferred drain direction for outlet selection (AUTO = legacy). */
    public void setDrain(DrawnRiverGraph.Drain drain) {
        if (computing) return;
        this.drain = drain == null ? DrawnRiverGraph.Drain.AUTO : drain;
        if (preview == null) drainAtPreview = this.drain;
    }

    public DrawnRiverGraph.Drain drain() { return drain; }

    /** Manual outlet pixel; nearest degree-1 end within 2 blocks wins. */
    public void setOutletOverride(DrawnRiverGraph.Pixel pixel) {
        if (computing) return;
        this.outletOverride = pixel;
        if (preview == null) overrideAtPreview = pixel;
    }

    public DrawnRiverGraph.Pixel outletOverride() { return outletOverride; }

    private UiClass classifyUi(DrawnRiverGraph.Result graph, PassResult pass, int rejectedGroups) {
        boolean multi = graph.diagnostics().stream()
                .anyMatch(d -> d.kind() == DrawnRiverNormalizer.IssueKind.MULTI_OUTLET);
        if (multi) return UiClass.AMBIGUOUS_OUTLET;
        boolean unresolved = rejectedGroups > 0 && pass.courses.isEmpty();
        if (unresolved || graph.basins().isEmpty() && !graph.diagnostics().isEmpty())
            return UiClass.UNRESOLVED_DRAWING;
        if (pass.courses.isEmpty() || pass.rejected > 0 && pass.courses.isEmpty())
            return UiClass.TERRAIN_UNSUITABLE;
        if (pass.rejected > 0 && pass.courses.isEmpty()) return UiClass.TERRAIN_UNSUITABLE;
        return UiClass.OK;
    }

    private boolean blockedCell(DrawnRiverGraph.Pixel p) {
        int x = p.x(), y = p.y();
        return dimension.getBitLayerValueAt(ReadOnly.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(NotPresent.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(NotPresentBlock.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y);
    }

    public boolean isStale() {
        return dimension.getChangeNo() != revision
                || !excludedEdges.equals(excludedAtPreview)
                || drain != drainAtPreview
                || !java.util.Objects.equals(outletOverride, overrideAtPreview);
    }
    public boolean hasStrongSuggestion() { return preview != null && preview.strongSuggested(); }
    public TerrainAdjustmentPlan terrainAdjustmentPlan() { return terrainPlan; }

    public ShallowRiverCarver.Result apply() {
        if (preview == null || !preview.canApply() || applied) throw new IllegalStateException("Uygulanabilir önizleme yok.");
        if (isStale()) throw new IllegalStateException("Dünya değişti; çizimi yeniden planlayın.");
        if (!dimension.isUndoAvailable()) throw new IllegalStateException("Undo açık olmalı.");
        applying = true;
        applied = true;
        boolean ownsEvents = !dimension.isEventsInhibited();
        dimension.rememberChanges();
        if (ownsEvents) dimension.setEventsInhibited(true);
        try {
            if (!plan.isFootprintAllowed(null)) throw new IllegalStateException("Plan korunan alanla çakışıyor.");
            if (terrainPlan != null && !terrainPlan.isEmpty()) terrainPlan.apply(dimension, this::check);
            return plan.apply();
        } catch (RuntimeException | Error failure) {
            // The carver restores its writes to the graded snapshot. Restore
            // the grading as well, including failures before carving starts.
            if (terrainPlan != null && !terrainPlan.isEmpty()) {
                try { terrainPlan.restore(dimension); }
                catch (RuntimeException | Error rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        } finally {
            dimension.armSavePoint();
            if (ownsEvents) dimension.setEventsInhibited(false);
            applying = false;
        }
    }

    private PassResult runPass(DrawnRiverGraph.Result graph, DrawnRiverRoutePlanner router,
                               TerrainAdjustmentPlan overlay, TerrainAdjustmentPlan.Mode mode,
                               boolean withTerrainStage) {
        ShallowRiverCarver carver = makeCarver();
        if (overlay != null && !overlay.isEmpty()) {
            carver.setPlanningHeightOverlay((x, y) -> overlay.adjustedHeight(x, y, dimension.getHeightAt(x, y)));
        }
        List<RiverSearchResult.Course> courses = new ArrayList<>();
        List<DrawnRiverGraph.Pixel> suggested = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<FailedReach> failed = new ArrayList<>();
        Set<DrawnRiverGraph.Reach> acceptedReaches = new HashSet<>();
        int sources = 0, junctions = 0, rejected = 0;
        Stage stage = withTerrainStage
                ? (mode == TerrainAdjustmentPlan.Mode.STRONG ? Stage.STRONG_TERRAIN : Stage.LIGHT_TERRAIN)
                : Stage.PRESERVE;
        boolean needsStrong = false;
        Set<DrawnRiverGraph.Pixel> connected = new HashSet<>();
        Map<DrawnRiverGraph.Pixel, Double> junctionWidths = new HashMap<>();
        Map<DrawnRiverGraph.Pixel, DrawnRiverGraph.Pixel> junctionRelocation = new HashMap<>();

        for (var basin : graph.basins()) {
            sources += basin.sources();
            junctions += basin.junctions();
            if (basin.outlet() != null && (basin.edgeOutlet() || atWorldEdge(basin.outlet()))) {
                carver.setEdgeOutlet(basin.outlet().x(), basin.outlet().y());
            }
            // Continuation tips at the world edge are thick inflows — same off-map bank exemption.
            for (var reach : basin.downstreamFirst()) {
                var tip = reach.pixels().get(0);
                if (atWorldEdge(tip) && strokeHintsCached != null
                        && strokeHintsCached.continuationPixels().contains(tip)) {
                    carver.setEdgeOutlet(tip.x(), tip.y());
                }
            }
            Set<DrawnRiverGraph.Pixel> roots = new HashSet<>();
            for (var reach : basin.downstreamFirst()) roots.add(reach.pixels().get(reach.pixels().size() - 1));
            for (var reach : basin.downstreamFirst()) roots.remove(reach.pixels().get(0));

            for (var reach : basin.downstreamFirst()) {
                check();
                var pixels = reach.pixels();
                var end = pixels.get(pixels.size() - 1);
                if (!roots.contains(end) && !connected.contains(end) && !junctionRelocation.containsKey(end)) {
                    rejected++;
                    messages.add("Alt kol reddedildiği için bağlı üst kol uygulanmayacak: " + pixels.get(0));
                    continue;
                }
                DrawnRiverGraph.Pixel regionHub = regionRepresentative(end, graph);
                DrawnRiverGraph.Pixel joinTarget = junctionRelocation.getOrDefault(end,
                        regionHub != null ? regionHub : end);
                boolean joining = connected.contains(joinTarget) || carver.plannedWetAt(joinTarget.x(), joinTarget.y());
                double outletWidth = joining
                        ? Math.max(reach.width(), junctionWidths.getOrDefault(joinTarget, reach.width()))
                        : reach.width();

                List<DrawnRiverGraph.Pixel> path = relocateEnd(pixels, joining ? joinTarget : end);
                boolean tipIsEdge = basin.edgeOutlet() && basin.outlet() != null
                        && path.get(path.size() - 1).equals(basin.outlet());
                Accept acc = tryStages(carver, router, path, reach, joining, joinTarget, outletWidth,
                        withTerrainStage, tipIsEdge);
                if (acc.stage.ordinal() > stage.ordinal()) stage = acc.stage;
                if (acc.ok) {
                    acceptedReaches.add(reach);
                    if (acc.relocated != null && !acc.relocated.equals(end)) {
                        junctionRelocation.put(end, acc.relocated);
                        messages.add("Birleşim taşındı: " + end + " → " + acc.relocated);
                    }
                    // Prefer junction-region representative when joining into a normalized hub.
                    DrawnRiverGraph.Pixel hub = regionRepresentative(end, graph);
                    if (hub != null) {
                        junctionRelocation.putIfAbsent(end, hub);
                        connected.add(hub);
                    }
                    connected.addAll(pixels);
                    if (acc.relocated != null) connected.add(acc.relocated);
                    junctionWidths.put(pixels.get(0), reach.width());
                    courses.add(new RiverSearchResult.Course(
                            acc.path.stream().map(p -> new RiverSearchResult.Point(p.x(), p.y())).toList(),
                            reach.width(), outletWidth, 0));
                    suggested.addAll(acc.path);
                    if (acc.message != null) messages.add(acc.message);
                } else {
                    rejected++;
                    failed.add(new FailedReach(path, reach));
                    messages.add("Kol " + pixels.get(0) + ": " + acc.message);
                    if (acc.needsStrong) needsStrong = true;
                }
            }
        }
        messages.addAll(0, graph.orientationNotes());
        return new PassResult(carver, courses, suggested, messages, blockers, failed,
                sources, junctions, rejected, stage, needsStrong, Set.copyOf(acceptedReaches));
    }

    private Accept tryStages(ShallowRiverCarver carver, DrawnRiverRoutePlanner router,
                             List<DrawnRiverGraph.Pixel> path, DrawnRiverGraph.Reach reach,
                             boolean joining, DrawnRiverGraph.Pixel joinTarget, double outletWidth,
                             boolean afterTerrain, boolean tipIsEdgeOutlet) {
        if (tryCarve(carver, path, reach, joining, outletWidth)) {
            return Accept.ok(path, afterTerrain ? Stage.LIGHT_TERRAIN : Stage.PRESERVE, null, joining ? joinTarget : null);
        }
        String last = carver.getLastRejection();

        boolean pinStart = reach.contributors() > 1;
        DrawnRiverRoutePlanner.Route refined = router.refine(path, depth, pinStart, joining, joining ? joinTarget : null);
        if (refined != null) {
            List<DrawnRiverGraph.Pixel> alt = joining ? relocateEnd(refined.pixels(), joinTarget) : refined.pixels();
            if (tryCarve(carver, alt, reach, joining, outletWidth)) {
                return Accept.ok(alt, Stage.CORRIDOR, "Koridor " + refined.corridorWidth() + ": " + path.get(0),
                        joining ? joinTarget : null);
            }
            last = carver.getLastRejection();
        }

        if (!joining && !tipIsEdgeOutlet) {
            DrawnRiverGraph.Pixel tip = path.get(path.size() - 1);
            for (int radius : new int[]{128, 256, 512}) {
                check();
                DrawnRiverGraph.Pixel outlet = router.findOutlet(tip, radius);
                if (outlet == null) continue;
                List<DrawnRiverGraph.Pixel> extended = new ArrayList<>(path);
                if (!extended.get(extended.size() - 1).equals(outlet)) extended.add(outlet);
                DrawnRiverRoutePlanner.Route toOut = router.refine(extended, depth, true, true, outlet);
                // Failure to refine the outlet extension must not replace the
                // entire user drawing with a straight source-to-sea shortcut.
                List<DrawnRiverGraph.Pixel> candidate = toOut != null ? toOut.pixels() : extended;
                if (tryCarve(carver, candidate, reach, false, outletWidth)) {
                    return Accept.ok(candidate, Stage.CORRIDOR, "Çıkış " + radius + "→" + outlet, null);
                }
                last = carver.getLastRejection();
            }
        }

        double extra = estimateExtraCut(path, reach.width());
        boolean needsStrong = extra > TerrainAdjustmentPlan.Limits.of(TerrainAdjustmentPlan.Mode.LIGHT).maxCut() + 0.05;
        return Accept.fail(last, needsStrong);
    }

    private DrawnRiverGraph.Pixel regionRepresentative(DrawnRiverGraph.Pixel end, DrawnRiverGraph.Result graph) {
        for (var region : graph.junctionRegions()) {
            if (region.cells().contains(end) || region.representative().equals(end))
                return region.representative();
        }
        return null;
    }

    private static List<DrawnRiverGraph.Pixel> densifyLine(DrawnRiverGraph.Pixel a, DrawnRiverGraph.Pixel b) {
        List<DrawnRiverGraph.Pixel> out = new ArrayList<>();
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(a.x() - b.x(), a.y() - b.y())));
        for (int s = 0; s <= steps; s++) {
            out.add(new DrawnRiverGraph.Pixel(
                    a.x() + (b.x() - a.x()) * s / steps,
                    a.y() + (b.y() - a.y()) * s / steps));
        }
        return out;
    }

    private boolean tryCarve(ShallowRiverCarver carver, List<DrawnRiverGraph.Pixel> path,
                             DrawnRiverGraph.Reach reach, boolean joining, double outletWidth) {
        int[] xs = path.stream().mapToInt(DrawnRiverGraph.Pixel::x).toArray();
        int[] ys = path.stream().mapToInt(DrawnRiverGraph.Pixel::y).toArray();
        return joining ? carver.addJoiningPath(xs, ys, reach.width(), outletWidth)
                : carver.addPath(xs, ys, reach.width(), reach.width());
    }

    private static List<DrawnRiverGraph.Pixel> relocateEnd(List<DrawnRiverGraph.Pixel> pixels, DrawnRiverGraph.Pixel end) {
        if (pixels.isEmpty() || pixels.get(pixels.size() - 1).equals(end)) return pixels;
        List<DrawnRiverGraph.Pixel> out = new ArrayList<>(pixels.subList(0, Math.max(0, pixels.size() - 1)));
        out.add(end);
        return out;
    }

    private double estimateExtraCut(List<DrawnRiverGraph.Pixel> path, double width) {
        return TerrainAdjustmentPlan.requiredExtraCut(dimension,
                path.stream().mapToInt(DrawnRiverGraph.Pixel::x).toArray(),
                path.stream().mapToInt(DrawnRiverGraph.Pixel::y).toArray(), width, depth, this::check);
    }

    private ShallowRiverCarver makeCarver() {
        ShallowRiverCarver c = new ShallowRiverCarver(dimension, sourceWidth, maximumWidth, depth, smooth, granite, seed,
                new ScriptProgress(null, null) {
                    @Override public void checkForCancel() { check(); }
                    @Override public void setProgress(double progress) { check(); }
                });
        c.enableTerrainPreservation();
        c.setLowerInteriorDirt(true);
        c.setWaterlineBankDetail(waterlineBankDetail);
        c.setPlanningCellLimit(20_000_000);
        return c;
    }

    private List<DrawnRiverGraph.Pixel> collectDrawing() {
        Set<DrawnRiverGraph.Pixel> unique = new HashSet<>();
        collectBits(layer, unique);
        for (Layer extra : pathLikeLayers()) {
            if (extra == layer) continue;
            collectBits(extra, unique);
        }
        // Outlet marks that sit on the skeleton are already included; isolated marks are not path.
        List<DrawnRiverGraph.Pixel> drawing = new ArrayList<>(unique);
        Collections.sort(drawing);
        return drawing;
    }

    private DrawnRiverGraph.StrokeHints collectStrokeHints() {
        Set<DrawnRiverGraph.Pixel> outlets = new HashSet<>();
        Set<DrawnRiverGraph.Pixel> mini = new HashSet<>();
        Set<DrawnRiverGraph.Pixel> cont = new HashSet<>();
        Layer outletLayer = findLayerByName(RiverDrawingLayerNames.OUTLET);
        Layer miniLayer = findLayerByName(RiverDrawingLayerNames.MINI);
        Layer contLayer = findLayerByName(RiverDrawingLayerNames.CONTINUE);
        if (outletLayer != null) collectBits(outletLayer, outlets);
        if (miniLayer != null) collectBits(miniLayer, mini);
        if (contLayer != null) collectBits(contLayer, cont);
        // Primary layer may itself be Mini/Continue when user opens dialog from that pen.
        if (RiverDrawingLayerNames.MINI.equals(layer.getName())) collectBits(layer, mini);
        if (RiverDrawingLayerNames.CONTINUE.equals(layer.getName())) collectBits(layer, cont);
        if (RiverDrawingLayerNames.OUTLET.equals(layer.getName())) collectBits(layer, outlets);
        strokeHintsCached = new DrawnRiverGraph.StrokeHints(outlets, mini, cont);
        return strokeHintsCached;
    }

    private void collectBits(Layer bitLayer, Set<DrawnRiverGraph.Pixel> into) {
        if (bitLayer == null || bitLayer.getDataSize() != Layer.DataSize.BIT) return;
        for (var tile : dimension.getTiles()) {
            if (!tile.hasLayer(bitLayer)) continue;
            for (int y = 0; y < 128; y++) {
                check();
                for (int x = 0; x < 128; x++) {
                    if (!tile.getBitLayerValue(bitLayer, x, y)) continue;
                    if (into.size() >= 20_000_000)
                        throw new IllegalArgumentException("Çizim çok büyük; 20.000.000 hücre sınırı. Daha küçük bir alan çizin.");
                    into.add(new DrawnRiverGraph.Pixel(tile.getX() * 128 + x, tile.getY() * 128 + y));
                }
            }
        }
    }

    private List<Layer> pathLikeLayers() {
        List<Layer> found = new ArrayList<>();
        for (String name : List.of(RiverDrawingLayerNames.PATH, RiverDrawingLayerNames.MINI,
                RiverDrawingLayerNames.CONTINUE)) {
            Layer l = findLayerByName(name);
            if (l != null) found.add(l);
        }
        return found;
    }

    private Layer findLayerByName(String name) {
        if (name == null) return null;
        if (name.equals(layer.getName())) return layer;
        for (Layer candidate : dimension.getAllLayers(false)) {
            if (name.equals(candidate.getName()) && candidate.getDataSize() == Layer.DataSize.BIT) {
                return candidate;
            }
        }
        for (var custom : dimension.getCustomLayers()) {
            if (name.equals(custom.getName()) && custom.getDataSize() == Layer.DataSize.BIT) {
                return custom;
            }
        }
        return null;
    }

    private void check() {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new CancellationException("İptal edildi; dünya değiştirilmedi.");
        if (!applying) {
            if (isStale()) throw new IllegalStateException("Dünya değişti; yeniden önizleyin.");
            long now = System.nanoTime();
            if (now > deadline)
                throw new CancellationException("Planlama bütçesi doldu; dünya değiştirilmedi.");
            if (preservePhase && now > preserveCap)
                throw new PhaseBudgetExceeded();
        }
    }

    private static int countInvalid(DrawnRiverGraph.Result graph) {
        return (int) graph.diagnostics().stream().filter(diag ->
                diag.kind() == DrawnRiverNormalizer.IssueKind.MULTI_OUTLET
                        || diag.kind() == DrawnRiverNormalizer.IssueKind.REAL_LOOP
                        || diag.kind() == DrawnRiverNormalizer.IssueKind.UNRESOLVED).count();
    }

    @FunctionalInterface
    interface DeepCompute {
        Preview compute(DrawnRiverGraph.Result graph, List<DrawnRiverGraph.Pixel> drawing);
    }

    private static final class PhaseBudgetExceeded extends RuntimeException { }

    private record FailedReach(List<DrawnRiverGraph.Pixel> path, DrawnRiverGraph.Reach reach) {}
    private record Accept(boolean ok, List<DrawnRiverGraph.Pixel> path, Stage stage, String message,
                          boolean needsStrong, DrawnRiverGraph.Pixel relocated) {
        static Accept ok(List<DrawnRiverGraph.Pixel> path, Stage stage, String message, DrawnRiverGraph.Pixel relocated) {
            return new Accept(true, path, stage, message, false, relocated);
        }
        static Accept fail(String message, boolean needsStrong) {
            return new Accept(false, List.of(), Stage.PRESERVE, message, needsStrong, null);
        }
    }
    private record PassResult(ShallowRiverCarver carver, List<RiverSearchResult.Course> courses,
                              List<DrawnRiverGraph.Pixel> suggested, List<String> messages, List<String> blockers,
                              List<FailedReach> failed, int sources, int junctions, int rejected,
                              Stage stage, boolean needsStrong, Set<DrawnRiverGraph.Reach> acceptedReaches) {}

    private final Dimension dimension;
    private final Layer layer;
    private final double sourceWidth, maximumWidth, depth;
    private final boolean smooth, granite, waterlineBankDetail;
    private final long seed;
    private final BooleanSupplier cancelled;
    private final long revision;
    private long deadline, preserveCap;
    private ShallowRiverCarver plan;
    private TerrainAdjustmentPlan terrainPlan = new TerrainAdjustmentPlan(TerrainAdjustmentPlan.Mode.LIGHT);
    private Preview preview;
    private boolean applying, applied, computing, preservePhase;
    private Set<DrawnRiverGraph.Edge> excludedEdges = Set.of();
    private Set<DrawnRiverGraph.Edge> excludedAtPreview = Set.of();
    private DrawnRiverGraph.Drain drain = DrawnRiverGraph.Drain.AUTO;
    private DrawnRiverGraph.Drain drainAtPreview = DrawnRiverGraph.Drain.AUTO;
    private DrawnRiverGraph.Pixel outletOverride;
    private DrawnRiverGraph.Pixel overrideAtPreview;
    private DrawnRiverGraph.StrokeHints strokeHintsCached;
}
