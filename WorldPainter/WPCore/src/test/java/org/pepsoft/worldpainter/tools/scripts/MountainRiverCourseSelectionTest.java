package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.HeightMapTileFactory;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.heightMaps.ConstantHeightMap;
import org.pepsoft.worldpainter.themes.SimpleTheme;

import java.awt.Point;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

/** The named automatic tool must distinguish mountain courses from easy coastal fragments. */
public class MountainRiverCourseSelectionTest {
    @Test
    public void mountainCourseSelectionStillWorksOnFlatLandWithoutExistingWater() {
        final Dimension dimension = flat();
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = mountainRouter(dimension, plan);

        final int found = findAutomatic(router);
        assertEquals(router.getSummary(), 1, found);
        assertEquals(1, plan.getAcceptedPaths());
        assertEquals("Planning must not change the world", before, fingerprint(dimension));
        assertTrue("The flat-world fallback must create a new channel", router.getLastNewTerrainLength() > 0);
        assertEquals("Flat terrain must not acquire an invented downhill grade", 0, router.getLastWaterDrop());

        final ShallowRiverCarver.Result result = plan.apply();
        assertTrue(result.changedCells() > 0);
        assertEquals(0, result.raisedCells());
        assertEquals(0, result.maximumFill(), 0);
        assertTrue(result.maximumCut() <= 1.85 + 1.0 / 256);
    }

    @Test
    public void automaticMountainCourseStartsUpstreamInsteadOfAcceptingTheEasyCoastalFragment() {
        final Dimension dimension = windingMountain();
        final long before = fingerprint(dimension);

        final ShallowRiverCarver knownMountainCourse = plan(dimension);
        final int[] xs = new int[VALLEY.length], ys = new int[VALLEY.length];
        for (int i = 0; i < VALLEY.length; i++) {
            xs[i] = VALLEY[i].x;
            ys[i] = VALLEY[i].y;
        }
        final boolean mountainFeasible = knownMountainCourse.addPath(xs, ys);
        assertTrue("The fixture must offer a physically safe mountain river: " + knownMountainCourse.getLastRejection(),
                mountainFeasible);

        // This 84-block mouth is also physically safe. Accepting it would hide
        // the failure to discover the much longer, higher mountain course.
        final ShallowRiverCarver coastalFragment = plan(dimension);
        final boolean coastFeasible = coastalFragment.addPath(new int[] {620, 704}, new int[] {96, 96});
        assertTrue("The easy coastal alternative must be feasible: " + coastalFragment.getLastRejection(), coastFeasible);
        assertEquals(before, fingerprint(dimension));

        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = mountainRouter(dimension, plan);
        final int found = findAutomatic(router);
        assertEquals(router.getSummary(), 1, found);
        assertEquals(1, plan.getAcceptedPaths());
        assertEquals("All source and outlet trials must remain read-only", before, fingerprint(dimension));
        assertTrue("New channel length must come from the upstream valley, not the short mouth: " + router.getSummary(),
                router.getLastNewTerrainLength() >= 300);
        assertTrue("The selected course must traverse substantial relief: " + router.getSummary(),
                router.getLastWaterDrop() >= 20);

        final ShallowRiverCarver.Result result = plan.apply();
        assertTrue(result.changedCells() > 0);
        assertEquals(0, result.raisedCells());
        assertEquals(0, result.maximumFill(), 0);
        assertTrue("Mountain selection must preserve the shallow excavation limit", result.maximumCut() <= 1.85 + 1.0 / 256);

        int highestNewWater = Integer.MIN_VALUE, lowestNewWater = Integer.MAX_VALUE;
        int firstNewX = Integer.MAX_VALUE, lastNewX = Integer.MIN_VALUE;
        boolean reachesSea = false;
        for (Tile tile : dimension.getTiles()) {
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final int wx = tile.getX() * 128 + x;
                if (wx >= SHORE) {
                    assertEquals(SEA - 2f, tile.getHeight(x, y), 0);
                    assertEquals(SEA, tile.getWaterLevel(x, y));
                } else if (tile.getWaterLevel(x, y) > Math.round(tile.getHeight(x, y))) {
                    highestNewWater = Math.max(highestNewWater, tile.getWaterLevel(x, y));
                    lowestNewWater = Math.min(lowestNewWater, tile.getWaterLevel(x, y));
                    firstNewX = Math.min(firstNewX, wx);
                    lastNewX = Math.max(lastNewX, wx);
                    if (wx == SHORE - 1) reachesSea = true;
                }
            }
        }
        assertTrue("The actual upstream water must originate well above this world's coast", highestNewWater >= SEA + 30);
        assertTrue("The applied river must descend through the valley", highestNewWater - lowestNewWater >= 20);
        assertTrue("A coastal patch cannot satisfy the required upstream extent", lastNewX - firstNewX >= 300);
        assertTrue("The new channel must join the existing sea", reachesSea);
    }

    @Test
    public void ineligibleHighBoundaryRimCannotExcludeEveryInteriorSource() {
        final Dimension dimension = flat();
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            final int edge = Math.min(Math.min(x, 127 - x), Math.min(y, 127 - y));
            if (edge < 10) {
                dimension.setHeightAt(x, y, 140f);
            } else if (x >= 96 && x < 118 && y >= 24 && y < 104) {
                dimension.setHeightAt(x, y, 98f);
                dimension.setWaterLevelAt(x, y, 100);
            }
        }
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = mountainRouter(dimension, plan);

        final int found = findAutomatic(router);
        assertEquals("Every elevated rim sample is outside the allowed source inset: " + router.getSummary(), 1, found);
        assertEquals("The eligible interior is flat and must retain its normal fallback", 0, router.getLastWaterDrop());
        assertEquals(before, fingerprint(dimension));
        assertTrue(plan.apply().changedCells() > 0);
    }

    @Test
    public void firstWaterOnADiagonalStopsTheNewTerrainLength() throws ReflectiveOperationException {
        final Dimension dimension = flat();
        dimension.setHeightAt(65, 65, 98f);
        dimension.setWaterLevelAt(65, 65, 100);
        final long before = fingerprint(dimension);
        final ShallowRiverRouter router = mountainRouter(dimension, plan(dimension));

        // On the first (6,3) segment, whole-block samples round to (65,64),
        // (66,65), ... and miss (65,65). The carver's half-block samples see it.
        final Class<?> pointType = Class.forName(ShallowRiverRouter.class.getName() + "$Point");
        final Constructor<?> point = pointType.getDeclaredConstructor(int.class, int.class);
        point.setAccessible(true);
        final List<?> path = List.of(point.newInstance(64, 64), point.newInstance(70, 67), point.newInstance(100, 82));
        final Object geometry = invokePrivate(router, "courseGeometry", new Class<?>[] {List.class}, path);

        assertEquals(65, recordNumber(geometry, "downstreamX").intValue());
        assertEquals(65, recordNumber(geometry, "downstreamY").intValue());
        assertTrue("The later dry segments must not inflate the course before its first existing water",
                recordNumber(geometry, "newTerrainLength").doubleValue() < 2);
        assertEquals(before, fingerprint(dimension));
    }

    @Test
    public void alreadyAcceptedSharedPathDoesNotCountAsNewTerrain() throws ReflectiveOperationException {
        final Dimension dimension = flat();
        final long before = fingerprint(dimension);
        final ShallowRiverCarver sharedPlan = plan(dimension);
        final boolean accepted = sharedPlan.addPath(new int[] {64, 112}, new int[] {64, 64});
        assertTrue("The prior path must be feasible without applying it: " + sharedPlan.getLastRejection(), accepted);
        assertTrue(sharedPlan.plannedWetAt(64, 64));
        assertFalse(sharedPlan.plannedWetAt(32, 64));
        assertEquals(MIN_HEIGHT, dimension.getWaterLevelAt(64, 64));
        final ShallowRiverRouter router = mountainRouter(dimension, sharedPlan);

        final Class<?> pointType = Class.forName(ShallowRiverRouter.class.getName() + "$Point");
        final Constructor<?> point = pointType.getDeclaredConstructor(int.class, int.class);
        point.setAccessible(true);
        final List<?> joiningPath = List.of(point.newInstance(32, 64), point.newInstance(112, 64));
        final Object joining = invokePrivate(router, "courseGeometry", new Class<?>[] {List.class}, joiningPath);
        final double newLength = recordNumber(joining, "newTerrainLength").doubleValue();
        assertTrue("Only the approach to the prior path creates new river terrain", newLength > 0 && newLength <= 32);
        assertTrue(sharedPlan.plannedWetAt(recordNumber(joining, "downstreamX").intValue(),
                recordNumber(joining, "downstreamY").intValue()));

        final List<?> repeatedPath = List.of(point.newInstance(64, 64), point.newInstance(112, 64));
        final Object repeated = invokePrivate(router, "courseGeometry", new Class<?>[] {List.class}, repeatedPath);
        assertEquals("Starting in an accepted path cannot claim any new terrain", 0,
                recordNumber(repeated, "newTerrainLength").doubleValue(), 0);
        assertEquals(1, sharedPlan.getAcceptedPaths());
        assertEquals("Neither the query nor the measurements may apply the shared plan", before, fingerprint(dimension));
    }

    @Test
    public void upstreamLakeMayRetainItsOwnPlaneBeforeALowerSeaOutlet() throws ReflectiveOperationException {
        final Dimension dimension = descendingLakes();
        final long before = fingerprint(dimension);
        final ShallowRiverCarver knownCourse = plan(dimension);
        final boolean feasible = knownCourse.addPath(new int[] {32, 484}, new int[] {64, 64});
        assertTrue("The final carver must prove that the lake-to-sea course is safe: " + knownCourse.getLastRejection(), feasible);

        final ShallowRiverRouter router = mountainRouter(dimension, plan(dimension));
        assertNotNull("The upstream lake stays at Y100 even though the final sea is Y62",
                routeSegment(router, 108, 64, 148, 64, 101, SEA));
        assertEquals(before, fingerprint(dimension));

        final ShallowRiverRouter lowerArrival = mountainRouter(dimension, plan(dimension));
        assertNull("An arrival below the existing lake must still be rejected instead of raising its water",
                routeSegment(lowerArrival, 112, 64, 116, 64, 99, SEA));
        assertEquals(before, fingerprint(dimension));
    }

    @Test
    public void laterRoundsBroadenHeadwaterSourcesWithoutWeakeningTheCourseRequirements() throws ReflectiveOperationException {
        final Dimension dimension = windingMountain();
        final long before = fingerprint(dimension);
        final ShallowRiverRouter router = mountainRouter(dimension, plan(dimension));
        invokePrivate(router, "initialise", new Class<?>[0]);
        final Object profile = privateField(privateField(router, "grid"), "course");
        assertNotNull("The fixture must activate the mountain-course policy", profile);

        final double base = recordNumber(profile, "base").doubleValue();
        final double minimumDrop = recordNumber(profile, "minimumDrop").doubleValue();
        final double minimumLength = recordNumber(profile, "minimumLength").doubleValue();
        final double minimumSpan = minimumLength * 0.6;
        final double headwater = recordNumber(profile, "sourceFloor").doubleValue();
        final double shoulder = recordNumber(profile, "shoulderSourceFloor").doubleValue();
        final double upperValley = recordNumber(profile, "finalSourceFloor").doubleValue();
        final double[] expected = {headwater, headwater, shoulder, shoulder, upperValley, upperValley};
        final double[] actual = new double[6];
        final Field round = ShallowRiverRouter.class.getDeclaredField("automaticRound");
        round.setAccessible(true);
        for (int i = 0; i < actual.length; i++) {
            round.setInt(router, i + 1);
            actual[i] = ((Number) invokePrivate(router, "effectiveSourceFloor", new Class<?>[0])).doubleValue();
            assertEquals("Each source band receives two rounds", Math.max(base + minimumDrop, expected[i]), actual[i], 0);
            assertTrue("Later sources must retain the original descent above the valley base", actual[i] >= base + minimumDrop);
            if (i > 0) assertTrue("Source exploration must broaden monotonically", actual[i] <= actual[i - 1]);
            assertEquals("Source retries must not reduce the required water drop", minimumDrop,
                    recordNumber(profile, "minimumDrop").doubleValue(), 0);
            assertEquals("Source retries must not turn a short coastal fragment into a valid new course", minimumLength,
                    recordNumber(profile, "minimumLength").doubleValue(), 0);
            assertEquals("The minimum upstream extent remains fixed", minimumSpan,
                    recordNumber(profile, "minimumLength").doubleValue() * 0.6, 0);
        }
        assertTrue("The third round must explore more than the highest headwaters", actual[2] < actual[1]);
        assertTrue("The fifth round must make a further bounded expansion", actual[4] < actual[3]);
        assertEquals(before, fingerprint(dimension));
    }

    private static ShallowRiverRouter mountainRouter(Dimension dimension, ShallowRiverCarver plan) {
        final ShallowRiverRouter router = new ShallowRiverRouter(dimension, plan, 5, 12, 1.10, true, SEED, null, null);
        router.enableMountainCourseSelection();
        return router;
    }

    private static int findAutomatic(ShallowRiverRouter router) {
        int found = 0;
        for (int round = 0; round < 3 && found == 0; round++) {
            if (round > 0 && !router.canContinueAutomaticSearch()) break;
            found += router.findAutomatic(1);
        }
        return found;
    }

    private static ShallowRiverCarver plan(Dimension dimension) {
        final ShallowRiverCarver plan = new ShallowRiverCarver(dimension, 5, 12, 1.10, true, true, SEED, null);
        plan.enableTerrainPreservation();
        return plan;
    }

    private static Dimension flat() {
        final HeightMapTileFactory factory = factory(100);
        final Dimension dimension = dimension("Flat mountain-policy fallback", factory);
        final Tile tile = factory.createTile(0, 0);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            tile.setHeight(x, y, 100f);
            tile.setWaterLevel(x, y, MIN_HEIGHT);
            tile.setTerrain(x, y, Terrain.GRASS);
        }
        dimension.addTile(tile);
        return dimension;
    }

    private static Dimension windingMountain() {
        final HeightMapTileFactory factory = factory(SEA + 48);
        final Dimension dimension = dimension("Mountain and easy coast", factory);
        final double[] starts = new double[VALLEY.length];
        for (int i = 1; i < VALLEY.length; i++) starts[i] = starts[i - 1] + VALLEY[i].distance(VALLEY[i - 1]);
        final double lastDryAlong = starts[starts.length - 1] - (VALLEY[VALLEY.length - 1].x - SHORE + 1);
        for (int ty = 0; ty < 3; ty++) for (int tx = 0; tx < 6; tx++) {
            final Tile tile = factory.createTile(tx, ty);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final int wx = tx * 128 + x, wy = ty * 128 + y;
                final boolean ocean = wx >= SHORE;
                final Projection location = project(starts, wx, wy);
                final double height = SEA + 0.125 + 48 * Math.max(0, (lastDryAlong - location.along) / lastDryAlong)
                        + Math.max(0, location.distance - 10) * 0.6;
                tile.setHeight(x, y, ocean ? SEA - 2f : (float) height);
                tile.setWaterLevel(x, y, ocean ? SEA : MIN_HEIGHT);
                tile.setTerrain(x, y, ocean ? Terrain.SAND : Terrain.GRASS);
            }
            dimension.addTile(tile);
        }
        return dimension;
    }

    private static Dimension descendingLakes() {
        final HeightMapTileFactory factory = factory(110);
        final Dimension dimension = dimension("Upstream lake and lower sea", factory);
        for (int tx = 0; tx < 4; tx++) {
            final Tile tile = factory.createTile(tx, 0);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final int wx = tx * 128 + x;
                final boolean lake = wx >= 112 && wx < 144, sea = wx >= 480;
                final double height = wx < 112 ? 110.125 + (32 - wx) * 0.125
                        : 100.125 - (wx - 144) * (38.0 / 336);
                tile.setHeight(x, y, lake ? 98f : sea ? SEA - 2f : (float) height);
                tile.setWaterLevel(x, y, lake ? 100 : sea ? SEA : MIN_HEIGHT);
                tile.setTerrain(x, y, lake || sea ? Terrain.SAND : Terrain.GRASS);
            }
            dimension.addTile(tile);
        }
        return dimension;
    }

    private static Object routeSegment(ShallowRiverRouter router, double x1, double y1, double x2, double y2,
                                       int water, int outletWater) throws ReflectiveOperationException {
        return invokePrivate(router, "segment", new Class<?>[] {double.class, double.class, double.class, double.class,
                int.class, Integer.class, double.class, double.class}, x1, y1, x2, y2, water, outletWater, 2.5, 2.5);
    }

    private static Number recordNumber(Object record, String field) throws ReflectiveOperationException {
        return (Number) invokePrivate(record, field, new Class<?>[0]);
    }

    private static Object privateField(Object target, String name) throws ReflectiveOperationException {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invokePrivate(Object target, String name, Class<?>[] types, Object... arguments)
            throws ReflectiveOperationException {
        final Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static HeightMapTileFactory factory(int height) {
        return new HeightMapTileFactory(SEED, new ConstantHeightMap(height), MIN_HEIGHT, MAX_HEIGHT, false,
                SimpleTheme.createSingleTerrain(Terrain.GRASS, MIN_HEIGHT, MAX_HEIGHT, MIN_HEIGHT));
    }

    private static Dimension dimension(String name, HeightMapTileFactory factory) {
        return new Dimension(new World2(TestData.PLATFORM, MIN_HEIGHT, MAX_HEIGHT), name, SEED, factory,
                Dimension.Anchor.NORMAL_DETAIL);
    }

    private static Projection project(double[] starts, int x, int y) {
        double nearestSquared = Double.POSITIVE_INFINITY, along = 0;
        for (int i = 0; i < VALLEY.length - 1; i++) {
            final double dx = VALLEY[i + 1].x - VALLEY[i].x, dy = VALLEY[i + 1].y - VALLEY[i].y;
            final double t = Math.max(0, Math.min(1, ((x - VALLEY[i].x) * dx + (y - VALLEY[i].y) * dy) / (dx * dx + dy * dy)));
            final double ex = x - VALLEY[i].x - t * dx, ey = y - VALLEY[i].y - t * dy;
            final double squared = ex * ex + ey * ey;
            if (squared < nearestSquared) {
                nearestSquared = squared;
                along = starts[i] + t * Math.hypot(dx, dy);
            }
        }
        return new Projection(along, Math.sqrt(nearestSquared));
    }

    private static long fingerprint(Dimension dimension) {
        long result = 1;
        for (Tile tile : dimension.getTiles()) {
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                result = 31 * result + Float.floatToIntBits(tile.getHeight(x, y));
                result = 31 * result + tile.getWaterLevel(x, y);
                result = 31 * result + tile.getTerrain(x, y).ordinal();
            }
        }
        return result;
    }

    private record Projection(double along, double distance) { }
    private static final long SEED = 733L;
    private static final int MIN_HEIGHT = -64, MAX_HEIGHT = 320, SEA = 62, SHORE = 700;
    private static final Point[] VALLEY = {new Point(32, 96), new Point(160, 96), new Point(300, 176),
            new Point(440, 176), new Point(580, 96), new Point(704, 96)};
}
