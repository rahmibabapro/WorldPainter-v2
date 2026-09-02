package org.pepsoft.worldpainter.spatial.module;

import java.io.Serializable;
import java.util.*;

/**
 * Scale-level level design prefab (Village, Harbour, Pass, Boss Arena) with terrain blending & ports.
 */
public class WorldModulePrefab implements Serializable {
    public final String moduleId;
    public final String moduleName;
    public final int width, height;
    public final int blendMargin;
    public final float[][] heightPatch;
    private final List<ModulePort> ports = new ArrayList<>();

    public WorldModulePrefab(String moduleId, String moduleName, int width, int height, int blendMargin, float[][] heightPatch) {
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.width = width;
        this.height = height;
        this.blendMargin = Math.max(1, blendMargin);
        this.heightPatch = heightPatch;
    }

    public void addPort(ModulePort port) {
        if (port != null) ports.add(port);
    }

    public List<ModulePort> getPorts() {
        return Collections.unmodifiableList(ports);
    }

    public void applyToTerrain(float[][] worldHeights, int worldW, int worldH, int placeX, int placeZ, float targetElevation) {
        for (int lx = 0; lx < width; lx++) {
            for (int lz = 0; lz < height; lz++) {
                int wx = placeX + lx;
                int wz = placeZ + lz;
                if (wx < 0 || wx >= worldW || wz < 0 || wz >= worldH) continue;

                float patchY = (heightPatch != null) ? heightPatch[lx][lz] : 0f;
                float finalModuleY = targetElevation + patchY;

                // Calculate distance to nearest boundary for smooth Hermite blending
                int distToEdge = Math.min(Math.min(lx, width - 1 - lx), Math.min(lz, height - 1 - lz));
                if (distToEdge >= blendMargin) {
                    worldHeights[wx][wz] = finalModuleY;
                } else {
                    float t = (float) distToEdge / blendMargin;
                    // Smoothstep Hermite polynomial: 3t^2 - 2t^3
                    float smoothT = t * t * (3.0f - 2.0f * t);
                    worldHeights[wx][wz] = worldHeights[wx][wz] * (1.0f - smoothT) + finalModuleY * smoothT;
                }
            }
        }
    }

    private static final long serialVersionUID = 1L;
}
