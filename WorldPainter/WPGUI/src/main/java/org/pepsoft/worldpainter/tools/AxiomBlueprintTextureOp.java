package org.pepsoft.worldpainter.tools;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.SnowDepth;
import org.pepsoft.worldpainter.layers.exporters.ExporterSettings;
import org.pepsoft.worldpainter.tools.scripts.AxiomTextureMix;
import org.pepsoft.worldpainter.tools.scripts.AxiomTextureProfile;
import org.pepsoft.worldpainter.tools.scripts.AxiomTextureTransfer;
import org.pepsoft.worldpainter.tools.scripts.ScriptProgress;
import org.pepsoft.worldpainter.tools.scripts.SmoothSnow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.pepsoft.util.swing.MessageUtils.beepAndShowError;

/** One-click, exemplar-based mountain + rolling-plains surface transfer from the user's Axiom blueprints. */
public final class AxiomBlueprintTextureOp {
    private AxiomBlueprintTextureOp() {
    }

    /** Runs the operation without displaying the generic script parameter form. */
    public static void run(Window parent, App app, World2 world, Dimension dimension) {
        if ((world == null) || (dimension == null)) {
            beepAndShowError(parent, "No world is open.", "Axiom Blueprint Texture");
            return;
        }
        try {
            final File mountainBlueprint = locateMountainBlueprint();
            final File plainsBlueprint = locatePlainsBlueprint();
            final Result result = ProgressDialog.executeTask(parent, new ProgressTask<>() {
                @Override
                public String getName() {
                    return "Axiom blueprints: dağ, düzlük ve kar";
                }

                @Override
                public Result execute(ProgressReceiver progress) throws OperationCancelled {
                    try {
                        return apply(world, dimension, mountainBlueprint, plainsBlueprint, app != null, progress);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not read blueprint: " + e.getMessage(), e);
                    }
                }
            });
            if (result != null) {
                updateView(app, world, result);
            } else if (app != null) {
                app.refreshCurrentDimensionView();
            }
        } catch (IOException | RuntimeException e) {
            logger.error("Axiom blueprint surface transfer failed", e);
            if (app != null) {
                app.refreshCurrentDimensionView();
            }
            beepAndShowError(parent, "Axiom blueprint texture failed:\n" + e.getMessage(), "Axiom Texture Error");
        }
    }

    /** Entry point for the bundled, parameter-free JavaScript as well as the native menu action. */
    public static String runFromScript(World2 world, Dimension dimension, ScriptProgress scriptProgress)
            throws IOException, OperationCancelled {
        final App app = App.getInstanceIfExists();
        final boolean activeWorld = (app != null) && (app.getWorld() == world);
        final ProgressReceiver progress = scriptProgress == null ? null : new ProgressReceiver() {
            @Override public void setProgress(float fraction) { scriptProgress.setProgress(fraction); }
            @Override public void checkForCancellation() { scriptProgress.checkForCancel(); }
            @Override public void setMessage(String message) { logger.info(message); }
            @Override public void reset() { }
            @Override public void done() { }
            @Override public void exceptionThrown(Throwable t) { logger.error("Texture script failed", t); }
            @Override public void subProgressStarted(SubProgressReceiver subProgressReceiver) { }
        };
        final Result result = apply(world, dimension, locateMountainBlueprint(), locatePlainsBlueprint(), activeWorld, progress);
        if (activeWorld) {
            SwingUtilities.invokeLater(() -> updateView(app, world, result));
        }
        return result.description();
    }

    /**
     * Analyses before modifying anything. Cancellation or failure rolls back both changed cells
     * and newly allocated terrain definitions. Existing definitions are never repurposed by name.
     */
    static Result apply(World2 world, Dimension dimension, File blueprint, boolean activeWorld,
                        ProgressReceiver progress) throws IOException, OperationCancelled {
        return apply(world, dimension, blueprint, locatePlainsBlueprint(), activeWorld, progress);
    }

    static Result apply(World2 world, Dimension dimension, File mountainBlueprint, File plainsBlueprint, boolean activeWorld,
                        ProgressReceiver progress) throws IOException, OperationCancelled {
        if ((world == null) || (dimension == null)) {
            throw new IllegalArgumentException("An open world and dimension are required");
        }
        if (! dimension.isUndoAvailable()) {
            throw new IllegalStateException("Enable Undo in WorldPainter Preferences before applying this whole-map operation.");
        }
        final long start = System.nanoTime();
        final AxiomTextureProfile mountainProfile = AxiomTextureProfile.load(mountainBlueprint, sub(progress, 0.0f, 0.16f));
        final AxiomTextureProfile plainsProfile = AxiomTextureProfile.load(plainsBlueprint, sub(progress, 0.16f, 0.16f));
        return applyProfiles(world, dimension, mountainProfile, plainsProfile, activeWorld, progress, start);
    }

    static Result applyProfile(World2 world, Dimension dimension, AxiomTextureProfile profile, boolean activeWorld,
                               ProgressReceiver progress, long start) throws OperationCancelled {
        return applyProfiles(world, dimension, profile, null, activeWorld, progress, start);
    }

    /**
     * Applies the mountain source first and overlays the rolling-plains source only where its
     * target geometry fits. Both passes deliberately share a palette transaction and undo step.
     * A null plains profile is retained for the single-source compatibility/test entry point.
     */
    static Result applyProfiles(World2 world, Dimension dimension, AxiomTextureProfile mountainProfile,
                                AxiomTextureProfile plainsProfile, boolean activeWorld,
                                ProgressReceiver progress, long start) throws OperationCancelled {
        if (world == null || dimension == null || dimension.getWorld() != world) {
            throw new IllegalArgumentException("The target dimension must belong to the supplied world");
        }
        if (!dimension.isUndoAvailable()) {
            throw new IllegalStateException("Enable Undo before applying Axiom textures.");
        }
        if (progress != null) progress.checkForCancellation();
        final Set<org.pepsoft.minecraft.Material> materials = new LinkedHashSet<>(mountainProfile.getRequiredMaterials());
        // Flat plains deliberately retain only the grass state from terrain.bp. The native custom
        // terrain slot is still allocated atomically with mountain states, but cannot introduce
        // cobblestone, slabs or any other source detail between grass blocks.
        final AxiomTextureMix.Result plainsMix = (plainsProfile != null)
                ? AxiomTextureMix.createGrassOnly("Axiom plains: grass only", plainsProfile) : null;
        final AxiomTextureTerrains terrains = AxiomTextureTerrains.prepare(world, materials,
                plainsMix != null ? plainsMix.material() : null, activeWorld);
        final ExporterSettings originalFrost = dimension.getLayerSettings(Frost.INSTANCE);
        final boolean ownsEventInhibition = ! dimension.isEventsInhibited();
        boolean committed = false;
        dimension.rememberChanges();
        if (ownsEventInhibition) {
            dimension.setEventsInhibited(true);
        }
        try {
            terrains.install();
            final float mountainStart = (plainsProfile == null) ? 0.25f : 0.32f;
            final float mountainExtent = (plainsProfile == null) ? 0.52f : 0.29f;
            final AxiomTextureTransfer.Summary mountainTransfer = AxiomTextureTransfer.apply(dimension, mountainProfile,
                    terrains.getTerrains(), TEXTURE_SEED, sub(progress, mountainStart, mountainExtent),
                    (x, y, sourceWhite) -> {
                        dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, sourceWhite);
                        if (! sourceWhite) {
                            dimension.setLayerValueAt(SnowDepth.INSTANCE, x, y, 0);
                        }
                    });
            // Steep terrain is rock at every elevation. This happens before the flat grass pass,
            // whose maximum slope is below the rock threshold, so it cannot be overwritten.
            final float rockStart = (plainsProfile == null) ? 0.77f : 0.61f;
            final AxiomTextureTransfer.MixedTerrainSummary rockTransfer = AxiomTextureTransfer.applyMixedTerrain(dimension,
                    Terrain.STONE, AxiomTextureTransfer.steepRockFilter(AxiomTextureTransfer.SUMMIT_WHITE_MAXIMUM_SLOPE),
                    sub(progress, rockStart, 0.08f), (x, y, ignored) -> {
                        dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, false);
                        dimension.setLayerValueAt(SnowDepth.INSTANCE, x, y, 0);
                    });
            final String plainsTransfer;
            if (plainsProfile != null) {
                // Flat land is grass only. It clears all prior snow values it replaces, while the
                // filter keeps the grass below Y=150 and away from the steep-rock treatment.
                plainsTransfer = plainsMix + "; " + AxiomTextureTransfer.applyMixedTerrain(dimension,
                        terrains.getMixedTerrain(), AxiomTextureTransfer.rollingPlainsFilter(PLAINS_MAXIMUM_HEIGHT,
                                PLAINS_HEIGHT_TRANSITION, PLAINS_FULL_SLOPE, PLAINS_REJECTED_SLOPE,
                                TEXTURE_SEED ^ PLAINS_SEED_SALT), sub(progress, 0.69f, 0.16f),
                        (x, y, ignoredSourceSnow) -> {
                            dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, false);
                            dimension.setLayerValueAt(SnowDepth.INSTANCE, x, y, 0);
                        });
            } else {
                plainsTransfer = null;
            }
            // Summit white starts at Y=150 and is retained only below 30 degrees. Every retained
            // white block receives explicit, smoothly thickening snow rather than sparse holes.
            final SmoothSnow.Result snow = SmoothSnow.applySummitBlueprintMask(dimension, TEXTURE_SEED,
                    sub(progress, 0.85f, 0.15f), AxiomTextureTransfer.SUMMIT_WHITE_MINIMUM_HEIGHT,
                    SUMMIT_FULL_SNOW_HEIGHT, AxiomTextureTransfer.SUMMIT_WHITE_MAXIMUM_SLOPE);
            if (progress != null) {
                progress.checkForCancellation();
            }
            final Result result = new Result(terrains, mountainProfile.getSummary().toString(),
                    plainsProfile != null ? plainsProfile.getSummary().toString() : null,
                    mountainTransfer + "; steep rock: " + rockTransfer, plainsTransfer, snow,
                    (System.nanoTime() - start) / 1_000_000L);
            dimension.armSavePoint();
            committed = true;
            logger.info("Axiom blueprint surface transfer: {}", result.description());
            return result;
        } finally {
            try {
                if (! committed) {
                    dimension.undoChanges();
                    dimension.clearRedo();
                    dimension.setLayerSettings(Frost.INSTANCE, originalFrost);
                    terrains.rollback();
                }
            } finally {
                if (ownsEventInhibition) {
                    dimension.setEventsInhibited(false);
                }
            }
        }
    }

    private static File locateMountainBlueprint() throws IOException {
        final String configured = System.getProperty("worldpainter.v2.axiomTextureBlueprint");
        final File file = ((configured != null) && ! configured.isBlank())
                ? new File(configured) : new File(System.getProperty("user.home"), "Downloads/dag.bp");
        if (! file.isFile()) {
            throw new IOException("Mountain blueprint not found: " + file.getAbsolutePath());
        }
        return file;
    }

    private static File locatePlainsBlueprint() throws IOException {
        final String configured = System.getProperty("worldpainter.v2.axiomPlainsTextureBlueprint");
        final File file = ((configured != null) && ! configured.isBlank())
                ? new File(configured) : new File(System.getProperty("user.home"), "Desktop/terrain.bp");
        if (! file.isFile()) {
            throw new IOException("Plains blueprint not found: " + file.getAbsolutePath());
        }
        return file;
    }

    private static ProgressReceiver sub(ProgressReceiver progress, float start, float extent) throws OperationCancelled {
        return (progress != null) ? new SubProgressReceiver(progress, start, extent) : null;
    }

    private static void updateView(App app, World2 world, Result result) {
        if ((app != null) && (app.getWorld() == world)) {
            result.terrains().getCreatedMaterials().forEach((index, material) ->
                    app.addButtonForNewCustomTerrain(index, material, false));
            app.refreshCurrentDimensionView();
        }
    }

    record Result(AxiomTextureTerrains terrains, String mountainSource, String plainsSource,
                  String mountainTransfer, String plainsTransfer, SmoothSnow.Result snow, long millis) {
        String description() {
            return "Mountain source: " + mountainSource + "; mountain transfer: " + mountainTransfer
                    + (plainsSource != null ? "; plains source: " + plainsSource + "; plains transfer: " + plainsTransfer : "")
                    + "; snow cells: " + snow.snowCovered() + "; elapsed: " + millis + " ms. White mountain source terrain is retained and receives snow layers; surface texture only."
                    + " Source geometry and decorations are not copied.";
        }
    }

    private static final long TEXTURE_SEED = 8647981921575488628L;
    private static final long PLAINS_SEED_SALT = 0x45b2f33d7a1c9e0bL;
    private static final float PLAINS_MAXIMUM_HEIGHT = AxiomTextureTransfer.SUMMIT_WHITE_MINIMUM_HEIGHT;
    private static final float PLAINS_HEIGHT_TRANSITION = 8.0f;
    private static final float PLAINS_FULL_SLOPE = 18.0f;
    private static final float PLAINS_REJECTED_SLOPE = 30.0f;
    private static final float SUMMIT_FULL_SNOW_HEIGHT = 190.0f;
    private static final Logger logger = LoggerFactory.getLogger(AxiomBlueprintTextureOp.class);
}
