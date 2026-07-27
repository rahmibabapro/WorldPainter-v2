package org.pepsoft.worldpainter.ui;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Branding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

/**
 * After a period of inactivity, drops redo stacks and rendered tile caches to reduce idle RSS.
 * Tile/undo snapshot data for the loaded world remains in memory until the world is closed.
 */
public final class IdleMemoryGuard implements AWTEventListener {
    private static final long IDLE_MS = 10L * 60L * 1000L;
    private static final long CHECK_MS = 60_000L;

    private final App app;
    private final Timer timer;
    private long lastActivityMs = System.currentTimeMillis();
    private boolean sleepActive;

    public IdleMemoryGuard(App app) {
        this.app = app;
        timer = new Timer((int) CHECK_MS, e -> onTimer());
        timer.setRepeats(true);
    }

    public void install() {
        if (! Branding.isV2()) {
            return;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(this,
                AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        timer.start();
    }

    public void dispose() {
        timer.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(this);
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        if (event.getID() == MouseEvent.MOUSE_MOVED) {
            return;
        }
        noteActivity();
    }

    private void noteActivity() {
        lastActivityMs = System.currentTimeMillis();
        if (sleepActive) {
            sleepActive = false;
            logger.debug("Idle memory guard: active again");
        }
    }

    private void onTimer() {
        if (app.getWorld() == null) {
            return;
        }
        if ((System.currentTimeMillis() - lastActivityMs) < IDLE_MS) {
            return;
        }
        if (sleepActive) {
            return;
        }
        sleepActive = true;
        logger.info("Idle memory guard: releasing redo history and tile render caches after {} minutes idle", IDLE_MS / 60_000L);
        SwingUtilities.invokeLater(this::releaseIdleMemory);
    }

    private void releaseIdleMemory() {
        if (app.getWorld() == null) {
            return;
        }
        app.releaseIdleMemory();
    }

    private static final Logger logger = LoggerFactory.getLogger(IdleMemoryGuard.class);
}
