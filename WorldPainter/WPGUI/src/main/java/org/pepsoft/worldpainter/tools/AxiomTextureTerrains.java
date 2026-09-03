package org.pepsoft.worldpainter.tools;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.MixedMaterial;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.World2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Preflights and installs exact-state terrains and optional native mixed terrain as one transaction.
 * The active editor keeps its authoritative palette in {@link Terrain}, while {@link World2}
 * may retain older definitions until save. Neither occupied palette is treated as free space.
 *
 * <p>Headless callers must still install the static palette before rendering/exporting. With
 * {@code activeWorld=false}, conflicting existing static entries are never overwritten; a
 * compatible or entirely free slot is used instead. Roll back before disposing of a temporary
 * headless world to restore the original static palette.
 */
public final class AxiomTextureTerrains {
    private AxiomTextureTerrains(World2 world, Map<Material, Terrain> terrains,
                                 Terrain mixedTerrain, Map<Integer, Slot> slots, Map<Integer, MixedMaterial> created) {
        this.world = world;
        this.terrains = Collections.unmodifiableMap(new LinkedHashMap<>(terrains));
        this.mixedTerrain = mixedTerrain;
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        createdMaterials = Collections.unmodifiableMap(new LinkedHashMap<>(created));
    }

    /** Inspect both palettes and reserve every required slot without changing either palette. */
    public static AxiomTextureTerrains prepare(World2 world, Collection<Material> materials, boolean activeWorld) {
        return prepare(world, materials, null, activeWorld);
    }

    /**
     * As above, also reserving one complete WorldPainter mixed material in the same transaction.
     * The mixture is never installed separately, so cancellation and palette conflicts remain
     * atomic with all exact mountain terrain definitions.
     */
    public static AxiomTextureTerrains prepare(World2 world, Collection<Material> materials,
                                               MixedMaterial mixedMaterial, boolean activeWorld) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(materials, "materials");
        final List<Material> ordered = materials.stream()
                .map(material -> Objects.requireNonNull(material, "material"))
                .distinct().sorted(Comparator.comparing(AxiomTextureTerrains::materialId)).toList();
        synchronized (Terrain.class) {
            final MixedMaterial[] saved = new MixedMaterial[Terrain.CUSTOM_TERRAIN_COUNT];
            final MixedMaterial[] live = new MixedMaterial[Terrain.CUSTOM_TERRAIN_COUNT];
            for (int index = 0; index < saved.length; index++) {
                saved[index] = world.getMixedMaterial(index);
                live[index] = Terrain.getCustomMaterial(index);
            }
            final Map<Material, Terrain> result = new LinkedHashMap<>();
            final Map<Integer, Slot> slots = new LinkedHashMap<>();
            final Map<Integer, MixedMaterial> created = new LinkedHashMap<>();
            for (Material material : ordered) {
                result.put(material, reserve(saved, live, slots, created, new ExactDefinition(material), activeWorld));
            }
            final Terrain mixedTerrain;
            if (mixedMaterial != null) {
                mixedTerrain = reserve(saved, live, slots, created, new MixedDefinition(mixedMaterial), activeWorld);
            } else {
                mixedTerrain = null;
            }
            return new AxiomTextureTerrains(world, result, mixedTerrain, slots, created);
        }
    }

    public Map<Material, Terrain> getTerrains() {
        return terrains;
    }

    /** The optional native mixed terrain requested during preparation, or {@code null}. */
    public Terrain getMixedTerrain() {
        return mixedTerrain;
    }

    /** Newly created definitions only; callers can add editor palette buttons after success. */
    public Map<Integer, MixedMaterial> getCreatedMaterials() {
        return createdMaterials;
    }

    /** Recheck the reservation, then install all definitions, restoring originals on failure. */
    public void install() {
        synchronized (Terrain.class) {
            if (installed) {
                return;
            }
            if (rolledBack) {
                throw new IllegalStateException("This terrain transaction was already rolled back");
            }
            // Check every reservation before the first mutation, including mutable definitions.
            for (Map.Entry<Integer, Slot> entry : slots.entrySet()) {
                final int index = entry.getKey();
                final Slot slot = entry.getValue();
                if (world.getMixedMaterial(index) != slot.savedBefore
                        || Terrain.getCustomMaterial(index) != slot.liveBefore
                        || ! slot.definition.matches(slot.savedAfter)
                        || ! slot.definition.matches(slot.liveAfter)) {
                    throw new IllegalStateException("Custom Terrain palette changed during Axiom texture preparation; retry the operation");
                }
            }
            try {
                for (Map.Entry<Integer, Slot> entry : slots.entrySet()) {
                    final int index = entry.getKey();
                    final Slot slot = entry.getValue();
                    // Record first: a property-change listener can throw after World2 mutates.
                    touched.add(index);
                    setSavedReference(index, slot.savedAfter);
                    Terrain.setCustomMaterial(index, slot.liveAfter);
                }
                installed = true;
            } catch (RuntimeException | Error failure) {
                try {
                    restore();
                } catch (RuntimeException | Error restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
                rolledBack = true;
                throw failure;
            }
        }
    }

    /** Restore exactly the saved and live references present before installation. Idempotent. */
    public void rollback() {
        synchronized (Terrain.class) {
            if (rolledBack) {
                return;
            }
            restore();
            installed = false;
            rolledBack = true;
        }
    }

    private void restore() {
        Throwable firstFailure = null;
        for (int offset = touched.size() - 1; offset >= 0; offset--) {
            final int index = touched.get(offset);
            final Slot slot = slots.get(index);
            Terrain.setCustomMaterial(index, slot.liveBefore);
            try {
                setSavedReference(index, slot.savedBefore);
            } catch (RuntimeException | Error failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        touched.clear();
        if (firstFailure instanceof RuntimeException exception) {
            throw exception;
        } else if (firstFailure instanceof Error error) {
            throw error;
        }
    }

    private void setSavedReference(int index, MixedMaterial material) {
        final MixedMaterial current = world.getMixedMaterial(index);
        if (current != material) {
            // World2 compares UUID equality. A stale clone with the same UUID must still be
            // replaced, and rollback must restore the original object, not merely its UUID.
            if (current != null && material != null && current.equals(material)) {
                world.setMixedMaterial(index, null);
            }
            world.setMixedMaterial(index, material);
        }
    }

    private static Terrain reserve(MixedMaterial[] saved, MixedMaterial[] live, Map<Integer, Slot> slots,
                                   Map<Integer, MixedMaterial> created, Definition definition, boolean activeWorld) {
        int index = findReusable(saved, live, slots, definition, activeWorld);
        final MixedMaterial desiredSaved;
        final MixedMaterial desiredLive;
        if (index >= 0) {
            if (activeWorld) {
                desiredSaved = desiredLive = live[index];
            } else {
                // A compatible live definition may belong to another world. Keep its identity and
                // metadata instead of silently substituting this world's saved definition.
                desiredSaved = (saved[index] != null) ? saved[index] : live[index];
                desiredLive = (live[index] != null) ? live[index] : saved[index];
            }
        } else {
            index = findFree(saved, live, slots);
            if (index < 0) {
                throw new IllegalStateException("Axiom texture needs more free Custom Terrain slots; "
                        + "no palette was changed. Could not allocate " + definition.description());
            }
            desiredSaved = desiredLive = definition.create();
            created.put(index, desiredSaved);
        }
        slots.put(index, new Slot(saved[index], live[index], desiredSaved, desiredLive, definition));
        return Terrain.getCustomTerrain(index);
    }

    private static int findReusable(MixedMaterial[] saved, MixedMaterial[] live, Map<Integer, Slot> reservations,
                                    Definition definition, boolean activeWorld) {
        for (int index = 0; index < saved.length; index++) {
            if (reservations.containsKey(index)) {
                continue;
            }
            if (activeWorld) {
                if (definition.matches(live[index])) {
                    return index;
                }
            } else if ((saved[index] != null || live[index] != null)
                    && (saved[index] == null || definition.matches(saved[index]))
                    && (live[index] == null || definition.matches(live[index]))) {
                return index;
            }
        }
        return -1;
    }

    private static int findFree(MixedMaterial[] saved, MixedMaterial[] live, Map<Integer, Slot> reservations) {
        for (int index = 0; index < saved.length; index++) {
            if (saved[index] == null && live[index] == null && ! reservations.containsKey(index)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isExact(MixedMaterial mixed, Material material) {
        return mixed != null && mixed.getMode() == MixedMaterial.Mode.SIMPLE
                && material.equals(mixed.getSingleMaterial());
    }

    /** Compares output-relevant settings, not user-controlled names or UUID identities. */
    private static boolean isEquivalent(MixedMaterial first, MixedMaterial second) {
        if (first == null || second == null || first.getMode() != second.getMode()
                || first.getBiome() != second.getBiome() || Float.compare(first.getScale(), second.getScale()) != 0
                || ! Objects.equals(first.getColour(), second.getColour())
                || ! Objects.equals(first.getVariation(), second.getVariation())
                || first.isRepeat() != second.isRepeat()
                || Double.compare(first.getLayerXSlope(), second.getLayerXSlope()) != 0
                || Double.compare(first.getLayerYSlope(), second.getLayerYSlope()) != 0) {
            return false;
        }
        final MixedMaterial.Row[] firstRows = first.getRows(), secondRows = second.getRows();
        if (firstRows.length != secondRows.length) {
            return false;
        }
        for (int index = 0; index < firstRows.length; index++) {
            final MixedMaterial.Row firstRow = firstRows[index], secondRow = secondRows[index];
            if (! Objects.equals(firstRow.material, secondRow.material) || firstRow.occurrence != secondRow.occurrence
                    || Float.compare(firstRow.scale, secondRow.scale) != 0) {
                return false;
            }
        }
        return true;
    }

    private static String materialId(Material material) {
        final Map<String, String> properties = material.getProperties();
        return material.name + ((properties == null || properties.isEmpty()) ? "" : new TreeMap<>(properties).toString());
    }

    private interface Definition {
        boolean matches(MixedMaterial material);
        MixedMaterial create();
        String description();
    }

    private record ExactDefinition(Material material) implements Definition {
        @Override
        public boolean matches(MixedMaterial candidate) {
            return isExact(candidate, material);
        }

        @Override
        public MixedMaterial create() {
            return new MixedMaterial("Axiom dag: " + materialId(material),
                    new MixedMaterial.Row(material, 1, 1.0f), -1, null);
        }

        @Override
        public String description() {
            return materialId(material);
        }
    }

    private record MixedDefinition(MixedMaterial material) implements Definition {
        MixedDefinition {
            Objects.requireNonNull(material, "mixedMaterial");
        }

        @Override
        public boolean matches(MixedMaterial candidate) {
            return isEquivalent(candidate, material);
        }

        @Override
        public MixedMaterial create() {
            return material;
        }

        @Override
        public String description() {
            return material.getName();
        }
    }

    private final World2 world;
    private final Map<Material, Terrain> terrains;
    private final Terrain mixedTerrain;
    private final Map<Integer, Slot> slots;
    private final Map<Integer, MixedMaterial> createdMaterials;
    private final List<Integer> touched = new ArrayList<>();
    private boolean installed;
    private boolean rolledBack;

    private record Slot(MixedMaterial savedBefore, MixedMaterial liveBefore,
                        MixedMaterial savedAfter, MixedMaterial liveAfter, Definition definition) {
    }
}
