package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.util.ProgressReceiver;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Per-run script state. Never retain a previous world, parameter map or JS global. */
final class ScriptExecution {
    private ScriptExecution() { }

    static ScriptEngine createEngine(String extension) {
        return new ScriptEngineManager().getEngineByExtension(extension);
    }

    static boolean isCancellation(Throwable failure) {
        final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof ScriptingContext.InterruptedException
                    || cause instanceof ProgressReceiver.OperationCancelled
                    || cause instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }
}
