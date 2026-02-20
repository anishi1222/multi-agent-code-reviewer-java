package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.util.RetryPolicyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/// Executes review attempts with retry/backoff behavior.
final class ReviewRetryExecutor {

    static final long DEFAULT_BACKOFF_BASE_MS = 1000L;
    static final long DEFAULT_BACKOFF_MAX_MS = 8000L;
    static final int DEFAULT_CIRCUIT_BREAKER_FAILURE_THRESHOLD = 8;
    static final long DEFAULT_CIRCUIT_BREAKER_RESET_TIMEOUT_MS = 30_000L;

    private static final SharedCircuitBreaker GLOBAL_CIRCUIT_BREAKER =
        new SharedCircuitBreaker(DEFAULT_CIRCUIT_BREAKER_FAILURE_THRESHOLD, DEFAULT_CIRCUIT_BREAKER_RESET_TIMEOUT_MS);

    @FunctionalInterface
    interface AttemptExecutor {
        ReviewResult execute() throws Exception;
    }

    @FunctionalInterface
    interface ExceptionMapper {
        ReviewResult map(Exception e);
    }

    @FunctionalInterface
    interface SleepStrategy {
        void sleep(long millis) throws InterruptedException;
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewRetryExecutor.class);

    private final String agentName;
    private final int maxRetries;
    private final long backoffBaseMs;
    private final long backoffMaxMs;
    private final SleepStrategy sleepStrategy;
    private final SharedCircuitBreaker circuitBreaker;

    ReviewRetryExecutor(String agentName,
                        int maxRetries,
                        long backoffBaseMs,
                        long backoffMaxMs) {
        this(agentName, maxRetries, backoffBaseMs, backoffMaxMs, Thread::sleep, GLOBAL_CIRCUIT_BREAKER);
    }

    ReviewRetryExecutor(String agentName, int maxRetries) {
        this(agentName, maxRetries, DEFAULT_BACKOFF_BASE_MS, DEFAULT_BACKOFF_MAX_MS, Thread::sleep,
            GLOBAL_CIRCUIT_BREAKER);
    }

    ReviewRetryExecutor(String agentName,
                        int maxRetries,
                        long backoffBaseMs,
                        long backoffMaxMs,
                        SleepStrategy sleepStrategy) {
        this(agentName, maxRetries, backoffBaseMs, backoffMaxMs, sleepStrategy, GLOBAL_CIRCUIT_BREAKER);
    }

    ReviewRetryExecutor(String agentName,
                        int maxRetries,
                        long backoffBaseMs,
                        long backoffMaxMs,
                        SleepStrategy sleepStrategy,
                        SharedCircuitBreaker circuitBreaker) {
        this.agentName = agentName;
        this.maxRetries = maxRetries;
        this.backoffBaseMs = backoffBaseMs;
        this.backoffMaxMs = backoffMaxMs;
        this.sleepStrategy = sleepStrategy;
        this.circuitBreaker = circuitBreaker;
    }

    ReviewResult execute(AttemptExecutor attemptExecutor, ExceptionMapper exceptionMapper) {
        if (!circuitBreaker.allowRequest()) {
            logger.warn("Agent {} skipped by open circuit breaker", agentName);
            return exceptionMapper.map(new IllegalStateException("Circuit breaker is open for Copilot calls"));
        }

        int totalAttempts = maxRetries + 1;
        ReviewResult lastResult = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                lastResult = attemptExecutor.execute();
                if (lastResult.success()) {
                    circuitBreaker.onSuccess();
                    logRetrySuccess(attempt, totalAttempts);
                    return lastResult;
                }

                circuitBreaker.onFailure();
                boolean retryableFailure = isRetryableFailure(lastResult);
                if (shouldRetry(attempt, totalAttempts, retryableFailure)) {
                    waitRetryBackoff(attempt);
                    logResultFailureRetry(attempt, totalAttempts, lastResult.errorMessage());
                } else {
                    logResultFailureFinal(attempt, totalAttempts, lastResult.errorMessage());
                    if (!retryableFailure) {
                        logger.info("Agent {} encountered non-retryable failure on attempt {}/{}",
                            agentName, attempt, totalAttempts);
                    }
                    break;
                }
            } catch (Exception e) {
                lastResult = exceptionMapper.map(e);
                circuitBreaker.onFailure();

                boolean transientException = isTransientException(e);
                if (shouldRetry(attempt, totalAttempts, transientException)) {
                    waitRetryBackoff(attempt);
                    logExceptionRetry(attempt, totalAttempts, e);
                } else {
                    logExceptionFinal(attempt, totalAttempts, e);
                    if (!transientException) {
                        logger.info("Agent {} encountered non-transient exception and will not retry: {}",
                            agentName, e.getClass().getSimpleName());
                    }
                    break;
                }
            }
        }

        return lastResult;
    }

    private boolean shouldRetry(int attempt, int totalAttempts, boolean retryable) {
        return retryable && attempt < totalAttempts;
    }

    private void logRetrySuccess(int attempt, int totalAttempts) {
        if (attempt > 1) {
            logger.info("Agent {} succeeded on attempt {}/{}", agentName, attempt, totalAttempts);
        }
    }

    private void logResultFailureRetry(int attempt, int totalAttempts, String errorMessage) {
        logger.warn("Agent {} failed on attempt {}/{}: {}. Retrying...",
            agentName, attempt, totalAttempts, errorMessage);
    }

    private void logResultFailureFinal(int attempt, int totalAttempts, String errorMessage) {
        logger.error("Agent {} failed on final attempt {}/{}: {}",
            agentName, attempt, totalAttempts, errorMessage);
    }

    private void logExceptionRetry(int attempt, int totalAttempts, Exception e) {
        logger.warn("Agent {} threw exception on attempt {}/{}: {}. Retrying...",
            agentName, attempt, totalAttempts, e.getMessage(), e);
    }

    private void logExceptionFinal(int attempt, int totalAttempts, Exception e) {
        logger.error("Agent {} threw exception on final attempt {}/{}: {}",
            agentName, attempt, totalAttempts, e.getMessage(), e);
    }

    private void waitRetryBackoff(int attempt) {
        long backoffMs = RetryPolicyUtils.computeBackoffWithJitter(backoffBaseMs, backoffMaxMs, attempt);
        try {
            sleepStrategy.sleep(backoffMs);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isTransientException(Exception exception) {
        return exception instanceof SessionEventException || RetryPolicyUtils.isTransientException(exception);
    }

    private boolean isRetryableFailure(ReviewResult result) {
        return RetryPolicyUtils.isRetryableFailureMessage(result.errorMessage());
    }

    static final class SharedCircuitBreaker {
        private final int failureThreshold;
        private final long resetTimeoutMs;
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private volatile long openedAtMs = -1L;

        SharedCircuitBreaker(int failureThreshold, long resetTimeoutMs) {
            this.failureThreshold = failureThreshold;
            this.resetTimeoutMs = resetTimeoutMs;
        }

        boolean allowRequest() {
            int failures = consecutiveFailures.get();
            if (failures < failureThreshold) {
                return true;
            }

            long openedAt = openedAtMs;
            if (openedAt < 0) {
                return true;
            }
            long elapsedMs = System.currentTimeMillis() - openedAt;
            if (elapsedMs >= resetTimeoutMs) {
                consecutiveFailures.set(Math.max(0, failureThreshold - 1));
                openedAtMs = -1L;
                return true;
            }
            return false;
        }

        void onSuccess() {
            consecutiveFailures.set(0);
            openedAtMs = -1L;
        }

        void onFailure() {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= failureThreshold && openedAtMs < 0) {
                openedAtMs = System.currentTimeMillis();
            }
        }
    }
}