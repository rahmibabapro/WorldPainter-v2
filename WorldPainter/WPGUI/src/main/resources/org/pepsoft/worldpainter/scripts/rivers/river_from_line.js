//-- River from drawn line script
// Çizilen bir çizgiyi nehir yapıyor ve otomatik olarak yan kollar ekliyor

// script.description=Çizdiğiniz çizgiyi kursal alan nehrine dönüştürür.\nYukarıdan aşağıya doğru akar.\nOtomatik olarak gerçekçi yan kollar oluşturur.

// script.name=River From Line -- Çiziden Nehir Yap

// script.param.riverLayer.type=string
// script.param.riverLayer.displayName=Nehir çizgisinin bulunduğu layer
// script.param.riverLayer.description=Nehri çizmek için kullandığınız layer adı (örn: river)
// script.param.riverLayer.default=river
// script.param.riverLayer.optional=false

// script.param.riverWidth.type=integer
// script.param.riverWidth.displayName=Nehir genişliği
// script.param.riverWidth.description=Nehrin orta kısmındaki genişlik (blok sayısı)
// script.param.riverWidth.default=8
// script.param.riverWidth.optional=false

// script.param.riverDepth.type=float
// script.param.riverDepth.displayName=Nehir derinliği
// script.param.riverDepth.description=Nehrin kazılacağı derinlik
// script.param.riverDepth.default=3
// script.param.riverDepth.optional=false

// script.param.tributaryCount.type=integer
// script.param.tributaryCount.displayName=Yan kol sayısı
// script.param.tributaryCount.description=Otomatik oluşturulacak yan kol sayısı
// script.param.tributaryCount.default=8
// script.param.tributaryCount.optional=false

// script.param.tributaryRandomness.type=float
// script.param.tributaryRandomness.displayName=Yan kol rastgeleliği (0-1)
// script.param.tributaryRandomness.description=Yan kolların ne kadar eğri olacağı (0=düz, 1=çok rastgele)
// script.param.tributaryRandomness.default=0.6
// script.param.tributaryRandomness.optional=false

// script.param.waterLevel.type=integer
// script.param.waterLevel.displayName=Su seviyesi
// script.param.waterLevel.description=Su bloklarının konacağı yükseklik
// script.param.waterLevel.default=62
// script.param.waterLevel.optional=false

// script.param.bankSmoothing.type=boolean
// script.param.bankSmoothing.displayName=Nehir kıyılarını yumuşat
// script.param.bankSmoothing.description=Dik nehir kıyılarını doğal görünümlü hale getir
// script.param.bankSmoothing.default=true
// script.param.bankSmoothing.optional=false

// script.param.enableWaterfalls.type=boolean
// script.param.enableWaterfalls.displayName=Selaleleri etkinleştir
// script.param.enableWaterfalls.description=Dik yerlerinde selaleler oluştur
// script.param.enableWaterfalls.default=true
// script.param.enableWaterfalls.optional=false

// Get parameters
var riverLayerName = params['riverLayer'];
var riverWidth = params['riverWidth'];
var riverDepth = params['riverDepth'];
var tributaryCount = params['tributaryCount'];
var tributaryRandomness = params['tributaryRandomness'];
var waterLevel = params['waterLevel'];
var bankSmoothing = params['bankSmoothing'];
var enableWaterfalls = params['enableWaterfalls'];

// Initialize
var app = org.pepsoft.worldpainter.App.getInstance();
var dimension = app.dimension;
var extent = dimension.getExtent();
var worldWidth = extent.getWidth() * 128;
var worldHeight = extent.getHeight() * 128;
var minX = dimension.getLowestX() * 128;
var minY = dimension.getLowestY() * 128;
var maxX = minX + worldWidth - 1;
var maxY = minY + worldHeight - 1;

if (typeof initRandom === 'function') {
    initRandom(this);
    if (typeof noise !== 'undefined' && noise && typeof noise.seed === 'function') {
        noise.seed(Math.random());
    }
}

var startTime = new Date().getTime();
var processedBlocks = 0;
var riversCreated = 0;

print("===========================================");
print("River From Line Script başlıyor...");
print("===========================================");

// Get the layer with the river line
var riverLayer = null;
var layers = app.getAllLayers();

for (var i = 0; i < layers.length; i++) {
    if (layers[i].getName() === riverLayerName) {
        riverLayer = layers[i];
        break;
    }
}

if (!riverLayer) {
    print("HATA: '" + riverLayerName + "' adında bir layer bulunamadı!");
    print("Mevcut layerler:");
    for (var i = 0; i < layers.length; i++) {
        print("  - " + layers[i].getName());
    }
    java.lang.System.exit(1);
}

print("Layer bulundu: " + riverLayerName);

// Find all points where the river layer is set
var riverPoints = [];
var riverMap = {};

for (var x = 0; x < worldWidth; x++) {
    for (var y = 0; y < worldHeight; y++) {
        var ax = minX + x;
        var ay = minY + y;
        if (dimension.getBitLayerValueAt(riverLayer, ax, ay)) {
            riverPoints.push({x: ax, y: ay});
            riverMap[ax + "," + ay] = true;
        }
    }
}

print("Bulunan nehir çizgisi noktaları: " + riverPoints.length);

if (riverPoints.length === 0) {
    print("HATA: Seçilen layerde hiç işaretlenmiş nokta yok!");
    java.lang.System.exit(1);
}

// Find starting point (topmost)
var startPoint = riverPoints[0];
for (var i = 1; i < riverPoints.length; i++) {
    if (riverPoints[i].y < startPoint.y) {
        startPoint = riverPoints[i];
    }
}

print("Başlangıç noktası: (" + startPoint.x + ", " + startPoint.y + ")");

// Process main river
processRiver(startPoint, riverPoints, riverMap, true);

// Create tributaries
createTributaries(riverPoints);

print("===========================================");
print("İşlem tamamlandı");
print("Toplam işlenen blok: " + processedBlocks);
print("Oluşturulan nehir: " + riversCreated);
var elapsed = new Date().getTime() - startTime;
print("Süre: " + elapsed + " ms");
print("===========================================");

// Main river processing function
function processRiver(startPoint, riverPoints, riverMap, isMainRiver) {
    var halfWidth = Math.floor(riverWidth / 2);
    
    // Sort points by Y coordinate (top to bottom) for water flow
    var orderedPoints = riverPoints.slice().sort(function(a, b) {
        return a.y - b.y;
    });
    
    var waterH = waterLevel;
    
    for (var idx = 0; idx < orderedPoints.length; idx++) {
        var point = orderedPoints[idx];
        
        // Create river bed width
        for (var dx = -halfWidth; dx <= halfWidth; dx++) {
            var nx = point.x + dx;
            if (nx >= minX && nx <= maxX) {
                // Dig the river channel
                var currentH = dimension.getHeightAt(nx, point.y);
                
                // Make water flow down slightly
                if (idx > 0) {
                    waterH = dimension.getHeightAt(orderedPoints[idx - 1].x, orderedPoints[idx - 1].y) - (riverDepth * 0.1);
                }
                
                // Create river bed
                if (currentH > waterH) {
                    dimension.setHeightAt(nx, point.y, Math.max(waterH - riverDepth, 0));
                }
                
                // Set water
                var newH = dimension.getHeightAt(nx, point.y);
                if (newH <= waterH) {
                    dimension.setWaterLevelAt(nx, point.y, Math.max(waterH, newH + 1));
                }
            }
        }
        
        processedBlocks += riverWidth;
    }
    
    riversCreated++;
    
    // Bank smoothing
    if (bankSmoothing) {
        smoothBanks(riverPoints, halfWidth);
    }
}

// Create tributary rivers
function createTributaries(mainRiverPoints) {
    print("Yan kollar oluşturuluyor (" + tributaryCount + " tane)...");
    
    for (var t = 0; t < tributaryCount; t++) {
        // Random start position
        var startX = minX + Math.floor(Math.random() * worldWidth);
        var startY = minY + Math.floor(Math.random() * (worldHeight * 0.7)); // Tributaries start from top 70%
        
        // Find closest main river point
        var closestPoint = mainRiverPoints[0];
        var minDist = 999999;
        
        for (var i = 0; i < mainRiverPoints.length; i++) {
            var dist = Math.sqrt(
                Math.pow(mainRiverPoints[i].x - startX, 2) + 
                Math.pow(mainRiverPoints[i].y - startY, 2)
            );
            if (dist < minDist) {
                minDist = dist;
                closestPoint = mainRiverPoints[i];
            }
        }
        
        // Create tributary path
        var path = createTributaryPath(startX, startY, closestPoint);
        
        if (path.length > 10) {
            // Draw tributary with decreasing width
            for (var p = 0; p < path.length; p++) {
                var point = path[p];
                var progress = p / path.length;
                var currentWidth = Math.max(2, Math.floor(riverWidth * (1 - progress * 0.8)));
                var currentDepth = riverDepth * (1 - progress * 0.5);
                
                drawRiverSegment(point.x, point.y, currentWidth, currentDepth);
            }
            
            riversCreated++;
        }
    }
}

// Create tributary path using pathfinding
function createTributaryPath(startX, startY, targetPoint) {
    var path = [];
    var currentX = Math.floor(startX);
    var currentY = Math.floor(startY);
    var steps = 0;
    var maxSteps = 500;
    
    while (steps < maxSteps && Math.sqrt(
        Math.pow(currentX - targetPoint.x, 2) + 
        Math.pow(currentY - targetPoint.y, 2)
    ) > 10) {
        
        path.push({x: currentX, y: currentY});
        
        // Calculate direction to target
        var dx = targetPoint.x - currentX;
        var dy = targetPoint.y - currentY;
        var len = Math.sqrt(dx * dx + dy * dy);
        
        if (len > 0) {
            dx /= len;
            dy /= len;
        }
        
        // Add randomness
        var randAngle = (Math.random() - 0.5) * tributaryRandomness * Math.PI;
        var cos = Math.cos(randAngle);
        var sin = Math.sin(randAngle);
        
        var newDx = dx * cos - dy * sin;
        var newDy = dx * sin + dy * cos;
        
        currentX += Math.floor(newDx * 3);
        currentY += Math.floor(newDy * 3);
        
        // Keep within bounds
        currentX = Math.max(minX, Math.min(maxX, currentX));
        currentY = Math.max(minY, Math.min(maxY, currentY));
        
        steps++;
    }
    
    return path;
}

// Draw river segment
function drawRiverSegment(cx, cy, width, depth) {
    var halfWidth = Math.floor(width / 2);
    
    for (var dx = -halfWidth; dx <= halfWidth; dx++) {
        var nx = cx + dx;
        if (nx >= minX && nx <= maxX && cy >= minY && cy <= maxY) {
            var currentH = dimension.getHeightAt(nx, cy);
            var targetH = waterLevel - depth;
            
            if (currentH > targetH) {
                dimension.setHeightAt(nx, cy, Math.max(targetH, 0));
            }
            
            // Set water
            if (dimension.getHeightAt(nx, cy) <= waterLevel) {
                dimension.setWaterLevelAt(nx, cy, waterLevel);
            }
            
            processedBlocks++;
        }
    }
}

// Smooth river banks
function smoothBanks(riverPoints, halfWidth) {
    for (var p = 0; p < riverPoints.length; p++) {
        var point = riverPoints[p];
        
        // Check points around the river
        for (var x = point.x - halfWidth - 2; x <= point.x + halfWidth + 2; x++) {
            if (x >= minX && x <= maxX) {
                var height = dimension.getHeightAt(x, point.y);
                
                // Get average height of neighbors
                var neighborHeights = [];
                for (var dx = -1; dx <= 1; dx++) {
                    var nx = x + dx;
                    if (nx >= minX && nx <= maxX) {
                        neighborHeights.push(dimension.getHeightAt(nx, point.y));
                    }
                }
                
                neighborHeights.sort(function(a, b) { return a - b; });
                var medianHeight = neighborHeights[Math.floor(neighborHeights.length / 2)];
                
                // Smooth steep banks
                if (Math.abs(height - medianHeight) > 1) {
                    dimension.setHeightAt(x, point.y, Math.round((height + medianHeight) / 2));
                }
            }
        }
    }
}

print("Script başarıyla tamamlandı!");
