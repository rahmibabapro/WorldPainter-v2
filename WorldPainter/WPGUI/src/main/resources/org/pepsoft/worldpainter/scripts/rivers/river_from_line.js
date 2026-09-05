//-- River from drawn line script
// Çizilen bir çizgiyi nehir yapıyor ve otomatik olarak yan kollar ekliyor

// script.description=Çizdiğiniz bağlı merkez hattını nehre dönüştürür.\nAkış, hattın en yüksek ucundan en alçak ucuna gerçek bağlantıları izler.\nİsteğe bağlı yan kollar ekleyebilir.

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

// script.param.presetId.type=integer
// script.param.presetId.displayName=Nehir hazır ayarı
// script.param.presetId.description=0=eski ayarlar, 1=dere, 2=doğal, 3=geniş, 4=kanyon, 5=dağ. Genişlik kaynaktan aşağıya kademeli artar.
// script.param.presetId.default=0
// script.param.presetId.optional=false

// script.param.linkSparseWaypoints.type=boolean
// script.param.linkSparseWaypoints.displayName=Ayrık noktaları bir yola bağla
// script.param.linkSparseWaypoints.default=true
// script.param.linkSparseWaypoints.optional=false

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
// script.param.waterLevel.description=Eski ayarlarda hedef seviye. Yeni hazır nokta-yolu ayarlarında su mevcut araziyi izler; sabit hedef Y uygulanmaz.
// script.param.waterLevel.default=62
// script.param.waterLevel.optional=false

// script.param.bankSmoothing.type=boolean
// script.param.bankSmoothing.displayName=Nehir kıyılarını yumuşat
// script.param.bankSmoothing.description=Hazır ayarlarda yalnız su altı yatak kesitini yumuşatır; kuru kıyı ve yamaçları değiştirmez.
// script.param.bankSmoothing.default=true
// script.param.bankSmoothing.optional=false

// script.param.enableWaterfalls.type=boolean
// script.param.enableWaterfalls.displayName=Selaleleri etkinleştir
// script.param.enableWaterfalls.description=Eski arayüz uyumluluğu için saklanır. Nokta-yolu motoru ek şelale havuzu üretmez; doğal arazi düşüşlerini izler.
// script.param.enableWaterfalls.default=true
// script.param.enableWaterfalls.optional=false

// script.param.shallowGraniteDetail.type=boolean
// script.param.shallowGraniteDetail.displayName=Sığ yatak granit detayı
// script.param.shallowGraniteDetail.description=Sığ su altındaki seçili taban ve kıyıları granite çevirir. Exporttaki 2x2x2 yumuşatıcı granite slab/stair yönünü güvenli olarak üretir.
// script.param.shallowGraniteDetail.default=false
// script.param.shallowGraniteDetail.optional=false

// script.param.shallowGraniteMaxDepth.type=float
// script.param.shallowGraniteMaxDepth.displayName=En fazla sığ su derinliği
// script.param.shallowGraniteMaxDepth.description=Yalnızca bu derinlikte veya daha sığ su altına granite uygulanır.
// script.param.shallowGraniteMaxDepth.default=1.25
// script.param.shallowGraniteMaxDepth.optional=false

// script.param.shallowGraniteFloorCoverage.type=float
// script.param.shallowGraniteFloorCoverage.displayName=Taban granit oranı
// script.param.shallowGraniteFloorCoverage.description=Sığ yatağın iç kısmında granite uygulanma oranı (0-1).
// script.param.shallowGraniteFloorCoverage.default=0.20
// script.param.shallowGraniteFloorCoverage.optional=false

// script.param.shallowGraniteBankCoverage.type=float
// script.param.shallowGraniteBankCoverage.displayName=Kıyı granit oranı
// script.param.shallowGraniteBankCoverage.description=Sığ yatağın suya yakın kenarında granite uygulanma oranı (0-1).
// script.param.shallowGraniteBankCoverage.default=0.40
// script.param.shallowGraniteBankCoverage.optional=false

// script.param.shallowGraniteClusterSize.type=integer
// script.param.shallowGraniteClusterSize.displayName=Granit küme boyutu
// script.param.shallowGraniteClusterSize.description=Benzer granite hücrelerinin birlikte oluştuğu ortalama blok ölçeği.
// script.param.shallowGraniteClusterSize.default=5
// script.param.shallowGraniteClusterSize.optional=false

// script.param.shallowGraniteSeed.type=integer
// script.param.shallowGraniteSeed.displayName=Granit seed
// script.param.shallowGraniteSeed.description=Aynı seed aynı granit dağılımını verir.
// script.param.shallowGraniteSeed.default=1337
// script.param.shallowGraniteSeed.optional=false

// Get parameters
var riverLayerName = params['riverLayer'];
var riverWidth = params['riverWidth'];
var riverDepth = params['riverDepth'];
var riverPreset = getRealisticRiverPreset(params['presetId']);
if (riverPreset != null) {
    riverWidth = riverPreset.endWidth;
    riverDepth = riverPreset.maxDepth;
}
var tributaryCount = params['tributaryCount'];
var tributaryRandomness = params['tributaryRandomness'];
var waterLevel = params['waterLevel'];
var bankSmoothing = params['bankSmoothing'];
var enableWaterfalls = params['enableWaterfalls'];
var shallowGraniteDetail = params['shallowGraniteDetail'];
var shallowGraniteMaxDepth = params['shallowGraniteMaxDepth'];
var shallowGraniteFloorCoverage = params['shallowGraniteFloorCoverage'];
var shallowGraniteBankCoverage = params['shallowGraniteBankCoverage'];
var shallowGraniteClusterSize = params['shallowGraniteClusterSize'];
var shallowGraniteSeed = params['shallowGraniteSeed'];

// Initialize
var app = org.pepsoft.worldpainter.App.getInstance();
var dimension = app.dimension;
if (dimension == null) throw new Error('Nehir oluşturmak için önce bir dünya açın.');
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
var shallowGraniteCells = 0;
var shallowGraniteFloorCells = 0;
var shallowGraniteBankCells = 0;
var shallowGraniteMarked = {};
var shallowGraniteCandidates = {};
var graniteTerrain = org.pepsoft.worldpainter.Terrain.GRANITE;

if (shallowGraniteDetail == null) {
    shallowGraniteDetail = false;
}
if (shallowGraniteMaxDepth == null) {
    shallowGraniteMaxDepth = 1.25;
}
if (shallowGraniteFloorCoverage == null) {
    shallowGraniteFloorCoverage = 0.20;
}
if (shallowGraniteBankCoverage == null) {
    shallowGraniteBankCoverage = 0.40;
}
if (shallowGraniteClusterSize == null) {
    shallowGraniteClusterSize = 5;
}
if (shallowGraniteSeed == null) {
    shallowGraniteSeed = 1337;
}
shallowGraniteMaxDepth = riverNumber(shallowGraniteMaxDepth, 'Granit derinliği', 0.25, 512, false);
shallowGraniteFloorCoverage = riverNumber(shallowGraniteFloorCoverage, 'Taban granit oranı', 0, 1, false);
shallowGraniteBankCoverage = riverNumber(shallowGraniteBankCoverage, 'Kıyı granit oranı', 0, 1, false);
shallowGraniteClusterSize = riverNumber(shallowGraniteClusterSize, 'Granit küme boyutu', 1, 4096, true);
shallowGraniteSeed = riverNumber(shallowGraniteSeed, 'Granit seed', -2147483648, 2147483647, true);
riverWidth = riverNumber(riverWidth, 'Nehir genişliği', 2, 128, false);
riverDepth = riverNumber(riverDepth, 'Nehir derinliği', 0.25, 64, false);
tributaryCount = riverNumber(tributaryCount, 'Yan kol sayısı', 0, 128, true);
tributaryRandomness = riverNumber(tributaryRandomness, 'Yan kol rastgeleliği', 0, 1, false);
waterLevel = riverNumber(waterLevel, 'Su seviyesi', dimension.getMinHeight(), dimension.getMaxHeight() - 1, true);

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
    throw new Error("River Designer stopped because the selected input is invalid");
}

print("Layer bulundu: " + riverLayerName);

// Find all points where the river layer is set
var lineInput = collectRiverLine(dimension, riverLayer, 65536);
var riverPoints = lineInput.points;
var riverMap = lineInput.map;

print("Bulunan nehir çizgisi noktaları: " + riverPoints.length);

if (riverPoints.length === 0) {
    print("HATA: Seçilen layerde hiç işaretlenmiş nokta yok!");
    throw new Error("River Designer stopped because the selected input is invalid");
}

// Kopuk / seyrek noktaları araziye uygun path ile birleştir (Mod 2).
var linkSparseWaypoints = params['linkSparseWaypoints'];
if (linkSparseWaypoints == null) {
    linkSparseWaypoints = true;
}
if (linkSparseWaypoints) {
    var linked = linkSparseWaypointsToPath(riverPoints, riverMap, riverLayer);
    riverPoints = linked.points;
    riverMap = linked.map;
    print("Path birleştirme: " + linked.waypoints + " waypoint → " + riverPoints.length + " bağlı nokta");
}

// Resolve the actual painted connections. The old script simply sorted every painted
// point by screen Y, which broke curved, east-west and branching centre lines.
var lineSelection = resolveDrawnCentreline(riverPoints, riverMap);
var orderedRiverPoints = lineSelection.path;
if (orderedRiverPoints.length < 2) {
    print("HATA: Çizgi en az iki bağlı noktadan oluşmalı!");
    throw new Error("River Designer stopped because the selected input is invalid");
}
if (lineSelection.componentSize < riverPoints.length) {
    print("UYARI: " + (riverPoints.length - lineSelection.componentSize)
        + " kopuk çizgi noktası yok sayıldı. En büyük/yüksek bağlı hat işlendi.");
}

print("Kaynak: (" + lineSelection.source.x + ", " + lineSelection.source.y + ")  Y="
    + round2(dimension.getHeightAt(lineSelection.source.x, lineSelection.source.y)));
print("Çıkış: (" + lineSelection.outlet.x + ", " + lineSelection.outlet.y + ")  Y="
    + round2(dimension.getHeightAt(lineSelection.outlet.x, lineSelection.outlet.y)));
print("Takip edilen merkez hattı: " + orderedRiverPoints.length + " nokta");

// Process main river
if (riverPreset != null) {
    carveShallowNamedLine(orderedRiverPoints);
} else {
processRiver(orderedRiverPoints, true);

// Create tributaries
createTributaries(orderedRiverPoints);

// Each water column is anchored to its final bed. This prevents the old script's
// water-level interpolation from creating suspended curtains across valleys.
stabiliseRiverWater();

// Tributaries can overlap and deepen a cell after the main channel touched it.
// Apply material only after every carve, using the final water and bed levels.
applyShallowGraniteDetail();
} // Named presets must not run the old recarve/smoothing passes afterwards.

print("===========================================");
print("İşlem tamamlandı");
print("Toplam işlenen blok: " + processedBlocks);
print("Oluşturulan nehir: " + riversCreated);
if (shallowGraniteDetail) {
    print("Sığ yatak granite hücreleri: " + shallowGraniteCells
        + " (taban=" + shallowGraniteFloorCells + ", kıyı=" + shallowGraniteBankCells + ")");
}
var elapsed = new Date().getTime() - startTime;
print("Süre: " + elapsed + " ms");
print("===========================================");

// Main river processing function
function checkRiverLineCancel() {
    if (typeof progress !== 'undefined' && progress != null) progress.checkForCancel();
    if (typeof wp !== 'undefined' && typeof wp.checkForInterrupt === 'function') wp.checkForInterrupt();
}

function collectRiverLine(dim, layer, limit) {
    var size = String(layer.getDataSize());
    if (size !== 'BIT' && size !== 'BIT_PER_CHUNK') throw new Error('Nehir çizgisi için bit türünde bir layer seçin.');
    var tiles = Java.from(dim.getTiles().toArray());
    tiles.sort(function(a, b) { return a.getX() - b.getX() || a.getY() - b.getY(); });
    var points = [], map = {};
    for (var t = 0; t < tiles.length; t++) {
        checkRiverLineCancel();
        var tile = tiles[t];
        if (!tile.hasLayer(layer)) continue;
        for (var x = 0; x < 128; x++) {
            checkRiverLineCancel();
            for (var y = 0; y < 128; y++) {
                if (!tile.getBitLayerValue(layer, x, y)) continue;
                if (points.length >= limit) throw new Error('Nehir çizgisi ' + limit + ' nokta sınırını aşıyor; daha ince veya kısa bir çizgi kullanın.');
                var ax = tile.getX() * 128 + x, ay = tile.getY() * 128 + y;
                points.push({x: ax, y: ay});
                map[ax + ',' + ay] = true;
            }
        }
    }
    return {points: points, map: map};
}

function canEditRiverCell(x, y) {
    if (!isFinite(x) || !isFinite(y) || x < -2147483584 || x > 2147483583
            || y < -2147483584 || y > 2147483583) return false;
    if (!dimension.isTilePresent(x >> 7, y >> 7) || !isFinite(dimension.getHeightAt(x, y))) return false;
    var layers = org.pepsoft.worldpainter.layers;
    var guards = [layers.ReadOnly.INSTANCE, layers.NotPresent.INSTANCE, layers.NotPresentBlock.INSTANCE,
        layers.Void.INSTANCE, layers.FloodWithLava.INSTANCE, layers.River.INSTANCE];
    for (var i = 0; i < guards.length; i++) if (dimension.getBitLayerValueAt(guards[i], x, y)) return false;
    return true;
}

function processRiver(orderedPoints, isMainRiver) {
    for (var pi = 0; pi < orderedPoints.length; pi++) {
        checkRiverLineCancel();
        if (!canEditRiverCell(orderedPoints[pi].x, orderedPoints[pi].y)) {
            throw new Error('Nehir çizgisi korunan veya eksik arazi ile çakışıyor; kaynak çizgisini düzeltin.');
        }
    }
    // Snapshot centre heights before a diagonal cross-section touches the next
    // centre; otherwise reading already-carved heights deepens the path again.
    var originalHeights = [];
    if (riverPreset != null) {
        for (var oi = 0; oi < orderedPoints.length; oi++) {
            originalHeights.push(dimension.getHeightAt(orderedPoints[oi].x, orderedPoints[oi].y));
        }
    }
    var waterH = riverPreset == null ? Math.max(waterLevel, dimension.getHeightAt(orderedPoints[0].x, orderedPoints[0].y) - 0.25)
            : originalHeights[0] - 0.25;
    for (var idx = 0; idx < orderedPoints.length; idx++) {
        checkRiverLineCancel();
        var point = orderedPoints[idx];
        var groundHeight = riverPreset == null ? dimension.getHeightAt(point.x, point.y) : originalHeights[idx];
        if (idx > 0) {
            // Water never climbs upstream. Small steps are intentional and become
            // natural rapids; steep steps remain suitable waterfall candidates.
            waterH = riverPreset == null ? Math.max(waterLevel, Math.min(waterH - 0.05, groundHeight - 0.2))
                    : Math.min(waterH, groundHeight - 0.2);
        }
        if (riverPreset != null) {
            waterH = Math.min(waterH, groundHeight - 0.2);
        }
        var tangent = getLineTangent(orderedPoints, idx);
        var width = getRealisticLineWidth(idx, orderedPoints.length);
        carveCrossSection(point.x, point.y, tangent, width, riverDepth, waterH);
    }
    
    riversCreated++;
    
    // Bank smoothing
    if (bankSmoothing) {
        smoothBanks(orderedPoints, Math.floor(riverWidth / 2));
    }
}

function carveCrossSection(cx, cy, tangent, width, depth, waterSurface) {
    if (riverPreset != null) {
        width = Math.min(width, riverPreset.endWidth);
        depth = Math.min(depth, riverPreset.maxDepth);
    }
    var tangentLength = Math.sqrt(tangent.x * tangent.x + tangent.y * tangent.y);
    if (tangentLength === 0) {
        tangent = {x: 1, y: 0};
        tangentLength = 1;
    }
    var normalX = -tangent.y / tangentLength;
    var normalY = tangent.x / tangentLength;
    var halfWidth = Math.max(0.5, (width - (riverPreset == null ? 0 : 1)) / 2.0);
    var sampled = {};
    for (var offset = -Math.ceil(halfWidth); offset <= Math.ceil(halfWidth); offset++) {
        var lateral = Math.abs(offset) / halfWidth;
        if (lateral > 1.0) {
            continue;
        }
        var x = Math.round(cx + normalX * offset);
        var y = Math.round(cy + normalY * offset);
        var key = x + "," + y;
        if (sampled[key] || x < minX || x > maxX || y < minY || y > maxY || !canEditRiverCell(x, y)) {
            continue;
        }
        sampled[key] = true;
        var previousCell = shallowGraniteCandidates[key];
        var originalHeight = riverPreset != null && previousCell != null
                ? previousCell.originalHeight : dimension.getHeightAt(x, y);
        var bankFactor = 1.0 - lateral;
        var localDepth = Math.max(0.5, depth * (0.55 + 0.45 * bankFactor));
        var targetHeight = Math.max(dimension.getMinHeight(), waterSurface - localDepth);
        if (dimension.getHeightAt(x, y) > targetHeight) {
            dimension.setHeightAt(x, y, targetHeight);
        }
        if (dimension.getHeightAt(x, y) <= waterSurface) {
            dimension.setWaterLevelAt(x, y, Math.max(Math.round(waterSurface), Math.ceil(targetHeight) + 1));
        }
        rememberShallowGraniteCandidate(x, y, lateral, originalHeight, waterSurface);
        processedBlocks++;
    }
}

function rememberShallowGraniteCandidate(x, y, lateral, originalHeight, plannedWater) {
    var key = x + "," + y;
    var previous = shallowGraniteCandidates[key];
    if (previous == null) {
        shallowGraniteCandidates[key] = {
            x: x, y: y, minLateral: lateral, maxLateral: lateral,
            surfaceCap: Math.floor(originalHeight), originalHeight: originalHeight,
            plannedWater: plannedWater
        };
    } else {
        // Intersections can be in the middle of one river and at the edge of
        // another. Keep both facts: water follows the inner channel while granite
        // can still favour the visible bank.
        previous.minLateral = Math.min(previous.minLateral, lateral);
        previous.maxLateral = Math.max(previous.maxLateral, lateral);
        previous.surfaceCap = Math.min(previous.surfaceCap, Math.floor(originalHeight));
        previous.plannedWater = Math.min(previous.plannedWater, plannedWater);
    }
}

function stabiliseRiverWater() {
    for (var key in shallowGraniteCandidates) {
        checkRiverLineCancel();
        var candidate = shallowGraniteCandidates[key];
        if (!canEditRiverCell(candidate.x, candidate.y)) continue;
        var bedHeight = dimension.getHeightAt(candidate.x, candidate.y);
        if (candidate.minLateral > 0.85) {
            // The carved lip is dry; clearing its artificial water keeps the bank
            // attached to terrain instead of becoming a wide floating sheet.
            dimension.setWaterLevelAt(candidate.x, candidate.y, Math.floor(bedHeight));
        } else {
            // Keep the requested depth where the original terrain can contain it,
            // while never filling higher than this cell's pre-carve ground level.
            // Sharp descents now become grounded falls, not free-standing walls.
            var level = riverPreset == null ? Math.max(Math.floor(bedHeight) + 1, candidate.surfaceCap)
                    : boundedRiverWaterLevel(bedHeight, candidate.plannedWater, candidate.surfaceCap, riverPreset.maxDepth);
            dimension.setWaterLevelAt(candidate.x, candidate.y, level);
        }
    }
}

function applyShallowGraniteDetail() {
    if (!shallowGraniteDetail) {
        return;
    }
    for (var key in shallowGraniteCandidates) {
        checkRiverLineCancel();
        var candidate = shallowGraniteCandidates[key];
        if (!canEditRiverCell(candidate.x, candidate.y)) continue;
        var actualWaterDepth = Math.max(0, dimension.getWaterLevelAt(candidate.x, candidate.y)
                - dimension.getHeightAt(candidate.x, candidate.y));
        if (shouldPlaceShallowGranite(candidate.x, candidate.y, candidate.maxLateral, actualWaterDepth)) {
            dimension.setTerrainAt(candidate.x, candidate.y, graniteTerrain);
            markShallowGranite(candidate.x, candidate.y, candidate.maxLateral >= 0.55);
        }
    }
}

// River editing deliberately stores full granite terrain. SurfaceSmoother later turns
// this into correctly oriented bottom slabs/stairs and waterlogs partial blocks. Direct
// stair placement here would make turns and diagonals depend on paint order.
function shouldPlaceShallowGranite(x, y, lateral, actualWaterDepth) {
    if (!shallowGraniteDetail || graniteTerrain == null || actualWaterDepth <= 0.05
            || actualWaterDepth > shallowGraniteMaxDepth) {
        return false;
    }
    var coverage = lateral >= 0.55 ? shallowGraniteBankCoverage : shallowGraniteFloorCoverage;
    return clusteredGraniteNoise(x, y) < coverage;
}

function markShallowGranite(x, y, bank) {
    var key = x + "," + y;
    if (shallowGraniteMarked[key]) {
        return;
    }
    shallowGraniteMarked[key] = true;
    shallowGraniteCells++;
    if (bank) {
        shallowGraniteBankCells++;
    } else {
        shallowGraniteFloorCells++;
    }
}

function clusteredGraniteNoise(x, y) {
    var gridX = Math.floor(x / shallowGraniteClusterSize);
    var gridY = Math.floor(y / shallowGraniteClusterSize);
    // Coarse component keeps adjacent granite blocks in natural short stretches;
    // fine component prevents obvious square borders.
    return 0.78 * graniteHash(gridX, gridY, 0) + 0.22 * graniteHash(x, y, 1);
}

function graniteHash(x, y, salt) {
    var n = Math.sin(x * 12.9898 + y * 78.233 + (shallowGraniteSeed + salt * 101) * 37.719) * 43758.5453123;
    return n - Math.floor(n);
}

function clamp01(value) {
    return Math.max(0, Math.min(1, isNaN(value) ? 0 : value));
}

// Keep these values aligned with RiverPreset and the source/automatic script.
function riverNumber(value, label, minimum, maximum, integer) {
    var number = Number(value);
    if (value == null || !isFinite(number) || number < minimum || number > maximum
            || (integer && Math.floor(number) !== number)) {
        throw new Error(label + ': geçerli ' + (integer ? 'tam ' : '') + 'sayı ' + minimum + ' ile ' + maximum + ' arasında olmalı.');
    }
    return number;
}

function getRealisticRiverPreset(id) {
    var definitions = [null,
        { startWidth: 3, endWidth: 6, maxDepth: 0.85 },
        { startWidth: 5, endWidth: 12, maxDepth: 1.10 },
        { startWidth: 8, endWidth: 20, maxDepth: 1.40 },
        { startWidth: 4, endWidth: 10, maxDepth: 2.0 },
        { startWidth: 3, endWidth: 8, maxDepth: 1.0 }];
    var value = id == null || id === '' ? 0 : Number(id);
    if (!isFinite(value) || Math.floor(value) !== value || value < 0 || value >= definitions.length) {
        throw new Error('Geçersiz nehir hazır ayarı; 0 ile 5 arasında bir değer seçin.');
    }
    return definitions[value];
}

function carveShallowNamedLine(points) {
    function coordinate(value) {
        var number = Number(value);
        if (value == null || (typeof value === 'string' && value.replace(/\s/g, '') === '')
                || !isFinite(number) || number < -2147483584 || number > 2147483583) {
            throw new Error('Geçersiz nehir koordinatı: ' + value);
        }
        return Math.round(number);
    }
    if (!points || points.length < 2 || points.length > 65536) throw new Error('Nehir yolu 2 ile 65536 nokta içermeli.');
    if (!isFinite(Number(shallowGraniteSeed))) throw new Error('Nehir seed değeri sonlu bir sayı olmalı.');
    var Carver = Java.type('org.pepsoft.worldpainter.tools.scripts.ShallowRiverCarver');
    var plan = new Carver(dimension, riverPreset.startWidth, riverPreset.endWidth, riverPreset.maxDepth,
        bankSmoothing, shallowGraniteDetail, shallowGraniteSeed, typeof progress === 'undefined' ? null : progress);
    // Explicit waypoints are not moved to another valley. Reject an unsuitable
    // route instead of excavating/filling the surrounding terrain to fit it.
    plan.enableTerrainPreservation();
    var xs = [], ys = [];
    for (var i = 0; i < points.length; i++) {
        if (typeof progress !== 'undefined' && progress != null) progress.checkForCancel();
        if (points[i] == null) throw new Error('Nehir yolunda boş koordinat var.');
        xs.push(coordinate(points[i].x)); ys.push(coordinate(points[i].y));
    }
    if (!plan.addPath(Java.to(xs, 'int[]'), Java.to(ys, 'int[]'))) throw new Error(plan.getLastRejection());
    // This mode has no custom avoid-layer input. Null still validates the
    // complete wet footprint against current protected/missing cells.
    if (!plan.isFootprintAllowed(null)) {
        throw new Error('Son nehir yatağı korunan veya geçersiz bir alana taşıyor. Dünya değiştirilmedi.');
    }
    var result = plan.apply();
    processedBlocks = result.changedCells(); riversCreated = result.paths();
    shallowGraniteCells = result.graniteCells();
    print('Sığ yatak azami toplam kazı: ' + result.maximumCut().toFixed(2) + ' blok; azami yerel dolgu: '
        + result.maximumFill().toFixed(2) + ' blok.');
    print('Nehir exportu — mod: Nokta yolu; yerel 2×2×2 seçeneği: ' + (shallowGraniteDetail ? 'açık' : 'kapalı')
        + '; granit hücresi: ' + result.graniteCells()
        + '; dünya geneli: ' + dimension.getSurfaceSmoothing() + ' (değiştirilmedi).');
    if (shallowGraniteDetail) print('Yerel slab/merdiven yalnız yeni işaretli, ıslak granitte uygun geometride uygulanır; düz yüzey, dik uçurum ve gerekli taşıyıcı bloklar tam kalır.');
}

function getRealisticLineWidth(index, length) {
    if (riverPreset == null) {
        return riverWidth;
    }
    var progress = clamp01(index / Math.max(1, length - 1));
    var smooth = progress * progress * (3 - 2 * progress);
    return riverPreset.startWidth + smooth * (riverPreset.endWidth - riverPreset.startWidth);
}

function boundedRiverWaterLevel(bed, plannedWater, originalCap, maximumDepth) {
    // Dry fringe cells stay dry; never raise a surface just to guarantee water.
    return Math.max(Math.floor(bed), Math.floor(Math.min(plannedWater, originalCap, bed + maximumDepth)));
}

/**
 * Clusters sparse painted dots into waypoints, orders high→low, and paints
 * terrain-following segments so resolveDrawnCentreline sees one connected path.
 */
function linkSparseWaypointsToPath(points, map, layer) {
    var clusters = clusterWaypoints(points, 3);
    if (clusters.length === 0) {
        return {points: points, map: map, waypoints: 0};
    }
    if (clusters.length === 1 && points.length >= 2 && isMostlyConnected(points, map)) {
        return {points: points, map: map, waypoints: clusters.length};
    }

    clusters.sort(function (a, b) {
        return b.height - a.height;
    });

    if (clusters.length < 2) {
        if (points.length < 2) {
            print("HATA: En az iki rota noktası boyayın (veya Mod 3 Kaynak kullanın).");
            throw new Error("River Designer stopped because the selected input is invalid");
        }
        return {points: points, map: map, waypoints: clusters.length};
    }

    var ordered = [clusters[0]];
    var remaining = clusters.slice(1);
    while (remaining.length > 0) {
        var last = ordered[ordered.length - 1];
        var bestIndex = 0;
        var bestScore = Number.MAX_VALUE;
        for (var i = 0; i < remaining.length; i++) {
            var candidate = remaining[i];
            var dx = candidate.x - last.x;
            var dy = candidate.y - last.y;
            var dist = Math.sqrt(dx * dx + dy * dy);
            var climb = Math.max(0, candidate.height - last.height);
            var score = dist + climb * 8;
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        ordered.push(remaining[bestIndex]);
        remaining.splice(bestIndex, 1);
    }

    for (var s = 0; s < ordered.length - 1; s++) {
        paintTerrainPath(ordered[s], ordered[s + 1], map, points, layer);
    }

    // Deduplicate points list from map keys
    var rebuilt = [];
    for (var key in map) {
        if (map.hasOwnProperty(key) && map[key]) {
            var parts = key.split(",");
            rebuilt.push({x: parseInt(parts[0], 10), y: parseInt(parts[1], 10)});
        }
    }
    return {points: rebuilt, map: map, waypoints: ordered.length};
}

function clusterWaypoints(points, radius) {
    var used = {};
    var pointIndex = indexPoints(points);
    var clusters = [];
    for (var i = 0; i < points.length; i++) {
        if ((i & 127) === 0) checkRiverLineCancel();
        var p = points[i];
        var key = lineKey(p.x, p.y);
        if (used[key]) {
            continue;
        }
        var queue = [p];
        used[key] = true;
        var sumX = 0;
        var sumY = 0;
        var count = 0;
        var maxH = -1e9;
        for (var head = 0; head < queue.length; head++) {
            if ((head & 127) === 0) checkRiverLineCancel();
            var cur = queue[head];
            sumX += cur.x;
            sumY += cur.y;
            count++;
            var h = dimension.getHeightAt(cur.x, cur.y);
            if (h > maxH) {
                maxH = h;
            }
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dy = -radius; dy <= radius; dy++) {
                    if (dx === 0 && dy === 0) {
                        continue;
                    }
                    var nx = cur.x + dx;
                    var ny = cur.y + dy;
                    var nKey = lineKey(nx, ny);
                    if (!used[nKey] && pointIndex[nKey]) {
                        used[nKey] = true;
                        queue.push({x: nx, y: ny});
                    }
                }
            }
        }
        clusters.push({
            x: Math.round(sumX / count),
            y: Math.round(sumY / count),
            height: maxH
        });
        if (clusters.length > 1024) throw new Error('Çok fazla ayrık waypoint var; en fazla 1024 nokta kümesi kullanın.');
    }
    return clusters;
}

function mapHas(points, x, y) {
    for (var i = 0; i < points.length; i++) {
        if (points[i].x === x && points[i].y === y) {
            return true;
        }
    }
    return false;
}

function isMostlyConnected(points, map) {
    if (points.length === 0) {
        return true;
    }
    var visited = {};
    var component = collectLineComponent(points[0], map, visited, indexPoints(points));
    return component.length >= Math.max(2, Math.floor(points.length * 0.85));
}

function indexPoints(points) {
    var byKey = {};
    for (var i = 0; i < points.length; i++) {
        byKey[lineKey(points[i].x, points[i].y)] = points[i];
    }
    return byKey;
}

function paintTerrainPath(from, to, map, points, layer) {
    var x = from.x;
    var y = from.y;
    var guard = 0;
    var maxSteps = Math.max(64, Math.floor(Math.abs(to.x - from.x) + Math.abs(to.y - from.y)) * 8);
    markPathCell(x, y, map, points, layer);
    while ((x !== to.x || y !== to.y) && guard < maxSteps) {
        checkRiverLineCancel();
        guard++;
        var best = null;
        var bestScore = Number.MAX_VALUE;
        for (var dx = -1; dx <= 1; dx++) {
            for (var dy = -1; dy <= 1; dy++) {
                if (dx === 0 && dy === 0) {
                    continue;
                }
                var nx = x + dx;
                var ny = y + dy;
                if (nx < minX || ny < minY || nx > maxX || ny > maxY || !canEditRiverCell(nx, ny)) {
                    continue;
                }
                var dist = Math.abs(nx - to.x) + Math.abs(ny - to.y);
                var climb = Math.max(0, dimension.getHeightAt(nx, ny) - dimension.getHeightAt(x, y));
                var score = dist + climb * 6;
                // Prefer stepping closer to target
                if (dist > Math.abs(x - to.x) + Math.abs(y - to.y)) {
                    score += 2;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = {x: nx, y: ny};
                }
            }
        }
        if (!best) {
            break;
        }
        // Prevent infinite oscillation: force Bresenham-ish progress every few steps
        if (guard % 12 === 0) {
            best = {
                x: x + (to.x === x ? 0 : (to.x > x ? 1 : -1)),
                y: y + (to.y === y ? 0 : (to.y > y ? 1 : -1))
            };
        }
        x = best.x;
        y = best.y;
        markPathCell(x, y, map, points, layer);
    }
    markPathCell(to.x, to.y, map, points, layer);
}

function markPathCell(x, y, map, points, layer) {
    if (!canEditRiverCell(x, y)) throw new Error('Nehir yolu korunan, eksik veya lavlı arazi üzerinden geçiyor.');
    var key = lineKey(x, y);
    if (!map[key]) {
        if (points.length >= 65536) throw new Error('Bağlı nehir yolu 65536 nokta sınırını aşıyor.');
        map[key] = true;
        points.push({x: x, y: y});
        // Planning uses only this temporary map. Never alter the user's marker
        // layer before the whole path has passed carver validation.
    }
}

function getLineTangent(points, index) {
    var previous = points[Math.max(0, index - 1)];
    var next = points[Math.min(points.length - 1, index + 1)];
    var x = next.x - previous.x;
    var y = next.y - previous.y;
    return (x === 0 && y === 0) ? {x: 1, y: 0} : {x: x, y: y};
}

function resolveDrawnCentreline(points, map) {
    var pointByKey = {};
    for (var i = 0; i < points.length; i++) {
        pointByKey[lineKey(points[i].x, points[i].y)] = points[i];
    }
    var visited = {};
    var selected = [];
    for (var i = 0; i < points.length; i++) {
        var point = points[i];
        var key = lineKey(point.x, point.y);
        if (visited[key]) {
            continue;
        }
        var component = collectLineComponent(point, map, visited, pointByKey);
        if (component.length > selected.length
                || (component.length === selected.length && highestLinePoint(component).height > highestLinePoint(selected).height)) {
            selected = component;
        }
    }

    // A ridge or dip inside a drawn line is not an endpoint. Preserve the
    // complete stroke when its topology identifies two ends; only use height
    // to orient the stroke. Branches/closed loops retain the legacy policy
    // until explicit branch and start/end editing is available.
    var endpoints = [];
    for (var i = 0; i < selected.length; i++) {
        if ((i & 127) === 0) checkRiverLineCancel();
        if (lineNeighbours(selected[i], map, pointByKey).length === 1) {
            endpoints.push(selected[i]);
        }
    }
    var limits = endpoints.length === 2 ? endpoints : selected;
    var source = highestLinePoint(limits).point;
    var outlet = lowestLinePoint(limits, source).point;
    var path = findConnectedLinePath(source, outlet, map, pointByKey);
    return {path: path, source: source, outlet: outlet, componentSize: selected.length};
}

function collectLineComponent(start, map, visited, pointByKey) {
    var queue = [start];
    var component = [];
    visited[lineKey(start.x, start.y)] = true;
    for (var head = 0; head < queue.length; head++) {
        if ((head & 127) === 0) checkRiverLineCancel();
        var point = queue[head];
        component.push(point);
        var neighbours = lineNeighbours(point, map, pointByKey);
        for (var i = 0; i < neighbours.length; i++) {
            var key = lineKey(neighbours[i].x, neighbours[i].y);
            if (!visited[key]) {
                visited[key] = true;
                queue.push(neighbours[i]);
            }
        }
    }
    return component;
}

function findConnectedLinePath(source, outlet, map, pointByKey) {
    var sourceKey = lineKey(source.x, source.y);
    var outletKey = lineKey(outlet.x, outlet.y);
    var queue = [source];
    var seen = {};
    var parent = {};
    seen[sourceKey] = true;
    for (var head = 0; head < queue.length; head++) {
        if ((head & 127) === 0) checkRiverLineCancel();
        var point = queue[head];
        var currentKey = lineKey(point.x, point.y);
        if (currentKey === outletKey) {
            break;
        }
        var neighbours = lineNeighbours(point, map, pointByKey);
        for (var i = 0; i < neighbours.length; i++) {
            var next = neighbours[i];
            var nextKey = lineKey(next.x, next.y);
            if (!seen[nextKey]) {
                seen[nextKey] = true;
                parent[nextKey] = currentKey;
                queue.push(next);
            }
        }
    }
    if (!seen[outletKey]) {
        return [];
    }
    var path = [];
    var key = outletKey;
    while (key != null) {
        path.push(pointByKey[key]);
        key = parent[key];
    }
    path.reverse();
    return path;
}

function lineNeighbours(point, map, pointByKey) {
    var result = [];
    for (var dx = -1; dx <= 1; dx++) {
        for (var dy = -1; dy <= 1; dy++) {
            if (dx === 0 && dy === 0) {
                continue;
            }
            // Prefer the painted orthogonal corner over a diagonal shortcut.
            // True diagonal strokes still connect when neither bridge exists.
            if (dx !== 0 && dy !== 0
                    && (map[lineKey(point.x + dx, point.y)] || map[lineKey(point.x, point.y + dy)])) {
                continue;
            }
            var key = lineKey(point.x + dx, point.y + dy);
            if (map[key]) {
                result.push(pointByKey[key]);
            }
        }
    }
    return result;
}

function highestLinePoint(points) {
    var selected = points[0];
    var height = dimension.getHeightAt(selected.x, selected.y);
    for (var i = 1; i < points.length; i++) {
        var candidateHeight = dimension.getHeightAt(points[i].x, points[i].y);
        if (candidateHeight > height) {
            selected = points[i];
            height = candidateHeight;
        }
    }
    return {point: selected, height: height};
}

function lowestLinePoint(points, except) {
    var selected = null;
    var height = Number.MAX_VALUE;
    for (var i = 0; i < points.length; i++) {
        if (points[i].x === except.x && points[i].y === except.y) {
            continue;
        }
        var candidateHeight = dimension.getHeightAt(points[i].x, points[i].y);
        if (candidateHeight < height) {
            selected = points[i];
            height = candidateHeight;
        }
    }
    return {point: selected || except, height: height};
}

function lineKey(x, y) {
    return x + "," + y;
}

function round2(value) {
    return Math.round(value * 100) / 100;
}

// Create tributary rivers
function createTributaries(mainRiverPoints) {
    print("Yan kollar oluşturuluyor (" + tributaryCount + " tane)...");
    
    for (var t = 0; t < tributaryCount; t++) {
        checkRiverLineCancel();
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
                var currentWidth = riverPreset == null ? Math.max(2, Math.floor(riverWidth * (1 - progress * 0.8)))
                        : Math.max(2, getRealisticLineWidth(p, path.length) * 0.5);
                var currentDepth = riverPreset == null ? riverDepth * (1 - progress * 0.5)
                        : riverDepth * (0.5 + progress * 0.25);
                
                drawRiverSegment(point.x, point.y, currentWidth, currentDepth, getLineTangent(path, p));
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
function drawRiverSegment(cx, cy, width, depth, tangent) {
    var surface = Math.max(waterLevel, dimension.getHeightAt(cx, cy) - 0.25);
    carveCrossSection(cx, cy, tangent || {x: 1, y: 0}, width, depth, surface);
}

// Smooth river banks
function smoothBanks(riverPoints, halfWidth) {
    for (var p = 0; p < riverPoints.length; p++) {
        var point = riverPoints[p];
        var tangent = getLineTangent(riverPoints, p);
        var length = Math.sqrt(tangent.x * tangent.x + tangent.y * tangent.y);
        var normalX = -tangent.y / length;
        var normalY = tangent.x / length;
        var localHalfWidth = riverPreset == null ? halfWidth : Math.floor((getRealisticLineWidth(p, riverPoints.length) - 1) / 2);
        for (var side = -1; side <= 1; side += 2) {
            for (var offset = localHalfWidth + 1; offset <= localHalfWidth + 2; offset++) {
                smoothBankCell(Math.round(point.x + normalX * offset * side), Math.round(point.y + normalY * offset * side));
            }
        }
    }
}

function smoothBankCell(x, y) {
    if (x < minX || x > maxX || y < minY || y > maxY || !canEditRiverCell(x, y)) {
        return;
    }
    var heights = [];
    for (var dx = -1; dx <= 1; dx++) {
        for (var dy = -1; dy <= 1; dy++) {
            var nx = x + dx;
            var ny = y + dy;
            if (nx >= minX && nx <= maxX && ny >= minY && ny <= maxY && canEditRiverCell(nx, ny)) {
                heights.push(dimension.getHeightAt(nx, ny));
            }
        }
    }
    heights.sort(function(a, b) { return a - b; });
    var current = dimension.getHeightAt(x, y);
    var median = heights[Math.floor(heights.length / 2)];
    if (Math.abs(current - median) > 1) {
        dimension.setHeightAt(x, y, (current + median) / 2.0);
    }
}

print("Script başarıyla tamamlandı!");
