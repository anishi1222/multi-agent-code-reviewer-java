package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Schedules idle-timeout checks for review sessions.
final class IdleTimeoutScheduler {

    private static final long DEFAULT_MIN_CHECK_INTERVAL_MS = 5000L;
    private static final Logger logger = LoggerFactory.getLogger(IdleTimeoutScheduler.class);

    private static final ScheduledFuture<?> NO_OP_FUTURE = new ScheduledFuture<>() {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
    };

    private final long minCheckIntervalMs;

    private IdleTimeoutScheduler(long minCheckIntervalMs) {
        this.minCheckIntervalMs = minCheckIntervalMs;
    }

    static IdleTimeoutScheduler defaultScheduler() {
        return new IdleTimeoutScheduler(DEFAULT_MIN_CHECK_INTERVAL_MS);
    }

    static IdleTimeoutScheduler withMinInterval(long minCheckIntervalMs) {
        return new IdleTimeoutScheduler(minCheckIntervalMs);
    }

    ScheduledFuture<?> schedule(ScheduledExecutorService scheduler,
                                ContentCollector collector,
                                long idleTimeoutMs) {
        long checkInterval = computeCheckInterval(idleTimeoutMs);
        Runnable timeoutCheck = createTimeoutCheck(collector, idleTimeoutMs);
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            logger.warn("Idle-timeout scheduler is not available (already shut down); continuing without idle watchdog");
            return NO_OP_FUTURE;
        }
        try {
            return scheduler.scheduleAtFixedRate(timeoutCheck, checkInterval, checkInterval, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            logger.warn("Idle-timeout task scheduling was rejected; continuing without idle watchdog: {}", e.getMessage());
            return NO_OP_FUTURE;
        }
    }

    long computeCheckInterval(long idleTimeoutMs) {
        return Math.max(idleTimeoutMs / 4, minCheckIntervalMs);
    }

    private Runnable createTimeoutCheck(ContentCollector collector, long idleTimeoutMs) {
        return () -> {
            long elapsed = collector.getElapsedSinceLastActivity();
            if (elapsed >= idleTimeoutMs) {
                collector.onIdleTimeout(elapsed, idleTimeoutMs);
            }
        };
    }
}
