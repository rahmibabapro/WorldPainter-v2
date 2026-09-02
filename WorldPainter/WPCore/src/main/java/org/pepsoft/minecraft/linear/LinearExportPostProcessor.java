package org.pepsoft.minecraft.linear;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * After Anvil export, optionally write sibling {@code .linear} region files for AgeOfMC.
 * Leaves {@code .mca} in place (vanilla/FO default).
 */
public final class LinearExportPostProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(LinearExportPostProcessor.class);

    private LinearExportPostProcessor() {
    }

    public static void convertWorldRegions(Path worldDir, int zstdLevel) throws IOException {
        if (worldDir == null || ! Files.isDirectory(worldDir)) {
            return;
        }
        int converted = 0;
        try (Stream<Path> walk = Files.walk(worldDir)) {
            for (Path mca : (Iterable<Path>) walk.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("r.") && name.endsWith(".mca");
            })::iterator) {
                Path linear = mca.resolveSibling(mca.getFileName().toString().replace(".mca", ".linear"));
                try {
                    McaToLinearConverter.convert(mca, linear, zstdLevel);
                    converted++;
                } catch (IOException e) {
                    LOGGER.warn("Failed to convert {} to Linear: {}", mca, e.toString());
                }
            }
        }
        LOGGER.info("Linear post-process: converted {} region file(s) under {}", converted, worldDir);
    }
}
