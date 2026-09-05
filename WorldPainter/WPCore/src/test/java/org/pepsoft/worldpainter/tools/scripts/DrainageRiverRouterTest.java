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
import org.pepsoft.worldpainter.layers.RiverSurfaceDetail;
import org.pepsoft.worldpainter.themes.SimpleTheme;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.Assert.*;

/** Integration contracts for drainage proposals followed by the unchanged full carver and A*. */
public class DrainageRiverRouterTest {
    @Test
    public void comparesShortDryEdgeCourseWithLongMountainCourseBeforeCommitting() {
        final Dimension dimension = competingValleys();
        final Snapshot before = new Snapshot(dimension);
        final ShallowRiverCarver shorter = plan(dimension), longer = plan(dimension);
        final boolean shortFeasible = shorter.addPath(new int[] {220, 8}, new int[] {64, 64});
        assertTrue("The earlier upper valley must offer a real, safe dry-edge alternative: " + shorter.getLastRejection(),
                shortFeasible);
        final boolean longFeasible = longer.addPath(new int[] {32, 704}, new int[] {256, 256});
        assertTrue("The lower valley must offer a longer safe mountain-to-sea course: " + longer.getLastRejection(),
                longFeasible);
        before.assertUnchanged(dimension);

        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, null);
        assertEquals(router.getSummary(), 1, findAutomatic(router));

        assertEquals("Candidate comparison must commit only the winning course", 1, plan.getAcceptedPaths());
        assertEquals("A single component needs one drainage graph", 1, router.getDrainagePasses());
        assertTrue("More than the first drainage proposal must be validated before selection: " + router.getSummary(),
                router.getDrainageCandidatesTested() >= 2);
        assertTrue("The 212-block dry-edge course must not hide the longer upstream river: " + router.getSummary(),
                router.getLastNewTerrainLength() >= 400);
        assertTrue("The selected course must descend from the mountain", router.getLastWaterDrop() >= 30);
        before.assertUnchanged(dimension);

        assertSafeApplied(before, dimension, plan.apply());
        assertTrue("The long lower valley must receive the winning river", anyWet(dimension, 200, 240, 240, 272));
        assertFalse("Comparing the earlier short course must not carve it", anyWet(dimension, 32, 96, 56, 72));
    }

    @Test
    public void shallowClosedDipFallsBackToAStarWithoutInventingDrainageOrRaisingGround() {
        final Dimension dimension = interruptedValley(0.75);
        final Snapshot before = new Snapshot(dimension);
        assertNoDownhillDrainageCourse(dimension);
        final ShallowRiverCarver known = plan(dimension);
        final boolean feasible = known.addPath(new int[] {32, 484}, new int[] {64, 64});
        assertTrue("The shallow dip must be crossable within the existing cut budget: " + known.getLastRejection(), feasible);
        before.assertUnchanged(dimension);

        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, VALLEY_CORRIDOR);
        assertEquals("A strict-D8 dead end must not suppress the proven A* fallback: " + router.getSummary(),
                1, findAutomatic(router));

        assertEquals(1, router.getDrainagePasses());
        assertTrue("The dip requires a genuine cost-search fallback", router.getSearchAttempts() > 0);
        assertEquals(1, plan.getAcceptedPaths());
        assertTrue(router.getSummary(), router.getLastNewTerrainLength() >= 300);
        assertTrue(router.getSummary(), router.getLastWaterDrop() >= 30);
        before.assertUnchanged(dimension);
        assertSafeApplied(before, dimension, plan.apply());
    }

    @Test
    public void deepClosedBasinIsNotFilledAndRepeatedRoundsDoNotRebuildThePrepass() {
        final Dimension dimension = interruptedValley(6.0);
        final Snapshot before = new Snapshot(dimension);
        assertNoDownhillDrainageCourse(dimension);
        final ShallowRiverCarver known = plan(dimension);
        assertFalse("The deep pit must not be escapable with a 1.85-block cut",
                known.addPath(new int[] {32, 484}, new int[] {64, 64}));
        before.assertUnchanged(dimension);

        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, VALLEY_CORRIDOR);
        for (int round = 0; round < 6; round++) {
            assertEquals("No allowed outlet can be reached through this closed basin: " + router.getSummary(),
                    0, router.findAutomatic(1));
            assertEquals("A repeat round must reuse the existing component drainage graph", 1, router.getDrainagePasses());
            assertEquals(0, plan.getAcceptedPaths());
            before.assertUnchanged(dimension);
        }
        assertEquals("Rejected drainage and A* proposals must leave an empty shared plan", 0, plan.apply().changedCells());
        before.assertUnchanged(dimension);
    }

    private static int findAutomatic(ShallowRiverRouter router) {
        for (int round = 0; round < 6; round++) {
            if (round > 0 && !router.canContinueAutomaticSearch()) break;
            final int found = router.findAutomatic(1);
            if (found != 0) return found;
        }
        return 0;
    }

    private static ShallowRiverRouter router(Dimension dimension, ShallowRiverCarver plan,
                                             BiPredicate<Integer, Integer> avoid) {
        final ShallowRiverRouter router = new ShallowRiverRouter(dimension, plan, 5, 12, 1.10, true, SEED, null, avoid);
        router.enableMountainCourseSelection();
        return router;
    }

    private static ShallowRiverCarver plan(Dimension dimension) {
        final ShallowRiverCarver plan = new ShallowRiverCarver(dimension, 5, 12, 1.10, true, true, SEED, null);
        plan.enableTerrainPreservation();
        return plan;
    }

    private static Dimension competingValleys() {
        final HeightMapTileFactory factory = factory();
        final Dimension dimension = dimension("Competing short edge and long sea valleys", factory);
        for (int ty = 0; ty < 3; ty++) for (int tx = 0; tx < 6; tx++) {
            final Tile tile = factory.createTile(tx, ty);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final int wx = tx * 128 + x, wy = ty * 128 + y;
                final double shortAlong = Math.max(0, Math.min(224, wx - 8));
                final double shortDistance = Math.hypot(wx < 8 ? 8 - wx : Math.max(0, wx - 232), wy - 64);
                final double shortValley = SEA + 4.125 + shortAlong * (44.0 / 224)
                        + Math.max(0, shortDistance - 10) * 0.6;
                final double longValley = SEA + 0.125 + 48 * Math.max(0, (699.0 - wx) / 667)
                        + Math.max(0, Math.abs(wy - 256) - 10) * 0.6;
                initialise(tile, x, y, wx >= 700 ? SEA - 2f : (float) Math.min(shortValley, longValley), wx >= 700);
            }
            dimension.addTile(tile);
        }
        return dimension;
    }

    private static Dimension interruptedValley(double depression) {
        final HeightMapTileFactory factory = factory();
        final Dimension dimension = dimension("Closed valley dip " + depression, factory);
        for (int tx = 0; tx < 4; tx++) {
            final Tile tile = factory.createTile(tx, 0);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final int wx = tx * 128 + x;
                final double valley = SEA + 0.125 + 48 * Math.max(0, (479.0 - wx) / 447)
                        + Math.max(0, Math.abs(y - 64) - 10) * 0.6;
                final double height = valley - (wx >= 220 && wx <= 226 ? depression : 0);
                initialise(tile, x, y, wx >= 480 ? SEA - 2f : (float) height, wx >= 480);
            }
            dimension.addTile(tile);
        }
        return dimension;
    }

    /** Independently prove that no high source in the corridor has a downhill D8 escape. */
    private static void assertNoDownhillDrainageCourse(Dimension dimension) {
        final int columns = 256, rows = 64, step = 2;
        final float[] heights = new float[columns * rows];
        final boolean[] blocked = new boolean[heights.length], terminals = new boolean[heights.length], sources = new boolean[heights.length];
        for (int index = 0; index < heights.length; index++) {
            final int x = 1 + index % columns * step, y = 1 + index / columns * step;
            blocked[index] = VALLEY_CORRIDOR.test(x, y);
            terminals[index] = wet(dimension, x, y);
            heights[index] = terminals[index] ? dimension.getWaterLevelAt(x, y) : dimension.getHeightAt(x, y);
            sources[index] = !blocked[index] && !terminals[index] && heights[index] >= 100;
        }
        final RiverDrainageCandidates drainage = new RiverDrainageCandidates(columns, rows, step, heights,
                blocked, terminals, sources, () -> { });
        assertTrue("A strict-downhill drainage prepass may not breach or fill the local minimum",
                drainage.candidates(8, 200, 30, 100, 20).isEmpty());
    }

    private static HeightMapTileFactory factory() {
        return new HeightMapTileFactory(SEED, new ConstantHeightMap(SEA + 48), MIN_HEIGHT, MAX_HEIGHT, false,
                SimpleTheme.createSingleTerrain(Terrain.GRASS, MIN_HEIGHT, MAX_HEIGHT, MIN_HEIGHT));
    }

    private static Dimension dimension(String name, HeightMapTileFactory factory) {
        return new Dimension(new World2(TestData.PLATFORM, MIN_HEIGHT, MAX_HEIGHT), name, SEED, factory,
                Dimension.Anchor.NORMAL_DETAIL);
    }

    private static void initialise(Tile tile, int x, int y, float height, boolean ocean) {
        tile.setHeight(x, y, height);
        tile.setWaterLevel(x, y, ocean ? SEA : MIN_HEIGHT);
        tile.setTerrain(x, y, ocean ? Terrain.SAND : Terrain.GRASS);
        tile.setLayerValue(Biome.INSTANCE, x, y, 1);
        tile.setBitLayerValue(Frost.INSTANCE, x, y, false);
        tile.setBitLayerValue(RiverSurfaceDetail.INSTANCE, x, y, false);
    }

    private static void assertSafeApplied(Snapshot before, Dimension dimension, ShallowRiverCarver.Result result) {
        assertTrue(result.changedCells() > 0);
        assertEquals(0, result.raisedCells());
        assertEquals(0, result.maximumFill(), 0);
        assertTrue("Drainage candidates must retain the exact shallow cut budget", result.maximumCut() <= 1.85 + HEIGHT_QUANTUM);
        Point upstream = null;
        int highest = Integer.MIN_VALUE;
        for (SavedTile old : before.tiles) {
            final Tile tile = dimension.getTile(old.x, old.y);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                final int index = x + y * 128;
                if (old.wasWet(index) || tile.getWaterLevel(x, y) <= Math.round(tile.getHeight(x, y))) {
                    old.assertCell(tile, x, y);
                } else {
                    assertTrue("No original terrain may be raised", tile.getHeight(x, y) <= old.heights[index]);
                    assertTrue(old.heights[index] - tile.getHeight(x, y) <= 1.85 + HEIGHT_QUANTUM);
                    assertTrue(tile.getWaterLevel(x, y) - tile.getHeight(x, y) <= 1.10 + HEIGHT_QUANTUM);
                    if (tile.getWaterLevel(x, y) > highest) {
                        highest = tile.getWaterLevel(x, y);
                        upstream = new Point(old.x * 128 + x, old.y * 128 + y);
                    }
                }
            }
        }
        assertNotNull(upstream);
        assertTrue("The high new river must connect to unchanged receiving water", connectsToOriginalWater(dimension, before, upstream));
    }

    private static boolean connectsToOriginalWater(Dimension dimension, Snapshot before, Point start) {
        final ArrayDeque<Point> queue = new ArrayDeque<>();
        final Set<Long> seen = new HashSet<>();
        queue.add(start);
        seen.add(key(start.x, start.y));
        while (!queue.isEmpty()) {
            final Point point = queue.remove();
            if (before.wasWet(point.x, point.y)) return true;
            for (int[] offset : CARDINAL) {
                final int x = point.x + offset[0], y = point.y + offset[1];
                if (dimension.isTilePresent(x >> 7, y >> 7) && wet(dimension, x, y) && seen.add(key(x, y))) queue.add(new Point(x, y));
            }
        }
        return false;
    }

    private static boolean anyWet(Dimension dimension, int minX, int maxX, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) if (wet(dimension, x, y)) return true;
        return false;
    }

    private static boolean wet(Dimension dimension, int x, int y) {
        return dimension.getWaterLevelAt(x, y) > Math.round(dimension.getHeightAt(x, y));
    }

    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }

    private static final class Snapshot {
        private final List<SavedTile> tiles = new ArrayList<>();
        private final Map<Long, SavedTile> byTile = new HashMap<>();
        private final long changes;
        Snapshot(Dimension dimension) {
            changes = dimension.getChangeNo();
            for (Tile tile : dimension.getTiles()) {
                final SavedTile saved = new SavedTile(tile);
                tiles.add(saved);
                byTile.put(key(saved.x, saved.y), saved);
            }
        }
        void assertUnchanged(Dimension dimension) {
            assertEquals("Planning must not mutate dimension state", changes, dimension.getChangeNo());
            assertEquals(tiles.size(), dimension.getTileCount());
            for (SavedTile saved : tiles) {
                final Tile tile = dimension.getTile(saved.x, saved.y);
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) saved.assertCell(tile, x, y);
            }
        }
        boolean wasWet(int x, int y) {
            final SavedTile tile = byTile.get(key(x >> 7, y >> 7));
            return tile != null && tile.wasWet((x & 127) + (y & 127) * 128);
        }
    }

    private static final class SavedTile {
        private final int x, y;
        private final float[] heights = new float[16384];
        private final int[] water = new int[16384];
        private final Terrain[] terrain = new Terrain[16384];
        SavedTile(Tile tile) {
            x = tile.getX(); y = tile.getY();
            for (int cy = 0; cy < 128; cy++) for (int cx = 0; cx < 128; cx++) {
                final int index = cx + cy * 128;
                heights[index] = tile.getHeight(cx, cy);
                water[index] = tile.getWaterLevel(cx, cy);
                terrain[index] = tile.getTerrain(cx, cy);
            }
        }
        boolean wasWet(int index) { return water[index] > Math.round(heights[index]); }
        void assertCell(Tile tile, int cx, int cy) {
            final int index = cx + cy * 128;
            assertEquals(heights[index], tile.getHeight(cx, cy), 0);
            assertEquals(water[index], tile.getWaterLevel(cx, cy));
            assertSame(terrain[index], tile.getTerrain(cx, cy));
            assertEquals(1, tile.getLayerValue(Biome.INSTANCE, cx, cy));
            assertFalse(tile.getBitLayerValue(Frost.INSTANCE, cx, cy));
            assertFalse(tile.getBitLayerValue(RiverSurfaceDetail.INSTANCE, cx, cy));
        }
    }

    private static final long SEED = 733L;
    private static final int MIN_HEIGHT = -64, MAX_HEIGHT = 320, SEA = 62;
    private static final double HEIGHT_QUANTUM = 1.0 / 256;
    private static final int[][] CARDINAL = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final BiPredicate<Integer, Integer> VALLEY_CORRIDOR = (x, y) -> x < 16 || y < 40 || y > 88;
}
