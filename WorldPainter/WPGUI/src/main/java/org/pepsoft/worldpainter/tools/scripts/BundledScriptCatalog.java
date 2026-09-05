package org.pepsoft.worldpainter.tools.scripts;

import org.pepsoft.worldpainter.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

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
            new BundledScript(Category.GLOBALS, "stone_grass_slope", PREFIX + "globals/global_ops_stone_grass_45deg.js", "global_ops_stone_grass_45deg.js", "Taş Çimen", true),
            // Advanced variants stay available through Run script; the library exposes the safe one-click preset.
            new BundledScript(Category.GLOBALS, "terrain_texture_painter", PREFIX + "globals/terrain_texture_painter.js", "terrain_texture_painter.js", "Terrain Texture Painter", false),
            new BundledScript(Category.SNOW, "snowify_smooth", PREFIX + "snow/snowify.js", "snowify_smooth.js", "Snowify Smooth", false),
            new BundledScript(Category.GLOBALS, "axiom_mountain_smooth_snow", PREFIX + "globals/axiom_mountain_smooth_snow.js", "axiom_mountain_smooth_snow.js", "Axiom Mountain Smooth Snow", false),
            new BundledScript(Category.GLOBALS, "axiom_mountain_style", PREFIX + "globals/axiom_mountain_smooth_snow.js", "axiom_mountain_style.js", "Axiom Dağ Stili (Taş, Çimen, Kar)", false),
            new BundledScript(Category.GLOBALS, "axiom_blueprint_texture", PREFIX + "globals/axiom_blueprint_texture.js", "axiom_blueprint_texture.js", "Axiom Blueprint Dağ + Düzlük + Kar", true)
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
        return materialise(script, cacheDir.toPath());
    }

    /** Separate cache root keeps tests and other isolated callers out of the user's profile. */
    static synchronized File materialise(BundledScript script, Path cacheDir) throws IOException {
        cacheDir = cacheDir.toAbsolutePath().normalize();
        final Path target = cacheDir.resolve(script.category().name().toLowerCase(Locale.ROOT) + "_" + script.fileName()).normalize();
        if (! cacheDir.equals(target.getParent())) {
            throw new IOException("Bundled script file name escapes the cache directory: " + script.fileName());
        }
        final ClassLoader cl = BundledScriptCatalog.class.getClassLoader();
        final byte[] bytes;
        try (InputStream in = cl.getResourceAsStream(script.resourcePath())) {
            if (in == null) {
                throw new IOException("Bundled script not found: " + script.resourcePath());
            }
            bytes = in.readAllBytes();
        }
        Files.createDirectories(cacheDir);
        final boolean exists = Files.exists(target, NOFOLLOW_LINKS);
        if (exists && ! Files.isRegularFile(target, NOFOLLOW_LINKS)) {
            throw new IOException("Script cache target is not a regular file: " + target);
        }
        // Timestamps are not content versions: a copied/edited cache may be newer
        // than every future JAR. Identical content needs neither a backup nor a write.
        if (exists && Files.size(target) == bytes.length && Arrays.equals(Files.readAllBytes(target), bytes)) {
            return target.toFile();
        }
        final Path staged = Files.createTempFile(cacheDir, ".bundled-script-", ".tmp");
        try {
            Files.write(staged, bytes);
            if (Files.exists(target, NOFOLLOW_LINKS)) {
                if (! Files.isRegularFile(target, NOFOLLOW_LINKS)) {
                    throw new IOException("Script cache target changed to a non-regular file: " + target);
                }
                final Path backupRoot = cacheDir.resolve("backups");
                Files.createDirectories(backupRoot);
                final String prefix = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + "-";
                final Path backup = Files.createTempDirectory(backupRoot, prefix).resolve(target.getFileName());
                // Never overwrite a customised file unless its complete predecessor
                // was successfully preserved. A failed backup leaves the live file alone.
                Files.copy(target, backup, COPY_ATTRIBUTES);
                if (Files.mismatch(target, backup) != -1) {
                    throw new IOException("Script cache changed while it was being backed up: " + target);
                }
            }
            try {
                Files.move(staged, target, ATOMIC_MOVE, REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Script cache does not support safe atomic replacement: " + cacheDir, e);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
        return target.toFile();
    }

    /**
     * Only an exact shipped script may opt into the host-managed edit transaction.
     * A filename, copied header or edited script is not evidence of that contract.
     * The native blueprint script owns its own transaction and is always excluded.
     */
    public static boolean usesHostUndoTransaction(String source) {
        return source != null && HostUndoSources.SOURCES.contains(source);
    }

    public static List<File> materialiseAll() throws IOException {
        final List<File> files = new ArrayList<>();
        for (BundledScript script: SCRIPTS) {
            files.add(materialise(script));
        }
        return Collections.unmodifiableList(files);
    }

    private static final class HostUndoSources {
        private static Set<String> load() {
            final Set<String> result = new HashSet<>(), visited = new HashSet<>();
            String nativeSource = null;
            for (BundledScript script : SCRIPTS) {
                if (! visited.add(script.resourcePath())) continue;
                try (InputStream in = BundledScriptCatalog.class.getClassLoader().getResourceAsStream(script.resourcePath())) {
                    if (in == null) continue;
                    final String source = new String(in.readAllBytes(), Charset.defaultCharset());
                    if (script.resourcePath().equals(PREFIX + "globals/axiom_blueprint_texture.js")) nativeSource = source;
                    else result.add(source);
                } catch (IOException e) {
                    // Failure to verify provenance must not silently run a known
                    // editing script without its undo/rollback protection.
                    throw new UncheckedIOException("Could not verify bundled script " + script.resourcePath(), e);
                }
            }
            result.remove(nativeSource);
            return Set.copyOf(result);
        }

        private static final Set<String> SOURCES = load();
    }
}
