package org.pepsoft.worldpainter.presets;

/**
 * Built-in map preparation presets exposed in the Quick Map Presets dialog.
 */
public enum MapQuickPreset {
    GRASS_BASE_STEEP_ROCK("Grass base + steep rock"),
    REALISTIC_SNOW("Realistic snow"),
    DRY_WORLD("Dry world (remove water)"),
    STONE_BASE("Stone base");

    MapQuickPreset(String label) {
        this.label = label;
    }

    public final String label;
}
