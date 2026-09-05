// script.name= Stone Mix / Grass Slope Split
// script.description=Paint Stone Mix on slopes at or above the threshold and grass below it. Stone Mix uses stone (not deepslate) at all heights. Protected, absent and lava cells are preserved.
// script.param.slopeAngleDeg.type=float
// script.param.slopeAngleDeg.description=Terrain slope threshold in degrees from horizontal. 45 means a 1:1 height change over horizontal distance.
// script.param.slopeAngleDeg.displayName=Slope threshold (deg)
// script.param.slopeAngleDeg.default=45
// script.param.slopeAngleDeg.optional=false

var dim = (typeof dimension !== 'undefined' && dimension != null)
        ? dimension : org.pepsoft.worldpainter.App.getInstance().getDimension();
if (dim == null) { throw "Open a dimension before running this operation."; }
var ReadOnly = Java.type('org.pepsoft.worldpainter.layers.ReadOnly').INSTANCE;
var VoidLayer = Java.type('org.pepsoft.worldpainter.layers.Void').INSTANCE;
var NotPresent = Java.type('org.pepsoft.worldpainter.layers.NotPresent').INSTANCE;
var NotPresentBlock = Java.type('org.pepsoft.worldpainter.layers.NotPresentBlock').INSTANCE;
var FloodWithLava = Java.type('org.pepsoft.worldpainter.layers.FloodWithLava').INSTANCE;
var totalBlocks = dim.getTiles().size() * 128 * 128;
var processed = 0;

function hasSurface(tile, x, y) {
    return tile != null && isFinite(tile.getHeight(x, y))
            && !tile.getBitLayerValue(VoidLayer, x, y)
            && !tile.getBitLayerValue(NotPresent, x, y)
            && !tile.getBitLayerValue(NotPresentBlock, x, y);
}
function editable(tile, x, y) {
    return hasSurface(tile, x, y) && !tile.getBitLayerValue(ReadOnly, x, y)
            && !tile.getBitLayerValue(FloodWithLava, x, y);
}
function reportProgress() {
    if (typeof progress !== 'undefined' && progress != null) {
        progress.checkForCancel();
        progress.setProgress(processed / Math.max(1, totalBlocks));
    }
}
function visitPresentTiles(operation) {
    reportProgress();
    var tiles = dim.getTiles().iterator();
    while (tiles.hasNext()) {
        var source = tiles.next();
        var tile = dim.getTileForEditing(source.getX(), source.getY());
        if (tile == null) { continue; }
        for (var lx = 0; lx < 128; lx++) {
            if ((lx & 7) === 0) { reportProgress(); }
            for (var ly = 0; ly < 128; ly++) {
                if (editable(tile, lx, ly)) { operation(tile, lx, ly); }
                processed++;
            }
        }
    }
    reportProgress();
}

var slopeAngleDeg = params['slopeAngleDeg'];
if (slopeAngleDeg == null) { slopeAngleDeg = params['splitAngleDeg']; }
if (slopeAngleDeg == null) { slopeAngleDeg = 45; }
slopeAngleDeg = Number(slopeAngleDeg);
if (!isFinite(slopeAngleDeg) || slopeAngleDeg < 0 || slopeAngleDeg > 90) {
    throw "Slope threshold must be a finite number between 0 and 90 degrees.";
}
var slopeThreshold = Math.tan(slopeAngleDeg * Math.PI / 180.0);

function buildTerrainMap() {
    var map = {};
    var terrains = org.pepsoft.worldpainter.Terrain.VALUES;
    for (var i = 0; i < terrains.length; i++) {
        map[String(terrains[i]).replace(/\s+/g, "").toLowerCase()] = terrains[i];
        map[String(terrains[i].getName()).replace(/\s+/g, "").toLowerCase()] = terrains[i];
    }
    return map;
}
function resolveTerrain(map, aliases) {
    for (var i = 0; i < aliases.length; i++) {
        var terrain = map[aliases[i].replace(/\s+/g, "").toLowerCase()];
        if (terrain != null) { return terrain; }
    }
    return null;
}

var terrainMap = buildTerrainMap();
var stoneMixTerrain = resolveTerrain(terrainMap, ["stonemix", "stone mix"]);
var grassTerrain = resolveTerrain(terrainMap, ["grass", "grassland", "lushgrass"]);
if (stoneMixTerrain == null || grassTerrain == null) {
    throw "Could not find Stone Mix and grass terrains in this WorldPainter version.";
}
function heightOrCenter(x, y, center) {
    var tile = dim.getTile(Math.floor(x / 128), Math.floor(y / 128));
    var lx = ((x % 128) + 128) % 128, ly = ((y % 128) + 128) % 128;
    return hasSurface(tile, lx, ly) ? tile.getHeight(lx, ly) : center;
}
function slopeAt(tile, lx, ly) {
    // Retain WorldPainter's max-of-four-directions slope; prevent missing
    // neighbours from becoming a fictitious cliff at tile/no-data edges.
    if (lx > 0 && lx < 127 && ly > 0 && ly < 127
            && !tile.hasLayer(VoidLayer) && !tile.hasLayer(NotPresent) && !tile.hasLayer(NotPresentBlock)) {
        return tile.getSlope(lx, ly);
    }
    var x = tile.getX() * 128 + lx, y = tile.getY() * 128 + ly;
    var h = tile.getHeight(lx, ly);
    return Math.max(
        Math.abs(heightOrCenter(x + 1, y, h) - heightOrCenter(x - 1, y, h)) / 2,
        Math.abs(heightOrCenter(x, y + 1, h) - heightOrCenter(x, y - 1, h)) / 2,
        Math.abs(heightOrCenter(x + 1, y + 1, h) - heightOrCenter(x - 1, y - 1, h)) / Math.sqrt(8),
        Math.abs(heightOrCenter(x - 1, y + 1, h) - heightOrCenter(x + 1, y - 1, h)) / Math.sqrt(8));
}
var stoneMixCount = 0, grassCount = 0;
print("Starting stone/grass slope split at " + slopeAngleDeg + " degrees...");
visitPresentTiles(function (tile, x, y) {
    var slope = slopeAt(tile, x, y);
    if (!isFinite(slope)) { return; }
    if (slope >= slopeThreshold) {
        tile.setTerrain(x, y, stoneMixTerrain);
        stoneMixCount++;
    } else {
        tile.setTerrain(x, y, grassTerrain);
        grassCount++;
    }
});
print("Done. Stone Mix on " + stoneMixCount + " steep cells; grass on " + grassCount + " gentle cells.");
