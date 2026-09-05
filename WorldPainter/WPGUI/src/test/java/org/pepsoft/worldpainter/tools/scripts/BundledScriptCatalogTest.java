package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/** Every cache fixture is isolated: never resolve or modify Configuration's real profile. */
public class BundledScriptCatalogTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void missingCacheIsMaterialisedFromTheShippedBytes() throws Exception {
        final Path cache = temporary.getRoot().toPath().resolve("new-cache");
        final var script = river();
        final Path result = BundledScriptCatalog.materialise(script, cache).toPath();
        assertEquals(cache.resolve("rivers_river_script.js"), result);
        assertArrayEquals(resource(script), Files.readAllBytes(result));
        assertFalse(Files.exists(cache.resolve("backups")));
        assertNoStagingFiles(cache);
    }

    @Test public void identicalCacheContentIsNotRewrittenEvenWhenItsTimestampIsOld() throws Exception {
        final Path cache = temporary.newFolder("identical").toPath();
        final Path target = BundledScriptCatalog.materialise(river(), cache).toPath();
        final FileTime old = FileTime.fromMillis(1_500_000_000_000L);
        Files.setLastModifiedTime(target, old);
        assertEquals(target, BundledScriptCatalog.materialise(river(), cache).toPath());
        assertEquals(old, Files.getLastModifiedTime(target));
        assertFalse(Files.exists(cache.resolve("backups")));
        assertNoStagingFiles(cache);
    }

    @Test public void newerEditedCacheCannotHideTheCurrentScriptAndItsExactBytesAreBackedUp() throws Exception {
        final Path cache = temporary.newFolder("newer-edited").toPath();
        final Path target = cache.resolve("rivers_river_script.js");
        final byte[] customised = "// user customisation\r\nprint('mine');\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(target, customised);
        Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis() + 86_400_000L));

        BundledScriptCatalog.materialise(river(), cache);

        assertArrayEquals(resource(river()), Files.readAllBytes(target));
        final List<Path> backups = backups(cache);
        assertEquals(1, backups.size());
        assertEquals(target.getFileName(), backups.get(0).getFileName());
        assertArrayEquals(customised, Files.readAllBytes(backups.get(0)));
        assertNoStagingFiles(cache);
    }

    @Test public void repeatedEditsCreateDistinctBackupSubfoldersWithoutReplacingEarlierBackups() throws Exception {
        final Path cache = temporary.newFolder("repeated-edits").toPath();
        final Path target = BundledScriptCatalog.materialise(river(), cache).toPath();
        for (String edit : new String[] {"first custom source", "second custom source"}) {
            Files.writeString(target, edit);
            BundledScriptCatalog.materialise(river(), cache);
        }
        final List<Path> copies = backups(cache);
        assertEquals(2, copies.size());
        assertNotEquals(copies.get(0).getParent(), copies.get(1).getParent());
        final List<String> contents = copies.stream().map(path -> {
            try { return Files.readString(path); }
            catch (IOException e) { throw new AssertionError(e); }
        }).toList();
        assertTrue(contents.contains("first custom source"));
        assertTrue(contents.contains("second custom source"));
        assertNoStagingFiles(cache);
    }

    @Test public void equalLengthButDifferentContentStillRefreshesAndPreservesTheOldFile() throws Exception {
        final Path cache = temporary.newFolder("equal-length").toPath();
        final byte[] original = resource(river());
        final byte[] modified = original.clone();
        modified[modified.length - 1] ^= 1;
        final Path target = cache.resolve("rivers_river_script.js");
        Files.write(target, modified);
        BundledScriptCatalog.materialise(river(), cache);
        assertArrayEquals(original, Files.readAllBytes(target));
        assertArrayEquals(modified, Files.readAllBytes(backups(cache).get(0)));
    }

    @Test public void failedBackupLeavesTheLiveUserFileUntouched() throws Exception {
        final Path cache = temporary.newFolder("backup-failure").toPath();
        final Path target = cache.resolve("rivers_river_script.js");
        Files.writeString(target, "user version must survive");
        Files.writeString(cache.resolve("backups"), "a file prevents creation of the backup directory");
        assertThrows(IOException.class, () -> BundledScriptCatalog.materialise(river(), cache));
        assertEquals("user version must survive", Files.readString(target));
        assertNoStagingFiles(cache);
    }

    @Test public void directoryTargetAndMissingResourceAreNotOverwritten() throws Exception {
        final Path cache = temporary.newFolder("invalid-target").toPath();
        final Path target = Files.createDirectory(cache.resolve("rivers_river_script.js"));
        Files.writeString(target.resolve("keep.txt"), "keep");
        assertThrows(IOException.class, () -> BundledScriptCatalog.materialise(river(), cache));
        assertEquals("keep", Files.readString(target.resolve("keep.txt")));
        final var missing = new BundledScriptCatalog.BundledScript(BundledScriptCatalog.Category.RIVERS,
                "missing", "not/a/bundled/resource.js", "missing.js", "Missing", false);
        final Path existing = cache.resolve("rivers_missing.js");
        Files.writeString(existing, "do not replace with an empty file");
        assertThrows(IOException.class, () -> BundledScriptCatalog.materialise(missing, cache));
        assertEquals("do not replace with an empty file", Files.readString(existing));
        assertFalse(Files.exists(cache.resolve("backups")));
        assertNoStagingFiles(cache);
    }

    @Test public void cacheNamesRemainStableUnderTurkishLocale() throws Exception {
        final Path cache = temporary.newFolder("turkish").toPath();
        final Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("rivers_river_script.js", BundledScriptCatalog.materialise(river(), cache).getName());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test public void hostUndoRequiresExactBundledTextAndExcludesTheNativeBlueprintTransaction() throws Exception {
        for (var script : BundledScriptCatalog.getAll()) {
            final String source = new String(resource(script), Charset.defaultCharset());
            final boolean nativeTransaction = script.resourcePath().endsWith("/globals/axiom_blueprint_texture.js");
            assertEquals(script.id(), ! nativeTransaction, BundledScriptCatalog.usesHostUndoTransaction(source));
            assertFalse(script.id() + " edited", BundledScriptCatalog.usesHostUndoTransaction(source + "\n// edited"));
            assertFalse(script.id() + " leading BOM", BundledScriptCatalog.usesHostUndoTransaction("\ufeff" + source));
        }
        assertFalse(BundledScriptCatalog.usesHostUndoTransaction(null));
        assertFalse(BundledScriptCatalog.usesHostUndoTransaction(""));
        assertFalse(BundledScriptCatalog.usesHostUndoTransaction("// script.name=river script\nprint('untrusted');"));
    }

    private static BundledScriptCatalog.BundledScript river() {
        return BundledScriptCatalog.find(BundledScriptCatalog.Category.RIVERS, "river_script");
    }

    private static byte[] resource(BundledScriptCatalog.BundledScript script) throws IOException {
        try (InputStream in = BundledScriptCatalogTest.class.getClassLoader().getResourceAsStream(script.resourcePath())) {
            assertNotNull(script.resourcePath(), in);
            return in.readAllBytes();
        }
    }

    private static List<Path> backups(Path cache) throws IOException {
        try (Stream<Path> paths = Files.walk(cache.resolve("backups"))) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private static void assertNoStagingFiles(Path cache) throws IOException {
        try (Stream<Path> paths = Files.list(cache)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(".bundled-script-")));
        }
    }
}
