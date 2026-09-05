package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.River;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Bounded read-only alternative routing for the named shallow-river tools.
 * This class never applies its plans, moves a manual source, or relaxes the
 * carver's final safety validation. The caller owns the single apply/undo step.
 */
public final class ShallowRiverRouter {
    public ShallowRiverRouter(Dimension dimension, ShallowRiverCarver plan,
                              double startWidth, double endWidth, double depth, boolean smoothBanks,
                              long seed, ScriptProgress progress, BiPredicate<Integer, Integer> avoid) {
        if (dimension == null || plan == null || !Double.isFinite(startWidth) || !Double.isFinite(endWidth)
                || !Double.isFinite(depth) || startWidth < 2 || endWidth < startWidth || endWidth > 64
                || depth < 0.65 || depth > 5) throw new IllegalArgumentException("Invalid river router settings");
        this.dimension = dimension;
        this.plan = plan;
        this.startWidth = startWidth;
        this.endWidth = endWidth;
        this.depth = depth;
        this.smoothBanks = smoothBanks;
        this.seed = seed;
        this.progress = progress;
        this.avoid = avoid;
        adaptation = plan.isTerrainAdaptationEnabled();
        preservation = plan.isTerrainPreservationEnabled();
        maximumCut = plan.getMaximumCut();
        maximumFill = plan.getMaximumFill();
    }

    /** The predicate supplied to the constructor means true = blocked. */
    public boolean findFromSource(int x, int y) {
        initialise();
        for (Bounds component : components) {
            if (component.tiles.contains(key(x >> 7, y >> 7))) {
                if (component != activeComponent) initialiseGrid(component);
                break;
            }
        }
        if (grid == null || budgetExhausted()) return false;
        final Sample source = sample(x, y);
        if (source.blocked || source.wet || source.height < dimension.getMinHeight() + depth) {
            reason = "Seçilen kaynak kuru ve korunmayan uygun arazi üzerinde değil; konumu değiştirilmedi.";
            return false;
        }
        return route(x, y, false);
    }

    /** Named automatic rivers should form a headwater-to-valley course, not a tiny coastal patch. */
    public void enableMountainCourseSelection() {
        mountainCourseSelection = true;
    }

    /** Number of additional paths added, not the total already in the shared plan. */
    public int findAutomatic(int wantedAdditional) {
        if (wantedAdditional < 0 || wantedAdditional > 12) throw new IllegalArgumentException("River count must be 0..12");
        if (wantedAdditional == 0) return 0;
        automaticRound++;
        initialise();
        if (components.isEmpty()) return 0;
        int added = 0;
        for (Bounds component : components) {
            if (component != activeComponent) initialiseGrid(component);
            if (grid == null) continue;
            if (preservation && mountainCourseSelection) collectRankedOnGrid();
            else added += findOnGrid(wantedAdditional - added);
            if (added == wantedAdditional || budgetExhausted()) break;
        }
        if (preservation && mountainCourseSelection) {
            // Compare the surveyed components before committing; a short course
            // in the first component must not win solely by iteration order.
            added += commitRanked(wantedAdditional, false);
            // A short edge course remains an incumbent between rounds. It may
            // not end round one while upstream sources remain unexplored.
            if (added < wantedAdditional && (automaticRound >= 6 || !canContinueAutomaticSearch())) {
                added += commitRanked(wantedAdditional - added, true);
            }
            return added;
        }
        // A short coastal route is useful as a last resort, but it must not
        // prevent a meaningful upstream river from being considered first.
        automaticCandidate = false;
        deferred.sort(Comparator.comparingDouble((Deferred d) -> length(d.path)).reversed());
        for (Deferred candidate : deferred) {
            if (added == wantedAdditional) break;
            if (candidate.component != activeComponent) initialiseGrid(candidate.component);
            // Deferred candidates are the original validated route. Appearance
            // refinement runs only when a route is ready to enter the plan.
            if (grid != null && acceptCandidate(candidate.path, candidate.sourceX, candidate.sourceY, candidate.outlet)) added++;
        }
        deferred.clear();
        return added;
    }

    /** Compare complete drainage courses before any candidate enters the shared edit plan. */
    private void collectRankedOnGrid() {
        collectingCandidates = true;
        try {
            findDrainageCandidates();
            // A validated long drainage route already gives a good incumbent.
            // Otherwise compare several independent source/outlet searches;
            // finding the first small edge channel is not the end of the job.
            if (ranked.stream().noneMatch(r -> r.component == activeComponent && r.newLength >= r.preferredLength)
                    && !budgetExhausted()) findOnGrid(4);
        } finally {
            collectingCandidates = false;
            drainageCorridor = null;
        }
    }

    private void findDrainageCandidates() {
        if (budgetExhausted()) return;
        if (grid.drainage == null) {
            final int size = grid.size();
            final float[] heights = new float[size];
            final boolean[] blocked = new boolean[size], terminals = new boolean[size], sources = new boolean[size];
            for (int i = 0; i < size; i++) {
                check();
                final Sample cell = grid.cells[i];
                final int x = grid.x(i), y = grid.y(i);
                final boolean plannedWet = plan.plannedWetAt(x, y);
                final double edge = edgeDistance(x, y);
                heights[i] = plannedWet ? plan.plannedWaterLevelAt(x, y) : cell.wet ? cell.water : cell.height;
                blocked[i] = cell.blocked;
                terminals[i] = !cell.blocked && (cell.wet || plannedWet
                        || (edge >= inset() && edge <= inset() + grid.step + 2));
                sources[i] = eligibleAutomaticSource(i) && !plannedWet;
            }
            grid.drainage = new RiverDrainageCandidates(grid.columns, grid.rows, grid.step,
                    heights, blocked, terminals, sources, this::check);
            drainagePasses++;
        }
        final double requiredLength = mountainCourseActive() ? grid.course.minimumLength : minimumLength();
        final double requiredDrop = mountainCourseActive() ? grid.course.minimumDrop : 0;
        final double sourceFloor = mountainCourseActive() ? effectiveSourceFloor() : dimension.getMinHeight();
        final List<RiverDrainageCandidates.Course> courses = grid.drainage.candidates(24,
                requiredLength, requiredDrop, sourceFloor, Math.max(endWidth * 2, minimumLength()));
        int tried = 0;
        final int budgetStart = edgeSamples;
        for (RiverDrainageCandidates.Course course : courses) {
            check();
            if (tried >= 8 || budgetExhausted() || edgeSamples - budgetStart >= 2_000_000) break;
            final int x = grid.x(course.source()), y = grid.y(course.source());
            if (nearAcceptedSource(x, y) || !attemptedDrainageSources.add(key(x, y))) continue;
            final int[] indices = grid.drainage.path(course, MAX_PATH_POINTS);
            if (indices.length < 2) continue;
            tried++;
            drainageCandidatesTested++;
            final List<Point> path = new ArrayList<>(indices.length);
            for (int index : indices) path.add(new Point(grid.x(index), grid.y(index)));
            automaticCandidate = true;
            if (acceptCandidate(path, x, y, course.outlet())) continue;
            if (budgetExhausted()) break;
            // D8 supplies the valley, not a safe channel cross-section. Refine
            // inside its corridor and retain the unchanged full-width checks.
            drainageCorridor = drainageCorridor(indices);
            try {
                searchAttempts++;
                final int allowance = Math.min(250_000, 2_000_000 - (edgeSamples - budgetStart));
                if (allowance <= 0) break;
                final List<Point> refined = searchPreserving(x, y, course.outlet(), 0, allowance, course.length());
                if (refined != null) acceptCandidate(refined, x, y, course.outlet());
            } finally {
                drainageCorridor = null;
            }
        }
    }

    private boolean[] drainageCorridor(int[] path) {
        final boolean[] corridor = new boolean[grid.size()];
        final int radius = Math.max(3, (int) Math.ceil(Math.max(12, endWidth * 2) / grid.step));
        // Multi-source Chebyshev dilation visits each grid cell at most once.
        // Stamping a radius-sized disk around every path point could perform
        // hundreds of millions of iterations at the supported width limit.
        // This square superset only admits more cells to the search corridor;
        // segment and final carver validation still enforce the exact channel.
        final int[] distance = new int[grid.size()];
        final int[] queue = new int[grid.size()];
        Arrays.fill(distance, -1);
        int head = 0, tail = 0;
        for (int index : path) {
            check();
            if (index >= 0 && index < grid.size() && distance[index] < 0) {
                distance[index] = 0;
                corridor[index] = true;
                queue[tail++] = index;
            }
        }
        while (head < tail) {
            check();
            final int index = queue[head++], nextDistance = distance[index] + 1;
            if (nextDistance > radius) continue;
            final int cx = index % grid.columns, cy = index / grid.columns;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                final int cell = grid.index(cx + dx, cy + dy);
                if (cell >= 0 && distance[cell] < 0) {
                    distance[cell] = nextDistance;
                    corridor[cell] = true;
                    queue[tail++] = cell;
                }
            }
        }
        return corridor;
    }

    private boolean nearAcceptedSource(int x, int y) {
        for (Point source : acceptedSources) {
            if (Math.hypot(source.x - x, source.y - y) < Math.max(endWidth * 2, minimumLength())) return true;
        }
        return false;
    }

    private int commitRanked(int wanted, boolean includeShort) {
        final List<RankedCandidate> ordered = new ArrayList<>(ranked);
        ordered.sort(RANKED_ORDER);
        int added = 0;
        automaticCandidate = false;
        for (RankedCandidate candidate : ordered) {
            check();
            if (added >= wanted || attempts >= MAX_CANDIDATES) break;
            if (!includeShort && candidate.newLength < candidate.preferredLength) continue;
            if (candidate.component != activeComponent) initialiseGrid(candidate.component);
            if (grid == null || nearAcceptedSource(candidate.sourceX, candidate.sourceY)) {
                ranked.remove(candidate);
                continue;
            }
            ranked.remove(candidate);
            // Revalidate against previously selected rivers; shortlist entries
            // contain only bounded coordinates/metrics, never full edit plans.
            committingRanked = true;
            try {
                if (acceptCandidate(candidate.path, candidate.sourceX, candidate.sourceY, candidate.outlet)) {
                    added++;
                    grid.drainage = null; // Later requested rivers see new planned water.
                }
            } finally {
                committingRanked = false;
            }
        }
        return added;
    }

    private int findOnGrid(int wantedAdditional) {
        final List<Integer> sources = new ArrayList<>();
        for (int index = 0; index < grid.size(); index++) {
            final Sample cell = grid.cells[index];
            if (eligibleAutomaticSource(index)
                    && (!mountainCourseActive() || cell.height >= effectiveSourceFloor())) sources.add(index);
        }
        // Share the source budget across actual elevation bands, not the first
        // 32 pixels of one unusable summit. Spatial separation below also covers
        // failed sources, so successive retries explore genuinely different areas.
        final List<List<Integer>> bands = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        double lowest = Double.POSITIVE_INFINITY, highest = Double.NEGATIVE_INFINITY;
        for (int index : sources) {
            lowest = Math.min(lowest, grid.cells[index].height);
            highest = Math.max(highest, grid.cells[index].height);
        }
        for (int index : sources) {
            final int band = highest <= lowest ? 1
                    : Math.min(2, (int) ((grid.cells[index].height - lowest) * 3 / (highest - lowest)));
            bands.get(band).add(index);
        }
        for (List<Integer> band : bands) {
            band.sort(Comparator.comparingDouble((Integer i) -> preservation ? sourceSuitability(i)
                            : noise(grid.x(i), grid.y(i), 0))
                    .thenComparingInt(Integer::intValue));
        }
        final List<Integer> diverseSources = new ArrayList<>(sources.size());
        for (int rank = 0; diverseSources.size() < sources.size(); rank++) {
            for (int band : preservation ? new int[] { 0, 2, 1 } : new int[] { 2, 1, 0 }) {
                if (rank < bands.get(band).size()) diverseSources.add(bands.get(band).get(rank));
            }
        }
        int added = 0, tried = 0;
        for (int index : diverseSources) {
            check();
            if (added == wantedAdditional || tried >= MAX_SOURCES || budgetExhausted()) break;
            final int x = grid.x(index), y = grid.y(index);
            boolean nearby = false;
            for (Point existing : acceptedSources) {
                if (Math.hypot(existing.x - x, existing.y - y) < Math.max(endWidth * 2, minimumLength())) {
                    nearby = true;
                    break;
                }
            }
            if (!nearby) {
                final double failedSourceSpacing = automaticRound <= 1
                        ? Math.max(endWidth * 2, minimumLength())
                        : automaticRound == 2
                        ? Math.max(endWidth * 1.25, minimumLength() * 0.5)
                        : Math.max(endWidth, minimumLength() * 0.25);
                for (Point existing : attemptedAutoSources) {
                    if (Math.hypot(existing.x - x, existing.y - y) < failedSourceSpacing) {
                        nearby = true;
                        break;
                    }
                }
            }
            if (nearby) continue;
            tried++;
            attemptedAutoSources.add(new Point(x, y));
            if (route(x, y, true)) added++;
        }
        if (tried >= MAX_SOURCES && added < wantedAdditional) searchLimited = true;
        if (sources.isEmpty()) reason = "Koruma, seçili kaçınma katmanı veya dünya sınırları nedeniyle uygun kaynak örneği yok.";
        return added;
    }

    public String getSummary() {
        return String.format(Locale.ROOT,
                "Alternatif rota: %d kabul, %d aday, %d yol araması; %d arazi örneği, %d/%d kenar örneği, %d/%d arama düğümü; tur %d. Son yol %.1f blok (en az %.1f); yeni yatak %.1f blok, su kotu düşüşü %d blok; hedef su derinliği %.2f. Havza: %d tarama, %d hat denemesi, %d bekleyen aday. %s Red nedenleri: %s. Son aday: %s. Son red: %s. Kaynak denemeleri: %d%s",
                accepted, attempts, searchAttempts, scanned, edgeSamples, MAX_EDGE_SAMPLES, expanded, MAX_TOTAL_NODES,
                automaticRound, lastLength, grid == null ? 0 : minimumLength(), lastNewTerrainLength, lastWaterDrop,
                depth, drainagePasses, drainageCandidatesTested, ranked.size(), reason, rejections, lastCandidate,
                lastProbeRejection.isEmpty() ? lastCause : lastProbeRejection, attemptedAutoSources.size(),
                attemptedAutoSources.isEmpty() ? "" : " (ilk 12: " + attemptedAutoSources.subList(0, Math.min(12, attemptedAutoSources.size())) + ")");
    }

    /** More automatic source rounds may run inside the same absolute safety budget. */
    public boolean canContinueAutomaticSearch() {
        return attempts < candidateSearchLimit() && expanded < MAX_TOTAL_NODES && edgeSamples < MAX_EDGE_SAMPLES;
    }

    /** A bounded search cannot prove that every possible route is impossible. */
    public boolean isSearchLimited() { return searchLimited; }

    public String getFailureReason() {
        final String detail = accepted > 0 ? "Bazı rotalar bulundu; istenen ek rotalar için arama tamamlanamadı. "
                + (lastProbeRejection.isEmpty() ? "" : lastProbeRejection) : reason;
        return searchLimited
                ? "Arama sınırına ulaşıldı; bu dünyada nehir yapılamadığı anlamına gelmez. Kaynak/çıkış alternatiflerinin tamamı incelenemedi. " + detail
                : "Denenen ek rotalar seçilen genişlik, sığ kazı veya koruma koşullarını sağlayamadı. " + detail;
    }

    private boolean route(int sourceX, int sourceY, boolean automatic) {
        automaticCandidate = automatic;
        final Sample source = sample(sourceX, sourceY);
        final int sourceBudgetStart = edgeSamples;
        // An automatic run must share work between alternative sources. A
        // manual run has just one immutable source and can use that full budget.
        final int sourceAllowance = preservation && automatic
                ? (automaticRound <= 1 ? FAST_SOURCE_ALLOWANCE : EXTENDED_SOURCE_ALLOWANCE)
                : MAX_EDGE_SAMPLES;
        final List<Integer> outlets = new ArrayList<>();
        final double minimum = mountainCourseActive() ? Math.max(minimumLength(), grid.course.minimumLength * 0.6) : minimumLength();
        for (int index = 0; index < grid.size(); index++) {
            final Sample target = grid.cells[index];
            if (target.blocked || Math.hypot(grid.x(index) - sourceX, grid.y(index) - sourceY) < minimum) continue;
            if (mountainCourseActive() && dryWaterUpper(source.height)
                    - (target.wet ? target.water : dryWaterUpper(target.height)) < grid.course.minimumDrop) continue;
            if (target.wet) {
                if (target.water <= dryWaterUpper(source.height) + maximumFill && shore(index)) outlets.add(index);
            } else {
                final double edge = edgeDistance(grid.x(index), grid.y(index));
                if (edge >= inset() && edge <= inset() + grid.step + 2
                        && target.height <= source.height + maximumCut - depth) outlets.add(index);
            }
        }
        outlets.sort(Comparator.comparingInt((Integer i) -> grid.cells[i].wet ? 0 : 1)
                .thenComparingDouble(i -> Math.hypot(grid.x(i) - sourceX, grid.y(i) - sourceY)
                        + Math.max(0, grid.cells[i].height - source.height) * 8)
                // Jitter is a tie-break only. In a long narrow valley, adjacent
                // coast distances differ by hundredths of a block; adding noise
                // first could pick a hillside outlet, consume its entire 16-block
                // area, and hide the safe valley-centre mouth in the same area.
                .thenComparingDouble(i -> noise(grid.x(i), grid.y(i), attempts))
                .thenComparingInt(Integer::intValue));
        int searches = 0, waterSearches = 0;
        final Set<OutletArea> triedOutletAreas = new HashSet<>();
        for (int outlet : outlets) {
            check();
            if (searches >= MAX_OUTLETS_PER_SOURCE || budgetExhausted()
                    || (preservation && edgeSamples - sourceBudgetStart >= sourceAllowance)) {
                searchLimited = true;
                break;
            }
            // Reserve retries for dry exits when all nearby coast options are
            // unsafe; a large water body must not crowd them out of the budget.
            if (grid.cells[outlet].wet && waterSearches >= (preservation ? 6 : 5)) {
                searchLimited = true;
                continue;
            }
            // Try another shore/edge area, not many almost-identical neighbouring pixels.
            final int area = preservation ? Math.max(16, grid.step * 8) : Math.max(8, grid.step * 2);
            // A descending lake/river shore can contain several water levels in
            // one spatial bucket. Treating the whole bucket as one outlet used
            // to hide a valid full-width mouth after the first (too narrow or
            // differently levelled) pixel failed. Keep the spatial bound, but
            // allow one deterministic attempt per water level in that bucket.
            final OutletArea outletArea = new OutletArea(Math.floorDiv(grid.x(outlet), area),
                    Math.floorDiv(grid.y(outlet), area), grid.cells[outlet].wet ? grid.cells[outlet].water : Integer.MIN_VALUE);
            if (!triedOutletAreas.add(outletArea)) continue;
            searches++;
            if (grid.cells[outlet].wet) waterSearches++;
            if (preservation) {
                // Avoid flooding thousands of equal-cost cells on flat ground.
                // A direct candidate is only a fast path: the SAME complete
                // carver validation (width, wet banks, protections) must pass.
                final List<Point> direct = List.of(new Point(sourceX, sourceY), new Point(grid.x(outlet), grid.y(outlet)));
                final Integer water = grid.cells[outlet].wet ? grid.cells[outlet].water : null;
                if (directFollowsValley(sourceX, sourceY, grid.x(outlet), grid.y(outlet))
                        && segment(sourceX, sourceY, grid.x(outlet), grid.y(outlet), dryWaterUpper(source.height), water,
                        startWidth / 2, startWidth / 2) != null && acceptPath(direct, sourceX, sourceY, outlet)) return true;
            }
            searchAttempts++;
            final List<Point> path = search(sourceX, sourceY, outlet, searches, sourceAllowance / 2);
            if (path == null || path.size() < 2) {
                recordRejection("A* hedefe ulaşamadı");
                continue;
            }
            if (acceptPath(path, sourceX, sourceY, outlet)) return true;
            if (preservation && !budgetExhausted()) {
                // A completed candidate supplies an actual length estimate.
                // Retry with its growing width, instead of prematurely widening
                // from straight-line distance or accepting a pinched channel.
                searchAttempts++;
                final List<Point> refined = searchPreserving(sourceX, sourceY, outlet, searches,
                        sourceAllowance / 2, length(path));
                if (refined != null && acceptPath(refined, sourceX, sourceY, outlet)) return true;
            }
        }
        if (outlets.isEmpty()) reason = "Kaynağa uygun, güvenli mesafede bir göl/deniz veya kuru çıkış bulunamadı.";
        else if (reason.isEmpty()) reason = "Sınırlı alternatif aramada sığ kazı ve koruma koşullarını sağlayan rota bulunamadı.";
        return false;
    }

    private boolean acceptPath(List<Point> path, int sourceX, int sourceY, int outlet) {
        return acceptCandidate(path, sourceX, sourceY, outlet);
    }

    private boolean acceptCandidate(List<Point> path, int sourceX, int sourceY, int outlet) {
            final Sample source = sample(sourceX, sourceY);
            final CourseGeometry course = mountainCourseActive() ? courseGeometry(path) : null;
            if (course != null && (source.height < effectiveSourceFloor()
                    || course.newTerrainLength < grid.course.minimumLength
                    || course.span < grid.course.minimumLength * 0.6)) {
                reason = "Aday dağdan vadiye yeterli yeni nehir yatağı oluşturmuyor; kısa kıyı parçası başarı sayılmadı.";
                recordRejection(reason);
                return false;
            }
            lastCandidate = String.format(Locale.ROOT, "(%d,%d Y=%.2f) -> (%d,%d %s=%s), %.1f blok",
                    sourceX, sourceY, source.height, grid.x(outlet), grid.y(outlet),
                    grid.cells[outlet].wet ? "su" : "arazi",
                    grid.cells[outlet].wet ? Integer.toString(grid.cells[outlet].water) : Float.toString(grid.cells[outlet].height),
                    length(path));
            if (!avoidsFootprint(path)) {
                recordRejection(reason);
                return false;
            }
            attempts++;
            final ShallowRiverCarver probe = new ShallowRiverCarver(dimension,
                    startWidth, endWidth, depth, smoothBanks, false, seed, progress);
            if (preservation) probe.enableTerrainPreservation();
            else if (adaptation) probe.enableTerrainAdaptation();
            int[] xs = new int[path.size()], ys = new int[path.size()];
            for (int i = 0; i < path.size(); i++) {
                xs[i] = path.get(i).x;
                ys[i] = path.get(i).y;
            }
            // A rejected probe and its original-cell cache die here. Do not grow
            // the shared plan's snapshot for every unsuccessful candidate.
            if (!probe.addPath(xs, ys)) {
                reason = probe.getLastRejection();
                lastProbeRejection = reason;
                recordRejection(reason);
                return false;
            }
            if (!probe.isFootprintAllowed(avoid)) {
                reason = "Nehir ağzının gerçek ıslak alanı kaçınma katmanına giriyor; aday uygulanmadı.";
                recordRejection(reason);
                return false;
            }
            if (course != null && probe.plannedWaterLevelAt(sourceX, sourceY)
                    - probe.plannedWaterLevelAt(course.downstreamX, course.downstreamY) < grid.course.minimumDrop) {
                reason = "Adayın gerçek su kotu düşüşü dağdan aşağı akış için yetersiz; daha yüksek kaynak aranıyor.";
                recordRejection(reason);
                return false;
            }
            if (collectingCandidates) {
                // Rank the validated, unrefined geometry. Appearance refinement
                // is deliberately deferred to final selection: doing another
                // valley search for every shortlist entry exhausts the budget.
                final CourseGeometry measured = course == null ? courseGeometry(path) : course;
                final RankedCandidate candidate = new RankedCandidate(List.copyOf(path), sourceX, sourceY, outlet,
                        activeComponent, measured.newTerrainLength,
                        probe.plannedWaterLevelAt(sourceX, sourceY)
                                - probe.plannedWaterLevelAt(measured.downstreamX, measured.downstreamY),
                        measured.span, Math.max(minimumLength(),
                                Math.min(1024, Math.max(maxX - minX + 1, maxY - minY + 1) * 0.5)));
                final RankedCandidate previous = ranked.stream().filter(r -> r.component == activeComponent
                        && r.sourceX == sourceX && r.sourceY == sourceY).findFirst().orElse(null);
                if (previous == null || RANKED_ORDER.compare(candidate, previous) < 0) {
                    ranked.remove(previous);
                    ranked.add(candidate);
                    ranked.sort(RANKED_ORDER);
                    if (ranked.size() > 12) ranked.remove(ranked.size() - 1);
                }
                return true;
            }
            if (automaticCandidate && preservation && length(path) < Math.min(512, Math.max(maxX - minX + 1, maxY - minY + 1) / 2.0)) {
                // Keep at most one validated fallback per source, without adding
                // it to the shared plan or changing any world data yet.
                if (deferred.size() < 12 && deferred.stream().noneMatch(d -> d.sourceX == sourceX && d.sourceY == sourceY)) {
                    deferred.add(new Deferred(List.copyOf(path), sourceX, sourceY, outlet, activeComponent));
                }
                return false;
            }
            // Weighted search finds a feasible long route quickly, but may pop
            // a carveable ridge before a cheaper valley detour. Keep the proven
            // route as an incumbent and spend a bounded extra pass on true
            // g+distance ordering when the route climbs above an earlier low
            // point. Already descending long valleys need no extra search.
            if (preservation && risesAboveEarlierLowPoint(path) && !budgetExhausted()) {
                searchAttempts++;
                final List<Point> valley = searchPreserving(sourceX, sourceY, outlet, 0,
                        Math.min(650_000, MAX_EDGE_SAMPLES - edgeSamples), length(path), 1);
                if (valley != null && !valley.equals(path) && avoidsFootprint(valley)) {
                    final int[] vx = new int[valley.size()], vy = new int[valley.size()];
                    for (int i = 0; i < valley.size(); i++) { vx[i] = valley.get(i).x; vy[i] = valley.get(i).y; }
                    final ShallowRiverCarver lowerIncision = new ShallowRiverCarver(dimension,
                            startWidth, endWidth, depth, smoothBanks, false, seed, progress);
                    lowerIncision.enableTerrainPreservation();
                    if (lowerIncision.addPath(vx, vy) && lowerIncision.isFootprintAllowed(avoid)
                            && lowerIncision.plannedWaterLevelAt(sourceX, sourceY) == probe.plannedWaterLevelAt(sourceX, sourceY)
                            && qualifiesMountainCourse(valley, lowerIncision)) {
                        path = valley; xs = vx; ys = vy;
                    }
                }
            }
            // Refine ONLY a proven feasible route. Trying appearance variants
            // for every rejected route would consume the finite search budget
            // and hide viable outlets on tall/long worlds. Keep the original as
            // a fallback if the valley or an existing junction is too tight.
            // Measure against prior paths before this candidate enters the
            // shared plan; its own wet cells must not truncate its new length.
            CourseGeometry acceptedCourse = courseGeometry(path);
            boolean added = false;
            if (preservation && smoothBanks) {
                final int[][] shaped = RiverPathShape.refine(xs, ys, Math.min(3, startWidth * 0.5), seed, progress);
                final List<Point> candidate = new ArrayList<>(shaped[0].length);
                for (int i = 0; i < shaped[0].length; i++) candidate.add(new Point(shaped[0][i], shaped[1][i]));
                if (!candidate.equals(path) && avoidsFootprint(candidate)) {
                    final ShallowRiverCarver refined = new ShallowRiverCarver(dimension,
                            startWidth, endWidth, depth, smoothBanks, false, seed, progress);
                    refined.enableTerrainPreservation();
                    if (refined.addPath(shaped[0], shaped[1]) && refined.isFootprintAllowed(avoid)
                            && refined.plannedWaterLevelAt(sourceX, sourceY) == probe.plannedWaterLevelAt(sourceX, sourceY)
                            && qualifiesMountainCourse(candidate, refined)) {
                        final CourseGeometry refinedCourse = courseGeometry(candidate);
                        added = plan.addPath(shaped[0], shaped[1]);
                        if (added) {
                            path = candidate;
                            acceptedCourse = refinedCourse;
                        }
                    }
                }
            }
            if (!added && !plan.addPath(xs, ys)) {
                reason = plan.getLastRejection();
                recordRejection(reason);
                return false;
            }
            accepted++;
            acceptedSources.add(new Point(sourceX, sourceY));
            lastLength = length(path);
            lastNewTerrainLength = acceptedCourse.newTerrainLength;
            lastWaterDrop = plan.plannedWaterLevelAt(sourceX, sourceY)
                    - plan.plannedWaterLevelAt(acceptedCourse.downstreamX, acceptedCourse.downstreamY);
            reason = grid.cells[outlet].wet ? "Mevcut göl/denize güvenli bağlantı bulundu."
                    : "Mevcut su bulunamadı veya bağlanamadı; dünya kenarından içeride güvenli kuru çıkış bulundu.";
            return true;
    }

    private boolean mountainCourseActive() {
        return mountainCourseSelection && grid != null && grid.course != null;
    }

    private double effectiveSourceFloor() {
        final CourseProfile course = grid.course;
        final double floor = automaticRound <= 2 ? course.sourceFloor
                : automaticRound <= 4 ? course.shoulderSourceFloor : course.finalSourceFloor;
        // Later rounds may start on a mountain shoulder, but retain the original
        // headwater profile's descent requirement and stay above the valley base.
        return Math.max(course.base + course.minimumDrop, floor);
    }

    private boolean eligibleAutomaticSource(int index) {
        final Sample cell = grid.cells[index];
        return !cell.blocked && !cell.wet
                && edgeDistance(grid.x(index), grid.y(index)) >= inset() + grid.step
                && cell.height >= dimension.getMinHeight() + depth;
    }

    private boolean qualifiesMountainCourse(List<Point> path, ShallowRiverCarver candidate) {
        if (!mountainCourseActive()) return true;
        final Point source = path.get(0);
        final CourseGeometry course = courseGeometry(path);
        return sample(source.x, source.y).height >= effectiveSourceFloor()
                && course.newTerrainLength >= grid.course.minimumLength
                && course.span >= grid.course.minimumLength * 0.6
                && candidate.plannedWaterLevelAt(source.x, source.y)
                - candidate.plannedWaterLevelAt(course.downstreamX, course.downstreamY) >= grid.course.minimumDrop;
    }

    /** Stop at the FIRST existing or already planned water, so another river cannot inflate the new length. */
    private CourseGeometry courseGeometry(List<Point> path) {
        final Point source = path.get(0);
        if (sample(source.x, source.y).wet || plan.plannedWetAt(source.x, source.y)) {
            return new CourseGeometry(source.x, source.y, 0, 0);
        }
        double travelled = 0;
        Point downstream = source;
        for (int i = 1; i < path.size(); i++) {
            final Point a = path.get(i - 1), b = path.get(i);
            final double distance = Math.hypot(b.x - a.x, b.y - a.y);
            // Match the carver's half-block samples: whole-block steps can
            // skip a wet voxel crossed briefly by a diagonal segment.
            final int steps = Math.max(1, (int) Math.ceil(distance * 2));
            for (int step = 1; step <= steps; step++) {
                check();
                downstream = new Point((int) Math.round(a.x + (b.x - (double) a.x) * step / steps),
                        (int) Math.round(a.y + (b.y - (double) a.y) * step / steps));
                if (sample(downstream.x, downstream.y).wet || plan.plannedWetAt(downstream.x, downstream.y)) {
                    return new CourseGeometry(downstream.x, downstream.y, travelled + distance * step / steps,
                            Math.hypot(downstream.x - source.x, downstream.y - source.y));
                }
            }
            travelled += distance;
        }
        return new CourseGeometry(downstream.x, downstream.y, travelled,
                Math.hypot(downstream.x - source.x, downstream.y - source.y));
    }

    public double getLastNewTerrainLength() { return lastNewTerrainLength; }
    public int getLastWaterDrop() { return lastWaterDrop; }
    int getDrainagePasses() { return drainagePasses; }
    int getDrainageCandidatesTested() { return drainageCandidatesTested; }
    int getSearchAttempts() { return searchAttempts; }

    private boolean risesAboveEarlierLowPoint(List<Point> path) {
        double low = Double.POSITIVE_INFINITY;
        for (Point p : path) {
            check();
            final Sample point = sample(p.x, p.y);
            if (point.wet) break;
            if (point.height > low + 0.125) return true;
            low = Math.min(low, point.height);
        }
        return false;
    }

    private boolean directFollowsValley(int x1, int y1, int x2, int y2) {
        final double length = Math.hypot((double) x2 - x1, (double) y2 - y1);
        final int steps = Math.max(1, (int) Math.ceil(length));
        double lowest = sample(x1, y1).height;
        for (int i = 1; i <= steps; i++) {
            check();
            if (++edgeSamples >= MAX_EDGE_SAMPLES) {
                searchLimited = true;
                return false;
            }
            final Sample point = sample((int) Math.round(x1 + (x2 - (double) x1) * i / steps),
                    (int) Math.round(y1 + (y2 - (double) y1) * i / steps));
            if (point.blocked) return false;
            if (point.wet) return true;
            // A straight, technically carveable cut across a low ridge must not
            // outrank an existing valley. Leave even small rises to cost search.
            if (point.height > lowest + 0.125) return false;
            lowest = Math.min(lowest, point.height);
        }
        return true;
    }

    private List<Point> search(int sourceX, int sourceY, int goal, int variant, int sampleAllowance) {
        if (preservation) return searchPreserving(sourceX, sourceY, goal, variant, sampleAllowance, 0);
        final int searchBudgetStart = edgeSamples;
        final int size = grid.size();
        final double[] best = new double[size];
        final double[] travelled = new double[size];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        final int[] previous = new int[size], levels = new int[size];
        Arrays.fill(previous, -1);
        final Integer outletWater = grid.cells[goal].wet ? grid.cells[goal].water : null;
        final int sourceLevel = outletWater == null ? dryWaterUpper(sample(sourceX, sourceY).height)
                : Math.max(outletWater, dryWaterUpper(sample(sourceX, sourceY).height));
        final PriorityQueue<SearchNode> queue = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::priority)
                .thenComparingDouble(SearchNode::cost).thenComparingInt(SearchNode::index));
        final int column = (int) Math.round((sourceX - grid.firstX) / (double) grid.step);
        final int row = (int) Math.round((sourceY - grid.firstY) / (double) grid.step);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int start = grid.index(column + dx, row + dy);
                if (start < 0 || grid.cells[start].blocked) continue;
                final double distance = Math.hypot(grid.x(start) - sourceX, grid.y(start) - sourceY);
                final Segment segment = segment(sourceX, sourceY, grid.x(start), grid.y(start), sourceLevel, outletWater,
                        routingRadius(0, heuristic(start, goal)), routingRadius(distance, heuristic(start, goal)));
                if (segment == null) continue;
                final int water = segment.water;
                final double cost = Math.hypot(grid.x(start) - sourceX, grid.y(start) - sourceY) + segment.penalty;
                best[start] = cost;
                travelled[start] = distance;
                levels[start] = water;
                previous[start] = -2;
                queue.add(new SearchNode(start, water, cost, cost + priorityHeuristic(start, goal)));
            }
        }
        int localExpanded = 0;
        while (!queue.isEmpty() && localExpanded < MAX_SEARCH_NODES && !budgetExhausted()
                && (!preservation || edgeSamples - searchBudgetStart < sampleAllowance)) {
            check();
            final SearchNode current = queue.remove();
            if (current.cost != best[current.index] || current.water != levels[current.index]) continue;
            localExpanded++;
            expanded++;
            if (current.index == goal) return reconstruct(previous, goal, sourceX, sourceY);
            final int[][] directions = preservation ? VALLEY_DIRECTIONS : DIRECTIONS;
            for (int offset = 0; offset < directions.length; offset++) {
                final int[] direction = directions[(offset + variant) % directions.length];
                final int neighbour = grid.index(current.index % grid.columns + direction[0],
                        current.index / grid.columns + direction[1]);
                if (neighbour < 0 || grid.cells[neighbour].blocked) continue;
                final double distance = Math.hypot(direction[0], direction[1]) * grid.step;
                final double currentRadius = routingRadius(travelled[current.index], heuristic(current.index, goal));
                int incomingWater = current.water;
                if (preservation) {
                    final int parent = previous[current.index];
                    final double px = parent >= 0 ? grid.x(parent) : sourceX;
                    final double py = parent >= 0 ? grid.y(parent) : sourceY;
                    final double incomingLength = Math.hypot(grid.x(current.index) - px, grid.y(current.index) - py);
                    if (incomingLength > 0) {
                        final double tx = (grid.x(current.index) - px) / incomingLength
                                + (grid.x(neighbour) - grid.x(current.index)) / distance;
                        final double ty = (grid.y(current.index) - py) / incomingLength
                                + (grid.y(neighbour) - grid.y(current.index)) / distance;
                        final double tangent = Math.hypot(tx, ty);
                        if (tangent < 0.001) continue;
                        // Carver blends the incoming/outgoing tangent exactly at
                        // each corner. Check that cross-section too, not just
                        // two individually safe straight pieces.
                        final Segment corner = segment(grid.x(current.index) - tx / tangent * 0.25,
                                grid.y(current.index) - ty / tangent * 0.25,
                                grid.x(current.index) + tx / tangent * 0.25,
                                grid.y(current.index) + ty / tangent * 0.25,
                                current.water, outletWater, currentRadius, currentRadius);
                        if (corner == null) continue;
                        incomingWater = corner.water;
                    }
                }
                final Segment segment = segment(grid.x(current.index), grid.y(current.index),
                        grid.x(neighbour), grid.y(neighbour), incomingWater, outletWater, currentRadius,
                        routingRadius(travelled[current.index] + distance, heuristic(neighbour, goal)));
                if (segment == null) continue;
                final int level = segment.water;
                final double rise = Math.max(0, grid.cells[neighbour].height - grid.cells[current.index].height);
                final double cost = current.cost + distance * (1 + noise(grid.x(neighbour), grid.y(neighbour), variant)
                        * (preservation ? 0.02 : 0.12)) + rise * (preservation ? 12 : 8)
                        + segment.penalty + (preservation ? turnPenalty(previous[current.index], current.index, neighbour,
                        sourceX, sourceY) : 0);
                if (cost < best[neighbour] || (Double.isFinite(best[neighbour]) && level > levels[neighbour])) {
                    best[neighbour] = cost;
                    travelled[neighbour] = travelled[current.index] + distance;
                    levels[neighbour] = level;
                    previous[neighbour] = current.index;
                    queue.add(new SearchNode(neighbour, level, cost, cost + priorityHeuristic(neighbour, goal)));
                }
            }
        }
        return null;
    }

    /**
     * A river label includes its incoming direction and water plane. Two paths
     * at the same XY are NOT interchangeable: a cheaper low-water arrival can
     * be unable to cross the next shallow rise, or its corner can cross a bank.
     * Immutable parents also prevent another label update corrupting the path.
     */
    private List<Point> searchPreserving(int sourceX, int sourceY, int goal, int variant, int sampleAllowance, double estimatedLength) {
        return searchPreserving(sourceX, sourceY, goal, variant, sampleAllowance, estimatedLength, 4);
    }

    private List<Point> searchPreserving(int sourceX, int sourceY, int goal, int variant, int sampleAllowance,
                                       double estimatedLength, double heuristicWeight) {
        final int searchBudgetStart = edgeSamples;
        final Map<LabelKey, Arrival> best = new HashMap<>();
        final PriorityQueue<Arrival> queue = new PriorityQueue<>(Comparator.comparingDouble(Arrival::priority)
                .thenComparingDouble(Arrival::cost).thenComparingInt(a -> a.key.index)
                .thenComparingInt(a -> a.key.water).thenComparingInt(a -> a.key.heading));
        final Integer outletWater = grid.cells[goal].wet ? grid.cells[goal].water : null;
        final int sourceWater = dryWaterUpper(sample(sourceX, sourceY).height);
        final int column = (int) Math.round((sourceX - grid.firstX) / (double) grid.step);
        final int row = (int) Math.round((sourceY - grid.firstY) / (double) grid.step);
        // Width growth depends on the length of a COMPLETED route, not straight
        // distance to its goal. Minimum radius is a necessary-condition filter;
        // acceptPath always validates the actual growing full-width footprint.
        final double radius = Math.max(1.5, startWidth / 2);
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            final int index = grid.index(column + dx, row + dy);
            if (index < 0 || grid.cells[index].blocked || (drainageCorridor != null && !drainageCorridor[index])) continue;
            final Segment segment = segment(sourceX, sourceY, grid.x(index), grid.y(index), sourceWater,
                    outletWater, radius, radius);
            if (segment == null) continue;
            final double distance = Math.hypot(grid.x(index) - sourceX, grid.y(index) - sourceY);
            final double cost = distance + segment.penalty * 0.5;
            final LabelKey key = new LabelKey(index, segment.water, VALLEY_DIRECTIONS.length);
            final Arrival start = new Arrival(key, null, cost, cost + heuristic(index, goal) * heuristicWeight, distance);
            best.put(key, start);
            queue.add(start);
        }
        int localExpanded = 0;
        while (!queue.isEmpty()) {
            check();
            if (localExpanded >= MAX_SEARCH_NODES || budgetExhausted()
                    || edgeSamples - searchBudgetStart >= sampleAllowance || best.size() >= MAX_SEARCH_NODES * 8) {
                searchLimited = true;
                break;
            }
            final Arrival current = queue.remove();
            if (best.get(current.key) != current) continue;
            localExpanded++;
            expanded++;
            if (current.key.index == goal) {
                final List<Point> result = new ArrayList<>();
                for (Arrival a = current; a != null; a = a.parent) {
                    result.add(new Point(grid.x(a.key.index), grid.y(a.key.index)));
                    if (result.size() >= MAX_PATH_POINTS) { searchLimited = true; return null; }
                }
                final Point source = new Point(sourceX, sourceY);
                if (!result.get(result.size() - 1).equals(source)) result.add(source);
                Collections.reverse(result);
                return result;
            }
            for (int offset = 0; offset < VALLEY_DIRECTIONS.length; offset++) {
                final int heading = (offset + variant) % VALLEY_DIRECTIONS.length;
                final int[] direction = VALLEY_DIRECTIONS[heading];
                final int index = current.key.index;
                final int next = grid.index(index % grid.columns + direction[0], index / grid.columns + direction[1]);
                if (next < 0 || grid.cells[next].blocked || (drainageCorridor != null && !drainageCorridor[next])
                        || (current.parent != null && current.parent.key.index == next)) continue;
                final double distance = Math.hypot(direction[0], direction[1]) * grid.step;
                final double px = current.parent == null ? sourceX : grid.x(current.parent.key.index);
                final double py = current.parent == null ? sourceY : grid.y(current.parent.key.index);
                final double ax = grid.x(index) - px, ay = grid.y(index) - py, incomingLength = Math.hypot(ax, ay);
                int incomingWater = current.key.water;
                double turn = 0;
                if (incomingLength > 0) {
                    final double bx = grid.x(next) - grid.x(index), by = grid.y(next) - grid.y(index);
                    final double tx = ax / incomingLength + bx / distance, ty = ay / incomingLength + by / distance;
                    final double tangent = Math.hypot(tx, ty);
                    if (tangent < 0.001) continue;
                    final Segment corner = segment(grid.x(index) - tx / tangent * 0.25, grid.y(index) - ty / tangent * 0.25,
                            grid.x(index) + tx / tangent * 0.25, grid.y(index) + ty / tangent * 0.25,
                            incomingWater, outletWater, radius, radius);
                    if (corner == null) continue;
                    incomingWater = corner.water;
                    turn = grid.step * 0.8 * (1 - (ax * bx + ay * by) / (incomingLength * distance));
                }
                final double currentRadius = estimatedLength <= 0 ? radius
                        : routingRadius(current.travelled, Math.max(0, estimatedLength - current.travelled));
                final double nextRadius = estimatedLength <= 0 ? radius
                        : routingRadius(current.travelled + distance, Math.max(0, estimatedLength - current.travelled - distance));
                final Segment segment = segment(grid.x(index), grid.y(index), grid.x(next), grid.y(next),
                        incomingWater, outletWater, currentRadius, nextRadius);
                if (segment == null) continue;
                final double rise = Math.max(0, grid.cells[next].height - grid.cells[index].height);
                final double cost = current.cost + distance * (1 + noise(grid.x(next), grid.y(next), variant) * 0.02)
                        + rise * 64 + segment.penalty * 0.5 + turn;
                final LabelKey key = new LabelKey(next, segment.water, heading);
                final Arrival old = best.get(key);
                if (old != null && old.cost <= cost) continue;
                final Arrival arrival = new Arrival(key, current, cost, cost + heuristic(next, goal) * heuristicWeight,
                        current.travelled + distance);
                best.put(key, arrival);
                queue.add(arrival);
            }
        }
        return null;
    }

    private Segment segment(double x1, double y1, double x2, double y2, int previousWater, Integer outletWater,
                            double startRadius, double endRadius) {
        final double length = Math.hypot(x2 - x1, y2 - y1);
        final int steps = Math.max(1, (int) Math.ceil(preservation ? length * 2 : length / 2));
        final double nx = length == 0 ? 0 : -(y2 - y1) / length, ny = length == 0 ? 1 : (x2 - x1) / length;
        int water = previousWater;
        double penalty = 0;
        for (int i = 0; i <= steps; i++) {
            if (++edgeSamples > MAX_EDGE_SAMPLES) return null;
            check();
            final double x = x1 + (x2 - x1) * i / (double) steps, y = y1 + (y2 - y1) * i / (double) steps;
            final double radius = startRadius + (endRadius - startRadius) * i / steps;
            final Sample centre = sample((int) Math.round(x), (int) Math.round(y));
            if (centre.blocked) return null;
            int upper = centre.wet ? centre.water : dryWaterUpper(centre.height + (adaptation ? maximumFill : 0));
            final Sample[] core = new Sample[3];
            Sample left = null, right = null;
            for (int side = -2; side <= 2; side++) {
                final Sample bank = sample((int) Math.round(x + nx * radius * side / 2),
                        (int) Math.round(y + ny * radius * side / 2));
                if (bank.blocked) return null;
                if (!preservation && !bank.wet) upper = Math.min(upper, Math.round((float) (bank.height + (adaptation ? maximumFill : 0))));
                if (Math.abs(side) <= 1) core[side + 1] = bank;
                if (side == -2) left = bank;
                if (side == 2) right = bank;
            }
            if (preservation) {
                // Use the same half-block longitudinal density and every integer
                // lateral offset as the final carver. A low bank between sparse
                // samples must not unexpectedly lower its water plane later.
                for (int offset = -(int) Math.ceil(radius); offset <= Math.ceil(radius); offset++) {
                    final Sample bank = sample((int) Math.round(x + nx * offset), (int) Math.round(y + ny * offset));
                    if (bank.blocked) return null;
                    if (!bank.wet) upper = Math.min(upper, dryWaterUpper(bank.height));
                }
            }
            water = Math.min(water, upper);
            if (outletWater != null) water = Math.max(outletWater, water);
            if (centre.wet && water != centre.water) return null;
            if (!centre.wet && (centre.height - water > maximumCut - (preservation ? 0.65 : depth) + 0.001
                    || water - depth - centre.height > maximumFill + 0.001)) return null;
            for (Sample bank : core) {
                // Existing upstream water must match this section's plane,
                // not the lower lake/sea level at the final outlet.
                if (preservation && bank.wet && bank.water != water) return null;
                if (!bank.wet && bank.height - water > maximumCut - 0.65 + 0.001) return null;
            }
            if (water - depth < dimension.getMinHeight()) return null;
            if (preservation && !centre.wet) {
                // Distance alone favours a short cut across a hillside. Price
                // the actual extra incision and transverse slope instead, so a
                // slightly longer existing valley remains the cheaper route.
                final double extraCut = Math.max(0, centre.height - water);
                final double crossSlope = !left.wet && !right.wet
                        ? Math.abs(left.height - right.height) / (2 * radius) : 0;
                final double convexRidge = !left.wet && !right.wet
                        ? Math.max(0, centre.height - (left.height + right.height) / 2) : 0;
                double bankCut = 0;
                for (Sample bank : core) if (!bank.wet) bankCut += Math.max(0, bank.height - water);
                penalty += extraCut * 6 + crossSlope * 8 + convexRidge * 4 + bankCut;
            }
        }
        return new Segment(water, preservation ? length * penalty / (steps + 1) : 0);
    }

    private double routingRadius(double travelled, double remaining) {
        if (!preservation) return Math.max(2.5, endWidth / 2);
        final double t = travelled / Math.max(0.001, travelled + remaining);
        final double growth = t * t * (3 - 2 * t);
        return Math.max(1.5, (startWidth + (endWidth - startWidth) * growth) / 2);
    }

    // Exported full terrain support uses round(height), not floor(height).
    // Flooring wrongly forces an extra block of excavation on fractional ground.
    private int dryWaterUpper(double height) { return Math.round((float) height); }

    private double turnPenalty(int previous, int current, int next, int sourceX, int sourceY) {
        final double ax = grid.x(current) - (previous >= 0 ? grid.x(previous) : sourceX);
        final double ay = grid.y(current) - (previous >= 0 ? grid.y(previous) : sourceY);
        final double bx = grid.x(next) - grid.x(current), by = grid.y(next) - grid.y(current);
        final double divisor = Math.hypot(ax, ay) * Math.hypot(bx, by);
        return divisor == 0 ? 0 : grid.step * 0.8 * (1 - (ax * bx + ay * by) / divisor);
    }

    private double sourceSuitability(int index) {
        final Sample centre = grid.cells[index];
        double lowest = centre.height, sum = 0;
        int count = 0;
        for (int[] direction : DIRECTIONS) {
            final int next = grid.index(index % grid.columns + direction[0], index / grid.columns + direction[1]);
            if (next < 0 || grid.cells[next].blocked) continue;
            lowest = Math.min(lowest, grid.cells[next].height);
            sum += grid.cells[next].height;
            count++;
        }
        if (count == 0) return Double.MAX_VALUE;
        final double mean = sum / count;
        // Prefer low-gradient concave ground, but retain the existing high/mid/
        // low band and failed-source spacing so one valley cannot monopolise it.
        return Math.max(0, centre.height - lowest) * 0.35 + Math.max(0, centre.height - mean) * 2
                - Math.min(2, Math.max(0, mean - centre.height)) * 0.1
                + noise(grid.x(index), grid.y(index), 0) * 0.15;
    }

    private List<Point> reconstruct(int[] previous, int goal, int sourceX, int sourceY) {
        final List<Point> coarse = new ArrayList<>();
        final Set<Integer> seen = new HashSet<>();
        int current = goal;
        while (current >= 0) {
            if (!seen.add(current) || coarse.size() > MAX_PATH_POINTS) return null;
            coarse.add(new Point(grid.x(current), grid.y(current)));
            current = previous[current];
        }
        coarse.add(new Point(sourceX, sourceY));
        Collections.reverse(coarse);
        // Carver already samples continuous segments at half-block spacing.
        // Rasterising an oblique line here first turns its tangent into a stair
        // zigzag and can falsely rotate the cross-section onto a steep hillside.
        if (preservation) return coarse;
        final List<Point> dense = new ArrayList<>();
        dense.add(coarse.get(0));
        for (int i = 1; i < coarse.size(); i++) {
            final Point a = coarse.get(i - 1), b = coarse.get(i);
            final int steps = Math.max(1, (int) Math.ceil(Math.hypot(b.x - a.x, b.y - a.y)));
            for (int j = 1; j <= steps; j++) {
                final Point point = new Point((int) Math.round(a.x + (b.x - a.x) * j / (double) steps),
                        (int) Math.round(a.y + (b.y - a.y) * j / (double) steps));
                if (!point.equals(dense.get(dense.size() - 1))) dense.add(point);
                if (dense.size() > MAX_PATH_POINTS) return null;
            }
        }
        return dense;
    }

    private boolean avoidsFootprint(List<Point> path) {
        if (avoid == null) return true;
        final double radius = Math.max(2.5, endWidth / 2) + (preservation ? 0 : adaptation ? 12 : smoothBanks ? 8 : 0.5) + 1;
        final Set<Long> checked = new HashSet<>();
        for (int i = 1; i < path.size(); i++) {
            check();
            final Point a = path.get(i - 1), b = path.get(i);
            final double dx = b.x - a.x, dy = b.y - a.y, length2 = dx * dx + dy * dy;
            final int pieces = Math.max(1, (int) Math.ceil(Math.sqrt(length2) / 16));
            // Scan a narrow tube, not the entire bounding rectangle of an
            // 8K diagonal. This also keeps cancellation responsive on long paths.
            for (int part = 0; part < pieces; part++) {
                check();
                final double ax = a.x + dx * part / pieces, ay = a.y + dy * part / pieces;
                final double bx = a.x + dx * (part + 1) / pieces, by = a.y + dy * (part + 1) / pieces;
                for (int y = (int) Math.floor(Math.min(ay, by) - radius); y <= Math.ceil(Math.max(ay, by) + radius); y++) {
                for (int x = (int) Math.floor(Math.min(ax, bx) - radius); x <= Math.ceil(Math.max(ax, bx) + radius); x++) {
                    final double t = length2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.x) * dx + (y - a.y) * dy) / length2));
                    if (Math.hypot(x - a.x - dx * t, y - a.y - dy * t) > radius) continue;
                    if (!checked.add(key(x, y))) continue;
                    if (checked.size() > MAX_FOOTPRINT) {
                        searchLimited = true;
                        reason = "Kaçınma katmanı denetimi güvenli çalışma bütçesine ulaştı.";
                        return false;
                    }
                    if (sample(x, y).avoided) {
                        reason = "Alternatif yolun kıyıları seçilen kaçınma katmanına giriyor.";
                        return false;
                    }
                }
                }
            }
        }
        return true;
    }

    private void initialise() {
        if (initialised) return;
        initialised = true;
        if (dimension.getTiles().isEmpty()) {
            reason = "Dünyada taranabilecek arazi yok.";
            return;
        }
        // A distant disconnected tile must not coarsen (or erase) the valley
        // under a manual source. Missing tiles are barriers, not huge terrain.
        final Set<Long> remaining = new HashSet<>();
        for (org.pepsoft.worldpainter.Tile tile : dimension.getTiles()) remaining.add(key(tile.getX(), tile.getY()));
        final List<Long> ordered = new ArrayList<>(remaining);
        Collections.sort(ordered);
        for (long start : ordered) {
            if (!remaining.remove(start)) continue;
            final Bounds component = new Bounds();
            final java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                check();
                final long tile = queue.remove();
                final int x = (int) (tile >> 32), y = (int) tile;
                component.add(x, y);
                for (int[] direction : CARDINAL) {
                    final long neighbour = key(x + direction[0], y + direction[1]);
                    if (remaining.remove(neighbour)) queue.add(neighbour);
                }
            }
            components.add(component);
        }
        components.sort(Comparator.comparingInt((Bounds b) -> b.tiles.size()).reversed()
                .thenComparingInt(b -> b.lowX).thenComparingInt(b -> b.lowY));
        initialiseGrid(components.get(0));
    }

    private void initialiseGrid(Bounds component) {
        activeComponent = component;
        grid = null;
        minX = (long) component.lowX * 128;
        minY = (long) component.lowY * 128;
        maxX = ((long) component.highX + 1) * 128 - 1;
        maxY = ((long) component.highY + 1) * 128 - 1;
        final long width = maxX - minX + 1, height = maxY - minY + 1;
        if (minX < Integer.MIN_VALUE + 64L || minY < Integer.MIN_VALUE + 64L
                || maxX > Integer.MAX_VALUE - 64L || maxY > Integer.MAX_VALUE - 64L) {
            searchLimited = true;
            reason = "Dünya koordinat sınırları güvenli rota taramasını aşıyor.";
            return;
        }
        long step = Math.max(preservation ? 2 : 4, (long) Math.ceil(Math.sqrt(width * (double) height / MAX_GRID_CELLS)));
        while (((width + step - 1) / step) * ((height + step - 1) / step) > MAX_GRID_CELLS) step++;
        if (step > 4096) {
            searchLimited = true;
            reason = "Dağınık dünya sınırları bu sınırlı rota taraması için fazla büyük.";
            return;
        }
        final int columns = (int) ((width + step - 1) / step), rows = (int) ((height + step - 1) / step);
        grid = new Grid((int) minX + (int) step / 2, (int) minY + (int) step / 2, (int) step, columns, rows);
        for (int index = 0; index < grid.size(); index++) {
            check();
            grid.cells[index] = sample(grid.x(index), grid.y(index));
            scanned++;
        }
        grid.course = measureCourseProfile();
    }

    private CourseProfile measureCourseProfile() {
        final List<Float> land = new ArrayList<>(), valleys = new ArrayList<>();
        for (int index = 0; index < grid.size(); index++) {
            check();
            final Sample centre = grid.cells[index];
            // Only potential sources may set the headwater threshold. A high
            // excluded boundary rim must not disqualify every interior source.
            if (!eligibleAutomaticSource(index)) continue;
            land.add(centre.height);
            double low = centre.height, sum = 0;
            int count = 0;
            for (int[] direction : DIRECTIONS) {
                final int other = grid.index(index % grid.columns + direction[0], index / grid.columns + direction[1]);
                if (other < 0 || grid.cells[other].blocked || grid.cells[other].wet) continue;
                low = Math.min(low, grid.cells[other].height);
                sum += grid.cells[other].height;
                count++;
            }
            // Estimate headwater elevations from locally gentle/concave ground,
            // not from high ridgelines. This is a profile, not a hard slope mask.
            if (count >= 4 && centre.height - low <= Math.max(0.5, maximumCut - depth) * grid.step / 3.0
                    && centre.height - sum / count <= 0.125) valleys.add(centre.height);
        }
        if (land.size() < 16) return null;
        Collections.sort(land);
        Collections.sort(valleys);
        final List<Float> valleyProfile = valleys.size() < 16 ? land : valleys;
        final double base = percentile(valleyProfile, 0.10);
        final double landTop = percentile(land, 0.90);
        final double top = Math.min(percentile(valleyProfile, 0.99), landTop);
        final double relief = Math.max(top - base, (landTop - percentile(land, 0.10)) * 0.25);
        if (relief < Math.max(8, depth * 4)) return null;
        return new CourseProfile(top, Math.min(percentile(valleyProfile, 0.95), landTop),
                Math.min(percentile(valleyProfile, 0.90), landTop), base, Math.max(4, relief * 0.35),
                Math.max(minimumLength(), Math.min(1024, Math.max(maxX - minX + 1, maxY - minY + 1) * 0.2)));
    }

    private static double percentile(List<Float> values, double fraction) {
        return values.get(Math.min(values.size() - 1, (int) Math.floor((values.size() - 1) * fraction)));
    }

    private Sample sample(int x, int y) {
        final long key = key(x, y);
        Sample result = cache.get(key);
        if (result != null) return result;
        if (!dimension.isTilePresent(x >> 7, y >> 7)) result = new Sample(Float.NaN, 0, false, true, false);
        else {
            final boolean avoided = avoid != null && avoid.test(x, y);
            final boolean blocked = avoided || dimension.getBitLayerValueAt(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(NotPresent.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(NotPresentBlock.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(ReadOnly.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y)
                    || dimension.getBitLayerValueAt(River.INSTANCE, x, y);
            final float height = dimension.getHeightAt(x, y);
            final int water = dimension.getWaterLevelAt(x, y);
            result = new Sample(height, water, water > Math.round(height), blocked || !Float.isFinite(height), avoided);
        }
        if (cache.size() >= MAX_CACHE) cache.remove(cache.keySet().iterator().next());
        cache.put(key, result);
        return result;
    }

    private boolean shore(int index) {
        for (int[] direction : CARDINAL) {
            final int other = grid.index(index % grid.columns + direction[0], index / grid.columns + direction[1]);
            if (other >= 0 && !grid.cells[other].blocked && !grid.cells[other].wet) return true;
        }
        return false;
    }

    private boolean budgetExhausted() {
        if (attempts >= candidateSearchLimit() || expanded >= MAX_TOTAL_NODES || edgeSamples >= MAX_EDGE_SAMPLES) {
            searchLimited = true;
            reason = "Sınırlı alternatif arama çalışma bütçesine ulaştı; kabul edilmeyen rotalar dünyayı değiştirmedi.";
            return true;
        }
        return false;
    }

    private int candidateSearchLimit() {
        // Reserve room for all twelve shortlisted final revalidations. A full
        // discovery budget must neither prevent a safe commit nor exceed 192.
        return preservation && mountainCourseSelection && !committingRanked
                ? MAX_CANDIDATES - 12 : MAX_CANDIDATES;
    }

    private double heuristic(int from, int to) { return Math.hypot(grid.x(from) - grid.x(to), grid.y(from) - grid.y(to)); }
    // A bounded terrain search needs goal-directed progress, not a global
    // least-cost flood. Final carver validation is unchanged by this priority.
    private double priorityHeuristic(int from, int to) { return heuristic(from, to) * (preservation ? 4 : 1); }
    private void recordRejection(String cause) {
        lastCause = cause;
        final int detail = cause.indexOf(" [");
        rejections.merge(detail < 0 ? cause : cause.substring(0, detail), 1, Integer::sum);
    }
    private double edgeDistance(int x, int y) { return Math.min(Math.min(x - minX, maxX - x), Math.min(y - minY, maxY - y)); }
    private double inset() { return Math.ceil(Math.max(2.5, endWidth / 2)) + 2; }
    private double minimumLength() { return Math.max(8, Math.min(64, Math.min(maxX - minX + 1, maxY - minY + 1) / 8.0)); }
    private void check() { if (progress != null) progress.checkForCancel(); }
    private double noise(int x, int y, int variant) {
        long hash = seed ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) y * 0xC2B2AE3D27D4EB4FL) ^ variant;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        return ((hash ^ (hash >>> 31)) >>> 11) * 0x1.0p-53;
    }
    private static double length(List<Point> path) {
        double result = 0;
        for (int i = 1; i < path.size(); i++) result += Math.hypot(path.get(i).x - path.get(i - 1).x, path.get(i).y - path.get(i - 1).y);
        return result;
    }
    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
    private record Point(int x, int y) {}
    private record Sample(float height, int water, boolean wet, boolean blocked, boolean avoided) {}
    private record SearchNode(int index, int water, double cost, double priority) {}
    private record LabelKey(int index, int water, int heading) {}
    private record Arrival(LabelKey key, Arrival parent, double cost, double priority, double travelled) {}
    private record Deferred(List<Point> path, int sourceX, int sourceY, int outlet, Bounds component) {}
    private record RankedCandidate(List<Point> path, int sourceX, int sourceY, int outlet, Bounds component,
                                   double newLength, int drop, double span, double preferredLength) {}
    private static final Comparator<RankedCandidate> RANKED_ORDER = Comparator
            .comparingDouble(RankedCandidate::newLength).reversed()
            .thenComparing(Comparator.comparingInt(RankedCandidate::drop).reversed())
            .thenComparing(Comparator.comparingDouble(RankedCandidate::span).reversed())
            .thenComparingInt(RankedCandidate::sourceX).thenComparingInt(RankedCandidate::sourceY)
            .thenComparingInt(RankedCandidate::outlet);
    private record OutletArea(int x, int y, int water) {}
    private record CourseProfile(double sourceFloor, double shoulderSourceFloor, double finalSourceFloor,
                                 double base, double minimumDrop, double minimumLength) {}
    private record CourseGeometry(int downstreamX, int downstreamY, double newTerrainLength, double span) {}
    private static final class Bounds {
        final Set<Long> tiles = new HashSet<>();
        int lowX = Integer.MAX_VALUE, lowY = Integer.MAX_VALUE, highX = Integer.MIN_VALUE, highY = Integer.MIN_VALUE;
        void add(int x, int y) {
            tiles.add(key(x, y));
            lowX = Math.min(lowX, x); highX = Math.max(highX, x);
            lowY = Math.min(lowY, y); highY = Math.max(highY, y);
        }
    }
    private record Segment(int water, double penalty) {}
    private static final class Grid {
        Grid(int firstX, int firstY, int step, int columns, int rows) {
            this.firstX = firstX; this.firstY = firstY; this.step = step; this.columns = columns; this.rows = rows;
            cells = new Sample[columns * rows];
        }
        int size() { return cells.length; }
        int x(int index) { return firstX + index % columns * step; }
        int y(int index) { return firstY + index / columns * step; }
        int index(int column, int row) { return column < 0 || column >= columns || row < 0 || row >= rows ? -1 : row * columns + column; }
        final int firstX, firstY, step, columns, rows;
        final Sample[] cells;
        CourseProfile course;
        RiverDrainageCandidates drainage;
    }

    private final Dimension dimension;
    private final ShallowRiverCarver plan;
    private final double startWidth, endWidth, depth, maximumCut, maximumFill;
    private final boolean smoothBanks, adaptation, preservation;
    private final long seed;
    private final ScriptProgress progress;
    private final BiPredicate<Integer, Integer> avoid;
    private final Map<Long, Sample> cache = new LinkedHashMap<>(1024, 0.75f, true);
    private final List<Point> acceptedSources = new ArrayList<>();
    private final List<Point> attemptedAutoSources = new ArrayList<>();
    private final Set<Long> attemptedDrainageSources = new HashSet<>();
    private final List<RankedCandidate> ranked = new ArrayList<>();
    private boolean collectingCandidates, committingRanked;
    private boolean[] drainageCorridor;
    private int drainagePasses, drainageCandidatesTested;
    private Grid grid;
    private final List<Bounds> components = new ArrayList<>();
    private Bounds activeComponent;
    private final List<Deferred> deferred = new ArrayList<>();
    private boolean automaticCandidate;
    private boolean mountainCourseSelection;
    private boolean initialised;
    private long minX, minY, maxX, maxY;
    private int scanned, expanded, edgeSamples, attempts, accepted, searchAttempts, automaticRound;
    private boolean searchLimited;
    private double lastLength;
    private double lastNewTerrainLength;
    private int lastWaterDrop;
    private String reason = "";
    private String lastCandidate = "yok";
    private String lastCause = "yok";
    private String lastProbeRejection = "";
    private final Map<String, Integer> rejections = new LinkedHashMap<>();
    private static final int MAX_GRID_CELLS = 65_536, MAX_CACHE = 65_536, MAX_SEARCH_NODES = 12_000,
            MAX_TOTAL_NODES = 360_000, MAX_EDGE_SAMPLES = 24_000_000, MAX_CANDIDATES = 192, MAX_SOURCES = 32,
            FAST_SOURCE_ALLOWANCE = 62_500, EXTENDED_SOURCE_ALLOWANCE = 250_000,
            MAX_OUTLETS_PER_SOURCE = 8, MAX_PATH_POINTS = 20_000, MAX_FOOTPRINT = 350_000;
    private static final int[][] CARDINAL = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } },
            DIRECTIONS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 }, { -1, -1 }, { 1, -1 }, { -1, 1 }, { 1, 1 } },
            VALLEY_DIRECTIONS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 },
                    { -1, -1 }, { 1, -1 }, { -1, 1 }, { 1, 1 },
                    { -2, -1 }, { -2, 1 }, { 2, -1 }, { 2, 1 },
                    { -1, -2 }, { -1, 2 }, { 1, -2 }, { 1, 2 } };
}
