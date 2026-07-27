package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.tools.scripts.BundledScriptCatalog.Category;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static org.pepsoft.util.swing.MessageUtils.beepAndShowWarning;

/**
 * River generation presets using the bundled river script.
 */
public class RiverToolsDialog extends WorldPainterDialog {
    private static final int MODE_MANUAL = 0;
    private static final int MODE_AUTO_DELTA = 1;

    public RiverToolsDialog(Window parent, App app, Dimension dimension) {
        super(parent);
        this.app = app;
        this.dimension = dimension;
        initComponents();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setTitle("Nehir Oluştur / Generate Rivers");
        setModal(true);

        final int riverTerrainIndex = RiverTerrainSupport.ensureRiverSourceTerrain(app);
        final boolean riverTerrainReady = riverTerrainIndex >= 0;

        final JRadioButton manualModeRadio = new JRadioButton("Boyalı kaynaklardan / From painted sources");
        final JRadioButton autoModeRadio = new JRadioButton("Otomatik delta nehirler / Auto delta rivers", true);
        final ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(manualModeRadio);
        modeGroup.add(autoModeRadio);

        final JTextArea manualHelp = new JTextArea(
                "Dağ kaynaklarını cyan \"river\" terrain ile boyayın, ardından Oluştur'a basın.\n"
                + "Paint mountain sources with the cyan \"river\" custom terrain, then Generate.");
        manualHelp.setEditable(false);
        manualHelp.setOpaque(false);
        manualHelp.setLineWrap(true);
        manualHelp.setWrapStyleWord(true);
        manualHelp.setFont(UIManager.getFont("Label.font"));
        manualHelp.setForeground(UIManager.getColor("Label.disabledForeground"));

        final JTextArea autoHelp = new JTextArea(
                "Ana nehirler ve kollar otomatik oluşturulur; boyama gerekmez.\n"
                + "Main rivers and tributaries are generated automatically; no painting required.");
        autoHelp.setEditable(false);
        autoHelp.setOpaque(false);
        autoHelp.setLineWrap(true);
        autoHelp.setWrapStyleWord(true);
        autoHelp.setFont(UIManager.getFont("Label.font"));
        autoHelp.setForeground(UIManager.getColor("Label.disabledForeground"));

        final JButton paintSourcesButton = new JButton("Kaynak boya… / Paint river sources…");
        paintSourcesButton.setToolTipText("Select the river custom terrain and switch to the Terrain panel");
        paintSourcesButton.setEnabled(riverTerrainReady);
        paintSourcesButton.addActionListener(e -> {
            dispose();
            app.selectRiverSourceTerrainForPainting();
        });

        final JLabel terrainStatusLabel = new JLabel(
                riverTerrainReady
                        ? "Kaynak terrain: \"" + RiverTerrainSupport.RIVER_SOURCE_TERRAIN_NAME + "\" (hazır / ready)"
                        : "Uyarı: \"river\" terrain için boş slot yok. / No free custom terrain slot.");
        terrainStatusLabel.setFont(terrainStatusLabel.getFont().deriveFont(Font.ITALIC));

        final JPanel manualPanel = new JPanel(new BorderLayout(0, 8));
        manualPanel.add(manualHelp, BorderLayout.NORTH);
        final JPanel manualActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        manualActions.add(paintSourcesButton);
        manualPanel.add(manualActions, BorderLayout.CENTER);
        manualPanel.add(terrainStatusLabel, BorderLayout.SOUTH);

        final JSpinner deltaRiverCount = new JSpinner(new SpinnerNumberModel(6, 4, 7, 1));
        final JPanel autoPanel = new JPanel(new GridBagLayout());
        autoPanel.add(autoHelp, gridCell(0, 0, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        autoPanel.add(new JLabel("Ana nehir sayısı / Main rivers:"), gridCell(1, 0, 1, 1, GridBagConstraints.NONE, 0));
        autoPanel.add(deltaRiverCount, gridCell(1, 1, 1, 1, GridBagConstraints.HORIZONTAL, 1.0));

        final CardLayout modeCardLayout = new CardLayout();
        final JPanel modeCardPanel = new JPanel(modeCardLayout);
        modeCardPanel.add(manualPanel, "manual");
        modeCardPanel.add(autoPanel, "auto");

        final Runnable updateModePanel = () ->
                modeCardLayout.show(modeCardPanel, manualModeRadio.isSelected() ? "manual" : "auto");
        manualModeRadio.addActionListener(e -> updateModePanel.run());
        autoModeRadio.addActionListener(e -> updateModePanel.run());

        final JComboBox<String> styleCombo = new JComboBox<>(new String[] {
                "Soft valleys / Yumuşak vadiler",
                "Dramatic / Dramatik",
                "Realistic hydrology / Gerçekçi hidroloji"
        });
        styleCombo.setSelectedIndex(2);
        final JSpinner seaLevel = new JSpinner(new SpinnerNumberModel(0.0, -64.0, 256.0, 0.5));
        final JTextField sourceLayerField = new JTextField(20);

        final JPanel advancedPanel = new JPanel(new GridBagLayout());
        advancedPanel.setBorder(BorderFactory.createTitledBorder("Gelişmiş / Advanced"));
        advancedPanel.add(new JLabel("Görünüm / Style:"), gridCell(0, 0, 1, 1, GridBagConstraints.NONE, 0));
        advancedPanel.add(styleCombo, gridCell(0, 1, 1, 1, GridBagConstraints.HORIZONTAL, 1.0));
        advancedPanel.add(new JLabel("Deniz seviyesi / Sea level:"), gridCell(1, 0, 1, 1, GridBagConstraints.NONE, 0));
        advancedPanel.add(seaLevel, gridCell(1, 1, 1, 1, GridBagConstraints.HORIZONTAL, 1.0));
        advancedPanel.add(new JLabel("Kaynak layer (isteğe bağlı) / Source layer (optional):"), gridCell(2, 0, 1, 1, GridBagConstraints.NONE, 0));
        advancedPanel.add(sourceLayerField, gridCell(2, 1, 1, 1, GridBagConstraints.HORIZONTAL, 1.0));

        final JToggleButton advancedToggle = new JToggleButton("Gelişmiş ayarlar… / Advanced settings…");
        advancedToggle.addActionListener(e -> {
            advancedPanel.setVisible(advancedToggle.isSelected());
            pack();
        });
        advancedPanel.setVisible(false);

        final JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));

        final JPanel modeRow = new JPanel(new GridLayout(2, 1, 0, 4));
        modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeRow.add(manualModeRadio);
        modeRow.add(autoModeRadio);
        form.add(modeRow);
        form.add(Box.createVerticalStrut(8));
        modeCardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(modeCardPanel);
        form.add(Box.createVerticalStrut(8));
        advancedToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(advancedToggle);
        form.add(Box.createVerticalStrut(4));
        advancedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(advancedPanel);

        final JButton generateButton = new JButton("Oluştur / Generate");
        generateButton.addActionListener(e -> runGenerate(
                manualModeRadio.isSelected() ? MODE_MANUAL : MODE_AUTO_DELTA,
                deltaRiverCount, seaLevel, styleCombo, sourceLayerField));
        final JButton scriptEditorButton = new JButton("Script düzenleyici… / Script editor…");
        scriptEditorButton.addActionListener(e -> {
            dispose();
            ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                    Category.RIVERS, "river_script", null);
        });
        final JButton cancelButton = new JButton("İptal / Cancel");
        cancelButton.addActionListener(e -> dispose());

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(generateButton);
        buttons.add(scriptEditorButton);
        buttons.add(cancelButton);

        final JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        updateModePanel.run();
        pack();
        setMinimumSize(getSize());
    }

    private void runGenerate(int mode, JSpinner deltaRiverCount, JSpinner seaLevel,
            JComboBox<String> styleCombo, JTextField sourceLayerField) {
        if (RiverTerrainSupport.ensureRiverSourceTerrain(app) < 0) {
            beepAndShowWarning(this,
                    "Could not create the \"river\" custom terrain.\n"
                    + "\"river\" custom terrain oluşturulamadı.\n"
                    + "Free a custom terrain slot and try again.",
                    "River Terrain Missing");
            return;
        }

        final Map<String, Object> params = new HashMap<>();
        params.put("riverMode", mode);
        if (mode == MODE_AUTO_DELTA) {
            params.put("riverLayoutPreset", 1);
            params.put("modeRiverCount", deltaRiverCount.getValue());
            params.put("styleProfile", 3);
        } else {
            params.put("styleProfile", styleCombo.getSelectedIndex() + 1);
        }

        params.put("deltaSeaLevel", seaLevel.getValue());
        params.put("manualSourceTerrain", RiverTerrainSupport.RIVER_SOURCE_TERRAIN_NAME);

        if (! sourceLayerField.getText().trim().isEmpty()) {
            params.put("manualSourceLayer", sourceLayerField.getText().trim());
        }

        dispose();
        ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                Category.RIVERS, "river_script", params);
    }

    private static GridBagConstraints gridCell(int x, int y, int width, int height, int fill, double weightx) {
        final GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.gridheight = height;
        c.fill = fill;
        c.weightx = weightx;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 2, 2, 8);
        return c;
    }

    private final App app;
    private final Dimension dimension;
}
