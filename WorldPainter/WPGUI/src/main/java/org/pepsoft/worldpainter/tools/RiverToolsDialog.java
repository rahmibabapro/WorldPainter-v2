package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.tools.scripts.BundledScriptCatalog.Category;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import static org.pepsoft.util.swing.MessageUtils.beepAndShowWarning;

/**
 * Simple native-feeling river UI with three modes. Scripts stay behind the scenes.
 */
public class RiverToolsDialog extends WorldPainterDialog {
    private static final int MODE_AUTO = 0;
    private static final int MODE_WAYPOINTS = 1;
    private static final int MODE_SOURCES = 2;
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(RiverToolsDialog.class).node("river-designer");

    public RiverToolsDialog(Window parent, App app, Dimension dimension) {
        super(parent);
        this.app = app;
        this.dimension = dimension;
        initComponents();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setTitle("Nehir / River");
        setModal(true);
        applySafeRiverDefaults();

        RiverTerrainSupport.ensureRiverSourceTerrain(app);
        RiverPathSupport.ensureRiverPathLayer(app);

        final JRadioButton autoRadio = new JRadioButton("1. Otomatik — hiç boyama yok", true);
        final JRadioButton waypointsRadio = new JRadioButton("2. Nokta yolu — ince noktalar koy, sistem birleştirir");
        final JRadioButton sourcesRadio = new JRadioButton("3. Kaynak — cyan nokta(lar); tek nokta yeter");
        final ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(autoRadio);
        modeGroup.add(waypointsRadio);
        modeGroup.add(sourcesRadio);
        switch (savedInt("easyMode", MODE_AUTO, MODE_AUTO, MODE_SOURCES)) {
            case MODE_WAYPOINTS -> waypointsRadio.setSelected(true);
            case MODE_SOURCES -> sourcesRadio.setSelected(true);
            default -> autoRadio.setSelected(true);
        }

        final JTextArea autoHelp = helpText(
                "Araziye bakıp doğal nehir ağı üretir. Hiçbir şey boyaman gerekmez.");
        final JSpinner autoRiverCount = integerSpinner(savedInt("autoRiverCount", 5, 1, 12), 1, 12, 1);
        final JPanel autoPanel = new JPanel(new GridBagLayout());
        autoPanel.add(autoHelp, gridCell(0, 0, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        addRow(autoPanel, 1, "Ana nehir sayısı:", autoRiverCount);

        final JTextArea waypointsHelp = helpText(
                "\"River Path\" layer’ına ince noktalar koy. Kopuk noktaları araziye uygun path ile birleştiririz. "
                        + "Sıra önemli değil; yüksekten alçağa bağlanır.");
        final JButton paintWaypointsButton = new JButton("Nokta boya…");
        paintWaypointsButton.addActionListener(e -> {
            dispose();
            app.selectRiverPathLayerForPainting();
        });
        final JPanel waypointsPanel = new JPanel(new GridBagLayout());
        waypointsPanel.add(waypointsHelp, gridCell(0, 0, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        waypointsPanel.add(paintWaypointsButton, gridCell(0, 1, 2, 1, GridBagConstraints.NONE, 0));

        final JTextArea sourcesHelp = helpText(
                "Cyan \"river\" terrain ile dağda kaynak boya. Bir nokta bile yeter; aşağı doğru nehir akar.");
        final JButton paintSourcesButton = new JButton("Kaynak boya…");
        paintSourcesButton.addActionListener(e -> {
            dispose();
            app.selectRiverSourceTerrainForPainting();
        });
        final JPanel sourcesPanel = new JPanel(new GridBagLayout());
        sourcesPanel.add(sourcesHelp, gridCell(0, 0, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        sourcesPanel.add(paintSourcesButton, gridCell(0, 1, 2, 1, GridBagConstraints.NONE, 0));

        final CardLayout modeCards = new CardLayout();
        final JPanel modeCardPanel = new JPanel(modeCards);
        modeCardPanel.add(autoPanel, "auto");
        modeCardPanel.add(waypointsPanel, "waypoints");
        modeCardPanel.add(sourcesPanel, "sources");
        final Runnable showMode = () -> modeCards.show(modeCardPanel,
                autoRadio.isSelected() ? "auto" : waypointsRadio.isSelected() ? "waypoints" : "sources");
        autoRadio.addActionListener(e -> showMode.run());
        waypointsRadio.addActionListener(e -> showMode.run());
        sourcesRadio.addActionListener(e -> showMode.run());

        final JComboBox<RiverStyle> styleCombo = new JComboBox<>(RiverStyle.values());
        styleCombo.setSelectedItem(RiverStyle.fromPreference(PREFERENCES.get("style", RiverStyle.NATURAL_RIVER.name())));
        final int defaultWater = 62;
        final JSpinner waterLevel = integerSpinner(savedInt("waterLevel", defaultWater, -64, 512), -64, 512, 1);
        final JCheckBox waterfalls = new JCheckBox("Şelaleler", PREFERENCES.getBoolean("waterfalls", true));
        final JCheckBox smoothBanks = new JCheckBox("Kıyıları yumuşat", PREFERENCES.getBoolean("smoothBanks", true));

        final JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(BorderFactory.createTitledBorder("Ayarlar"));
        addRow(settings, 0, "Stil:", styleCombo);
        addRow(settings, 1, "Su / deniz seviyesi:", waterLevel);
        settings.add(waterfalls, gridCell(0, 2, 2, 1, GridBagConstraints.NONE, 0));
        settings.add(smoothBanks, gridCell(0, 3, 2, 1, GridBagConstraints.NONE, 0));

        styleCombo.addActionListener(e -> {
            final RiverStyle style = (RiverStyle) styleCombo.getSelectedItem();
            if (style != null) {
                waterfalls.setSelected(style.waterfalls);
            }
        });

        final JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        final JPanel modeRow = new JPanel(new GridLayout(3, 1, 0, 4));
        modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeRow.add(autoRadio);
        modeRow.add(waypointsRadio);
        modeRow.add(sourcesRadio);
        form.add(modeRow);
        form.add(Box.createVerticalStrut(8));
        modeCardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(modeCardPanel);
        form.add(Box.createVerticalStrut(8));
        settings.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(settings);

        final JButton applyButton = new JButton("Uygula");
        applyButton.addActionListener(e -> {
            final int mode = autoRadio.isSelected() ? MODE_AUTO
                    : waypointsRadio.isSelected() ? MODE_WAYPOINTS : MODE_SOURCES;
            runApply(mode, autoRiverCount, styleCombo, waterLevel, waterfalls, smoothBanks);
        });
        final JButton cancelButton = new JButton("İptal");
        cancelButton.addActionListener(e -> dispose());
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(applyButton);
        buttons.add(cancelButton);

        final JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        showMode.run();
        pack();
        setMinimumSize(getSize());
    }

    private void runApply(int mode, JSpinner autoRiverCount, JComboBox<RiverStyle> styleCombo,
            JSpinner waterLevel, JCheckBox waterfalls, JCheckBox smoothBanks) {
        final RiverStyle style = (RiverStyle) styleCombo.getSelectedItem();
        final int level = ((Number) waterLevel.getValue()).intValue();
        PREFERENCES.putInt("easyMode", mode);
        PREFERENCES.putInt("autoRiverCount", (Integer) autoRiverCount.getValue());
        PREFERENCES.put("style", style.name());
        PREFERENCES.putInt("waterLevel", level);
        PREFERENCES.putBoolean("waterfalls", waterfalls.isSelected());
        PREFERENCES.putBoolean("smoothBanks", smoothBanks.isSelected());

        if (mode == MODE_WAYPOINTS) {
            final CustomLayer pathLayer = RiverPathSupport.ensureRiverPathLayer(app);
            if (pathLayer == null) {
                beepAndShowWarning(this, "River Path layer oluşturulamadı.", "Nehir");
                return;
            }
            final Map<String, Object> params = new HashMap<>();
            params.put("riverLayer", RiverPathSupport.RIVER_PATH_LAYER_NAME);
            params.put("riverWidth", style.lineWidth);
            params.put("riverDepth", style.lineDepth);
            params.put("tributaryCount", 0);
            params.put("tributaryRandomness", 0.4);
            params.put("waterLevel", level);
            params.put("bankSmoothing", smoothBanks.isSelected());
            params.put("enableWaterfalls", waterfalls.isSelected());
            params.put("linkSparseWaypoints", true);
            params.put("shallowGraniteDetail", false);
            dispose();
            ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                    Category.RIVERS, "river_from_line", params);
            return;
        }

        if (RiverTerrainSupport.ensureRiverSourceTerrain(app) < 0) {
            beepAndShowWarning(this,
                    "\"river\" cyan kaynak terrain oluşturulamadı.\nBoş bir Custom Terrain slotu açın.",
                    "Nehir");
            return;
        }

        final Map<String, Object> params = new HashMap<>();
        if (mode == MODE_AUTO) {
            params.put("riverMode", 1);
            params.put("riverLayoutPreset", 0);
            params.put("modeRiverCount", autoRiverCount.getValue());
            params.put("disableBranching", false);
        } else {
            // Mode 3: painted cyan sources only
            params.put("riverMode", 0);
            params.put("riverLayoutPreset", 0);
            params.put("modeRiverCount", 1);
            params.put("manualSourceTerrain", RiverTerrainSupport.RIVER_SOURCE_TERRAIN_NAME);
            params.put("disableBranching", false);
        }
        params.put("styleProfile", style.scriptProfile);
        params.put("deltaSeaLevel", (double) level);
        params.put("enableWaterfalls", waterfalls.isSelected());
        params.put("shallowGraniteDetail", false);
        dispose();
        ScriptLibraryActions.runBundledScript(this, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                Category.RIVERS, "river_script", params);
    }

    private static void applySafeRiverDefaults() {
        if (PREFERENCES.getInt("riverSafetyVersion", 0) < 2) {
            PREFERENCES.putInt("lineTributaries", 0);
            PREFERENCES.putBoolean("disableBranching", true);
            PREFERENCES.putInt("easyMode", MODE_AUTO);
            PREFERENCES.putInt("riverSafetyVersion", 2);
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

    private static int savedInt(String key, int defaultValue, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, PREFERENCES.getInt(key, defaultValue)));
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
        STREAM("Dere", 1, 5, 2.0, false),
        NATURAL_RIVER("Doğal nehir", 3, 8, 3.0, true),
        WIDE_RIVER("Geniş nehir", 3, 14, 4.0, false),
        CANYON("Kanyon", 2, 8, 6.0, true),
        WATERFALL_MOUNTAIN("Şelaleli dağ", 2, 5, 4.0, true);

        RiverStyle(String displayName, int scriptProfile, int lineWidth, double lineDepth, boolean waterfalls) {
            this.displayName = displayName;
            this.scriptProfile = scriptProfile;
            this.lineWidth = lineWidth;
            this.lineDepth = lineDepth;
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
        private final boolean waterfalls;
    }

    private final App app;
    private final Dimension dimension;
}
