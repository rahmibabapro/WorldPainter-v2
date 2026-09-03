//-- get info on delimiter here: https://github.com/Captain-Chaos/WorldPainter/blob/219f7eb1402e49d9c79fed72799c82503385d669/WorldPainter/WPGUI/src/test/resources/descriptortest.js

// script.description=Klasik modda nehir baslangicini bir layer ile verebilir veya rastgele baslangic sayisi secebilirsin.\n\nDelta modunda sadece ana nehir sayisini secersin; buyuk ana nehirler ve onlara baglanan ince kollar otomatik olusur.\n\nIsteyenler icin selale modu da vardir.

// script.name= River Script -- by sijmen_v_b.

// script.param.riverMode.type=integer
// script.param.riverMode.description=0=Klasik, 1=Delta modu
// script.param.riverMode.displayName=Nehir modu (0/1)
// script.param.riverMode.default=0
// script.param.riverMode.optional=false

// script.param.riverLayoutPreset.type=integer
// script.param.riverLayoutPreset.description=0=otomatik, 1=Border Princes referans haritasi benzeri ana korfez ve nehir yerlesimi.
// script.param.riverLayoutPreset.displayName=Nehir yerlesim preset (0/1)
// script.param.riverLayoutPreset.default=1
// script.param.riverLayoutPreset.optional=false

// script.param.modeRiverCount.type=integer
// script.param.modeRiverCount.description=Delta modunda ana nehir sayisi. Border Princes benzeri harita icin 4-7 arasi daha dogal sonuc verir.
// script.param.modeRiverCount.displayName=Delta ana nehir sayisi
// script.param.modeRiverCount.default=6
// script.param.modeRiverCount.optional=false

// script.param.manualStartCoords.type=string
// script.param.manualStartCoords.description=Elle baslangic koordinatlari. Format: x,y; x,y; x,y. Doluysa klasik moda gecer ve bu noktalardan nehir uretir.
// script.param.manualStartCoords.displayName=Elle baslangic koordinatlari
// script.param.manualStartCoords.default=
// script.param.manualStartCoords.optional=true

// script.param.manualSourceLayer.type=string
// script.param.manualSourceLayer.description=Dag kaynak noktalarini boyadigin layer adi. Doluysa klasik moda gecer ve bu noktalardan birlesen drenaj agi uretir.
// script.param.manualSourceLayer.displayName=Elle kaynak layer adi
// script.param.manualSourceLayer.default=
// script.param.manualSourceLayer.optional=true

// script.param.manualSourceTerrain.type=string
// script.param.manualSourceTerrain.description=Dag kaynaklarini isaretlemek icin kullanilan custom terrain adi. Varsayilan: river. Bu terrain boyanan piksellerden kaynak baslatir.
// script.param.manualSourceTerrain.displayName=Elle kaynak terrain adi
// script.param.manualSourceTerrain.default=river
// script.param.manualSourceTerrain.optional=true

// script.param.deltaSeaLevel.type=float
// script.param.deltaSeaLevel.description=Delta modunda nehirlerin inmeye calistigi hedef seviye (ornek: 0).
// script.param.deltaSeaLevel.displayName=Delta hedef deniz seviyesi
// script.param.deltaSeaLevel.default=-1
// script.param.deltaSeaLevel.optional=false

// script.param.styleProfile.type=integer
// script.param.styleProfile.description=0=manual, 1=soft natural valleys, 2=dramatic but controlled, 3=realistic (hydrology, meanders, floodplain).
// script.param.styleProfile.displayName=Gorunum profili (0/1/2/3)
// script.param.styleProfile.default=1
// script.param.styleProfile.optional=false

// script.param.enableWaterfalls.type=boolean
// script.param.enableWaterfalls.description=Enable natural waterfall steps where steep drops exist.
// script.param.enableWaterfalls.displayName=Selaleleri etkinlestir
// script.param.enableWaterfalls.default=true

// script.param.disableBranching.type=boolean
// script.param.disableBranching.description=Delta modunda yan kol ve alt kol olusumunu kapatir.
// script.param.disableBranching.displayName=Dallanmayi kapat
// script.param.disableBranching.default=false

// script.param.avoidLayer.type=string
// script.param.avoidLayer.displayName=Kacinilacak layer adi
// script.param.avoidLayer.description=The path finding for the rivers wil not cross this layer.
// script.param.avoidLayer.optional=true
// script.param.avoidLayer.default=avoid

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


//################################################################# \/ priority que \/ #################################################################

function Candidate(weight, x, y, dist) {
	this.weight = weight;
	this.x = x
	this.y = y
	this.dist = dist
}


Candidate.prototype.getWeight = function () {
	return this.weight;
}


Candidate.prototype.setWeight = function (value) {
	this.weight = value;
}


function PriorityQueue() {
	this.values = []
}

PriorityQueue.prototype.add = function (element) {
	this.values.push(element);
	var index = this.values.length - 1;
	var current = this.values[index];

	while (index > 0) {
		var parentIndex = Math.floor((index - 1) / 2);
		var parent = this.values[parentIndex];

		if (parent.weight >= current.weight) {
			this.values[parentIndex] = current;
			this.values[index] = parent;
			index = parentIndex;
		} else break;
	}
}

PriorityQueue.prototype.poll = function () {
	if (this.values.length === 0) {
		return null;
	}

	var max = this.values[0];
	var end = this.values.pop();
	if (this.values.length > 0) {
		this.values[0] = end;
	}

	var index = 0;
	var length = this.values.length;
	var current = this.values[0];
	while (true) {
		var leftChildIndex = 2 * index + 1;
		var rightChildIndex = 2 * index + 2;
		var leftChild, rightChild;
		var swap = null;

		if (leftChildIndex < length) {
			leftChild = this.values[leftChildIndex];
			if (leftChild.weight < current.weight) swap = leftChildIndex;
		}
		if (rightChildIndex < length) {
			rightChild = this.values[rightChildIndex];
			if (
				(swap === null && rightChild.weight < current.weight) ||
				(swap !== null && rightChild.weight < leftChild.weight)
			) {
				swap = rightChildIndex;
			}
		}

		if (swap === null) break;
		this.values[index] = this.values[swap];
		this.values[swap] = current;
		index = swap;
	}

	return max;
}

//################################################################# /\ priority que /\ #################################################################

var app = org.pepsoft.worldpainter.App.getInstance();
var d = new Date();
var startTime = d.getTime();
initRandom(this);
noise.seed(Math.random); // make this noise.seed(0); to make the noise consistent
var dimension = app.dimension;
var lookingAtTunnelLayer = false;
var report = ""; //stores all the information to be displayed 
var tunnelLayer = null
if (dimension.getAnchor().role == org.pepsoft.worldpainter.Dimension.Role.CAVE_FLOOR) {
	tunnelLayer = org.pepsoft.worldpainter.layers.tunnel.TunnelLayer.find(dimension);
	lookingAtTunnelLayer = true;
	print("Generating River in Tunnel Layer. (Make sure there is water for the river to end in!)")
	report += "Generating River in Tunnel Layer. (Make sure there is water for the river to end in!)\n"
}
var surfaceDimension = world.getDimension(dimension.getAnchor().dim);
var scale = 100;
extent = dimension.getExtent();
worldWidth = extent.getWidth() * 128;
worldHeight = extent.getHeight() * 128;
var minX = dimension.getLowestX() * 128;
var minY = dimension.getLowestY() * 128;
var thick = false;
var maskOn = 0;
var count = 0;
var numberOfRivers = 0;
var waterLevel = 62;
var runScript = true; //set to false to stop the script from running. used when issuing the help command.
var startPositionLayer = "";
var randomStartingPositions = 0;
var riverMode = params['riverMode'];
var riverLayoutPreset = params['riverLayoutPreset'];
var modeRiverCount = params['modeRiverCount'];
var manualStartCoords = params['manualStartCoords'];
var manualSourceLayer = params['manualSourceLayer'];
var manualSourceTerrain = params['manualSourceTerrain'];
var deltaSeaLevel = params['deltaSeaLevel'];
var shallowGraniteDetail = params['shallowGraniteDetail'];
var shallowGraniteMaxDepth = params['shallowGraniteMaxDepth'];
var shallowGraniteFloorCoverage = params['shallowGraniteFloorCoverage'];
var shallowGraniteBankCoverage = params['shallowGraniteBankCoverage'];
var shallowGraniteClusterSize = params['shallowGraniteClusterSize'];
var shallowGraniteSeed = params['shallowGraniteSeed'];
var randomness = 1000;
var noiseSize = 100;
var endWidth = 18;
var startWidth = 5;
var riverDepth = 0.14;
var onlyFlowDown = true;// determines if the river will only be allowed to go down and therefore
var lavaMode = false;// determines if the river will only be allowed to go down and therefore
var minRiverLength = 300;// removes rivers shorter than this.
var bankSmoothing = true;// smooth steep river bank walls
var maxBankWallHeight = 2.5;// maximum wall height before smoothing starts
var bankSmoothingIterations = 2;// number of smoothing passes
var styleProfile = params['styleProfile'];// 0=manual, 1=soft natural, 2=dramatic controlled
var enableWaterfalls = params['enableWaterfalls'];// generate waterfalls on steep drops
var disableBranching = params['disableBranching'];// disable tributary/sub-branch generation in delta mode
var waterfallChance = 0.35;// chance to keep waterfall candidate
var minWaterfallDrop = 2.75;// minimum height drop for waterfall
var maxWaterfallsPerRiver = 2;// max waterfalls per river
var waterfallPoolDepth = 2.0;// extra carving below waterfall
var baseWaterfallPoolDepth = waterfallPoolDepth;
var maxChangeSlope = 0.1; // the maximum allowed change in slope.
var baseRiverDepth = riverDepth;
var baseMaxChangeSlope = maxChangeSlope;
var baseDykeSize = 0;
var MaxOrigins = 1000;// a limit on the starting positions that will be processed to prevent accidental masks that are not 1 pixel.
var lavaLayer = org.pepsoft.worldpainter.layers.FloodWithLava.INSTANCE
var HashMap = Java.type('java.util.HashMap');
var maskMap = new HashMap(); //stores [x,y,width_of_river]
var newWaterMap = new HashMap();//stores [x,y,[new_height1,...]]
var riverWaterCapMap = new HashMap();// pre-carve terrain height per generated river cell
var randomMap = new HashMap();// used to give the same random values each time.
var waterfallMap = new HashMap();// stores [x,y,strength]
var junctionBoostMap = new HashMap();// stores [x,y,strength] for natural confluence widening
var confluenceMap = new HashMap();// stores [x,y,strength] for junction depth carving
var manualSourceTerrainHasPaintedPixels = false;
var foundPathMaskData = [];
var pathWaterSurfaceMap = new HashMap();// stores target water level per path centre block
var classicHydroModel = null;
var classicMeanderStrength = 0.6;
var dischargeWidthBlendClassic = 0.65;
var dischargeWidthBlendDelta = 0.4;
var enableFloodplain = false;
var enableBendAsymmetry = false;
var fantasyMapRiverStyle = true;// smoother, fewer trunk rivers with thin tributaries
var riverBoundaryMargin = Math.max(64, Math.min(220, parseInt(Math.min(worldWidth, worldHeight) * 0.045)));
var interruptProbeCounter = 0;
var shallowGraniteCells = 0;
var shallowGraniteFloorCells = 0;
var shallowGraniteBankCells = 0;
var shallowGraniteMarked = new HashMap();

function checkForAbort(forceCheck) {
	interruptProbeCounter++;
	if (!forceCheck && (interruptProbeCounter % 2048 != 0)) {
		return;
	}
	if (typeof wp !== "undefined") {
		if (wp.interrupted) {
			throw "Script interrupted by user.";
		}
		if (typeof wp.checkForInterrupt === "function") {
			wp.checkForInterrupt();
		}
	}
}
//create map with all the terrain types. where the keys are the names lowercase without spaces. (use .replaceAll(" ","").toLocaleLowerCase())
var terrainMap = new HashMap();
var terrainEnum = org.pepsoft.worldpainter.Terrain.VALUES

for (var i = 0; i < terrainEnum.length; i++) {
	terrainMap.put(terrainEnum[i].toString().replaceAll(" ", "").toLocaleLowerCase(), terrainEnum[i]) // add the name to the and the terrain to the enum 
	terrainMap.put(terrainEnum[i].getName().replaceAll(" ", "").toLocaleLowerCase(), terrainEnum[i]) // also add the custom name so instead of custom4 you can also use the name of the custom layer.
}
var graniteTerrain = terrainMap.get("granite");

var terrain; //used to store the terrain
var applyRiverTerrain = false; //do not change this value. will be done in the terrain section.  

var layerArr = [];

// load the layer from the GUI! (no longer require the layer to be on the map.) where the keys are the names lowercase without spaces. (use .replaceAll(" ","").toLocaleLowerCase())
var layers = app.getAllLayers();
var layerMap = new HashMap();

for (var i = 0; i < layers.length; i++) {
	layerMap.put(layers[i].getName().replaceAll(" ", "").toLocaleLowerCase(), layers[i])
}
var frostLayer = layerMap.get("Frost".replaceAll(" ", "").toLocaleLowerCase());
var BiomesLayer = org.pepsoft.worldpainter.layers.Biome.INSTANCE;
var frostRiverBiomeId = 11;
var frostBeachBiomeId = 26;
var riverBiomeId = 7;
var beachBiomeId = 16;
var oldNoise = false;// use the old math. random instead of simplex noise.
var riverBraiding = true;
var dykeSize = 0;//size of the dykes generated.
var avoidLayerName = params['avoidLayer'];
if (avoidLayerName == null) {
	avoidLayerName = "";
}
if (bankSmoothing == null) {
	bankSmoothing = true;
}
if (riverMode == null) {
	riverMode = 0;
}
if (riverLayoutPreset == null) {
	riverLayoutPreset = 1;
}
if (modeRiverCount == null) {
	modeRiverCount = 5;
}
if (deltaSeaLevel == null) {
	deltaSeaLevel = 0;
}
if (styleProfile == null) {
	styleProfile = 0;
}
if (enableWaterfalls == null) {
	enableWaterfalls = true;
}
if (disableBranching == null) {
	disableBranching = false;
}
if (manualSourceTerrain == null) {
	manualSourceTerrain = "river";
}
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
shallowGraniteMaxDepth = Math.max(0.25, Number(shallowGraniteMaxDepth));
shallowGraniteFloorCoverage = clampBetweenZeroAndOne(Number(shallowGraniteFloorCoverage));
shallowGraniteBankCoverage = clampBetweenZeroAndOne(Number(shallowGraniteBankCoverage));
shallowGraniteClusterSize = Math.max(1, Math.floor(Number(shallowGraniteClusterSize)));
shallowGraniteSeed = Math.floor(Number(shallowGraniteSeed));

applyStyleProfile(styleProfile);
applyRiverModeSettings();
var avoidLayer = layerMap.get(avoidLayerName.replaceAll(" ", "").toLocaleLowerCase());
//var maskLayer = layerMap.get("mask".replaceAll(" ", "").toLocaleLowerCase());


for (var i = 0; i < arguments.length; i++)//loop trough all arguments
{
	if (arguments[i].toLocaleLowerCase() == "terrain" || arguments[i].toLocaleLowerCase() == "t") {

		if (arguments.length <= i + 1)//if there are not 2 lines following this keyword print an error.
		{
			print("ERROR! name of terrain was expected!");
			report += "ERROR! name of terrain was expected!\n";
		}
		else {
			terrain = terrainMap.get(arguments[i + 1].replaceAll(" ", "").toLocaleLowerCase())

			if (terrain != null) {
				applyRiverTerrain = true;
			} else {
				print("ERROR! can NOT find terrain with name \"" + arguments[i + 1] + "\" !")
				report += "ERROR! can NOT find terrain with name \"" + arguments[i + 1] + "\" !\n";
			}

			i++;
		}
	} else if (arguments[i].toLocaleLowerCase() == "layer" || arguments[i].toLocaleLowerCase() == "l") {

		if (arguments.length <= i + 1)//if there are not 2 lines following this keyword print an error.
		{
			print("ERROR! name of layer was expected!");
			report += "ERROR! name of layer was expected!\n";
		}
		else {
			layer = layerMap.get(arguments[i + 1].replaceAll(" ", "").toLocaleLowerCase())

			if (layer != null) {
				layerArr.push(layer);
			} else {
				print("ERROR! cant find layer with name \"" + arguments[i + 1] + "\" !")
				report += "ERROR! cant find layer with name \"" + arguments[i + 1] + "\" !\n";
			}

			i++;
		}
	}
	else if (arguments[i].toLocaleUpperCase() == "MaxOrigins".toLocaleUpperCase() || arguments[i].toLocaleUpperCase() == "mo".toLocaleUpperCase()) {
		if (arguments.length <= i + 1)//if there are not 1 lines following this keyword print an error.
		{
			print("!number of origins was expected!");
			report += "!number of origins was expected!\n";
		}
		else {
			MaxOrigins = arguments[i + 1];
			print("MaxOrigins has been set to " + MaxOrigins + ". (many origins can take a long time)");
			report += "MaxOrigins has been set to " + MaxOrigins + ". (many origins can take a long time)\n";
			i++;
		}
	}
	else if (arguments[i].toLocaleUpperCase() == "dykeSize".toLocaleUpperCase() || arguments[i].toLocaleUpperCase() == "d".toLocaleUpperCase()) {
		if (arguments.length <= i + 1)//if there are not 1 lines following this keyword print an error.
		{
			print("! dykes size (0-100) was expected!");
			report += "! dykes size (0-100) was expected!\n";
		}
		else {
			dykeSize = arguments[i + 1] / 100;
			print("Dykes size has been set to " + dykeSize + ". (expected value: 0 - 100)");
			report += "Dykes size has been set to " + dykeSize + ". (expected value: 0 - 100)\n";
			i++;
		}
	}
	else if (arguments[i].toLocaleUpperCase() == "oldNoise".toLocaleUpperCase() || arguments[i].toLocaleUpperCase() == "on".toLocaleUpperCase()) {

		oldNoise = true;
		print("Old noise is now used!");
		report += "Old noise is now used!\n";


	}
	else if (arguments[i].toLocaleUpperCase() == "profile".toLocaleUpperCase() || arguments[i].toLocaleUpperCase() == "p".toLocaleUpperCase()) {
		if (arguments.length <= i + 1) {
			print("! profile value was expected! (0, 1, 2 or 3)");
			report += "! profile value was expected! (0, 1, 2 or 3)\n";
		}
		else {
			styleProfile = parseInt(arguments[i + 1]);
			applyStyleProfile(styleProfile);
			print("Style profile has been set to " + styleProfile + ".");
			report += "Style profile has been set to " + styleProfile + ".\n";
			i++;
		}
	}
	else if (arguments[i].toLocaleUpperCase() == "mode".toLocaleUpperCase() || arguments[i].toLocaleUpperCase() == "m") {
		if (arguments.length <= i + 1) {
			print("! mode degeri bekleniyordu! (0 veya 1)");
			report += "! mode degeri bekleniyordu! (0 veya 1)\n";
		}
		else {
			riverMode = parseInt(arguments[i + 1]);
			applyRiverModeSettings();
			print("Nehir modu " + riverMode + " olarak ayarlandi.");
			report += "Nehir modu " + riverMode + " olarak ayarlandi.\n";
			i++;
		}
	}
	else if (arguments[i].toLocaleUpperCase() == "help".toLocaleUpperCase() || arguments[i].toLocaleUpperCase() == "h".toLocaleUpperCase()) {
		//print the help
		print("Komutlar:\n\nterrain\n[terrain_adi]\n\nlayer\n[layer_adi]\n\ndykeSize\n[1-100]\n\nprofile\n[0|1|2|3]\n\nmode\n[0|1]\n\nMode 1 (Delta): Ana nehirler ve onlara akan daha ince kollar otomatik olusturulur.\nProfil 3: Realistic (hidroloji, meander, tas kin ovasi).\n\nKisaltmalar: terrain=t, layer=l, dykeSize=d, profile=p, mode=m\n\nGelismis:\nMaxOrigins (mo), oldNoise (on)");
		runScript = false;
	}
	else if (arguments[i] == "") {
		//do nothing on empty line
	}
	else {
		print(arguments[i], " is not a valid command type \"help\" for help.");
		report += arguments[i] + " is not a valid command type \"help\" for help.\n";
	}
}


if (runScript) {
	var foundPaths = [];
	var pathWidthScales = [];
	var pathProfiles = [];
	foundPathMaskData = [];

	if (riverMode == 1) {
		print("Delta modu aktif: Buyuk ana nehirler uretiliyor" + (disableBranching ? " (dallanma kapali)." : " ve ince kollar uretiliyor..."));
		report += "Delta modu aktif.\n";
		var network = generateDeltaNetwork(modeRiverCount, deltaSeaLevel);
		foundPaths = network.paths;
		pathWidthScales = network.widthScales;
		pathProfiles = network.pathProfiles == null ? [] : network.pathProfiles;
	} else {

	var startPositions = [];
	var manualStarts = parseManualStartCoords(manualStartCoords);
	if (manualStarts.length > 0) {
		print("Elle baslangic koordinatlari alindi: " + manualStarts.length);
		for (var ms = 0; ms < manualStarts.length; ms++) {
			startPositions.push(manualStarts[ms]);
		}
	}

	var selectedStartLayer = hasManualSourceLayer() ? manualSourceLayer : startPositionLayer;
	if (selectedStartLayer != null && ("" + selectedStartLayer).replace(/\s/g, "").length > 0) {
		var originLayer = layerMap.get(selectedStartLayer.replaceAll(" ", "").toLocaleLowerCase());
		if (originLayer == null) {
			print("ERROR! could NOT find layer with name \"" + selectedStartLayer + "\"!")
			report += "ERROR! could NOT find layer with name \"" + selectedStartLayer + "\"!\n";
		} else {

			var minTileX = dimension.getLowestX()
			var minTileY = dimension.getLowestY()
			for (var tileX = 0; tileX < extent.getWidth(); tileX++) {
				checkForAbort(false);
				for (var tileY = 0; tileY < extent.getHeight(); tileY++) {
					//print("haslayer:", dimension.getTile(tileX + minTileX, tileY + minTileY).containsOneOf(originLayer))
					if (dimension.getTile(tileX + minTileX, tileY + minTileY) == null || !dimension.getTile(tileX + minTileX, tileY + minTileY).containsOneOf(originLayer)) {
						continue;
					}
					for (var x = tileX * 128; x < (tileX + 1) * 128; x++) {
						for (var y = tileY * 128; y < (tileY + 1) * 128; y++) {
							if (originLayer.getDataSize().toString() == "BIT") {
								if (dimension.getBitLayerValueAt(originLayer, x + minX, y + minY) && !avoidCondition(x + minX, y + minY)) {//add the start positions
									startPositions.push([x + minX, y + minY]);
								}
							} else {
								if (dimension.getLayerValueAt(originLayer, x + minX, y + minY) > 0 && !avoidCondition(x + minX, y + minY)) {//add the start positions
									startPositions.push([x + minX, y + minY]);
								}
							}
						}
					}
				}
				if (tileX % 10 == 0) {
					print("finding start positions: " + parseInt((tileX) / (extent.getWidth()) * 100 + 0.2) + "%");
				}
			}

		}
	}
	var manualTerrain = getManualSourceTerrain();
	if (manualTerrain != null) {
		var terrainStarts = findManualSourceTerrainPositions(manualTerrain);
		manualSourceTerrainHasPaintedPixels = terrainStarts.length > 0;
		print("Elle kaynak terrain noktasi bulundu: " + terrainStarts.length + " terrain=" + manualSourceTerrain);
		for (var mt = 0; mt < terrainStarts.length; mt++) {
			startPositions.push(terrainStarts[mt]);
		}
	}
	var manualStartsProvided = hasManualDrainageInput();
	print("finding start positions: 100%");



	var maxTries = 50;//how often to retry when starting position is not on land.
	for (var i = 0; i < randomStartingPositions; i++) {
		checkForAbort(false);
		var tries = 0;
		var randomX, randomY;
		do {
			randomX = Math.floor(Math.random() * worldWidth) + minX;
			randomY = Math.floor(Math.random() * worldHeight) + minY;
			tries++;
		} while (dimension.getHeightAt(randomX, randomY) < (dimension.getWaterLevelAt(randomX, randomY) - 0.5) && tries < maxTries && !avoidCondition(randomX, randomY));

		if (tries >= maxTries) {
			continue;
		}
		startPositions.push([randomX, randomY]);
	}

	if (startPositions.length == 0) {
		print("ERROR! No starting positions found!")
		report += "ERROR! No starting positions found!\n";
	}
	if (startPositions.length > MaxOrigins) {
		print("WARNING! More start positions found than the allowed maximum of " + MaxOrigins)
		print("This is to prevent the script taking too long if one accidentally paints more than single pixels. Use the \"MaxOrigins\" command to increase the maximum for big maps.")
		report += "WARNING! More start positions found than the allowed maximum of " + MaxOrigins + "\nThis is to prevent the script taking too long if one accidentally paints more than single pixels. Use the \"MaxOrigins\" command to increase the maximum for big maps.\n";
	}

	var acceptedRiverBlockMap = new HashMap();
	var acceptedStartBlockMap = new HashMap();
	var startSeparationRadius = Math.max(32, Math.min(180, parseInt(Math.min(worldWidth, worldHeight) / Math.max(10, parseInt(Math.sqrt(Math.max(1, Math.min(startPositions.length, MaxOrigins)))) * 1.8))));
	var riverNoMergeRadius = Math.max(5, Math.min(14, parseInt(endWidth * 0.7 + 2)));

	if (manualStartsProvided) {
		var manualNetwork = generateManualDrainageNetwork(startPositions, deltaSeaLevel);
		foundPaths = manualNetwork.paths;
		pathWidthScales = manualNetwork.widthScales;
		pathProfiles = manualNetwork.pathProfiles;
	} else {
	for (var i = 0; i < Math.min(startPositions.length, MaxOrigins); i++) {
		checkForAbort(false);
		print("Finding path for river", i + 1, "of", Math.min(startPositions.length, MaxOrigins), "START POS:", startPositions[i])
		var sx = startPositions[i][0];
		var sy = startPositions[i][1];
		if (!manualStartsProvided && acceptedStartBlockMap.get(toCoordinate(sx, sy)) != null) {
			print("Start point too close to another accepted river; skipping.");
			continue;
		}
		if (riverBraiding) {
			noise.seed(i);
		}
		path = manualStartsProvided ? findGuaranteedManualPath(sx, sy, acceptedRiverBlockMap) : findPath(sx, sy);

		if (!manualStartsProvided && path != null && path.length >= 2 && path[0][0] != null) {
			path = stylizeRiverPath(densifyPath(path, 1), true);
			path = snapPathToLocalValleyFloor(path);
		}

		if (path.length < minRiverLength && !manualStartsProvided) {// if the rive ris shorter than the minimum length.
			//do not add the path
			print("This river is too short and won't be added.");
		} else if (!manualStartsProvided && dimension.getHeightAt(path[0][0], path[0][1]) > dimension.getHeightAt(path[path.length - 1][0], path[path.length - 1][1])) {
			//do not add the path
			print("The river starts lower than it ends and won't be added.");
		} else if (!manualStartsProvided && getPathBlockedRatio(path, acceptedRiverBlockMap, 3) > 0.18) {
			print("This river overlaps too much with existing rivers and won't be added.");
		} else if (path.length < 2 || path[0][0] == null) {
			print("Manual path could not be generated.");
		} else {
			foundPaths.push(path); //add the path
			pathWidthScales.push(1.0);
			pathProfiles.push(makePathProfile("main"));
			addPathToBlockMap(path, acceptedRiverBlockMap, riverNoMergeRadius);
			addPathToBlockMap([[sx, sy]], acceptedStartBlockMap, startSeparationRadius);
		}

	}
	}
	}


	numberOfRivers = foundPaths.length

	if (foundPaths.length > 0 && classicHydroModel == null) {
		print("Building hydrology model for discharge-based river widths...");
		classicHydroModel = buildHydrologyModel(deltaSeaLevel);
	}

	if (riverMode == 0 && foundPaths.length > 1 && !hasManualDrainageInput()) {
		print("Detecting classic river confluences...");
		detectClassicConfluences(foundPaths, pathProfiles);
		stitchNearbyRiverChannels(foundPaths, pathProfiles);
	}


	//make sure the terrain only flows down.
	if (onlyFlowDown) {
		print("making sure rivers only flow down")
		var stable = false;
		var gravityPasses = 0;
		var maxGravityPasses = 1024;
		while (!stable && gravityPasses < maxGravityPasses) {//repeat until stable
			checkForAbort(true);
			print("\trespecting gravity...");
			stable = true;//assume stable
			for (var pathIndex = 0; pathIndex < foundPaths.length; pathIndex++) {
				checkForAbort(false);
				var path = foundPaths[pathIndex];
				var minHeight = 100000000000;
				for (var i = path.length - 1; i >= 0; i--) {
					var x = path[i][0];
					var y = path[i][1];
					if (x == null || y == null) {
						continue;
					}

					height = dimension.getHeightAt(x, y)
					if (height > minHeight) {
						dimension.setHeightAt(x, y, minHeight);
						stable = false;//if not stable repeat.
					} else {
						minHeight = height;
					}
				}
			}
			gravityPasses++;
		}
		if (!stable) {
			print("Warning! Gravity stabilization reached safety limit and stopped early.");
			report += "Warning! Gravity stabilization reached safety limit and stopped early.\n";
		}
	}

	print("Limiting the change of slope")
	limitAllRiverPathSlopes(foundPaths);

	print("Calculating width, slope and depth.")
	for (var pathIndex = 0; pathIndex < foundPaths.length; pathIndex++) {
		checkForAbort(false);
		var path = foundPaths[pathIndex];
		var pathScale = pathWidthScales[pathIndex] == null ? 1.0 : pathWidthScales[pathIndex];
		var pathProfile = pathProfiles[pathIndex] == null ? makePathProfile("main") : pathProfiles[pathIndex];
		var minWaterDepth = dimension.getWaterLevelAt(path[0][0], path[0][1]); //get the water level at the end of the river.
		if (enableWaterfalls) {
			markWaterfallsOnPath(path);
		}

		var minLength = 200;//the minimum apparent length. so shorter rivers will not have the full end width.
		var length = Math.max(path.length, minLength); // length of the river, taken to have a minimum length to prevent short rivers getting really thick.
		var pathMaskData = buildPathMaskData(path, pathProfile, pathScale, length, classicHydroModel);
		smoothPathWidths(pathMaskData);
		enforceClassicDownstreamWidthFloor(pathMaskData, pathProfile);
		applyDownstreamWidthEnvelope(pathMaskData);
		applyConfluenceWidthEnvelope(path, pathMaskData, pathProfile);
		applySegmentTypeModifiers(pathMaskData, pathProfile);
		enforceClassicDownstreamWidthFloor(pathMaskData, pathProfile);
		if (enableWaterfalls) {
			blendWaterfallDepthOnPathData(path, pathProfile, pathMaskData);
		}
		buildPathWaterSurfaceProfile(path, pathMaskData, minWaterDepth);

		for (var i = path.length - 1; i >= 1; i--) {
			var p0 = path[i];
			var p1 = path[i - 1];
			if (p0 == null || p1 == null || p0[0] == null || p1[0] == null || pathMaskData[i] == null || pathMaskData[i - 1] == null) {
				continue;
			}
			var entry0 = pathMaskData[i];
			var entry1 = pathMaskData[i - 1];
			setMaskAlongSegment(p0[0], p0[1], p1[0], p1[1], entry0.width, entry1.width, minWaterDepth, Math.max(entry0.slope, entry1.slope), Math.max(entry0.depthMultiplier, entry1.depthMultiplier), Math.max(entry0.waterfallStrength, entry1.waterfallStrength), pathIndex, i, i - 1);
		}
		if (path.length == 1 && path[0] != null && path[0][0] != null && pathMaskData[0] != null) {
			var solo = pathMaskData[0];
			var soloTangent = getPathTangentAt(path, 0);
			setMaskMaxSize(path[0][0], path[0][1], solo.width, minWaterDepth, solo.slope, solo.depthMultiplier, solo.waterfallStrength, soloTangent, pathIndex, 0);
		}
		foundPathMaskData[pathIndex] = pathMaskData;
		sealRiverPathMask(path, pathProfile, minWaterDepth, pathMaskData, pathIndex);
		addFloodplainShelf(path, pathMaskData, pathProfile, minWaterDepth);
		print("Calculating width, slope and depth for river", pathIndex, "of", numberOfRivers);
		if (pathProfile.type == "feeder" || pathProfile.skipOutletExtension) {
			continue;
		}
		//extend the paths in the direction of the water to make the terrain blend better, make sure this does not break connections to tiny streams. (special only on water step?)
		var pointX = path[0][0];
		var pointY = path[0][1];

		var points = pathFindDown(pointX, pointY, endWidth * 4);

		for (var i = 0; i < points.length; i++) {

			var x = points[i][0];
			var y = points[i][1];

			var profileWidthRange = getProfileWidthRange(pathProfile);
			var maxWidthReached = profileWidthRange.end;

			setMaskMaxSize(x, y, Math.max(1, parseInt(profileWidthRange.start + (i / points.length) * (maxWidthReached - profileWidthRange.start))), minWaterDepth, 0, i / points.length, getWaterfallStrengthAt(x, y))
		}

	}





	//*
	var counter = 0;
	for (key in maskMap) { //for every block on a river (centre) path
		checkForAbort(false);
		var arr = maskMap.get(key);
		var x = arr[0]
		var y = arr[1]
		var distance = arr[2]
		var minWaterDepth = arr[3]
		var slope = arr[4]
		var depthMultiplier = arr[5]
		var waterfallStrength = arr[6]
		var tangentAngle = arr.length > 7 ? arr[7] : null;
		var maskPathIndex = arr.length > 8 ? arr[8] : null;
		var maskPointIndex = arr.length > 9 ? arr[9] : null;

		loadTerrainData(dimension, x, y, distance, minWaterDepth, slope, depthMultiplier, waterfallStrength, tangentAngle, maskPathIndex, maskPointIndex);

		if (counter % 100 == 0) {
			print("loading the terrain data: " + parseInt((counter) / (maskMap.size()) * 100 + 0.2) + "%");
		}
		counter++
	}


	print("Digging out the rivers and setting the terrain and layer(s)")
	var newWaterMapLength = newWaterMap.size()
	counter = 0;
	//set the terrain.
	for (key in newWaterMap) {
		checkForAbort(false);
		var arr = newWaterMap.get(key);
		var x = arr[0];
		var y = arr[1];
		var heightsArr = arr[2];
		var dist = arr[3];// between 0 and 1 where 0 is the center and 1 is the edge of the river.
		var widthArr = arr[4];
		var minWaterDepth = arr[5];
		var slope = arr[6];
		var depthMultiplier = arr[7];
		var waterfallStrength = arr[8];
		length = heightsArr.length
		if (length > 0) {
			var sum = 0;
			for (var i = 0; i < length; i++) {
				sum += heightsArr[i];
			}

			//calculate the width of the river
			widthSum = 0
			for (var i = 0; i < widthArr.length; i++) {
				widthSum += widthArr[i];
			}
			width = widthSum / widthArr.length;

			var localGround = dimension.getHeightAt(x, y);
			riverWaterCapMap.put(toCoordinate(x, y), Math.floor(localGround));
			var overlapCount = heightsArr.length;
			var confluence = getConfluenceStrengthAt(x, y);
			var innerLimit = 0.35;
			if (width > 14) {
				innerLimit = 0.40;
			}
			if (overlapCount > 1 || confluence > 0.15) {
				innerLimit = Math.max(innerLimit, 0.50);
			}
			var referenceHeight = getReferenceHeightForCarve(heightsArr, localGround, dist, innerLimit);
			var bankFreeboard = getBankFreeboard(width, slope);

			//set the water level:
			var newWaterLevel
			var waterEdgeLimit = getWaterEdgeLimit(slope, width);
			var profileWater = pathWaterSurfaceMap.get(toCoordinate(x, y));
			if (!(dist > waterEdgeLimit)) { //if there is a slope and therefore river "guardrails" do not set the water near the edge.
				//slope correction, used to lower the water on steep and broad rivers.
				slopeCorrectionStartWidth = 7; //river width at witch it starts to lower the water.
				slopeCorrectionFallOff = 4; //the slope of the falloff the width will be divided by this.
				slopeCorrection = clampBetweenZeroAndOne((width - slopeCorrectionStartWidth) / slopeCorrectionFallOff) * Math.max(clampBetweenZeroAndOne(slope * 4), 0.25);

				if (profileWater != null && dist < 0.42) {
					newWaterLevel = Math.max(minWaterDepth, Math.min(profileWater, referenceHeight + 0.5));
				} else {
					newWaterLevel = Math.max(referenceHeight - 0.5 - 1.3 * clampBetweenZeroAndOne(2 * slopeCorrection), minWaterDepth);
				}
				var groundCap = localGround - bankFreeboard;
				newWaterLevel = Math.min(newWaterLevel, groundCap);
				newWaterLevel = Math.max(newWaterLevel, minWaterDepth);

				dimension.setWaterLevelAt(x, y, newWaterLevel);
			}
			//set the terrain.
			var factor = 0.0;

			factor = Math.min(dist, 0.99);
			if (width > 12 && factor > 0.58) {
				var bankSoftness = clampBetweenZeroAndOne((width - 12) / 24);
				factor = Math.min(0.99, factor + (0.99 - factor) * 0.20 * bankSoftness);
			}
			if (dist < 0.28) {
				factor = 0;
			}

			var depthFactor = Math.max(0.15, 1 - dist * dist);
			var depth = (width * riverDepth * depthFactor + 1.2) * depthMultiplier;
			var maxBedDepth = Math.min(width * 0.42 + 2.0, styleProfile == 3 ? 9.0 : 7.0);
			depth = Math.min(depth, maxBedDepth);
			if (enableWaterfalls && waterfallStrength > 0) {
				depth += waterfallPoolDepth * waterfallStrength * (1 - 0.75 * factor);
				depth = Math.min(depth, maxBedDepth + waterfallPoolDepth * 0.75);
			}
			var slopeAdjust = getSlopeDepthAdjustments(slope, width);
			depth *= slopeAdjust.depthScale;
			var guardRailRelax = slopeAdjust.guardRailRelax;
			var maskPathIndex = arr[10];
			var maskPointIndex = arr[11];
			var lateralNorm = arr[12];
			var bankAdjust = getBendBankOffset(maskPathIndex, maskPointIndex, dist, width, lateralNorm, x, y);
			depth *= bankAdjust.depthScale;
			if (overlapCount > 1 || confluence > 0) {
				guardRailRelax *= 0.45;
				depth *= 1.0 + 0.05 * Math.max(0, overlapCount - 1) + confluence * 0.06;
				bankAdjust.heightOffset = Math.min(0, bankAdjust.heightOffset);
			}
			var guardRailScale = width > 12 ? 1.6 : 2.4;
			var riverGuardRailsScale = guardRailScale * guardRailRelax * Math.max(0.18 + dykeSize //the minimum dyke size.
				, Math.min(1.2, slope)); //the steepness of the terrain.

			var bedHeight = referenceHeight - depth;
			var newHeight;
			if (dist <= innerLimit) {
				newHeight = bedHeight;
				if (overlapCount > 1 || confluence > 0) {
					newHeight -= 0.10 * Math.max(0, overlapCount - 1) + confluence * 0.14;
				}
			} else {
				var edgeBlend = clampBetweenZeroAndOne((dist - innerLimit) / (1 - innerLimit));
				newHeight = (1 - edgeBlend) * bedHeight + edgeBlend * localGround;
				if (edgeBlend > 0.80 && width > 10 && overlapCount <= 1 && confluence <= 0) {
					var dykeLift = depth * riverGuardRailsScale * 0.04 * ((edgeBlend - 0.80) / 0.20);
					newHeight = Math.min(localGround + dykeLift, newHeight + dykeLift * 0.20);
				}
			}
			if (dist <= innerLimit + 0.10) {
				bankAdjust.heightOffset = Math.min(0, bankAdjust.heightOffset);
			}
			newHeight += bankAdjust.heightOffset;
			if (dist <= 0.58) {
				newHeight = Math.min(newHeight, localGround);
			}
			newHeight = Math.max(newHeight, bedHeight - 0.25);
			newHeight = Math.min(newHeight, localGround + (dist > 0.72 && overlapCount <= 1 && confluence <= 0 ? depth * 0.15 : 0));
			dimension.setHeightAt(x, y, newHeight);



			//add the terrain to the river.
			if (applyRiverTerrain) {
				if ((((dist < 0.4) && depthMultiplier == 1) || (Math.random() * 0.5 + 0.5) * depthMultiplier > dist + (0.25 * (1 - (width / endWidth) * (width / endWidth)))) && newHeight - newWaterLevel < 1.5) {
					dimension.setTerrainAt(x, y, terrain)
				}
			}

			// Keep the source script's normal terrain behaviour intact. This extra pass only
			// marks shallow water cells as full granite; SurfaceSmoother owns safe 2x2x2
			// slab/stair orientation and waterlogging during export.
			var actualWaterDepth = Math.max(0, dimension.getWaterLevelAt(x, y) - newHeight);
			if (shouldPlaceShallowGranite(x, y, dist, actualWaterDepth, slope, waterfallStrength)) {
				dimension.setTerrainAt(x, y, graniteTerrain);
				markShallowGranite(x, y, dist >= 0.45);
			}


			//add layer(s) on river.
			//add lava option.
			if (lavaMode && dimension.getWaterLevelAt(x, y) > minWaterDepth) { //if the new water is not at the same heigh as the end river/ocean.
				dimension.setBitLayerValueAt(lavaLayer, x, y, true);
			}

			for (var i = 0; i < layerArr.length; i++) {
				layer = layerArr[i];

				if (layer.getDataSize().toString() == "BIT") {
					if (dimension.getBitLayerValueAt)
						dimension.setBitLayerValueAt(layer, x, y, 1)
				} else {
					dimension.setLayerValueAt(layer, x, y, parseInt((1 - dist) * 8) % 16)
				}

			}

			//add Biomes.
			if (dimension.getWaterLevelAt(x, y) > dimension.getHeightAt(x, y)) {
				if (dimension.getBitLayerValueAt(frostLayer, x, y)) {
					dimension.setLayerValueAt(BiomesLayer, x, y, frostRiverBiomeId)
				} else {
					dimension.setLayerValueAt(BiomesLayer, x, y, riverBiomeId)
				}
			} else {
				if (dimension.getBitLayerValueAt(frostLayer, x, y)) {
					dimension.setLayerValueAt(BiomesLayer, x, y, frostBeachBiomeId)
				} else {
					dimension.setLayerValueAt(BiomesLayer, x, y, beachBiomeId)
				}
			}

		}

		//print progress
		if (counter % 1000 == 0) {
			print("Digging out the rivers: " + parseInt((counter) / (newWaterMapLength) * 100 + 0.2) + "%");
		}
		counter++
	}

	//*/
	//fix water escaping:
	print("making sure water does not escape the river.")
	counter = 0;
	//set the terrain.
	for (key in newWaterMap) {
		checkForAbort(false);
		var adjacentPoints = [
			{ x: 0, y: 1 },
			{ x: 0, y: -1 },
			{ x: 1, y: 0 },
			{ x: -1, y: 0 }
		];

		var sparseCirclePoints = [
			{ x: 3, y: 0 },
			{ x: 2, y: 2 },
			{ x: 0, y: 3 },
			{ x: 2, y: -2 },
			{ x: 0, y: -3 },
			{ x: -2, y: -2 },
			{ x: -3, y: 0 },
			{ x: -2, y: 2 },
		];
		var arr = newWaterMap.get(key);
		var x = arr[0];
		var y = arr[1];
		var dist = arr[3];// between 0 and 1 where 0 is the center and 1 is the edge of the river.
		var minWaterDepth = arr[5];
		var slope = arr[6];
		var widthArr = arr[4];
		var heightsArr = arr[2];
		length = heightsArr.length
		if (length > 0) {
			var escapeWidth = 9;
			if (widthArr != null && widthArr.length > 0) {
				var escapeWidthSum = 0;
				for (var wi = 0; wi < widthArr.length; wi++) {
					escapeWidthSum += widthArr[wi];
				}
				escapeWidth = escapeWidthSum / widthArr.length;
			}
			if ((dist > getWaterEdgeLimit(slope, escapeWidth))) {
				var localGround = dimension.getHeightAt(x, y);
				var freeboard = getBankFreeboard(escapeWidth, slope);
				var groundCap = localGround - freeboard;
				currentHeight = parseInt(dimension.getHeightAt(x, y) - 0.5);
				var myWater = dimension.getWaterLevelAt(x, y);
				for (var k = 0; k < adjacentPoints.length; k++) {
					var point = adjacentPoints[k];
					var i = point.x;
					var j = point.y;
					currentHeight = parseInt(dimension.getHeightAt(x, y) - 0.5);
					if (parseInt(dimension.getWaterLevelAt(x + i, y + j) - 0.5) >= currentHeight && dimension.getWaterLevelAt(x + i, y + j) != minWaterDepth) {

						var maxWaterLevel = dimension.getWaterLevelAt(x + i, y + j)//+0.5
						//get water level:
						for (var l = 0; l < sparseCirclePoints.length; l++) {
							var sparseCirclePoint = sparseCirclePoints[l];
							var ix = sparseCirclePoint.x;
							var jy = sparseCirclePoint.y;
							maxWaterLevel = Math.max(maxWaterLevel, dimension.getWaterLevelAt(x + ix, y + jy))
						}

						var safeWater = Math.min(maxWaterLevel, groundCap);
						if (myWater > safeWater + 0.01) {
							dimension.setWaterLevelAt(x, y, Math.max(minWaterDepth, safeWater));
							myWater = dimension.getWaterLevelAt(x, y);
						}
						var currentTerrain = dimension.getHeightAt(x, y);
						if (currentTerrain > localGround) {
							dimension.setHeightAt(x, y, localGround);
						} else if (currentTerrain > maxWaterLevel + 0.25 && currentTerrain > localGround - freeboard) {
							dimension.setHeightAt(x, y, Math.min(localGround, maxWaterLevel - 0.25));
						}
					}
				}
			}
		}

		//print progress
		if (counter % 1000 == 0) {
			print("restricting water to the bounds of the river: " + parseInt((counter) / (newWaterMapLength) * 100 + 0.2) + "%");
		}
		counter++
	}



	//run fixify on the rivers

	print("Running fixify on the rivers")
	counter = 0;
	for (key in newWaterMap) {
		checkForAbort(false);
		var arr = newWaterMap.get(key);
		var x = arr[0];
		var y = arr[1];
		var dist = arr[3];
		if (x - minX < 1 || x - minX > worldWidth || y - minY < 1 || y - minY > worldHeight) {//check if coordinatres are within the map
			continue;//skip to next iteration of for loop
		}
		if (dist < 0.7) {
			fixupCenterSpike(dimension, x, y);
			if (getConfluenceStrengthAt(x, y) > 0.08) {
				fixupRelaxed(dimension, x, y);
			}
			if (counter % 1000 == 0) {
				print("Removing one by one block holes: " + parseInt((counter) / (newWaterMapLength) * 100 + 0.2) + "%");
			}
			counter++
			continue;
		}

		fixupRelaxed(dimension, x, y);

		if (counter % 1000 == 0) {
			print("Removing one by one block holes: " + parseInt((counter) / (newWaterMapLength) * 100 + 0.2) + "%");
		}
		counter++
	}

	smoothConfluenceHeights(dimension);

	print("fixing water on narrow river sections")
	expandWaterAlongPathWidth(foundPaths, foundPathMaskData);
	smoothRiverBedCenter(dimension);
	if (bankSmoothing) {
		print("Smoothing steep river banks for a more natural look.");
		smoothRiverBanks(maxBankWallHeight, bankSmoothingIterations);
	}
	for (var pathIndex = 0; pathIndex < foundPaths.length; pathIndex++) {
		checkForAbort(false);
		print("fixing water on narrow river section", pathIndex, "of", numberOfRivers);
		var path = foundPaths[pathIndex];
		if (path == null || path[0][0] == null || path[0][1] == null) {
			continue;
		}
		for (var i = 0; i < path.length; i++) {
			var x = path[i][0];
			var y = path[i][1];
			if (parseInt(dimension.getHeightAt(x, y) - 0.5) >= (dimension.getWaterLevelAt(x, y) - 1)) {
				//dimension.setBitLayerValueAt(maskLayer, x, y, true);
				dimension.setHeightAt(x, y, dimension.getWaterLevelAt(x, y) - 1);
			}
		}
	}

	// The legacy generator may calculate a water surface far above a later valley.
	// Re-anchor every generated water cell to its final carved bed before exporting.
	stabiliseGeneratedRiverWater(dimension);



	print("\n\n=============  Report:  =============\n" + report);
	if (shallowGraniteDetail) {
		print("Sığ yatak granite hücreleri: " + shallowGraniteCells
			+ " (taban=" + shallowGraniteFloorCells + ", kıyı=" + shallowGraniteBankCells + ")");
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
	print("took:\t" + timeStr);
}

print("\nDone! generated " + numberOfRivers + " rivers -- script provided by sijmen_v_b");

// River editing stores full granite terrain, not individual stairs/slabs. The export
// smoother reads the surrounding 2x2 heights, then picks the valid bottom partial
// and waterlogs it if water occupies the same block.
function shouldPlaceShallowGranite(x, y, dist, actualWaterDepth, slope, waterfallStrength) {
	if (!shallowGraniteDetail || graniteTerrain == null || actualWaterDepth <= 0.05
			|| actualWaterDepth > shallowGraniteMaxDepth
			|| slope > 0.55 || waterfallStrength > 0.02) {
		return false;
	}
	var coverage = dist >= 0.45 ? shallowGraniteBankCoverage : shallowGraniteFloorCoverage;
	return clusteredGraniteNoise(x, y) < coverage;
}

function markShallowGranite(x, y, bank) {
	var key = toCoordinate(x, y);
	if (shallowGraniteMarked.get(key) != null) {
		return;
	}
	shallowGraniteMarked.put(key, true);
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
	// The coarse value creates short connected stretches; the fine value breaks the
	// square grid edge without turning the bed into isolated salt-and-pepper blocks.
	return 0.78 * graniteHash(gridX, gridY, 0) + 0.22 * graniteHash(x, y, 1);
}

function graniteHash(x, y, salt) {
	var n = Math.sin(x * 12.9898 + y * 78.233 + (shallowGraniteSeed + salt * 101) * 37.719) * 43758.5453123;
	return n - Math.floor(n);
}



function repeatableRandom(x, y) {
	if (oldNoise) {
		key = toCoordinate(x, y);
		value = randomMap.get(key);
		if (value == null) {
			value = Math.random();
			randomMap.put(key, value);
		}
		return value;
	}

	x = x * 100 / noiseSize
	y = y * 100 / noiseSize

	noiseStrength = 30;
	DisplacementSize = 30;

	result = reMap(Math.abs(
		Math.sin((x + 200 * noise.simplex2((x + 1000) / 1000, (y + 1000) / 1000) + noiseStrength * noise.simplex2(x / (2000 / DisplacementSize), y / (2000 / DisplacementSize))) / DisplacementSize)
		+ Math.sin((y + noiseStrength * noise.simplex2((x + 100) / (2000 / DisplacementSize), (y + 100) / (2000 / DisplacementSize))) / DisplacementSize)
	), 0, 1, 0, 1)
	result = Math.max(result, 0.02)
	return reMap(result, 0, 2, 0, 1);

}

function reMap(num, fromMin, fromMax, toMin, toMax) {
	return ((num - fromMin) / (fromMax - fromMin)) * (toMax - toMin) + toMin
}

function loadTerrainData(dimension, x, y, distance, minWaterDepth, slope, depthMultiplier, waterfallStrength, tangentAngle, pathIndex, pointIndex) {
	var height = getRiverBedReferenceHeight(dimension, x, y, slope)
	var alongRadius = distance;
	var acrossRadius = Math.max(1, distance * 0.85);
	var useEllipse = tangentAngle != null && !isNaN(tangentAngle);
	var cosA = useEllipse ? Math.cos(tangentAngle) : 1;
	var sinA = useEllipse ? Math.sin(tangentAngle) : 0;
	for (var i = -1 * distance; i < distance + 1; i++) {
		for (var j = -1 * distance; j < distance + 1; j++)//loop trough all coordinates(blocks) of the map.
		{
			if (x + i > minX && x + i < worldWidth + minX && y + j > minY && y + j < worldHeight + minY) {
				var distNorm;
				var lateralNorm = 0;
				if (useEllipse) {
					var lx = i * cosA + j * sinA;
					var ly = -i * sinA + j * cosA;
					distNorm = (lx * lx) / (alongRadius * alongRadius) + (ly * ly) / (acrossRadius * acrossRadius);
					lateralNorm = ly / acrossRadius;
				} else {
					var dist = getSquaredDistance(x + i, y + j, x, y);
					if (dist > distance * distance) {
						continue;
					}
					distNorm = dist / (distance * distance);
				}
				if (distNorm <= 1) {
					addWater(x + i - minX, y + j - minY, height, distNorm, distance, minWaterDepth, slope, depthMultiplier, waterfallStrength, tangentAngle, pathIndex, pointIndex, lateralNorm);
				}
			}
		}
	}
}

// updates maskMap to include a [x,y,size] array where if the coordinate is already exists it makes size the maximum of the two.
function setMaskMaxSize(x, y, size, minWaterDepth, slope, depthMultiplier, waterfallStrength, tangentAngle, pathIndex, pointIndex) {
	key = toCoordinate(x, y);
	current = maskMap.get(key);
	if (waterfallStrength == null) {
		waterfallStrength = 0;
	}
	if (current == null || current[2] < size) {
		maskMap.put(key, [x, y, size, minWaterDepth, slope, depthMultiplier, waterfallStrength, tangentAngle, pathIndex, pointIndex])
	} else if (current != null) {
		if (current[6] < waterfallStrength) {
			current[6] = waterfallStrength;
		}
		if (current[5] < depthMultiplier) {
			current[5] = depthMultiplier;
		}
		if (tangentAngle != null && current[7] == null) {
			current[7] = tangentAngle;
		}
		if (pathIndex != null && current[8] == null) {
			current[8] = pathIndex;
		}
		if (pointIndex != null && current[9] == null) {
			current[9] = pointIndex;
		}
		maskMap.put(key, current)
	}
}

// appends the height to the map of blocks around the river. also passes information on the distance from the centre arr[3] and the width at that point (as a list) arr[4] 
function addWater(x, y, height, distanceFromCenter, width, minWaterDepth, slope, depthMultiplier, waterfallStrength, tangentAngle, pathIndex, pointIndex, lateralNorm) {
	key = toCoordinate(x + minX, y + minY);
	arr = newWaterMap.get(key);
	if (waterfallStrength == null) {
		waterfallStrength = 0;
	}
	if (arr == null) {
		arr = [x + minX, y + minY, [], distanceFromCenter, [], minWaterDepth, slope, depthMultiplier, waterfallStrength, tangentAngle, pathIndex, pointIndex, lateralNorm];
	}
	arr[2].push(height);
	current = arr[3];
	if (current > distanceFromCenter) {
		arr[3] = distanceFromCenter;
	}
	arr[4].push(width);

	current = arr[5];
	if (current > minWaterDepth) {
		arr[5] = minWaterDepth;
	}

	current = arr[6];
	if (current > slope) {
		arr[6] = slope;
	}

	current = arr[7];
	if (current < depthMultiplier) {
		arr[7] = depthMultiplier;
	}

	if (arr[8] < waterfallStrength) {
		arr[8] = waterfallStrength;
	}
	if (tangentAngle != null && arr[9] == null) {
		arr[9] = tangentAngle;
	}
	if (pathIndex != null && arr[10] == null) {
		arr[10] = pathIndex;
	}
	if (pointIndex != null && arr[11] == null) {
		arr[11] = pointIndex;
	}
	if (lateralNorm != null && (arr[12] == null || Math.abs(lateralNorm) > Math.abs(arr[12]))) {
		arr[12] = lateralNorm;
	}
	if (arr[13] == null) {
		arr[13] = 0;
	}
	arr[13]++;
	newWaterMap.put(key, arr);
}

function getSquaredDistance(x1, y1, x2, y2) {
	var x = x1 - x2;
	var y = y1 - y2;
	return x * x + y * y;
}

function contains(coordList, coord) {
	for (var i = 0; i < coordList.length; i++) {
		if (coordList[i][0] === coord[0] && coordList[i][1] === coord[1]) {
			return true;
		}
	}
	return false;
}
//used for the hashmap
function toCoordinate(x, y) {
	return x + y * worldWidth
}

//take an x and a y and the hashmap storing arrays (with length 2) of x and y  to trace back to the start
function getTrail(x, y, coordinates) {
	var trail = [];
	var visitedSteps = 0;
	var maxTrailSteps = Math.max(1000, worldWidth * worldHeight + 16);
	while (x != null && y != null) {
		checkForAbort(false);
		if (visitedSteps > maxTrailSteps) {
			print("Warning! Trail reconstruction aborted after safety limit.");
			break;
		}
		cameFrom = coordinates.get(toCoordinate(x, y))
		if (cameFrom == null) {
			break;
		}
		trail.push([x, y]);
		x = cameFrom[0];
		y = cameFrom[1];
		visitedSteps++;
	}
	return trail;
}

//gives me the distance of all the 8 directions of x and y where x and y are either 1 or 0 (so for a 3x3 grid)
function calculateCelDist(x, y) {
	if (Math.abs(x) + Math.abs(y) <= 1) {
		return 1.0;

	} else {
		return Math.sqrt(x * x + y * y);
	}
}

//used for path finding in water
function calculateCelDist2(x, y) {
	if (Math.abs(x) + Math.abs(y) <= 1) {
		return 1.0;
	} else {
		return 1.4142135623730951;
	}
}

//gives the intermediary blocks in case we use the "rim of 5x5 square without the corners".
//returns a list of tuples.
function getIntermediatePositions(x, y) {
	if (Math.max(Math.abs(x), Math.abs(y)) < 2) {
		return []
	}

	if (Math.abs(x) == 2) {
		if (y == 0) {
			return [{ x: x - x / 2, y: 0 }]
		}
		return [{ x: x - x / 2, y: 0 }, { x: x - x / 2, y: y }]
	} else { //y is biggest component
		if (x == 0) {
			return [{ x: 0, y: y - y / 2 }]
		}
		return [{ x: 0, y: y - y / 2 }, { x: x, y: y - y / 2 }]
	}
}

//function that returns true if the river may not cross this point.
function avoidCondition(x, y) {
	if (isNearWorldBoundary(x, y, riverBoundaryMargin)) {
		return true;
	}
	return avoidLayerCondition(x, y);
}

function avoidLayerCondition(x, y) {
	if (avoidLayer == null) {
		return false;
	}
	if (avoidLayer.getDataSize().toString() == "BIT") {
		if (dimension.getBitLayerValueAt(avoidLayer, x, y)) {//add the start positions
			return true;
		}
	} else {
		if (dimension.getLayerValueAt(avoidLayer, x, y) > 0) {//add the start positions
			return true;
		}
	}

	return false;
}

function isNearWorldBoundary(x, y, margin) {
	margin = Math.max(0, parseInt(margin));
	return x - minX < margin || y - minY < margin || (minX + worldWidth - 1) - x < margin || (minY + worldHeight - 1) - y < margin;
}

function pathTouchesWorldBoundary(path, margin) {
	if (path == null || path.length == 0) {
		return false;
	}
	for (var i = 0; i < path.length; i++) {
		var p = path[i];
		if (p == null || p[0] == null) {
			continue;
		}
		if (isNearWorldBoundary(p[0], p[1], margin)) {
			return true;
		}
	}
	return false;
}

function findPath(x, y) {
	/* var points = [
		{ x: -1, y: -1 },
		{ x: -1, y: 0 },
		{ x: -1, y: 1 },
		{ x: 0, y: -1 },
		{ x: 0, y: 1 },
		{ x: 1, y: -1 },
		{ x: 1, y: 0 },
		{ x: 1, y: 1 },
	]; */
	// rim of 5x5 square without the corners.
	var points = [
		{ x: 2, y: 1 },
		{ x: 2, y: 0 },
		{ x: 2, y: -1 },
		{ x: -2, y: 1 },
		{ x: -2, y: 0 },
		{ x: -2, y: -1 },
		{ x: 1, y: 2 },
		{ x: 0, y: 2 },
		{ x: -1, y: 2 },
		{ x: 1, y: -2 },
		{ x: 0, y: -2 },
		{ x: -1, y: -2 }
	];

	if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight) {//check if coordinatres are within the map
		print("ERROR! starting position not on the map!");
		report += "ERROR! starting position not on the map!\n";
		return [[null, null]];
	}

	if (dimension.getHeightAt(x, y) < (dimension.getWaterLevelAt(x, y) - 0.5)) {
		print("Warning! starting position already on water, skipping (" + x + "," + y + ")!");
		report += "Warning! starting position already on water, skipping (" + x + "," + y + ")!\n";
		return [[null, null]];
	}

	openSet = new PriorityQueue(); //contains a array with [priority,x,y,current length from start]
	openSet.add(new Candidate(4, x, y, 4));

	var addedPositions = new HashMap(); //contains the nodes that where added and where they came from
	addedPositions.put(toCoordinate(x, y), [null, null])

	var previousBool = false; //used to check if the pathfinding is stuck
	var count = 0
	while (openSet.values.length != 0 && addedPositions.size() < worldWidth * worldHeight) {//go over all the candidates till we have no more or we looked at every pixel of the map.

		checkForAbort(false);

		current = openSet.poll();
		if (count % 100000 == 99999) {
			print("pathfinding info: number of candidates = " + openSet.values.length + " addedPositions = " + addedPositions.size() + "  max: " + worldWidth * worldHeight)

			//check if the pathfinding is stuck now and if it was stuck last time.
			if (openSet.values.length == 1) {
				if (previousBool) {
					print("Warning! could not find any path towards water!, skipping (" + x + "," + y + ")!");
					report += "Warning! could not find any path towards water!, skipping (" + x + "," + y + ")!\n";
					return [[null, null]];
				} else {
					previousBool = true;
				}
			}
			else {
				previousBool = false;
			}
		}



		count++

		if (dimension.getHeightAt(current.x, current.y) < (dimension.getWaterLevelAt(current.x, current.y) - 0.5)) {
			return getTrail(current.x, current.y, addedPositions);
		}
		//add adjacent

		// shuffle the points so they are added in a random order:
		// shuffle the array using the Fisher-Yates algorithm
		for (var i = points.length - 1; i > 0; i--) {
			var j = Math.floor(Math.random() * (i + 1));
			var temp = points[i];
			points[i] = points[j];
			points[j] = temp;
		}


		for (var k = 0; k < points.length; k++) {
			var point = points[k];
			var i = point.x;
			var j = point.y;
			if (current.x + i - minX < 0 || current.x + i - minX >= worldWidth || current.y + j - minY < 0 || current.y + j - minY >= worldHeight//check if coordinates are within the map
				|| avoidCondition(current.x + i, current.y + j)
				|| (lookingAtTunnelLayer && !(lookingAtTunnelLayer && surfaceDimension.getBitLayerValueAt(tunnelLayer, current.x + i, current.y + j)))) //check if the height exists (used for custom cave floor dimension)
			{
				continue
			}
			if (addedPositions.get(toCoordinate(current.x + i, current.y + j)) == null) {//if not already reached
				//calculate the weight
				var distanceSaveBeforeRerouting = 150;//150; //the number of blocks the river must be shorter in order to go up 1 block. 
				var weight = (current.dist + calculateCelDist(i, j)) * 1 + (distanceSaveBeforeRerouting * dimension.getHeightAt(current.x + i, current.y + j)) + (repeatableRandom(current.x, current.y) * randomness)
				//add it to the que
				//make sure we add the intermediate coordinates.
				var candidates = getIntermediatePositions(i, j);
				if (candidates == null) {
					addedPositions.put(toCoordinate(current.x + i, current.y + j), [current.x, current.y])
				} else {
					var bestX = candidates[0].x;
					var bestY = candidates[0].y;
					var minHeight = Infinity
					if (addedPositions.get(toCoordinate(current.x + bestX, current.y + bestY)) == null) {
						minHeight = dimension.getHeightAt(current.x + bestX, current.y + bestY)
					}
					for (var candidateIndex = 1; candidateIndex < candidates.length; candidateIndex++) {
						var newX = candidates[candidateIndex].x;
						var newY = candidates[candidateIndex].y;
						var newHeight = dimension.getHeightAt(current.x + newX, current.y + newY)
						if (newHeight < minHeight && addedPositions.get(toCoordinate(current.x + newX, current.y + newY)) == null) {
							minHeight = newHeight;
							bestX = newX;
							bestY = newY;

						}
					}
					if (minHeight != Infinity) { //if the intermediary was not already added.
						addedPositions.put(toCoordinate(current.x + bestX, current.y + bestY), [current.x, current.y]);
						//dimension.setBitLayerValueAt(maskLayer, current.x + bestX, current.y + bestY, true)
					}
					addedPositions.put(toCoordinate(current.x + i, current.y + j), [current.x + bestX, current.y + bestY]);
					//dimension.setBitLayerValueAt(maskLayer, current.x + i, current.y + j, true)

					//addedPositions.put(toCoordinate(current.x + i, current.y + j), [current.x, current.y]);
				}

				openSet.add(new Candidate(weight, current.x + i, current.y + j, current.dist + calculateCelDist(i, j)))


			}
		}

	}

	var d = openSet.poll()
	if (d == null) {
		return [[null, null]];
	}
	return [[d.x, d.y]];
}


//similar to findPath but instead of stopping at water it will stop as soon as a path with length(actual distance not number of blocks.) of distanceTarget is reached.
function pathFindDown(x, y, distanceTarget) {

	if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight || avoidCondition(x, y)) {//check if coordinates are within the map
		print("ERROR! starting position not on the map!");
		report += "ERROR! starting position not on the map!\n";
		return [[null, null]];
	}

	var openSet = new PriorityQueue(); //contains a array with [priority,x,y,current length from start]
	openSet.add(new Candidate(4, x, y, 0));

	var addedPositions = new HashMap(); //contains the nodes that where added and where they came from
	addedPositions.put(toCoordinate(x, y), [null, null])


	var count = 0
	while (openSet.values.length != 0 && addedPositions.size() < worldWidth * worldHeight) {//go over all the candidates till we have no more or we looked at every pixel of the map.

		checkForAbort(false);

		current = openSet.poll();
		if (count % 10000 == -1 % 10000) {
			print("FIXING RIVER TO SEA! number of candidates = " + openSet.values.length + " addedPositions = " + addedPositions.size() + "  max:" + worldWidth * worldHeight)
		}
		count++

		if (current.dist > distanceTarget) {
			return getTrail(current.x, current.y, addedPositions);
		}
		//add adjacent
		for (var i = -1; i < 2; i++) {
			for (var j = -1; j < 2; j++) {
				if (i == 0 && j == 0) {
					continue
				}
				if (current.x + i - minX < 0 || current.x + i - minX >= worldWidth || current.y + j - minY < 0 || current.y + j - minY >= worldHeight //check if coordinates are within the map
					|| avoidCondition(current.x + i, current.y + j)
					|| (lookingAtTunnelLayer && !(lookingAtTunnelLayer && surfaceDimension.getBitLayerValueAt(tunnelLayer, current.x + i, current.y + j)))) //check if the height exists (used for custom cave floor dimension)
				{
					continue
				}
				if (addedPositions.get(toCoordinate(current.x + i, current.y + j)) == null) {//if not already reached
					//calculate the weight
					var distanceSaveBeforeRerouting = 150; //the number of blocks the river must be shorter in order to go up 1 block. 
					weight = current.dist + calculateCelDist2(i, j) + distanceSaveBeforeRerouting * dimension.getHeightAt(current.x + i, current.y + j)
					//add it to the que
					addedPositions.put(toCoordinate(current.x + i, current.y + j), [current.x, current.y])
					openSet.add(new Candidate(weight, current.x + i, current.y + j, current.dist + calculateCelDist2(i, j)))
				}
			}
		}


	}

	var d = openSet.poll()
	if (d == null) {
		return [[null, null]];
	}
	return [[d.x, d.y]];

}

function stabiliseGeneratedRiverWater(dimension) {
	var groundedWaterCells = 0;
	var dryBankCells = 0;
	for (key in newWaterMap) {
		checkForAbort(false);
		var arr = newWaterMap.get(key);
		var x = arr[0];
		var y = arr[1];
		var widthArr = arr[4];
		var width = 1;
		if (widthArr != null && widthArr.length > 0) {
			var widthSum = 0;
			for (var wi = 0; wi < widthArr.length; wi++) {
				widthSum += widthArr[wi];
			}
			width = widthSum / widthArr.length;
		}
		var bedHeight = dimension.getHeightAt(x, y);
		if (arr[3] <= getWaterEdgeLimit(arr[6], width)) {
			// Keep the intended depth up to this cell's original ground level. The cap
			// prevents the old global water interpolation from spanning a lower valley.
			var originalSurfaceCap = riverWaterCapMap.get(toCoordinate(x, y));
			var waterSurface = Math.max(Math.floor(bedHeight) + 1,
				originalSurfaceCap != null ? originalSurfaceCap : Math.floor(bedHeight));
			dimension.setWaterLevelAt(x, y, waterSurface);
			groundedWaterCells++;
		} else if (dimension.getWaterLevelAt(x, y) > bedHeight) {
			// Do not leave the mask's dry lip flooded after the channel has been carved.
			dimension.setWaterLevelAt(x, y, Math.floor(bedHeight));
			dryBankCells++;
		}
	}
	print("Grounded river water: " + groundedWaterCells + " cells; cleared dry banks: " + dryBankCells);
}

function smoothRiverBedCenter(dimension) {
	print("Smoothing river bed centers...");
	var adjacentPoints = [
		{ x: 0, y: 1 },
		{ x: 0, y: -1 },
		{ x: 1, y: 0 },
		{ x: -1, y: 0 }
	];
	var updates = new HashMap();
	for (key in newWaterMap) {
		var arr = newWaterMap.get(key);
		var x = arr[0];
		var y = arr[1];
		var dist = arr[3];
		if (dist >= 0.4) {
			continue;
		}
		var currentHeight = dimension.getHeightAt(x, y);
		var sum = 0;
		var countLocal = 0;
		for (var k = 0; k < adjacentPoints.length; k++) {
			var p = adjacentPoints[k];
			var nx = x + p.x;
			var ny = y + p.y;
			if (nx - minX < 0 || nx - minX >= worldWidth || ny - minY < 0 || ny - minY >= worldHeight) {
				continue;
			}
			sum += dimension.getHeightAt(nx, ny);
			countLocal++;
		}
		if (countLocal == 0) {
			continue;
		}
		var avgHeight = sum / countLocal;
		if (currentHeight > avgHeight + 0.15) {
			updates.put(key, [x, y, currentHeight * 0.5 + avgHeight * 0.5]);
		}
	}
	var changed = 0;
	for (updateKey in updates) {
		var update = updates.get(updateKey);
		dimension.setHeightAt(update[0], update[1], update[2]);
		changed++;
	}
	print("\triver bed center smoothing updated " + changed + " blocks");
}

function smoothRiverBanks(maxWallHeight, iterations) {
	var adjacentPoints = [
		{ x: 0, y: 1 },
		{ x: 0, y: -1 },
		{ x: 1, y: 0 },
		{ x: -1, y: 0 },
		{ x: 1, y: 1 },
		{ x: 1, y: -1 },
		{ x: -1, y: 1 },
		{ x: -1, y: -1 }
	];

	iterations = Math.max(parseInt(iterations), 1);
	maxWallHeight = Math.max(maxWallHeight, 0.5);

	for (var pass = 0; pass < iterations; pass++) {
		var updates = new HashMap();
		for (key in newWaterMap) {
			var arr = newWaterMap.get(key);
			var x = arr[0];
			var y = arr[1];
			var dist = arr[3];
			var slope = arr[6];

			if (dist < 0.40 || dist > 1.0) {
				continue;
			}

			var waterLevel = dimension.getWaterLevelAt(x, y);
			var currentHeight = dimension.getHeightAt(x, y);
			if (currentHeight <= waterLevel + 0.5) {
				continue;
			}

			var sum = 0;
			var countLocal = 0;
			for (var k = 0; k < adjacentPoints.length; k++) {
				var p = adjacentPoints[k];
				var nx = x + p.x;
				var ny = y + p.y;
				if (nx - minX < 0 || nx - minX >= worldWidth || ny - minY < 0 || ny - minY >= worldHeight) {
					continue;
				}
				sum += dimension.getHeightAt(nx, ny);
				countLocal++;
			}

			if (countLocal == 0) {
				continue;
			}

			var avgHeight = sum / countLocal;
			var slopeAllowance = 0.35 * clampBetweenZeroAndOne(slope * 2);
			var allowedHeight = avgHeight + maxWallHeight + slopeAllowance;
			var target = Math.max(waterLevel + 1, Math.min(currentHeight, allowedHeight));
			var blended = currentHeight * 0.45 + target * 0.55;

			if (blended < currentHeight - 0.01) {
				updates.put(key, [x, y, blended]);
			}
		}

		var changed = 0;
		for (updateKey in updates) {
			var update = updates.get(updateKey);
			dimension.setHeightAt(update[0], update[1], update[2]);
			changed++;
		}

		print("\tbank smoothing pass " + (pass + 1) + "/" + iterations + ", updated " + changed + " blocks");
		if (changed == 0) {
			break;
		}
	}
}

function applyStyleProfile(profile) {
	profile = parseInt(profile);
	riverDepth = baseRiverDepth;
	maxChangeSlope = baseMaxChangeSlope;
	dykeSize = baseDykeSize;
	waterfallPoolDepth = baseWaterfallPoolDepth;
	bankSmoothing = true;
	maxBankWallHeight = 2.5;
	bankSmoothingIterations = 2;
	classicMeanderStrength = 0.6;
	enableFloodplain = false;
	enableBendAsymmetry = false;
	dischargeWidthBlendClassic = 0.65;
	fantasyMapRiverStyle = profile != 3;

	if (isNaN(profile) || profile <= 0) {
		styleProfile = 0;
		return;
	}

	if (profile == 1) {
		styleProfile = 1;
		maxBankWallHeight = 1.0;
		bankSmoothingIterations = 4;
		dykeSize = 0.02;
		maxChangeSlope = 0.075;
		riverDepth = baseRiverDepth * 1.0;
		waterfallPoolDepth = baseWaterfallPoolDepth * 0.65;
		classicMeanderStrength = 0.55;
		enableFloodplain = false;
		enableBendAsymmetry = true;
	} else if (profile == 2) {
		styleProfile = 2;
		maxBankWallHeight = 2.2;
		bankSmoothingIterations = 2;
		dykeSize = 0.08;
		maxChangeSlope = 0.14;
		riverDepth = baseRiverDepth * 1.12;
		waterfallPoolDepth = baseWaterfallPoolDepth;
		classicMeanderStrength = 0.45;
		enableFloodplain = false;
		enableBendAsymmetry = false;
	} else if (profile == 3) {
		styleProfile = 3;
		maxBankWallHeight = 1.35;
		bankSmoothingIterations = 3;
		dykeSize = 0.015;
		maxChangeSlope = 0.07;
		riverDepth = baseRiverDepth * 0.98;
		waterfallPoolDepth = baseWaterfallPoolDepth * 0.55;
		classicMeanderStrength = 0.75;
		enableFloodplain = true;
		enableBendAsymmetry = true;
		dischargeWidthBlendClassic = 0.72;
	}
}

function getBankFreeboard(width, slope) {
	var freeboard = width > 14 ? 1.5 : 1.0;
	if (slope > 0.35) {
		freeboard = Math.max(0.6, freeboard - slope * 0.8);
	}
	return freeboard;
}

function getReferenceHeightForCarve(heightsArr, localGround, dist, innerLimit) {
	if (heightsArr == null || heightsArr.length == 0) {
		return localGround;
	}
	var minH = localGround;
	var sum = 0;
	for (var i = 0; i < heightsArr.length; i++) {
		sum += heightsArr[i];
		if (heightsArr[i] < minH) {
			minH = heightsArr[i];
		}
	}
	if (dist <= innerLimit) {
		return Math.min(localGround, minH);
	}
	return Math.min(sum / heightsArr.length, localGround);
}

function getWaterfallStrengthAt(x, y) {
	var data = waterfallMap.get(toCoordinate(x, y));
	if (data == null) {
		return 0;
	}
	return data[2];
}

function makePathProfile(type, fixedWidth) {
	return {
		type: type == null ? "main" : type,
		fixedWidth: fixedWidth == null ? 0 : fixedWidth
	};
}

function getPathWidthAt(profile, pathScale, length, i, pathLength, slope, junctionBoost) {
	if (profile != null && profile.manualNetwork) {
		return getManualNetworkWidthAt(profile, pathScale, length, i, pathLength, slope, junctionBoost);
	}
	if (profile != null && profile.fixedWidth != null && profile.fixedWidth > 0) {
		return Math.max(1, parseInt(profile.fixedWidth));
	}
	var widths = getProfileWidthRange(profile == null ? "main" : profile);
	var t = (length - (i - (pathLength - length))) / length;
	t = clampBetweenZeroAndOne(t);
	var width = widths.start + t * (widths.end - widths.start);
	if (profile != null && profile.type == "main") {
		width += 2 * (slope * slope);
		width *= (1 + 0.22 * junctionBoost);
	} else if (profile != null && profile.type == "medium") {
		width += slope * slope;
		width *= (1 + 0.10 * junctionBoost);
	}
	return Math.max(1, parseInt(width));
}

function getManualNetworkWidthAt(profile, pathScale, length, i, pathLength, slope, junctionBoost) {
	var localFlow = getManualLocalFlowAt(profile, i, pathLength);
	var widths = getManualFlowWidthRange(localFlow);
	var t = (length - (i - (pathLength - length))) / length;
	t = clampBetweenZeroAndOne(t);
	var width = widths.start + t * (widths.end - widths.start);
	width = Math.max(width, getManualFlowWidthFloor(localFlow));
	if (localFlow >= 5) {
		width += 2 * (slope * slope);
		width *= (1 + 0.16 * junctionBoost);
	} else if (localFlow >= 2) {
		width += slope * slope;
		width *= (1 + 0.08 * junctionBoost);
	}
	width *= pathScale == null ? 1.0 : pathScale;
	return Math.max(1, parseInt(width));
}

function getManualLocalFlowAt(profile, index, pathLength) {
	var flow = profile.baseFlowCount == null ? 1 : profile.baseFlowCount;
	if (profile.manualFlowSegments == null) {
		return flow;
	}
	for (var s = 0; s < profile.manualFlowSegments.length; s++) {
		var segment = profile.manualFlowSegments[s];
		if (segment == null) {
			continue;
		}
		var joinIndex = Math.max(0, Math.min(pathLength - 1, parseInt(segment.maxIndex)));
		var transition = Math.max(8, parseInt(segment.transition == null ? 32 : segment.transition));
		if (index <= joinIndex) {
			flow += segment.amount;
		} else if (index <= joinIndex + transition) {
			flow += segment.amount * (1 - ((index - joinIndex) / transition));
		}
	}
	return Math.max(1, flow);
}

function getManualFlowWidthRange(flowCount) {
	flowCount = Math.max(1, flowCount);
	var end = 4 + Math.min(34, Math.pow(flowCount, 0.78) * 5.2);
	var start = flowCount < 1.5 ? 1 : Math.min(8, 1 + Math.pow(flowCount - 1, 0.65) * 3.2);
	return { start: start, end: end };
}

function getManualFlowWidthFloor(flowCount) {
	if (flowCount < 1.5) {
		return 1;
	}
	return Math.min(14, 3 + Math.pow(flowCount - 1, 0.72) * 3.4);
}

function getProfileWidthRange(type) {
	if (type != null && typeof type == "object") {
		if (type.customStartWidth != null && type.customEndWidth != null) {
			return { start: type.customStartWidth, end: type.customEndWidth };
		}
		type = type.type;
	}
	if (type == "feeder") {
		return { start: 1, end: 1 };
	}
	if (type == "small") {
		return { start: 2, end: 7 };
	}
	if (type == "medium") {
		return { start: 5, end: 14 };
	}
	return { start: 9, end: 36 };
}

function getProfileDepthMultiplier(type) {
	var extra = 1.0;
	if (type != null && typeof type == "object") {
		if (type.depthMultiplier != null) {
			extra = type.depthMultiplier;
		}
		type = type.type;
	}
	if (type == "feeder") {
		return 0.28 * extra;
	}
	if (type == "small") {
		return 0.48 * extra;
	}
	if (type == "medium") {
		return 0.72 * extra;
	}
	return 1.0 * extra;
}

function getBendDepthBoostAt(path, index, type) {
	if (path == null || path.length < 9 || type == "feeder") {
		return 1.0;
	}
	var reach = type == "main" ? 14 : (type == "medium" ? 10 : 7);
	var before = path[Math.max(0, index - reach)];
	var center = path[index];
	var after = path[Math.min(path.length - 1, index + reach)];
	if (before == null || center == null || after == null || before[0] == null || after[0] == null) {
		return 1.0;
	}
	var ax = center[0] - before[0];
	var ay = center[1] - before[1];
	var bx = after[0] - center[0];
	var by = after[1] - center[1];
	var al = Math.sqrt(ax * ax + ay * ay);
	var bl = Math.sqrt(bx * bx + by * by);
	if (al < 1 || bl < 1) {
		return 1.0;
	}
	var dot = (ax * bx + ay * by) / (al * bl);
	dot = Math.max(-1, Math.min(1, dot));
	var bend = clampBetweenZeroAndOne((1 - dot) * 0.5);
	var strength = type == "main" ? 0.055 : (type == "medium" ? 0.04 : 0.025);
	return 1.0 + bend * strength;
}

function limitAllRiverPathSlopes(paths) {
	for (var pathIndex = 0; pathIndex < paths.length; pathIndex++) {
		checkForAbort(false);
		print("Limiting the change of slope for river", pathIndex, "of", paths.length);
		limitRiverPathSlopeChange(paths[pathIndex]);
	}
}

function limitRiverPathSlopeChange(path) {
	if (path == null || path[0][0] == null || path[0][1] == null) {
		return;
	}
	var previousHeight2 = dimension.getHeightAt(path[0][0], path[0][1]);
	var previousHeight1 = dimension.getHeightAt(path[1][0], path[1][1]);
	for (var i = 2; i < path.length; i++) {
		var x = path[i][0];
		var y = path[i][1];
		if (x == null || y == null) {
			continue;
		}

		var currHeight = dimension.getHeightAt(x, y)
		if (enableWaterfalls && getWaterfallStrengthAt(x, y) > 0) {
			previousHeight2 = previousHeight1;
			previousHeight1 = currHeight;
			continue;
		}
		var prevSlope = Math.abs(previousHeight1 - previousHeight2);
		var currSlope = Math.abs(previousHeight1 - currHeight);
		if (Math.abs(prevSlope - currSlope) > maxChangeSlope) {
			dimension.setHeightAt(x, y, Math.min(currHeight, previousHeight1 + (prevSlope + maxChangeSlope)));
		}
		previousHeight2 = previousHeight1;
		previousHeight1 = dimension.getHeightAt(x, y);
	}
}

function getPathSlopeAt(path, i) {
	var x = path[i][0];
	var y = path[i][1];
	if (x == null || y == null) {
		return 0;
	}
	var slope = 0;
	if (i < path.length - 1) {
		slope = Math.min((dimension.getHeightAt(path[i + 1][0], path[i + 1][1]) - dimension.getHeightAt(x, y)), 1);
	}
	if (i > path.length - 5) {
		slope = 0.5;
	}
	return slope;
}

function getPathTangentAt(path, i) {
	if (path == null || path.length < 2) {
		return 0;
	}
	var i0 = Math.max(0, i - 1);
	var i1 = Math.min(path.length - 1, i + 1);
	var p0 = path[i0];
	var p1 = path[i1];
	if (p0 == null || p1 == null || p0[0] == null || p1[0] == null) {
		return 0;
	}
	return Math.atan2(p1[1] - p0[1], p1[0] - p0[0]);
}

function snapPathToLocalValleyFloor(path) {
	if (path == null || path.length == 0) {
		return path;
	}
	var radius = 3;
	var maxShiftSq = 9;
	var result = [];
	for (var i = 0; i < path.length; i++) {
		var px = path[i][0];
		var py = path[i][1];
		if (px == null || py == null) {
			result.push(path[i]);
			continue;
		}
		var bestX = px;
		var bestY = py;
		var bestH = dimension.getHeightAt(px, py);
		for (var dx = -radius; dx <= radius; dx++) {
			for (var dy = -radius; dy <= radius; dy++) {
				if (dx * dx + dy * dy > maxShiftSq) {
					continue;
				}
				var nx = px + dx;
				var ny = py + dy;
				if (nx - minX < 0 || nx - minX >= worldWidth || ny - minY < 0 || ny - minY >= worldHeight || avoidCondition(nx, ny)) {
					continue;
				}
				var nh = dimension.getHeightAt(nx, ny);
				if (nh < bestH) {
					bestH = nh;
					bestX = nx;
					bestY = ny;
				}
			}
		}
		result.push([bestX, bestY]);
	}
	return removeConsecutiveDuplicatePoints(result);
}

function sampleHydroAccumulationAt(hydro, x, y) {
	if (hydro == null || x == null || y == null) {
		return 1;
	}
	return hydro.accumulation[worldToHydroIndex(hydro, x, y)];
}

function getDischargeWidthAt(accum, maxAccum, profile, linearWidth) {
	var widths = getProfileWidthRange(profile == null ? "main" : profile);
	var ratio = clampBetweenZeroAndOne(accum / Math.max(1, maxAccum));
	var dischargeWidth = widths.start + Math.pow(ratio, 0.52) * (widths.end - widths.start);
	return Math.max(widths.start, parseInt(dischargeWidth));
}

function classifySegmentType(slope) {
	if (slope > 0.45) {
		return "rapid";
	}
	if (slope < 0.12) {
		return "pool";
	}
	return "normal";
}

function applySegmentTypeModifiers(pathMaskData, pathProfile) {
	if (pathMaskData == null) {
		return;
	}
	var rapidScale = pathProfile != null && pathProfile.type == "main" ? 0.95 : 0.9;
	for (var i = 0; i < pathMaskData.length; i++) {
		if (pathMaskData[i] == null) {
			continue;
		}
		var type = pathMaskData[i].segmentType;
		if (type == "rapid") {
			pathMaskData[i].width = Math.max(1, parseInt(pathMaskData[i].width * rapidScale));
			pathMaskData[i].depthMultiplier *= 0.92;
		} else if (type == "pool") {
			pathMaskData[i].width = Math.max(1, parseInt(pathMaskData[i].width * 1.05));
			pathMaskData[i].depthMultiplier *= 1.08;
		}
	}
}

function getBendSideAt(path, index) {
	if (path == null || index < 2 || index >= path.length - 2) {
		return 0;
	}
	var before = path[index - 1];
	var center = path[index];
	var after = path[index + 1];
	if (before == null || center == null || after == null || before[0] == null || after[0] == null) {
		return 0;
	}
	var ax = center[0] - before[0];
	var ay = center[1] - before[1];
	var bx = after[0] - center[0];
	var by = after[1] - center[1];
	var cross = ax * by - ay * bx;
	var al = Math.sqrt(ax * ax + ay * ay);
	var bl = Math.sqrt(bx * bx + by * by);
	if (al < 0.5 || bl < 0.5) {
		return 0;
	}
	var dot = (ax * bx + ay * by) / (al * bl);
	dot = Math.max(-1, Math.min(1, dot));
	var bend = clampBetweenZeroAndOne((1 - dot) * 0.5);
	if (bend < 0.08) {
		return 0;
	}
	return cross > 0 ? 1 : -1;
}

function getBendBankOffset(pathIndex, pointIndex, dist, width, lateralNorm, blockX, blockY) {
	if (!enableBendAsymmetry || pathIndex == null || pointIndex == null || dist < 0.35 || lateralNorm == null) {
		return { heightOffset: 0, depthScale: 1.0 };
	}
	if (foundPaths == null || pathIndex < 0 || pathIndex >= foundPaths.length) {
		return { heightOffset: 0, depthScale: 1.0 };
	}
	if (blockX != null && blockY != null && getConfluenceStrengthAt(blockX, blockY) > 0) {
		return { heightOffset: 0, depthScale: 1.05 };
	}
	if (width > 14 && dist < 0.52) {
		return { heightOffset: 0, depthScale: 1.0 };
	}
	var path = foundPaths[pathIndex];
	var bendSide = getBendSideAt(path, pointIndex);
	if (bendSide == 0) {
		return { heightOffset: 0, depthScale: 1.0 };
	}
	var center = path[pointIndex];
	if (center != null && center[0] != null && getConfluenceStrengthAt(center[0], center[1]) > 0) {
		return { heightOffset: 0, depthScale: 1.05 };
	}
	var outer = (bendSide > 0 && lateralNorm > 0.2) || (bendSide < 0 && lateralNorm < -0.2);
	var inner = (bendSide > 0 && lateralNorm < -0.2) || (bendSide < 0 && lateralNorm > 0.2);
	var strength = styleProfile == 3 ? 1.0 : 0.75;
	if (outer && dist > 0.48) {
		return {
			heightOffset: (0.18 + clampBetweenZeroAndOne(dist) * 0.35) * strength,
			depthScale: 1.0
		};
	}
	if (inner) {
		return {
			heightOffset: -(0.15 + clampBetweenZeroAndOne(dist) * 0.25) * strength,
			depthScale: 0.82
		};
	}
	return { heightOffset: 0, depthScale: 1.0 };
}

function buildPathWaterSurfaceProfile(path, pathMaskData, minWaterDepth) {
	if (path == null || pathMaskData == null) {
		return;
	}
	var bedHeights = [];
	for (var i = 0; i < path.length; i++) {
		if (path[i] == null || path[i][0] == null) {
			bedHeights.push(null);
			continue;
		}
		bedHeights.push(dimension.getHeightAt(path[i][0], path[i][1]));
	}
	for (var m = path.length - 2; m >= 0; m--) {
		if (bedHeights[m] == null || bedHeights[m + 1] == null) {
			continue;
		}
		if (bedHeights[m] < bedHeights[m + 1]) {
			bedHeights[m] = bedHeights[m + 1] - 0.05;
		}
	}

	for (var n = 0; n < path.length; n++) {
		if (path[n] == null || pathMaskData[n] == null || bedHeights[n] == null) {
			continue;
		}
		var targetDepth = (pathMaskData[n].width * riverDepth + 1.2) * pathMaskData[n].depthMultiplier * 0.35;
		var wl = Math.max(minWaterDepth, bedHeights[n] - targetDepth);
		var localGround = dimension.getHeightAt(path[n][0], path[n][1]);
		var pathSlope = pathMaskData[n].slope != null ? pathMaskData[n].slope : 0;
		var freeboard = getBankFreeboard(pathMaskData[n].width, pathSlope);
		wl = Math.min(wl, localGround - freeboard);
		wl = Math.max(minWaterDepth, wl);
		pathWaterSurfaceMap.put(toCoordinate(path[n][0], path[n][1]), wl);
	}
}

function addFloodplainShelf(path, pathMaskData, pathProfile, minWaterDepth) {
	if (!enableFloodplain || path == null || pathMaskData == null) {
		return;
	}
	for (var i = 0; i < path.length; i += 3) {
		if (pathMaskData[i] == null || pathMaskData[i].width <= 14) {
			continue;
		}
		var x = path[i][0];
		var y = path[i][1];
		if (x == null || y == null) {
			continue;
		}
		var tangent = getPathTangentAt(path, i);
		var perpX = -Math.sin(tangent);
		var perpY = Math.cos(tangent);
		var shelfWidth = 3;
		var shelfDist = pathMaskData[i].width + 2 + (i % 3);
		for (var side = -1; side <= 1; side += 2) {
			var sx = Math.round(x + perpX * shelfDist * side);
			var sy = Math.round(y + perpY * shelfDist * side);
			if (sx - minX < 0 || sx - minX >= worldWidth || sy - minY < 0 || sy - minY >= worldHeight || avoidCondition(sx, sy)) {
				continue;
			}
			setMaskMaxSize(sx, sy, shelfWidth, minWaterDepth, 0.05, pathMaskData[i].depthMultiplier * 0.35, 0, tangent, null, null);
		}
	}
}

function buildPathMaskData(path, pathProfile, pathScale, length, hydro) {
	var data = [];
	var manualWidthFloor = 1;
	var blend = riverMode == 1 ? dischargeWidthBlendDelta : dischargeWidthBlendClassic;
	var downstreamFloor = getProfileWidthRange(pathProfile).start;
	if (pathProfile != null && pathProfile.type == "main") {
		downstreamFloor = Math.max(downstreamFloor, Math.max(6, parseInt(endWidth * 0.35)));
	}
	for (var i = path.length - 1; i >= 0; i--) {
		var x = path[i][0];
		var y = path[i][1];
		if (x == null || y == null) {
			data[i] = null;
			continue;
		}
		var slope = getPathSlopeAt(path, i);
		var junctionBoost = getJunctionBoostAt(x, y);
		var linearWidth = getPathWidthAt(pathProfile, pathScale, length, i, path.length, slope, junctionBoost);
		var width = linearWidth;
		if (hydro != null) {
			var accum = sampleHydroAccumulationAt(hydro, x, y);
			var dischargeWidth = getDischargeWidthAt(accum, hydro.maxAccum, pathProfile, linearWidth);
			width = Math.max(1, parseInt(linearWidth * (1 - blend) + dischargeWidth * blend));
			width = Math.max(width, parseInt(linearWidth * 0.85), parseInt(downstreamFloor * 0.94));
		}
		var localFlow = getClassicLocalFlowAt(pathProfile, i, path.length);
		if (localFlow > 1) {
			width = Math.max(width, getManualFlowWidthFloor(localFlow));
			if (hydro != null) {
				var flowAccum = sampleHydroAccumulationAt(hydro, x, y) * localFlow;
				var flowWidth = getDischargeWidthAt(flowAccum, hydro.maxAccum, pathProfile, linearWidth);
				width = Math.max(width, parseInt(flowWidth * (0.85 + 0.08 * Math.min(localFlow, 6))));
			} else {
				width = Math.max(width, parseInt(getProfileWidthRange(pathProfile).start + (getProfileWidthRange(pathProfile).end - getProfileWidthRange(pathProfile).start) * (0.35 + 0.12 * Math.sqrt(localFlow - 1))));
			}
		}
		if (pathProfile.manualNetwork) {
			width = Math.max(width, manualWidthFloor);
			manualWidthFloor = Math.max(manualWidthFloor, width);
		}
		downstreamFloor = Math.max(downstreamFloor, width);
		var depthMultiplier = getProfileDepthMultiplier(pathProfile) * (pathProfile.type == "main" ? (1 + 0.08 * junctionBoost) : 1) * getBendDepthBoostAt(path, i, pathProfile.type);
		data[i] = {
			width: width,
			slope: slope,
			depthMultiplier: depthMultiplier,
			waterfallStrength: getWaterfallStrengthAt(x, y),
			segmentType: classifySegmentType(slope)
		};
	}
	return data;
}

function smoothPathWidths(pathMaskData) {
	if (pathMaskData == null || pathMaskData.length < 3) {
		return;
	}
	var window = 5;
	var half = parseInt(window / 2);
	var copy = [];
	for (var i = 0; i < pathMaskData.length; i++) {
		copy.push(pathMaskData[i] == null ? 0 : pathMaskData[i].width);
	}
	for (var j = 0; j < pathMaskData.length; j++) {
		if (pathMaskData[j] == null) {
			continue;
		}
		var sum = 0;
		var count = 0;
		for (var k = Math.max(0, j - half); k <= Math.min(pathMaskData.length - 1, j + half); k++) {
			if (copy[k] <= 0) {
				continue;
			}
			sum += copy[k];
			count++;
		}
		if (count > 0) {
			pathMaskData[j].width = Math.max(1, parseInt(sum / count));
		}
	}
}

function enforceClassicDownstreamWidthFloor(pathMaskData, pathProfile) {
	if (pathMaskData == null || pathProfile == null || pathProfile.manualNetwork) {
		return;
	}
	var floor = getProfileWidthRange(pathProfile).start;
	if (pathProfile.type == "main") {
		floor = Math.max(floor, Math.max(6, parseInt(endWidth * 0.35)));
	}
	for (var i = pathMaskData.length - 1; i >= 0; i--) {
		if (pathMaskData[i] == null) {
			continue;
		}
		floor = Math.max(floor, pathMaskData[i].width);
		pathMaskData[i].width = Math.max(pathMaskData[i].width, parseInt(floor * 0.94));
	}
}

function applyDownstreamWidthEnvelope(pathMaskData) {
	if (pathMaskData == null) {
		return;
	}
	for (var i = 0; i < pathMaskData.length - 1; i++) {
		if (pathMaskData[i] == null || pathMaskData[i + 1] == null) {
			continue;
		}
		pathMaskData[i].width = Math.max(pathMaskData[i].width, parseInt(pathMaskData[i + 1].width * 0.85));
	}
}

function applyConfluenceWidthEnvelope(path, pathMaskData, pathProfile) {
	if (pathMaskData == null || pathProfile == null || pathProfile.confluenceFlowSegments == null) {
		return;
	}
	for (var s = 0; s < pathProfile.confluenceFlowSegments.length; s++) {
		var segment = pathProfile.confluenceFlowSegments[s];
		if (segment == null) {
			continue;
		}
		var joinIndex = Math.max(0, Math.min(pathMaskData.length - 1, parseInt(segment.maxIndex)));
		if (pathMaskData[joinIndex] == null) {
			continue;
		}
		var extraFlow = segment.amount == null ? 1 : segment.amount;
		var growth = 1 + 0.28 * Math.sqrt(extraFlow);
		var tribHint = segment.tribWidthHint == null ? 0 : segment.tribWidthHint;
		var joinWidth = Math.max(pathMaskData[joinIndex].width, tribHint);
		for (var i = 0; i <= joinIndex; i++) {
			if (pathMaskData[i] == null) {
				continue;
			}
			var downstreamBlend = joinIndex <= 0 ? 1 : (0.75 + 0.25 * (i / joinIndex));
			pathMaskData[i].width = Math.max(pathMaskData[i].width, parseInt(joinWidth * growth * downstreamBlend));
		}
	}
}

function blendWaterfallDepthOnPathData(path, pathProfile, pathMaskData) {
	if (!enableWaterfalls || path == null || pathMaskData == null) {
		return;
	}
	var peaks = [];
	for (var i = 0; i < path.length; i++) {
		if (pathMaskData[i] == null) {
			continue;
		}
		var wf = pathMaskData[i].waterfallStrength;
		if (wf > 0) {
			peaks.push({ index: i, boost: 1 + wf * 0.4 });
		}
	}
	var radius = 4;
	for (var p = 0; p < peaks.length; p++) {
		var peak = peaks[p];
		for (var j = 0; j < path.length; j++) {
			if (pathMaskData[j] == null) {
				continue;
			}
			var dist = Math.abs(j - peak.index);
			if (dist > radius) {
				continue;
			}
			var t = 1 - dist / (radius + 1);
			var blended = pathMaskData[j].depthMultiplier * (1 + (peak.boost - 1) * t);
			pathMaskData[j].depthMultiplier = Math.max(pathMaskData[j].depthMultiplier, blended);
		}
	}
}

function getMinimumSealWidth(type, width) {
	width = Math.max(1, parseInt(width));
	if (type == "feeder" || type == "small") {
		return Math.max(2, width);
	}
	if (type == "medium") {
		return Math.max(4, parseInt(width * 0.55));
	}
	return Math.max(6, parseInt(width * 0.55));
}

function setMaskAlongSegment(x0, y0, x1, y1, width0, width1, minWaterDepth, slope, depthMultiplier, waterfallStrength, pathIndex, index0, index1) {
	var dx = x1 - x0;
	var dy = y1 - y0;
	var tangent = Math.atan2(dy, dx);
	var steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)), parseInt(Math.sqrt(dx * dx + dy * dy)));
	for (var s = 0; s <= steps; s++) {
		var t = s / steps;
		var x = Math.round(x0 + dx * t);
		var y = Math.round(y0 + dy * t);
		var segWidth = Math.max(1, parseInt(width0 + t * (width1 - width0)));
		var pointIndex = parseInt(index0 + t * (index1 - index0));
		setMaskMaxSize(x, y, segWidth, minWaterDepth, slope, depthMultiplier, waterfallStrength, tangent, pathIndex, pointIndex);
	}
}

function getWaterEdgeLimit(slope, width) {
	var limit = 0.55 + 0.08 * (1 - clampBetweenZeroAndOne(slope * 6));
	if (width > 12) {
		limit += 0.045 * clampBetweenZeroAndOne((width - 12) / 24);
		limit = Math.min(0.62, limit + 0.04);
	}
	return limit;
}

function getSlopeDepthAdjustments(slope, width) {
	var guardRailRelax = 1.0;
	var depthScale = 1.0;
	if (slope > 0.35) {
		guardRailRelax = styleProfile == 1 ? 0.72 : 0.78;
		depthScale = 1.0 + slope * 0.15;
	}
	return { guardRailRelax: guardRailRelax, depthScale: depthScale };
}

function getRiverBedReferenceHeight(dimension, x, y, slope) {
	var height = dimension.getHeightAt(x, y);
	if (slope <= 0.25) {
		return height;
	}
	var minH = height;
	if (x - 1 >= minX) {
		minH = Math.min(minH, dimension.getHeightAt(x - 1, y));
	}
	if (x + 1 < worldWidth + minX) {
		minH = Math.min(minH, dimension.getHeightAt(x + 1, y));
	}
	if (y - 1 >= minY) {
		minH = Math.min(minH, dimension.getHeightAt(x, y - 1));
	}
	if (y + 1 < worldHeight + minY) {
		minH = Math.min(minH, dimension.getHeightAt(x, y + 1));
	}
	return minH;
}

function sealRiverPathMask(path, pathProfile, minWaterDepth, pathMaskData, pathIndex) {
	if (path == null || path.length < 2) {
		return;
	}
	var type = pathProfile == null ? "main" : pathProfile.type;
	if (pathMaskData == null) {
		var widthRange = getProfileWidthRange(pathProfile == null ? type : pathProfile);
		if (!(type == "small" || type == "feeder" || widthRange.end <= 8)) {
			return;
		}
	}
	for (var i = 1; i < path.length; i++) {
		var p0 = path[i - 1];
		var p1 = path[i];
		if (p0 == null || p1 == null || p0[0] == null || p1[0] == null) {
			continue;
		}
		var w0;
		var w1;
		var slope;
		var depthMul;
		var wf;
		if (pathMaskData != null && pathMaskData[i - 1] != null && pathMaskData[i] != null) {
			w0 = getMinimumSealWidth(type, pathMaskData[i - 1].width);
			w1 = getMinimumSealWidth(type, pathMaskData[i].width);
			slope = (pathMaskData[i - 1].slope + pathMaskData[i].slope) * 0.5;
			depthMul = Math.max(pathMaskData[i - 1].depthMultiplier, pathMaskData[i].depthMultiplier) * 1.05;
			wf = Math.max(pathMaskData[i - 1].waterfallStrength, pathMaskData[i].waterfallStrength);
		} else {
			w0 = w1 = type == "small" || type == "feeder" ? 2 : (type == "medium" ? 3 : 4);
			slope = 0.15;
			depthMul = getProfileDepthMultiplier(pathProfile) * 1.08;
			wf = getWaterfallStrengthAt(p1[0], p1[1]);
		}
		setMaskAlongSegment(p0[0], p0[1], p1[0], p1[1], w0, w1, minWaterDepth, slope, depthMul, wf, pathIndex, i - 1, i);
	}
}

function addWaterfallPoint(x, y, strength) {
	if (strength <= 0) {
		return;
	}
	var key = toCoordinate(x, y);
	var current = waterfallMap.get(key);
	if (current == null || current[2] < strength) {
		waterfallMap.put(key, [x, y, strength]);
	}
}

function addJunctionBoost(x, y, strength, radius) {
	if (strength <= 0) {
		return;
	}
	radius = radius == null ? Math.max(8, parseInt(endWidth * 0.45)) : Math.max(4, parseInt(radius));
	for (var dx = -radius; dx <= radius; dx++) {
		for (var dy = -radius; dy <= radius; dy++) {
			var distSq = dx * dx + dy * dy;
			if (distSq > radius * radius) {
				continue;
			}
			var wx = x + dx;
			var wy = y + dy;
			if (wx - minX < 0 || wx - minX >= worldWidth || wy - minY < 0 || wy - minY >= worldHeight) {
				continue;
			}
			var falloff = 1 - Math.sqrt(distSq) / radius;
			var localStrength = strength * falloff;
			var key = toCoordinate(wx, wy);
			var current = junctionBoostMap.get(key);
			if (current == null || current[2] < localStrength) {
				junctionBoostMap.put(key, [wx, wy, localStrength]);
			}
		}
	}
}

function addConfluenceMergeZone(mainPath, joinIndex, branchPath, branchWidthScale, deepMerge) {
	if (mainPath == null || branchPath == null || branchPath.length < 2 || mainPath.length < 2) {
		return;
	}
	joinIndex = Math.max(0, Math.min(mainPath.length - 1, parseInt(joinIndex)));
	branchWidthScale = branchWidthScale == null ? 0.35 : branchWidthScale;
	if (branchWidthScale < 0.35) {
		return;
	}
	var joinPoint = mainPath[joinIndex];
	if (joinPoint == null || joinPoint[0] == null) {
		return;
	}

	var mergeWidth = Math.max(5, Math.min(16, parseInt(endWidth * (0.20 + branchWidthScale * 0.22))));
	if (deepMerge) {
		mergeWidth = Math.max(mergeWidth, parseInt(mergeWidth * 1.15));
	}
	var tail = Math.max(6, Math.min(16, parseInt(endWidth * 0.45)));
	var mainWindow = Math.max(5, Math.min(16, parseInt(endWidth * 0.45)));
	var junctionStrength = deepMerge ? 0.85 : 0.32;
	var joinDepth = deepMerge ? 0.72 : 0.64;
	var discScale = deepMerge ? 1.1 : 0.82;
	addJunctionBoost(joinPoint[0], joinPoint[1], junctionStrength, Math.max(4, parseInt(mergeWidth * 0.9)));
	registerConfluencePoint(joinPoint[0], joinPoint[1], deepMerge ? 1.35 : 1.0, Math.max(6, parseInt(mergeWidth * 0.55)));
	addMergeMaskDisc(joinPoint[0], joinPoint[1], parseInt(mergeWidth * discScale), joinDepth);

	for (var m = Math.max(0, joinIndex - mainWindow); m <= Math.min(mainPath.length - 1, joinIndex + mainWindow); m += 3) {
		var mp = mainPath[m];
		if (mp == null || mp[0] == null) {
			continue;
		}
		addMergeMaskDisc(mp[0], mp[1], Math.max(2, parseInt(mergeWidth * 0.28)), 0.52);
	}

	var start = 0;
	var end = Math.min(branchPath.length, tail);
	for (var i = start; i < end; i += 2) {
		var bp = branchPath[i];
		if (bp == null || bp[0] == null) {
			continue;
		}
		if (i <= 4) {
			addMergeMaskLine(bp[0], bp[1], joinPoint[0], joinPoint[1], Math.max(2, parseInt(mergeWidth * 0.32)), 0.58);
		}
		addMergeMaskDisc(bp[0], bp[1], Math.max(2, parseInt(mergeWidth * 0.26)), 0.50);
	}
}

function addTinyFeederMouth(mainPath, joinIndex, feederPath) {
	if (mainPath == null || feederPath == null || feederPath.length < 2) {
		return;
	}
	joinIndex = Math.max(0, Math.min(mainPath.length - 1, parseInt(joinIndex)));
	var joinPoint = mainPath[joinIndex];
	if (joinPoint == null || joinPoint[0] == null) {
		return;
	}
	addJunctionBoost(joinPoint[0], joinPoint[1], 0.12, 3);
	addMergeMaskDisc(joinPoint[0], joinPoint[1], 2, 0.38);
	for (var m = Math.max(0, joinIndex - 4); m <= Math.min(mainPath.length - 1, joinIndex + 4); m += 2) {
		var mp = mainPath[m];
		if (mp != null && mp[0] != null) {
			addMergeMaskDisc(mp[0], mp[1], 2, 0.34);
		}
	}
	var mouthLength = Math.min(10, feederPath.length);
	for (var i = 0; i < mouthLength; i += 2) {
		var fp = feederPath[i];
		if (fp != null && fp[0] != null) {
			addMergeMaskDisc(fp[0], fp[1], i < 4 ? 2 : 1, 0.32);
		}
	}
}

function addMergeMaskDisc(x, y, width, depthMultiplier) {
	if (x == null || y == null) {
		return;
	}
	if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight || avoidCondition(x, y)) {
		return;
	}
	setMaskMaxSize(x, y, Math.max(1, parseInt(width)), dimension.getWaterLevelAt(x, y), 0.05, depthMultiplier, getWaterfallStrengthAt(x, y));
}

function addMergeMaskLine(x0, y0, x1, y1, width, depthMultiplier) {
	var dx = x1 - x0;
	var dy = y1 - y0;
	var steps = Math.max(1, parseInt(Math.sqrt(dx * dx + dy * dy)));
	for (var i = 0; i <= steps; i++) {
		var t = i / steps;
		var x = Math.round(x0 + dx * t);
		var y = Math.round(y0 + dy * t);
		addMergeMaskDisc(x, y, width, depthMultiplier);
	}
}

function findNearestPathPoint(path, x, y, startIndex, endIndex) {
	var best = null;
	var bestDist = Infinity;
	startIndex = Math.max(0, parseInt(startIndex));
	endIndex = Math.min(path.length - 1, parseInt(endIndex));
	for (var i = startIndex; i <= endIndex; i++) {
		var p = path[i];
		if (p == null || p[0] == null) {
			continue;
		}
		var dist = getSquaredDistance(x, y, p[0], p[1]);
		if (dist < bestDist) {
			bestDist = dist;
			best = p;
		}
	}
	return best;
}

function registerConfluencePoint(x, y, strength, radius) {
	if (x == null || y == null || strength <= 0) {
		return;
	}
	radius = radius == null ? Math.max(6, parseInt(endWidth * 0.45)) : Math.max(4, parseInt(radius));
	for (var dx = -radius; dx <= radius; dx++) {
		for (var dy = -radius; dy <= radius; dy++) {
			var distSq = dx * dx + dy * dy;
			if (distSq > radius * radius) {
				continue;
			}
			var wx = x + dx;
			var wy = y + dy;
			if (wx - minX < 0 || wx - minX >= worldWidth || wy - minY < 0 || wy - minY >= worldHeight) {
				continue;
			}
			var falloff = 1 - Math.sqrt(distSq) / radius;
			var localStrength = strength * falloff;
			var key = toCoordinate(wx, wy);
			var current = confluenceMap.get(key);
			if (current == null || current[2] < localStrength) {
				confluenceMap.put(key, [wx, wy, localStrength]);
			}
		}
	}
}

function getConfluenceStrengthAt(x, y) {
	var data = confluenceMap.get(toCoordinate(x, y));
	if (data == null) {
		return 0;
	}
	return data[2];
}

function getJunctionBoostAt(x, y) {
	var data = junctionBoostMap.get(toCoordinate(x, y));
	if (data == null) {
		return 0;
	}
	return data[2];
}

function getClassicLocalFlowAt(profile, index, pathLength) {
	var flow = 1;
	if (profile == null || profile.confluenceFlowSegments == null) {
		return flow;
	}
	for (var s = 0; s < profile.confluenceFlowSegments.length; s++) {
		var segment = profile.confluenceFlowSegments[s];
		if (segment == null) {
			continue;
		}
		var joinIndex = Math.max(0, Math.min(pathLength - 1, parseInt(segment.maxIndex)));
		var transition = Math.max(8, parseInt(segment.transition == null ? 32 : segment.transition));
		if (index <= joinIndex) {
			flow += segment.amount;
		} else if (index <= joinIndex + transition) {
			flow += segment.amount * (1 - ((index - joinIndex) / transition));
		}
	}
	return Math.max(1, flow);
}

function getClassicTributaryWidthHint(tribPath, tribJoinIndex, tribProfile) {
	if (tribPath == null || tribPath.length < 2) {
		return getProfileWidthRange(tribProfile == null ? "medium" : tribProfile).start;
	}
	tribJoinIndex = Math.max(0, Math.min(tribPath.length - 1, parseInt(tribJoinIndex)));
	var slope = getPathSlopeAt(tribPath, tribJoinIndex);
	var linear = getPathWidthAt(tribProfile == null ? makePathProfile("medium") : tribProfile, 1.0, Math.max(tribPath.length, 200), tribJoinIndex, tribPath.length, slope, 0);
	return Math.max(getProfileWidthRange(tribProfile == null ? "medium" : tribProfile).start, linear);
}

function expandWaterAlongPathWidth(paths, pathMaskDataList) {
	if (paths == null || pathMaskDataList == null) {
		return;
	}
	for (var pathIndex = 0; pathIndex < paths.length; pathIndex++) {
		checkForAbort(false);
		var path = paths[pathIndex];
		var maskData = pathMaskDataList[pathIndex];
		if (path == null || maskData == null || path.length < 1 || path[0] == null || path[0][0] == null) {
			continue;
		}
		var minWaterDepth = dimension.getWaterLevelAt(path[0][0], path[0][1]);
		for (var i = 0; i < path.length; i += 2) {
			if (path[i] == null || path[i][0] == null || maskData[i] == null) {
				continue;
			}
			var x = path[i][0];
			var y = path[i][1];
			var width = Math.max(2, maskData[i].width);
			var profileWater = pathWaterSurfaceMap.get(toCoordinate(x, y));
			var centerWater = profileWater != null ? Math.max(minWaterDepth, profileWater) : Math.max(minWaterDepth, dimension.getWaterLevelAt(x, y));
			var radius = Math.max(2, parseInt(width * 0.58));
			for (var dx = -radius; dx <= radius; dx++) {
				for (var dy = -radius; dy <= radius; dy++) {
					if (dx * dx + dy * dy > radius * radius) {
						continue;
					}
					var wx = x + dx;
					var wy = y + dy;
					if (wx - minX < 0 || wx - minX >= worldWidth || wy - minY < 0 || wy - minY >= worldHeight || avoidCondition(wx, wy)) {
						continue;
					}
					var blockGround = dimension.getHeightAt(wx, wy);
					if (blockGround <= centerWater) {
						var current = dimension.getWaterLevelAt(wx, wy);
						if (centerWater > current) {
							dimension.setWaterLevelAt(wx, wy, centerWater);
						}
					}
				}
			}
		}
	}
}

function addClassicFlowSegment(profile, joinIndex, amount, path) {
	if (profile == null || joinIndex == null || joinIndex < 0) {
		return;
	}
	if (profile.confluenceFlowSegments == null) {
		profile.confluenceFlowSegments = [];
	}
	var pathLength = path == null ? 0 : path.length;
	var transition = Math.max(18, Math.min(90, parseInt(Math.max(1, pathLength) * 0.035)));
	profile.confluenceFlowSegments.push({
		maxIndex: Math.max(0, parseInt(joinIndex)),
		amount: amount,
		transition: transition
	});
}

function propagateClassicFlow(pathProfiles, mainIndex, joinIndex, amount, mainPath) {
	if (pathProfiles == null || mainIndex < 0 || mainIndex >= pathProfiles.length) {
		return;
	}
	var profile = pathProfiles[mainIndex];
	if (profile == null) {
		return;
	}
	addClassicFlowSegment(profile, joinIndex, amount, mainPath);
}

function detectClassicConfluences(paths, pathProfiles) {
	if (paths == null || paths.length < 2) {
		return;
	}
	var maxJoinDist = Math.max(18, parseInt(endWidth * 0.55));
	var maxJoinDistSq = maxJoinDist * maxJoinDist;
	var sourceFraction = 0.72;
	var interiorFraction = 0.15;
	var detected = 0;
	var usedPairs = {};

	for (var a = 0; a < paths.length; a++) {
		var pathA = paths[a];
		if (pathA == null || pathA.length < 24) {
			continue;
		}
		for (var b = a + 1; b < paths.length; b++) {
			var pathB = paths[b];
			if (pathB == null || pathB.length < 24) {
				continue;
			}
			var best = null;
			var bestDistSq = Infinity;
			for (var ia = 0; ia < pathA.length; ia += 2) {
				var pa = pathA[ia];
				if (pa == null || pa[0] == null) {
					continue;
				}
				var nearestB = findNearestPathPointWithIndex(pathB, pa[0], pa[1], 0, pathB.length - 1);
				if (nearestB == null || nearestB.distSq > maxJoinDistSq || nearestB.distSq >= bestDistSq) {
					continue;
				}
				bestDistSq = nearestB.distSq;
				best = { pathAIndex: a, pathBIndex: b, indexA: ia, indexB: nearestB.index, distSq: nearestB.distSq };
			}
			for (var ib = 0; ib < pathB.length; ib += 2) {
				var pb = pathB[ib];
				if (pb == null || pb[0] == null) {
					continue;
				}
				var nearestA = findNearestPathPointWithIndex(pathA, pb[0], pb[1], 0, pathA.length - 1);
				if (nearestA == null || nearestA.distSq > maxJoinDistSq || nearestA.distSq >= bestDistSq) {
					continue;
				}
				bestDistSq = nearestA.distSq;
				best = { pathAIndex: a, pathBIndex: b, indexA: nearestA.index, indexB: ib, distSq: nearestA.distSq };
			}
			if (best == null) {
				continue;
			}

			var tribPathIndex = null;
			var mainPathIndex = null;
			var tribJoinIndex = null;
			var mainJoinIndex = null;
			var tribPath = null;
			var mainPath = null;
			var mouthFraction = 1 - sourceFraction;

			if (best.indexA >= pathA.length * sourceFraction && best.indexB > pathB.length * interiorFraction) {
				tribPathIndex = a;
				mainPathIndex = b;
				tribJoinIndex = best.indexA;
				mainJoinIndex = best.indexB;
			} else if (best.indexB >= pathB.length * sourceFraction && best.indexA > pathA.length * interiorFraction) {
				tribPathIndex = b;
				mainPathIndex = a;
				tribJoinIndex = best.indexB;
				mainJoinIndex = best.indexA;
			} else if (best.indexA <= pathA.length * mouthFraction && best.indexB > pathB.length * interiorFraction) {
				tribPathIndex = a;
				mainPathIndex = b;
				tribJoinIndex = best.indexA;
				mainJoinIndex = best.indexB;
			} else if (best.indexB <= pathB.length * mouthFraction && best.indexA > pathA.length * interiorFraction) {
				tribPathIndex = b;
				mainPathIndex = a;
				tribJoinIndex = best.indexB;
				mainJoinIndex = best.indexA;
			} else {
				continue;
			}

			tribPath = paths[tribPathIndex];
			mainPath = paths[mainPathIndex];
			if (!isNaturalTributaryJoin(tribPath, mainPath, mainJoinIndex)) {
				continue;
			}

			var pairKey = tribPathIndex + ":" + mainPathIndex + ":" + mainJoinIndex;
			if (usedPairs[pairKey]) {
				continue;
			}
			usedPairs[pairKey] = true;

			var joinPoint = mainPath[mainJoinIndex];
			if (joinPoint == null || joinPoint[0] == null) {
				continue;
			}

			propagateClassicFlow(pathProfiles, mainPathIndex, mainJoinIndex, 1, mainPath);
			var mainProfile = pathProfiles[mainPathIndex];
			if (mainProfile != null && mainProfile.confluenceFlowSegments != null && mainProfile.confluenceFlowSegments.length > 0) {
				mainProfile.confluenceFlowSegments[mainProfile.confluenceFlowSegments.length - 1].tribWidthHint = getClassicTributaryWidthHint(tribPath, tribJoinIndex, pathProfiles[tribPathIndex]);
			}
			addConfluenceMergeZone(mainPath, mainJoinIndex, tribPath, 0.5, true);
			addJunctionBoost(joinPoint[0], joinPoint[1], 0.85, Math.max(5, parseInt(endWidth * 0.35)));
			detected++;
		}
	}
	if (detected > 0) {
		print("[Classic] Birlesme noktasi algilandi: " + detected);
	}
}

function stitchNearbyRiverChannels(paths, pathProfiles) {
	if (paths == null || paths.length < 2) {
		return;
	}
	var totalStitches = 0;
	for (var a = 0; a < paths.length; a++) {
		var pathA = paths[a];
		var typeA = pathProfiles != null && pathProfiles[a] != null ? pathProfiles[a].type : "main";
		if (pathA == null || pathA.length < 20 || typeA == "feeder") {
			continue;
		}
		for (var b = a + 1; b < paths.length; b++) {
			var pathB = paths[b];
			var typeB = pathProfiles != null && pathProfiles[b] != null ? pathProfiles[b].type : "main";
			if (pathB == null || pathB.length < 20 || typeB == "feeder") {
				continue;
			}
			var stitchDistance = getChannelStitchDistance(typeA, typeB);
			var stitchDistanceSq = stitchDistance * stitchDistance;
			var bestPair = null;
			var bestDistSq = Infinity;
			for (var i = 0; i < pathA.length; i += 10) {
				var p = pathA[i];
				if (p == null || p[0] == null) {
					continue;
				}
				var nearest = findNearestPathPoint(pathB, p[0], p[1], 0, pathB.length - 1);
				if (nearest == null || nearest[0] == null) {
					continue;
				}
				var distSq = getSquaredDistance(p[0], p[1], nearest[0], nearest[1]);
				if (distSq > stitchDistanceSq || distSq >= bestDistSq) {
					continue;
				}
				bestDistSq = distSq;
				bestPair = [p[0], p[1], nearest[0], nearest[1]];
			}
			if (bestPair != null) {
				var width = Math.max(2, Math.min(8, parseInt(stitchDistance * 0.22)));
				addMergeMaskLine(bestPair[0], bestPair[1], bestPair[2], bestPair[3], width, 0.56);
				addMergeMaskDisc(bestPair[0], bestPair[1], Math.max(width, parseInt(stitchDistance * 0.20)), 0.50);
				addMergeMaskDisc(bestPair[2], bestPair[3], Math.max(width, parseInt(stitchDistance * 0.20)), 0.50);
				addJunctionBoost(bestPair[0], bestPair[1], 0.18, Math.max(3, parseInt(width * 1.1)));
				totalStitches++;
			}
		}
	}
	if (totalStitches > 0) {
		print("[Merge] Yakin paralel/ayrik kanallar birlestirildi: " + totalStitches + " stitch");
	}
}

function getChannelStitchDistance(typeA, typeB) {
	if (typeA == "main" || typeB == "main") {
		return Math.max(26, parseInt(endWidth * 1.45));
	}
	if (typeA == "medium" || typeB == "medium") {
		return Math.max(18, parseInt(endWidth * 1.05));
	}
	return Math.max(9, parseInt(endWidth * 0.45));
}

function markWaterfallsOnPath(path) {
	if (path == null || path.length < 4) {
		return;
	}

	var created = 0;
	for (var i = path.length - 2; i >= 1 && created < maxWaterfallsPerRiver; i--) {
		var up = path[i];
		var down = path[i - 1];
		if (up == null || down == null || up[0] == null || down[0] == null) {
			continue;
		}

		var drop = dimension.getHeightAt(up[0], up[1]) - dimension.getHeightAt(down[0], down[1]);
		if (drop < minWaterfallDrop || Math.random() > waterfallChance) {
			continue;
		}

		var strength = clampBetweenZeroAndOne((drop - minWaterfallDrop + 1) / 3);
		addWaterfallPoint(up[0], up[1], Math.max(0.5, strength));
		addWaterfallPoint(down[0], down[1], Math.max(0.8, strength));
		if (i > 1) {
			var lower = path[i - 2];
			if (lower != null && lower[0] != null) {
				addWaterfallPoint(lower[0], lower[1], Math.max(0.45, strength * 0.7));
			}
		}

		created++;
	}
}

function applyRiverModeSettings() {
	riverMode = parseInt(riverMode);
	if (isNaN(riverMode) || riverMode < 0 || riverMode > 1) {
		riverMode = 0;
	}
	riverLayoutPreset = parseInt(riverLayoutPreset);
	if (isNaN(riverLayoutPreset) || riverLayoutPreset < 0 || riverLayoutPreset > 1) {
		riverLayoutPreset = 1;
	}
	modeRiverCount = Math.max(1, parseInt(modeRiverCount));
	if (isNaN(modeRiverCount)) {
		modeRiverCount = 6;
	}
	startPositionLayer = "";
	randomStartingPositions = Math.max(5, modeRiverCount);
	if (hasManualDrainageInput()) {
		riverMode = 0;
		startPositionLayer = "";
		randomStartingPositions = 0;
	}
	if (riverMode == 1) {
		startPositionLayer = "";
		randomStartingPositions = 0;
		if (styleProfile == 0) {
			applyStyleProfile(3);
		}
		modeRiverCount = Math.min(Math.max(modeRiverCount, 4), 7);
		startWidth = Math.max(startWidth, 8);
		endWidth = Math.max(endWidth, 30);
		enableWaterfalls = true;
		maxWaterfallsPerRiver = Math.max(maxWaterfallsPerRiver, 3);
	}
}

function hasManualStartCoords() {
	if (manualStartCoords == null) {
		return false;
	}
	return ("" + manualStartCoords).replace(/\s/g, "").length > 0;
}

function hasManualSourceLayer() {
	if (manualSourceLayer == null) {
		return false;
	}
	return ("" + manualSourceLayer).replace(/\s/g, "").length > 0;
}

function hasManualSourceTerrain() {
	return getManualSourceTerrain() != null && manualSourceTerrainHasPaintedPixels;
}

function getManualSourceTerrain() {
	if (manualSourceTerrain == null || ("" + manualSourceTerrain).replace(/\s/g, "").length == 0) {
		return null;
	}
	return terrainMap.get(("" + manualSourceTerrain).replaceAll(" ", "").toLocaleLowerCase());
}

function hasManualDrainageInput() {
	return hasManualStartCoords() || hasManualSourceLayer() || hasManualSourceTerrain();
}

function parseManualStartCoords(text) {
	var result = [];
	if (text == null) {
		return result;
	}
	var parts = ("" + text).replace(/\r/g, "\n").split(/[;\n]+/);
	for (var i = 0; i < parts.length; i++) {
		var part = parts[i];
		if (part == null || part.replace(/\s/g, "").length == 0) {
			continue;
		}
		var match = part.match(/(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)/);
		if (match == null) {
			print("Elle koordinat okunamadi: " + part);
			continue;
		}
		var x = Math.round(parseFloat(match[1]));
		var y = Math.round(parseFloat(match[2]));
		if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight) {
			print("Elle koordinat harita disinda: " + x + "," + y);
			continue;
		}
		if (avoidCondition(x, y)) {
			print("Elle koordinat avoid/sinir alaninda: " + x + "," + y);
			continue;
		}
		result.push([x, y]);
	}
	return result;
}

function findManualSourceTerrainPositions(sourceTerrain) {
	var result = [];
	if (sourceTerrain == null) {
		return result;
	}
	var lastProgress = -1;
	for (var x = minX; x < minX + worldWidth; x++) {
		checkForAbort(false);
		var progress = parseInt(((x - minX) / Math.max(1, worldWidth)) * 100);
		if (progress >= lastProgress + 10) {
			print("[ManualTerrain] kaynak terrain taraniyor: %" + progress);
			lastProgress = progress;
		}
		for (var y = minY; y < minY + worldHeight; y++) {
			var t = dimension.getTerrainAt(x, y);
			if ((t == sourceTerrain || (t != null && t.equals(sourceTerrain))) && !avoidLayerCondition(x, y)) {
				result.push([x, y]);
			}
		}
	}
	return result;
}

function generateManualDrainageNetwork(startPositions, targetSeaLevel) {
	var paths = [];
	var widthScales = [];
	var pathProfiles = [];
	var flowCounts = [];
	var downstreamIndex = [];
	var downstreamJoinIndex = [];
	var blockMap = new HashMap();
	if (startPositions == null || startPositions.length == 0) {
		return { paths: paths, widthScales: widthScales, pathProfiles: pathProfiles };
	}

	startPositions = dedupeManualSources(sortManualSourcesByHeight(startPositions));
	print("[ManualNetwork] Dag kaynaklari isleniyor: " + startPositions.length + " hedef seviye=" + targetSeaLevel);

	for (var i = 0; i < Math.min(startPositions.length, MaxOrigins); i++) {
		checkForAbort(false);
		var source = startPositions[i];
		var sx = source[0];
		var sy = source[1];
		print("[ManualNetwork] Kaynak " + (i + 1) + "/" + Math.min(startPositions.length, MaxOrigins) + ": " + sx + "," + sy);
		var fullPath = findGuaranteedManualPath(sx, sy, null);
		if (!isUsablePath(fullPath, 2)) {
			print("[ManualNetwork] Kaynak icin path uretilemedi: " + sx + "," + sy);
			continue;
		}
		fullPath = normalizeContinuousPath(stylizeRiverPath(densifyPath(fullPath, 1), false));
		var join = findManualDrainageJoin(fullPath, paths, pathProfiles);
		var acceptedPath;
		var newIndex = paths.length;
		if (join != null) {
			acceptedPath = fullPath.slice(join.pathIndex);
			if (acceptedPath.length < 2) {
				continue;
			}
			acceptedPath = prependConnectorToPath(acceptedPath, join.point);
			acceptedPath = normalizeContinuousPath(densifyPath(acceptedPath, 1));
			paths.push(acceptedPath);
			widthScales.push(1.0);
			flowCounts.push(1);
			downstreamIndex.push(join.targetPathIndex);
			downstreamJoinIndex.push(join.targetIndex);
			pathProfiles.push(makeManualFlowProfile(1));
			addTinyFeederMouth(paths[join.targetPathIndex], join.targetIndex, acceptedPath);
			propagateManualFlowFromPath(newIndex, flowCounts, downstreamIndex, downstreamJoinIndex, pathProfiles, paths, 1);
			print("[ManualNetwork] Kaynak mevcut agla birlesti. target=" + join.targetPathIndex + " flow=" + flowCounts[join.targetPathIndex]);
		} else {
			acceptedPath = fullPath;
			paths.push(acceptedPath);
			widthScales.push(1.0);
			flowCounts.push(1);
			downstreamIndex.push(-1);
			downstreamJoinIndex.push(-1);
			pathProfiles.push(makeManualFlowProfile(1));
			print("[ManualNetwork] Kaynak ana cikisa kadar indi.");
		}
		addPathToBlockMap(acceptedPath, blockMap, getManualNetworkBlockRadius(flowCounts[newIndex]));
		printPathNaturalStats("[ManualNetwork] kaynak", acceptedPath, pathProfiles[newIndex].type);
	}

	for (var p = 0; p < paths.length; p++) {
		var previousProfile = pathProfiles[p];
		pathProfiles[p] = makeManualFlowProfile(flowCounts[p]);
		if (previousProfile != null && previousProfile.manualFlowSegments != null) {
			pathProfiles[p].manualFlowSegments = previousProfile.manualFlowSegments;
		}
		if (downstreamIndex[p] >= 0) {
			pathProfiles[p].skipOutletExtension = true;
		}
	}
	print("[ManualNetwork] Tamamlandi. Path=" + paths.length);
	return { paths: paths, widthScales: widthScales, pathProfiles: pathProfiles };
}

function dedupeManualSources(startPositions) {
	var result = [];
	var minSpacing = Math.max(12, Math.min(48, parseInt(Math.min(worldWidth, worldHeight) * 0.004)));
	var minSpacingSq = minSpacing * minSpacing;
	for (var i = 0; i < startPositions.length; i++) {
		var p = startPositions[i];
		var ok = true;
		for (var r = 0; r < result.length; r++) {
			if (getSquaredDistance(p[0], p[1], result[r][0], result[r][1]) < minSpacingSq) {
				ok = false;
				break;
			}
		}
		if (ok) {
			result.push(p);
		}
	}
	return result;
}

function normalizeContinuousPath(path) {
	if (path == null || path.length < 2) {
		return path;
	}
	var result = [];
	result.push(path[0]);
	for (var i = 1; i < path.length; i++) {
		var prev = result[result.length - 1];
		var next = path[i];
		if (prev == null || next == null || prev[0] == null || next[0] == null) {
			continue;
		}
		var dx = next[0] - prev[0];
		var dy = next[1] - prev[1];
		var steps = Math.max(Math.abs(dx), Math.abs(dy));
		if (steps <= 0) {
			continue;
		}
		for (var s = 1; s <= steps; s++) {
			var t = s / steps;
			var x = Math.round(prev[0] + dx * t);
			var y = Math.round(prev[1] + dy * t);
			var last = result[result.length - 1];
			if (last[0] != x || last[1] != y) {
				result.push([x, y]);
			}
		}
	}
	return result;
}

function prependConnectorToPath(path, joinPoint) {
	if (path == null || path.length < 1 || joinPoint == null || joinPoint[0] == null) {
		return path;
	}
	var head = path[0];
	if (head == null || head[0] == null) {
		return path;
	}
	var connector = densifyPath([[joinPoint[0], joinPoint[1]], [head[0], head[1]]], 1);
	var result = [];
	for (var c = 0; c < connector.length; c++) {
		result.push(connector[c]);
	}
	for (var i = 1; i < path.length; i++) {
		result.push(path[i]);
	}
	return result;
}

function sortManualSourcesByHeight(startPositions) {
	var copy = [];
	for (var i = 0; i < startPositions.length; i++) {
		var p = startPositions[i];
		if (p == null || p[0] == null) {
			continue;
		}
		copy.push([p[0], p[1], dimension.getHeightAt(p[0], p[1])]);
	}
	copy.sort(function (a, b) { return b[2] - a[2]; });
	var result = [];
	for (var c = 0; c < copy.length; c++) {
		result.push([copy[c][0], copy[c][1]]);
	}
	return result;
}

function findManualDrainageJoin(path, paths, pathProfiles) {
	if (path == null || path.length < 40 || paths == null || paths.length == 0) {
		return null;
	}
	var best = null;
	var bestScore = Infinity;
	var sourceBuffer = Math.max(30, parseInt(path.length * 0.06));
	var mouthBuffer = 10;
	for (var i = path.length - 1 - sourceBuffer; i >= mouthBuffer; i -= 5) {
		var p = path[i];
		if (p == null || p[0] == null) {
			continue;
		}
		for (var targetPathIndex = 0; targetPathIndex < paths.length; targetPathIndex++) {
			var targetPath = paths[targetPathIndex];
			if (targetPath == null || targetPath.length < 20) {
				continue;
			}
			var nearest = findNearestPathPointWithIndex(targetPath, p[0], p[1], 0, targetPath.length - 1);
			if (nearest == null) {
				continue;
			}
			var targetType = pathProfiles != null && pathProfiles[targetPathIndex] != null ? pathProfiles[targetPathIndex].type : "small";
			var joinRadius = getManualJoinRadius(targetType);
			if (nearest.distSq > joinRadius * joinRadius) {
				continue;
			}
			var score = (path.length - i) * 0.25 + nearest.distSq;
			if (score < bestScore) {
				bestScore = score;
				best = {
					pathIndex: i,
					targetPathIndex: targetPathIndex,
					targetIndex: nearest.index,
					point: nearest.point
				};
			}
		}
		if (best != null && (path.length - i) > sourceBuffer + 80) {
			break;
		}
	}
	return best;
}

function findNearestPathPointWithIndex(path, x, y, startIndex, endIndex) {
	var best = null;
	var bestDist = Infinity;
	startIndex = Math.max(0, parseInt(startIndex));
	endIndex = Math.min(path.length - 1, parseInt(endIndex));
	for (var i = startIndex; i <= endIndex; i += 3) {
		var p = path[i];
		if (p == null || p[0] == null) {
			continue;
		}
		var dist = getSquaredDistance(x, y, p[0], p[1]);
		if (dist < bestDist) {
			bestDist = dist;
			best = { point: p, index: i, distSq: dist };
		}
	}
	return best;
}

function getManualJoinRadius(targetType) {
	if (targetType == "main") {
		return Math.max(42, parseInt(endWidth * 1.7));
	}
	if (targetType == "medium") {
		return Math.max(30, parseInt(endWidth * 1.25));
	}
	return Math.max(18, parseInt(endWidth * 0.8));
}

function propagateManualFlow(index, flowCounts, downstreamIndex, pathProfiles, amount) {
	var guard = 0;
	while (index != null && index >= 0 && guard < 256) {
		flowCounts[index] = Math.max(1, (flowCounts[index] == null ? 1 : flowCounts[index]) + amount);
		pathProfiles[index] = makeManualFlowProfile(flowCounts[index]);
		index = downstreamIndex[index];
		guard++;
	}
}

function propagateManualFlowFromPath(childIndex, flowCounts, downstreamIndex, downstreamJoinIndex, pathProfiles, paths, amount) {
	var child = childIndex;
	var guard = 0;
	while (child != null && child >= 0 && guard < 256) {
		var parent = downstreamIndex[child];
		if (parent == null || parent < 0) {
			break;
		}
		flowCounts[parent] = Math.max(1, (flowCounts[parent] == null ? 1 : flowCounts[parent]) + amount);
		var previousProfile = pathProfiles[parent];
		var previousSegments = previousProfile != null && previousProfile.manualFlowSegments != null ? previousProfile.manualFlowSegments : [];
		pathProfiles[parent] = makeManualFlowProfile(flowCounts[parent]);
		pathProfiles[parent].manualFlowSegments = previousSegments;
		addManualFlowSegment(pathProfiles[parent], downstreamJoinIndex[child], amount, paths[parent]);
		child = parent;
		guard++;
	}
}

function addManualFlowSegment(profile, joinIndex, amount, path) {
	if (profile == null || joinIndex == null || joinIndex < 0) {
		return;
	}
	if (profile.manualFlowSegments == null) {
		profile.manualFlowSegments = [];
	}
	var pathLength = path == null ? 0 : path.length;
	var transition = Math.max(18, Math.min(90, parseInt(Math.max(1, pathLength) * 0.035)));
	profile.manualFlowSegments.push({
		maxIndex: Math.max(0, parseInt(joinIndex)),
		amount: amount,
		transition: transition
	});
}

function makeManualFlowProfile(flowCount) {
	flowCount = Math.max(1, parseInt(flowCount));
	var end = 4 + Math.min(34, parseInt(Math.pow(flowCount, 0.78) * 5.2));
	var type = flowCount >= 5 ? "main" : (flowCount >= 2 ? "medium" : "small");
	return {
		type: type,
		manualNetwork: true,
		baseFlowCount: 1,
		totalFlowCount: flowCount,
		manualFlowSegments: [],
		fixedWidth: 0,
		customStartWidth: 1,
		customEndWidth: end,
		depthMultiplier: flowCount >= 5 ? 1.25 : (flowCount >= 2 ? 1.12 : 1.0)
	};
}

function getManualNetworkBlockRadius(flowCount) {
	flowCount = Math.max(1, parseInt(flowCount));
	return Math.max(5, Math.min(32, 4 + flowCount * 4));
}

function findGuaranteedManualPath(sx, sy, blockedMap) {
	var budget = Math.max(350000, Math.min(5000000, parseInt(worldWidth * worldHeight * 0.08)));
	var path = findManualPathToLevelOrBoundary(sx, sy, deltaSeaLevel, budget, blockedMap);
	if (isUsablePath(path, 2)) {
		print("[Manual] -1 seviye veya harita sinirina path bulundu.");
		return path;
	}

	path = findPathToLevelOrWater(sx, sy, deltaSeaLevel, budget, blockedMap);
	if (isUsablePath(path, 2)) {
		print("[Manual] Boundary path bulunamadi, hedef seviyeye path kullanildi.");
		return path;
	}

	path = findPath(sx, sy);
	if (isUsablePath(path, 2)) {
		print("[Manual] Hedef path bulunamadi, normal path kullanildi.");
		return path;
	}

	var outlet = findNearestManualOutlet(sx, sy);
	if (outlet != null) {
		path = findPathToPoint(sx, sy, outlet[0], outlet[1], budget, false);
		if (isUsablePath(path, 2)) {
			print("[Manual] En yakin su/alcak cikisa pathfind ile baglandi.");
			return path;
		}
		print("[Manual] Pathfind basarisiz; emergency route kullaniliyor.");
		return buildManualEmergencyRoute(sx, sy, outlet[0], outlet[1]);
	}

	return [[null, null]];
}

function findManualPathToLevelOrBoundary(startX, startY, targetSeaLevel, maxVisited, blockedMap) {
	var openSet = new PriorityQueue();
	openSet.add(new Candidate(0, startX, startY, 0));
	var addedPositions = new HashMap();
	addedPositions.put(toCoordinate(startX, startY), [null, null]);
	var pathStartMs = new Date().getTime();
	var nextLog = 50000;
	var points = [
		{ x: 2, y: 1 }, { x: 2, y: 0 }, { x: 2, y: -1 },
		{ x: -2, y: 1 }, { x: -2, y: 0 }, { x: -2, y: -1 },
		{ x: 1, y: 2 }, { x: 0, y: 2 }, { x: -1, y: 2 },
		{ x: 1, y: -2 }, { x: 0, y: -2 }, { x: -1, y: -2 }
	];

	while (openSet.values.length != 0 && addedPositions.size() < maxVisited) {
		checkForAbort(false);
		if (addedPositions.size() >= nextLog) {
			var elapsed = new Date().getTime() - pathStartMs;
			var progress = addedPositions.size() / maxVisited;
			var etaMs = progress > 0 ? (elapsed / progress) - elapsed : 0;
			print("[ManualPath] %" + parseInt(progress * 100) + "  ziyaret=" + addedPositions.size() + "/" + maxVisited + "  ETA=" + formatDurationShort(etaMs));
			nextLog += 50000;
		}

		var current = openSet.poll();
		if (current == null) {
			break;
		}

		var h = dimension.getHeightAt(current.x, current.y);
		if (h <= targetSeaLevel || isWaterAt(current.x, current.y) || isNearWorldBoundary(current.x, current.y, 2)) {
			return getTrail(current.x, current.y, addedPositions);
		}

		for (var k = 0; k < points.length; k++) {
			var p = points[k];
			var nx = current.x + p.x;
			var ny = current.y + p.y;
			if (nx - minX < 0 || nx - minX >= worldWidth || ny - minY < 0 || ny - minY >= worldHeight || avoidLayerCondition(nx, ny)) {
				continue;
			}
			if (blockedMap != null && blockedMap.get(toCoordinate(nx, ny)) != null && dimension.getHeightAt(nx, ny) > targetSeaLevel + 1) {
				continue;
			}
			if (addedPositions.get(toCoordinate(nx, ny)) != null) {
				continue;
			}

			var intermediates = getIntermediatePositions(p.x, p.y);
			var parentX = current.x;
			var parentY = current.y;
			if (intermediates != null && intermediates.length > 0) {
				var best = null;
				var bestH = Infinity;
				for (var t = 0; t < intermediates.length; t++) {
					var ix = current.x + intermediates[t].x;
					var iy = current.y + intermediates[t].y;
					if (ix - minX < 0 || ix - minX >= worldWidth || iy - minY < 0 || iy - minY >= worldHeight || avoidLayerCondition(ix, iy)) {
						continue;
					}
					if (addedPositions.get(toCoordinate(ix, iy)) != null) {
						continue;
					}
					var ih = dimension.getHeightAt(ix, iy);
					if (ih < bestH) {
						bestH = ih;
						best = [ix, iy];
					}
				}
				if (best != null) {
					addedPositions.put(toCoordinate(best[0], best[1]), [current.x, current.y]);
					parentX = best[0];
					parentY = best[1];
				}
			}

			addedPositions.put(toCoordinate(nx, ny), [parentX, parentY]);
			var heightPenalty = Math.max(0, dimension.getHeightAt(nx, ny) - targetSeaLevel) * 95;
			var boundaryDist = Math.min(nx - minX, ny - minY, (minX + worldWidth - 1) - nx, (minY + worldHeight - 1) - ny);
			var boundaryPenalty = Math.max(0, boundaryDist) * 0.03;
			var waterPenalty = isWaterAt(nx, ny) ? -200 : 0;
			var weight = current.dist + calculateCelDist(p.x, p.y) + heightPenalty + boundaryPenalty + waterPenalty + repeatableRandom(nx, ny) * (randomness * 0.35);
			openSet.add(new Candidate(weight, nx, ny, current.dist + calculateCelDist(p.x, p.y)));
		}
	}
	print("[ManualPath] yol bulunamadi veya butce doldu. ziyaret=" + addedPositions.size() + "/" + maxVisited);
	return [[null, null]];
}

function isUsablePath(path, minLength) {
	return path != null && path.length >= minLength && path[0] != null && path[0][0] != null;
}

function findNearestManualOutlet(sx, sy) {
	var best = null;
	var bestScore = Infinity;
	var maxRadius = Math.max(512, parseInt(Math.min(worldWidth, worldHeight) * 0.65));
	for (var radius = 128; radius <= maxRadius; radius = parseInt(radius * 1.55)) {
		var step = Math.max(8, parseInt(radius / 28));
		for (var dx = -radius; dx <= radius; dx += step) {
			for (var dy = -radius; dy <= radius; dy += step) {
				if (dx * dx + dy * dy > radius * radius) {
					continue;
				}
				var x = sx + dx;
				var y = sy + dy;
				if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight || avoidCondition(x, y)) {
					continue;
				}
				var h = dimension.getHeightAt(x, y);
				var wl = dimension.getWaterLevelAt(x, y);
				var isOutlet = isWaterAt(x, y) || h <= deltaSeaLevel + 1 || hasWaterNearby(x, y, Math.max(8, parseInt(endWidth * 0.7)));
				if (!isOutlet) {
					continue;
				}
				var dist = Math.sqrt(dx * dx + dy * dy);
				var score = dist + Math.max(0, h - wl) * 120;
				if (score < bestScore) {
					bestScore = score;
					best = [x, y];
				}
			}
		}
		if (best != null) {
			return best;
		}
	}

	var coarseStep = Math.max(16, parseInt(Math.min(worldWidth, worldHeight) / 220));
	for (var x = minX; x < minX + worldWidth; x += coarseStep) {
		for (var y = minY; y < minY + worldHeight; y += coarseStep) {
			if (avoidCondition(x, y)) {
				continue;
			}
			var h = dimension.getHeightAt(x, y);
			var dist = Math.sqrt(getSquaredDistance(sx, sy, x, y));
			var score = h * 100 + dist;
			if (score < bestScore) {
				bestScore = score;
				best = [x, y];
			}
		}
	}
	return best;
}

function buildManualEmergencyRoute(sx, sy, tx, ty) {
	var forward = [];
	var dx = tx - sx;
	var dy = ty - sy;
	var length = Math.sqrt(dx * dx + dy * dy);
	var steps = Math.max(8, parseInt(length / 8));
	var normalX = length > 0 ? -dy / length : 0;
	var normalY = length > 0 ? dx / length : 0;
	for (var i = 0; i <= steps; i++) {
		var t = i / steps;
		var fade = Math.sin(Math.PI * t);
		var baseX = sx + dx * t;
		var baseY = sy + dy * t;
		var offset = routeSmoothNoise(baseX, baseY, 180, 77) * Math.min(28, Math.max(6, length * 0.018)) * fade;
		var x = Math.round(baseX + normalX * offset);
		var y = Math.round(baseY + normalY * offset);
		x = Math.max(minX, Math.min(minX + worldWidth - 1, x));
		y = Math.max(minY, Math.min(minY + worldHeight - 1, y));
		var prev = forward.length > 0 ? forward[forward.length - 1] : null;
		if (prev == null || prev[0] != x || prev[1] != y) {
			forward.push([x, y]);
		}
	}
	var reversed = [];
	for (var r = forward.length - 1; r >= 0; r--) {
		reversed.push(forward[r]);
	}
	return densifyPath(reversed, 1);
}

function generateDeltaNetwork(mainRiverCount, targetSeaLevel) {
	var paths = [];
	var widthScales = [];
	var pathProfiles = [];
	var mainRiverBlockMap = new HashMap();
	var mainNoMergeRadius = Math.max(10, parseInt(endWidth * 0.8));
	mainRiverCount = Math.max(1, parseInt(mainRiverCount));
	var deltaStartMs = new Date().getTime();
	print("Delta agi olusturma basladi. Ana nehir hedefi: " + mainRiverCount);

	if (riverLayoutPreset == 1) {
		var presetNetwork = generateBorderPrincesPresetNetwork(targetSeaLevel);
		if (presetNetwork.paths.length >= 3) {
			var presetElapsedMs = new Date().getTime() - deltaStartMs;
			print("Border Princes preset tamamlandi. Toplam nehir: " + presetNetwork.paths.length + "  (sure: " + formatDurationShort(presetElapsedMs) + ")");
			return presetNetwork;
		}
		print("Border Princes preset yeterli nehir uretemedi, otomatik hidrolojiye geciliyor.");
	}

	var hydro = buildHydrologyModel(targetSeaLevel);
	var mainSources = selectMainHydroSources(hydro, mainRiverCount, targetSeaLevel);
	print("Delta hidroloji kaynaklari bulundu: " + mainSources.length + " / " + mainRiverCount);

	var mainPathBudget = Math.max(250000, Math.min(3000000, parseInt(worldWidth * worldHeight * 0.04)));
	for (var i = 0; i < mainSources.length; i++) {
		checkForAbort(false);
		var source = mainSources[i];
		var sourceWorld = hydroIndexToWorld(hydro, source.idx);
		print("[Delta-Hydro] Ana nehir " + (i + 1) + "/" + mainSources.length + " akis izleme...");
		var mainPath = traceHydroPathToOutlet(hydro, source.idx, targetSeaLevel, mainRiverBlockMap, parseInt(hydro.cellCount * 0.6));

		if (mainPath.length < 2 || approximatePathLength(mainPath) < Math.max(minRiverLength, 220)) {
			print("[Delta-Hydro] Akis yolu yetersiz, fallback pathfind deneniyor...");
			mainPath = findPathToLevelOrWater(sourceWorld[0], sourceWorld[1], targetSeaLevel, mainPathBudget, mainRiverBlockMap);
		}

		if (mainPath.length >= 2 && approximatePathLength(mainPath) >= Math.max(minRiverLength, 220) && mainPath[0][0] != null) {
			mainPath = densifyPath(mainPath, 1);
			mainPath = stylizeRiverPath(mainPath, true);
			if (pathTouchesWorldBoundary(mainPath, riverBoundaryMargin)) {
				print("[Delta] Ana nehir " + (i + 1) + " harita sinirina fazla yaklastigi icin atlandi.");
				continue;
			}
			if (getPathBlockedRatio(mainPath, mainRiverBlockMap, 4) > 0.16) {
				print("[Delta] Ana nehir " + (i + 1) + " cok fazla cakisti, atlandi.");
				continue;
			}
			paths.push(mainPath);
			var dischargeRatio = clampBetweenZeroAndOne(source.accum / hydro.maxAccum);
			widthScales.push(0.95 + 0.50 * Math.sqrt(dischargeRatio));
			pathProfiles.push(makePathProfile("main"));
			addPathToBlockMap(mainPath, mainRiverBlockMap, mainNoMergeRadius);
			print("[Delta] Ana nehir " + (i + 1) + " eklendi. Uzunluk: " + parseInt(approximatePathLength(mainPath)) + " blok");
		} else {
			print("[Delta] Ana nehir " + (i + 1) + " atlandi (kisa veya bulunamadi).");
		}

		if (paths.length >= mainRiverCount) {
			break;
		}
	}

	var initialMainCount = paths.length;
	if (disableBranching) {
		print("[Delta] Dallanma kapali: yalnizca ana nehirler olusturulacak.");
	}
	if (disableBranching) {
		stitchNearbyRiverChannels(paths, pathProfiles);
		var deltaElapsedNoBranchMs = new Date().getTime() - deltaStartMs;
		print("Delta agi tamamlandi. Toplam nehir: " + paths.length + "  (sure: " + formatDurationShort(deltaElapsedNoBranchMs) + ")");
		return { paths: paths, widthScales: widthScales, pathProfiles: pathProfiles };
	}

	for (var mainIndex = 0; mainIndex < initialMainCount; mainIndex++) {
		checkForAbort(false);
		var mainPathRef = paths[mainIndex];
		if (mainPathRef.length < 80) {
			continue;
		}
		print("[Delta] Yan kol asamasi ana nehir " + (mainIndex + 1) + "/" + initialMainCount + "...");

		var branchCount = 1 + parseInt(Math.random() * 3);
		for (var b = 0; b < branchCount; b++) {
			var targetIndex = Math.max(24, parseInt((0.30 + Math.random() * 0.50) * (mainPathRef.length - 1)));
			var confluence = mainPathRef[targetIndex];
			if (confluence == null || confluence[0] == null) {
				continue;
			}

			var tSource = findHydroTributarySource(hydro, confluence[0], confluence[1], 120, 360);
			if (tSource == null) {
				continue;
			}

			var branchPath = traceHydroPathToJoinPoint(hydro, tSource.idx, confluence[0], confluence[1], parseInt(hydro.cellCount * 0.35));
			if (branchPath.length < 2 || approximatePathLength(branchPath) < 70) {
				var sWorld = hydroIndexToWorld(hydro, tSource.idx);
				branchPath = findPathToPoint(sWorld[0], sWorld[1], confluence[0], confluence[1], 80000);
			}
			if (branchPath.length < 70 || branchPath[0][0] == null) {
				print("[Delta] Yan kol " + (b + 1) + "/" + branchCount + " bulunamadi.");
				continue;
			}
			branchPath = densifyPath(branchPath, 1);
			branchPath = stylizeRiverPath(branchPath, false);
			if (pathTouchesWorldBoundary(branchPath, riverBoundaryMargin)) {
				print("[Delta] Yan kol " + (b + 1) + "/" + branchCount + " harita sinirina fazla yaklastigi icin atlandi.");
				continue;
			}
			if (!isNaturalTributaryJoin(branchPath, mainPathRef, targetIndex)) {
				print("[Delta] Yan kol " + (b + 1) + "/" + branchCount + " ana nehre paralel baglandigi icin atlandi.");
				continue;
			}
			if (getPathBlockedRatioBeforeEnd(branchPath, mainRiverBlockMap, 4, 22) > 0.10) {
				print("[Delta] Yan kol " + (b + 1) + "/" + branchCount + " ana yataga fazla paralel gittigi icin atlandi.");
				continue;
			}

			paths.push(branchPath);
			var tRatio = clampBetweenZeroAndOne(tSource.accum / hydro.maxAccum);
			var branchWidthScale = 0.30 + 0.25 * Math.sqrt(tRatio);
			widthScales.push(branchWidthScale);
			pathProfiles.push(makePathProfile("medium"));
			addJunctionBoost(confluence[0], confluence[1], 1.0);
			addConfluenceMergeZone(mainPathRef, targetIndex, branchPath, branchWidthScale);
			print("[Delta] Yan kol " + (b + 1) + "/" + branchCount + " eklendi. Uzunluk: " + branchPath.length);

			if (branchPath.length > 150 && Math.random() < 0.55) {
				var subTarget = branchPath[Math.max(10, parseInt(branchPath.length * 0.45))];
				var subSource = findHydroTributarySource(hydro, subTarget[0], subTarget[1], 80, 220);
				if (subSource != null) {
					var subBranch = traceHydroPathToJoinPoint(hydro, subSource.idx, subTarget[0], subTarget[1], parseInt(hydro.cellCount * 0.28));
					if (subBranch.length < 2 || approximatePathLength(subBranch) < 50) {
						var subWorld = hydroIndexToWorld(hydro, subSource.idx);
						subBranch = findPathToPoint(subWorld[0], subWorld[1], subTarget[0], subTarget[1], 50000);
					}
					if (subBranch.length >= 50 && subBranch[0][0] != null) {
						subBranch = densifyPath(subBranch, 1);
						subBranch = stylizeRiverPath(subBranch, false);
						if (pathTouchesWorldBoundary(subBranch, riverBoundaryMargin)) {
							print("[Delta] Alt kol harita sinirina fazla yaklastigi icin atlandi.");
						} else if (!isNaturalTributaryJoin(subBranch, branchPath, Math.max(10, parseInt(branchPath.length * 0.45)))) {
							print("[Delta] Alt kol paralel baglandigi icin atlandi.");
						} else if (getPathBlockedRatioBeforeEnd(subBranch, mainRiverBlockMap, 4, 18) > 0.08) {
							print("[Delta] Alt kol ana yataga fazla yakin oldugu icin atlandi.");
						} else {
							paths.push(subBranch);
							var subRatio = clampBetweenZeroAndOne(subSource.accum / hydro.maxAccum);
							var subWidthScale = 0.18 + 0.15 * Math.sqrt(subRatio);
							widthScales.push(subWidthScale);
							pathProfiles.push(makePathProfile("small"));
							addJunctionBoost(subTarget[0], subTarget[1], 0.65);
							addConfluenceMergeZone(branchPath, Math.max(10, parseInt(branchPath.length * 0.45)), subBranch, subWidthScale);
							addPathToBlockMap(subBranch, mainRiverBlockMap, Math.max(2, parseInt(endWidth * 0.18)));
							print("[Delta] Alt kol eklendi. Uzunluk: " + subBranch.length);
						}
					}
				}
			}
			addPathToBlockMap(branchPath, mainRiverBlockMap, Math.max(3, parseInt(endWidth * 0.25)));
		}
	}

	var deltaElapsedMs = new Date().getTime() - deltaStartMs;
	stitchNearbyRiverChannels(paths, pathProfiles);
	print("Delta agi tamamlandi. Toplam nehir: " + paths.length + "  (sure: " + formatDurationShort(deltaElapsedMs) + ")");

	return { paths: paths, widthScales: widthScales, pathProfiles: pathProfiles };
}

function generateBorderPrincesPresetNetwork(targetSeaLevel) {
	var paths = [];
	var widthScales = [];
	var pathProfiles = [];
	var blockMap = new HashMap();
	var budget = Math.max(180000, Math.min(1600000, parseInt(worldWidth * worldHeight * 0.08)));
	print("[BorderPreset] Referans harita benzeri nehir yerlesimi uretiliyor...");

	var mainDefs = [
		{ name: "kuzey ana nehir", source: [0.50, 0.14], waypoints: [[0.49, 0.27], [0.47, 0.40]], outlet: [0.47, 0.52], width: 1.0, profile: "main" },
		{ name: "dogu ana nehir", source: [0.86, 0.25], waypoints: [[0.79, 0.35], [0.70, 0.45]], outlet: [0.61, 0.51], width: 1.0, profile: "main" },
		{ name: "guneydogu kolu", source: [0.79, 0.86], waypoints: [[0.72, 0.72], [0.66, 0.61]], outlet: [0.58, 0.56], width: 1.0, profile: "medium" },
		{ name: "bati kolu", source: [0.13, 0.42], waypoints: [[0.18, 0.50], [0.25, 0.57]], outlet: [0.34, 0.59], width: 1.0, profile: "medium" },
		{ name: "guneybati kolu", source: [0.18, 0.84], waypoints: [[0.24, 0.73], [0.30, 0.66]], outlet: [0.39, 0.63], width: 1.0, profile: "medium" }
	];

	for (var i = 0; i < mainDefs.length; i++) {
		checkForAbort(false);
		var path = buildPresetPath(mainDefs[i], budget);
		if (path.length < 80 || path[0][0] == null) {
			print("[BorderPreset] " + mainDefs[i].name + " bulunamadi.");
			continue;
		}
		if (pathTouchesWorldBoundary(path, riverBoundaryMargin)) {
			print("[BorderPreset] " + mainDefs[i].name + " harita sinirina fazla yaklastigi icin atlandi.");
			continue;
		}
		var overlapLimit = mainDefs[i].profile == "small" ? 0.34 : (mainDefs[i].profile == "medium" ? 0.28 : 0.22);
		if (getPathBlockedRatio(path, blockMap, 5) > overlapLimit) {
			print("[BorderPreset] " + mainDefs[i].name + " fazla cakistigi icin atlandi.");
			continue;
		}
		paths.push(path);
		widthScales.push(mainDefs[i].width);
		pathProfiles.push(makePathProfile(mainDefs[i].profile));
		var blockRadius = mainDefs[i].profile == "main" ? Math.max(48, parseInt(endWidth * 2.6)) : (mainDefs[i].profile == "medium" ? Math.max(32, parseInt(endWidth * 1.8)) : 8);
		addPathToBlockMap(path, blockMap, blockRadius);
		printPathNaturalStats("[BorderPreset] " + mainDefs[i].name, path, mainDefs[i].profile);
		print("[BorderPreset] " + mainDefs[i].name + " eklendi.");
	}

	var presetMainCount = paths.length;

	if (!disableBranching) {
		var branchDefs = [
			{ name: "kuzeybati yan kol", main: 0, join: 0.43, source: [0.35, 0.19], waypoints: [[0.40, 0.30]], width: 1.0, profile: "small" },
			{ name: "kuzeydogu yan kol", main: 0, join: 0.55, source: [0.62, 0.17], waypoints: [[0.55, 0.30]], width: 1.0, profile: "small" },
			{ name: "dogu dag kolu", main: 1, join: 0.46, source: [0.88, 0.47], waypoints: [[0.80, 0.47]], width: 1.0, profile: "small" },
			{ name: "guney dag kolu", main: 2, join: 0.48, source: [0.86, 0.76], waypoints: [[0.79, 0.72]], width: 1.0, profile: "small" },
			{ name: "bati orman kolu", main: 3, join: 0.50, source: [0.10, 0.25], waypoints: [[0.16, 0.38]], width: 1.0, profile: "small" },
			{ name: "kuzeybati ince kol", main: 3, join: 0.64, source: [0.26, 0.18], waypoints: [[0.31, 0.31], [0.34, 0.45]], width: 1.0, profile: "small" },
			{ name: "dogu dag ince kol", main: 1, join: 0.58, source: [0.91, 0.44], waypoints: [[0.82, 0.48], [0.73, 0.51]], width: 1.0, profile: "small" }
		];

		for (var b = 0; b < branchDefs.length; b++) {
			checkForAbort(false);
			var branchDef = branchDefs[b];
			if (branchDef.main >= paths.length) {
				continue;
			}
			var mainPath = paths[branchDef.main];
			var joinIndex = Math.max(8, Math.min(mainPath.length - 8, parseInt(mainPath.length * branchDef.join)));
			var joinPoint = mainPath[joinIndex];
			var branchPath = buildPresetBranchPath(branchDef, joinPoint, budget);
			if (branchPath.length < 45 || branchPath[0][0] == null) {
				print("[BorderPreset] " + branchDef.name + " bulunamadi.");
				continue;
			}
			if (pathTouchesWorldBoundary(branchPath, riverBoundaryMargin)) {
				print("[BorderPreset] " + branchDef.name + " harita sinirina fazla yaklastigi icin atlandi.");
				continue;
			}
			if (!isJoinEndpointConnected(branchPath, joinPoint, 3)) {
				print("[BorderPreset] " + branchDef.name + " ana nehre tam baglanamadigi icin atlandi.");
				continue;
			}
			if (!isNaturalTributaryJoin(branchPath, mainPath, joinIndex)) {
				print("[BorderPreset] " + branchDef.name + " paralel baglandigi icin atlandi.");
				continue;
			}
			if (getPathBlockedRatioAfterStart(branchPath, blockMap, 4, 22) > 0.16) {
				print("[BorderPreset] " + branchDef.name + " fazla cakistigi icin atlandi.");
				continue;
			}
			paths.push(branchPath);
			widthScales.push(branchDef.width);
			pathProfiles.push(makePathProfile(branchDef.profile));
			addJunctionBoost(joinPoint[0], joinPoint[1], 0.65);
			addConfluenceMergeZone(mainPath, joinIndex, branchPath, branchDef.profile == "small" ? 0.34 : 0.45);
			addPathToBlockMap(branchPath, blockMap, Math.max(2, parseInt(endWidth * 0.18)));
			printPathNaturalStats("[BorderPreset] " + branchDef.name, branchPath, branchDef.profile);
			print("[BorderPreset] " + branchDef.name + " eklendi.");
		}

		addMountainFeederStreams(paths, widthScales, pathProfiles, blockMap, presetMainCount, budget);
	}

	stitchNearbyRiverChannels(paths, pathProfiles);
	return { paths: paths, widthScales: widthScales, pathProfiles: pathProfiles };
}

function addMountainFeederStreams(paths, widthScales, pathProfiles, blockMap, mainCount, budget) {
	var feederJoinFractions = [
		[0.34, 0.58],
		[0.30, 0.52, 0.70],
		[0.38, 0.62],
		[0.42, 0.66],
		[0.46]
	];
	for (var mainIndex = 0; mainIndex < mainCount; mainIndex++) {
		checkForAbort(false);
		var mainPath = paths[mainIndex];
		if (mainPath == null || mainPath.length < 90) {
			continue;
		}
		var fractions = feederJoinFractions[Math.min(mainIndex, feederJoinFractions.length - 1)];
		for (var f = 0; f < fractions.length; f++) {
			var joinIndex = Math.max(12, Math.min(mainPath.length - 12, parseInt(mainPath.length * fractions[f])));
			var joinPoint = mainPath[joinIndex];
			var source = findMountainFeederSource(joinPoint[0], joinPoint[1], 70, 230);
			if (source == null) {
				continue;
			}
			var feeder = findPathAlongRoute([source, joinPoint], Math.max(80000, parseInt(budget * 0.38)), "feeder", "join");
			if (feeder.length < 45 || feeder[0][0] == null) {
				continue;
			}
			var feederLength = approximatePathLength(feeder);
			if (feederLength > 280) {
				continue;
			}
			if (getPathDirectnessRatio(feeder) > getNaturalRouteParams("feeder").maxDirectness) {
				continue;
			}
			if (pathTouchesWorldBoundary(feeder, riverBoundaryMargin)) {
				continue;
			}
			if (!isJoinEndpointConnected(feeder, joinPoint, 3)) {
				continue;
			}
			if (!isNaturalTributaryJoin(feeder, mainPath, joinIndex)) {
				continue;
			}
			if (getPathBlockedRatioAfterStart(feeder, blockMap, 4, 12) > 0.10) {
				continue;
			}
			paths.push(feeder);
			widthScales.push(1.0);
			pathProfiles.push(makePathProfile("feeder", 1));
			addTinyFeederMouth(mainPath, joinIndex, feeder);
			addPathToBlockMap(feeder, blockMap, 2);
			printPathNaturalStats("[BorderPreset] dagdan gelen ince kol", feeder, "feeder");
			print("[BorderPreset] dagdan gelen ince kol eklendi. Ana nehir: " + (mainIndex + 1));
		}
	}
}

function findMountainFeederSource(targetX, targetY, minRadius, maxRadius) {
	var targetHeight = dimension.getHeightAt(targetX, targetY);
	var best = null;
	var bestScore = -Infinity;
	for (var i = 0; i < 220; i++) {
		var angle = Math.random() * 6.28318530718;
		var radius = minRadius + Math.random() * (maxRadius - minRadius);
		var x = Math.round(targetX + Math.cos(angle) * radius);
		var y = Math.round(targetY + Math.sin(angle) * radius);
		if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight || avoidCondition(x, y)) {
			continue;
		}
		if (isWaterAt(x, y)) {
			continue;
		}
		var h = dimension.getHeightAt(x, y);
		if (h <= targetHeight + 2.0) {
			continue;
		}
		var roughness = localReliefScore(x, y, 10);
		var score = h * 1.4 + roughness * 7.0 + radius * 0.04 + repeatableRandom(x, y);
		if (score > bestScore) {
			bestScore = score;
			best = [x, y];
		}
	}
	return best;
}

function localReliefScore(x, y, radius) {
	var center = dimension.getHeightAt(x, y);
	var minH = center;
	var maxH = center;
	var step = Math.max(2, parseInt(radius / 3));
	for (var dx = -radius; dx <= radius; dx += step) {
		for (var dy = -radius; dy <= radius; dy += step) {
			var wx = x + dx;
			var wy = y + dy;
			if (wx - minX < 0 || wx - minX >= worldWidth || wy - minY < 0 || wy - minY >= worldHeight) {
				continue;
			}
			var h = dimension.getHeightAt(wx, wy);
			if (h < minH) {
				minH = h;
			}
			if (h > maxH) {
				maxH = h;
			}
		}
	}
	return maxH - minH;
}

function buildPresetPath(def, budget) {
	var source = findTemplateLandPoint(def.source[0], def.source[1], presetSearchRadius(0.08, 120, 700));
	var outlet = findTemplateRiverMouthPoint(def.outlet[0], def.outlet[1], presetSearchRadius(0.08, 120, 760));
	var route = [source];
	for (var i = 0; i < def.waypoints.length; i++) {
		route.push(findTemplateRoutePoint(def.waypoints[i][0], def.waypoints[i][1], presetSearchRadius(0.045, 90, 420)));
	}
	route.push(outlet);
	return findPathAlongRoute(route, budget, def.profile, "outlet");
}

function buildPresetBranchPath(def, joinPoint, budget) {
	var source = findTemplateLandPoint(def.source[0], def.source[1], presetSearchRadius(0.07, 100, 600));
	var route = [source];
	for (var i = 0; i < def.waypoints.length; i++) {
		route.push(findTemplateRoutePoint(def.waypoints[i][0], def.waypoints[i][1], presetSearchRadius(0.04, 70, 360)));
	}
	route.push(joinPoint);
	return findPathAlongRoute(route, Math.max(90000, parseInt(budget * 0.65)), def.profile, "join");
}

function findPathAlongRoute(route, budget, profileType, targetMode) {
	if (route == null || route.length < 2) {
		return [[null, null]];
	}
	profileType = normalizeRouteProfileType(profileType);
	targetMode = targetMode == null ? "outlet" : targetMode;
	if ((profileType == "small" || profileType == "feeder") && targetMode != "join") {
		return [[null, null]];
	}

	var bestPath = null;
	var bestMetrics = null;
	for (var attempt = 0; attempt < 3; attempt++) {
		var forward = buildNaturalRoute(route, profileType, targetMode, attempt);
		if (forward.length < 2) {
			continue;
		}
		forward = applyNaturalMeanders(forward, profileType, attempt);
		if (forward.length < 2) {
			continue;
		}
		var metrics = getNaturalRouteMetrics(forward, profileType);
		if (bestMetrics == null || metrics.score < bestMetrics.score) {
			bestMetrics = metrics;
			bestPath = forward;
		}
		if (isNaturalRouteAcceptable(metrics, profileType)) {
			break;
		}
	}
	if (bestPath == null || bestMetrics == null || !isNaturalRouteAcceptable(bestMetrics, profileType)) {
		return [[null, null]];
	}
	var forward = removeConsecutiveDuplicatePoints(bestPath);
	var reversed = [];
	for (var i = forward.length - 1; i >= 0; i--) {
		reversed.push(forward[i]);
	}
	return reversed;
}

function normalizeRouteProfileType(profileType) {
	if (profileType === true) {
		return "main";
	}
	if (profileType === false || profileType == null) {
		return "small";
	}
	return profileType;
}

function buildNaturalRoute(route, profileType, targetMode, attempt) {
	var result = [];
	for (var i = 0; i < route.length - 1; i++) {
		if (!appendNaturalSegment(result, route[i], route[i + 1], profileType, targetMode, i, route.length - 1, attempt)) {
			return [];
		}
	}
	return result;
}

function appendNaturalSegment(result, start, target, profileType, targetMode, segmentIndex, totalSegments, attempt) {
	if (start == null || target == null || start[0] == null || target[0] == null) {
		return false;
	}
	var dx = target[0] - start[0];
	var dy = target[1] - start[1];
	var length = Math.sqrt(dx * dx + dy * dy);
	if (length < 1) {
		return false;
	}
	var params = getNaturalRouteParams(profileType);
	var current = clampGuidedPoint(start[0], start[1]);
	var targetPoint = clampGuidedPoint(target[0], target[1]);
	var last = result.length > 0 ? result[result.length - 1] : null;
	if (last == null || last[0] != current[0] || last[1] != current[1]) {
		result.push(current);
	}

	var previousAngle = Math.atan2(targetPoint[1] - current[1], targetPoint[0] - current[0]);
	var maxSteps = Math.max(24, parseInt((length / params.stepDistance) * 3.2));
	for (var step = 0; step < maxSteps; step++) {
		var distToTarget = Math.sqrt(getSquaredDistance(current[0], current[1], targetPoint[0], targetPoint[1]));
		if (distToTarget <= params.stepDistance * 1.55) {
			var prev = result[result.length - 1];
			if (prev[0] != targetPoint[0] || prev[1] != targetPoint[1]) {
				result.push(targetPoint);
			}
			return true;
		}

		var next = chooseNextNaturalStep(current, targetPoint, previousAngle, params, segmentIndex, step, attempt);
		if (next == null) {
			return false;
		}
		var nextDist = Math.sqrt(getSquaredDistance(next[0], next[1], targetPoint[0], targetPoint[1]));
		if (nextDist >= distToTarget - Math.max(0.45, params.stepDistance * params.minProgressRatio) && distToTarget > params.stepDistance * 4) {
			return false;
		}
		var prev = result[result.length - 1];
		if (prev == null || prev[0] != next[0] || prev[1] != next[1]) {
			result.push(next);
		}
		previousAngle = Math.atan2(next[1] - current[1], next[0] - current[0]);
		current = next;
	}
	return false;
}

function chooseNextNaturalStep(current, target, previousAngle, params, segmentIndex, stepIndex, attempt) {
	var distToTarget = Math.sqrt(getSquaredDistance(current[0], current[1], target[0], target[1]));
	var baseAngle = Math.atan2(target[1] - current[1], target[0] - current[0]);
	var endpointFade = clampBetweenZeroAndOne(distToTarget / Math.max(params.stepDistance * 8, params.driftScale * 0.55));
	var drift = routeSmoothNoise(current[0], current[1], params.driftScale, segmentIndex + attempt * 9) * params.driftAngle * endpointFade;
	var spread = params.angleSpread * (0.55 + 0.45 * endpointFade);
	var centerAngle = baseAngle + drift;
	var best = null;
	var bestScore = Infinity;
	var prevHeight = dimension.getHeightAt(current[0], current[1]);
	for (var a = -params.angleSamples; a <= params.angleSamples; a++) {
		var offset = (a / Math.max(1, params.angleSamples)) * spread;
		var angle = centerAngle + offset;
		for (var sl = 0; sl < params.stepScales.length; sl++) {
			var stepLength = params.stepDistance * params.stepScales[sl];
			var wx = Math.round(current[0] + Math.cos(angle) * stepLength);
			var wy = Math.round(current[1] + Math.sin(angle) * stepLength);
			if (wx - minX < 0 || wx - minX >= worldWidth || wy - minY < 0 || wy - minY >= worldHeight || avoidCondition(wx, wy) || isWaterAt(wx, wy)) {
				continue;
			}
			var newDist = Math.sqrt(getSquaredDistance(wx, wy, target[0], target[1]));
			if (newDist > distToTarget - Math.max(0.35, params.stepDistance * params.minProgressRatio) && distToTarget > params.stepDistance * 4) {
				continue;
			}
			var h = dimension.getHeightAt(wx, wy);
			var wl = dimension.getWaterLevelAt(wx, wy);
			var uphill = Math.max(0, h - prevHeight);
			var heightAboveWater = Math.max(0, h - wl);
			var localLow = getLocalLowPenalty(wx, wy, params.localRadius);
			var turn = Math.abs(angleDifference(angle, previousAngle));
			var driftMiss = Math.abs(angleDifference(angle, centerAngle));
			var fineNoise = routeSmoothNoise(wx, wy, params.noiseCell, segmentIndex * 17 + attempt * 31);
			var score = newDist * params.targetWeight;
			score += uphill * params.uphillWeight;
			score += heightAboveWater * params.heightWeight;
			score += localLow * params.localLowWeight;
			score += turn * params.turnWeight;
			score += driftMiss * params.driftWeight;
			score -= fineNoise * params.noiseWeight;
			if (score < bestScore) {
				bestScore = score;
				best = [wx, wy];
			}
		}
	}
	return best;
}

function routeSmoothNoise(x, y, scale, seed) {
	scale = Math.max(1, scale);
	return noise.simplex2((x + seed * 137.0) / scale, (y - seed * 79.0) / scale);
}

function angleDifference(a, b) {
	var d = a - b;
	while (d > Math.PI) {
		d -= Math.PI * 2;
	}
	while (d < -Math.PI) {
		d += Math.PI * 2;
	}
	return d;
}

function clampGuidedPoint(x, y) {
	return [
		Math.max(minX, Math.min(minX + worldWidth - 1, Math.round(x))),
		Math.max(minY, Math.min(minY + worldHeight - 1, Math.round(y)))
	];
}

function chooseNaturalCorridorPoint(x, y, prev, target, params) {
	x = Math.max(minX, Math.min(minX + worldWidth - 1, Math.round(x)));
	y = Math.max(minY, Math.min(minY + worldHeight - 1, Math.round(y)));
	var radius = Math.max(1, parseInt(params.searchRadius));
	var best = null;
	var bestScore = Infinity;
	var step = radius <= 6 ? 1 : 2;
	var prevHeight = prev == null ? dimension.getHeightAt(x, y) : dimension.getHeightAt(prev[0], prev[1]);
	var candidateTargetDist = Math.sqrt(getSquaredDistance(x, y, target[0], target[1]));
	for (var dx = -radius; dx <= radius; dx += step) {
		for (var dy = -radius; dy <= radius; dy += step) {
			if (dx * dx + dy * dy > radius * radius) {
				continue;
			}
			var wx = x + dx;
			var wy = y + dy;
			if (wx - minX < 0 || wx - minX >= worldWidth || wy - minY < 0 || wy - minY >= worldHeight || avoidCondition(wx, wy) || isWaterAt(wx, wy)) {
				continue;
			}
			var h = dimension.getHeightAt(wx, wy);
			var wl = dimension.getWaterLevelAt(wx, wy);
			var heightAboveWater = Math.max(0, h - wl);
			var uphill = Math.max(0, h - prevHeight);
			var targetDrift = Math.max(0, Math.sqrt(getSquaredDistance(wx, wy, target[0], target[1])) - candidateTargetDist);
			var localLow = getLocalLowPenalty(wx, wy, params.localRadius);
			var score = dx * dx + dy * dy;
			score += heightAboveWater * params.heightWeight;
			score += uphill * params.uphillWeight;
			score += targetDrift * params.targetWeight;
			score += localLow * params.localLowWeight;
			score += repeatableRandom(wx, wy) * 0.45;
			if (score < bestScore) {
				bestScore = score;
				best = [wx, wy];
			}
		}
	}
	return best;
}

function getLocalLowPenalty(x, y, radius) {
	radius = Math.max(2, parseInt(radius));
	var center = dimension.getHeightAt(x, y);
	var minH = center;
	var step = Math.max(1, parseInt(radius / 3));
	for (var dx = -radius; dx <= radius; dx += step) {
		for (var dy = -radius; dy <= radius; dy += step) {
			var wx = x + dx;
			var wy = y + dy;
			if (wx - minX < 0 || wx - minX >= worldWidth || wy - minY < 0 || wy - minY >= worldHeight) {
				continue;
			}
			var h = dimension.getHeightAt(wx, wy);
			if (h < minH) {
				minH = h;
			}
		}
	}
	return Math.max(0, center - minH);
}

function applyNaturalMeanders(path, profileType, attempt) {
	if (path == null || path.length < 3) {
		return path;
	}
	var params = getNaturalRouteParams(profileType);
	var sparse = simplifyPathByDistance(path, params.simplifyDistance);
	var smooth = profileType == "feeder" ? sparse : chaikinSmoothPath(sparse, 1);
	var dense = densifyPath(smooth, 1);
	if (dense.length > 1) {
		dense[0] = path[0];
		dense[dense.length - 1] = path[path.length - 1];
	}
	return removeConsecutiveDuplicatePoints(dense);
}

function getNaturalRouteParams(profileType) {
	profileType = normalizeRouteProfileType(profileType);
	if (profileType == "feeder") {
		return {
			stepDistance: 5,
			stepScales: [0.75, 1.0, 1.22],
			angleSamples: 4,
			angleSpread: 0.48,
			driftAngle: 0.14,
			driftScale: 125,
			turnWeight: 7.0,
			driftWeight: 4.5,
			noiseWeight: 2.0,
			searchRadius: 5,
			localRadius: 4,
			noiseCell: 22,
			heightWeight: 1.2,
			uphillWeight: 5.0,
			targetWeight: 0.82,
			localLowWeight: 1.8,
			minProgressRatio: 0.08,
			simplifyDistance: 5,
			maxStraightRun: 120,
			straightDeviation: 2.2,
			maxDirectness: 0.999,
			minDirectness: 0.70
		};
	}
	if (profileType == "small") {
		return {
			stepDistance: 7,
			stepScales: [0.78, 1.0, 1.25],
			angleSamples: 5,
			angleSpread: 0.62,
			driftAngle: 0.24,
			driftScale: 170,
			turnWeight: 11.0,
			driftWeight: 6.0,
			noiseWeight: 3.2,
			searchRadius: 7,
			localRadius: 5,
			noiseCell: 24,
			heightWeight: 1.45,
			uphillWeight: 5.6,
			targetWeight: 0.76,
			localLowWeight: 2.0,
			minProgressRatio: 0.075,
			simplifyDistance: 7,
			maxStraightRun: 190,
			straightDeviation: 4.5,
			maxDirectness: 0.999,
			minDirectness: 0.55
		};
	}
	if (profileType == "medium") {
		return {
			stepDistance: 10,
			stepScales: [0.8, 1.0, 1.25],
			angleSamples: 5,
			angleSpread: 0.70,
			driftAngle: 0.32,
			driftScale: 270,
			turnWeight: 16.0,
			driftWeight: 8.0,
			noiseWeight: 5.2,
			searchRadius: 12,
			localRadius: 7,
			noiseCell: 34,
			heightWeight: 1.75,
			uphillWeight: 6.4,
			targetWeight: 0.68,
			localLowWeight: 2.35,
			minProgressRatio: 0.065,
			simplifyDistance: 10,
			maxStraightRun: 270,
			straightDeviation: 8.5,
			maxDirectness: 0.999,
			minDirectness: 0.45
		};
	}
	return {
		stepDistance: 13,
		stepScales: [0.82, 1.0, 1.24],
		angleSamples: 6,
		angleSpread: 0.78,
		driftAngle: 0.40,
		driftScale: 430,
		turnWeight: 24.0,
		driftWeight: 10.0,
		noiseWeight: 7.2,
		searchRadius: 18,
		localRadius: 9,
		noiseCell: 48,
		heightWeight: 1.9,
		uphillWeight: 7.2,
		targetWeight: 0.62,
		localLowWeight: 2.7,
		minProgressRatio: 0.055,
		simplifyDistance: 14,
		maxStraightRun: 320,
		straightDeviation: 16,
		maxDirectness: 0.999,
		minDirectness: 0.35
	};
}

function getNaturalRouteMetrics(path, profileType) {
	var length = approximatePathLength(path);
	var directness = getPathDirectnessRatio(path);
	var maxStraightRun = getMaxStraightRun(path, profileType);
	var params = getNaturalRouteParams(profileType);
	var straightPenalty = Math.max(0, maxStraightRun - params.maxStraightRun) / Math.max(1, params.maxStraightRun);
	var directPenalty = Math.max(0, directness - params.maxDirectness) + Math.max(0, params.minDirectness - directness);
	return {
		length: length,
		directness: directness,
		maxStraightRun: maxStraightRun,
		score: straightPenalty * 4 + directPenalty * 8
	};
}

function isNaturalRouteAcceptable(metrics, profileType) {
	if (metrics == null) {
		return false;
	}
	var params = getNaturalRouteParams(profileType);
	return metrics.maxStraightRun <= params.maxStraightRun && metrics.directness <= params.maxDirectness && metrics.directness >= params.minDirectness;
}

function getMaxStraightRun(path, profileType) {
	if (path == null || path.length < 3) {
		return 0;
	}
	var params = getNaturalRouteParams(profileType);
	var sampled = simplifyPathByDistance(path, Math.max(6, params.simplifyDistance * 2));
	if (sampled.length < 3) {
		return approximatePathLength(path);
	}
	var maxRun = 0;
	for (var i = 0; i < sampled.length - 2; i++) {
		var runLength = 0;
		for (var j = i + 2; j < sampled.length; j++) {
			var chord = Math.sqrt(getSquaredDistance(sampled[i][0], sampled[i][1], sampled[j][0], sampled[j][1]));
			if (chord < runLength) {
				continue;
			}
			var maxDeviation = 0;
			for (var k = i + 1; k < j; k++) {
				var deviation = getPointLineDistance(sampled[k], sampled[i], sampled[j]);
				if (deviation > maxDeviation) {
					maxDeviation = deviation;
				}
			}
			if (maxDeviation <= params.straightDeviation) {
				runLength = chord;
				if (runLength > maxRun) {
					maxRun = runLength;
				}
			} else {
				break;
			}
		}
	}
	return maxRun;
}

function getPointLineDistance(point, a, b) {
	var dx = b[0] - a[0];
	var dy = b[1] - a[1];
	var lenSq = dx * dx + dy * dy;
	if (lenSq <= 0) {
		return Math.sqrt(getSquaredDistance(point[0], point[1], a[0], a[1]));
	}
	var t = ((point[0] - a[0]) * dx + (point[1] - a[1]) * dy) / lenSq;
	t = clampBetweenZeroAndOne(t);
	var px = a[0] + dx * t;
	var py = a[1] + dy * t;
	var ox = point[0] - px;
	var oy = point[1] - py;
	return Math.sqrt(ox * ox + oy * oy);
}

function printPathNaturalStats(label, path, profileType) {
	var metrics = getNaturalRouteMetrics(path, profileType);
	print(label + " stats: type=" + profileType + " length=" + parseInt(metrics.length) + " directness=" + roundForLog(metrics.directness, 3) + " maxStraight=" + parseInt(metrics.maxStraightRun));
}

function roundForLog(value, precision) {
	var multiplier = Math.pow(10, precision == null ? 2 : precision);
	return Math.round(value * multiplier) / multiplier;
}

function isJoinEndpointConnected(path, joinPoint, maxDistance) {
	if (path == null || path.length < 1 || joinPoint == null) {
		return false;
	}
	maxDistance = Math.max(1, maxDistance == null ? 3 : maxDistance);
	return getSquaredDistance(path[0][0], path[0][1], joinPoint[0], joinPoint[1]) <= maxDistance * maxDistance;
}

function presetSearchRadius(fraction, minRadius, maxRadius) {
	return Math.max(minRadius, Math.min(maxRadius, parseInt(Math.min(worldWidth, worldHeight) * fraction)));
}

function toWorldRelativePoint(rx, ry) {
	return [
		minX + Math.max(0, Math.min(worldWidth - 1, Math.round(rx * (worldWidth - 1)))),
		minY + Math.max(0, Math.min(worldHeight - 1, Math.round(ry * (worldHeight - 1))))
	];
}

function findTemplateWaterPoint(rx, ry, radius) {
	var center = toWorldRelativePoint(rx, ry);
	var best = null;
	var bestScore = Infinity;
	var step = Math.max(4, parseInt(radius / 28));
	for (var dx = -radius; dx <= radius; dx += step) {
		for (var dy = -radius; dy <= radius; dy += step) {
			var x = center[0] + dx;
			var y = center[1] + dy;
			if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight) {
				continue;
			}
			if (dimension.getHeightAt(x, y) >= dimension.getWaterLevelAt(x, y) - 0.5) {
				continue;
			}
			var score = dx * dx + dy * dy;
			if (score < bestScore) {
				bestScore = score;
				best = [x, y];
			}
		}
	}
	return best == null ? center : best;
}

function findTemplateRiverMouthPoint(rx, ry, radius) {
	var center = toWorldRelativePoint(rx, ry);
	var best = null;
	var bestScore = Infinity;
	var step = Math.max(4, parseInt(radius / 34));
	for (var dx = -radius; dx <= radius; dx += step) {
		for (var dy = -radius; dy <= radius; dy += step) {
			var x = center[0] + dx;
			var y = center[1] + dy;
			if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight || avoidCondition(x, y)) {
				continue;
			}
			if (isWaterAt(x, y) || !hasWaterNearby(x, y, Math.max(6, parseInt(endWidth * 0.8)))) {
				continue;
			}
			var h = dimension.getHeightAt(x, y);
			var wl = dimension.getWaterLevelAt(x, y);
			var heightAboveWater = Math.max(0, h - wl);
			var score = dx * dx + dy * dy + heightAboveWater * 140;
			if (score < bestScore) {
				bestScore = score;
				best = [x, y];
			}
		}
	}
	if (best != null) {
		return best;
	}
	return findTemplateRoutePoint(rx, ry, radius);
}

function isWaterAt(x, y) {
	return dimension.getHeightAt(x, y) < dimension.getWaterLevelAt(x, y) - 0.5;
}

function hasWaterNearby(x, y, radius) {
	radius = Math.max(1, parseInt(radius));
	var step = Math.max(1, parseInt(radius / 4));
	for (var dx = -radius; dx <= radius; dx += step) {
		for (var dy = -radius; dy <= radius; dy += step) {
			if (dx * dx + dy * dy > radius * radius) {
				continue;
			}
			var wx = x + dx;
			var wy = y + dy;
			if (wx - minX < 0 || wx - minX >= worldWidth || wy - minY < 0 || wy - minY >= worldHeight) {
				continue;
			}
			if (isWaterAt(wx, wy)) {
				return true;
			}
		}
	}
	return false;
}

function findTemplateLandPoint(rx, ry, radius) {
	var center = toWorldRelativePoint(rx, ry);
	var best = null;
	var bestScore = -Infinity;
	var step = Math.max(6, parseInt(radius / 24));
	for (var dx = -radius; dx <= radius; dx += step) {
		for (var dy = -radius; dy <= radius; dy += step) {
			var x = center[0] + dx;
			var y = center[1] + dy;
			if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight || avoidCondition(x, y)) {
				continue;
			}
			var h = dimension.getHeightAt(x, y);
			if (h < dimension.getWaterLevelAt(x, y) - 0.5) {
				continue;
			}
			var dist = Math.sqrt(dx * dx + dy * dy);
			var score = h * 2.0 - dist * 0.04 + repeatableRandom(x, y);
			if (score > bestScore) {
				bestScore = score;
				best = [x, y];
			}
		}
	}
	return best == null ? center : best;
}

function findTemplateRoutePoint(rx, ry, radius) {
	var center = toWorldRelativePoint(rx, ry);
	var best = null;
	var bestScore = Infinity;
	var step = Math.max(5, parseInt(radius / 26));
	for (var dx = -radius; dx <= radius; dx += step) {
		for (var dy = -radius; dy <= radius; dy += step) {
			var x = center[0] + dx;
			var y = center[1] + dy;
			if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight || avoidCondition(x, y) || isWaterAt(x, y)) {
				continue;
			}
			var h = dimension.getHeightAt(x, y);
			var wl = dimension.getWaterLevelAt(x, y);
			var heightAboveWater = Math.max(0, h - wl);
			var score = dx * dx + dy * dy + heightAboveWater * 10 + repeatableRandom(x, y) * 3;
			if (score < bestScore) {
				bestScore = score;
				best = [x, y];
			}
		}
	}
	return best == null ? findTemplateLandPoint(rx, ry, radius) : best;
}

function addPathToBlockMap(path, blockMap, radius) {
	if (path == null || blockMap == null) {
		return;
	}
	radius = Math.max(1, parseInt(radius));
	for (var i = 0; i < path.length; i += 2) {
		var p = path[i];
		if (p == null || p[0] == null) {
			continue;
		}
		for (var dx = -radius; dx <= radius; dx++) {
			for (var dy = -radius; dy <= radius; dy++) {
				if (dx * dx + dy * dy > radius * radius) {
					continue;
				}
				var x = p[0] + dx;
				var y = p[1] + dy;
				if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight) {
					continue;
				}
				blockMap.put(toCoordinate(x, y), 1);
			}
		}
	}
}

function getPathBlockedRatio(path, blockMap, sampleStep) {
	if (path == null || path.length == 0 || blockMap == null || blockMap.size() == 0) {
		return 0;
	}
	sampleStep = Math.max(1, parseInt(sampleStep));
	var samples = 0;
	var blocked = 0;
	for (var i = 0; i < path.length; i += sampleStep) {
		var p = path[i];
		if (p == null || p[0] == null) {
			continue;
		}
		samples++;
		if (blockMap.get(toCoordinate(p[0], p[1])) != null) {
			blocked++;
		}
	}
	if (samples == 0) {
		return 0;
	}
	return blocked / samples;
}

function getPathBlockedRatioBeforeEnd(path, blockMap, sampleStep, ignoredTailLength) {
	if (path == null || path.length == 0 || blockMap == null || blockMap.size() == 0) {
		return 0;
	}
	sampleStep = Math.max(1, parseInt(sampleStep));
	ignoredTailLength = Math.max(0, parseInt(ignoredTailLength));
	var limit = Math.max(0, path.length - ignoredTailLength);
	var samples = 0;
	var blocked = 0;
	for (var i = 0; i < limit; i += sampleStep) {
		var p = path[i];
		if (p == null || p[0] == null) {
			continue;
		}
		samples++;
		if (blockMap.get(toCoordinate(p[0], p[1])) != null) {
			blocked++;
		}
	}
	if (samples == 0) {
		return 0;
	}
	return blocked / samples;
}

function getPathBlockedRatioAfterStart(path, blockMap, sampleStep, ignoredHeadLength) {
	if (path == null || path.length == 0 || blockMap == null || blockMap.size() == 0) {
		return 0;
	}
	sampleStep = Math.max(1, parseInt(sampleStep));
	ignoredHeadLength = Math.max(0, parseInt(ignoredHeadLength));
	var start = Math.min(path.length, ignoredHeadLength);
	var samples = 0;
	var blocked = 0;
	for (var i = start; i < path.length; i += sampleStep) {
		var p = path[i];
		if (p == null || p[0] == null) {
			continue;
		}
		samples++;
		if (blockMap.get(toCoordinate(p[0], p[1])) != null) {
			blocked++;
		}
	}
	if (samples == 0) {
		return 0;
	}
	return blocked / samples;
}

function buildHydrologyModel(targetSeaLevel) {
	var cellSize = Math.max(16, Math.min(40, parseInt(Math.min(worldWidth, worldHeight) / 320)));
	var gridW = Math.max(12, parseInt(worldWidth / cellSize));
	var gridH = Math.max(12, parseInt(worldHeight / cellSize));
	var cellCount = gridW * gridH;
	print("[Hydro] Model olusturuluyor... grid=" + gridW + "x" + gridH + " cellSize=" + cellSize + " (" + cellCount + " hucre)");

	var heights = new Array(cellCount);
	var flowTo = new Array(cellCount);
	var accumulation = new Array(cellCount);
	var indices = new Array(cellCount);
	var offsets = [
		[-1, -1], [0, -1], [1, -1],
		[-1, 0], [1, 0],
		[-1, 1], [0, 1], [1, 1]
	];

	for (var gy = 0; gy < gridH; gy++) {
		checkForAbort(false);
		for (var gx = 0; gx < gridW; gx++) {
			var idx = gx + gy * gridW;
			var wx = minX + gx * cellSize + parseInt(cellSize / 2);
			var wy = minY + gy * cellSize + parseInt(cellSize / 2);
			heights[idx] = dimension.getHeightAt(wx, wy);
			flowTo[idx] = -1;
			accumulation[idx] = 1;
			indices[idx] = idx;
		}
		if (gy % 50 == 0) {
			print("[Hydro] Yukseklik ornekleme: %" + parseInt((gy / gridH) * 100));
		}
	}

	for (var gy = 0; gy < gridH; gy++) {
		checkForAbort(false);
		for (var gx = 0; gx < gridW; gx++) {
			var idx = gx + gy * gridW;
			var h = heights[idx];
			var bestIdx = -1;
			var bestScore = 0;
			var lowestIdx = -1;
			var lowestH = h;
			for (var o = 0; o < offsets.length; o++) {
				var nx = gx + offsets[o][0];
				var ny = gy + offsets[o][1];
				if (nx < 0 || nx >= gridW || ny < 0 || ny >= gridH) {
					continue;
				}
				var nIdx = nx + ny * gridW;
				var nh = heights[nIdx];
				if (nh < lowestH) {
					lowestH = nh;
					lowestIdx = nIdx;
				}
				var drop = h - nh;
				if (drop > 0) {
					var stepDist = (Math.abs(offsets[o][0]) + Math.abs(offsets[o][1]) == 2) ? 1.4142135623730951 : 1.0;
					var slope = drop / stepDist;
					if (slope > bestScore) {
						bestScore = slope;
						bestIdx = nIdx;
					}
				}
			}

			if (bestIdx >= 0) {
				flowTo[idx] = bestIdx;
			} else if (h > targetSeaLevel + 0.5 && lowestIdx >= 0) {
				// breach-lite: local pit cells still drain toward the lowest neighbor
				flowTo[idx] = lowestIdx;
			} else {
				flowTo[idx] = -1;
			}
		}
	}

	indices.sort(function (a, b) {
		return heights[b] - heights[a];
	});

	var maxAccum = 1;
	for (var i = 0; i < indices.length; i++) {
		checkForAbort(false);
		var idx = indices[i];
		var to = flowTo[idx];
		if (to >= 0) {
			accumulation[to] += accumulation[idx];
			if (accumulation[to] > maxAccum) {
				maxAccum = accumulation[to];
			}
		}
		if (i % 50000 == 0 && i > 0) {
			print("[Hydro] Birikim hesabi: %" + parseInt((i / indices.length) * 100));
		}
	}

	print("[Hydro] Model hazir. maxAccum=" + parseInt(maxAccum));
	return {
		cellSize: cellSize,
		gridW: gridW,
		gridH: gridH,
		cellCount: cellCount,
		heights: heights,
		flowTo: flowTo,
		accumulation: accumulation,
		maxAccum: maxAccum
	};
}

function hydroIndexToWorld(hydro, idx) {
	var gx = idx % hydro.gridW;
	var gy = parseInt(idx / hydro.gridW);
	return [
		minX + gx * hydro.cellSize + parseInt(hydro.cellSize / 2),
		minY + gy * hydro.cellSize + parseInt(hydro.cellSize / 2)
	];
}

function worldToHydroIndex(hydro, x, y) {
	var gx = parseInt((x - minX) / hydro.cellSize);
	var gy = parseInt((y - minY) / hydro.cellSize);
	gx = Math.max(0, Math.min(hydro.gridW - 1, gx));
	gy = Math.max(0, Math.min(hydro.gridH - 1, gy));
	return gx + gy * hydro.gridW;
}

function selectMainHydroSources(hydro, count, targetSeaLevel) {
	var candidates = [];
	var minAccum = Math.max(8, hydro.maxAccum * 0.01);
	for (var idx = 0; idx < hydro.cellCount; idx++) {
		var acc = hydro.accumulation[idx];
		if (acc < minAccum) {
			continue;
		}
		var h = hydro.heights[idx];
		if (h <= targetSeaLevel + 8) {
			continue;
		}
		var worldPoint = hydroIndexToWorld(hydro, idx);
		if (avoidCondition(worldPoint[0], worldPoint[1])) {
			continue;
		}
		var score = acc * 1.0 + h * 4.0;
		candidates.push({ idx: idx, accum: acc, height: h, score: score });
	}

	candidates.sort(function (a, b) { return b.score - a.score; });

	var result = [];
	var areaPerRiver = hydro.cellCount / Math.max(1, count);
	var adaptiveSpacingCells = Math.max(6, parseInt(Math.sqrt(areaPerRiver) * 0.6));
	var minSpacingCells = Math.max(6, Math.max(parseInt(Math.min(hydro.gridW, hydro.gridH) / (count + 2)), adaptiveSpacingCells));
	var minSpacingSq = minSpacingCells * minSpacingCells;
	for (var i = 0; i < candidates.length && result.length < count; i++) {
		var c = candidates[i];
		var gx = c.idx % hydro.gridW;
		var gy = parseInt(c.idx / hydro.gridW);
		var ok = true;
		for (var r = 0; r < result.length; r++) {
			var rgx = result[r].idx % hydro.gridW;
			var rgy = parseInt(result[r].idx / hydro.gridW);
			var dx = gx - rgx;
			var dy = gy - rgy;
			if (dx * dx + dy * dy < minSpacingSq) {
				ok = false;
				break;
			}
		}
		if (ok) {
			result.push(c);
		}
	}

	return result;
}

function traceHydroPathToOutlet(hydro, startIdx, targetSeaLevel, blockedMap, maxSteps) {
	var path = [];
	var visited = new HashMap();
	var idx = startIdx;
	var reachedOutlet = false;
	maxSteps = Math.max(100, maxSteps);

	for (var step = 0; step < maxSteps && idx >= 0; step++) {
		checkForAbort(false);
		if (visited.get(idx) != null) {
			break;
		}
		visited.put(idx, 1);
		var p = hydroIndexToWorld(hydro, idx);
		if (blockedMap != null && step > 4 && blockedMap.get(toCoordinate(p[0], p[1])) != null && dimension.getHeightAt(p[0], p[1]) > targetSeaLevel + 1) {
			return [];
		}
		path.push([p[0], p[1]]);

		var h = dimension.getHeightAt(p[0], p[1]);
		if (h <= targetSeaLevel || h < (dimension.getWaterLevelAt(p[0], p[1]) - 0.5)) {
			reachedOutlet = true;
			break;
		}

		idx = hydro.flowTo[idx];
	}

	if (path.length < 2 || !reachedOutlet) {
		return [];
	}
	return densifyPath(path, Math.max(6, parseInt(hydro.cellSize / 2)));
}

function traceHydroPathToJoinPoint(hydro, startIdx, targetX, targetY, maxSteps) {
	var path = [];
	var visited = new HashMap();
	var idx = startIdx;
	var targetIdx = worldToHydroIndex(hydro, targetX, targetY);
	var reachedJoin = false;
	maxSteps = Math.max(80, maxSteps);

	for (var step = 0; step < maxSteps && idx >= 0; step++) {
		checkForAbort(false);
		if (visited.get(idx) != null) {
			break;
		}
		visited.put(idx, 1);
		var p = hydroIndexToWorld(hydro, idx);
		path.push([p[0], p[1]]);

		var distSq = getSquaredDistance(p[0], p[1], targetX, targetY);
		if (distSq <= 64 || idx == targetIdx) {
			path.push([targetX, targetY]);
			reachedJoin = true;
			break;
		}

		idx = hydro.flowTo[idx];
	}

	if (path.length < 2 || !reachedJoin) {
		return [];
	}
	return densifyPath(path, Math.max(6, parseInt(hydro.cellSize / 2)));
}

function findHydroTributarySource(hydro, targetX, targetY, minRadius, maxRadius) {
	var targetIdx = worldToHydroIndex(hydro, targetX, targetY);
	var targetAccum = hydro.accumulation[targetIdx];
	var best = null;
	var bestScore = -Infinity;
	var targetHeight = dimension.getHeightAt(targetX, targetY);
	for (var i = 0; i < 260; i++) {
		var angle = Math.random() * 6.28318530718;
		var radius = minRadius + Math.random() * (maxRadius - minRadius);
		var wx = Math.round(targetX + Math.cos(angle) * radius);
		var wy = Math.round(targetY + Math.sin(angle) * radius);
		if (wx - minX < 0 || wx - minX >= worldWidth || wy - minY < 0 || wy - minY >= worldHeight) {
			continue;
		}
		if (avoidCondition(wx, wy) || dimension.getHeightAt(wx, wy) < (dimension.getWaterLevelAt(wx, wy) - 0.5)) {
			continue;
		}
		var idx = worldToHydroIndex(hydro, wx, wy);
		var acc = hydro.accumulation[idx];
		if (acc >= targetAccum * 0.85 || acc < 4) {
			continue;
		}
		var h = hydro.heights[idx];
		if (h <= targetHeight + 1.4) {
			continue;
		}
		var flow = hydro.flowTo[idx];
		if (flow < 0) {
			continue;
		}
		var score = h * 1.7 + acc * 1.45 + radius * 0.08;
		if (score > bestScore) {
			bestScore = score;
			best = { idx: idx, accum: acc, height: h };
		}
	}
	return best;
}

function densifyPath(path, step) {
	step = Math.max(2, parseInt(step));
	var dense = [];
	if (path.length == 0) {
		return dense;
	}
	dense.push(path[0]);
	for (var i = 1; i < path.length; i++) {
		var p0 = path[i - 1];
		var p1 = path[i];
		var dx = p1[0] - p0[0];
		var dy = p1[1] - p0[1];
		var segLen = Math.sqrt(dx * dx + dy * dy);
		var n = Math.max(1, parseInt(segLen / step));
		for (var s = 1; s <= n; s++) {
			var t = s / n;
			var x = Math.round(p0[0] + dx * t);
			var y = Math.round(p0[1] + dy * t);
			if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight) {
				continue;
			}
			var prev = dense[dense.length - 1];
			if (prev[0] != x || prev[1] != y) {
				dense.push([x, y]);
			}
		}
	}
	return dense;
}

function approximatePathLength(path) {
	if (path == null || path.length < 2) {
		return 0;
	}
	var sum = 0;
	for (var i = 1; i < path.length; i++) {
		var dx = path[i][0] - path[i - 1][0];
		var dy = path[i][1] - path[i - 1][1];
		sum += Math.sqrt(dx * dx + dy * dy);
	}
	return sum;
}

function getPathDirectnessRatio(path) {
	if (path == null || path.length < 2) {
		return 1;
	}
	var length = approximatePathLength(path);
	if (length <= 0) {
		return 1;
	}
	var first = path[0];
	var last = path[path.length - 1];
	if (first == null || last == null || first[0] == null || last[0] == null) {
		return 1;
	}
	return Math.sqrt(getSquaredDistance(first[0], first[1], last[0], last[1])) / length;
}

function stylizeRiverPath(path, isMainRiver) {
	if (!fantasyMapRiverStyle || path == null || path.length < 8) {
		return path;
	}
	var meander = classicMeanderStrength == null ? 0.6 : classicMeanderStrength;
	var minStep = isMainRiver ? Math.max(6, parseInt(11 / (0.45 + meander))) : Math.max(5, parseInt(8 / (0.45 + meander)));
	var sparse = simplifyPathByDistance(path, minStep);
	var passes = isMainRiver ? (meander > 0.65 ? 2 : 1) : 1;
	var smooth = chaikinSmoothPath(sparse, passes);
	var dense = densifyPath(smooth, 1);
	if (dense.length > 1) {
		dense[0] = path[0];
		dense[dense.length - 1] = path[path.length - 1];
	}
	return removeConsecutiveDuplicatePoints(dense);
}

function simplifyPathByDistance(path, minDistance) {
	var result = [];
	if (path == null || path.length == 0) {
		return result;
	}
	minDistance = Math.max(2, parseInt(minDistance));
	result.push(path[0]);
	var last = path[0];
	for (var i = 1; i < path.length - 1; i++) {
		var p = path[i];
		if (p == null || p[0] == null) {
			continue;
		}
		if (Math.sqrt(getSquaredDistance(p[0], p[1], last[0], last[1])) >= minDistance) {
			result.push(p);
			last = p;
		}
	}
	var end = path[path.length - 1];
	var prev = result[result.length - 1];
	if (end != null && end[0] != null && (prev[0] != end[0] || prev[1] != end[1])) {
		result.push(end);
	}
	return result;
}

function chaikinSmoothPath(path, passes) {
	if (path == null || path.length < 3) {
		return path;
	}
	var current = path;
	passes = Math.max(1, parseInt(passes));
	for (var pass = 0; pass < passes; pass++) {
		var next = [];
		next.push(current[0]);
		for (var i = 0; i < current.length - 1; i++) {
			var p0 = current[i];
			var p1 = current[i + 1];
			var qx = Math.round(p0[0] * 0.75 + p1[0] * 0.25);
			var qy = Math.round(p0[1] * 0.75 + p1[1] * 0.25);
			var rx = Math.round(p0[0] * 0.25 + p1[0] * 0.75);
			var ry = Math.round(p0[1] * 0.25 + p1[1] * 0.75);
			if (qx - minX >= 0 && qx - minX < worldWidth && qy - minY >= 0 && qy - minY < worldHeight) {
				next.push([qx, qy]);
			}
			if (rx - minX >= 0 && rx - minX < worldWidth && ry - minY >= 0 && ry - minY < worldHeight) {
				next.push([rx, ry]);
			}
		}
		next.push(current[current.length - 1]);
		current = removeConsecutiveDuplicatePoints(next);
		if (current.length < 3) {
			break;
		}
	}
	return current;
}

function removeConsecutiveDuplicatePoints(path) {
	var result = [];
	for (var i = 0; i < path.length; i++) {
		var p = path[i];
		if (p == null || p[0] == null) {
			continue;
		}
		var prev = result.length > 0 ? result[result.length - 1] : null;
		if (prev == null || prev[0] != p[0] || prev[1] != p[1]) {
			result.push(p);
		}
	}
	return result;
}

function isNaturalTributaryJoin(branchPath, mainPath, mainIndex) {
	if (branchPath == null || branchPath.length < 20 || mainPath == null || mainPath.length < 30) {
		return true;
	}
	var b0 = branchPath[Math.min(branchPath.length - 1, 18)];
	var b1 = branchPath[0];
	var m0 = mainPath[Math.max(0, mainIndex - 14)];
	var m1 = mainPath[Math.min(mainPath.length - 1, mainIndex + 14)];
	if (b0 == null || b1 == null || m0 == null || m1 == null) {
		return true;
	}
	var bvx = b1[0] - b0[0];
	var bvy = b1[1] - b0[1];
	var mvx = m1[0] - m0[0];
	var mvy = m1[1] - m0[1];
	var bl = Math.sqrt(bvx * bvx + bvy * bvy);
	var ml = Math.sqrt(mvx * mvx + mvy * mvy);
	if (bl < 1 || ml < 1) {
		return true;
	}
	var parallel = Math.abs((bvx * mvx + bvy * mvy) / (bl * ml));
	return parallel < 0.91;
}

function getHighLandStartPositions(count, targetSeaLevel) {
	count = Math.max(1, parseInt(count));
	var candidates = [];
	var attempts = Math.max(1800, count * 900);
	print("[Delta] Uygun yuksek baslangic noktasi aranıyor... deneme: " + attempts);
	for (var i = 0; i < attempts; i++) {
		checkForAbort(false);
		var x = Math.floor(Math.random() * worldWidth) + minX;
		var y = Math.floor(Math.random() * worldHeight) + minY;
		if (avoidCondition(x, y)) {
			continue;
		}
		var h = dimension.getHeightAt(x, y);
		if (h <= targetSeaLevel + 8 || h < (dimension.getWaterLevelAt(x, y) - 0.5)) {
			continue;
		}
		candidates.push([h, x, y]);
		if ((i + 1) % 5000 == 0) {
			print("[Delta] Baslangic arama ilerleme: %" + parseInt(((i + 1) / attempts) * 100));
		}
	}

	candidates.sort(function (a, b) { return b[0] - a[0]; });
	var result = [];
	var minSpacing = Math.max(90, parseInt(Math.min(worldWidth, worldHeight) / (count + 1)));
	var minSpacingSq = minSpacing * minSpacing;
	for (var c = 0; c < candidates.length && result.length < count; c++) {
		var candidate = candidates[c];
		var ok = true;
		for (var r = 0; r < result.length; r++) {
			var dx = candidate[1] - result[r][0];
			var dy = candidate[2] - result[r][1];
			if (dx * dx + dy * dy < minSpacingSq) {
				ok = false;
				break;
			}
		}
		if (ok) {
			result.push([candidate[1], candidate[2]]);
		}
	}

	return result;
}

function findTributarySource(targetX, targetY, minRadius, maxRadius) {
	var targetHeight = dimension.getHeightAt(targetX, targetY);
	var best = null;
	var bestScore = -Infinity;
	for (var i = 0; i < 120; i++) {
		var angle = Math.random() * 6.28318530718;
		var radius = minRadius + Math.random() * (maxRadius - minRadius);
		var x = Math.round(targetX + Math.cos(angle) * radius);
		var y = Math.round(targetY + Math.sin(angle) * radius);
		if (x - minX < 0 || x - minX >= worldWidth || y - minY < 0 || y - minY >= worldHeight) {
			continue;
		}
		if (avoidCondition(x, y) || dimension.getHeightAt(x, y) < (dimension.getWaterLevelAt(x, y) - 0.5)) {
			continue;
		}
		var h = dimension.getHeightAt(x, y);
		if (h <= targetHeight + 1.2) {
			continue;
		}
		var score = h + radius * 0.05;
		if (score > bestScore) {
			bestScore = score;
			best = [x, y];
		}
	}
	return best;
}

function findPathToPoint(startX, startY, targetX, targetY, maxVisited, avoidWater) {
	var openSet = new PriorityQueue();
	openSet.add(new Candidate(0, startX, startY, 0));
	var addedPositions = new HashMap();
	addedPositions.put(toCoordinate(startX, startY), [null, null]);
	var pathStartMs = new Date().getTime();
	var nextLog = 20000;
	var points = [
		{ x: -1, y: -1 }, { x: -1, y: 0 }, { x: -1, y: 1 },
		{ x: 0, y: -1 }, { x: 0, y: 1 },
		{ x: 1, y: -1 }, { x: 1, y: 0 }, { x: 1, y: 1 }
	];

	while (openSet.values.length != 0 && addedPositions.size() < maxVisited) {
		checkForAbort(false);
		if (addedPositions.size() >= nextLog) {
			var elapsed = new Date().getTime() - pathStartMs;
			var progress = addedPositions.size() / maxVisited;
			var etaMs = progress > 0 ? (elapsed / progress) - elapsed : 0;
			print("[Path->Nokta] %" + parseInt(progress * 100) + "  ziyaret=" + addedPositions.size() + "/" + maxVisited + "  ETA=" + formatDurationShort(etaMs));
			nextLog += 20000;
		}

		var current = openSet.poll();
		if (current == null) {
			break;
		}
		if (getSquaredDistance(current.x, current.y, targetX, targetY) <= 4) {
			return getTrail(current.x, current.y, addedPositions);
		}

		for (var k = 0; k < points.length; k++) {
			var p = points[k];
			var nx = current.x + p.x;
			var ny = current.y + p.y;
			if (nx - minX < 0 || nx - minX >= worldWidth || ny - minY < 0 || ny - minY >= worldHeight || avoidCondition(nx, ny)) {
				continue;
			}
			if (avoidWater && isWaterAt(nx, ny) && getSquaredDistance(nx, ny, targetX, targetY) > 36) {
				continue;
			}
			if (addedPositions.get(toCoordinate(nx, ny)) != null) {
				continue;
			}

			addedPositions.put(toCoordinate(nx, ny), [current.x, current.y]);
			var terrainPenalty = Math.max(0, dimension.getHeightAt(nx, ny) - deltaSeaLevel) * 35;
			var waterPenalty = (avoidWater && isWaterAt(nx, ny)) ? 100000 : 0;
			var targetPenalty = Math.sqrt(getSquaredDistance(nx, ny, targetX, targetY)) * 5;
			var weight = current.dist + calculateCelDist2(p.x, p.y) + terrainPenalty + waterPenalty + targetPenalty + repeatableRandom(nx, ny) * (randomness * 0.4);
			openSet.add(new Candidate(weight, nx, ny, current.dist + calculateCelDist2(p.x, p.y)));
		}
	}
	print("[Path->Nokta] yol bulunamadi veya butce doldu. ziyaret=" + addedPositions.size() + "/" + maxVisited);

	return [[null, null]];
}

function findPathToLevelOrWater(startX, startY, targetSeaLevel, maxVisited, blockedMap) {
	var openSet = new PriorityQueue();
	openSet.add(new Candidate(0, startX, startY, 0));
	var addedPositions = new HashMap();
	addedPositions.put(toCoordinate(startX, startY), [null, null]);
	var pathStartMs = new Date().getTime();
	var nextLog = 50000;
	var points = [
		{ x: 2, y: 1 }, { x: 2, y: 0 }, { x: 2, y: -1 },
		{ x: -2, y: 1 }, { x: -2, y: 0 }, { x: -2, y: -1 },
		{ x: 1, y: 2 }, { x: 0, y: 2 }, { x: -1, y: 2 },
		{ x: 1, y: -2 }, { x: 0, y: -2 }, { x: -1, y: -2 }
	];

	while (openSet.values.length != 0 && addedPositions.size() < maxVisited) {
		checkForAbort(false);
		if (addedPositions.size() >= nextLog) {
			var elapsed = new Date().getTime() - pathStartMs;
			var progress = addedPositions.size() / maxVisited;
			var etaMs = progress > 0 ? (elapsed / progress) - elapsed : 0;
			print("[Path->Seviye] %" + parseInt(progress * 100) + "  ziyaret=" + addedPositions.size() + "/" + maxVisited + "  ETA=" + formatDurationShort(etaMs));
			nextLog += 50000;
		}

		var current = openSet.poll();
		if (current == null) {
			break;
		}

		var h = dimension.getHeightAt(current.x, current.y);
		if (h <= targetSeaLevel || h < (dimension.getWaterLevelAt(current.x, current.y) - 0.5)) {
			return getTrail(current.x, current.y, addedPositions);
		}

		for (var k = 0; k < points.length; k++) {
			var p = points[k];
			var nx = current.x + p.x;
			var ny = current.y + p.y;
			if (nx - minX < 0 || nx - minX >= worldWidth || ny - minY < 0 || ny - minY >= worldHeight || avoidCondition(nx, ny)) {
				continue;
			}
			if (blockedMap != null && blockedMap.get(toCoordinate(nx, ny)) != null && dimension.getHeightAt(nx, ny) > targetSeaLevel + 1) {
				continue;
			}
			if (addedPositions.get(toCoordinate(nx, ny)) != null) {
				continue;
			}

			var intermediates = getIntermediatePositions(p.x, p.y);
			var parentX = current.x;
			var parentY = current.y;
			if (intermediates != null && intermediates.length > 0) {
				var best = null;
				var bestH = Infinity;
				for (var t = 0; t < intermediates.length; t++) {
					var ix = current.x + intermediates[t].x;
					var iy = current.y + intermediates[t].y;
					if (ix - minX < 0 || ix - minX >= worldWidth || iy - minY < 0 || iy - minY >= worldHeight) {
						continue;
					}
					if (addedPositions.get(toCoordinate(ix, iy)) != null) {
						continue;
					}
					var ih = dimension.getHeightAt(ix, iy);
					if (ih < bestH) {
						bestH = ih;
						best = [ix, iy];
					}
				}
				if (best != null) {
					addedPositions.put(toCoordinate(best[0], best[1]), [current.x, current.y]);
					parentX = best[0];
					parentY = best[1];
				}
			}

			addedPositions.put(toCoordinate(nx, ny), [parentX, parentY]);
			var terrainPenalty = Math.max(0, dimension.getHeightAt(nx, ny) - targetSeaLevel) * 120;
			var weight = current.dist + calculateCelDist(p.x, p.y) + terrainPenalty + repeatableRandom(nx, ny) * (randomness * 0.35);
			openSet.add(new Candidate(weight, nx, ny, current.dist + calculateCelDist(p.x, p.y)));
		}
	}
	print("[Path->Seviye] yol bulunamadi veya butce doldu. ziyaret=" + addedPositions.size() + "/" + maxVisited);

	return [[null, null]];
}

function formatDurationShort(ms) {
	if (ms == null || ms < 0 || !isFinite(ms)) {
		return "?";
	}
	var totalSec = parseInt(ms / 1000);
	var min = parseInt(totalSec / 60);
	var sec = totalSec % 60;
	if (min > 0) {
		return min + "dk " + sec + "sn";
	}
	return sec + "sn";
}

function clampBetweenZeroAndOne(x) {
	return Math.max(Math.min(x, 1), 0)
}

function fixupCenterSpike(dimension, x, y) {
	var height = parseInt(dimension.getHeightAt(x, y) - 0.5);
	var left = parseInt(dimension.getHeightAt(x - 1, y) - 0.5);
	var right = parseInt(dimension.getHeightAt(x + 1, y) - 0.5);
	var top = parseInt(dimension.getHeightAt(x, y - 1) - 0.5);
	var bottom = parseInt(dimension.getHeightAt(x, y + 1) - 0.5);
	if (height > left && height > right && height > top && height > bottom) {
		var sum = dimension.getHeightAt(x + 1, y) + dimension.getHeightAt(x - 1, y) + dimension.getHeightAt(x, y + 1) + dimension.getHeightAt(x, y - 1);
		dimension.setHeightAt(x, y, sum / 4);
	}
}

function smoothConfluenceHeights(dimension) {
	if (confluenceMap == null || confluenceMap.size() == 0) {
		return;
	}
	print("Smoothing confluence junction terrain...");
	for (key in confluenceMap) {
		var data = confluenceMap.get(key);
		if (data == null) {
			continue;
		}
		var cx = data[0];
		var cy = data[1];
		var radius = Math.max(5, parseInt(endWidth * 0.28));
		for (var dx = -radius; dx <= radius; dx++) {
			for (var dy = -radius; dy <= radius; dy++) {
				if (dx * dx + dy * dy > radius * radius) {
					continue;
				}
				var wx = cx + dx;
				var wy = cy + dy;
				if (wx - minX < 1 || wx - minX > worldWidth - 1 || wy - minY < 1 || wy - minY > worldHeight - 1) {
					continue;
				}
				if (getConfluenceStrengthAt(wx, wy) <= 0) {
					continue;
				}
				fixupCenterSpike(dimension, wx, wy);
				fixupRelaxed(dimension, wx, wy);
			}
		}
	}
}

// this was taken from fixify
function fixupRelaxed(dimension, x, y) {
	height = parseInt(dimension.getHeightAt(x, y) - 0.5)
	left = parseInt(dimension.getHeightAt(x - 1, y) - 0.5)
	right = parseInt(dimension.getHeightAt(x + 1, y) - 0.5)
	top = parseInt(dimension.getHeightAt(x, y - 1) - 0.5)
	bottom = parseInt(dimension.getHeightAt(x, y + 1) - 0.5)

	if (height > left &&
		height > right &&
		height > top &&
		height > bottom) {
		count = count + 1;
		//set the block to the average of the four blocks. (average to make sure snow layers look fine.)
		var sum = dimension.getHeightAt(x + 1, y) + dimension.getHeightAt(x - 1, y) + dimension.getHeightAt(x, y + 1) + dimension.getHeightAt(x, y - 1);
		var average = sum / 4;
		dimension.setHeightAt(x, y, Math.max(average, left + 0.5, right + 0.5, top + 0.5, bottom + 0.5));
	}
	else if (height < left &&
		height < right &&
		height < top &&
		height < bottom) {
		count = count + 1;
		//set the block to the average of the four blocks. (average to make sure snow layers look fine.)
		var sum = dimension.getHeightAt(x + 1, y) + dimension.getHeightAt(x - 1, y) + dimension.getHeightAt(x, y + 1) + dimension.getHeightAt(x, y - 1);
		var average = sum / 4;
		dimension.setHeightAt(x, y, Math.min(average, left + 0.5, right + 0.5, top + 0.5, bottom + 0.5));
	}
}



//################################################################# randomness #################################################################
//source: https://github.com/josephg/noisejs
function initRandom(global) {
	var module = global.noise = {};

	function Grad(x, y, z) {
		this.x = x; this.y = y; this.z = z;
	}

	Grad.prototype.dot2 = function (x, y) {
		return this.x * x + this.y * y;
	};

	Grad.prototype.dot3 = function (x, y, z) {
		return this.x * x + this.y * y + this.z * z;
	};

	var grad3 = [new Grad(1, 1, 0), new Grad(-1, 1, 0), new Grad(1, -1, 0), new Grad(-1, -1, 0),
	new Grad(1, 0, 1), new Grad(-1, 0, 1), new Grad(1, 0, -1), new Grad(-1, 0, -1),
	new Grad(0, 1, 1), new Grad(0, -1, 1), new Grad(0, 1, -1), new Grad(0, -1, -1)];

	var p = [151, 160, 137, 91, 90, 15,
		131, 13, 201, 95, 96, 53, 194, 233, 7, 225, 140, 36, 103, 30, 69, 142, 8, 99, 37, 240, 21, 10, 23,
		190, 6, 148, 247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32, 57, 177, 33,
		88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175, 74, 165, 71, 134, 139, 48, 27, 166,
		77, 146, 158, 231, 83, 111, 229, 122, 60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244,
		102, 143, 54, 65, 25, 63, 161, 1, 216, 80, 73, 209, 76, 132, 187, 208, 89, 18, 169, 200, 196,
		135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64, 52, 217, 226, 250, 124, 123,
		5, 202, 38, 147, 118, 126, 255, 82, 85, 212, 207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42,
		223, 183, 170, 213, 119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9,
		129, 22, 39, 253, 19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104, 218, 246, 97, 228,
		251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241, 81, 51, 145, 235, 249, 14, 239, 107,
		49, 192, 214, 31, 181, 199, 106, 157, 184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254,
		138, 236, 205, 93, 222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180];
	// To remove the need for index wrapping, double the permutation table length
	var perm = new Array(512);
	var gradP = new Array(512);

	// This isn't a very good seeding function, but it works ok. It supports 2^16
	// different seed values. Write something better if you need more seeds.
	module.seed = function (seed) {
		if (seed > 0 && seed < 1) {
			// Scale the seed out
			seed *= 65536;
		}

		seed = Math.floor(seed);
		if (seed < 256) {
			seed |= seed << 8;
		}

		for (var i = 0; i < 256; i++) {
			var v;
			if (i & 1) {
				v = p[i] ^ (seed & 255);
			} else {
				v = p[i] ^ ((seed >> 8) & 255);
			}

			perm[i] = perm[i + 256] = v;
			gradP[i] = gradP[i + 256] = grad3[v % 12];
		}
	};

	module.seed(0);

	/*
	for(var i=0; i<256; i++) {
	  perm[i] = perm[i + 256] = p[i];
	  gradP[i] = gradP[i + 256] = grad3[perm[i] % 12];
	}*/

	// Skewing and unskewing factors for 2, 3, and 4 dimensions
	var F2 = 0.5 * (Math.sqrt(3) - 1);
	var G2 = (3 - Math.sqrt(3)) / 6;

	var F3 = 1 / 3;
	var G3 = 1 / 6;

	// 2D simplex noise
	module.simplex2 = function (xin, yin) {
		var n0, n1, n2; // Noise contributions from the three corners
		// Skew the input space to determine which simplex cell we're in
		var s = (xin + yin) * F2; // Hairy factor for 2D
		var i = Math.floor(xin + s);
		var j = Math.floor(yin + s);
		var t = (i + j) * G2;
		var x0 = xin - i + t; // The x,y distances from the cell origin, unskewed.
		var y0 = yin - j + t;
		// For the 2D case, the simplex shape is an equilateral triangle.
		// Determine which simplex we are in.
		var i1, j1; // Offsets for second (middle) corner of simplex in (i,j) coords
		if (x0 > y0) { // lower triangle, XY order: (0,0)->(1,0)->(1,1)
			i1 = 1; j1 = 0;
		} else {    // upper triangle, YX order: (0,0)->(0,1)->(1,1)
			i1 = 0; j1 = 1;
		}
		// A step of (1,0) in (i,j) means a step of (1-c,-c) in (x,y), and
		// a step of (0,1) in (i,j) means a step of (-c,1-c) in (x,y), where
		// c = (3-sqrt(3))/6
		var x1 = x0 - i1 + G2; // Offsets for middle corner in (x,y) unskewed coords
		var y1 = y0 - j1 + G2;
		var x2 = x0 - 1 + 2 * G2; // Offsets for last corner in (x,y) unskewed coords
		var y2 = y0 - 1 + 2 * G2;
		// Work out the hashed gradient indices of the three simplex corners
		i &= 255;
		j &= 255;
		var gi0 = gradP[i + perm[j]];
		var gi1 = gradP[i + i1 + perm[j + j1]];
		var gi2 = gradP[i + 1 + perm[j + 1]];
		// Calculate the contribution from the three corners
		var t0 = 0.5 - x0 * x0 - y0 * y0;
		if (t0 < 0) {
			n0 = 0;
		} else {
			t0 *= t0;
			n0 = t0 * t0 * gi0.dot2(x0, y0);  // (x,y) of grad3 used for 2D gradient
		}
		var t1 = 0.5 - x1 * x1 - y1 * y1;
		if (t1 < 0) {
			n1 = 0;
		} else {
			t1 *= t1;
			n1 = t1 * t1 * gi1.dot2(x1, y1);
		}
		var t2 = 0.5 - x2 * x2 - y2 * y2;
		if (t2 < 0) {
			n2 = 0;
		} else {
			t2 *= t2;
			n2 = t2 * t2 * gi2.dot2(x2, y2);
		}
		// Add contributions from each corner to get the final noise value.
		// The result is scaled to return values in the interval [-1,1].
		return 70 * (n0 + n1 + n2);
	};

	// 3D simplex noise
	module.simplex3 = function (xin, yin, zin) {
		var n0, n1, n2, n3; // Noise contributions from the four corners

		// Skew the input space to determine which simplex cell we're in
		var s = (xin + yin + zin) * F3; // Hairy factor for 2D
		var i = Math.floor(xin + s);
		var j = Math.floor(yin + s);
		var k = Math.floor(zin + s);

		var t = (i + j + k) * G3;
		var x0 = xin - i + t; // The x,y distances from the cell origin, unskewed.
		var y0 = yin - j + t;
		var z0 = zin - k + t;

		// For the 3D case, the simplex shape is a slightly irregular tetrahedron.
		// Determine which simplex we are in.
		var i1, j1, k1; // Offsets for second corner of simplex in (i,j,k) coords
		var i2, j2, k2; // Offsets for third corner of simplex in (i,j,k) coords
		if (x0 >= y0) {
			if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
			else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
			else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
		} else {
			if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
			else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
			else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
		}
		// A step of (1,0,0) in (i,j,k) means a step of (1-c,-c,-c) in (x,y,z),
		// a step of (0,1,0) in (i,j,k) means a step of (-c,1-c,-c) in (x,y,z), and
		// a step of (0,0,1) in (i,j,k) means a step of (-c,-c,1-c) in (x,y,z), where
		// c = 1/6.
		var x1 = x0 - i1 + G3; // Offsets for second corner
		var y1 = y0 - j1 + G3;
		var z1 = z0 - k1 + G3;

		var x2 = x0 - i2 + 2 * G3; // Offsets for third corner
		var y2 = y0 - j2 + 2 * G3;
		var z2 = z0 - k2 + 2 * G3;

		var x3 = x0 - 1 + 3 * G3; // Offsets for fourth corner
		var y3 = y0 - 1 + 3 * G3;
		var z3 = z0 - 1 + 3 * G3;

		// Work out the hashed gradient indices of the four simplex corners
		i &= 255;
		j &= 255;
		k &= 255;
		var gi0 = gradP[i + perm[j + perm[k]]];
		var gi1 = gradP[i + i1 + perm[j + j1 + perm[k + k1]]];
		var gi2 = gradP[i + i2 + perm[j + j2 + perm[k + k2]]];
		var gi3 = gradP[i + 1 + perm[j + 1 + perm[k + 1]]];

		// Calculate the contribution from the four corners
		var t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
		if (t0 < 0) {
			n0 = 0;
		} else {
			t0 *= t0;
			n0 = t0 * t0 * gi0.dot3(x0, y0, z0);  // (x,y) of grad3 used for 2D gradient
		}
		var t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
		if (t1 < 0) {
			n1 = 0;
		} else {
			t1 *= t1;
			n1 = t1 * t1 * gi1.dot3(x1, y1, z1);
		}
		var t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
		if (t2 < 0) {
			n2 = 0;
		} else {
			t2 *= t2;
			n2 = t2 * t2 * gi2.dot3(x2, y2, z2);
		}
		var t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
		if (t3 < 0) {
			n3 = 0;
		} else {
			t3 *= t3;
			n3 = t3 * t3 * gi3.dot3(x3, y3, z3);
		}
		// Add contributions from each corner to get the final noise value.
		// The result is scaled to return values in the interval [-1,1].
		return 32 * (n0 + n1 + n2 + n3);

	};

	// ##### Perlin noise stuff

	function fade(t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	function lerp(a, b, t) {
		return (1 - t) * a + t * b;
	}

	// 2D Perlin Noise
	module.perlin2 = function (x, y) {
		// Find unit grid cell containing point
		var X = Math.floor(x), Y = Math.floor(y);
		// Get relative xy coordinates of point within that cell
		x = x - X; y = y - Y;
		// Wrap the integer cells at 255 (smaller integer period can be introduced here)
		X = X & 255; Y = Y & 255;

		// Calculate noise contributions from each of the four corners
		var n00 = gradP[X + perm[Y]].dot2(x, y);
		var n01 = gradP[X + perm[Y + 1]].dot2(x, y - 1);
		var n10 = gradP[X + 1 + perm[Y]].dot2(x - 1, y);
		var n11 = gradP[X + 1 + perm[Y + 1]].dot2(x - 1, y - 1);

		// Compute the fade curve value for x
		var u = fade(x);

		// Interpolate the four results
		return lerp(
			lerp(n00, n10, u),
			lerp(n01, n11, u),
			fade(y));
	};

	// 3D Perlin Noise
	module.perlin3 = function (x, y, z) {
		// Find unit grid cell containing point
		var X = Math.floor(x), Y = Math.floor(y), Z = Math.floor(z);
		// Get relative xyz coordinates of point within that cell
		x = x - X; y = y - Y; z = z - Z;
		// Wrap the integer cells at 255 (smaller integer period can be introduced here)
		X = X & 255; Y = Y & 255; Z = Z & 255;

		// Calculate noise contributions from each of the eight corners
		var n000 = gradP[X + perm[Y + perm[Z]]].dot3(x, y, z);
		var n001 = gradP[X + perm[Y + perm[Z + 1]]].dot3(x, y, z - 1);
		var n010 = gradP[X + perm[Y + 1 + perm[Z]]].dot3(x, y - 1, z);
		var n011 = gradP[X + perm[Y + 1 + perm[Z + 1]]].dot3(x, y - 1, z - 1);
		var n100 = gradP[X + 1 + perm[Y + perm[Z]]].dot3(x - 1, y, z);
		var n101 = gradP[X + 1 + perm[Y + perm[Z + 1]]].dot3(x - 1, y, z - 1);
		var n110 = gradP[X + 1 + perm[Y + 1 + perm[Z]]].dot3(x - 1, y - 1, z);
		var n111 = gradP[X + 1 + perm[Y + 1 + perm[Z + 1]]].dot3(x - 1, y - 1, z - 1);

		// Compute the fade curve value for x, y, z
		var u = fade(x);
		var v = fade(y);
		var w = fade(z);

		// Interpolate
		return lerp(
			lerp(
				lerp(n000, n100, u),
				lerp(n001, n101, u), w),
			lerp(
				lerp(n010, n110, u),
				lerp(n011, n111, u), w),
			v);
	};

}
































































