package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.tools.scripts.BundledScriptCatalog.Category;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import static org.pepsoft.util.swing.MessageUtils.beepAndShowWarning;

/**
 * The focused entry point for all bundled river workflows. The old scripts remain
 * the execution engines, while this dialog keeps their modes and safe presets in
 * one place.
 */
public class RiverToolsDialog extends WorldPainterDialog {
    private static final int MODE_PAINTED_SOURCES = 0;
    private static final int MODE_AUTO_BASIN = 1;
    private static final int MODE_DRAWN_LINE = 2;
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(RiverToolsDialog.class).node("river-designer");

    public RiverToolsDialog(Window parent, App app, Dimension dimension) {
        super(parent);
        this.app = app;
        this.dimension = dimension;
        initComponents();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setTitle("River Designer / Nehir Tasarımcısı");
        setModal(true);
        applySafeRiverDefaults();

        final boolean riverTerrainReady = RiverTerrainSupport.ensureRiverSourceTerrain(app) >= 0;

        final JRadioButton paintedModeRadio = new JRadioButton("Boyalı kaynaklar / Painted sources", true);
        final JRadioButton lineModeRadio = new JRadioButton("Çizilmiş hat / Drawn centreline");
        final JRadioButton autoModeRadio = new JRadioButton("Otomatik havza / Automatic basin");
        final ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(paintedModeRadio);
        modeGroup.add(lineModeRadio);
        modeGroup.add(autoModeRadio);
        switch (savedInt("mode", MODE_PAINTED_SOURCES, MODE_PAINTED_SOURCES, MODE_DRAWN_LINE)) {
            case MODE_DRAWN_LINE -> lineModeRadio.setSelected(true);
            case MODE_AUTO_BASIN -> autoModeRadio.setSelected(true);
            default -> paintedModeRadio.setSelected(true);
        }

        final JButton paintSourcesButton = new JButton("Kaynak boya… / Paint sources…");
        paintSourcesButton.setToolTipText("Selects the cyan river terrain in the Terrain panel");
        paintSourcesButton.setEnabled(riverTerrainReady);
        paintSourcesButton.addActionListener(e -> {
            dispose();
            app.selectRiverSourceTerrainForPainting();
        });
        final JLabel terrainStatusLabel = new JLabel(riverTerrainReady
                ? "Kaynak terrain: \"" + RiverTerrainSupport.RIVER_SOURCE_TERRAIN_NAME + "\" (hazır / ready)"
                : "Uyarı: \"river\" terrain için boş Custom Terrain slotu yok.");
        terrainStatusLabel.setFont(terrainStatusLabel.getFont().deriveFont(Font.ITALIC));

        final JTextArea paintedHelp = helpText(
                "Dağdaki kaynak noktalarını cyan \"river\" terrain ile boyayın. Birden fazla kaynak "
                        + "tek bir doğal drenaj ağına birleşebilir.");
        final JTextField sourceLayerField = new JTextField(PREFERENCES.get("sourceLayer", ""), 20);
        final JPanel paintedPanel = new JPanel(new GridBagLayout());
        paintedPanel.add(paintedHelp, gridCell(0, 0, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        paintedPanel.add(paintSourcesButton, gridCell(0, 1, 2, 1, GridBagConstraints.NONE, 0));
        paintedPanel.add(terrainStatusLabel, gridCell(0, 2, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        addRow(paintedPanel, 3, "Ek kaynak layer (isteğe bağlı):", sourceLayerField);

        final JTextArea lineHelp = helpText(
                "Bir BIT layer üzerine kesintisiz merkez hattını çizin. Script hattın gerçek bağlantılarını "
                        + "takip eder; genişlik, yatak ve yan kollar aşağıdaki ayarlardan gelir.");
        final JTextField lineLayerField = new JTextField(PREFERENCES.get("lineLayer", RiverTerrainSupport.RIVER_SOURCE_TERRAIN_NAME), 20);
        final JSpinner lineWidth = integerSpinner(savedInt("lineWidth", 8, 1, 256), 1, 256, 1);
        final JSpinner lineDepth = decimalSpinner(savedDouble("lineDepth", 3.0, 0.5, 32.0), 0.5, 32.0, 0.5);
        // A painted centreline is the authoritative river path. Extra generated
        // branches are opt-in because they can cross terrain the user did not mark.
        final JSpinner lineTributaries = integerSpinner(savedInt("lineTributaries", 0, 0, 64), 0, 64, 1);
        final JSpinner lineRandomness = decimalSpinner(savedDouble("lineRandomness", 0.6, 0.0, 1.0), 0.0, 1.0, 0.05);
        final JPanel linePanel = new JPanel(new GridBagLayout());
        linePanel.add(lineHelp, gridCell(0, 0, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        addRow(linePanel, 1, "Çizgi layer adı / Line layer:", lineLayerField);
        addRow(linePanel, 2, "Orta genişlik / Middle width:", lineWidth);
        addRow(linePanel, 3, "Yatak derinliği / Bed depth:", lineDepth);
        addRow(linePanel, 4, "Yan kol sayısı / Tributaries:", lineTributaries);
        addRow(linePanel, 5, "Yan kol kıvrımı / Tributary meander:", lineRandomness);

        final JTextArea autoHelp = helpText(
                "Yükseklik haritasından kaynaklar, akış yönleri, ana kollar ve yan kollar çıkarılır. "
                        + "Bu mod referans yerleşim kullanmaz; mevcut arazi havzasını takip eder.");
        final JSpinner autoRiverCount = integerSpinner(savedInt("autoRiverCount", 6, 1, 16), 1, 16, 1);
        final JPanel autoPanel = new JPanel(new GridBagLayout());
        autoPanel.add(autoHelp, gridCell(0, 0, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        addRow(autoPanel, 1, "Ana nehir sayısı / Main rivers:", autoRiverCount);

        final CardLayout modeCardLayout = new CardLayout();
        final JPanel modeCardPanel = new JPanel(modeCardLayout);
        modeCardPanel.add(paintedPanel, "painted");
        modeCardPanel.add(linePanel, "line");
        modeCardPanel.add(autoPanel, "auto");
        final Runnable updateModePanel = () -> modeCardLayout.show(modeCardPanel,
                paintedModeRadio.isSelected() ? "painted" : lineModeRadio.isSelected() ? "line" : "auto");
        paintedModeRadio.addActionListener(e -> updateModePanel.run());
        lineModeRadio.addActionListener(e -> updateModePanel.run());
        autoModeRadio.addActionListener(e -> updateModePanel.run());

        final JComboBox<RiverStyle> styleCombo = new JComboBox<>(RiverStyle.values());
        styleCombo.setSelectedItem(RiverStyle.fromPreference(PREFERENCES.get("style", RiverStyle.NATURAL_RIVER.name())));
        final JSpinner waterLevel = integerSpinner(savedInt("waterLevel", 62, -64, 512), -64, 512, 1);
        final JTextField avoidLayerField = new JTextField(PREFERENCES.get("avoidLayer", ""), 20);
        final JCheckBox waterfalls = new JCheckBox("Şelaleler / Waterfalls", PREFERENCES.getBoolean("waterfalls", true));
        final JCheckBox smoothBanks = new JCheckBox("Kıyıları yumuşat / Smooth banks", PREFERENCES.getBoolean("smoothBanks", true));
        final JCheckBox disableBranching = new JCheckBox("Otomatik yan kolları kapat / Disable automatic tributaries", PREFERENCES.getBoolean("disableBranching", false));
        final JCheckBox shallowGraniteDetail = new JCheckBox("Sığ yatakta granit detay / Granite detail in shallow water",
                PREFERENCES.getBoolean("shallowGraniteDetail", true));
        final JSpinner shallowGraniteMaxDepth = decimalSpinner(savedDouble("shallowGraniteMaxDepth", 1.25, 0.25, 4.0), 0.25, 4.0, 0.05);
        final JSpinner shallowGraniteFloorCoverage = decimalSpinner(savedDouble("shallowGraniteFloorCoverage", 0.20, 0.0, 1.0), 0.0, 1.0, 0.05);
        final JSpinner shallowGraniteBankCoverage = decimalSpinner(savedDouble("shallowGraniteBankCoverage", 0.40, 0.0, 1.0), 0.0, 1.0, 0.05);
        final JSpinner shallowGraniteClusterSize = integerSpinner(savedInt("shallowGraniteClusterSize", 5, 1, 16), 1, 16, 1);
        final JSpinner shallowGraniteSeed = integerSpinner(savedInt("shallowGraniteSeed", 1337, Integer.MIN_VALUE, Integer.MAX_VALUE), Integer.MIN_VALUE, Integer.MAX_VALUE, 1);

        styleCombo.addActionListener(e -> {
            final RiverStyle style = (RiverStyle) styleCombo.getSelectedItem();
            lineWidth.setValue(style.lineWidth);
            lineDepth.setValue(style.lineDepth);
            lineTributaries.setValue(style.tributaries);
            waterfalls.setSelected(style.waterfalls);
        });

        final JPanel advancedPanel = new JPanel(new GridBagLayout());
        advancedPanel.setBorder(BorderFactory.createTitledBorder("Ayarlar / Settings"));
        addRow(advancedPanel, 0, "Hazır stil / Style:", styleCombo);
        addRow(advancedPanel, 1, "Hedef su/deniz seviyesi / Water or sea level:", waterLevel);
        addRow(advancedPanel, 2, "Kaçınılacak layer / Avoid layer:", avoidLayerField);
        advancedPanel.add(waterfalls, gridCell(0, 3, 2, 1, GridBagConstraints.NONE, 0));
        advancedPanel.add(smoothBanks, gridCell(0, 4, 2, 1, GridBagConstraints.NONE, 0));
        advancedPanel.add(disableBranching, gridCell(0, 5, 2, 1, GridBagConstraints.NONE, 0));

        final JPanel riverbedDetailPanel = new JPanel(new GridBagLayout());
        riverbedDetailPanel.setBorder(BorderFactory.createTitledBorder("Sığ yatak detayı / Shallow riverbed detail"));
        riverbedDetailPanel.add(shallowGraniteDetail, gridCell(0, 0, 2, 1, GridBagConstraints.NONE, 0));
        addRow(riverbedDetailPanel, 1, "En fazla su derinliği / Maximum water depth:", shallowGraniteMaxDepth);
        addRow(riverbedDetailPanel, 2, "Taban granit oranı / Floor granite coverage:", shallowGraniteFloorCoverage);
        addRow(riverbedDetailPanel, 3, "Kıyı granit oranı / Bank granite coverage:", shallowGraniteBankCoverage);
        addRow(riverbedDetailPanel, 4, "Küme boyutu / Cluster size:", shallowGraniteClusterSize);
        addRow(riverbedDetailPanel, 5, "Seed:", shallowGraniteSeed);
        final JComponent[] shallowGraniteControls = {
                shallowGraniteMaxDepth, shallowGraniteFloorCoverage, shallowGraniteBankCoverage,
                shallowGraniteClusterSize, shallowGraniteSeed
        };
        final Runnable updateShallowGraniteControls = () -> {
            for (JComponent control : shallowGraniteControls) {
                control.setEnabled(shallowGraniteDetail.isSelected());
            }
        };
        shallowGraniteDetail.addActionListener(e -> updateShallowGraniteControls.run());
        updateShallowGraniteControls.run();

        final JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        final JPanel modeRow = new JPanel(new GridLayout(3, 1, 0, 4));
        modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeRow.add(paintedModeRadio);
        modeRow.add(lineModeRadio);
        modeRow.add(autoModeRadio);
        form.add(modeRow);
        form.add(Box.createVerticalStrut(8));
        modeCardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(modeCardPanel);
        form.add(Box.createVerticalStrut(8));
        advancedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(advancedPanel);
        form.add(Box.createVerticalStrut(8));
        riverbedDetailPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(riverbedDetailPanel);

        final JButton generateButton = new JButton("Uygula / Apply");
        generateButton.addActionListener(e -> runGenerate(
                paintedModeRadio.isSelected() ? MODE_PAINTED_SOURCES : lineModeRadio.isSelected() ? MODE_DRAWN_LINE : MODE_AUTO_BASIN,
                autoRiverCount, sourceLayerField, lineLayerField, lineWidth, lineDepth, lineTributaries, lineRandomness,
                waterLevel, styleCombo, avoidLayerField, waterfalls, smoothBanks, disableBranching,
                shallowGraniteDetail, shallowGraniteMaxDepth, shallowGraniteFloorCoverage, shallowGraniteBankCoverage,
                shallowGraniteClusterSize, shallowGraniteSeed));
        final JButton scriptEditorButton = new JButton("Gelişmiş script ayarları… / Advanced script settings…");
        scriptEditorButton.addActionListener(e -> {
            final String scriptId = lineModeRadio.isSelected() ? "river_from_line" : "river_script";
            dispose();
            ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                    Category.RIVERS, scriptId, null);
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

    private void runGenerate(int mode, JSpinner autoRiverCount, JTextField sourceLayerField, JTextField lineLayerField,
            JSpinner lineWidth, JSpinner lineDepth, JSpinner lineTributaries, JSpinner lineRandomness,
            JSpinner waterLevel, JComboBox<RiverStyle> styleCombo, JTextField avoidLayerField,
            JCheckBox waterfalls, JCheckBox smoothBanks, JCheckBox disableBranching,
            JCheckBox shallowGraniteDetail, JSpinner shallowGraniteMaxDepth, JSpinner shallowGraniteFloorCoverage,
            JSpinner shallowGraniteBankCoverage, JSpinner shallowGraniteClusterSize, JSpinner shallowGraniteSeed) {
        final Map<String, Object> params = new HashMap<>();
        final int level = ((Number) waterLevel.getValue()).intValue();

        if (mode == MODE_DRAWN_LINE) {
            final String layerName = lineLayerField.getText().trim();
            if (layerName.isEmpty()) {
                beepAndShowWarning(this, "Çizilmiş merkez hattının layer adını girin.\nEnter the drawn centreline layer name.", "River Designer");
                return;
            }
            saveSettings(mode, autoRiverCount, sourceLayerField, lineLayerField, lineWidth, lineDepth, lineTributaries,
                    lineRandomness, waterLevel, styleCombo, avoidLayerField, waterfalls, smoothBanks, disableBranching,
                    shallowGraniteDetail, shallowGraniteMaxDepth, shallowGraniteFloorCoverage, shallowGraniteBankCoverage,
                    shallowGraniteClusterSize, shallowGraniteSeed);
            params.put("riverLayer", layerName);
            params.put("riverWidth", lineWidth.getValue());
            params.put("riverDepth", lineDepth.getValue());
            params.put("tributaryCount", lineTributaries.getValue());
            params.put("tributaryRandomness", lineRandomness.getValue());
            params.put("waterLevel", level);
            params.put("bankSmoothing", smoothBanks.isSelected());
            params.put("enableWaterfalls", waterfalls.isSelected());
            addShallowGraniteParams(params, shallowGraniteDetail, shallowGraniteMaxDepth, shallowGraniteFloorCoverage,
                    shallowGraniteBankCoverage, shallowGraniteClusterSize, shallowGraniteSeed);
            dispose();
            ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                    Category.RIVERS, "river_from_line", params);
            return;
        }

        if (RiverTerrainSupport.ensureRiverSourceTerrain(app) < 0) {
            beepAndShowWarning(this,
                    "Could not create the \"river\" custom terrain.\n"
                            + "\"river\" custom terrain oluşturulamadı. Boş bir Custom Terrain slotu açın.",
                    "River Terrain Missing");
            return;
        }

        saveSettings(mode, autoRiverCount, sourceLayerField, lineLayerField, lineWidth, lineDepth, lineTributaries,
                lineRandomness, waterLevel, styleCombo, avoidLayerField, waterfalls, smoothBanks, disableBranching,
                shallowGraniteDetail, shallowGraniteMaxDepth, shallowGraniteFloorCoverage, shallowGraniteBankCoverage,
                shallowGraniteClusterSize, shallowGraniteSeed);
        final RiverStyle style = (RiverStyle) styleCombo.getSelectedItem();
        params.put("riverMode", mode == MODE_AUTO_BASIN ? 1 : 0);
        params.put("riverLayoutPreset", 0); // Automatic mode follows the actual height-map basin.
        params.put("modeRiverCount", autoRiverCount.getValue());
        params.put("styleProfile", style.scriptProfile);
        params.put("deltaSeaLevel", (double) level);
        params.put("manualSourceTerrain", RiverTerrainSupport.RIVER_SOURCE_TERRAIN_NAME);
        params.put("enableWaterfalls", waterfalls.isSelected());
        params.put("disableBranching", disableBranching.isSelected());
        addShallowGraniteParams(params, shallowGraniteDetail, shallowGraniteMaxDepth, shallowGraniteFloorCoverage,
                shallowGraniteBankCoverage, shallowGraniteClusterSize, shallowGraniteSeed);
        if (! sourceLayerField.getText().trim().isEmpty()) {
            params.put("manualSourceLayer", sourceLayerField.getText().trim());
        }
        if (! avoidLayerField.getText().trim().isEmpty()) {
            params.put("avoidLayer", avoidLayerField.getText().trim());
        }
        dispose();
        ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                Category.RIVERS, "river_script", params);
    }

    private static void saveSettings(int mode, JSpinner autoRiverCount, JTextField sourceLayerField, JTextField lineLayerField,
            JSpinner lineWidth, JSpinner lineDepth, JSpinner lineTributaries, JSpinner lineRandomness,
            JSpinner waterLevel, JComboBox<RiverStyle> styleCombo, JTextField avoidLayerField,
            JCheckBox waterfalls, JCheckBox smoothBanks, JCheckBox disableBranching,
            JCheckBox shallowGraniteDetail, JSpinner shallowGraniteMaxDepth, JSpinner shallowGraniteFloorCoverage,
            JSpinner shallowGraniteBankCoverage, JSpinner shallowGraniteClusterSize, JSpinner shallowGraniteSeed) {
        PREFERENCES.putInt("mode", mode);
        PREFERENCES.putInt("autoRiverCount", (Integer) autoRiverCount.getValue());
        PREFERENCES.put("sourceLayer", sourceLayerField.getText().trim());
        PREFERENCES.put("lineLayer", lineLayerField.getText().trim());
        PREFERENCES.putInt("lineWidth", (Integer) lineWidth.getValue());
        PREFERENCES.putDouble("lineDepth", ((Number) lineDepth.getValue()).doubleValue());
        PREFERENCES.putInt("lineTributaries", (Integer) lineTributaries.getValue());
        PREFERENCES.putDouble("lineRandomness", ((Number) lineRandomness.getValue()).doubleValue());
        PREFERENCES.putInt("waterLevel", (Integer) waterLevel.getValue());
        PREFERENCES.put("style", ((RiverStyle) styleCombo.getSelectedItem()).name());
        PREFERENCES.put("avoidLayer", avoidLayerField.getText().trim());
        PREFERENCES.putBoolean("waterfalls", waterfalls.isSelected());
        PREFERENCES.putBoolean("smoothBanks", smoothBanks.isSelected());
        PREFERENCES.putBoolean("disableBranching", disableBranching.isSelected());
        PREFERENCES.putBoolean("shallowGraniteDetail", shallowGraniteDetail.isSelected());
        PREFERENCES.putDouble("shallowGraniteMaxDepth", ((Number) shallowGraniteMaxDepth.getValue()).doubleValue());
        PREFERENCES.putDouble("shallowGraniteFloorCoverage", ((Number) shallowGraniteFloorCoverage.getValue()).doubleValue());
        PREFERENCES.putDouble("shallowGraniteBankCoverage", ((Number) shallowGraniteBankCoverage.getValue()).doubleValue());
        PREFERENCES.putInt("shallowGraniteClusterSize", (Integer) shallowGraniteClusterSize.getValue());
        PREFERENCES.putInt("shallowGraniteSeed", (Integer) shallowGraniteSeed.getValue());
    }

    private static void addShallowGraniteParams(Map<String, Object> params, JCheckBox shallowGraniteDetail,
            JSpinner shallowGraniteMaxDepth, JSpinner shallowGraniteFloorCoverage, JSpinner shallowGraniteBankCoverage,
            JSpinner shallowGraniteClusterSize, JSpinner shallowGraniteSeed) {
        params.put("shallowGraniteDetail", shallowGraniteDetail.isSelected());
        params.put("shallowGraniteMaxDepth", ((Number) shallowGraniteMaxDepth.getValue()).doubleValue());
        params.put("shallowGraniteFloorCoverage", ((Number) shallowGraniteFloorCoverage.getValue()).doubleValue());
        params.put("shallowGraniteBankCoverage", ((Number) shallowGraniteBankCoverage.getValue()).doubleValue());
        params.put("shallowGraniteClusterSize", shallowGraniteClusterSize.getValue());
        params.put("shallowGraniteSeed", shallowGraniteSeed.getValue());
    }

    /**
     * The original drawn-line defaults silently generated many unpainted tributaries.
     * Keep existing worlds safe after upgrading; users can still opt in explicitly.
     */
    private static void applySafeRiverDefaults() {
        if (PREFERENCES.getInt("riverSafetyVersion", 0) < 1) {
            PREFERENCES.putInt("lineTributaries", 0);
            PREFERENCES.putBoolean("disableBranching", true);
            PREFERENCES.putInt("riverSafetyVersion", 1);
        }
    }

    private static JTextArea helpText(String text) {
        final JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UIManager.getFont("Label.font"));
        area.setForeground(UIManager.getColor("Label.disabledForeground"));
        return area;
    }

    private static JSpinner integerSpinner(int value, int minimum, int maximum, int step) {
        return new JSpinner(new SpinnerNumberModel(value, minimum, maximum, step));
    }

    private static JSpinner decimalSpinner(double value, double minimum, double maximum, double step) {
        return new JSpinner(new SpinnerNumberModel(value, minimum, maximum, step));
    }

    private static int savedInt(String key, int defaultValue, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, PREFERENCES.getInt(key, defaultValue)));
    }

    private static double savedDouble(String key, double defaultValue, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, PREFERENCES.getDouble(key, defaultValue)));
    }

    private static void addRow(JPanel panel, int row, String label, JComponent field) {
        panel.add(new JLabel(label), gridCell(0, row, 1, 1, GridBagConstraints.NONE, 0));
        panel.add(field, gridCell(1, row, 1, 1, GridBagConstraints.HORIZONTAL, 1.0));
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

    private enum RiverStyle {
        STREAM("Dere / Stream", 1, 5, 2.0, 0, false),
        NATURAL_RIVER("Doğal nehir / Natural river", 3, 8, 3.0, 0, true),
        WIDE_RIVER("Geniş nehir / Wide river", 3, 16, 4.0, 0, false),
        CANYON("Kanyon nehri / Canyon river", 2, 8, 6.0, 0, true),
        WATERFALL_MOUNTAIN("Şelaleli dağ nehri / Waterfall mountain river", 2, 5, 4.0, 0, true);

        RiverStyle(String displayName, int scriptProfile, int lineWidth, double lineDepth, int tributaries, boolean waterfalls) {
            this.displayName = displayName;
            this.scriptProfile = scriptProfile;
            this.lineWidth = lineWidth;
            this.lineDepth = lineDepth;
            this.tributaries = tributaries;
            this.waterfalls = waterfalls;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private static RiverStyle fromPreference(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                return NATURAL_RIVER;
            }
        }

        private final String displayName;
        private final int scriptProfile;
        private final int lineWidth;
        private final double lineDepth;
        private final int tributaries;
        private final boolean waterfalls;
    }

    private final App app;
    private final Dimension dimension;
}
