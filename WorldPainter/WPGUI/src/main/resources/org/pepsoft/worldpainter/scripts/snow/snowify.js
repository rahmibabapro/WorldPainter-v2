// script.name=Snowify - Kr0wn
// script.description=Place snow and adds snow height to map - input amount of layers and melt angle
// script.param.mA.type=integer
// script.param.mA.description=The maximum angle which the snow will pile up to
// script.param.mA.displayName=Melt angle
// script.param.mA.default=45
// script.param.maxlayers.type=integer
// script.param.maxlayers.description=Amount of minecraft snow layers to apply (maximum snow thickness)
// script.param.maxlayers.displayName=Snow layers
// script.param.maxlayers.default=16
// script.param.addHeight.type=boolean
// script.param.addHeight.description=Add snow depth as height to your world 
// script.param.addHeight.displayName=Add snow depth
// script.param.addHeight.default=true
// script.hideCmdLineParams=true
var maxlayers = params["maxlayers"]
var maxAngle = Math.sin(params["mA"]*Math.PI/180);
var terrainArray = ["grass", "baregrass", "dirt", "permadirt", "podzol", "sand", "redsand", "desert", "reddesert", "mesa", "hardenedterracotta", "whiteterracotta", "orangeterracotta", "magentaterracotta", "lightblueterracotta", "yellowterracotta", "limeterracotta", "pinkterracotta", "greyterracotta", "lightgreyterracotta", "cyanterracotta", "purpleterracotta", "blueterracotta", "brownterracotta", "greenterracotta", "redterracotta", "blackterracotta", "sandstone", "stone", "rock", "cobblestone", "mossyCobblestone", "obsidian", "bedrock", "gravel", "clay", "beaches", "water", "lava", "stonesnow", "deepsnow", "netherrack", "soulsand", "netherlike", "mycelium", "endStone", "resources", "custom1", "custom2", "custom3", "custom4", "custom5", "custom6", "custom7", "custom8", "custom9", "custom10", "custom11", "custom12", "custom13", "custom14", "custom15", "custom16", "custom17", "custom18", "custom19", "custom20", "custom21", "custom22", "custom23", "custom24", "redsandstone", "granite", "diorite", "andesite", "stonemix", "custom25", "custom26", "custom27", "custom28", "custom29", "custom30", "custom31", "custom32", "custom33", "custom34", "custom35", "custom36", "custom37", "custom38", "custom39", "custom40", "custom41", "custom42", "custom43", "custom44", "custom45", "custom46", "custom47", "custom48", "grasspath", "magma"];
var dim = world.getDimension(0);
var Xmax = ((dim.getWidth() + dim.getLowestX()) * 128)
var Xmin = (dim.getLowestX() * 128)
var Ymax = ((dim.getHeight() + dim.getLowestY()) * 128)
var Ymin = (dim.getLowestY() * 128)
var snowMap = {}
var snowDepth = 1/8
var dx = 0
var dy = 0
var s = 0
var totalHeight = 0
var layers = 0
var snow = org.pepsoft.worldpainter.Terrain.VALUES[(terrainArray.indexOf("deepsnow".toLocaleLowerCase()))];
var annotations = wp.getLayer().withName('Annotations').go();
var frost = wp.getLayer().withName('Frost').go();
var Xmax2 = Xmin
var Xmin2 = Xmax
var Ymax2 = Ymin
var Ymin2 = Ymax

function snowKey(x, y) {
    return x + "," + y;
}

function getSnowDepth(x, y) {
    return snowMap[snowKey(x, y)] || 0;
}

function setSnowDepth(x, y, depth) {
    var key = snowKey(x, y);
    if (depth <= 0) {
        delete snowMap[key];
    } else {
        snowMap[key] = depth;
    }
}

function checkProgress(fraction) {
    if (typeof progress !== 'undefined' && progress != null) {
        progress.setProgress(fraction);
        progress.checkForCancel();
    }
}

print('Finding snow region')
var scanTotal = Math.max(1, (Xmax - Xmin) * (Ymax - Ymin));
var scanned = 0;
for (var x = Xmin; x < Xmax; x++) {
    for (var y = Ymin; y < Ymax; y++) {
        if (dim.getLayerValueAt(annotations, x, y) == 4) {
            if (x < Xmin2) { Xmin2 = x; }
            if (x > Xmax2) { Xmax2 = x; }
            if (y < Ymin2) { Ymin2 = y; }
            if (y > Ymax2) { Ymax2 = y; }
        }
        scanned++;
    }
    if ((scanned % 50000) === 0) {
        checkProgress(scanned / scanTotal * 0.1);
    }
}

if (Xmin2 > Xmax2 || Ymin2 > Ymax2) {
    print('No snow annotation (value 4) found on Annotations layer.');
} else {
    Xmin = Xmin2
    Xmax = Xmax2 + 1
    Ymin = Ymin2
    Ymax = Ymax2 + 1
    print('Adding snow')
    for (layers = 0; layers < maxlayers; layers ++) {
        for (var x = Xmin; x < Xmax; x++) {
            for (var y = Ymin; y < Ymax; y++) {
                if (dim.getLayerValueAt(annotations, x, y) == 4) {
                    setSnowDepth(x, y, getSnowDepth(x, y) + snowDepth);
                }
            }
        }

        for (var x = Xmin; x < Xmax; x++) {
            for (var y = Ymin; y < Ymax; y++) {
                getSteepnessAt(x, y)
                if (s > maxAngle && dim.getLayerValueAt(annotations, x, y) == 4) {
                    setSnowDepth(x, y, getSnowDepth(x, y) - snowDepth);
                }
            }
        }

        checkProgress(0.1 + ((layers + 1) / maxlayers) * 0.7);
        print(Math.round((layers + 1) / maxlayers * 100) + '%')
    }

    if (params["addHeight"] == true) {
        for (var key in snowMap) {
            var parts = key.split(",");
            var hx = parseInt(parts[0]);
            var hy = parseInt(parts[1]);
            getTotalHeightAt(hx, hy);
            dim.setHeightAt(hx, hy, totalHeight);
        }
    }

    var applyIndex = 0;
    var applyTotal = Object.keys(snowMap).length;
    for (var key in snowMap) {
        var parts = key.split(",");
        var sx = parseInt(parts[0]);
        var sy = parseInt(parts[1]);
        var depth = snowMap[key];
        if (depth > 0) {
            dim.setBitLayerValueAt(frost, sx, sy, 1);
            dim.setLayerValueAt(annotations, sx, sy, 0);
        }
        if (depth >= 1) {
            dim.setBitLayerValueAt(frost, sx, sy, 1);
            dim.setTerrainAt(sx, sy, snow);
        }
        applyIndex++;
        if ((applyIndex % 1000) === 0) {
            checkProgress(0.8 + (applyIndex / Math.max(1, applyTotal)) * 0.2);
        }
    }
}

print('DONE!')

function getSteepnessAt(x, y) {
   var height = getTotalHeightAt(x, y)

   dx = getTotalHeightAt(x + 1 , y) - height
   dy = getTotalHeightAt(x , y + 1) - height
   dx2 = getTotalHeightAt(x - 1 , y) - height
   dy2 = getTotalHeightAt(x , y - 1) - height
   s = (Math.sqrt(dx * dx + dy * dy) + Math.sqrt(dx2 * dx2 + dy2 * dy2)) /2
   if(dx > 0 && dy > 0 && dx2 > 0 && dy2 > 0){
    s = 0
   }
   return s
}

function getTotalHeightAt(x, y) {
    if (x >= Xmin && x < Xmax && y >= Ymin && y < Ymax) {
        totalHeight = dim.getHeightAt(x, y) + getSnowDepth(x, y);
    } else {
        totalHeight = dim.getHeightAt(x, y);
    }
    return totalHeight
}
