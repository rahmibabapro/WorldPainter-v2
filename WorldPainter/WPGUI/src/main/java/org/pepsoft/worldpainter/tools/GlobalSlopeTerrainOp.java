package org.pepsoft.worldpainter.tools;

import org.pepsoft.util.ProgressReceiver.OperationCancelled;
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
        dimension.rememberChanges();
        try {
            final int selectedDegrees = degreesAbove;
            final Dimension result = ProgressDialog.executeTask(parent, new ProgressTask<>() {
                @Override
                public String getName() {
                    return "Stone Mix / grass slope split (" + selectedDegrees + "°)";
                }

                @Override
                public Dimension execute(org.pepsoft.util.ProgressReceiver progressReceiver) throws OperationCancelled {
                    dimension.setEventsInhibited(true);
                    try {
                        MapQuickPresetExecutor.applyStoneMixGrassSlopeSplit(dimension, selectedDegrees, progressReceiver);
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
            beepAndShowError(parent, "Stone/grass slope split failed:\n" + t.getMessage(), "Error");
        }
    }
}
