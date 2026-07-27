package org.pepsoft.worldpainter.tools;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.MixedMaterial;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.World2;

import static org.pepsoft.worldpainter.Terrain.CUSTOM_TERRAIN_COUNT;

/**
 * Ensures the bundled river script's default {@code manualSourceTerrain} ("river") exists as a custom terrain.
 */
public final class RiverTerrainSupport {
    /** Must match {@code river_script.js} default {@code manualSourceTerrain}. */
    public static final String RIVER_SOURCE_TERRAIN_NAME = "river";

    private RiverTerrainSupport() {
    }

    public static int findRiverSourceTerrainIndex() {
        for (int i = 0; i < CUSTOM_TERRAIN_COUNT; i++) {
            if (! Terrain.isCustomMaterialConfigured(i)) {
                continue;
            }
            final MixedMaterial material = Terrain.getCustomMaterial(i);
            if ((material != null) && RIVER_SOURCE_TERRAIN_NAME.equalsIgnoreCase(material.getName())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Creates or reuses the {@code river} custom terrain slot. Returns index, or {@code -1} if no slot is free.
     */
    public static int ensureRiverSourceTerrain(App app) {
        final World2 world = app.getWorld();
        if (world == null) {
            return -1;
        }

        final int existing = findRiverSourceTerrainIndex();
        if (existing >= 0) {
            registerTerrainButtonIfNeeded(app, existing);
            world.setMixedMaterial(existing, Terrain.getCustomMaterial(existing));
            return existing;
        }

        final int index = app.findNextAvailableCustomTerrainIndex();
        if (index < 0) {
            return -1;
        }

        final MixedMaterial material = MixedMaterial.create(RIVER_SOURCE_TERRAIN_NAME, Material.LIGHT_BLUE_CLAY);
        app.addButtonForNewCustomTerrain(index, material, false);
        world.setMixedMaterial(index, material);
        return index;
    }

    public static void selectRiverSourceTerrainForPainting(App app) {
        final int index = ensureRiverSourceTerrain(app);
        if (index < 0) {
            return;
        }
        if (app.getCustomMaterialButton(index) != null) {
            app.getCustomMaterialButton(index).setSelected(true);
            app.setTerrainPaint(Terrain.getCustomTerrain(index));
        }
        app.activateDockPanel("terrain");
        app.showCustomTerrainPanel();
    }

    private static void registerTerrainButtonIfNeeded(App app, int index) {
        if (app.getCustomMaterialButton(index) == null) {
            app.addButtonForNewCustomTerrain(index, Terrain.getCustomMaterial(index), false);
        }
    }
}
