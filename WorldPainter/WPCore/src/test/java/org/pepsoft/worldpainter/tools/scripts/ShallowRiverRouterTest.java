package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.Void;

import java.util.function.BiPredicate;

import static org.junit.Assert.*;

/** Real terrain fixtures: routing itself may not write any world data. */
public class ShallowRiverRouterTest {
    @Test
    public void manualSourceFindsAShortSafeRouteOnOneFlatTileWithoutMovingTheSource() {
        final Dimension dimension = flat(100);
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null, null);

        assertTrue(router.getSummary(), router.findFromSource(64, 64));

        assertEquals("Router must only plan", before, fingerprint(dimension));
        assertEquals(1, plan.getAcceptedPaths());
        final ShallowRiverCarver.Result result = plan.apply();
        assertTrue("The exact requested source, not a silently substituted one, must be wet",
                wet(dimension, 64, 64));
        assertTrue(result.changedCells() > 0);
        assertTrue(result.maximumCut() <= 1.85 + 1.0 / 256);
        assertTrue(router.getSummary().contains("kabul"));
    }

    @Test
    public void automaticFlatLowlandDoesNotRequireHeightAboveSeaOrThreeHundredBlocks() {
        final Dimension dimension = flat(64);
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null, null);

        assertEquals(router.getSummary(), 1, router.findAutomatic(1));

        assertEquals(before, fingerprint(dimension));
        assertEquals(1, plan.apply().paths());
        assertTrue(countWet(dimension) >= 3);
        assertEquals(1, dimension.getTiles().size());
    }

    @Test
    public void lowlandSourceAtSeaHeightConnectsToExistingOceanWithoutChangingIt() {
        final Dimension dimension = coastal();
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null, null);

        final boolean found = router.findFromSource(48, 64);
        assertTrue(router.getSummary(), found);
        assertEquals(before, fingerprint(dimension));
        plan.apply();

        assertWet(dimension, 48, 64);
        assertTrue("Real water should be preferred over a dry map-edge fallback", router.getSummary().contains("göl/denize"));
        assertOceanUnchanged(dimension);
    }

    @Test
    public void ridgeBlockingTheFirstStraightRouteIsBypassedWithoutDeepCutting() {
        final Dimension dimension = coastal();
        for (int x = 56; x <= 64; x++) {
            for (int y = 32; y <= 95; y++) dimension.setHeightAt(x, y, 112f);
        }
        final ShallowRiverCarver rejectedStraight = plan(dimension);
        assertFalse(rejectedStraight.addPath(new int[] { 32, 98 }, new int[] { 64, 64 }));
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null, null);

        final boolean found = router.findFromSource(32, 64);
        assertTrue(router.getSummary(), found);
        assertEquals(before, fingerprint(dimension));
        final ShallowRiverCarver.Result result = plan.apply();

        assertWet(dimension, 32, 64);
        assertEquals("The central ridge must be bypassed, not punched through", 112f, dimension.getHeightAt(60, 64), 0f);
        assertTrue(result.maximumCut() <= 1.85 + 1.0 / 256);
        assertTrue("A bypass should still reach the ocean", router.getSummary().contains("göl/denize"));
        assertOceanUnchanged(dimension);
    }

    @Test
    public void protectedManualSourceIsNotSilentlyReplacedWithAnotherSource() {
        final Dimension dimension = flat(100);
        dimension.setBitLayerValueAt(Void.INSTANCE, 64, 64, true);
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null, null);

        assertFalse(router.findFromSource(64, 64));

        assertEquals(0, plan.getAcceptedPaths());
        assertEquals(0, plan.apply().changedCells());
        assertEquals(before, fingerprint(dimension));
        assertTrue(router.getSummary().contains("konumu değiştirilmedi"));
    }

    @Test
    public void fullyProtectedOrAvoidedWorldFailsReadOnlyInsteadOfForcingARiver() {
        for (boolean customAvoid : new boolean[] { false, true }) {
            final Dimension dimension = flat(100);
            if (!customAvoid) {
                for (int x = 0; x < 128; x += 16) {
                    for (int y = 0; y < 128; y += 16) dimension.setBitLayerValueAt(ReadOnly.INSTANCE, x, y, true);
                }
            }
            final long before = fingerprint(dimension);
            final ShallowRiverCarver plan = plan(dimension);
            final ShallowRiverRouter router = router(dimension, plan, 733L, null, customAvoid ? (x, y) -> true : null);

            assertEquals(0, router.findAutomatic(1));
            assertFalse(router.findFromSource(64, 64));
            assertEquals(0, plan.apply().changedCells());
            assertEquals(before, fingerprint(dimension));
        }
    }

    @Test
    public void cancelledCoarseScanLeavesTerrainAndSharedPlanUntouched() {
        final Dimension dimension = flat(100);
        final long before = fingerprint(dimension);
        final int[] checks = { 0 };
        final ScriptProgress progress = new ScriptProgress(null, null) {
            @Override
            public void checkForCancel() {
                if (++checks[0] == 50) throw new ScriptingContext.InterruptedException();
            }
        };
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, progress, null);

        assertThrows(ScriptingContext.InterruptedException.class, () -> router.findAutomatic(1));

        assertEquals(50, checks[0]);
        assertEquals(0, plan.getAcceptedPaths());
        assertEquals(before, fingerprint(dimension));
    }

    @Test
    public void sameSeedProducesIdenticalAutomaticPlanAndAppliedWorld() {
        final Dimension first = flat(100), second = flat(100);
        final ShallowRiverCarver firstPlan = plan(first), secondPlan = plan(second);
        final ShallowRiverRouter firstRouter = router(first, firstPlan, 904L, null, null);
        final ShallowRiverRouter secondRouter = router(second, secondPlan, 904L, null, null);

        assertEquals(1, firstRouter.findAutomatic(1));
        assertEquals(1, secondRouter.findAutomatic(1));
        assertEquals(firstRouter.getSummary(), secondRouter.getSummary());
        assertEquals(firstPlan.apply(), secondPlan.apply());
        assertEquals(fingerprint(first), fingerprint(second));
    }

    @Test
    public void noRequestedRiversDoesNotEvenScanOrCallTheAvoidPredicate() {
        final Dimension dimension = flat(100);
        final int[] calls = { 0 };
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null, (x, y) -> {
            calls[0]++;
            return false;
        });
        assertEquals(0, router.findAutomatic(0));
        assertEquals(0, calls[0]);
        assertEquals(0, plan.getAcceptedPaths());
    }

    @Test
    public void automaticRetriesDifferentElevationBandsInsteadOfSpendingEverySourceOnBadSummits() {
        final Dimension dimension = flat(100);
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                if (x < 80) {
                    // Most of the map is an unusable high checkerboard: a high
                    // sample alone says nothing about a safe shallow river bed.
                    dimension.setHeightAt(x, y, ((x / 4 + y / 4) & 1) == 0 ? 112f : 128f);
                } else if (x >= 112) {
                    dimension.setHeightAt(x, y, 98f);
                    dimension.setWaterLevelAt(x, y, 100);
                    dimension.setTerrainAt(x, y, Terrain.SAND);
                }
            }
        }
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = plan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null, null);

        final int found = router.findAutomatic(1);
        assertEquals(router.getSummary(), 1, found);
        assertEquals(before, fingerprint(dimension));
        plan.apply();

        int valleyWater = 0;
        for (int x = 80; x < 112; x++) {
            for (int y = 0; y < 128; y++) if (wet(dimension, x, y)) valleyWater++;
        }
        assertTrue("A viable low valley must receive a trial despite many higher bad sources", valleyWater >= 3);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 128; y++) assertFalse(wet(dimension, x, y));
        }
    }

    @Test
    public void adaptiveShouldersRespectAvoidMaskEvenWhenLegacySmoothBanksIsOff() {
        final Dimension dimension = coastal();
        dimension.setHeightAt(64, 72, 102f);
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = new ShallowRiverCarver(dimension,
                5, 8, 1.10, false, true, 733L, null);
        plan.enableTerrainAdaptation();
        final ShallowRiverRouter router = new ShallowRiverRouter(dimension, plan,
                5, 8, 1.10, false, 733L, null, (x, y) -> x == 64 && y == 72);

        assertFalse("Adaptive terrain always has a wide shoulder; the legacy checkbox cannot shrink avoid validation",
                router.findFromSource(64, 64));

        assertEquals(0, plan.getAcceptedPaths());
        assertEquals(0, plan.apply().changedCells());
        assertEquals(before, fingerprint(dimension));
    }

    @Test
    public void preservingRouterChoosesExistingValleyInsteadOfAKazilabilirShortRidge() {
        final Dimension dimension = coastal();
        for (int x = 48; x <= 72; x++) {
            for (int y = 44; y <= 84; y++) dimension.setHeightAt(x, y, 100.40f);
        }
        final float[][] beforeHeights = snapshotHeights(dimension);
        final long before = fingerprint(dimension);
        final ShallowRiverCarver direct = preservingPlan(dimension);
        assertTrue("The comparison ridge is shallow enough to carve, so this tests route preference, not just rejection",
                direct.addPath(new int[] { 32, 98 }, new int[] { 64, 64 }));
        final ShallowRiverCarver plan = preservingPlan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null, null);

        final boolean found = router.findFromSource(32, 64);

        assertTrue(router.getSummary(), found);
        assertEquals("Alternatives must be planned without changing the world", before, fingerprint(dimension));
        final ShallowRiverCarver.Result result = plan.apply();
        assertWet(dimension, 32, 64);
        assertEquals(0, result.raisedCells());
        assertEquals(0, result.maximumFill(), 0);
        assertTrue(result.maximumCut() <= 1.85 + 1.0 / 256);
        int valleyWater = 0;
        for (int x = 52; x <= 68; x++) {
            for (int y = 0; y < 128; y++) {
                if ((y < 44 || y > 84) && wet(dimension, x, y)) valleyWater++;
            }
            for (int y = 52; y <= 76; y++) {
                assertEquals("Do not excavate a shorter trench across the middle of the ridge",
                        beforeHeights[y][x], dimension.getHeightAt(x, y), 0);
            }
        }
        assertTrue("A longer low-incision valley must win over the direct ridge", valleyWater > 10);
        assertTrue("Suitable existing water must win over an arbitrary dry edge", router.getSummary().contains("göl/denize"));
        assertDryTerrainUnchanged(dimension, beforeHeights);
        assertOceanUnchanged(dimension);
    }

    @Test
    public void preservingRouterDoesNotTreatUntouchedDryBankAsAnAdaptiveShoulder() {
        final Dimension dimension = coastal();
        final float[][] beforeHeights = snapshotHeights(dimension);
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = preservingPlan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null,
                (x, y) -> x == 64 && y == 72);

        final boolean found = router.findFromSource(64, 64);

        assertTrue(router.getSummary(), found);
        assertEquals(before, fingerprint(dimension));
        plan.apply();
        assertWet(dimension, 64, 64);
        assertEquals("The avoided dry bank eight blocks away must remain intact", 100f, dimension.getHeightAt(64, 72), 0);
        assertFalse(wet(dimension, 64, 72));
        assertDryTerrainUnchanged(dimension, beforeHeights);
        assertOceanUnchanged(dimension);
    }

    @Test
    public void preservingAutomaticRoutingIsDeterministicAndNeverGradingTheDryLandscape() {
        final Dimension first = coastal(), second = coastal();
        final float[][] firstBefore = snapshotHeights(first), secondBefore = snapshotHeights(second);
        final ShallowRiverCarver firstPlan = preservingPlan(first), secondPlan = preservingPlan(second);
        final ShallowRiverRouter firstRouter = router(first, firstPlan, 904L, null, null);
        final ShallowRiverRouter secondRouter = router(second, secondPlan, 904L, null, null);

        assertEquals(1, firstRouter.findAutomatic(1));
        assertEquals(1, secondRouter.findAutomatic(1));
        assertEquals(firstRouter.getSummary(), secondRouter.getSummary());
        assertEquals(firstPlan.apply(), secondPlan.apply());
        assertEquals(fingerprint(first), fingerprint(second));
        assertDryTerrainUnchanged(first, firstBefore);
        assertDryTerrainUnchanged(second, secondBefore);
        assertOceanUnchanged(first);
        assertOceanUnchanged(second);
    }

    @Test
    public void preservationCannotMoveAnAvoidedManualSourceOrForceABlockedValley() {
        final Dimension dimension = coastal();
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = preservingPlan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null,
                (x, y) -> x == 48 && y == 64);

        assertFalse(router.findFromSource(48, 64));
        assertEquals(0, plan.getAcceptedPaths());
        assertEquals(0, plan.apply().changedCells());
        assertEquals(before, fingerprint(dimension));
    }

    @Test
    public void preservingSourceMayBecomeAShallowRiffleWithoutDemandingExactMaximumDepth() {
        final Dimension dimension = coastal();
        for (int x = 56; x <= 68; x++) {
            // Low unmodified banks cap the water at 100 even though the centre
            // exports at 101. This is a real shallow riffle after voxel-correct
            // round(height), rather than relying on the former floor bug.
            for (int y = 62; y <= 66; y++) dimension.setHeightAt(x, y, 100.90f);
        }
        final float[][] beforeHeights = snapshotHeights(dimension);
        final long before = fingerprint(dimension);
        final ShallowRiverCarver plan = preservingPlan(dimension);
        final ShallowRiverRouter router = router(dimension, plan, 733L, null, null);

        final boolean found = router.findFromSource(64, 64);

        assertTrue(router.getSummary(), found);
        assertEquals(before, fingerprint(dimension));
        final ShallowRiverCarver.Result result = plan.apply();
        assertWet(dimension, 64, 64);
        final double actualDepth = dimension.getWaterLevelAt(64, 64) - dimension.getHeightAt(64, 64);
        assertTrue("The maximum depth is a cap, not mandatory deep excavation", actualDepth < 1.0);
        assertTrue(actualDepth >= 0.65 - 1.0 / 256);
        assertTrue(result.maximumCut() <= 1.85 + 1.0 / 256);
        assertEquals(0, result.maximumFill(), 0);
        assertDryTerrainUnchanged(dimension, beforeHeights);
        assertOceanUnchanged(dimension);
    }

    private static ShallowRiverCarver preservingPlan(Dimension dimension) {
        final ShallowRiverCarver plan = plan(dimension);
        plan.enableTerrainPreservation();
        return plan;
    }

    private static float[][] snapshotHeights(Dimension dimension) {
        final float[][] result = new float[128][128];
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) result[y][x] = dimension.getHeightAt(x, y);
        return result;
    }

    private static void assertDryTerrainUnchanged(Dimension dimension, float[][] before) {
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 96; x++) {
                if (!wet(dimension, x, y)) {
                    assertEquals("Untouched dry landscape at " + x + "," + y,
                            before[y][x], dimension.getHeightAt(x, y), 0);
                    assertEquals(Terrain.GRASS, dimension.getTerrainAt(x, y));
                    assertEquals(0, dimension.getWaterLevelAt(x, y));
                }
            }
        }
    }

    private static ShallowRiverCarver plan(Dimension dimension) {
        return new ShallowRiverCarver(dimension, 5, 8, 1.10, true, true, 733L, null);
    }

    private static ShallowRiverRouter router(Dimension dimension, ShallowRiverCarver plan, long seed,
                                             ScriptProgress progress, BiPredicate<Integer, Integer> avoid) {
        return new ShallowRiverRouter(dimension, plan, 5, 8, 1.10, true, seed, progress, avoid);
    }

    private static Dimension coastal() {
        final Dimension dimension = flat(100);
        for (int x = 96; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                dimension.setHeightAt(x, y, 98f);
                dimension.setWaterLevelAt(x, y, 100);
                dimension.setTerrainAt(x, y, Terrain.SAND);
            }
        }
        return dimension;
    }

    private static void assertOceanUnchanged(Dimension dimension) {
        for (int x = 96; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                assertEquals(98f, dimension.getHeightAt(x, y), 0f);
                assertEquals(100, dimension.getWaterLevelAt(x, y));
                assertEquals(Terrain.SAND, dimension.getTerrainAt(x, y));
            }
        }
    }

    private static Dimension flat(int height) {
        final TileFactory factory = TestData.createTileFactory(height);
        final Dimension dimension = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "Router", 733L, factory, Dimension.Anchor.NORMAL_DETAIL);
        final Tile tile = factory.createTile(0, 0);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                tile.setHeight(x, y, height);
                tile.setWaterLevel(x, y, 0);
                tile.setTerrain(x, y, Terrain.GRASS);
            }
        }
        dimension.addTile(tile);
        return dimension;
    }

    private static boolean wet(Dimension dimension, int x, int y) {
        return dimension.getWaterLevelAt(x, y) > Math.round(dimension.getHeightAt(x, y));
    }

    private static void assertWet(Dimension dimension, int x, int y) { assertTrue("Dry cell at " + x + "," + y, wet(dimension, x, y)); }

    private static int countWet(Dimension dimension) {
        int result = 0;
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) if (wet(dimension, x, y)) result++;
        return result;
    }

    private static long fingerprint(Dimension dimension) {
        long result = 1;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                result = 31 * result + Float.floatToIntBits(dimension.getHeightAt(x, y));
                result = 31 * result + dimension.getWaterLevelAt(x, y);
                result = 31 * result + dimension.getTerrainAt(x, y).ordinal();
                result = 31 * result + dimension.getLayerValueAt(Biome.INSTANCE, x, y);
                result = 31 * result + (dimension.getBitLayerValueAt(Void.INSTANCE, x, y) ? 1 : 0);
                result = 31 * result + (dimension.getBitLayerValueAt(ReadOnly.INSTANCE, x, y) ? 1 : 0);
            }
        }
        return result;
    }
}
