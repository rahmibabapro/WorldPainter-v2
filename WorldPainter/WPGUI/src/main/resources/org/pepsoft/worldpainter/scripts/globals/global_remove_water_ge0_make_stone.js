//-- get info on delimiter here: https://github.com/Captain-Chaos/WorldPainter/blob/219f7eb1402e49d9c79fed72799c82503385d669/WorldPainter/WPGUI/src/test/resources/descriptortest.js

// script.name= Global Remove Water>=0 + Stone Fill
// script.description=Removes all water with water level >= 0, then sets terrain to stone for the whole current dimension.

var app = org.pepsoft.worldpainter.App.getInstance();
var dimension = app.dimension;
var extent = dimension.getExtent();
var minTileX = dimension.getLowestX();
var minTileY = dimension.getLowestY();
var tileWidth = extent.getWidth();
var tileHeight = extent.getHeight();

var terrainMap = buildTerrainMap();
var stoneTerrain = resolveTerrain(terrainMap, ["stone", "rock", "barerock"]);

if (stoneTerrain == null) {
    throw "Could not find a stone/rock terrain in this WorldPainter version.";
}

var startTime = new Date().getTime();
var total = tileWidth * tileHeight * 128 * 128;
var processed = 0;
var driedCount = 0;
var nextLog = Math.max(10000, parseInt(total / 20));

print("Starting global operation...");
print("Step 1: remove water where water level >= 0");
print("Step 2: set entire terrain to stone");

for (var tx = 0; tx < tileWidth; tx++) {
    for (var ty = 0; ty < tileHeight; ty++) {
        var tileX = minTileX + tx;
        var tileY = minTileY + ty;

        for (var lx = 0; lx < 128; lx++) {
            for (var ly = 0; ly < 128; ly++) {
                var x = tileX * 128 + lx;
                var y = tileY * 128 + ly;
                var wl = dimension.getWaterLevelAt(x, y);

                if (wl >= 0) {
                    dimension.setWaterLevelAt(x, y, -1);
                    driedCount++;
                }

                dimension.setTerrainAt(x, y, stoneTerrain);

                processed++;
                if (processed >= nextLog) {
                    var elapsedMs = new Date().getTime() - startTime;
                    var progress = processed / total;
                    var etaMs = progress > 0 ? (elapsedMs / progress) - elapsedMs : 0;
                    print("Progress: " + parseInt(progress * 100) + "%  (" + processed + "/" + total + ")  ETA: " + formatDurationShort(etaMs));
                    nextLog += Math.max(10000, parseInt(total / 20));
                }
            }
        }
    }
}

print("Done.");
print("Dried water cells: " + driedCount);
print("Terrain set to stone on all cells in current dimension.");

function buildTerrainMap() {
    var map = {};
    var terrains = org.pepsoft.worldpainter.Terrain.VALUES;
    for (var i = 0; i < terrains.length; i++) {
        var terrain = terrains[i];
        var name1 = terrain.toString().replace(/\s+/g, "").toLowerCase();
        var name2 = terrain.getName().replace(/\s+/g, "").toLowerCase();
        map[name1] = terrain;
        map[name2] = terrain;
    }
    return map;
}

function resolveTerrain(map, aliases) {
    for (var i = 0; i < aliases.length; i++) {
        var key = aliases[i].replace(/\s+/g, "").toLowerCase();
        if (map[key] != null) {
            return map[key];
        }
    }
    return null;
}

function formatDurationShort(ms) {
    if (ms == null || ms < 0 || !isFinite(ms)) {
        return "?";
    }
    var totalSec = parseInt(ms / 1000);
    var min = parseInt(totalSec / 60);
    var sec = totalSec % 60;
    if (min > 0) {
        return min + "m " + sec + "s";
    }
    return sec + "s";
}
