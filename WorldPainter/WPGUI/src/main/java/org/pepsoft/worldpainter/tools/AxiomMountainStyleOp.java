package org.pepsoft.worldpainter.tools;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.Void;
import org.pepsoft.worldpainter.layers.exporters.ExporterSettings;
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
        try {
            final Dimension result = ProgressDialog.executeTask(parent, new ProgressTask<>() {
                @Override
                public String getName() {
                    return "Axiom mountain texture and smooth snow";
                }

                @Override
                public Dimension execute(ProgressReceiver progressReceiver) throws OperationCancelled {
                    apply(dimension, progressReceiver);
                    return dimension;
                }
            });
        } catch (Throwable t) {
            beepAndShowError(parent, "Axiom mountain operation failed:\n" + t.getMessage(), "Error");
        } finally {
            if (app != null) app.refreshCurrentDimensionView();
        }
    }

    /** One native undo transaction for terrain and snow, including runtime failures. */
    static void apply(Dimension dimension, ProgressReceiver progress) throws OperationCancelled {
        if (dimension == null || !dimension.isUndoAvailable()) {
            throw new IllegalStateException("Enable Undo before applying the Axiom mountain operation.");
        }
        final ExporterSettings originalFrost = dimension.getLayerSettings(Frost.INSTANCE);
        final boolean ownsEvents = !dimension.isEventsInhibited();
        dimension.rememberChanges();
        if (ownsEvents) dimension.setEventsInhibited(true);
        try {
            if (progress != null) progress.checkForCancellation();
            applySurfaceStyle(dimension, progress == null ? null : new SubProgressReceiver(progress, 0, 0.5f));
            SmoothSnow.apply(dimension, false, 4, SmoothSnow.DEFAULT_SNOW_LINE_HEIGHT,
                    SmoothSnow.DEFAULT_FULL_SNOW_HEIGHT, 8, 25.0f, 55.0f, 0.15f, STYLE_SEED,
                    true, false, false, Terrain.DEEP_SNOW,
                    progress == null ? null : new SubProgressReceiver(progress, 0.5f, 0.5f));
            if (progress != null) progress.checkForCancellation();
            dimension.armSavePoint();
        } catch (OperationCancelled | RuntimeException | Error failure) {
            try {
                try {
                    dimension.undoChanges();
                    dimension.clearRedo();
                } finally {
                    dimension.setLayerSettings(Frost.INSTANCE, originalFrost);
                }
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            if (ownsEvents) dimension.setEventsInhibited(false);
        }
    }

    private static void applySurfaceStyle(Dimension dimension, ProgressReceiver progressReceiver) throws OperationCancelled {
        final float steepRock = (float) Math.tan(Math.toRadians(45.0));
        final float rockySlope = (float) Math.tan(Math.toRadians(30.0));
        int visited = 0;
        for (Tile original : dimension.getTiles()) {
            final Tile tile = dimension.getTileForEditing(original.getX(), original.getY());
            final int startX = tile.getX() << 7;
            final int startY = tile.getY() << 7;
            for (int localX = 0; localX < 128; localX++) {
                if (progressReceiver != null && (localX & 31) == 0) progressReceiver.checkForCancellation();
                for (int localY = 0; localY < 128; localY++) {
                    final int x = startX | localX;
                    final int y = startY | localY;
                    final float height = dimension.getHeightAt(x, y);
                    // Keep flooded land intact; Frost handles snow/ice only on dry mountain cells.
                    if (!Float.isFinite(height) || height <= dimension.getWaterLevelAt(x, y) + 1.0f
                            || tile.getBitLayerValue(ReadOnly.INSTANCE, localX, localY)
                            || tile.getBitLayerValue(FloodWithLava.INSTANCE, localX, localY)
                            || tile.getBitLayerValue(Void.INSTANCE, localX, localY)
                            || tile.getBitLayerValue(NotPresent.INSTANCE, localX, localY)
                            || tile.getBitLayerValue(NotPresentBlock.INSTANCE, localX, localY)) {
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
            if (progressReceiver != null) progressReceiver.setProgress(++visited / (float) Math.max(1, dimension.getTiles().size()));
        }
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
