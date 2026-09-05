// script.name= Global Remove Water >=1
// script.description=Removes water where water level is 1 or higher on the whole current dimension; protected, absent and lava cells are preserved.

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


// Water storage is unsigned relative to minHeight: writing -1 into a legacy
// minY=0 world wraps to 255/65535 instead of removing water.
var dryLevel = Math.max(dim.getMinHeight(), -1);
if (dryLevel >= dim.getMaxHeight()) { throw "The dimension has an invalid height range."; }

var removed = 0;
print("Starting: removing water where water level >= 1...");
visitPresentTiles(function (tile, x, y) {
    if (tile.getWaterLevel(x, y) >= 1) {
        tile.setWaterLevel(x, y, dryLevel);
        removed++;
    }
});
print("Done. Removed water cells: " + removed);
