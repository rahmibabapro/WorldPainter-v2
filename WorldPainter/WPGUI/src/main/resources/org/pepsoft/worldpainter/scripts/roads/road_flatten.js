//-- get info on delimiter here: https://github.com/Captain-Chaos/WorldPainter/blob/219f7eb1402e49d9c79fed72799c82503385d669/WorldPainter/WPGUI/src/test/resources/descriptortest.js

// script.name= Road Flatten 2.1         --by sijmen_v_b 
// script.description= Flattens out roads based on a mask of a center line.\n\nHow to use:\n   1. set a distance\n   2. use ONLY 1 of the 3 filters. (it only takes take the first non-empty one)\n----------------------------------------------------------------------------------------------------------\nSlabify is now (optionally) integrated. for instructions see:\nhttps://youtu.be/d7rEcpH_YLw?si=EwOohPHpcTq1TF4H
// script.param.distance.type=integer
// script.param.distance.description= the number of blocks the road is wide from the center line (radius)
// script.param.distance.displayName=Distance [values over 10 may cause slowdown]
// script.param.distance.default=4

// script.param.layerMask.type=string
// script.param.layerMask.description= the name of layer where you want the script to be applied
// script.param.layerMask.displayName=Layer mask                      (choose one of these)
// script.param.layerMask.optional=true
// script.param.layerMask.default=

// script.param.terrainMask.type=string
// script.param.terrainMask.description= the name of terrain where you want the script to be applied
// script.param.terrainMask.displayName=Terrain mask                    (choose one of these)
// script.param.terrainMask.optional=true
// script.param.terrainMask.default=

// script.param.fileMask.type=string
// script.param.fileMask.description= the file-path pointing to a mask
// script.param.fileMask.displayName=file-path to mask.png      (choose one of these)
// script.param.fileMask.optional=true
// script.param.fileMask.default=

// script.param.roadLayer.type=string
// script.param.roadLayer.description= will apply this layer on the entire road.
// script.param.roadLayer.displayName=Optional: Apply layer to roads
// script.param.roadLayer.optional=true
// script.param.roadLayer.default=

// script.param.roadSlab.type=string
// script.param.roadSlab.description=integrates Slabify.
// script.param.roadSlab.displayName=Optional: Slabify road layer
// script.param.roadSlab.optional=true
// script.param.roadSlab.default=

// script.hideCmdLineParams=true

var useThickSlabs = false; // Retain the original thin-edge Slabify default.
var dim = (typeof dimension !== 'undefined' && dimension != null)
        ? dimension : org.pepsoft.worldpainter.App.getInstance().getDimension();
if (dim == null) { throw "Open a dimension before flattening roads."; }
var ReadOnly = Java.type('org.pepsoft.worldpainter.layers.ReadOnly').INSTANCE;
var VoidLayer = Java.type('org.pepsoft.worldpainter.layers.Void').INSTANCE;
var NotPresent = Java.type('org.pepsoft.worldpainter.layers.NotPresent').INSTANCE;
var NotPresentBlock = Java.type('org.pepsoft.worldpainter.layers.NotPresentBlock').INSTANCE;
var FloodWithLava = Java.type('org.pepsoft.worldpainter.layers.FloodWithLava').INSTANCE;
var HashMap = Java.type('java.util.HashMap');
var terrainMap = new HashMap(), layerMap = new HashMap();
var terrainEnum = org.pepsoft.worldpainter.Terrain.VALUES;
for (var i = 0; i < terrainEnum.length; i++) {
    terrainMap.put(normalize(terrainEnum[i]), terrainEnum[i]);
    terrainMap.put(normalize(terrainEnum[i].getName()), terrainEnum[i]);
}
var app = org.pepsoft.worldpainter.App.getInstanceIfExists();
var layers = app == null ? dim.getAllLayers(false) : app.getAllLayers();
var layerIterator = layers.iterator();
while (layerIterator.hasNext()) {
    var layer = layerIterator.next();
    layerMap.put(normalize(layer.getName()), layer);
}

// The host saves parameter values; never rewrite a bundled script on disk.
var distanceValue = params['distance'];
var distance = distanceValue == null ? 4 : Number(distanceValue);
if (!isFinite(distance) || distance < 0 || Math.floor(distance) !== distance || distance > 2147483647
        || (distanceValue != null && String(distanceValue).trim() === '')) {
    throw "Distance must be a non-negative whole number no larger than 2147483647.";
}
var layerMask = optionalText(params['layerMask']);
var terrainMask = optionalText(params['terrainMask']);
var fileMask = optionalText(params['fileMask']);
var roadLayer = resolveLayer(optionalText(params['roadLayer']), "Road layer", true);
var roadSlab = resolveLayer(optionalText(params['roadSlab']), "Road slab layer", true);
var maskLayer = null, maskTerrain = null, heightMap = null, halfway = 0;
if (layerMask != null) {
    maskLayer = resolveLayer(layerMask, "Layer mask", false);
} else if (terrainMask != null) {
    maskTerrain = terrainMap.get(normalize(terrainMask));
    if (maskTerrain == null || (maskTerrain.isCustom() && !org.pepsoft.worldpainter.Terrain.isCustomMaterialConfigured(maskTerrain.getCustomTerrainIndex()))) {
        throw "Unknown or unconfigured terrain mask: " + terrainMask;
    }
} else if (fileMask != null) {
    if (fileMask.charAt(0) === '"' && fileMask.charAt(fileMask.length - 1) === '"') {
        fileMask = fileMask.slice(1, -1);
    }
    heightMap = wp.getHeightMap().fromFile(fileMask).go();
    var range = heightMap.getRange();
    if (range == null || !isFinite(range[1])) { throw "The file mask has an invalid value range."; }
    halfway = range[1] > 256 ? 32767 : 127;
} else {
    throw "Choose a layer, terrain or file mask before flattening roads.";
}

var totalCells = dim.getTiles().size() * 16384;
var scanned = 0, candidateChecks = 0, counter = 0, fraction = 0;
var pointsToBeFlattened = new HashMap();
var runtime = Java.type('java.lang.Runtime').getRuntime();
var availableHeap = Math.max(0, runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()));
// Planning completes before any writes. Reserve most heap for the world/undo;
// sum/count avoids retaining every overlapping source height for each target.
var maxPlannedPoints = Math.max(1, Math.min(1000000, Math.floor(availableHeap * 0.15 / 512)));
var maxCandidateChecks = 100000000;
var minX = dim.getLowestX() * 128, minY = dim.getLowestY() * 128;
var maxX = (dim.getHighestX() + 1) * 128 - 1, maxY = (dim.getHighestY() + 1) * 128 - 1;
checkProgress(0);
print("Gathering road heights from present, editable terrain...");
var tileIterator = dim.getTiles().iterator();
while (tileIterator.hasNext()) {
    var tile = tileIterator.next();
    for (var lx = 0; lx < 128; lx++) {
        if ((lx & 7) === 0) { checkProgress(0.55 * scanned / Math.max(1, totalCells)); }
        for (var ly = 0; ly < 128; ly++) {
            scanned++;
            if (!editable(tile, lx, ly)) { continue; }
            var x = tile.getX() * 128 + lx, y = tile.getY() * 128 + ly;
            if (selected(tile, lx, ly, x, y)) { gather(x, y, tile.getHeight(lx, ly)); }
        }
    }
}
checkProgress(0.55);
var pointsToBeFlattenedLength = pointsToBeFlattened.size();
print("Flattening " + pointsToBeFlattenedLength + " road cells...");
var points = pointsToBeFlattened.values().iterator();
while (points.hasNext()) {
    if ((counter & 255) === 0) { checkProgress(0.55 + 0.35 * counter / Math.max(1, pointsToBeFlattenedLength)); }
    var point = points.next();
    var target = editableAt(point.x, point.y);
    if (target != null) {
        // Same arithmetic mean as the original per-point list of source heights.
        dim.setHeightAt(point.x, point.y, point.sum / point.count);
        applyLayer(roadLayer, point.x, point.y);
    }
    counter++;
}
checkProgress(0.90);
if (roadSlab != null) {
    counter = 0;
    points = pointsToBeFlattened.values().iterator();
    while (points.hasNext()) {
        if ((counter & 255) === 0) { checkProgress(0.90 + 0.10 * counter / Math.max(1, pointsToBeFlattenedLength)); }
        var point = points.next();
        if (editableAt(point.x, point.y) != null && (thinLower(point.x, point.y)
                || (useThickSlabs && thickLower(point.x, point.y)))) {
            applyLayer(roadSlab, point.x, point.y);
        }
        counter++;
    }
}
checkProgress(1);
print("Done. Flattened " + pointsToBeFlattenedLength + " cells. Original script by sijmen_v_b.");

function optionalText(value) {
    if (value == null) { return null; }
    var text = String(value).trim();
    return text.length === 0 ? null : text;
}
function normalize(value) { return String(value).replace(/\s+/g, "").toLowerCase(); }
function resolveLayer(name, description, output) {
    if (name == null) { return null; }
    var layer = layerMap.get(normalize(name));
    if (layer == null) { throw description + " was not found: " + name; }
    var size = String(layer.getDataSize());
    if (size !== 'BIT' && size !== 'BIT_PER_CHUNK' && size !== 'NIBBLE' && size !== 'BYTE') {
        throw description + " is not a paintable layer: " + name;
    }
    if (output && (size === 'BIT_PER_CHUNK' || layer === ReadOnly || layer === VoidLayer
            || layer === NotPresent || layer === NotPresentBlock || layer === FloodWithLava)) {
        throw description + " must be a per-block road layer, not a protected/no-data layer: " + name;
    }
    return layer;
}
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
function editableAt(x, y) {
    var tile = dim.getTile(Math.floor(x / 128), Math.floor(y / 128));
    return editable(tile, ((x % 128) + 128) % 128, ((y % 128) + 128) % 128) ? tile : null;
}
function selected(tile, lx, ly, x, y) {
    if (maskLayer != null) {
        var size = String(maskLayer.getDataSize());
        return (size === 'BIT' || size === 'BIT_PER_CHUNK')
                ? tile.getBitLayerValue(maskLayer, lx, ly) : tile.getLayerValue(maskLayer, lx, ly) > 0;
    }
    if (maskTerrain != null) { return tile.getTerrain(lx, ly) === maskTerrain; }
    var value = heightMap.getHeight(x, y);
    return isFinite(value) && value > halfway;
}
function checkProgress(value) {
    fraction = value;
    if (typeof progress !== 'undefined' && progress != null) {
        progress.checkForCancel();
        progress.setProgress(value);
    }
}
function gather(x, y, height) {
    var lowX = Math.max(minX, x - distance), highX = Math.min(maxX, x + distance);
    var lowY = Math.max(minY, y - distance), highY = Math.min(maxY, y + distance);
    for (var dx = lowX; dx <= highX; dx++) {
        for (var dy = lowY; dy <= highY; dy++) {
            if ((++candidateChecks & 1023) === 0) { checkProgress(fraction); }
            if (candidateChecks > maxCandidateChecks) {
                throw "Road mask/radius exceeds the safe planning work budget. Use a thinner centerline mask or smaller distance; no road changes were applied.";
            }
            var offsetX = dx - x, offsetY = dy - y;
            if (offsetX * offsetX + offsetY * offsetY > distance * distance || editableAt(dx, dy) == null) { continue; }
            var key = dx + "," + dy;
            var point = pointsToBeFlattened.get(key);
            if (point == null) {
                if (pointsToBeFlattened.size() >= maxPlannedPoints) {
                    throw "Road mask exceeds the available-memory planning budget (" + maxPlannedPoints
                            + " cells). Use a smaller mask; no road changes were applied.";
                }
                point = {x: dx, y: dy, sum: 0, count: 0};
                pointsToBeFlattened.put(key, point);
            }
            point.sum += height;
            point.count++;
        }
    }
}
function applyLayer(layer, x, y) {
    if (layer == null) { return; }
    if (String(layer.getDataSize()) === 'BIT') { dim.setBitLayerValueAt(layer, x, y, true); }
    else { dim.setLayerValueAt(layer, x, y, 8); }
}
function heightOrCenter(x, y, center) {
    var tile = dim.getTile(Math.floor(x / 128), Math.floor(y / 128));
    var lx = ((x % 128) + 128) % 128, ly = ((y % 128) + 128) % 128;
    return hasSurface(tile, lx, ly) ? tile.getHeight(lx, ly) : center;
}
function lowerNeighbour(x, y, dx, dy) {
    var height = dim.getHeightAt(x, y);
    // Keep the legacy Slabify rounding, including negative heights.
    return parseInt(heightOrCenter(x + dx, y + dy, height) - 0.5) === parseInt(height - 0.5) - 1;
}
function thinLower(x, y) {
    return lowerNeighbour(x, y, 1, 0) || lowerNeighbour(x, y, -1, 0)
            || lowerNeighbour(x, y, 0, 1) || lowerNeighbour(x, y, 0, -1);
}
function thickLower(x, y) {
    return lowerNeighbour(x, y, 1, -1) || lowerNeighbour(x, y, -1, 1)
            || lowerNeighbour(x, y, 1, 1) || lowerNeighbour(x, y, -1, -1);
}


