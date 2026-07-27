package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.tools.scripts.BundledScriptCatalog.Category;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.pepsoft.util.swing.MessageUtils.beepAndShowError;

/**
 * Flatten a painted line layer into a road using the bundled road flatten script.
 */
public class RoadToolsDialog extends WorldPainterDialog {
    public RoadToolsDialog(Window parent, App app, Dimension dimension, List<Layer> layers) {
        super(parent);
        this.app = app;
        this.dimension = dimension;
        initComponents(layers);
        setLocationRelativeTo(parent);
    }

    private void initComponents(List<Layer> layers) {
        setTitle("Flatten Road");
        setModal(true);

        final JComboBox<Layer> layerCombo = new JComboBox<>(layers.toArray(new Layer[0]));
        final JSpinner distanceSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 20, 1));
        final JTextField roadLayerField = new JTextField(16);
        final JTextField roadSlabField = new JTextField(16);

        final JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        final GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 2, 2, 8);
        form.add(new JLabel("Center line layer:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        form.add(layerCombo, c);

        c.gridx = 0;
        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(new JLabel("Road half-width (blocks):"), c);
        c.gridx = 1;
        form.add(distanceSpinner, c);

        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Optional road surface layer:"), c);
        c.gridx = 1;
        form.add(roadLayerField, c);

        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Optional slabify layer:"), c);
        c.gridx = 1;
        form.add(roadSlabField, c);

        final JButton runButton = new JButton("Flatten");
        runButton.addActionListener(e -> {
            final Layer layer = (Layer) layerCombo.getSelectedItem();
            if (layer == null) {
                beepAndShowError(this, "Select a line layer to use as the road center line.", "Error");
                return;
            }
            final Map<String, Object> params = new HashMap<>();
            params.put("distance", distanceSpinner.getValue());
            params.put("layerMask", layer.getName());
            if (! roadLayerField.getText().trim().isEmpty()) {
                params.put("roadLayer", roadLayerField.getText().trim());
            }
            if (! roadSlabField.getText().trim().isEmpty()) {
                params.put("roadSlab", roadSlabField.getText().trim());
            }
            dispose();
            ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                    Category.ROADS, "road_flatten", params);
        });
        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(runButton);
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
