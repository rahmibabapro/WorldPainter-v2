package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.minecraft.Material;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.Void;

import java.util.*;

import static org.pepsoft.worldpainter.tools.scripts.AxiomTextureProfile.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

/**
 * Geometry-conditioned categorical image quilting. Donors are actual contiguous 16x16 source
 * neighbourhoods, with four-block minimum-error seams. Memory is source-sized plus a sixteen-row
 * output ribbon (512 KiB at width 8192); filtered passes add only a matching 128 KiB selection
 * ribbon, rather than a second world-sized terrain/height map.
 * This transfers structural top texture only; vertical cliff faces and foliage need a separate
 * export representation. Global material percentages need not match on a different height field.
 */
public final class AxiomTextureTransfer {
    private AxiomTextureTransfer() { }

    public static Summary apply(Dimension dimension, AxiomTextureProfile profile, Map<Material, Terrain> terrains,
                                long seed, ProgressReceiver progress) throws OperationCancelled {
        return apply(dimension, profile, terrains, seed, progress, null, null);
    }

    /** The callback runs once for each final painted dry cell, after overlap seams are resolved. */
    public static Summary apply(Dimension dimension, AxiomTextureProfile profile, Map<Material, Terrain> terrains,
                                long seed, ProgressReceiver progress, SnowMaskSink snowMask) throws OperationCancelled {
        return apply(dimension, profile, terrains, seed, progress, snowMask, null);
    }

    /**
     * Transfers into cells admitted by {@code targetFilter}. The filter never sees water, void or
     * not-present cells. Its result is kept in the existing sixteen-row ribbon, not in a second
     * world-sized mask, so a filtered 8K transfer has bounded extra memory.
     */
    public static Summary apply(Dimension dimension, AxiomTextureProfile profile, Map<Material, Terrain> terrains,
                                long seed, ProgressReceiver progress, SnowMaskSink snowMask,
                                TargetCellFilter targetFilter) throws OperationCancelled {
        return apply(dimension, profile, terrains, seed, progress, snowMask, targetFilter, MossPolicy.MOUNTAIN_BAND);
    }

    /** As above, with an explicit source-context policy for measured moss materials. */
    public static Summary apply(Dimension dimension, AxiomTextureProfile profile, Map<Material, Terrain> terrains,
                                long seed, ProgressReceiver progress, SnowMaskSink snowMask,
                                TargetCellFilter targetFilter, MossPolicy mossPolicy) throws OperationCancelled {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(mossPolicy, "mossPolicy");
        final Terrain[] mapped = new Terrain[profile.palette.length];
        for (int i = 0; i < mapped.length; i++) {
            mapped[i] = terrains.get(profile.palette[i]);
        }
        final Target target = new Target(dimension);
        final Bounds bounds = measure(dimension, progress);
        if (bounds.eligible == 0) return summary(profile, bounds, new long[mapped.length], 0, 0, false, 0, 0, 0,
                bounds.eligible, 0);
        if (targetFilter == null && sameHeightField(target, profile, bounds, progress)) {
            final long[] counts = new long[mapped.length];
            long changed = 0, sourceSnowCells = 0, mossRedirected = 0;
            for (int y = 0; y < profile.length; y++) {
                for (int x = 0; x < profile.width; x++) {
                    final int i = x + y * profile.width, wx = bounds.minX + x, wy = bounds.minY + y;
                    if (profile.labels[i] < 0 || ! target.dry(wx, wy)) continue;
                    final int label = outputLabel(profile, target, i, wx, wy, mossPolicy);
                    final boolean snowy = isSummitWhite(profile, target, i, wx, wy, label);
                    if (snowy) sourceSnowCells++;
                    if (snowMask != null) snowMask.accept(wx, wy, snowy);
                    if (label < 0) continue;
                    if (label != profile.labels[i]) mossRedirected++;
                    if (target.paint(wx, wy, mappedTerrain(profile, mapped, label))) changed++;
                    counts[label]++;
                }
                check(progress, 0.1f + 0.9f * (y + 1) / profile.length);
            }
            return summary(profile, bounds, counts, changed, 0, mossRedirected == 0, 0, sourceSnowCells, mossRedirected,
                    bounds.eligible, 0);
        }
        if (profile.origins.length == 0) throw new IllegalArgumentException("Blueprint needs a contiguous 16 by 16 structural surface for texture transfer");
        final long ribbonSize = (long) bounds.width() * PATCH;
        if (ribbonSize > MAX_RIBBON_CELLS) throw new IllegalArgumentException("World is too widely scattered for the texture ribbon; maximum span is 1,048,576 blocks");
        final Worker worker = new Worker(target, profile, mapped, bounds, seed, snowMask, targetFilter, mossPolicy);
        worker.run(progress);
        return summary(profile, bounds, worker.counts, worker.changed, worker.patches, false,
                worker.patches == 0 ? 0 : worker.geometryError / worker.patches, worker.sourceSnowCells, worker.mossRedirected,
                targetFilter == null ? bounds.eligible : worker.selectedCells, worker.ribbonBytes());
    }

    /**
     * Applies one native mixed terrain directly to the accepted dry-land cells. Unlike
     * {@link #apply}, this does not choose, orient or seam source patches: WorldPainter's own
     * mixed-material generator varies the surface from the world seed and coordinates at export.
     */
    public static MixedTerrainSummary applyMixedTerrain(Dimension dimension, Terrain terrain,
                                                        TargetCellFilter targetFilter, ProgressReceiver progress,
                                                        SnowMaskSink snowMask) throws OperationCancelled {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(targetFilter, "targetFilter");
        final Collection<? extends Tile> tiles = dimension.getTiles();
        final Target target = new Target(dimension);
        long selected = 0, changed = 0, protectedCells = 0, rejected = 0;
        int tileNumber = 0;
        for (Tile tile : tiles) {
            final int worldX = tile.getX() * TILE_SIZE;
            final int worldY = tile.getY() * TILE_SIZE;
            for (int y = 0; y < TILE_SIZE; y++) {
                for (int x = 0; x < TILE_SIZE; x++) {
                    if (! target.dry(tile, x, y)) {
                        protectedCells++;
                        continue;
                    }
                    final int wx = worldX + x, wy = worldY + y;
                    final float height = tile.getHeight(x, y);
                    if (! targetFilter.accept(wx, wy, height, target.slopeDegrees(wx, wy, height))) {
                        rejected++;
                        continue;
                    }
                    selected++;
                    if (snowMask != null) {
                        snowMask.accept(wx, wy, false);
                    }
                    if (target.paint(wx, wy, terrain)) {
                        changed++;
                    }
                    if (((x + y * TILE_SIZE) & 4095) == 0 && progress != null) {
                        progress.checkForCancellation();
                    }
                }
            }
            check(progress, ++tileNumber / (float) Math.max(1, tiles.size()));
        }
        return new MixedTerrainSummary(selected, changed, protectedCells, rejected);
    }

    private static Bounds measure(Dimension dimension, ProgressReceiver progress) throws OperationCancelled {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        float minHeight = Float.POSITIVE_INFINITY, maxHeight = Float.NEGATIVE_INFINITY;
        long eligible = 0, protectedCells = 0;
        int checked = 0;
        final Collection<? extends Tile> tiles = dimension.getTiles();
        for (Tile tile : tiles) {
            minX = Math.min(minX, tile.getX() * 128);
            minY = Math.min(minY, tile.getY() * 128);
            maxX = Math.max(maxX, tile.getX() * 128 + 127);
            maxY = Math.max(maxY, tile.getY() * 128 + 127);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final float h = tile.getHeight(x, y);
                if (! Float.isFinite(h) || h <= tile.getWaterLevel(x, y) + 1.0f || tile.getBitLayerValue(Void.INSTANCE, x, y)
                        || tile.getBitLayerValue(NotPresent.INSTANCE, x, y) || tile.getBitLayerValue(NotPresentBlock.INSTANCE, x, y)) {
                    protectedCells++;
                    continue;
                }
                eligible++;
                minHeight = Math.min(minHeight, h);
                maxHeight = Math.max(maxHeight, h);
            }
            check(progress, 0.07f * ++checked / Math.max(1, tiles.size()));
        }
        return new Bounds(minX, minY, maxX, maxY, minHeight, maxHeight, eligible, protectedCells);
    }

    private static boolean sameHeightField(Target target, AxiomTextureProfile profile, Bounds bounds, ProgressReceiver progress) throws OperationCancelled {
        if (bounds.width() != profile.width || bounds.height() != profile.length) return false;
        for (int y = 0; y < profile.length; y++) {
            for (int x = 0; x < profile.width; x++) {
                final int i = x + y * profile.width, wx = bounds.minX + x, wy = bounds.minY + y;
                if ((profile.labels[i] >= 0) != target.dry(wx, wy)) return false;
                if (profile.labels[i] >= 0 && Math.abs(normalise(target.height(wx, wy, 0), bounds.minHeight, bounds.maxHeight)
                        - profile.features[4 * i]) > 0.00001f) return false;
            }
            check(progress, 0.07f + 0.03f * (y + 1) / profile.length);
        }
        return true;
    }

    private static Summary summary(AxiomTextureProfile p, Bounds b, long[] counts, long changed, long patches, boolean identity,
                                   double error, long sourceSnowCells, long mossRedirected, long eligibleCells,
                                   long ribbonBytes) {
        final Map<String, Long> actual = new TreeMap<>();
        long painted = 0;
        for (int i = 0; i < counts.length; i++) if (counts[i] != 0) { actual.put(materialKey(p.palette[i]), counts[i]); painted += counts[i]; }
        final Map<String, Double> reference = new TreeMap<>();
        double variation = 0;
        final Set<String> keys = new TreeSet<>(p.getSummary().materialCounts().keySet());
        keys.addAll(actual.keySet());
        for (String key : keys) {
            final double sourceRatio = p.getSummary().materialCounts().getOrDefault(key, 0L) / (double) p.getSummary().structuralColumns();
            reference.put(key, sourceRatio);
            variation += Math.abs(sourceRatio - (painted == 0 ? 0 : actual.getOrDefault(key, 0L) / (double) painted));
        }
        return new Summary(painted, changed, b.protectedCells, Math.max(0, eligibleCells - painted), patches, identity, b.minHeight, b.maxHeight,
                Collections.unmodifiableMap(actual), Collections.unmodifiableMap(reference), variation / 2, error,
                identity ? 0 : ribbonBytes, sourceSnowCells, mossRedirected);
    }

    private static final class Worker {
        Worker(Target target, AxiomTextureProfile profile, Terrain[] terrains, Bounds bounds, long seed, SnowMaskSink snowMask,
               TargetCellFilter targetFilter, MossPolicy mossPolicy) {
            this.target = target; p = profile; mapped = terrains; b = bounds; this.seed = seed;
            this.snowMask = snowMask; this.targetFilter = targetFilter; this.mossPolicy = mossPolicy;
            ribbon = new int[b.width() * PATCH];
            Arrays.fill(ribbon, -1);
            selection = (targetFilter != null) ? new boolean[b.width() * PATCH] : null;
            counts = new long[mapped.length];
            previousDonors = new int[(b.width() + STRIDE - 1) / STRIDE];
            Arrays.fill(previousDonors, -1);
        }

        void run(ProgressReceiver progress) throws OperationCancelled {
            for (int y = b.minY; y <= b.maxY; y += STRIDE) {
                final int firstClear = y == b.minY ? 0 : OVERLAP;
                for (int row = firstClear; row < PATCH; row++) {
                    final int start = Math.floorMod(y + row - b.minY, PATCH) * b.width();
                    Arrays.fill(ribbon, start, start + b.width(), -1);
                }
                if (selection != null) readSelectionRows(y, firstClear, progress);
                int leftDonor = -1, column = 0;
                for (int x = b.minX; x <= b.maxX; x += STRIDE, column++) {
                    readTarget(x, y);
                    if (! anyValid) { previousDonors[column] = -1; leftDonor = -1; continue; }
                    final int donor = choose(x, y, leftDonor, previousDonors[column]);
                    quilt(x, y, donor);
                    leftDonor = donor;
                    previousDonors[column] = donor;
                    patches++;
                    if ((column & 31) == 0 && progress != null) progress.checkForCancellation();
                }
                final int rows = y + STRIDE > b.maxY ? Math.min(PATCH, b.maxY - y + 1) : STRIDE;
                for (int row = 0; row < rows; row++) for (int x = b.minX; x <= b.maxX; x++) {
                    final int source = ribbon[ribbonIndex(x, y + row)];
                    if (source < 0 || (selection != null && ! selection[ribbonIndex(x, y + row)])) continue;
                    final int label = outputLabel(p, target, source, x, y + row, mossPolicy);
                    final boolean snowy = isSummitWhite(p, target, source, x, y + row, label);
                    if (snowy) sourceSnowCells++;
                    if (snowMask != null) snowMask.accept(x, y + row, snowy);
                    if (label < 0) continue;
                    if (label != p.labels[source]) mossRedirected++;
                    if (target.paint(x, y + row, mappedTerrain(p, mapped, label))) changed++;
                    counts[label]++;
                }
                check(progress, Math.min(1.0f, 0.1f + 0.9f * (y - b.minY + rows) / b.height()));
            }
            check(progress, 1);
        }

        /** Computes a filtered cell once, retaining the four overlap rows for the next patch row. */
        void readSelectionRows(int y, int firstClear, ProgressReceiver progress) throws OperationCancelled {
            for (int row = firstClear; row < PATCH; row++) {
                final int wy = y + row;
                final int start = Math.floorMod(wy - b.minY, PATCH) * b.width();
                if (wy > b.maxY) {
                    Arrays.fill(selection, start, start + b.width(), false);
                    continue;
                }
                for (int x = b.minX; x <= b.maxX; x++) {
                    final boolean accepted = target.accepts(x, wy, targetFilter);
                    selection[start + x - b.minX] = accepted;
                    if (accepted) selectedCells++;
                    if (((x - b.minX) & 4095) == 0 && progress != null) progress.checkForCancellation();
                }
            }
        }

        void readTarget(int x, int y) {
            anyValid = false;
            Arrays.fill(targetFeatures, 0);
            for (int dy = 0; dy < PATCH; dy++) for (int dx = 0; dx < PATCH; dx++) {
                final int i = dx + dy * PATCH, wx = x + dx, wy = y + dy;
                valid[i] = wx <= b.maxX && wy <= b.maxY
                        && (selection == null ? target.dry(wx, wy) : selection[ribbonIndex(wx, wy)]);
                targetHeights[i] = valid[i] ? target.height(wx, wy, 0) : Float.NaN;
                anyValid |= valid[i];
            }
            for (int sy = 0; sy < SAMPLES.length; sy++) for (int sx = 0; sx < SAMPLES.length; sx++) {
                final int dx = SAMPLES[sx], dy = SAMPLES[sy], i = sx + sy * SAMPLES.length, wx = x + dx, wy = y + dy;
                sampleValid[i] = valid[dx + dy * PATCH];
                if (! sampleValid[i]) continue;
                final float h = targetHeights[dx + dy * PATCH];
                targetFeatures[4 * i] = normalise(h, b.minHeight, b.maxHeight);
                targetFeatures[4 * i + 1] = gradient((target.height(wx + 2, wy, h) - target.height(wx - 2, wy, h)) / 4);
                targetFeatures[4 * i + 2] = gradient((target.height(wx, wy + 2, h) - target.height(wx, wy - 2, h)) / 4);
                targetFeatures[4 * i + 3] = curvature(h, target.height(wx - 4, wy, h), target.height(wx + 4, wy, h),
                        target.height(wx, wy - 4, h), target.height(wx, wy + 4, h));
            }
        }

        int choose(int x, int y, int left, int above) {
            int count = 0;
            if (left >= 0 && left % p.width + STRIDE <= p.width - PATCH && validOrigin(left + STRIDE)) candidates[count++] = left + STRIDE;
            if (above >= 0 && above / p.width + STRIDE <= p.length - PATCH && validOrigin(above + STRIDE * p.width)) candidates[count++] = above + STRIDE * p.width;
            int sample = 12;
            if (! sampleValid[sample]) for (int i = 0; i < sampleValid.length; i++) if (sampleValid[i]) { sample = i; break; }
            final int preferred = bucket(targetFeatures[4 * sample], targetFeatures[4 * sample + 1], targetFeatures[4 * sample + 2]);
            final int hb = preferred / (SLOPE_BINS * ASPECT_BINS), sb = (preferred / ASPECT_BINS) % SLOPE_BINS, ab = preferred % ASPECT_BINS;
            final long hash = hash(seed ^ ((long) x << 32) ^ (y & 0xffffffffL));
            // Search progressively farther feature bins; all candidates still undergo 25-point
            // matching. A flat source cannot randomly win on a steep target just by bucket fallback.
            for (int distance = 0; distance < 14 && count < CANDIDATES - 4; distance++) {
                for (int bucket = 0; bucket < p.candidateBuckets.length && count < CANDIDATES - 4; bucket++) {
                    final int bh = bucket / (SLOPE_BINS * ASPECT_BINS), bs = (bucket / ASPECT_BINS) % SLOPE_BINS, ba = bucket % ASPECT_BINS;
                    if (2 * Math.abs(bh - hb) + Math.abs(bs - sb) + (ba == ab ? 0 : ba == 0 || ab == 0 ? 1 : 2) != distance) continue;
                    final int[] pool = p.candidateBuckets[bucket];
                    final int take = Math.min(pool.length, distance == 0 ? 24 : 8);
                    for (int j = 0; j < take && count < CANDIDATES - 4; j++) {
                        candidates[count++] = pool[(int) Math.floorMod(hash(hash + bucket * 31L + j * 0x9e3779b97f4a7c15L), pool.length)];
                    }
                }
            }
            for (int j = 0; j < 4; j++) candidates[count++] = p.origins[(int) Math.floorMod(hash(hash + j), p.origins.length)];
            int best = candidates[0];
            double bestScore = Double.POSITIVE_INFINITY, bestGeometry = 0;
            for (int candidateIndex = 0; candidateIndex < count; candidateIndex++) {
                final int origin = candidates[candidateIndex];
                double error = 0;
                int n = 0;
                for (int sy = 0; sy < SAMPLES.length; sy++) for (int sx = 0; sx < SAMPLES.length; sx++) {
                    final int i = sx + sy * SAMPLES.length;
                    if (! sampleValid[i]) continue;
                    final int si = 4 * (origin + SAMPLES[sx] + SAMPLES[sy] * p.width);
                    for (int f = 0; f < 4; f++) { final double delta = p.features[si + f] - targetFeatures[4 * i + f]; error += delta * delta * WEIGHTS[f]; }
                    n++;
                }
                error /= Math.max(1, n);
                int mismatches = 0, overlap = 0;
                for (int dy = 0; dy < PATCH; dy++) for (int dx = 0; dx < PATCH; dx++) {
                    if (dx >= OVERLAP && dy >= OVERLAP || ! valid[dx + dy * PATCH]) continue;
                    final int old = ribbon[ribbonIndex(x + dx, y + dy)];
                    if (old < 0) continue;
                    overlap++;
                    if (! sameSurface(old, sourceCell(origin, dx, dy))) mismatches++;
                }
                final double score = error + (overlap == 0 ? 0 : 0.18 * mismatches / overlap)
                        + (hash(hash ^ origin) >>> 40) * 0x1.0p-24 * 0.000001;
                if (score < bestScore) { bestScore = score; best = origin; bestGeometry = error; }
            }
            geometryError += bestGeometry;
            return best;
        }

        void quilt(int x, int y, int origin) {
            seam(x, y, origin, true, leftSeam);
            seam(x, y, origin, false, topSeam);
            for (int dy = 0; dy < PATCH; dy++) for (int dx = 0; dx < PATCH; dx++) {
                if (! valid[dx + dy * PATCH]) continue;
                final int at = ribbonIndex(x + dx, y + dy), old = ribbon[at];
                if (old >= 0 && (dx < OVERLAP && dx < leftSeam[dy] || dy < OVERLAP && dy < topSeam[dx])) continue;
                ribbon[at] = sourceCell(origin, dx, dy);
            }
        }

        void seam(int x, int y, int origin, boolean vertical, int[] seam) {
            for (int along = 0; along < PATCH; along++) for (int across = 0; across < OVERLAP; across++) {
                final int dx = vertical ? across : along, dy = vertical ? along : across, i = along * OVERLAP + across;
                final int old = valid[dx + dy * PATCH] ? ribbon[ribbonIndex(x + dx, y + dy)] : -1;
                float cost = old < 0 || sameSurface(old, sourceCell(origin, dx, dy)) ? 0 : 1;
                int predecessor = across;
                if (along > 0) {
                    float previous = Float.POSITIVE_INFINITY;
                    for (int k = Math.max(0, across - 1); k <= Math.min(OVERLAP - 1, across + 1); k++) {
                        if (seamCost[(along - 1) * OVERLAP + k] < previous) { previous = seamCost[(along - 1) * OVERLAP + k]; predecessor = k; }
                    }
                    cost += previous;
                }
                seamCost[i] = cost;
                seamPredecessor[i] = predecessor;
            }
            int end = 0;
            for (int k = 1; k < OVERLAP; k++) if (seamCost[(PATCH - 1) * OVERLAP + k] < seamCost[(PATCH - 1) * OVERLAP + end]) end = k;
            for (int along = PATCH - 1; along >= 0; along--) { seam[along] = end; end = seamPredecessor[along * OVERLAP + end]; }
        }

        int sourceCell(int origin, int x, int y) { return origin + x + y * p.width; }
        boolean sameSurface(int first, int second) { return p.labels[first] == p.labels[second] && p.snowMask[first] == p.snowMask[second]; }
        boolean validOrigin(int origin) { return Arrays.binarySearch(p.origins, origin) >= 0; }
        int ribbonIndex(int x, int y) { return Math.floorMod(y - b.minY, PATCH) * b.width() + x - b.minX; }

        final Target target;
        final AxiomTextureProfile p;
        final Terrain[] mapped;
        final Bounds b;
        final long seed;
        final SnowMaskSink snowMask;
        final TargetCellFilter targetFilter;
        final MossPolicy mossPolicy;
        final int[] ribbon, previousDonors;
        final boolean[] selection;
        final long[] counts;
        final boolean[] valid = new boolean[PATCH * PATCH], sampleValid = new boolean[25];
        final float[] targetHeights = new float[PATCH * PATCH], targetFeatures = new float[100], seamCost = new float[PATCH * OVERLAP];
        final int[] candidates = new int[CANDIDATES], seamPredecessor = new int[PATCH * OVERLAP], leftSeam = new int[PATCH], topSeam = new int[PATCH];
        boolean anyValid;
        long changed, patches, sourceSnowCells, mossRedirected, selectedCells;
        double geometryError;

        long ribbonBytes() {
            return (long) b.width() * PATCH * (Integer.BYTES + (selection == null ? 0 : 1));
        }
    }

    /**
     * Source moss is a palette cue, not permission to put green blocks on an exposed cliff.
     * A rejected moss cell uses the nearest genuine non-moss/non-snow source material; when a
     * deliberately all-moss source offers no such material, the old target terrain is retained.
     */
    private static int outputLabel(AxiomTextureProfile profile, Target target, int source, int x, int y, MossPolicy mossPolicy) {
        final int label = profile.labels[source];
        if (label < 0) {
            return label;
        }
        // White source terrain is a summit treatment, not a material for low hills or exposed
        // cliff faces. Keep its genuine measured undercoat outside the summit envelope.
        if (profile.snowMask[source] && ! target.supportsSummitWhite(x, y)) {
            return profile.warmLabels[source] >= 0 ? profile.warmLabels[source] : profile.mossFallbackLabels[source];
        }
        if (! AxiomTextureProfile.isMossMaterial(profile.palette[label]) || target.acceptsMoss(x, y, mossPolicy)) {
            return label;
        }
        return profile.mossFallbackLabels[source];
    }

    private static boolean isSummitWhite(AxiomTextureProfile profile, Target target, int source, int x, int y, int label) {
        return profile.snowMask[source] && label == profile.labels[source] && target.supportsSummitWhite(x, y);
    }

    private static Terrain mappedTerrain(AxiomTextureProfile profile, Terrain[] mapped, int label) {
        return Objects.requireNonNull(mapped[label], "Missing exact terrain mapping for " + materialKey(profile.palette[label]));
    }

    /** White summit terrain and its snow mask are valid only at/above this elevation. */
    public static final float SUMMIT_WHITE_MINIMUM_HEIGHT = 150.0f;

    /** At this slope and steeper summit white is replaced by its measured undercoat/rock. */
    public static final float SUMMIT_WHITE_MAXIMUM_SLOPE = 30.0f;

    /** Shared mountain rule: high slopes are rock, independently of altitude. */
    public static TargetCellFilter steepRockFilter(float minimumSlopeDegrees) {
        if (! Float.isFinite(minimumSlopeDegrees) || minimumSlopeDegrees < 0.0f || minimumSlopeDegrees >= 90.0f) {
            throw new IllegalArgumentException("Invalid rock slope threshold");
        }
        return (x, y, height, slopeDegrees) -> slopeDegrees >= minimumSlopeDegrees;
    }

    /**
     * Builds the low-elevation, low-slope routing used for the measured rolling-plains source.
     * It is certain below {@code maximumHeight - heightTransition} and {@code fullSlopeDegrees},
     * fades deterministically through the transition bands, and is absent at the height or slope
     * limits. The low-frequency threshold makes the boundary into connected terrain regions
     * rather than a one-cell checkerboard.
     */
    public static TargetCellFilter rollingPlainsFilter(float maximumHeight, float heightTransition,
                                                       float fullSlopeDegrees, float rejectedSlopeDegrees,
                                                       long seed) {
        if (! Float.isFinite(maximumHeight) || heightTransition <= 0.0f || fullSlopeDegrees < 0.0f
                || rejectedSlopeDegrees <= fullSlopeDegrees) {
            throw new IllegalArgumentException("Invalid rolling-plains filter limits");
        }
        return (x, y, height, slopeDegrees) -> {
            if (height >= maximumHeight || slopeDegrees >= rejectedSlopeDegrees) {
                return false;
            }
            final float heightWeight = 1.0f - smoothStep((height - (maximumHeight - heightTransition)) / heightTransition);
            final float slopeWeight = 1.0f - smoothStep((slopeDegrees - fullSlopeDegrees)
                    / (rejectedSlopeDegrees - fullSlopeDegrees));
            final float coverage = Math.max(0.0f, Math.min(heightWeight, slopeWeight));
            return coverage >= 1.0f || (coverage > 0.0f && lowFrequencyNoise(seed, x, y, 24) < coverage);
        };
    }

    private static final class Target {
        Target(Dimension dimension) { this.dimension = dimension; Arrays.fill(keys, Long.MIN_VALUE); }
        Tile tile(int x, int y) {
            final int tx = x >> 7, ty = y >> 7, slot = (tx * 31 + ty) & 31;
            final long key = ((long) tx << 32) ^ (ty & 0xffffffffL);
            if (keys[slot] != key) { keys[slot] = key; tiles[slot] = dimension.getTile(tx, ty); }
            return tiles[slot];
        }
        boolean dry(int x, int y) {
            final Tile tile = tile(x, y);
            return dry(tile, x & 127, y & 127);
        }
        boolean accepts(int x, int y, TargetCellFilter filter) {
            final Tile tile = tile(x, y);
            final int tx = x & 127, ty = y & 127;
            if (! dry(tile, tx, ty)) return false;
            final float height = tile.getHeight(tx, ty);
            return filter.accept(x, y, height, slopeDegrees(x, y, height));
        }
        float height(int x, int y, float fallback) { final Tile t = tile(x, y); return t == null || t.getBitLayerValue(Void.INSTANCE, x & 127, y & 127)
                || t.getBitLayerValue(NotPresent.INSTANCE, x & 127, y & 127) || t.getBitLayerValue(NotPresentBlock.INSTANCE, x & 127, y & 127) ? fallback : t.getHeight(x & 127, y & 127); }
        boolean paint(int x, int y, Terrain terrain) {
            final Tile tile = tile(x, y);
            if (tile.getTerrain(x & 127, y & 127) == terrain) return false;
            dimension.getTileForEditing(x >> 7, y >> 7).setTerrain(x & 127, y & 127, terrain);
            return true;
        }
        boolean acceptsMoss(int x, int y, MossPolicy mossPolicy) {
            final float center = height(x, y, Float.NaN);
            if (! Float.isFinite(center)) return false;
            final float west = height(x - 4, y, center), east = height(x + 4, y, center);
            final float north = height(x, y - 1, center), south = height(x, y + 1, center);
            final float farNorth = height(x, y - 4, center), farSouth = height(x, y + 4, center);
            final float dx = (east - west) / 8.0f, dy = (farSouth - farNorth) / 8.0f;
            final float concavity = (height(x - 4, y, center) + height(x + 4, y, center)
                    + height(x, y - 4, center) + height(x, y + 4, center) - 4.0f * center) / 16.0f;
            return mossPolicy == MossPolicy.ROLLING_PLAINS
                    ? MossSuitability.acceptsRollingPlainsMoss(dx, dy, north - center, south - center, concavity)
                    : MossSuitability.acceptsSourceMoss(center, dx, dy, north - center, south - center, concavity);
        }
        boolean supportsSummitWhite(int x, int y) {
            final float center = height(x, y, Float.NaN);
            return Float.isFinite(center) && center >= SUMMIT_WHITE_MINIMUM_HEIGHT
                    && slopeDegrees(x, y, center) < SUMMIT_WHITE_MAXIMUM_SLOPE;
        }
        private boolean dry(Tile tile, int x, int y) {
            return tile != null && Float.isFinite(tile.getHeight(x, y))
                    && tile.getHeight(x, y) > tile.getWaterLevel(x, y) + 1.0f
                    && ! tile.getBitLayerValue(Void.INSTANCE, x, y) && ! tile.getBitLayerValue(NotPresent.INSTANCE, x, y)
                    && ! tile.getBitLayerValue(NotPresentBlock.INSTANCE, x, y);
        }
        private float slopeDegrees(int x, int y, float center) {
            final float dx = (height(x + 4, y, center) - height(x - 4, y, center)) / 8.0f;
            final float dy = (height(x, y + 4, center) - height(x, y - 4, center)) / 8.0f;
            return (float) Math.toDegrees(Math.atan(Math.sqrt(dx * dx + dy * dy)));
        }
        final Dimension dimension;
        final Tile[] tiles = new Tile[32];
        final long[] keys = new long[32];
    }

    private static long hash(long value) { value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L; value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL; return value ^ (value >>> 31); }
    private static float lowFrequencyNoise(long seed, int x, int y, int scale) {
        final int cellX = Math.floorDiv(x, scale), cellY = Math.floorDiv(y, scale);
        final float localX = Math.floorMod(x, scale) / (float) scale;
        final float localY = Math.floorMod(y, scale) / (float) scale;
        final float top = lerp(unitNoise(seed, cellX, cellY), unitNoise(seed, cellX + 1, cellY), smoothStep(localX));
        final float bottom = lerp(unitNoise(seed, cellX, cellY + 1), unitNoise(seed, cellX + 1, cellY + 1), smoothStep(localX));
        return lerp(top, bottom, smoothStep(localY));
    }
    private static float unitNoise(long seed, int x, int y) {
        return (hash(seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL) >>> 40) / (float) (1 << 24);
    }
    private static float smoothStep(float value) {
        value = Math.max(0.0f, Math.min(1.0f, value));
        return value * value * (3.0f - 2.0f * value);
    }
    private static float lerp(float first, float second, float t) { return first + (second - first) * t; }
    private record Bounds(int minX, int minY, int maxX, int maxY, float minHeight, float maxHeight, long eligible, long protectedCells) {
        int width() { return maxX - minX + 1; }
        int height() { return maxY - minY + 1; }
    }
    private static final int MAX_RIBBON_CELLS = 16 * 1024 * 1024, CANDIDATES = 40;
    private static final int[] SAMPLES = {0, 4, 8, 12, 15};
    private static final double[] WEIGHTS = {6.0, 2.0, 2.0, 1.0};

    @FunctionalInterface
    public interface SnowMaskSink {
        void accept(int x, int y, boolean sourceSnow) throws OperationCancelled;
    }

    /** Receives actual target geometry after dry-land protection has already been applied. */
    @FunctionalInterface
    public interface TargetCellFilter {
        boolean accept(int x, int y, float height, float slopeDegrees);
    }

    /** Mountain moss stays in its measured 80–120 band; plains moss follows the flat source itself. */
    public enum MossPolicy {
        MOUNTAIN_BAND,
        ROLLING_PLAINS
    }

    /** Summary for direct native-mix application, which has no donor patch or texture ribbon. */
    public record MixedTerrainSummary(long selectedCells, long changedCells, long protectedCells, long rejectedCells) {
        @Override
        public String toString() {
            return "native mixed terrain; selected=" + selectedCells + "; changed=" + changedCells
                    + "; protected=" + protectedCells + "; rejected=" + rejectedCells;
        }
    }

    /** Reference ratios are measurements, not a promise of identical ratios on different geometry. */
    public record Summary(long paintedCells, long changedCells, long protectedCells, long untexturedCells, long patches,
                          boolean identityReconstruction, float targetMinimumHeight, float targetMaximumHeight,
                          Map<String, Long> actualMaterialCounts, Map<String, Double> sourceMaterialRatios,
                          double globalRatioTotalVariation, double meanGeometryError, long ribbonBytes, long sourceSnowCells,
                          long mossRedirectedCells) { }
}
