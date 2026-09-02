package org.pepsoft.worldpainter.terrain.hydrology.river;

import org.junit.Test;
import java.awt.Rectangle;

import static org.junit.Assert.*;

public class CompanionPluginManifestTest {

    @Test
    public void testManifestJsonSerialization() {
        CompanionPluginManifest manifest = new CompanionPluginManifest("MyCustomWorld", 1700000000L);
        manifest.addCorridor(new Rectangle(0, 0, 128, 64));
        manifest.addCorridor(new Rectangle(128, 64, 256, 128));

        assertEquals(2, manifest.getFrozenCorridors().size());
        String json = manifest.toJson();

        assertNotNull(json);
        assertTrue(json.contains("\"world\": \"MyCustomWorld\""));
        assertTrue(json.contains("\"frozenCorridors\":"));
        assertTrue(json.contains("\"minX\": 0"));
        assertTrue(json.contains("\"maxX\": 128"));
    }
}
