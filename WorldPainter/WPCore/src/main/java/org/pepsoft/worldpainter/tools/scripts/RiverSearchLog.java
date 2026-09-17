package org.pepsoft.worldpainter.tools.scripts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Append-only NDJSON for river search sessions (UI and headless). */
public final class RiverSearchLog {
    private RiverSearchLog() {}

    public static Path write(RiverSearchSession.Settings settings, RiverSearchResult result, boolean forced, Path extraCopy) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "WorldPainter [V2]", "logs");
        Path file;
        try {
            Files.createDirectories(dir);
            file = dir.resolve("river-search-" + stamp + ".ndjson");
            String line = line(settings, result, forced);
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8);
            if (extraCopy != null) {
                Files.createDirectories(extraCopy.getParent());
                Files.writeString(extraCopy, line + System.lineSeparator(), StandardCharsets.UTF_8);
            }
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    static String line(RiverSearchSession.Settings settings, RiverSearchResult result, boolean forced) {
        var d = result.diagnostics();
        String reason = escape(d.reason());
        return "{\"status\":\"" + result.status() + "\",\"found\":" + result.courses().size()
                + ",\"requested\":" + result.requested()
                + ",\"forced\":" + forced
                + ",\"seaLevel\":" + settings.seaLevel()
                + ",\"overviewStep\":" + d.overviewStep()
                + ",\"candidates\":" + d.candidates()
                + ",\"rejected\":" + d.rejected()
                + ",\"elapsedMillis\":" + d.elapsedMillis()
                + ",\"lakeCells\":" + d.lakeCells()
                + ",\"reason\":\"" + reason + "\"}";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
