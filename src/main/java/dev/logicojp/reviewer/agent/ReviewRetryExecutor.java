package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.report.ReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Executes review attempts with retry/backoff behavior.
final class ReviewRetryExecutor {

    static final long DEFAULT_BACKOFF_BASE_MS = 1000L;
    static final long DEFAULT_BACKOFF_MAX_MS = 8000L;

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

    ReviewRetryExecutor(String agentName,
                        int maxRetries,
                        long backoffBaseMs,
                        long backoffMaxMs) {
        this(agentName, maxRetries, backoffBaseMs, backoffMaxMs, Thread::sleep);
    }

    ReviewRetryExecutor(String agentName, int maxRetries) {
        this(agentName, maxRetries, DEFAULT_BACKOFF_BASE_MS, DEFAULT_BACKOFF_MAX_MS, Thread::sleep);
    }

    ReviewRetryExecutor(String agentName,
                        int maxRetries,
                        long backoffBaseMs,
                        long backoffMaxMs,
                        SleepStrategy sleepStrategy) {
        this.agentName = agentName;
        this.maxRetries = maxRetries;
        this.backoffBaseMs = backoffBaseMs;
        this.backoffMaxMs = backoffMaxMs;
        this.sleepStrategy = sleepStrategy;
    }

    ReviewResult execute(AttemptExecutor attemptExecutor, ExceptionMapper exceptionMapper) {
        int totalAttempts = maxRetries + 1;
        ReviewResult lastResult = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                lastResult = attemptExecutor.execute();
                if (lastResult.success()) {
                    logRetrySuccess(attempt, totalAttempts);
                    return lastResult;
                }

                if (shouldRetry(attempt, totalAttempts)) {
                    waitRetryBackoff(attempt);
                    logResultFailureRetry(attempt, totalAttempts, lastResult.errorMessage());
                } else {
                    logResultFailureFinal(attempt, totalAttempts, lastResult.errorMessage());
                }
            } catch (Exception e) {
                lastResult = exceptionMapper.map(e);

                if (shouldRetry(attempt, totalAttempts)) {
                    waitRetryBackoff(attempt);
                    logExceptionRetry(attempt, totalAttempts, e);
                } else {
                    logExceptionFinal(attempt, totalAttempts, e);
                }
            }
        }

        return lastResult;
    }

    private boolean shouldRetry(int attempt, int totalAttempts) {
        return attempt < totalAttempts;
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
            agentName, attempt, totalAttempts, e.getMessage());
    }

    private void logExceptionFinal(int attempt, int totalAttempts, Exception e) {
        logger.error("Agent {} threw exception on final attempt {}/{}: {}",
            agentName, attempt, totalAttempts, e.getMessage(), e);
    }

    private void waitRetryBackoff(int attempt) {
        long backoffMs = Math.min(backoffBaseMs << Math.max(0, attempt - 1), backoffMaxMs);
        try {
            sleepStrategy.sleep(backoffMs);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}