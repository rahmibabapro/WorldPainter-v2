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
 * <p>
 * Scripts remain registered for internal callers (Map Quick Preset, river/road/snow dialogs).
 * The Script Library UI only lists entries with {@link BundledScript#inLibrary()}.
 */
public final class BundledScriptCatalog {
    public enum Category {
        RIVERS,
        ROADS,
        SNOW,
        GLOBALS
    }

    public record BundledScript(Category category, String id, String resourcePath, String fileName, String displayName, boolean inLibrary) {
    }

    private static final String PREFIX = "org/pepsoft/worldpainter/scripts/";
    private static final List<BundledScript> SCRIPTS = List.of(
            // Internal only — not shown in Script Library
            new BundledScript(Category.RIVERS, "river_script", PREFIX + "rivers/river_script.js", "river_script.js", "river script", false),
            new BundledScript(Category.RIVERS, "river_from_line", PREFIX + "rivers/river_from_line.js", "river_from_line.js", "river from line", false),
            new BundledScript(Category.ROADS, "road_flatten", PREFIX + "roads/road_flatten.js", "road_flatten.js", "road flatten", false),
            new BundledScript(Category.SNOW, "snowify", PREFIX + "snow/snowify.js", "snowify.js", "snowify", false),
            new BundledScript(Category.GLOBALS, "remove_water_ge1", PREFIX + "globals/global_remove_water_ge1.js", "global_remove_water_ge1.js", "remove water ge1", false),
            new BundledScript(Category.GLOBALS, "set_water_level_0", PREFIX + "globals/global_set_water_level_0.js", "global_set_water_level_0.js", "set water level 0", false),
            new BundledScript(Category.GLOBALS, "dry_to_stone", PREFIX + "globals/global_remove_water_ge0_make_stone.js", "global_remove_water_ge0_make_stone.js", "dry to stone", false),
            new BundledScript(Category.GLOBALS, "realistic_snow", PREFIX + "globals/global_realistic_snow.js", "global_realistic_snow.js", "realistic snow", false),
            // Script Library (flat list)
            new BundledScript(Category.GLOBALS, "stone_grass_slope", PREFIX + "globals/global_ops_stone_grass_45deg.js", "global_ops_stone_grass_45deg.js", "Taş Çimen", true)
    );

    private BundledScriptCatalog() {
    }

    public static List<BundledScript> getAll() {
        return SCRIPTS;
    }

    /** Scripts shown in the Script Library menu (flat, no categories). */
    public static List<BundledScript> getLibraryScripts() {
        return SCRIPTS.stream().filter(BundledScript::inLibrary).toList();
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
