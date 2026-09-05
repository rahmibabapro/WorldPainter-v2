package org.pepsoft.worldpainter.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable UI-to-script contract. Widths describe the full channel, never its radius. */
public enum RiverPreset {
    STREAM("Dere", 1, 3, 6, 0.85, false),
    NATURAL_RIVER("Doğal nehir", 2, 5, 12, 1.10, false),
    WIDE_RIVER("Geniş nehir", 3, 8, 20, 1.40, false),
    CANYON("Kanyon", 4, 4, 10, 2.0, false),
    WATERFALL_MOUNTAIN("Dağ deresi", 5, 3, 8, 1.0, false);

    public enum Mode { AUTO, WAYPOINTS, SOURCES }

    RiverPreset(String title, int id, int sourceWidth, int maximumWidth, double depth, boolean waterfalls) {
        this.title = title;
        this.id = id;
        this.sourceWidth = sourceWidth;
        this.maximumWidth = maximumWidth;
        this.depth = depth;
        this.waterfalls = waterfalls;
    }

    public String description() {
        return "Kaynak → aşağı akış: " + sourceWidth + "–" + maximumWidth
                + " blok yatak koridoru; en fazla " + depth + " blok su derinliği.\n"
                + "Yalnız ıslak kanalda en fazla " + (depth + 0.75) + " blok kazı; dolgu yapılmaz.\n"
                + "Kuru kıyı ve yamaçlar aynen korunur. Uygun olmayan rota için geniş düzlük veya teras oluşturulmaz.";
    }

    public Map<String, Object> parameters(Mode mode, int count, int targetLevel,
                                          boolean waterfalls, boolean smoothBanks) {
        if (count < 1 || count > 12) throw new IllegalArgumentException("River count must be 1–12");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("presetId", id);
        params.put("bankSmoothing", smoothBanks);
        params.put("enableWaterfalls", waterfalls);
        params.put("shallowGraniteDetail", true);
        if (mode == Mode.WAYPOINTS) {
            params.put("riverLayer", RiverPathSupport.RIVER_PATH_LAYER_NAME);
            params.put("riverWidth", maximumWidth);
            params.put("riverDepth", depth);
            params.put("tributaryCount", 0);
            params.put("tributaryRandomness", 0.25);
            params.put("waterLevel", targetLevel);
            params.put("linkSparseWaypoints", true);
        } else {
            params.put("riverMode", mode == Mode.AUTO ? 1 : 0);
            params.put("riverLayoutPreset", 0);
            params.put("modeRiverCount", mode == Mode.AUTO ? count : 1);
            // Never accidentally interpret old painted sources as an automatic-mode override.
            params.put("manualSourceTerrain", mode == Mode.SOURCES ? RiverTerrainSupport.RIVER_SOURCE_TERRAIN_NAME : "");
            params.put("manualSourceLayer", "");
            params.put("manualStartCoords", "");
            params.put("disableBranching", true);
            params.put("styleProfile", 1);
            params.put("deltaSeaLevel", (double) targetLevel);
        }
        return params;
    }

    public static RiverPreset fromPreference(String name) {
        try { return valueOf(name); }
        catch (IllegalArgumentException | NullPointerException e) { return NATURAL_RIVER; }
    }

    @Override public String toString() { return title; }
    public final int id, sourceWidth, maximumWidth;
    public final double depth;
    public final boolean waterfalls;
    private final String title;
}
