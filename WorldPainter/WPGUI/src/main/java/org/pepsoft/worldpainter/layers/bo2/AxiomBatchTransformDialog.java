package org.pepsoft.worldpainter.layers.bo2;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Apply batch transforms to selected Axiom {@code .bp} blueprints (#509).
 */
public class AxiomBatchTransformDialog extends JDialog {
    public AxiomBatchTransformDialog(Window owner, List<WPObject> selected, Platform platform) {
        super(owner, "Axiom blueprint batch", ModalityType.APPLICATION_MODAL);
        this.selected = selected;
        this.platform = platform;
        result = new ArrayList<>(selected);

        final JSpinner spinnerY = new JSpinner(new SpinnerNumberModel(0, -256, 256, 1));
        final JComboBox<String> comboRotate = new JComboBox<>(new String[]{
                "None", "Rotate 90° CW", "Rotate 180°", "Rotate 270° CW"
        });
        final JComboBox<String> comboMirror = new JComboBox<>(new String[]{
                "None", "Mirror X", "Mirror Z (WP Y)"
        });
        final JCheckBox checkReplaceAir = new JCheckBox("Replace air with:");
        final JTextField fieldAirMaterial = new JTextField("minecraft:stone");
        fieldAirMaterial.setEnabled(false);
        checkReplaceAir.addActionListener(e -> fieldAirMaterial.setEnabled(checkReplaceAir.isSelected()));

        final JButton apply = new JButton("Apply to selection");
        final JButton cancel = new JButton("Cancel");
        apply.addActionListener(e -> {
            try {
                result = transformAll(
                        (Integer) spinnerY.getValue(),
                        comboRotate.getSelectedIndex(),
                        comboMirror.getSelectedIndex(),
                        checkReplaceAir.isSelected() ? parseMaterial(fieldAirMaterial.getText().trim()) : null);
                applied = true;
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Transform failed", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancel.addActionListener(e -> dispose());

        final JPanel form = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("Y offset (WP Z):"), c);
        c.gridx = 1;
        form.add(spinnerY, c);
        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("Rotate:"), c);
        c.gridx = 1;
        form.add(comboRotate, c);
        c.gridx = 0; c.gridy = 2;
        form.add(new JLabel("Mirror:"), c);
        c.gridx = 1;
        form.add(comboMirror, c);
        c.gridx = 0; c.gridy = 3;
        form.add(checkReplaceAir, c);
        c.gridx = 1;
        form.add(fieldAirMaterial, c);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(apply);
        buttons.add(cancel);

        setLayout(new BorderLayout(8, 8));
        add(new JLabel(selected.size() + " object(s) selected — only Axiom .bp are transformed."), BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isApplied() {
        return applied;
    }

    public List<WPObject> getResult() {
        return result;
    }

    private List<WPObject> transformAll(int yOffset, int rotateIndex, int mirrorIndex, Material airReplacement) {
        final List<WPObject> out = new ArrayList<>(selected.size());
        for (WPObject object : selected) {
            if (! (object instanceof AxiomBlueprint)) {
                out.add(object);
                continue;
            }
            WPObject cur = (AxiomBlueprint) object;
            if (yOffset != 0) {
                cur = AxiomBlueprintBatch.applyYOffset((AxiomBlueprint) cur, yOffset);
            }
            if (rotateIndex == 1) {
                cur = AxiomBlueprintBatch.rotate90(cur, platform);
            } else if (rotateIndex == 2) {
                cur = AxiomBlueprintBatch.rotate180(cur, platform);
            } else if (rotateIndex == 3) {
                cur = AxiomBlueprintBatch.rotate270(cur, platform);
            }
            if (mirrorIndex == 1) {
                cur = AxiomBlueprintBatch.mirror(cur, false, platform);
            } else if (mirrorIndex == 2) {
                cur = AxiomBlueprintBatch.mirror(cur, true, platform);
            }
            if (airReplacement != null) {
                cur = AxiomBlueprintBatch.replaceAir(cur, airReplacement);
            }
            out.add(cur);
        }
        return out;
    }

    private static Material parseMaterial(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Material name required");
        }
        final String id = name.contains(":") ? name : ("minecraft:" + name);
        return Material.get(id);
    }

    private final List<WPObject> selected;
    private final Platform platform;
    private List<WPObject> result;
    private boolean applied;
}
