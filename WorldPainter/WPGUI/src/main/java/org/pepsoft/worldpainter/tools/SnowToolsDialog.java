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

        final JSpinner meltAngle = new JSpinner(new SpinnerNumberModel(45, 0, 89, 1));
        final JSpinner maxLayers = new JSpinner(new SpinnerNumberModel(16, 1, 64, 1));
        final JCheckBox addHeight = new JCheckBox("Add snow depth as terrain height", true);

        final JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        final GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 2, 2, 8);
        form.add(new JLabel("Melt angle (degrees):"), c);
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

        final JButton runButton = new JButton("Run Snowify");
        runButton.addActionListener(e -> {
            final Map<String, Object> params = new HashMap<>();
            params.put("mA", meltAngle.getValue());
            params.put("maxlayers", maxLayers.getValue());
            params.put("addHeight", addHeight.isSelected());
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

    private final App app;
    private final Dimension dimension;
}
