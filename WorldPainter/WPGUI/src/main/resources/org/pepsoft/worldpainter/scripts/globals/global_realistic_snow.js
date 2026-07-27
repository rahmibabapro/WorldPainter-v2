//-- get info on delimiter here: https://github.com/Captain-Chaos/WorldPainter/blob/219f7eb1402e49d9c79fed72799c82503385d669/WorldPainter/WPGUI/src/test/resources/descriptortest.js

// script.name= Global Realistic Snow
// script.description=Adds realistic snow coverage based on height, slope and water proximity. Uses Snow/Frost layer if available and can optionally paint snowy biome ids.

// script.param.snowLineHeight.type=float
// script.param.snowLineHeight.description=Base height where snow begins to appear.
// script.param.snowLineHeight.displayName=Snow line height
// script.param.snowLineHeight.default=90
// script.param.snowLineHeight.optional=false

// script.param.fullSnowHeight.type=float
// script.param.fullSnowHeight.description=Height where snow coverage becomes nearly complete.
// script.param.fullSnowHeight.displayName=Full snow height
// script.param.fullSnowHeight.default=120
// script.param.fullSnowHeight.optional=false

// script.param.slopeLimit.type=float
// script.param.slopeLimit.description=Maximum local slope for snow accumulation. Higher values mean snow can stay on steeper terrain.
// script.param.slopeLimit.displayName=Slope limit
// script.param.slopeLimit.default=4.0
// script.param.slopeLimit.optional=false

// script.param.waterBuffer.type=float
// script.param.waterBuffer.description=Extra height buffer above water level required for snow to appear.
// script.param.waterBuffer.displayName=Water buffer
// script.param.waterBuffer.default=2.5
// script.param.waterBuffer.optional=false

// script.param.useBiomeLayer.type=boolean
// script.param.useBiomeLayer.description=Also set snowy biomes where snow is placed, if the Biome layer is available.
// script.param.useBiomeLayer.displayName=Paint snowy biomes
// script.param.useBiomeLayer.default=true

// script.param.snowBiomeId.type=integer
// script.param.snowBiomeId.description=Biome id used when painting snowy biomes.
// script.param.snowBiomeId.displayName=Snow biome id
// script.param.snowBiomeId.default=12
// script.param.snowBiomeId.optional=false

// script.param.clearLowSnow.type=boolean
// script.param.clearLowSnow.description=Removes snow layer below the snow line instead of leaving old snow behind.
// script.param.clearLowSnow.displayName=Clear low snow
// script.param.clearLowSnow.default=true

var app = org.pepsoft.worldpainter.App.getInstance();
var dimension = app.dimension;
var extent = dimension.getExtent();
var minX = dimension.getLowestX() * 128;
var minY = dimension.getLowestY() * 128;
var worldWidth = extent.getWidth() * 128;
var worldHeight = extent.getHeight() * 128;

var snowLineHeight = params['snowLineHeight'];
var fullSnowHeight = params['fullSnowHeight'];
var slopeLimit = params['slopeLimit'];
var waterBuffer = params['waterBuffer'];
var useBiomeLayer = params['useBiomeLayer'];
var snowBiomeId = params['snowBiomeId'];
var clearLowSnow = params['clearLowSnow'];

if (snowLineHeight == null) {
    snowLineHeight = 90;
}
if (fullSnowHeight == null) {
    fullSnowHeight = 120;
}
if (slopeLimit == null) {
    slopeLimit = 4.0;
}
if (waterBuffer == null) {
    waterBuffer = 2.5;
}
if (useBiomeLayer == null) {
    useBiomeLayer = true;
}
if (snowBiomeId == null) {
    snowBiomeId = 12;
}
if (clearLowSnow == null) {
    clearLowSnow = true;
}

if (fullSnowHeight <= snowLineHeight) {
    fullSnowHeight = snowLineHeight + 1;
}

var layers = app.getAllLayers();
var layerMap = {};
for (var i = 0; i < layers.length; i++) {
    var key = layers[i].getName().replace(/\s+/g, '').toLowerCase();
    layerMap[key] = layers[i];
}

var snowLayer = layerMap['snow'] || layerMap['frost'];
var biomeLayer = org.pepsoft.worldpainter.layers.Biome.INSTANCE;
var terrainMap = buildTerrainMap();

var total = worldWidth * worldHeight;
var processed = 0;
var changed = 0;
var nextLog = Math.max(10000, parseInt(total / 20));
var startTime = new Date().getTime();

print("Starting realistic snow pass...");
print("Snow line: " + snowLineHeight + "  Full snow: " + fullSnowHeight + "  Slope limit: " + slopeLimit);
print("Snow layer found: " + (snowLayer != null ? snowLayer.getName() : "none"));
print("Biome painting: " + useBiomeLayer);

for (var x = minX; x < minX + worldWidth; x++) {
    for (var y = minY; y < minY + worldHeight; y++) {
        var height = dimension.getHeightAt(x, y);
        var waterLevel = dimension.getWaterLevelAt(x, y);
        var slope = getLocalSlope(dimension, x, y);
        var nearWater = height <= (waterLevel + waterBuffer);
        var snowChance = computeSnowChance(height, slope, snowLineHeight, fullSnowHeight, slopeLimit, nearWater);
        var shouldSnow = snowChance > 0.5;

        if (snowLayer != null) {
            applySnowLayer(dimension, snowLayer, x, y, shouldSnow, snowChance, clearLowSnow);
        }

        if (useBiomeLayer && shouldSnow) {
            dimension.setLayerValueAt(biomeLayer, x, y, snowBiomeId);
        }

        changed += shouldSnow ? 1 : 0;
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

print("Done. Snow-covered cells: " + changed);

function computeSnowChance(height, slope, snowLine, fullSnow, slopeLimitValue, nearWater) {
    if (height < snowLine) {
        return 0;
    }

    var heightFactor = (height - snowLine) / (fullSnow - snowLine);
    heightFactor = clamp01(heightFactor);

    var slopeFactor = 1 - clamp01(slope / slopeLimitValue);
    var waterFactor = nearWater ? 0.25 : 1.0;

    var chance = heightFactor * heightFactor * slopeFactor * waterFactor;
    return clamp01(chance);
}

function applySnowLayer(dimension, layer, x, y, shouldSnow, snowChance, clearLowSnowValue) {
    var dataSize = layer.getDataSize().toString();
    if (dataSize == "BIT") {
        if (shouldSnow) {
            dimension.setBitLayerValueAt(layer, x, y, true);
        } else if (clearLowSnowValue) {
            dimension.setBitLayerValueAt(layer, x, y, false);
        }
    } else {
        var amount = 0;
        if (shouldSnow) {
            amount = Math.max(1, parseInt(snowChance * 8));
        } else if (clearLowSnowValue) {
            amount = 0;
        }
        dimension.setLayerValueAt(layer, x, y, amount);
    }
}

function getLocalSlope(dimension, x, y) {
    var center = dimension.getHeightAt(x, y);
    var maxDiff = 0;
    for (var dx = -1; dx <= 1; dx++) {
        for (var dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dy == 0) {
                continue;
            }
            var nx = x + dx;
            var ny = y + dy;
            if (nx - minX < 0 || nx - minX >= worldWidth || ny - minY < 0 || ny - minY >= worldHeight) {
                continue;
            }
            var diff = Math.abs(center - dimension.getHeightAt(nx, ny));
            if (diff > maxDiff) {
                maxDiff = diff;
            }
        }
    }
    return maxDiff;
}

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

function clamp01(value) {
    if (value < 0) {
        return 0;
    }
    if (value > 1) {
        return 1;
    }
    return value;
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
