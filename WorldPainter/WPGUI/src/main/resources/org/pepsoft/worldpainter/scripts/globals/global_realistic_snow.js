//-- get info on delimiter here: https://github.com/Captain-Chaos/WorldPainter/blob/219f7eb1402e49d9c79fed72799c82503385d669/WorldPainter/WPGUI/src/test/resources/descriptortest.js

// script.name= Global Realistic Snow
// script.description=Adds realistic snow coverage based on height, slope and water proximity. Uses the built-in Frost layer and can optionally paint snowy biome ids; preserves protected cells and supports cancellation.

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

// script.param.dryRun.type=boolean
// script.param.dryRun.description=Report coverage without changing terrain, snow or biomes.
// script.param.dryRun.displayName=Dry run
// script.param.dryRun.default=false

var SmoothSnow = Java.type('org.pepsoft.worldpainter.tools.scripts.SmoothSnow');
if (typeof dimension === 'undefined' || dimension == null) {
    throw 'Global Realistic Snow requires an open WorldPainter dimension.';
}
var snowParams = typeof params === 'undefined' || params == null ? {} : params;

function numberParam(name, fallback, integer, minimum, maximum) {
    var value = snowParams[name];
    if (value == null) value = fallback;
    if (String(value).trim() === '') throw name + ' must be a finite number.';
    value = Number(value);
    if (!isFinite(value) || (integer && Math.floor(value) !== value)
            || value < minimum || value > maximum) throw 'Invalid ' + name + '.';
    return value;
}

function flag(name, fallback) {
    var value = snowParams[name];
    if (value == null) return fallback;
    var text = String(value).trim().toLowerCase();
    if (text !== 'true' && text !== 'false') throw name + ' must be true or false.';
    return text === 'true';
}

var snowLine = numberParam('snowLineHeight', 90, false, -3.4e38, 3.4e38);
var fullSnow = numberParam('fullSnowHeight', 120, false, -3.4e38, 3.4e38);
var slopeLimit = numberParam('slopeLimit', 4, false, Number.MIN_VALUE, 3.4e38);
var waterBuffer = numberParam('waterBuffer', 2.5, false, 0, 3.4e38);
var biome = numberParam('snowBiomeId', 12, true, 0, 255);
if (!(fullSnow > snowLine)) throw 'Full snow height must exceed snow line height.';
var paintBiome = flag('useBiomeLayer', true);
var clearLowSnow = flag('clearLowSnow', true);
var dryRun = flag('dryRun', false);

print('Global Realistic Snow: ' + snowLine + ' to ' + fullSnow
    + ', slope limit ' + slopeLimit + (dryRun ? ' (dry run).' : '.'));
var result = SmoothSnow.applyRealistic(dimension, snowLine, fullSnow, slopeLimit, waterBuffer,
    paintBiome, biome, clearLowSnow, dryRun, typeof progress === 'undefined' ? null : progress);
print((dryRun ? 'Dry run complete.' : 'Snow applied.') + ' Checked: ' + result.checked()
    + ', snow-covered: ' + result.snowCovered() + ', cleared: ' + result.cleared() + '.');
