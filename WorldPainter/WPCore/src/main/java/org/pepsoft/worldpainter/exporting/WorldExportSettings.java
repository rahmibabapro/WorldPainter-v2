package org.pepsoft.worldpainter.exporting;

import java.awt.*;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;

public class WorldExportSettings implements Serializable {
    public WorldExportSettings() {
        // Do nothing
    }

    public WorldExportSettings(Set<Integer> dimensionsToExport, Set<Point> tilesToExport, Set<Step> stepsToSkip) {
        this.dimensionsToExport = dimensionsToExport;
        this.tilesToExport = tilesToExport;
        this.stepsToSkip = stepsToSkip;
    }

    public boolean isExportEverything() {
        return ((dimensionsToExport == null) || dimensionsToExport.isEmpty())
                && ((tilesToExport == null) || tilesToExport.isEmpty())
                && ((stepsToSkip == null) || stepsToSkip.isEmpty());
    }

    public Set<Integer> getDimensionsToExport() {
        return dimensionsToExport;
    }

    public void setDimensionsToExport(Set<Integer> dimensionsToExport) {
        this.dimensionsToExport = dimensionsToExport;
    }

    public Set<Point> getTilesToExport() {
        return tilesToExport;
    }

    public void setTilesToExport(Set<Point> tilesToExport) {
        this.tilesToExport = tilesToExport;
    }

    public Set<Step> getStepsToSkip() {
        return stepsToSkip;
    }

    public void setStepsToSkip(Set<Step> stepsToSkip) {
        this.stepsToSkip = stepsToSkip;
    }

    public boolean isHollowInterior() {
        return hollowInterior;
    }

    public void setHollowInterior(boolean hollowInterior) {
        this.hollowInterior = hollowInterior;
    }

    /** Shell thickness preserved before clearing interiors ({@code //hollow <n>}); default 2. */
    public int getHollowThickness() {
        return hollowThickness;
    }

    public void setHollowThickness(int hollowThickness) {
        this.hollowThickness = Math.max(0, hollowThickness);
    }

    /**
     * The dimension(s) to export. If this is {@code null} then <em>all</em> dimensions must be exported.
     */
    private Set<Integer> dimensionsToExport;

    /**
     * The tiles to export. This may only be set if {@code dimensionsToExport} is set and contains one dimension.
     */
    private Set<Point> tilesToExport;

    /**
     * Export steps to skip. If this is {@code null} than <em>all</em> steps must be performed.
     */
    private Set<Step> stepsToSkip;

    /** When true, enclosed terrain blocks are replaced with air before second-pass layers (trees, etc.). */
    private boolean hollowInterior;

    /** Layers of solid terrain kept from air / export bounds before hollowing (WorldEdit {@code //hollow n}). */
    private int hollowThickness = ChunkInteriorHollower.DEFAULT_HOLLOW_THICKNESS;

    public static final WorldExportSettings EXPORT_EVERYTHING = new WorldExportSettings() {
        @Override
        public void setDimensionsToExport(Set<Integer> dimensionsToExport) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setTilesToExport(Set<Point> tilesToExport) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setStepsToSkip(Set<Step> stepsToSkip) {
            throw new UnsupportedOperationException();
        }
    };

    /** Skips caves, resources, lighting and leaves; optionally hollows buried terrain before trees/resources. */
    public static WorldExportSettings turboExportSettings() {
        final WorldExportSettings settings = new WorldExportSettings(null, null, EnumSet.of(Step.CAVES, Step.RESOURCES, Step.LIGHTING, Step.LEAVES));
        settings.setHollowInterior(false);
        return settings;
    }

    /** Turbo export with interior hollowing enabled (slower, smaller files). */
    public static WorldExportSettings turboExportSettingsWithHollow() {
        final WorldExportSettings settings = turboExportSettings();
        settings.setHollowInterior(true);
        settings.setHollowThickness(ChunkInteriorHollower.DEFAULT_HOLLOW_THICKNESS);
        return settings;
    }

    private static final long serialVersionUID = 1L;

    public enum Step { CAVES, RESOURCES, LIGHTING, LEAVES }
}