package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.exporting.SurfaceSmoother;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.River;
import org.pepsoft.worldpainter.layers.RiverSurfaceDetail;
import org.pepsoft.worldpainter.layers.RiverWaterlineDetail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * A bounded, shallow channel for the named river presets. All geometry is planned
 * against the ORIGINAL heightfield, never a previously carved neighbour. Legacy
 * scripts retain their old carver. Coordinates and depths are WorldPainter blocks;
 * the exporter's 2x2x2 surface sampling does not multiply world dimensions.
 */
public final class ShallowRiverCarver {
    public ShallowRiverCarver(Dimension dimension, double startWidth, double endWidth, double depth,
                              boolean smoothBanks, boolean granite, long seed, ScriptProgress progress) {
        if (dimension == null || !Double.isFinite(startWidth) || !Double.isFinite(endWidth)
                || !Double.isFinite(depth) || startWidth < 2 || endWidth < startWidth || endWidth > 64
                || depth < 0.65 || depth > 5) {
            throw new IllegalArgumentException("Invalid shallow river geometry");
        }
        this.dimension = dimension;
        this.startWidth = startWidth;
        this.endWidth = endWidth;
        this.depth = depth;
        this.smoothBanks = smoothBanks;
        this.granite = granite;
        this.seed = seed;
        this.progress = progress;
        maxCut = depth + 0.75;
    }

    /** Optional local earthworks; water depth is NOT increased by this mode. */
    public void enableTerrainAdaptation() {
        if (paths != 0 || applied) throw new IllegalStateException("Configure grading before adding paths");
        terrainAdaptation = true;
        terrainPreservation = false;
        maxCut = depth + 3.75;
        maxFill = 2.0;
    }

    /** Carve only the wet bed; never reshape or raise surrounding dry terrain. */
    public void enableTerrainPreservation() {
        if (paths != 0 || applied) throw new IllegalStateException("Configure preservation before adding paths");
        terrainPreservation = true;
        terrainAdaptation = false;
        // Preserve the shallow-channel safety contract. Route search may choose
        // another valley, but it must not turn a saddle into a deep trench.
        maxCut = depth + 0.75;
        maxFill = 0;
    }

    public boolean isTerrainAdaptationEnabled() { return terrainAdaptation; }
    public boolean isTerrainPreservationEnabled() { return terrainPreservation; }
    public double getMaximumCut() { return maxCut; }
    public double getMaximumFill() { return maxFill; }

    /**
     * Optional read-only planning heights (e.g. TerrainAdjustmentPlan). Cut/fill
     * and bed targets use this surface; {@link #apply()} still writes against the
     * live dimension after the caller has applied those deltas.
     */
    public void setPlanningHeightOverlay(ToFloatBiFunction<Integer, Integer> overlay) {
        if (applied) throw new IllegalStateException("Plan already applied");
        if (paths != 0) throw new IllegalStateException("Configure height overlay before adding paths");
        planningHeightOverlay = overlay;
        originals.clear();
        dirtBedPlan = null;
        waterlinePlan = null;
    }

    public void clearPlanningHeightOverlay() {
        setPlanningHeightOverlay(null);
    }

    /**
     * Mark a map-edge mouth. Only the terminal disk around this cell relaxes
     * off-map bank sampling and shoreline containment; protected/old-river
     * rejects are unchanged. May be called again for additional basins.
     */
    public void setEdgeOutlet(int x, int y) {
        if (applied) throw new IllegalStateException("Cannot configure edge outlet after apply");
        edgeOutlets.add(key(x, y));
    }

    public void clearEdgeOutlet() {
        if (applied) throw new IllegalStateException("Cannot clear edge outlet after apply");
        edgeOutlets.clear();
    }

    public boolean hasEdgeOutlet() { return !edgeOutlets.isEmpty(); }

    @FunctionalInterface
    public interface ToFloatBiFunction<A, B> {
        float applyAsFloat(A a, B b);
    }

    /** Source-to-outlet order. A rejected route makes NO terrain changes. */
    /** Per-reach widths in a shared plan; failed branches do not change accepted geometry. */
    public boolean addJoiningPath(int[] xs, int[] ys, double sourceWidth, double outletWidth) {
        if (xs == null || ys == null || xs.length < 2 || xs.length != ys.length) return reject("Geçersiz birleşim yolu.");
        Proposal receiver = proposals.get(key(xs[xs.length - 1], ys[ys.length - 1]));
        if (receiver == null || !receiver.waterline) return reject("Birleşim düğümünde doğrulanmış ana yatak yok.");
        joiningWater = receiver.water;
        final int joiningRoute=paths;
        joinedReceivers.put(joiningRoute,receiver);
        joiningWidths.put(joiningRoute,outletWidth);
        boolean committed=false;
        try {
            boolean accepted = addPath(xs, ys, sourceWidth, outletWidth);
            committed=accepted;
            if (accepted) {
                int radius = (int) Math.ceil(outletWidth);
                int x = xs[xs.length - 1], y = ys[ys.length - 1];
                for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++)
                    junctionDirtExclusions.add(key(x + dx, y + dy));
                dirtBedPlan = null;
                waterlinePlan = null;
            }
            return accepted;
        }
        finally { joiningWater = null; if(!committed){joinedReceivers.remove(joiningRoute);joiningWidths.remove(joiningRoute);} }
    }

    public boolean addPath(int[] xs, int[] ys, double sourceWidth, double outletWidth) {
        if (!Double.isFinite(sourceWidth) || !Double.isFinite(outletWidth)
                || sourceWidth < 1 || outletWidth < sourceWidth || outletWidth > 64) {
            throw new IllegalArgumentException("Invalid network reach widths");
        }
        double previousStart = startWidth, previousEnd = endWidth;
        try {
            startWidth = sourceWidth; endWidth = outletWidth;
            return addPath(xs, ys);
        } finally {
            startWidth = previousStart; endWidth = previousEnd;
        }
    }

    boolean addProfiledPath(int[] xs,int[] ys,double sourceWidth,double outletWidth,int[] water,boolean join) {
        if(water==null||water.length<2)throw new IllegalArgumentException("Missing water profile");
        fixedWaterProfile=water.clone();
        try{return join?addJoiningPath(xs,ys,sourceWidth,outletWidth):addPath(xs,ys,sourceWidth,outletWidth);}
        finally{fixedWaterProfile=null;}
    }

    public boolean addPath(int[] xs, int[] ys) {
        if (applied) throw new IllegalStateException("Plan already applied");
        if (xs == null || ys == null || xs.length != ys.length || xs.length < 2) {
            return reject("Nehir yolu en az iki noktadan oluşmalı.");
        }
        if (xs.length > MAX_INPUT_POINTS) return reject("Nehir çizgisi çok fazla nokta içeriyor; daha kısa veya ince bir hat seçin.");
        final List<Point> raw = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            check();
            if (xs[i] < Integer.MIN_VALUE + GEOMETRY_MARGIN || xs[i] > Integer.MAX_VALUE - GEOMETRY_MARGIN
                    || ys[i] < Integer.MIN_VALUE + GEOMETRY_MARGIN || ys[i] > Integer.MAX_VALUE - GEOMETRY_MARGIN) {
                return reject("Nehir koordinatları güvenli dünya sınırını aşıyor; rota uygulanmadı.");
            }
            final Cell c = sample(xs[i], ys[i]);
            if (c == null || c.protectedCell) return reject("Yol eksik/korunan arazi veya lav üzerinden geçiyor.");
            if (c.oldRiver) return reject("Yolda eski River katmanı var; ikinci export kazısını önlemek için rota uygulanmadı.");
            if (raw.isEmpty() || raw.get(raw.size() - 1).x != xs[i] || raw.get(raw.size() - 1).y != ys[i]) {
                raw.add(new Point(xs[i], ys[i]));
            }
        }
        if (raw.size() < 2) return reject("Nehir yolu tek noktaya indirgeniyor.");
        // Remove raster zigzags without relocating a path onto higher ground.
        final List<Point> smooth = new ArrayList<>(raw);
        for (int i = 2; !terrainPreservation && i < raw.size() - 2; i++) {
            double x = 0, y = 0;
            for (int j = -2; j <= 2; j++) {
                final int weight = 3 - Math.abs(j);
                x += raw.get(i + j).x * weight;
                y += raw.get(i + j).y * weight;
            }
            x /= 9; y /= 9;
            final Cell proposed = sample((int) Math.round(x), (int) Math.round(y));
            final Cell original = sample((int) raw.get(i).x, (int) raw.get(i).y);
            if (proposed != null && !proposed.protectedCell && !proposed.oldRiver
                    && Math.abs(proposed.original - original.original) <= 0.5) smooth.set(i, new Point(x, y));
        }
        final List<Point> points = densify(smooth);
        if(fixedWaterProfile!=null&&fixedWaterProfile.length!=points.size())return reject("Ortak su profili çizimle eşleşmiyor.");
        // The receiving water body's stored level is a boundary condition, not
        // another low terrain sample. Never lower an existing sea/lake to match
        // an upstream depression. A directly adjacent coast may close the path.
        // A graph junction is solved by the validated UNION below, not by requiring
        // its entire tributary cross-section to exist before the tributary is added.
        final OutletConnection outlet = joiningWater == null ? connectNearbyOutlet(points)
                : new OutletConnection(joiningWater, null);
        if (outlet.rejection != null) return reject(outlet.rejection);
        final Integer outletLevel = outlet.waterLevel;
        final double[] grades = terrainAdaptation ? smoothGrades(points) : null;
        final double[] radii = new double[points.size()];
        final double[] distanceToEnd = new double[points.size()];
        if (joiningWater != null) for (int i = points.size() - 2; i >= 0; i--) {
            check();
            distanceToEnd[i] = distanceToEnd[i + 1] + Math.hypot(points.get(i + 1).x - points.get(i).x,
                    points.get(i + 1).y - points.get(i).y);
        }
        final int[] levels = new int[points.size()];
        int previousWater = Integer.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            check();
            final Point p = points.get(i);
            final Cell centre = sample((int) Math.round(p.x), (int) Math.round(p.y));
            if (centre == null || centre.protectedCell || centre.oldRiver) return reject("Yol korunan bir bölgeye giriyor.");
            final double t = i / (double) (points.size() - 1);
            final double widthProgress = joiningWater == null ? t : clamp(1 - distanceToEnd[i] / (4 * endWidth));
            final double radius = Math.max(MIN_WET_RADIUS,
                    (startWidth + smoothstep(widthProgress) * (endWidth - startWidth)) / 2);
            radii[i] = radius;
            final Point a = points.get(Math.max(0, i - 1)), b = points.get(Math.min(points.size() - 1, i + 1));
            final double length = Math.max(0.001, Math.hypot(b.x - a.x, b.y - a.y));
            final double nx = -(b.y - a.y) / length, ny = (b.x - a.x) / length;
            int upper = waterCeiling(centre);
            // A whole cross-section has ONE water level. Contain it at both shores
            // instead of lowering every lateral cell to a different water level.
            for (int offset = -(int) Math.ceil(radius); offset <= Math.ceil(radius); offset++) {
                final int bx = (int) Math.round(p.x + nx * offset), by = (int) Math.round(p.y + ny * offset);
                final Cell bank = sample(bx, by);
                if (bank == null) {
                    // Off-map / missing tile: only allowed inside a marked edge-outlet disk.
                    if (isInEdgeOutletDisk(bx, by) || isInEdgeOutletDisk(centre.x, centre.y)) continue;
                    return reject("Nehir kesiti korunan/eksik arazi ile çakışıyor.");
                }
                if (bank.protectedCell || bank.oldRiver) return reject("Nehir kesiti korunan/eksik arazi ile çakışıyor.");
                if (!bank.existingWater) upper = Math.min(upper, waterCeiling(bank));
            }
            if (terrainAdaptation && !centre.existingWater) upper = Math.min(upper, (int) Math.round(grades[i]));
            final int water = fixedWaterProfile!=null ? fixedWaterProfile[Math.min(i,fixedWaterProfile.length-1)]
                    : outletLevel == null ? Math.min(previousWater, upper)
                    : Math.max(outletLevel, Math.min(previousWater, upper));
            if(water>previousWater)return reject("Ortak su profili yukarı akıyor.");
            if (centre.existingWater && water != centre.originalWater) {
                return reject("Yol farklı seviyeli mevcut suları birleştiriyor; göl/deniz seviyesi değiştirilmedi.");
            }
            // Never dig a canyon just to force a path over a downstream ridge.
            final double requiredDepth = terrainPreservation ? MIN_WET_DEPTH : depth;
            if (!centre.existingWater && RiverWaterProfile.exceedsCut(centre.original, water, requiredDepth, maxCut)) {
                return reject("Bu rota sığ yatak için fazla yükseliyor veya yamaca çapraz: başka kaynak/nokta yolu seçin. ["
                        + "hücre=" + centre.x + "," + centre.y + "; arazi=" + centre.original
                        + "; su=" + water + "; örnek=" + i + "; gerekenKazı="
                        + RiverWaterProfile.requiredCut(centre.original, water, requiredDepth) + "]");
            }
            if (water - depth < dimension.getMinHeight()) return reject("Yatak dünyanın alt sınırını aşıyor.");
            levels[i] = water;
            previousWater = water;
        }
        if (planningHeightOverlay != null && fixedWaterProfile==null) {
            double[] chainage=new double[points.size()];
            for(int i=1;i<points.size();i++)chainage[i]=chainage[i-1]+Math.hypot(points.get(i).x-points.get(i-1).x,points.get(i).y-points.get(i-1).y);
            RiverWaterProfile.spreadDrops(levels,chainage,RiverWaterProfile.gradeStepSpacing(endWidth));
        }
        if (joiningWater != null && levels[levels.length - 1] != joiningWater) {
            return reject("Yan kol ana yatağın birleşim su kotuna güvenle inemiyor.");
        }
        final Map<Long, Proposal> pending = new LinkedHashMap<>();
        final Map<Long, BlockedSample> blocked = new LinkedHashMap<>();
        for (int i = 0; i < points.size() - 1; i++) {
            check();
            final Point a = points.get(i), b = points.get(i + 1);
            final double radius = Math.max(radii[i], radii[i + 1]);
            // A local flatten-brush envelope, not a second narrow ledge at the
            // waterline. The dry shoulder blends ORIGINAL ground towards the
            // section level and fades completely within eight blocks of the bed.
            final double shoulder = terrainPreservation ? 0.5
                    : terrainAdaptation ? 12 : smoothBanks ? Math.min(8, Math.max(6, depth * 3)) : 0.5;
            final double reach = radius + shoulder;
            final double dx = b.x - a.x, dy = b.y - a.y, len2 = dx * dx + dy * dy;
            for (int y = (int) Math.floor(Math.min(a.y, b.y) - reach); y <= Math.ceil(Math.max(a.y, b.y) + reach); y++) {
                for (int x = (int) Math.floor(Math.min(a.x, b.x) - reach); x <= Math.ceil(Math.max(a.x, b.x) + reach); x++) {
                    final double projection = len2 == 0 ? 0 : ((x - a.x) * dx + (y - a.y) * dy) / len2;
                    // Do not round-cap a downhill outlet with the upstream water
                    // plane several blocks beyond the last route cross-section.
                    final boolean clippedEndpoint = (i == 0 && projection < 0 && -projection * Math.sqrt(len2) > 0.5)
                            || (i == points.size() - 2 && projection > 1 && (projection - 1) * Math.sqrt(len2) > 0.5);
                    final double t = clamp(projection);
                    final double distance = Math.hypot(x - (a.x + dx * t), y - (a.y + dy * t));
                    final double localRadius = radii[i] + (radii[i + 1] - radii[i]) * t;
                    final double wetRadius = Math.max(MIN_WET_RADIUS, localRadius * 0.60);
                    if (distance > localRadius + shoulder) continue;
                    final Cell cell = sample(x, y);
                    if (cell == null || cell.protectedCell) {
                        // Normal cross-section samples do not cover every raster
                        // cell of an oblique wet core. Preserve its closest
                        // segment assignment so it cannot silently become a dry
                        // hole, while clipped caps and dry shoulders stay exempt.
                        final long blockedKey = key(x, y);
                        final BlockedSample old = blocked.get(blockedKey);
                        if (old == null || distance < old.distance) {
                            blocked.put(blockedKey, new BlockedSample(distance,
                                    distance <= wetRadius, clippedEndpoint));
                        }
                        continue;
                    }
                    if (cell.existingWater) continue;
                    if (cell.oldRiver) return reject("Eski River katmanı yatakla çakışıyor; ikinci kazı yapılmadı.");
                    // Nearest continuous segment wins. Overlapping stamps must not
                    // repeatedly subtract depth or take a distant downhill water level.
                    final long key = key(x, y);
                    final Proposal old = pending.get(key);
                    if (old != null && old.distance <= distance) continue;
                    final int water = t < 0.5 ? levels[i] : levels[i + 1];
                    pending.put(key, createProposal(cell, distance, localRadius, shoulder, water,
                            clippedEndpoint, i + t, outletLevel));
                }
            }
        }
        // Assign the closest segment BEFORE clipping its end cap. Otherwise an
        // older, farther segment's rounded stamp survives beyond the outlet.
        if (terrainAdaptation) {
            // Clip WATER at the endpoint, not the dry grading envelope. Small
            // original-ground cap proposals can contain a source/outlet after
            // bounded feathering; no upstream stamp may put water beyond it.
            for (Map.Entry<Long, Proposal> entry : pending.entrySet()) {
                final Proposal p = entry.getValue();
                if (p.clippedEndpoint) entry.setValue(new Proposal(p.cell, p.cell.original,
                        p.cell.originalWater, p.lateral, false, false, p.distance, true,
                        p.pathProgress, p.routeId, false, false));
            }
        } else pending.values().removeIf(Proposal::clippedEndpoint);
        for (BlockedSample sample : blocked.values()) {
            check();
            if (sample.wetCore && !sample.clippedEndpoint) {
                return reject("En az 3 blok ıslak kanal eksik/korunan arazi veya lav ile çakışıyor; rota uygulanmadı.");
            }
        }
        // Work on a detached prospective map: a rejected second route must not
        // replace or refit even one proposal of an earlier accepted route.
        final Map<Long, Proposal> combined = new LinkedHashMap<>(proposals);
        for (Map.Entry<Long, Proposal> entry : pending.entrySet()) {
            final Proposal p = entry.getValue(), old = combined.get(entry.getKey());
            // A tributary must not overwrite the receiving reach's downstream
            // steps with its terminal junction plane. Keep the validated trunk
            // and validate directed one-block contacts below.
            if(joiningWater!=null && old!=null && old.waterline && p.waterline && old.water!=p.water)continue;
            // A junction is a UNION of wet footprints. Dry shoulders/caps are
            // only geometry helpers and must never beat another route's real
            // wet core, even when their centreline distances tie exactly.
            if (old != null && old.waterline && !p.waterline) continue;
            if (old != null && !old.waterline && p.waterline) {
                combined.put(entry.getKey(), p);
                continue;
            }
            if (old != null && old.waterline && p.waterline && old.water == p.water) {
                // Union two wet beds, never substitute the new tributary's
                // shallower edge merely because its centreline is closer.
                // Both depths are bounded against the SAME original surface.
                if (p.height < old.height) combined.put(entry.getKey(), p);
            } else if (old == null || p.distance < old.distance) combined.put(entry.getKey(), p);
        }
        if (terrainAdaptation) containDryBanks(combined);
        if (terrainPreservation && fixedWaterProfile==null) refinePreservingWaterSupport(combined, radii, levels, outletLevel, points);
        // Validate final nearest-segment assignments, never an intermediate stamp
        // from an upstream segment which a closer downstream segment will replace.
        if(!validatePlannedGeometry(combined,!networkAssembly,fixedWaterProfile!=null))return false;
        proposals.clear();
        proposals.putAll(combined);
        dirtBedPlan = null;
        waterlinePlan = null;
        if (outletLevel != null) markMouthDirtGuard(points);
        paths++;
        return true;
    }

    void beginNetworkPlan() {
        if(paths!=0||applied)throw new IllegalStateException("Network must begin before paths");
        networkAssembly=true;networkValidated=false;
    }

    boolean finishNetworkPlan() {
        networkAssembly=false;
        networkValidated=validatePlannedGeometry(proposals,true,true);
        return networkValidated;
    }

    private boolean validatePlannedGeometry(Map<Long,Proposal> combined,boolean complete,boolean profiled) {
        for (Proposal p : combined.values()) {
            check();
            if (p.wetCore && !p.waterline) {
                return reject("En az 3 blok ıslak kanal bu rotada sığ kazı sınırına sığmıyor; vadiye yakın bir yol seçin. ["
                        + p.cell.x + "," + p.cell.y + "; arazi=" + p.cell.original + "; taban=" + p.height + "; su=" + p.water + "]");
            }
            if (terrainPreservation && p.wetCore && p.water - p.height < MIN_WET_DEPTH - 1.0 / 256.0) {
                return reject("Sığ kanalın asgari su derinliği araziyi koruyan kazı sınırına sığmıyor; başka rota gerekli. ["
                        + p.cell.x + "," + p.cell.y + "; arazi=" + p.cell.original + "; taban=" + p.height + "; su=" + p.water + "]");
            }
            if (complete && p.waterline && touchesDifferentExistingWater(p)) {
                return reject("Nehir farklı seviyeli mevcut suya yandan temas ediyor; gömülü ağız veya taşma oluşturmamak için rota uygulanmadı.");
            }
            if (complete && p.waterline) {
                for (int[] offset : NEIGHBOURS) {
                    final Proposal neighbour = combined.get(key(p.cell.x + offset[0], p.cell.y + offset[1]));
                    if (neighbour != null && neighbour.waterline && neighbour.routeId != p.routeId
                            && neighbour.water != p.water
                            && !isDirectedDownstreamStep(p,neighbour) && !isDirectedDownstreamStep(neighbour,p)) {
                        return reject("Nehirler birleşimde farklı su seviyelerine sahip; önceki rota değiştirilmedi. ["
                                +p.cell.x+","+p.cell.y+" su="+p.water+" kol="+p.routeId+" -> "
                                +neighbour.cell.x+","+neighbour.cell.y+" su="+neighbour.water+" kol="+neighbour.routeId+"]");
                    }
                }
            }
            final boolean atOutletLevel = p.atOutletLevel;
            if (p.waterline && p.water > Math.round(p.cell.original) && !atOutletLevel && !p.lake && !terrainAdaptation
                    && (!profiled || p.water>dimension.getIntHeightAt(p.cell.x,p.cell.y))) {
                return reject("Ortak su seviyesi bu kesitte arazinin üstünde kalıyor (" + p.cell.x + ", " + p.cell.y
                        + "; su=" + p.water + ", arazi=" + p.cell.original + "); rota uygulanmadı.");
            }
            if (p.waterline && (atOutletLevel || p.lake || terrainAdaptation || terrainPreservation)) {
                // A shallow enclosed depression may safely back up to sea level;
                // an open hillside may not. Check the final dry bank boundary,
                // including the unmodified cells beyond the plan's footprint.
                if (!p.lake && p.water - p.height > depth + 1.0 / 256.0) {
                    return reject("Deniz/göl bağlantısı sığ yatak derinliğini aşıyor; farklı bir ağız seçin. ["
                            +p.cell.x+","+p.cell.y+"; su="+p.water+"; taban="+p.height+"; arazi="+p.cell.original+"]");
                }
                if (complete && !isInEdgeOutletDisk(p.cell.x, p.cell.y)
                        && !hasContainingNeighbours(p, combined, (terrainAdaptation || terrainPreservation) && !atOutletLevel && !p.lake)) {
                    return reject("Deniz/göl seviyesini tutacak kıyı yok; taşma oluşturabilecek rota uygulanmadı. ["
                            + containmentDetail + "]");
                }
            }
        }
        return true;
    }

    /** Local mouth disk only — same water level elsewhere stays dirt-eligible. */
    private void markMouthDirtGuard(List<Point> points) {
        Point last = points.get(points.size() - 1);
        int cx = (int) Math.round(last.x), cy = (int) Math.round(last.y);
        int radius = (int) Math.ceil(Math.max(MIN_WET_RADIUS, endWidth * 0.60));
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
            if (dx * dx + dy * dy <= r2) mouthDirtExclusions.add(key(cx + dx, cy + dy));
        }
    }

    /** ScriptRunner owns the undo boundary; cancelled/failed application restores this plan's writes. */
    public Result apply() {
        if (applied) throw new IllegalStateException("Plan already applied");
        if(networkAssembly||!networkValidated)throw new IllegalStateException("Ağ blok doğrulaması tamamlanmadı; dünya değiştirilmedi.");
        // Optimistic snapshot validation also prevents applying a stale preview.
        for (Cell cell : originals.values()) {
            check();
            if (Math.abs(dimension.getHeightAt(cell.x, cell.y) - cell.original) > 1e-3f
                    || dimension.getWaterLevelAt(cell.x, cell.y) != cell.originalWater
                    || dimension.getTerrainAt(cell.x, cell.y) != cell.originalTerrain
                    || dimension.getLayerValueAt(Biome.INSTANCE, cell.x, cell.y) != cell.originalBiome
                    || dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, cell.x, cell.y) != cell.originalSurfaceDetail
                    || dimension.getBitLayerValueAt(RiverWaterlineDetail.INSTANCE, cell.x, cell.y) != cell.originalWaterlineDetail
                    || protectedAt(cell.x, cell.y) != cell.protectedCell
                    || dimension.getBitLayerValueAt(River.INSTANCE, cell.x, cell.y) != cell.oldRiver) {
                throw new IllegalStateException("Arazi planlamadan sonra değişti; nehir yeniden planlanmalı.");
            }
        }
        prepareDirtBed();
        applied = true;
        long changed = 0, graniteCount = 0, raisedCells = 0;
        double maximumCut = 0, maximumFill = 0;
        final List<Cell> written = new ArrayList<>(proposals.size());
        final List<long[]> waterlineWrites = new ArrayList<>();
        try {
            for (Proposal p : proposals.values()) {
                check();
                final Cell c = p.cell;
                // Dry banks retain their exact height, terrain, water and biome;
                // even sub-block shoulder levelling can draw artificial terraces.
                if (terrainPreservation && !p.waterline) continue;
                // Don't repaint untouched plains just because they're inside the shoulder envelope.
                if (Math.abs(c.original - p.height) < 0.02 && !p.waterline
                        && Math.round(c.original) == Math.round((float) p.height)) continue;
                written.add(c);
                dimension.setHeightAt(c.x, c.y, correctedHeight(p));
                maximumCut = Math.max(maximumCut, c.original - dimension.getHeightAt(c.x, c.y));
                final double fill = dimension.getHeightAt(c.x, c.y) - c.original;
                maximumFill = Math.max(maximumFill, fill);
                if (fill > 0) raisedCells++;
                if (p.waterline) {
                    dimension.setWaterLevelAt(c.x, c.y, p.water);
                    final boolean wet = p.water > Math.round((float) p.height);
                    final double coverage = p.lateral > 0.50 ? 0.85 : 0.28;
                    // Random dirt/granite variation is fine on a flat bed, but
                    // it must never interrupt a rounded-height contour. Dirt has
                    // no stair/slab family, so one dirt cell at a Y transition
                    // recreates the full-block underwater ledge which local
                    // 2x2x2 detail is meant to remove. Force both wet sides of
                    // each one-block contour into the granite/detail family.
                    final boolean contourDetail = granite && wetRoundedTransition(p);
                    final boolean shoreDetail = granite && wetShoreline(p);
                    if (granite && (shoreDetail || contourDetail || patch(c.x, c.y) < coverage)) {
                        dimension.setTerrainAt(c.x, c.y, Terrain.GRANITE);
                        dimension.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, c.x, c.y, true);
                        graniteCount++;
                    } else if (wet) {
                        dimension.setTerrainAt(c.x, c.y, Terrain.DIRT);
                        dimension.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, c.x, c.y, false);
                    }
                    if (wet) dimension.setLayerValueAt(Biome.INSTANCE, c.x, c.y,
                            dimension.getBitLayerValueAt(Frost.INSTANCE, c.x, c.y) ? 11 : 7);
                } else if (c.originalWater > Math.round((float) p.height)) {
                    dimension.setWaterLevelAt(c.x, c.y, Math.min(c.originalWater, Math.round((float) p.height)));
                }
                changed++;
                if (progress != null && (changed & 1023) == 0) progress.setProgress(changed / (double) Math.max(1, proposals.size()));
            }
            prepareWaterlinePlan();
            for (long k : waterlinePlan.accepted()) {
                check();
                int x = (int) (k >> 32), y = (int) k;
                boolean was = dimension.getBitLayerValueAt(RiverWaterlineDetail.INSTANCE, x, y);
                waterlineWrites.add(new long[]{k, was ? 1 : 0});
                dimension.setBitLayerValueAt(RiverWaterlineDetail.INSTANCE, x, y, true);
            }
            if (progress != null) progress.setProgress(1);
        } catch (RuntimeException | Error failure) {
            // No cancellation checks during rollback. Restore only cells touched by
            // this operation, including the biome and original custom terrain.
            for (Cell c : written) {
                try {
                    dimension.setHeightAt(c.x, c.y, c.original);
                    dimension.setWaterLevelAt(c.x, c.y, c.originalWater);
                    dimension.setTerrainAt(c.x, c.y, c.originalTerrain);
                    dimension.setLayerValueAt(Biome.INSTANCE, c.x, c.y, c.originalBiome);
                    dimension.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, c.x, c.y, c.originalSurfaceDetail);
                    dimension.setBitLayerValueAt(RiverWaterlineDetail.INSTANCE, c.x, c.y, c.originalWaterlineDetail);
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            for (long[] w : waterlineWrites) {
                try {
                    int x = (int) (w[0] >> 32), y = (int) w[0];
                    dimension.setBitLayerValueAt(RiverWaterlineDetail.INSTANCE, x, y, w[1] != 0);
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
        return new Result(paths, rejectedPaths, changed, graniteCount, maximumCut, raisedCells, maximumFill);
    }

    private boolean wetRoundedTransition(Proposal proposal) {
        if (!proposal.waterline || proposal.water <= Math.round((float) proposal.height)) return false;
        final int height = Math.round((float) proposal.height);
        for (int[] offset : NEIGHBOURS) {
            final Proposal neighbour = proposals.get(key(proposal.cell.x + offset[0], proposal.cell.y + offset[1]));
            if (neighbour != null && neighbour.waterline
                    && neighbour.water > Math.round((float) neighbour.height)
                    && Math.abs(Math.round((float) neighbour.height) - height) == 1) return true;
        }
        return false;
    }

    /**
     * Mark the outermost wet cell, never the original dry bank. The exporter
     * rechecks the final terrain/water topology before turning this local detail
     * marker into a mud-brick slab or stair, so stale plans cannot paint the
     * channel interior or an uncontained shoreline.
     */
    private boolean wetShoreline(Proposal proposal) {
        if (!proposal.waterline || proposal.water <= Math.round((float) proposal.height)) return false;
        for (int[] offset : NEIGHBOURS) {
            final long neighbourKey = key(proposal.cell.x + offset[0], proposal.cell.y + offset[1]);
            final Proposal neighbour = proposals.get(neighbourKey);
            if (neighbour != null) {
                // Dry bank inside the plan (preserved height) supporting this water.
                if (!neighbour.waterline && Math.round((float) neighbour.height) >= proposal.water) return true;
            } else {
                final Cell original = originals.get(neighbourKey);
                if (original != null && !original.protectedCell && !original.oldRiver && !original.existingWater
                        && Math.round(original.original) >= proposal.water) return true;
                // Neighbour outside the carve footprint: still a supporting bank
                // if the live dimension holds water at/below its surface.
                if (original == null) {
                    final float live = dimension.getHeightAt(proposal.cell.x + offset[0], proposal.cell.y + offset[1]);
                    if (Float.isFinite(live) && Math.round(live) >= proposal.water
                            && dimension.getWaterLevelAt(proposal.cell.x + offset[0], proposal.cell.y + offset[1]) < proposal.water
                            && !dimension.getBitLayerValueAt(org.pepsoft.worldpainter.layers.ReadOnly.INSTANCE,
                                    proposal.cell.x + offset[0], proposal.cell.y + offset[1])) return true;
                }
            }
        }
        return false;
    }

    public String getLastRejection() { return lastRejection; }
    public int getAcceptedPaths() { return paths; }
    public int getLakeCellCount() {
        int n=0;
        for(Proposal p:proposals.values()) if(p.lake && p.waterline) n++;
        return n;
    }

    /**
     * Raise water in a closed, banked depression without cutting a canyon.
     * Terrain heights stay original. Existing seas/lakes are not lowered.
     */
    public boolean addLake(List<RiverSearchResult.Point> cells, int water) {
        if (applied) throw new IllegalStateException("Plan already applied");
        if (cells == null || cells.size() < 4) return reject("Göl en az birkaç kapalı hücre ister.");
        if (water < dimension.getMinHeight() || water >= dimension.getMaxHeight()) {
            return reject("Göl su kotu dünya sınırları dışında.");
        }
        final Map<Long, Proposal> combined = new LinkedHashMap<>(proposals);
        int wet = 0;
        for (RiverSearchResult.Point point : cells) {
            check();
            final Cell cell = sample(point.x(), point.y());
            if (cell == null || cell.protectedCell || cell.oldRiver) {
                return reject("Göl korunan/eksik arazi veya lav ile çakışıyor.");
            }
            if (cell.existingWater && cell.originalWater != water) {
                return reject("Göl farklı seviyeli mevcut suya karışıyor; kot düşürülmedi.");
            }
            if (Math.round(cell.original) >= water) continue;
            final long k = key(cell.x, cell.y);
            final Proposal old = combined.get(k);
            if (old != null && old.waterline && old.water != water && !old.lake) {
                return reject("Göl mevcut nehir kotuyla çakışıyor.");
            }
            combined.put(k, new Proposal(cell, cell.original, water, 0, true, true, 0, false, 0, paths, true, true));
            wet++;
        }
        if (wet < 4) return reject("Göl kıyı kotunun altında yeterince hücre yok.");
        for (Proposal p : combined.values()) {
            if (!p.lake) continue;
            if (!hasContainingNeighbours(p, combined, false)) {
                return reject("Göl kıyısı suyu tutmuyor; taşma oluşturulmadı. [" + containmentDetail + "]");
            }
        }
        proposals.clear();
        proposals.putAll(combined);
        dirtBedPlan = null;
        waterlinePlan = null;
        lakeCells = wet;
        if (paths == 0) paths++;
        return true;
    }
    private int lakeCells;

    /** Detached geometry for a read-only preview; no mutable proposal escapes. */
    public List<PreviewCell> previewCells() {
        prepareDirtBed();
        prepareWaterlinePlan();
        final List<PreviewCell> cells = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Proposal p : proposals.values()) {
            if (terrainPreservation && !p.waterline) continue;
            long k = key(p.cell.x, p.cell.y);
            seen.add(k);
            boolean lowered = dirtBedPlan.lowered().contains(k);
            boolean waterline = waterlinePlan.accepted().contains(k);
            cells.add(new PreviewCell(p.cell.x, p.cell.y, p.cell.original, correctedHeight(p), p.water,
                    lowered, waterline));
        }
        for (long k : waterlinePlan.accepted()) {
            if (!seen.add(k)) continue;
            int x = (int) (k >> 32), y = (int) k;
            float h = dimension.getHeightAt(x, y);
            int w = waterlinePlan.proposals().get(k) != null ? waterlinePlan.proposals().get(k).water()
                    : dimension.getWaterLevelAt(x, y);
            cells.add(new PreviewCell(x, y, h, h, w, false, true));
        }
        return List.copyOf(cells);
    }

    public record PreviewCell(int x, int y, float originalHeight, float bedHeight, int waterLevel,
                              boolean loweredDirt, boolean waterlineBank) {
        public PreviewCell(int x, int y, float originalHeight, float bedHeight, int waterLevel) {
            this(x, y, originalHeight, bedHeight, waterLevel, false, false);
        }
        public PreviewCell(int x, int y, float originalHeight, float bedHeight, int waterLevel, boolean loweredDirt) {
            this(x, y, originalHeight, bedHeight, waterLevel, loweredDirt, false);
        }
    }

    public void setLowerInteriorDirt(boolean enabled) {
        if (applied) throw new IllegalStateException("Plan already applied");
        lowerInteriorDirt=enabled; dirtBedPlan=null;
    }
    public void setWaterlineBankDetail(boolean enabled) {
        if (applied) throw new IllegalStateException("Plan already applied");
        waterlineBankDetail=enabled; waterlinePlan=null;
    }
    public boolean isWaterlineBankDetailEnabled() { return waterlineBankDetail; }
    public int getLoweredDirtCells() {prepareDirtBed();return dirtBedPlan.lowered().size();}
    public int getSkippedDirtRegions() {prepareDirtBed();return dirtBedPlan.skippedRegions();}
    public int getCandidateDirtCells() {prepareDirtBed();return dirtBedPlan.candidateDirt();}
    public int getWaterlineBankCells() {prepareWaterlinePlan();return waterlinePlan.accepted().size();}
    public int getWaterlineRejected() {prepareWaterlinePlan();return waterlinePlan.rejected();}
    public String dirtBedSummary() {
        prepareDirtBed();
        if (!lowerInteriorDirt || !terrainPreservation || !granite) {
            return "Dirt tabanı: kapalı (koruma+granite gerekir).";
        }
        int lowered = dirtBedPlan.lowered().size();
        int candidates = dirtBedPlan.candidateDirt();
        int skipped = dirtBedPlan.skippedRegions();
        if (lowered > 0) {
            return "Dirt tabanı: "+candidates+" aday, "+lowered+" hücre indirilecek; "
                    +skipped+" bölge korunacak. Ek derinlik: 1 blok.";
        }
        return "Dirt tabanı: 0 hücre indirildi ("+skipReasonLabel(dirtBedPlan.primarySkipReason())
                +"); aday="+candidates+", korunmuş bölge="+skipped+".";
    }
    public String waterlineBankSummary() {
        prepareWaterlinePlan();
        if (!waterlineBankDetail || !terrainPreservation) {
            return "Üst kıyı yumuşatma: kapalı.";
        }
        int n = waterlinePlan.accepted().size();
        int rejected = waterlinePlan.rejected();
        if (n > 0) {
            return "Üst kıyı: "+n+" hücre yumuşatılacak; "+rejected+" nokta tam bırakıldı.";
        }
        return "Üst kıyı: 0 hücre ("+waterlineSkipLabel(waterlinePlan.primarySkipReason())
                +"); reddedilen="+rejected+".";
    }
    private static String skipReasonLabel(RiverDirtBedPlan.SkipReason reason) {
        return switch (reason == null ? RiverDirtBedPlan.SkipReason.NO_DIRT : reason) {
            case NONE -> "indirildi";
            case NO_DIRT -> "dirt yok";
            case WORLD_FLOOR -> "dünya alt sınırı";
            case NO_SUPPORT -> "destek yok";
            case GEOMETRY_FAILED -> "geometri doğrulaması başarısız";
            case PROTECTED -> "bağlantı bölgesi";
        };
    }
    private static String waterlineSkipLabel(RiverWaterlinePlan.SkipReason reason) {
        return switch (reason == null ? RiverWaterlinePlan.SkipReason.NO_CANDIDATE : reason) {
            case NONE -> "uygulandı";
            case NO_CANDIDATE -> "aday yok";
            case GUARD -> "ağız/birleşim koruması";
            case PROTECTED -> "korunan alan";
            case HIGH_WALL -> "yüksek duvar";
            case LEAK_RISK -> "kaçak riski";
            case UNSUPPORTED_MATERIAL -> "desteklenmeyen malzeme";
            case GEOMETRY -> "geometri uygun değil";
        };
    }
    private float correctedHeight(Proposal p) {
        return (float)p.height-(dirtBedPlan.lowered().contains(key(p.cell.x,p.cell.y))?1:0);
    }
    private void prepareDirtBed() {
        if(dirtBedPlan!=null)return;
        if(!lowerInteriorDirt||!terrainPreservation||!granite) {
            dirtBedPlan=RiverDirtBedPlan.Result.empty(RiverDirtBedPlan.SkipReason.NO_DIRT);return;
        }
        Map<Long,RiverDirtBedPlan.Cell> cells=new LinkedHashMap<>();
        for(var entry:proposals.entrySet()) {
            check(); Proposal p=entry.getValue();
            if(!p.waterline)continue;
            boolean shore=wetShoreline(p);
            boolean graniteDetail=!shore&&(wetRoundedTransition(p)
                    ||patch(p.cell.x,p.cell.y)<(p.lateral>.50?.85:.28));
            boolean link=p.clippedEndpoint||p.cell.existingWater||p.cell.protectedCell||p.cell.oldRiver
                    ||junctionDirtExclusions.contains(entry.getKey())
                    ||mouthDirtExclusions.contains(entry.getKey());
            RiverDirtBedPlan.Role role;
            if(link) role=RiverDirtBedPlan.Role.PROTECTED_LINK;
            else if(shore) role=RiverDirtBedPlan.Role.WET_SHORE;
            else if(graniteDetail) role=RiverDirtBedPlan.Role.INTERIOR_GRANITE;
            else if(p.wetCore) role=RiverDirtBedPlan.Role.INTERIOR_DIRT;
            else role=RiverDirtBedPlan.Role.PROTECTED_LINK;
            boolean dirt=role==RiverDirtBedPlan.Role.INTERIOR_DIRT;
            boolean detail=role==RiverDirtBedPlan.Role.INTERIOR_GRANITE||role==RiverDirtBedPlan.Role.WET_SHORE;
            // atOutletLevel stays for water-profile safety; it does NOT exempt dirt.
            boolean eligible=dirt;
            cells.put(entry.getKey(),new RiverDirtBedPlan.Cell(p.cell.x,p.cell.y,(float)p.height,p.water,
                    dirt,detail,eligible,p.routeId,role));
        }
        dirtBedPlan=RiverDirtBedPlan.create(dimension,cells,this::check);
    }

    private void prepareWaterlinePlan() {
        if (waterlinePlan != null) return;
        if (!waterlineBankDetail || !terrainPreservation) {
            waterlinePlan = RiverWaterlinePlan.Result.empty(RiverWaterlinePlan.SkipReason.NO_CANDIDATE);
            return;
        }
        prepareDirtBed();
        List<RiverWaterlinePlan.WetSeed> seeds = new ArrayList<>();
        for (Proposal p : proposals.values()) {
            check();
            if (!p.waterline || !p.wetCore) continue;
            seeds.add(new RiverWaterlinePlan.WetSeed(p.cell.x, p.cell.y, p.water,
                    p.wetCore, p.clippedEndpoint, p.lake));
        }
        Set<Long> guards = new HashSet<>();
        guards.addAll(mouthDirtExclusions);
        guards.addAll(junctionDirtExclusions);
        waterlinePlan = RiverWaterlinePlan.create(plannedWaterlineColumns(), dimension, seeds, guards, null, this::check);
    }

    /** Planned wet bed + live dry banks so preview matches apply without writing the world. */
    private SurfaceSmoother.WaterlineColumns plannedWaterlineColumns() {
        final SurfaceSmoother.WaterlineColumns live = SurfaceSmoother.columnsOf(dimension, null);
        return new SurfaceSmoother.WaterlineColumns() {
            @Override public float height(int x, int y) {
                Proposal p = proposals.get(key(x, y));
                if (p != null && p.waterline) return correctedHeight(p);
                return live.height(x, y);
            }
            @Override public int water(int x, int y) {
                Proposal p = proposals.get(key(x, y));
                if (p != null && p.waterline) return p.water;
                return live.water(x, y);
            }
            @Override public boolean tilePresent(int x, int y) { return live.tilePresent(x, y); }
            @Override public boolean blocked(int x, int y) { return live.blocked(x, y); }
        };
    }

    private boolean lowerInteriorDirt;
    private boolean waterlineBankDetail = true;
    private final Set<Long> junctionDirtExclusions = new HashSet<>();
    private final Set<Long> mouthDirtExclusions = new HashSet<>();
    private RiverDirtBedPlan.Result dirtBedPlan;
    private RiverWaterlinePlan.Result waterlinePlan;

    /** Compare optional route refinements without applying either plan. */
    int plannedWaterLevelAt(int x, int y) {
        final Proposal proposal = proposals.get(key(x, y));
        return proposal != null && proposal.waterline ? proposal.water : dimension.getWaterLevelAt(x, y);
    }

    /** Whether an already accepted path will make this cell wet, without applying the plan. */
    boolean plannedWetAt(int x, int y) {
        final Proposal proposal = proposals.get(key(x, y));
        return proposal != null && proposal.waterline;
    }

    /**
     * Validate the actual new wet edit footprint, including a short mouth
     * extension. A supplied predicate means true = blocked; null still checks
     * the current built-in protections. Existing water and untouched dry banks
     * are not edits. Call this on the final shared plan before applying it too.
     */
    public boolean isFootprintAllowed(BiPredicate<Integer, Integer> blocked) {
        for (Proposal proposal : proposals.values()) {
            check();
            if (!proposal.waterline) continue;
            final int x = proposal.cell.x, y = proposal.cell.y;
            if (protectedAt(x, y) || dimension.getBitLayerValueAt(River.INSTANCE, x, y)
                    || (blocked != null && blocked.test(x, y))) return false;
        }
        return true;
    }

    private boolean reject(String reason) { rejectedPaths++; lastRejection = reason; return false; }
    private void check() { if (progress != null) progress.checkForCancel(); }

    private boolean isInEdgeOutletDisk(int x, int y) {
        if (edgeOutlets.isEmpty()) return false;
        double r = Math.max(MIN_WET_RADIUS, endWidth * 0.60) + 1.0;
        double r2 = r * r;
        for (long k : edgeOutlets) {
            int ox = (int) (k >> 32), oy = (int) k;
            double dx = x - ox, dy = y - oy;
            if (dx * dx + dy * dy <= r2) return true;
        }
        return false;
    }

    private OutletConnection connectNearbyOutlet(List<Point> points) {
        final Point last = points.get(points.size() - 1), before = points.get(points.size() - 2);
        final Cell end = sample((int) Math.round(last.x), (int) Math.round(last.y));
        final double length = Math.hypot(last.x - before.x, last.y - before.y);
        if (end == null || length == 0) return new OutletConnection(null, null);
        if (isInEdgeOutletDisk(end.x, end.y)) {
            int edgeWater = Math.max(dimension.getMinHeight(),
                    (int) Math.floor(end.original - depth));
            return new OutletConnection(edgeWater, null);
        }
        // Repeating a fully accepted route is idempotent, not a request to
        // extend that route's dry terminal cap into a new receiving waterbody.
        if (!end.existingWater && isInsideAcceptedWater(points)) return new OutletConnection(null, null);
        final double directionX = (last.x - before.x) / length, directionY = (last.y - before.y) / length;
        final List<OutletCandidate> candidates = new ArrayList<>();
        for (int y = (int) Math.floor(last.y - 2); y <= Math.ceil(last.y + 2); y++) {
            for (int x = (int) Math.floor(last.x - 2); x <= Math.ceil(last.x + 2); x++) {
                check();
                final double dx = x - last.x, dy = y - last.y, distance = Math.hypot(dx, dy);
                final double forward = dx * directionX + dy * directionY;
                // A coast beside the last waypoint is as real as a coast ahead.
                // Keep the extension bounded and never make a backwards U-turn.
                if (distance > 2.0 || forward < -1.0e-9) continue;
                final Cell candidate = sample(x, y);
                final Integer water = receivingWater(candidate);
                if (water != null) {
                    candidates.add(new OutletCandidate(candidate, water, distance, forward));
                }
            }
        }
        if (candidates.isEmpty()) return new OutletConnection(null, null);
        candidates.sort(Comparator.comparingInt((OutletCandidate c) -> c.forward > 1.0e-9 ? 0 : 1)
                .thenComparingDouble(OutletCandidate::distance)
                .thenComparingDouble(c -> -c.forward)
                .thenComparingInt(c -> c.cell.x).thenComparingInt(c -> c.cell.y));
        // The final clipped cap must lie INSIDE a receiving strip wide enough
        // for the wet core. Stopping at a headland's first wet voxel leaves a
        // one-column neck immediately after an otherwise broad last section.
        // Preserve every supplied waypoint; only append this bounded connection.
        final double wetRadius = Math.max(MIN_WET_RADIUS, endWidth * 0.30);
        final double reach = Math.max(4, Math.ceil(endWidth / 2) + 3);
        final Set<Integer> triedLevels = new LinkedHashSet<>();
        for (OutletCandidate candidate : candidates) {
            if (!triedLevels.add(candidate.water)) continue;
            final int water = candidate.water;
            if (fullReceivingSection(last, directionX, directionY, wetRadius, water)) {
                return new OutletConnection(water, null);
            }
            // Continue the incoming tangent whenever possible: a coast touch
            // should not introduce an arbitrary right-angle turn at the mouth.
            for (double distance = 0.5; distance <= reach; distance += 0.5) {
                check();
                final Point target = new Point(last.x + directionX * distance, last.y + directionY * distance);
                if (fullReceivingSection(target, directionX, directionY, wetRadius, water)
                        && appendOutletConnection(points, last, target, water)) {
                    return new OutletConnection(water, null);
                }
            }
            // A grazing/side coast may need a slight forward-side correction.
            // Prefer low turning angles; no backward U-turn or distant reroute.
            final List<MouthTarget> targets = new ArrayList<>();
            for (int y = (int) Math.floor(last.y - reach); y <= Math.ceil(last.y + reach); y++) {
                for (int x = (int) Math.floor(last.x - reach); x <= Math.ceil(last.x + reach); x++) {
                    check();
                    final double dx = x - last.x, dy = y - last.y, distance = Math.hypot(dx, dy);
                    final double forward = dx * directionX + dy * directionY;
                    if (distance == 0 || distance > reach || forward < -1.0e-9) continue;
                    final Integer receiver = receivingWater(sample(x, y));
                    if (receiver == null || receiver != water) continue;
                    targets.add(new MouthTarget(new Point(x, y), distance + (1 - forward / distance) * reach * 1.5));
                }
            }
            targets.sort(Comparator.comparingDouble(MouthTarget::cost)
                    .thenComparingDouble(t -> t.point.x).thenComparingDouble(t -> t.point.y));
            for (MouthTarget target : targets) {
                final double dx = target.point.x - last.x, dy = target.point.y - last.y;
                final double distance = Math.hypot(dx, dy);
                if (fullReceivingSection(target.point, dx / distance, dy / distance, wetRadius, water)
                        && appendOutletConnection(points, last, target.point, water)) {
                    return new OutletConnection(water, null);
                }
            }
        }
        return new OutletConnection(null,
                "Nehir ağzında tam ıslak genişliği taşıyan güvenli alıcı su kesiti bulunamadı; tek blokluk boğaz oluşturulmadı.");
    }

    private Integer receivingWater(Cell cell) {
        if (cell == null || cell.protectedCell || cell.oldRiver) return null;
        if (cell.existingWater) return cell.originalWater;
        final Proposal planned = proposals.get(key(cell.x, cell.y));
        return planned != null && planned.waterline ? planned.water : null;
    }

    private boolean isInsideAcceptedWater(List<Point> points) {
        if (proposals.isEmpty()) return false;
        for (Point point : points) {
            check();
            final Proposal prior = proposals.get(key((int) Math.round(point.x), (int) Math.round(point.y)));
            if (prior == null || !prior.waterline) return false;
        }
        return true;
    }

    private boolean fullReceivingSection(Point point, double dx, double dy, double wetRadius, int water) {
        // Appended points need the same raster-loop safety margin as user points.
        if (point.x < Integer.MIN_VALUE + GEOMETRY_MARGIN || point.x > Integer.MAX_VALUE - GEOMETRY_MARGIN
                || point.y < Integer.MIN_VALUE + GEOMETRY_MARGIN || point.y > Integer.MAX_VALUE - GEOMETRY_MARGIN) return false;
        final double reach = wetRadius + 2;
        int cells = 0;
        for (int y = (int) Math.floor(point.y - reach); y <= Math.ceil(point.y + reach); y++) {
            for (int x = (int) Math.floor(point.x - reach); x <= Math.ceil(point.x + reach); x++) {
                final double along = (x - point.x) * dx + (y - point.y) * dy;
                final double across = -(x - point.x) * dy + (y - point.y) * dx;
                if (along < -0.5 || along > 1.5 || Math.abs(across) > wetRadius + 0.5) continue;
                check();
                final Integer receiver = receivingWater(sample(x, y));
                if (receiver == null || receiver != water) return false;
                cells++;
            }
        }
        return cells >= 3;
    }

    private boolean appendOutletConnection(List<Point> points, Point from, Point to, int water) {
        final int steps = Math.max(1, (int) Math.ceil(Math.hypot(to.x - from.x, to.y - from.y) * 2));
        if (points.size() + steps > MAX_DENSE_POINTS) return false;
        final List<Point> connection = new ArrayList<>(steps);
        for (int j = 1; j <= steps; j++) {
            check();
            final Point point = new Point(from.x + (to.x - from.x) * j / steps,
                    from.y + (to.y - from.y) * j / steps);
            final Cell cell = sample((int) Math.round(point.x), (int) Math.round(point.y));
            if (cell == null || cell.protectedCell || cell.oldRiver) return false;
            final Integer receiver = receivingWater(cell);
            if (receiver != null && receiver != water) return false;
            connection.add(point);
        }
        points.addAll(connection);
        return true;
    }

    private int waterCeiling(Cell cell) {
        if (cell.existingWater) return cell.originalWater;
        // The chunk factory puts the original full surface at round(height),
        // not floor(height). That voxel may be replaced by shallow water without
        // raising terrain. Flooring a fractional bank needlessly drops a whole
        // water level and can make an otherwise safe valley exceed maxCut.
        // Final wet-depth and ORIGINAL dry-bank containment checks still apply;
        // dry banks remain untouched and their boundary voxels stay full on export.
        // Detached earthworks are already designed against the pre-grading
        // longitudinal profile. Re-deriving that profile from excavated cells
        // lowers it again where neighbouring cross-sections overlap.
        // Geometry and containment below still validate the adjusted surface.
        final float reference = planningHeightOverlay == null ? cell.original
                : dimension.getHeightAt(cell.x, cell.y);
        return terrainPreservation ? RiverWaterProfile.dryWaterCeiling(reference)
                : Math.round((float) (cell.original + maxFill));
    }

    private Proposal createProposal(Cell cell, double distance, double localRadius, double shoulder,
                                    int water, boolean clippedEndpoint, double pathProgress, Integer outletLevel) {
        final double wetRadius = Math.max(MIN_WET_RADIUS, localRadius * 0.60);
        final double lateral = distance / localRadius;
        final double targetBeforeLimits;
        if (lateral <= 1) {
            targetBeforeLimits=water-RiverWaterProfile.sectionDepth(distance,localRadius,depth,terrainPreservation&&!smoothBanks);
        } else {
            final double blend = smoothstep((distance - localRadius) / shoulder);
            targetBeforeLimits = water + (cell.original - water) * blend;
        }
        double target = Math.min(cell.original + maxFill, Math.max(targetBeforeLimits, cell.original - maxCut));
        target = Math.max(dimension.getMinHeight(), Math.ceil(target * 256.0) / 256.0);
        target = Math.min(dimension.getMaxHeight() - 1, target);
        final boolean intendedWet=water>Math.round((float)(Math.ceil(targetBeforeLimits*256)/256));
        // A neighbouring branch's valley cut must not turn our planned DRY
        // shoulder into accidental deep water. The shared plan owns its wet
        // footprint; the completed network still checks every containing face.
        final boolean waterline = lateral <= 1 && water > Math.round((float) target)
                && (fixedWaterProfile==null||intendedWet);
        // Dry bank geometry/material/water are never changed in preserving mode.
        if (terrainPreservation && !waterline) target = cell.original;
        return new Proposal(cell, target, water, lateral, waterline, distance <= wetRadius,
                distance, clippedEndpoint, pathProgress, paths,
                joiningWater == null && outletLevel != null && water == outletLevel, false);
    }

    /**
     * An oblique raster edge can see a dry cardinal neighbour that none of the
     * normal cross-section samples visited. Lower only the water sections whose
     * ACTUAL dry boundary lacks a full supporting voxel; never fill that bank.
     * All violations are batched before each downstream monotonic pass, so a
     * 100-level mountain descent does not require 100 refinement iterations.
     * This is only a planner: the unchanged final validation still rejects any
     * insufficient wet width/depth, excavation budget or incompatible outlet.
     */
    private void refinePreservingWaterSupport(Map<Long, Proposal> pending, double[] radii,
                                              int[] levels, Integer outletLevel, List<Point> points) {
        final double[] chainage=new double[points.size()];
        for(int i=1;i<points.size();i++)chainage[i]=chainage[i-1]+Math.hypot(points.get(i).x-points.get(i-1).x,points.get(i).y-points.get(i-1).y);
        for (int pass = 0; pass < 4; pass++) {
            final int[] upper = levels.clone();
            boolean changed = false;
            for (Proposal wet : pending.values()) {
                check();
                if (wet.routeId != paths || !wet.waterline || wet.clippedEndpoint) continue;
                for (int[] offset : NEIGHBOURS) {
                    final int x = wet.cell.x + offset[0], y = wet.cell.y + offset[1];
                    final Proposal neighbour = pending.get(key(x, y));
                    // An intended downstream wet step is not a missing dry bank.
                    if (neighbour != null && neighbour.waterline) continue;
                    final Cell bank = neighbour == null ? sample(x, y) : neighbour.cell;
                    if (bank == null || bank.protectedCell || bank.oldRiver || bank.existingWater) continue;
                    final int supportedWater = Math.round(bank.original);
                    if (supportedWater >= wet.water) continue;
                    // Existing receiving water is a fixed boundary, never lowered.
                    final int allowed = outletLevel == null ? supportedWater : Math.max(outletLevel, supportedWater);
                    final int index = Math.min(levels.length - 1, (int) Math.floor(wet.pathProgress + 0.5));
                    if (allowed < upper[index]) {
                        upper[index] = allowed;
                        changed = true;
                    }
                }
            }
            if (!changed) return;
            int previous = Integer.MAX_VALUE;
            for (int i = 0; i < levels.length; i++) {
                check();
                levels[i] = Math.min(previous, upper[i]);
                previous = levels[i];
            }
            if(planningHeightOverlay!=null)RiverWaterProfile.spreadDrops(levels,chainage,RiverWaterProfile.gradeStepSpacing(endWidth));
            for (Map.Entry<Long, Proposal> entry : pending.entrySet()) {
                check();
                final Proposal proposal = entry.getValue();
                // A failed later route must never refit an accepted earlier route.
                if (proposal.routeId != paths || proposal.clippedEndpoint) continue;
                final int index = Math.min(levels.length - 1, (int) Math.floor(proposal.pathProgress + 0.5));
                if (levels[index] == proposal.water) continue;
                final int from = Math.min(radii.length - 1, (int) Math.floor(proposal.pathProgress));
                final int to = Math.min(radii.length - 1, from + 1);
                final double fraction = proposal.pathProgress - from;
                final double radius = radii[from] + (radii[to] - radii[from]) * fraction;
                entry.setValue(createProposal(proposal.cell, proposal.distance, radius, 0.5,
                        levels[index], false, proposal.pathProgress, outletLevel));
            }
        }
    }

    private double[] smoothGrades(List<Point> points) {
        final double[] prefix = new double[points.size() + 1], result = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            check();
            final Point p = points.get(i);
            final Cell cell = sample((int) Math.round(p.x), (int) Math.round(p.y));
            // Missing/protected cells will still be rejected by the normal route checks.
            prefix[i + 1] = prefix[i] + (cell == null ? dimension.getMinHeight()
                    : cell.existingWater ? cell.originalWater : cell.original);
        }
        // Densified samples are <= half a block apart: an eight-block local brush
        // suppresses tiny pits/ridges without imposing a flat plane on the river.
        for (int i = 0; i < points.size(); i++) {
            final int from = Math.max(0, i - 16), to = Math.min(points.size(), i + 17);
            result[i] = (prefix[to] - prefix[from]) / (to - from);
        }
        return result;
    }

    private boolean touchesDifferentExistingWater(Proposal proposal) {
        for (int[] offset : NEIGHBOURS) {
            final Cell neighbour = sample(proposal.cell.x + offset[0], proposal.cell.y + offset[1]);
            if (neighbour != null && neighbour.existingWater && neighbour.originalWater != proposal.water) return true;
        }
        return false;
    }

    /** Close rasterised bank steps with real, budgeted earth rather than accepting a leak. */
    private void containDryBanks(Map<Long, Proposal> pending) {
        final Map<Long, Double> bankFloors = new LinkedHashMap<>();
        for (Proposal wet : pending.values()) {
            check();
            if (!wet.waterline) continue;
            for (int[] offset : NEIGHBOURS) {
                final Proposal dry = pending.get(key(wet.cell.x + offset[0], wet.cell.y + offset[1]));
                if (dry == null || dry.waterline || dry.wetCore) continue;
                final double floor = dryBankFloor(dry.cell, wet.water);
                if (dry.height >= floor) continue;
                bankFloors.merge(key(dry.cell.x, dry.cell.y), floor, Math::max);
            }
        }
        final Map<Long, Double> wanted = new LinkedHashMap<>();
        for (Map.Entry<Long, Double> floor : bankFloors.entrySet()) {
            check();
            final Proposal bank = pending.get(floor.getKey());
            // Blend the closure through four blocks of the existing 12-block
            // shoulder. Never extend the route, touch water or override a mask.
            for (int y = bank.cell.y - 4; y <= bank.cell.y + 4; y++) {
                for (int x = bank.cell.x - 4; x <= bank.cell.x + 4; x++) {
                    final double distance = Math.hypot(x - bank.cell.x, y - bank.cell.y);
                    if (distance > 4) continue;
                    final long key = key(x, y);
                    final Proposal dry = pending.get(key);
                    if (dry == null || dry.waterline || dry.wetCore) continue;
                    final double required = dry.height + Math.max(0, floor.getValue() - dry.height)
                            * (1 - smoothstep(distance / 4));
                    final double allowed = Math.min(dimension.getMaxHeight() - 1, dry.cell.original + maxFill);
                    final double height = Math.min(allowed, Math.ceil(required * 256) / 256);
                    if (height > dry.height) wanted.merge(key, height, Math::max);
                }
            }
        }
        for (Map.Entry<Long, Double> change : wanted.entrySet()) {
            check();
            final Proposal dry = pending.get(change.getKey());
            pending.put(change.getKey(), new Proposal(dry.cell, change.getValue(), dry.water,
                    dry.lateral, false, dry.wetCore, dry.distance, dry.clippedEndpoint, dry.pathProgress,
                    dry.routeId, dry.atOutletLevel, dry.lake));
        }
        // Ordinary final containment still rejects an insufficient budget, a
        // protected/out-of-footprint bank or an incompatible existing waterbody.
    }

    private double dryBankFloor(Cell cell, int water) {
        // Heightmap rounding alone does not contain water beside a dry bottom
        // slab/stair. Keep the full supporting voxel at water Y underneath the
        // smoothed surface. Custom mixtures conservatively reserve that voxel
        // regardless of which of their materials is selected during export.
        final Terrain terrain = cell.originalTerrain;
        final boolean partial = terrainAdaptation
                && dimension.getSurfaceSmoothing() != Dimension.SurfaceSmoothing.NONE
                && (terrain.isCustom() || SurfaceSmoother.getFamily(terrain.getMaterial(
                        dimension.getWorld().getPlatform(), dimension.getSeed(), cell.x, cell.y,
                        water, water)) != null);
        return water - 0.5 + (partial ? 1 : 0);
    }

    private boolean hasContainingNeighbours(Proposal proposal, Map<Long, Proposal> pending, boolean allowDownstreamStep) {
        for (int[] offset : NEIGHBOURS) {
            final int x = proposal.cell.x + offset[0], y = proposal.cell.y + offset[1];
            final Proposal neighbour = pending.get(key(x, y));
            if (neighbour != null) {
                if ((neighbour.waterline && neighbour.water >= proposal.water)
                        || neighbour.height >= dryBankFloor(neighbour.cell, proposal.water)) continue;
                // A connected, already planned downstream wet channel is an
                // intended one-block descent, not an uncontained dry bank.
                // Outlet water levels and lateral/unplanned holes stay strict.
                if (allowDownstreamStep && neighbour.waterline
                        && isDirectedDownstreamStep(proposal,neighbour)) continue;
            } else {
                final Cell original = sample(x, y);
                if (original != null && !original.protectedCell && !original.oldRiver
                        && (original.original >= dryBankFloor(original, proposal.water)
                        || (original.existingWater && original.originalWater >= proposal.water))) continue;
            }
            containmentDetail = "hücre=" + proposal.cell.x + "," + proposal.cell.y + " su=" + proposal.water
                    + " ilkArazi=" + proposal.cell.original + "; komşu=" + x + "," + y
                    + (neighbour == null ? " mevcut hücre" : " planlananSu=" + neighbour.water
                    + " ıslak=" + neighbour.waterline + " içYatak=" + neighbour.wetCore + " yatak=" + neighbour.height
                    + " ilerleme=" + proposal.pathProgress + "->" + neighbour.pathProgress);
            return false;
        }
        return true;
    }

    private Cell sample(int x, int y) {
        if (!dimension.isTilePresent(x >> 7, y >> 7)) return null;
        final long key = key(x, y);
        Cell cell = originals.get(key);
        if (cell == null) {
            float live = dimension.getHeightAt(x, y);
            if (!Float.isFinite(live)) return null;
            float height = live;
            if (planningHeightOverlay != null) {
                float planned = planningHeightOverlay.applyAsFloat(x, y);
                if (Float.isFinite(planned)) height = planned;
            }
            cell = new Cell(x, y, height, dimension.getWaterLevelAt(x, y),
                    dimension.getTerrainAt(x, y), dimension.getLayerValueAt(Biome.INSTANCE, x, y),
                    protectedAt(x, y), dimension.getBitLayerValueAt(River.INSTANCE, x, y),
                    dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, y),
                    dimension.getBitLayerValueAt(RiverWaterlineDetail.INSTANCE, x, y),
                    dimension.getWaterLevelAt(x,y)>Math.round(live));
            if (originals.size() >= planningCellLimit) throw new IllegalStateException("Çizim planı bellek hücre sınırına ulaştı; daha küçük bir ağ seçin. Dünya değiştirilmedi.");
            originals.put(key, cell);
        }
        return cell;
    }

    private boolean isDirectedDownstreamStep(Proposal high, Proposal low) {
        if(low.water!=high.water-1)return false;
        if(high.routeId==low.routeId)return low.pathProgress>high.pathProgress+1e-6;
        Proposal receiver=joinedReceivers.get(high.routeId);
        for(int i=0;receiver!=null&&i<=joinedReceivers.size();i++) {
            if(receiver.routeId==low.routeId)return low.pathProgress>=receiver.pathProgress-1e-6;
            receiver=joinedReceivers.get(receiver.routeId);
        }
        // Two incoming wet footprints may meet just before their shared node.
        // Permit their one-block approach step only near that actual junction,
        // never between unrelated rivers or into an unplanned/dry neighbour.
        Proposal a=joinedReceivers.get(high.routeId),b=joinedReceivers.get(low.routeId);
        double reach=2*Math.max(joiningWidths.getOrDefault(high.routeId,0d),joiningWidths.getOrDefault(low.routeId,0d));
        for(int i=0;a!=null&&i<=joinedReceivers.size();i++,a=joinedReceivers.get(a.routeId)) {
            for(Proposal q=b;q!=null;q=joinedReceivers.get(q.routeId)) {
                if(a.cell.x==q.cell.x&&a.cell.y==q.cell.y&&a.water==q.water&&low.water>=a.water
                        &&Math.hypot(high.cell.x-a.cell.x,high.cell.y-a.cell.y)<=reach
                        &&Math.hypot(low.cell.x-a.cell.x,low.cell.y-a.cell.y)<=reach)return true;
            }
        }
        return false;
    }

    private boolean protectedAt(int x, int y) {
        return !dimension.isTilePresent(x >> 7, y >> 7)
                || dimension.getBitLayerValueAt(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(NotPresent.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(NotPresentBlock.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(ReadOnly.INSTANCE, x, y)
                || dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y);
    }

    private List<Point> densify(List<Point> path) {
        final List<Point> result = new ArrayList<>();
        result.add(path.get(0));
        for (int i = 1; i < path.size(); i++) {
            check();
            final Point a = path.get(i - 1), b = path.get(i);
            final int steps = Math.max(1, (int) Math.ceil(Math.hypot(b.x - a.x, b.y - a.y) * 2));
            if (steps > 32768) throw new IllegalArgumentException("River segment too long");
            if (result.size() + steps > MAX_DENSE_POINTS) {
                throw new IllegalArgumentException("Nehir yolu güvenli örnekleme sınırını aşıyor; daha kısa bir hat seçin.");
            }
            for (int j = 1; j <= steps; j++) result.add(new Point(a.x + (b.x - a.x) * j / steps, a.y + (b.y - a.y) * j / steps));
        }
        return result;
    }

    private double patch(int x, int y) {
        // Stable small blobs; no traversal-order randomness or global terrain changes.
        long v = seed ^ (Math.floorDiv(x, 2) * 0x9E3779B97F4A7C15L) ^ (Math.floorDiv(y, 2) * 0xC2B2AE3D27D4EB4FL);
        v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
        v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
        return ((v ^ (v >>> 31)) >>> 11) * 0x1.0p-53;
    }

    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private double wetProfile(double value) {
        return terrainPreservation && !smoothBanks ? clamp(value) : smoothstep(value);
    }
    private static double smoothstep(double value) { final double t = clamp(value); return t * t * (3 - 2 * t); }
    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }

    public record Result(int paths, int rejectedPaths, long changedCells, long graniteCells, double maximumCut,
                         long raisedCells, double maximumFill) {}
    private record Point(double x, double y) {}
    private record Proposal(Cell cell, double height, int water, double lateral, boolean waterline,
                            boolean wetCore, double distance, boolean clippedEndpoint, double pathProgress,
                            int routeId, boolean atOutletLevel, boolean lake) {}
    private record BlockedSample(double distance, boolean wetCore, boolean clippedEndpoint) {}
    private record OutletCandidate(Cell cell, int water, double distance, double forward) {}
    private record MouthTarget(Point point, double cost) {}
    private record OutletConnection(Integer waterLevel, String rejection) {}
    private static final class Cell {
        Cell(int x, int y, float original, int originalWater, Terrain originalTerrain, int originalBiome,
             boolean protectedCell, boolean oldRiver, boolean originalSurfaceDetail, boolean originalWaterlineDetail,
             boolean existingWater) {
            this.x = x; this.y = y; this.original = original; this.originalWater = originalWater;
            this.originalTerrain = originalTerrain;
            this.originalBiome = originalBiome;
            this.protectedCell = protectedCell || !Float.isFinite(original); this.oldRiver = oldRiver;
            this.originalSurfaceDetail = originalSurfaceDetail;
            this.originalWaterlineDetail = originalWaterlineDetail;
            this.existingWater = existingWater;
        }
        final int x, y, originalWater, originalBiome;
        final float original;
        final Terrain originalTerrain;
        final boolean protectedCell, oldRiver, existingWater, originalSurfaceDetail, originalWaterlineDetail;
    }
    private final Dimension dimension;
    private double startWidth, endWidth;
    private final double depth;
    private double maxCut, maxFill;
    private boolean terrainAdaptation, terrainPreservation;
    private ToFloatBiFunction<Integer, Integer> planningHeightOverlay;
    private Integer joiningWater;
    private final Set<Long> edgeOutlets = new HashSet<>();
    private int[] fixedWaterProfile;
    private boolean networkAssembly,networkValidated=true;
    private final boolean smoothBanks, granite;
    private final long seed;
    private final ScriptProgress progress;
    private final Map<Long, Cell> originals = new LinkedHashMap<>();
    private int planningCellLimit = Integer.MAX_VALUE;
    /** A hard detached-data bound; this is not a measurement of JVM heap usage. */
    public void setPlanningCellLimit(int limit) {
        if (limit < 1 || !originals.isEmpty()) throw new IllegalArgumentException("Configure limit before planning");
        planningCellLimit = limit;
    }
    private final Map<Long, Proposal> proposals = new LinkedHashMap<>();
    private final Map<Integer, Proposal> joinedReceivers = new LinkedHashMap<>();
    private final Map<Integer,Double> joiningWidths=new LinkedHashMap<>();
    private static final double MIN_WET_RADIUS = 1.5;
    private static final double MIN_WET_DEPTH = RiverWaterProfile.MIN_WET_DEPTH;
    private static final int GEOMETRY_MARGIN = 64, MAX_INPUT_POINTS = 65_536, MAX_DENSE_POINTS = 131_072;
    private static final int[][] NEIGHBOURS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
    private int paths, rejectedPaths;
    private boolean applied;
    private String lastRejection = "";
    private String containmentDetail = "";
}
