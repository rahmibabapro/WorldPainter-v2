// script.name=Axiom Mountain Smooth Snow
// script.description=Applies the Axiom mountain snow profile to all eligible terrain: sparse snow at Y=160, full coverage at Y=190, with slope, aspect and broad weather variation.
// script.param.snowTerrain.type=string
// script.param.snowTerrain.description=Terrain used for near-complete summit snow. Use a configured Axiom Snow Ice custom terrain when available; Deep Snow is the safe default.
// script.param.snowTerrain.displayName=Summit snow terrain
// script.param.snowTerrain.default=Deep Snow
// script.param.snowLineHeight.type=float
// script.param.snowLineHeight.description=Height where sparse snow starts appearing.
// script.param.snowLineHeight.displayName=Snow line height
// script.param.snowLineHeight.default=160
// script.param.fullSnowHeight.type=float
// script.param.fullSnowHeight.description=Height where summit snow coverage becomes complete.
// script.param.fullSnowHeight.displayName=Full snow height
// script.param.fullSnowHeight.default=190
// script.param.maxSnowLayers.type=integer
// script.param.maxSnowLayers.description=Maximum optional added snow depth, in 1/8-block layers (1 to 8).
// script.param.maxSnowLayers.displayName=Maximum snow layers
// script.param.maxSnowLayers.default=8
// script.param.slopeStart.type=float
// script.param.slopeStart.description=Slope angle where snow begins thinning.
// script.param.slopeStart.displayName=Snow thinning slope
// script.param.slopeStart.default=25
// script.param.slopeReject.type=float
// script.param.slopeReject.description=Slope angle where exposed rock receives no snow.
// script.param.slopeReject.displayName=Snow rejection slope
// script.param.slopeReject.default=55
// script.param.northFacingBoost.type=float
// script.param.northFacingBoost.description=Extra snow retained on north-facing slopes. Set 0 to disable.
// script.param.northFacingBoost.displayName=North-facing boost
// script.param.northFacingBoost.default=0.15
// script.param.seed.type=integer
// script.param.seed.description=Seed for natural, repeatable weather variation.
// script.param.seed.displayName=Snow seed
// script.param.seed.default=160190
// script.param.clearLowSnow.type=boolean
// script.param.clearLowSnow.description=Clear Frost below Y=160.
// script.param.clearLowSnow.displayName=Clear low snow
// script.param.clearLowSnow.default=true
// script.param.addHeight.type=boolean
// script.param.addHeight.description=Raise terrain by a limited snow depth. Keep off for a Frost-only pass.
// script.param.addHeight.displayName=Add smooth snow height
// script.param.addHeight.default=false
// script.param.dryRun.type=boolean
// script.param.dryRun.description=Report the result without modifying the world.
// script.param.dryRun.displayName=Dry run
// script.param.dryRun.default=true
// script.hideCmdLineParams=true

var SmoothSnow = Java.type('org.pepsoft.worldpainter.tools.scripts.SmoothSnow');
var Terrain = Java.type('org.pepsoft.worldpainter.Terrain');

if (typeof dimension === 'undefined' || dimension == null) {
    throw 'Axiom Mountain Smooth Snow must be run from an open WorldPainter dimension.';
}

function flag(name, fallback) {
    var value = params[name];
    if (value == null) { return fallback; }
    var text = String(value).trim().toLowerCase();
    if (text !== 'true' && text !== 'false') { throw name + ' must be true or false.'; }
    return text === 'true';
}

function normalise(value) {
    return String(value).toLowerCase().replace(/[ _-]/g, '');
}

function terrainNamed(name) {
    var wanted = normalise(name);
    for (var i = 0; i < Terrain.VALUES.length; i++) {
        var terrain = Terrain.VALUES[i];
        if (normalise(terrain.getName()) === wanted || normalise(terrain.toString()) === wanted) {
            if (terrain.isCustom() && !Terrain.isCustomMaterialConfigured(terrain.getCustomTerrainIndex())) {
                throw 'Custom summit terrain is not configured: ' + name;
            }
            return terrain;
        }
    }
    throw 'Unknown summit snow terrain: ' + name;
}

function finiteNumber(name) {
    var value = params[name];
    var number = value == null || String(value).trim().length === 0 ? NaN : Number(value);
    if (!isFinite(number)) throw 'A finite numeric value is required for ' + name + '.';
    return number;
}

var snowLine = finiteNumber('snowLineHeight');
var fullSnow = finiteNumber('fullSnowHeight');
var maximumLayers = finiteNumber('maxSnowLayers');
var slopeStart = finiteNumber('slopeStart');
var slopeReject = finiteNumber('slopeReject');
var northBoost = finiteNumber('northFacingBoost');
var snowSeed = finiteNumber('seed');
if (!(fullSnow > snowLine)) {
    throw 'Full snow height must be greater than snow line height.';
}
if (Math.floor(maximumLayers) !== maximumLayers || maximumLayers < 1 || maximumLayers > 8) {
    throw 'Maximum snow layers must be an integer from 1 to 8.';
}
if (Math.floor(snowSeed) !== snowSeed || Math.abs(snowSeed) > 9007199254740991) {
    throw 'Snow seed must be an exactly representable integer.';
}
var dryRun = flag('dryRun', true);
var clearLowSnow = flag('clearLowSnow', true);
var addHeight = flag('addHeight', false);
print('Axiom Mountain Smooth Snow: all eligible terrain; ' + snowLine + ' to ' + fullSnow + (dryRun ? ' (dry run).' : '.'));

var result = SmoothSnow.applyScript(
    dimension, false, 4, snowLine, fullSnow, maximumLayers,
    slopeStart, slopeReject, northBoost, snowSeed,
    clearLowSnow, addHeight, dryRun, terrainNamed(params['snowTerrain']),
    (typeof progress !== 'undefined' && progress != null) ? progress : null
);
print((dryRun ? 'Dry run complete.' : 'Axiom mountain snow applied.')
    + ' Checked: ' + result.checked() + ', snow: ' + result.snowCovered()
    + ', cleared: ' + result.cleared() + ', summit terrain: ' + result.deepSnow() + '.');
