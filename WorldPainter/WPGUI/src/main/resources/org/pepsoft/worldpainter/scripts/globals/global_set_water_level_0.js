//-- get info on delimiter here: https://github.com/Captain-Chaos/WorldPainter/blob/219f7eb1402e49d9c79fed72799c82503385d669/WorldPainter/WPGUI/src/test/resources/descriptortest.js

// script.name= Global Set Water Level 0
// script.description=Sets water level to 0 on the whole current dimension.

var app = org.pepsoft.worldpainter.App.getInstance();
var dimension = app.dimension;
var extent = dimension.getExtent();
var minTileX = dimension.getLowestX();
var minTileY = dimension.getLowestY();
var tileWidth = extent.getWidth();
var tileHeight = extent.getHeight();

var total = tileWidth * tileHeight * 128 * 128;
var processed = 0;
var changed = 0;
var nextLog = Math.max(10000, parseInt(total / 20));
var startTime = new Date().getTime();

print("Starting: setting global water level to 0...");

for (var tx = 0; tx < tileWidth; tx++) {
    for (var ty = 0; ty < tileHeight; ty++) {
        var tileX = minTileX + tx;
        var tileY = minTileY + ty;

        for (var lx = 0; lx < 128; lx++) {
            for (var ly = 0; ly < 128; ly++) {
                var x = tileX * 128 + lx;
                var y = tileY * 128 + ly;
                var wl = dimension.getWaterLevelAt(x, y);

                if (wl != 0) {
                    dimension.setWaterLevelAt(x, y, 0);
                    changed++;
                }

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
    }
}

print("Done. Updated cells: " + changed);

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
