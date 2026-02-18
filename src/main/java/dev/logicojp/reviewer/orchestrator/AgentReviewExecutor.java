package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.ArrayList;
import java.util.List;

final class AgentReviewExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AgentReviewExecutor.class);
    private final Semaphore concurrencyLimit;
    private final ExecutorService agentExecutionExecutor;
    private final ReviewOrchestrator.AgentReviewerFactory reviewerFactory;

    AgentReviewExecutor(Semaphore concurrencyLimit,
                        ExecutorService agentExecutionExecutor,
                        ReviewOrchestrator.AgentReviewerFactory reviewerFactory) {
        this.concurrencyLimit = concurrencyLimit;
        this.agentExecutionExecutor = agentExecutionExecutor;
        this.reviewerFactory = reviewerFactory;
    }

    List<ReviewResult> executeAgentPassesSafely(AgentConfig config,
                                                ReviewTarget target,
                                                ReviewContext context,
                                                int reviewPasses,
                                                long perAgentTimeoutMinutes) {
        try {
            concurrencyLimit.acquire();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return failedResults(config, target, reviewPasses,
                "Review interrupted while waiting for concurrency permit");
        }
        try {
            return executePassesWithTimeout(config, target, context, reviewPasses, perAgentTimeoutMinutes);
        } finally {
            concurrencyLimit.release();
        }
    }

    private List<ReviewResult> executePassesWithTimeout(AgentConfig config,
                                                        ReviewTarget target,
                                                        ReviewContext context,
                                                        int reviewPasses,
                                                        long perAgentTimeoutMinutes) {
        try {
            ReviewOrchestrator.AgentReviewer reviewer = reviewerFactory.create(config, context);
            Future<List<ReviewResult>> future = agentExecutionExecutor.submit(
                () -> reviewer.reviewPasses(target, reviewPasses)
            );
            try {
                long totalTimeoutMinutes = perAgentTimeoutMinutes * Math.max(1, reviewPasses);
                return future.get(totalTimeoutMinutes, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            }
        } catch (TimeoutException e) {
            long totalTimeoutMinutes = perAgentTimeoutMinutes * Math.max(1, reviewPasses);
            logger.warn("Agent {} timed out after {} minutes for {} pass(es)",
                config.name(), totalTimeoutMinutes, reviewPasses, e);
            return failedResults(config, target, reviewPasses,
                "Review timed out after " + totalTimeoutMinutes + " minutes");
        } catch (ExecutionException e) {
            logger.error("Agent {} execution failed: {}", config.name(), e.getMessage(), e);
            return failedResults(config, target, reviewPasses, "Review failed: " + e.getMessage());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return failedResults(config, target, reviewPasses, "Review interrupted during execution");
        }
    }

    private List<ReviewResult> failedResults(AgentConfig config,
                                             ReviewTarget target,
                                             int reviewPasses,
                                             String errorMessage) {
        List<ReviewResult> results = new ArrayList<>(reviewPasses);
        for (int pass = 0; pass < reviewPasses; pass++) {
            results.add(ReviewResult.builder()
                .agentConfig(config)
                .repository(target.displayName())
                .success(false)
                .errorMessage(errorMessage)
                .build());
        }
        return results;
    }
}