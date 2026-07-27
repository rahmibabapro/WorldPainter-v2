package org.pepsoft.worldpainter.ui;

import org.pepsoft.worldpainter.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight UI health probes: EDT stall detection, heap snapshots, and log file location hints.
 */
public final class UiDiagnostics {
    private static final Logger logger = LoggerFactory.getLogger(UiDiagnostics.class);
    private static final AtomicLong LAST_EDT_PING_MS = new AtomicLong(System.currentTimeMillis());
    private static ScheduledExecutorService watchdog;
    private static volatile long lastSlowEdtLogMs;

    private UiDiagnostics() {
    }

    public static void logStartupPaths() {
        final File configDir = Configuration.getConfigDir();
        final File logFile = new File(configDir, "logfile0.txt");
        logger.info("Configuration directory: {}", configDir.getAbsolutePath());
        logger.info("Application log file: {}", logFile.getAbsolutePath());
        logger.info("Tip: set -Dorg.pepsoft.worldpainter.debugLogging=true for DEBUG logs if diagnosing freezes");
    }

    public static void start(JFrame frame) {
        if (watchdog != null) {
            return;
        }
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "wp-ui-diagnostics");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleAtFixedRate(() -> pingEdt(frame), 2, 2, TimeUnit.SECONDS);
        logger.debug("UI diagnostics watchdog started");
    }

    public static void stop() {
        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }
    }

    /** Log a user-visible UI stall (e.g. repeated menu open while EDT is busy). */
    public static void logUiStall(String context) {
        final Runtime runtime = Runtime.getRuntime();
        final long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        logger.warn("UI stall reported [{}]: heap ~{} MB, last EDT ping {} ms ago",
                context, usedMb, System.currentTimeMillis() - LAST_EDT_PING_MS.get());
        dumpEdtStackIfBlocked();
    }

    private static void pingEdt(JFrame frame) {
        final long pingStart = System.currentTimeMillis();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            SwingUtilities.invokeLater(() -> {
                LAST_EDT_PING_MS.set(System.currentTimeMillis());
                latch.countDown();
            });
            if (! latch.await(5, TimeUnit.SECONDS)) {
                final long now = System.currentTimeMillis();
                if (now - lastSlowEdtLogMs >= 10_000) {
                    lastSlowEdtLogMs = now;
                    final Runtime runtime = Runtime.getRuntime();
                    final long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
                    logger.warn("EDT did not respond within 5s (possible UI freeze); main window showing={}; heap ~{} MB",
                            frame != null && frame.isShowing(), usedMb);
                    dumpEdtStackIfBlocked();
                }
                return;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }
        final long blockedMs = System.currentTimeMillis() - pingStart;
        if (blockedMs < 400) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastSlowEdtLogMs < 10_000) {
            return;
        }
        lastSlowEdtLogMs = now;
        final Runtime runtime = Runtime.getRuntime();
        final long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        logger.warn("EDT blocked for {} ms (possible UI freeze); heap ~{} MB", blockedMs, usedMb);
        dumpEdtStackIfBlocked();
    }

    private static void dumpEdtStackIfBlocked() {
        final ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        for (ThreadInfo info: mx.dumpAllThreads(false, false)) {
            if ("AWT-EventQueue".equals(info.getThreadName())) {
                final StackTraceElement[] stack = info.getStackTrace();
                if (stack.length == 0) {
                    return;
                }
                final StringBuilder sb = new StringBuilder("EDT stack (top 8 frames):");
                for (int i = 0; i < Math.min(8, stack.length); i++) {
                    sb.append("\n  at ").append(stack[i]);
                }
                logger.warn(sb.toString());
                return;
            }
        }
    }
}
