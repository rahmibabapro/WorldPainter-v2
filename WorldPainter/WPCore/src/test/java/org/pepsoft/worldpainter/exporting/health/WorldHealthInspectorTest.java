package org.pepsoft.worldpainter.exporting.health;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class WorldHealthInspectorTest {

    @Test
    public void testHealthInspectorFlagsOverflowSector() throws Exception {
        File tempWorld = Files.createTempDirectory("wp-health-bad-").toFile();
        try {
            File regionDir = new File(tempWorld, "region");
            regionDir.mkdirs();
            File badMca = new File(regionDir, "r.0.0.mca");
            try (FileOutputStream fos = new FileOutputStream(badMca)) {
                byte[] header = new byte[8192];
                // Claim offset far beyond file
                header[0] = 0; header[1] = 0; header[2] = 50; header[3] = 1;
                fos.write(header);
            }
            WorldHealthInspector.HealthReport report = WorldHealthInspector.inspectExportedWorld(tempWorld);
            assertTrue(report.hasErrors());
        } finally {
            deleteRecursively(tempWorld);
        }
    }

    @Test
    public void testHealthInspectorValidatesMca() throws Exception {
        File tempWorld = Files.createTempDirectory("wp-health-test-").toFile();
        try {
            File regionDir = new File(tempWorld, "region");
            regionDir.mkdirs();

            File validMca = new File(regionDir, "r.0.0.mca");
            try (FileOutputStream fos = new FileOutputStream(validMca)) {
                byte[] header = new byte[8192];
                // Sector 2 (offset 2, count 1) for first chunk
                header[0] = 0; header[1] = 0; header[2] = 2; header[3] = 1;
                fos.write(header);
                // Sector 2: length=1 (only compression byte), compression=2 (zlib)
                byte[] sectorData = new byte[4096];
                sectorData[0] = 0; sectorData[1] = 0; sectorData[2] = 0; sectorData[3] = 1;
                sectorData[4] = 2;
                fos.write(sectorData);
            }

            WorldHealthInspector.HealthReport report = WorldHealthInspector.inspectExportedWorld(tempWorld);
            assertNotNull(report);
            assertFalse("Report should have no fatal errors: " + report.getIssues(), report.hasErrors());
            assertEquals(1, report.verifiedRegions);
            assertEquals(1, report.verifiedChunks);
            assertTrue(report.summaryLine().contains("OK"));
            assertTrue(report.summaryLine().contains("header/sector"));
            assertEquals(1, report.regionFoldersScanned);
        } finally {
            deleteRecursively(tempWorld);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
