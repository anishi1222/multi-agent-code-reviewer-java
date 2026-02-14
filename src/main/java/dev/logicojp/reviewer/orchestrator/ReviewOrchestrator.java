package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewAgent;
import dev.logicojp.reviewer.agent.ReviewContext;
import dev.logicojp.reviewer.config.ExecutionConfig;
import dev.logicojp.reviewer.config.GithubMcpConfig;
import dev.logicojp.reviewer.instruction.CustomInstruction;
import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.FeatureFlags;
import com.github.copilot.sdk.CopilotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Orchestrates parallel execution of multiple review agents.
public class ReviewOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviewOrchestrator.class);
    
    private final CopilotClient client;
    private final String githubToken;
    private final GithubMcpConfig githubMcpConfig;
    private final ExecutionConfig executionConfig;
    private final Semaphore concurrencyLimit;
    private final List<CustomInstruction> customInstructions;
    private final String reasoningEffort;
    private final String outputConstraints;
    private final boolean structuredConcurrencyEnabled;
    /// Lazily initialized — not created when Structured Concurrency mode is active.
    private volatile ExecutorService executorService;

    public ReviewOrchestrator(CopilotClient client,
                              String githubToken,
                              GithubMcpConfig githubMcpConfig,
                              ExecutionConfig executionConfig,
                              List<CustomInstruction> customInstructions,
                              String reasoningEffort,
                              String outputConstraints) {
        this.client = client;
        this.githubToken = githubToken;
        this.githubMcpConfig = githubMcpConfig;
        this.executionConfig = executionConfig;
        // Limit concurrent agent executions via --parallelism
        this.concurrencyLimit = new Semaphore(executionConfig.parallelism());
        this.customInstructions = customInstructions != null ? List.copyOf(customInstructions) : List.of();
        this.reasoningEffort = reasoningEffort;
        this.outputConstraints = outputConstraints;
        this.structuredConcurrencyEnabled = FeatureFlags.isStructuredConcurrencyEnabled();
        
        logger.info("Parallelism set to {}", executionConfig.parallelism());
        if (!this.customInstructions.isEmpty()) {
            logger.info("Custom instructions loaded ({} instruction(s))", this.customInstructions.size());
        }
    }
    
    /// Executes reviews for all provided agents in parallel.
    /// @param agents Map of agent name to AgentConfig
    /// @param target The target to review (GitHub repository or local directory)
    /// @return List of ReviewResults from all agents
    public List<ReviewResult> executeReviews(Map<String, AgentConfig> agents, ReviewTarget target) {
        logger.info("Starting parallel review for {} agents on target: {}", 
            agents.size(), target.displayName());

        if (structuredConcurrencyEnabled) {
            return executeReviewsStructured(agents, target);
        }
        
        List<CompletableFuture<ReviewResult>> futures = new ArrayList<>(agents.size());
        long timeoutMinutes = executionConfig.orchestratorTimeoutMinutes();
        // Per-agent timeout accounts for retries: each attempt gets the full agent timeout
        long perAgentTimeoutMinutes = executionConfig.agentTimeoutMinutes()
            * (executionConfig.maxRetries() + 1L);

        ExecutorService executor = getOrCreateExecutorService();
        
        for (var config : agents.values()) {
            var context = new ReviewContext(client, githubToken, githubMcpConfig,
                executionConfig.agentTimeoutMinutes(), executionConfig.idleTimeoutMinutes(),
                customInstructions, reasoningEffort,
                executionConfig.maxRetries(), outputConstraints);
            var agent = new ReviewAgent(config, context);
            CompletableFuture<ReviewResult> future = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        concurrencyLimit.acquire();
                        try {
                            return agent.review(target);
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
                }, executor)
                .orTimeout(perAgentTimeoutMinutes, TimeUnit.MINUTES)
                .exceptionally(ex -> {
                    logger.error("Agent {} failed with timeout or error: {}", 
                        config.name(), ex.getMessage());
                    return ReviewResult.builder()
                        .agentConfig(config)
                        .repository(target.displayName())
                        .success(false)
                        .errorMessage("Review timed out or failed: " + ex.getMessage())
                        .build();
                });
            futures.add(future);
        }
        
        // Wait for all reviews to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.get(timeoutMinutes + 1, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Error waiting for reviews to complete: {}", e.getMessage());
        }
        
        // Collect results
        List<ReviewResult> results = new ArrayList<>(futures.size());
        for (CompletableFuture<ReviewResult> future : futures) {
            try {
                results.add(future.getNow(null));
            } catch (Exception e) {
                logger.error("Error collecting review result: {}", e.getMessage());
            }
        }
        
        // Filter out nulls
        results.removeIf(Objects::isNull);
        
        logger.info("Completed {} reviews (success: {}, failed: {})",
            results.size(),
            results.stream().filter(ReviewResult::isSuccess).count(),
            results.stream().filter(r -> !r.isSuccess()).count());
        
        return results;
    }

    private List<ReviewResult> executeReviewsStructured(Map<String, AgentConfig> agents, ReviewTarget target) {
        long timeoutMinutes = executionConfig.orchestratorTimeoutMinutes();
        long perAgentTimeoutMinutes = executionConfig.agentTimeoutMinutes()
            * (executionConfig.maxRetries() + 1L);

        List<StructuredTaskScope.Subtask<ReviewResult>> tasks = new ArrayList<>(agents.size());
        try (var scope = StructuredTaskScope.<ReviewResult>open()) {
            for (var config : agents.values()) {
                var context = new ReviewContext(client, githubToken, githubMcpConfig,
                    executionConfig.agentTimeoutMinutes(), executionConfig.idleTimeoutMinutes(),
                    customInstructions, reasoningEffort,
                    executionConfig.maxRetries(), outputConstraints);
                var agent = new ReviewAgent(config, context);
                tasks.add(scope.fork(() -> {
                    try {
                        concurrencyLimit.acquire();
                        try {
                            return agent.review(target);
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
                    } catch (Exception e) {
                        return ReviewResult.builder()
                            .agentConfig(config)
                            .repository(target.displayName())
                            .success(false)
                            .errorMessage("Review failed: " + e.getMessage())
                            .build();
                    }
                }));
            }

            // Workaround: StructuredTaskScope.join() does not support timeout natively.
            // Wrapping in CompletableFuture.runAsync() to enforce a wall-clock deadline.
            // TODO: Replace with scope.joinUntil(Instant) when available in a future JDK release.
            var joinFuture = CompletableFuture.runAsync(() -> {
                try {
                    scope.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });

            try {
                joinFuture.get(timeoutMinutes + 1, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                logger.error("Structured concurrency timed out after {} minutes", timeoutMinutes, e);
                scope.close();
            } catch (ExecutionException e) {
                logger.error("Structured concurrency join failed", e);
            }

            List<ReviewResult> results = new ArrayList<>(tasks.size());
            for (var task : tasks) {
                results.add(summarizeTaskResult(task, target, perAgentTimeoutMinutes));
            }

            results.removeIf(Objects::isNull);
            logger.info("Completed {} reviews (success: {}, failed: {})",
                results.size(),
                results.stream().filter(ReviewResult::isSuccess).count(),
                results.stream().filter(r -> !r.isSuccess()).count());
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Structured concurrency interrupted", e);
            return List.of();
        }
    }

    private ReviewResult summarizeTaskResult(StructuredTaskScope.Subtask<ReviewResult> task,
                                             ReviewTarget target,
                                             long perAgentTimeoutMinutes) {
        return switch (task.state()) {
            case SUCCESS -> task.get();
            case FAILED -> {
                Throwable cause = task.exception();
                yield ReviewResult.builder()
                    .agentConfig(null)
                    .repository(target.displayName())
                    .success(false)
                    .errorMessage("Review failed: " + (cause != null ? cause.getMessage() : "unknown"))
                    .build();
            }
            case UNAVAILABLE -> ReviewResult.builder()
                .agentConfig(null)
                .repository(target.displayName())
                .success(false)
                .errorMessage("Review cancelled after " + perAgentTimeoutMinutes + " minutes")
                .build();
        };
    }
    
    /// Shuts down the executor service.
    public void shutdown() {
        if (executorService == null) return;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException _) {
            // Java 22+: Unnamed variable - exception not used
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private ExecutorService getOrCreateExecutorService() {
        if (executorService == null) {
            executorService = Executors.newVirtualThreadPerTaskExecutor();
        }
        return executorService;
    }
}
