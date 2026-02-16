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
                return ReviewResult.builder()
                    .agentConfig(config)
                    .repository(target.displayName())
                    .success(false)
                    .errorMessage("Review timed out after " + perAgentTimeoutMinutes + " minutes")
                    .build();
            } catch (ExecutionException e) {
                logger.error("Agent {} execution failed: {}", config.name(), e.getMessage(), e);
                return ReviewResult.builder()
                    .agentConfig(config)
                    .repository(target.displayName())
                    .success(false)
                    .errorMessage("Review failed: " + e.getMessage())
                    .build();
            } finally {
                concurrencyLimit.release();
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return ReviewResult.builder()
                .agentConfig(config)
                .repository(target.displayName())
                .success(false)
                .errorMessage("Review interrupted while waiting for concurrency permit")
                .build();
        }
    }
}