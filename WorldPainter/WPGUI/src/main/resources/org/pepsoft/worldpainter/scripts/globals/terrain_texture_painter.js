// script.name=Terrain Texture Painter
// script.description=Paint terrain globally from a colour texture, with optional greyscale masking, terrain/layer filters and slope-aware side textures.\n\nPalette format: #RRGGBB=Terrain;#RRGGBB=Terrain. Terrain may be a built-in or configured Custom Terrain name.\n\nTexture pixels repeat by default. Use the texture scale to define how many world blocks one texture pixel covers. Start with Dry run enabled to inspect the affected area without changing the world.
// script.param.texture.type=file
// script.param.texture.description=Base colour texture (PNG, JPG or another ImageIO-supported image). It is sampled in world X/Y coordinates.
// script.param.texture.displayName=Base colour texture
// script.param.northTexture.type=file
// script.param.northTexture.description=Optional texture used on slopes rising north. Missing directional textures fall back to the base texture.
// script.param.northTexture.displayName=North slope texture
// script.param.northTexture.optional=true
// script.param.northTexture.default=
// script.param.eastTexture.type=file
// script.param.eastTexture.description=Optional texture used on slopes rising east.
// script.param.eastTexture.displayName=East slope texture
// script.param.eastTexture.optional=true
// script.param.eastTexture.default=
// script.param.southTexture.type=file
// script.param.southTexture.description=Optional texture used on slopes rising south.
// script.param.southTexture.displayName=South slope texture
// script.param.southTexture.optional=true
// script.param.southTexture.default=
// script.param.westTexture.type=file
// script.param.westTexture.description=Optional texture used on slopes rising west.
// script.param.westTexture.displayName=West slope texture
// script.param.westTexture.optional=true
// script.param.westTexture.default=
// script.param.mask.type=file
// script.param.mask.description=Optional greyscale or alpha mask. Black skips, white applies fully, and grey blends by deterministic dithering.
// script.param.mask.displayName=Optional opacity mask
// script.param.mask.optional=true
// script.param.mask.default=
// script.param.palette.type=string
// script.param.palette.description=Semicolon-separated colour to terrain entries. Example: #4F7F35=Grass;#77736B=Rock;#A87847=Dirt;#D7C18B=Sand
// script.param.palette.displayName=Colour palette
// script.param.palette.default=#4F7F35=Grass;#77736B=Rock;#A87847=Dirt;#D7C18B=Sand
// script.param.layerFilter.type=string
// script.param.layerFilter.description=Optional existing layer name. Only cells with a positive value in this layer are painted.
// script.param.layerFilter.displayName=Optional layer filter
// script.param.layerFilter.optional=true
// script.param.layerFilter.default=
// script.param.terrainFilter.type=string
// script.param.terrainFilter.description=Optional existing terrain name. Only cells currently using this terrain are painted.
// script.param.terrainFilter.displayName=Optional source terrain filter
// script.param.terrainFilter.optional=true
// script.param.terrainFilter.default=
// script.param.minHeight.type=integer
// script.param.minHeight.description=Lowest terrain level which may be painted.
// script.param.minHeight.displayName=Minimum height
// script.param.minHeight.default=-2048
// script.param.maxHeight.type=integer
// script.param.maxHeight.description=Highest terrain level which may be painted.
// script.param.maxHeight.displayName=Maximum height
// script.param.maxHeight.default=2048
// script.param.minSlope.type=integer
// script.param.minSlope.description=Minimum local slope angle in degrees.
// script.param.minSlope.displayName=Minimum slope
// script.param.minSlope.default=0
// script.param.maxSlope.type=integer
// script.param.maxSlope.description=Maximum local slope angle in degrees.
// script.param.maxSlope.displayName=Maximum slope
// script.param.maxSlope.default=90
// script.param.sideSlope.type=integer
// script.param.sideSlope.description=At and above this slope angle, directional side textures are used when provided.
// script.param.sideSlope.displayName=Directional texture slope
// script.param.sideSlope.default=35
// script.param.textureScale.type=float
// script.param.textureScale.description=World blocks covered by one texture pixel. 1 means one pixel per block; 2 means one pixel per two blocks.
// script.param.textureScale.displayName=Blocks per texture pixel
// script.param.textureScale.default=1
// script.param.offsetX.type=integer
// script.param.offsetX.description=World X offset used while sampling the texture.
// script.param.offsetX.displayName=Texture X offset
// script.param.offsetX.default=0
// script.param.offsetY.type=integer
// script.param.offsetY.description=World Y offset used while sampling the texture.
// script.param.offsetY.displayName=Texture Y offset
// script.param.offsetY.default=0
// script.param.repeatTexture.type=boolean
// script.param.repeatTexture.description=Repeat texture and mask when the world is larger than the image. Disable to clamp at image edges.
// script.param.repeatTexture.displayName=Repeat texture
// script.param.repeatTexture.default=true
// script.param.dither.type=boolean
// script.param.dither.description=Blend close palette colours and partial mask values with a repeatable random pattern.
// script.param.dither.displayName=Use deterministic dithering
// script.param.dither.default=true
// script.param.seed.type=integer
// script.param.seed.description=Seed used for deterministic dithering. The same settings and seed always produce the same result.
// script.param.seed.displayName=Dithering seed
// script.param.seed.default=1337
// script.param.dryRun.type=boolean
// script.param.dryRun.description=Scan and report the affected cells without changing the world. Turn this off only after reviewing the output.
// script.param.dryRun.displayName=Dry run (do not modify world)
// script.param.dryRun.default=true
// script.hideCmdLineParams=true

var ImageIO = Java.type('javax.imageio.ImageIO');
var File = Java.type('java.io.File');
var HashMap = Java.type('java.util.HashMap');
var Terrain = Java.type('org.pepsoft.worldpainter.Terrain');

if (world == null || dimension == null) {
    throw 'Terrain Texture Painter must be run from an open WorldPainter world.';
}

var baseTexture = readImageParam('texture', true);
var northTexture = readImageParam('northTexture', false);
var eastTexture = readImageParam('eastTexture', false);
var southTexture = readImageParam('southTexture', false);
var westTexture = readImageParam('westTexture', false);
var maskTexture = readImageParam('mask', false);

var palette = parsePalette(String(params['palette']));
var sourceTerrain = resolveTerrain(optionalText('terrainFilter'));
var layerFilter = resolveLayer(optionalText('layerFilter'));
var minHeight = Number(params['minHeight']);
var maxHeight = Number(params['maxHeight']);
var minSlope = Number(params['minSlope']);
var maxSlope = Number(params['maxSlope']);
var sideSlope = Number(params['sideSlope']);
var textureScale = Number(params['textureScale']);
var offsetX = Number(params['offsetX']);
var offsetY = Number(params['offsetY']);
var repeatTexture = String(params['repeatTexture']).toLowerCase() === 'true';
var dither = String(params['dither']).toLowerCase() === 'true';
var seed = Number(params['seed']);
var dryRun = String(params['dryRun']).toLowerCase() === 'true';

if (!(textureScale > 0)) {
    throw 'Blocks per texture pixel must be greater than zero.';
}
if (minHeight > maxHeight) {
    throw 'Minimum height may not be greater than maximum height.';
}
if (minSlope < 0 || maxSlope > 90 || minSlope > maxSlope) {
    throw 'Slope limits must be between 0 and 90 degrees.';
}

var minX = dimension.getLowestX() * 128;
var minY = dimension.getLowestY() * 128;
var maxX = minX + dimension.getWidth() * 128;
var maxY = minY + dimension.getHeight() * 128;
var total = Math.max(1, (maxX - minX - 2) * (maxY - minY - 2));
var checked = 0;
var eligible = 0;
var painted = 0;
var skippedMask = 0;
var changedByTerrain = new HashMap();

print('Terrain Texture Painter');
print('Texture: ' + baseTexture.getWidth() + 'x' + baseTexture.getHeight() + ', palette entries: ' + palette.length);
print('Region: X ' + minX + '..' + (maxX - 1) + ', Y ' + minY + '..' + (maxY - 1));
print(dryRun ? 'Dry run enabled; the world will not be changed.' : 'Applying terrain texture...');

for (var x = minX + 1; x < maxX - 1; x++) {
    for (var y = minY + 1; y < maxY - 1; y++) {
        checked++;
        var height = dimension.getHeightAt(x, y);
        if (height < minHeight || height > maxHeight) {
            progressTick();
            continue;
        }
        if (sourceTerrain != null && !sameTerrain(dimension.getTerrainAt(x, y), sourceTerrain)) {
            progressTick();
            continue;
        }
        if (layerFilter != null && !layerHasValue(layerFilter, x, y)) {
            progressTick();
            continue;
        }

        var surface = getSurface(x, y);
        if (surface.slope < minSlope || surface.slope > maxSlope) {
            progressTick();
            continue;
        }

        var opacity = maskTexture == null ? 1.0 : imageOpacity(maskTexture, x, y);
        if (opacity <= 0 || (opacity < 1.0 && deterministicRandom(x, y, seed + 17) > opacity)) {
            skippedMask++;
            progressTick();
            continue;
        }

        eligible++;
        var texture = selectTexture(surface);
        var terrain = chooseTerrain(sampleColour(texture, x, y), x, y);
        if (terrain != null && !sameTerrain(dimension.getTerrainAt(x, y), terrain)) {
            painted++;
            increment(changedByTerrain, terrain.getName());
            if (!dryRun) {
                dimension.setTerrainAt(x, y, terrain);
            }
        }
        progressTick();
    }
}

if (typeof progress !== 'undefined' && progress != null) {
    progress.setProgress(1.0);
    progress.checkForCancel();
}
print((dryRun ? 'Dry run complete.' : 'Terrain texture applied.'));
print('Checked: ' + checked + ', eligible: ' + eligible + ', changed: ' + painted + ', mask skipped: ' + skippedMask + '.');
var entries = changedByTerrain.entrySet().iterator();
while (entries.hasNext()) {
    var entry = entries.next();
    print('  ' + entry.getKey() + ': ' + entry.getValue());
}

function progressTick() {
    if ((checked & 4095) === 0 && typeof progress !== 'undefined' && progress != null) {
        progress.setProgress(checked / total);
        progress.checkForCancel();
    }
}

function readImageParam(name, required) {
    var value = params[name];
    if (value == null || String(value).trim().length === 0) {
        if (required) {
            throw 'A base colour texture is required.';
        }
        return null;
    }
    var file = new File(String(value));
    if (!file.isFile()) {
        throw 'Image file does not exist: ' + file;
    }
    var image = ImageIO.read(file);
    if (image == null) {
        throw 'Unsupported or unreadable image: ' + file;
    }
    return image;
}

function optionalText(value) {
    if (value == null) {
        return null;
    }
    var text = String(value).trim();
    return text.length === 0 ? null : text;
}

function normalise(value) {
    return String(value).toLowerCase().replace(/[ _-]/g, '');
}

function resolveTerrain(name) {
    if (name == null) {
        return null;
    }
    var wanted = normalise(name);
    for (var i = 0; i < Terrain.VALUES.length; i++) {
        var terrain = Terrain.VALUES[i];
        if (normalise(terrain.getName()) === wanted || normalise(terrain.toString()) === wanted) {
            return terrain;
        }
    }
    throw 'Unknown terrain: ' + name;
}

function sameTerrain(first, second) {
    return first != null && first.equals(second);
}

function resolveLayer(name) {
    if (name == null) {
        return null;
    }
    var app = Java.type('org.pepsoft.worldpainter.App').getInstance();
    var layers = app.getAllLayers();
    var wanted = normalise(name);
    for (var i = 0; i < layers.length; i++) {
        if (normalise(layers[i].getName()) === wanted) {
            return layers[i];
        }
    }
    throw 'Unknown layer: ' + name;
}

function layerHasValue(layer, x, y) {
    var size = String(layer.getDataSize());
    return size.indexOf('BIT') === 0
        ? dimension.getBitLayerValueAt(layer, x, y)
        : dimension.getLayerValueAt(layer, x, y) > 0;
}

function parsePalette(value) {
    var result = [];
    var definitions = value.split(';');
    for (var i = 0; i < definitions.length; i++) {
        var definition = definitions[i].trim();
        if (definition.length === 0) {
            continue;
        }
        var separator = definition.indexOf('=');
        if (separator <= 0 || separator === definition.length - 1) {
            throw 'Invalid palette entry: ' + definition + '. Use #RRGGBB=Terrain.';
        }
        var colourText = definition.substring(0, separator).trim().replace('#', '');
        if (!/^[0-9a-fA-F]{6}$/.test(colourText)) {
            throw 'Invalid palette colour: ' + definition.substring(0, separator).trim();
        }
        result.push({
            red: parseInt(colourText.substring(0, 2), 16),
            green: parseInt(colourText.substring(2, 4), 16),
            blue: parseInt(colourText.substring(4, 6), 16),
            terrain: resolveTerrain(definition.substring(separator + 1).trim())
        });
    }
    if (result.length === 0) {
        throw 'At least one palette entry is required.';
    }
    return result;
}

function getSurface(x, y) {
    var dx = (dimension.getHeightAt(x + 1, y) - dimension.getHeightAt(x - 1, y)) / 2.0;
    var dy = (dimension.getHeightAt(x, y + 1) - dimension.getHeightAt(x, y - 1)) / 2.0;
    var slope = Math.atan(Math.sqrt(dx * dx + dy * dy)) * 180.0 / Math.PI;
    var direction;
    if (Math.abs(dx) >= Math.abs(dy)) {
        direction = dx >= 0 ? 'east' : 'west';
    } else {
        direction = dy >= 0 ? 'south' : 'north';
    }
    return { slope: slope, direction: direction };
}

function selectTexture(surface) {
    if (surface.slope < sideSlope) {
        return baseTexture;
    }
    if (surface.direction === 'north' && northTexture != null) { return northTexture; }
    if (surface.direction === 'east' && eastTexture != null) { return eastTexture; }
    if (surface.direction === 'south' && southTexture != null) { return southTexture; }
    if (surface.direction === 'west' && westTexture != null) { return westTexture; }
    return baseTexture;
}

function sampleColour(image, x, y) {
    return image.getRGB(sampleCoordinate(x + offsetX, image.getWidth()), sampleCoordinate(y + offsetY, image.getHeight()));
}

function imageOpacity(image, x, y) {
    var colour = sampleColour(image, x, y);
    var alpha = (colour >>> 24) & 255;
    var red = (colour >>> 16) & 255;
    var green = (colour >>> 8) & 255;
    var blue = colour & 255;
    return (alpha / 255.0) * ((red + green + blue) / 765.0);
}

function sampleCoordinate(value, imageSize) {
    var coordinate = Math.floor(value / textureScale);
    if (repeatTexture) {
        coordinate = coordinate % imageSize;
        return coordinate < 0 ? coordinate + imageSize : coordinate;
    }
    return Math.max(0, Math.min(imageSize - 1, coordinate));
}

function chooseTerrain(colour, x, y) {
    var alpha = (colour >>> 24) & 255;
    if (alpha === 0) {
        return null;
    }
    var red = (colour >>> 16) & 255;
    var green = (colour >>> 8) & 255;
    var blue = colour & 255;
    var best = null;
    var second = null;
    var bestDistance = Number.MAX_VALUE;
    var secondDistance = Number.MAX_VALUE;
    for (var i = 0; i < palette.length; i++) {
        var candidate = palette[i];
        var distance = colourDistance(red, green, blue, candidate.red, candidate.green, candidate.blue);
        if (distance < bestDistance) {
            second = best;
            secondDistance = bestDistance;
            best = candidate;
            bestDistance = distance;
        } else if (distance < secondDistance) {
            second = candidate;
            secondDistance = distance;
        }
    }
    if (!dither || second == null || secondDistance <= 0) {
        return best.terrain;
    }
    var secondWeight = bestDistance / (bestDistance + secondDistance);
    return deterministicRandom(x, y, seed) < secondWeight ? second.terrain : best.terrain;
}

function colourDistance(r1, g1, b1, r2, g2, b2) {
    var r = r1 - r2;
    var g = g1 - g2;
    var b = b1 - b2;
    return 2 * r * r + 4 * g * g + 3 * b * b;
}

function deterministicRandom(x, y, salt) {
    var value = Math.sin(x * 12.9898 + y * 78.233 + salt * 37.719) * 43758.5453;
    return value - Math.floor(value);
}

function increment(map, key) {
    var old = map.get(key);
    map.put(key, old == null ? 1 : old + 1);
}
