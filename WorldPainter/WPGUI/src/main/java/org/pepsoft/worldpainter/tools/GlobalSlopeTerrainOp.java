package org.pepsoft.worldpainter.tools;

import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.presets.MapQuickPresetExecutor;

import java.awt.*;

import static org.pepsoft.util.swing.MessageUtils.beepAndShowError;

/**
 * Native global operation: Stone Mix on slopes above the threshold, grass on gentler slopes.
 */
public final class GlobalSlopeTerrainOp {
    private static final int DEFAULT_DEGREES_ABOVE = 45;

    private GlobalSlopeTerrainOp() {
    }

    public static void run(Window parent, App app, Dimension dimension) {
        run(parent, app, dimension, DEFAULT_DEGREES_ABOVE);
    }

    public static void run(Window parent, App app, Dimension dimension, int degreesAbove) {
        if (dimension == null) {
            beepAndShowError(parent, "No dimension is open.", "Error");
            return;
        }
        try {
            final int selectedDegrees = degreesAbove;
            final Dimension result = ProgressDialog.executeTask(parent, new ProgressTask<>() {
                @Override
                public String getName() {
                    return "Stone Mix / grass slope split (" + selectedDegrees + "°)";
                }

                @Override
                public Dimension execute(ProgressReceiver progressReceiver) throws OperationCancelled {
                    apply(dimension, selectedDegrees, progressReceiver);
                    return dimension;
                }
            });
        } catch (Throwable t) {
            beepAndShowError(parent, "Stone/grass slope split failed:\n" + t.getMessage(), "Error");
        } finally {
            if (app != null) app.refreshCurrentDimensionView();
        }
    }

    /** One native transaction, including cancellation or failure during painting. */
    static void apply(Dimension dimension, int degreesAbove, ProgressReceiver progress) throws OperationCancelled {
        if (degreesAbove < 0 || degreesAbove > 90) {
            throw new IllegalArgumentException("Slope threshold must be between 0 and 90 degrees.");
        }
        if (dimension == null || !dimension.isUndoAvailable()) {
            throw new IllegalStateException("Enable Undo before applying the stone/grass operation.");
        }
        final boolean ownsEvents = !dimension.isEventsInhibited();
        dimension.rememberChanges();
        if (ownsEvents) dimension.setEventsInhibited(true);
        try {
            if (progress != null) progress.checkForCancellation();
            MapQuickPresetExecutor.applyStoneMixGrassSlopeSplit(dimension, degreesAbove, progress);
            if (progress != null) progress.checkForCancellation();
            dimension.armSavePoint();
        } catch (OperationCancelled | RuntimeException | Error failure) {
            try {
                dimension.undoChanges();
                dimension.clearRedo();
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            if (ownsEvents) dimension.setEventsInhibited(false);
        }
    }
}
