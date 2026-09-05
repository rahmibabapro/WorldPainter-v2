package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;

/**
 * Progress and cancellation API exposed to JavaScript scripts.
 */
@SuppressWarnings("unused")
public class ScriptProgress {
    public ScriptProgress(ScriptingContext context, ProgressReceiver progressReceiver) {
        this.context = context;
        this.progressReceiver = progressReceiver;
    }

    public void setProgress(double progress) {
        context.checkForInterrupt();
        if (!Double.isFinite(progress)) {
            throw new IllegalArgumentException("Progress must be finite");
        }
        if (progressReceiver != null) {
            try {
                progressReceiver.setProgress((float) Math.max(0.0, Math.min(1.0, progress)));
            } catch (OperationCancelled e) {
                context.interrupt();
                throw new ScriptingContext.InterruptedException();
            }
        }
    }

    public void checkForCancel() {
        context.checkForInterrupt();
        if (progressReceiver != null) {
            try {
                progressReceiver.checkForCancellation();
            } catch (OperationCancelled e) {
                context.interrupt();
                throw new ScriptingContext.InterruptedException();
            }
        }
    }

    private final ScriptingContext context;
    private final ProgressReceiver progressReceiver;
}
