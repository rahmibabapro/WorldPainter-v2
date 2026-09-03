package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.minecraft.Material;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.layers.bo2.AxiomBlueprint;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * An immutable, measured structural top-surface exemplar. This deliberately does not pretend
 * that a WorldPainter height field can encode a blueprint's caves, overhangs or decorations.
 * Every retained material includes its original block states; solid wood is terrain texture.
 */
public final class AxiomTextureProfile {
    static final int PATCH = 16, OVERLAP = 4, STRIDE = PATCH - OVERLAP;
    static final int HEIGHT_BINS = 8, SLOPE_BINS = 4, ASPECT_BINS = 5;

    public static AxiomTextureProfile load(File file, ProgressReceiver progress) throws IOException, OperationCancelled {
        check(progress, 0);
        return analyze(AxiomBlueprint.load(file), progress);
    }

    public static AxiomTextureProfile analyse(WPObject object, ProgressReceiver progress) throws OperationCancelled {
        return analyze(object, progress);
    }

    public static AxiomTextureProfile analyze(WPObject object, ProgressReceiver progress) throws OperationCancelled {
        Objects.requireNonNull(object, "object");
        final Point3i d = object.getDimensions();
        if (d.x <= 0 || d.y <= 0 || d.z <= 0 || (long) d.x * d.y > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid blueprint dimensions: " + d);
        }
        final int size = d.x * d.y;
        final int[] heights = new int[size];
        final Material[] surface = new Material[size], warm = new Material[size];
        final boolean[] snowMask = new boolean[size];
        final Map<String, Long> excluded = new TreeMap<>();
        Arrays.fill(heights, -1);
        int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
        for (int y = 0; y < d.y; y++) {
            for (int x = 0; x < d.x; x++) {
                final int i = x + y * d.x;
                boolean snowLayerAbove = false;
                for (int z = d.z - 1; z >= 0; z--) {
                    if (! object.getMask(x, y, z)) continue;
                    final Material m = object.getMaterial(x, y, z);
                    if (m == null || m.empty) continue; // Mask may also contain explicit air.
                    if (! isStructural(m)) {
                        if (surface[i] == null) {
                            excluded.merge(materialKey(m), 1L, Long::sum);
                            if (m.name.equals("minecraft:snow")) snowLayerAbove = true;
                            // Snow on a tree canopy or a plant is decoration, not ground snow.
                            else if (m.name.endsWith("_leaves") || m.vegetation || m.name.startsWith("minecraft:potted_")) snowLayerAbove = false;
                        }
                        continue;
                    }
                    if (surface[i] == null) {
                        surface[i] = m;
                        snowMask[i] = snowLayerAbove || isSnowMaskMaterial(m);
                        heights[i] = z;
                        minimum = Math.min(minimum, z);
                        maximum = Math.max(maximum, z);
                    }
                    if (! isSnowMaskMaterial(m)) {
                        warm[i] = m;
                        break;
                    }
                }
            }
            check(progress, 0.7f * (y + 1) / d.y);
        }
        if (minimum == Integer.MAX_VALUE) throw new IllegalArgumentException("Blueprint has no structural top surface");
        // The user's summit whites remain measured terrain and separately become a snow-layer
        // mask. The non-white undercoat is retained only as diagnostic/source context. A white
        // column with no rock beneath it uses the closest observed non-white block for that
        // context; it is never used to replace the measured white surface.
        final int[] nearest = new int[size], queue = new int[size];
        Arrays.fill(nearest, -1);
        int head = 0, tail = 0;
        for (int i = 0; i < size; i++) if (warm[i] != null) {
            nearest[i] = i;
            queue[tail++] = i;
        }
        if (tail > 0) {
            while (head < tail) {
                final int i = queue[head++], x = i % d.x, y = i / d.x;
                if (x > 0) tail = flood(i, i - 1, nearest, queue, tail);
                if (x + 1 < d.x) tail = flood(i, i + 1, nearest, queue, tail);
                if (y > 0) tail = flood(i, i - d.x, nearest, queue, tail);
                if (y + 1 < d.y) tail = flood(i, i + d.x, nearest, queue, tail);
            }
            for (int i = 0; i < size; i++) if (surface[i] != null && warm[i] == null && nearest[i] >= 0) warm[i] = warm[nearest[i]];
        }
        check(progress, 0.8f);
        final AxiomTextureProfile profile = new AxiomTextureProfile(d, heights, surface, warm, snowMask, minimum, maximum, excluded);
        check(progress, 1);
        return profile;
    }

    private AxiomTextureProfile(Point3i d, int[] heights, Material[] surface, Material[] warm, boolean[] snowMask,
                                int minimum, int maximum, Map<String, Long> excluded) {
        width = d.x;
        length = d.y;
        this.heights = heights;
        this.snowMask = snowMask;
        minHeight = minimum;
        maxHeight = maximum;
        final TreeMap<String, Material> distinct = new TreeMap<>();
        final TreeMap<String, Long> counts = new TreeMap<>();
        final TreeMap<String, Long> undercoatCounts = new TreeMap<>();
        long columns = 0, snowColumns = 0;
        for (int i = 0; i < surface.length; i++) if (surface[i] != null) {
            distinct.put(materialKey(surface[i]), surface[i]);
            counts.merge(materialKey(surface[i]), 1L, Long::sum);
            if (warm[i] != null) {
                distinct.put(materialKey(warm[i]), warm[i]);
                undercoatCounts.merge(materialKey(warm[i]), 1L, Long::sum);
            }
            columns++;
            if (snowMask[i]) snowColumns++;
        }
        palette = distinct.values().toArray(new Material[0]);
        final Map<Material, Integer> ids = new HashMap<>();
        for (int i = 0; i < palette.length; i++) {
            ids.put(palette[i], i);
        }
        labels = new int[surface.length];
        warmLabels = new int[surface.length];
        features = new float[surface.length * 4];
        Arrays.fill(labels, -1);
        Arrays.fill(warmLabels, -1);
        for (int y = 0; y < length; y++) for (int x = 0; x < width; x++) {
            final int i = x + y * width;
            if (surface[i] == null) continue;
            labels[i] = ids.get(surface[i]);
            if (warm[i] != null) warmLabels[i] = ids.get(warm[i]);
            features[4 * i] = normalise(heights[i], minimum, maximum);
            final float dx = (height(x + 2, y, heights[i]) - height(x - 2, y, heights[i])) / 4.0f;
            final float dy = (height(x, y + 2, heights[i]) - height(x, y - 2, heights[i])) / 4.0f;
            features[4 * i + 1] = gradient(dx);
            features[4 * i + 2] = gradient(dy);
            features[4 * i + 3] = curvature(heights[i], height(x - 4, y, heights[i]), height(x + 4, y, heights[i]),
                    height(x, y - 4, heights[i]), height(x, y + 4, heights[i]));
        }
        mossFallbackLabels = nearestNonMossLabels(labels, palette, width, length);
        final List<List<Integer>> buckets = new ArrayList<>();
        for (int i = 0; i < HEIGHT_BINS * SLOPE_BINS * ASPECT_BINS; i++) buckets.add(new ArrayList<>());
        final ArrayList<Integer> candidates = new ArrayList<>();
        if (width >= PATCH && length >= PATCH) {
            // All possible origins are retained, making an existing neighbourhood an admissible
            // donor without snapping its texture to a two- or sixteen-block source grid.
            final int[] missing = new int[(width + 1) * (length + 1)];
            for (int y = 0; y < length; y++) for (int x = 0; x < width; x++) {
                final int a = x + 1 + (y + 1) * (width + 1);
                missing[a] = (labels[x + y * width] < 0 ? 1 : 0) + missing[a - 1] + missing[a - width - 1] - missing[a - width - 2];
            }
            for (int y = 0; y <= length - PATCH; y++) for (int x = 0; x <= width - PATCH; x++) {
                final int a = x + y * (width + 1), b = a + PATCH, c = a + PATCH * (width + 1), e = c + PATCH;
                if (missing[e] - missing[b] - missing[c] + missing[a] != 0) continue;
                final int origin = x + y * width, centre = origin + PATCH / 2 + (PATCH / 2) * width;
                buckets.get(bucket(features[4 * centre], features[4 * centre + 1], features[4 * centre + 2])).add(origin);
                candidates.add(origin);
            }
        }
        origins = candidates.stream().mapToInt(Integer::intValue).toArray();
        candidateBuckets = buckets.stream().map(v -> v.stream().mapToInt(Integer::intValue).toArray()).toArray(int[][]::new);
        long sameX = 0, pairsX = 0, sameY = 0, pairsY = 0;
        final Set<String> names = new HashSet<>();
        for (int y = 0; y < length; y++) for (int x = 0; x < width; x++) {
            final int i = x + y * width;
            if (labels[i] < 0) continue;
            names.add(palette[labels[i]].name);
            if (x > 0 && labels[i - 1] >= 0) { pairsX++; if (labels[i] == labels[i - 1]) sameX++; }
            if (y > 0 && labels[i - width] >= 0) { pairsY++; if (labels[i] == labels[i - width]) sameY++; }
        }
        summary = new Summary(width, length, d.z, columns, minimum, maximum, names.size(), counts.size(),
                Collections.unmodifiableMap(counts), Collections.unmodifiableMap(new TreeMap<>(excluded)),
                pairsX == 0 ? 0 : (double) sameX / pairsX, pairsY == 0 ? 0 : (double) sameY / pairsY, origins.length, snowColumns,
                Collections.unmodifiableMap(undercoatCounts));
    }

    public int getWidth() { return width; }
    public int getLength() { return length; }
    public int getSurfaceHeight(int x, int y) { return heights[index(x, y)]; }
    public Material getSurfaceMaterial(int x, int y) { final int id = labels[index(x, y)]; return id < 0 ? null : palette[id]; }
    public Material getUndercoatMaterial(int x, int y) { final int id = warmLabels[index(x, y)]; return id < 0 ? null : palette[id]; }
    public boolean isSnowMask(int x, int y) { return snowMask[index(x, y)]; }
    /** Every measured structural surface state is written as terrain, including the source whites. */
    public Set<Material> getMaterials() {
        final Set<Material> result = new LinkedHashSet<>();
        for (int id : labels) if (id >= 0) result.add(palette[id]);
        return Collections.unmodifiableSet(result);
    }

    /**
     * Surface materials plus the real non-white undercoats needed when a summit-white source cell
     * is outside the allowed elevation or slope range. These are allocated before any painting so
     * a white-to-rock/soil fallback never leaves an unresolved custom terrain reference.
     */
    public Set<Material> getRequiredMaterials() {
        final Set<Material> result = new LinkedHashSet<>(getMaterials());
        for (int id : warmLabels) if (id >= 0) result.add(palette[id]);
        return Collections.unmodifiableSet(result);
    }
    public Summary getSummary() { return summary; }

    /** Canonical block-state key, independent of a map's iteration order. */
    public static String materialKey(Material m) {
        return m.name + (m.getProperties() == null || m.getProperties().isEmpty() ? "" : new TreeMap<>(m.getProperties()).toString());
    }

    public static boolean isStructural(Material m) {
        if (m == null || m.empty) return false;
        // WorldPainter classifies even full snow blocks as "insubstantial" for editing. They
        // must still be a measured surface here, otherwise the original white mask disappears.
        if (isSnowOrIce(m)) return true;
        if (! m.solid || m.vegetation || m.tileEntity || m.name.endsWith("_leaves")) return false;
        final String n = m.name;
        return ! (n.equals("minecraft:diamond_block")
                || n.startsWith("minecraft:potted_") || n.endsWith("_sapling") || n.equals("minecraft:flower_pot")
                || n.endsWith("_stairs") || n.endsWith("_slab") || n.endsWith("_fence") || n.endsWith("_fence_gate")
                || n.endsWith("_wall") || n.endsWith("_door") || n.endsWith("_trapdoor") || n.endsWith("_banner")
                || n.endsWith("_carpet") || n.endsWith("_button") || n.endsWith("_pressure_plate") || n.endsWith("_pane")
                || n.equals("minecraft:pointed_dripstone") || n.equals("minecraft:snow") || n.equals("minecraft:iron_bars")
                || n.equals("minecraft:chain") || n.equals("minecraft:ladder"));
    }

    public static boolean isSnowOrIce(Material m) {
        return m.name.equals("minecraft:snow_block") || m.name.equals("minecraft:ice") || m.name.endsWith("_ice") || m.name.equals("minecraft:powder_snow");
    }

    /** Explicit source palette interpretation requested by the user; no RGB or broad name guessing. */
    public static boolean isSnowMaskMaterial(Material m) {
        if (isSnowOrIce(m)) return true;
        return switch (m.name) {
            case "minecraft:birch_wood", "minecraft:diorite", "minecraft:white_concrete_powder",
                    "minecraft:white_concrete", "minecraft:white_wool", "minecraft:calcite",
                    "minecraft:quartz_block",
                    "minecraft:smooth_quartz" -> true;
            default -> false;
        };
    }

    /** Moss texture must be constrained by the target terrain rather than copied onto cliffs. */
    public static boolean isMossMaterial(Material material) {
        return material != null && (material.name.equals("minecraft:moss_block") || material.name.equals("minecraft:pale_moss_block"));
    }

    static float normalise(float h, float min, float max) { return max > min ? Math.max(0, Math.min(1, (h - min) / (max - min))) : 0.5f; }
    static float gradient(float value) { return (float) (Math.atan(value) / (Math.PI / 2)); }
    static float curvature(float h, float west, float east, float north, float south) { return gradient((west + east + north + south - 4 * h) / 16.0f); }
    static int bucket(float h, float gx, float gy) {
        final int hb = Math.min(HEIGHT_BINS - 1, Math.max(0, (int) (h * HEIGHT_BINS)));
        final float slope = Math.max(Math.abs(gx), Math.abs(gy));
        final int sb = slope < 0.11f ? 0 : slope < 0.28f ? 1 : slope < 0.50f ? 2 : 3;
        // Gradient points uphill; outward-facing normal points in the opposite direction.
        final int aspect = slope < 0.11f ? 0 : Math.abs(gx) >= Math.abs(gy) ? (gx >= 0 ? 1 : 2) : (gy >= 0 ? 3 : 4);
        return (hb * SLOPE_BINS + sb) * ASPECT_BINS + aspect;
    }

    private int index(int x, int y) { if (x < 0 || y < 0 || x >= width || y >= length) throw new IndexOutOfBoundsException(); return x + y * width; }
    private int height(int x, int y, int fallback) { return x < 0 || y < 0 || x >= width || y >= length || heights[x + y * width] < 0 ? fallback : heights[x + y * width]; }
    private static int flood(int from, int to, int[] nearest, int[] queue, int tail) { if (nearest[to] < 0) { nearest[to] = nearest[from]; queue[tail++] = to; } return tail; }
    private static int[] nearestNonMossLabels(int[] labels, Material[] palette, int width, int length) {
        final int[] nearest = new int[labels.length], queue = new int[labels.length], fallback = new int[labels.length];
        Arrays.fill(nearest, -1);
        Arrays.fill(fallback, -1);
        int head = 0, tail = 0;
        for (int i = 0; i < labels.length; i++) {
            final int label = labels[i];
            // Never turn rejected moss into a snow-coloured terrain; use a nearby measured rock,
            // soil or grass state instead.
            if (label >= 0 && ! isMossMaterial(palette[label]) && ! isSnowMaskMaterial(palette[label])) {
                nearest[i] = i;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            final int i = queue[head++], x = i % width, y = i / width;
            if (x > 0) tail = flood(i, i - 1, nearest, queue, tail);
            if (x + 1 < width) tail = flood(i, i + 1, nearest, queue, tail);
            if (y > 0) tail = flood(i, i - width, nearest, queue, tail);
            if (y + 1 < length) tail = flood(i, i + width, nearest, queue, tail);
        }
        for (int i = 0; i < labels.length; i++) if (labels[i] >= 0 && nearest[i] >= 0) fallback[i] = labels[nearest[i]];
        return fallback;
    }
    static void check(ProgressReceiver progress, float fraction) throws OperationCancelled { if (progress != null) { progress.setProgress(fraction); progress.checkForCancellation(); } }

    final int width, length, minHeight, maxHeight;
    final int[] heights, labels, warmLabels, mossFallbackLabels, origins;
    final int[][] candidateBuckets;
    final float[] features;
    final Material[] palette;
    final boolean[] snowMask;
    private final Summary summary;

    public record Summary(int width, int length, int verticalHeight, long structuralColumns, int minimumSurfaceHeight,
                          int maximumSurfaceHeight, int uniqueNames, int uniqueStates, Map<String, Long> materialCounts,
                          Map<String, Long> excludedOverlayCounts, double adjacentSameStateX, double adjacentSameStateY,
                          int admissiblePatches, long sourceSnowMaskColumns, Map<String, Long> undercoatMaterialCounts) { }
}
