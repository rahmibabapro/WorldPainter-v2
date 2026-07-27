// script.name= Global Remove Water >=1
// script.description=Removes water where water level is 1 or higher on the whole current dimension.

var dim = (typeof dimension !== 'undefined' && dimension != null)
        ? dimension
        : org.pepsoft.worldpainter.App.getInstance().getDimension();
var removed = 0;
var processed = 0;
var totalBlocks = dim.getWidth() * dim.getHeight() * 128 * 128;

print("Starting: removing water where water level >= 1...");

function processTile(tile) {
    var worldTileX = tile.getX() * 128;
    var worldTileY = tile.getY() * 128;
    for (var lx = 0; lx < 128; lx++) {
        for (var ly = 0; ly < 128; ly++) {
            var x = worldTileX + lx;
            var y = worldTileY + ly;
            if (dim.getWaterLevelAt(x, y) >= 1) {
                dim.setWaterLevelAt(x, y, -1);
                removed++;
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
            for (var lx = 0; lx < 128; lx++) {
                for (var ly = 0; ly < 128; ly++) {
                    var x = (minTileX + tx) * 128 + lx;
                    var y = (minTileY + ty) * 128 + ly;
                    if (dim.getWaterLevelAt(x, y) >= 1) {
                        dim.setWaterLevelAt(x, y, -1);
                        removed++;
                    }
                }
            }
        }
    }
}

print("Done. Removed water cells: " + removed);
