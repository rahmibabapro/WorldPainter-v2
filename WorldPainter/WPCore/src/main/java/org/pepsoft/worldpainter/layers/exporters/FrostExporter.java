/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.pepsoft.worldpainter.layers.exporters;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.exporting.AbstractLayerExporter;
import org.pepsoft.worldpainter.exporting.Fixup;
import org.pepsoft.worldpainter.exporting.MinecraftWorld;
import org.pepsoft.worldpainter.exporting.SecondPassLayerExporter;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.SnowDepth;

import java.awt.*;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.pepsoft.minecraft.Constants.MC_SNOW;
import static org.pepsoft.minecraft.Constants.MC_WATER;
import static org.pepsoft.minecraft.Constants.MC_LAVA;
import static org.pepsoft.minecraft.Material.*;
import static org.pepsoft.worldpainter.exporting.SecondPassLayerExporter.Stage.ADD_FEATURES;

/**
 * @author pepijn
 */
public class FrostExporter extends AbstractLayerExporter<Frost> implements SecondPassLayerExporter {
    public FrostExporter(Dimension dimension, Platform platform, ExporterSettings settings) {
        super(dimension, platform, (settings != null) ? settings : new FrostSettings(), Frost.INSTANCE);
    }

    @Override
    public Set<Stage> getStages() {
        return singleton(ADD_FEATURES);
    }

    @Override
    public List<Fixup> addFeatures(final Rectangle area, final Rectangle exportedArea, final MinecraftWorld minecraftWorld) {
        final FrostSettings settings = (FrostSettings) super.settings;
        final boolean frostEverywhere = settings.isFrostEverywhere();
        final int mode = settings.getMode();
        final boolean snowUnderTrees = settings.isSnowUnderTrees();
        final Random random = new Random(); // Only used for random snow height, so it's not a big deal if it's different every time
        String customNoSnowOnIds = System.getProperty("org.pepsoft.worldpainter.noSnowOn");
        if ((customNoSnowOnIds != null) && (! customNoSnowOnIds.trim().isEmpty())) {
            throw new IllegalArgumentException("The org.pepsoft.worldpainter.noSnowOn property is no longer supported; please let the author know if you need it");
        }
        for (int x = area.x; x < area.x + area.width; x++) {
            for (int y = area.y; y < area.y + area.height; y++) {
                final boolean paintedFrost = dimension.getBitLayerValueAt(Frost.INSTANCE, x, y);
                if (frostEverywhere || paintedFrost) {
                    final int explicitDepth = paintedFrost ? dimension.getLayerValueAt(SnowDepth.INSTANCE, x, y) : 0;
                    if (explicitDepth > 0) {
                        placeExplicitSurfaceSnow(minecraftWorld, x, y, explicitDepth);
                        // A protected/unsupported surface must not fall through to legacy Frost,
                        // which scans tree canopies and freezes water.
                        continue;
                    }
                    int highestNonAirBlock = minecraftWorld.getHighestNonAirBlock(x, y);
                    Material previousMaterial = (highestNonAirBlock == maxZ) ? minecraftWorld.getMaterialAt(x, y, maxZ) : AIR;
                    int leafBlocksEncountered = 0;
                    for (int height = Math.min(highestNonAirBlock, maxZ - 1); height >= minHeight; height--) {
                        Material material = minecraftWorld.getMaterialAt(x, y, height);
                        if ((material.isNamed(MC_WATER) && (material.getProperty(LAYERS, 0) == 0))
                                || (material.containsWater() && material.insubstantial)) {
                            minecraftWorld.setMaterialAt(x, y, height, ICE);
                            // Remove insubstantial blocks above, assuming they are plants we cut in half
                            for (int dz = height + 1; dz <= highestNonAirBlock; dz++) {
                                if (minecraftWorld.getMaterialAt(x, y, dz).insubstantial) {
                                    minecraftWorld.setMaterialAt(x, y, dz, AIR);
                                } else {
                                    break;
                                }
                            }
                            break;
                        } else if (material.canSupportSnow) {
                            if ((material.leafBlock) || (material.sustainsLeaves)) {
                                if (previousMaterial.empty) {
                                    minecraftWorld.setMaterialAt(x, y, height + 1, SNOW);
                                }
                                leafBlocksEncountered++;
                                if ((! snowUnderTrees) && (leafBlocksEncountered > 1)) {
                                    break;
                                }
                            } else {
                                // Obliterate tall grass, 'cause there is too much of it, and leaving it in would look
                                // strange. Also replace existing snow, as we might want to place thicker snow
                                if (previousMaterial.empty || (previousMaterial == GRASS) || (previousMaterial == FERN) || (previousMaterial == SNOW)) {
                                    if ((mode == FrostSettings.MODE_SMOOTH_AT_ALL_ELEVATIONS)
                                            || (height == dimension.getIntHeightAt(x, y))) {
                                        // Only vary the snow thickness if we're at surface height, otherwise it looks
                                        // odd
                                        switch (mode) {
                                            case FrostSettings.MODE_FLAT:
                                                placeSnow(minecraftWorld, x, y, height, 1);
                                                break;
                                            case FrostSettings.MODE_RANDOM:
                                                placeSnow(minecraftWorld, x, y, height, random.nextInt(3) + 1);
                                                break;
                                            case FrostSettings.MODE_SMOOTH:
                                            case FrostSettings.MODE_SMOOTH_AT_ALL_ELEVATIONS:
                                                int layers = (int) Math.floor((dimension.getHeightAt(x, y) + 0.5f - dimension.getIntHeightAt(x, y)) / 0.125f) + 1;
                                                if ((layers > 1) && (! frostEverywhere)) {
                                                    layers = Math.max(Math.min(layers, dimension.getBitLayerCount(Frost.INSTANCE, x, y, 1) - 1), 1);
                                                }
                                                placeSnow(minecraftWorld, x, y, height, layers);
                                                break;
                                        }
                                    } else {
                                        // At other elevations just place a
                                        // regular thin snow block
                                        placeSnow(minecraftWorld, x, y, height, 1);
                                    }
                                }
                                break;
                            }
                        } else {
                            previousMaterial = material;
                            continue;
                        }
                        previousMaterial = material;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Export the requested snow block state directly above the actual terrain block. Unlike
     * ordinary Frost this mode never scans upwards to trees, changes the bed, freezes fluids,
     * or replaces the snow material with full snow/ice blocks.
     */
    private void placeExplicitSurfaceSnow(MinecraftWorld minecraftWorld, int x, int y, int layers) {
        if (layers < 1 || layers > 8) {
            return; // Nibble values 9-15 are reserved; do not turn invalid data into legacy Frost.
        }
        final int surfaceHeight = dimension.getIntHeightAt(x, y);
        final int lowerBound = Math.max(minHeight, minecraftWorld.getMinHeight());
        final int upperBound = Math.min(maxHeight, minecraftWorld.getMaxHeight());
        if (surfaceHeight < lowerBound || surfaceHeight >= upperBound - 1
                || dimension.getWaterLevelAt(x, y) >= surfaceHeight) {
            return;
        }
        final Material supportingBlock = minecraftWorld.getMaterialAt(x, y, surfaceHeight);
        if (supportingBlock == null || ! supportingBlock.canSupportSnow || supportingBlock.leafBlock
                || supportingBlock.containsWater() || supportingBlock.isNamed(MC_WATER)
                || supportingBlock.isNamed(MC_LAVA)) {
            return;
        }
        final Material existing = minecraftWorld.getMaterialAt(x, y, surfaceHeight + 1);
        if (existing == null || existing.containsWater() || existing.isNamed(MC_WATER) || existing.isNamed(MC_LAVA)) {
            return;
        }
        if (existing.empty || existing.isNamed(MC_SNOW) || existing == GRASS || existing == SHORT_GRASS || existing == FERN) {
            // Explicit depth is authoritative, including when reducing previously thicker snow.
            minecraftWorld.setMaterialAt(x, y, surfaceHeight + 1, SNOW.withProperty(LAYERS, layers));
        }
    }

    /**
     * Place a snow block with a specific thickness, but only if thicker snow is
     * not already present.
     */
    private void placeSnow(MinecraftWorld minecraftWorld, int x, int y, int height, int layers) {
        if ((layers < 1) || (layers > 8)) {
            throw new IllegalArgumentException("layers " + layers);
        }
        Material existingMaterial = minecraftWorld.getMaterialAt(x, y, height + 1);
        if (existingMaterial.isNamed(MC_SNOW)) {
            // If there is already snow there, don't lower it
            layers = Math.max(layers, existingMaterial.getProperty(LAYERS));
        }
        minecraftWorld.setMaterialAt(x, y, height + 1, SNOW.withProperty(LAYERS, layers));
    }

    public static class FrostSettings implements ExporterSettings {
        @Override
        public boolean isApplyEverywhere() {
            return frostEverywhere;
        }

        @Override
        public Frost getLayer() {
            return Frost.INSTANCE;
        }

        public boolean isFrostEverywhere() {
            return frostEverywhere;
        }

        public void setFrostEverywhere(boolean frostEverywhere) {
            this.frostEverywhere = frostEverywhere;
        }

        public int getMode() {
            return mode;
        }

        public void setMode(int mode) {
            this.mode = mode;
        }

        public boolean isSnowUnderTrees() {
            return snowUnderTrees;
        }

        public void setSnowUnderTrees(boolean snowUnderTrees) {
            this.snowUnderTrees = snowUnderTrees;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final FrostSettings other = (FrostSettings) obj;
            if (this.frostEverywhere != other.frostEverywhere) {
                return false;
            }
            if (this.mode != other.mode) {
                return false;
            }
            if (this.snowUnderTrees != other.snowUnderTrees) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 23 * hash + (this.frostEverywhere ? 1 : 0);
            hash = 23 * hash + mode;
            hash = 23 * hash + (this.snowUnderTrees ? 1 : 0);
            return hash;
        }

        @Override
        public FrostSettings clone() {
            try {
                return (FrostSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        private boolean frostEverywhere;
        private int mode = MODE_SMOOTH;
        private boolean snowUnderTrees = true;

        public static final int MODE_FLAT = 0; // Always place thin snow blocks
        public static final int MODE_RANDOM = 1; // Place random height snow blocks on the surface
        public static final int MODE_SMOOTH = 2; // Place smooth snow blocks on the surface
        public static final int MODE_SMOOTH_AT_ALL_ELEVATIONS = 3; // Place smooth snow blocks at any elevation

        private static final long serialVersionUID = 2011060801L;
    }
}
