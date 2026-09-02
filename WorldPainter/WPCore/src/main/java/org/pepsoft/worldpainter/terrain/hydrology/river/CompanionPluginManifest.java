package org.pepsoft.worldpainter.terrain.hydrology.river;

import java.awt.Rectangle;
import java.io.Serializable;
import java.util.*;

/**
 * Manifest (.wp-fluids.json) exported for companion server plugins (Paper/Fabric) to freeze river fluids.
 */
public class CompanionPluginManifest implements Serializable {
    public final String worldName;
    public final long exportTimestamp;
    private final List<Rectangle> frozenCorridors = new ArrayList<>();

    public CompanionPluginManifest(String worldName, long exportTimestamp) {
        this.worldName = worldName != null ? worldName : "world";
        this.exportTimestamp = exportTimestamp;
    }

    public void addCorridor(Rectangle bounds) {
        if (bounds != null) {
            frozenCorridors.add(bounds);
        }
    }

    public List<Rectangle> getFrozenCorridors() {
        return Collections.unmodifiableList(frozenCorridors);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"world\": \"").append(worldName).append("\",\n");
        sb.append("  \"timestamp\": ").append(exportTimestamp).append(",\n");
        sb.append("  \"frozenCorridors\": [\n");
        for (int i = 0; i < frozenCorridors.size(); i++) {
            Rectangle r = frozenCorridors.get(i);
            sb.append(String.format(Locale.ROOT, "    {\"minX\": %d, \"minZ\": %d, \"maxX\": %d, \"maxZ\": %d}",
                    r.x, r.y, r.x + r.width, r.y + r.height));
            if (i < frozenCorridors.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static final long serialVersionUID = 1L;
}
