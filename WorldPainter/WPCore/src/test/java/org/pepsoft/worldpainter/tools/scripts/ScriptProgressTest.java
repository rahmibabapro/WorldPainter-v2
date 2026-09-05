package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.ProgressReceiver;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ScriptProgressTest {
    @Test public void cancellationIsCheckedEvenWithoutAProgressReceiver() {
        final ScriptingContext context = new ScriptingContext(false);
        final ScriptProgress progress = new ScriptProgress(context, null);
        progress.setProgress(.25);
        context.interrupt();
        assertThrows(ScriptingContext.InterruptedException.class, () -> progress.setProgress(.5));
        assertThrows(ScriptingContext.InterruptedException.class, progress::checkForCancel);
    }

    @Test public void invalidProgressIsRejectedEvenWithoutAReceiver() {
        final ScriptProgress progress = new ScriptProgress(new ScriptingContext(false), null);
        for (double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> progress.setProgress(value));
        }
    }

    @Test public void finiteProgressIsClampedBeforeDelivery() {
        final List<Float> received = new ArrayList<>();
        final ScriptProgress progress = new ScriptProgress(new ScriptingContext(false), receiver(received, false, false));
        progress.setProgress(-10);
        progress.setProgress(.25);
        progress.setProgress(10);
        assertEquals(List.of(0f, .25f, 1f), received);
        assertThrows(IllegalArgumentException.class, () -> progress.setProgress(Double.NaN));
        assertEquals(3, received.size());
    }

    @Test public void receiverCancellationDuringSetProgressPermanentlyInterruptsContext() {
        final ScriptingContext context = new ScriptingContext(false);
        final ScriptProgress progress = new ScriptProgress(context, receiver(new ArrayList<>(), true, false));
        assertThrows(ScriptingContext.InterruptedException.class, () -> progress.setProgress(.25));
        assertThrows(ScriptingContext.InterruptedException.class, context::checkForInterrupt);
    }

    @Test public void receiverCancellationDuringExplicitCheckPermanentlyInterruptsContext() {
        final ScriptingContext context = new ScriptingContext(false);
        final ScriptProgress progress = new ScriptProgress(context, receiver(new ArrayList<>(), false, true));
        assertThrows(ScriptingContext.InterruptedException.class, progress::checkForCancel);
        assertThrows(ScriptingContext.InterruptedException.class, context::checkForInterrupt);
    }

    private static ProgressReceiver receiver(List<Float> received, boolean cancelProgress, boolean cancelCheck) {
        return new ProgressReceiver() {
            @Override public void setProgress(float progress) throws OperationCancelled {
                if (cancelProgress) throw new OperationCancelled("test cancellation");
                received.add(progress);
            }
            @Override public void checkForCancellation() throws OperationCancelled {
                if (cancelCheck) throw new OperationCancelled("test cancellation");
            }
            @Override public void exceptionThrown(Throwable exception) { }
            @Override public void done() { }
            @Override public void setMessage(String message) { }
            @Override public void reset() { }
            @Override public void subProgressStarted(org.pepsoft.util.SubProgressReceiver subProgressReceiver) { }
        };
    }
}
