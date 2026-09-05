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
import org.pepsoft.worldpainter.layers.River;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

/** Connected-component selection must work from geometry, not a particular world or origin. */
public class RouteSelectionSafetyTest {
    private static final int FAR_TILE = 100_000;
    private static final long SEED = 904L;

    @Test(timeout = 30000)
    public void manualSourceUsesItsDisconnectedFarComponentWithoutMovingOrCoarseningIt() {
        Dimension dimension = fixture(0, 1, FAR_TILE);
        long before = fingerprint(dimension);
        ShallowRiverCarver plan = plan(dimension);
        ShallowRiverRouter router = router(dimension, plan, null);
        int sourceX = FAR_TILE * 128 + 64;

        boolean found = router.findFromSource(sourceX, 64);

        assertTrue(router.getSummary(), found);
        assertEquals("Routing is read-only", before, fingerprint(dimension));
        assertEquals(1, plan.getAcceptedPaths());
        assertEquals(1, plan.apply().paths());
        assertTrue("The exact far source must be used", dimension.getWaterLevelAt(sourceX,64)
                > dimension.getIntHeightAt(sourceX,64));
        assertOriginalTile(dimension.getTile(0,0));
        assertOriginalTile(dimension.getTile(1,0));
        assertFalse("No bridge or phantom tiles may be created in the gap", dimension.isTilePresent(50_000,0));
        assertEquals(3, dimension.getTiles().size());
    }

    @Test(timeout = 30000)
    public void automaticSkipsTheProtectedLargestComponentAndIsDeterministicAcrossInsertionOrder() {
        Dimension first = fixture(0, 1, FAR_TILE);
        Dimension second = fixture(FAR_TILE, 1, 0);
        protectNearComponent(first);
        protectNearComponent(second);
        long firstBefore = fingerprint(first), secondBefore = fingerprint(second);
        ShallowRiverCarver firstPlan = plan(first), secondPlan = plan(second);
        ShallowRiverRouter firstRouter = router(first, firstPlan, null);
        ShallowRiverRouter secondRouter = router(second, secondPlan, null);

        assertEquals(firstRouter.getSummary(), 1, firstRouter.findAutomatic(1));
        assertEquals(secondRouter.getSummary(), 1, secondRouter.findAutomatic(1));

        assertEquals(firstBefore, fingerprint(first));
        assertEquals(secondBefore, fingerprint(second));
        assertEquals(firstRouter.getSummary(), secondRouter.getSummary());
        assertEquals(firstRouter.isSearchLimited(), secondRouter.isSearchLimited());
        assertEquals(1, firstPlan.apply().paths());
        assertEquals(1, secondPlan.apply().paths());
        assertEquals(fingerprint(first), fingerprint(second));
        assertTrue("The usable smaller component must receive the river", countWet(first.getTile(FAR_TILE,0)) > 0);
        for (Dimension dimension : new Dimension[] {first, second}) {
            assertOriginalTile(dimension.getTile(0,0));
            assertOriginalTile(dimension.getTile(1,0));
            assertEquals(3, dimension.getTiles().size());
        }
    }

    @Test(timeout = 30000)
    public void cancellationDuringComponentDiscoveryLeavesEveryComponentAndPlanUntouched() {
        Dimension dimension = fixture(0, 1, FAR_TILE);
        long before = fingerprint(dimension);
        int[] checks = {0};
        ScriptProgress progress = new ScriptProgress(null, null) {
            @Override public void checkForCancel() {
                if (++checks[0] == 2) throw new ScriptingContext.InterruptedException();
            }
        };
        ShallowRiverCarver plan = plan(dimension);
        ShallowRiverRouter router = router(dimension, plan, progress);

        assertThrows(ScriptingContext.InterruptedException.class, () -> router.findAutomatic(1));

        assertEquals(2, checks[0]);
        assertEquals(0, plan.getAcceptedPaths());
        assertEquals(before, fingerprint(dimension));
        assertFalse("User cancellation is not a proof of route impossibility or an exhausted search budget",
                router.isSearchLimited());
    }

    private static Dimension fixture(int... tileXs) {
        TileFactory factory = TestData.createTileFactory(100);
        Dimension dimension = new Dimension(new World2(TestData.PLATFORM, TestData.MIN_HEIGHT, TestData.MAX_HEIGHT),
                "Component routing", SEED, factory, Dimension.Anchor.NORMAL_DETAIL);
        for (int tileX : tileXs) {
            Tile tile = factory.createTile(tileX,0);
            for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
                tile.setHeight(x,y,100);
                tile.setWaterLevel(x,y,0);
                tile.setTerrain(x,y,Terrain.GRASS);
            }
            dimension.addTile(tile);
        }
        return dimension;
    }

    private static void protectNearComponent(Dimension dimension) {
        for (int y=0;y<128;y+=16) for (int x=0;x<256;x+=16) {
            dimension.setBitLayerValueAt(ReadOnly.INSTANCE,x,y,true);
        }
    }

    private static ShallowRiverCarver plan(Dimension dimension) {
        ShallowRiverCarver plan = new ShallowRiverCarver(dimension,5,8,1.1,true,true,SEED,null);
        plan.enableTerrainPreservation();
        return plan;
    }

    private static ShallowRiverRouter router(Dimension dimension, ShallowRiverCarver plan, ScriptProgress progress) {
        return new ShallowRiverRouter(dimension,plan,5,8,1.1,true,SEED,progress,null);
    }

    private static void assertOriginalTile(Tile tile) {
        for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
            assertEquals(100f,tile.getHeight(x,y),0f);
            assertEquals(0,tile.getWaterLevel(x,y));
            assertSame(Terrain.GRASS,tile.getTerrain(x,y));
            assertFalse(tile.getBitLayerValue(River.INSTANCE,x,y));
        }
    }

    private static int countWet(Tile tile) {
        int result = 0;
        for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
            if (tile.getWaterLevel(x,y) > Math.round(tile.getHeight(x,y))) result++;
        }
        return result;
    }

    private static long fingerprint(Dimension dimension) {
        List<Tile> tiles = new ArrayList<>(dimension.getTiles());
        tiles.sort(Comparator.comparingInt(Tile::getX).thenComparingInt(Tile::getY));
        long result = 1;
        for (Tile tile : tiles) {
            result = 31 * result + tile.getX();
            result = 31 * result + tile.getY();
            for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
                result = 31 * result + Float.floatToIntBits(tile.getHeight(x,y));
                result = 31 * result + tile.getWaterLevel(x,y);
                result = 31 * result + tile.getTerrain(x,y).ordinal();
                result = 31 * result + tile.getLayerValue(Biome.INSTANCE,x,y);
                result = 31 * result + (tile.getBitLayerValue(ReadOnly.INSTANCE,x,y) ? 1 : 0);
                result = 31 * result + (tile.getBitLayerValue(River.INSTANCE,x,y) ? 1 : 0);
            }
        }
        return result;
    }
}
