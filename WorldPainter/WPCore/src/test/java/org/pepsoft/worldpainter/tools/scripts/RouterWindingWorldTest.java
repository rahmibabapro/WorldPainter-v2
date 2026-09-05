package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.HeightMapTileFactory;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.heightMaps.ConstantHeightMap;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.themes.SimpleTheme;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/** General-world routing regressions; only generated, unsaved in-memory worlds are used. */
public class RouterWindingWorldTest {
    @Test public void broadSCurveRoutesAroundTheNaturalRidgeBlockingTheStraightShortcut() {
        final Fixture fixture = winding();
        proveKnownPath(fixture);
        final ShallowRiverCarver shortcut = plan(fixture.dimension);
        final boolean direct = shortcut.addPath(new int[] {fixture.source().x, fixture.outlet().x},
                new int[] {fixture.source().y, fixture.outlet().y});
        assertFalse("The straight shortcut must hit a real hillside, not an invented routing obstacle", direct);
        routeAndVerify(fixture, false, 600, 40);
    }

    @Test public void automaticMountainRiverTraversesMeaningfulReliefInsteadOfStoppingAtATinyCoastPatch() {
        final Fixture fixture = winding();
        // The land is a finite protected working area; the unprotected sea is
        // inside it. A short arbitrary dry-map-edge exit is not an outlet here.
        for (int y = 0; y < 384; y += 16) for (int x = 0; x < 768; x += 16) {
            if (x == 0 || x == 752 || y == 0 || y == 368) {
                fixture.dimension.setBitLayerValueAt(ReadOnly.INSTANCE, x, y, true);
            }
        }
        routeAndVerify(fixture, true, 300, 20);
    }

    @Test public void eightKByOneTileStripKeepsItsRealEightKilometreRouteWithinShallowBudgets() {
        final Fixture fixture = straight("8192x128", 64, -64, 320, 0, 160);
        assertEquals(64, fixture.dimension.getTiles().size());
        routeAndVerify(fixture, false, 7900, 150);
        assertEquals("Never expand an 8K strip into an 8K square", 64, fixture.dimension.getTiles().size());
    }

    @Test public void negativeWaterLevelInATallWorldUsesItsActualMinHeightAndLakeLevel() {
        final Fixture fixture = straight("negative-Y lake", 4, -512, 1536, -128, 36);
        assertTrue(fixture.dimension.getHeightAt(fixture.source().x, fixture.source().y) < 0);
        routeAndVerify(fixture, false, 400, 30);
    }

    @Test public void highAltitudeWorldDoesNotAssumeTheOld256BlockCeiling() {
        final Fixture fixture = straight("Y1050-1160 tall valley", 4, -512, 1536, 1050, 110);
        assertTrue(fixture.dimension.getHeightAt(fixture.source().x, fixture.source().y) > 1000);
        routeAndVerify(fixture, false, 400, 100);
    }

    private static Fixture winding() {
        final Point[] path = {new Point(32, 96), new Point(160, 96), new Point(300, 176),
                new Point(440, 176), new Point(580, 96), new Point(704, 96)};
        return create("S-valley", 6, 3, -64, 320, 62, 48, 700, path);
    }

    private static Fixture straight(String name, int columns, int minHeight, int maxHeight, int sea, double drop) {
        final int shore = columns * 128 - 32;
        return create(name, columns, 1, minHeight, maxHeight, sea, drop, shore,
                new Point[] {new Point(32, 64), new Point(shore + 2, 64)});
    }

    private static Fixture create(String name, int columns, int rows, int minHeight, int maxHeight,
                                  int sea, double drop, int shore, Point[] path) {
        final HeightMapTileFactory factory = new HeightMapTileFactory(733,
                new ConstantHeightMap(sea + drop), minHeight, maxHeight, false,
                SimpleTheme.createSingleTerrain(Terrain.GRASS, minHeight, maxHeight, minHeight));
        final Dimension dimension = new Dimension(new World2(TestData.PLATFORM, minHeight, maxHeight),
                name + " (synthetic)", 733, factory, Dimension.Anchor.NORMAL_DETAIL);
        final double[] starts = new double[path.length];
        for (int i = 1; i < path.length; i++) starts[i] = starts[i - 1] + path[i].distance(path[i - 1]);
        final double lastDryAlong = starts[starts.length - 1] - (path[path.length - 1].x - shore + 1);
        for (int ty = 0; ty < rows; ty++) for (int tx = 0; tx < columns; tx++) {
            final Tile tile = factory.createTile(tx, ty);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final int wx = tx * 128 + x, wy = ty * 128 + y;
                final boolean ocean = wx >= shore;
                final Projection location = projection(path, starts, wx, wy);
                final double h = sea + 0.125 + drop * Math.max(0, (lastDryAlong - location.along) / lastDryAlong)
                        + Math.max(0, location.distance - 10) * 0.6;
                tile.setHeight(x, y, ocean ? sea - 2f : (float) h);
                tile.setWaterLevel(x, y, ocean ? sea : minHeight);
                tile.setTerrain(x, y, ocean ? Terrain.SAND : Terrain.GRASS);
                tile.setLayerValue(Biome.INSTANCE, x, y, 1);
                tile.setBitLayerValue(Frost.INSTANCE, x, y, false);
            }
            dimension.addTile(tile);
        }
        return new Fixture(name, dimension, path);
    }

    private static Projection projection(Point[] path, double[] starts, int x, int y) {
        double bestSquared = Double.POSITIVE_INFINITY, along = 0;
        for (int i = 0; i < path.length - 1; i++) {
            final double dx = path[i + 1].x - path[i].x, dy = path[i + 1].y - path[i].y;
            final double t = Math.max(0, Math.min(1, ((x - path[i].x) * dx + (y - path[i].y) * dy) / (dx * dx + dy * dy)));
            final double ex = x - path[i].x - t * dx, ey = y - path[i].y - t * dy;
            final double squared = ex * ex + ey * ey;
            if (squared < bestSquared) {
                bestSquared = squared;
                along = starts[i] + t * Math.hypot(dx, dy);
            }
        }
        return new Projection(along, Math.sqrt(bestSquared));
    }

    private static void proveKnownPath(Fixture fixture) {
        final Snapshot before = new Snapshot(fixture.dimension);
        final ShallowRiverCarver known = plan(fixture.dimension);
        final int[] xs = new int[fixture.path.length], ys = new int[fixture.path.length];
        for (int i = 0; i < xs.length; i++) { xs[i] = fixture.path[i].x; ys[i] = fixture.path[i].y; }
        final boolean feasible = known.addPath(xs, ys);
        assertTrue("Fixture must have a physically safe route before testing search: " + fixture.name
                + "; " + known.getLastRejection(), feasible);
        before.verifyAll(fixture.dimension);
    }

    private static void routeAndVerify(Fixture fixture, boolean automatic, double minimumSpan, int minimumDrop) {
        proveKnownPath(fixture);
        final Snapshot before = new Snapshot(fixture.dimension);
        final ShallowRiverCarver plan = plan(fixture.dimension);
        final ShallowRiverRouter router = new ShallowRiverRouter(fixture.dimension, plan, 5, 12, 1.10, true, 733, null, null);
        if (automatic) router.enableMountainCourseSelection();
        final long start = System.nanoTime();
        final boolean found = automatic ? router.findAutomatic(1) == 1
                : router.findFromSource(fixture.source().x, fixture.source().y);
        System.out.printf(Locale.ROOT, "[router-winding] %s automatic=%s %.3fs: %s%n", fixture.name, automatic,
                (System.nanoTime() - start) / 1_000_000_000.0, router.getSummary());
        assertTrue("Search missed a proven safe route: " + fixture.name + "; " + router.getSummary(), found);
        before.verifyAll(fixture.dimension);
        assertEquals(1, plan.getAcceptedPaths());
        final ShallowRiverCarver.Result result = plan.apply();
        assertEquals(0, result.raisedCells());
        assertEquals(0, result.maximumFill(), 0);
        assertTrue("No wider excavation budget", result.maximumCut() <= 1.85 + 1.0 / 256);

        Point upstream = null;
        int highest = Integer.MIN_VALUE, lowest = Integer.MAX_VALUE, minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        long newWet = 0;
        for (SavedTile tile : before.tiles) {
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final int wx = tile.x * 128 + x, wy = tile.y * 128 + y, index = x + y * 128;
                final boolean wet = wet(fixture.dimension, wx, wy);
                if (tile.wasWet(index) || !wet) tile.verifyCell(fixture.dimension, x, y);
                else {
                    final float h = fixture.dimension.getHeightAt(wx, wy);
                    assertTrue(h <= tile.heights[index]);
                    assertTrue(tile.heights[index] - h <= 1.85 + 1.0 / 256);
                    final int level = fixture.dimension.getWaterLevelAt(wx, wy);
                    if (level > highest) { highest = level; upstream = new Point(wx, wy); }
                    lowest = Math.min(lowest, level);
                    minX = Math.min(minX, wx); maxX = Math.max(maxX, wx);
                    newWet++;
                }
            }
        }
        assertTrue("Must create actual channel cells", newWet > 0);
        assertTrue("A tiny coastal patch is not a meaningful mountain river: " + router.getSummary(), maxX - minX >= minimumSpan);
        assertTrue("The river must cross the proven elevation range", highest - lowest >= minimumDrop);
        if (!automatic) {
            assertTrue("The requested source cannot be moved", wet(fixture.dimension, fixture.source().x, fixture.source().y));
            upstream = fixture.source();
        }
        assertTrue("The high connected river must reach existing water", connectsToSea(fixture.dimension, before, upstream));
        System.out.printf(Locale.ROOT, "[router-winding-result] %s newWet=%d span=%d drop=%d maxCut=%.4f fill=%.4f%n",
                fixture.name, newWet, maxX - minX, highest - lowest, result.maximumCut(), result.maximumFill());
    }

    private static boolean connectsToSea(Dimension dimension, Snapshot before, Point source) {
        final ArrayDeque<Point> queue = new ArrayDeque<>();
        final Set<Long> visited = new HashSet<>();
        queue.add(source); visited.add(key(source.x, source.y));
        while (!queue.isEmpty()) {
            final Point point = queue.remove();
            if (before.wasWet(point.x, point.y)) return true;
            for (int[] step : NEIGHBOURS) {
                final int x = point.x + step[0], y = point.y + step[1];
                if (dimension.isTilePresent(x >> 7, y >> 7) && wet(dimension, x, y) && visited.add(key(x, y))) {
                    queue.add(new Point(x, y));
                }
            }
        }
        return false;
    }

    private static ShallowRiverCarver plan(Dimension dimension) {
        final ShallowRiverCarver result = new ShallowRiverCarver(dimension, 5, 12, 1.10, true, true, 733, null);
        result.enableTerrainPreservation();
        return result;
    }
    private static boolean wet(Dimension d, int x, int y) { return d.getWaterLevelAt(x, y) > d.getIntHeightAt(x, y); }
    private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }
    private static final int[][] NEIGHBOURS = {{1,0}, {-1,0}, {0,1}, {0,-1}};
    private record Projection(double along, double distance) { }
    private record Fixture(String name, Dimension dimension, Point[] path) {
        Point source() { return path[0]; }
        Point outlet() { return path[path.length - 1]; }
    }

    private static final class Snapshot {
        private final List<SavedTile> tiles = new ArrayList<>();
        private final Map<Long, SavedTile> indexed = new HashMap<>();
        Snapshot(Dimension dimension) {
            for (Tile tile : dimension.getTiles()) {
                final SavedTile saved = new SavedTile(tile);
                tiles.add(saved); indexed.put(key(saved.x, saved.y), saved);
            }
        }
        void verifyAll(Dimension dimension) {
            assertEquals(tiles.size(), dimension.getTiles().size());
            for (SavedTile tile : tiles) for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                tile.verifyCell(dimension, x, y);
            }
        }
        boolean wasWet(int x, int y) {
            final SavedTile tile = indexed.get(key(x >> 7, y >> 7));
            return tile != null && tile.wasWet((x & 127) + (y & 127) * 128);
        }
    }
    private static final class SavedTile {
        private final int x, y;
        private final float[] heights = new float[16384];
        private final int[] water = new int[16384];
        private final boolean[] readOnly = new boolean[16384];
        SavedTile(Tile tile) {
            x = tile.getX(); y = tile.getY();
            for (int cy = 0; cy < 128; cy++) for (int cx = 0; cx < 128; cx++) {
                final int index = cx + cy * 128;
                heights[index] = tile.getHeight(cx, cy);
                water[index] = tile.getWaterLevel(cx, cy);
                readOnly[index] = tile.getBitLayerValue(ReadOnly.INSTANCE, cx, cy);
            }
        }
        boolean wasWet(int index) { return water[index] > Math.round(heights[index]); }
        void verifyCell(Dimension dimension, int cx, int cy) {
            final int wx = x * 128 + cx, wy = y * 128 + cy, index = cx + cy * 128;
            assertEquals(heights[index], dimension.getHeightAt(wx, wy), 0);
            assertEquals(water[index], dimension.getWaterLevelAt(wx, wy));
            assertEquals(wasWet(index) ? Terrain.SAND : Terrain.GRASS, dimension.getTerrainAt(wx, wy));
            assertEquals(1, dimension.getLayerValueAt(Biome.INSTANCE, wx, wy));
            assertFalse(dimension.getBitLayerValueAt(Frost.INSTANCE, wx, wy));
            assertEquals(readOnly[index], dimension.getBitLayerValueAt(ReadOnly.INSTANCE, wx, wy));
        }
    }
}
