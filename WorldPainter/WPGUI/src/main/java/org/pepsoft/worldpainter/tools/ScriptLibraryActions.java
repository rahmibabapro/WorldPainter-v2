package org.pepsoft.worldpainter.tools;

import org.pepsoft.util.undo.UndoManager;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.tools.scripts.BundledScriptCatalog;
import org.pepsoft.worldpainter.tools.scripts.BundledScriptCatalog.BundledScript;
import org.pepsoft.worldpainter.tools.scripts.BundledScriptCatalog.Category;
import org.pepsoft.worldpainter.tools.scripts.ScriptRunner;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static org.pepsoft.util.swing.MessageUtils.beepAndShowError;

/**
 * Opens bundled script tools from the Tools menu.
 */
public final class ScriptLibraryActions {
    private ScriptLibraryActions() {
    }

    public static void runBundledScript(Window parent, World2 world, Dimension dimension, Collection<UndoManager> undoManagers, Category category, String scriptId) {
        runBundledScript(parent, world, dimension, undoManagers, category, scriptId, null);
    }

    public static void runBundledScript(Window parent, World2 world, Dimension dimension, Collection<UndoManager> undoManagers, Category category, String scriptId, Map<String, Object> presetParams) {
        final BundledScript script = BundledScriptCatalog.find(category, scriptId);
        if (script == null) {
            beepAndShowError(parent, "Unknown bundled script: " + scriptId, "Error");
            return;
        }
        if (world == null || dimension == null) {
            beepAndShowError(parent, "No world is open.", "Error");
            return;
        }
        if (category == Category.GLOBALS && "stone_grass_slope".equals(scriptId)) {
            final App app = parent instanceof App ? (App) parent : null;
            int degreesAbove = 45;
            if (presetParams != null && presetParams.get("slopeAngleDeg") instanceof Number n) {
                degreesAbove = n.intValue();
            }
            GlobalSlopeTerrainOp.run(parent, app, dimension, degreesAbove);
            return;
        }
        try {
            final File scriptFile = BundledScriptCatalog.materialise(script);
            final ScriptRunner dialog = new ScriptRunner(parent, world, dimension, undoManagers, scriptFile, presetParams);
            dialog.setVisible(true);
        } catch (IOException ex) {
            beepAndShowError(parent, "Could not load bundled script:\n" + ex.getMessage(), "Error");
        }
    }
}
