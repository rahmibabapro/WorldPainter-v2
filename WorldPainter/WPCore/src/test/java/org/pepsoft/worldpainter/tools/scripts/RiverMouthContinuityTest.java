package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.RiverSurfaceDetail;
import org.pepsoft.worldpainter.layers.Void;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** A contact with one old water voxel is not yet a full-width river mouth. */
public class RiverMouthContinuityTest {
    @Test
    public void pointedCoastDoesNotPinchTheCompletedWetCoreToOneColumn() {
        final Dimension dimension = terrain(false);
        final List<Cell> before = snapshot(dimension);
        final ShallowRiverCarver plan = plan(dimension, null);
        assertAccepted(plan, new int[] {20, 96}, new int[] {64, 64});
        assertEquals("Mouth planning is read-only", before, snapshot(dimension));
        assertFalse("Avoidance must include newly planned mouth cells after the last waypoint",
                plan.isFootprintAllowed((x, y) -> x > 96));
        assertTrue("Existing receiving water is not a new terrain edit",
                plan.isFootprintAllowed((x, y) -> x >= 116));

        final ShallowRiverCarver.Result result = plan.apply();

        for (int x = 92; x <= 104; x++) for (int y = 61; y <= 67; y++) {
            assertWet(dimension, x, y);
        }
        assertSafety(before, dimension, result);
    }

    @Test
    public void diagonalPointedCoastKeepsTheWholeObliqueWetCoreConnected() {
        final Dimension dimension = terrain(true);
        final List<Cell> before = snapshot(dimension);
        final ShallowRiverCarver plan = plan(dimension, null);
        assertAccepted(plan, new int[] {20, 80}, new int[] {20, 80});
        assertEquals(before, snapshot(dimension));

        final ShallowRiverCarver.Result result = plan.apply();

        for (int along = -3; along <= 6; along++) for (int across = -2; across <= 2; across++) {
            assertWet(dimension, 80 + along + across, 80 + along - across);
        }
        assertSafety(before, dimension, result);
    }

    @Test
    public void protectedMouthExtensionRejectsWithoutCarvingUpToTheBarrier() {
        final Dimension dimension = terrain(false);
        for (int y = 0; y < 128; y++) dimension.setBitLayerValueAt(Void.INSTANCE, 99, y, true);
        final List<Cell> before = snapshot(dimension);
        final ShallowRiverCarver plan = plan(dimension, null);

        assertFalse("Do not accept a pinched contact when a safe full-width connection is blocked",
                plan.addPath(new int[] {20, 96}, new int[] {64, 64}));

        assertEquals(before, snapshot(dimension));
        assertEquals(0, plan.apply().changedCells());
        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void aOneColumnWaterThreadIsNotAStableFullWidthReceivingRiver() {
        final Dimension dimension = flat();
        for (int x = 96; x < 128; x++) water(dimension, x, 64);
        final List<Cell> before = snapshot(dimension);
        final ShallowRiverCarver plan = plan(dimension, null);

        assertFalse("An isolated water thread must not justify a one-column river mouth",
                plan.addPath(new int[] {20, 96}, new int[] {64, 64}));

        assertEquals(before, snapshot(dimension));
        assertEquals(0, plan.apply().changedCells());
        assertEquals(before, snapshot(dimension));
    }

    @Test
    public void laterSameLevelTributaryNeverRaisesOrNarrowsAnAcceptedWetBed() {
        final Dimension expected = flat(), actual = flat();
        final ShallowRiverCarver first = plan(expected, null), combined = plan(actual, null);
        assertAccepted(first, new int[] {20, 108}, new int[] {64, 64});
        first.apply();
        final List<Cell> before = snapshot(actual);
        assertAccepted(combined, new int[] {20, 108}, new int[] {64, 64});
        assertAccepted(combined, new int[] {90, 108}, new int[] {62, 80});
        assertEquals(before, snapshot(actual));

        final ShallowRiverCarver.Result result = combined.apply();

        assertEquals(2, result.paths());
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            if (expected.getWaterLevelAt(x, y) <= expected.getIntHeightAt(x, y)) continue;
            assertWet(actual, x, y);
            assertTrue("A tributary's shallower edge may not overwrite the accepted bed at " + x + "," + y,
                    actual.getHeightAt(x, y) <= expected.getHeightAt(x, y));
        }
        assertSafety(before, actual, result);
    }

    @Test
    public void plannedReceivingRiverAlsoGetsAFullWidthJoinWithoutApplyingItFirst() {
        final Dimension dimension = flat();
        final List<Cell> before = snapshot(dimension);
        final ShallowRiverCarver plan = plan(dimension, null);
        assertAccepted(plan, new int[] {96, 96}, new int[] {20, 108});
        assertAccepted(plan, new int[] {20, 94}, new int[] {64, 64});
        assertEquals(before, snapshot(dimension));

        final ShallowRiverCarver.Result result = plan.apply();

        for (int x = 90; x <= 97; x++) for (int y = 61; y <= 67; y++) assertWet(dimension, x, y);
        assertEquals(2, result.paths());
        assertSafety(before, dimension, result);
    }

    @Test
    public void wetJunctionGeometryDoesNotDependOnWhichDryShoulderWasPlannedFirst() {
        final Dimension mainFirst = flat(), branchFirst = flat();
        final ShallowRiverCarver first = plan(mainFirst, null), second = plan(branchFirst, null);
        assertAccepted(first, new int[] {96, 96}, new int[] {20, 108});
        assertAccepted(first, new int[] {20, 94}, new int[] {64, 64});
        assertAccepted(second, new int[] {20, 94}, new int[] {64, 64});
        assertAccepted(second, new int[] {96, 96}, new int[] {20, 108});

        first.apply();
        second.apply();

        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            assertEquals("Wet geometry must be a union, not a dry-placeholder tie at " + x + "," + y,
                    mainFirst.getHeightAt(x, y), branchFirst.getHeightAt(x, y), 0f);
            assertEquals(mainFirst.getWaterLevelAt(x, y), branchFirst.getWaterLevelAt(x, y));
        }
        for (Dimension dimension : new Dimension[] {mainFirst, branchFirst}) {
            for (int x = 90; x <= 97; x++) for (int y = 61; y <= 67; y++) assertWet(dimension, x, y);
        }
    }

    @Test
    public void cancelledApplicationRestoresLocalSurfaceMetadataAsWellAsTheMouth() {
        final Dimension dimension = terrain(false);
        dimension.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, 40, 64, true);
        final List<Cell> before = snapshot(dimension);
        final boolean[] sawNewDetail = {false};
        final ScriptProgress cancellation = new ScriptProgress(null, null) {
            @Override public void checkForCancel() { }
            @Override public void setProgress(double value) {
                for (Cell original : before) {
                    if (!original.detail && dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, original.x, original.y)) {
                        sawNewDetail[0] = true;
                        break;
                    }
                }
                throw new ScriptingContext.InterruptedException();
            }
        };
        final ShallowRiverCarver plan = plan(dimension, cancellation);
        assertAccepted(plan, new int[] {20, 96}, new int[] {64, 64});

        assertThrows(ScriptingContext.InterruptedException.class, plan::apply);

        assertTrue("Cancellation must occur after real local-detail writes", sawNewDetail[0]);
        assertEquals("Rollback must restore every old marker, including a previously enabled one",
                before, snapshot(dimension));
    }

    @Test
    public void changedLocalDetailMarkerInvalidatesAStalePreview() {
        final Dimension dimension = terrain(false);
        final ShallowRiverCarver plan = plan(dimension, null);
        assertAccepted(plan, new int[] {20, 96}, new int[] {64, 64});
        dimension.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, 64, 64, true);
        final List<Cell> afterUserEdit = snapshot(dimension);

        assertThrows(IllegalStateException.class, plan::apply);

        assertEquals(afterUserEdit, snapshot(dimension));
    }

    @Test
    public void finalFootprintGuardChecksCurrentProtectionsEvenWithoutACustomMask() {
        final Dimension dimension = terrain(false);
        final ShallowRiverCarver plan = plan(dimension, null);
        assertAccepted(plan, new int[] {20, 96}, new int[] {64, 64});
        assertTrue(plan.isFootprintAllowed(null));
        dimension.setBitLayerValueAt(Void.INSTANCE, 64, 64, true);
        final List<Cell> afterUserEdit = snapshot(dimension);

        assertFalse(plan.isFootprintAllowed(null));

        assertEquals("Mask validation itself must never write", afterUserEdit, snapshot(dimension));
    }

    @Test
    public void soilOnlyChannelClearsStaleDetailOnlyOnItsNewWetCells() {
        final Dimension dimension = flat();
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            dimension.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, y, true);
        }
        final List<Cell> before = snapshot(dimension);
        final ShallowRiverCarver plan = new ShallowRiverCarver(dimension, 5, 12, 1.10, true, false, 733L, null);
        plan.enableTerrainPreservation();
        assertAccepted(plan, new int[] {20, 108}, new int[] {64, 64});

        final ShallowRiverCarver.Result result = plan.apply();

        assertEquals(0, result.graniteCells());
        for (Cell original : before) {
            final Cell after = cell(dimension, original.x, original.y);
            if (after.water > Math.round(after.height)) {
                assertSame(Terrain.DIRT, after.terrain);
                assertFalse(after.detail);
            } else assertEquals("Do not clear metadata on dry terrain", original, after);
        }
    }

    @Test
    public void aLongSafeValleyAcceptsTheCoastAndNeighbouringInteriorMouthCoordinates() {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 1024, 128), 100);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 1024; x++) {
            final boolean sea = x >= 996;
            final double side = Math.min(100, Math.max(0, Math.abs(y - 64) - 8) * 0.6);
            final double height = 0.375 + 110 * Math.max(0, (995.0 - x) / 975) + side;
            dimension.setHeightAt(x, y, sea ? -2f : (float) height);
            dimension.setWaterLevelAt(x, y, 0);
            dimension.setTerrainAt(x, y, sea ? Terrain.SAND : Terrain.GRASS);
        }
        final long before = dimension.getChangeNo();
        // All these endpoints are in the same broad, safe valley. Area-level
        // outlet deduplication must not hide them behind a jittered hillside
        // target such as (997,75), whose dry approach genuinely exceeds maxCut.
        for (int x : new int[] {996, 997, 998, 999}) {
            for (int y : new int[] {61, 63, 64, 65, 67}) {
                final ShallowRiverCarver plan = plan(dimension, null);
                final boolean accepted = plan.addPath(new int[] {20, x}, new int[] {64, y});
                assertTrue("Safe long-valley endpoint " + x + "," + y + ": " + plan.getLastRejection(), accepted);
                assertEquals(1, plan.getAcceptedPaths());
                assertEquals("Coordinate probes stay read-only", before, dimension.getChangeNo());
                assertEquals(1.85, plan.getMaximumCut(), 0.000001);
                assertEquals(0, plan.getMaximumFill(), 0);
            }
        }
    }

    private static Dimension terrain(boolean diagonal) {
        final Dimension dimension = flat();
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            if (diagonal ? x + y - 160 >= 2 * Math.abs(x - y) : x >= 96 + 2 * Math.abs(y - 64)) {
                water(dimension, x, y);
            }
        }
        return dimension;
    }

    private static Dimension flat() {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            dimension.setHeightAt(x, y, 100f);
            dimension.setWaterLevelAt(x, y, 0);
            dimension.setTerrainAt(x, y, Terrain.GRASS);
            dimension.setLayerValueAt(Biome.INSTANCE, x, y, 4);
            dimension.setBitLayerValueAt(Frost.INSTANCE, x, y, false);
            dimension.setBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, y, false);
        }
        return dimension;
    }

    private static void water(Dimension dimension, int x, int y) {
        dimension.setHeightAt(x, y, 98f);
        dimension.setWaterLevelAt(x, y, 100);
        dimension.setTerrainAt(x, y, Terrain.SAND);
        dimension.setLayerValueAt(Biome.INSTANCE, x, y, 0);
    }

    private static ShallowRiverCarver plan(Dimension dimension, ScriptProgress progress) {
        final ShallowRiverCarver plan = new ShallowRiverCarver(dimension, 5, 12, 1.10, true, true, 733L, progress);
        plan.enableTerrainPreservation();
        return plan;
    }

    private static void assertAccepted(ShallowRiverCarver plan, int[] xs, int[] ys) {
        final boolean accepted = plan.addPath(xs, ys);
        assertTrue(plan.getLastRejection(), accepted);
    }

    private static void assertWet(Dimension dimension, int x, int y) {
        assertEquals("Water level at " + x + "," + y, 100, dimension.getWaterLevelAt(x, y));
        assertTrue("Full wet corridor at " + x + "," + y,
                dimension.getWaterLevelAt(x, y) > dimension.getIntHeightAt(x, y));
    }

    private static void assertSafety(List<Cell> before, Dimension dimension, ShallowRiverCarver.Result result) {
        assertEquals(0, result.raisedCells());
        assertEquals(0, result.maximumFill(), 0);
        assertTrue(result.maximumCut() <= 1.85);
        int detailed = 0;
        for (Cell original : before) {
            final Cell after = cell(dimension, original.x, original.y);
            assertTrue(after.height <= original.height);
            assertTrue(original.height - after.height <= 1.85);
            if (original.water > Math.round(original.height) || after.water <= Math.round(after.height)) {
                assertEquals("Existing water and all final dry terrain must be untouched", original, after);
            } else {
                assertTrue(after.water - after.height <= 1.10 + 1.0 / 256);
                assertEquals("Only new wet granite receives local slab/stair metadata",
                        after.terrain == Terrain.GRANITE, after.detail);
                if (after.detail) detailed++;
            }
        }
        assertTrue("Granite detail must be produced without enabling global smoothing", detailed > 0);
    }

    private static List<Cell> snapshot(Dimension dimension) {
        final List<Cell> result = new ArrayList<>(128 * 128);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) result.add(cell(dimension, x, y));
        return result;
    }

    private static Cell cell(Dimension dimension, int x, int y) {
        return new Cell(x, y, dimension.getHeightAt(x, y), dimension.getWaterLevelAt(x, y),
                dimension.getTerrainAt(x, y), dimension.getLayerValueAt(Biome.INSTANCE, x, y),
                dimension.getBitLayerValueAt(Frost.INSTANCE, x, y),
                dimension.getBitLayerValueAt(Void.INSTANCE, x, y),
                dimension.getBitLayerValueAt(RiverSurfaceDetail.INSTANCE, x, y));
    }

    private record Cell(int x, int y, float height, int water, Terrain terrain,
                        int biome, boolean frost, boolean voidCell, boolean detail) { }
}
