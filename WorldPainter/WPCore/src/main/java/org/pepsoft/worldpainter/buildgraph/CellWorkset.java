package org.pepsoft.worldpainter.buildgraph;

import java.awt.Rectangle;
import java.io.Serializable;

/**
 * Spatial workset partitioning large worlds into manageable cells with halo padding.
 */
public class CellWorkset implements Serializable {
    public final int cellX, cellY;
    public final int coreWidth, coreHeight;
    public final int haloPadding;

    public CellWorkset(int cellX, int cellY, int coreWidth, int coreHeight, int haloPadding) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.coreWidth = coreWidth;
        this.coreHeight = coreHeight;
        this.haloPadding = Math.max(0, haloPadding);
    }

    public Rectangle getCoreBounds() {
        return new Rectangle(cellX * coreWidth, cellY * coreHeight, coreWidth, coreHeight);
    }

    public Rectangle getPaddedBounds() {
        return new Rectangle(
                (cellX * coreWidth) - haloPadding,
                (cellY * coreHeight) - haloPadding,
                coreWidth + (haloPadding * 2),
                coreHeight + (haloPadding * 2)
        );
    }

    public boolean isInCore(int worldX, int worldY) {
        return getCoreBounds().contains(worldX, worldY);
    }

    private static final long serialVersionUID = 1L;
}
