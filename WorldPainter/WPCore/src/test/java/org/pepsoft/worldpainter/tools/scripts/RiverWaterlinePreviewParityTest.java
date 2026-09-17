package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.RiverWaterlineDetail;

import java.awt.Rectangle;

import static org.junit.Assert.*;

/** Preview must use planned water; caching preview must not zero apply markers. */
public class RiverWaterlinePreviewParityTest {
    @Test public void previewFindsWaterlineBanksOnDryWorldBeforeApply() {
        Dimension d = dryGrass();
        ShallowRiverCarver plan = carver(d);
        assertTrue(plan.getLastRejection(), plan.addPath(new int[]{20, 108}, new int[]{64, 64}));
        long revision = d.getChangeNo();
        var preview = plan.previewCells();
        assertEquals(revision, d.getChangeNo());
        long previewBanks = preview.stream().filter(ShallowRiverCarver.PreviewCell::waterlineBank).count();
        assertTrue("planned water must yield waterline lips before apply; got " + previewBanks
                + "; " + plan.waterlineBankSummary(), previewBanks > 0);
        assertEquals(previewBanks, plan.getWaterlineBankCells());
    }

    @Test public void previewThenApplyWritesSameWaterlineMarkerCount() {
        Dimension d = dryGrass();
        ShallowRiverCarver plan = carver(d);
        assertTrue(plan.addPath(new int[]{20, 108}, new int[]{64, 64}));
        long previewBanks = plan.previewCells().stream().filter(ShallowRiverCarver.PreviewCell::waterlineBank).count();
        assertTrue(previewBanks > 0);
        // Second preview must not clear the cache into an empty live-water pass.
        assertEquals(previewBanks, plan.previewCells().stream().filter(ShallowRiverCarver.PreviewCell::waterlineBank).count());
        plan.apply();
        int marked = 0;
        for (int y = 50; y <= 78; y++) for (int x = 18; x <= 110; x++) {
            if (d.getBitLayerValueAt(RiverWaterlineDetail.INSTANCE, x, y)) marked++;
        }
        assertEquals("preview cache must not starve apply markers", previewBanks, marked);
    }

    private static Dimension dryGrass() {
        Dimension d = TestData.createDimension(new Rectangle(0, 0, 128, 128), 100);
        d.setSurfaceSmoothing(Dimension.SurfaceSmoothing.NONE);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            d.setHeightAt(x, y, 100);
            d.setWaterLevelAt(x, y, 0);
            d.setTerrainAt(x, y, Terrain.GRASS);
        }
        return d;
    }

    private static ShallowRiverCarver carver(Dimension d) {
        ShallowRiverCarver plan = new ShallowRiverCarver(d, 5, 12, 1.1, true, true, 1337, null);
        plan.enableTerrainPreservation();
        plan.setLowerInteriorDirt(true);
        plan.setWaterlineBankDetail(true);
        return plan;
    }
}
