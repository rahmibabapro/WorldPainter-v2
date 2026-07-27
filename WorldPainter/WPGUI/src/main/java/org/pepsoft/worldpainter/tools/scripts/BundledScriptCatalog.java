package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Catalog of scripts bundled in {@code org/pepsoft/worldpainter/scripts/}.
 */
public final class BundledScriptCatalog {
    public enum Category {
        RIVERS("Rivers"),
        ROADS("Roads"),
        SNOW("Snow"),
        GLOBALS("Global Presets");

        Category(String label) {
            this.label = label;
        }

        public final String label;
    }

    public record BundledScript(Category category, String id, String resourcePath, String fileName) {
        public String displayName() {
            return id.replace('_', ' ');
        }
    }

    private static final String PREFIX = "org/pepsoft/worldpainter/scripts/";
    private static final List<BundledScript> SCRIPTS = List.of(
            new BundledScript(Category.RIVERS, "river_script", PREFIX + "rivers/river_script.js", "river_script.js"),
            new BundledScript(Category.RIVERS, "river_from_line", PREFIX + "rivers/river_from_line.js", "river_from_line.js"),
            new BundledScript(Category.ROADS, "road_flatten", PREFIX + "roads/road_flatten.js", "road_flatten.js"),
            new BundledScript(Category.SNOW, "snowify", PREFIX + "snow/snowify.js", "snowify.js"),
            new BundledScript(Category.GLOBALS, "stone_grass_slope", PREFIX + "globals/global_ops_stone_grass_45deg.js", "global_ops_stone_grass_45deg.js"),
            new BundledScript(Category.GLOBALS, "remove_water_ge1", PREFIX + "globals/global_remove_water_ge1.js", "global_remove_water_ge1.js"),
            new BundledScript(Category.GLOBALS, "set_water_level_0", PREFIX + "globals/global_set_water_level_0.js", "global_set_water_level_0.js"),
            new BundledScript(Category.GLOBALS, "dry_to_stone", PREFIX + "globals/global_remove_water_ge0_make_stone.js", "global_remove_water_ge0_make_stone.js"),
            new BundledScript(Category.GLOBALS, "realistic_snow", PREFIX + "globals/global_realistic_snow.js", "global_realistic_snow.js")
    );

    private BundledScriptCatalog() {
    }

    public static List<BundledScript> getAll() {
        return SCRIPTS;
    }

    public static List<BundledScript> getByCategory(Category category) {
        return SCRIPTS.stream().filter(s -> s.category() == category).toList();
    }

    public static BundledScript find(Category category, String id) {
        return SCRIPTS.stream().filter(s -> s.category() == category && s.id().equals(id)).findFirst().orElse(null);
    }

    public static File materialise(BundledScript script) throws IOException {
        final File cacheDir = new File(new File(Configuration.getConfigDir(), "scripts"), "bundled-cache");
        if (! cacheDir.exists() && ! cacheDir.mkdirs()) {
            throw new IOException("Could not create script cache directory " + cacheDir);
        }
        final File target = new File(cacheDir, script.category().name().toLowerCase() + "_" + script.fileName());
        final ClassLoader cl = BundledScriptCatalog.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(script.resourcePath())) {
            if (in == null) {
                throw new IOException("Bundled script not found: " + script.resourcePath());
            }
            final byte[] bytes = in.readAllBytes();
            if ((! target.exists()) || target.lastModified() < getResourceTimestamp(cl, script.resourcePath())) {
                Files.write(target.toPath(), bytes);
            }
        }
        return target;
    }

    public static List<File> materialiseAll() throws IOException {
        final List<File> files = new ArrayList<>();
        for (BundledScript script: SCRIPTS) {
            files.add(materialise(script));
        }
        return Collections.unmodifiableList(files);
    }

    private static long getResourceTimestamp(ClassLoader cl, String resourcePath) throws IOException {
        final URL url = cl.getResource(resourcePath);
        if (url == null) {
            return 0L;
        }
        if ("jar".equals(url.getProtocol())) {
            final JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                final Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    final JarEntry entry = entries.nextElement();
                    if (resourcePath.equals(entry.getName())) {
                        return entry.getTime();
                    }
                }
            }
        } else {
            try {
                return Files.getLastModifiedTime(java.nio.file.Path.of(url.toURI())).toMillis();
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }
        return System.currentTimeMillis();
    }
}
