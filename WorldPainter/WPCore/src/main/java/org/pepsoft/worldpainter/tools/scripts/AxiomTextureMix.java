package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.MixedMaterial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Turns a measured blueprint surface into one normal WorldPainter Blob mixed material.
 *
 * <p>The source counts are deliberately used as the rows' occurrence values. This gives the
 * user-visible WorldPainter mixer the same input proportions as the blueprint, while its seeded
 * blob generator creates new, naturally scattered patches instead of copying donor stripes.
 */
public final class AxiomTextureMix {
    private AxiomTextureMix() {
    }

    /** Slightly broader than the stock tiny blobs, without turning the plains into large bands. */
    public static final float DEFAULT_BLOB_SCALE = 1.25f;

    /** Builds the native mixed material from every measured structural top-surface state. */
    public static Result create(String name, AxiomTextureProfile profile) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(profile, "profile");
        final List<Entry> entries = sourceEntries(profile);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Blueprint has no structural materials for a native mix");
        }
        final MixedMaterial material;
        if (entries.size() == 1) {
            final Entry entry = entries.get(0);
            material = new MixedMaterial(name, new MixedMaterial.Row(entry.material, 1, 1.0f), -1, null);
        } else {
            final MixedMaterial.Row[] rows = new MixedMaterial.Row[entries.size()];
            for (int index = 0; index < rows.length; index++) {
                final Entry entry = entries.get(index);
                rows[index] = new MixedMaterial.Row(entry.material, Math.toIntExact(entry.count), 1.0f);
            }
            material = new MixedMaterial(name, rows, -1, null, DEFAULT_BLOB_SCALE);
        }
        final Map<String, Long> counts = new LinkedHashMap<>();
        final Map<String, Double> ratios = new LinkedHashMap<>();
        final long total = entries.stream().mapToLong(Entry::count).sum();
        for (Entry entry : entries) {
            counts.put(entry.key, entry.count);
            ratios.put(entry.key, entry.count / (double) total);
        }
        return new Result(material, Collections.unmodifiableMap(counts), Collections.unmodifiableMap(ratios));
    }

    /**
     * Flat plains deliberately use plain grass only. This is kept here rather than selecting a
     * visual colour so the exact grass-block state measured from terrain.bp is retained.
     */
    public static Result createGrassOnly(String name, AxiomTextureProfile profile) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(profile, "profile");
        final Material grass = profile.getMaterials().stream()
                .filter(material -> material.name.equals("minecraft:grass_block"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("terrain.bp has no grass_block for the flat-plains terrain"));
        final MixedMaterial material = new MixedMaterial(name, new MixedMaterial.Row(grass, 1, 1.0f), -1, null);
        final String key = AxiomTextureProfile.materialKey(grass);
        return new Result(material, Map.of(key, 1L), Map.of(key, 1.0));
    }

    private static List<Entry> sourceEntries(AxiomTextureProfile profile) {
        final Map<String, Long> counts = profile.getSummary().materialCounts();
        final Map<String, Material> materials = new TreeMap<>();
        for (Material material : profile.getMaterials()) {
            materials.put(AxiomTextureProfile.materialKey(material), material);
        }
        final List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Material> material : materials.entrySet()) {
            final long count = counts.getOrDefault(material.getKey(), 0L);
            if (count > 0) {
                entries.add(new Entry(material.getKey(), material.getValue(), count));
            }
        }
        entries.sort(Comparator.comparing(Entry::key));
        return entries;
    }

    public record Result(MixedMaterial material, Map<String, Long> sourceCounts, Map<String, Double> sourceRatios) {
        @Override
        public String toString() {
            return material.getMode() == MixedMaterial.Mode.BLOBS
                    ? "native WorldPainter blobs; source states=" + sourceCounts.size()
                    : "native WorldPainter grass terrain";
        }
    }

    private record Entry(String key, Material material, long count) {
    }
}
