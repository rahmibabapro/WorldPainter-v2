package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.tools.scripts.BundledScriptCatalog.Category;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Snowify preset dialog for realistic snow placement.
 */
public class SnowToolsDialog extends WorldPainterDialog {
    public SnowToolsDialog(Window parent, App app, Dimension dimension) {
        super(parent);
        this.app = app;
        this.dimension = dimension;
        initComponents();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setTitle("Snowify");
        setModal(true);

        final JSpinner snowLine = new JSpinner(new SpinnerNumberModel(160, -2048, 8192, 1));
        final JSpinner fullSnow = new JSpinner(new SpinnerNumberModel(190, -2048, 8192, 1));
        final JSpinner meltAngle = new JSpinner(new SpinnerNumberModel(55, 26, 90, 1));
        final JSpinner maxLayers = new JSpinner(new SpinnerNumberModel(8, 1, 8, 1));
        final JCheckBox addHeight = new JCheckBox("Add snow depth as terrain height", false);
        final JCheckBox dryRun = new JCheckBox("Dry run (do not change the world)", true);

        final JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        final GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 2, 2, 8);
        form.add(new JLabel("Snow starts at height:"), c);
        c.gridx = 1;
        form.add(snowLine, c);
        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Full snow height:"), c);
        c.gridx = 1;
        form.add(fullSnow, c);
        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Snow rejection slope (degrees):"), c);
        c.gridx = 1;
        form.add(meltAngle, c);
        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Maximum snow layers:"), c);
        c.gridx = 1;
        form.add(maxLayers, c);
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        form.add(addHeight, c);
        c.gridy++;
        form.add(dryRun, c);
        c.gridy++;
        form.add(new JLabel("Targets blue Annotation 4. Use Advanced for all terrain."), c);

        final JButton runButton = new JButton("Run Snowify");
        runButton.addActionListener(e -> {
            final Map<String, Object> params;
            try {
                snowLine.commitEdit();
                fullSnow.commitEdit();
                meltAngle.commitEdit();
                maxLayers.commitEdit();
                params = parameters(((Number) snowLine.getValue()).doubleValue(),
                        ((Number) fullSnow.getValue()).doubleValue(), ((Number) maxLayers.getValue()).intValue(),
                        ((Number) meltAngle.getValue()).doubleValue(), addHeight.isSelected(), dryRun.isSelected());
            } catch (java.text.ParseException | IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, "Check the snow heights, slope and layer count.\n" + ex.getMessage(),
                        "Invalid snow settings", JOptionPane.ERROR_MESSAGE);
                return;
            }
            dispose();
            ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                    Category.SNOW, "snowify", params);
        });
        final JButton advancedButton = new JButton("Advanced…");
        advancedButton.addActionListener(e -> {
            dispose();
            ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                    Category.SNOW, "snowify", null);
        });
        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(runButton);
        buttons.add(advancedButton);
        buttons.add(cancelButton);

        final JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
    }

    /** Keep the small dialog's keys and limits aligned with the current Snowify descriptor. */
    static Map<String, Object> parameters(double snowLine, double fullSnow, int maxLayers,
                                          double rejectionSlope, boolean addHeight, boolean dryRun) {
        if (!Double.isFinite(snowLine) || !Double.isFinite(fullSnow) || fullSnow <= snowLine
                || !Double.isFinite(rejectionSlope) || rejectionSlope <= 25 || rejectionSlope > 90
                || maxLayers < 1 || maxLayers > 8) {
            throw new IllegalArgumentException("Full snow must exceed the snow line; slope must be above 25 and at most 90; layers must be 1 to 8.");
        }
        final Map<String, Object> params = new HashMap<>();
        params.put("targetMode", "annotation");
        params.put("annotationValue", 4);
        params.put("snowLineHeight", snowLine);
        params.put("fullSnowHeight", fullSnow);
        params.put("maxSnowLayers", maxLayers);
        params.put("slopeStart", 25.0);
        params.put("slopeReject", rejectionSlope);
        params.put("addHeight", addHeight);
        params.put("dryRun", dryRun);
        return params;
    }

    private final App app;
    private final Dimension dimension;
}
