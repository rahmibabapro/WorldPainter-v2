package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.pepsoft.worldpainter.tools.scripts.DrawnRiverSession;

import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertTrue;

public class DrawnRiverDialogTest {
    @Test public void abortMessagesDistinguishCancelTimeoutAndError() {
        assertTrue(DrawnRiverDialog.explain(new CancellationException("İptal edildi; dünya değiştirilmedi.")).startsWith("İptal"));
        assertTrue(DrawnRiverDialog.explain(new CancellationException("Planlama bütçesi doldu; dünya değiştirilmedi.")).startsWith("Süre doldu"));
        assertTrue(DrawnRiverDialog.explain(new IllegalStateException("beklenmeyen")).startsWith("Hesap hatası"));
        String npe = DrawnRiverDialog.explain(new java.util.concurrent.ExecutionException(new NullPointerException()));
        assertTrue(npe.contains("beklenmeyen boş değer") || npe.contains("NPE"));
        String empty = DrawnRiverDialog.explain(new java.util.concurrent.ExecutionException(new java.util.NoSuchElementException()));
        assertTrue(empty.contains("Outlet") || empty.contains("uç"));
    }
    @Test public void previewStatusIncludesIdentityStageAndMaxCut() {
        var p=new DrawnRiverSession.Preview(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),
                java.util.List.of("ok"),java.util.List.of(),java.util.Set.of(),java.util.Set.of(),java.util.Set.of(),
                java.util.List.of(),8,6,1,0,DrawnRiverSession.Stage.DEEP_TERRAIN,false,DrawnRiverSession.UiClass.OK,
                16,"2.0.0 (test) · nehri-hazırla/joint-16");
        String text=DrawnRiverDialog.formatPreview(p);
        assertTrue(text.contains("nehri-hazırla"));
        assertTrue(text.contains("Kaynak 8"));
        assertTrue(text.contains("birleşim 6"));
        assertTrue(text.contains("parça 0"));
        assertTrue(text.contains("Azami ek kazı: 16"));
        assertTrue(text.contains("DEEP_TERRAIN"));
    }
}
