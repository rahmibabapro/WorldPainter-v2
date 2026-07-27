//-- get info on delimiter here: https://github.com/Captain-Chaos/WorldPainter/blob/219f7eb1402e49d9c79fed72799c82503385d669/WorldPainter/WPGUI/src/test/resources/descriptortest.js

// script.description=Global terrain operation: paint Stone Mix on slopes above the threshold and grass on slopes below (relative to the vertical/Y axis). Stone Mix uses stone (not deepslate) at all heights including below y=0.

// script.name= Stone Mix / Grass Slope Split

// script.param.slopeAngleDeg.type=float
// script.param.slopeAngleDeg.description=Terrain slope threshold in degrees. Areas above this get Stone Mix; areas below get grass. 45 means a 1:1 height change over horizontal distance.
// script.param.slopeAngleDeg.displayName=Slope threshold (deg)
// script.param.slopeAngleDeg.default=45
// script.param.slopeAngleDeg.optional=false

var dim = (typeof dimension !== 'undefined' && dimension != null)
        ? dimension
        : org.pepsoft.worldpainter.App.getInstance().getDimension();

var slopeAngleDeg = params['slopeAngleDeg'];
if (slopeAngleDeg == null) {
    slopeAngleDeg = params['splitAngleDeg'];
}
if (slopeAngleDeg == null) {
    slopeAngleDeg = 45;
}

var slopeThreshold = Math.tan(slopeAngleDeg * Math.PI / 180.0);

var terrainMap = buildTerrainMap();
var stoneMixTerrain = resolveTerrain(terrainMap, ["stonemix", "stone mix"]);
var grassTerrain = resolveTerrain(terrainMap, ["grass", "grassland", "lushgrass"]);

if (stoneMixTerrain == null) {
    throw "Could not find Stone Mix terrain in this WorldPainter version.";
}
if (grassTerrain == null) {
    throw "Could not find a grass terrain in this WorldPainter version.";
}

var processed = 0;
var stoneMixCount = 0;
var grassCount = 0;
var totalBlocks = dim.getWidth() * dim.getHeight() * 128 * 128;

print("Starting stone/grass slope split...");
print("Slope threshold: " + slopeAngleDeg + " deg (above = Stone Mix, below = grass)");

function getTerrainSlopeAt(x, y) {
    if (typeof scriptDimension !== 'undefined' && scriptDimension != null) {
        return scriptDimension.getSlopeAt(x, y);
    }
    if (typeof dim.getSlopeAt === 'function') {
        return dim.getSlopeAt(x, y);
    }
    return dim.getSlope(x, y);
}

function processTile(tile) {
    var worldTileX = tile.getX() * 128;
    var worldTileY = tile.getY() * 128;
    for (var lx = 0; lx < 128; lx++) {
        for (var ly = 0; ly < 128; ly++) {
            var x = worldTileX + lx;
            var y = worldTileY + ly;
            var slope = getTerrainSlopeAt(x, y);
            if (slope >= slopeThreshold) {
                tile.setTerrain(lx, ly, stoneMixTerrain);
                stoneMixCount++;
            } else {
                tile.setTerrain(lx, ly, grassTerrain);
                grassCount++;
            }
            processed++;
        }
    }
    if (typeof progress !== 'undefined' && progress != null) {
        progress.setProgress(processed / Math.max(1, totalBlocks));
        progress.checkForCancel();
    }
}

if (typeof scriptDimension !== 'undefined' && scriptDimension != null) {
    scriptDimension.visitTilesForEditing(processTile);
} else {
    var extent = dim.getExtent();
    var minTileX = dim.getLowestX();
    var minTileY = dim.getLowestY();
    for (var tx = 0; tx < extent.getWidth(); tx++) {
        for (var ty = 0; ty < extent.getHeight(); ty++) {
            var tile = dim.getTile(minTileX + tx, minTileY + ty);
            if (tile != null) {
                processTile(tile);
            }
        }
    }
}

print("Done. Stone Mix on " + stoneMixCount + " steep cells (>= " + slopeAngleDeg + " deg); grass on " + grassCount + " gentle cells.");

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
