package org.pepsoft.worldpainter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Autosave paths under the WorldPainter config directory and helpers to validate or discard them.
 */
public final class AutosaveFiles {
    private AutosaveFiles() {
    }

    public static File autosaveFile() {
        return new File(Configuration.getConfigDir(), "autosave.world");
    }

    public static File autosaveTempFile() {
        return new File(Configuration.getConfigDir(), "autosave.world.tmp");
    }

    public static void discardAll() {
        deleteQuietly(autosaveFile());
        deleteQuietly(autosaveTempFile());
        final File configDir = Configuration.getConfigDir();
        final File[] backups = configDir.listFiles(BACKUP_FILTER);
        if (backups != null) {
            for (File backup : backups) {
                deleteQuietly(backup);
            }
        }
    }

    /**
     * If autosave recovery is enabled, loads a valid autosave when present. Corrupt or stale files are removed
     * without bothering the user.
     *
     * @return {@code true} if a world was recovered and a startup warning should be shown
     */
    public static boolean tryRecover(App app, boolean autosaveInhibited) {
        final Configuration config = Configuration.getInstance();
        if (autosaveInhibited || (! config.isAutosaveEnabled())) {
            return false;
        }

        final File autosaveFile = autosaveFile();
        final File autosaveTempFile = autosaveTempFile();
        File recoveryFile = null;
        String recoveryWarning = null;

        if (autosaveTempFile.isFile()) {
            if (WorldIO.isLoadableWorldFile(autosaveTempFile)) {
                if (autosaveFile.isFile() && (! autosaveFile.delete())) {
                    logger.warn("Could not remove existing autosave file while recovering temp autosave");
                }
                if (autosaveTempFile.renameTo(autosaveFile)) {
                    recoveryFile = autosaveFile;
                    recoveryWarning = "WorldPainter was interrupted during autosave.\nYour world has been recovered from the incomplete autosave temp file.\nMake sure to Save it if you want to keep it!";
                } else {
                    recoveryFile = autosaveTempFile;
                    recoveryWarning = "WorldPainter recovered an interrupted autosave from a temporary file.\nSave the world under a new name to preserve it.";
                }
            } else {
                logger.warn("Discarding corrupt autosave temp file {}", autosaveTempFile.getAbsolutePath());
                deleteQuietly(autosaveTempFile);
            }
        }

        if ((recoveryFile == null) && autosaveFile.isFile()) {
            if (WorldIO.isLoadableWorldFile(autosaveFile)) {
                recoveryFile = autosaveFile;
                recoveryWarning = "WorldPainter was not shut down correctly.\nYour world has been recovered from the most recent autosave.\nMake sure to Save it if you want to keep it!";
            } else {
                logger.warn("Discarding corrupt autosave file {}", autosaveFile.getAbsolutePath());
                discardAll();
            }
        }

        if (recoveryFile == null) {
            return false;
        }

        logger.info("Recovering autosaved world from {}", recoveryFile.getAbsolutePath());
        app.open(recoveryFile);
        if (app.getWorld() != null) {
            StartupMessages.addWarning(recoveryWarning);
            return true;
        }

        logger.warn("Autosave recovery failed after validation; discarding autosave files");
        discardAll();
        return false;
    }

    private static void deleteQuietly(File file) {
        if (file.isFile() && (! file.delete())) {
            logger.warn("Could not delete autosave file {}", file.getAbsolutePath());
        }
    }

    private static final FilenameFilter BACKUP_FILTER = (dir, name) -> name.matches("autosave\\.\\d+\\.world");

    private static final Logger logger = LoggerFactory.getLogger(AutosaveFiles.class);
}
