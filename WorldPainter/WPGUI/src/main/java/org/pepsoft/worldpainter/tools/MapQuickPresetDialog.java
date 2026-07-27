package org.pepsoft.worldpainter.tools;

import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.presets.MapQuickPreset;
import org.pepsoft.worldpainter.presets.MapQuickPresetExecutor;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static org.pepsoft.util.swing.MessageUtils.beepAndShowError;

/**
 * One-click map preparation presets (grass + steep rock, stone base, dry world, snow).
 */
public class MapQuickPresetDialog extends WorldPainterDialog {
    public MapQuickPresetDialog(Window parent, Dimension dimension, App app) {
        super(parent);
        this.dimension = dimension;
        this.app = app;
        initComponents();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setTitle("Quick Map Presets");
        setModal(true);

        final JPanel slopePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        slopePanel.add(new JLabel("Steep slope threshold:"));
        final JComboBox<String> slopeThreshold = new JComboBox<>(new String[] {"45%", "55%", "65%"});
        slopeThreshold.setSelectedItem("55%");
        slopePanel.add(slopeThreshold);

        final JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        buttonPanel.add(createPresetButton(MapQuickPreset.GRASS_BASE_STEEP_ROCK, () -> parseSlopeThreshold((String) slopeThreshold.getSelectedItem())));
        buttonPanel.add(createPresetButton(MapQuickPreset.STONE_BASE, () -> 0.55f));
        buttonPanel.add(createPresetButton(MapQuickPreset.DRY_WORLD, () -> 0.55f));
        buttonPanel.add(createPresetButton(MapQuickPreset.REALISTIC_SNOW, () -> 0.55f));

        final JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(slopePanel, BorderLayout.NORTH);
        content.add(buttonPanel, BorderLayout.CENTER);
        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        final JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(closeButton);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
    }

    private JButton createPresetButton(MapQuickPreset preset, SlopeThresholdProvider slopeProvider) {
        final JButton button = new JButton(preset.label);
        button.addActionListener(e -> runPreset(preset, slopeProvider.getSlopeThreshold()));
        return button;
    }

    private void runPreset(MapQuickPreset preset, float slopeThreshold) {
        if (dimension == null) {
            beepAndShowError(this, "No dimension is open.", "Error");
            return;
        }
        if (preset == MapQuickPreset.REALISTIC_SNOW) {
            dispose();
            final Map<String, Object> params = new HashMap<>();
            params.put("mA", 45);
            params.put("maxlayers", 16);
            params.put("addHeight", true);
            ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                    org.pepsoft.worldpainter.tools.scripts.BundledScriptCatalog.Category.SNOW, "snowify", params);
            return;
        }
        dimension.setEventsInhibited(true);
        dimension.rememberChanges();
        try {
            final MapQuickPreset selectedPreset = preset;
            final float selectedSlope = slopeThreshold;
            final Dimension result = ProgressDialog.executeTask(this, new ProgressTask<>() {
                @Override
                public String getName() {
                    return selectedPreset.label;
                }

                @Override
                public Dimension execute(org.pepsoft.util.ProgressReceiver progressReceiver) throws OperationCancelled {
                    MapQuickPresetExecutor.apply(dimension, selectedPreset, selectedSlope, progressReceiver);
                    return dimension;
                }
            });
            if (result != null) {
                dimension.armSavePoint();
                app.refreshCurrentDimensionView();
            } else if (dimension.undoChanges()) {
                dimension.clearRedo();
                app.refreshCurrentDimensionView();
            }
        } finally {
            dimension.setEventsInhibited(false);
        }
    }

    private static float parseSlopeThreshold(String label) {
        return switch (label) {
            case "45%" -> 0.45f;
            case "65%" -> 0.65f;
            default -> 0.55f;
        };
    }

    @FunctionalInterface
    private interface SlopeThresholdProvider {
        float getSlopeThreshold();
    }

    private final Dimension dimension;
    private final App app;
}
