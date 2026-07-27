package org.pepsoft.worldpainter.merging;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystemException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JavaWorldMergerFileInUseTest {
    @Test
    public void detectsEnglishSharingViolation() {
        assertTrue(JavaWorldMerger.isLikelyFileInUse(
                new IOException("The process cannot access the file because it is being used by another process")));
    }

    @Test
    public void detectsTurkishLockMessage() {
        assertTrue(JavaWorldMerger.isLikelyFileInUse(new IOException(
                "Ba\u015fka bir i\u015flem dosyan\u0131n bir b\u00f6l\u00fcm\u00fcn\u00fc kilitledi\u011finden bu i\u015flem dosyaya eri\u015femiyor")));
    }

    @Test
    public void detectsFileSystemExceptionReason() {
        final FileSystemException fse = new FileSystemException("map", null, "The file is being used by another process");
        assertTrue(JavaWorldMerger.isLikelyFileInUse(new IOException("copy failed", fse)));
    }

    @Test
    public void ignoresUnrelatedIoErrors() {
        assertFalse(JavaWorldMerger.isLikelyFileInUse(new IOException("Disk full")));
        assertFalse(JavaWorldMerger.isLikelyFileInUse(null));
    }
}
