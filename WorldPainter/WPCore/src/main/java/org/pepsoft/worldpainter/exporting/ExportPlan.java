package org.pepsoft.worldpainter.exporting;

import org.pepsoft.worldpainter.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Persisted export intent for Retry after {@code FileInUseException} (#529).
 * Format: simple Java properties (no JSON dependency).
 */
public final class ExportPlan {
    public static final String FILENAME = "last-export-plan.properties";

    public String worldName;
    public String baseDir;
    public String mapName;
    public String platformId;
    public boolean turbo;
    public boolean linear;
    public boolean hollow;
    public String dimensionsCsv;
    public String unfinishedRegions;
    /** True when a complete {@code *.wp-exporting} temp exists and needs promote-only Retry. */
    public boolean promotePending;
    public String tempDirName;
    public long savedAtEpochMs;

    public void save(Path file) throws IOException {
        final Properties p = new Properties();
        put(p, "worldName", worldName);
        put(p, "baseDir", baseDir);
        put(p, "mapName", mapName);
        put(p, "platformId", platformId);
        p.setProperty("turbo", Boolean.toString(turbo));
        p.setProperty("linear", Boolean.toString(linear));
        p.setProperty("hollow", Boolean.toString(hollow));
        put(p, "dimensionsCsv", dimensionsCsv);
        put(p, "unfinishedRegions", unfinishedRegions);
        p.setProperty("promotePending", Boolean.toString(promotePending));
        put(p, "tempDirName", tempDirName);
        p.setProperty("savedAtEpochMs", Long.toString(savedAtEpochMs));
        Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, "WorldPainter v2 export plan — close Minecraft and Retry");
        }
    }

    public static ExportPlan load(Path file) throws IOException {
        final Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        final ExportPlan plan = new ExportPlan();
        plan.worldName = p.getProperty("worldName");
        plan.baseDir = p.getProperty("baseDir");
        plan.mapName = p.getProperty("mapName");
        plan.platformId = p.getProperty("platformId");
        plan.turbo = Boolean.parseBoolean(p.getProperty("turbo", "false"));
        plan.linear = Boolean.parseBoolean(p.getProperty("linear", "false"));
        plan.hollow = Boolean.parseBoolean(p.getProperty("hollow", "false"));
        plan.dimensionsCsv = p.getProperty("dimensionsCsv");
        plan.unfinishedRegions = p.getProperty("unfinishedRegions");
        plan.promotePending = Boolean.parseBoolean(p.getProperty("promotePending", "false"));
        plan.tempDirName = p.getProperty("tempDirName");
        plan.savedAtEpochMs = Long.parseLong(p.getProperty("savedAtEpochMs", "0"));
        return plan;
    }

    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".worldpainter-v2", FILENAME);
    }

    public static Path pathForConfigDir(java.io.File configDir) {
        return configDir.toPath().resolve(FILENAME);
    }

    public Map<String, String> summary() {
        final Map<String, String> m = new LinkedHashMap<>();
        m.put("World", worldName);
        m.put("Output", baseDir + "/" + mapName);
        m.put("Turbo", Boolean.toString(turbo));
        m.put("Linear", Boolean.toString(linear));
        if (promotePending) {
            m.put("Promote pending", tempDirName != null ? tempDirName : "yes");
        }
        if (unfinishedRegions != null && ! unfinishedRegions.isBlank()) {
            m.put("Unfinished regions", unfinishedRegions);
        }
        return m;
    }

    public static String dimensionsToCsv(Set<Integer> dims) {
        if (dims == null || dims.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (Integer d : dims) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(d);
        }
        return sb.toString();
    }

    private static void put(Properties p, String key, String value) {
        if (value != null) {
            p.setProperty(key, value);
        }
    }
}
