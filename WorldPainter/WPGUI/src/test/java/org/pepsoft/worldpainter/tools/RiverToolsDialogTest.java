package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Checks display logic without constructing Swing windows or changing a world. */
public class RiverToolsDialogTest {
    @Test
    public void exportHintExplainsLocalWetGraniteWithoutAskingForAGlobalSettingChange() {
        for (Dimension.SurfaceSmoothing mode : Dimension.SurfaceSmoothing.values()) {
            final String hint = RiverToolsDialog.exportHintText(mode);
            assertTrue(hint.contains("işaretli, ıslak granit"));
            assertTrue(hint.contains("global ayardan bağımsız"));
            assertTrue(hint.contains("2×2×2 slab/merdiven"));
            assertTrue(hint.contains("kuru araziyi ve eski işaretsiz nehirleri etkilemez"));
            assertTrue(hint.contains("ayarı korunur"));
            assertFalse(hint.contains("etkinleştirin"));
            assertFalse(hint.contains("açmalısınız"));
        }
    }

    @Test
    public void exportHintDoesNotPromiseThatEveryGraniteBlockBecomesAStair() {
        final String hint = RiverToolsDialog.exportHintText(Dimension.SurfaceSmoothing.NONE);
        assertTrue(hint.contains("Düz yüzeyler, dik uçurumlar"));
        assertTrue(hint.contains("taşıyıcı bloklar tam kalır"));
        assertTrue(hint.contains("her granit blok merdivene dönüşmez"));
        assertTrue(hint.contains("Dünya geneli: kapalı"));
        assertTrue(RiverToolsDialog.exportHintText(Dimension.SurfaceSmoothing.SLABS_AND_STAIRS)
                .contains("Dünya geneli: açık"));
    }

    @Test
    public void drawingBrushInstructionsDescribeTheDedicatedOneBlockCentrelineWorkflow() {
        final String help = RiverToolsDialog.drawingBrushHelpText();
        assertTrue(help.contains("River Path"));
        assertTrue(help.contains("Pencil"));
        assertTrue(help.contains("tek blokluk merkez hattı"));
        assertTrue(help.contains("Sol tuşla sürükleyerek"));
        assertTrue(help.contains("sağ tuşla hatayı sil"));
        assertTrue(help.contains("genişliğini fırça değil"));
        assertTrue(help.contains("Uygula'ya bas"));
    }
}
