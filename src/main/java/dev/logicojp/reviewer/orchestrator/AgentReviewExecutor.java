package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import org.slf4j.Logger;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class AgentReviewExecutor {

    private final Logger logger;
    private final Semaphore concurrencyLimit;
    private final ExecutorService agentExecutionExecutor;
    private final ReviewOrchestrator.AgentReviewerFactory reviewerFactory;

    AgentReviewExecutor(Logger logger,
                        Semaphore concurrencyLimit,
                        ExecutorService agentExecutionExecutor,
                        ReviewOrchestrator.AgentReviewerFactory reviewerFactory) {
        this.logger = logger;
        this.concurrencyLimit = concurrencyLimit;
        this.agentExecutionExecutor = agentExecutionExecutor;
        this.reviewerFactory = reviewerFactory;
    }

    ReviewResult executeAgentSafely(AgentConfig config,
                                    ReviewTarget target,
                                    ReviewContext context,
                                    long perAgentTimeoutMinutes) {
        try {
            concurrencyLimit.acquire();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return failedResult(config, target, "Review interrupted while waiting for concurrency permit");
        }
        try {
            return executeWithTimeout(config, target, context, perAgentTimeoutMinutes);
        } finally {
            concurrencyLimit.release();
        }
    }

    private ReviewResult executeWithTimeout(AgentConfig config,
                                            ReviewTarget target,
                                            ReviewContext context,
                                            long perAgentTimeoutMinutes) {
        try {
            ReviewOrchestrator.AgentReviewer reviewer = reviewerFactory.create(config, context);
            Future<ReviewResult> future = agentExecutionExecutor.submit(() -> reviewer.review(target));
            try {
                return future.get(perAgentTimeoutMinutes, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            }
        } catch (TimeoutException e) {
            logger.warn("Agent {} timed out after {} minutes", config.name(), perAgentTimeoutMinutes, e);
            return failedResult(config, target, "Review timed out after " + perAgentTimeoutMinutes + " minutes");
        } catch (ExecutionException e) {
            logger.error("Agent {} execution failed: {}", config.name(), e.getMessage(), e);
            return failedResult(config, target, "Review failed: " + e.getMessage());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return failedResult(config, target, "Review interrupted during execution");
        }
    }

    private ReviewResult failedResult(AgentConfig config, ReviewTarget target, String errorMessage) {
        return ReviewResult.builder()
            .agentConfig(config)
            .repository(target.displayName())
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }
}