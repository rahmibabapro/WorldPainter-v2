package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.World2;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_19;

/** Real Nashorn runs: engine-local globals must not retain a preceding world. */
public class ScriptExecutionTest {
    @Test public void freshRunsIsolateGlobalsWorldsAndParameterMaps() throws Exception {
        final World2 firstWorld = new World2(JAVA_ANVIL_1_19, -64, 320);
        final World2 secondWorld = new World2(JAVA_ANVIL_1_19, -64, 320);
        firstWorld.setName("first world");
        secondWorld.setName("second world");
        final Map<String, Object> firstParams = new HashMap<>(), secondParams = new HashMap<>();
        firstParams.put("height", 160);
        secondParams.put("height", 190);
        final ScriptEngine first = engine();
        first.put("world", firstWorld);
        first.put("params", firstParams);
        first.eval("var previousGlobal = world; params.put('visited', true); var previousHeight = params.get('height');");
        final ScriptEngine second = engine();
        assertNotSame(first, second);
        second.put("world", secondWorld);
        second.put("params", secondParams);
        assertEquals("undefined", second.eval("typeof previousGlobal"));
        assertEquals("undefined", second.eval("typeof previousHeight"));
        assertEquals("second world", second.eval("world.getName()"));
        assertEquals(190, ((Number) second.eval("params.get('height')")).intValue());
        assertEquals(Boolean.TRUE, firstParams.get("visited"));
        assertFalse(secondParams.containsKey("visited"));
    }

    @Test public void missingBindingsAreNotInheritedAfterAPreviousRunThrows() throws Exception {
        final ScriptEngine failed = engine();
        failed.put("world", new Object());
        failed.put("dimension", new Object());
        failed.put("params", Map.of("marker", 17));
        assertThrows(ScriptException.class,
                () -> failed.eval("var retained = world; Object.prototype.oldRun = 5; throw new Error('test failure');"));
        final ScriptEngine next = engine();
        assertEquals("undefined", next.eval("typeof retained"));
        assertEquals("undefined", next.eval("typeof world"));
        assertEquals("undefined", next.eval("typeof dimension"));
        assertEquals("undefined", next.eval("typeof params"));
        assertEquals("undefined", next.eval("typeof ({}).oldRun"));
    }

    @Test public void cancellationEscapingActualNashornIsRecognised() throws Exception {
        final ScriptingContext context = new ScriptingContext(false);
        final ScriptEngine engine = engine();
        engine.put("progress", new ScriptProgress(context, null));
        context.interrupt();
        final Throwable failure = assertThrows(Throwable.class, () -> engine.eval("progress.setProgress(0.5);"));
        assertTrue("Actual engine failure: " + failure, ScriptExecution.isCancellation(failure));
    }

    @Test public void causeChainsRecogniseOnlyCancellationFailures() {
        assertTrue(ScriptExecution.isCancellation(new ScriptException(new OperationCancelled("cancelled"))));
        assertTrue(ScriptExecution.isCancellation(new RuntimeException(new InterruptedException("cancelled"))));
        assertFalse(ScriptExecution.isCancellation(new RuntimeException("ordinary failure")));
        assertFalse(ScriptExecution.isCancellation(null));
    }

    @Test(timeout = 1000) public void cyclicCauseChainsDoNotHangErrorReporting() {
        final Throwable first = new Exception("first"), second = new Exception("second");
        first.initCause(second);
        second.initCause(first);
        assertFalse(ScriptExecution.isCancellation(first));
    }

    private static ScriptEngine engine() {
        final ScriptEngine engine = ScriptExecution.createEngine("js");
        assertNotNull("Bundled Nashorn engine must be discoverable", engine);
        return engine;
    }
}
