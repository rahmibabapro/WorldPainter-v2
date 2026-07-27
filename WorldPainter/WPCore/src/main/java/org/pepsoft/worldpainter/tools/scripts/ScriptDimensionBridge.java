package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.Dimension.TileVisitor;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * Script-friendly dimension helpers (tile visitor API, slope lookup).
 */
@SuppressWarnings("unused")
public class ScriptDimensionBridge {
    public ScriptDimensionBridge(Dimension dimension, ScriptingContext context, ScriptEngine scriptEngine, ProgressReceiver progressReceiver) {
        this.dimension = dimension;
        this.context = context;
        this.scriptEngine = scriptEngine;
        this.progressReceiver = progressReceiver;
    }

    public float getSlopeAt(int x, int y) {
        return dimension.getSlope(x, y);
    }

    public float getSurfaceSlopeAt(int x, int y) {
        return dimension.getSlope(x, y);
    }

    public float getHeightAt(int x, int y) {
        return dimension.getHeightAt(x, y);
    }

    public void visitTilesForEditing(Object jsVisitor) throws ScriptException, OperationCancelled {
        visitTilesForEditing(new ScriptProgress(context, progressReceiver), jsVisitor);
    }

    public void visitTilesForEditing(ScriptProgress progress, Object jsVisitor) throws ScriptException, OperationCancelled {
        if (jsVisitor == null) {
            throw new ScriptException("visitor function is required");
        }
        final TileVisitor visitor = ((Invocable) scriptEngine).getInterface(jsVisitor, TileVisitor.class);
        if (visitor == null) {
            throw new ScriptException("Could not adapt JavaScript visitor to tile visitor");
        }
        dimension.visitTilesForEditing().andDo(tile -> {
            progress.checkForCancel();
            visitor.visit(tile);
        }, progressReceiver);
    }

    private final Dimension dimension;
    private final ScriptingContext context;
    private final ScriptEngine scriptEngine;
    private final ProgressReceiver progressReceiver;
}
