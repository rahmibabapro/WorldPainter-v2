package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.Biome;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** Executes the actual shipped bridge with Nashorn and the real Java carver. */
public class RiverScriptBridgeTest {
    @Test public void drawnLineKeepsBothEndsDespiteInteriorPeakAndDip() throws Exception {
        Dimension d = flat();
        d.setHeightAt(20, 64, 103);
        d.setHeightAt(60, 64, 104);
        d.setHeightAt(80, 64, 98);
        long before = d.getChangeNo();
        ScriptEngine engine = drawnLineEngine(d);
        engine.eval("var points=[];for(var x=20;x<=108;x++)points.push({x:x,y:64});"
                + "var selected=resolveFixture(points);"
                + "if(selected.path.length!==89 || selected.source.x!==20 || selected.outlet.x!==108)"
                + "throw 'interior extrema truncated the stroke';");
        assertEquals("Route selection must not edit the terrain", before, d.getChangeNo());
    }

    @Test public void drawnCornerIsNotCutDiagonally() throws Exception {
        ScriptEngine engine = drawnLineEngine(flat());
        engine.eval("var points=[{x:20,y:20},{x:21,y:20},{x:21,y:21},{x:21,y:22}];"
                + "var selected=resolveFixture(points);"
                + "if(selected.path.length!==4)throw 'painted corner skipped';");
    }

    @Test public void trulyDiagonalDrawnLineRemainsConnected() throws Exception {
        ScriptEngine engine = drawnLineEngine(flat());
        engine.eval("var points=[];for(var i=20;i<=40;i++)points.push({x:i,y:i});"
                + "var selected=resolveFixture(points);"
                + "if(selected.path.length!==21)throw 'diagonal disconnected';");
    }

    @Test public void reversedInputStillOrientsFromHigherEndpoint() throws Exception {
        Dimension d = flat();
        d.setHeightAt(20, 64, 102);
        ScriptEngine engine = drawnLineEngine(d);
        engine.eval("var points=[];for(var x=108;x>=20;x--)points.push({x:x,y:64});"
                + "var selected=resolveFixture(points);"
                + "if(selected.path.length!==89 || selected.source.x!==20 || selected.outlet.x!==108)"
                + "throw 'input ordering changed flow';");
    }

    @Test public void drawnLineSelectionRemainsCancellable() throws Exception {
        ScriptEngine engine = drawnLineEngine(flat());
        engine.eval("function checkRiverLineCancel(){throw 'cancelled';};");
        assertThrows(ScriptException.class, () -> engine.eval(
                "resolveFixture([{x:20,y:20},{x:21,y:20}]);"));
    }

    @Test public void selectedStrokeFeedsTheRealCarverWithoutLosingItsEnds() throws Exception {
        Dimension d = flat();
        d.setHeightAt(20, 64, 100.125f);
        d.setHeightAt(60, 64, 100.25f);
        ScriptEngine engine = drawnLineEngine(d);
        engine.eval("var points=[];for(var x=20;x<=108;x++)points.push({x:x,y:64});"
                + "carveShallowNamedLine(resolveFixture(points).path);");
        assertChannel(d);
    }

    @Test public void closedLoopRetainsConnectedLegacyFallback() throws Exception {
        ScriptEngine engine = drawnLineEngine(flat());
        engine.eval("var points=[];for(var x=20;x<=24;x++)points.push({x:x,y:20});"
                + "for(var y=21;y<=24;y++)points.push({x:24,y:y});"
                + "for(var x=23;x>=20;x--)points.push({x:x,y:24});"
                + "for(var y=23;y>=21;y--)points.push({x:20,y:y});"
                + "var selected=resolveFixture(points);"
                + "if(selected.componentSize!==16 || selected.path.length<2)throw 'loop fallback failed';");
    }

    private ScriptEngine drawnLineEngine(Dimension d) throws Exception {
        ScriptEngine engine = engine(d, "river_from_line.js", "resolveDrawnCentreline",
                "collectLineComponent", "findConnectedLinePath", "lineNeighbours",
                "highestLinePoint", "lowestLinePoint", "lineKey", "carveShallowNamedLine");
        engine.eval("function checkRiverLineCancel(){};"
                + "function resolveFixture(points){var map={};for(var i=0;i<points.length;i++)"
                + "map[lineKey(points[i].x,points[i].y)]=true;return resolveDrawnCentreline(points,map);};");
        return engine;
    }

    @Test public void sourceReversesOutletFirstAndInvokesNewCarver() throws Exception {
        Dimension d = flat();
        ScriptEngine engine = engine(d, "river_script.js", "carveShallowNamedPaths");
        engine.eval("carveShallowNamedPaths([[[108,64],[20,64]]]);");
        assertChannel(d);
    }

    @Test public void waypointUsesSameCarverAndGranite() throws Exception {
        Dimension d = flat();
        ScriptEngine engine = engine(d, "river_from_line.js", "carveShallowNamedLine");
        engine.eval("carveShallowNamedLine([{x:20,y:64},{x:108,y:64}]);");
        assertChannel(d);
        assertTrue(((Number) engine.get("shallowGraniteCells")).longValue() > 0);
    }

    @Test public void malformedSecondRouteCannotApplyThePreviouslyAcceptedSharedPlan() throws Exception {
        for (String invalid : new String[] {"NaN", "Infinity", "-Infinity", "2147483648", "' '"}) {
            Dimension d = flat();
            ScriptEngine engine = engine(d, "river_script.js", "carveShallowNamedPaths");
            assertThrows("Invalid coordinate: " + invalid, ScriptException.class,
                    () -> engine.eval("carveShallowNamedPaths([[[108,64],[20,64]],[[108,96],[" + invalid + ",96]]]);"));
            assertFlatUntouched(d);
        }
    }

    @Test public void malformedWaypointCoordinatesAreRejectedBeforeJavaIntegerCoercion() throws Exception {
        for (String invalid : new String[] {"NaN", "Infinity", "-2147483649", "null", "'bad'"}) {
            Dimension d = flat();
            ScriptEngine engine = engine(d, "river_from_line.js", "carveShallowNamedLine");
            assertThrows("Invalid coordinate: " + invalid, ScriptException.class,
                    () -> engine.eval("carveShallowNamedLine([{x:20,y:64},{x:" + invalid + ",y:64}]);"));
            assertFlatUntouched(d);
        }
    }

    @Test public void legacyFixupsPreserveProtectedAndMissingCells() throws Exception {
        for (org.pepsoft.worldpainter.layers.Layer layer : new org.pepsoft.worldpainter.layers.Layer[] {
                org.pepsoft.worldpainter.layers.ReadOnly.INSTANCE,
                org.pepsoft.worldpainter.layers.NotPresent.INSTANCE,
                org.pepsoft.worldpainter.layers.NotPresentBlock.INSTANCE,
                org.pepsoft.worldpainter.layers.Void.INSTANCE,
                org.pepsoft.worldpainter.layers.FloodWithLava.INSTANCE,
                org.pepsoft.worldpainter.layers.River.INSTANCE}) {
            Dimension d = flat();
            d.setHeightAt(64, 64, 110);
            d.setBitLayerValueAt(layer, 64, 64, true);
            ScriptEngine engine = engine(d, "river_script.js", "canEditLegacyRiverCell", "fixupCenterSpike", "fixupRelaxed");
            engine.eval("if(canEditLegacyRiverCell(dimension,64,64)) throw 'protected cell allowed';"
                    + "if(canEditLegacyRiverCell(dimension,128,64)) throw 'missing tile allowed';"
                    + "fixupCenterSpike(dimension,64,64);fixupRelaxed(dimension,64,64);");
            assertEquals(layer.getName(), 110f, d.getHeightAt(64, 64), 0);
            assertEquals(0, d.getWaterLevelAt(64, 64));
        }
    }

    private void assertFlatUntouched(Dimension d) {
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            assertEquals(100f, d.getHeightAt(x, y), 0);
            assertEquals(0, d.getWaterLevelAt(x, y));
            assertEquals(Terrain.GRASS, d.getTerrainAt(x, y));
        }
    }

    @Test public void namedSourceAndWaypointPreserveDryHillsidesWithEitherSmoothingSetting() throws Exception {
        for (String script : new String[] {"river_script.js", "river_from_line.js"}) {
            for (boolean smooth : new boolean[] {false, true}) {
                Dimension d = flat();
                // A slightly raised surrounding slope exposed the former wide,
                // flattened 12-block shoulders even though the centre is flat.
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                    d.setHeightAt(x, y, 100 + Math.max(0, Math.abs(y - 64) - 3) * 0.08f);
                }
                final float[] oldHeights = new float[128 * 128];
                final int[] oldBiomes = new int[128 * 128];
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                    oldHeights[x + y * 128] = d.getHeightAt(x, y);
                    oldBiomes[x + y * 128] = d.getLayerValueAt(Biome.INSTANCE, x, y);
                }
                final boolean waypoint = script.equals("river_from_line.js");
                ScriptEngine engine = engine(d, script, waypoint ? "carveShallowNamedLine" : "carveShallowNamedPaths");
                engine.eval("bankSmoothing=" + smooth + ";"
                        + (waypoint ? "carveShallowNamedLine([{x:20,y:64},{x:108,y:64}]);"
                        : "carveShallowNamedPaths([[[108,64],[20,64]]]);"));
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                    final String context = script + "/smooth=" + smooth + " at " + x + "," + y;
                    final float original = oldHeights[x + y * 128];
                    final float current = d.getHeightAt(x, y);
                    assertTrue("No terrain fill: " + context, current <= original);
                    assertTrue("Only shallow cuts: " + context, original - current <= 1.855);
                    if (d.getWaterLevelAt(x, y) <= d.getIntHeightAt(x, y)) {
                        assertEquals("Dry terrain must not be flattened: " + context, original, current, 0);
                        assertEquals("Dry terrain material must stay intact: " + context, Terrain.GRASS, d.getTerrainAt(x, y));
                        assertEquals("Dry water value must stay intact: " + context, 0, d.getWaterLevelAt(x, y));
                        assertEquals("Dry biome must stay intact: " + context, oldBiomes[x + y * 128],
                                d.getLayerValueAt(Biome.INSTANCE, x, y));
                    }
                }
                assertTrue(d.getWaterLevelAt(20, 64) > d.getIntHeightAt(20, 64));
                assertTrue(d.getWaterLevelAt(108, 64) > d.getIntHeightAt(108, 64));
            }
        }
    }

    @Test public void unsuitableExplicitWaypointIsNotMovedOrForcedThroughARidge() throws Exception {
        Dimension d = flat();
        d.setHeightAt(64, 64, 112);
        ScriptEngine engine = engine(d, "river_from_line.js", "carveShallowNamedLine");
        assertThrows(ScriptException.class,
                () -> engine.eval("carveShallowNamedLine([{x:20,y:64},{x:64,y:64},{x:108,y:64}]);"));
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            assertEquals(x == 64 && y == 64 ? 112f : 100f, d.getHeightAt(x, y), 0);
            assertEquals(0, d.getWaterLevelAt(x, y));
            assertEquals(Terrain.GRASS, d.getTerrainAt(x, y));
        }
    }

    @Test public void rejectedPathIsReroutedWithoutCuttingTheHighObstacle() throws Exception {
        Dimension d = flat();
        d.setHeightAt(64, 64, 112);
        ScriptEngine engine = engine(d, "river_script.js", "carveShallowNamedPaths");
        engine.eval("var result=carveShallowNamedPaths([[[108,64],[64,64],[20,64]]]);");
        assertNotNull("A rejected initial path must trigger real alternative routing", engine.get("result"));
        assertTrue(d.getWaterLevelAt(20, 64) > d.getIntHeightAt(20, 64));
        assertEquals(112f, d.getHeightAt(64, 64), 0);
    }

    @Test public void emptyAutomaticCandidateListStillGeneratesRealRiverOnSmallFlatMap() throws Exception {
        Dimension d = flat();
        ScriptEngine engine = engine(d, "river_script.js", "carveShallowNamedPaths");
        engine.eval("var result=carveShallowNamedPaths([],[],true,1);");
        assertNotNull(engine.get("result"));
        assertEquals(1, ((Number) engine.get("numberOfRivers")).intValue());
        assertTrue(((Number) engine.get("shallowGraniteCells")).longValue() > 0);
    }

    @Test public void missingManualSourcesRaiseAVisibleExplanationWithoutChangingTerrain() throws Exception {
        Dimension d = flat();
        ScriptEngine engine = engine(d, "river_script.js", "carveShallowNamedPaths");
        ScriptException failure = assertThrows(ScriptException.class,
                () -> engine.eval("carveShallowNamedPaths([],[],false,1);"));
        assertTrue(failure.getMessage().contains("Geçerli boyanmış kaynak bulunamadı"));
        assertTrue(failure.getMessage().contains("Dünya değiştirilmedi"));
        assertEquals(0, ((Number) engine.get("numberOfRivers")).intValue());
        for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
            assertEquals(100f, d.getHeightAt(x,y), 0);
            assertEquals(0, d.getWaterLevelAt(x,y));
        }
    }

    @Test public void fullyProtectedAutomaticWorldReportsZeroResultInsteadOfSilentSuccess() throws Exception {
        Dimension d = flat();
        for (int y=0;y<128;y+=16) for (int x=0;x<128;x+=16) {
            d.setBitLayerValueAt(org.pepsoft.worldpainter.layers.ReadOnly.INSTANCE, x, y, true);
        }
        ScriptEngine engine = engine(d, "river_script.js", "carveShallowNamedPaths");
        ScriptException failure = assertThrows(ScriptException.class,
                () -> engine.eval("carveShallowNamedPaths([],[],true,2);"));
        assertTrue(failure.getMessage().contains("İstenen: 2, oluşturulan: 0"));
        assertTrue(failure.getMessage().contains("Dünya değiştirilmedi"));
        assertTrue(failure.getMessage().contains("Neden:"));
        assertFlatUntouched(d);
    }

    @Test public void wetManualSourceReportsItsFailureWithoutMovingTheSource() throws Exception {
        Dimension d = flat();
        d.setWaterLevelAt(32,64,120);
        ScriptEngine engine = engine(d, "river_script.js", "carveShallowNamedPaths");
        ScriptException failure = assertThrows(ScriptException.class,
                () -> engine.eval("carveShallowNamedPaths([],[[32,64]],false,1);"));
        assertTrue(failure.getMessage().contains("İstenen: 1, oluşturulan: 0"));
        assertTrue(failure.getMessage().contains("Neden:"));
        for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
            assertEquals(100f, d.getHeightAt(x,y), 0);
            assertEquals(x==32 && y==64 ? 120 : 0, d.getWaterLevelAt(x,y));
        }
    }

    @Test public void avoidPredicateHonoursChunkBitLayersWithoutTheOldBoundaryExclusion() throws Exception {
        Dimension d = flat();
        d.setBitLayerValueAt(org.pepsoft.worldpainter.layers.ReadOnly.INSTANCE,64,64,true);
        ScriptEngine engine = engine(d, "river_script.js", "createNamedAvoidPredicate");
        engine.put("avoidLayer", org.pepsoft.worldpainter.layers.ReadOnly.INSTANCE);
        engine.eval("var blocked=createNamedAvoidPredicate();"
                + "if(!blocked.test(65,65)) throw 'chunk bit lost';"
                + "if(blocked.test(2,2)) throw 'old boundary exclusion leaked';");
    }

    @Test public void onePaintedSourcePatchProducesOneSourceAndRoutesFromThatExactPatch() throws Exception {
        Dimension d = flat();
        for (int y=64;y<67;y++) for (int x=32;x<35;x++) d.setTerrainAt(x,y,Terrain.STONE);
        ScriptEngine engine = engine(d, "river_script.js", "findManualSourceTerrainPositions", "addNamedSource",
                "avoidCondition", "avoidLayerCondition", "isNearWorldBoundary", "carveShallowNamedPaths");
        engine.put("sourceTerrain", Terrain.STONE);
        engine.eval("var minX=0,minY=0,worldWidth=128,worldHeight=128,riverBoundaryMargin=64,avoidLayer=null;"
                + "var sources=findManualSourceTerrainPositions(sourceTerrain);"
                + "if(sources.length!==1 || sources[0][0]!==32 || sources[0][1]!==64) throw 'paint patch was lost';"
                + "var result=carveShallowNamedPaths([],sources,false,1);");
        assertNotNull(engine.get("result"));
        assertTrue(d.getWaterLevelAt(32,64)>d.getIntHeightAt(32,64));
        assertEquals(1, ((Number) engine.get("numberOfRivers")).intValue());
    }

    @Test public void automaticHydrologyProducerNormalisesSourceFirstBeforeRealCarver() throws Exception {
        Dimension d = wide(true);
        ScriptEngine engine = automaticEngine(d, false);
        engine.eval("var generated=generateDeltaNetwork(1,101);"
                + "if(generated.paths.length!==1 || generated.paths[0][0][0]!==308"
                + " || generated.paths[0][generated.paths[0].length-1][0]!==20) throw 'wrong producer order';"
                + "carveShallowNamedPaths(generated.paths);");
        assertDescendingChannel(d);
        assertEquals(0, ((Number) engine.get("fallbackCalls")).intValue());
    }

    @Test public void automaticFallbackKeepsPredecessorTrailOutletFirst() throws Exception {
        Dimension d = wide(true);
        ScriptEngine engine = automaticEngine(d, true);
        engine.eval("var generated=generateDeltaNetwork(1,101);"
                + "if(generated.paths.length!==1 || generated.paths[0][0][0]!==308) throw 'wrong fallback order';"
                + "carveShallowNamedPaths(generated.paths);");
        assertDescendingChannel(d);
        assertEquals(1, ((Number) engine.get("fallbackCalls")).intValue());
    }

    @Test public void manualSourceProducerKeepsOutletFirstBeforeRealCarver() throws Exception {
        Dimension d = wide(true);
        ScriptEngine engine = engine(d, "river_script.js", "carveShallowNamedPaths", "getTrail",
                "findGuaranteedManualPath", "isUsablePath");
        routeFixture(engine);
        engine.eval("var deltaSeaLevel=101;"
                + "function findManualPathToLevelOrBoundary(){return getTrail(308,64,predecessors);};"
                + "var manual=findGuaranteedManualPath(20,64,null);"
                + "if(manual[0][0]!==308 || manual[manual.length-1][0]!==20) throw 'wrong manual order';"
                + "carveShallowNamedPaths([manual]);");
        assertDescendingChannel(d);
    }

    @Test public void flatHydrologyJoinUsesProducerOrderNotHeightHeuristic() throws Exception {
        Dimension d = wide(false);
        ScriptEngine engine = engine(d, "river_script.js", "carveShallowNamedPaths",
                "traceHydroPathToJoinPoint", "densifyPath", "getSquaredDistance");
        routeFixture(engine);
        engine.eval("var path=traceHydroPathToJoinPoint(hydro,0,308,64,1000);"
                + "if(path[0][0]!==308 || path[path.length-1][0]!==20) throw 'wrong join order';"
                + "carveShallowNamedPaths([path]);");
        assertTrue("Downstream must receive the wider channel even on equal-height terrain",
                d.getWaterLevelAt(300, 67) > d.getIntHeightAt(300, 67));
        assertFalse("Source must not incorrectly receive the downstream width",
                d.getWaterLevelAt(24, 67) > d.getIntHeightAt(24, 67));
    }

    @Test public void legacyHydrologyProducerRetainsItsOriginalSourceFirstOrder() throws Exception {
        ScriptEngine engine = engine(wide(true), "river_script.js", "traceHydroPathToOutlet",
                "traceHydroPathToJoinPoint", "densifyPath", "getSquaredDistance");
        routeFixture(engine);
        engine.eval("riverPreset=null;"
                + "var outlet=traceHydroPathToOutlet(hydro,0,101,null,1000);"
                + "var join=traceHydroPathToJoinPoint(hydro,0,308,64,1000);"
                + "if(outlet[0][0]!==20 || outlet[outlet.length-1][0]!==308) throw 'legacy outlet changed';"
                + "if(join[0][0]!==20 || join[join.length-1][0]!==308) throw 'legacy join changed';");
    }

    private ScriptEngine automaticEngine(Dimension d, boolean fallback) throws Exception {
        ScriptEngine engine = engine(d, "river_script.js", "carveShallowNamedPaths", "generateDeltaNetwork",
                "traceHydroPathToOutlet", "densifyPath", "stylizeRiverPath", "approximatePathLength", "getTrail");
        routeFixture(engine);
        // Exercise the actual network producer and bridge; substitute only source
        // selection / search and unrelated junction diagnostics for this fixed route.
        engine.eval("var endWidth=6,minRiverLength=220,riverLayoutPreset=0,riverBoundaryMargin=0;"
                + "var disableBranching=true,fantasyMapRiverStyle=false,fallbackCalls=0;"
                + "function buildHydrologyModel(){return hydro;};"
                + "function selectMainHydroSources(){return [{idx:0,accum:100}];};"
                + "function findPathToLevelOrWater(){fallbackCalls++;return getTrail(308,64,predecessors);};"
                + "function pathTouchesWorldBoundary(){return false;};function getPathBlockedRatio(){return 0;};"
                + "function makePathProfile(){return {};};function addPathToBlockMap(){};"
                + "function stitchNearbyRiverChannels(){};function formatDurationShort(){return '';};"
                + "function clampBetweenZeroAndOne(value){return Math.max(0,Math.min(1,value));};");
        if (fallback) engine.eval("hydro.flowTo=[-1,-1,-1];");
        return engine;
    }

    private void routeFixture(ScriptEngine engine) throws Exception {
        engine.eval("var HashMap=Java.type('java.util.HashMap');"
                + "var minX=0,minY=0,worldWidth=384,worldHeight=128;"
                + "var routePoints=[[20,64],[164,64],[308,64]];"
                + "var hydro={flowTo:[1,2,-1],cellSize:32,cellCount:1000,maxAccum:100};"
                + "function hydroIndexToWorld(model,index){return routePoints[index];};"
                + "function worldToHydroIndex(){return 2;};"
                + "function toCoordinate(x,y){return x+y*worldWidth;};"
                + "var predecessors=new HashMap();"
                + "predecessors.put(toCoordinate(20,64),[null,null]);"
                + "predecessors.put(toCoordinate(164,64),[20,64]);"
                + "predecessors.put(toCoordinate(308,64),[164,64]);");
    }

    private ScriptEngine engine(Dimension d, String file, String... functions) throws Exception {
        ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        engine.put("dimension", d);
        engine.eval("var riverPreset={startWidth:5,endWidth:12,maxDepth:1.10}; var bankSmoothing=true;"
                + "var shallowGraniteDetail=true,shallowGraniteSeed=1337;var progress=null;"
                + "function checkForAbort(){};function print(){};");
        try (InputStream in = getClass().getResourceAsStream("/org/pepsoft/worldpainter/scripts/rivers/" + file)) {
            assertNotNull(in);
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (file.equals("river_script.js")) {
                Matcher helper = Pattern.compile("(?ms)^function createNamedAvoidPredicate\\(.*?^}").matcher(source);
                assertTrue(helper.find());
                engine.eval(helper.group());
            }
            for (String function : functions) {
                Matcher m = Pattern.compile("(?ms)^function " + function + "\\(.*?^}").matcher(source);
                assertTrue("Missing shipped function " + function, m.find());
                engine.eval(m.group());
            }
            assertTrue("Named presets must be separated from legacy passes", source.contains("legacy carving only")
                    || source.contains("old recarve/smoothing passes afterwards"));
        }
        return engine;
    }

    private Dimension flat() {
        World2 world = new World2(DefaultPlugin.JAVA_ANVIL_1_18, -64, 320);
        TileFactory factory = TileFactoryFactory.createFlatTileFactory(0, Terrain.GRASS, -64, 320, 100, 0, false, false);
        Dimension d = new Dimension(world, "River", 0, factory, Dimension.Anchor.NORMAL_DETAIL);
        d.addTile(factory.createTile(0, 0));
        for (int y=0;y<128;y++) for (int x=0;x<128;x++) d.setTerrainAt(x,y,Terrain.GRASS);
        return d;
    }

    private Dimension wide(boolean slope) {
        Dimension d = flat();
        TileFactory factory = TileFactoryFactory.createFlatTileFactory(0, Terrain.GRASS, -64, 320, 100, 0, false, false);
        d.addTile(factory.createTile(1, 0));
        d.addTile(factory.createTile(2, 0));
        for (int y=0;y<128;y++) for (int x=0;x<384;x++) {
            d.setTerrainAt(x,y,Terrain.GRASS);
            if (slope) d.setHeightAt(x,y,104f-x/80f);
        }
        return d;
    }

    private void assertDescendingChannel(Dimension d) {
        for (int x=20;x<=308;x++) {
            assertTrue("Centre must stay wet at " + x, d.getWaterLevelAt(x,64)>d.getIntHeightAt(x,64));
            assertTrue("No deep trench at " + x, (104f-x/80f)-d.getHeightAt(x,64)<=1.855);
        }
        assertTrue(d.getWaterLevelAt(24,64)>d.getWaterLevelAt(304,64));
        assertEquals(Terrain.GRASS,d.getTerrainAt(164,40));
    }

    private void assertChannel(Dimension d) {
        int granite = 0;
        for (int x=20;x<=108;x++) {
            assertTrue(d.getWaterLevelAt(x,64)>d.getIntHeightAt(x,64));
            assertTrue(100-d.getHeightAt(x,64)<=1.85);
        }
        for (int y=48;y<80;y++) for (int x=16;x<112;x++) if(d.getTerrainAt(x,y)==Terrain.GRANITE) granite++;
        assertTrue(granite>0);
        assertEquals(Terrain.GRASS,d.getTerrainAt(64,40));
        assertEquals(100f,d.getHeightAt(64,40),0);
    }
}
