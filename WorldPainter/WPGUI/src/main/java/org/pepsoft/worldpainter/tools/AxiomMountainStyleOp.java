package org.pepsoft.worldpainter.tools;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.tools.scripts.SmoothSnow;
import org.pepsoft.worldpainter.tools.scripts.MossSuitability;

import java.awt.*;

import static org.pepsoft.util.swing.MessageUtils.beepAndShowError;

/** One-click Axiom-inspired mountain surface pass: rock, grass, moss, wet soil and smooth snow. */
public final class AxiomMountainStyleOp {
    private static final long STYLE_SEED = 160190L;

    private AxiomMountainStyleOp() {
    }

    public static void run(Window parent, App app, Dimension dimension) {
        if (dimension == null) {
            beepAndShowError(parent, "No dimension is open.", "Error");
            return;
        }
        dimension.rememberChanges();
        try {
            final Dimension result = ProgressDialog.executeTask(parent, new ProgressTask<>() {
                @Override
                public String getName() {
                    return "Axiom mountain texture and smooth snow";
                }

                @Override
                public Dimension execute(ProgressReceiver progressReceiver) throws OperationCancelled {
                    dimension.setEventsInhibited(true);
                    try {
                        applySurfaceStyle(dimension, new SubProgressReceiver(progressReceiver, 0.0f, 0.50f));
                        SmoothSnow.apply(dimension, false, 4, SmoothSnow.DEFAULT_SNOW_LINE_HEIGHT,
                                SmoothSnow.DEFAULT_FULL_SNOW_HEIGHT, 8,
                                25.0f, 55.0f, 0.15f, STYLE_SEED,
                                true, false, false, Terrain.DEEP_SNOW,
                                new SubProgressReceiver(progressReceiver, 0.50f, 0.50f));
                    } finally {
                        dimension.setEventsInhibited(false);
                    }
                    return dimension;
                }
            });
            if (result != null) {
                dimension.armSavePoint();
                if (app != null) {
                    app.refreshCurrentDimensionView();
                }
            } else if (dimension.undoChanges()) {
                dimension.clearRedo();
                if (app != null) {
                    app.refreshCurrentDimensionView();
                }
            }
        } catch (Throwable t) {
            beepAndShowError(parent, "Axiom mountain operation failed:\n" + t.getMessage(), "Error");
        }
    }

    private static void applySurfaceStyle(Dimension dimension, ProgressReceiver progressReceiver) throws OperationCancelled {
        final float steepRock = (float) Math.tan(Math.toRadians(45.0));
        final float rockySlope = (float) Math.tan(Math.toRadians(30.0));
        dimension.visitTilesForEditing().andDo(tile -> {
            final int startX = tile.getX() << 7;
            final int startY = tile.getY() << 7;
            for (int localX = 0; localX < 128; localX++) {
                for (int localY = 0; localY < 128; localY++) {
                    final int x = startX | localX;
                    final int y = startY | localY;
                    final float height = dimension.getHeightAt(x, y);
                    // Keep flooded land intact; Frost handles snow/ice only on dry mountain cells.
                    if (height <= dimension.getWaterLevelAt(x, y) + 1.0f) {
                        continue;
                    }
                    final float slope = dimension.getSlope(x, y);
                    final float variation = noise(x, y);
                    final float moss = mossSuitability(dimension, x, y, height);
                    final Terrain target;
                    if (slope >= steepRock) {
                        target = Terrain.STONE_MIX;
                    } else if (slope >= rockySlope) {
                        target = variation < 0.18f ? Terrain.COBBLESTONE : (variation < 0.34f ? Terrain.BASALT : Terrain.STONE_MIX);
                    } else if (moss >= 0.50f && variation < 0.22f * moss) {
                        target = variation < 0.16f * moss ? Terrain.MOSS : Terrain.PALE_MOSS;
                    } else if (height < 115.0f && variation < 0.28f) {
                        target = Terrain.MUD;
                    } else {
                        target = Terrain.GRASS;
                    }
                    if (tile.getTerrain(localX, localY) != target) {
                        tile.setTerrain(localX, localY, target);
                    }
                }
            }
        }, progressReceiver);
    }

    private static float noise(int x, int y) {
        long value = STYLE_SEED ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (value >>> 40) / (float) (1 << 24);
    }

    private static float mossSuitability(Dimension dimension, int x, int y, float center) {
        final float west = heightAtOrCenter(dimension, x - 4, y, center), east = heightAtOrCenter(dimension, x + 4, y, center);
        final float north = heightAtOrCenter(dimension, x, y - 1, center), south = heightAtOrCenter(dimension, x, y + 1, center);
        final float farNorth = heightAtOrCenter(dimension, x, y - 4, center), farSouth = heightAtOrCenter(dimension, x, y + 4, center);
        final float dx = (east - west) / 8.0f, dy = (farSouth - farNorth) / 8.0f;
        final float concavity = (heightAtOrCenter(dimension, x - 4, y, center) + heightAtOrCenter(dimension, x + 4, y, center)
                + heightAtOrCenter(dimension, x, y - 4, center) + heightAtOrCenter(dimension, x, y + 4, center) - 4.0f * center) / 16.0f;
        return MossSuitability.coverage(center, dx, dy, north - center, south - center, concavity);
    }

    private static float heightAtOrCenter(Dimension dimension, int x, int y, float center) {
        return dimension.isTilePresent(x >> 7, y >> 7) ? dimension.getHeightAt(x, y) : center;
    }
}
