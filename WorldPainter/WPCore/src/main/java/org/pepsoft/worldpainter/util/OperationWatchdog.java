package org.pepsoft.worldpainter.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logs when a long-running operation exceeds a threshold and invokes a callback.
 */
public final class OperationWatchdog {
    private OperationWatchdog() {
    }

    public static WatchdogHandle start(String operationName, long thresholdMs, Runnable onThreshold) {
        final AtomicBoolean triggered = new AtomicBoolean(false);
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread thread = new Thread(r, "wp-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        final ScheduledFuture<?> future = executor.schedule(() -> {
            if (triggered.compareAndSet(false, true)) {
                logger.warn("{} has been running for more than {} ms", operationName, thresholdMs);
                if (onThreshold != null) {
                    onThreshold.run();
                }
            }
        }, thresholdMs, TimeUnit.MILLISECONDS);
        return new WatchdogHandle(executor, future);
    }

    private static final Logger logger = LoggerFactory.getLogger(OperationWatchdog.class);

    public static final class WatchdogHandle implements AutoCloseable {
        WatchdogHandle(ScheduledExecutorService executor, ScheduledFuture<?> future) {
            this.executor = executor;
            this.future = future;
        }

        @Override
        public void close() {
            future.cancel(false);
            executor.shutdownNow();
        }

        private final ScheduledExecutorService executor;
        private final ScheduledFuture<?> future;
    }
}
