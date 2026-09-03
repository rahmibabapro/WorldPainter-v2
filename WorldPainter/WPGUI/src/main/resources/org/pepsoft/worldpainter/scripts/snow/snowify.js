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

if (dimension == null) {
    throw 'Snowify Smooth must be run from an open WorldPainter dimension.';
}

function flag(value) {
    return String(value).toLowerCase() === 'true';
}

var mode = String(params['targetMode']).trim().toLowerCase();
if (mode !== 'annotation' && mode !== 'all') {
    throw 'Target mode must be annotation or all.';
}

var snowLine = Number(params['snowLineHeight']);
var fullSnow = Number(params['fullSnowHeight']);
var maxLayers = Number(params['maxSnowLayers']);
var slopeStart = Number(params['slopeStart']);
var slopeReject = Number(params['slopeReject']);
if (!(fullSnow > snowLine)) {
    throw 'Full snow height must be greater than snow line height.';
}

var dryRun = flag(params['dryRun']);
print('Snowify Smooth: ' + (mode === 'annotation' ? 'Annotation ' + params['annotationValue'] : 'all eligible terrain'));
print('Snow begins at ' + snowLine + ', becomes full at ' + fullSnow + (dryRun ? ' (dry run).' : '.'));

var result = SmoothSnow.apply(
    dimension,
    mode === 'annotation', Number(params['annotationValue']),
    snowLine, fullSnow, maxLayers,
    slopeStart, slopeReject, Number(params['northFacingBoost']), Number(params['seed']),
    flag(params['clearLowSnow']), flag(params['addHeight']), dryRun, Terrain.DEEP_SNOW, progress
);

print((dryRun ? 'Dry run complete.' : 'Snow applied.')
    + ' Checked: ' + result.checked()
    + ', snow: ' + result.snowCovered()
    + ', cleared: ' + result.cleared()
    + ', deep snow terrain: ' + result.deepSnow() + '.');
