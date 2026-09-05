package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Hard work bounds for ranked automatic drainage discovery and corridor refinement. */
public class DrainageRouterBudgetTest {
    @Test
    public void rankedDiscoveryReservesTwelveOfTheCandidateBudgetForFinalRevalidation() throws Exception {
        final Dimension dimension = flatDimension();
        final ShallowRiverCarver plan = plan(dimension, 12);
        final ShallowRiverRouter router = new ShallowRiverRouter(dimension, plan,
                5, 12, 1.10, true, 733L, null, null);
        router.enableMountainCourseSelection();
        set(router, "collectingCandidates", true);

        set(router, "attempts", 179);
        assertTrue("The last discovery slot before the twelve-slot reserve remains usable",
                router.canContinueAutomaticSearch());
        set(router, "attempts", 180);
        assertFalse("Ranked discovery must stop with twelve final validations still available",
                router.canContinueAutomaticSearch());

        set(router, "committingRanked", true);
        set(router, "attempts", 191);
        assertTrue("Final revalidation may consume the reserved slots", router.canContinueAutomaticSearch());
        set(router, "attempts", 192);
        assertFalse("Final revalidation may not exceed the absolute candidate cap",
                router.canContinueAutomaticSearch());

        final ShallowRiverRouter ordinary = new ShallowRiverRouter(dimension, plan,
                5, 12, 1.10, true, 733L, null, null);
        set(ordinary, "attempts", 191);
        assertTrue("Non-ranked/manual routing retains the full absolute budget",
                ordinary.canContinueAutomaticSearch());
        set(ordinary, "attempts", 192);
        assertFalse(ordinary.canContinueAutomaticSearch());
    }

    @Test(timeout = 2_000)
    public void maximumWidthCorridorVisitsOnlyTheBoundedGrid() throws Exception {
        final Dimension dimension = flatDimension();
        final long worldChanges = dimension.getWorld().getChangeNo(), dimensionChanges = dimension.getChangeNo();
        final ShallowRiverRouter router = new ShallowRiverRouter(dimension, plan(dimension, 64),
                5, 64, 1.10, true, 733L, null, null);

        // Construct the maximum router grid directly. Supplying every cell as a
        // path point makes per-point radius stamping prohibitively expensive;
        // bounded multi-source dilation still visits each cell once.
        final Class<?> gridType = Class.forName(ShallowRiverRouter.class.getName() + "$Grid");
        final Constructor<?> constructor = gridType.getDeclaredConstructor(
                int.class, int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        final int columns = 256, rows = 256, size = columns * rows;
        final Object grid = constructor.newInstance(0, 0, 2, columns, rows);
        set(router, "grid", grid);
        final int[] path = new int[size];
        for (int i = 0; i < size; i++) path[i] = i;

        final Method method = ShallowRiverRouter.class.getDeclaredMethod("drainageCorridor", int[].class);
        method.setAccessible(true);
        final boolean[] corridor = (boolean[]) method.invoke(router, (Object) path);
        assertEquals("The corridor cannot allocate or return cells outside the fixed router grid", size, corridor.length);
        for (boolean included : corridor) assertTrue("Every supplied path cell belongs to its corridor", included);
        assertEquals("Building a search corridor must not mutate the world", worldChanges, dimension.getWorld().getChangeNo());
        assertEquals("Building a search corridor must not mutate the dimension", dimensionChanges, dimension.getChangeNo());
    }

    private static ShallowRiverCarver plan(Dimension dimension, double endWidth) {
        final ShallowRiverCarver plan = new ShallowRiverCarver(dimension,
                5, endWidth, 1.10, true, true, 733L, null);
        plan.enableTerrainPreservation();
        return plan;
    }

    private static Dimension flatDimension() {
        final TileFactory factory = TestData.createTileFactory(100);
        final World2 world = new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT);
        final Dimension dimension = new Dimension(world, "Drainage budget", 733L, factory,
                Dimension.Anchor.NORMAL_DETAIL);
        final Tile tile = factory.createTile(0, 0);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            tile.setHeight(x, y, 100);
            tile.setWaterLevel(x, y, 0);
            tile.setTerrain(x, y, Terrain.GRASS);
        }
        dimension.addTile(tile);
        return dimension;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        final Field field = ShallowRiverRouter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
