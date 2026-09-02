package org.pepsoft.worldpainter.exporting.health;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Post-export MCA <b>header/sector</b> integrity checks (not full NBT validation).
 * Scans overworld {@code region/}, {@code DIM-1/region/}, {@code DIM1/region/}.
 * {@code .linear} files: size presence only.
 */
public final class WorldHealthInspector {

    public enum Severity { INFO, WARNING, ERROR }

    public static class HealthIssue {
        public final Severity severity;
        public final String component;
        public final String message;

        public HealthIssue(Severity severity, String component, String message) {
            this.severity = severity;
            this.component = component;
            this.message = message;
        }

        @Override
        public String toString() {
            return "[" + severity + "] (" + component + ") " + message;
        }
    }

    public static class HealthReport {
        private final List<HealthIssue> issues = new ArrayList<>();
        public int verifiedRegions = 0;
        public int verifiedChunks = 0;
        public int linearRegionsSeen = 0;
        public int regionFoldersScanned = 0;

        public void addIssue(Severity severity, String component, String message) {
            issues.add(new HealthIssue(severity, component, message));
        }

        public List<HealthIssue> getIssues() {
            return Collections.unmodifiableList(issues);
        }

        public boolean hasErrors() {
            return issues.stream().anyMatch(i -> i.severity == Severity.ERROR);
        }

        public String summaryLine() {
            final int warnings = (int) issues.stream().filter(i -> i.severity == Severity.WARNING).count();
            final int errors = (int) issues.stream().filter(i -> i.severity == Severity.ERROR).count();
            return "MCA header/sector check (no NBT): "
                    + verifiedRegions + " region(s), " + verifiedChunks + " chunk(s)"
                    + " in " + regionFoldersScanned + " folder(s)"
                    + (linearRegionsSeen > 0 ? "; " + linearRegionsSeen + " .linear (size-only)" : "")
                    + (errors > 0 ? "; " + errors + " error(s)" : "")
                    + (warnings > 0 ? "; " + warnings + " warning(s)" : "")
                    + (errors == 0 && warnings == 0 ? "; OK" : "");
        }
    }

    private WorldHealthInspector() {
    }

    public static HealthReport inspectExportedWorld(File worldDir) {
        final HealthReport report = new HealthReport();
        if (worldDir == null || ! worldDir.exists()) {
            report.addIssue(Severity.ERROR, "Directory", "Export world directory does not exist");
            return report;
        }

        final File[] regionDirs = {
                new File(worldDir, "region"),
                new File(worldDir, "DIM-1/region"),
                new File(worldDir, "DIM1/region")
        };
        boolean anyFolder = false;
        for (File regionDir : regionDirs) {
            if (regionDir.isDirectory()) {
                anyFolder = true;
                inspectRegionFolder(regionDir, report);
            }
        }
        if (! anyFolder) {
            report.addIssue(Severity.WARNING, "Region", "No region directory found (region/, DIM-1/region, DIM1/region)");
        }
        return report;
    }

    private static void inspectRegionFolder(File regionDir, HealthReport report) {
        report.regionFoldersScanned++;
        final File[] files = regionDir.listFiles((dir, name) -> name.endsWith(".mca") || name.endsWith(".linear"));
        if (files == null || files.length == 0) {
            report.addIssue(Severity.WARNING, regionDir.getPath(), "Region directory is empty");
            return;
        }
        for (File file : files) {
            if (file.getName().endsWith(".mca")) {
                inspectMcaFile(file, report);
            } else {
                inspectLinearFile(file, report);
            }
        }
    }

    private static void inspectLinearFile(File regionFile, HealthReport report) {
        report.linearRegionsSeen++;
        if (regionFile.length() <= 0) {
            report.addIssue(Severity.ERROR, regionFile.getName(), "Empty .linear file");
        } else {
            report.addIssue(Severity.INFO, regionFile.getName(),
                    "Linear region present (" + regionFile.length() + " bytes); deep NBT check not implemented");
        }
    }

    private static void inspectMcaFile(File regionFile, HealthReport report) {
        final long fileLen = regionFile.length();
        if (fileLen < 8192) {
            report.addIssue(Severity.ERROR, regionFile.getName(), "File size is smaller than MCA header (8KB)");
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(regionFile, "r")) {
            final byte[] locations = new byte[4096];
            raf.readFully(locations);
            raf.seek(4096);
            raf.readFully(new byte[4096]);

            report.verifiedRegions++;
            int chunkCount = 0;
            final int sectorCountTotal = (int) (fileLen / 4096L);

            for (int i = 0; i < 1024; i++) {
                final int off = i * 4;
                final int sectorOffset = ((locations[off] & 0xFF) << 16)
                        | ((locations[off + 1] & 0xFF) << 8)
                        | (locations[off + 2] & 0xFF);
                final int sectorCount = locations[off + 3] & 0xFF;
                if (sectorOffset == 0 || sectorCount == 0) {
                    continue;
                }
                chunkCount++;

                if (sectorOffset + sectorCount > sectorCountTotal) {
                    report.addIssue(Severity.ERROR, regionFile.getName(),
                            "Chunk slot " + i + " sector range exceeds file (offset=" + sectorOffset
                                    + ", count=" + sectorCount + ", fileSectors=" + sectorCountTotal + ")");
                    continue;
                }

                final long chunkPos = (long) sectorOffset * 4096L;
                raf.seek(chunkPos);
                final int length = raf.readInt();
                if (length <= 0) {
                    report.addIssue(Severity.ERROR, regionFile.getName(),
                            "Chunk slot " + i + " has non-positive length " + length);
                    continue;
                }
                final int maxPayload = sectorCount * 4096;
                if (length + 4 > maxPayload) {
                    report.addIssue(Severity.ERROR, regionFile.getName(),
                            "Chunk slot " + i + " length " + length + " does not fit in " + sectorCount + " sector(s)");
                    continue;
                }
                final int compression = raf.readUnsignedByte();
                if (compression != 1 && compression != 2 && compression != 3) {
                    report.addIssue(Severity.WARNING, regionFile.getName(),
                            "Chunk slot " + i + " unusual compression type " + compression);
                }
            }
            report.verifiedChunks += chunkCount;
        } catch (IOException e) {
            report.addIssue(Severity.ERROR, regionFile.getName(), "Failed reading MCA: " + e.getMessage());
        }
    }
}
