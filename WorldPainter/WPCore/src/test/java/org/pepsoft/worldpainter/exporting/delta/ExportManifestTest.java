package org.pepsoft.worldpainter.exporting.delta;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class ExportManifestTest {

    @Test
    public void testManifestSaveAndLoadRoundtrip() throws Exception {
        File tempDir = Files.createTempDirectory("wp-manifest-test-").toFile();
        try {
            ExportManifest manifest = new ExportManifest("TestWorld", "org.pepsoft.anvil.1.20");
            manifest.putTileHash(0, 0, 0, 1234567890L);
            manifest.putTileHash(0, 1, 0, 9876543210L);
            manifest.putTileHash(0, 0, 1, 5555555555L);

            manifest.save(tempDir);

            ExportManifest loaded = ExportManifest.load(tempDir);
            assertNotNull(loaded);
            assertEquals("TestWorld", loaded.getWorldName());
            assertEquals("org.pepsoft.anvil.1.20", loaded.getPlatformId());
            assertEquals(3, loaded.getTileHashes().size());
            assertEquals(Long.valueOf(1234567890L), loaded.getTileHash(0, 0, 0));
            assertEquals(Long.valueOf(9876543210L), loaded.getTileHash(0, 1, 0));
            assertEquals(Long.valueOf(5555555555L), loaded.getTileHash(0, 0, 1));
        } finally {
            for (File f : tempDir.listFiles()) {
                f.delete();
            }
            tempDir.delete();
        }
    }
}
