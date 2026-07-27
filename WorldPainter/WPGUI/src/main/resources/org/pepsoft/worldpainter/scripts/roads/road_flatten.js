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



//################ CHANGE THESE VARIABLES ################### 
//################ CHANGE THESE VARIABLES ###################
//################ CHANGE THESE VARIABLES ###################
//################ CHANGE THESE VARIABLES ###################
//################ CHANGE THESE VARIABLES ###################

useThickSlabs = false // change this to true to get thick edge selection for slabify by default (is a bit slower.)


//################ CHANGE THESE VARIABLES ###################
//################ CHANGE THESE VARIABLES ###################
//################ CHANGE THESE VARIABLES ###################
//################ CHANGE THESE VARIABLES ###################
//################ CHANGE THESE VARIABLES ###################










if (world == null) {
	print("Running from terminal!");
	print("I can make this work from the terminal, just haven't bothered. contact me on discord: @sijmen_v_b");
}
var app = org.pepsoft.worldpainter.App.getInstance();

// ############# \/ save last entered value \/ ###############
var fs = Java.type('java.nio.file.Files');
var Paths = Java.type('java.nio.file.Paths');
var StandardOpenOption = Java.type('java.nio.file.StandardOpenOption');
var StandardCharsets = Java.type('java.nio.charset.StandardCharsets');
var System = Java.type('java.lang.System');
var scriptFilePath = Paths.get(scriptDir, __FILE__).toString()
// ############# /\ save last entered value /\ ###############

var dimension = world.getDimension(0);
var scale = 100;
extent = dimension.getExtent();
worldWidth = extent.getWidth() * 128;
worldHeight = extent.getHeight() * 128;
var minX = dimension.getLowestX() * 128;
var minY = dimension.getLowestY() * 128;
var thick = false;
var maskOn = 0;

var d = new Date();
var startTime = d.getTime();

var HashMap = Java.type('java.util.HashMap');

var distance = params['distance'];
var layerMask = params['layerMask'];
var terrainMask = params['terrainMask'];
var fileMask = params['fileMask'];
var roadLayerName = params['roadLayer'];
var roadSlabName = params['roadSlab'];



// ############# \/ save last entered value \/ ###############
var paramDefaults = [
    ["// script.param.distance.default=",distance],
    ["// script.param.layerMask.default=",layerMask],
    ["// script.param.terrainMask.default=",terrainMask],
    ["// script.param.fileMask.default=",fileMask],
    ["// script.param.roadLayer.default=",roadLayerName],
    ["// script.param.roadSlab.default=",roadSlabName]
]

var str = readFile(scriptFilePath);
newStr = replaceParamValue(str,paramDefaults)
createAndWriteFile(scriptFilePath, newStr);
// ############# /\ save last entered value /\ ###############



var pointsToBeFlattened = new HashMap();

//create map with all the terrain types. where the keys are the names lowercase without spaces. (use .replaceAll(" ","").toLocaleLowerCase())
var terrainMap = new HashMap();
var terrainEnum = org.pepsoft.worldpainter.Terrain.VALUES

for (var i = 0; i < terrainEnum.length; i++) {
	terrainMap.put(terrainEnum[i].toString().replaceAll(" ", "").toLocaleLowerCase(), terrainEnum[i]) // add the name to the and the terrain to the enum 
	terrainMap.put(terrainEnum[i].getName().replaceAll(" ", "").toLocaleLowerCase(), terrainEnum[i]) // also add the custom name so instead of custom4 you can also use the name of the custom layer.
}

// load the layer from the GUI! (no longer require the layer to be on the map.) where the keys are the names lowercase without spaces. (use .replaceAll(" ","").toLocaleLowerCase())
var layers = app.getAllLayers();
var layerMap = new HashMap();

for (var i = 0; i < layers.length; i++) {
	layerMap.put(layers[i].getName().replaceAll(" ", "").toLocaleLowerCase(), layers[i])
}

function Point(x, y) {
	this.x = x;
	this.y = y;
	this.heights = [];
}

Point.prototype.getHeights = function () {
	return this.heights;
}

Point.prototype.setHeights = function (heights) {
	this.heights = heights;
}


var applyRoadLayer = roadLayerName != null
var roadLayerIsBit = false
if (applyRoadLayer) {
	var roadLayer = layerMap.get(roadLayerName.replaceAll(" ", "").toLocaleLowerCase());
	s = ""
	s = s + roadLayer.getDataSize()
	if (s == "BIT")//bit layer
	{
		roadLayerIsBit = true
	}
}

var applyRoadSlab = roadSlabName != null
var roadSlabIsBit = false
if (applyRoadSlab) {
	var roadSlab = layerMap.get(roadSlabName.replaceAll(" ", "").toLocaleLowerCase());
	s = ""
	s = s + roadSlab.getDataSize()
	if (s == "BIT")//bit layer
	{
		roadSlabIsBit = true
	}
}



if (layerMask != null) {
	var maskLayer = layerMap.get(layerMask.replaceAll(" ", "").toLocaleLowerCase());//load the layer as mask-layer.
	print("layer: ", maskLayer, "selected as mask");

	var bitLayer = maskLayer.getDataSize() == "BIT";
	maskOn = 1;
}
else if (terrainMask != null) {
	var terrain = terrainMap.get(terrainMask.replaceAll(" ", "").toLocaleLowerCase())//load the terrain.
	print("terrain: ", terrain, "selected as mask");
	maskOn = 2;
}
else if (fileMask != null) {
	if (fileMask[0] == "\"") {
		//print("removing \" from file path")
		fileMask = fileMask.slice(1, -1);
	}

	var heightMap = wp.getHeightMap().fromFile(fileMask).go();//load image as heightmap
	if (heightMap.getRange()[1] > 256)//get the maximum value of the heightmap and if the maximum value is under 255 assume a 8 bit heightmap.
	{
		var halfway = 32767;//set the halfway value to the half height of the image.
		print("16 bit mask detected.")
	}
	else {
		var halfway = 127;//set the halfway value to the half height of the image.
		print("8 bit mask detected.")
	}


	maskOn = 3;
}




if (maskOn == 0) {
	print("NO mask was selected. (accidentally running without a mask is super slow so a mask is mandatory.)")
} else {
	for (var x = minX + 1; x < worldWidth + minX - 1; x++) {
		for (var y = minY + 1; y < worldHeight + minY - 1; y++)//loop trough all coordinates(blocks) of the map.
		{
			if (maskOn == 1)//if the mask is a layer
			{
				if (bitLayer) {
					if (dimension.getBitLayerValueAt(maskLayer, x, y) == 1)//if the layer value is not 0 on a block (so the layer is there) continue in applying the edge detection.
					{
						fixup(dimension, x, y);
					}
				}
				else {
					if (dimension.getLayerValueAt(maskLayer, x, y) > 0)//if the layer value is not 0 on a block (so the layer is there) continue in applying the edge detection.
					{
						fixup(dimension, x, y);
					}
				}
			}
			else if (maskOn == 2) { //if the mask is a terrain
				if (dimension.getTerrainAt(x, y) == terrain)//compare the terrain at the block to the terrain of the mask. id they are the same continue in applying the edge detection.
				{
					fixup(dimension, x, y);
				}
			}
			else if (maskOn == 3) { //if the mask is a file
				if (heightMap.getHeight(x, y) > halfway)//if the value of the mask is bigger than 50% of the maximum height (gray) continue in applying the edge detection.  
				{
					fixup(dimension, x, y);
				}
			}

		}
		if (x % 10 == 0 || x == worldWidth + minX - 1) {
			print("gathering height information status: " + parseInt((x - minX) / (worldWidth) * 100 + 0.2) + "%");
		}
	}
}

var pointsToBeFlattenedLength = pointsToBeFlattened.length;
var counter = 0;
for (key in pointsToBeFlattened) {
	point = pointsToBeFlattened.get(key);
	var x = point.x;
	var y = point.y;
	var heights = point.getHeights();
	if (heights == null) {
		print("skipping", x, y);
		continue;
	}

	var length = heights.length

	var sum = 0;
	for (var i = 0; i < length; i++) {
		sum += heights[i];
	}
	dimension.setHeightAt(x, y, (sum / length));

	if (counter % 1000 == 0) {
		print("flattening roads: " + parseInt((counter) / (pointsToBeFlattenedLength) * 100 + 0.2) + "%");
	}

	if (applyRoadLayer) {
		if (roadLayerIsBit) {
			dimension.setBitLayerValueAt(roadLayer, x, y, true)
		} else {
			dimension.setLayerValueAt(roadLayer, x, y, 8)
		}
	}



	counter++
}

for (key in pointsToBeFlattened) {
	point = pointsToBeFlattened.get(key);
	var x = point.x;
	var y = point.y;
	if (applyRoadSlab && (thinLower(dimension, x, y) || useThickSlabs && thickLower(dimension, x, y) )) {
		if (roadSlabIsBit) {
			dimension.setBitLayerValueAt(roadSlab, x, y, true)
		} else {
			dimension.setLayerValueAt(roadSlab, x, y, 8)
		}
	}
}


d = new Date();
var endTime = d.getTime();
var elapsedMs = endTime - startTime;

// Convert elapsed time from milliseconds to seconds
var elapsedSec = Math.floor(elapsedMs / 1000);

// Calculate hours, minutes, and seconds
var hours = Math.floor(elapsedSec / 3600);
var minutes = Math.floor((elapsedSec - (hours * 3600)) / 60);
var seconds = elapsedSec - (hours * 3600) - (minutes * 60);
var milliseconds = elapsedMs - (elapsedSec * 1000);

// Format the time string
var timeStr = '';
if (hours > 0) {
	timeStr += hours + ' hour';
	if (hours > 1) {
		timeStr += 's';
	}
	timeStr += ' ';
}
if (minutes > 0 || hours > 0) {
	timeStr += minutes + ' minute';
	if (minutes > 1) {
		timeStr += 's';
	}
	timeStr += ' ';
}
if (seconds > 0 || minutes > 0 || hours > 0) {
	timeStr += seconds + ' second';
	if (seconds > 1) {
		timeStr += 's';
	}
	timeStr += ' ';
}
timeStr += milliseconds + ' millisecond';
if (milliseconds != 1) {
	timeStr += 's';
}
print("\ntook:\t" + timeStr);


print("\nDone! -- script provided by sijmen_v_b");

function fixup(dimension, x, y) {
	//print("x " + (x - minX) + " y " + (y - minY));
	var height = dimension.getHeightAt(x, y);
	for (var i = -1 * distance; i < distance + 1; i++) {
		for (var j = -1 * distance; j < distance + 1; j++)//loop trough all coordinates(blocks) of the map.
		{
			if (x + i > minX && x + i < worldWidth + minX && y + j > minY && y + j < worldHeight + minY) {
				var dist = getSquaredDistance(x + i, y + j, x, y);
				if (dist <= distance * distance) {
					addHeight(x + i, y + j, height);
				}
			}

		}
	}
}

function addHeight(x, y, height) {
	var index = CoordinateToIndex(x, y);
	var point = pointsToBeFlattened.get(index);

	if (point == null) {
		point = new Point(x, y);
	}
	point.getHeights().push(height);
	pointsToBeFlattened.put(index, point);
}

function getSquaredDistance(x1, y1, x2, y2) {
	var x = x1 - x2;
	var y = y1 - y2;
	return x * x + y * y;
}


//used for the hashmap
function CoordinateToIndex(x, y) {
	return x + y * worldWidth
}

function thinLower(dimension, x, y) {
	return parseInt(dimension.getHeightAt(x + 1, y) - 0.5) == parseInt(dimension.getHeightAt(x, y) - 0.5) - 1 ||
		parseInt(dimension.getHeightAt(x - 1, y) - 0.5) == parseInt(dimension.getHeightAt(x, y) - 0.5) - 1 ||
		parseInt(dimension.getHeightAt(x, y + 1) - 0.5) == parseInt(dimension.getHeightAt(x, y) - 0.5) - 1 ||
		parseInt(dimension.getHeightAt(x, y - 1) - 0.5) == parseInt(dimension.getHeightAt(x, y) - 0.5) - 1
}

function thickLower(dimension, x, y) {
	return  parseInt(dimension.getHeightAt(x + 1, y - 1) - 0.5) == parseInt(dimension.getHeightAt(x, y) - 0.5) - 1 ||
		parseInt(dimension.getHeightAt(x - 1, y + 1) - 0.5) == parseInt(dimension.getHeightAt(x, y) - 0.5) - 1 ||
		parseInt(dimension.getHeightAt(x + 1, y + 1) - 0.5) == parseInt(dimension.getHeightAt(x, y) - 0.5) - 1 ||
		parseInt(dimension.getHeightAt(x - 1, y - 1) - 0.5) == parseInt(dimension.getHeightAt(x, y) - 0.5) - 1

}

//###################### \/ functions for saving the last entered values \/ ########################################
function createAndWriteFile(filePath, content) {
    var path = Paths.get(filePath);
    
    try {
        // Create the file if it doesn't exist, truncate it if it does
        var writer = fs.newBufferedWriter(path, [StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING]);
        
        // Write the content to the file
        writer.write(content);
        writer.newLine();
        
        // Close the writer
        writer.close();
        
        print("File updated successfully:", filePath);
    } catch (e) {
        print("Error updating file:", e);
    }
}



function readFile(filePath) {
    var path = Paths.get(filePath);
    if (!fs.exists(path)) {
        print("ERROR:", filePath, "does not exist.")
        return null;
    }
    var content = new java.lang.String(fs.readAllBytes(path), StandardCharsets.UTF_8).toString();
    return content;
}

function replaceParamValue(inputString, patternsAndValues) {
    var lines = inputString.split('\n'); // Split inputString into lines
    var updatedLines = [];

    lines.forEach(function(line) {
        var lineUpdated = false;
        patternsAndValues.forEach(function(tuple) {
            var pattern = tuple[0];
            var newValue = tuple[1];
            if (newValue == undefined){
                newValue = "";
            }

            if (line.startsWith(pattern)) {
                var newLine = pattern + newValue;
                updatedLines.push(newLine);
                lineUpdated = true;
            }
        });

        if (!lineUpdated) {
            updatedLines.push(line); // Push lines that don't match any pattern unchanged
        }
    });

    // Join the updated lines back into a single string
    var updatedString = updatedLines.join('\n');
    return updatedString;
}
//###################### /\ functions for saving the last entered values /\ ########################################


