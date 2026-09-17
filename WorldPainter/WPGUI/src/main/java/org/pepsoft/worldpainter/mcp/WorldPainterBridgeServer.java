package org.pepsoft.worldpainter.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.tools.scripts.BridgeRiverOps;
import org.pepsoft.worldpainter.tools.scripts.BridgeTerrainOps;
import org.pepsoft.worldpainter.tools.scripts.DrawnRiverGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.swing.SwingUtilities;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * Embedded local HTTP bridge for AI / MCP integration on 127.0.0.1:8765.
 */
public final class WorldPainterBridgeServer {
    private static final Logger logger = LoggerFactory.getLogger(WorldPainterBridgeServer.class);
    private static final int PORT = 8765;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static HttpServer server;
    private static App appInstance;
    private static String bridgeToken;

    public static synchronized void start(App app) {
        if (server != null) return;
        appInstance = app;
        bridgeToken = System.getenv("WP_BRIDGE_TOKEN");
        if (bridgeToken == null || bridgeToken.isBlank()) {
            bridgeToken = System.getProperty("wp.bridge.token");
        }
        if (bridgeToken != null && bridgeToken.isBlank()) bridgeToken = null;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/world", new StatusHandler());
            server.createContext("/api/inspect", new InspectHandler());
            server.createContext("/api/river/carve", new RiverCarveHandler());
            server.createContext("/api/terrain/sculpt", new TerrainSculptHandler());
            server.createContext("/api/terrain/paint", new TerrainPaintHandler());
            server.createContext("/api/script", new ScriptHandler());
            server.createContext("/api/undo", new UndoHandler());
            server.createContext("/api/redo", new RedoHandler());
            server.createContext("/api/save", new SaveHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            if (bridgeToken == null) {
                logger.info("WorldPainter AI Bridge on http://127.0.0.1:{} (localhost only; set WP_BRIDGE_TOKEN for header auth)", PORT);
            } else {
                logger.info("WorldPainter AI Bridge on http://127.0.0.1:{} (token auth enabled)", PORT);
            }
        } catch (IOException e) {
            logger.error("Failed to start WorldPainter AI Bridge on port " + PORT, e);
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            logger.info("WorldPainter AI Bridge stopped.");
        }
    }

    private static boolean authorize(HttpExchange exchange) throws IOException {
        if (bridgeToken == null) return true;
        String provided = exchange.getRequestHeaders().getFirst("X-WP-Token");
        if (bridgeToken.equals(provided)) return true;
        sendJson(exchange, 401, Map.of("error", "Unauthorized"));
        return false;
    }

    private static <T> T onEdt(Callable<T> work) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return work.call();
        final Object[] box = new Object[2];
        SwingUtilities.invokeAndWait(() -> {
            try {
                box[0] = work.call();
            } catch (Throwable t) {
                box[1] = t;
            }
        });
        if (box[1] instanceof Exception e) throw e;
        if (box[1] instanceof Error e) throw e;
        @SuppressWarnings("unchecked") T value = (T) box[0];
        return value;
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    private static JsonNode readJson(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.isBlank()) return JSON.createObjectNode();
        return JSON.readTree(body);
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> map = new LinkedHashMap<>();
        String q = exchange.getRequestURI().getQuery();
        if (q == null || q.isBlank()) return map;
        for (String param : q.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) map.put(pair[0], pair[1]);
            else if (pair.length == 1) map.put(pair[0], "");
        }
        return map;
    }

    private static Dimension requireDimension() {
        if (appInstance == null) throw new IllegalStateException("App not initialized");
        Dimension dim = appInstance.getDimension();
        if (dim == null) throw new IllegalStateException("No world loaded");
        return dim;
    }

    private static final class StatusHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) return;
            try {
                Map<String, Object> result = onEdt(() -> {
                    World2 world = appInstance != null ? appInstance.getWorld() : null;
                    Dimension dim = appInstance != null ? appInstance.getDimension() : null;
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("online", true);
                    if (world == null || dim == null) {
                        json.put("worldLoaded", false);
                        return json;
                    }
                    int minTileX = Integer.MAX_VALUE, maxTileX = Integer.MIN_VALUE;
                    int minTileY = Integer.MAX_VALUE, maxTileY = Integer.MIN_VALUE;
                    for (Tile tile : dim.getTiles()) {
                        minTileX = Math.min(minTileX, tile.getX());
                        maxTileX = Math.max(maxTileX, tile.getX());
                        minTileY = Math.min(minTileY, tile.getY());
                        maxTileY = Math.max(maxTileY, tile.getY());
                    }
                    int minX = minTileX == Integer.MAX_VALUE ? 0 : minTileX * 128;
                    int maxX = maxTileX == Integer.MIN_VALUE ? 0 : (maxTileX + 1) * 128 - 1;
                    int minY = minTileY == Integer.MAX_VALUE ? 0 : minTileY * 128;
                    int maxY = maxTileY == Integer.MIN_VALUE ? 0 : (maxTileY + 1) * 128 - 1;
                    List<String> layers = new ArrayList<>();
                    for (Layer layer : appInstance.getAllLayers()) layers.add(layer.getName());
                    java.io.File file = appInstance.getCurrentWorldFile();
                    json.put("worldLoaded", true);
                    json.put("name", world.getName());
                    json.put("tileCount", dim.getTileCount());
                    json.put("bounds", Map.of("minX", minX, "maxX", maxX, "minY", minY, "maxY", maxY));
                    json.put("width", maxX - minX + 1);
                    json.put("height", maxY - minY + 1);
                    json.put("minHeight", dim.getMinHeight());
                    json.put("maxHeight", dim.getMaxHeight());
                    json.put("defaultWaterLevel", dim.getWaterLevelAt(0, 0));
                    json.put("layers", layers);
                    json.put("worldFile", file != null ? file.getAbsolutePath() : null);
                    json.put("dirty", appInstance.isWorldDirty());
                    return json;
                });
                sendJson(exchange, 200, result);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", String.valueOf(e.getMessage())));
            }
        }
    }

    private static final class InspectHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) return;
            try {
                Map<String, String> q = query(exchange);
                int minX = Integer.parseInt(q.getOrDefault("minX", "-256"));
                int minY = Integer.parseInt(q.getOrDefault("minY", "-256"));
                int maxX = Integer.parseInt(q.getOrDefault("maxX", "256"));
                int maxY = Integer.parseInt(q.getOrDefault("maxY", "256"));
                int step = Math.max(1, Integer.parseInt(q.getOrDefault("step", "8")));
                Map<String, Object> result = onEdt(() -> {
                    Dimension dim = requireDimension();
                    float minH = Float.MAX_VALUE, maxH = Float.MIN_VALUE;
                    double sumH = 0;
                    int count = 0, waterCount = 0;
                    List<List<Object>> grid = new ArrayList<>();
                    for (int y = minY; y <= maxY; y += step) {
                        List<Object> row = new ArrayList<>();
                        for (int x = minX; x <= maxX; x += step) {
                            float h = dim.getHeightAt(x, y);
                            int w = dim.getWaterLevelAt(x, y);
                            if (Float.isFinite(h)) {
                                minH = Math.min(minH, h);
                                maxH = Math.max(maxH, h);
                                sumH += h;
                                count++;
                                if (w > Math.round(h)) waterCount++;
                                row.add(Math.round(h * 10) / 10.0);
                            } else {
                                row.add(null);
                            }
                        }
                        grid.add(row);
                    }
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("minX", minX);
                    json.put("minY", minY);
                    json.put("maxX", maxX);
                    json.put("maxY", maxY);
                    json.put("step", step);
                    json.put("minHeight", count > 0 ? Math.round(minH * 10) / 10.0 : 0);
                    json.put("maxHeight", count > 0 ? Math.round(maxH * 10) / 10.0 : 0);
                    json.put("avgHeight", count > 0 ? Math.round((sumH / count) * 10) / 10.0 : 0);
                    json.put("samples", count);
                    json.put("waterCells", waterCount);
                    json.put("heightGrid", grid);
                    return json;
                });
                sendJson(exchange, 200, result);
            } catch (Exception e) {
                sendJson(exchange, 400, Map.of("error", String.valueOf(e.getMessage())));
            }
        }
    }

    private static final class RiverCarveHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            try {
                JsonNode body = readJson(exchange);
                List<DrawnRiverGraph.Pixel> points = new ArrayList<>();
                JsonNode arr = body.get("points");
                if (arr != null && arr.isArray()) {
                    for (JsonNode p : arr) {
                        points.add(new DrawnRiverGraph.Pixel(p.path("x").asInt(), p.path("y").asInt()));
                    }
                }
                if (points.size() < 2) {
                    sendJson(exchange, 400, Map.of("error", "At least 2 points required"));
                    return;
                }
                double width = body.path("width").asDouble(8.0);
                double depth = body.path("depth").asDouble(3.0);
                boolean smooth = body.path("smooth").asBoolean(true);
                boolean granite = body.path("granite").asBoolean(true);
                BridgeRiverOps.Mode mode = BridgeRiverOps.parseMode(textOrNull(body, "mode"));

                Map<String, Object> result = onEdt(() -> {
                    Dimension dim = requireDimension();
                    if (!dim.isUndoAvailable()) {
                        throw new IllegalStateException("Undo must be available");
                    }
                    dim.rememberChanges();
                    boolean ownsEvents = !dim.isEventsInhibited();
                    if (ownsEvents) dim.setEventsInhibited(true);
                    boolean committed = false;
                    try {
                        BridgeRiverOps.Result carved = BridgeRiverOps.carve(
                                dim, points, width, depth, smooth, granite, mode);
                        Map<String, Object> json = BridgeRiverOps.toJson(carved);
                        if (!carved.success()) {
                            try { dim.undoChanges(); } catch (RuntimeException ignored) {}
                            return json;
                        }
                        dim.armSavePoint();
                        committed = true;
                        return json;
                    } catch (RuntimeException failure) {
                        if (!committed) {
                            try { dim.undoChanges(); } catch (RuntimeException ignored) {}
                        }
                        throw failure;
                    } finally {
                        if (ownsEvents) dim.setEventsInhibited(false);
                    }
                });
                sendJson(exchange, result.get("success") == Boolean.TRUE ? 200 : 400, result);
            } catch (Exception e) {
                sendJson(exchange, 400, Map.of("error", String.valueOf(e.getMessage()), "success", false));
            }
        }
    }

    private static final class TerrainSculptHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            try {
                JsonNode body = readJson(exchange);
                int cx = body.path("x").asInt();
                int cy = body.path("y").asInt();
                int radius = body.path("radius").asInt(50);
                double amount = body.path("amount").asDouble(20.0);
                String mode = body.path("mode").asText("RAISE");

                Map<String, Object> result = onEdt(() -> {
                    Dimension dim = requireDimension();
                    dim.rememberChanges();
                    boolean ownsEvents = !dim.isEventsInhibited();
                    if (ownsEvents) dim.setEventsInhibited(true);
                    try {
                        int modified = BridgeTerrainOps.sculpt(dim, cx, cy, radius, amount, mode);
                        dim.armSavePoint();
                        return Map.<String, Object>of("success", true, "modifiedCells", modified);
                    } catch (RuntimeException failure) {
                        try { dim.undoChanges(); } catch (RuntimeException ignored) {}
                        throw failure;
                    } finally {
                        if (ownsEvents) dim.setEventsInhibited(false);
                    }
                });
                sendJson(exchange, 200, result);
            } catch (Exception e) {
                sendJson(exchange, 400, Map.of("error", String.valueOf(e.getMessage()), "success", false));
            }
        }
    }

    private static final class TerrainPaintHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            try {
                JsonNode body = readJson(exchange);
                String terrainName = textOrNull(body, "terrain");
                String layerName = textOrNull(body, "layer");
                int minX = body.path("minX").asInt();
                int maxX = body.path("maxX").asInt();
                int minY = body.path("minY").asInt();
                int maxY = body.path("maxY").asInt();
                double minSlopeDeg = body.path("minSlope").asDouble(0);
                double maxSlopeDeg = body.path("maxSlope").asDouble(90);

                Map<String, Object> result = onEdt(() -> {
                    Dimension dim = requireDimension();
                    double minHeight = body.has("minHeight") ? body.get("minHeight").asDouble() : dim.getMinHeight();
                    double maxHeight = body.has("maxHeight") ? body.get("maxHeight").asDouble() : dim.getMaxHeight();

                    Terrain targetTerrain = null;
                    if (terrainName != null) {
                        for (Terrain t : Terrain.VALUES) {
                            if (t.getName().equalsIgnoreCase(terrainName) || t.name().equalsIgnoreCase(terrainName)) {
                                targetTerrain = t;
                                break;
                            }
                        }
                        if (targetTerrain == null) throw new IllegalArgumentException("Unknown terrain: " + terrainName);
                    }
                    Layer targetLayer = null;
                    if (layerName != null) {
                        for (Layer l : appInstance.getAllLayers()) {
                            if (l.getName().equalsIgnoreCase(layerName)) {
                                targetLayer = l;
                                break;
                            }
                        }
                        if (targetLayer == null) throw new IllegalArgumentException("Unknown layer: " + layerName);
                    }
                    if (targetTerrain == null && targetLayer == null) {
                        throw new IllegalArgumentException("terrain or layer required");
                    }

                    dim.rememberChanges();
                    boolean ownsEvents = !dim.isEventsInhibited();
                    if (ownsEvents) dim.setEventsInhibited(true);
                    try {
                        int modified = BridgeTerrainOps.paint(dim, minX, maxX, minY, maxY,
                                targetTerrain, targetLayer, minHeight, maxHeight, minSlopeDeg, maxSlopeDeg);
                        dim.armSavePoint();
                        return Map.<String, Object>of("success", true, "paintedCells", modified);
                    } catch (RuntimeException failure) {
                        try { dim.undoChanges(); } catch (RuntimeException ignored) {}
                        throw failure;
                    } finally {
                        if (ownsEvents) dim.setEventsInhibited(false);
                    }
                });
                sendJson(exchange, 200, result);
            } catch (Exception e) {
                sendJson(exchange, 400, Map.of("error", String.valueOf(e.getMessage()), "success", false));
            }
        }
    }

    private static final class ScriptHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            try {
                JsonNode body = readJson(exchange);
                String code = body.path("code").asText("");
                if (code.length() > BridgeTerrainOps.MAX_SCRIPT_CHARS) {
                    sendJson(exchange, 400, Map.of("error", "Script exceeds " + BridgeTerrainOps.MAX_SCRIPT_CHARS + " chars"));
                    return;
                }
                Map<String, Object> result = onEdt(() -> {
                    Dimension dim = appInstance != null ? appInstance.getDimension() : null;
                    World2 world = appInstance != null ? appInstance.getWorld() : null;
                    ScriptEngineManager manager = new ScriptEngineManager();
                    ScriptEngine engine = manager.getEngineByName("nashorn");
                    if (engine == null) engine = manager.getEngineByName("JavaScript");
                    if (engine == null) throw new IllegalStateException("No JavaScript engine available");
                    engine.put("app", appInstance);
                    engine.put("world", world);
                    engine.put("dimension", dim);
                    if (dim != null) dim.rememberChanges();
                    try {
                        Object evalResult = engine.eval(code);
                        if (dim != null) dim.armSavePoint();
                        return Map.<String, Object>of("success", true, "result", evalResult != null ? evalResult.toString() : "ok");
                    } catch (Exception failure) {
                        if (dim != null) {
                            try { dim.undoChanges(); } catch (RuntimeException ignored) {}
                        }
                        throw new IllegalStateException(failure.getMessage(), failure);
                    }
                });
                sendJson(exchange, 200, result);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", String.valueOf(e.getMessage()), "success", false));
            }
        }
    }

    private static final class UndoHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) return;
            try {
                Boolean ok = onEdt(() -> appInstance != null && appInstance.performUndo());
                sendJson(exchange, 200, Map.of("success", true, "undone", ok));
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", String.valueOf(e.getMessage())));
            }
        }
    }

    private static final class RedoHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) return;
            try {
                Boolean ok = onEdt(() -> appInstance != null && appInstance.performRedo());
                sendJson(exchange, 200, Map.of("success", true, "redone", ok));
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", String.valueOf(e.getMessage())));
            }
        }
    }

    private static final class SaveHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            try {
                Map<String, Object> result = onEdt(() -> {
                    if (appInstance == null) throw new IllegalStateException("App not initialized");
                    if (appInstance.getWorld() == null) throw new IllegalStateException("No world loaded");
                    if (appInstance.getCurrentWorldFile() == null) {
                        throw new IllegalStateException("No known world file; save once from the UI first");
                    }
                    boolean ok = appInstance.saveCurrentWorld();
                    Map<String, Object> json = new LinkedHashMap<>();
                    json.put("success", ok);
                    json.put("worldFile", appInstance.getCurrentWorldFile().getAbsolutePath());
                    if (!ok) json.put("error", "Save failed");
                    return json;
                });
                sendJson(exchange, result.get("success") == Boolean.TRUE ? 200 : 500, result);
            } catch (Exception e) {
                sendJson(exchange, 400, Map.of("error", String.valueOf(e.getMessage()), "success", false));
            }
        }
    }

    private static String textOrNull(JsonNode body, String field) {
        JsonNode n = body.get(field);
        if (n == null || n.isNull()) return null;
        String t = n.asText();
        return t == null || t.isBlank() ? null : t;
    }
}
