package org.pepsoft.worldpainter.exporting.delta;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.World2;

import java.awt.Point;
import java.util.*;

import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

/**
 * Calculates delta tiles to export by comparing world state against an export manifest.
 */
public final class DeltaExportCalculator {

    public static class DeltaPlan {
        public final Set<Point> dirtyTiles;
        public final int totalTiles;
        public final int unchangedTiles;

        public DeltaPlan(Set<Point> dirtyTiles, int totalTiles, int unchangedTiles) {
            this.dirtyTiles = Collections.unmodifiableSet(dirtyTiles);
            this.totalTiles = totalTiles;
            this.unchangedTiles = unchangedTiles;
        }

        public boolean isFullExportNeeded() {
            return unchangedTiles == 0;
        }

        public double getSavedPercentage() {
            return totalTiles > 0 ? (100.0 * unchangedTiles / totalTiles) : 0.0;
        }
    }

    private DeltaExportCalculator() {}

    public static DeltaPlan computeDelta(World2 world, ExportManifest manifest, int borderExpansion) {
        if (world == null || manifest == null) {
            return new DeltaPlan(Collections.emptySet(), 0, 0);
        }

        final Dimension dim = world.getDimension(NORMAL_DETAIL);
        if (dim == null) {
            return new DeltaPlan(Collections.emptySet(), 0, 0);
        }

        final Set<Point> coreDirty = new HashSet<>();
        final Collection<? extends Tile> allTiles = dim.getTiles();
        final int total = allTiles.size();

        for (Tile tile : allTiles) {
            int x = tile.getX();
            int y = tile.getY();
            long currentHash = TileFingerprinter.computeTileFingerprint(tile);
            Long prevHash = manifest.getTileHash(0, x, y);

            if (prevHash == null || prevHash != currentHash) {
                coreDirty.add(new Point(x, y));
            }
        }

        // Expand by border to ensure cross-tile objects/seams merge cleanly
        final Set<Point> expandedDirty = new HashSet<>(coreDirty);
        if (borderExpansion > 0 && !coreDirty.isEmpty()) {
            for (Point p : coreDirty) {
                for (int dx = -borderExpansion; dx <= borderExpansion; dx++) {
                    for (int dy = -borderExpansion; dy <= borderExpansion; dy++) {
                        Point neighbor = new Point(p.x + dx, p.y + dy);
                        if (dim.getTile(neighbor.x, neighbor.y) != null) {
                            expandedDirty.add(neighbor);
                        }
                    }
                }
            }
        }

        int unchanged = total - expandedDirty.size();
        return new DeltaPlan(expandedDirty, total, Math.max(0, unchanged));
    }
}
