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

final class ReviewExecutionModeRunner {

    private record ExecutionParams(int reviewPasses, int agentCount, long timeoutMinutes, long perAgentTimeoutMinutes) {
    }

    private record SubtaskWithConfig(StructuredTaskScope.Subtask<List<ReviewResult>> subtask, AgentConfig config) {
    }

    @FunctionalInterface
    interface AgentPassExecutor {
        List<ReviewResult> execute(AgentConfig config,
                                   ReviewTarget target,
                                   ReviewContext context,
                                   int reviewPasses,
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

        List<CompletableFuture<List<ReviewResult>>> futures = new ArrayList<>(params.agentCount());
        for (var config : agents.values()) {
            CompletableFuture<List<ReviewResult>> future = CompletableFuture
                .supplyAsync(() -> {
                    return executeAgentPasses(
                        config,
                        target,
                        sharedContext,
                        params.reviewPasses(),
                        params.perAgentTimeoutMinutes(),
                        false,
                        agentPassExecutor
                    );
                }, executorService)
                .exceptionally(ex -> {
                    logger.error("Agent {} failed with timeout or error: {}",
                        config.name(), ex.getMessage(), ex);
                    return ReviewResult.failedResults(
                        config,
                        target.displayName(),
                        params.reviewPasses(),
                        "Review timed out or failed: " + ex.getMessage()
                    );
                });
            futures.add(future);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        awaitAsyncCompletion(allFutures, params.timeoutMinutes());
        return finalizeResults(params.reviewPasses(), collectAsyncResults(futures, target));
    }

    List<ReviewResult> executeStructured(Map<String, AgentConfig> agents,
                                         ReviewTarget target,
                                         ReviewContext sharedContext,
                                         AgentPassExecutor agentPassExecutor) {
        ExecutionParams params = executionParams(agents.size());
        List<SubtaskWithConfig> tasks = new ArrayList<>(params.agentCount());
        try (var scope = StructuredTaskScope.<List<ReviewResult>>open()) {
            for (var config : agents.values()) {
                tasks.add(new SubtaskWithConfig(scope.fork(() -> executeAgentPasses(
                    config,
                    target,
                    sharedContext,
                    params.reviewPasses(),
                    params.perAgentTimeoutMinutes(),
                    true,
                    agentPassExecutor
                )), config));
            }

            joinStructuredWithTimeout(scope, params.timeoutMinutes());

            return finalizeResults(
                params.reviewPasses(),
                collectStructuredResults(tasks, target, params.perAgentTimeoutMinutes())
            );
        }
    }

    private ExecutionParams executionParams(int agentCount) {
        int reviewPasses = executionConfig.reviewPasses();
        return new ExecutionParams(
            reviewPasses,
            agentCount,
            executionConfig.orchestratorTimeoutMinutes(),
            perAgentTimeoutMinutes()
        );
    }

    private long perAgentTimeoutMinutes() {
        return executionConfig.agentTimeoutMinutes() * (executionConfig.maxRetries() + 1L);
    }

    private List<ReviewResult> summarizeTaskResult(SubtaskWithConfig taskWithConfig,
                                                   ReviewTarget target,
                                                   long perAgentTimeoutMinutes) {
        return switch (taskWithConfig.subtask().state()) {
            case SUCCESS -> taskWithConfig.subtask().get();
            case FAILED -> {
                Throwable cause = taskWithConfig.subtask().exception();
                yield ReviewResult.failedResults(taskWithConfig.config(), target.displayName(), executionConfig.reviewPasses(),
                    "Review failed: " + (cause != null ? cause.getMessage() : "unknown"));
            }
            case UNAVAILABLE -> ReviewResult.failedResults(taskWithConfig.config(), target.displayName(), executionConfig.reviewPasses(),
                "Review cancelled after " + perAgentTimeoutMinutes + " minutes");
        };
    }

    private List<ReviewResult> collectStructuredResults(
            List<SubtaskWithConfig> tasks,
            ReviewTarget target,
            long perAgentTimeoutMinutes) {
        List<ReviewResult> results = new ArrayList<>(tasks.size() * executionConfig.reviewPasses());
        for (var task : tasks) {
            results.addAll(summarizeTaskResult(task, target, perAgentTimeoutMinutes));
        }
        return results;
    }

    private List<ReviewResult> finalizeResults(int reviewPasses, List<ReviewResult> results) {
        return reviewResultPipeline.finalizeResults(results, reviewPasses);
    }

    private List<ReviewResult> collectAsyncResults(List<CompletableFuture<List<ReviewResult>>> futures,
                                                   ReviewTarget target) {
        List<ReviewResult> results = new ArrayList<>(futures.size() * executionConfig.reviewPasses());
        for (CompletableFuture<List<ReviewResult>> future : futures) {
            List<ReviewResult> perAgentResults = future.getNow(List.of());
            if (perAgentResults == null || perAgentResults.isEmpty()) {
                logger.warn("Review future completed without per-agent results for target {}", target.displayName());
                continue;
            }
            results.addAll(perAgentResults);
        }
        return results;
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

    private void joinStructuredWithTimeout(StructuredTaskScope<List<ReviewResult>, Void> scope,
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

    private List<ReviewResult> executeAgentPasses(AgentConfig config,
                                                  ReviewTarget target,
                                                  ReviewContext sharedContext,
                                                  int reviewPasses,
                                                  long perAgentTimeoutMinutes,
                                                  boolean structured,
                                                  AgentPassExecutor agentPassExecutor) {
        logAgentStart(config, reviewPasses, structured);
        return agentPassExecutor.execute(
            config,
            target,
            sharedContext,
            reviewPasses,
            perAgentTimeoutMinutes
        );
    }

    private void logAgentStart(AgentConfig config,
                              int reviewPasses,
                              boolean structured) {
        if (reviewPasses <= 1) {
            return;
        }
        if (structured) {
            logger.info("Agent {}: starting {} passes (structured)",
                config.name(), reviewPasses);
            return;
        }
        logger.info("Agent {}: starting {} passes",
            config.name(), reviewPasses);
    }

}