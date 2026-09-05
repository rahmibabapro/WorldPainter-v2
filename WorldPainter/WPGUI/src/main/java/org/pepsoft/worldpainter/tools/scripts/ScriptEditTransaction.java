package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.exporters.ExporterSettings;

/**
 * One current-dimension undo boundary. Automatic rollback is restricted to exact
 * bundled sources with known tile/Frost edits; arbitrary scripts may do external
 * I/O, edit other worlds or create their own undo frames and cannot be rolled back here.
 */
final class ScriptEditTransaction implements AutoCloseable {
    ScriptEditTransaction(Dimension dimension, boolean automaticRollback) {
        this.dimension = dimension;
        this.automaticRollback = automaticRollback;
        if (dimension == null) {
            ownsEvents = false;
            originalFrost = null;
            return;
        }
        if (automaticRollback && !dimension.isUndoAvailable()) {
            throw new IllegalStateException("Enable Undo before running bundled editing scripts.");
        }
        originalFrost = dimension.getLayerSettings(Frost.INSTANCE);
        ownsEvents = !dimension.isEventsInhibited();
        dimension.rememberChanges();
        if (ownsEvents) dimension.setEventsInhibited(true);
    }

    void commit() { completed = true; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (dimension == null) return;
        try {
            if (!completed && automaticRollback) {
                try {
                    // A nested native rollback may already have made the frame clean.
                    // Do not call UndoManager.undo(): that could undo a user's earlier edit.
                    if (dimension.undoChanges()) dimension.clearRedo();
                } finally {
                    if (dimension.getLayerSettings(Frost.INSTANCE) != originalFrost) {
                        dimension.setLayerSettings(Frost.INSTANCE, originalFrost);
                    }
                }
            }
        } finally {
            try {
                dimension.armSavePoint();
            } finally {
                if (ownsEvents) dimension.setEventsInhibited(false);
            }
        }
    }

    private final Dimension dimension;
    private final boolean automaticRollback, ownsEvents;
    private final ExporterSettings originalFrost;
    private boolean completed, closed;
}
