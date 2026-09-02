package org.pepsoft.worldpainter.exporting.delta;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manifest recording exported tiles and hashes to enable Delta Exports.
 */
public class ExportManifest implements Serializable {
    public static final String MANIFEST_FILE_NAME = ".wpexport-manifest.json";
    private int schemaVersion = 1;
    private String worldName;
    private long timestamp;
    private String platformId;
    private Map<String, Long> tileHashes = new LinkedHashMap<>();

    public ExportManifest() {}

    public ExportManifest(String worldName, String platformId) {
        this.worldName = worldName;
        this.platformId = platformId;
        this.timestamp = System.currentTimeMillis();
    }

    public void putTileHash(int dim, int tileX, int tileY, long hash) {
        tileHashes.put(tileKey(dim, tileX, tileY), hash);
    }

    public Long getTileHash(int dim, int tileX, int tileY) {
        return tileHashes.get(tileKey(dim, tileX, tileY));
    }

    public boolean hasTile(int dim, int tileX, int tileY) {
        return tileHashes.containsKey(tileKey(dim, tileX, tileY));
    }

    public static String tileKey(int dim, int tileX, int tileY) {
        return dim + ":" + tileX + "," + tileY;
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getWorldName() { return worldName; }
    public long getTimestamp() { return timestamp; }
    public String getPlatformId() { return platformId; }
    public Map<String, Long> getTileHashes() { return Collections.unmodifiableMap(tileHashes); }

    public void save(File targetWorldDir) throws IOException {
        File file = new File(targetWorldDir, MANIFEST_FILE_NAME);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"schemaVersion\": ").append(schemaVersion).append(",\n");
        sb.append("  \"worldName\": \"").append(escapeJson(worldName)).append("\",\n");
        sb.append("  \"timestamp\": ").append(timestamp).append(",\n");
        sb.append("  \"platformId\": \"").append(escapeJson(platformId)).append("\",\n");
        sb.append("  \"tiles\": {\n");
        int count = 0;
        for (Map.Entry<String, Long> entry : tileHashes.entrySet()) {
            if (count > 0) sb.append(",\n");
            sb.append("    \"").append(escapeJson(entry.getKey())).append("\": ").append(entry.getValue());
            count++;
        }
        sb.append("\n  }\n}");
        Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }

    public void touch() {
        this.timestamp = System.currentTimeMillis();
    }

    /** Replace all hashes with fingerprints from the dimension (full export). */
    public void replaceAllFromDimension(org.pepsoft.worldpainter.Dimension dimension, int dimId) {
        tileHashes.clear();
        if (dimension == null) {
            return;
        }
        for (org.pepsoft.worldpainter.Tile tile : dimension.getTiles()) {
            putTileHash(dimId, tile.getX(), tile.getY(), TileFingerprinter.computeTileFingerprint(tile));
        }
    }

    /** Update hashes for a subset of tiles (delta export). */
    public void updateFromTiles(org.pepsoft.worldpainter.Dimension dimension, int dimId, java.util.Set<java.awt.Point> tiles) {
        if (dimension == null || tiles == null) {
            return;
        }
        for (java.awt.Point p : tiles) {
            org.pepsoft.worldpainter.Tile tile = dimension.getTile(p.x, p.y);
            if (tile != null) {
                putTileHash(dimId, p.x, p.y, TileFingerprinter.computeTileFingerprint(tile));
            }
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static ExportManifest load(File targetWorldDir) throws IOException {
        File file = new File(targetWorldDir, MANIFEST_FILE_NAME);
        if (!file.exists()) {
            return null;
        }
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        ExportManifest manifest = new ExportManifest();

        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.startsWith("\"worldName\":")) {
                manifest.worldName = extractStringValue(line);
            } else if (line.startsWith("\"platformId\":")) {
                manifest.platformId = extractStringValue(line);
            } else if (line.startsWith("\"timestamp\":")) {
                manifest.timestamp = extractLongValue(line);
            } else if (line.contains(":") && !line.startsWith("{") && !line.startsWith("}") && !line.startsWith("\"tiles\":") && !line.startsWith("\"schemaVersion\":")) {
                int p = line.lastIndexOf(':');
                if (p > 0) {
                    String key = line.substring(0, p).replaceAll("[\" ]", "");
                    String valStr = line.substring(p + 1).replaceAll("[, ]", "");
                    try {
                        long val = Long.parseLong(valStr);
                        manifest.tileHashes.put(key, val);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return manifest;
    }

    private static String extractStringValue(String line) {
        int firstQuote = line.indexOf('"', line.indexOf(':'));
        if (firstQuote != -1) {
            int secondQuote = line.indexOf('"', firstQuote + 1);
            if (secondQuote != -1) {
                return line.substring(firstQuote + 1, secondQuote);
            }
        }
        return "";
    }

    private static long extractLongValue(String line) {
        String digits = line.replaceAll("[^0-9-]", "");
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    private static final long serialVersionUID = 1L;
}
