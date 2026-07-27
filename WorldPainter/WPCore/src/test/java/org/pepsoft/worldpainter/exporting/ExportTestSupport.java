package org.pepsoft.worldpainter.exporting;

import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.WPContext;
import org.pepsoft.worldpainter.plugins.WPPluginManager;

import java.io.IOException;

/**
 * Shared bootstrap for headless export integration tests.
 */
public final class ExportTestSupport {
    private static volatile boolean ready;

    private ExportTestSupport() {
    }

    public static synchronized void ensureReady() {
        if (ready) {
            return;
        }
        Configuration config = Configuration.getInstance();
        if (config == null) {
            try {
                config = Configuration.load();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException("Failed to load configuration for export tests", e);
            }
            if (config == null) {
                config = new Configuration();
            }
            Configuration.setInstance(config);
        }
        try {
            WPPluginManager.initialise(config.getUuid(), WPContext.INSTANCE);
        } catch (IllegalStateException e) {
            if (! "Already initialised".equals(e.getMessage())) {
                throw e;
            }
        }
        ready = true;
    }
}
