package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.report.core.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.StructuredConcurrencyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

final class ReviewExecutionModeRunner {

    private record ExecutionParams(int reviewPasses, int totalTasks, long timeoutMinutes, long perAgentTimeoutMinutes) {
    }

    @FunctionalInterface
    interface AgentPassExecutor {
        ReviewResult execute(AgentConfig config,
                             ReviewTarget target,
                             ReviewContext context,
                             long perAgentTimeoutMinutes);
    }

    private static final Logger logger = LoggerFactory.getLogger(ReviewExecutionModeRunner.class);

    private final ExecutionConfig executionConfig;
    private final ExecutorService executorService;
    private final ReviewResultPipeline reviewResultPipeline;

    ReviewExecutionModeRunner(ExecutionConfig executionConfig,
                              ExecutorService executorService,
                              ReviewResultPipeline reviewResultPipeline) {
        this.executionConfig = executionConfig;
        this.executorService = executorService;
        this.reviewResultPipeline = reviewResultPipeline;
    }

    List<ReviewResult> executeAsync(Map<String, AgentConfig> agents,
                                    ReviewTarget target,
                                    ReviewContext sharedContext,
                                    AgentPassExecutor agentPassExecutor) {
        ExecutionParams params = executionParams(agents.size());

        List<CompletableFuture<ReviewResult>> futures = new ArrayList<>(params.totalTasks());
        forEachAgentPass(agents, params.reviewPasses(), (config, passNumber) -> {
            CompletableFuture<ReviewResult> future = CompletableFuture
                .supplyAsync(() -> {
                    logPassStart(config, passNumber, params.reviewPasses(), false);
                    return agentPassExecutor.execute(config, target, sharedContext, params.perAgentTimeoutMinutes());
                }, executorService)
                .exceptionally(ex -> {
                    logger.error("Agent {} (pass {}) failed with timeout or error: {}",
                        config.name(), passNumber, ex.getMessage(), ex);
                    return failedResult(
                        config,
                        target,
                        "Review timed out or failed (pass %d): %s".formatted(passNumber, ex.getMessage())
                    );
                });
            futures.add(future);
        });

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        awaitAsyncCompletion(allFutures, params.timeoutMinutes());
        return finalizeAsyncResults(params.reviewPasses(), futures);
    }

    List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
                                         ReviewTarget target,
                                         ReviewContext sharedContext,
                                         AgentPassExecutor agentPassExecutor) {
        ExecutionParams params = executionParams(agents.size());
        List<StructuredTaskScope.Subtask<ReviewResult>> tasks = new ArrayList<>(params.totalTasks());
        try (var scope = StructuredTaskScope.<ReviewResult>open()) {
            forEachAgentPass(agents, params.reviewPasses(), (config, passNumber) ->
                tasks.add(scope.fork(() -> {
                    logPassStart(config, passNumber, params.reviewPasses(), true);
                    return agentPassExecutor.execute(config, target, sharedContext, params.perAgentTimeoutMinutes());
                }))
            );

            joinStructuredWithTimeout(scope, params.timeoutMinutes());

            return finalizeCollectedResults(
                params.reviewPasses(),
                collectStructuredResults(tasks, target, params.perAgentTimeoutMinutes())
            );
        }
    }

    private ExecutionParams executionParams(int agentCount) {
        int reviewPasses = executionConfig.reviewPasses();
        return new ExecutionParams(
            reviewPasses,
            agentCount * reviewPasses,
            executionConfig.orchestratorTimeoutMinutes(),
            perAgentTimeoutMinutes()
        );
    }

    private long perAgentTimeoutMinutes() {
        return executionConfig.agentTimeoutMinutes() * (executionConfig.maxRetries() + 1L);
    }

    private ReviewResult summarizeTaskResult(StructuredTaskScope.Subtask<ReviewResult> task,
                                             ReviewTarget target,
                                             long perAgentTimeoutMinutes) {
        return switch (task.state()) {
            case SUCCESS -> task.get();
            case FAILED -> {
                Throwable cause = task.exception();
                yield failedResult(target, "Review failed: " + (cause != null ? cause.getMessage() : "unknown"));
            }
            case UNAVAILABLE -> failedResult(target,
                "Review cancelled after " + perAgentTimeoutMinutes + " minutes");
        };
    }

    private List<ReviewResult> collectStructuredResults(
            List<StructuredTaskScope.Subtask<ReviewResult>> tasks,
            ReviewTarget target,
            long perAgentTimeoutMinutes) {
        List<ReviewResult> results = new ArrayList<>(tasks.size());
        for (var task : tasks) {
            results.add(summarizeTaskResult(task, target, perAgentTimeoutMinutes));
        }
        return results;
    }

    private List<ReviewResult> finalizeCollectedResults(int reviewPasses, List<ReviewResult> results) {
        return reviewResultPipeline.finalizeResults(results, reviewPasses);
    }

    private List<ReviewResult> finalizeAsyncResults(int reviewPasses,
                                                    List<CompletableFuture<ReviewResult>> futures) {
        return finalizeCollectedResults(reviewPasses, reviewResultPipeline.collectFromFutures(futures));
    }

    private void awaitAsyncCompletion(CompletableFuture<Void> allFutures, long timeoutMinutes) {
        try {
            allFutures.get(timeoutMinutes + 1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Review orchestration interrupted: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error waiting for reviews to complete: {}", e.getMessage(), e);
        }
    }

    private void joinStructuredWithTimeout(StructuredTaskScope<ReviewResult, Void> scope,
                                           long timeoutMinutes) {
        try {
            StructuredConcurrencyUtils.joinWithTimeout(scope, timeoutMinutes + 1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Structured concurrency interrupted", e);
        } catch (TimeoutException e) {
            logger.error("Structured concurrency timed out after {} minutes", timeoutMinutes, e);
            scope.close();
        }
    }

    private void forEachAgentPass(Map<String, AgentConfig> agents,
                                  int reviewPasses,
                                  BiConsumer<AgentConfig, Integer> action) {
        for (var config : agents.values()) {
            for (int pass = 1; pass <= reviewPasses; pass++) {
                action.accept(config, pass);
            }
        }
    }

    private void logPassStart(AgentConfig config,
                              int passNumber,
                              int reviewPasses,
                              boolean structured) {
        if (reviewPasses <= 1) {
            return;
        }
        if (structured) {
            logger.info("Agent {}: starting pass {}/{} (structured)",
                config.name(), passNumber, reviewPasses);
            return;
        }
        logger.info("Agent {}: starting pass {}/{}",
            config.name(), passNumber, reviewPasses);
    }

    private ReviewResult failedResult(AgentConfig config, ReviewTarget target, String errorMessage) {
        return ReviewResult.builder()
            .agentConfig(config)
            .repository(target.displayName())
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    private ReviewResult failedResult(ReviewTarget target, String errorMessage) {
        return failedResult(null, target, errorMessage);
    }
}