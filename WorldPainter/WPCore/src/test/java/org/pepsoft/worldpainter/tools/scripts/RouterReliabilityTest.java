package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.ReadOnly;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Constructible, long river routes, independent of any user's world or one coast.
 * Every positive fixture first proves a safe path with the unchanged carver.
 * All applications below modify newly generated in-memory test dimensions only.
 */
public class RouterReliabilityTest {
    @Test public void flatWorldWithoutAnySeaStillFindsALongTerrainPreservingOutlet() {
        final Dimension dimension = rectangle(0, 0, 3, 3);
        fill(dimension, (x, y) -> 100.375, (x, y) -> false, 0);
        final Fixture fixture = new Fixture("flat/no sea", dimension, new Point(192, 192), new Point(360, 192), 0);
        routeAndVerify(fixture, false, 0, 140);
    }

    @Test public void shortLowlandValleyLosesMoreThanTenBlocksBeforeReachingSeaZero() {
        routeAndVerify(straightValley("12Y / 256 lowland", 0, 0, 2, 12, 0), true, 10, 170);
    }

    @Test public void rollingValleyLosesMoreThanThirtyBlocksAndReachesTheExistingLakeAt62() {
        routeAndVerify(straightValley("36Y / 512 lake", 0, 0, 4, 36, 62), true, 30, 400);
    }

    @Test public void mountainValleyTraversesMoreThanNineHundredBlocksAndOneHundredY() {
        routeAndVerify(straightValley("110Y / 1024 mountain", 0, 0, 8, 110, 0), true, 100, 900);
    }

    @Test public void obliqueValleyIsNotRejectedBecauseCoarseGridTurnsRotateItsCrossSections() {
        final Dimension dimension = rectangle(0, 0, 4, 3);
        final Point source = new Point(20, 64), outlet = new Point(460, 210);
        final double dx = outlet.x - source.x, dy = outlet.y - source.y;
        final double length = Math.hypot(dx, dy);
        fill(dimension, (x, y) -> {
            final double along = ((x - source.x) * dx + (y - source.y) * dy) / length;
            final double across = Math.abs((x - source.x) * dy - (y - source.y) * dx) / length;
            return 62.375 + 40 * Math.max(0, (length - 2 - along) / (length - 2)) + valleySide(across);
        }, (x, y) -> ((x - source.x) * dx + (y - source.y) * dy) / length >= length - 2, 62);
        routeAndVerify(new Fixture("oblique 1:3 valley", dimension, source, outlet, 62), true, 30, 400);
    }

    @Test public void negativeCoordinatesAndADistantDisconnectedTileDoNotHideTheContinuousValley() {
        final Fixture fixture = straightValley("negative sparse valley", -3, -2, 4, 36, 62);
        final Tile distant = fixture.dimension.getTileFactory().createTile(5, 3);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            distant.setHeight(x, y, 140);
            distant.setWaterLevel(x, y, 0);
            distant.setTerrain(x, y, Terrain.GRASS);
            distant.setBitLayerValue(Frost.INSTANCE, x, y, false);
        }
        fixture.dimension.addTile(distant);
        assertFalse("Fixture must contain real missing tiles", fixture.dimension.isTilePresent(1, -2));
        routeAndVerify(fixture, true, 30, 400);
    }

    @Test public void protectedBarrierAndProtectedDryExitsStillRejectWithoutChangingTheWorld() {
        final Fixture fixture = straightValley("protected outlet", 0, 0, 2, 24, 0);
        assertKnownSafePath(fixture);
        for (int y = 0; y < 128; y += 16) for (int x = 0; x < 256; x += 16) {
            if (x == 0 || x >= 208 || y == 0 || y == 112) {
                fixture.dimension.setBitLayerValueAt(ReadOnly.INSTANCE, x, y, true);
            }
        }
        final Snapshot before = new Snapshot(fixture.dimension);
        final ShallowRiverCarver plan = plan(fixture.dimension);
        final ShallowRiverRouter router = router(fixture.dimension, plan);
        final boolean found = router.findFromSource(fixture.source.x, fixture.source.y);
        assertFalse("No protection bypass is permitted: " + router.getSummary(), found);
        assertEquals(0, plan.getAcceptedPaths());
        assertEquals(0, plan.apply().changedCells());
        before.assertUnchanged(fixture.dimension);
    }

    private static void routeAndVerify(Fixture fixture, boolean reachesExistingWater, int minimumDrop, double minimumReach) {
        assertKnownSafePath(fixture);
        final Dimension dimension = fixture.dimension;
        final Snapshot before = new Snapshot(dimension);
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan);
        final long start = System.nanoTime();
        final boolean found = router.findFromSource(fixture.source.x, fixture.source.y);
        System.out.printf(java.util.Locale.ROOT, "[router-reliability] %s, %.3fs: %s%n",
                fixture.name, (System.nanoTime() - start) / 1_000_000_000.0, router.getSummary());
        assertTrue("Known-safe route was missed in " + fixture.name + ": " + router.getSummary(), found);
        before.assertUnchanged(dimension);
        assertEquals(1, plan.getAcceptedPaths());

        final ShallowRiverCarver.Result result = plan.apply();
        assertTrue(result.changedCells() > 0);
        assertEquals("No filling surrounding terrain", 0, result.raisedCells());
        assertEquals(0, result.maximumFill(), 0);
        assertTrue("Never enlarge the 1.85-block excavation budget", result.maximumCut() <= 1.85 + 1.0 / 256);
        assertTrue("Manual source cannot be silently moved", wet(dimension, fixture.source.x, fixture.source.y));

        // Follow actual water connectivity, not a success string or a tiny route
        // which happened to spawn near the ocean while the mountain was skipped.
        final Connectivity connection = connectedWater(dimension, before, fixture.source);
        assertTrue("A short near-source exit is not a full valley river: " + fixture.name,
                connection.furthestNewWater >= minimumReach);
        if (reachesExistingWater) {
            assertTrue("The connected source river must reach the unchanged receiving water", connection.reachesExisting);
            assertTrue("Must traverse the requested height gradient, not just the coast",
                    dimension.getWaterLevelAt(fixture.source.x, fixture.source.y) - connection.lowestNewWater >= minimumDrop);
        }
        for (SavedTile tile : before.tiles) {
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final int wx = tile.x * 128 + x, wy = tile.y * 128 + y, index = x + y * 128;
                if (tile.water[index] > Math.round(tile.height[index]) || !wet(dimension, wx, wy)) {
                    tile.assertCellUnchanged(dimension, x, y);
                } else {
                    assertTrue("No new channel fill", dimension.getHeightAt(wx, wy) <= tile.height[index]);
                    assertTrue("Local cut budget", tile.height[index] - dimension.getHeightAt(wx, wy) <= 1.85 + 1.0 / 256);
                }
            }
        }
    }

    private static void assertKnownSafePath(Fixture fixture) {
        final Snapshot before = new Snapshot(fixture.dimension);
        final ShallowRiverCarver known = plan(fixture.dimension);
        final boolean feasible = known.addPath(new int[] { fixture.source.x, fixture.outlet.x },
                new int[] { fixture.source.y, fixture.outlet.y });
        assertTrue("Fixture is not a constructible preserving river (not a router failure): "
                + fixture.name + ": " + known.getLastRejection(), feasible);
        before.assertUnchanged(fixture.dimension);
    }

    private static Fixture straightValley(String name, int tileX, int tileY, int widthTiles, double drop, int sea) {
        final Dimension dimension = rectangle(tileX, tileY, widthTiles, 1);
        final int originX = tileX * 128, originY = tileY * 128;
        final int sourceX = originX + 20, centreY = originY + 64, shoreX = originX + widthTiles * 128 - 28;
        fill(dimension, (x, y) -> sea + 0.375
                        + drop * Math.max(0, (shoreX - 1.0 - x) / (shoreX - 1.0 - sourceX))
                        + valleySide(Math.abs(y - centreY)),
                (x, y) -> x >= shoreX, sea);
        return new Fixture(name, dimension, new Point(sourceX, centreY), new Point(shoreX + 2, centreY), sea);
    }

    /** Flat-bottomed valley, with natural surrounding hills, never a pre-dug channel. */
    private static double valleySide(double distance) { return Math.min(100, Math.max(0, distance - 8) * 0.6); }

    private static Dimension rectangle(int firstTileX, int firstTileY, int columns, int rows) {
        final TileFactory factory = TestData.createTileFactory(100);
        final Dimension dimension = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "Router reliability (synthetic)", 733, factory, Dimension.Anchor.NORMAL_DETAIL);
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
            dimension.addTile(factory.createTile(firstTileX + x, firstTileY + y));
        }
        return dimension;
    }

    private static void fill(Dimension dimension, Height height, Water water, int sea) {
        for (Tile tile : dimension.getTiles()) {
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final int wx = tile.getX() * 128 + x, wy = tile.getY() * 128 + y;
                final boolean wet = water.at(wx, wy);
                tile.setHeight(x, y, wet ? sea - 2f : (float) height.at(wx, wy));
                tile.setWaterLevel(x, y, wet ? sea : 0);
                tile.setTerrain(x, y, wet ? Terrain.SAND : Terrain.GRASS);
                tile.setLayerValue(Biome.INSTANCE, x, y, wet ? 0 : 1);
                tile.setBitLayerValue(Frost.INSTANCE, x, y, false);
            }
        }
    }

    private static ShallowRiverCarver plan(Dimension dimension) {
        final ShallowRiverCarver result = new ShallowRiverCarver(dimension, 5, 12, 1.10, true, true, 733, null);
        result.enableTerrainPreservation();
        return result;
    }

    private static ShallowRiverRouter router(Dimension dimension, ShallowRiverCarver plan) {
        return new ShallowRiverRouter(dimension, plan, 5, 12, 1.10, true, 733, null, null);
    }

    private static Connectivity connectedWater(Dimension dimension, Snapshot before, Point source) {
        final ArrayDeque<Point> queue = new ArrayDeque<>();
        final Set<Long> visited = new HashSet<>();
        queue.add(source);
        visited.add(key(source.x, source.y));
        boolean reachesExisting = false;
        double furthest = 0;
        int lowest = Integer.MAX_VALUE;
        while (!queue.isEmpty()) {
            final Point p = queue.remove();
            if (before.wasWet(p.x, p.y)) {
                reachesExisting = true;
                continue; // Receiving sea is preserved; don't traverse its whole area.
            }
            furthest = Math.max(furthest, p.distance(source));
            lowest = Math.min(lowest, dimension.getWaterLevelAt(p.x, p.y));
            for (int[] offset : NEIGHBOURS) {
                final int x = p.x + offset[0], y = p.y + offset[1];
                if (dimension.isTilePresent(x >> 7, y >> 7) && wet(dimension, x, y) && visited.add(key(x, y))) {
                    queue.add(new Point(x, y));
                }
            }
        }
        return new Connectivity(reachesExisting, furthest, lowest);
    }

    private static boolean wet(Dimension d, int x, int y) { return d.getWaterLevelAt(x, y) > d.getIntHeightAt(x, y); }
    private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }
    private static final int[][] NEIGHBOURS = {{1,0}, {-1,0}, {0,1}, {0,-1}};
    private interface Height { double at(int x, int y); }
    private interface Water { boolean at(int x, int y); }
    private record Fixture(String name, Dimension dimension, Point source, Point outlet, int sea) { }
    private record Connectivity(boolean reachesExisting, double furthestNewWater, int lowestNewWater) { }

    private static final class Snapshot {
        private final List<SavedTile> tiles = new ArrayList<>();
        private final Map<Long, SavedTile> byCoordinate = new HashMap<>();
        Snapshot(Dimension dimension) {
            for (Tile tile : dimension.getTiles()) {
                final SavedTile saved = new SavedTile(tile);
                tiles.add(saved);
                byCoordinate.put(key(saved.x, saved.y), saved);
            }
            tiles.sort(Comparator.comparingInt((SavedTile t) -> t.x).thenComparingInt(t -> t.y));
        }
        void assertUnchanged(Dimension dimension) {
            assertEquals("Planning cannot add/remove tiles", tiles.size(), dimension.getTiles().size());
            for (SavedTile tile : tiles) for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                tile.assertCellUnchanged(dimension, x, y);
            }
        }
        boolean wasWet(int x, int y) {
            final SavedTile tile = byCoordinate.get(key(x >> 7, y >> 7));
            if (tile == null) return false;
            final int index = (x & 127) + (y & 127) * 128;
            return tile.water[index] > Math.round(tile.height[index]);
        }
    }

    private static final class SavedTile {
        private final int x, y;
        private final float[] height = new float[16384];
        private final int[] water = new int[16384], biomes = new int[16384];
        private final Terrain[] terrain = new Terrain[16384];
        private final boolean[] frost = new boolean[16384], readOnly = new boolean[16384];
        SavedTile(Tile tile) {
            x = tile.getX(); y = tile.getY();
            for (int dy = 0; dy < 128; dy++) for (int dx = 0; dx < 128; dx++) {
                final int i = dx + dy * 128;
                height[i] = tile.getHeight(dx, dy);
                water[i] = tile.getWaterLevel(dx, dy);
                terrain[i] = tile.getTerrain(dx, dy);
                biomes[i] = tile.getLayerValue(Biome.INSTANCE, dx, dy);
                frost[i] = tile.getBitLayerValue(Frost.INSTANCE, dx, dy);
                readOnly[i] = tile.getBitLayerValue(ReadOnly.INSTANCE, dx, dy);
            }
        }
        void assertCellUnchanged(Dimension dimension, int dx, int dy) {
            final int wx = x * 128 + dx, wy = y * 128 + dy, i = dx + dy * 128;
            final String cell = wx + "," + wy;
            assertEquals("height " + cell, height[i], dimension.getHeightAt(wx, wy), 0);
            assertEquals("water " + cell, water[i], dimension.getWaterLevelAt(wx, wy));
            assertEquals("terrain " + cell, terrain[i], dimension.getTerrainAt(wx, wy));
            assertEquals("biome " + cell, biomes[i], dimension.getLayerValueAt(Biome.INSTANCE, wx, wy));
            assertEquals("Frost " + cell, frost[i], dimension.getBitLayerValueAt(Frost.INSTANCE, wx, wy));
            assertEquals("ReadOnly " + cell, readOnly[i], dimension.getBitLayerValueAt(ReadOnly.INSTANCE, wx, wy));
        }
    }
}
