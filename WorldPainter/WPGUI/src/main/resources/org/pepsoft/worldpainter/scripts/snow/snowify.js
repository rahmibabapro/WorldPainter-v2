// script.name=Snowify Smooth
// script.description=Smooth, deterministic snow. Annotation mode keeps the existing blue Annotation (value 4) workflow; all-terrain mode is intended for mountain-wide snow.
// script.param.targetMode.type=string
// script.param.targetMode.description=annotation uses only Annotation value 4; all applies to every eligible dry terrain cell.
// script.param.targetMode.displayName=Target mode (annotation or all)
// script.param.targetMode.default=annotation
// script.param.annotationValue.type=integer
// script.param.annotationValue.description=Annotation value used in annotation mode. Existing Snowify selections use 4.
// script.param.annotationValue.displayName=Annotation value
// script.param.annotationValue.default=4
// script.param.snowLineHeight.type=float
// script.param.snowLineHeight.description=Height where sparse snow first becomes visible.
// script.param.snowLineHeight.displayName=Snow line height
// script.param.snowLineHeight.default=160
// script.param.fullSnowHeight.type=float
// script.param.fullSnowHeight.description=Height where the mountain reaches full snow coverage.
// script.param.fullSnowHeight.displayName=Full snow height
// script.param.fullSnowHeight.default=190
// script.param.maxSnowLayers.type=integer
// script.param.maxSnowLayers.description=Maximum optional height addition in 1/8-block layers (1 to 8).
// script.param.maxSnowLayers.displayName=Maximum snow layers
// script.param.maxSnowLayers.default=8
// script.param.slopeStart.type=float
// script.param.slopeStart.description=Slope angle where snow starts thinning.
// script.param.slopeStart.displayName=Snow thinning slope
// script.param.slopeStart.default=25
// script.param.slopeReject.type=float
// script.param.slopeReject.description=Slope angle where exposed rock receives no snow.
// script.param.slopeReject.displayName=Snow rejection slope
// script.param.slopeReject.default=55
// script.param.northFacingBoost.type=float
// script.param.northFacingBoost.description=Extra snow retained on north-facing slopes. 0 disables aspect influence.
// script.param.northFacingBoost.displayName=North-facing boost
// script.param.northFacingBoost.default=0.15
// script.param.seed.type=integer
// script.param.seed.description=Seed for broad, repeatable weather variation.
// script.param.seed.displayName=Snow seed
// script.param.seed.default=160190
// script.param.clearLowSnow.type=boolean
// script.param.clearLowSnow.description=Clear existing Frost below the snow line in the target area.
// script.param.clearLowSnow.displayName=Clear low snow
// script.param.clearLowSnow.default=true
// script.param.addHeight.type=boolean
// script.param.addHeight.description=Raise terrain by up to the chosen snow layers. Leave off for a non-destructive Frost-only pass.
// script.param.addHeight.displayName=Add smooth snow height
// script.param.addHeight.default=false
// script.param.dryRun.type=boolean
// script.param.dryRun.description=Report coverage without changing the world.
// script.param.dryRun.displayName=Dry run
// script.param.dryRun.default=true
// script.hideCmdLineParams=true

var SmoothSnow = Java.type('org.pepsoft.worldpainter.tools.scripts.SmoothSnow');
var Terrain = Java.type('org.pepsoft.worldpainter.Terrain');

if (typeof dimension === 'undefined' || dimension == null) {
    throw 'Snowify Smooth must be run from an open WorldPainter dimension.';
}

var snowParams = typeof params === 'undefined' || params == null ? {} : params;

function flag(name, fallback) {
    var value = snowParams[name];
    if (value == null) return fallback;
    var text = String(value).trim().toLowerCase();
    if (text !== 'true' && text !== 'false') throw name + ' must be true or false.';
    return text === 'true';
}

function numberParam(name, fallback, integer, minimum, maximum) {
    var value = snowParams[name];
    if (value == null) value = fallback;
    if (String(value).trim() === '') throw name + ' must be a finite number.';
    value = Number(value);
    if (!isFinite(value) || (integer && Math.floor(value) !== value)
            || value < minimum || value > maximum) throw 'Invalid ' + name + '.';
    return value;
}

var mode = String(snowParams['targetMode'] == null ? 'annotation' : snowParams['targetMode']).trim().toLowerCase();
if (mode !== 'annotation' && mode !== 'all') {
    throw 'Target mode must be annotation or all.';
}

var snowLine = numberParam('snowLineHeight', 160, false, -3.4e38, 3.4e38);
var fullSnow = numberParam('fullSnowHeight', 190, false, -3.4e38, 3.4e38);
var maxLayers = numberParam('maxSnowLayers', 8, true, 1, 8);
var slopeStart = numberParam('slopeStart', 25, false, 0, 90);
var slopeReject = numberParam('slopeReject', 55, false, 0, 90);
var annotationValue = numberParam('annotationValue', 4, true, 0, 15);
var northBoost = numberParam('northFacingBoost', 0.15, false, 0, 3.4e38);
var snowSeed = numberParam('seed', 160190, true, -9007199254740991, 9007199254740991);
if (!(fullSnow > snowLine)) {
    throw 'Full snow height must be greater than snow line height.';
}
if (!(slopeReject > slopeStart)) throw 'Snow rejection slope must exceed snow thinning slope.';

var dryRun = flag('dryRun', true);
var clearLowSnow = flag('clearLowSnow', true);
var addHeight = flag('addHeight', false);
print('Snowify Smooth: ' + (mode === 'annotation' ? 'Annotation ' + annotationValue : 'all eligible terrain'));
print('Snow begins at ' + snowLine + ', becomes full at ' + fullSnow + (dryRun ? ' (dry run).' : '.'));

var result = SmoothSnow.applyScript(
    dimension,
    mode === 'annotation', annotationValue,
    snowLine, fullSnow, maxLayers,
    slopeStart, slopeReject, northBoost, snowSeed,
    clearLowSnow, addHeight, dryRun, Terrain.DEEP_SNOW,
    typeof progress === 'undefined' ? null : progress
);

print((dryRun ? 'Dry run complete.' : 'Snow applied.')
    + ' Checked: ' + result.checked()
    + ', snow: ' + result.snowCovered()
    + ', cleared: ' + result.cleared()
    + ', deep snow terrain: ' + result.deepSnow() + '.');
