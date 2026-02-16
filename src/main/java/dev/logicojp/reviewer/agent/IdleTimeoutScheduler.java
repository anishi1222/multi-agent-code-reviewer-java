package dev.logicojp.reviewer.agent;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/// Schedules idle-timeout checks for review sessions.
final class IdleTimeoutScheduler {

    private static final long DEFAULT_MIN_CHECK_INTERVAL_MS = 5000L;

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
        return scheduler.scheduleAtFixedRate(timeoutCheck, checkInterval, checkInterval, TimeUnit.MILLISECONDS);
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
