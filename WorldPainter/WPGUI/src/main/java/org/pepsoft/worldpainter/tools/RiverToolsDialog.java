package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.HeightMapTileFactory;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.tools.scripts.BundledScriptCatalog.Category;

import javax.swing.*;
import java.awt.*;
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
        setTitle("Nehir / River — Araziyi koruyan sığ nehir");
        setModal(true);
        applySafeRiverDefaults();

        final JRadioButton autoRadio = new JRadioButton("1. Otomatik — hiç boyama yok", true);
        final JRadioButton waypointsRadio = new JRadioButton("2. Çizim fırçası — nehrin merkez hattını çiz");
        final JRadioButton sourcesRadio = new JRadioButton("3. Kaynak — cyan nokta(lar)dan rota ara");
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
                "Önce akış yönlerini ve havzaları tarar; yüksek kaynaklardan inen uzun vadi hatlarını karşılaştırır. "
                        + "İlk bulunan kısa kenar rotası aramayı bitirmez. "
                        + "Bulamazsa başka kaynak ve çıkışları kademeli dener. Düz arazide sığ kanal arar; çevreyi düzleştirmez veya doldurmaz. "
                        + "İstenen sayı hedef üst sınırdır. Sonuçta kaç nehir bulunduğu ve arama sınırına ulaşılıp ulaşılmadığı belirtilir. "
                        + "Aramanın sonuçsuz kalması bütün dünyada nehir yapılamayacağı anlamına gelmez.");
        final JSpinner autoRiverCount = integerSpinner(savedInt("autoRiverCount", 1, 1, 12), 1, 12, 1);
        final JPanel autoPanel = new JPanel(new GridBagLayout());
        autoPanel.add(autoHelp, gridCell(0, 0, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        addRow(autoPanel, 1, "Ana nehir sayısı:", autoRiverCount);

        final JTextArea waypointsHelp = helpText(drawingBrushHelpText());
        final JButton paintWaypointsButton = new JButton("Nehir çizim fırçasını aç…");
        paintWaypointsButton.setToolTipText("River Path katmanını, Pencil aracını ve tek blokluk merkez hattı fırçasını seçer.");
        paintWaypointsButton.addActionListener(e -> {
            PREFERENCES.putInt("easyMode", MODE_WAYPOINTS);
            dispose();
            app.selectRiverPathLayerForPainting();
        });
        final JPanel waypointsPanel = new JPanel(new GridBagLayout());
        waypointsPanel.add(waypointsHelp, gridCell(0, 0, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        waypointsPanel.add(paintWaypointsButton, gridCell(0, 1, 2, 1, GridBagConstraints.NONE, 0));

        final JTextArea sourcesHelp = helpText(
                "Cyan \"river\" terrain ile kaynak boya. O noktadan başlayan, mevcut vadilere uyan sığ rota aranır. "
                        + "Tek nokta aramayı başlatır; kaynak başka yere taşınmaz. Uygun çıkış bulunamazsa "
                        + "neden ve varsa arama sınırı gösterilir; çevreye kazı/dolgu yapılmaz.");
        final JButton paintSourcesButton = new JButton("Kaynak boya…");
        paintSourcesButton.addActionListener(e -> {
            PREFERENCES.putInt("easyMode", MODE_SOURCES);
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
        final JComboBox<RiverPreset> styleCombo = new JComboBox<>(RiverPreset.values());
        final RiverPreset selectedPreset = RiverPreset.fromPreference(PREFERENCES.get("style", RiverPreset.NATURAL_RIVER.name()));
        styleCombo.setSelectedItem(selectedPreset);
        final int minWater = dimension.getMinHeight();
        final int maxWater = dimension.getMaxHeight() - 1;
        final int defaultWater = dimension.getTileFactory() instanceof HeightMapTileFactory factory ? factory.getWaterHeight() : 62;
        // Use the current world's level, not the last unrelated world's global preference.
        final JSpinner waterLevel = integerSpinner(Math.max(minWater, Math.min(maxWater, defaultWater)), minWater, maxWater, 1);
        waterLevel.setToolTipText("Referans değeridir. Rota, bağlandığı mevcut göl/denizin gerçek su seviyesini korur.");
        final JCheckBox waterfalls = new JCheckBox("Ek şelale kazısı (sığ profillerde kapalı)", false);
        final JCheckBox smoothBanks = new JCheckBox("Yalnız su altı yatak kesitini yumuşat", PREFERENCES.getBoolean("smoothBanks", true));
        smoothBanks.setToolTipText("Sadece ıslak kanalın kesitini etkiler. Kuru kıyılar ve çevredeki yamaçlar düzleştirilmez; dolgu yapılmaz.");
        final Runnable showMode = () -> {
            final boolean waypointMode = waypointsRadio.isSelected();
            modeCards.show(modeCardPanel, autoRadio.isSelected() ? "auto" : waypointMode ? "waypoints" : "sources");
            waterLevel.setEnabled(false);
            waterfalls.setEnabled(false);
            waterfalls.setToolTipText("Sığ blueprint profilleri ek derin havuz kazmaz; arazinin doğal kot geçişleri korunur.");
        };
        autoRadio.addActionListener(e -> showMode.run());
        waypointsRadio.addActionListener(e -> showMode.run());
        sourcesRadio.addActionListener(e -> showMode.run());

        final JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(BorderFactory.createTitledBorder("Ayarlar"));
        addRow(settings, 0, "Stil:", styleCombo);
        addRow(settings, 1, "Referans deniz Y (mevcut su korunur):", waterLevel);
        settings.add(waterfalls, gridCell(0, 2, 2, 1, GridBagConstraints.NONE, 0));
        settings.add(smoothBanks, gridCell(0, 3, 2, 1, GridBagConstraints.NONE, 0));
        final JTextArea profileSummary = helpText(selectedPreset.description());
        settings.add(profileSummary, gridCell(0, 4, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        final JButton resetPreset = new JButton("Hazır ayara dön");
        settings.add(resetPreset, gridCell(0, 5, 2, 1, GridBagConstraints.NONE, 0));
        final JTextArea exportHint = helpText(exportHintText(dimension.getSurfaceSmoothing()));
        settings.add(exportHint, gridCell(0, 6, 2, 1, GridBagConstraints.HORIZONTAL, 1.0));
        final Runnable resetOptions = () -> {
            final RiverPreset style = (RiverPreset) styleCombo.getSelectedItem();
            if (style != null) {
                waterfalls.setSelected(style.waterfalls);
                smoothBanks.setSelected(true);
                profileSummary.setText(style.description());
            }
        };
        resetPreset.addActionListener(e -> resetOptions.run());

        styleCombo.addActionListener(e -> resetOptions.run());

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
        applyButton.setText(autoRadio.isSelected() ? "Rota ara / Önizleme…" : "Uygula");
        autoRadio.addActionListener(e -> applyButton.setText("Rota ara / Önizleme…"));
        waypointsRadio.addActionListener(e -> applyButton.setText("Uygula"));
        sourcesRadio.addActionListener(e -> applyButton.setText("Uygula"));
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

    private void runApply(int mode, JSpinner autoRiverCount, JComboBox<RiverPreset> styleCombo,
            JSpinner waterLevel, JCheckBox waterfalls, JCheckBox smoothBanks) {
        final RiverPreset style = (RiverPreset) styleCombo.getSelectedItem();
        final int level = ((Number) waterLevel.getValue()).intValue();
        PREFERENCES.putInt("easyMode", mode);
        PREFERENCES.putInt("autoRiverCount", (Integer) autoRiverCount.getValue());
        PREFERENCES.put("style", style.name());
        PREFERENCES.putInt("waterLevel", level);
        PREFERENCES.putBoolean("waterfalls", waterfalls.isSelected());
        PREFERENCES.putBoolean("smoothBanks", smoothBanks.isSelected());

        if (mode == MODE_AUTO) {
            dispose();
            new RiverSearchDialog(app, dimension, style, (Integer) autoRiverCount.getValue(), smoothBanks.isSelected()).setVisible(true);
            return;
        }

        if (mode == MODE_WAYPOINTS) {
            final CustomLayer pathLayer = RiverPathSupport.ensureRiverPathLayer(app);
            if (pathLayer == null) {
                beepAndShowWarning(this, "River Path layer oluşturulamadı.", "Nehir");
                return;
            }
            final Map<String, Object> params = style.parameters(RiverPreset.Mode.WAYPOINTS, 1, level,
                    waterfalls.isSelected(), smoothBanks.isSelected());
            dispose();
            ScriptLibraryActions.runBundledScript(app, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                    Category.RIVERS, "river_from_line", params);
            return;
        }

        if (mode == MODE_SOURCES && RiverTerrainSupport.ensureRiverSourceTerrain(app) < 0) {
            beepAndShowWarning(this,
                    "\"river\" cyan kaynak terrain oluşturulamadı.\nBoş bir Custom Terrain slotu açın.",
                    "Nehir");
            return;
        }

        final Map<String, Object> params = style.parameters(mode == MODE_AUTO ? RiverPreset.Mode.AUTO : RiverPreset.Mode.SOURCES,
                (Integer) autoRiverCount.getValue(), level, waterfalls.isSelected(), smoothBanks.isSelected());
        dispose();
        ScriptLibraryActions.runBundledScript(app, app.getWorld(), dimension, app.getUndoManagersForScripts(),
                Category.RIVERS, "river_script", params);
    }

    private static void applySafeRiverDefaults() {
        if (PREFERENCES.getInt("riverSafetyVersion", 0) < 3) {
            PREFERENCES.putInt("lineTributaries", 0);
            PREFERENCES.putBoolean("disableBranching", true);
            PREFERENCES.putInt("easyMode", MODE_AUTO);
            PREFERENCES.putInt("autoRiverCount", 1);
            PREFERENCES.putBoolean("waterfalls", false);
            PREFERENCES.putInt("riverSafetyVersion", 3);
        }
        if (PREFERENCES.getInt("riverSafetyVersion", 0) < 4) {
            PREFERENCES.putBoolean("smoothBanks", true);
            PREFERENCES.putInt("riverSafetyVersion", 4);
        }
    }

    /** Pure display logic: river creation never changes the whole-dimension export setting. */
    static String exportHintText(Dimension.SurfaceSmoothing globalMode) {
        return "Yeni nehirlerin işaretli, ıslak granit hücreleri exportta global ayardan bağımsız "
                + "2×2×2 slab/merdiven yumuşatması için değerlendirilir. Düz yüzeyler, dik uçurumlar ve "
                + "gerekli taşıyıcı bloklar tam kalır; her granit blok merdivene dönüşmez. "
                + "Bu yerel işlem kuru araziyi ve eski işaretsiz nehirleri etkilemez. Dünya geneli: "
                + (globalMode == Dimension.SurfaceSmoothing.SLABS_AND_STAIRS ? "açık" : "kapalı")
                + " (ayarı korunur).";
    }

    static String drawingBrushHelpText() {
        return "Fırçayı açınca mavi River Path katmanı, Pencil aracı ve tek blokluk merkez hattı otomatik seçilir. "
                + "Sol tuşla sürükleyerek tek parça rota çiz; Shift+tık ile düz bölüm ekle, sağ tuşla hatayı sil. "
                + "Nehir genişliğini fırça değil seçtiğin stil belirler. Çizim bitince Araçlar → Nehir ekranını yeniden aç, "
                + "Çizim fırçası modunu seç ve Uygula'ya bas. Hat yüksek ucundan alçak ucuna işlenir; küçük kopukluklar "
                + "araziye uygun biçimde birleştirilir, rota başka bir vadiye taşınmaz.";
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

    private final App app;
    private final Dimension dimension;
}
